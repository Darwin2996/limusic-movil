//! Android playback adapter. Native ExoPlayer lives in the Tauri MediaSessionService plugin;
//! this module preserves the small synchronous player API used by the shared queue coordinator.

use std::{
    collections::HashMap,
    sync::{
        atomic::{AtomicBool, AtomicI64, AtomicU64, Ordering},
        Arc, Mutex,
    },
};

use serde::{Deserialize, Serialize};
use tokio::sync::mpsc::{unbounded_channel, UnboundedReceiver, UnboundedSender};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Android playback bridge is unavailable")]
    BridgeClosed,
    #[error("Pitch shifting isn't available in this Android build")]
    NoPitchFilter,
}

#[derive(Debug, Clone)]
pub enum PlayerEvent {
    Position(f64),
    Duration(f64),
    Playing(bool),
    Paused(bool),
    TrackEnded,
    Looped,
    TrackFailed(String),
    AudioDeviceLost,
    LookaheadFailed(String),
    VideoFailed(String),
    Error(String),
    Next,
    Previous,
    /// The heart on the media notification or lock screen was tapped (PlaybackService).
    LikeToggled,
}

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "action", rename_all = "camelCase")]
pub enum PlayerCommand {
    Load {
        url: String,
        headers: HashMap<String, String>,
        gain_db: Option<f64>,
        start: Option<f64>,
    },
    Enqueue { url: String },
    ClearPlaylist,
    Stop,
    Play,
    Pause,
    Toggle,
    Seek { position_secs: f64 },
    SetVolume { volume: i64 },
    SetGain { gain_db: Option<f64> },
    SetSpeed { speed: f64 },
    SetLoopFile { on: bool },
    SetHttpProxy { proxy: Option<String> },
    SetMetadata {
        title: String,
        artist: String,
        album: Option<String>,
        artwork: Option<String>,
    },
    /// Whether the media notification's heart is filled: the current track's rating.
    SetLiked { on: bool },
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NativeEvent {
    pub kind: String,
    #[serde(default)]
    pub message: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PlayerSnapshot {
    #[serde(default)]
    pub position_ms: i64,
    #[serde(default)]
    pub duration_ms: i64,
    #[serde(default)]
    pub is_playing: bool,
    #[serde(default)]
    pub is_idle: bool,
    #[serde(default)]
    pub play_when_ready: bool,
    #[serde(default)]
    pub current_url: Option<String>,
    #[serde(default)]
    pub events: Vec<NativeEvent>,
}

struct Shared {
    command_tx: UnboundedSender<PlayerCommand>,
    event_tx: UnboundedSender<PlayerEvent>,
    idle: AtomicBool,
    volume: AtomicI64,
    position_bits: AtomicU64,
    current_url: Mutex<Option<String>>,
    gain_db: Mutex<Option<f64>>,
    last_playing: Mutex<Option<bool>>,
    last_paused: Mutex<Option<bool>>,
    last_duration_ms: AtomicI64,
}

pub struct Player {
    shared: Arc<Shared>,
    events: Option<UnboundedReceiver<PlayerEvent>>,
    commands: Option<UnboundedReceiver<PlayerCommand>>,
}

#[derive(Clone)]
pub struct PlayerMonitor {
    shared: Arc<Shared>,
}

impl Player {
    pub fn new(_cache_dir: &str) -> Result<Self, Error> {
        let (command_tx, commands) = unbounded_channel();
        let (event_tx, events) = unbounded_channel();
        Ok(Self {
            shared: Arc::new(Shared {
                command_tx,
                event_tx,
                idle: AtomicBool::new(true),
                volume: AtomicI64::new(100),
                position_bits: AtomicU64::new(0.0f64.to_bits()),
                current_url: Mutex::new(None),
                gain_db: Mutex::new(None),
                last_playing: Mutex::new(None),
                last_paused: Mutex::new(None),
                last_duration_ms: AtomicI64::new(0),
            }),
            events: Some(events),
            commands: Some(commands),
        })
    }

    pub fn take_events(&mut self) -> Option<UnboundedReceiver<PlayerEvent>> {
        self.events.take()
    }

    pub fn take_commands(&mut self) -> Option<(UnboundedReceiver<PlayerCommand>, PlayerMonitor)> {
        self.commands.take().map(|commands| {
            (commands, PlayerMonitor { shared: self.shared.clone() })
        })
    }

    fn send(&self, command: PlayerCommand) -> Result<(), Error> {
        self.shared.command_tx.send(command).map_err(|_| Error::BridgeClosed)
    }

    pub fn load(
        &self,
        url: &str,
        headers: &HashMap<String, String>,
        gain_db: Option<f64>,
        start: Option<f64>,
    ) -> Result<(), Error> {
        *self.shared.current_url.lock().unwrap() = Some(url.to_owned());
        *self.shared.gain_db.lock().unwrap() = gain_db;
        self.shared.idle.store(false, Ordering::Relaxed);
        self.shared.position_bits.store(start.unwrap_or(0.0).to_bits(), Ordering::Relaxed);
        self.send(PlayerCommand::Load {
            url: url.to_owned(),
            headers: headers.clone(),
            gain_db,
            start,
        })
    }

    pub fn enqueue(&self, url: &str) -> Result<(), Error> {
        self.send(PlayerCommand::Enqueue { url: url.to_owned() })
    }

    pub fn set_crossfade(&self, _secs: Option<f64>) {}

    pub fn clear_playlist(&self) -> Result<(), Error> {
        self.send(PlayerCommand::ClearPlaylist)
    }

    pub fn stop(&self) -> Result<(), Error> {
        self.shared.idle.store(true, Ordering::Relaxed);
        *self.shared.current_url.lock().unwrap() = None;
        self.send(PlayerCommand::Stop)
    }

    pub fn is_idle(&self) -> bool {
        self.shared.idle.load(Ordering::Relaxed)
    }

    pub fn play(&self) -> Result<(), Error> {
        self.send(PlayerCommand::Play)
    }

    pub fn pause(&self) -> Result<(), Error> {
        self.send(PlayerCommand::Pause)
    }

    pub fn toggle(&self) -> Result<(), Error> {
        self.send(PlayerCommand::Toggle)
    }

    pub fn set_loop_file(&self, on: bool) -> Result<(), Error> {
        self.send(PlayerCommand::SetLoopFile { on })
    }

    pub fn seek(&self, position_secs: f64) -> Result<(), Error> {
        self.shared.position_bits.store(position_secs.max(0.0).to_bits(), Ordering::Relaxed);
        self.send(PlayerCommand::Seek { position_secs: position_secs.max(0.0) })
    }

    pub fn set_volume(&self, volume: i64) -> Result<(), Error> {
        let volume = volume.clamp(0, 100);
        self.shared.volume.store(volume, Ordering::Relaxed);
        self.send(PlayerCommand::SetVolume { volume })
    }

    pub fn volume(&self) -> i64 {
        self.shared.volume.load(Ordering::Relaxed)
    }

    pub fn set_http_proxy(&self, proxy: Option<&str>) -> Result<(), Error> {
        self.send(PlayerCommand::SetHttpProxy { proxy: proxy.map(str::to_owned) })
    }

    pub fn set_gain(&self, gain_db: Option<f64>) -> Result<(), Error> {
        *self.shared.gain_db.lock().unwrap() = gain_db;
        self.send(PlayerCommand::SetGain { gain_db })
    }

    pub fn set_speed(&self, speed: f64) -> Result<(), Error> {
        self.send(PlayerCommand::SetSpeed { speed: speed.clamp(0.25, 2.0) })
    }

    pub fn set_pitch(&self, semitones: i32) -> Result<(), Error> {
        if semitones == 0 {
            Ok(())
        } else {
            Err(Error::NoPitchFilter)
        }
    }

    pub fn set_metadata(
        &self,
        title: &str,
        artist: &str,
        album: Option<&str>,
        artwork: Option<&str>,
    ) -> Result<(), Error> {
        self.send(PlayerCommand::SetMetadata {
            title: title.to_owned(),
            artist: artist.to_owned(),
            album: album.map(str::to_owned),
            artwork: artwork.map(str::to_owned),
        })
    }

    /// Fill (or empty) the heart on the media notification and lock screen.
    pub fn set_liked(&self, on: bool) -> Result<(), Error> {
        self.send(PlayerCommand::SetLiked { on })
    }

    pub fn set_video_for(&self, _audio_url: &str, _video_url: &str) {}

    pub fn current_path(&self) -> Option<String> {
        self.shared.current_url.lock().ok()?.clone()
    }
}

impl PlayerMonitor {
    pub fn apply(&self, snapshot: PlayerSnapshot) {
        self.shared.idle.store(snapshot.is_idle, Ordering::Relaxed);
        if let Ok(mut current) = self.shared.current_url.lock() {
            *current = snapshot.current_url;
        }

        // A transition can make the queue load another item. Queue events first so the following
        // position and duration values describe the item that is now current.
        for event in snapshot.events {
            let event = match event.kind.as_str() {
                "transition" | "ended" => Some(PlayerEvent::TrackEnded),
                "looped" => Some(PlayerEvent::Looped),
                "failed" => Some(PlayerEvent::TrackFailed(
                    event.message.unwrap_or_else(|| "Playback failed".into()),
                )),
                "audioDeviceLost" => Some(PlayerEvent::AudioDeviceLost),
                "lookaheadFailed" => Some(PlayerEvent::LookaheadFailed(
                    event.message.unwrap_or_else(|| "Couldn't preload the next track".into()),
                )),
                "next" => Some(PlayerEvent::Next),
                "previous" => Some(PlayerEvent::Previous),
                "like" => Some(PlayerEvent::LikeToggled),
                _ => None,
            };
            if let Some(event) = event {
                let _ = self.shared.event_tx.send(event);
            }
        }

        let position = (snapshot.position_ms.max(0) as f64) / 1000.0;
        self.shared.position_bits.store(position.to_bits(), Ordering::Relaxed);
        let _ = self.shared.event_tx.send(PlayerEvent::Position(position));

        if snapshot.duration_ms != self.shared.last_duration_ms.swap(snapshot.duration_ms, Ordering::Relaxed) {
            let _ = self.shared.event_tx.send(PlayerEvent::Duration(
                (snapshot.duration_ms.max(0) as f64) / 1000.0,
            ));
        }

        if let Ok(mut last) = self.shared.last_playing.lock() {
            if *last != Some(snapshot.is_playing) {
                *last = Some(snapshot.is_playing);
                let _ = self.shared.event_tx.send(PlayerEvent::Playing(snapshot.is_playing));
            }
        }

        if let Ok(mut last) = self.shared.last_paused.lock() {
            if *last != Some(!snapshot.play_when_ready) {
                *last = Some(!snapshot.play_when_ready);
                let _ = self.shared.event_tx.send(PlayerEvent::Paused(!snapshot.play_when_ready));
            }
        }

    }

    pub fn current_position(&self) -> f64 {
        f64::from_bits(self.shared.position_bits.load(Ordering::Relaxed))
    }

    pub fn error(&self, message: String) {
        let _ = self.shared.event_tx.send(PlayerEvent::Error(message));
    }
}
