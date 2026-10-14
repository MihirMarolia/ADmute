# Architecture

```text
TV setup UI → local preferences
                       ↓
Accessibility service → fresh selected-app tree → bounded label snapshot
                       → per-app detector → monotonic-time AdSession
                       → VolumeLease / non-focusable ScreenCover
```

## Trust boundaries

The foreground root's package is checked before traversing. Unsupported packages immediately clear ownership. Accessibility events only wake a poll; their text and source are never ad evidence. Null roots release immediately because foreground ownership cannot be established. Partial supported-app trees return UNKNOWN unless an explicit ad label is present. Tree walks stop at 1,000 nodes or depth 40 and recycle node handles.

Ad labels must be short, whole-label matches and must come from a recognized player container/view ID, a SeekBar, or button class. Title, description, thumbnail, search, browse, poster, and recommendation containers are excluded. This deliberately misses players with generic IDs and no identifiable player context. The remaining exact-button-label ambiguity is a device-test risk, not a solved universal classifier. Resource ID semantics may change across app releases; do not treat synthetic matches as certification.

## State transitions

| State | Input | Result |
| --- | --- | --- |
| IDLE | AD | CANDIDATE, no mute/cover yet |
| CANDIDATE | AD after ≥300 ms | PROTECTED |
| CANDIDATE | CLEAR or UNKNOWN | IDLE |
| PROTECTED | AD | Continue; hard deadline unchanged |
| PROTECTED | CLEAR for ≥1.5 s | IDLE and release |
| PROTECTED | UNKNOWN for ≥8 s since AD | COOLDOWN and release |
| PROTECTED | ≥180 s total | COOLDOWN and release |
| COOLDOWN | CLEAR for ≥1.5 s | IDLE |
| Any | Unsupported app, disabled preference, changed owner | Release; new owner must confirm again |

An error pauses evaluation for one minute. Double-Back uses monotonic in-process timing and pauses one minute without consuming remote input. An interrupted service also releases and pauses. Polling runs on the service's main looper; bounds and package filtering limit work. Optional YouTube control OCR runs on one worker as described below.

## Volume ownership

Before requesting native mute, synchronously save mute ownership unless the stream was already muted. Verify the reported state; if it remains unmuted, attempt a zero-volume fallback after synchronously saving its baseline. Never snapshot a second baseline while the same lease is active or recovery is unresolved. Native mute can work on routes that reject volume changes. On release/reconnect, unmute only an owned mute, then restore a saved volume only while current volume remains zero and Android permits volume control. A nonzero user change wins. Exceptions, silently ignored restores, and a newly fixed route retain the recovery record. The setup status reports pending audio recovery. A system volume-index change during native unmute cannot reliably be distinguished from a user adjustment; the TCL report records this unresolved device behavior.

There is no cross-process watchdog. Process death removes windows but may leave media volume zero; persisted recovery runs when the accessibility service reconnects. Manual volume adjustment is always the fallback. Muting is at Android's media stream, not an app-specific audio session; foreground ownership is therefore essential. The app cannot directly address a TV's IR speaker volume or soundbar CEC controls.

## Visual cover

`TYPE_ACCESSIBILITY_OVERLAY`, opaque pixel format, full window bounds, not focusable/not touchable. A procedural dark weave has no text, animation, or network asset. Underlying accessibility labels remain inspectable according to Android's overlay model. Whether a particular TV's secure video surface/system bars are fully covered must be verified on that device. Home/navigation and skip controls continue receiving remote input.

## Optional YouTube ad controls

The setting defaults off. On Android 11 or newer, a selected foreground YouTube app can be scanned at most once every 1.5 seconds, without an active cover. Google Play Services text recognition runs on one worker; pixels are recycled after use, and screen text is never persisted. Only an exact lower-right `Skip ad`, or `Skip`/`Dismiss` paired with visible in-bounds `Sponsored`, qualifies. Tubi/Plex OCR controls are disabled until their layouts are inspected. OCR control selection does not provide ad evidence to the comfort state machine.

Every pending scan has a generation token and a 2.5-second maximum age. Navigation keys, window changes, settings changes, pause, and lifecycle exits invalidate old results. Foreground ownership, settings, and cover visibility are rechecked before dispatch. Attempts are latched until two clear reads from the same evidence source, or an ownership/settings reset; inaccessible tree reads cannot rearm an OCR action. Accessibility clicks match package, exact label, enabled/visible state, and exact target bounds, with the same traversal limits as snapshots. Recognition is a conservative heuristic, not a guarantee that arbitrary video content cannot resemble these controls.

Screenshot capability is declared only in API 30+ resources, and accessibility-tool metadata only in API 31+ resources. Debug builds additionally offer explicit screenshot-brightness and ten-second comfort probes. Recognizer creation, use, and disposal all run on the same worker.

Missing OCR models retain a visible status reason and retry after one minute; other scan failures retry after ten seconds. A successful scan clears the reason. The TCL test found the legacy Google Play Services OCR module unavailable. Enabling accessibility alone does not establish OCR availability. TCL's separate background-start operation also required an owner-authorized device setting; the app cannot grant that setting to itself.
