/**
 * How a block's repeat reads, built from its first date: the choices in the block sheet
 * ("Every month on the 3rd Monday"), the schedule line ("Monthly on the 3rd Monday") and
 * the agenda tag ("Monthly"). Values from a newer server read as "Repeats".
 */
import type { Repeat } from '#lib/types';
import { endOfMonth, parseKey, weekdayOf } from '#lib/client/dates';
import { WEEKDAY_NAMES } from '#lib/client/requirements';

const UTC = (opts: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat('en-GB', { ...opts, timeZone: 'UTC' });
const dayMonthLong = UTC({ day: 'numeric', month: 'long' });
const dayMonthShort = UTC({ day: 'numeric', month: 'short' });

/** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st. */
export function ordinal(n: number): string {
	const teen = n % 100 >= 11 && n % 100 <= 13;
	const suffix = teen ? 'th' : (['th', 'st', 'nd', 'rd'][n % 10] ?? 'th');
	return `${n}${suffix}`;
}

/** "3rd Monday", or "last Monday" when `day` is in its month's last 7 days (the server's rule). */
export function weekdayOfMonth(day: string): string {
	const date = parseKey(day).getUTCDate();
	const last = date > parseKey(endOfMonth(day)).getUTCDate() - 7;
	const weekday = WEEKDAY_NAMES[weekdayOf(day)];
	return `${last ? 'last' : ordinal(Math.ceil(date / 7))} ${weekday}`;
}

const dateOfMonth = (day: string) => ordinal(parseKey(day).getUTCDate());

/** The block sheet's choices, labelled from the first date. */
export function repeatChoices(day: string): { value: Repeat; label: string }[] {
	const weekday = WEEKDAY_NAMES[weekdayOf(day)];
	return [
		{ value: 'none', label: 'Never' },
		{ value: 'daily', label: 'Every day' },
		{ value: 'weekdays', label: 'Every weekday (Mon–Fri)' },
		{ value: 'weekly', label: `Every week on ${weekday}` },
		{ value: 'fortnightly', label: `Every 2 weeks on ${weekday}` },
		{ value: 'monthly', label: `Every month on the ${dateOfMonth(day)}` },
		{ value: 'monthlyWeekday', label: `Every month on the ${weekdayOfMonth(day)}` },
		{ value: 'yearly', label: `Every year on ${dayMonthLong.format(parseKey(day))}` }
	];
}

/** The sheet's label for a repeat ("Every week on Monday"); unknown values read "Repeats". */
export const repeatLabel = (repeat: string, day: string) => repeatChoices(day).find((c) => c.value === repeat)?.label ?? 'Repeats';

/** The schedule line's lead ("Every Tuesday", "Monthly on the 16th"); null for a one-off. */
export function scheduleLead(repeat: string, day: string): string | null {
	const weekday = WEEKDAY_NAMES[weekdayOf(day)];
	switch (repeat) {
		case 'none':
			return null;
		case 'daily':
			return 'Every day';
		case 'weekdays':
			return 'Weekdays';
		case 'weekly':
			return `Every ${weekday}`;
		case 'fortnightly':
			return `Every other ${weekday}`;
		case 'monthly':
			return `Monthly on the ${dateOfMonth(day)}`;
		case 'monthlyWeekday':
			return `Monthly on the ${weekdayOfMonth(day)}`;
		case 'yearly':
			return `Yearly on ${dayMonthShort.format(parseKey(day))}`;
		default:
			return 'Repeats';
	}
}

/** The agenda's tag for a block's repeat. */
export function repeatTag(repeat: string | undefined): string {
	switch (repeat) {
		case undefined:
		case 'none':
			return 'Your block';
		case 'daily':
			return 'Daily';
		case 'weekdays':
			return 'Weekdays';
		case 'weekly':
			return 'Weekly';
		case 'fortnightly':
			return 'Every other week';
		case 'monthly':
		case 'monthlyWeekday':
			return 'Monthly';
		case 'yearly':
			return 'Yearly';
		default:
			return 'Repeats';
	}
}
