<script lang="ts">
	import { untrack } from 'svelte';
	import type { GroupEvent } from '#lib/types';
	import { deleteEvent, removeEventImage, setEventImage, updateEvent } from '#lib/client/events';
	import { draftFromRange, draftTimes, optional, type EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import { createEventSchema } from '#lib/schemas';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import EventFields from './EventFields.svelte';
	import CoverField from './CoverField.svelte';

	/**
	 * Edits an event (its creator or an admin): title, time, place, note and cover photo, or deletes
	 * it for everyone. Like the event editor on iOS and Android. Key it by the event's id.
	 */
	interface Props {
		event: GroupEvent | null;
		onclose: () => void;
		/** Saved or deleted: the list reloads. */
		onchanged: () => void;
		/** Offer Delete (not for a confirmed plan's event, which is cancelled as a plan instead). */
		canDelete?: boolean;
	}
	let { event, onclose, onchanged, canDelete = true }: Props = $props();

	const fmt = useFormat();
	// Made once per event (the parent keys this sheet by event), before the fields mount: the date
	// control would otherwise settle on a placeholder first.
	const initial = untrack(() => event);
	let draft = $state<EventDraft>(
		initial
			? {
					...draftFromRange(initial.start, initial.end, initial.allDay, fmt.current.timeZone, initial.title),
					location: initial.location ?? '',
					note: initial.note ?? ''
				}
			: draftFromRange(new Date().toISOString(), new Date(Date.now() + 3 * 3600_000).toISOString(), false, fmt.current.timeZone)
	);
	let cover = $state<File | null>(null);
	let removeCover = $state(false);
	let error = $state<string | null>(null);
	let busy = $state(false);
	let confirmsDelete = $state(false);

	async function save(e: SubmitEvent) {
		e.preventDefault();
		if (!event) return;
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
			await updateEvent(event.id, { ...body, title: draft.title.trim() });
			if (cover) await setEventImage(event.id, cover);
			else if (removeCover && event.image) await removeEventImage(event.id);
			toasts.success('Event updated for everyone');
			onchanged();
			onclose();
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}

	async function destroy() {
		if (!event) return;
		await deleteEvent(event.id);
		toasts.success('Event deleted');
		onchanged();
		onclose();
	}
</script>

<Sheet open={event !== null} title="Edit event" {onclose}>
	<form class="form" onsubmit={save} novalidate>
		{#if event?.image && !cover && !removeCover}
			<div class="current">
				<img src={event.image} alt="" />
				<Button variant="secondary" size="sm" icon="trash" onclick={() => (removeCover = true)} disabled={busy}>Remove cover photo</Button>
			</div>
		{/if}
		<CoverField bind:file={cover} disabled={busy} />
		<EventFields bind:draft titleRequired {error} />
		<div class="actions">
			{#if canDelete}
				<Button variant="danger" icon="trash" onclick={() => (confirmsDelete = true)} disabled={busy}>Delete</Button>
			{:else}
				<span></span>
			{/if}
			<Button type="submit" variant="solid" loading={busy}>Save</Button>
		</div>
	</form>
</Sheet>

<ConfirmSheet
	bind:open={confirmsDelete}
	title="Delete this event?"
	message="{event?.title ?? 'It'} goes from the group calendar for everyone."
	confirmLabel="Delete event"
	destructive
	onconfirm={destroy}
/>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.current {
		display: flex;
		flex-direction: column;
		gap: 8px;
		align-items: flex-start;
	}
	.current img {
		width: 100%;
		max-height: 180px;
		object-fit: cover;
		border-radius: 16px;
	}
	.actions {
		display: flex;
		justify-content: space-between;
		gap: 10px;
	}
</style>
