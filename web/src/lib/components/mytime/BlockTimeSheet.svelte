<script lang="ts">
	import type { ManualBlock, Repeat } from '#lib/types';
	import { createBlockSchema } from '#lib/schemas';
	import { createBlock, updateBlock } from '#lib/client/blocks';
	import { addDays, dayKeyIn, timeIn, todayIn, zonedToIso } from '#lib/client/dates';
	import { formatDayKey } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import DateRangeInput from '#lib/components/ui/DateRangeInput.svelte';
	import MonthCalendar from '#lib/components/ui/MonthCalendar.svelte';
	import { clock, DAY_MINUTES } from './parts';
	import { repeatChoices, repeatLabel } from './repeat';

	/** "Block a time": the date range (all day or times, over one or several days), repeat, ends, and a private note. */
	interface Props {
		open: boolean;
		timeZone: string;
		/** Edit this block instead of creating one. */
		block?: ManualBlock | null;
		defaultDay: string;
		/** Prefill exact times (minutes since midnight on `defaultDay`), e.g. from a tap on the week grid. */
		defaultRange?: { from: number; to: number } | null;
		/** Start on "All day", e.g. from the "All day…" and "Away for a few days…" choices. */
		defaultAllDay?: boolean;
		/** Prefill the last day of an all-day block that runs over several days. */
		defaultLastDay?: string | null;
		onsaved: (block: ManualBlock) => void;
	}
	let {
		open = $bindable(),
		timeZone,
		block = null,
		defaultDay,
		defaultRange = null,
		defaultAllDay = false,
		defaultLastDay = null,
		onsaved
	}: Props = $props();

	const uid = $props.id();

	let startDate = $state('');
	let startTime = $state('09:00');
	/** All day: the last day (inclusive). Timed: the day the block ends. */
	let endDate = $state('');
	let endTime = $state('10:00');
	let allDay = $state(false);
	let repeat = $state<Repeat>('none');
	let until = $state<string | null>(null);
	let note = $state('');
	let error = $state<string | null>(null);
	let busy = $state(false);
	let panel = $state<'repeat' | 'ends' | null>(null);

	// Seed the form whenever the sheet opens.
	$effect.pre(() => {
		if (!open) return;
		error = null;
		panel = null;
		if (!block) {
			startDate = defaultDay;
			allDay = defaultAllDay;
			startTime = defaultRange ? clock(defaultRange.from) : '09:00';
			if (allDay) {
				endDate = defaultLastDay && defaultLastDay > defaultDay ? defaultLastDay : defaultDay;
				endTime = '10:00';
			} else {
				// A range to 24:00 ends at midnight the next day.
				const to = defaultRange?.to ?? 10 * 60;
				endDate = to >= DAY_MINUTES ? addDays(defaultDay, 1) : defaultDay;
				endTime = clock(to % DAY_MINUTES);
			}
			repeat = 'none';
			until = null;
			note = '';
			return;
		}
		startDate = dayKeyIn(block.start, timeZone);
		allDay = block.allDay;
		repeat = block.repeat;
		until = block.until;
		note = block.note ?? '';
		if (block.allDay) {
			const last = addDays(dayKeyIn(block.end, timeZone), -1);
			endDate = last > startDate ? last : startDate;
			startTime = '09:00';
			endTime = '10:00';
			return;
		}
		startTime = timeIn(block.start, timeZone);
		endDate = dayKeyIn(block.end, timeZone);
		endTime = timeIn(block.end, timeZone);
	});

	const today = $derived(todayIn(timeZone));
	/** Past days are read-only: new blocks start today or later; one being edited may keep its own (earlier) start. */
	const earliest = $derived.by(() => {
		const own = block ? dayKeyIn(block.start, timeZone) : today;
		return own < today ? own : today;
	});
	// A repeat this app doesn't know yet (from a newer server) still shows, as "Repeats".
	const choices = $derived.by(() => {
		const list = repeatChoices(startDate || today);
		return list.some((c) => c.value === repeat) ? list : [...list, { value: repeat, label: 'Repeats' }];
	});
	const repeatText = $derived(repeatLabel(repeat, startDate || today));
	const endsText = $derived(until ? `On ${formatDayKey(until)}` : 'Never');

	const togglePanel = (p: 'repeat' | 'ends') => (panel = panel === p ? null : p);
	function chooseRepeat(value: Repeat) {
		repeat = value;
		panel = null;
	}
	function chooseUntil(value: string | null) {
		until = value;
		panel = null;
	}

	function range(): { start: string; end: string; allDay: boolean } | string {
		if (!startDate || !endDate) return 'Choose a date';
		if (startDate < earliest) return 'That day has passed. Block time from today onwards.';
		if (allDay) {
			if (endDate < startDate) return 'The last day is before the first';
			return { start: zonedToIso(startDate, '00:00', timeZone), end: zonedToIso(addDays(endDate, 1), '00:00', timeZone), allDay: true };
		}
		if (!startTime || !endTime) return 'Choose a start and end time';
		const start = zonedToIso(startDate, startTime, timeZone);
		const end = zonedToIso(endDate, endTime, timeZone);
		if (end <= start) return 'It ends before it starts';
		return { start, end, allDay: false };
	}

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const r = range();
		if (typeof r === 'string') {
			error = r;
			return;
		}
		const repeatUntil = repeat === 'none' ? null : until;
		if (repeatUntil && repeatUntil < startDate) {
			error = 'The repeat ends before the first date';
			return;
		}
		const parsed = createBlockSchema.safeParse({ ...r, repeat, until: repeatUntil, note });
		if (!parsed.success) {
			error = parsed.error.issues[0]?.message ?? 'Check the details';
			return;
		}
		busy = true;
		error = null;
		try {
			const saved = block ? await updateBlock(block.id, parsed.data) : await createBlock(parsed.data);
			onsaved(saved);
			toasts.success(block ? 'Block updated' : 'Time blocked');
			open = false;
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title={block ? 'Edit block' : 'Block a time'}>
	<form class="form" onsubmit={submit} novalidate>
		<DateRangeInput bind:startDate bind:startTime bind:endDate bind:endTime bind:allDay {today} minDate={earliest} />

		<div class="glass options">
			<button type="button" class="row" aria-expanded={panel === 'repeat'} aria-controls="repeat-{uid}" onclick={() => togglePanel('repeat')}>
				<span class="k"><Icon name="repeat" size={18} />Repeat</span>
				<span class="v">{repeatText}</span>
				<Icon name="chevron-down" size={16} class={panel === 'repeat' ? 'flip' : ''} />
			</button>
			{#if panel === 'repeat'}
				<div class="list" id="repeat-{uid}" role="radiogroup" aria-label="Repeat">
					{#each choices as choice (choice.value)}
						<button
							type="button"
							role="radio"
							aria-checked={repeat === choice.value}
							class:sel={repeat === choice.value}
							onclick={() => chooseRepeat(choice.value)}
						>
							<span>{choice.label}</span>
							{#if repeat === choice.value}<Icon name="check" size={18} />{/if}
						</button>
					{/each}
				</div>
			{/if}

			{#if repeat !== 'none'}
				<button type="button" class="row" aria-expanded={panel === 'ends'} aria-controls="ends-{uid}" onclick={() => togglePanel('ends')}>
					<span class="k"><Icon name="calendar" size={18} />Ends</span>
					<span class="v">{endsText}</span>
					<Icon name="chevron-down" size={16} class={panel === 'ends' ? 'flip' : ''} />
				</button>
				{#if panel === 'ends'}
					<div class="ends" id="ends-{uid}">
						<button type="button" class="never" class:sel={!until} aria-pressed={!until} onclick={() => chooseUntil(null)}>
							<span>Never</span>
							{#if !until}<Icon name="check" size={18} />{/if}
						</button>
						<MonthCalendar value={until ?? ''} min={startDate} {today} label="Last date it repeats" onpick={(d) => chooseUntil(d)} />
					</div>
				{/if}
			{/if}
		</div>

		<TextField label="Note (only you see this)" bind:value={note} placeholder="e.g. Wedding" maxlength={120} />

		{#if error}<p class="error" role="alert">{error}</p>{/if}
		<Button type="submit" variant="accent" size="md" block loading={busy}>{block ? 'Save changes' : 'Block this time'}</Button>
	</form>
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.options {
		border-radius: 22px;
		padding: 4px 10px;
		display: flex;
		flex-direction: column;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 52px;
		padding: 0 6px;
		text-align: left;
		color: var(--ink);
	}
	.row + .row,
	.list + .row {
		border-top: 1px solid var(--line);
	}
	.k {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		font-size: 15px;
		font-weight: 600;
		flex: none;
	}
	.k :global(svg) {
		color: var(--muted);
	}
	.v {
		flex: 1;
		min-width: 0;
		text-align: right;
		font-size: 14px;
		font-weight: 600;
		line-height: 1.3;
		color: var(--accent-ink);
		padding: 6px 0;
		text-wrap: balance;
	}
	.row > :global(svg:last-child) {
		flex: none;
		color: var(--muted);
		transition: transform 0.2s ease;
	}
	.row > :global(svg.flip) {
		transform: rotate(180deg);
	}
	.list,
	.ends {
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 2px 0 8px;
	}
	.list button,
	.never {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		min-height: 44px;
		padding: 0 12px;
		border-radius: 12px;
		text-align: left;
		font-size: 15px;
		font-weight: 500;
		color: var(--ink);
	}
	.list button:hover:not(.sel),
	.never:hover:not(.sel) {
		background: var(--line);
	}
	.list button.sel,
	.never.sel {
		background: var(--accent-soft);
		color: var(--accent-ink);
		font-weight: 600;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
