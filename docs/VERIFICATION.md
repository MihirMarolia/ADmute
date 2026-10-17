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

## Live retest · October 16, 2026 (America/Toronto)

Rebuilt the current source (0.1.7 / code 8): 47 unit tests passed; debug lint reported no issues; debug APK assembly passed. SHA-256 remains `da71df08b04d71429c5d530cdbadf734cf656897a3d41a43ad78295ce71abe2d`. Installed in place successfully on the TCL TV. Accessibility registration persisted and the replacement process bound successfully: running=true, paused=false, automatic controls enabled. No newer app version exists in this checkout.

Opened a recommended YouTube video. A screenshot visibly confirmed a real sponsored pre-roll with Sponsored, Send to phone, and Skip in 13. At this time ADMute remained IDLE, with 11 nodes, nine empty labels, zero meaningful labels, UNKNOWN evidence, and SCAN_FAILED / OCR model missing. **Automatic ad handling failed this live test.** The black video region in the screenshot is not proof of an ADMute cover; sponsored controls were still visible.

Armed the separate ten-second comfort diagnostic, then checked after expiry. The service remained alive and no saved mute/volume ownership record remained in preferences. This does not establish visible cover or audible output success; no independent observation of TV audio was available, and the baseline was already muted in the prior local diagnostics. Returned the TV to Home. Accessibility remains enabled. Full-break acceptance and readiness remain blocked by inaccessible labels and unavailable OCR; this build is not ready to rely on for automatic protection on this TV.

### Immediate second live test · 23:15, October 16

Observed another real sponsored ad (Coca-Cola), resumed playback, and sampled the running service: still IDLE, zero meaningful labels, UNKNOWN, OCR model missing. Automatic detection failed again. Separately forced the ten-second comfort diagnostic: screenshot confirmed the full-screen dark weave, preferences recorded savedMute=true, and Android reported STREAM_MUSIC muted. After timeout, screenshot confirmed the weave was removed and savedMute ownership was cleared. Audible HDMI output and exact baseline restoration were not independently verified. This confirms the overlay output and timed cleanup, not automatic recognition. Local screenshots remain under .device-state only.

## Visual detector implementation and live tests · 0.1.8, October 16, 2026

Implemented a plain-Java pixel matcher for the actual English 1920×1080 YouTube player footer. It recognizes Sponsored + Send to phone together, including the observed odd-pixel horizontal button displacement. It requires distinct fresh captures, rejects stale/navigation-invalidated results, and reports unsupported dimensions. No native OCR engine or downloaded model is used for this mode. It is a separate explicit setting, off by default; enabled on the test TV for the owner-requested tests. The owner explicitly selected automatic release with tiny label areas left visible. Two label windows occupy approximately 0.45% of the frame. Remaining pixels stay covered continuously; the app does not lift the cover to inspect ads. Three clear successful captures spanning 1.5 seconds release protection. Capture failures retain current visual protection and back off five seconds. OCR button controls are suspended in visual mode; no Skip/Dismiss pass is claimed.

Measured live results before the final reboot:

- First detector run missed a live ad whose phone button was shifted by an odd number of pixels; inspected that frame, fixed both horizontal sampling phases and added a regression test.
- With the corrected build, a paused real ad produced AD_PAIR at capture 261737465, then active=true at 261738686. A device screenshot showed the dark weave across the frame with only Sponsored and Send to phone visible. Preferences recorded owned mute and Android reported STREAM_MUSIC muted. Resumed playback; three NO_MARKERS captures (261761146, 261762341, 261763660) released the cover automatically. This was the visual path, not comfort-test.
- A second live ad on another video automatically activated the same cover and released after three successful clear captures. The phone label had another observed position; the windows remained narrow. Normal playback, player controls, home screens, and a Sponsored home banner without the footer pair stayed uncovered in inspected samples.
- Native audio commands and automatic unmute were observed, but ARC can change the volume index. A later explicit output test with baseline 1 ended unmuted at 2. Exact index restoration and independent audible-output confirmation remain open; this is not a physical audio acceptance pass.

Additional corrections: preserve the media baseline before native mute as well as volume fallback, retain a restoration record across 1.5 seconds of stable original-level observations to tolerate delayed CEC zero callbacks, crop retained worker bitmaps to the 1920×180 footer, and add an ongoing foreground-service notification. TCL silently blocked foreground promotion from the accessibility binding alone. Explicitly starting the already-enabled service from visible setup succeeded: Android reported isForeground=true, foregroundId=101. This did not eliminate OEM eviction: events still recorded tguard-kill under memory pressure, including one at process importance 50. No native/Java OCR crash was observed in these new tests.

The TV repeatedly exhausted graphics/ION allocations during this session; even ADB screencap failed while ADMute was stopped. Closing YouTube restored capture temporarily, but the failure recurred. A full TV reboot was performed to recover before a further physical-output test. These device-wide failures and OEM process eviction remain blockers to a reliability claim. Holding a cover on capture failure does not protect against Android killing the overlay process. Initial ad frames and the two label windows remain exposed, and unrecognized layouts can be missed entirely.

Software verification: 63 unit tests passed; debug and release lint reported zero errors with one pinned-Gradle-version advisory each; debug and release assemblies passed (release unsigned); debug APK signature verified. Final debug APK SHA-256: a3326d0e5142d3f1292308a09f60ff5e94dc2c5f34c0a90bd74f1af84455817c. This source is 0.1.8 / code 9. Existing local wrapper edits were preserved. Private screenshots and raw device material remain outside tracked source; committed fixtures contain only thresholded Sponsored / Send to phone marker pixels, without account information or advertiser content.

## Corrected audio restore and final outcome · 0.1.9 / code 10

The owner watched the post-reboot automatic ad test and reported that sound did not return after the cover cleared. That automatic break therefore **failed end-to-end acceptance**. Investigation found two concrete recovery defects: the ignored zero-volume fallback discarded the baseline even though a native mute request was still pending, and an immediately reported unmute cleared ownership before delayed ARC callbacks had settled. Version 0.1.9 retains the pre-command baseline when fallback is ignored and retains mute ownership until unmuted observations stay stable for 1.5 seconds. Added deterministic regressions for these delayed behaviors; different nonzero levels remain authoritative rather than fighting user/CEC changes.

A controlled ten-second test on 0.1.9 started from Android unmuted level 3, recorded savedVolume=3 plus owned mute during protection, then finished unmuted at nonzero level 2 with both recovery records cleared. The owner explicitly confirmed: **sound returned**. This is a controlled output/restore pass, not a new automatic full-break pass or exact ARC index restoration pass.

Changed the explicitly started foreground service to START_STICKY while protection is enabled. An idle debug restart probe (refuses active cover/audio ownership) killed only ADMute from its own process. Android logged death of pid 561, scheduled restart at 1000ms, started pid 738, and restored its foreground notification without opening setup. This verifies that particular idle recovery path. The probe is debug-only; no system process is targeted.

The final automatic-cycle attempt nevertheless missed a real sponsored ad because TCL had killed the service again before entry. Restored its foreground state through setup. Added only org.admute to Android's device-idle whitelist; this scoped mitigation is not verified to exempt TCL's proprietary tguard killer and is not an acceptance pass. OEM eviction and capture allocation failures remain unresolved blockers to long-term reliability.

Post-reboot Android crash logs additionally recorded fatal system_server SecurityExceptions while binding RoleControllerService, followed by DeadSystemException in other apps and unavailable audio/activity services. These logs are separate device evidence, not proof that ADMute caused or repaired the firmware failure. No system package was disabled or removed as part of this task. All diagnostic screenshots remain local; on-device files created for the investigation were removed at cleanup.

Final software checks: 64 unit tests passed; debug/release lint zero errors, one pinned-Gradle advisory each; both variants assemble; debug signature verified. Version 0.1.9 is installed with visual mode and accessibility enabled. Current result: visual detection/automatic cover release demonstrated on the inspected layout, corrected physical sound return confirmed in the controlled test, but **not ready to rely on for disability/sensory protection**. Initial label latency, small uncovered label windows, unrecognized layouts, TV process termination, graphics failures and exact ARC restoration remain limitations. Tubi/Plex, Android 14 window capture and wider device acceptance have not passed.

Final installed 0.1.9 debug APK SHA-256: `d12d5808adb2788d0b9755ab97060d35ca354e000224e30d7094b6b515b95232`. Returned the TV to Home for cleanup; final audio state and ownership inspection are recorded locally. The Android device-idle exemption is the only additional persistent TV policy change in this follow-up; the existing ADMute startup allowance was retained.
