<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { createEventSchema } from '#lib/schemas';
	import { createEvent } from '#lib/client/events';
	import { blankDraft, draftTimes, optional, type EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EventFields from '#lib/components/events/EventFields.svelte';

	let { data } = $props();

	const fmt = useFormat();
	const back = $derived(`/app/groups/${data.group.id}`);
	const queryDate = page.url.searchParams.get('date');

	let draft = $state<EventDraft>(blankDraft(queryDate && /^\d{4}-\d{2}-\d{2}$/.test(queryDate) ? queryDate : fmt.current.today()));
	let error = $state<string | null>(null);
	let busy = $state(false);

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const times = draftTimes(draft, fmt.current.timeZone);
		if ('error' in times) {
			error = times.error;
			return;
		}
		const body = { title: draft.title, ...times, location: optional(draft.location), note: optional(draft.note) };
		const parsed = createEventSchema.safeParse(body);
		if (!parsed.success) {
			error = draft.title.trim() ? (parsed.error.issues[0]?.message ?? 'Check the details') : 'Give the event a title';
			return;
		}
		error = null;
		busy = true;
		try {
			await createEvent(data.group.id, { ...body, title: draft.title.trim() });
			toasts.success('Event added for everyone');
			await goto(back);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>Add event · {data.group.name} · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader title="Add event" subtitle={data.group.name} backHref={back} backLabel="Back to calendar" />
	<form class="glass panel" onsubmit={submit} novalidate>
		<p class="lead">Already decided? Add it straight to the group calendar. No vote needed.</p>
		<EventFields bind:draft titleRequired titlePlaceholder="e.g. Book club at Hana's" {error} />
		<div class="actions">
			<Button href={back} variant="secondary">Cancel</Button>
			<Button type="submit" variant="solid" loading={busy}>Add event</Button>
		</div>
	</form>
</div>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 16px;
		max-width: 600px;
		width: 100%;
	}
	.panel {
		border-radius: 28px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.lead {
		font-size: 14px;
		color: var(--muted);
	}
	.actions {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 8px;
	}
</style>
