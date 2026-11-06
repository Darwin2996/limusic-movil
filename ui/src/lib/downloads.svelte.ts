// Offline downloads (Android): which songs are saved, which are on their way, and the actions on
// them. The files and the download itself live in the backend (src-tauri/src/downloads.rs); this
// mirrors their state for the menus, the pages' Download buttons and Library ▸ Downloads.
import * as api from '$lib/api';
import type { SongItem } from '$lib/api';
import { toast } from '$lib/player.svelte';
import { t } from '$lib/i18n.svelte';

class Downloads {
	/** Saved songs, newest first. */
	songs = $state.raw<SongItem[]>([]);
	bytes = $state(0);
	/** In flight or queued: videoId → fraction done (0 until the first chunk lands). */
	pending = $state<Record<string, number>>({});

	has(videoId: string) {
		return this.songs.some((s) => s.video_id === videoId);
	}
}
export const downloads = new Downloads();

async function reload() {
	const { songs, bytes } = await api.listDownloads();
	downloads.songs = songs;
	downloads.bytes = bytes;
}

/** Load what is saved and follow the backend's events. Returns the teardown. */
export function initDownloads(): () => void {
	reload().catch(() => {});
	const listeners = [
		api.onDownloadProgress(({ videoId, received, total }) => {
			downloads.pending[videoId] = total ? received / total : 0;
		}),
		api.onDownloadDone((videoId) => {
			delete downloads.pending[videoId];
			reload().catch(() => {});
		}),
		api.onDownloadFailed((videoId, error) => {
			delete downloads.pending[videoId];
			toast.error(t('downloads.failed', { error }));
		})
	];
	return () => listeners.forEach((l) => l.then((u) => u()));
}

/** Queue songs for download, skipping what is saved or already on its way. */
export function download(songs: SongItem[]) {
	const fresh = songs.filter(
		(s) => !api.isLocalId(s.video_id) && !downloads.has(s.video_id) && !(s.video_id in downloads.pending)
	);
	if (!fresh.length) {
		toast(t('downloads.nothing_new'));
		return;
	}
	for (const s of fresh) {
		downloads.pending[s.video_id] = 0;
		api.downloadSong(s).catch(() => delete downloads.pending[s.video_id]);
	}
	toast(t('downloads.queued', { count: fresh.length }));
}

export async function removeDownload(videoId: string) {
	await api.removeDownload(videoId);
	await reload();
}
