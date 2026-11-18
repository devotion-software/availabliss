<script lang="ts" module>
	export interface MessageMenuAnchor {
		x: number;
		y: number;
		/** Opened from the keyboard: focus the first item (otherwise the menu itself, without a focus ring). */
		keyboard?: boolean;
	}
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { portal } from '#lib/components/ui/portal';

	/**
	 * Reply / Info, and Edit / Delete on your own messages: opened by long-press, right-click or the bubble's ⋯ button.
	 * Floats at the pointer (or the button), kept inside the viewport. Same look as the group's More menu.
	 */
	interface Props {
		/** Where to open it; null when closed. */
		anchor: MessageMenuAnchor | null;
		oninfo: () => void;
		/** Each is left out of the menu when not given. */
		onreply?: () => void;
		onedit?: () => void;
		ondelete?: () => void;
		onclose: () => void;
	}
	let { anchor, oninfo, onreply, onedit, ondelete, onclose }: Props = $props();

	type Item = { label: string; icon: 'reply' | 'info' | 'pencil' | 'trash'; run: () => void; danger?: boolean };
	const items = $derived(
		[
			onreply && { label: 'Reply', icon: 'reply', run: onreply },
			{ label: 'Info', icon: 'info', run: oninfo },
			onedit && { label: 'Edit', icon: 'pencil', run: onedit },
			ondelete && { label: 'Delete', icon: 'trash', run: ondelete, danger: true }
		].filter(Boolean) as Item[]
	);

	let el: HTMLElement | undefined = $state();
	let pos = $state({ left: 0, top: 0 });
	let buttons: HTMLElement[] = $state([]);
	let returnFocus: HTMLElement | null = null;

	// Place it beside the anchor (to the left, since your bubbles sit on the right), inside the viewport.
	$effect(() => {
		if (!anchor || !el) return;
		const { width, height } = el.getBoundingClientRect();
		const pad = 8;
		const vw = window.innerWidth;
		const vh = window.innerHeight;
		let left = anchor.x - width;
		if (left < pad) left = Math.min(anchor.x, vw - width - pad);
		let top = anchor.y + 4;
		if (top + height > vh - pad) top = Math.max(pad, anchor.y - height - 4);
		pos = { left: Math.max(pad, left), top };
	});

	// Focus the first item; close on outside press, scroll or resize; give focus back after.
	$effect(() => {
		if (!anchor) return;
		returnFocus = document.activeElement as HTMLElement | null;
		const keyboard = anchor.keyboard;
		tick().then(() => (keyboard ? buttons[0] : el)?.focus({ preventScroll: true }));
		const onPointer = (e: PointerEvent) => {
			if (el && !el.contains(e.target as Node)) onclose();
		};
		const onScroll = (e: Event) => {
			if (!el?.contains(e.target as Node)) onclose();
		};
		const onResize = () => onclose();
		document.addEventListener('pointerdown', onPointer, true);
		document.addEventListener('scroll', onScroll, true);
		window.addEventListener('resize', onResize);
		return () => {
			document.removeEventListener('pointerdown', onPointer, true);
			document.removeEventListener('scroll', onScroll, true);
			window.removeEventListener('resize', onResize);
		};
	});

	/** Closes, hands focus back to what opened it (so sheets restore it there too), then acts. */
	function choose(action: () => void) {
		const back = returnFocus;
		onclose();
		if (back?.isConnected && back !== document.body) back.focus({ preventScroll: true });
		action();
	}

	function onkeydown(e: KeyboardEvent) {
		// The menu's items change with the message; drop slots left from a longer one.
		const live = buttons.slice(0, items.length);
		const i = live.indexOf(document.activeElement as HTMLElement);
		if (e.key === 'Escape' || e.key === 'Tab') {
			e.preventDefault();
			e.stopPropagation();
			choose(() => {});
		} else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			const n = live.length;
			const next = i < 0 ? (e.key === 'ArrowDown' ? 0 : n - 1) : (i + (e.key === 'ArrowDown' ? 1 : -1) + n) % n;
			live[next]?.focus();
		}
	}
</script>

{#if anchor}
	<div
		class="menu glass"
		role="menu"
		aria-label="Message actions"
		tabindex="-1"
		bind:this={el}
		style:left="{pos.left}px"
		style:top="{pos.top}px"
		use:portal
		{onkeydown}
	>
		{#each items as item, i (item.label)}
			<button bind:this={buttons[i]} type="button" role="menuitem" class:danger={item.danger} onclick={() => choose(item.run)}>
				<Icon name={item.icon} size={18} />{item.label}
			</button>
		{/each}
	</div>
{/if}

<style>
	.menu {
		position: fixed;
		z-index: 90;
		min-width: 180px;
		border-radius: 22px;
		padding: 8px;
		display: flex;
		flex-direction: column;
		gap: 2px;
		background: color-mix(in srgb, var(--bg) 82%, transparent);
		animation: pop 0.18s var(--ease-out) both;
	}
	.menu:focus {
		outline: none;
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
	.danger,
	.danger:hover {
		color: var(--warn-ink);
	}
	.danger:hover,
	.danger:focus-visible {
		background: var(--warn-bg);
	}
	@keyframes pop {
		from {
			transform: scale(0.96);
			opacity: 0;
		}
	}
</style>
