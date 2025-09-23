package com.vvai.calmwave

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class ExoPlayerAudioPlayer(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null

    fun initialize(playerView: PlayerView, audioFiles: List<String>) {
        release()
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            playerView.player = player
            val mediaItems = audioFiles.map { MediaItem.fromUri(it) }
            player.setMediaItems(mediaItems)
            player.prepare()
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun next() {
        exoPlayer?.let {
            if (it.hasNextMediaItem()) it.seekToNextMediaItem()
        }
    }

    fun previous() {
        exoPlayer?.let {
            if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }

    fun getDuration(): Long {
        return exoPlayer?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun initializeCustom(audioFile: String) {
        release()
        exoPlayer = ExoPlayer.Builder(context).build().also { player ->
            val mediaItem = MediaItem.fromUri(audioFile)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        // Always preserve pitch (pitch = 1.0f) for natural sound
        exoPlayer?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed, 1.0f))
    }
}
