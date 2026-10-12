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

An error pauses evaluation for one minute. Double-Back uses monotonic in-process timing and pauses one minute without consuming remote input. An interrupted service also releases and pauses. Polling runs on the service's main looper; bounds and package filtering limit work. No background OCR or image generation runs on the TV.

## Volume ownership

Before muting, save the baseline synchronously. Never snapshot a second baseline while the same lease is active. Zero baseline or fixed volume acquires no restoration claim. On release/reconnect, only restore when the saved baseline exists, current volume is zero, and Android permits volume control. A nonzero user change wins. Failed writes/recovery preserve a record for a later retry where possible.

There is no cross-process watchdog. Process death removes windows but may leave media volume zero; persisted recovery runs when the accessibility service reconnects. Manual volume adjustment is always the fallback. Muting is at Android's media stream, not an app-specific audio session; foreground ownership is therefore essential. The app cannot directly address a TV's IR speaker volume or soundbar CEC controls.

## Visual cover

`TYPE_ACCESSIBILITY_OVERLAY`, opaque pixel format, full window bounds, not focusable/not touchable. A procedural dark weave has no text, animation, or network asset. Underlying accessibility labels remain inspectable according to Android's overlay model. Whether a particular TV's secure video surface/system bars are fully covered must be verified on that device. Home/navigation and skip controls continue receiving remote input; no ad is clicked or skipped automatically.
