# Thermal SDK sample

A sample Android app that shows how to use the thermal camera of RealWear Navigator headsets
with the FLIR Atlas Android SDK. It shows the live thermal image, the temperature at the
crosshair, and saves a radiometric FLIR JPEG.

The sample is built against **FLIR Atlas Android SDK 2.24.0** (RealWear package).

## Get the SDK

The FLIR SDK is not in this repository. Ask your RealWear contact for the two SDK 2.24.0 files:

- `thermalsdk-release.aar`
- `androidsdk-release.aar`

## Build and run

You need JDK 21 and Android SDK platform 37.

1. Put the two `.aar` files in `app/libs/`.
2. Connect the headset and run `./gradlew :app:installDebug`.
3. On the headset, open **My Programs → Thermal SDK Sample**.

Say **"Near"**, **"Middle"** or **"Far"** to set the distance to the object. Say **"Take picture"** to
save a picture. Say **"Navigate home"** to close the app.

## Requirements

- SDK 2.24.0 needs `minSdk` 33 and `compileSdk` 37.
- If your app's `minSdk` is lower than 33, add
  `<uses-sdk tools:overrideLibrary="com.flir.thermalsdk, com.flir.androidsdk" />` to your
  `AndroidManifest.xml`. Then use the thermal camera only when `Build.VERSION.SDK_INT >= 33`.
- If your app uses R8, add these rules:

  ```
  -keep class com.flir.** { *; }
  -dontwarn com.flir.**
  -dontwarn org.jetbrains.annotations.**
  ```

## Updating from SDK 1.14.0-dev to 2.24.0

1. Replace your two SDK 1.14.0-dev `.aar` files with the two 2.24.0 files. SDK 2.24.0 supports
   16 KB memory pages, which Google Play requires. The RealWear package also includes a fix for
   Android 16, so you do not need to change anything for Android 16.
2. Make these code changes:

| SDK 1.14.0-dev | SDK 2.24.0 |
|---|---|
| `com.flir.thermalsdk.image.palettes.Palette`, `PaletteManager` | `com.flir.thermalsdk.image.Palette`, `PaletteManager` |
| `thermalImage.imageParameters.emissivity` | `thermalImage.thermalParameters.objectEmissivity` |
| `thermalImage.imageParameters.externalOpticsTransmission` | `thermalImage.thermalParameters.externalOpticsTransmission` |
| `thermalImage.imageParameters.distance` | `thermalImage.thermalParameters.objectDistance` |
| `DiscoveryEventListener.onCameraFound(Identity)` | Override `onCameraFound(DiscoveredCamera)` and use `discoveredCamera.identity` |
| `Stream.toggleRotation()` | Removed. Rotate the image in your app (see below). |

3. Rotate the image yourself. SDK 2.24.0 gives a portrait image (480x640), while 1.14.0-dev
   gave a landscape one. Rotate the bitmap 270° for the right eye and 90° for the left eye. To
   save a landscape picture, call `thermalImage.rotate(...)` just before `saveAs(...)`. See
   `ThermalCamera.kt`.

In Java, `new ThermalStreamer(...)`, `Stream.start(...)` and `ThermalStreamer.update()` throw
`ErrorCodeException` in SDK 2.24.0. Kotlin code does not change.

## Where to look in the code

- `ThermalCamera.kt`: check that the headset has a thermal camera (`isModuleAvailable()`),
  connect, stream, read the temperature in °C and °F, change the object distance, save a picture.
- `TemperatureOverlay.kt`: draws the crosshair and the temperature into the frame. Use it when
  you send frames to someone else, for example in a video call.
- `RealWearCameraModes.kt`: sets the headset camera modes that the thermal camera needs, and
  restores them after.
- `ThermalSettings.kt`: the recommended thermal settings and the object distances
  (Near 0.3 m, Middle 0.9 m, Far 3 m).

The image stops for 1 to 2 seconds from time to time. The thermal camera recalibrates itself.
The sample shows "Calibrating…" while this happens (`Calibration.nucState()`).

## License

The sample code is licensed under the [Apache License 2.0](LICENSE). The FLIR SDK is not part of
this repository and is not covered by this license. It is Teledyne FLIR software under its own terms.

## Note for RealWear

The Android 16 fix in `androidsdk-release.aar` replaces the class
`com.flir.thermalsdk.androidsdk.live.connectivity.integrated.LeptonServiceV1Polyfill`. It stores
the calibration file in the app cache, because Android 16 blocks the location FLIR uses.
