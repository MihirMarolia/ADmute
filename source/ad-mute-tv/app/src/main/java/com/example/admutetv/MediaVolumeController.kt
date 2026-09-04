package com.example.admutetv

import android.content.Context
import android.media.AudioManager

/**
 * Owns one mute session at a time. It restores volume only when this instance
 * previously reduced a non-zero STREAM_MUSIC level to zero.
 */
internal class MediaVolumeController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var savedMusicVolume: Int? = null
    private var changedVolume = false
    private var forceMuteEnabled = false

    val isMutedByUs: Boolean
        get() = changedVolume
    
    fun setForceMute(enabled: Boolean) {
        forceMuteEnabled = enabled
    }

    fun mute(): Result {
        if (audioManager.isVolumeFixed) return Result.FixedVolumeDevice
        if (changedVolume) return Result.AlreadyMutedByUs

        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        // Enhanced logging
        android.util.Log.d("AdMute", "Mute attempt - Current volume: $current/$maxVolume, Fixed: ${audioManager.isVolumeFixed}, Force: $forceMuteEnabled")
        
        // Skip the "already muted" check if force mute is enabled
        if (!forceMuteEnabled && current <= 0) {
            android.util.Log.d("AdMute", "Volume already muted externally (current: $current)")
            return Result.AlreadyMutedExternally
        }

        savedMusicVolume = current
        android.util.Log.d("AdMute", "Setting volume to 0, saved: $savedMusicVolume")
        
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            0,
            0 // Avoid displaying a system volume panel over playback.
        )
        
        // Verify the mute worked
        val afterMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        android.util.Log.d("AdMute", "After mute attempt - Volume: $afterMute")
        
        changedVolume = true
        return Result.Muted(current)
    }

    fun restore(): Result {
        val volumeToRestore = savedMusicVolume
        if (!changedVolume || volumeToRestore == null) {
            clearOwnership()
            return Result.NothingToRestore
        }

        // If media volume is already non-zero, a person or another controller
        // changed it during the mute session. Never overwrite that later choice.
        if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
            clearOwnership()
            return Result.UserOrSystemChangedVolume
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0)
        clearOwnership()
        return Result.Restored(volumeToRestore)
    }

    fun abandonWithoutRestore() {
        clearOwnership()
    }

    private fun clearOwnership() {
        savedMusicVolume = null
        changedVolume = false
    }

    sealed interface Result {
        data class Muted(val previousVolume: Int) : Result
        data class Restored(val restoredVolume: Int) : Result
        data object AlreadyMutedByUs : Result
        data object AlreadyMutedExternally : Result
        data object FixedVolumeDevice : Result
        data object NothingToRestore : Result
        data object UserOrSystemChangedVolume : Result
    }
}
