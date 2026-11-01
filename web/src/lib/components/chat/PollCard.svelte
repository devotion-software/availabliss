<script lang="ts">
	import type { Poll } from '#lib/types';
	import Icon from '#lib/components/ui/Icon.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';

	/** A poll in the chat (Chat.dc.html): tap an option to vote, bars show the split. */
	interface Props {
		poll: Poll;
		meId: string;
		mine?: boolean;
		canClose?: boolean;
		compact?: boolean;
		onvote: (optionIds: string[]) => void;
		onclose?: () => void;
	}
	let { poll, meId, mine = false, canClose = false, compact = false, onvote, onclose }: Props = $props();

	const counts = $derived(poll.options.map((o) => o.voterIds.length));
	const total = $derived(counts.reduce((a, b) => a + b, 0));
	const max = $derived(Math.max(0, ...counts));
	const myVotes = $derived(poll.options.filter((o) => o.voterIds.includes(meId)).map((o) => o.id));

	function pick(id: string) {
		if (poll.closed) return;
		const has = myVotes.includes(id);
		if (poll.multi) onvote(has ? myVotes.filter((v) => v !== id) : [...myVotes, id]);
		else onvote(has ? [] : [id]);
	}
</script>

<section class="poll glass" class:mine class:compact aria-label="Poll: {poll.question}">
	<div class="head">
		<Icon name="poll" size={16} strokeWidth={2.2} />
		<span>Poll · {total} vote{total === 1 ? '' : 's'}{poll.multi ? ' · pick any' : ''}</span>
		{#if poll.closed}<StatusPill>Closed</StatusPill>{/if}
	</div>
	<h3>{poll.question}</h3>
	<div class="options" role="group" aria-label="Options">
		{#each poll.options as option, i (option.id)}
			{@const on = myVotes.includes(option.id)}
			{@const n = counts[i]}
			<button
				type="button"
				class="option"
				class:on
				aria-pressed={on}
				disabled={poll.closed}
				aria-label="{option.label}, {n} vote{n === 1 ? '' : 's'}"
				onclick={() => pick(option.id)}
			>
				<span class="bar" class:lead={n > 0 && n === max} style:width="{total ? Math.round((n / total) * 100) : 0}%"></span>
				<span class="mark" class:square={poll.multi}>
					{#if on}<Icon name="check" size={11} strokeWidth={3.5} />{/if}
				</span>
				<span class="label">{option.label}</span>
				<span class="count">{n}</span>
			</button>
		{/each}
	</div>
	{#if canClose && !poll.closed && onclose}
		<button type="button" class="close" onclick={onclose}>Close poll</button>
	{/if}
</section>

<style>
	.poll {
		width: min(100%, 320px);
		border-radius: 22px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.poll.mine {
		align-self: flex-end;
	}
	.compact {
		width: 100%;
		background: var(--surface);
		box-shadow: none;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		border-radius: 16px;
		padding: 10px 12px;
		gap: 6px;
	}
	.head {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 12px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.head span:first-of-type {
		flex: 1;
	}
	h3 {
		font-size: 17px;
		font-weight: 650;
	}
	.compact h3 {
		font-size: 14px;
	}
	.options {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.option {
		position: relative;
		min-height: 46px;
		border-radius: 14px;
		overflow: hidden;
		padding: 0 12px;
		display: flex;
		align-items: center;
		gap: 8px;
		text-align: left;
		background: var(--surface);
		border: 1px solid var(--glass-border);
		transition: border-color 0.2s ease;
	}
	.compact .option {
		min-height: 44px;
		background: var(--line);
	}
	.option.on {
		border-color: var(--accent);
	}
	.option:disabled {
		cursor: default;
	}
	.bar {
		position: absolute;
		left: 0;
		top: 0;
		bottom: 0;
		background: var(--line);
		transition: width 0.45s cubic-bezier(0.3, 1.2, 0.5, 1);
	}
	.bar.lead {
		background: var(--accent-soft);
	}
	.mark {
		position: relative;
		width: 18px;
		height: 18px;
		flex: none;
		border-radius: 50%;
		border: 2px solid var(--line-3);
		display: flex;
		align-items: center;
		justify-content: center;
		color: #fff;
	}
	.mark.square {
		border-radius: 6px;
	}
	.on .mark {
		background: var(--accent);
		border-color: var(--accent);
	}
	.label {
		position: relative;
		flex: 1;
		font-size: 14px;
		font-weight: 600;
	}
	.count {
		position: relative;
		font-size: 13px;
		font-weight: 600;
		color: var(--neutral-ink);
	}
	.close {
		align-self: flex-start;
		min-height: 44px;
		padding: 0 4px;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
</style>
