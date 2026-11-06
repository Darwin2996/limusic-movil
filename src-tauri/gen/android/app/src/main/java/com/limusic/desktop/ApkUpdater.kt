package com.limusic.desktop

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** The in-app update (lib/apkupdate.ts): download a release's APK into the cache and hand it to
 * Android's package installer, which asks the user to confirm. Android never installs silently.
 *
 * An update installs over this app only when it is signed with the same key, so releases have to
 * be built with the keystore that built the installed APK (scripts/android-release.ps1). */
object ApkUpdater {
  /** How far the download in flight has got, for the update notice's progress bar
   * (`apkUpdateProgress`). `total` is -1 until the server says how long the APK is. */
  @Volatile var received = 0L
    private set
  @Volatile var total = -1L
    private set

  /** Download [url] and open the installer. [done] gets null on success, else why it stopped:
   * `INSTALL_PERMISSION` when the user still has to allow installs from LiMusic (the settings
   * screen for that has just been opened), or the download error. Runs off the main thread. */
  fun install(activity: Activity, url: String, done: (String?) -> Unit) {
    Thread {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
          !activity.packageManager.canRequestPackageInstalls()
        ) {
          activity.startActivity(
            Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:${activity.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
          )
          done("INSTALL_PERMISSION")
          return@Thread
        }
        val file = File(activity.cacheDir, "update.apk")
        received = 0L
        total = -1L
        download(url, file)
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(
          Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        done(null)
      } catch (error: Exception) {
        done(error.message ?: "Download failed")
      }
    }.start()
  }

  /** GitHub serves a release asset through a redirect to its storage host, so follow those. */
  private fun download(url: String, out: File) {
    var target = URL(url)
    repeat(5) {
      val connection = target.openConnection() as HttpURLConnection
      connection.instanceFollowRedirects = false
      connection.connectTimeout = 15_000
      connection.readTimeout = 30_000
      when (connection.responseCode) {
        in 300..399 -> {
          target = URL(target, connection.getHeaderField("Location"))
          connection.disconnect()
        }
        HttpURLConnection.HTTP_OK -> {
          total = connection.contentLengthLong
          connection.inputStream.use { input ->
            out.outputStream().use { output ->
              val buffer = ByteArray(64 * 1024)
              while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                received += n
              }
            }
          }
          return
        }
        else -> throw IllegalStateException("HTTP ${connection.responseCode}")
      }
    }
    throw IllegalStateException("Too many redirects")
  }
}
