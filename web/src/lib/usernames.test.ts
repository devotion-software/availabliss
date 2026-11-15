import { describe, expect, test } from 'bun:test';
import { normalizeUsername, usernameBase, usernameProblem } from './usernames';

describe('usernames', () => {
	test('normalizes what was typed', () => {
		expect(normalizeUsername('  @Sam.Taylor ')).toBe('sam.taylor');
	});

	test('explains what is wrong', () => {
		expect(usernameProblem('sam_t.99')).toBeNull();
		expect(usernameProblem('sa')).toBeTruthy();
		expect(usernameProblem('a'.repeat(21))).toBeTruthy();
		expect(usernameProblem('sam taylor')).toBeTruthy();
		expect(usernameProblem('.sam')).toBeTruthy();
		expect(usernameProblem('sam.')).toBeTruthy();
		expect(usernameProblem('sam..t')).toBeTruthy();
		expect(usernameProblem('admin')).toBeTruthy();
	});

	test('starts from the name, else the email', () => {
		expect(usernameBase('Zoë O’Brien', 'x@y.z')).toBe('zoeobrien');
		expect(usernameBase('', 'sam.t+cal@example.com')).toBe('samtcal');
		expect(usernameBase('', 'jo@example.com')).toBe('jouser');
		expect(usernameProblem(usernameBase('A very very long name indeed', null))).toBeNull();
	});
});
