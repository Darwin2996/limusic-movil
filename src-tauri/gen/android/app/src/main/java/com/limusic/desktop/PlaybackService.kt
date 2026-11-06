package com.limusic.desktop

import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.ForwardingPlayer
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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

      // The home-screen widget (PlayerWidget) follows the track and the play state.
      override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateWidget()

      override fun onIsPlayingChanged(isPlaying: Boolean) = updateWidget()
    })
    mediaSession = MediaSession.Builder(this, QueueControlPlayer(player))
      .setCallback(LikeCallback())
      .build()
    showLike()
  }

  /** The heart on the notification and lock screen. Tapping it asks the app to rate the track
   * (the `like` event: the UI does the rating, as its own heart does), and the app answers with
   * `setLiked` so the icon shows the result. */
  private inner class LikeCallback : MediaSession.Callback {
    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
      .setAvailableSessionCommands(
        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(LIKE).build(),
      )
      .build()

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      if (customCommand.customAction == LIKE.customAction) AndroidPlaybackEvents.add("like")
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
  }

  private fun updateWidget() {
    val metadata = player.mediaMetadata
    PlayerWidget.update(
      this,
      metadata.title?.toString(),
      metadata.artist?.toString(),
      metadata.artworkUri?.toString(),
      player.isPlaying,
    )
  }

  private fun showLike() {
    val button = CommandButton.Builder(
      if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
      .setDisplayName(if (liked) "Quitar de Me gusta" else "Me gusta")
      .setSessionCommand(LIKE)
      .build()
    mediaSession.setMediaButtonPreferences(listOf(button))
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
    private val LIKE = SessionCommand("com.limusic.LIKE", Bundle.EMPTY)
    @Volatile private var liked = false

    /** The widget's play button: toggle the player if the service is up. False when it isn't. */
    fun togglePlayback(): Boolean {
      val player = currentService?.player ?: return false
      if (player.isPlaying) player.pause() else player.play()
      return true
    }

    /** Fill or empty the notification's heart (the current track's rating, from the app). */
    fun setLiked(on: Boolean) {
      liked = on
      currentService?.showLike()
    }

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
