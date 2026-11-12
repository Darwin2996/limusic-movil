//! Plays made on the account's other devices, added to this device's listening history so the
//! stats page covers them. The stats are counted per device (`AppState::hear`); the desktop app
//! doesn't send its counts anywhere this one could read, so they are rebuilt from what it does
//! leave behind, in one of two places:
//!
//! - **ListenBrainz**, when it is connected here and on the other device. Every play is a listen
//!   there with its time, its length and the YouTube id it was played from, so the import is
//!   exact, and reaches back to the first listen the account has.
//! - **The YouTube Music history** otherwise. It only lists what was played lately, newest first
//!   and each song once, under a date bucket and with no times: what is new since the last look
//!   is counted on its bucket's day. An approximation, but one that needs nothing set up.
//!
//! Either way a play this device already counted is skipped (`Db::import_listen`), and the time
//! heard is taken to be the song's length: nothing says how much of it was really heard.

use std::time::Duration;

use innertube::SongItem;

use crate::db::Db;

const LB_ROOT: &str = "https://api.listenbrainz.org";
const USER_AGENT: &str =
    concat!("Limusic/", env!("CARGO_PKG_VERSION"), " (https://github.com/Darwin2996/limusic-movil)");
/// ListenBrainz's own cap on one page of listens.
const LB_PAGE: usize = 1000;
/// Pages per run, so a first import of a long history is spread over a few runs.
const LB_MAX_PAGES: usize = 10;
/// A song with no known length counts as this long.
const DEFAULT_SECS: i64 = 180;
/// How much of the history's head is remembered between looks.
const HISTORY_KEPT: usize = 300;

/// "3:07" or "1:02:03" as seconds.
fn length_secs(duration: Option<&str>) -> Option<i64> {
    let secs = duration?.split(':').try_fold(0i64, |n, part| Some(n * 60 + part.parse::<i64>().ok()?))?;
    (secs > 0).then_some(secs)
}

fn clock(secs: i64) -> String {
    format!("{}:{:02}", secs / 60, secs % 60)
}

/// The `v=` of a YouTube or YouTube Music watch link.
fn video_id(url: &str) -> Option<&str> {
    let id = url.split(['?', '&']).find_map(|p| p.strip_prefix("v="))?;
    (id.len() == 11).then_some(id)
}

/// One ListenBrainz listen as `(video id, song, played at, secs)`, or None for a listen that
/// didn't come from YouTube: there is no song here to count it against.
fn listen(l: &serde_json::Value) -> Option<(String, SongItem, i64, i64)> {
    let at = l.get("listened_at")?.as_i64()?;
    let meta = l.get("track_metadata")?;
    let info = meta.get("additional_info")?;
    let id = video_id(info.get("origin_url")?.as_str()?)?.to_string();
    let secs = info.get("duration_ms").and_then(|d| d.as_i64()).map(|ms| ms / 1000).filter(|s| *s > 0);
    let song: SongItem = serde_json::from_value(serde_json::json!({
        "video_id": id,
        "title": meta.get("track_name")?.as_str()?,
        "artists": meta.get("artist_name").and_then(|a| a.as_str()).unwrap_or_default(),
        "album": meta.get("release_name").and_then(|a| a.as_str()),
        "duration": secs.map(clock),
        "thumbnail": format!("https://i.ytimg.com/vi/{id}/mqdefault.jpg"),
    }))
    .ok()?;
    Some((id, song, at, secs.unwrap_or(DEFAULT_SECS)))
}

/// Import the listens `user` has on ListenBrainz since the last run. Returns how many were new.
pub async fn from_listenbrainz(db: &Db, user: &str, token: Option<&str>) -> Result<usize, String> {
    let mut since: i64 =
        db.get_setting("listenbrainz_imported_to").and_then(|s| s.parse().ok()).unwrap_or(0);
    let mut added = 0;
    for _ in 0..LB_MAX_PAGES {
        // With `min_ts`, a page is the listens just after it: the cursor walks forward in time.
        let mut req = crate::http::client()
            .get(format!("{LB_ROOT}/1/user/{user}/listens"))
            .query(&[("min_ts", since.max(1).to_string()), ("count", LB_PAGE.to_string())])
            .header("User-Agent", USER_AGENT)
            .timeout(Duration::from_secs(20));
        if let Some(token) = token {
            req = req.header("Authorization", format!("Token {token}"));
        }
        let resp = req.send().await.map_err(|e| e.to_string())?;
        if !resp.status().is_success() {
            return Err(format!("ListenBrainz: {}", resp.status()));
        }
        let body: serde_json::Value = resp.json().await.map_err(|e| e.to_string())?;
        let listens = body["payload"]["listens"].as_array().cloned().unwrap_or_default();
        let Some(newest) = listens.iter().filter_map(|l| l["listened_at"].as_i64()).max() else {
            break;
        };
        for (id, song, at, secs) in listens.iter().filter_map(listen) {
            let Ok(json) = serde_json::to_string(&song) else { continue };
            // A listen is stamped when the song starts and this device counts it some way in, so
            // the same play is up to a song's length apart in the two.
            if db.import_listen(&id, &json, at, secs, "listenbrainz", secs + 120) {
                added += 1;
            }
        }
        if newest <= since {
            break;
        }
        since = newest;
        db.set_setting("listenbrainz_imported_to", &since.to_string());
        if listens.len() < LB_PAGE {
            break;
        }
    }
    Ok(added)
}

/// What is new at the head of `history` (newest first) since `seen`, the head as it was last time.
/// A song that was already there but has moved up was played again. The walk stops where the old
/// history resumes: at the old head, followed by what followed it then.
fn new_plays<'a>(history: &'a [SongItem], seen: &[String]) -> Vec<&'a SongItem> {
    let mut out: Vec<&SongItem> = Vec::new();
    // The old history without the songs found replayed so far: they left their old slots.
    let old = |out: &[&SongItem]| -> Vec<&String> {
        seen.iter().filter(|id| !out.iter().any(|s| s.video_id == **id)).collect()
    };
    for (i, song) in history.iter().enumerate() {
        let rest = old(&out);
        if rest.first().is_some_and(|id| **id == song.video_id) {
            // The old head, unless only it moved: then what follows is not what followed it.
            let follows = match (history.get(i + 1), rest.get(1)) {
                (Some(next), Some(then)) => next.video_id == **then,
                _ => true,
            };
            if follows {
                break;
            }
        }
        out.push(song);
    }
    out
}

/// The date bucket a history row sits under. YouTube Music words them in the account's language;
/// the ones that can't be told apart here are `Other`, dated by when they were first seen instead.
#[derive(Clone, Copy, PartialEq)]
enum Bucket {
    Today,
    Yesterday,
    ThisWeek,
    LastWeek,
    Other,
}

fn bucket(title: &str) -> Bucket {
    match title.trim().to_lowercase().as_str() {
        "today" | "hoy" => Bucket::Today,
        "yesterday" | "ayer" => Bucket::Yesterday,
        "this week" | "esta semana" => Bucket::ThisWeek,
        "last week" | "la semana pasada" => Bucket::LastWeek,
        _ => Bucket::Other,
    }
}

const DAY: i64 = 86_400;
/// Plays dated only to a day are laid out from this hour of it, one after the other. The day is
/// real; the hour is a guess, since the history has none.
const ASSUMED_START: i64 = 16 * 3600;

/// Import what the YouTube Music history shows as played on other devices. Returns how many plays
/// were new.
///
/// The history is newest first, each song once, under date buckets. What is new at its head since
/// the last look was played since then; the bucket says which day. The first look has no "since",
/// so it takes the last two weeks' buckets as they stand and leaves the older months alone: a song
/// listed under a month says too little about when, or how often.
pub fn from_history(db: &Db, groups: &[innertube::HistoryGroup], now: i64) -> usize {
    let history: Vec<(&SongItem, Bucket)> = groups
        .iter()
        .flat_map(|g| g.items.iter().map(move |s| (s, bucket(&g.title))))
        .collect();
    let songs: Vec<SongItem> = history.iter().map(|(s, _)| (*s).clone()).collect();
    let seen: Option<Vec<String>> =
        db.get_setting("history_seen_v2").and_then(|s| serde_json::from_str(&s).ok());
    let last = db.get_setting("history_seen_at_v2").and_then(|s| s.parse::<i64>().ok());
    let head: Vec<&str> = songs.iter().take(HISTORY_KEPT).map(|s| s.video_id.as_str()).collect();
    db.set_setting("history_seen_v2", &serde_json::to_string(&head).unwrap_or_default());
    db.set_setting("history_seen_at_v2", &now.to_string());

    let fresh: Vec<(&SongItem, Bucket)> = match &seen {
        Some(seen) => {
            let n = new_plays(&songs, seen).len();
            history[..n].to_vec()
        }
        None => history.iter().filter(|(_, b)| *b != Bucket::Other).copied().collect(),
    };
    let today = db.local_day_start(now);
    let in_bucket = |b: Bucket| fresh.iter().filter(|(_, x)| *x == b).count().max(1) as i64;
    let (this_week, last_week) = (in_bucket(Bucket::ThisWeek), in_bucket(Bucket::LastWeek));

    // Since the last look, back from now: for today's plays and the ones no bucket dates.
    let mut recent = now;
    // How far into each assumed day the plays laid out so far reach.
    let mut filled: std::collections::HashMap<i64, i64> = std::collections::HashMap::new();
    let mut nth = [0i64; 2];
    let mut added = 0;
    for (song, kind) in fresh {
        let secs = length_secs(song.duration.as_deref()).unwrap_or(DEFAULT_SECS);
        let mut on_day = |day: i64| {
            let at = filled.entry(day).or_insert(ASSUMED_START);
            let start = day + *at;
            *at += secs;
            start
        };
        // `window`: how far from `at` a play this device counted is taken to be this same one.
        let (at, window) = match kind {
            Bucket::Today | Bucket::Other => {
                let floor = match (kind, last) {
                    (Bucket::Today, Some(last)) => last.max(today),
                    (Bucket::Today, None) => today,
                    (_, Some(last)) => last,
                    (_, None) => continue,
                };
                recent = (recent - secs).max(floor);
                (recent, now - floor + 600)
            }
            Bucket::Yesterday => (on_day(today - DAY), DAY),
            // Newest first across the days the bucket spans: 2 to 6 days ago, then 7 to 13.
            Bucket::ThisWeek => {
                let day = today - (2 + nth[0] * 5 / this_week) * DAY;
                nth[0] += 1;
                (on_day(day), 3 * DAY)
            }
            Bucket::LastWeek => {
                let day = today - (7 + nth[1] * 7 / last_week) * DAY;
                nth[1] += 1;
                (on_day(day), 4 * DAY)
            }
        };
        let Ok(json) = serde_json::to_string(song) else { continue };
        if db.import_listen(&song.video_id, &json, at, secs, "history", window) {
            added += 1;
        }
    }
    added
}

#[cfg(test)]
mod tests {
    use super::*;

    fn song(id: &str) -> SongItem {
        serde_json::from_value(serde_json::json!({
            "video_id": id, "title": id, "artists": "", "duration": "3:00"
        }))
        .unwrap()
    }
    fn ids(songs: &[&SongItem]) -> Vec<String> {
        songs.iter().map(|s| s.video_id.clone()).collect()
    }
    fn seen(ids: &[&str]) -> Vec<String> {
        ids.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn new_plays_are_what_sits_above_the_old_history() {
        let history: Vec<SongItem> = ["x", "y", "a", "b", "c"].map(song).into();
        assert_eq!(ids(&new_plays(&history, &seen(&["a", "b", "c"]))), ["x", "y"]);
        // Nothing played since.
        assert!(new_plays(&history, &seen(&["x", "y", "a", "b", "c"])).is_empty());
        // A song from the old history played again moves to the top, and counts.
        let history: Vec<SongItem> = ["c", "x", "a", "b"].map(song).into();
        assert_eq!(ids(&new_plays(&history, &seen(&["a", "b", "c"]))), ["c", "x"]);
        // So does the old head played again, after something else.
        let history: Vec<SongItem> = ["a", "x", "b", "c"].map(song).into();
        assert_eq!(ids(&new_plays(&history, &seen(&["a", "b", "c"]))), ["a", "x"]);
    }

    #[test]
    fn a_listenbrainz_listen_is_read_with_its_youtube_id_and_length() {
        let l = serde_json::json!({
            "listened_at": 1_700_000_000,
            "track_metadata": {
                "track_name": "Song", "artist_name": "Artist", "release_name": "Album",
                "additional_info": {
                    "origin_url": "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                    "duration_ms": 187_000
                }
            }
        });
        let (id, song, at, secs) = listen(&l).unwrap();
        assert_eq!((id.as_str(), at, secs), ("dQw4w9WgXcQ", 1_700_000_000, 187));
        assert_eq!(song.duration.as_deref(), Some("3:07"));
        assert_eq!(song.album.as_deref(), Some("Album"));
        // A listen from another player has no YouTube link, and is left out.
        let other = serde_json::json!({
            "listened_at": 1, "track_metadata": { "track_name": "S", "additional_info": {} }
        });
        assert!(listen(&other).is_none());
    }

    fn group(title: &str, ids: &[&str]) -> innertube::HistoryGroup {
        innertube::HistoryGroup { title: title.into(), items: ids.iter().map(|id| song(id)).collect() }
    }

    #[test]
    fn the_first_look_takes_the_recent_buckets_and_later_looks_only_what_is_new() {
        let db = Db::open(std::path::Path::new(":memory:")).unwrap();
        let now = 1_800_000_000;
        // Yesterday's two are imported, dated yesterday; the month's song is left alone.
        let before = [group("Ayer", &["a", "b"]), group("Octubre de 2026", &["old"])];
        assert_eq!(from_history(&db, &before, now), 2);
        let l = db.listening(0, now + 10);
        assert_eq!(l.songs.len(), 2);
        let today = db.local_day_start(now);
        assert!(l.first_at.is_some_and(|at| at < today && at >= today - DAY));
        // Played here since: it is in the history, and already counted.
        db.record_listen("y", "{}", now + 500, 180);
        let after =
            [group("Hoy", &["x", "y"]), group("Ayer", &["a", "b"]), group("Octubre de 2026", &["old"])];
        assert_eq!(from_history(&db, &after, now + 1_000), 1);
        assert_eq!(from_history(&db, &after, now + 2_000), 0);
        assert_eq!(db.listening(0, now + 3_000).songs.len(), 4);
    }
}
