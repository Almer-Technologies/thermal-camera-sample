package com.realwear.thermalsample

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid
import com.realwear.thermalsample.thermal.RealWearCameraModes
import com.realwear.thermalsample.thermal.TemperatureOverlay
import com.realwear.thermalsample.thermal.ThermalCamera
import com.realwear.thermalsample.thermal.ThermalFrame
import com.realwear.thermalsample.thermal.ThermalSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ThermalUiState())
    val state: StateFlow<ThermalUiState> = _state.asStateFlow()

    // FLIR SDK calls block and must not overlap, so they all run on one thread.
    private val cameraDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val cameraModes = RealWearCameraModes(application)
    private var sessionJob: Job? = null
    private var isDisplayFlipped = false

    @Volatile
    private var thermalCamera: ThermalCamera? = null
    private var frameCount = 0L

    init {
        ThermalSdkAndroid.init(application)
        val version = ThermalSdkAndroid.getVersion()
        Log.i(TAG, "FLIR SDK version $version")
        _state.update { it.copy(sdkVersion = version) }
    }

    fun start() {
        if (sessionJob?.isActive == true) return
        frameCount = 0
        val camera = ThermalCamera(ThermalSettings(objectDistance = _state.value.objectDistance))
        camera.isDisplayFlipped = isDisplayFlipped
        sessionJob = viewModelScope.launch(cameraDispatcher) {
            _state.update { it.copy(status = ThermalStatus.Searching) }
            try {
                if (!camera.connect()) {
                    _state.update { it.copy(status = ThermalStatus.NoModule) }
                    return@launch
                }
                cameraModes.applyThermalModes()
                thermalCamera = camera
                _state.update { it.copy(status = ThermalStatus.Connecting) }
                camera.startStreaming(onFrame = ::onFrame, onCalibrating = ::onCalibrating, onError = ::onStreamError)
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Thermal start failed", e)
                _state.update { it.copy(status = ThermalStatus.Error(e.message.orEmpty())) }
            } finally {
                withContext(NonCancellable) {
                    thermalCamera = null
                    camera.close()
                    cameraModes.restore()
                    Log.i(TAG, "Thermal session stopped after $frameCount frames")
                }
            }
        }
    }

    fun stop() {
        thermalCamera = null
        sessionJob?.cancel()
        _state.update { it.copy(status = ThermalStatus.Starting, frame = null, isCalibrating = false) }
    }

    fun setDisplayFlipped(isFlipped: Boolean) {
        isDisplayFlipped = isFlipped
        thermalCamera?.isDisplayFlipped = isFlipped
    }

    fun setObjectDistance(distance: ThermalSettings.ObjectDistance) {
        _state.update { it.copy(objectDistance = distance) }
        val camera = thermalCamera ?: return
        viewModelScope.launch(cameraDispatcher) { camera.setObjectDistance(distance) }
    }

    fun takePicture() {
        val camera = thermalCamera ?: return
        viewModelScope.launch {
            val (file, saved) = withContext(Dispatchers.IO) {
                val directory = File(getApplication<Application>().getExternalFilesDir(null), "pictures").apply { mkdirs() }
                val file = File(directory, "thermal_${System.currentTimeMillis()}.jpg")
                file to camera.takePicture(file)
            }
            Log.i(TAG, "Take picture: saved=$saved file=${file.absolutePath}")
            _state.update { it.copy(lastPicture = if (saved) file.name else null, pictureFailed = !saved) }
        }
    }

    private fun onFrame(frame: ThermalFrame) {
        // A frame can still arrive while the session closes. Do not show it.
        if (thermalCamera == null) return
        frameCount++
        if (frameCount == 1L || frameCount % FRAME_LOG_INTERVAL == 0L) {
            Log.d(TAG, "Frame $frameCount ${frame.bitmap.width}x${frame.bitmap.height} spot=${frame.celsius} C")
        }
        val bitmap = TemperatureOverlay.draw(frame.bitmap, frame.celsius, frame.fahrenheit)
        _state.update { it.copy(status = ThermalStatus.Streaming, frame = bitmap) }
    }

    private fun onCalibrating(isCalibrating: Boolean) {
        _state.update { it.copy(isCalibrating = isCalibrating) }
    }

    private fun onStreamError(message: String) {
        Log.e(TAG, "Thermal stream error: $message")
        _state.update { it.copy(status = ThermalStatus.Error(message)) }
    }

    private companion object {
        const val TAG = "ThermalViewModel"
        const val FRAME_LOG_INTERVAL = 100L
    }
}

data class ThermalUiState(
    val status: ThermalStatus = ThermalStatus.Starting,
    val frame: Bitmap? = null,
    val isCalibrating: Boolean = false,
    val objectDistance: ThermalSettings.ObjectDistance = ThermalSettings().objectDistance,
    val sdkVersion: String = "",
    val lastPicture: String? = null,
    val pictureFailed: Boolean = false,
)

sealed interface ThermalStatus {
    data object Starting : ThermalStatus
    data object Searching : ThermalStatus
    data object Connecting : ThermalStatus
    data object Streaming : ThermalStatus
    data object NoModule : ThermalStatus
    data class Error(val message: String) : ThermalStatus
}
