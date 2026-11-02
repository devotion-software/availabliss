<script lang="ts">
	import type { ManualBlock, Repeat } from '#lib/types';
	import { createBlockSchema } from '#lib/schemas';
	import { createBlock, updateBlock } from '#lib/client/blocks';
	import { addDays, dayKeyIn, timeIn, zonedToIso } from '#lib/client/dates';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import { clock } from './parts';

	/** "Block a time" (BlockTime.dc.html): a date, all day or exact times, repeat, and a private note. */
	interface Props {
		open: boolean;
		timeZone: string;
		/** Edit this block instead of creating one. */
		block?: ManualBlock | null;
		defaultDay: string;
		/** Prefill exact times (minutes since midnight on `defaultDay`), e.g. from a tap on the week grid. */
		defaultRange?: { from: number; to: number } | null;
		onsaved: (block: ManualBlock) => void;
	}
	let { open = $bindable(), timeZone, block = null, defaultDay, defaultRange = null, onsaved }: Props = $props();

	type When = 'day' | 'times';
	const WHENS: { value: When; label: string }[] = [
		{ value: 'times', label: 'Set times' },
		{ value: 'day', label: 'All day' }
	];
	const REPEATS: { value: Repeat; label: string }[] = [
		{ value: 'none', label: 'Once' },
		{ value: 'weekly', label: 'Weekly' },
		{ value: 'fortnightly', label: 'Fortnightly' }
	];

	let date = $state('');
	let when = $state<When>('day');
	let from = $state('14:00');
	let to = $state('18:00');
	let repeat = $state<Repeat>('none');
	let until = $state('');
	let note = $state('');
	let error = $state<string | null>(null);
	let busy = $state(false);

	// Seed the form whenever the sheet opens.
	$effect.pre(() => {
		if (!open) return;
		error = null;
		if (!block) {
			date = defaultDay;
			when = 'times';
			from = defaultRange ? clock(defaultRange.from) : '09:00';
			// 24:00 is entered as 00:00, which the form reads as "runs to midnight".
			to = defaultRange ? clock(defaultRange.to % (24 * 60)) : '10:00';
			repeat = 'none';
			until = '';
			note = '';
			return;
		}
		date = dayKeyIn(block.start, timeZone);
		repeat = block.repeat;
		until = block.until ?? '';
		note = block.note ?? '';
		if (block.allDay) {
			when = 'day';
			return;
		}
		when = 'times';
		from = timeIn(block.start, timeZone);
		to = timeIn(block.end, timeZone);
	});

	const timeLabel = $derived(when === 'times' ? `${from} – ${to}` : 'All day');

	function range(): { start: string; end: string; allDay: boolean } | string {
		if (!date) return 'Choose a date';
		if (when === 'day') return { start: zonedToIso(date, '00:00', timeZone), end: zonedToIso(addDays(date, 1), '00:00', timeZone), allDay: true };
		if (!from || !to) return 'Choose a start and end time';
		// An end at or before the start runs past midnight.
		const endDay = to <= from ? addDays(date, 1) : date;
		return { start: zonedToIso(date, from, timeZone), end: zonedToIso(endDay, to, timeZone), allDay: false };
	}

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const r = range();
		if (typeof r === 'string') {
			error = r;
			return;
		}
		const parsed = createBlockSchema.safeParse({ ...r, repeat, until: repeat === 'none' ? null : until || null, note });
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
		<div class="pair">
			<TextField label="Date" type="date" bind:value={date} required />
			<div class="time-card" aria-live="polite">
				<span class="k">Time</span>
				<span class="v">{timeLabel}</span>
			</div>
		</div>

		<SegmentedControl options={WHENS} bind:value={when} label="All day or set times" variant="soft" size="sm" block />

		{#if when === 'times'}
			<div class="pair">
				<TextField label="From" type="time" bind:value={from} />
				<TextField label="To" type="time" bind:value={to} />
			</div>
		{/if}

		<div class="repeat">
			<span class="eyebrow" id="repeat-label">Repeat</span>
			<SegmentedControl options={REPEATS} bind:value={repeat} label="Repeat" variant="soft" size="sm" block />
		</div>
		{#if repeat !== 'none'}
			<TextField label="Until (optional)" type="date" bind:value={until} min={date} hint="Leave empty to keep repeating" />
		{/if}

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
	.pair {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
		align-items: end;
	}
	.time-card {
		min-height: 48px;
		border-radius: 14px;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		padding: 6px 12px;
		display: flex;
		flex-direction: column;
		justify-content: center;
	}
	.k {
		font-size: 11px;
		font-weight: 600;
		color: var(--muted);
	}
	.v {
		font-size: 15px;
		font-weight: 600;
	}
	.repeat {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
