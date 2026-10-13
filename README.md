# Random Image Flash

Android app that rapidly switches between generated abstract images and photos selected from the device.

## Features
- Adjustable image-change rate from 1 to 20 Hz.
- Generated random abstract frames.
- Select multiple images using Android's system picker.
- Randomized or picker/list order for selected photos.
- Fit or crop photos without changing the display area's dimensions.
- Start/pause and skip-frame controls.
- GitHub Actions workflow builds a debug APK.

## Build
Requires JDK 17 and Android SDK 35. Run `gradle assembleDebug`.

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

The selected frequency is a target update interval. Actual rendering speed depends on device performance and Android scheduling. At 20 Hz, the target interval is 50 ms.
