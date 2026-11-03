<script lang="ts">
	import type { Availability, Member } from '#lib/types';
	import { firstName, listNames, useFormat } from '#lib/client/format';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * "Sam's calendar is only checked up to 4 Mar 2029": shown when someone's
	 * busy time isn't known to the end of the period on screen, so free-looking
	 * days past that date might still clash. Renders nothing otherwise.
	 */
	interface Props {
		gaps: Availability['checkedUntil'];
		members: ReadonlyMap<string, Member>;
		meId: string;
	}
	let { gaps, members, meId }: Props = $props();

	const fmt = useFormat();
	const earliest = $derived(gaps.length ? gaps.reduce((a, b) => (a.until < b.until ? a : b)).until : null);
	const mine = $derived(gaps.some((g) => g.userId === meId));
	const others = $derived(gaps.filter((g) => g.userId !== meId).map((g) => firstName(members.get(g.userId)?.name ?? 'Someone')));
	const whose = $derived.by(() => {
		if (!others.length) return 'Your calendar is';
		if (!mine && others.length === 1) return `${others[0]}'s calendar is`;
		return `Calendars for ${listNames(mine ? ['you', ...others] : others, 3)} are`;
	});
</script>

{#if earliest}
	<p class="note" role="note">
		<Icon name="info" size={15} />
		<span>
			{whose} only checked up to <strong>{fmt.current.date(earliest)}</strong>, so later days may not show every clash yet.
			{#if mine}Open the app on your phone to sync further ahead.{/if}
		</span>
	</p>
{/if}

<style>
	.note {
		display: flex;
		align-items: flex-start;
		gap: 8px;
		margin: 0;
		padding: 8px 12px;
		border-radius: 14px;
		font-size: 13px;
		line-height: 1.4;
		background: var(--warn-bg);
		color: var(--warn-ink);
	}
	.note :global(svg) {
		flex: none;
		margin-top: 2px;
	}
	strong {
		font-weight: 600;
	}
</style>
