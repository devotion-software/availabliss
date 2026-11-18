/** Small pieces of cross-component UI state. */
import type { Group } from '#lib/types';

class SheetState {
	open = $state(false);
	show = () => (this.open = true);
}

/**
 * The "New group" sheet can be opened from the app header and the groups home, or from a group as
 * "New group with these people" (`showFrom`), starting from that group's people, colour and timing.
 */
class NewGroupSheetState extends SheetState {
	basedOn = $state<Group | null>(null);
	show = () => {
		this.basedOn = null;
		return (this.open = true);
	};
	showFrom = (group: Group) => {
		this.basedOn = group;
		return (this.open = true);
	};
}
export const newGroupSheet = new NewGroupSheetState();

/** The Pro upgrade sheet: from the group limit, settings, and Pro-only features. */
class UpgradeSheetState extends SheetState {
	/** Why it opened, shown as the sheet's lead ("You've used your 3 free groups"). */
	reason = $state<string | null>(null);
	showFor = (reason: string | null = null) => {
		this.reason = reason;
		this.open = true;
	};
}
export const upgradeSheet = new UpgradeSheetState();
