<script lang="ts" module>
	/** The message a reply quotes: who sent it and one line of it. */
	export interface BubbleQuote {
		id: string;
		name: string;
		snippet: string;
		deleted: boolean;
	}
</script>

<script lang="ts">
	import type { Member, Message } from '#lib/types';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import type { MessageMenuAnchor } from './MessageMenu.svelte';

	/**
	 * A text message. Others' are glass with an avatar; yours are the theme accent. A tap opens message info.
	 * A menu (Reply / Info, plus Edit / Delete on yours) opens on long-press, right-click or the ⋯ button;
	 * hovering also shows a Reply button. A reply shows the message it quotes above its text: tapping that opens the thread.
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
		/** Can be replied to (sent and not deleted). */
		canReply?: boolean;
		/** The message this one replies to. */
		quote?: BubbleQuote | null;
		oninfo?: (message: Message) => void;
		onmenu?: (message: Message, anchor: MessageMenuAnchor) => void;
		onreply?: (message: Message) => void;
		onquote?: (messageId: string) => void;
		/** What tapping the quote does, for screen readers. */
		quoteHint?: string;
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
		canReply = false,
		quote = null,
		oninfo,
		onmenu,
		onreply,
		onquote,
		quoteHint = 'Show the thread'
	}: Props = $props();

	const pending = $derived(message.id.startsWith('temp-'));
	const name = $derived(author?.name ?? 'Someone');
	const edited = $derived(!!message.editedAt && !message.deleted && showEdited);
	const hasMenu = $derived(canModify || canReply);
	const quoteLabel = $derived(quote ? `, replying to ${quote.name}: ${quote.snippet}` : '');

	// Touch long-press opens the menu (iOS Safari has no contextmenu event for it); the click that follows is swallowed.
	const LONG_PRESS_MS = 450;
	let pressTimer: ReturnType<typeof setTimeout> | undefined;
	let pressStart: { x: number; y: number } | null = null;
	let suppressClick = false;

	function openMenu(anchor: MessageMenuAnchor) {
		if (hasMenu) onmenu?.(message, anchor);
	}

	function onpointerdown(e: PointerEvent) {
		suppressClick = false;
		if (!hasMenu || e.pointerType === 'mouse') return;
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
		if (!hasMenu) return;
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

{#snippet actions(side: 'left' | 'right')}
	{#if hasMenu && !pending}
		<span class="actions {side}">
			{#if canReply}
				<button type="button" class="act" aria-label="Reply" title="Reply" onclick={() => onreply?.(message)}>
					<Icon name="reply" size={17} strokeWidth={2.2} />
				</button>
			{/if}
			<button type="button" class="act" aria-label="Message actions" aria-haspopup="menu" onclick={openFromButton}>
				<Icon name="more" size={18} strokeWidth={2.6} />
			</button>
		</span>
	{/if}
{/snippet}

{#snippet quoteBlock()}
	{#if quote}
		<button type="button" class="quote" class:gone={quote.deleted} onclick={() => onquote?.(quote.id)}>
			<span class="sr-only">Replying to </span>
			<span class="quote-name">{quote.name}</span>
			<span class="quote-text">{quote.snippet}</span>
			<span class="sr-only">. {quoteHint}</span>
		</button>
	{/if}
{/snippet}

{#snippet body(label: string)}
	<button
		type="button"
		class="body"
		disabled={pending}
		aria-label={label}
		{onclick}
		{onpointerdown}
		{onpointermove}
		onpointerup={cancelPress}
		onpointercancel={cancelPress}
		onpointerleave={cancelPress}
		{oncontextmenu}>{message.body}</button
	>
{/snippet}

{#if mine}
	<div class="mine-wrap" class:compact>
		<div class="mine-line">
			{#if message.deleted}
				<div class="bubble deleted mine" role="note" aria-label="Message deleted">You deleted this message</div>
			{:else}
				{@render actions('left')}
				<div class="bubble mine" class:pending class:pressable={hasMenu} class:quoted={!!quote}>
					{@render quoteBlock()}
					{@render body(`Your message${quoteLabel}: ${message.body}${edited ? ' (edited)' : ''}. Show message info`)}
				</div>
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
				<div class="line">
					<div class="bubble glass theirs" class:pressable={hasMenu} class:quoted={!!quote}>
						{@render quoteBlock()}
						{@render body(`${name}${quoteLabel}: ${message.body}${edited ? ' (edited)' : ''}. Show message info`)}
					</div>
					{@render actions('right')}
				</div>
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
		display: flex;
		flex-direction: column;
		min-width: 0;
		border-radius: 18px 18px 18px 6px;
		font-size: 14px;
		line-height: 1.4;
	}
	.body {
		padding: 9px 12px;
		border-radius: inherit;
		font: inherit;
		color: inherit;
		text-align: left;
		white-space: pre-wrap;
		overflow-wrap: anywhere;
		cursor: pointer;
	}
	.quoted .body {
		padding-top: 6px;
	}
	.compact .bubble {
		font-size: 13px;
	}
	.compact .body {
		padding: 8px 11px;
	}
	.compact .quoted .body {
		padding-top: 5px;
	}
	.compact .bubble.glass {
		background: var(--surface);
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		box-shadow: none;
		border-color: transparent;
	}
	.line {
		position: relative;
		display: flex;
		align-items: center;
		min-width: 0;
	}
	.theirs {
		align-self: flex-start;
		color: inherit;
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
		border-radius: 18px 18px 6px 18px;
		background: var(--accent);
		color: var(--on-accent);
		transition:
			opacity 0.2s ease,
			transform 0.15s ease;
	}
	.pressable .body {
		-webkit-touch-callout: none;
		-webkit-user-select: none;
		user-select: none;
	}
	.mine:has(.body:active:not(:disabled)) {
		transform: scale(0.98);
	}
	.mine.pending {
		opacity: 0.6;
	}
	.bubble.deleted {
		display: block;
		padding: 9px 12px;
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
	/* The quoted message above a reply: name over one line, a bar down the side. */
	.quote {
		margin: 6px 6px 0;
		padding: 5px 10px 5px 10px;
		border-radius: 12px;
		border-left: 3px solid var(--accent-ink);
		background: var(--accent-soft);
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
		text-align: left;
		font-size: 12px;
		line-height: 1.35;
		color: var(--muted);
		cursor: pointer;
	}
	.mine .quote {
		border-left-color: color-mix(in srgb, var(--on-accent) 60%, transparent);
		background: color-mix(in srgb, var(--on-accent) 16%, transparent);
		color: color-mix(in srgb, var(--on-accent) 82%, transparent);
	}
	.quote-name {
		font-weight: 600;
		color: var(--accent-ink);
	}
	.mine .quote-name {
		color: var(--on-accent);
	}
	.mine .quote:focus-visible {
		outline-color: var(--on-accent);
		outline-offset: -2px;
	}
	.quote-name,
	.quote-text {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.quote.gone .quote-text {
		font-style: italic;
	}
	.quote:hover {
		filter: brightness(0.97);
	}
	/* Reply and ⋯ beside the bubble: shown on hover or keyboard focus (pointer devices); reachable by keyboard and screen readers always. */
	.actions {
		position: absolute;
		top: 50%;
		translate: 0 -50%;
		display: flex;
		gap: 2px;
		opacity: 0;
		transition: opacity 0.15s ease;
	}
	.actions.left {
		right: calc(100% + 4px);
		flex-direction: row-reverse;
	}
	.actions.right {
		left: calc(100% + 4px);
	}
	.actions:focus-within {
		opacity: 1;
	}
	.act {
		width: 32px;
		height: 32px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
		transition: background-color 0.15s ease;
	}
	.act:hover {
		color: var(--ink);
		background: var(--surface);
	}
	@media (hover: hover) {
		.mine-line:hover .actions,
		.line:hover .actions {
			opacity: 1;
		}
	}
	@media (hover: none) {
		/* Long-press opens the menu on touch screens; keep the buttons for keyboards only. */
		.actions:not(:focus-within) {
			pointer-events: none;
		}
	}
</style>
