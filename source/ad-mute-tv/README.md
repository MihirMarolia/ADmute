# AdMute TV

**AdMute TV** is a personal, sideloaded Android TV application that runs as an Android accessibility service. It watches for **accessibility text** exposed by the foreground application, mutes the shared media stream when a configured ad marker is visible, and restores the previous media-volume level after the marker disappears.

> This is a conservative assistive utility, **not a universal ad-recognition plug-in**. It does not record the display, capture audio, inspect network traffic, dismiss advertisements, or automate another app. It can only react when a foreground app exposes matching accessible text.

## Included deliverables

| Item | Purpose |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | Installable debug APK for personal sideloading. |
| `app/src/main/java/...` | Kotlin source for the accessibility service, text detector, volume guard, status overlay, and TV settings screen. |
| `technical_constraints.md` | Design constraints and evidence considered before implementation. |
| `README.md` | Build, installation, configuration, and safety guidance. |

## What it does

The accessibility service receives a small set of foreground-window events and inspects the exposed accessibility-node text and descriptions. This is a documented capability when the service declares `canRetrieveWindowContent="true"`. [1]

When a matching marker is observed, the utility saves the current `STREAM_MUSIC` integer level and sets that stream to zero. A small **non-interactive accessibility overlay** announces the mute state without stealing D-pad focus. When matching evidence has been absent for the configured delay, the utility restores the saved level. Android mixes the media stream across apps, so this is intentionally a **device-wide media mute**, not app-isolated volume control. [2]

| Behavior | Implementation choice |
|---|---|
| Detection input | Foreground accessibility text, node text, descriptions, and hint text only. |
| Default markers | `ad`, `advertisement`, `sponsored`, `ad 1 of`, and `skip ad`. |
| Scope | All foreground apps by default; optionally restrict to one package name per line. |
| Mute behavior | Saves a non-zero `STREAM_MUSIC` level, then uses `AudioManager.setStreamVolume(..., 0, 0)`. |
| Restore behavior | Restores only a level this utility changed. If media volume is non-zero during restore, it leaves that newer choice unchanged. |
| Anti-flicker behavior | Waits **1.8 seconds** after the last matching label before restoring; configurable from 0–15 seconds. |
| Status UI | A non-focusable `TYPE_ACCESSIBILITY_OVERLAY` is visible only while this utility has muted the media stream. [3] |

## Personal sideloading

This build has been compiled as a **debug APK**. Connect to the TV with Android Debug Bridge and install the included APK:

```bash
adb connect <TV-IP-address>:5555
adb install -r app-debug.apk
```

Alternatively, copy `app-debug.apk` to a file manager on the TV and use its installation flow. You may need to enable developer options, an available debugging method, or installation from unknown sources in the device’s own settings.

After installation, open **AdMute TV** from the TV launcher. Select **Open Accessibility settings**, then explicitly enable the **AdMute TV** service. Android requires this user-controlled enablement and binds an accessibility service through the protected `BIND_ACCESSIBILITY_SERVICE` permission. [1]

## Setup and tuning

Start with the all-app default only for a short test. If the service behaves too broadly, clear **Watch all foreground apps** and provide the exact Android package name for each app you want to observe, one per line. The app’s own package is always ignored.

Keep markers precise and test one streaming app at a time. For example, use labels actually visible on that service in your language rather than relying on a generic `ad` marker. A marker on an unrelated accessibility element can cause a false mute. If the ad audio starts before an accessible label arrives, the mute will also start late; no screen or audio analysis is used to compensate for that.

The **Restore volume now** action is deliberately conservative. It restores a saved volume only when this utility owns the mute session. If you changed media volume during a mute period, the app leaves the non-zero current level in place instead of overwriting your choice.

## Limitations and expected results

| Situation | Expected behavior |
|---|---|
| App exposes an ad label such as “Advertisement” | AdMute TV should mute `STREAM_MUSIC`, show the compact MUTE overlay, and restore after the configured delay. |
| App exposes no accessibility tree, no text, or localized text that does not match | No detection; add an appropriate marker only if the app exposes one. |
| Other media apps are playing concurrently | They are muted too because Android’s media stream is shared. [2] |
| TV reports fixed volume | AdMute TV detects this and will not claim to have muted audio; fixed-volume devices do not allow the relevant stream adjustment. [2] |
| You set volume above zero while AdMute TV is muted | The utility will not overwrite that newer level during automatic restore. |
| Service is disabled or interrupted while it owns the mute | It attempts to restore the saved level before it stops. |
| App changes its UI or ad labels | Update your marker list or accept that automatic detection may no longer work. |

## Building from source

The project uses Kotlin, Android Gradle Plugin 8.6.1, JDK 17 compatibility, `compileSdk 35`, and `minSdk 26`. With a local Android SDK that includes platform 35 and build tools, run:

```bash
./gradlew assembleDebug
```

The checked build used Gradle 8.7 and produced:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Source layout

| Path | Responsibility |
|---|---|
| `AdMuteAccessibilityService.kt` | Event loop, local restore command, debounce logic, lifecycle safety. |
| `AdDetector.kt` | Bounded breadth-first text inspection and marker matching. |
| `MediaVolumeController.kt` | Volume snapshot, mute ownership, and conservative restore guard. |
| `StatusOverlay.kt` | The passive status overlay visible during an app-owned mute session. |
| `SettingsRepository.kt` | SharedPreferences-backed settings. |
| `MainActivity.kt` | D-pad-friendly configuration and manual restore control surface. |

## References

[1]: https://developer.android.com/guide/topics/ui/accessibility/service "Android Developers — Create an accessibility service"
[2]: https://developer.android.com/media/platform/output "Android Developers — Handling changes in audio output"
[3]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "Android Developers — AccessibilityService API reference"
