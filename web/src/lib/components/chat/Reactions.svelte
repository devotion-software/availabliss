<script lang="ts">
	import type { Member, MessageReaction } from '#lib/types';
	import { myReaction, reactorNames } from '#lib/client/reactions';

	/**
	 * A message's reactions as pills under it, tucked up against the bubble: each emoji with how many
	 * reacted with it (yours highlighted). Tapping one reacts with it, or takes yours back.
	 */
	interface Props {
		reactions: MessageReaction[];
		meId: string;
		members: Map<string, Member>;
		mine: boolean;
		/** Indent past the avatar beside others' messages. */
		indent?: boolean;
		compact?: boolean;
		onreact?: (emoji: string) => void;
	}
	let { reactions, meId, members, mine, indent = false, compact = false, onreact }: Props = $props();

	const yours = $derived(myReaction(reactions, meId));
	const name = (id: string) => members.get(id)?.name ?? 'Someone';
</script>

{#if reactions.length}
	<div class="reactions" class:mine class:indent class:compact role="group" aria-label="Reactions">
		{#each reactions as r (r.emoji)}
			{@const who = reactorNames(r.userIds, meId, name)}
			<button
				type="button"
				class="pill glass"
				class:on={yours === r.emoji}
				title={who}
				aria-pressed={yours === r.emoji}
				aria-label="{r.emoji} {who}. {yours === r.emoji ? 'Remove your reaction' : `React with ${r.emoji}`}"
				disabled={!onreact}
				onclick={() => onreact?.(r.emoji)}
			>
				<span class="emoji">{r.emoji}</span>
				{#if r.userIds.length > 1}<span class="count">{r.userIds.length}</span>{/if}
			</button>
		{/each}
	</div>
{/if}

<style>
	.reactions {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		/* Snug under the bubble. */
		margin-top: -6px;
		padding: 0 10px;
		position: relative;
		z-index: 1;
		max-width: min(85%, 420px);
	}
	.reactions.mine {
		align-self: flex-end;
		justify-content: flex-end;
	}
	.reactions.indent {
		padding-left: 46px;
	}
	.reactions.indent.compact {
		padding-left: 44px;
	}
	.pill {
		display: inline-flex;
		align-items: center;
		gap: 3px;
		height: 26px;
		padding: 0 8px;
		border-radius: 13px;
		border: 1px solid var(--line-2);
		background: color-mix(in srgb, var(--bg) 88%, transparent);
		font-size: 12px;
		font-weight: 700;
		color: var(--muted);
		animation: pop 0.22s var(--ease-out) both;
	}
	.pill.on {
		border-color: color-mix(in srgb, var(--accent) 45%, transparent);
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.pill:disabled {
		cursor: default;
	}
	.emoji {
		font-size: 15px;
		line-height: 1;
	}
	@keyframes pop {
		from {
			transform: scale(0.6);
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.pill {
			animation: none;
		}
	}
</style>
