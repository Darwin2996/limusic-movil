package com.limusic.desktop

import android.app.Activity
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

/** Re-mints the YouTube Music cookies from the Google session that [GoogleLoginActivity] left in the
 * app's cookie jar, with nothing on screen: a WebView that is never attached loads
 * music.youtube.com, and YouTube's own startup is what makes Google hand out rotated cookies.
 *
 * The Android side of `refresh_session` (src-tauri/src/session.rs). The desktop opens a hidden
 * window for it, which wry can't do here: its single WebView would replace the app's UI. */
object GoogleSessionRefresher {
  private const val URL = "https://music.youtube.com/"
  private const val TIMEOUT_MS = 45_000L
  private var webView: WebView? = null

  /** Load music.youtube.com; [onLoaded] says whether it got there before the timeout. */
  fun open(activity: Activity, onLoaded: (Boolean) -> Unit) {
    activity.runOnUiThread {
      destroy()
      var answered = false
      val answer = { loaded: Boolean ->
        if (!answered) {
          answered = true
          onLoaded(loaded)
        }
      }
      val view = WebView(activity)
      view.settings.javaScriptEnabled = true
      view.settings.domStorageEnabled = true
      // Same browser identity as the sign-in screen (see GoogleLoginActivity).
      view.settings.userAgentString = view.settings.userAgentString.replace("; wv)", ")")
      CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
      view.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
          if (Uri.parse(url).host == "music.youtube.com") answer(true)
        }
      }
      view.postDelayed({ answer(false) }, TIMEOUT_MS)
      webView = view
      view.loadUrl(URL)
    }
  }

  /** The youtube.com cookies as a `Cookie` header, HttpOnly ones included. */
  fun cookies(): String = CookieManager.getInstance().getCookie(URL).orEmpty()

  fun close(activity: Activity) {
    activity.runOnUiThread { destroy() }
  }

  private fun destroy() {
    webView?.destroy()
    webView = null
  }
}
