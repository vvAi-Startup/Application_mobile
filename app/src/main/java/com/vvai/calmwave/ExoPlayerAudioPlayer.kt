package com.vvai.calmwave

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView

class ExoPlayerAudioPlayer(private val context: Context) {
    private val exoPlayer get() = PlaybackPlayerHolder.getPlayer(context)

    fun initialize(playerView: PlayerView, audioFiles: List<String>) {
        playerView.player = exoPlayer
        if (audioFiles.isNotEmpty()) {
            val mediaItems = audioFiles.map { MediaItem.fromUri(it) }
            exoPlayer.setMediaItems(mediaItems)
            exoPlayer.prepare()
        }
    }

    fun initializeQueue(audioFiles: List<String>, startIndex: Int = 0) {
        if (audioFiles.isEmpty()) return
        PlaybackPlayerHolder.setQueue(context, audioFiles, startIndex)
    }

    fun play() {
        PlaybackForegroundService.start(context)
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun next() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        }
    }

    fun previous() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        }
    }

    fun release() {
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        PlaybackForegroundService.stop(context)
    }

    fun stopAllPlayback() {
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        PlaybackForegroundService.stop(context)
    }

    fun getDuration(): Long {
        return exoPlayer.duration
    }

    fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    fun isPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun initializeCustom(audioFile: String) {
        initializeQueue(listOf(audioFile), 0)
    }

    fun setPlaybackSpeed(speed: Float) {
        // Always preserve pitch (pitch = 1.0f) for natural sound
        exoPlayer.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed, 1.0f))
    }

    fun getCurrentMediaPath(): String? {
        return PlaybackPlayerHolder.currentMediaPath()
    }
}
