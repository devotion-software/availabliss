<script lang="ts">
	import type { MyPlan } from '#lib/client/plans';
	import { planHref } from '#lib/client/plans';
	import { dayNumber, formatDayKey, monthShort, useFormat, weekdayName } from '#lib/client/format';
	import { RESPONSE_PILL } from '#lib/components/proposals/responses';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';

	/** One plan in My plans: date tile, title, group and time, your reply if it's not a yes, and whether it's in your calendar. */
	interface Props {
		plan: MyPlan;
		/** This browser added it to a calendar (the web can't know about other devices). */
		inCalendar: boolean;
		/** Can't make it, or long gone: a quieter row. */
		quiet?: boolean;
	}
	let { plan, inCalendar, quiet = false }: Props = $props();

	const fmt = useFormat();
	const day = $derived(fmt.current.dayKey(plan.start));
	const parts = $derived(fmt.current.whenParts(plan.start, plan.end, plan.allDay));
	/** "18:30 – 21:00"; a range for plans over several days; "All day" when the tile already says which day. */
	const when = $derived(parts.time ?? (parts.date === formatDayKey(day) ? 'All day' : parts.date));
	/** Only a maybe or no reply yet earns a pill; a yes is the default here and "can't make it" has its own section. */
	const pill = $derived(plan.myResponse === 'maybe' || plan.myResponse === 'pending' ? RESPONSE_PILL[plan.myResponse] : null);
	const pillText = $derived(plan.myResponse === 'pending' ? 'Not replied' : pill?.text);
</script>

<a class="row" class:quiet href={planHref(plan)}>
	<span class="tile" aria-hidden="true">
		<span class="wd">{weekdayName(day)}</span>
		<span class="dn">{dayNumber(day)}</span>
		<span class="mo">{monthShort(day)}</span>
	</span>
	<span class="text">
		<span class="title">{plan.title}</span>
		<span class="meta">
			<span class="when">{when}</span>
			<span class="dot" aria-hidden="true">·</span>
			<span class="group">{plan.groupName}</span>
		</span>
		<span class="sr-only">{fmt.current.when(plan.start, plan.end, plan.allDay)}</span>
	</span>
	<span class="end">
		{#if pill}<StatusPill tone={pill.tone}>{pillText}</StatusPill>{/if}
		{#if inCalendar}
			<span class="in-cal" title="In your calendar">
				<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
					<rect x="3" y="5" width="18" height="16" rx="3" />
					<path d="M3 10h18M8 3v4M16 3v4M9 15.5l2 2 4-4" />
				</svg>
				<span class="sr-only">In your calendar</span>
			</span>
		{/if}
	</span>
</a>

<style>
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 64px;
		padding: 10px 6px;
		border-radius: 16px;
		text-decoration: none;
		color: var(--ink);
	}
	.row:hover {
		background: var(--surface);
		color: var(--ink);
	}
	.tile {
		width: 44px;
		flex: none;
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 4px 0 5px;
		border-radius: 12px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		line-height: 1.1;
	}
	.wd,
	.mo {
		font-size: 10px;
		font-weight: 650;
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}
	.dn {
		font-size: 18px;
		font-weight: 750;
	}
	.quiet .tile {
		background: var(--line);
		color: var(--neutral-ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.title {
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.quiet .title {
		font-weight: 550;
		color: var(--muted);
	}
	.meta {
		display: flex;
		gap: 5px;
		min-width: 0;
		font-size: 13px;
		color: var(--muted);
	}
	.when {
		flex: none;
		white-space: nowrap;
	}
	.group {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.end {
		flex: none;
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.in-cal {
		display: flex;
		color: var(--ok-ink);
	}
	.quiet .in-cal {
		color: var(--muted);
	}
</style>
