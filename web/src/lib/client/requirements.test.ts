import { afterEach, describe, expect, test } from 'bun:test';
import type { Requirement, RequirementLength } from '#lib/types';
import {
	applyPreset,
	defaultRequirement,
	detailLabel,
	fitCountLabel,
	lengthLabel,
	PRESETS,
	presetOf,
	previewRequirement,
	spanLabel,
	summarize,
	summarizeDays
} from './requirements';

test('a week opens up every day, shorter presets keep the chosen days', () => {
	const week = defaultRequirement('Europe/London', '2026-11-02', 'week');
	expect(week.days).toEqual([0, 1, 2, 3, 4, 5, 6]);
	expect(presetOf(week)).toBe('week');

	const weekend = defaultRequirement('Europe/London', '2026-11-02', 'weekend');
	expect(weekend.days).toEqual([4, 5, 6]);
	expect(applyPreset(weekend, 'evening').days).toEqual([4, 5, 6]);
	expect(applyPreset(weekend, 'week').days).toEqual([0, 1, 2, 3, 4, 5, 6]);
});

const base = (preset: Parameters<typeof defaultRequirement>[2] = 'weekend') => defaultRequirement('Europe/London', '2026-11-02', preset);
const withLength = (length: RequirementLength, over: Partial<Requirement> = {}): Requirement => ({ ...base(), length, ...over });

describe('presets', () => {
	test('every preset round trips through presetOf', () => {
		for (const p of PRESETS) if (p.id !== 'custom') expect(presetOf(base(p.id))).toBe(p.id);
	});

	test('default range is three calendar months', () => {
		expect(base().range).toEqual({ from: '2026-11-02', to: '2027-01-31' });
	});

	test('odd lengths are custom', () => {
		expect(presetOf(withLength({ type: 'duration', minutes: 90 }))).toBe('custom');
		expect(presetOf(withLength({ type: 'days', count: 3 }))).toBe('custom');
		expect(presetOf(withLength({ type: 'days', count: 1 }, { window: { from: '17:00', to: '23:59' } }))).toBe('custom');
	});

	test('duration presets keep a customised window; custom changes nothing', () => {
		const twoHours = { ...base('2h'), window: { from: '10:00', to: '16:00' } };
		expect(applyPreset(twoHours, '4h').window).toEqual({ from: '10:00', to: '16:00' });
		expect(applyPreset(base('evening'), '2h').window).toEqual({ from: '08:00', to: '23:00' });
		const r = base();
		expect(applyPreset(r, 'custom')).toBe(r);
	});

	test('applyPreset does not share the preset objects', () => {
		const r = applyPreset(base(), 'weekend');
		(r.length as { startTime: string }).startTime = '09:00';
		expect(presetOf(base())).toBe('weekend');
	});
});

describe('labels', () => {
	test('summarizeDays', () => {
		expect(summarizeDays([])).toBe('any day');
		expect(summarizeDays([6, 5, 4, 3, 2, 1, 0])).toBe('any day');
		expect(summarizeDays([0, 1, 2, 3, 4])).toBe('weekdays');
		expect(summarizeDays([6, 5, 5])).toBe('weekends');
		expect(summarizeDays([4, 5, 6])).toBe('Fri–Sun');
		expect(summarizeDays([0, 2, 4])).toBe('Mon, Wed, Fri');
		expect(summarizeDays([1, 2])).toBe('Tue, Wed');
	});

	test('lengthLabel', () => {
		expect(lengthLabel(base('weekend'))).toBe('A weekend');
		expect(lengthLabel(base('evening'))).toBe('An evening from 18:00');
		expect(lengthLabel(withLength({ type: 'duration', minutes: 180 }))).toBe('3 hours');
		expect(lengthLabel(withLength({ type: 'duration', minutes: 60 }))).toBe('1 hour');
		expect(lengthLabel(withLength({ type: 'duration', minutes: 90 }))).toBe('90 minutes');
		expect(lengthLabel(withLength({ type: 'days', count: 1 }, { window: { from: '09:00', to: '17:00' } }))).toBe('One day');
		expect(lengthLabel(withLength({ type: 'days', count: 3 }))).toBe('3 days');
		expect(lengthLabel(withLength({ type: 'span', startDay: 0, startTime: '09:00', endDay: 2, endTime: '17:00', weeksLater: 0 }))).toBe('A span');
	});

	test('spanLabel', () => {
		expect(spanLabel({ type: 'span', startDay: 4, startTime: '18:00', endDay: 6, endTime: '23:59', weeksLater: 0 })).toBe('Fri from 18:00 → Sun');
		expect(spanLabel({ type: 'span', startDay: 1, startTime: '00:00', endDay: 3, endTime: '12:00', weeksLater: 1 })).toBe('Tue → following Thu 12:00');
	});

	test('detailLabel and summarize', () => {
		expect(summarize(base('weekend'))).toBe('A weekend · Fri from 18:00 → Sun · Nov – Jan');
		expect(summarize(base('evening'))).toBe('An evening from 18:00 · Fri–Sun · Nov – Jan');
		expect(detailLabel(base('2h'))).toBe('08:00–23:00 · Fri–Sun · Nov – Jan');
		expect(detailLabel(withLength({ type: 'days', count: 1 }, { window: { from: '09:00', to: '17:00' }, days: [] }))).toBe(
			'09:00–17:00 · any day · Nov – Jan'
		);
		expect(
			detailLabel({ ...base('day'), schoolHolidaysOnly: true, dates: [{ from: '2026-12-01', to: '2026-12-02' }] })
		).toBe('Fri–Sun · school holidays only · 1 date range');
	});

	test('fitCountLabel', () => {
		expect(fitCountLabel(base('week'), 1)).toBe('1 week');
		expect(fitCountLabel(withLength({ type: 'days', count: 3 }), 4)).toBe('4 options');
	});
});

describe('previewRequirement', () => {
	const realFetch = globalThis.fetch;
	afterEach(() => {
		globalThis.fetch = realFetch;
	});

	test('posts the requirement to the preview endpoint', async () => {
		let sent: { url: string; body: unknown } | null = null;
		globalThis.fetch = (async (url: string, init: RequestInit) => {
			sent = { url, body: JSON.parse(String(init.body)) };
			return new Response(JSON.stringify({ count: 13, from: '2026-11-02', to: '2027-01-31', summary: 'x' }));
		}) as typeof fetch;
		const req = base();
		expect((await previewRequirement(req)).count).toBe(13);
		expect(sent!).toEqual({ url: '/api/v1/requirements/preview', body: { requirement: req } });
	});
});
