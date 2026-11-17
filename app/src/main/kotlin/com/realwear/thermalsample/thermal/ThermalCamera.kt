package com.realwear.thermalsample.thermal

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.flir.thermalsdk.ErrorCode
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid
import com.flir.thermalsdk.image.PaletteManager
import com.flir.thermalsdk.image.Point
import com.flir.thermalsdk.image.RotationAngle
import com.flir.thermalsdk.image.ThermalImage
import com.flir.thermalsdk.live.Camera
import com.flir.thermalsdk.live.CommunicationInterface
import com.flir.thermalsdk.live.ConnectParameters
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.live.discovery.DiscoveredCamera
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener
import com.flir.thermalsdk.live.discovery.DiscoveryFactory
import com.flir.thermalsdk.live.remote.Calibration
import com.flir.thermalsdk.live.streaming.Stream
import com.flir.thermalsdk.live.streaming.ThermalStreamer
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One session with the integrated thermal module: discover, connect, stream, close.
 *
 * FLIR SDK calls block. Call [connect], [setObjectDistance] and [close] from one background
 * thread or a single-thread dispatcher. Frames arrive on a FLIR SDK thread.
 */
class ThermalCamera(private val settings: ThermalSettings = ThermalSettings()) {

    private var camera: Camera? = null
    private var stream: Stream? = null
    private var streamer: ThermalStreamer? = null

    @Volatile
    private var objectDistance = settings.objectDistance

    // The image settings are applied on the next frame, because only frames give a ThermalImage.
    @Volatile
    private var applyImageSettingsOnNextFrame = true

    @Volatile
    var isDisplayFlipped = false

    private val pictureRequest = AtomicReference<PictureRequest?>(null)

    /** Returns false when no thermal camera is found. */
    suspend fun connect(): Boolean {
        val identity = discover(DISCOVERY_TIMEOUT) ?: return false
        Log.d(TAG, "Found thermal camera ${identity.deviceId} on ${identity.communicationInterface}")
        camera = connectWithRetry(identity)
        return true
    }

    /**
     * [onCalibrating] is true while the module recalibrates (NUC). The image then stops for a
     * second or two. The module does this from time to time and when its temperature changes.
     */
    fun startStreaming(
        onFrame: (ThermalFrame) -> Unit,
        onCalibrating: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ) {
        val connectedCamera = checkNotNull(camera) { "Call connect() first" }
        val thermalStream = connectedCamera.streams.firstOrNull { it.isThermal }
            ?: throw ThermalCameraException("The camera has no thermal stream")
        val thermalStreamer = ThermalStreamer(thermalStream)
        stream = thermalStream
        streamer = thermalStreamer
        applyCameraSettings(connectedCamera)
        connectedCamera.remoteControl?.calibration?.nucState()?.subscribe { state ->
            Log.d(TAG, "Calibration state $state")
            onCalibrating(state == Calibration.NucState.PROGRESS)
        }
        thermalStream.start(
            { onFrame(readFrame(thermalStreamer)) },
            { error: ErrorCode -> onError(error.message) },
        )
        Log.d(TAG, "Thermal stream started")
    }

    fun setObjectDistance(distance: ThermalSettings.ObjectDistance) {
        objectDistance = distance
        camera?.remoteControl?.fusionController?.distance()?.setSync(distance.meters)
        applyImageSettingsOnNextFrame = true
        Log.i(TAG, "Object distance set to $distance")
    }

    /** Saves the next frame as a radiometric FLIR JPEG. Returns false on timeout or error. */
    suspend fun takePicture(file: File): Boolean {
        val request = PictureRequest(file)
        pictureRequest.set(request)
        return withTimeoutOrNull(PICTURE_TIMEOUT) { request.result.await() } ?: false
    }

    fun close() {
        camera?.remoteControl?.calibration?.nucState()?.unsubscribe()
        stream?.stop()
        streamer?.close()
        camera?.disconnect()
        camera?.close()
        stream = null
        streamer = null
        camera = null
        Log.d(TAG, "Thermal camera closed")
    }

    private suspend fun connectWithRetry(identity: Identity): Camera {
        repeat(CONNECT_ATTEMPTS) { attempt ->
            val newCamera = Camera()
            try {
                newCamera.connect(
                    identity,
                    { reason -> Log.d(TAG, "Thermal camera disconnected: ${reason?.message}") },
                    ConnectParameters(),
                )
                Log.d(TAG, "Connected on attempt ${attempt + 1}")
                return newCamera
            } catch (e: Exception) {
                Log.w(TAG, "Connect attempt ${attempt + 1} of $CONNECT_ATTEMPTS failed", e)
                newCamera.close()
                delay(1.seconds)
            }
        }
        throw ThermalCameraException("Could not connect after $CONNECT_ATTEMPTS attempts")
    }

    private fun applyCameraSettings(camera: Camera) {
        val remoteControl = camera.remoteControl ?: return
        remoteControl.temperatureRange?.selectedIndex()?.setSync(settings.gain.rangeIndex)
        remoteControl.fusionController?.distance()?.setSync(objectDistance.meters)
    }

    private fun readFrame(thermalStreamer: ThermalStreamer): ThermalFrame {
        thermalStreamer.update()
        val image = thermalStreamer.image
        var celsius: Double? = null
        var fahrenheit: Double? = null
        thermalStreamer.withThermalImage { thermalImage ->
            if (applyImageSettingsOnNextFrame) {
                applyImageSettings(thermalImage)
                applyImageSettingsOnNextFrame = false
            }
            val center = thermalImage.getValueAt(Point(thermalImage.width / 2, thermalImage.height / 2))
            celsius = center?.asCelsius()?.value
            fahrenheit = center?.asFahrenheit()?.value
            pictureRequest.getAndSet(null)?.let { savePicture(thermalImage, it) }
        }
        val bitmap = BitmapAndroid.createBitmap(image).bitMap.rotated(orientation().bitmapDegrees)
        return ThermalFrame(bitmap, celsius, fahrenheit)
    }

    private fun applyImageSettings(thermalImage: ThermalImage) {
        thermalImage.thermalParameters?.apply {
            objectEmissivity = settings.emissivity
            externalOpticsTransmission = settings.externalOpticsTransmission
            objectDistance = this@ThermalCamera.objectDistance.meters
        }
        thermalImage.palette = PaletteManager.getDefaultPalettes().firstOrNull { it.name == settings.paletteName }
        thermalImage.fusion?.setFusionMode(settings.fusionMode)
        Log.d(TAG, "Applied image settings $settings, distance $objectDistance, to a ${thermalImage.width}x${thermalImage.height} image")
    }

    private fun savePicture(thermalImage: ThermalImage, request: PictureRequest) {
        val saved = try {
            // rotate() changes only this frame's image, so the live stream stays as it was.
            thermalImage.rotate(orientation().imageAngle)
            thermalImage.saveAs(request.file.absolutePath)
            request.file.length() > 0
        } catch (e: Exception) {
            Log.w(TAG, "Saving the picture failed", e)
            false
        }
        request.result.complete(saved)
    }

    private fun orientation() = if (isDisplayFlipped) Orientation.LEFT_EYE else Orientation.RIGHT_EYE

    private fun Bitmap.rotated(degrees: Float): Bitmap =
        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, false)

    /**
     * SDK 2.24.0 streams the integrated module in portrait (480x640). Stream.toggleRotation() is
     * gone, Camera.setDisplayProperties() throws "unsupported camera type", and
     * ThermalImage.rotate() does not reach the rendered stream. So the app turns the live bitmap,
     * and turns the image by the same angle before it saves a picture.
     */
    private enum class Orientation(val bitmapDegrees: Float, val imageAngle: RotationAngle) {
        RIGHT_EYE(270f, RotationAngle.ROTATION_ANGLE_270_DEGREES),
        LEFT_EYE(90f, RotationAngle.ROTATION_ANGLE_90_DEGREES),
    }

    private class PictureRequest(val file: File) {
        val result = CompletableDeferred<Boolean>()
    }

    companion object {
        private const val TAG = "ThermalCamera"
        private const val CONNECT_ATTEMPTS = 10
        private val DISCOVERY_TIMEOUT = 10.seconds
        private val PICTURE_TIMEOUT = 5.seconds

        /**
         * Returns true when the FLIR SDK finds the headset's integrated thermal camera within
         * [timeout]. Use it before you show a thermal button. Call it after `ThermalSdkAndroid.init`.
         */
        suspend fun isModuleAvailable(timeout: Duration = 3.seconds): Boolean = discover(timeout) != null

        /** Returns null when no thermal camera is found within [timeout]. */
        private suspend fun discover(timeout: Duration): Identity? = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : DiscoveryEventListener {
                    override fun onCameraFound(discoveredCamera: DiscoveredCamera) {
                        DiscoveryFactory.getInstance().stop(CommunicationInterface.INTEGRATED)
                        if (continuation.isActive) continuation.resume(discoveredCamera.identity)
                    }

                    override fun onDiscoveryError(communicationInterface: CommunicationInterface, error: ErrorCode) {
                        Log.i(TAG, "Discovery error: ${error.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                continuation.invokeOnCancellation { DiscoveryFactory.getInstance().stop(CommunicationInterface.INTEGRATED) }
                DiscoveryFactory.getInstance().scan(listener, CommunicationInterface.INTEGRATED)
            }
        }
    }
}

data class ThermalFrame(val bitmap: Bitmap, val celsius: Double?, val fahrenheit: Double?)

class ThermalCameraException(message: String) : Exception(message)
