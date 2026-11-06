//! Android uses its notification/media-session controls instead of a desktop system tray.

use tauri::{AppHandle, Emitter, Manager};

pub fn init(_app: &AppHandle) -> tauri::Result<()> {
    Ok(())
}

pub fn available() -> bool {
    false
}

pub fn set_available(_available: bool) {}

pub fn show_main(app: &AppHandle) {
    crate::mini::close(app);
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.show();
        let _ = window.set_focus();
        set_main_visible(app, true);
    }
}

pub fn set_main_visible(app: &AppHandle, visible: bool) {
    let _ = app.emit_to("main", "ui-visible", visible);
}

pub fn set_playing(_app: &AppHandle, _playing: bool) {}

pub fn set_icon(_app: &AppHandle, _icon: &tauri::image::Image<'_>) {}
