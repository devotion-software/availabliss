<script lang="ts">
	import { goto } from '$app/navigation';
	import { createProposal } from '#lib/client/proposals';
	import { blankDraft, draftFromRange, draftTimes, optional, type EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EventFields from './EventFields.svelte';

	/** Propose a time to the group. Prefilled from a slot / day, editable before sending. */
	interface Props {
		open: boolean;
		groupId: string;
		/** What to prefill: a slot that works, or just a day. */
		initial?: { start: string; end: string; allDay?: boolean; label?: string } | { day: string } | null;
	}
	let { open = $bindable(), groupId, initial = null }: Props = $props();

	const fmt = useFormat();
	let draft = $state<EventDraft>(blankDraft('2000-01-01'));
	let error = $state<string | null>(null);
	let busy = $state(false);

	// Re-seed each time the sheet opens.
	$effect.pre(() => {
		if (!open) return;
		const tz = fmt.current.timeZone;
		draft = !initial
			? blankDraft(fmt.current.today())
			: 'day' in initial
				? blankDraft(initial.day)
				: draftFromRange(initial.start, initial.end, !!initial.allDay, tz);
		error = null;
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const times = draftTimes(draft, fmt.current.timeZone);
		if ('error' in times) {
			error = times.error;
			return;
		}
		busy = true;
		try {
			const proposal = await createProposal(groupId, {
				...times,
				title: optional(draft.title),
				location: optional(draft.location),
				note: optional(draft.note)
			});
			open = false;
			toasts.success('Proposed. Everyone will be asked.');
			await goto(`/app/proposals/${proposal.id}`);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title="Propose a time" description="Everyone in the group gets asked. You accept automatically.">
	<form class="form" onsubmit={submit} novalidate>
		<EventFields bind:draft {error} />
		<Button type="submit" variant="accent" size="lg" block loading={busy}>Send proposal</Button>
	</form>
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
</style>
