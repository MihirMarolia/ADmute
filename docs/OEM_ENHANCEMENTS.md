# OEM Reliability Enhancements

## Overview

ADMute has been enhanced with multiple layers of protection against OEM-specific restrictions that prevent reliable operation in regular use. These additions address the critical gaps identified during testing on TCL TVs and other Android TV devices.

## New Features

### 1. OEM Diagnostics (`OemDiagnostics.java`)

A comprehensive diagnostic system that detects and reports OEM-specific restrictions:

**Checks performed:**
- Battery optimization status
- Background process limits
- Manufacturer-specific restrictions (TCL, Sony, Samsung, LG)
- Developer options availability
- System overlay permissions
- Foreground service restrictions

**Usage:**
- Open ADMute settings
- Click "OEM diagnostics & fixes"
- Review detected issues and warnings
- Follow guided fixes to enable required settings

**Manufacturer-specific warnings:**
- **TCL devices**: Warns about `APP_AUTO_START` permission and tguard-kill process termination
- **Sony devices**: Alerts to additional background restrictions
- **Samsung devices**: Notes potential accessibility service blocking
- **LG devices**: Warns about aggressive power management

### 2. Process Guard (`ProcessGuard.java`)

Protects against OEM process eviction (e.g., TCL's tguard-kill):

**Features:**
- Periodic heartbeat checks every 30 seconds
- Automatic service restart if killed by OEM
- Uses AlarmManager with wake locks for reliability
- Configurable enable/disable

**How it works:**
1. Service records heartbeat every 350ms during normal operation
2. ProcessGuard checks if heartbeat is within 60-second window
3. If heartbeat is stale, attempts to restart the service
4. Reschedules next check automatically

**Integration:**
- Automatically enabled when accessibility service starts
- Disabled when service stops
- Persists state across restarts

### 3. Enhanced Audio Controller (`EnhancedAudioController.java`)

Multiple fallback strategies for audio control when standard methods fail:

**Mute strategies (attempted in order):**
1. Native stream mute (`ADJUST_MUTE`)
2. Volume zero fallback (`setStreamVolume(..., 0)`)
3. Audio focus with ducking (some OEMs respect this)
4. Direct volume adjustment as hint

**Unmute strategies (attempted in order):**
1. Native stream unmute (`ADJUST_UNMUTE`)
2. Volume restore (coordinates with VolumeLease)
3. Default volume restoration

**Additional features:**
- HDMI ARC/eARC detection
- Audio routing diagnostics
- Fixed volume detection
- Audio focus management

**Usage:**
- Click "Audio diagnostics" in settings to view current audio state
- Automatically attempts multiple strategies when VolumeLease is used

### 4. Additional Permissions

Added to `AndroidManifest.xml`:
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - Required for battery optimization exemption
- `SYSTEM_ALERT_WINDOW` - Required for overlay permissions
- `PACKAGE_USAGE_STATS` - Enhanced app state monitoring

### 5. Settings UI Enhancements

Added to MainActivity:
- "OEM diagnostics & fixes" button
- "Audio diagnostics" button
- Guided setup for manufacturer-specific settings
- One-click navigation to relevant Android settings screens

## Developer Options Required

For maximum reliability, users should enable these Android Developer Options:

1. **Background process limits**
   - Settings → Developer Options → Background process limit
   - Set to "No limit" or "Standard limit" (not "At most 1 process")

2. **Stay awake while charging**
   - Settings → Developer Options → Stay awake
   - Helps prevent device sleep during streaming

3. **Strict mode**
   - Settings → Developer Options → Strict mode
   - Disable for testing (can cause crashes)

4. **Device idle whitelist**
   - Settings → Apps → Special access → Device idle apps
   - Add ADMute to whitelist (especially for TCL devices)

5. **APP_AUTO_START (TCL specific)**
   - Settings → Apps → ADMute → Allow background activity
   - Set to "Allow" (prevents tguard-kill termination)

## OEM-Specific Workarounds

### TCL TVs

**Known issues:**
- tguard-kill terminates background services under memory pressure
- APP_AUTO_START permission blocked by default
- Graphics/ION allocation failures during screen capture

**Workarounds implemented:**
- ProcessGuard with automatic restart
- OEM diagnostics that detect TCL devices
- Guidance to enable APP_AUTO_START
- Device idle whitelist recommendation

**Additional manual steps:**
1. Enable Developer Options
2. Settings → Apps → ADMute → Battery → Unrestricted
3. Settings → Apps → Special access → Device idle apps → Allow ADMute
4. Settings → Apps → ADMute → Allow background activity → Allow

### Sony TVs

**Known issues:**
- Additional background restrictions
- May block accessibility services

**Workarounds:**
- OEM diagnostics detection
- Guidance to manufacturer app settings
- Battery optimization exemption

### Samsung TVs

**Known issues:**
- Accessibility service blocking
- Aggressive power management

**Workarounds:**
- OEM diagnostics detection
- Battery optimization exemption
- Foreground service protection

### LG TVs

**Known issues:**
- Aggressive power management
- Background activity restrictions

**Workarounds:**
- OEM diagnostics detection
- Battery optimization exemption
- ProcessGuard protection

## Installation and Setup

1. Install the updated APK: `app/build/outputs/apk/debug/app-debug.apk`
2. Open ADMute from launcher
3. Click "OEM diagnostics & fixes"
4. Review and apply recommended fixes
5. Enable accessibility service
6. Test with "Audio diagnostics" to verify audio routing
7. Test with "Detection check" to verify accessibility tree access

## Limitations

These enhancements **do not** guarantee:
- Protection against all OEM process eviction mechanisms
- Audio control on all device/soundbar combinations
- Accessibility tree access on all streaming apps
- Screenshot capture on all devices

What they **do** provide:
- Early detection of common OEM restrictions
- Guidance to enable required settings
- Automatic service restart when possible
- Multiple audio control fallback strategies
- Comprehensive diagnostics for troubleshooting

## Testing Recommendations

After installing the enhanced version:

1. Run OEM diagnostics and apply all recommended fixes
2. Enable Developer Options and configure background limits
3. Add ADMute to device idle whitelist
4. Disable battery optimization for ADMute
5. Test full ad break in each supported app
6. Monitor process longevity with `adb shell dumpsys activity services org.admute`
7. Check audio diagnostics before and during ad playback
8. Verify ProcessGuard is active via logcat: `adb logcat | grep ADMuteProcessGuard`

## Version Information

- Added in version 0.2.0 (unreleased)
- Based on production version 0.1.9
- Build with JDK 17, Android SDK 35
- Requires Android 8.0 (API 26) or newer
- Enhanced features require Android 11 (API 30) or newer

## References

- [OEM Background Restrictions](https://developer.android.com/guide/components/activities/background-processes)
- [AlarmManager Wake Locks](https://developer.android.com/training/scheduling/wakelock)
- [Audio Focus Management](https://developer.android.com/guide/topics/media-apps/audio-focus)
- [Battery Optimization Exemptions](https://developer.android.com/training/monitoring-device-state/doze-standby)
