<script lang="ts">
	import { MediaQuery } from 'svelte/reactivity';
	import type { Snippet } from 'svelte';
	import { fade, fly, scale } from 'svelte/transition';
	import { cubicOut } from 'svelte/easing';
	import IconButton from './IconButton.svelte';
	import { focusTrap } from './focus-trap';
	import { portal } from './portal';

	interface Props {
		open: boolean;
		title: string;
		/** Visible line under the title. */
		description?: string;
		/** `auto`: bottom sheet on phones, centred modal on wider screens. `center`: always a centred modal. */
		placement?: 'auto' | 'center';
		width?: number;
		onclose?: () => void;
		children: Snippet;
		footer?: Snippet;
	}
	let { open = $bindable(), title, description, placement = 'auto', width = 480, onclose, children, footer }: Props = $props();

	const uid = $props.id();
	const phone = new MediaQuery('(max-width: 639px)');
	const isPhone = $derived(phone.current);

	// Lock page scroll while open.
	$effect(() => {
		if (!open) return;
		const prev = document.documentElement.style.overflow;
		document.documentElement.style.overflow = 'hidden';
		return () => {
			document.documentElement.style.overflow = prev;
		};
	});

	const asSheet = $derived(placement === 'auto' && isPhone);

	function close() {
		open = false;
		onclose?.();
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			e.stopPropagation();
			close();
		}
	}

	function panelIn(node: Element) {
		return asSheet ? fly(node, { y: 60, duration: 320, easing: cubicOut }) : scale(node, { start: 0.96, duration: 200, easing: cubicOut });
	}
</script>

{#if open}
	<div class="overlay" class:sheet={asSheet} use:portal transition:fade={{ duration: 160 }}>
		<button class="scrim" tabindex="-1" aria-label="Close" onclick={close}></button>
		<div
			class="panel glass"
			role="dialog"
			aria-modal="true"
			aria-labelledby="sheet-title-{uid}"
			aria-describedby={description ? `sheet-desc-${uid}` : undefined}
			tabindex="-1"
			style:--w="{width}px"
			use:focusTrap
			{onkeydown}
			in:panelIn
			out:fade={{ duration: 140 }}
		>
			<header>
				<div class="titles">
					<h2 id="sheet-title-{uid}">{title}</h2>
					{#if description}<p id="sheet-desc-{uid}">{description}</p>{/if}
				</div>
				<IconButton icon="x" label="Close" variant="line" size={40} iconSize={14} onclick={close} />
			</header>
			<div class="body">{@render children()}</div>
			{#if footer}<footer>{@render footer()}</footer>{/if}
		</div>
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 100;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 24px 16px;
	}
	.overlay.sheet {
		align-items: flex-end;
		padding: 8px;
	}
	.scrim {
		position: absolute;
		inset: 0;
		background: rgba(12, 13, 29, 0.3);
		cursor: default;
	}
	.panel {
		position: relative;
		width: 100%;
		max-width: var(--w);
		max-height: calc(100dvh - 48px);
		overflow-y: auto;
		overscroll-behavior: contain;
		border-radius: var(--radius-sheet);
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		/* Sheets sit on the page colour so content stays legible over anything. */
		background: color-mix(in srgb, var(--bg) 88%, transparent);
	}
	.sheet .panel {
		max-width: none;
		max-height: calc(100dvh - 16px);
		padding-bottom: max(18px, env(safe-area-inset-bottom));
	}
	.panel:focus {
		outline: none;
	}
	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 12px;
	}
	.titles {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding-top: 4px;
	}
	h2 {
		font-size: 22px;
		font-weight: 700;
	}
	p {
		font-size: 14px;
		color: var(--muted);
	}
	.body {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	/* Stays at the bottom of a long sheet so its actions are always reachable. */
	footer {
		display: flex;
		gap: 8px;
		position: sticky;
		bottom: -18px;
		margin: 0 -18px -18px;
		padding: 12px 18px 18px;
		background: color-mix(in srgb, var(--bg) 94%, transparent);
		border-top: 1px solid var(--line);
		z-index: 1;
	}
	.sheet footer {
		bottom: calc(-1 * max(18px, env(safe-area-inset-bottom)));
		margin-bottom: calc(-1 * max(18px, env(safe-area-inset-bottom)));
		padding-bottom: max(18px, env(safe-area-inset-bottom));
	}
	footer:empty {
		display: none;
	}
	footer > :global(*) {
		flex: 1;
	}
</style>
