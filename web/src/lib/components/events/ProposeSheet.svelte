<script lang="ts">
	import { goto } from '$app/navigation';
	import type { Group } from '#lib/types';
	import { createProposal, getConflicts } from '#lib/client/proposals';
	import { memberMap } from '#lib/client/members';
	import { clashText } from '#lib/components/proposals/clash';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { blankDraft, draftFromRange, draftTimes, optional, type EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EventFields from './EventFields.svelte';

	/**
	 * Propose a time to the group. Prefilled from a slot / day, editable before
	 * sending. Times that clash with someone's busy time can still be proposed,
	 * with a warning naming who's busy.
	 */
	interface Props {
		open: boolean;
		group: Pick<Group, 'id' | 'members'>;
		meId: string;
		/** What to prefill: a slot that works, or just a day. */
		initial?: { start: string; end: string; allDay?: boolean; label?: string } | { day: string } | null;
	}
	let { open = $bindable(), group, meId, initial = null }: Props = $props();
	const groupId = $derived(group.id);

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

	// Check who's busy whenever the times change (debounced; stale answers are ignored).
	let busyIds = $state<string[]>([]);
	$effect(() => {
		if (!open) return;
		const times = draftTimes(draft, fmt.current.timeZone);
		if ('error' in times) {
			busyIds = [];
			return;
		}
		let stale = false;
		const timer = setTimeout(() => {
			getConflicts(groupId, times.start, times.end)
				.then((ids) => !stale && (busyIds = ids))
				.catch(() => !stale && (busyIds = []));
		}, 250);
		return () => {
			stale = true;
			clearTimeout(timer);
		};
	});
	const clash = $derived(clashText(busyIds, memberMap(group), meId));

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
		{#if clash}
			<div class="clash" role="status">
				<Icon name="alert" size={18} />
				<span><strong>{clash}.</strong> You can still propose it; everyone will see the clash before they reply.</span>
			</div>
		{/if}
		<Button type="submit" variant="accent" size="lg" block loading={busy}>{clash ? 'Propose anyway' : 'Send proposal'}</Button>
	</form>
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.clash {
		display: flex;
		gap: 10px;
		align-items: flex-start;
		padding: 12px 14px;
		border-radius: 16px;
		background: var(--warn-bg);
		color: var(--warn-ink);
		font-size: 13px;
		line-height: 1.4;
	}
	.clash :global(svg) {
		flex: none;
		margin-top: 1px;
	}
</style>
