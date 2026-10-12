# Development lifecycle and acceptance criteria

## 1. Scope and requirements

| ID | Requirement | Verification |
| --- | --- | --- |
| R1 | Only user-enabled YouTube TV, Tubi, Plex can trigger protection | Detector allowlist / session tests; device app-switch tests |
| R2 | Recognize explicit player ad indicators, never broad title keywords | Detector positive/negative tests; browsing and movie-launch tests |
| R3 | Require repeated positive evidence and bridge short ad pod gaps | Session timing tests; pre/mid/post-roll tests |
| R4 | Restore exact baseline volume unless user changed it | Volume lease tests; speaker and HDMI device tests |
| R5 | Optional static dark visual cover, independent of mute | APK build/lint; texture preview and actual video tests |
| R6 | Keep remote control usable and provide immediate recovery | Key handling inspection; double-Back, Home and settings tests |
| R7 | Bounded traversal, detection gaps, and ad session duration | Timing tests; forced stale/incomplete-tree tests |
| R8 | Local processing without stored screen text or network access | Manifest/source review; detection-check review |
| R9 | TV launcher and readable remote-operated setup | Build/lint; D-pad setup walkthrough |
| R10 | Recover persisted baseline on reconnect | Volume recovery tests; service reconnect and process-death tests |

The initial user scope is ads only. It does not detect or reduce flashing in regular programming. No guarantees about medical outcomes, complete ad detection, initial flash suppression, or external speaker control are implied.

## 2. Design gate

Choose accessibility metadata rather than image capture: no continuous recordings, OCR cost, or unreliable whole-screen keyword search. Separate detector, state machine, and volume ownership from Android so critical behavior is testable. Use an accessibility overlay that does not take focus or touch input. Poll fresh trees because video countdown labels may not emit consistent events.

Current rules are English hypotheses, not observations from the user's device. Non-English support and app-version certification require actual accessibility metadata. A miss is preferable to obscuring a movie because its title mentions advertising.

## 3. Implementation gate

Initial project includes TV UI, preference controls, disclosure, service, bounded node collector, per-app rule selection, state machine, durable media-volume lease, static procedural cover, recovery behavior, tests, reproducible wrapper, and CI. No source existed in the starting directory, so this is a new implementation rather than an audit of the earlier APK.

On October 12, 2026, the owner supplied `MihirMarolia/ADmute`. Its existing source archive was inspected; [LEGACY_REVIEW.md](LEGACY_REVIEW.md) records the observed detector and service failure paths. The rebuild is added as unpacked source alongside the original deliverables and proposed on a separate branch.

## 4. Verification gate

Automated tests exercise synthetic examples and timing; they establish program behavior, not compatibility with current streaming-app versions. Lint and successful compilation are required before handing off an APK. Device testing must verify the accessibility labels, rendering over video, remote recovery, system volume control, and lifecycle behavior separately on each app/audio route.

Record measured entry latency, exit latency, false positives, missed breaks, baseline/restored volume, app version, TV model, Android version, language, and audio route. Initial product targets: 0 false activations across 20 movie starts and 10 minutes browsing per app; restore baseline on all 10 controlled ad exits; no cover after app exit; double-Back release within one polling interval after its second press. Detection latency depends on when app labels become available; observe it instead of asserting an unmeasured guarantee.

## 5. Release gate

No production claim until all device acceptance cases pass. Version rule changes and rerun negative-title and volume tests. Release signing keys belong to the owner; never commit them. Before any store submission, review applicable current accessibility/disclosure requirements and prepare accurate scope/limitations. Distribution, production signing, certification, and store submission have not been performed.

## 6. Maintenance

After app or TV updates, repeat at least one ad pod plus movie-launch and app-exit cases. If an app starts misfiring, disable its toggle, inspect its current visible player labels on a development device, and add narrowly scoped regression fixtures before changing rules. Never silently add new packages or broad `contains("ad")` rules.
