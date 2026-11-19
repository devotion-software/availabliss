<script lang="ts">
	import { tick } from 'svelte';
	import type { Group, Message } from '#lib/types';
	import type { ChatThread } from '#lib/client/chat-thread.svelte';
	import { useFormat } from '#lib/client/format';
	import { isAdminRole, memberMap } from '#lib/client/members';
	import Button from '#lib/components/ui/Button.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import MessageBubble, { type BubbleQuote } from './MessageBubble.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { replyAuthorName, replySnippet } from '#lib/client/chat-messages';
	import PollCard from './PollCard.svelte';
	import PollComposerSheet from './PollComposerSheet.svelte';
	import type { Poll } from '#lib/types';
	import ProposalCard from './ProposalCard.svelte';
	import EventCard from './EventCard.svelte';
	import ConfirmedBanner from './ConfirmedBanner.svelte';
	import { isConfirmationMessage } from '#lib/client/confirmation';
	import ReadReceipt from './ReadReceipt.svelte';
	import MessageInfoSheet from './MessageInfoSheet.svelte';
	import MessageMenu, { type MessageMenuAnchor } from './MessageMenu.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import ThreadSheet from './ThreadSheet.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';

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
	// Reply / Info, and Edit / Delete on your own messages.
	let menuAnchor = $state<MessageMenuAnchor | null>(null);
	let menuMessage = $state<Message | null>(null);
	// The thread open in its sheet, by the message it was opened from.
	let threadFor = $state<string | null>(null);
	// A message ("Show in chat" from a thread), flashed after scrolling to it.
	let highlighted = $state<string | null>(null);
	let highlightTimer: ReturnType<typeof setTimeout> | undefined;
	let confirmOpen = $state(false);
	let deleting = $state<Message | null>(null);

	// "Propose this" on a date poll's leading time: the propose sheet with it filled in. Proposing
	// closes your own poll (only its author can).
	let proposeOpen = $state(false);
	let proposeFrom = $state<{ pollId: string; mine: boolean; slot: { start: string; end: string; allDay: boolean } } | null>(null);
	function proposeFromPoll(m: Message, slot: { start: string; end: string; allDay: boolean }) {
		proposeFrom = { pollId: m.poll!.id, mine: m.authorId === meId, slot };
		proposeOpen = true;
	}
	async function proposed() {
		const from = proposeFrom;
		if (from?.mine && !thread.messages.find((m) => m.poll?.id === from.pollId)?.poll?.closed) await thread.closePoll(from.pollId);
	}

	// Plain (non-reactive) bookkeeping for scroll behaviour.
	let atBottom = true;
	let lastId = '';

	// Scrolled back further than this from the newest message, the jump-to-latest button shows,
	// counting messages from others that arrive meanwhile.
	const JUMP_THRESHOLD = 200;
	let scrolledBack = $state(false);
	let unseen = $state(0);
	/** When the list last scrolled: still gliding after a flick if it's very recent. */
	let lastScrollAt = 0;
	function onscroll() {
		lastScrollAt = performance.now();
		if (!scroller) return;
		scrolledBack = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight > JUMP_THRESHOLD;
		if (!scrolledBack) unseen = 0;
	}
	function jumpToLatest() {
		unseen = 0;
		const el = scroller;
		if (!el) return;
		// Still gliding from a flick: a smooth scroll would be ignored (iOS Safari) or fight the glide,
		// so stop the glide (briefly no scrolling) and jump straight there.
		if (performance.now() - lastScrollAt < 150) {
			el.style.overflowY = 'hidden';
			el.scrollTop = el.scrollHeight;
			requestAnimationFrame(() => {
				el.style.overflowY = '';
				el.scrollTop = el.scrollHeight;
			});
			return;
		}
		const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		el.scrollTo({ top: el.scrollHeight, behavior: reduce ? 'auto' : 'smooth' });
	}

	const RUN_GAP_MS = 5 * 60_000;
	// A message's day (in your time zone), worked out once per message: it's the costly part of
	// laying out the list, which runs again whenever any message changes.
	let dayCache = new Map<string, string>();
	let dayCacheFormat: unknown = null;
	function dayOf(iso: string): string {
		if (dayCacheFormat !== fmt.current) {
			dayCache = new Map();
			dayCacheFormat = fmt.current;
		}
		let day = dayCache.get(iso);
		if (day === undefined) {
			day = fmt.current.dayKey(iso);
			dayCache.set(iso, day);
		}
		return day;
	}
	// Reply previews kept while the quoted message and the members are the same, so unchanged rows
	// keep the same props and don't re-render.
	const quoteCache = $derived.by(() => {
		void members;
		void meId;
		return new WeakMap<NonNullable<Message['replyTo']>, BubbleQuote>();
	});
	function cachedQuote(r: NonNullable<Message['replyTo']>): BubbleQuote {
		let q = quoteCache.get(r);
		if (!q) {
			q = quoteFor(r);
			quoteCache.set(r, q);
		}
		return q;
	}
	const items = $derived.by(() => {
		const list = thread.messages;
		// The read receipt sits under your newest text or poll (not a deleted one).
		const lastMine = list.findLastIndex((m) => m.authorId === meId && !m.deleted && (m.kind === 'text' || m.kind === 'poll'));
		const days = list.map((m) => dayOf(m.createdAt));
		const times = list.map((m) => Date.parse(m.createdAt));
		const joins = (a: number, b: number) => {
			const x = list[a];
			const y = list[b];
			return (
				!!x &&
				!!y &&
				x.kind === 'text' &&
				y.kind === 'text' &&
				x.authorId === y.authorId &&
				days[a] === days[b] &&
				Math.abs(times[b] - times[a]) < RUN_GAP_MS
			);
		};
		return list.map((m, i) => ({
			m,
			divider: i === 0 || days[i - 1] !== days[i] ? fmt.current.dayDivider(m.createdAt) : null,
			first: !joins(i - 1, i),
			last: !joins(i, i + 1),
			mine: m.authorId === meId,
			receipt: i === lastMine,
			quote: m.replyTo && !m.deleted ? cachedQuote(m.replyTo) : null
		}));
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

	// Stay pinned to the newest message when the list or its box changes size while you're at the bottom:
	// cards that load after the first scroll, the edit strip above the composer, the phone keyboard.
	$effect(() => {
		const el = scroller;
		const log = el?.firstElementChild;
		if (!el || !log) return;
		const observer = new ResizeObserver(() => {
			if (atBottom) el.scrollTop = el.scrollHeight;
			onscroll();
		});
		observer.observe(el);
		observer.observe(log);
		return () => observer.disconnect();
	});

	// Follow new messages when you're already at the bottom (or just sent one).
	$effect.pre(() => {
		const last = thread.messages.at(-1);
		const id = last?.id ?? '';
		if (id === lastId) return;
		const initial = lastId === '';
		const previous = lastId;
		lastId = id;
		if (initial || atBottom || last?.authorId === meId) {
			tick().then(() => scroller?.scrollTo({ top: scroller.scrollHeight, behavior: initial ? 'auto' : 'smooth' }));
		} else {
			// Scrolled back: stay put and count what's new on the jump button.
			const from = thread.messages.findIndex((m) => m.id === previous);
			if (from >= 0) unseen += thread.messages.slice(from + 1).filter((m) => m.kind !== 'system' && m.authorId !== meId).length;
		}
	});

	/** Replies, and messages that have replies, are part of a thread. */
	const repliedTo = $derived(new Set(thread.messages.flatMap((m) => (m.replyTo ? [m.replyTo.id] : []))));
	const inThread = (m: Message) => !m.id.startsWith('temp-') && (!!m.replyTo || repliedTo.has(m.id));

	/** Closes the thread and goes to a message in the chat, loading earlier pages until it's there (also from search). */
	export async function showInChat(id: string) {
		threadFor = null;
		for (let pages = 0; !thread.messages.some((m) => m.id === id) && thread.hasMore && pages < 20; pages++) {
			const before = thread.messages.length;
			await loadOlder();
			if (thread.messages.length === before) break;
		}
		await tick();
		// After the sheet has faded away.
		setTimeout(() => showOriginal(id), 180);
	}

	function quoteFor(r: NonNullable<Message['replyTo']>): BubbleQuote {
		return { id: r.id, name: replyAuthorName(r, meId, members), snippet: replySnippet(r), deleted: r.deleted };
	}

	/** Scrolls to a message (when it's loaded) and flashes it. */
	function showOriginal(id: string) {
		const el = scroller?.querySelector<HTMLElement>(`[data-message-id="${CSS.escape(id)}"]`);
		if (!el) return;
		const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		el.scrollIntoView({ block: 'center', behavior: reduce ? 'auto' : 'smooth' });
		highlighted = id;
		clearTimeout(highlightTimer);
		highlightTimer = setTimeout(() => (highlighted = null), 1400);
	}

	function reply(m: Message) {
		thread.startReply(m);
	}

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

	function openMenu(message: Message, anchor: MessageMenuAnchor) {
		menuMessage = message;
		menuAnchor = anchor;
	}

	function confirmDelete(message: Message) {
		deleting = message;
		confirmOpen = true;
	}

	async function remove() {
		if (!deleting) return;
		try {
			await thread.deleteMessage(deleting);
		} catch {
			// The thread already toasted.
		}
	}

	// Your poll being edited (from its Edit button).
	let editingPoll = $state<Poll | null>(null);
	let editingOpen = $state(false);
	$effect(() => {
		if (editingPoll) editingOpen = true;
	});
	$effect(() => {
		if (!editingOpen) editingPoll = null;
	});
</script>

<div class="list">
	<div class="scroller" class:compact bind:this={scroller} {onscroll}>
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
				<div class="item" class:tight={!item.first} class:highlight={highlighted === m.id} data-message-id={m.id}>
					{#if isConfirmationMessage(m)}
						<ConfirmedBanner message={m} {compact} />
					{:else if m.kind === 'system'}
						<p class="system">{m.body}</p>
					{:else if m.kind === 'poll' && m.poll}
						<div class="poll-line" class:mine={item.mine}>
							<PollCard
								poll={m.poll}
								{meId}
								mine={item.mine}
								authorName={m.authorId ? members.get(m.authorId)?.name : undefined}
								{compact}
								canClose={item.mine}
								onvote={(ids) => thread.vote(m.poll!.id, ids)}
								onclose={() => thread.closePoll(m.poll!.id)}
								onedit={() => (editingPoll = m.poll!)}
								groupId={group.id}
								{members}
								onpropose={item.mine || admin ? (slot) => proposeFromPoll(m, slot) : undefined}
							/>
							{#if thread.canReply(m)}
								<button type="button" class="poll-reply" aria-label="Reply to the poll" title="Reply" onclick={() => reply(m)}>
									<Icon name="reply" size={17} strokeWidth={2.2} />
								</button>
							{/if}
						</div>
					{:else if m.kind === 'proposal' && m.proposalId}
						<ProposalCard proposalId={m.proposalId} {members} {meId} {compact} />
					{:else if m.kind === 'event'}
						<EventCard message={m} {meId} {admin} {compact} />
					{:else}
						<MessageBubble
							message={m}
							mine={item.mine}
							author={m.authorId ? members.get(m.authorId) : undefined}
							first={item.first}
							last={item.last}
							{compact}
							showEdited={!item.receipt}
							canModify={thread.canModify(m)}
							canReply={thread.canReply(m)}
							quote={item.quote}
							oninfo={openInfo}
							onmenu={openMenu}
							onreply={reply}
							onquote={() => (threadFor = m.id)}
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
	{#if scrolledBack}
		<div class="jump" class:compact>
			<IconButton
				icon="arrow-down"
				label={unseen > 0 ? `Jump to latest message, ${unseen} new` : 'Jump to latest message'}
				size={compact ? 40 : 44}
				badge={unseen}
				onclick={jumpToLatest}
			/>
		</div>
	{/if}
</div>

<PollComposerSheet bind:open={editingOpen} editing={editingPoll} onsave={(patch) => thread.editPoll(editingPoll!.id, patch)} />

<ThreadSheet focusId={threadFor} {group} {meId} onclose={() => (threadFor = null)} onshowinchat={showInChat} />

<MessageInfoSheet bind:open={infoOpen} message={infoMessage} {members} {meId} />
<ProposeSheet bind:open={proposeOpen} {group} {meId} initial={proposeFrom?.slot ?? null} onproposed={proposed} />

<MessageMenu
	anchor={menuAnchor}
	onclose={() => (menuAnchor = null)}
	oninfo={() => menuMessage && openInfo(menuMessage)}
	onreply={menuMessage && thread.canReply(menuMessage) ? () => menuMessage && reply(menuMessage) : undefined}
	onthread={menuMessage && inThread(menuMessage) ? () => menuMessage && (threadFor = menuMessage.id) : undefined}
	onedit={menuMessage && thread.canEdit(menuMessage) ? () => menuMessage && thread.startEdit(menuMessage) : undefined}
	ondelete={menuMessage && thread.canModify(menuMessage) ? () => menuMessage && confirmDelete(menuMessage) : undefined}
/>

<ConfirmSheet
	bind:open={confirmOpen}
	title="Delete this message?"
	message="It'll show as deleted for everyone."
	confirmLabel="Delete"
	destructive
	onconfirm={remove}
/>

<style>
	.list {
		position: relative;
		flex: 1;
		min-height: 0;
		display: flex;
		flex-direction: column;
	}
	.jump {
		position: absolute;
		right: 14px;
		bottom: 8px;
		animation: jump-in 0.2s var(--ease-out);
	}
	.jump.compact {
		right: 6px;
		bottom: 4px;
	}
	@keyframes jump-in {
		from {
			opacity: 0;
			transform: scale(0.6);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.jump {
			animation: none;
		}
	}
	.scroller {
		flex: 1;
		min-height: 0;
		overflow-x: hidden;
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
	.item {
		border-radius: 18px;
		transition:
			background-color 0.6s ease,
			box-shadow 0.6s ease;
	}
	.item.highlight {
		background: var(--accent-soft);
		box-shadow: 0 0 0 6px var(--accent-soft);
		transition-duration: 0.2s;
	}
	.poll-line {
		position: relative;
		display: flex;
		flex-direction: column;
	}
	.poll-reply {
		position: absolute;
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
	/* Beside the card: to its right for others' polls, left for yours. */
	.poll-line:not(.mine) .poll-reply {
		left: min(calc(100% + 4px), calc(320px + 4px));
	}
	.poll-line.mine .poll-reply {
		right: min(calc(100% + 4px), calc(320px + 4px));
	}
	.poll-reply:hover {
		color: var(--ink);
		background: var(--surface);
	}
	.poll-reply:focus-visible {
		opacity: 1;
	}
	@media (hover: hover) {
		.poll-line:hover .poll-reply {
			opacity: 1;
		}
	}
	@media (hover: none) {
		/* No hover on touch screens: keep it faintly visible. */
		.poll-reply {
			opacity: 0.7;
		}
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
