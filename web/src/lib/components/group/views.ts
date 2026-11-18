/** A group calendar shows a month or a week; the selected day's detail shows under either. */
export type CalendarView = 'month' | 'week';

export const CALENDAR_VIEWS = [
	{ value: 'month', label: 'Month' },
	{ value: 'week', label: 'Week' }
] as const;
