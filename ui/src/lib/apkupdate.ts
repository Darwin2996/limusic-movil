// Android: a newer APK on the mobile port's GitHub releases. The desktop updater
// (updater.svelte.ts) only knows desktop builds, so it stays off on Android (+layout.svelte) and
// this takes its place. Read-only and anonymous: GitHub's public API, no token, so the repository
// has to be public. Until it exists or has a release, the answer is simply "nothing new".
import { getVersion } from '@tauri-apps/api/app';

const REPO = 'Darwin2996/limusic-movil';
const DISMISSED = 'apk_update_dismissed';

/** `url` is the release page; `apk` the APK attached to it, when there is one. */
export type ApkRelease = { version: string; url: string; apk: string | null };

/** The latest release if it is newer than this build and not dismissed, else null. */
export async function checkApkUpdate(): Promise<ApkRelease | null> {
	try {
		const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
			headers: { Accept: 'application/vnd.github+json' }
		});
		if (!res.ok) return null; // no repository, or no release yet
		const release = (await res.json()) as {
			tag_name?: string;
			html_url?: string;
			assets?: { name: string; browser_download_url: string }[];
		};
		const version = release.tag_name?.replace(/^v/, '');
		if (!version || !release.html_url) return null;
		if (!isNewer(version, await getVersion()) || dismissed() === version) return null;
		const apk = release.assets?.find((a) => a.name.endsWith('.apk'))?.browser_download_url ?? null;
		return { version, url: release.html_url, apk };
	} catch {
		return null; // offline: ask again next launch
	}
}

/** "Not now" for this version: the next release is announced again. */
export function dismissApkUpdate(version: string) {
	try {
		localStorage.setItem(DISMISSED, version);
	} catch {
		/* a blocked store only means it may be announced again */
	}
}

function dismissed(): string | null {
	try {
		return localStorage.getItem(DISMISSED);
	} catch {
		return null;
	}
}

/** Semver order, prerelease included: 1.4.0 > 1.4.0-rc.2 > 1.4.0-rc.1 > 1.3.9. */
export function isNewer(candidate: string, current: string): boolean {
	const parse = (v: string) => {
		const [core, pre] = v.split('-', 2);
		return { nums: core.split('.').map((n) => Number(n) || 0), pre: pre ?? null };
	};
	const a = parse(candidate);
	const b = parse(current);
	for (let i = 0; i < 3; i++) {
		const d = (a.nums[i] ?? 0) - (b.nums[i] ?? 0);
		if (d) return d > 0;
	}
	if (a.pre === b.pre) return false;
	if (a.pre === null) return true; // a release beats its own prereleases
	if (b.pre === null) return false;
	return a.pre.localeCompare(b.pre, undefined, { numeric: true }) > 0;
}
