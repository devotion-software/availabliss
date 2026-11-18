import { afterEach, describe, expect, spyOn, test } from 'bun:test';
import { errorFields, log, withRequestContext } from './log';

describe('log', () => {
	afterEach(() => {
		(console.warn as unknown as { mockRestore?: () => void }).mockRestore?.();
	});

	test('errors become name, message and a short stack', () => {
		const fields = errorFields(Object.assign(new Error('boom'), { status: 503 }));
		expect(fields).toMatchObject({ name: 'Error', message: 'boom', status: 503 });
		expect((fields.stack as string[]).length).toBeLessThanOrEqual(5);
		expect(errorFields('plain')).toEqual({ message: 'plain' });
	});

	test('lines carry the request they were logged in', () => {
		const warn = spyOn(console, 'warn').mockImplementation(() => {});
		withRequestContext({ requestId: 'req-1', userId: 'u1' }, () => log.warn('calendars', 'sync failed', { provider: 'google' }));
		const line = String(warn.mock.calls[0][0]);
		expect(line).toContain('calendars');
		expect(line).toContain('sync failed');
		expect(line).toContain('google');
	});
});
