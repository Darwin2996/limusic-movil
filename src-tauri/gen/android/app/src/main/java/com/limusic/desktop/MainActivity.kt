package com.limusic.desktop

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge

class MainActivity : TauriActivity() {
  private var contentWebView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    // Phones stay in portrait (the manifest says so): a phone in landscape is wider than the web
    // layout's tablet breakpoint and would get the desktop UI. Declared there rather than asked for
    // here, because on HyperOS asking at runtime turns the phone's rotation lock off system-wide.
    // Tablets rotate: they let go of it, which is the one case that asks.
    if (resources.configuration.smallestScreenWidthDp >= 600) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  // A link shared to the app, at launch or while it runs (singleTask: the same activity).
  override fun onStart() {
    super.onStart()
    SharedLinks.capture(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    SharedLinks.capture(intent)
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)
    contentWebView = webView
    // Rebuild the surface after the first committed visual state too. Android 16 on this device
    // can leave the first WebView frame blank until another view is attached or invalidated.
    webView.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
      override fun onComplete(requestId: Long) {
        recoverWebViewSurface()
      }
    })
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) refreshWebViewSurface()
  }

  /** Request one hardware-backed redraw after the activity regains focus. */
  fun refreshWebViewSurface() {
    contentWebView?.post {
      val webView = contentWebView ?: return@post
      webView.invalidate()
      webView.postInvalidateOnAnimation()
      (webView.parent as? View)?.invalidate()
      window.decorView.postInvalidateOnAnimation()
    }
  }

  /** Reattach the existing WebView to rebuild its hardware surface without reloading the page. */
  fun recoverWebViewSurface() {
    contentWebView?.post {
      val webView = contentWebView ?: return@post
      val parent = webView.parent as? ViewGroup
      if (parent != null) {
        val index = parent.indexOfChild(webView)
        val params = webView.layoutParams
        if (index >= 0 && params != null) {
          parent.removeViewAt(index)
          parent.addView(webView, index, params)
        }
      }
      webView.invalidate()
      webView.postInvalidateOnAnimation()
      parent?.invalidate()
      window.decorView.postInvalidateOnAnimation()
    }
  }
}
