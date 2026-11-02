import { describe, expect, test } from 'bun:test';
import { blankDraft, draftFromRange, draftTimes, optional, type EventDraft } from './event-form';

const TZ = 'Europe/London';

describe('draftFromRange', () => {
	test('timed ranges keep the wall-clock times', () => {
		expect(draftFromRange('2026-07-01T17:00:00Z', '2026-07-01T20:30:00Z', false, TZ, 'Dinner')).toEqual({
			title: 'Dinner',
			startDate: '2026-07-01',
			startTime: '18:00',
			endDate: '2026-07-01',
			endTime: '21:30',
			allDay: false,
			location: '',
			note: ''
		});
	});

	test('all-day ends are exclusive midnights', () => {
		const d = draftFromRange('2026-11-13T00:00:00Z', '2026-11-16T00:00:00Z', true, TZ);
		expect(d).toMatchObject({ startDate: '2026-11-13', endDate: '2026-11-15', allDay: true, startTime: '18:00', endTime: '21:00' });
	});
});

describe('draftTimes', () => {
	const draft = (over: Partial<EventDraft>): EventDraft => ({ ...blankDraft('2026-11-13'), ...over });

	test('blank draft is an evening', () => {
		expect(draftTimes(blankDraft('2026-11-13'), TZ)).toEqual({
			start: '2026-11-13T18:00:00.000Z',
			end: '2026-11-13T21:00:00.000Z',
			allDay: false
		});
	});

	test('all-day spans whole local days', () => {
		expect(draftTimes(draft({ allDay: true, startDate: '2026-07-01', endDate: '2026-07-02' }), TZ)).toEqual({
			start: '2026-06-30T23:00:00.000Z',
			end: '2026-07-02T23:00:00.000Z',
			allDay: true
		});
	});

	test('round trips with draftFromRange', () => {
		const times = draftTimes(draft({ allDay: true, startDate: '2026-11-13', endDate: '2026-11-15' }), TZ);
		if ('error' in times) throw new Error(times.error);
		expect(draftFromRange(times.start, times.end, true, TZ)).toMatchObject({ startDate: '2026-11-13', endDate: '2026-11-15' });
	});

	test('readable problems', () => {
		expect(draftTimes(draft({ startDate: '' }), TZ)).toEqual({ error: 'Choose a date' });
		expect(draftTimes(draft({ allDay: true, endDate: '2026-11-12' }), TZ)).toEqual({ error: 'The end date is before the start' });
		expect(draftTimes(draft({ endTime: '' }), TZ)).toEqual({ error: 'Choose a start and end time' });
		expect(draftTimes(draft({ endTime: '18:00' }), TZ)).toEqual({ error: 'It ends before it starts' });
		expect(draftTimes(draft({ endTime: '17:00' }), TZ)).toEqual({ error: 'It ends before it starts' });
	});

	test('overnight events end on a later date', () => {
		expect(draftTimes(draft({ startTime: '22:00', endDate: '2026-11-14', endTime: '02:00' }), TZ)).toEqual({
			start: '2026-11-13T22:00:00.000Z',
			end: '2026-11-14T02:00:00.000Z',
			allDay: false
		});
	});
});

test('optional trims and nulls blanks', () => {
	expect(optional('  ')).toBeNull();
	expect(optional(' Pub ')).toBe('Pub');
});
