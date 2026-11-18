<script lang="ts">
	import type { Group, Proposal } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import { getEventCached } from '#lib/client/events';
	import Logo from '#lib/components/ui/Logo.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import { memberMap, memberLabel } from '#lib/client/members';
	import { everyoneIn, listNames } from './responses';

	/**
	 * The confirmed state: "Everyone's in." (or "It's on." when confirmed before everyone accepted)
	 * with Add to calendar, who's coming, who can't or never replied; Go to chat in the top corner.
	 */
	interface Props {
		proposal: Proposal;
		group: Group;
		/** The signed-in user's id, to mark "(you)". */
		me: string;
		/** Change your own reply once confirmed. */
		onrespond: (response: 'accepted' | 'declined') => Promise<unknown>;
		/** "Keep the plan" after someone dropped out (proposer or admin). */
		onkeep: () => Promise<unknown>;
		/** "Cancel and find another date" (proposer or admin). */
		oncancelplan: () => Promise<unknown>;
	}
	let { proposal, group, me, onrespond, onkeep, oncancelplan }: Props = $props();

	const fmt = useFormat();
	const range = $derived(fmt.current.when(proposal.start, proposal.end, proposal.allDay));
	const parts = $derived(fmt.current.whenParts(proposal.start, proposal.end, proposal.allDay));
	const members = $derived(memberMap(group));
	// Proposer first, then the group's order.
	const ordered = $derived([
		...group.members.filter((m) => m.userId === proposal.createdBy),
		...group.members.filter((m) => m.userId !== proposal.createdBy)
	]);
	const replies = $derived(new Map(proposal.responses.map((r) => [r.userId, r.response])));
	const coming = $derived(ordered.filter((m) => replies.get(m.userId) === 'accepted'));
	// Confirmed anyway without them: said they can't make it, or never replied.
	const cantMakeIt = $derived(ordered.filter((m) => replies.get(m.userId) === 'declined'));
	const notReplied = $derived(ordered.filter((m) => replies.get(m.userId) === 'pending'));
	// Replies are only asked of the members at the time, so anyone without one joined the group afterwards.
	const joinedLater = $derived(ordered.filter((m) => !replies.has(m.userId)));
	const allIn = $derived(everyoneIn(proposal));

	// Someone who was coming dropped out: the proposer or an admin keeps the plan or cancels it.
	const reviewer = $derived(proposal.createdBy === me || group.role === 'owner' || group.role === 'admin');
	const needsReview = $derived(reviewer && !!proposal.needsReview);
	const reviewHeadline = $derived.by(() => {
		const names = ordered
			.filter((m) => proposal.responses.some((r) => r.userId === m.userId && r.droppedOut))
			.map((m) => (m.userId === me ? 'You' : memberLabel(members, m.userId, me, { first: true })));
		return names.length ? `${listNames(names)} can’t make it any more` : 'Someone can’t make it any more';
	});
	let reviewOpen = $state(false);
	// The review sheet opens by itself once per visit; after that, the panel's button opens it.
	let prompted = false;
	$effect(() => {
		if (!needsReview || prompted) return;
		prompted = true;
		const timer = setTimeout(() => (reviewOpen = true), 600);
		return () => clearTimeout(timer);
	});

	// Your own reply, changeable once confirmed (only if you were asked: not if you joined since).
	const mine = $derived(replies.get(me));
	let replyOpen = $state(false);
	let busy = $state<'keep' | 'cancel' | 'accepted' | 'declined' | null>(null);
	async function run(kind: NonNullable<typeof busy>, action: () => Promise<unknown>) {
		busy = kind;
		try {
			await action();
			reviewOpen = false;
			replyOpen = false;
		} catch {
			// The page shows the error.
		} finally {
			busy = null;
		}
	}
	const count = $derived(coming.length || proposal.responses.length || group.members.length);

	// The confirmed event's cover photo, if someone has added one.
	let cover = $state<string | null>(null);
	$effect(() => {
		const id = proposal.eventId;
		cover = null;
		if (!id) return;
		let alive = true;
		getEventCached(id)
			.then((e) => alive && (cover = e.image))
			.catch(() => {});
		return () => {
			alive = false;
		};
	});
</script>

<div class="confirmed">
	<div class="top">
		<IconButton href="/app/groups/{group.id}" icon="chevron-left" label="Back to {group.name}" />
		<IconButton href="/app/groups/{group.id}/chat" icon="chat" label="Go to chat" />
	</div>
	{#if needsReview}
		<div class="review">
			<div class="dropped" role="status">
				<Icon name="alert" size={18} />
				<span><strong>{reviewHeadline}.</strong> Keep the plan with who can make it, or cancel it and find another date.</span>
			</div>
			<Button variant="secondary" block disabled={busy !== null} onclick={() => (reviewOpen = true)}>Keep or cancel…</Button>
		</div>
	{/if}
	<div class="status glass" role="status">
		<Logo size={34} />
		<div class="status-text">
			<span class="who">Availabliss <span class="now">· confirmed</span></span>
			<span>{allIn ? 'Everyone accepted' : "It's on:"} {range} for {group.name}</span>
		</div>
	</div>

	<div class="hero">
		<span class="bloom"><Logo size={132} /></span>
		<h1>{allIn ? "Everyone's in." : "It's on."}</h1>
		<p class="when">
			<span class="lead"
				>{allIn ? (count === 2 ? 'Both of you' : `All ${count} of you`) : `${coming.length} of ${proposal.responses.length}`} can make</span
			>
			<span class="date">{parts.date}</span>
			{#if parts.time}<span class="time">{parts.time}</span>{/if}
			{#if proposal.location}<span class="place">in {proposal.location}</span>{/if}
		</p>
		{#if proposal.title}<p class="title">{proposal.title}</p>{/if}
	</div>

	<!-- The first card, above Who's coming: Add to calendar, and changing your reply. -->
	<div class="card glass">
		{#if cover}<img class="cover" src={cover} alt="" onerror={() => (cover = null)} />{/if}
		{#if proposal.eventId}
			<AddToCalendar
				eventId={proposal.eventId}
				size="lg"
				item={{
					title: proposal.title ?? group.name,
					start: proposal.start,
					end: proposal.end,
					allDay: proposal.allDay,
					location: proposal.location,
					note: proposal.note
				}}
			/>
		{/if}
		{#if mine}
			<button type="button" class="change" disabled={busy !== null} onclick={() => (replyOpen = true)}>
				{mine === 'accepted' ? 'Can’t make it any more?' : mine === 'declined' ? 'Can make it after all?' : 'Can you make it?'}
			</button>
		{/if}
	</div>

	{#if coming.length}
		<section class="people glass" aria-labelledby="coming-heading">
			<h2 class="people-head" id="coming-heading"><span>Who’s coming</span><span>{coming.length}</span></h2>
			{#each coming as m (m.userId)}
				<div class="person">
					<Avatar name={m.name} id={m.userId} image={m.image} size={34} />
					<div class="who">
						<span class="name">{memberLabel(members, m.userId, me)}</span>
						{#if m.userId === proposal.createdBy}<span class="why">Proposed this</span>{/if}
					</div>
				</div>
			{/each}
		</section>
	{/if}
	{#each [{ id: 'cant', title: 'Can’t make it', people: cantMakeIt }, { id: 'pending', title: 'Not replied', people: notReplied }] as list (list.id)}
		{#if list.people.length}
			<section class="people glass quiet" aria-labelledby="{list.id}-heading">
				<h2 class="people-head" id="{list.id}-heading"><span>{list.title}</span><span>{list.people.length}</span></h2>
				{#each list.people as m (m.userId)}
					<div class="person">
						<Avatar name={m.name} id={m.userId} image={m.image} size={28} />
						<div class="who">
							<span class="name">{memberLabel(members, m.userId, me)}</span>
							{#if proposal.responses.some((r) => r.userId === m.userId && r.droppedOut)}<span class="why">Can’t make it any more</span>{/if}
						</div>
					</div>
				{/each}
			</section>
		{/if}
	{/each}
	{#if joinedLater.length}
		<section class="people glass quiet" aria-labelledby="later-heading">
			<h2 class="people-head" id="later-heading"><span>Joined since</span></h2>
			{#each joinedLater as m (m.userId)}
				<div class="person">
					<Avatar name={m.name} id={m.userId} image={m.image} size={28} />
					<div class="who">
						<span class="name">{memberLabel(members, m.userId, me)}</span>
						<span class="why">Joined after this was confirmed</span>
					</div>
				</div>
			{/each}
		</section>
	{/if}

</div>

<Sheet
	bind:open={reviewOpen}
	title={reviewHeadline}
	description="The plan’s still on. Keep it with who can make it, or cancel it and find another date."
	width={420}
>
	<div class="choices">
		<Button variant="solid" icon="check" block loading={busy === 'keep'} disabled={busy !== null} onclick={() => run('keep', onkeep)}
			>Keep the plan</Button
		>
		<Button variant="danger" icon="calendar" block loading={busy === 'cancel'} disabled={busy !== null} onclick={() => run('cancel', oncancelplan)}
			>Cancel and find another date</Button
		>
		<Button variant="secondary" block onclick={() => (reviewOpen = false)}>Decide later</Button>
	</div>
</Sheet>

<Sheet
	bind:open={replyOpen}
	title={mine === 'accepted' ? 'Can’t make it any more?' : 'Can you make it?'}
	description={mine === 'accepted'
		? 'The plan stays on. Whoever proposed it and the admins will be told, so they can keep it or find another date.'
		: 'Let everyone know whether you’re coming.'}
	width={420}
>
	<div class="choices">
		{#if mine !== 'accepted'}
			<Button variant="solid" icon="check" block loading={busy === 'accepted'} disabled={busy !== null} onclick={() => run('accepted', () => onrespond('accepted'))}
				>I can make it</Button
			>
		{/if}
		{#if mine !== 'declined'}
			<Button
				variant={mine === 'accepted' ? 'danger' : 'secondary'}
				block
				loading={busy === 'declined'}
				disabled={busy !== null}
				onclick={() => run('declined', () => onrespond('declined'))}>I can’t make it</Button
			>
		{/if}
		<Button variant="secondary" block onclick={() => (replyOpen = false)}>Cancel</Button>
	</div>
</Sheet>

<style>
	.review {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.dropped {
		display: flex;
		gap: 10px;
		align-items: flex-start;
		padding: 12px 14px;
		border-radius: 18px;
		background: var(--warn-bg);
		color: var(--warn-ink);
		font-size: 13px;
		line-height: 1.4;
	}
	.dropped :global(svg) {
		flex: none;
		margin-top: 1px;
	}
	.choices {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.change {
		align-self: center;
		min-height: 40px;
		padding: 0 12px;
		background: none;
		border: 0;
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
	}
	.confirmed {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 18px;
		max-width: 520px;
		width: 100%;
		margin: 0 auto;
	}
	.status {
		border-radius: 22px;
		padding: 12px 14px;
		display: flex;
		gap: 12px;
		align-items: center;
		font-size: 13px;
		animation: drop 0.5s var(--ease-spring) both;
	}
	.status-text {
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.who {
		font-weight: 700;
	}
	.now {
		font-weight: 500;
		color: var(--muted);
	}
	.hero {
		flex: 1;
		min-height: 320px;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 18px;
		text-align: center;
		padding: 0 10px;
	}
	.bloom {
		animation: bloom 0.7s var(--ease-spring) 0.15s both;
	}
	h1 {
		font-size: 40px;
		font-weight: 750;
		line-height: 1;
	}
	p {
		font-size: 16px;
		color: var(--muted);
		line-height: 1.4;
	}
	.when {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 6px;
	}
	.lead {
		margin-bottom: 2px;
	}
	.date {
		font-family: var(--font-display);
		font-size: 30px;
		font-weight: 700;
		line-height: 1.05;
		letter-spacing: -0.025em;
		color: var(--ink);
	}
	.time {
		font-family: var(--font-display);
		font-size: 22px;
		font-weight: 650;
		line-height: 1.15;
		letter-spacing: -0.025em;
		font-variant-numeric: tabular-nums;
		color: var(--ink);
	}
	.place {
		margin-top: 2px;
	}
	.title {
		font-weight: 600;
		color: var(--ink);
	}
	.people {
		border-radius: 24px;
		padding: 4px 14px;
	}
	.people-head {
		display: flex;
		justify-content: space-between;
		padding: 10px 0 6px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.person {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 52px;
		padding: 6px 0;
		border-top: 1px solid var(--line);
	}
	.quiet .person {
		min-height: 48px;
		opacity: 0.65;
	}
	.person .who {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.name {
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.why {
		font-size: 12px;
		color: var(--muted);
	}
	.card {
		border-radius: 28px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.cover {
		display: block;
		width: 100%;
		aspect-ratio: 2 / 1;
		object-fit: cover;
		border-radius: 18px;
		margin-bottom: 8px;
	}
	.top {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	@keyframes drop {
		from {
			transform: translateY(-24px);
			opacity: 0;
		}
	}
	@keyframes bloom {
		0% {
			transform: scale(0.6);
			opacity: 0;
		}
		60% {
			transform: scale(1.08);
			opacity: 1;
		}
		100% {
			transform: scale(1);
		}
	}
</style>
