import { describe, expect, test } from 'bun:test';
import { duration, shiftWall, spokenDate, withAllDay, withEnd, withStart, type RangeFields } from './date-range';

const timed = (startDate: string, startTime: string, endDate: string, endTime: string): RangeFields => ({ startDate, startTime, endDate, endTime, allDay: false });
const allDay = (startDate: string, endDate: string): RangeFields => ({ startDate, startTime: '09:00', endDate, endTime: '10:00', allDay: true });

describe('date range rules', () => {
	test('shiftWall crosses midnight both ways', () => {
		expect(shiftWall('2026-11-16', '23:30', 60)).toEqual({ date: '2026-11-17', time: '00:30' });
		expect(shiftWall('2026-11-16', '00:30', -60)).toEqual({ date: '2026-11-15', time: '23:30' });
	});

	test('moving the start time keeps the duration', () => {
		const r = withStart(timed('2026-11-16', '18:00', '2026-11-16', '21:00'), { time: '22:00' });
		expect(r).toMatchObject({ startTime: '22:00', endDate: '2026-11-17', endTime: '01:00' });
		expect(duration(r)).toBe(180);
	});

	test('moving the start date moves the end date with it', () => {
		expect(withStart(timed('2026-11-16', '18:00', '2026-11-16', '21:00'), { date: '2026-11-20' })).toMatchObject({ endDate: '2026-11-20', endTime: '21:00' });
		expect(withStart(allDay('2026-11-16', '2026-11-18'), { date: '2026-11-20' })).toMatchObject({ startDate: '2026-11-20', endDate: '2026-11-22' });
	});

	test('a start later than the end pushes the end along', () => {
		expect(withStart(timed('2026-11-16', '09:00', '2026-11-16', '10:00'), { time: '15:00' })).toMatchObject({ endTime: '16:00' });
	});

	test('an end before the start snaps to start + the old duration', () => {
		expect(withEnd(timed('2026-11-16', '18:00', '2026-11-16', '21:00'), { time: '17:00' })).toMatchObject({ endDate: '2026-11-16', endTime: '21:00' });
		expect(withEnd(timed('2026-11-16', '18:00', '2026-11-16', '18:10'), { time: '17:00' })).toMatchObject({ endTime: '19:00' });
		expect(withEnd(timed('2026-11-16', '18:00', '2026-11-16', '21:00'), { date: '2026-11-15' })).toMatchObject({ endDate: '2026-11-16', endTime: '21:00' });
	});

	test('an end after the start is kept, even on a later day', () => {
		expect(withEnd(timed('2026-11-16', '22:00', '2026-11-16', '23:00'), { date: '2026-11-17', time: '01:00' })).toMatchObject({ endDate: '2026-11-17', endTime: '01:00' });
	});

	test('all day: the end date never falls before the start', () => {
		expect(withEnd(allDay('2026-11-16', '2026-11-18'), { date: '2026-11-10' })).toMatchObject({ endDate: '2026-11-16' });
		expect(withEnd(allDay('2026-11-16', '2026-11-16'), { date: '2026-11-19' })).toMatchObject({ endDate: '2026-11-19' });
	});

	test('switching all day off fixes an end at or before the start', () => {
		expect(withAllDay({ ...allDay('2026-11-16', '2026-11-16'), startTime: '18:00', endTime: '09:00' }, false)).toMatchObject({ allDay: false, endTime: '19:00' });
		expect(withAllDay(timed('2026-11-16', '18:00', '2026-11-16', '21:00'), true)).toMatchObject({ allDay: true, endDate: '2026-11-16' });
	});

	test('spokenDate', () => {
		expect(spokenDate('2026-11-16')).toBe('Monday 16 November');
	});
});
