<script lang="ts">
	import type { Requirement, RequirementLength, Weekday } from '#lib/types';
	import {
		PRESETS,
		WEEKDAY_NAMES,
		applyPreset,
		presetOf,
		type PresetId
	} from '#lib/client/requirements';
	import { listSchoolAuthorities } from '#lib/client/regions';
	import Chip from '#lib/components/ui/Chip.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Select from '#lib/components/ui/Select.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import DayPicker from './DayPicker.svelte';
	import SettingRow from './SettingRow.svelte';
	import DateRangeChips from './DateRangeChips.svelte';

	/** The group timing editor (Requirements.dc.html). Read-only when `readonly`. */
	interface Props {
		requirement: Requirement;
		readonly?: boolean;
		/** The viewer's country, for bank holiday wording and school authorities. */
		country: string | null;
		regionLabel?: string;
	}
	let { requirement = $bindable(), readonly = false, country, regionLabel }: Props = $props();

	const uid = $props.id();
	const detected = $derived(presetOf(requirement));
	let customMode = $state(false);
	$effect.pre(() => {
		// Start in custom mode when the saved timing matches no preset.
		if (detected === 'custom') customMode = true;
	});
	const selected = $derived<PresetId>(customMode ? 'custom' : detected);

	function pick(id: PresetId) {
		customMode = id === 'custom';
		if (id !== 'custom') requirement = applyPreset(requirement, id);
	}

	const KINDS = [
		{ value: 'duration', label: 'Hours' },
		{ value: 'days', label: 'Days' },
		{ value: 'span', label: 'Span' }
	] as const;
	type Kind = (typeof KINDS)[number]['value'];

	function setKind(kind: Kind) {
		const len: Record<Kind, RequirementLength> = {
			duration: { type: 'duration', minutes: 180 },
			days: { type: 'days', count: 2 },
			span: { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 }
		};
		requirement.length = len[kind];
		requirement.window = kind === 'duration' ? { from: '08:00', to: '23:00' } : null;
	}

	const dayOptions = WEEKDAY_NAMES.map((n, i) => ({ value: String(i), label: n }));
	const asDay = (v: string) => Number(v) as Weekday;
	const valueOf = (e: Event) => (e.currentTarget as HTMLInputElement).value;

	function setPartOfDay(on: boolean) {
		requirement.window = on ? { from: '18:00', to: '23:59' } : null;
	}

	// School authorities for the viewer's country.
	let authorities = $state<{ value: string; label: string }[]>([]);
	$effect(() => {
		if (!country) return;
		listSchoolAuthorities(country)
			.then((list) => (authorities = list.map((a) => ({ value: a.id, label: a.name }))))
			.catch(() => (authorities = []));
	});
	const authorityName = $derived(authorities.find((a) => a.value === requirement.schoolAuthority)?.label);

</script>

<fieldset class="editor" disabled={readonly}>
	<legend class="sr-only">Group timing</legend>

	<section class="glass card">
		<h2 class="eyebrow">How long?</h2>
		<div class="chips">
			{#each PRESETS as p (p.id)}
				<Chip selected={selected === p.id} onclick={() => pick(p.id)}>{p.label}</Chip>
			{/each}
		</div>
		{#if selected === 'custom'}
			<SegmentedControl
				options={KINDS}
				value={requirement.length.type}
				label="Kind of length"
				variant="soft"
				size="sm"
				block
				onchange={setKind}
			/>
			{#if requirement.length.type === 'duration'}
				<TextField
					label="Hours"
					type="number"
					min="0.5"
					max="24"
					step="0.5"
					inputmode="decimal"
					value={String(requirement.length.minutes / 60)}
					onchange={(e) => {
						const h = Number(valueOf(e));
						if (h > 0 && requirement.length.type === 'duration') requirement.length.minutes = Math.round(h * 4) * 15;
					}}
				/>
			{:else if requirement.length.type === 'days'}
				<TextField
					label="Consecutive days"
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
			{/if}
		{/if}
	</section>

	<section class="glass card">
		<h2 class="eyebrow">Starts and ends</h2>
		{#if requirement.length.type === 'span'}
			{@const span = requirement.length}
			<div class="pair">
				<div class="box">
					<span class="box-label">Starts</span>
					<Select label="Start day" hideLabel options={dayOptions} value={String(span.startDay)} onchange={(v) => (span.startDay = asDay(v))} />
					<TextField label="Start time" hideLabel type="time" value={span.startTime} onchange={(e) => (span.startTime = valueOf(e))} />
				</div>
				<div class="box">
					<span class="box-label">Ends</span>
					<Select label="End day" hideLabel options={dayOptions} value={String(span.endDay)} onchange={(v) => (span.endDay = asDay(v))} />
					<TextField label="End time" hideLabel type="time" value={span.endTime} onchange={(e) => (span.endTime = valueOf(e))} />
				</div>
			</div>
			<div class="inline-row">
				<span id="wl-{uid}">Ends the following week</span>
				<Toggle checked={span.weeksLater === 1} labelledby="wl-{uid}" disabled={readonly} onchange={(on) => (span.weeksLater = on ? 1 : 0)} />
			</div>
		{:else}
			{#if requirement.length.type === 'days'}
				<div class="inline-row">
					<span id="pod-{uid}">Only part of the day</span>
					<Toggle checked={requirement.window !== null} labelledby="pod-{uid}" disabled={readonly} onchange={setPartOfDay} />
				</div>
			{/if}
			{#if requirement.window}
				{@const win = requirement.window}
				<div class="pair">
					<TextField
						label={requirement.length.type === 'duration' ? 'Can happen from' : 'Starts from'}
						type="time"
						value={win.from}
						onchange={(e) => (win.from = valueOf(e))}
					/>
					<TextField label="Until" type="time" value={win.to} onchange={(e) => (win.to = valueOf(e))} />
				</div>
			{:else}
				<p class="plain">Whole days, any time from midnight to midnight.</p>
			{/if}
			<DayPicker bind:days={requirement.days} />
			{#if requirement.days.length === 0}<p class="plain">No days picked means any day of the week.</p>{/if}
		{/if}
	</section>

	<section class="glass list">
		<SettingRow
			name="Bank holidays"
			sub="{regionLabel ? `${regionLabel} · ` : ''}treat like a weekend day"
			bind:checked={requirement.bankHolidays}
			disabled={readonly}
		/>
		<SettingRow
			name="School holidays only"
			sub={authorityName ?? (requirement.customSchoolHolidays.length ? 'Custom term dates' : 'Choose whose term dates')}
			bind:checked={requirement.schoolHolidaysOnly}
			disabled={readonly}
		>
			{#if requirement.schoolHolidaysOnly && authorities.length}
				<Select
					label="School authority"
					placeholder="Choose…"
					options={authorities}
					value={requirement.schoolAuthority ?? ''}
					disabled={readonly}
					onchange={(v) => (requirement.schoolAuthority = v || null)}
				/>
			{/if}
			{#if requirement.schoolHolidaysOnly}
				<div class="custom-holidays">
					<span class="eyebrow">{authorities.length ? 'Or add term breaks yourself' : 'Term breaks'}</span>
					<DateRangeChips
						bind:ranges={requirement.customSchoolHolidays}
						{readonly}
						start={requirement.range.from}
						addLabel="+ Add school holiday"
						emptyText="No custom school holidays."
						sheetTitle="Add school holiday"
						sheetDescription="Counts as a school holiday for this group."
					/>
				</div>
			{/if}
		</SettingRow>
	</section>

	<section class="glass card">
		<h2 class="eyebrow">Search between</h2>
		<div class="between">
			<TextField
				label="From"
				hideLabel
				type="date"
				value={requirement.range.from}
				onchange={(e) => (requirement.range.from = valueOf(e))}
			/>
			<span class="arrow" aria-hidden="true">→</span>
			<TextField
				label="To"
				hideLabel
				type="date"
				min={requirement.range.from}
				value={requirement.range.to}
				onchange={(e) => (requirement.range.to = valueOf(e))}
			/>
		</div>
		<h2 class="eyebrow">Or only these dates</h2>
		<DateRangeChips bind:ranges={requirement.dates} {readonly} start={requirement.range.from} emptyText="Any date in the search window." />
	</section>
</fieldset>


<style>
	.editor {
		border: 0;
		margin: 0;
		padding: 0;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.card {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.list {
		border-radius: 24px;
		padding: 4px 16px;
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
	.box {
		border-radius: 16px;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		padding: 10px;
		display: flex;
		flex-direction: column;
		gap: 6px;
		min-width: 0;
	}
	.box-label {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.inline-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 44px;
		font-size: 15px;
		font-weight: 600;
	}
	.custom-holidays {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding-bottom: 12px;
	}
	.plain {
		font-size: 13px;
		color: var(--muted);
	}
	.between {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.between > :global(.field) {
		flex: 1;
	}
	.arrow {
		color: var(--muted);
	}
	/* Read-only: controls keep their look but stop inviting interaction. */
	.editor:disabled :global(button),
	.editor:disabled :global(input),
	.editor:disabled :global(select) {
		cursor: default;
	}
	.editor:disabled :global(.toggle:disabled) {
		opacity: 0.85;
	}
</style>
