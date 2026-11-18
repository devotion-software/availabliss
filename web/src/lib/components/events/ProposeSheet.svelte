<script lang="ts">
	import { goto } from '$app/navigation';
	import type { Group, Proposal } from '#lib/types';
	import { createProposal, getConflicts } from '#lib/client/proposals';
	import { memberMap } from '#lib/client/members';
	import { clashText } from '#lib/components/proposals/clash';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { blankDraft, draftFromRange, draftTimes, optional, type EventDraft } from '#lib/client/event-form';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import { ApiError } from '#lib/client/api';
	import { getPlan } from '#lib/client/plan';
	import { draftWithAi } from '#lib/client/planner';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
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
		group: Pick<Group, 'id' | 'name' | 'members'>;
		meId: string;
		/** What to prefill: a slot that works, or just a day. */
		initial?: { start: string; end: string; allDay?: boolean; label?: string; title?: string; note?: string } | { day: string } | null;
		/** Sent (before going to the proposal), e.g. to close the date poll it came from. */
		onproposed?: (proposal: Proposal) => unknown;
	}
	let { open = $bindable(), group, meId, initial = null, onproposed }: Props = $props();
	const groupId = $derived(group.id);

	const fmt = useFormat();
	let draft = $state<EventDraft>(blankDraft('2000-01-01'));
	let error = $state<string | null>(null);
	let busy = $state(false);

	// Re-seed each time the sheet opens.
	$effect.pre(() => {
		if (!open) return;
		const tz = fmt.current.timeZone;
		const next = !initial
			? blankDraft(fmt.current.today())
			: 'day' in initial
				? blankDraft(initial.day)
				: { ...draftFromRange(initial.start, initial.end, !!initial.allDay, tz, initial.title ?? ''), note: initial.note ?? '' };
		// Prefill the title with the group's name, which is what the event is called anyway if left blank.
		if (!next.title) next.title = group.name;
		// Only write the draft (never read it here), so this doesn't re-run itself.
		draft = next;
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

	// "Draft with AI": only offered when the server has AI set up (like Plan with AI); Pro to use.
	let ai = $state<{ available: boolean; included: boolean } | null>(null);
	$effect(() => {
		// Reload when the upgrade sheet closes too, so buying Pro unlocks it straight away.
		if (!open || upgradeSheet.open) return;
		let stale = false;
		getPlan()
			.then((p) => !stale && (ai = p.aiPlanner))
			.catch(() => {});
		return () => {
			stale = true;
		};
	});
	let drafting = $state(false);
	async function aiDraft() {
		if (!ai?.included) {
			upgradeSheet.showFor('AI drafts are part of Pro: a title and message for the time you picked.');
			return;
		}
		const times = draftTimes(draft, fmt.current.timeZone);
		if ('error' in times) {
			error = times.error;
			return;
		}
		drafting = true;
		try {
			const result = await draftWithAi(groupId, times.start, times.end);
			draft.title = result.title;
			draft.note = result.note;
		} catch (err) {
			if (err instanceof ApiError && err.code === 'upgrade_required') upgradeSheet.showFor(err.message);
			else toasts.error(err);
		} finally {
			drafting = false;
		}
	}

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
			await Promise.resolve(onproposed?.(proposal)).catch(() => {});
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
		{#if ai?.available}
			<div class="ai">
				<Button variant="ghost" size="sm" icon="sparkle" loading={drafting} onclick={aiDraft}>Draft with AI</Button>
			</div>
		{/if}
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
	.ai {
		display: flex;
		justify-content: flex-end;
		margin: -6px 0 -10px;
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
