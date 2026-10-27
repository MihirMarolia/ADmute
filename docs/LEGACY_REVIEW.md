# Earlier implementation review

Inspected October 12, 2026: `MihirMarolia/ADmute`, commit `a57d802e5954eb2b8ab1b7516babbad99e28324f`. Original Kotlin source is inside `ad-mute-tv-source.zip` under `ad-mute-tv/`. The repository's original APK and ZIP are retained for comparison.

These are source findings, not conclusions from a reproduction on the user's TV.

| Finding | Evidence in original archive | New implementation |
| --- | --- | --- |
| All apps participate by default | `SettingsRepository.kt`: `watchAllApps` defaults to true | Closed allowlist: user-enabled YouTube TV, Tubi, Plex |
| A movie title or description can qualify as an ad | `AdDetector.kt`: single-word markers match any token; phrase markers match substrings, without player context | Whole-label specific patterns, player/control context, title/recommendation exclusions |
| Broad defaults encourage false triggers | `SettingsRepository.kt`: `ad`, `advertisement`, `sponsored`, `ad 1 of`, `skip ad` | Bare `ad`, `advertisement`, and `sponsored` do not trigger |
| Stale events and foreground trees are combined | `AdMuteAccessibilityService.kt`: scope from event package; detector receives both event text and active root | Scope and evidence from the same fresh active root; event text never qualifies |
| One positive observation immediately mutes | `AdMuteAccessibilityService.kt`: positive branch calls `volumeController.mute()` directly | Repeated evidence separated by 300 ms |
| Detection and restoration depend on future events | Service has no periodic scan; restore is scheduled from a subsequent negative/irrelevant event | Fresh-tree polling with bounded clear/unknown/max-duration transitions |
| Leaving the app uses the configured restore delay | Foreground filtering calls `scheduleRestore(settings.resumeDelayMs)` | Unsupported foreground / absent ownership releases immediately |
| No maximum positive-label duration | Repeated positives cancel restoration without a hard deadline | Three-minute hard deadline and cooldown until stable clear |
| Overlay only announces mute state | `StatusOverlay.kt` / original guide describe a compact MUTE overlay | Optional opaque dark texture independent of audio muting |

## Compatibility boundary

The new app is a separate package, `org.admute`, rather than an in-place upgrade of `com.example.admutetv`. Old signing credentials are not available, so no upgrade compatibility is claimed. Disable the earlier accessibility service before testing the rebuild. A manual volume increase remains the fallback if a service stops unexpectedly.

Synthetic tests and the local successful build are documented in [VERIFICATION.md](VERIFICATION.md). Current app-version/device compatibility remains pending. Use [DEVICE_TESTING.md](DEVICE_TESTING.md) to record actual labels and outcomes before expanding detection rules.
