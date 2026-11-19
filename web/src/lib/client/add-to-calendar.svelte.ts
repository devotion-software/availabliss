import { page } from '$app/state';

/** Whether to show "Add to calendar" on plans: the account setting (on unless turned off in Settings). */
export function showsAddToCalendar(): boolean {
	return page.data.user?.showAddToCalendar !== false;
}
