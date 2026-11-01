/** Small pieces of cross-component UI state. */
class NewGroupSheetState {
	open = $state(false);
	show = () => (this.open = true);
}

/** The "New group" sheet can be opened from the app header and the groups home. */
export const newGroupSheet = new NewGroupSheetState();
