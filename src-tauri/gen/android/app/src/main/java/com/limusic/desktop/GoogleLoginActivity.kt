package com.limusic.desktop

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Google sign-in for YouTube Music in a native WebView of its own.
 *
 * The desktop build opens a second Tauri window for this, but wry gives MainActivity a single
 * WebView, so on Android that window replaced the app's UI. YouTube Music has also rejected
 * third-party OAuth tokens since August 2025, so the session is the same youtube.com cookie jar the
 * desktop build exports: once Google redirects to music.youtube.com the cookies go back to the
 * caller as [EXTRA_COOKIE]. Android's autofill works here, so a saved password can be filled in. */
class GoogleLoginActivity : AppCompatActivity() {
  private lateinit var webView: WebView
  private var done = false

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val cookies = CookieManager.getInstance()
    val addAccount = intent.getBooleanExtra(EXTRA_ADD_ACCOUNT, false)
    // Add-account: sign this WebView out of Google first so the fresh sign-in lands on the new
    // account instead of continuing into the one the jar already holds. Saved accounts live in
    // LiMusic's database, not in this jar.
    if (addAccount) clearGoogleCookies(cookies)

    webView = WebView(this)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    // Google turns embedded WebViews away ("this browser may not be secure") on the `; wv` marker;
    // without it the UA is plain Chrome for Android, which is what this engine is.
    webView.settings.userAgentString = webView.settings.userAgentString.replace("; wv)", ")")
    cookies.setAcceptCookie(true)
    cookies.setAcceptThirdPartyCookies(webView, true)
    // Address bar, as a browser would show it, so anyone can check they are typing into Google's
    // own page rather than a LiMusic form.
    val address = TextView(this).apply {
      setTextColor(0xFFE0E0E0.toInt())
      setBackgroundColor(0xFF1E1E1E.toInt())
      textSize = 14f
      gravity = Gravity.CENTER
      val pad = (12 * resources.displayMetrics.density).toInt()
      setPadding(pad, pad, pad, pad)
    }
    webView.webViewClient = object : WebViewClient() {
      override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        val uri = Uri.parse(url)
        address.text = if (uri.scheme == "https") "🔒 ${uri.host}" else uri.host
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (Uri.parse(url).host == "music.youtube.com") captureSession(0)
      }
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xFF1E1E1E.toInt())
      addView(address, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
      addView(webView, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }
    setContentView(root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (webView.canGoBack()) webView.goBack() else finish()
      }
    })
    webView.loadUrl(if (addAccount) ADD_ACCOUNT_URL else LOGIN_URL)
  }

  override fun onDestroy() {
    webView.destroy()
    super.onDestroy()
  }

  /** The redirect that lands on music.youtube.com sets the youtube cookies, sometimes a beat after
   * the page finishes, so poll briefly before deciding the user is not signed in yet. */
  private fun captureSession(attempt: Int) {
    if (done) return
    val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
    val signedIn = cookie.split(';').any {
      val name = it.substringBefore('=').trim()
      name == "SAPISID" || name == "__Secure-3PAPISID"
    }
    if (signedIn) {
      done = true
      CookieManager.getInstance().flush()
      setResult(RESULT_OK, Intent().putExtra(EXTRA_COOKIE, cookie))
      finish()
    } else if (attempt < 6) {
      webView.postDelayed({ captureSession(attempt + 1) }, 500)
    }
  }

  /** Expire every google.com / youtube.com cookie. CookieManager has no per-domain delete, so each
   * name is overwritten expired both as a host-only and as a domain cookie. */
  private fun clearGoogleCookies(cookies: CookieManager) {
    val sites = mapOf(
      "https://accounts.google.com" to ".google.com",
      "https://www.google.com" to ".google.com",
      "https://music.youtube.com" to ".youtube.com",
      "https://www.youtube.com" to ".youtube.com",
    )
    for ((url, domain) in sites) {
      val names = cookies.getCookie(url).orEmpty().split(';')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
      for (name in names) {
        cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
        cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure; Domain=$domain")
      }
    }
    cookies.flush()
  }

  companion object {
    const val EXTRA_ADD_ACCOUNT = "addAccount"
    const val EXTRA_COOKIE = "cookie"
    // Same entry points as the desktop sign-in window (src-tauri/src/session.rs).
    private const val LOGIN_URL =
      "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/"
    private const val ADD_ACCOUNT_URL =
      "https://accounts.google.com/AddSession?service=youtube&continue=https://music.youtube.com/"
  }
}
