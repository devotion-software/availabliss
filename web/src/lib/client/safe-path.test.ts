import { describe, expect, test } from 'bun:test';
import { safeRedirectPath } from './safe-path';
import { safeRedirect } from '#lib/server/calendars/oauth';

describe('safeRedirectPath', () => {
	test('keeps same-origin paths', () => {
		expect(safeRedirectPath('/app')).toBe('/app');
		expect(safeRedirectPath('/app/groups/1?tab=chat#x')).toBe('/app/groups/1?tab=chat#x');
	});

	test.each([
		null,
		'',
		'app',
		'https://evil.com',
		'//evil.com',
		'/\\evil.com',
		'/\\/evil.com',
		'/\t/evil.com',
		'/%09/evil.com'.replace('%09', '\t'),
		'/\n/evil.com',
		'/\r/evil.com',
		'/\u0000x'
	])('rejects %p', (value) => {
		expect(safeRedirectPath(value)).toBeNull();
	});
});

describe('safeRedirect (calendar OAuth)', () => {
	test('allows app paths and the native scheme', () => {
		expect(safeRedirect('/app/calendars')).toBe('/app/calendars');
		expect(safeRedirect('availabliss://calendars')).toBe('availabliss://calendars');
	});

	test.each(['/\\evil.com', '//evil.com', '/\t/evil.com', 'https://evil.com', 'availabliss://x\n', null])('falls back for %p', (value) => {
		expect(safeRedirect(value)).toBe('/app/calendars');
	});
});
