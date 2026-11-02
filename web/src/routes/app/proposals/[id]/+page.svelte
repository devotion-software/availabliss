<script lang="ts">
	import { goto, invalidate } from '$app/navigation';
	import type { Member, ResponseKind } from '#lib/types';
	import { cancelProposal, overrideResponse, respondToProposal } from '#lib/client/proposals';
	import { live, proposalOf } from '#lib/client/live.svelte';
	import { useFormat } from '#lib/client/format';
	import { isAdminRole, memberMap, memberLabel } from '#lib/client/members';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import SyncRing from '#lib/components/ui/SyncRing.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import OverrideSheet from '#lib/components/proposals/OverrideSheet.svelte';
	import EveryoneIn from '#lib/components/proposals/EveryoneIn.svelte';
	import { RESPONSE_PILL, acceptedCount, askedCount, myResponse } from '#lib/components/proposals/responses';
	import { clashText } from '#lib/components/proposals/clash';

	let { data } = $props();

	const fmt = useFormat();
	// Seeded from the load, then replaced by responses and live events.
	let proposal = $derived(data.proposal);
	const group = $derived(data.group);
	const me = $derived(data.user.id);
	const members = $derived(memberMap(group));
	const admin = $derived(isAdminRole(group.role));
	const yes = $derived(acceptedCount(proposal));
	const total = $derived(askedCount(proposal, group));
	const mine = $derived(myResponse(proposal, me));
	const canWithdraw = $derived(proposal.status === 'open' && (proposal.createdBy === me || admin));
	const startHint = $derived(proposal.allDay ? '' : fmt.current.startsAt(proposal.start));
	const sub = $derived([startHint, proposal.location].filter(Boolean).join(' · '));
	const busyIds = $derived(proposal.status === 'open' ? (proposal.busyMemberIds ?? []) : []);
	const clash = $derived(clashText(busyIds, members, me));
	const iAmBusy = $derived(busyIds.includes(me));

	const rows = $derived(
		group.members.map((m) => {
			const r = proposal.responses.find((x) => x.userId === m.userId);
			return { member: m, response: (r?.response ?? 'pending') as ResponseKind, setBy: r?.setBy ?? null };
		})
	);

	function note(m: Member, response: ResponseKind, setBy: string | null): string {
		if (m.userId === proposal.createdBy) return 'Proposed this';
		if (setBy && setBy !== m.userId) return `Set by ${setBy === me ? 'you' : memberLabel(members, setBy, me, { first: true })} (admin)`;
		if (!m.synced) return 'Not synced yet';
		if (response === 'pending' && busyIds.includes(m.userId)) return 'Busy then · hasn’t replied';
		if (response === 'accepted' && busyIds.includes(m.userId)) return 'Busy then, but in';
		if (response === 'pending') return 'Hasn’t replied';
		if (response === 'declined') return 'Can’t make it';
		if (m.role === 'owner') return 'Owner';
		if (m.role === 'admin') return 'Admin';
		return 'Synced';
	}

	$effect(() => {
		const id = proposal.id;
		return live.on(['proposal.updated', 'proposal.confirmed'], (e) => {
			const next = proposalOf(e);
			if (next.id === id) proposal = next;
		});
	});

	let busy = $state<'accepted' | 'declined' | null>(null);
	async function respond(response: 'accepted' | 'declined') {
		busy = response;
		try {
			proposal = await respondToProposal(proposal.id, response);
			if (proposal.status !== 'confirmed') toasts.success(response === 'accepted' ? "You're in" : 'Thanks for letting everyone know');
			invalidate('app:groups');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = null;
		}
	}

	let overrideFor = $state<{ userId: string; name: string; current: ResponseKind } | null>(null);
	let overrideOpen = $state(false);
	function openOverride(userId: string, current: ResponseKind) {
		overrideFor = { userId, name: memberLabel(members, userId, me, { first: true }), current };
		overrideOpen = true;
	}
	async function applyOverride(response: ResponseKind) {
		if (!overrideFor) return;
		try {
			proposal = await overrideResponse(proposal.id, overrideFor.userId, response);
		} catch (err) {
			toasts.error(err);
		}
	}

	let withdrawOpen = $state(false);
	async function withdraw() {
		try {
			await cancelProposal(proposal.id);
			toasts.success('Proposal withdrawn');
			await goto(`/app/groups/${group.id}`, { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}
</script>

<svelte:head><title>Proposal · {group.name} · Availabliss</title></svelte:head>

{#if proposal.status === 'confirmed'}
	<EveryoneIn {proposal} {group} />
{:else}
	<div class="page">
		<PageHeader title="Proposal" subtitle={group.name} backHref="/app/groups/{group.id}" backLabel="Back to calendar" />

		{#if proposal.status === 'cancelled'}
			<div class="glass panel">
				<EmptyState
					icon="x"
					title="This proposal was withdrawn"
					message="{fmt.current.when(proposal.start, proposal.end, proposal.allDay)} is off the table. Pick another time from the group calendar."
				>
					{#snippet actions()}
						<Button href="/app/groups/{group.id}" variant="solid" size="sm">Back to calendar</Button>
					{/snippet}
				</EmptyState>
			</div>
		{:else}
			<div class="columns">
				<div class="main">
					<section class="summary glass">
						<div class="summary-text">
							<span class="by">
								{proposal.createdBy === me ? 'You' : memberLabel(members, proposal.createdBy, me, { first: true })} proposed · {fmt.current.relative(
									proposal.createdAt
								)}
							</span>
							<h2>{proposal.title ?? fmt.current.when(proposal.start, proposal.end, proposal.allDay)}</h2>
							{#if proposal.title}<span class="sub">{fmt.current.when(proposal.start, proposal.end, proposal.allDay)}</span>{/if}
							{#if sub}<span class="sub">{sub}</span>{/if}
						</div>
						<SyncRing value={yes} total={total} size={76} stroke={7} tone="ok" label="{yes} of {total} accepted">
							<span class="ring-num">{yes}/{total}</span>
							<span class="ring-label">accepted</span>
						</SyncRing>
					</section>

					{#if clash}
						<div class="clash" role="status">
							<Icon name="alert" size={18} />
							<span>
								<strong>{clash}.</strong>
								{busyIds.length === 1 && iAmBusy
									? 'Your calendar or blocked time overlaps. You can still accept if you can move things.'
									: 'Their calendars or blocked time overlap. They can still accept if they can move things.'}
							</span>
						</div>
					{/if}

					{#if proposal.note}
						<p class="note glass">{proposal.note}</p>
					{/if}

					<section class="people glass" aria-label="Replies">
						{#each rows as row (row.member.userId)}
							{@const pill = RESPONSE_PILL[row.response]}
							<div class="person">
								<Avatar name={row.member.name} id={row.member.userId} image={row.member.image} size={34} />
								<div class="who">
									<span class="name">{memberLabel(members, row.member.userId, me)}</span>
									<span class="why">{note(row.member, row.response, row.setBy)}</span>
								</div>
								{#if admin && row.member.userId !== me && (row.response !== 'accepted' || row.setBy)}
									<button
										class="override"
										aria-label="Override {row.member.name}'s reply"
										onclick={() => openOverride(row.member.userId, row.response)}>Override</button
									>
								{/if}
								<StatusPill tone={pill.tone}>{pill.text}</StatusPill>
							</div>
						{/each}
					</section>

					{#if admin}
						<p class="hint">
							<Icon name="shield" size={14} />
							As an admin you can set anyone's reply.
						</p>
					{/if}
					{#if canWithdraw}
						<Button variant="ghost" size="sm" icon="trash" onclick={() => (withdrawOpen = true)}>Withdraw proposal</Button>
					{/if}
				</div>
			</div>

			<div class="reply glass" role="group" aria-labelledby="reply-q">
				<span id="reply-q" class="q">
					{mine === 'accepted'
						? "You're in. Changed your mind?"
						: mine === 'declined'
							? "You said you can't make it"
							: iAmBusy
								? "You're busy then. Can you still make it?"
								: 'Can you make it?'}
				</span>
				<div class="buttons">
					<Button
						variant="secondary"
						loading={busy === 'declined'}
						disabled={busy !== null || mine === 'declined'}
						aria-pressed={mine === 'declined'}
						onclick={() => respond('declined')}>Can't make it</Button
					>
					<Button
						variant="solid"
						icon={mine === 'accepted' ? 'check' : undefined}
						loading={busy === 'accepted'}
						disabled={busy !== null || mine === 'accepted'}
						aria-pressed={mine === 'accepted'}
						onclick={() => respond('accepted')}>{mine === 'accepted' ? 'Accepted' : 'Accept'}</Button
					>
				</div>
			</div>
		{/if}
	</div>
{/if}

{#if overrideFor}
	<OverrideSheet bind:open={overrideOpen} name={overrideFor.name} current={overrideFor.current} onpick={applyOverride} />
{/if}
<ConfirmSheet
	bind:open={withdrawOpen}
	title="Withdraw this proposal?"
	message="Everyone's replies are cleared and it disappears from the chat."
	confirmLabel="Withdraw"
	destructive
	onconfirm={withdraw}
/>

<style>
	.page {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 14px;
		max-width: 640px;
		width: 100%;
		margin: 0 auto;
	}
	.columns,
	.main {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.panel {
		border-radius: 28px;
	}
	.summary {
		border-radius: 28px;
		padding: 18px;
		display: flex;
		gap: 14px;
		align-items: center;
	}
	.summary-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.by {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	h2 {
		font-size: 26px;
		font-weight: 700;
		line-height: 1.05;
	}
	.sub {
		font-size: 13px;
		color: var(--muted);
	}
	.ring-num {
		font-family: var(--font-display);
		font-size: 20px;
		font-weight: 700;
		letter-spacing: -0.025em;
	}
	.ring-label {
		font-size: 10px;
		color: var(--muted);
		margin-top: 2px;
	}
	.note {
		border-radius: 20px;
		padding: 12px 14px;
		font-size: 14px;
		white-space: pre-line;
	}
	.people {
		border-radius: 24px;
		padding: 4px 14px;
	}
	.person {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 56px;
		padding: 6px 0;
	}
	.person + .person {
		border-top: 1px solid var(--line);
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
	.override {
		height: 34px;
		padding: 0 10px;
		border-radius: 10px;
		border: 1px solid var(--line-2);
		background: var(--surface);
		font-size: 12px;
		font-weight: 600;
		position: relative;
	}
	/* Grow the hit area to 44px without changing the look. */
	.override::after {
		content: '';
		position: absolute;
		inset: -5px 0;
	}
	.hint {
		display: flex;
		gap: 6px;
		align-items: center;
		font-size: 12px;
		color: var(--muted);
	}
	.main > :global(.btn.ghost) {
		align-self: flex-start;
	}
	.reply {
		position: sticky;
		bottom: max(16px, env(safe-area-inset-bottom));
		margin-top: auto;
		border-radius: 26px;
		padding: 12px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		z-index: 5;
	}
	.q {
		font-size: 13px;
		font-weight: 600;
		padding-left: 4px;
	}
	.buttons {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}
	.clash {
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
	.clash :global(svg) {
		flex: none;
		margin-top: 1px;
	}
</style>
