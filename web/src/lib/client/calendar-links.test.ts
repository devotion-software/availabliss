import { describe, expect, test } from 'bun:test';
import { rememberAddedToCalendar, wasAddedToCalendar } from './calendar-links';

/** A minimal in-memory Storage. */
function memoryStorage(): Storage {
	const data = new Map<string, string>();
	return {
		get length() {
			return data.size;
		},
		clear: () => data.clear(),
		getItem: (key) => data.get(key) ?? null,
		key: (i) => [...data.keys()][i] ?? null,
		removeItem: (key) => void data.delete(key),
		setItem: (key, value) => void data.set(key, value)
	};
}

describe('added to calendar', () => {
	test('remembers events added in this browser', () => {
		const store = memoryStorage();
		expect(wasAddedToCalendar('e1', store)).toBe(false);
		rememberAddedToCalendar('e1', store);
		expect(wasAddedToCalendar('e1', store)).toBe(true);
		expect(wasAddedToCalendar('e2', store)).toBe(false);
	});

	test('keeps each event once', () => {
		const store = memoryStorage();
		rememberAddedToCalendar('e1', store);
		rememberAddedToCalendar('e1', store);
		expect(JSON.parse(store.getItem('calendar.addedEvents') ?? '[]')).toEqual(['e1']);
	});

	test('copes with blocked or broken storage', () => {
		expect(wasAddedToCalendar('e1', null)).toBe(false);
		expect(() => rememberAddedToCalendar('e1', null)).not.toThrow();
		const store = memoryStorage();
		store.setItem('calendar.addedEvents', 'not json');
		expect(wasAddedToCalendar('e1', store)).toBe(false);
		rememberAddedToCalendar('e1', store);
		expect(wasAddedToCalendar('e1', store)).toBe(true);
	});
});
