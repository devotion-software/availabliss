<script lang="ts">
	import type { Group } from '#lib/types';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';
	import PlannerSheet from './PlannerSheet.svelte';
	import type { GroupSheetState } from './sheets.svelte';

	/** The group page's Propose and AI planner sheets, opened through `sheets`. */
	interface Props {
		sheets: GroupSheetState;
		group: Group;
		meId: string;
	}
	let { sheets, group, meId }: Props = $props();

	// Reload whenever the upgrade sheet closes, so buying Pro unlocks the planner straight away.
	$effect(() => {
		if (upgradeSheet.open) return;
		let stale = false;
		getPlan()
			.then((p) => !stale && (sheets.aiPlanner = p.aiPlanner))
			.catch(() => {});
		return () => {
			stale = true;
		};
	});
</script>

<ProposeSheet bind:open={sheets.proposeOpen} {group} {meId} initial={sheets.proposeInitial} />
<PlannerSheet bind:open={sheets.plannerOpen} groupId={group.id} onpropose={({ slot, title, note }) => sheets.propose({ ...slot, title, note })} />
