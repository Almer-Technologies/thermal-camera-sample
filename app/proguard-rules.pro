# FLIR Thermal SDK: the native library calls Java classes, fields and methods by exact name,
# including the patched LeptonServiceV1Polyfill. R8 cannot see those calls, so without this
# rule it removes or renames most of the SDK and the app crashes on connect.
-keep class com.flir.** { *; }
-dontwarn com.flir.**

# FLIR 2.24.0 references annotations it does not ship. Without this R8 fails the build.
-dontwarn org.jetbrains.annotations.**
