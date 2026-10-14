# Verification · version 0.1.0

Test build prepared October 11, 2026 (America/New_York).

| Check | Result |
| --- | --- |
| Android APK assembly | PASS |
| JVM automated behavior tests | 21 passed, 0 failures/errors |
| Android lint | 0 errors, 3 warnings |
| APK signature | Debug signature verified with Android apksigner |
| TV launcher / API / permissions | Leanback launcher; min API 26; target API 35; MODIFY_AUDIO_SETTINGS only |
| Network permission | Absent |
| Attached TV | None; ADB device list empty |
| Live YouTube / Tubi / Plex tests | NOT RUN |
| Remote recovery / dark cover over secure video | Device tests pending |
| TV speaker / soundbar volume control | Device tests pending |

Lint advisory IDs: UnusedAttribute, AndroidGradlePluginVersion, DataExtractionRules. API-level metadata remains intentional for newer devices, tool versions are pinned, and legacy backup is disabled at the application level. Newer-device extraction rules exclude all preferences. No lint baseline or global disabling of checks is used.

Automated fixtures are synthetic. They validate label exclusions, repeated evidence, short inter-ad gaps, app ownership, bounded unknown/positive evidence, exact volume restoration, user overrides, already-muted/fixed-volume behavior, failed commands, and restart recovery. They do not prove streaming-app compatibility or sensory protection.

Build tools: JDK 17, Gradle 8.13 (checksum-pinned wrapper), Android Gradle Plugin 8.10.1, Android SDK 35 / build-tools 35.0.0. Gradle completed testDebugUnitTest, lintDebug, assembleDebug successfully after final code changes. The SDK/toolchain lives in a temporary local directory; use the README to configure a persistent development installation.

APK SHA-256: `dc06e17f6dcea74c3c9a0e216c2a2be666b2e78d09d45fa1ecd8c9a29233d6a7`

Device acceptance and signed production release remain outstanding. Follow [DEVICE_TESTING.md](DEVICE_TESTING.md) before relying on the app. This is a debug test artifact, not a published product.

## Diagnostic update · 0.1.1, October 12, 2026

25 synthetic tests passed; APK assembly and Android lint passed with zero errors. Debug APK signature verified. **The real TCL test failed:** the fresh YouTube tree has zero meaningful labels, and system ARC callbacks replaced the app's zero-volume request with nonzero levels. A home-screen screenshot probe succeeded; readable ad playback and OCR remain unverified. Comfort protection was switched off and confirmed Paused. No end-to-end compatibility or visual-output pass is claimed. See [the measured TCL investigation](TCL_TEST_2026-10-12.md).

Diagnostic APK SHA-256: `da89c2a9bc598b9d661a7995d9283b2b8bf26d62cd8b951af51d0c19f8cbcab6`

## Review update · 0.1.6, October 13, 2026

Reviewed the pending native-mute and optional OCR changes, corrected the issues below, and built version code 7.

| Check | Result |
| --- | --- |
| Automated behavior tests | 47 passed, zero failures/errors |
| Debug / release Android lint | Zero errors and zero warnings in both variants |
| Debug / release assembly | PASS; release APK is unsigned |
| Debug APK signature | Verified with Android apksigner |
| Packaged permissions | MODIFY_AUDIO_SETTINGS only; no network permission |
| Source whitespace check | git diff --check passed |
| TCL installation | In-place update succeeded; Android reports 0.1.6 / code 7 |
| Accessibility after install | Disabled; owner agreed to enable for testing |
| New live ad-control / full-break acceptance | Not yet verified |

Corrections include expired and invalidated scan rejection, package/label/bounds matching for accessible clicks, bounded click traversal, filtering excluded title/recommendation controls, YouTube-only OCR targets, per-source duplicate-action guards, recognizer disposal on its worker, selected-app diagnostics, retaining failed audio recovery records, surfacing recovery failures, and retaining attached covers for cleanup retries. Newer accessibility metadata uses API-qualified resources; legacy backup is explicitly disabled. Service disclosures, setup copy, and architecture now describe optional OCR accurately. CI also builds and lints the release variant.

Final debug APK SHA-256: `d6fdeba606e370d6141098e382e6077eb7d74bd39f7721fdaff6f0ced6871c94`.

Remaining acceptance work: live YouTube Dismiss/Skip, complete automatically triggered ad mute/cover and release, ARC native-unmute baseline behavior, repeated pods and remote recovery, readable labels/full-cover restoration on Android 11, and Tubi/Plex device compatibility. Production signing/publication remains separate. The reviewed implementation reduces specific failure paths; it does not establish universally correct recognition or close the measured TCL compatibility gates.

## Live follow-up · 0.1.7, October 13, 2026

The owner enabled ADMute, but the TCL settings screen showed a blue Enable switch and an Off service row. Android listed the service as enabled without binding it. Logs identified the exact cause: TCL rejected its bind request because `APP_AUTO_START` was `ignore`. Allowed this operation only for `org.admute` and refreshed the existing ADMute-only enabled-service registration. The service then bound successfully with capabilities 169, `running=true`, `paused=false`, and no crashed service. No other app's startup policy was changed.

In YouTube, screenshot-based scans reached the recognizer, but Google Play Services reported no available `vision.ocr`/`vision.dynamite.ocr` module. The recognizer remained non-operational. The service stayed alive; there was no confirmed control action. Accessibility still exposed 11 nodes, nine empty labels, zero meaningful labels, and UNKNOWN evidence. A separate screenshot-brightness probe returned error code 3 when requested alongside scans; that is not an ad-pixel acceptance pass.

Version 0.1.7 (code 8) makes OCR failures persist in the displayed status, backs off missing-model retries to one minute and other scan failures to ten seconds, and clears the issue after a successful scan or a relevant settings change. Re-ran all 47 tests, debug/release lint, and both assemblies successfully. Both lint reports say `No issues found.` Debug signature verified; the in-place TV update succeeded. After the update, the service restarted without another registration refresh, with `APP_AUTO_START: allow`, global accessibility enabled, and the missing-model reason visible in diagnostics/preferences. No saved mute/volume ownership record was present in the final inspected preferences.

Current APK SHA-256: `da71df08b04d71429c5d530cdbadf734cf656897a3d41a43ad78295ce71abe2d`.

**Current outcome:** the misleading enabled/off state is resolved. Live automatic ad handling is still blocked by missing accessibility labels and unavailable OCR. These prerequisites require a compatible, device-verified recognizer before any full-break pass can be claimed. The previous bundled recognizer's native SIGILL failure remains a reason not to silently switch back to it. Version 0.1.7 is a debug test build, not a certified release.
