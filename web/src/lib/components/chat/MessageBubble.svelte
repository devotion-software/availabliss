<script lang="ts">
	import type { Member, Message } from '#lib/types';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import type { MessageMenuAnchor } from './MessageMenu.svelte';

	/**
	 * A text message. Others' are glass with an avatar; yours are solid. A tap opens message info.
	 * Yours (when `canModify`) also open an Info / Edit / Delete menu: long-press, right-click or the ⋯ button.
	 * Deleted messages are a dashed outline with no actions.
	 */
	interface Props {
		message: Message;
		mine: boolean;
		author?: Member;
		/** First in a run from the same person: show their name. */
		first?: boolean;
		/** Last in a run: show the avatar. */
		last?: boolean;
		compact?: boolean;
		/** Show "Edited" under the bubble (off when the read receipt line says it instead). */
		showEdited?: boolean;
		/** Your own text message that can still be edited or deleted. */
		canModify?: boolean;
		oninfo?: (message: Message) => void;
		onmenu?: (message: Message, anchor: MessageMenuAnchor) => void;
	}
	let {
		message,
		mine,
		author,
		first = true,
		last = true,
		compact = false,
		showEdited = true,
		canModify = false,
		oninfo,
		onmenu
	}: Props = $props();

	const pending = $derived(message.id.startsWith('temp-'));
	const name = $derived(author?.name ?? 'Someone');
	const edited = $derived(!!message.editedAt && !message.deleted && showEdited);

	// Touch long-press opens the menu (iOS Safari has no contextmenu event for it); the click that follows is swallowed.
	const LONG_PRESS_MS = 450;
	let pressTimer: ReturnType<typeof setTimeout> | undefined;
	let pressStart: { x: number; y: number } | null = null;
	let suppressClick = false;

	function openMenu(anchor: MessageMenuAnchor) {
		if (canModify) onmenu?.(message, anchor);
	}

	function onpointerdown(e: PointerEvent) {
		suppressClick = false;
		if (!canModify || e.pointerType === 'mouse') return;
		pressStart = { x: e.clientX, y: e.clientY };
		clearTimeout(pressTimer);
		pressTimer = setTimeout(() => {
			suppressClick = true;
			navigator.vibrate?.(10);
			openMenu(pressStart!);
		}, LONG_PRESS_MS);
	}
	function onpointermove(e: PointerEvent) {
		if (pressStart && Math.hypot(e.clientX - pressStart.x, e.clientY - pressStart.y) > 10) cancelPress();
	}
	function cancelPress() {
		clearTimeout(pressTimer);
		pressStart = null;
	}
	function oncontextmenu(e: MouseEvent) {
		if (!canModify) return;
		e.preventDefault();
		// A touch long-press may fire this too (Android); the timer already handled it.
		if (suppressClick) return;
		cancelPress();
		// The context-menu key (or Shift+F10) has no pointer position: open by the bubble, ready for the keyboard.
		if (e.clientX === 0 && e.clientY === 0) {
			const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
			openMenu({ x: r.right, y: r.bottom, keyboard: true });
		} else openMenu({ x: e.clientX, y: e.clientY });
	}
	function onclick() {
		if (suppressClick) {
			suppressClick = false;
			return;
		}
		oninfo?.(message);
	}
	function openFromButton(e: MouseEvent) {
		const r = (e.currentTarget as HTMLElement).getBoundingClientRect();
		// detail 0: Enter or Space rather than a pointer click.
		openMenu({ x: r.right, y: r.bottom, keyboard: e.detail === 0 });
	}
</script>

{#if mine}
	<div class="mine-wrap" class:compact>
		<div class="mine-line">
			{#if message.deleted}
				<div class="bubble deleted mine" role="note" aria-label="Message deleted">You deleted this message</div>
			{:else}
				{#if canModify}
					<button type="button" class="more" aria-label="Message actions" aria-haspopup="menu" onclick={openFromButton}>
						<Icon name="more" size={18} strokeWidth={2.6} />
					</button>
				{/if}
				<button
					type="button"
					class="bubble mine"
					class:pending
					class:pressable={canModify}
					disabled={pending}
					aria-label="Your message: {message.body}{edited ? ' (edited)' : ''}. Show message info"
					{onclick}
					{onpointerdown}
					{onpointermove}
					onpointerup={cancelPress}
					onpointercancel={cancelPress}
					onpointerleave={cancelPress}
					{oncontextmenu}>{message.body}</button
				>
			{/if}
		</div>
		{#if edited}<span class="edited" aria-hidden="true">Edited</span>{/if}
	</div>
{:else}
	<div class="row" class:compact>
		<span class="avatar" class:hidden={!last}>
			<Avatar name={name} id={message.authorId ?? undefined} image={author?.image} size={compact ? 26 : 28} />
		</span>
		<div class="col">
			{#if first}<span class="name">{name.split(' ')[0]}</span>{/if}
			{#if message.deleted}
				<div class="bubble deleted theirs" role="note" aria-label="{name}: message deleted">This message was deleted</div>
			{:else}
				<button
					type="button"
					class="bubble glass theirs"
					aria-label="{name}: {message.body}{edited ? ' (edited)' : ''}. Show message info"
					onclick={() => oninfo?.(message)}>{message.body}</button
				>
				{#if edited}<span class="edited" aria-hidden="true">Edited</span>{/if}
			{/if}
		</div>
	</div>
{/if}

<style>
	.row {
		display: flex;
		gap: 8px;
		align-items: flex-end;
		max-width: min(85%, 420px);
	}
	.avatar.hidden {
		visibility: hidden;
	}
	.col {
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
	}
	.name {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
		padding-left: 4px;
	}
	.bubble {
		border-radius: 18px 18px 18px 6px;
		padding: 9px 12px;
		font-size: 14px;
		line-height: 1.4;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}
	.compact .bubble {
		font-size: 13px;
		padding: 8px 11px;
	}
	.compact .bubble.glass {
		background: var(--surface);
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		box-shadow: none;
		border-color: transparent;
	}
	.theirs {
		align-self: flex-start;
		text-align: left;
		color: inherit;
		font: inherit;
		font-size: 14px;
		cursor: pointer;
	}
	.mine-wrap {
		align-self: flex-end;
		max-width: min(80%, 400px);
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 3px;
	}
	.mine-line {
		position: relative;
		display: flex;
		justify-content: flex-end;
		min-width: 0;
		max-width: 100%;
	}
	.mine {
		min-width: 0;
		border-radius: 18px 18px 6px 18px;
		text-align: left;
		background: var(--solid);
		color: var(--on-solid);
		transition:
			opacity 0.2s ease,
			transform 0.15s ease;
	}
	.mine.pressable {
		-webkit-touch-callout: none;
		-webkit-user-select: none;
		user-select: none;
	}
	.mine:active:not(:disabled) {
		transform: scale(0.98);
	}
	.mine.pending {
		opacity: 0.6;
	}
	.bubble.deleted {
		background: transparent;
		color: var(--muted);
		font-style: italic;
		border: 1px dashed var(--line-3);
		cursor: default;
	}
	.edited {
		font-size: 11px;
		font-weight: 500;
		color: var(--muted);
		padding: 0 4px;
	}
	/* The ⋯ button: shown on hover or keyboard focus (pointer devices); reachable by keyboard and screen readers always. */
	.more {
		position: absolute;
		right: calc(100% + 4px);
		top: 50%;
		translate: 0 -50%;
		width: 32px;
		height: 32px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
		opacity: 0;
		transition:
			opacity 0.15s ease,
			background-color 0.15s ease;
	}
	.more:focus-visible {
		opacity: 1;
	}
	.more:hover {
		color: var(--ink);
		background: var(--surface);
	}
	@media (hover: hover) {
		.mine-line:hover .more {
			opacity: 1;
		}
	}
</style>
