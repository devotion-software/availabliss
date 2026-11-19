import { describe, expect, test } from 'bun:test';
import { byteRange } from './media';

describe('byte ranges', () => {
	test('start-end, open-ended and suffix', () => {
		expect(byteRange('bytes=0-1', 100)).toEqual({ start: 0, end: 1 });
		expect(byteRange('bytes=50-', 100)).toEqual({ start: 50, end: 99 });
		expect(byteRange('bytes=-10', 100)).toEqual({ start: 90, end: 99 });
		expect(byteRange('bytes=90-500', 100)).toEqual({ start: 90, end: 99 });
	});
	test('none, or unusable: the whole file', () => {
		expect(byteRange(null, 100)).toBeNull();
		expect(byteRange('bytes=200-300', 100)).toBeNull();
		expect(byteRange('bytes=0-1,5-6', 100)).toBeNull();
		expect(byteRange('items=0-1', 100)).toBeNull();
	});
});
