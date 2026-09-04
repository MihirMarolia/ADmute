# AdMute TV

**AdMute TV** is a personal, sideloaded Android TV application that runs as an Android accessibility service. Once the service is enabled it keeps working in the background across every app: it inspects the accessibility text of all foreground application windows, optionally watches ad-server DNS lookups, mutes the shared media stream while an ad break is detected, and restores the previous media-volume level afterwards. Its own screen does not need to be open.

> This is a conservative assistive utility, **not a universal ad-recognition plug-in**. It does not record the display, capture audio, dismiss advertisements, or automate another app. It reacts to accessible text and, optionally, to hostnames looked up while media plays.

## How detection works

| Source | What it uses | Default |
|---|---|---|
| Screen text | Text, content descriptions, hint text, and resource ids of **every** application window the platform exposes, including views the app marked unimportant for accessibility. | On |
| Ad-server DNS | An opt-in local `VpnService` routes only a private DNS address through a tunnel, reads each question name, forwards the lookup unchanged, and treats known ad hostnames as evidence of an ad break. Nothing is blocked. | Off |
| Playback gate | `AudioManager` is asked whether media is actually playing, so labels such as "Sponsored" on a launcher row do not mute a silent screen. | On |

Full-screen video players emit almost no accessibility events while they play, so window content is **polled** every 0.7 s (configurable, 0.3–5 s) rather than relying on events alone. Events, when they do arrive, just trigger an earlier scan.

## Behaviour

| Behavior | Implementation choice |
|---|---|
| Default text markers | `ad`, `ads`, `advertisement`, `sponsored`, `ad 1 of`, `ad 2 of`, `skip ad`, `skip ads`, `your video will resume`, `video will resume after`, `visit advertiser`, `ad will end in`. |
| Default id markers | `ad_countdown`, `ad_badge`, `ad_progress`, `skip_ad`, `ad_overlay`, `ad_text`. |
| Scope | All foreground apps by default; optionally restrict to one package name per line. |
| Mute behavior | Saves a non-zero `STREAM_MUSIC` level, then uses `AudioManager.setStreamVolume(..., 0, 0)`. |
| Restore behavior | Restores only a level this utility changed. If media volume is non-zero during restore, it leaves that newer choice unchanged. |
| Anti-flicker behavior | Waits 1.8 s after the last matching evidence before restoring; configurable from 0–15 s. |
| Status UI | A non-focusable `TYPE_ACCESSIBILITY_OVERLAY` is visible only while this utility has muted the media stream. |
| Activity log | The last 200 detections, mutes, restores, and (in verbose mode) the text each app exposed, readable from the app's own screen. |

Android mixes the media stream across apps, so this is intentionally a **device-wide media mute**, not app-isolated volume control.

## Personal sideloading

```bash
adb connect <TV-IP-address>:5555
adb install -r app-debug.apk
```

Open **AdMute TV** from the TV launcher, select **Open Accessibility settings**, then enable the **AdMute TV** service. Android requires this user-controlled enablement and binds the service through the protected `BIND_ACCESSIBILITY_SERVICE` permission.

## Tuning against your own apps

1. Enable **Verbose diagnostics** and leave the accessibility service on.
2. Play a video with an ad in the streaming app you care about.
3. Return to AdMute TV, select **Refresh activity log**, and read what that app actually exposed.
4. Add the exact labels you see (in your own language) to **Ad labels to match**, or add resource-id fragments such as `ad_countdown`.

If the log shows scans with `0 nodes`, that app exposes no accessibility tree at all — screen-text detection cannot work there, and the DNS option is the remaining signal.

## Optional ad-domain (network) detection

Enable **Also detect ad breaks from ad-server DNS lookups** and accept the Android VPN consent dialog. Android shows a VPN key icon while it runs. Only the private DNS address `10.111.222.3` is routed into the tunnel; media traffic never passes through this app. A lookup for a hostname in `AdHosts` marks an ad break as active for 8 s, refreshed by further lookups.

It does not help when the device or app uses DNS-over-HTTPS or a hardcoded resolver, and it cannot be used at the same time as another VPN app.

## Limitations

| Situation | Expected behavior |
|---|---|
| App exposes an ad label such as "Advertisement" | Mutes `STREAM_MUSIC`, shows the compact MUTE overlay, restores after the configured delay. |
| App exposes no accessibility tree and DNS detection is off | No detection. |
| Other media apps are playing concurrently | They are muted too because Android's media stream is shared. |
| TV reports fixed volume (some HDMI/eARC audio setups) | AdMute TV reports this in the activity log and will not claim to have muted audio. |
| You set volume above zero while AdMute TV is muted | The utility will not overwrite that newer level during automatic restore. |
| Service is disabled or interrupted while it owns the mute | It attempts to restore the saved level before it stops. |

## Building from source

Kotlin, Android Gradle Plugin 8.6.1, JDK 17, `compileSdk 35`, `minSdk 26`.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # DNS parsing and ad-host matching tests
./gradlew lintDebug
```

## Source layout

| Path | Responsibility |
|---|---|
| `AdMuteAccessibilityService.kt` | Background scan loop over all window roots, signal combination, debounce, lifecycle safety. |
| `AdDetector.kt` | Bounded breadth-first inspection of text, descriptions, hints, and view ids. |
| `AdDnsVpnService.kt` | Optional DNS-only tunnel that reads and forwards lookups. |
| `AdHosts.kt` | Ad-delivery hostname suffixes. |
| `AdSignals.kt` | Shared state between the DNS detector and the accessibility service. |
| `DiagnosticsLog.kt` | In-memory activity log shown in the app. |
| `MediaVolumeController.kt` | Volume snapshot, mute ownership, and conservative restore guard. |
| `StatusOverlay.kt` | Passive status overlay during an app-owned mute session. |
| `SettingsRepository.kt` | SharedPreferences-backed settings. |
| `MainActivity.kt` | D-pad-friendly configuration, diagnostics, and manual restore control. |

## References

1. [Create an accessibility service](https://developer.android.com/guide/topics/ui/accessibility/service)
2. [Handling changes in audio output](https://developer.android.com/media/platform/output)
3. [AccessibilityService API reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
4. [VpnService API reference](https://developer.android.com/reference/android/net/VpnService)
