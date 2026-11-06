//! Offline downloads: a song's audio saved under the app's data dir, which `AppState::resolve` plays
//! from before it goes to the network, so a downloaded song plays with or without a connection.
//!
//! Each song is two files in `downloads/`: `<videoId>.audio` (the stream exactly as served; the
//! player sniffs the container) and `<videoId>.json` (the `SongItem` it was downloaded as, for the
//! Downloads list). No database table: the folder is the whole state, so deleting a file is all a
//! removal takes. The queue keeps the YouTube id, so likes, history and stats work as for any song.

use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use innertube::SongItem;
use tauri::{Emitter, Manager};

use crate::state::AppState;

/// One bounded range per request, as the audio proxy does: googlevideo throttles an open-ended
/// read to about realtime, and rustypipe URLs answer nothing else.
const CHUNK: u64 = 4 * 1024 * 1024;
/// A chunk that makes no progress for this long is a dead connection.
const STALL: Duration = Duration::from_secs(30);

/// One download at a time: a playlist queues its songs here instead of opening dozens of streams.
static ONE_AT_A_TIME: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

pub fn dir(app: &tauri::AppHandle) -> Option<PathBuf> {
    app.path().app_data_dir().ok().map(|d| d.join("downloads"))
}

/// A YouTube id is `[A-Za-z0-9_-]`: anything else (a local file's id, a path) is never a download.
fn valid_id(video_id: &str) -> bool {
    !video_id.is_empty()
        && video_id.len() <= 64
        && video_id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

/// The downloaded audio for `video_id`, if there is one.
pub fn file_for(app: &tauri::AppHandle, video_id: &str) -> Option<PathBuf> {
    if !valid_id(video_id) {
        return None;
    }
    let path = dir(app)?.join(format!("{video_id}.audio"));
    path.is_file().then_some(path)
}

/// Every downloaded song, newest first.
pub fn list(app: &tauri::AppHandle) -> Vec<SongItem> {
    let Some(dir) = dir(app) else { return Vec::new() };
    let Ok(entries) = std::fs::read_dir(&dir) else { return Vec::new() };
    let mut songs: Vec<(std::time::SystemTime, SongItem)> = entries
        .flatten()
        .filter(|e| e.path().extension().is_some_and(|x| x == "json"))
        .filter_map(|e| {
            let song: SongItem = serde_json::from_slice(&std::fs::read(e.path()).ok()?).ok()?;
            // A sidecar without its audio is a removal or a download that never finished.
            file_for(app, &song.video_id)?;
            Some((e.metadata().ok()?.modified().ok()?, song))
        })
        .collect();
    songs.sort_by(|a, b| b.0.cmp(&a.0));
    songs.into_iter().map(|(_, s)| s).collect()
}

/// Bytes the downloads take on disk.
pub fn size(app: &tauri::AppHandle) -> u64 {
    let Some(dir) = dir(app) else { return 0 };
    let Ok(entries) = std::fs::read_dir(dir) else { return 0 };
    entries.flatten().filter_map(|e| e.metadata().ok()).map(|m| m.len()).sum()
}

pub fn remove(app: &tauri::AppHandle, video_id: &str) {
    let Some(dir) = dir(app) else { return };
    if !valid_id(video_id) {
        return;
    }
    for ext in ["audio", "json", "part"] {
        let _ = std::fs::remove_file(dir.join(format!("{video_id}.{ext}")));
    }
}

/// Download `song` (queued behind any download in flight). Reports `download-progress` while it
/// runs and ends with `download-done` or `download-failed`, all carrying the `videoId`.
pub async fn download(state: Arc<AppState>, song: SongItem) {
    let id = song.video_id.clone();
    let result = async {
        if !valid_id(&id) {
            return Err("Only YouTube songs can be downloaded".to_owned());
        }
        let _turn = ONE_AT_A_TIME.lock().await;
        let dir = dir(&state.app).ok_or("No app data directory")?;
        if file_for(&state.app, &id).is_some() {
            return Ok(()); // already here, perhaps queued twice
        }
        std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
        let duration = crate::state::parse_duration_ms(song.duration.as_deref()) / 1000;
        let data = state.resolve(&id, false, duration).await.map_err(|e| e.to_string())?;
        let part = dir.join(format!("{id}.part"));
        fetch(&state, &id, &data.stream_url, &data.headers, &part).await.inspect_err(|_| {
            let _ = std::fs::remove_file(&part);
        })?;
        std::fs::rename(&part, dir.join(format!("{id}.audio"))).map_err(|e| e.to_string())?;
        let json = serde_json::to_vec(&song).map_err(|e| e.to_string())?;
        std::fs::write(dir.join(format!("{id}.json")), json).map_err(|e| e.to_string())?;
        Ok(())
    }
    .await;
    match result {
        Ok(()) => {
            let _ = state.app.emit("download-done", serde_json::json!({ "videoId": id }));
        }
        Err(error) => {
            tracing::warn!(video_id = %id, %error, "download failed");
            let _ = state
                .app
                .emit("download-failed", serde_json::json!({ "videoId": id, "error": error }));
        }
    }
}

/// Copy the whole stream to `out` in bounded ranges, reporting progress per chunk.
async fn fetch(
    state: &AppState,
    id: &str,
    url: &str,
    headers: &std::collections::HashMap<String, String>,
    out: &Path,
) -> Result<(), String> {
    // Blocking writes, a 4 MiB chunk at a time: brief, and tokio's fs is not enabled here.
    let mut file = std::fs::File::create(out).map_err(|e| e.to_string())?;
    let mut pos = 0u64;
    let mut total: Option<u64> = None;
    loop {
        let end = match total {
            Some(t) => (pos + CHUNK - 1).min(t - 1),
            None => pos + CHUNK - 1,
        };
        let mut req = crate::http::client()
            .get(url)
            .header("Range", format!("bytes={pos}-{end}"))
            .header("Accept-Encoding", "identity");
        for (k, v) in headers {
            req = req.header(k.as_str(), v.as_str());
        }
        let resp = tokio::time::timeout(STALL, req.send())
            .await
            .map_err(|_| "The connection stalled".to_owned())?
            .and_then(|r| r.error_for_status())
            .map_err(|e| e.to_string())?;
        if total.is_none() {
            total = resp
                .headers()
                .get("Content-Range")
                .and_then(|v| v.to_str().ok())
                .and_then(|v| v.rsplit_once('/')?.1.trim().parse().ok());
        }
        let bytes = tokio::time::timeout(STALL, resp.bytes())
            .await
            .map_err(|_| "The connection stalled".to_owned())?
            .map_err(|e| e.to_string())?;
        if bytes.is_empty() {
            break;
        }
        file.write_all(&bytes).map_err(|e| e.to_string())?;
        pos += bytes.len() as u64;
        let _ = state.app.emit(
            "download-progress",
            serde_json::json!({ "videoId": id, "received": pos, "total": total }),
        );
        match total {
            Some(t) if pos >= t => break,
            // No length known: a short chunk was the last one.
            None if (bytes.len() as u64) < CHUNK => break,
            _ => {}
        }
    }
    file.flush().map_err(|e| e.to_string())?;
    if pos == 0 {
        return Err("The stream was empty".to_owned());
    }
    Ok(())
}
