import { describe, expect, test } from 'bun:test';
import { tokenEmail } from './id-token';

const token = (payload: object) => `h.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.s`;

describe('tokenEmail', () => {
	test("reads the token's email, Hide My Email addresses included", () => {
		expect(tokenEmail(token({ email: 'ana@example.com' }))).toBe('ana@example.com');
		expect(tokenEmail(token({ email: 'x7k2@privaterelay.appleid.com' }))).toBe('x7k2@privaterelay.appleid.com');
	});

	test('null without a token, an email, or a readable payload', () => {
		expect(tokenEmail(null)).toBeNull();
		expect(tokenEmail(token({ sub: '1' }))).toBeNull();
		expect(tokenEmail('not-a-jwt')).toBeNull();
	});
});
