package com.limusic.desktop

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.provider.DocumentsContract
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.tauri.annotation.Command
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.Plugin
import androidx.activity.result.ActivityResult

@InvokeArg
class ExecuteArgs {
  lateinit var action: String
  var url: String? = null
  var headers: Map<String, String> = emptyMap()
  var gainDb: Double? = null
  var start: Double? = null
  var positionSecs: Double = 0.0
  var volume: Int = 100
  var speed: Double = 1.0
  var on: Boolean = false
  var title: String? = null
  var artist: String? = null
  var album: String? = null
  var artwork: String? = null
}

@InvokeArg
class OpenExternalArgs {
  lateinit var url: String
}

@InvokeArg
class DeleteImportedFolderArgs {
  lateinit var path: String
}

@InvokeArg
class SignInGoogleArgs {
  var addAccount: Boolean = false
}

@TauriPlugin
class PlayerPlugin(private val activity: Activity) : Plugin(activity) {
  private val controllerFuture = MediaController.Builder(
    activity,
    SessionToken(activity, ComponentName(activity, PlaybackService::class.java)),
  ).buildAsync()

  @Command
  fun execute(invoke: Invoke) {
    val args = invoke.parseArgs(ExecuteArgs::class.java)
    controllerFuture.addListener({
      try {
        executeOnPlayer(controllerFuture.get(), args)
        invoke.resolve()
      } catch (error: Exception) {
        invoke.reject("Android playback command failed: ${error.message}")
      }
    }, ContextCompat.getMainExecutor(activity))
  }

  @Command
  fun poll(invoke: Invoke) {
    controllerFuture.addListener({
      try {
        val player = controllerFuture.get()
        val duration = player.duration.takeUnless { it < 0 } ?: 0L
        val snapshot = mapOf(
          "positionMs" to player.currentPosition.coerceAtLeast(0L),
          "durationMs" to duration,
          "isPlaying" to player.isPlaying,
          "isIdle" to (player.currentMediaItem == null || player.playbackState == androidx.media3.common.Player.STATE_ENDED),
          "playWhenReady" to player.playWhenReady,
          "currentUrl" to player.currentMediaItem?.localConfiguration?.uri?.toString(),
          "events" to AndroidPlaybackEvents.drain(),
        )
        invoke.resolveObject(snapshot)
      } catch (error: Exception) {
        invoke.reject("Android playback status unavailable: ${error.message}")
      }
    }, ContextCompat.getMainExecutor(activity))
  }

  @Command
  fun openExternal(invoke: Invoke) {
    try {
      val args = invoke.parseArgs(OpenExternalArgs::class.java)
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(args.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      activity.startActivity(intent)
      invoke.resolve()
    } catch (error: Exception) {
      invoke.reject("Could not open the link: ${error.message}")
    }
  }

  /** Open the native Google sign-in screen ([GoogleLoginActivity]). Resolves `{cookie}` with the
   * youtube.com cookie jar, or `{cookie: null}` when the user backs out. The cookies travel only
   * through native plugin IPC to Rust; they are never returned to the Svelte frontend. */
  @Command
  fun signInGoogle(invoke: Invoke) {
    val args = invoke.parseArgs(SignInGoogleArgs::class.java)
    val intent = Intent(activity, GoogleLoginActivity::class.java)
      .putExtra(GoogleLoginActivity.EXTRA_ADD_ACCOUNT, args.addAccount)
    startActivityForResult(invoke, intent, "onGoogleSignInResult")
  }

  @ActivityCallback
  fun onGoogleSignInResult(invoke: Invoke, result: ActivityResult) {
    val cookie = result.data?.getStringExtra(GoogleLoginActivity.EXTRA_COOKIE)
      ?.takeIf { result.resultCode == Activity.RESULT_OK && it.isNotBlank() }
    invoke.resolveObject(mapOf("cookie" to cookie))
  }

  /** The off-screen session refresh (`GoogleSessionRefresher`): load music.youtube.com, read the
   * jar as it rotates, and drop the WebView. Driven by `refresh_session` in session.rs. */
  @Command
  fun openGoogleRefresh(invoke: Invoke) {
    GoogleSessionRefresher.open(activity) { loaded -> invoke.resolveObject(mapOf("loaded" to loaded)) }
  }

  @Command
  fun readGoogleCookies(invoke: Invoke) {
    invoke.resolveObject(mapOf("cookie" to GoogleSessionRefresher.cookies()))
  }

  @Command
  fun closeGoogleRefresh(invoke: Invoke) {
    GoogleSessionRefresher.close(activity)
    invoke.resolve()
  }

  /** Back on the first page: leave the app the way Android's own Back does, to the background,
   * rather than finishing the activity. The page handles Back itself (`onBackButtonPress`), so
   * Tauri no longer does this. */
  @Command
  fun moveToBackground(invoke: Invoke) {
    activity.moveTaskToBack(true)
    invoke.resolve()
  }

  /** Whether Android exempts LiMusic from battery optimization. Without it, aggressive skins (MIUI,
   * One UI…) kill the process in the background and the music stops. */
  @Command
  fun batteryExempt(invoke: Invoke) {
    invoke.resolveObject(mapOf("exempt" to isBatteryExempt()))
  }

  /** Android's own "let this app run in the background" dialog; resolves with the outcome. */
  @Command
  fun requestBatteryExemption(invoke: Invoke) {
    if (isBatteryExempt()) {
      invoke.resolveObject(mapOf("exempt" to true))
      return
    }
    val intent = Intent(
      Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
      Uri.parse("package:${activity.packageName}"),
    )
    startActivityForResult(invoke, intent, "onBatteryExemptionResult")
  }

  @ActivityCallback
  fun onBatteryExemptionResult(invoke: Invoke, result: ActivityResult) {
    invoke.resolveObject(mapOf("exempt" to isBatteryExempt()))
  }

  private fun isBatteryExempt(): Boolean =
    (activity.getSystemService(Context.POWER_SERVICE) as PowerManager)
      .isIgnoringBatteryOptimizations(activity.packageName)

  /** Android's folder picker returns a SAF content URI, which std::fs and the Rust tag reader
   * cannot traverse. Import supported audio files into app-private storage so the existing local
   * library scanner and Media3 file playback can use ordinary paths. */
  @Command
  fun pickLocalMusicFolder(invoke: Invoke) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
      Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
    )
    startActivityForResult(invoke, intent, "onLocalMusicFolderPicked")
  }

  @Command
  fun deleteImportedLocalFolder(invoke: Invoke) {
    try {
      val args = invoke.parseArgs(DeleteImportedFolderArgs::class.java)
      val root = File(activity.filesDir, "local-music").canonicalFile
      val target = File(args.path).canonicalFile
      if (target.parentFile != root || !target.path.startsWith(root.path + File.separator)) {
        invoke.reject("LOCAL_FOLDER_DELETE_DENIED")
        return
      }
      if (!target.exists() || !target.deleteRecursively()) {
        invoke.reject("LOCAL_FOLDER_DELETE_FAILED")
        return
      }
      invoke.resolve()
    } catch (error: Exception) {
      invoke.reject("LOCAL_FOLDER_DELETE_FAILED: ${error.message}")
    }
  }

  @ActivityCallback
  fun onLocalMusicFolderPicked(invoke: Invoke, result: ActivityResult) {
    if (result.resultCode != Activity.RESULT_OK) {
      invoke.resolve()
      return
    }
    val tree = result.data?.data
    if (tree == null) {
      invoke.reject("Android did not return a folder URI")
      return
    }
    try {
      activity.contentResolver.takePersistableUriPermission(
        tree,
        result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    } catch (_: Exception) {
      // Some document providers do not offer persistable grants. The selected tree is still readable
      // during this import, and the copied files no longer depend on the grant afterwards.
    }
    Thread {
      var importRoot: File? = null
      try {
        val docId = DocumentsContract.getTreeDocumentId(tree)
        // OPEN_DOCUMENT_TREE returns a tree URI, which Android's DocumentsProvider does not
        // accept for metadata queries. Query the tree's root document URI instead.
        val name = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(tree, docId)) ?: "Music"
        val root = File(activity.filesDir, "local-music/${safeName(name)}-${System.currentTimeMillis()}")
        importRoot = root
        root.mkdirs()
        val copied = copyAudioTree(tree, docId, root)
        if (copied == 0) {
          root.deleteRecursively()
          invoke.reject("LOCAL_FOLDER_EMPTY")
        } else {
          invoke.resolveObject(root.canonicalPath)
        }
      } catch (error: Exception) {
        importRoot?.deleteRecursively()
        invoke.reject("LOCAL_FOLDER_READ_FAILED: ${error.message}")
      } finally {
        try {
          activity.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
          // Providers that did not persist the grant have nothing to release.
        }
      }
    }.start()
  }

  private fun queryDisplayName(uri: Uri): String? {
    activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) return c.getString(0)
    }
    return null
  }

  private fun safeName(value: String): String = value
    .replace(Regex("[^A-Za-z0-9._ -]"), "_")
    .trim('.', ' ')
    .take(80)
    .ifBlank { "Music" }

  private fun copyAudioTree(tree: Uri, parentId: String, output: File): Int {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
    var copied = 0
    activity.contentResolver.query(
      children,
      arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
      null,
      null,
      null,
    )?.use { cursor: Cursor ->
      while (cursor.moveToNext()) {
        val id = cursor.getString(0) ?: continue
        val name = safeName(cursor.getString(1) ?: continue)
        val mime = cursor.getString(2) ?: ""
        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
          val child = File(output, name)
          child.mkdirs()
          copied += copyAudioTree(tree, id, child)
        } else if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS) {
          val source = DocumentsContract.buildDocumentUriUsingTree(tree, id)
          activity.contentResolver.openInputStream(source)?.use { input ->
            val destination = File(output, name)
            FileOutputStream(destination).use { output -> input.copyTo(output) }
            copied++
          }
        }
      }
    }
    return copied
  }

  private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "wma", "aiff", "aif", "ape", "wv", "mka")

  private fun executeOnPlayer(player: MediaController, args: ExecuteArgs) {
    when (args.action) {
      "load" -> {
        PlaybackService.httpFactory?.setDefaultRequestProperties(args.headers)
        PlaybackService.setGainDb(args.gainDb)
        val media = MediaItem.Builder().setUri(args.url ?: return).build()
        if (args.start != null) player.setMediaItem(media, (args.start!! * 1000.0).toLong())
        else player.setMediaItem(media)
        player.prepare()
        player.play()
      }
      "enqueue" -> args.url?.let { player.addMediaItem(MediaItem.fromUri(it)) }
      "clearPlaylist" -> {
        val next = (player.currentMediaItemIndex + 1).coerceAtLeast(0)
        if (next < player.mediaItemCount) player.removeMediaItems(next, player.mediaItemCount)
      }
      "stop" -> {
        player.stop()
        player.clearMediaItems()
      }
      "play" -> player.play()
      "pause" -> player.pause()
      "toggle" -> if (player.isPlaying) player.pause() else player.play()
      "seek" -> player.seekTo((args.positionSecs.coerceAtLeast(0.0) * 1000.0).toLong())
      "setVolume" -> PlaybackService.setVolumePercent(args.volume)
      "setGain" -> PlaybackService.setGainDb(args.gainDb)
      "setSpeed" -> player.setPlaybackSpeed(args.speed.toFloat().coerceIn(0.25f, 2.0f))
      "setLoopFile" -> player.repeatMode = if (args.on) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
      "setMetadata" -> {
        val index = player.currentMediaItemIndex
        val current = player.currentMediaItem
        if (current != null && index >= 0) {
          val metadata = MediaMetadata.Builder()
            .setTitle(args.title)
            .setDisplayTitle(args.title)
            .setArtist(args.artist)
            .setAlbumTitle(args.album)
            .setArtworkUri(args.artwork?.let(Uri::parse))
            .build()
          player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(metadata).build())
        }
      }
      // Android's system ProxySelector is used by the native HTTP stack.
      "setHttpProxy" -> Unit
      else -> throw IllegalArgumentException("Unknown playback action: ${args.action}")
    }
  }
}
