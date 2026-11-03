import { expect, test } from 'bun:test';
import { monogram } from './monogram';

test('monogram', () => {
	expect(monogram('Lake District crew')).toBe('LD');
	expect(monogram('Book club')).toBe('BC');
	expect(monogram('Five')).toBe('FI');
	expect(monogram('  ')).toBe('?');
	expect(monogram('')).toBe('?');
	expect(monogram('🎉 ölçü party')).toBe('ÖP');
});
