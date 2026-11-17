<script lang="ts">
	import type { DateRange } from '#lib/types';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import DateRangeCalendar from '#lib/components/ui/DateRangeCalendar.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import { rangeNote } from '#lib/client/requirements';

	interface Props {
		open: boolean;
		/** The month the calendar opens on. */
		start: string;
		onadd: (range: DateRange) => void;
		/** Ranges already in the list: one they cover can't be added, and one that overlaps says it'll be joined. */
		existing?: DateRange[];
		title?: string;
		description?: string;
	}
	let {
		open = $bindable(),
		start,
		onadd,
		existing = [],
		title = 'Add date range',
		description = 'Only these dates will be searched.'
	}: Props = $props();

	let from = $state('');
	let to = $state('');
	const note = $derived(from && to ? rangeNote({ from, to }, existing) : null);

	$effect.pre(() => {
		if (!open) return;
		from = '';
		to = '';
	});

	function submit(e: SubmitEvent) {
		e.preventDefault();
		if (!from || !to || note?.blocked) return;
		onadd({ from, to });
		open = false;
	}
</script>

<Sheet bind:open {title} {description} placement="center" width={420}>
	<form class="form" onsubmit={submit} novalidate>
		<DateRangeCalendar bind:from bind:to opensAt={start} label="Dates" />
		{#if note}<p class="note" role="status">{note.text}</p>{/if}
		<Button type="submit" variant="solid" block disabled={!from || note?.blocked}>Add range</Button>
	</form>
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.note {
		font-size: 13px;
		color: var(--muted);
	}
</style>
