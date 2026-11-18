import { describe, expect, test } from 'bun:test';
import { wakesApp } from './apns';

describe('wakesApp', () => {
	test('plan changes wake the app to refresh its widgets; chat and nudges do not', () => {
		expect(wakesApp('proposal.created')).toBe(true);
		expect(wakesApp('proposal.confirmed')).toBe(true);
		expect(wakesApp('event.created')).toBe(true);
		expect(wakesApp('reminder')).toBe(true);
		expect(wakesApp('message')).toBe(false);
		expect(wakesApp('nudge')).toBe(false);
		expect(wakesApp(undefined)).toBe(false);
	});
});
