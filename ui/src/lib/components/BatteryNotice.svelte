<script lang="ts">
	// Android only, asked once: skins like MIUI kill a background app to save battery, which stops
	// the music. Settings ▸ Playback keeps the same switch for later.
	import { onMount } from 'svelte';
	import * as Dialog from '$lib/components/ui/dialog';
	import { Button } from '$lib/components/ui/button';
	import { androidBatteryExempt, requestAndroidBatteryExemption } from '$lib/api';
	import { t } from '$lib/i18n.svelte';

	const ASKED = 'battery_notice_asked';
	let open = $state(false);

	function asked(): boolean {
		try {
			return localStorage.getItem(ASKED) === '1';
		} catch {
			return true; // no storage: never nag on every launch
		}
	}
	function remember() {
		try {
			localStorage.setItem(ASKED, '1');
		} catch {
			/* a blocked store only means it may ask again */
		}
	}

	onMount(() => {
		if (asked()) return;
		androidBatteryExempt()
			.then((exempt) => (open = !exempt))
			.catch(() => {});
	});

	function close() {
		remember();
		open = false;
	}

	async function allow() {
		close();
		await requestAndroidBatteryExemption().catch(() => {});
	}
</script>

<Dialog.Root bind:open onOpenChange={(o) => !o && remember()}>
	<Dialog.Content class="sm:max-w-md">
		<Dialog.Header>
			<Dialog.Title>{t('battery_notice.title')}</Dialog.Title>
			<Dialog.Description>{t('battery_notice.body')}</Dialog.Description>
		</Dialog.Header>
		<div class="flex justify-end gap-2">
			<Button variant="ghost" class="min-h-11" onclick={close}>{t('battery_notice.later')}</Button>
			<Button class="min-h-11" onclick={allow}>{t('battery_notice.allow')}</Button>
		</div>
	</Dialog.Content>
</Dialog.Root>
