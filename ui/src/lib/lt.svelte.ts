// Reactive Listen Together state, driven by the Rust `lt-state` event. The modal + home-header
// button read this; mutations go through `api` commands. context/19.
import type { LtState } from './api';

export const lt = $state<LtState>({
	status: 'disconnected',
	role: 'none',
	requesting: false,
	roomCode: null,
	myId: null,
	serverUrl: '',
	defaultServerUrl: '',
	users: [],
	currentTrack: null,
	queue: [],
	pendingJoins: [],
	suggestions: []
});

/** True when we're in (or joining) a room. */
export function inRoom(): boolean {
	return lt.role !== 'none';
}

/** Replace the reactive state from a fresh `lt-state` snapshot. */
export function applyLtState(s: LtState) {
	Object.assign(lt, s);
}

// An invite bundles the server + code so a guest only pastes one thing: `<code>@<host>`.
// `wss://` and the `/ws` path are implied, so the usual invite stays short and typable.
// An empty server is the built-in default, which the backend fills in, so that invite is the
// bare code: a host on the default never shares a hostname, and a guest never types one.
export function makeInvite(server: string, code: string): string {
	if (!server) return code;
	return code + '@' + server.replace(/^wss:\/\//, '').replace(/\/ws$/, '');
}
export function parseInvite(raw: string): { server: string; code: string } | null {
	const s = raw.trim();
	// Invites minted before 0.6.5: `LMSC~<base64(server|code)>`.
	if (s.startsWith('LMSC~')) {
		try {
			const [server, code] = atob(s.slice(5)).split('|');
			return { server: server ?? '', code: (code ?? '').toUpperCase() };
		} catch {
			return null;
		}
	}
	const at = s.lastIndexOf('@');
	// A bare room code: reuse whatever server we last connected to.
	if (at < 0) return { server: '', code: s.toUpperCase() };
	let server = s.slice(at + 1);
	if (!/^wss?:\/\//.test(server)) server = 'wss://' + server;
	if (!server.replace(/^wss?:\/\//, '').includes('/')) server += '/ws';
	return { server, code: s.slice(0, at).toUpperCase() };
}
