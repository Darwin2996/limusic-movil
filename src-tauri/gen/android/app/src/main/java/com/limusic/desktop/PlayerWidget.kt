package com.limusic.desktop

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import java.net.HttpURLConnection
import java.net.URL

/** The home-screen player widget: cover, title, artist and the transport. PlaybackService pushes
 * the track and the play state here ([update]); the buttons come back through [onReceive] and act
 * on the service's player, previous/next going through the app's queue like the notification's.
 * With nothing playing (or the app closed) it shows LiMusic, and a tap opens the app. */
class PlayerWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    render(context)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    when (intent.action) {
      TOGGLE -> if (!PlaybackService.togglePlayback()) openApp(context)
      NEXT -> AndroidPlaybackEvents.add("next")
      PREVIOUS -> AndroidPlaybackEvents.add("previous")
    }
  }

  companion object {
    private const val TOGGLE = "com.limusic.widget.TOGGLE"
    private const val NEXT = "com.limusic.widget.NEXT"
    private const val PREVIOUS = "com.limusic.widget.PREVIOUS"

    @Volatile private var title: String? = null
    @Volatile private var artist: String? = null
    @Volatile private var playing = false
    @Volatile private var art: Bitmap? = null
    @Volatile private var artUrl: String? = null

    /** The track or play state changed. The cover is fetched off the main thread, once per URL. */
    fun update(context: Context, title: String?, artist: String?, artwork: String?, playing: Boolean) {
      this.title = title
      this.artist = artist
      this.playing = playing
      if (artwork != artUrl) {
        artUrl = artwork
        art = null
        if (artwork != null) {
          Thread {
            val bitmap = loadCover(artwork)
            if (artUrl == artwork) {
              art = bitmap
              render(context)
            }
          }.start()
        }
      }
      render(context)
    }

    private fun render(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
      if (ids.isEmpty()) return
      val views = RemoteViews(context.packageName, R.layout.widget_player)
      views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.app_name))
      views.setTextViewText(R.id.widget_artist, artist ?: context.getString(R.string.widget_idle))
      val cover = art
      if (cover != null) views.setImageViewBitmap(R.id.widget_art, cover)
      else views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
      views.setImageViewResource(
        R.id.widget_toggle,
        if (playing) R.drawable.widget_pause else R.drawable.widget_play,
      )
      views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, TOGGLE))
      views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, NEXT))
      views.setOnClickPendingIntent(R.id.widget_previous, broadcast(context, PREVIOUS))
      views.setOnClickPendingIntent(R.id.widget_root, launch(context))
      manager.updateAppWidget(ids, views)
    }

    private fun broadcast(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
      context,
      action.hashCode(),
      Intent(context, PlayerWidget::class.java).setAction(action),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun launch(context: Context): PendingIntent = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(context: Context) {
      context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }

    /** A small cover: widgets pass their bitmaps through a binder, which caps their size. */
    private fun loadCover(url: String): Bitmap? = try {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.connectTimeout = 10_000
      connection.readTimeout = 10_000
      connection.inputStream.use { BitmapFactory.decodeStream(it) }?.let {
        Bitmap.createScaledBitmap(it, 160, 160, true)
      }
    } catch (_: Exception) {
      null
    }
  }
}
