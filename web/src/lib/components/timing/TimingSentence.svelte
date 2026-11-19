<script lang="ts">
	import type { Requirement, RequirementLength, Weekday } from '#lib/types';
	import {
		PRESETS,
		WEEKDAY_NAMES,
		applyPreset,
		daysPhrase,
		kindPhrase,
		presetOf,
		spanEndPhrase,
		spanStartPhrase,
		windowPhrase,
		type PresetId
	} from '#lib/client/requirements';
	import { formatDayKey, plural } from '#lib/client/format';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Chip from '#lib/components/ui/Chip.svelte';
	import Select from '#lib/components/ui/Select.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import DateRangeCalendar from '#lib/components/ui/DateRangeCalendar.svelte';
	import DayPicker from './DayPicker.svelte';
	import DateRangeChips from './DateRangeChips.svelte';

	/**
	 * The group's timing as one sentence: "We're looking for [a weekend], from [Friday 18:00] to
	 * [Sunday night], any time between [20 Nov] and [28 Feb]." Each underlined part opens a sheet with
	 * a few quick picks and full control (any length, day, time or dates). Like iOS and Android.
	 */
	interface Props {
		requirement: Requirement;
		readonly?: boolean;
	}
	let { requirement = $bindable(), readonly = false }: Props = $props();

	const uid = $props.id();
	type SheetId = 'kind' | 'start' | 'end' | 'window' | 'days' | 'range';
	let open = $state<SheetId | null>(null);

	const span = $derived(requirement.length.type === 'span' ? requirement.length : null);
	const preset = $derived(presetOf(requirement));
	const dayOptions = WEEKDAY_NAMES.map((n, i) => ({ value: String(i), label: n }));
	const valueOf = (e: Event) => (e.currentTarget as HTMLInputElement).value;

	// ── Kind: a preset, or any length ──────────────────────────────────────────
	const QUICK = PRESETS.filter((p) => p.id !== 'custom');
	type Kind = RequirementLength['type'];
	const KINDS: { value: Kind; label: string }[] = [
		{ value: 'duration', label: 'Hours' },
		{ value: 'days', label: 'Days' },
		{ value: 'span', label: 'Start to end' }
	];
	function pickPreset(id: PresetId) {
		requirement = applyPreset(requirement, id);
		open = null;
	}
	function setKind(kind: Kind) {
		if (kind === requirement.length.type) return;
		const lengths: Record<Kind, RequirementLength> = {
			duration: { type: 'duration', minutes: 180 },
			days: { type: 'days', count: 2 },
			span: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 }
		};
		requirement.length = lengths[kind];
		requirement.window = kind === 'duration' ? { from: '08:00', to: '23:00' } : null;
		if (kind === 'days' && requirement.days.length && requirement.days.length < 7) requirement.days = [0, 1, 2, 3, 4, 5, 6];
	}
	const hours = $derived(requirement.length.type === 'duration' ? Math.floor(requirement.length.minutes / 60) : 0);
	const minutes = $derived(requirement.length.type === 'duration' ? requirement.length.minutes % 60 : 0);
	function setDuration(h: number, m: number) {
		const total = Math.min(24 * 60, Math.max(15, h * 60 + m));
		if (requirement.length.type === 'duration') requirement.length.minutes = Math.round(total / 15) * 15;
	}

	// ── Dates: any range, or only certain dates ────────────────────────────────
	let rangeFrom = $state('');
	let rangeTo = $state('');
	let onlyDates = $state(false);
	function openRange() {
		rangeFrom = requirement.range.from;
		rangeTo = requirement.range.to;
		onlyDates = requirement.dates.length > 0;
		open = 'range';
	}
	function applyRange(e: SubmitEvent) {
		e.preventDefault();
		if (rangeFrom && rangeTo) requirement.range = { from: rangeFrom, to: rangeTo };
		if (!onlyDates) requirement.dates = [];
		open = null;
	}

	const sheetTitle: Record<SheetId, string> = {
		kind: 'We’re looking for…',
		start: 'Starting',
		end: 'Ending',
		window: 'Time of day',
		days: 'On which days?',
		range: 'Any time between'
	};
</script>

{#snippet token(id: SheetId, text: string)}
	<button type="button" class="token" disabled={readonly} aria-haspopup="dialog" onclick={() => (id === 'range' ? openRange() : (open = id))}>
		{text}
	</button>
{/snippet}

<p class="sentence" aria-label="Group timing">
	We’re looking for {@render token('kind', kindPhrase(requirement))}{#if span}, from {@render token('start', spanStartPhrase(span))} to {@render token(
			'end',
			spanEndPhrase(span)
		)}{:else}{#if requirement.length.type === 'duration' || requirement.window || preset === 'evening'},
			{@render token('window', windowPhrase(requirement.window))}{/if}, on {@render token(
			'days',
			daysPhrase(requirement.days)
		)}{/if}{#if requirement.dates.length}, on {@render token('range', plural(requirement.dates.length, 'chosen date range'))}.{:else}, any time
		between {@render token('range', formatDayKey(requirement.range.from))} and {@render token('range', formatDayKey(requirement.range.to))}.{/if}
</p>
{#if !readonly}<p class="hint">Tap any underlined part to change it.</p>{/if}

<Sheet open={open !== null} title={open ? sheetTitle[open] : ''} width={460} onclose={() => (open = null)}>
	{#if open === 'kind'}
		<div class="stack">
			<div class="chips">
				{#each QUICK as p (p.id)}
					<Chip selected={preset === p.id} onclick={() => pickPreset(p.id)}>{p.label}</Chip>
				{/each}
			</div>
			<h3 class="eyebrow">Or any length</h3>
			<SegmentedControl options={KINDS} value={requirement.length.type} label="Kind of length" variant="soft" size="sm" block onchange={setKind} />
			{#if requirement.length.type === 'duration'}
				<div class="pair">
					<TextField
						label="Hours"
						type="number"
						min="0"
						max="24"
						inputmode="numeric"
						value={String(hours)}
						onchange={(e) => setDuration(Number(valueOf(e)) || 0, minutes)}
					/>
					<Select
						label="Minutes"
						options={['0', '15', '30', '45'].map((m) => ({ value: m, label: m }))}
						value={String(minutes)}
						onchange={(v) => setDuration(hours, Number(v))}
					/>
				</div>
			{:else if requirement.length.type === 'days'}
				<TextField
					label="Days in a row"
					type="number"
					min="1"
					max="31"
					inputmode="numeric"
					value={String(requirement.length.count)}
					onchange={(e) => {
						const n = Math.round(Number(valueOf(e)));
						if (n >= 1 && n <= 31 && requirement.length.type === 'days') requirement.length.count = n;
					}}
				/>
			{:else}
				<p class="plain">Set exactly when it starts and ends from the sentence.</p>
			{/if}
			<Button variant="solid" block onclick={() => (open = null)}>Done</Button>
		</div>
	{:else if open === 'start' && span}
		<div class="stack">
			<div class="pair">
				<Select label="Day" options={dayOptions} value={String(span.startDay)} onchange={(v) => (span.startDay = Number(v) as Weekday)} />
				<TextField label="Time" type="time" value={span.startTime} onchange={(e) => (span.startTime = valueOf(e) || '00:00')} />
			</div>
			<div class="chips">
				<Chip selected={span.startTime === '00:00'} onclick={() => (span.startTime = '00:00')}>Start of the day</Chip>
				<Chip selected={span.startTime === '09:00'} onclick={() => (span.startTime = '09:00')}>Morning</Chip>
				<Chip selected={span.startTime === '18:00'} onclick={() => (span.startTime = '18:00')}>Evening</Chip>
			</div>
			<Button variant="solid" block onclick={() => (open = null)}>Done</Button>
		</div>
	{:else if open === 'end' && span}
		<div class="stack">
			<div class="pair">
				<Select label="Day" options={dayOptions} value={String(span.endDay)} onchange={(v) => (span.endDay = Number(v) as Weekday)} />
				<TextField label="Time" type="time" value={span.endTime} onchange={(e) => (span.endTime = valueOf(e) || '23:59')} />
			</div>
			<div class="chips">
				<Chip selected={span.endTime === '23:59'} onclick={() => (span.endTime = '23:59')}>End of the day</Chip>
				<Chip selected={span.endTime === '17:00'} onclick={() => (span.endTime = '17:00')}>Teatime</Chip>
			</div>
			<div class="row">
				<span id="wl-{uid}">The following week</span>
				<Toggle checked={span.weeksLater === 1} labelledby="wl-{uid}" onchange={(on) => (span.weeksLater = on ? 1 : 0)} />
			</div>
			<Button variant="solid" block onclick={() => (open = null)}>Done</Button>
		</div>
	{:else if open === 'window'}
		<div class="stack">
			{#if requirement.length.type !== 'duration'}
				<div class="row">
					<span id="ad-{uid}">Any time of day</span>
					<Toggle
						checked={requirement.window === null}
						labelledby="ad-{uid}"
						onchange={(on) => (requirement.window = on ? null : { from: '18:00', to: '23:59' })}
					/>
				</div>
			{/if}
			{#if requirement.window}
				{@const win = requirement.window}
				<div class="pair">
					<TextField label="From" type="time" value={win.from} onchange={(e) => (win.from = valueOf(e) || '00:00')} />
					<TextField label="Until" type="time" value={win.to} onchange={(e) => (win.to = valueOf(e) || '23:59')} />
				</div>
				<div class="chips">
					<Chip onclick={() => (requirement.window = { from: '08:00', to: '23:00' })}>Daytime and evening</Chip>
					<Chip onclick={() => (requirement.window = { from: '09:00', to: '17:00' })}>Working hours</Chip>
					<Chip onclick={() => (requirement.window = { from: '18:00', to: '23:59' })}>Evenings</Chip>
				</div>
			{/if}
			<Button variant="solid" block onclick={() => (open = null)}>Done</Button>
		</div>
	{:else if open === 'days'}
		<div class="stack">
			<DayPicker bind:days={requirement.days} />
			<div class="chips">
				<Chip onclick={() => (requirement.days = [])}>Any day</Chip>
				<Chip onclick={() => (requirement.days = [0, 1, 2, 3, 4])}>Weekdays</Chip>
				<Chip onclick={() => (requirement.days = [5, 6])}>Weekends</Chip>
				<Chip onclick={() => (requirement.days = [4, 5, 6])}>Fri to Sun</Chip>
			</div>
			<p class="plain">No days picked means any day of the week.</p>
			<Button variant="solid" block onclick={() => (open = null)}>Done</Button>
		</div>
	{:else if open === 'range'}
		<form class="stack" onsubmit={applyRange} novalidate>
			<DateRangeCalendar bind:from={rangeFrom} bind:to={rangeTo} label="Any time between" />
			<div class="row">
				<span id="od-{uid}">Only on certain dates</span>
				<Toggle checked={onlyDates} labelledby="od-{uid}" onchange={(on) => (onlyDates = on)} />
			</div>
			{#if onlyDates}
				<DateRangeChips bind:ranges={requirement.dates} start={rangeFrom || requirement.range.from} emptyText="Add the dates it could be." />
			{/if}
			<Button type="submit" variant="solid" block disabled={!rangeFrom}>Done</Button>
		</form>
	{/if}
</Sheet>

<style>
	.sentence {
		margin: 0;
		font-family: var(--font-display);
		font-size: clamp(22px, 5.2vw, 27px);
		font-weight: 600;
		line-height: 1.5;
		letter-spacing: -0.2px;
		color: var(--ink);
	}
	.token {
		font: inherit;
		color: var(--accent-ink);
		background: transparent;
		border: 0;
		border-bottom: 2px dashed color-mix(in srgb, var(--accent-ink) 60%, transparent);
		padding: 0 2px;
		border-radius: 6px 6px 0 0;
		cursor: pointer;
	}
	.token:first-of-type {
		background: var(--accent-soft);
		border-bottom-style: solid;
		padding: 0 6px;
	}
	.token:hover:not(:disabled),
	.token:focus-visible {
		background: var(--accent-soft);
		outline: none;
	}
	.token:disabled {
		cursor: default;
		border-bottom-color: transparent;
	}
	.hint,
	.plain {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
	}
	.stack {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.pair {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 44px;
		font-size: 15px;
		font-weight: 600;
	}
	.eyebrow {
		margin: 4px 0 0;
	}
</style>
