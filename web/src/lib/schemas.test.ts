import { describe, expect, test } from 'bun:test';
import {
	createBlockSchema,
	createGroupSchema,
	createMessageSchema,
	createProposalSchema,
	createTemplateSchema,
	deviceSyncSchema,
	requirementSchema,
	updateBlockSchema,
	updateEventSchema,
	updateMeSchema
} from './schemas';

const requirement = (over: Record<string, unknown> = {}) => ({
	length: { type: 'days', count: 1 },
	window: { from: '18:00', to: '23:59' },
	days: [4, 5, 6],
	bankHolidays: false,
	schoolHolidaysOnly: false,
	schoolAuthority: null,
	customSchoolHolidays: [],
	range: { from: '2026-11-01', to: '2027-01-31' },
	dates: [],
	timezone: 'Europe/London',
	...over
});

const ok = (schema: { safeParse: (v: unknown) => { success: boolean } }, v: unknown) => schema.safeParse(v).success;

describe('requirementSchema', () => {
	test('accepts each length shape', () => {
		expect(ok(requirementSchema, requirement())).toBe(true);
		expect(ok(requirementSchema, requirement({ length: { type: 'duration', minutes: 120 } }))).toBe(true);
		expect(
			ok(
				requirementSchema,
				requirement({ length: { type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '23:59', weeksLater: 1 } })
			)
		).toBe(true);
	});

	test('length bounds', () => {
		expect(ok(requirementSchema, requirement({ length: { type: 'duration', minutes: 14 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { type: 'duration', minutes: 24 * 60 } }))).toBe(true);
		expect(ok(requirementSchema, requirement({ length: { type: 'duration', minutes: 24 * 60 + 1 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { type: 'duration', minutes: 90.5 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { type: 'days', count: 0 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { type: 'days', count: 32 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { type: 'weeks', count: 1 } }))).toBe(false);
		const span = { type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 };
		expect(ok(requirementSchema, requirement({ length: { ...span, weeksLater: 2 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { ...span, endDay: 7 } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ length: { ...span, startTime: '24:00' } }))).toBe(false);
	});

	test('time window must be HH:MM and end after it starts', () => {
		expect(ok(requirementSchema, requirement({ window: null }))).toBe(true);
		expect(ok(requirementSchema, requirement({ window: { from: '18:00', to: '18:00' } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ window: { from: '21:00', to: '18:00' } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ window: { from: '8:00', to: '18:00' } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ window: { from: '08:60', to: '18:00' } }))).toBe(false);
	});

	test('days, ranges and dates', () => {
		expect(ok(requirementSchema, requirement({ days: [] }))).toBe(true);
		expect(ok(requirementSchema, requirement({ days: [-1] }))).toBe(false);
		expect(ok(requirementSchema, requirement({ days: [0, 1, 2, 3, 4, 5, 6, 0] }))).toBe(false);
		expect(ok(requirementSchema, requirement({ range: { from: '2026-12-01', to: '2026-11-01' } }))).toBe(false);
		expect(ok(requirementSchema, requirement({ range: { from: '2026-11-01', to: '2026-11-01' } }))).toBe(true);
		expect(ok(requirementSchema, requirement({ range: { from: '2026-11-1', to: '2026-12-01' } }))).toBe(false);
		const range = { from: '2026-11-01', to: '2026-11-02' };
		expect(ok(requirementSchema, requirement({ dates: Array(50).fill(range) }))).toBe(true);
		expect(ok(requirementSchema, requirement({ dates: Array(51).fill(range) }))).toBe(false);
		expect(ok(requirementSchema, requirement({ customSchoolHolidays: [{ from: '2026-11-03', to: '2026-11-02' }] }))).toBe(false);
		expect(ok(requirementSchema, requirement({ timezone: '' }))).toBe(false);
	});
});

describe('block schemas', () => {
	const block = { start: '2026-11-13T18:00:00Z', end: '2026-11-13T20:00:00Z' };

	test('fills defaults and normalises optional fields', () => {
		expect(createBlockSchema.parse(block)).toEqual({ ...block, allDay: false, repeat: 'none', until: null, note: null });
		expect(createBlockSchema.parse({ ...block, note: '   ' }).note).toBeNull();
		expect(createBlockSchema.parse({ ...block, note: '  gym ' }).note).toBe('gym');
		expect(createBlockSchema.parse({ ...block, repeat: 'fortnightly', until: '2027-01-01' })).toMatchObject({
			repeat: 'fortnightly',
			until: '2027-01-01'
		});
	});

	test('rejects bad repeats, timestamps, ordering and long notes', () => {
		expect(ok(createBlockSchema, { ...block, repeat: 'daily' })).toBe(false);
		expect(ok(createBlockSchema, { ...block, start: '2026-11-13 18:00' })).toBe(false);
		expect(ok(createBlockSchema, { ...block, end: block.start })).toBe(false);
		expect(ok(createBlockSchema, { start: block.end, end: block.start })).toBe(false);
		expect(ok(createBlockSchema, { ...block, note: 'x'.repeat(121) })).toBe(false);
		expect(ok(createBlockSchema, { ...block, until: '2027/01/01' })).toBe(false);
	});

	test('accepts offsets other than Z', () => {
		expect(ok(createBlockSchema, { start: '2026-11-13T18:00:00+01:00', end: '2026-11-13T20:00:00+01:00' })).toBe(true);
	});

	test('partial updates only check order when both ends are given', () => {
		expect(ok(updateBlockSchema, {})).toBe(true);
		expect(ok(updateBlockSchema, { end: '2020-01-01T00:00:00Z' })).toBe(true);
		expect(ok(updateBlockSchema, { start: block.end, end: block.start })).toBe(false);
	});
});

describe('proposal and event schemas', () => {
	const times = { start: '2026-11-13T18:00:00Z', end: '2026-11-13T21:00:00Z' };

	test('proposal title is optional and blank becomes null', () => {
		expect(createProposalSchema.parse(times)).toEqual({ ...times, title: null, allDay: false, location: null, note: null });
		expect(createProposalSchema.parse({ ...times, title: '  ' }).title).toBeNull();
		expect(createProposalSchema.parse({ ...times, title: ' Dinner ' }).title).toBe('Dinner');
		expect(ok(createProposalSchema, { ...times, title: 'x'.repeat(121) })).toBe(false);
		expect(ok(createProposalSchema, { start: times.end, end: times.start })).toBe(false);
		expect(ok(createProposalSchema, { ...times, note: 'x'.repeat(1001) })).toBe(false);
	});

	test('event updates may be partial but still ordered', () => {
		expect(ok(updateEventSchema, { title: 'New' })).toBe(true);
		expect(ok(updateEventSchema, { title: '' })).toBe(false);
		expect(ok(updateEventSchema, { start: times.end, end: times.start })).toBe(false);
	});
});

describe('other schemas', () => {
	test('group names are trimmed and bounded', () => {
		expect(createGroupSchema.parse({ name: '  Climbers ' }).name).toBe('Climbers');
		expect(ok(createGroupSchema, { name: '   ' })).toBe(false);
		expect(ok(createGroupSchema, { name: 'x'.repeat(81) })).toBe(false);
		expect(ok(createGroupSchema, { name: 'A', color: 't9' })).toBe(false);
	});

	test('updateMe upper-cases the country', () => {
		expect(updateMeSchema.parse({ country: 'gb' })).toEqual({ country: 'GB' });
		expect(ok(updateMeSchema, { country: 'GBR' })).toBe(false);
	});

	test('messages are a body or a poll with 2–10 options', () => {
		expect(createMessageSchema.parse({ body: ' hi ' })).toEqual({ body: 'hi' });
		expect(createMessageSchema.parse({ poll: { question: 'When?', options: ['Fri', 'Sat'] } })).toEqual({
			poll: { question: 'When?', options: ['Fri', 'Sat'], multi: false }
		});
		expect(ok(createMessageSchema, { poll: { question: 'When?', options: ['Fri'] } })).toBe(false);
		expect(ok(createMessageSchema, { poll: { question: 'When?', options: Array(11).fill('x') } })).toBe(false);
		expect(ok(createMessageSchema, { body: '' })).toBe(false);
	});

	test('templates need a name and a valid requirement', () => {
		expect(ok(createTemplateSchema, { name: 'Weekend', requirement: requirement() })).toBe(true);
		expect(ok(createTemplateSchema, { name: 'x'.repeat(61), requirement: requirement() })).toBe(false);
		expect(ok(createTemplateSchema, { name: 'Weekend', requirement: requirement({ timezone: undefined }) })).toBe(false);
	});

	test('device sync limits', () => {
		const sync = { deviceId: 'd', deviceName: 'Phone', rangeStart: '2026-11-01T00:00:00Z', rangeEnd: '2027-02-01T00:00:00Z', calendars: [], busy: [] };
		expect(ok(deviceSyncSchema, sync)).toBe(true);
		expect(ok(deviceSyncSchema, { ...sync, calendars: Array(201).fill({ externalId: 'x', name: 'n', color: null }) })).toBe(false);
	});
});
