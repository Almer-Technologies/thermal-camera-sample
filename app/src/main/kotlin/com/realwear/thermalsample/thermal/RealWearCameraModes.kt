package com.realwear.thermalsample.thermal

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Switches the RealWear main camera to the modes the thermal module needs, and back.
 *
 * Thermal fusion uses the visual camera, which must run in binning mode with EIS and the
 * narrow field of view off. RealWear Settings owns these modes and exposes them through two
 * private broadcasts. [applyThermalModes] saves the user's modes first, [restore] puts them back.
 */
class RealWearCameraModes(private val context: Context) {

    private var savedModes: Modes? = null
    private var isReceiverRegistered = false

    private val modesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val current = Modes(
                sensor = intent.getStringExtra(EXTRA_SENSOR),
                eis = intent.getStringExtra(EXTRA_EIS),
                fov = intent.getStringExtra(EXTRA_FOV),
            )
            savedModes = current
            Log.d(TAG, "Saved camera modes $current, switching to thermal modes")
            send(THERMAL_MODES)
        }
    }

    fun applyThermalModes() {
        // Settings runs as the system uid, so a not-exported receiver still gets its reply.
        ContextCompat.registerReceiver(
            context,
            modesReceiver,
            IntentFilter(ACTION_GET),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        isReceiverRegistered = true
        context.sendBroadcast(Intent(ACTION_GET).setComponent(ComponentName(SETTINGS_PACKAGE, GET_RECEIVER)))
    }

    fun restore() {
        if (isReceiverRegistered) {
            context.unregisterReceiver(modesReceiver)
            isReceiverRegistered = false
        }
        savedModes?.let {
            Log.d(TAG, "Restoring camera modes $it")
            send(it)
        }
        savedModes = null
    }

    private fun send(modes: Modes) {
        val intent = Intent(ACTION_SET).setComponent(ComponentName(SETTINGS_PACKAGE, SET_RECEIVER))
        modes.sensor?.let { intent.putExtra(EXTRA_SENSOR, it) }
        modes.eis?.let { intent.putExtra(EXTRA_EIS, it) }
        modes.fov?.let { intent.putExtra(EXTRA_FOV, it) }
        context.sendBroadcast(intent)
    }

    private data class Modes(val sensor: String?, val eis: String?, val fov: String?)

    private companion object {
        const val TAG = "RealWearCameraModes"
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val ACTION_GET = "com.android.settings.realwear_camera_GET"
        const val ACTION_SET = "com.android.settings.realwear_camera_SET"
        const val GET_RECEIVER = "com.android.settings.RealwearCameraGetReceiver"
        const val SET_RECEIVER = "com.android.settings.RealwearCameraSetReceiver"
        const val EXTRA_SENSOR = "sensor"
        const val EXTRA_EIS = "eis"

        // "off" is the wide field of view, "on" is the narrow one.
        const val EXTRA_FOV = "fov"

        val THERMAL_MODES = Modes(sensor = "binning", eis = "off", fov = "off")
    }
}
