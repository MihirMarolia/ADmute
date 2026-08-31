# Android TV ad-muting utility: verified platform constraints

## Confirmed capabilities

Android accessibility services may receive configured accessibility events and, when declared with `canRetrieveWindowContent="true"`, inspect exposed accessibility-node text and content descriptions in the foreground window. This is the only cross-app signal used by this project; it is not screen or audio recognition.

Android’s `AudioManager` exposes the media (`STREAM_MUSIC`) volume level. An implementation can store its current integer level before muting, set that stream to zero, and restore the stored level only if the service remains the owner of the mute session.

Accessibility overlays are supported by `AccessibilityService` APIs on sufficiently recent Android versions. For broader compatibility, this project will use an optional application overlay guarded by `SYSTEM_ALERT_WINDOW`, with an alternative notification/status mechanism when the overlay cannot be displayed.

## Material limitations

The media stream is shared system-wide: changing `STREAM_MUSIC` affects every app using that stream, not only the foreground streaming application. Some television devices run at fixed volume, in which case programmatic stream-volume adjustment is unavailable. Apps may expose no useful accessibility tree or may use changing/localized ad labels, so universal automatic ad detection cannot be guaranteed.

The implementation will therefore use a conservative text-based heuristic. It will mute only when an accessible foreground window contains configured positive markers such as `ad`, `advertisement`, `sponsored`, or `ad 1 of`, optionally scoped to enabled package names. It will resume after the positive evidence has been absent for a short configurable debounce interval. The on-screen overlay will always provide a manual restore/disable control.

## Sources

1. Android Developers, “Create an accessibility service” — https://developer.android.com/guide/topics/ui/accessibility/service
2. Android Developers, “Handling changes in audio output” — https://developer.android.com/media/platform/output
3. Android Developers, `AccessibilityService` API reference — https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
4. Android Developers, `AudioManager` API reference — https://developer.android.com/reference/android/media/AudioManager

Retrieved 2026-08-27.

> Important: this is a personally sideloaded assistive utility, not a system plug-in and not a reliable universal ad detector. It does not record the screen, capture audio, bypass advertisements, automate ad interaction, or use network traffic inspection.

