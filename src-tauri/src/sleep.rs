//! Sleep timer: pause playback after a number of minutes, or once the current track ends.
//!
//! In the backend rather than the UI so it holds with the screen off, where Android all but stops a
//! background page's timers. One timer at a time: arming again (or switching off) bumps the
//! generation, and the previous watcher notices and quits.

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use serde::Serialize;
use tauri::Emitter;

use crate::state::AppState;

/// What the UI shows while a timer is armed. `until_ms` is wall-clock (Unix ms) so the UI can count
/// down on its own between events.
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Armed {
    pub until_ms: Option<i64>,
    pub end_of_track: bool,
}

#[derive(Default)]
pub struct SleepTimer {
    generation: AtomicU64,
    armed: Mutex<Option<Armed>>,
}

impl SleepTimer {
    pub fn current(&self) -> Option<Armed> {
        self.armed.lock().unwrap().clone()
    }
}

/// Arm the timer (`minutes`, `end_of_track`, or both: whichever comes first), or switch it off
/// with neither. Returns what is now armed.
pub async fn arm(state: Arc<AppState>, minutes: Option<u64>, end_of_track: bool) -> Option<Armed> {
    let timer = &state.sleep;
    let generation = timer.generation.fetch_add(1, Ordering::SeqCst) + 1;
    if minutes.is_none() && !end_of_track {
        *timer.armed.lock().unwrap() = None;
        return None;
    }
    let deadline = minutes.map(|m| Instant::now() + Duration::from_secs(m * 60));
    let now_ms = SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.as_millis() as i64);
    let armed = Armed { until_ms: minutes.map(|m| now_ms + (m * 60_000) as i64), end_of_track };
    *timer.armed.lock().unwrap() = Some(armed.clone());
    // The track to outlast: the timer fires as soon as anything else is playing, or nothing is.
    let track = match end_of_track {
        true => state.current_item().await.map(|item| item.video_id),
        false => None,
    };

    tauri::async_runtime::spawn(async move {
        loop {
            // Short enough that "end of track" pauses within a beat of the next song starting.
            tokio::time::sleep(Duration::from_millis(250)).await;
            if state.sleep.generation.load(Ordering::SeqCst) != generation {
                return; // re-armed or switched off
            }
            let due = deadline.is_some_and(|d| Instant::now() >= d);
            let track_over = match &track {
                Some(id) => {
                    state.player.is_idle()
                        || state.current_item().await.map(|item| item.video_id).as_ref() != Some(id)
                }
                None => false,
            };
            if due || track_over {
                let _ = state.player.pause();
                *state.sleep.armed.lock().unwrap() = None;
                let _ = state.app.emit("sleep-timer", Option::<Armed>::None);
                tracing::info!("sleep timer: playback paused");
                return;
            }
        }
    });
    Some(armed)
}
