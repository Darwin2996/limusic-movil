package com.limusic.desktop

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.concurrent.ConcurrentLinkedQueue

object AndroidPlaybackEvents {
  private val queue = ConcurrentLinkedQueue<Map<String, String?>>()

  fun add(kind: String, message: String? = null) {
    queue.add(mapOf("kind" to kind, "message" to message))
  }

  fun drain(): List<Map<String, String?>> {
    val events = mutableListOf<Map<String, String?>>()
    while (true) events.add(queue.poll() ?: break)
    return events
  }
}

class PlaybackService : MediaSessionService() {
  private lateinit var player: ExoPlayer
  private lateinit var mediaSession: MediaSession

  override fun onCreate() {
    super.onCreate()
    currentService = this
    val factory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    httpFactory = factory
    player = ExoPlayer.Builder(this)
      // Keep HTTP playback and also route app-private file paths through FileDataSource.
      .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, factory)))
      .build()
    player.setAudioAttributes(AudioAttributes.DEFAULT, true)
    applyGain()
    player.addListener(object : Player.Listener {
      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) AndroidPlaybackEvents.add("transition")
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) AndroidPlaybackEvents.add("ended")
      }

      override fun onPlayerError(error: PlaybackException) {
        AndroidPlaybackEvents.add("failed", error.message ?: "Audio playback failed")
      }
    })
    mediaSession = MediaSession.Builder(this, QueueControlPlayer(player)).build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

  override fun onDestroy() {
    mediaSession.release()
    player.release()
    httpFactory = null
    currentService = null
    super.onDestroy()
  }

  private class QueueControlPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
      .buildUpon()
      .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
      .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
      .build()

    override fun seekToNextMediaItem() {
      AndroidPlaybackEvents.add("next")
    }

    override fun seekToPreviousMediaItem() {
      AndroidPlaybackEvents.add("previous")
    }
  }

  companion object {
    @Volatile var httpFactory: DefaultHttpDataSource.Factory? = null
    @Volatile private var volumePercent: Int = 100
    @Volatile private var gainDb: Double? = null

    fun setVolumePercent(volume: Int) {
      volumePercent = volume.coerceIn(0, 100)
      applyGain()
    }

    fun setGainDb(gain: Double?) {
      gainDb = gain
      applyGain()
    }

    private fun applyGain() {
      val service = currentService ?: return
      val gain = gainDb?.let { Math.pow(10.0, it / 20.0) } ?: 1.0
      service.player.volume = ((volumePercent / 100.0) * gain).coerceIn(0.0, 1.0).toFloat()
    }

    @Volatile private var currentService: PlaybackService? = null
  }
}
