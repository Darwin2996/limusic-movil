//! Android has no system-wide shortcut registration equivalent to the desktop backends.

use std::collections::HashMap;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};
use tauri::AppHandle;

use crate::db::Db;

pub const SETTINGS_KEY: &str = "global_hotkeys";
pub(crate) static LAST_NONZERO_VOLUME: std::sync::atomic::AtomicI64 =
    std::sync::atomic::AtomicI64::new(100);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HotkeyAction {
    PlayPause,
    NextTrack,
    PrevTrack,
    VolumeUp,
    VolumeDown,
    MuteToggle,
    SeekForward,
    SeekBackward,
    ToggleShuffle,
    ToggleRepeat,
    ShowApp,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HotkeysConfig {
    pub enabled: bool,
    pub bindings: HashMap<HotkeyAction, String>,
}

impl Default for HotkeysConfig {
    fn default() -> Self {
        Self { enabled: false, bindings: HashMap::new() }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HotkeyRegisterResult {
    pub success: bool,
    pub config: HotkeysConfig,
    pub errors: HashMap<HotkeyAction, String>,
}

pub struct HotkeysManager(Mutex<HotkeysConfig>);

pub(crate) fn change_volume(
    state: &crate::state::AppState,
    app: &AppHandle,
    f: impl FnOnce(i64) -> i64,
) {
    static LOCK: Mutex<()> = Mutex::new(());
    let _guard = LOCK.lock().unwrap();
    let volume = f(state.player.volume()).clamp(0, 100);
    if state.player.set_volume(volume).is_ok() {
        if volume > 0 {
            LAST_NONZERO_VOLUME.store(volume, std::sync::atomic::Ordering::Relaxed);
        }
        state.db.set_setting("volume", &volume.to_string());
        use tauri::Emitter;
        let _ = app.emit("volume", volume);
        state.media_set_volume(volume);
    }
}

impl HotkeysManager {
    pub fn new(config: HotkeysConfig) -> Self {
        Self(Mutex::new(config))
    }

    pub fn get_config(&self) -> HotkeysConfig {
        self.0.lock().unwrap().clone()
    }

    pub fn apply_config(&self, _app: &AppHandle, config: HotkeysConfig) -> HotkeyRegisterResult {
        let errors = if config.enabled {
            config
                .bindings
                .keys()
                .copied()
                .map(|action| (action, "System-wide hotkeys are unavailable on Android".into()))
                .collect()
        } else {
            HashMap::new()
        };
        *self.0.lock().unwrap() = config.clone();
        HotkeyRegisterResult { success: errors.is_empty(), config, errors }
    }
}

pub fn load_config(db: &Db) -> HotkeysConfig {
    db.get_setting(SETTINGS_KEY)
        .and_then(|json| serde_json::from_str(&json).ok())
        .unwrap_or_default()
}

pub fn save_config(db: &Db, config: &HotkeysConfig) {
    if let Ok(json) = serde_json::to_string(config) {
        db.set_setting(SETTINGS_KEY, &json);
    }
}
