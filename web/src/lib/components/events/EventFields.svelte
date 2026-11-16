<script lang="ts">
	import type { EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import TextField from '#lib/components/ui/TextField.svelte';
	import DateRangeInput from '#lib/components/ui/DateRangeInput.svelte';

	/** Shared fields for proposals and events: title, when, where, note. */
	interface Props {
		draft: EventDraft;
		/** Proposals can go untitled; events need a title. */
		titleRequired?: boolean;
		titlePlaceholder?: string;
		error?: string | null;
	}
	let { draft = $bindable(), titleRequired = false, titlePlaceholder = 'e.g. Weekend in Keswick', error }: Props = $props();

	const fmt = useFormat();
</script>

<div class="fields">
	<TextField
		label={titleRequired ? 'Title' : 'Title (optional)'}
		bind:value={draft.title}
		placeholder={titlePlaceholder}
		maxlength={120}
		required={titleRequired}
	/>

	<DateRangeInput
		bind:startDate={draft.startDate}
		bind:startTime={draft.startTime}
		bind:endDate={draft.endDate}
		bind:endTime={draft.endTime}
		bind:allDay={draft.allDay}
		today={fmt.current.today()}
	/>

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
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
