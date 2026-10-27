package org.admute.core;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * Enhanced audio control with OEM-specific workarounds and multiple fallback strategies.
 * Attempts various methods to mute/unmute audio when standard methods fail.
 */
public final class EnhancedAudioController {
    private static final String TAG = "ADMuteAudio";
    private final AudioManager audioManager;
    private final Context context;
    private AudioFocusRequest focusRequest;
    private boolean hasAudioFocus = false;

    public EnhancedAudioController(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Attempts to mute audio using multiple strategies.
     * Returns true if at least one method succeeded.
     */
    public boolean mute() {
        if (audioManager == null) return false;

        boolean success = false;

        // Strategy 1: Native mute (preferred)
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE, 0);
            if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.d(TAG, "Native mute succeeded");
                success = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Native mute failed: " + e.getMessage());
        }

        // Strategy 2: Set volume to zero (fallback)
        if (!success) {
            try {
                if (!audioManager.isVolumeFixed()) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                    if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        Log.d(TAG, "Volume zero fallback succeeded");
                        success = true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Volume zero fallback failed: " + e.getMessage());
            }
        }

        // Strategy 3: Request audio focus with ducking (some OEMs respect this)
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                requestAudioFocusWithDucking();
                Log.d(TAG, "Audio focus ducking requested");
                success = true;
            } catch (Exception e) {
                Log.w(TAG, "Audio focus request failed: " + e.getMessage());
            }
        }

        // Strategy 4: Direct audio session mute (API 26+)
        if (!success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Some OEMs respond to this when they ignore stream-level commands
                audioManager.adjustVolume(AudioManager.ADJUST_LOWER, 0);
                Log.d(TAG, "Volume adjustment attempted");
            } catch (Exception e) {
                Log.w(TAG, "Volume adjustment failed: " + e.getMessage());
            }
        }

        return success;
    }

    /**
     * Attempts to unmute audio using multiple strategies.
     * Returns true if at least one method succeeded.
     */
    public boolean unmute() {
        if (audioManager == null) return false;

        boolean success = false;

        // Release audio focus first if we have it
        if (hasAudioFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest);
                hasAudioFocus = false;
                Log.d(TAG, "Audio focus released");
            } catch (Exception e) {
                Log.w(TAG, "Failed to release audio focus: " + e.getMessage());
            }
        }

        // Strategy 1: Native unmute (preferred)
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE, 0);
            if (!audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                Log.d(TAG, "Native unmute succeeded");
                success = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Native unmute failed: " + e.getMessage());
        }

        // Strategy 2: Restore saved volume (if VolumeLease provides it)
        // This is handled by VolumeLease, but we can verify here
        if (!success) {
            try {
                if (!audioManager.isVolumeFixed()) {
                    int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if (currentVol == 0) {
                        // Try to restore to a reasonable default if VolumeLease hasn't
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3, 0);
                        Log.d(TAG, "Volume restore attempted");
                        success = true;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Volume restore failed: " + e.getMessage());
            }
        }

        return success;
    }

    /**
     * Gets current mute state from AudioManager.
     */
    public boolean isMuted() {
        if (audioManager == null) return false;
        try {
            return audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            Log.w(TAG, "Failed to check mute state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets current volume level.
     */
    public int getVolume() {
        if (audioManager == null) return 0;
        try {
            return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get volume: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Checks if device has fixed volume (e.g., soundbar).
     */
    public boolean isVolumeFixed() {
        if (audioManager == null) return false;
        try {
            return audioManager.isVolumeFixed();
        } catch (Exception e) {
            Log.w(TAG, "Failed to check fixed volume: " + e.getMessage());
            return false;
        }
    }

    /**
     * Requests audio focus with ducking as a hint to lower volume.
     * Some OEMs respect this more than direct mute commands.
     */
    private void requestAudioFocusWithDucking() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build();

        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    hasAudioFocus = false;
                }
            })
            .build();

        int result = audioManager.requestAudioFocus(focusRequest);
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    /**
     * Gets diagnostic information about audio routing.
     */
    public String getDiagnostics() {
        if (audioManager == null) return "AudioManager unavailable";

        StringBuilder sb = new StringBuilder();
        sb.append("Volume fixed: ").append(isVolumeFixed()).append("\n");
        sb.append("Current volume: ").append(getVolume()).append("/").append(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).append("\n");
        sb.append("Muted: ").append(isMuted()).append("\n");
        sb.append("Has audio focus: ").append(hasAudioFocus).append("\n");

        // Try to detect if using HDMI ARC
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.media.AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (android.media.AudioDeviceInfo device : devices) {
                    if (device.getType() == android.media.AudioDeviceInfo.TYPE_HDMI_ARC ||
                        device.getType() == android.media.AudioDeviceInfo.TYPE_HDMI_EARC) {
                        sb.append("Audio route: HDMI ARC/eARC detected\n");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to detect audio route: " + e.getMessage());
        }

        return sb.toString();
    }
}
