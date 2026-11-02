<script lang="ts">
	import type { AvailabilityDay, Member, Slot } from '#lib/types';
	import { formatDayKey, useFormat } from '#lib/client/format';
	import Button from '#lib/components/ui/Button.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import { busyNames } from './grid';

	/** What the selected day means for the group, with the next steps. */
	interface Props {
		date: string;
		day: AvailabilityDay | undefined;
		slot: Slot | null;
		members: Map<string, Member>;
		/** "weekend", "evening", … for "Propose this weekend". */
		noun: string;
		onpropose: (slot: Slot) => void;
		/** Propose a day that doesn't work for everyone (the proposal sheet warns who's busy). */
		onproposeday?: (date: string) => void;
		onseeday: () => void;
	}
	let { date, day, slot, members, noun, onpropose, onproposeday, onseeday }: Props = $props();

	const fmt = useFormat();
	const works = $derived(day?.status === 'match' && slot !== null);
	const title = $derived.by(() => {
		const label = slot && works ? slot.label : formatDayKey(date);
		return date === fmt.current.today() && !works ? `Today · ${label}` : label;
	});
	const sub = $derived.by(() => {
		if (!day) return '';
		let text = '';
		if (works && slot) {
			const t = fmt.current.time(slot.start);
			text = t === '00:00' ? 'Everyone synced is free' : `Everyone synced is free from ${fmt.current.startsAt(slot.start).replace('From ', '')}`;
		} else if (day.status === 'busy') {
			const n = day.busyMemberIds.length;
			text = n ? `${busyNames(day.busyMemberIds, members)} ${n === 1 ? 'is' : 'are'} busy` : 'Someone is busy';
		} else if (day.status === 'free') text = "Everyone's free, but not for the whole timing";
		else if (day.status === 'hidden') text = "Outside this group's timing";
		return day.holiday ? `${text} · ${day.holiday}` : text;
	});
</script>

<section class="card glass" aria-live="polite" aria-label="Selected day">
	<div class="top">
		<div class="titles">
			<h3>{title}</h3>
			<span class="sub">{sub}</span>
		</div>
		<StatusPill tone={works ? 'mint' : 'neutral'}>{works ? 'Works' : 'Doesn’t fit'}</StatusPill>
	</div>
	<div class="actions">
		{#if works && slot}
			<Button variant="accent" size="sm" class="act" onclick={() => onpropose(slot)}>Propose this {noun}</Button>
		{:else if onproposeday && (day?.status === 'busy' || day?.status === 'free')}
			<Button variant="secondary" size="sm" class="act" onclick={() => onproposeday(date)}>Propose anyway</Button>
		{/if}
		<Button variant="secondary" size="sm" class="act" onclick={onseeday}>See who's free</Button>
	</div>
</section>

<style>
	.card {
		border-radius: 22px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
	.titles {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	h3 {
		font-size: 16px;
		font-weight: 650;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	.actions {
		display: flex;
		gap: 8px;
	}
	.actions :global(.act) {
		flex: 1;
		height: 44px;
		border-radius: 14px;
		font-size: 14px;
	}
</style>
