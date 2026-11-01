<script lang="ts">
	import type { EventDraft } from '#lib/client/event-form';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/** Shared fields for proposals and events: title, when, where, note. */
	interface Props {
		draft: EventDraft;
		/** Proposals can go untitled; events need a title. */
		titleRequired?: boolean;
		titlePlaceholder?: string;
		error?: string | null;
	}
	let { draft = $bindable(), titleRequired = false, titlePlaceholder = 'e.g. Weekend in Keswick', error }: Props = $props();

	const uid = $props.id();

	// Keep the end date from falling before the start.
	function onStartDate() {
		if (draft.endDate < draft.startDate) draft.endDate = draft.startDate;
	}
</script>

<div class="fields">
	<TextField
		label={titleRequired ? 'Title' : 'Title (optional)'}
		bind:value={draft.title}
		placeholder={titlePlaceholder}
		maxlength={120}
		required={titleRequired}
	/>

	<div class="row all-day">
		<span id="allday-{uid}">All day</span>
		<Toggle bind:checked={draft.allDay} labelledby="allday-{uid}" />
	</div>

	<div class="grid" class:dates-only={draft.allDay}>
		<TextField label="Starts" type="date" bind:value={draft.startDate} onchange={onStartDate} required />
		{#if !draft.allDay}<TextField label="at" type="time" bind:value={draft.startTime} required />{/if}
		<TextField label="Ends" type="date" bind:value={draft.endDate} min={draft.startDate} required />
		{#if !draft.allDay}<TextField label="at" type="time" bind:value={draft.endTime} required />{/if}
	</div>

	{#if error}<p class="error" role="alert">{error}</p>{/if}

	<TextField label="Where (optional)" bind:value={draft.location} placeholder="e.g. Keswick" maxlength={160} />
	<TextField label="Note (optional)" bind:value={draft.note} multiline rows={2} maxlength={1000} placeholder="Anything people should know" />
</div>

<style>
	.fields {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.grid {
		display: grid;
		grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
		gap: 8px 10px;
	}
	.grid.dates-only {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 44px;
		padding: 0 4px 0 2px;
		font-size: 15px;
		font-weight: 600;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
