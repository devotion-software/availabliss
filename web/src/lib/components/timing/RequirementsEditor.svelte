<script lang="ts">
	import type { Member, Requirement } from '#lib/types';
	import { listSchoolAuthorities } from '#lib/client/regions';
	import Chip from '#lib/components/ui/Chip.svelte';
	import Select from '#lib/components/ui/Select.svelte';
	import WhoNeedsFree from './WhoNeedsFree.svelte';
	import DateRangeChips from './DateRangeChips.svelte';
	import TimingSentence from './TimingSentence.svelte';

	/** The group timing editor: the timing as one sentence (TimingSentence), holiday options and who needs to be free. Read-only when `readonly`. */
	interface Props {
		requirement: Requirement;
		readonly?: boolean;
		/** The viewer's country, for bank holiday wording and school authorities. */
		country: string | null;
		regionLabel?: string;
		/** The group's members, for "Who needs to be free" (left out where there's no group yet). */
		members?: Member[];
		meId?: string;
	}
	let { requirement = $bindable(), readonly = false, country, regionLabel, members, meId }: Props = $props();

	// School authorities for the viewer's country.
	let authorities = $state<{ value: string; label: string }[]>([]);
	$effect(() => {
		if (!country) return;
		listSchoolAuthorities(country)
			.then((list) => (authorities = list.map((a) => ({ value: a.id, label: a.name }))))
			.catch(() => (authorities = []));
	});
	const authorityName = $derived(authorities.find((a) => a.value === requirement.schoolAuthority)?.label);
</script>

<fieldset class="editor" disabled={readonly}>
	<legend class="sr-only">Group timing</legend>

	<section class="glass card">
		<TimingSentence bind:requirement {readonly} />
		<div class="chips">
			<!-- With every day allowed (none picked means any), bank holidays are already included. -->
			{#if requirement.length.type === 'span' || (requirement.days.length > 0 && new Set(requirement.days).size < 7)}
				<Chip selected={requirement.bankHolidays} disabled={readonly} onclick={() => (requirement.bankHolidays = !requirement.bankHolidays)}>
					{requirement.bankHolidays ? '✓' : '+'} Include bank holidays{regionLabel ? ` (${regionLabel})` : ''}
				</Chip>
			{/if}
			<Chip
				selected={requirement.schoolHolidaysOnly}
				disabled={readonly}
				onclick={() => (requirement.schoolHolidaysOnly = !requirement.schoolHolidaysOnly)}
			>
				{requirement.schoolHolidaysOnly ? '✓' : '+'} Only school holidays
			</Chip>
		</div>
	</section>

	{#if requirement.schoolHolidaysOnly}
		<section class="glass card">
			<h2 class="eyebrow">Whose school holidays?</h2>
			{#if authorities.length}
				<Select
					label="School authority"
					hideLabel
					placeholder="Choose…"
					options={authorities}
					value={requirement.schoolAuthority ?? ''}
					disabled={readonly}
					onchange={(v) => (requirement.schoolAuthority = v || null)}
				/>
			{/if}
			<span class="eyebrow">{authorities.length ? 'Or add term breaks yourself' : 'Term breaks'}</span>
			<DateRangeChips
				bind:ranges={requirement.customSchoolHolidays}
				{readonly}
				start={requirement.range.from}
				addLabel="+ Add school holiday"
				emptyText="No custom school holidays."
				sheetTitle="Add school holiday"
				sheetDescription="Counts as a school holiday for this group."
			/>
			{#if !authorityName && !requirement.customSchoolHolidays.length}<p class="plain">Choose whose term dates to use.</p>{/if}
		</section>
	{/if}

	{#if members && members.length > 1}
		<WhoNeedsFree bind:requirement {members} {meId} {readonly} />
	{/if}
</fieldset>

<style>
	.editor {
		border: 0;
		margin: 0;
		padding: 0;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.card {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.plain {
		font-size: 13px;
		color: var(--muted);
	}
	/* Read-only: controls keep their look but stop inviting interaction. */
	.editor:disabled :global(button),
	.editor:disabled :global(input),
	.editor:disabled :global(select) {
		cursor: default;
	}
	.editor:disabled :global(.toggle:disabled) {
		opacity: 0.85;
	}
</style>
