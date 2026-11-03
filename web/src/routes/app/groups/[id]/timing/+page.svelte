<script lang="ts">
	import { untrack } from 'svelte';
	import { invalidate } from '$app/navigation';
	import type { Requirement } from '#lib/types';
	import { requirementSchema } from '#lib/schemas';
	import { createPreview } from '#lib/client/requirement-preview.svelte';
	import { updateGroup } from '#lib/client/groups';
	import { listSubdivisions } from '#lib/client/regions';
	import { isAdminRole } from '#lib/client/members';
	import { fitCountLabel, summarize } from '#lib/client/requirements';
	import { formatDayKey } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import RequirementsEditor from '#lib/components/timing/RequirementsEditor.svelte';
	import SaveTemplate from '#lib/components/timing/SaveTemplate.svelte';

	let { data } = $props();

	const admin = $derived(isAdminRole(data.group.role));
	let busy = $state(false);
	const saved = $derived(data.group.requirement);
	// A working copy of the saved timing. The group reloads whenever anyone joins or
	// changes role, so only reset it when the saved timing itself changed, and never
	// over unsaved edits: say someone else changed it instead.
	const clone = (r: Requirement) => structuredClone($state.snapshot(r)) as Requirement;
	const json = (r: Requirement) => JSON.stringify($state.snapshot(r));
	let draft = $state<Requirement>(untrack(() => clone(data.group.requirement)));
	let seeded = untrack(() => json(data.group.requirement));
	let changedElsewhere = $state(false);
	$effect.pre(() => {
		const next = json(saved);
		if (next === seeded) return;
		const unsaved = untrack(() => !busy && json(draft) !== seeded);
		seeded = next;
		if (unsaved && untrack(() => json(draft)) !== next) {
			changedElsewhere = true;
		} else {
			draft = clone(saved);
			changedElsewhere = false;
		}
	});
	function loadSaved() {
		draft = clone(saved);
		changedElsewhere = false;
	}
	const dirty = $derived(JSON.stringify(draft) !== JSON.stringify(saved));
	// "England", falling back to "United Kingdom", for the bank holiday hint.
	let subdivisionName = $state<string | null>(null);
	$effect(() => {
		const { country, subdivision } = data.user;
		subdivisionName = null;
		if (!country || !subdivision) return;
		listSubdivisions(country)
			.then((list) => (subdivisionName = list.find((s) => s.code === subdivision)?.name ?? null))
			.catch(() => {});
	});
	const countryName = $derived(
		data.user.country ? (new Intl.DisplayNames(['en-GB'], { type: 'region' }).of(data.user.country) ?? data.user.country) : ''
	);
	const regionLabel = $derived(subdivisionName ?? countryName);

	// Live "13 weekends fit this timing", recomputed (debounced) as the form changes.
	const live = createPreview(() => draft, () => data.user.timezone);
	const preview = $derived(live.preview);
	const previewing = $derived(live.loading);
	const fitBig = $derived(preview ? fitCountLabel(draft, preview.count) : summarize(draft).split(' · ')[0]);
	const fitSmall = $derived(
		preview
			? `fit this timing between ${formatDayKey(preview.from).replace(/^\w+ /, '')} and ${formatDayKey(preview.to).replace(/^\w+ /, '')}${admin && dirty ? ' · unsaved' : ''}`
			: summarize(draft)
	);

	async function save() {
		const parsed = requirementSchema.safeParse({ ...draft, timezone: draft.timezone || data.user.timezone });
		if (!parsed.success) {
			toasts.error(parsed.error.issues[0]?.message ?? 'Check the timing');
			return;
		}
		busy = true;
		try {
			await updateGroup(data.group.id, { requirement: { ...draft, timezone: parsed.data.timezone } });
			await Promise.all([invalidate('app:group'), invalidate('app:groups')]);
			changedElsewhere = false;
			toasts.success('Timing saved. Finding new times…');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>Timing · {data.group.name} · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader
	title="Timing"
	subtitle={data.group.name}
	backHref="/app/groups/{data.group.id}"
	backLabel="Back to calendar"
	size="lg"
	crumbs={[{ label: 'Groups', href: '/app' }, { label: data.group.name, href: `/app/groups/${data.group.id}` }, { label: 'Timing' }]}
>
		{#snippet actions()}
			<StatusPill tone="indigo" icon="shield" size="md">Admins only</StatusPill>
		{/snippet}
	</PageHeader>

	{#if !admin}
		<p class="note glass">
			<Icon name="lock" size={16} />
			Only admins can change the timing. This is what the group is looking for.
		</p>
	{/if}

	{#if changedElsewhere}
		<p class="note glass changed" role="status">
			<Icon name="info" size={16} />
			<span>Someone else changed the timing. Saving replaces it with yours.</span>
			<Button size="sm" variant="secondary" onclick={loadSaved}>Load theirs</Button>
		</p>
	{/if}

	<RequirementsEditor bind:requirement={draft} readonly={!admin} country={data.user.country} {regionLabel} />
	<!-- Anyone can keep a copy of a group's timing to reuse, admin or not. -->
	<SaveTemplate requirement={draft} suggestion={data.group.name} />

	<div class="savebar glass">
		<div class="summary" aria-live="polite" aria-busy={previewing}>
			<span class="big" class:stale={previewing}>{fitBig}</span>
			<span class="small">{fitSmall}</span>
		</div>
		{#if admin}
			<Button variant="solid" loading={busy} disabled={!dirty} onclick={save}>Save timing</Button>
		{/if}
	</div>
</div>

<style>
	.page {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 12px;
		max-width: 680px;
		width: 100%;
	}
	.note {
		border-radius: 18px;
		padding: 12px 14px;
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 13px;
		color: var(--muted);
	}
	.changed span {
		flex: 1;
	}
	.savebar {
		position: sticky;
		bottom: max(16px, env(safe-area-inset-bottom));
		margin-top: auto;
		z-index: 5;
		border-radius: 26px;
		padding: 12px 12px 12px 16px;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.summary {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.big {
		font-family: var(--font-display);
		font-size: 20px;
		font-weight: 700;
		letter-spacing: -0.025em;
	}
	.stale {
		opacity: 0.55;
		transition: opacity 0.2s ease;
	}
	.small {
		font-size: 12px;
		color: var(--muted);
	}
</style>
