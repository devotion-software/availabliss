import { describe, expect, test } from 'bun:test';
import { pushPreview } from './chat';

describe('pushPreview', () => {
	test('short text is unchanged', () => {
		expect(pushPreview('See you at 7')).toBe('See you at 7');
		expect(pushPreview('x'.repeat(200))).toBe('x'.repeat(200));
	});

	test('long text is cut to 200 characters with an ellipsis', () => {
		const out = pushPreview('word '.repeat(800));
		expect([...out].length).toBeLessThanOrEqual(200);
		expect(out.endsWith('…')).toBe(true);
		expect(out).not.toContain(' …');
	});

	test('never splits a surrogate pair', () => {
		const out = pushPreview('😀'.repeat(300));
		expect(out).toBe(`${'😀'.repeat(199)}…`);
	});
});
