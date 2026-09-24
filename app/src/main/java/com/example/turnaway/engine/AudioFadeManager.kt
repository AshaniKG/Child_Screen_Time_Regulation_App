package com.example.turnaway.engine

import android.content.Context
import android.media.AudioManager

/**
 * Legacy wrapper delegate around [MediaVolumeManager] for system-wide volume fading.
 */
class AudioFadeManager(
    context: Context,
    audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
) {
    private val delegate = MediaVolumeManager(context, audioManager)

    fun startFadeSession(durationMs: Long, enableFade: Boolean = true) {
        delegate.startFade(durationMs, enableFade)
    }

    fun getCurrentAllowedVolume(elapsedMs: Long): Int {
        return delegate.calculateCurrentTargetVolume(elapsedMs)
    }

    fun applyVolumeForElapsed(elapsedMs: Long) {
        delegate.applyVolumeForElapsed(elapsedMs)
    }

    fun enforceVolume(allowedVol: Int) {
        delegate.enforceVolume(allowedVol)
    }

    fun stopAndRestoreVolume() {
        delegate.onStopClicked()
    }
}
