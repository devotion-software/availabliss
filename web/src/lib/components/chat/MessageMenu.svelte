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
	import { MORE_REACTIONS, QUICK_REACTIONS, typedEmoji } from '#lib/client/reactions';

	/**
	 * A row of reactions (+ for more, or any emoji typed in) above
	 * Reply / View thread / Info, and Edit / Delete on your own messages (in a thread: Reply / Show in chat):
	 * opened by long-press, right-click or the bubble's ⋯ button.
	 * Floats at the pointer (or the button), kept inside the viewport. Same look as the group's More menu.
	 */
	interface Props {
		/** Where to open it; null when closed. */
		anchor: MessageMenuAnchor | null;
		/** Each is left out of the menu when not given. */
		oninfo?: () => void;
		onreply?: () => void;
		onthread?: () => void;
		onshowinchat?: () => void;
		onedit?: () => void;
		ondelete?: () => void;
		/** Reacting: the row of emoji is left out when not given. */
		onreact?: (emoji: string) => void;
		/** Your reaction to the message, highlighted (picking it again takes it back). */
		reacted?: string | null;
		onclose: () => void;
	}
	let { anchor, oninfo, onreply, onthread, onshowinchat, onedit, ondelete, onreact, reacted = null, onclose }: Props = $props();

	// "+": more emoji and a field for any other.
	let more = $state(false);
	let typed = $state('');
	const typedValid = $derived(typedEmoji(typed));
	$effect(() => {
		if (!anchor) {
			more = false;
			typed = '';
		}
	});

	type Item = { label: string; icon: 'reply' | 'chat' | 'arrow-right' | 'info' | 'pencil' | 'trash'; run: () => void; danger?: boolean };
	const items = $derived(
		[
			onreply && { label: 'Reply', icon: 'reply', run: onreply },
			onthread && { label: 'View thread', icon: 'chat', run: onthread },
			onshowinchat && { label: 'Show in chat', icon: 'arrow-right', run: onshowinchat },
			oninfo && { label: 'Info', icon: 'info', run: oninfo },
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
		void more;
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
		tick().then(() => (keyboard ? (el?.querySelector<HTMLElement>('.react') ?? buttons[0]) : el)?.focus({ preventScroll: true }));
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

	function react(emoji: string) {
		const run = onreact;
		if (run) choose(() => run(emoji));
	}

	function submitTyped(e: SubmitEvent) {
		e.preventDefault();
		if (typedValid) react(typedValid);
	}

	/** Left / right moves along a row of emoji. */
	function onrowkeydown(e: KeyboardEvent) {
		if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
		const row = [...(e.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button')];
		const i = row.indexOf(document.activeElement as HTMLElement);
		if (i < 0) return;
		e.preventDefault();
		row[(i + (e.key === 'ArrowRight' ? 1 : -1) + row.length) % row.length]?.focus();
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.target instanceof HTMLInputElement && e.key !== 'Escape') return;
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
		{#if onreact}
			<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
			<div class="reactions" role="group" aria-label="React" onkeydown={onrowkeydown}>
				{#each QUICK_REACTIONS as emoji (emoji)}
					<button
						type="button"
						class="react"
						class:on={reacted === emoji}
						aria-label={reacted === emoji ? `Remove your ${emoji} reaction` : `React with ${emoji}`}
						aria-pressed={reacted === emoji}
						onclick={() => react(emoji)}
					>
						{emoji}
					</button>
				{/each}
				<button
					type="button"
					class="react more"
					class:on={!!reacted && !QUICK_REACTIONS.includes(reacted as (typeof QUICK_REACTIONS)[number]) && !more}
					aria-label={more ? 'Fewer reactions' : 'More reactions'}
					aria-expanded={more}
					onclick={() => (more = !more)}
				>
					{#if reacted && !QUICK_REACTIONS.includes(reacted as (typeof QUICK_REACTIONS)[number]) && !more}{reacted}{:else}<Icon
							name={more ? 'minus' : 'plus'}
							size={18}
							strokeWidth={2.4}
						/>{/if}
				</button>
			</div>
			{#if more}
				<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
				<div class="grid" role="group" aria-label="More reactions" onkeydown={onrowkeydown}>
					{#each MORE_REACTIONS as emoji (emoji)}
						<button
							type="button"
							class="react"
							class:on={reacted === emoji}
							aria-label={reacted === emoji ? `Remove your ${emoji} reaction` : `React with ${emoji}`}
							aria-pressed={reacted === emoji}
							onclick={() => react(emoji)}
						>
							{emoji}
						</button>
					{/each}
				</div>
				<form class="typed" onsubmit={submitTyped}>
					<input
						type="text"
						bind:value={typed}
						placeholder="Or type any emoji"
						aria-label="Any emoji"
						enterkeyhint="send"
						autocomplete="off"
						spellcheck="false"
					/>
					<button type="submit" class="react" disabled={!typedValid} aria-label={typedValid ? `React with ${typedValid}` : 'React'}>
						{typedValid ?? '↵'}
					</button>
				</form>
			{/if}
			{#if items.length}<hr />{/if}
		{/if}
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
		/* Above sheets too: it also opens inside the thread sheet. */
		z-index: 110;
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
	.reactions {
		display: flex;
		gap: 2px;
		padding: 2px;
	}
	.grid {
		display: grid;
		grid-template-columns: repeat(8, 1fr);
		gap: 2px;
		padding: 4px 2px 2px;
		max-width: 296px;
	}
	.react {
		min-height: 0;
		width: 36px;
		height: 36px;
		padding: 0;
		justify-content: center;
		border-radius: 50%;
		font-size: 22px;
		line-height: 1;
		transition: transform 0.12s var(--ease-out);
	}
	.react:hover:not(:disabled) {
		transform: scale(1.15);
	}
	.react.on {
		background: var(--accent-soft);
	}
	.react.more {
		color: var(--muted);
		background: var(--surface);
		font-size: 20px;
	}
	.typed {
		display: flex;
		gap: 6px;
		align-items: center;
		padding: 6px 2px 2px;
	}
	.typed input {
		flex: 1;
		min-width: 0;
		height: 36px;
		padding: 0 12px;
		border-radius: 18px;
		border: 1px solid var(--line-2);
		background: var(--surface);
		color: var(--ink);
		font-size: 16px;
	}
	.typed .react:disabled {
		color: var(--muted);
		font-size: 16px;
	}
	hr {
		border: 0;
		border-top: 1px solid var(--line);
		margin: 4px 6px;
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
