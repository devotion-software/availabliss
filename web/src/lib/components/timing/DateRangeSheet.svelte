<script lang="ts">
	import type { DateRange } from '#lib/types';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import { rangeNote } from '#lib/client/requirements';

	interface Props {
		open: boolean;
		/** Default start date for the inputs. */
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
	let error = $state<string | null>(null);
	const note = $derived(from && to && from <= to ? rangeNote({ from, to }, existing) : null);

	$effect.pre(() => {
		if (!open) return;
		from = start;
		to = start;
		error = null;
	});

	function submit(e: SubmitEvent) {
		e.preventDefault();
		if (!from || !to) return void (error = 'Choose both dates');
		if (to < from) return void (error = 'The end is before the start');
		if (note?.blocked) return;
		onadd({ from, to });
		open = false;
	}
</script>

<Sheet bind:open {title} {description} placement="center" width={420}>
	<form class="form" onsubmit={submit} novalidate>
		<div class="grid">
			<TextField label="From" type="date" bind:value={from} required />
			<TextField label="To" type="date" bind:value={to} min={from} required {error} />
		</div>
		{#if note}<p class="note" role="status">{note.text}</p>{/if}
		<Button type="submit" variant="solid" block disabled={note?.blocked}>Add range</Button>
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
	.grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 10px;
	}
</style>
