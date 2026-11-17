package com.realwear.thermalsample

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realwear.thermalsample.ui.ThermalScreen

class MainActivity : ComponentActivity() {

    private val viewModel: ThermalViewModel by viewModels()
    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.start()
    }

    // A 180 degree turn between landscape and reverse landscape is not a configuration change.
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == display.displayId) viewModel.setDisplayFlipped(isDisplayFlipped())
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            ThermalScreen(
                state = state,
                hasCameraPermission = hasCameraPermission,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onObjectDistance = viewModel::setObjectDistance,
                onTakePicture = viewModel::takePicture,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService<DisplayManager>()?.registerDisplayListener(displayListener, null)
        viewModel.setDisplayFlipped(isDisplayFlipped())
        hasCameraPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPermission) {
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onStop() {
        getSystemService<DisplayManager>()?.unregisterDisplayListener(displayListener)
        viewModel.stop()
        super.onStop()
    }

    /**
     * The thermal module sits on the right side of the camera arm. Worn on the left eye, the
     * headset turns upside down, so the thermal image must turn 180 degrees too.
     */
    private fun isDisplayFlipped(): Boolean {
        val rotation = display.rotation
        return rotation == Surface.ROTATION_180 || rotation == Surface.ROTATION_270
    }
}
