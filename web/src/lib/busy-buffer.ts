import { BUSY_BUFFER_MINUTES, type BusyBufferMinutes } from './types';

/** A stored buffer, or 0 if it isn't one of the offered values. */
export const toBusyBuffer = (minutes: number): BusyBufferMinutes =>
	(BUSY_BUFFER_MINUTES as readonly number[]).includes(minutes) ? (minutes as BusyBufferMinutes) : 0;

/** "None", "15 min", "30 min", "1 hour", "2 hours". */
export function busyBufferLabel(minutes: BusyBufferMinutes): string {
	if (minutes === 0) return 'None';
	if (minutes < 60) return `${minutes} min`;
	const hours = minutes / 60;
	return hours === 1 ? '1 hour' : `${hours} hours`;
}
