<script lang="ts" module>
	import type { IconName } from './Icon.svelte';

	export interface MenuItem {
		label: string;
		icon: IconName;
		onselect: () => void;
	}
</script>

<script lang="ts">
	import { page } from '$app/state';
	import Icon from './Icon.svelte';
	import IconButton from './IconButton.svelte';

	/** An icon button that opens a small popover menu of actions (the group's "More" menu). */
	interface Props {
		icon: IconName;
		/** The trigger's accessible name, e.g. "More". */
		label: string;
		items: MenuItem[];
		variant?: 'glass' | 'surface' | 'plain';
	}
	let { icon, label, items, variant = 'glass' }: Props = $props();

	let open = $state(false);
	let root: HTMLElement | undefined = $state();
	let buttons: HTMLElement[] = $state([]);

	// Close when navigating or clicking elsewhere.
	$effect(() => {
		void page.url.pathname;
		open = false;
	});
	$effect(() => {
		if (!open) return;
		const onPointer = (e: PointerEvent) => {
			if (root && !root.contains(e.target as Node)) open = false;
		};
		document.addEventListener('pointerdown', onPointer);
		return () => document.removeEventListener('pointerdown', onPointer);
	});

	function toggle() {
		open = !open;
		if (open) queueMicrotask(() => buttons[0]?.focus());
	}

	function select(item: MenuItem) {
		open = false;
		item.onselect();
	}

	function onkeydown(e: KeyboardEvent) {
		if (!open) return;
		const i = buttons.indexOf(document.activeElement as HTMLElement);
		if (e.key === 'Escape') {
			open = false;
			root?.querySelector<HTMLElement>('.icon-btn')?.focus();
		} else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			const next = (i + (e.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
			buttons[next]?.focus();
		}
	}
</script>

<div class="menu-root" bind:this={root} role="presentation" {onkeydown}>
	<IconButton {icon} {label} {variant} aria-haspopup="menu" aria-expanded={open} onclick={toggle} />
	{#if open}
		<div class="menu glass" role="menu" aria-label={label}>
			{#each items as item, i (item.label)}
				<button bind:this={buttons[i]} type="button" role="menuitem" onclick={() => select(item)}>
					<Icon name={item.icon} size={18} />{item.label}
				</button>
			{/each}
		</div>
	{/if}
</div>

<style>
	.menu-root {
		position: relative;
	}
	.menu {
		position: absolute;
		right: 0;
		top: calc(100% + 8px);
		z-index: 60;
		min-width: 220px;
		border-radius: 22px;
		padding: 8px;
		display: flex;
		flex-direction: column;
		gap: 2px;
		background: color-mix(in srgb, var(--bg) 82%, transparent);
		animation: pop 0.2s var(--ease-out) both;
	}
	button {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 44px;
		padding: 0 12px;
		border-radius: 14px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
		text-align: left;
		white-space: nowrap;
	}
	button:hover,
	button:focus-visible {
		background: var(--surface);
	}
	@keyframes pop {
		from {
			transform: translateY(-6px) scale(0.98);
			opacity: 0;
		}
	}
</style>
