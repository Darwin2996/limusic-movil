package com.limusic.desktop

import android.content.Intent

/** A link shared to LiMusic ("Share" from another app, or "Open with" on a YouTube link), kept until
 * the page takes it (`take_shared_link`, polled by LinkDialog.svelte on start and whenever the app
 * comes back to the foreground, which is what a share does). */
object SharedLinks {
  @Volatile private var pending: String? = null

  fun capture(intent: Intent?) {
    val text = when (intent?.action) {
      Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
      Intent.ACTION_VIEW -> intent.dataString
      else -> null
    }
    if (!text.isNullOrBlank()) pending = text
    // Consumed: the activity keeps its intent, and coming back to the foreground would hand the
    // same link over again.
    intent?.action = null
  }

  fun take(): String? {
    val text = pending
    pending = null
    return text
  }
}
