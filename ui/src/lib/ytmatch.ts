// Find the YouTube Music song behind a music file on this device, so a playlist made from local music
// can live on the account: playable anywhere, with YouTube's suggestions, and in Listen Together,
// none of which a file on one phone can be. Each file is looked up by its title and artist and the
// closest song result is taken, or none when nothing is close enough: a wrong song is worse than a
// missing one.
import * as api from './api';
import type { SongItem } from './api';

/** Lower case, accents and punctuation gone, so "Canción (Remastered)" and "cancion remastered"
 *  compare equal word for word. */
function words(text: string): string[] {
	return text
		.toLowerCase()
		.normalize('NFD')
		.replace(/[̀-ͯ]/g, '')
		.replace(/[^\p{L}\p{N}]+/gu, ' ')
		.trim()
		.split(' ')
		.filter(Boolean);
}

/** What a file's title usually carries besides the song's name: a track number, the extension, and
 *  the "(Official Video)" kind of tag a download keeps. */
function cleanTitle(title: string): string {
	return title
		.replace(/\.(mp3|m4a|flac|ogg|opus|wav|aac|wma)$/i, '')
		.replace(/^\s*\d{1,3}\s*[-._)]\s*/, '')
		.replace(/[([](official|video|audio|lyrics?|letra|hd|hq|4k|visualizer)[^)\]]*[)\]]/gi, '')
		.replace(/_/g, ' ')
		.trim();
}

/** Share of `a`'s words found in `b`. */
function overlap(a: string[], b: string[]): number {
	if (!a.length) return 0;
	const set = new Set(b);
	return a.filter((w) => set.has(w)).length / a.length;
}

const seconds = (d?: string) =>
	d ? d.split(':').reduce((total, part) => total * 60 + (Number(part) || 0), 0) : 0;

/** Placeholder artists a file without tags gets: searching by them only adds noise. */
const NO_ARTIST = /^(unknown|desconocido|<unknown>|various artists?)$/i;

/** The YouTube Music song for one local file, or null when no result is a close enough match. */
export async function findOnYouTube(song: SongItem): Promise<SongItem | null> {
	const title = cleanTitle(song.title);
	const artist = NO_ARTIST.test(song.artists.trim()) ? '' : song.artists.trim();
	const results = await api.search(`${title} ${artist}`.trim());
	const want = words(title);
	const wantArtist = words(artist);
	const length = seconds(song.duration);
	let best: { song: SongItem; score: number } | null = null;
	for (const candidate of results.slice(0, 6)) {
		if (!candidate.video_id || api.isLocalId(candidate.video_id)) continue;
		const titleScore = Math.max(overlap(want, words(candidate.title)), overlap(words(candidate.title), want));
		if (titleScore < 0.6) continue;
		const artistScore = wantArtist.length ? overlap(wantArtist, words(candidate.artists)) : 0;
		const gap = length && seconds(candidate.duration) ? Math.abs(length - seconds(candidate.duration)) : null;
		// Same name is not the same song (covers, other artists' songs of that name): it also has to
		// be by the file's artist or last as long, whichever of the two the file can tell.
		const confirmed = artistScore >= 0.5 || (gap !== null && gap <= 5) || (!wantArtist.length && !length);
		if (!confirmed || (gap !== null && gap > 30)) continue;
		const score = titleScore + artistScore * 0.8 + (gap !== null && gap <= 5 ? 0.4 : 0);
		if (!best || score > best.score) best = { song: candidate, score };
	}
	return best?.song ?? null;
}

/**
 * Every song of `songs` as a YouTube song: tracks already on YouTube stay as they are, local files
 * are looked up a few at a time. Keeps the order, and reports progress as it goes. `cancelled` stops
 * it between lookups.
 */
export async function matchOnYouTube(
	songs: SongItem[],
	progress: (done: number, total: number) => void,
	cancelled: () => boolean = () => false
): Promise<{ found: SongItem[]; missing: SongItem[] }> {
	const out: (SongItem | null)[] = new Array(songs.length).fill(null);
	let next = 0;
	let done = 0;
	progress(0, songs.length);
	async function worker() {
		while (next < songs.length && !cancelled()) {
			const i = next++;
			const song = songs[i];
			out[i] = api.isLocalId(song.video_id) ? await findOnYouTube(song).catch(() => null) : song;
			progress(++done, songs.length);
		}
	}
	await Promise.all(Array.from({ length: 4 }, worker));
	const seen = new Set<string>();
	const found: SongItem[] = [];
	const missing: SongItem[] = [];
	songs.forEach((song, i) => {
		const match = out[i];
		if (!match) missing.push(song);
		// Two files of the same song (an mp3 and a flac) would otherwise go in twice.
		else if (!seen.has(match.video_id)) {
			seen.add(match.video_id);
			found.push(match);
		}
	});
	return { found, missing };
}
