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
