<script lang="ts">
	import type { Member, Message } from '#lib/types';
	import Avatar from '#lib/components/ui/Avatar.svelte';

	/** A text message. Others' are glass with an avatar; yours are solid and open message info. */
	interface Props {
		message: Message;
		mine: boolean;
		author?: Member;
		/** First in a run from the same person: show their name. */
		first?: boolean;
		/** Last in a run: show the avatar. */
		last?: boolean;
		compact?: boolean;
		oninfo?: (message: Message) => void;
	}
	let { message, mine, author, first = true, last = true, compact = false, oninfo }: Props = $props();

	const pending = $derived(message.id.startsWith('temp-'));
	const name = $derived(author?.name ?? 'Someone');
</script>

{#if mine}
	<button
		type="button"
		class="bubble mine"
		class:compact
		class:pending
		disabled={pending}
		aria-label="Your message: {message.body}. Show message info"
		onclick={() => oninfo?.(message)}>{message.body}</button
	>
{:else}
	<div class="row" class:compact>
		<span class="avatar" class:hidden={!last}>
			<Avatar name={name} id={message.authorId ?? undefined} image={author?.image} size={compact ? 26 : 28} />
		</span>
		<div class="col">
			{#if first}<span class="name">{name.split(' ')[0]}</span>{/if}
			<div class="bubble glass">{message.body}</div>
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
	.compact .bubble,
	.bubble.compact {
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
	.mine {
		align-self: flex-end;
		max-width: min(80%, 400px);
		border-radius: 18px 18px 6px 18px;
		text-align: left;
		background: var(--solid);
		color: var(--on-solid);
		transition:
			opacity 0.2s ease,
			transform 0.15s ease;
	}
	.mine:active:not(:disabled) {
		transform: scale(0.98);
	}
	.mine.pending {
		opacity: 0.6;
	}
</style>
