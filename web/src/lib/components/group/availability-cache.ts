import type { Availability, DayAvailability } from '#lib/types';
import { getAvailability, getDayAvailability } from '#lib/client/availability';
import { addMonths, endOfMonth } from '#lib/client/dates';

/**
 * A group calendar's months and days already fetched, and ones on their way (a page change or a
 * prefetch), so paging to a neighbouring month or week shows it straight away instead of waiting on
 * the network. Forgotten when something changes (busy time, the timing). Not reactive.
 */
export class AvailabilityCache {
	private months = new Map<string, Promise<Availability>>();
	private days = new Map<string, Promise<DayAvailability>>();
	readonly monthsReady = new Map<string, Availability>();
	readonly daysReady = new Map<string, DayAvailability>();

	constructor(private readonly groupId: () => string) {}

	month = (m: string): Promise<Availability> => {
		let request = this.months.get(m);
		if (!request) {
			const cache = this.months;
			request = getAvailability(this.groupId(), m, endOfMonth(m));
			cache.set(m, request);
			request.then(
				(a) => cache === this.months && this.monthsReady.set(m, a),
				() => cache.delete(m)
			);
		}
		return request;
	};

	day = (date: string): Promise<DayAvailability> => {
		let request = this.days.get(date);
		if (!request) {
			const cache = this.days;
			request = getDayAvailability(this.groupId(), date);
			cache.set(date, request);
			request.then(
				(d) => cache === this.days && this.daysReady.set(date, d),
				() => cache.delete(date)
			);
		}
		return request;
	};

	forget() {
		this.months = new Map();
		this.days = new Map();
		this.monthsReady.clear();
		this.daysReady.clear();
	}

	/** Caches the month the page loaded with, and fetches the months either side for the first swipe. */
	seed(m: string, a: Availability) {
		this.monthsReady.set(m, a);
		this.months.set(m, Promise.resolve(a));
		this.prefetchAround(m);
	}

	/** The months either side of `m`, for the next swipe. */
	prefetchAround(m: string) {
		this.month(addMonths(m, 1)).catch(() => {});
		this.month(addMonths(m, -1)).catch(() => {});
	}
}
