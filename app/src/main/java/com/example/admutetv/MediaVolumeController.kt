package com.example.admutetv

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Owns one mute session at a time. It restores volume only when this instance
 * previously silenced the media stream.
 *
 * Two different mechanisms are used together, because on a television they do
 * not reach the same place:
 *
 *  * [AudioManager.adjustStreamVolume] with `ADJUST_MUTE` is the call the
 *    platform's own volume UI makes. On sets that pass volume through to an
 *    HDMI-CEC audio system (a soundbar or receiver on ARC/eARC), this is the
 *    call the vendor bridges into a CEC command, so it is the one that can
 *    actually silence external speakers.
 *  * [AudioManager.setStreamVolume] to zero moves this device's own
 *    `STREAM_MUSIC` index, which is what silences the built-in speakers.
 *
 * Neither call reports whether an external audio system obeyed, so [Result.Muted]
 * also carries [Result.Muted.externalAudioRoute] and callers surface that
 * instead of claiming the ad was silenced.
 */
internal class MediaVolumeController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var savedMusicVolume: Int? = null
    private var changedVolume = false

    val isMutedByUs: Boolean
        get() = changedVolume

    fun mute(): Result {
        if (audioManager.isVolumeFixed) return Result.FixedVolumeDevice
        if (changedVolume) return Result.AlreadyMutedByUs

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current <= 0 || isStreamMuted()) return Result.AlreadyMutedExternally

        savedMusicVolume = current
        adjustMute(mute = true)
        // Flag 0 avoids displaying a system volume panel over playback.
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
        changedVolume = true
        return Result.Muted(current, hasExternalAudioRoute())
    }

    fun restore(): Result {
        val volumeToRestore = savedMusicVolume
        if (!changedVolume || volumeToRestore == null) {
            clearOwnership()
            return Result.NothingToRestore
        }

        // Undo the mute flag first: on a CEC audio system this is the half that
        // reaches the external amplifier.
        adjustMute(mute = false)

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (current > 0) {
            clearOwnership()
            // Unmuting can restore the pre-mute index by itself. Anything else
            // is a level a person chose during the mute, which is never
            // overwritten.
            return if (current == volumeToRestore) {
                Result.Restored(current)
            } else {
                Result.UserOrSystemChangedVolume
            }
        }

        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0) }
        clearOwnership()
        return Result.Restored(volumeToRestore)
    }

    fun abandonWithoutRestore() {
        clearOwnership()
    }

    private fun adjustMute(mute: Boolean) {
        val direction = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        runCatching { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
    }

    private fun isStreamMuted(): Boolean = runCatching {
        audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    }.getOrDefault(false)

    /**
     * Reports whether media audio can be leaving this device over HDMI, in which
     * case an external amplifier owns the level that is actually audible and a
     * stream-volume change here is not guaranteed to be heard.
     */
    private fun hasExternalAudioRoute(): Boolean = runCatching {
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI -> true
                else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_HDMI_EARC
            }
        }
    }.getOrDefault(false)

    private fun clearOwnership() {
        savedMusicVolume = null
        changedVolume = false
    }

    sealed interface Result {
        data class Muted(val previousVolume: Int, val externalAudioRoute: Boolean) : Result
        data class Restored(val restoredVolume: Int) : Result
        data object AlreadyMutedByUs : Result
        data object AlreadyMutedExternally : Result
        data object FixedVolumeDevice : Result
        data object NothingToRestore : Result
        data object UserOrSystemChangedVolume : Result
    }
}
