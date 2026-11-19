<script lang="ts">
	import { untrack } from 'svelte';
	import type { Member, Poll } from '#lib/types';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { firstName, useFormat } from '#lib/client/format';
	import { getConflicts } from '#lib/client/proposals';
	import { live } from '#lib/client/live.svelte';
	import { busyLine } from '#lib/components/proposals/clash';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	type PollSlot = NonNullable<Poll['options'][number]['slot']>;

	/**
	 * A poll in the chat (Chat.dc.html): tap an option to vote, bars show the split. A date poll's
	 * options are times: each says who's busy then, and its creator (or an admin) can propose the leader.
	 */
	interface Props {
		poll: Poll;
		meId: string;
		mine?: boolean;
		/** Who asked, shown as "Poll by Ana" (yours: "Your poll"). */
		authorName?: string;
		canClose?: boolean;
		compact?: boolean;
		onvote: (optionIds: string[]) => void;
		onclose?: () => void;
		/** For a date poll's "Ana's busy" per option. */
		groupId?: string;
		members?: Map<string, Member>;
		/** "Propose this" on each leading time (the poll's creator and admins). */
		onpropose?: (slot: PollSlot) => void;
	}
	let {
		poll,
		meId,
		mine = false,
		authorName,
		canClose = false,
		compact = false,
		onvote,
		onclose,
		groupId,
		members = new Map(),
		onpropose
	}: Props = $props();

	const fmt = useFormat();
	const dated = $derived(poll.options.some((o) => o.slot));
	const ahead = (slot: PollSlot) => new Date(slot.end).getTime() > Date.now();

	const counts = $derived(poll.options.map((o) => o.voterIds.length));
	const total = $derived(counts.reduce((a, b) => a + b, 0));
	const max = $derived(Math.max(0, ...counts));
	const myVotes = $derived(poll.options.filter((o) => o.voterIds.includes(meId)).map((o) => o.id));

	// The leading times still to come (most votes, every one of a tie), for "Propose this".
	const leadIds = $derived.by(() => {
		if (!max) return new Set<string>();
		return new Set(poll.options.filter((o, i) => counts[i] === max && o.slot && ahead(o.slot)).map((o) => o.id));
	});

	// Who's busy at each time still to come (refreshed when anyone's busy time changes). Not for closed polls.
	let busy = $state<Record<string, string[]>>({});
	const slotKey = $derived(
		poll.closed ? '' : poll.options.map((o) => (o.slot && ahead(o.slot) ? `${o.id}@${o.slot.start}/${o.slot.end}` : '')).join()
	);
	$effect(() => {
		const id = groupId;
		void slotKey;
		if (!id || !slotKey) return;
		// Keyed on the times only, so votes coming in don't refetch.
		const wanted = untrack(() => poll.options.filter((o) => o.slot && ahead(o.slot)));
		let stale = false;
		const refresh = () => {
			Promise.all(wanted.map((o) => getConflicts(id, o.slot!.start, o.slot!.end).then((ids) => [o.id, ids] as const)))
				.then((entries) => !stale && (busy = Object.fromEntries(entries)))
				.catch(() => {});
		};
		refresh();
		const off = live.on(['availability.changed'], (e) => {
			if (e.groupId === null || e.groupId === id) refresh();
		});
		return () => {
			stale = true;
			off();
		};
	});

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
		<span>
			{mine ? 'Your poll' : `Poll by ${firstName(authorName ?? 'Someone')}`} · {total} vote{total === 1 ? '' : 's'}{poll.multi ? ' · pick any' : ''}
		</span>
		{#if poll.closed}<StatusPill>Closed</StatusPill>{/if}
	</div>
	<h3>{poll.question}</h3>
	<div class="options" role="group" aria-label="Options">
		{#each poll.options as option, i (option.id)}
			{@const on = myVotes.includes(option.id)}
			{@const n = counts[i]}
			{@const label = option.slot ? fmt.current.when(option.slot.start, option.slot.end, option.slot.allDay) : option.label}
			{@const who = busy[option.id] ? busyLine(busy[option.id], members, meId) : null}
			<button
				type="button"
				class="option"
				class:on
				class:leading={dated && leadIds.has(option.id)}
				aria-pressed={on}
				disabled={poll.closed}
				aria-label="{label}, {n} vote{n === 1 ? '' : 's'}{who ? `, ${who}` : ''}"
				onclick={() => pick(option.id)}
			>
				<span class="bar" class:lead={n > 0 && n === max} style:width="{total ? Math.round((n / total) * 100) : 0}%"></span>
				<span class="mark" class:square={poll.multi}>
					{#if on}<Icon name="check" size={11} strokeWidth={3.5} />{/if}
				</span>
				<span class="label">
					{label}
					{#if who}<span class="busy" class:free={!busy[option.id].length}>{who}</span>{/if}
				</span>
				<span class="count">{n}</span>
			</button>
			{#if onpropose && option.slot && leadIds.has(option.id)}
				<div class="propose">
					<Button variant="accent" size="sm" icon="calendar-plus" onclick={() => onpropose(option.slot!)}>Propose this</Button>
				</div>
			{/if}
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
		min-width: 0;
		padding: 6px 0;
		display: flex;
		flex-direction: column;
		font-size: 14px;
		font-weight: 600;
	}
	.busy {
		font-size: 12px;
		font-weight: 600;
		color: var(--warn-ink);
	}
	.busy.free {
		color: var(--ok-ink);
	}
	.option.leading {
		border-color: var(--accent-soft);
		box-shadow: inset 0 0 0 1px var(--accent-soft);
	}
	.option.leading.on {
		border-color: var(--accent);
	}
	.propose {
		display: flex;
		justify-content: flex-end;
		margin: -2px 0 2px;
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
