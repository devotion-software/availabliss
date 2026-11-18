<script lang="ts">
	import { tick } from 'svelte';
	import type { Group, Message } from '#lib/types';
	import { ReplyThread } from '#lib/client/reply-thread.svelte';
	import { useFormat } from '#lib/client/format';
	import { memberMap } from '#lib/client/members';
	import { canReplyTo, replyAuthorName, replySnippet, replyingTo } from '#lib/client/chat-messages';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import MessageBubble, { type BubbleQuote } from './MessageBubble.svelte';
	import PollCard from './PollCard.svelte';
	import Composer from './Composer.svelte';
	import MessageMenu, { type MessageMenuAnchor } from './MessageMenu.svelte';

	/**
	 * A thread: the first message of a chain of replies and every reply under it, oldest first,
	 * updating live, with a composer that replies within it (to the latest message unless you pick
	 * another). Tapping a message offers Reply and Show in chat; tapping a quote goes to the original.
	 */
	interface Props {
		/** The message it was opened from; null while closed. */
		focusId: string | null;
		group: Group;
		meId: string;
		onclose: () => void;
		/** Closes the thread and goes to this message in the chat. */
		onshowinchat: (messageId: string) => void;
	}
	let { focusId, group, meId, onclose, onshowinchat }: Props = $props();

	const fmt = useFormat();
	const members = $derived(memberMap(group));
	const rt = $derived(focusId ? new ReplyThread(group.id, meId, focusId) : null);

	let list: HTMLElement | undefined = $state();
	let highlighted = $state<string | null>(null);
	let highlightTimer: ReturnType<typeof setTimeout> | undefined;
	let menuAnchor = $state<MessageMenuAnchor | null>(null);
	let menuMessage = $state<Message | null>(null);

	$effect(() => {
		const t = rt;
		if (!t) return;
		t.load().then(
			async () => {
				await tick();
				// Once laid out (the composer and the count arrive with the messages).
				requestAnimationFrame(() => {
					if (t.messages.length > 1) flash(t.focusId, 'auto');
				});
			},
			() => onclose()
		);
		return t.listen();
	});

	// Something new in the thread: follow it down.
	let lastId = '';
	$effect.pre(() => {
		const id = rt?.messages.at(-1)?.id ?? '';
		const previous = lastId;
		lastId = id;
		if (!previous || !id || previous === id) return;
		tick().then(() => element(id)?.scrollIntoView({ block: 'end', behavior: reduceMotion() ? 'auto' : 'smooth' }));
	});

	const items = $derived.by(() => {
		const messages = rt?.messages ?? [];
		return messages.map((m, i) => {
			const prev = messages[i - 1];
			const next = messages[i + 1];
			const day = fmt.current.dayKey(m.createdAt);
			const sameRun = (a: Message | undefined, b: Message | undefined) =>
				!!a && !!b && a.authorId === b.authorId && fmt.current.dayKey(a.createdAt) === fmt.current.dayKey(b.createdAt);
			return {
				m,
				divider: !prev || fmt.current.dayKey(prev.createdAt) !== day ? fmt.current.dayDivider(m.createdAt) : null,
				first: !sameRun(prev, m),
				last: !sameRun(m, next),
				mine: m.authorId === meId,
				quote: m.replyTo && !m.deleted ? quoteFor(m.replyTo) : null
			};
		});
	});

	function quoteFor(r: NonNullable<Message['replyTo']>): BubbleQuote {
		return { id: r.id, name: replyAuthorName(r, meId, members), snippet: replySnippet(r), deleted: r.deleted };
	}

	const reduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;
	const element = (id: string) => list?.querySelector<HTMLElement>(`[data-thread-message="${CSS.escape(id)}"]`);

	/** Scrolls to a message in the thread and flashes it. */
	function flash(id: string, behavior: ScrollBehavior = reduceMotion() ? 'auto' : 'smooth') {
		const el = element(id);
		if (!el) return;
		el.scrollIntoView({ block: 'center', behavior });
		highlighted = id;
		clearTimeout(highlightTimer);
		highlightTimer = setTimeout(() => (highlighted = null), 1400);
	}

	/** A tap on a message: its Reply / Show in chat menu, beside it. */
	function openMenuBy(m: Message) {
		const r = element(m.id)?.querySelector('.body, .bubble')?.getBoundingClientRect() ?? element(m.id)?.getBoundingClientRect();
		if (!r) return;
		menuMessage = m;
		menuAnchor = { x: r.right, y: r.bottom };
	}

	function openMenu(m: Message, anchor: MessageMenuAnchor) {
		menuMessage = m;
		menuAnchor = anchor;
	}

	const count = $derived(rt?.messages.length ?? 0);
</script>

<Sheet
	open={focusId !== null}
	title="Thread"
	description={count ? (count === 1 ? '1 message' : `${count} messages`) : undefined}
	tall
	width={560}
	{onclose}
>
	<div class="thread" bind:this={list} role="log" aria-label="Messages in this thread" aria-busy={rt?.loading}>
		{#if rt?.loading && !rt.messages.length}
			<div class="center"><Spinner /></div>
		{/if}
		{#each items as item (item.m.id)}
			{@const m = item.m}
			{#if item.divider}<span class="divider">{item.divider}</span>{/if}
			<div class="item" class:tight={!item.first} class:highlight={highlighted === m.id} data-thread-message={m.id}>
				{#if m.kind === 'poll' && m.poll}
					<div class="poll-line" class:mine={item.mine}>
						<PollCard
							poll={m.poll}
							{meId}
							mine={item.mine}
							authorName={m.authorId ? members.get(m.authorId)?.name : undefined}
							canClose={false}
							onvote={(ids) => rt?.vote(m.poll!.id, ids)}
							onclose={() => {}}
						/>
					</div>
				{:else if m.kind === 'text'}
					<MessageBubble
						message={m}
						mine={item.mine}
						author={m.authorId ? members.get(m.authorId) : undefined}
						first={item.first}
						last={item.last}
						canReply={canReplyTo(m)}
						quote={item.quote}
						quoteHint="Show the original message"
						oninfo={openMenuBy}
						onmenu={openMenu}
						onreply={(msg) => rt?.pick(msg)}
						onquote={(id) => flash(id)}
					/>
				{/if}
			</div>
		{/each}
		{#if rt?.truncated}
			<p class="note">Later replies aren’t shown here. Find them in the chat.</p>
		{/if}
	</div>
	{#snippet footer()}
		{#if rt?.replyTarget}
			<Composer
				groupId={group.id}
				plain
				placeholder="Reply in thread"
				onsend={(b, ids) => rt?.send(b, ids)}
				replying={replyingTo(rt.replyTarget, meId, members)}
				canCancelReply={rt.picked}
				cancelReplyLabel="Reply to the latest message instead"
				oncancelreply={() => rt?.pick(null)}
				focusOnReply={rt.picked}
			/>
		{/if}
	{/snippet}
</Sheet>

<MessageMenu
	anchor={menuAnchor}
	onclose={() => (menuAnchor = null)}
	onreply={menuMessage && canReplyTo(menuMessage) ? () => menuMessage && rt?.pick(menuMessage) : undefined}
	onshowinchat={() => menuMessage && onshowinchat(menuMessage.id)}
/>

<style>
	/* Scrolls on its own, so the title and the composer stay put. */
	.thread {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: thin;
		display: flex;
		flex-direction: column;
		gap: 10px;
		/* Room for the highlight's glow at the edges. */
		padding: 8px 8px 8px;
		margin: 0 -8px;
	}
	.item {
		display: flex;
		flex-direction: column;
		border-radius: 18px;
		transition:
			background-color 0.6s ease,
			box-shadow 0.6s ease;
	}
	.item.tight {
		margin-top: -6px;
	}
	.item.highlight {
		background: var(--accent-soft);
		box-shadow: 0 0 0 6px var(--accent-soft);
		transition-duration: 0.2s;
	}
	.poll-line {
		display: flex;
		flex-direction: column;
	}
	.center {
		display: flex;
		justify-content: center;
		color: var(--muted);
		padding: 24px 0;
	}
	.divider {
		align-self: center;
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
		padding: 4px 0;
	}
	.note {
		align-self: center;
		text-align: center;
		font-size: 12px;
		color: var(--muted);
	}
</style>
