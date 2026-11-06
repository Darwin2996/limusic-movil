//! The floating desktop mini-window is replaced by the mobile mini-player UI.

use tauri::AppHandle;

pub const LABEL: &str = "mini";

pub fn open(_app: &AppHandle) -> Result<(), String> {
    Err("The floating mini player is unavailable on Android".into())
}

pub fn set_compact(_app: &AppHandle, _compact: bool) -> Result<(), String> {
    Err("The floating mini player is unavailable on Android".into())
}

pub fn close(_app: &AppHandle) {}

pub fn save_position(_app: &AppHandle) {}
