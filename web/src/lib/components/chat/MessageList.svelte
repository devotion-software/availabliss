<script lang="ts">
	import { tick } from 'svelte';
	import type { Group, Message } from '#lib/types';
	import type { ChatThread } from '#lib/client/chat-thread.svelte';
	import { useFormat } from '#lib/client/format';
	import { isAdminRole, memberMap } from '#lib/client/members';
	import Button from '#lib/components/ui/Button.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import MessageBubble from './MessageBubble.svelte';
	import PollCard from './PollCard.svelte';
	import ProposalCard from './ProposalCard.svelte';
	import EventCard from './EventCard.svelte';
	import ReadReceipt from './ReadReceipt.svelte';
	import MessageInfoSheet from './MessageInfoSheet.svelte';

	/** The scrolling message log for a thread. Reports visibility so `seen` receipts are honest. */
	interface Props {
		thread: ChatThread;
		group: Group;
		meId: string;
		compact?: boolean;
	}
	let { thread, group, meId, compact = false }: Props = $props();

	const fmt = useFormat();
	const members = $derived(memberMap(group));
	const admin = $derived(isAdminRole(group.role));

	let scroller: HTMLElement | undefined = $state();
	let sentinel: HTMLElement | undefined = $state();
	let infoOpen = $state(false);
	let infoMessage = $state<Message | null>(null);

	// Plain (non-reactive) bookkeeping for scroll behaviour.
	let atBottom = true;
	let lastId = '';

	const RUN_GAP_MS = 5 * 60_000;
	const items = $derived.by(() => {
		const list = thread.messages;
		const lastMine = list.findLastIndex((m) => m.authorId === meId && (m.kind === 'text' || m.kind === 'poll'));
		return list.map((m, i) => {
			const prev = list[i - 1];
			const next = list[i + 1];
			const day = fmt.current.dayKey(m.createdAt);
			const divider = !prev || fmt.current.dayKey(prev.createdAt) !== day;
			const joins = (a: Message | undefined, b: Message | undefined) =>
				!!a &&
				!!b &&
				a.kind === 'text' &&
				b.kind === 'text' &&
				a.authorId === b.authorId &&
				fmt.current.dayKey(a.createdAt) === fmt.current.dayKey(b.createdAt) &&
				Math.abs(new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()) < RUN_GAP_MS;
			return {
				m,
				divider: divider ? fmt.current.dayDivider(m.createdAt) : null,
				first: !joins(prev, m),
				last: !joins(m, next),
				mine: m.authorId === meId,
				receipt: i === lastMine
			};
		});
	});

	// Report whether the newest messages are on screen.
	$effect(() => {
		if (!scroller || !sentinel) return;
		const observer = new IntersectionObserver(
			([entry]) => {
				atBottom = entry.isIntersecting;
				thread.setVisible(entry.isIntersecting);
			},
			{ root: scroller, rootMargin: '0px 0px 80px 0px' }
		);
		observer.observe(sentinel);
		return () => {
			observer.disconnect();
			thread.setVisible(false);
		};
	});

	// Follow new messages when you're already at the bottom (or just sent one).
	$effect.pre(() => {
		const last = thread.messages.at(-1);
		const id = last?.id ?? '';
		if (id === lastId) return;
		const initial = lastId === '';
		lastId = id;
		if (initial || atBottom || last?.authorId === meId) {
			tick().then(() => scroller?.scrollTo({ top: scroller.scrollHeight, behavior: initial ? 'auto' : 'smooth' }));
		}
	});

	async function loadOlder() {
		const before = scroller?.scrollHeight ?? 0;
		await thread.loadOlder();
		await tick();
		if (scroller) scroller.scrollTop += scroller.scrollHeight - before;
	}

	function openInfo(m: Message) {
		if (m.id.startsWith('temp-')) return;
		infoMessage = m;
		infoOpen = true;
	}
</script>

<div class="scroller" class:compact bind:this={scroller}>
	<div class="log" role="log" aria-label="Messages" aria-busy={thread.loading}>
		{#if thread.hasMore}
			<div class="older">
				<Button variant="ghost" size="sm" loading={thread.loadingOlder} onclick={loadOlder}>Load earlier messages</Button>
			</div>
		{/if}
		{#if thread.loading && !thread.messages.length}
			<div class="center"><Spinner /></div>
		{:else if !thread.messages.length}
			<EmptyState compact icon="chat" title="No messages yet" message="Say hello, start a poll, or propose a time." />
		{/if}

		{#each items as item (item.m.id)}
			{@const m = item.m}
			{#if item.divider}<span class="divider">{item.divider}</span>{/if}
			<div class="item" class:tight={!item.first}>
				{#if m.kind === 'system'}
					<p class="system">{m.body}</p>
				{:else if m.kind === 'poll' && m.poll}
					<PollCard
						poll={m.poll}
						{meId}
						mine={item.mine}
						{compact}
						canClose={item.mine || admin}
						onvote={(ids) => thread.vote(m.poll!.id, ids)}
						onclose={() => thread.closePoll(m.poll!.id)}
					/>
				{:else if m.kind === 'proposal' && m.proposalId}
					<ProposalCard proposalId={m.proposalId} {members} {meId} {compact} />
				{:else if m.kind === 'event'}
					<EventCard message={m} {compact} />
				{:else}
					<MessageBubble
						message={m}
						mine={item.mine}
						author={m.authorId ? members.get(m.authorId) : undefined}
						first={item.first}
						last={item.last}
						{compact}
						oninfo={openInfo}
					/>
				{/if}
				{#if item.receipt}
					<ReadReceipt message={m} {members} onopen={() => openInfo(m)} />
				{/if}
			</div>
		{/each}
		<span class="sentinel" bind:this={sentinel} aria-hidden="true"></span>
	</div>
</div>

<MessageInfoSheet bind:open={infoOpen} message={infoMessage} {members} />

<style>
	.scroller {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: thin;
		display: flex;
		flex-direction: column;
	}
	.log {
		margin-top: auto;
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding: 14px 16px 8px;
	}
	.compact .log {
		gap: 8px;
		padding: 4px 2px;
	}
	.item {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.item.tight {
		margin-top: -6px;
	}
	.older,
	.center {
		display: flex;
		justify-content: center;
		color: var(--muted);
		padding: 8px 0;
	}
	.divider {
		align-self: center;
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
		padding: 4px 0;
	}
	.system {
		align-self: center;
		max-width: 90%;
		text-align: center;
		font-size: 12px;
		color: var(--muted);
	}
	.sentinel {
		height: 1px;
		flex: none;
	}
</style>
