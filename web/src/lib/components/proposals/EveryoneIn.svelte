<script lang="ts">
	import type { Group, Proposal } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import { getEventCached } from '#lib/client/events';
	import Logo from '#lib/components/ui/Logo.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import { memberMap, memberLabel } from '#lib/client/members';

	/** The confirmed state: "Everyone's in." with who's coming, Add to calendar and Go to chat. */
	interface Props {
		proposal: Proposal;
		group: Group;
		/** The signed-in user's id, to mark "(you)". */
		me: string;
	}
	let { proposal, group, me }: Props = $props();

	const fmt = useFormat();
	const range = $derived(fmt.current.when(proposal.start, proposal.end, proposal.allDay));
	const parts = $derived(fmt.current.whenParts(proposal.start, proposal.end, proposal.allDay));
	const members = $derived(memberMap(group));
	// Proposer first, then the group's order.
	const ordered = $derived([
		...group.members.filter((m) => m.userId === proposal.createdBy),
		...group.members.filter((m) => m.userId !== proposal.createdBy)
	]);
	const accepted = $derived(new Set(proposal.responses.filter((r) => r.response === 'accepted').map((r) => r.userId)));
	// Everyone who accepted (everyone in the group when it confirmed).
	const coming = $derived(ordered.filter((m) => accepted.has(m.userId)));
	// A proposal confirms only once every member has accepted, and replies then close, so anyone
	// without an acceptance joined the group afterwards.
	const joinedLater = $derived(ordered.filter((m) => !accepted.has(m.userId)));
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
	<div class="status glass" role="status">
		<Logo size={34} />
		<div class="status-text">
			<span class="who">Availabliss <span class="now">· confirmed</span></span>
			<span>Everyone accepted {range} for {group.name}</span>
		</div>
	</div>

	<div class="hero">
		<span class="bloom"><Logo size={132} /></span>
		<h1>Everyone's in.</h1>
		<p class="when">
			<span class="lead">{count === 2 ? 'Both of you' : `All ${count} of you`} can make</span>
			<span class="date">{parts.date}</span>
			{#if parts.time}<span class="time">{parts.time}</span>{/if}
			{#if proposal.location}<span class="place">in {proposal.location}</span>{/if}
		</p>
		{#if proposal.title}<p class="title">{proposal.title}</p>{/if}
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
		<Button variant="secondary" size="lg" icon="chat" block href="/app/groups/{group.id}/chat">Go to chat</Button>
		<a class="back" href="/app/groups/{group.id}">Back to {group.name}</a>
	</div>
</div>

<style>
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
	.who {
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
	.back {
		align-self: center;
		min-height: 40px;
		display: flex;
		align-items: center;
		font-size: 13px;
		font-weight: 600;
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
