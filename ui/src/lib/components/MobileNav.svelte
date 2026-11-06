<script lang="ts">
	import { page } from '$app/state';
	import { HugeiconsIcon } from '@hugeicons/svelte';
	import { Home01Icon, Search01Icon, LibraryIcon, Chart02Icon } from '@hugeicons/core-free-icons';
	import MobileMore from './MobileMore.svelte';
	import { t } from '$lib/i18n.svelte';
	let { onToggleQueue, onToggleLyrics, onToggleComments } = $props<{
		onToggleQueue: () => void;
		onToggleLyrics: () => void;
		onToggleComments: () => void;
	}>();

	const links = $derived([
		{ href: '/', label: t('nav.home'), icon: Home01Icon },
		{ href: '/search', label: t('nav.search'), icon: Search01Icon },
		{ href: '/library', label: t('nav.library'), icon: LibraryIcon },
		{ href: '/stats', label: t('nav.stats'), icon: Chart02Icon }
	]);
	const isActive = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);
</script>

<nav class="mobile-nav flex shrink-0 items-stretch gap-1 border-t bg-card/95 px-2 pt-1.5 text-muted-foreground backdrop-blur md:hidden">
		{#each links as link (link.href)}
		<a
			href={link.href}
			aria-current={isActive(link.href) ? 'page' : undefined}
			class="flex min-w-0 flex-1 flex-col items-center justify-center gap-1 rounded-lg py-1 text-[0.65rem] font-medium transition-colors {isActive(link.href)
				? 'text-primary'
				: 'hover:text-foreground'}"
		>
			<HugeiconsIcon icon={link.icon} class="h-5 w-5" />
			<span class="max-w-full truncate">{link.label}</span>
		</a>
	{/each}
		<MobileMore {onToggleQueue} {onToggleLyrics} {onToggleComments} />
</nav>

<style>
	.mobile-nav {
		padding-bottom: max(env(safe-area-inset-bottom), 0.5rem);
	}
</style>
