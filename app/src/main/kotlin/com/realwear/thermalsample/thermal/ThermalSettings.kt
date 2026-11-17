package com.realwear.thermalsample.thermal

import com.flir.thermalsdk.image.fusion.FusionMode

/**
 * The values RealWear uses in its own apps for the integrated thermal module.
 *
 * [externalOpticsTransmission] corrects for the protective window in front of the sensor.
 * Without it, every temperature reads too low.
 */
data class ThermalSettings(
    val emissivity: Double = 0.95,
    val externalOpticsTransmission: Double = 0.85,
    val objectDistance: ObjectDistance = ObjectDistance.FAR,
    val paletteName: String = "iron",
    val gain: Gain = Gain.HIGH,
    val fusionMode: FusionMode = FusionMode.MSX,
) {
    /** Index into `RemoteControl.temperatureRange`. High gain is the low temperature range. */
    enum class Gain(val rangeIndex: Int) {
        HIGH(0),
        LOW(1),
    }

    /**
     * Distance to the object. It aligns the thermal and visual images for MSX and corrects the
     * temperature for the air between the sensor and the object.
     */
    enum class ObjectDistance(val meters: Double) {
        NEAR(0.3),
        MIDDLE(0.9),
        FAR(3.0),
    }
}
