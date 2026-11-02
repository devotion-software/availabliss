/** Small pieces of cross-component UI state. */
class SheetState {
	open = $state(false);
	show = () => (this.open = true);
}

/** The "New group" sheet can be opened from the app header and the groups home. */
export const newGroupSheet = new SheetState();

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
