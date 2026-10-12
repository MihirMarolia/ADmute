# ADMute · Google TV sensory comfort

An Android TV accessibility app that recognizes **English player ad labels** in YouTube (`com.google.android.youtube.tv`), Tubi (`com.tubitv`), and Plex (`com.plexapp.android`). Independently enable media muting and an opaque, static, low-contrast dark weave over the picture. Ads continue playing normally.

Repository: [MihirMarolia/ADmute](https://github.com/MihirMarolia/ADmute). The root-level `app-debug.apk`, its checksum, `ad-mute-tv-source.zip`, and `AdMute TV.md` are historical deliverables from the earlier implementation. For this implementation, build the source in `app/` or download the **ADMute-debug** artifact from this branch's successful GitHub Actions run. See [the earlier implementation review](docs/LEGACY_REVIEW.md).

The new application ID is `org.admute`; the earlier app used `com.example.admutetv`. This installs alongside the earlier app. **Disable the old AdMute TV accessibility service before enabling this one**, so two services do not compete over the shared media volume. Old settings are not imported.

**Status: the TCL Android 11 device test failed. YouTube exposes empty labels, and the zero-volume strategy was not honored on the HDMI ARC route. Comfort protection is paused on the tested TV. Version 0.1.1 is diagnostic, not a verified fix.** See [the measured findings and required compatibility gates](docs/TCL_TEST_2026-10-12.md). Allowlisting an app is not proof that its accessibility tree exposes usable ad labels. Custom video players, languages, or changed layouts can prevent detection. This app cannot guarantee protection from loud audio or flashing imagery, including the start of an ad before its label appears.

## Install and use

1. Install the debug APK on a Google TV / Android TV device running Android 8.0 or newer. See [the device test guide](docs/DEVICE_TESTING.md) for ADB commands.
2. Open ADMute from the TV app launcher. Choose apps, mute, and dark cover.
3. Select **Enable accessibility service**, read the explanation, and enable ADMute in TV accessibility settings. Some devices require allowing restricted settings for sideloaded apps; follow that device's settings interface.
4. Test one full ad break per app at a comfortable baseline volume. Opening a movie, browsing, and trailers must remain uncovered.
5. During a false trigger, **press Back twice within 0.8 seconds** to release protection and pause for one minute. These presses still reach the streaming app and may navigate back. Home / leaving the app also releases protection. If key filtering is unavailable because of device behavior or another accessibility service, use Home and the ADMute pause button.

The dark texture is drawn locally and never animates. **Preview dark texture** lets you inspect it without affecting sound. No internet permission, analytics, accounts, OCR, ad skipping, or external image assets are used. Release builds do not request screenshot capability. Debug builds allow an explicit developer screenshot probe; pixels are discarded after calculating brightness counts. There is no continuous capture, recording, image storage, or upload. Accessibility access is broad enough to observe foreground changes, but text traversal occurs only in selected apps. Screen labels remain in memory only. The detection check retains an app package, count, completeness flag, and evidence category, never titles or screen text. Debug event diagnostics retain only known marker categories and counts in memory.

## Build

Use JDK 17, Android SDK platform 35, and build-tools 35.0.0. The Gradle wrapper pins Gradle 8.13; Android Gradle Plugin is pinned to 8.10.1. Set `ANDROID_HOME` to your SDK, or configure `sdk.dir` in your local, ignored `local.properties`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed test build. Production signing and store publication are separate release work. CI repeats unit tests, lint, and APK assembly on changes.

A local `.signing/debug.keystore`, if present, is reused for test builds and ignored by Git. Preserve this local key to update an installed test APK without losing its signature. CI artifacts use the runner's debug key and may require reinstalling an earlier test build; they are not production-signed releases. Never commit signing keys.

## Reliability model

- Fresh accessibility trees, rather than stale event text, drive detection. Reads are bounded; selected apps are polled every 350 ms.
- Specific anchored labels need a player/control context. YouTube also recognizes the user-observed pair `Sponsored` + `Send to phone` in eligible fresh labels. Either label alone, bare `Ad`, `Skip intro`, titles, descriptions, and recommendation containers do not qualify.
- Two positive samples separated by at least 300 ms confirm an ad. Confirmed clear reads need 1.5 seconds to release, bridging short gaps within a pod. Missing active roots or leaving a selected app release immediately.
- Incomplete reads within the same known app allow at most 8 seconds since the last positive evidence. A 3-minute maximum never extends while labels stay present. Timeout releases require stable clear evidence before another ad can trigger.
- The original media volume is stored before changing it to zero. Repeated detections never overwrite it. On release, restore only if the volume is still the zero ADMute wrote. A nonzero user adjustment is respected and not muted again within that session. Already-silent devices stay silent.
- Recovery attempts run on service reconnect; lifecycle exits release the cover and sound. Abrupt process death can leave media volume at zero until reconnect. If that happens, raise the volume manually or re-enable the service. A saved volume remains until recovery succeeds. The overlay itself is removed by Android when its process dies.
- Fixed-volume HDMI/CEC, external soundbars, and devices that ignore Android media-volume writes may prevent muting. The app does not claim control over external audio devices. Test your actual audio route. A volume change to the same zero value cannot be distinguished from ADMute's zero.

See [SDLC and requirements](docs/SDLC.md), [architecture](docs/ARCHITECTURE.md), and [device testing](docs/DEVICE_TESTING.md). Rules are intentionally conservative; expand them using inspected player labels and regression tests from your own TV.

## Android references

- [Accessibility services](https://developer.android.com/guide/topics/ui/accessibility/service)
- [Accessibility overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY)
- [AudioManager and fixed-volume devices](https://developer.android.com/reference/android/media/AudioManager)
