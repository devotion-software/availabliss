<script lang="ts">
	import type { Group, Proposal } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import Logo from '#lib/components/ui/Logo.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';

	/** The confirmed state: "Everyone's in." with Add to calendar. */
	interface Props {
		proposal: Proposal;
		group: Group;
	}
	let { proposal, group }: Props = $props();

	const fmt = useFormat();
	const range = $derived(fmt.current.when(proposal.start, proposal.end, proposal.allDay));
	const count = $derived(proposal.responses.length || group.members.length);
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
		<p>
			{count === 2 ? 'Both of you' : `All ${count} of you`} can make<br />{range}{proposal.location ? ` in ${proposal.location}` : ''}.
		</p>
		{#if proposal.title}<p class="title">{proposal.title}</p>{/if}
	</div>

	<div class="card glass">
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
	.title {
		font-weight: 600;
		color: var(--ink);
	}
	.card {
		border-radius: 28px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 6px;
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
