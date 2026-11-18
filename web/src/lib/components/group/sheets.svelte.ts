import type { Slot } from '#lib/types';
import { upgradeSheet } from '#lib/client/ui-state.svelte';

/** What the Propose sheet opens with: a time (maybe with a title and note), a whole day, or nothing. */
export type ProposeInitial = (Slot & { title?: string; note?: string; allDay?: boolean }) | { day: string } | null;

/** Which of a group page's sheets are open, and what they open with. Rendered by GroupSheets.svelte. */
export class GroupSheetState {
	proposeOpen = $state(false);
	proposeInitial = $state<ProposeInitial>(null);
	plannerOpen = $state(false);
	/** The optional AI planner: only offered when the server has it set up; Pro to use. Loaded by GroupSheets. */
	aiPlanner = $state<{ available: boolean; included: boolean } | null>(null);

	propose = (slot: ProposeInitial) => {
		this.proposeInitial = slot;
		this.proposeOpen = true;
	};

	openPlanner = () => {
		if (this.aiPlanner?.included) this.plannerOpen = true;
		else upgradeSheet.showFor('AI planning is part of Pro: it picks the best time, suggests things to do and drafts the plan.');
	};
}
