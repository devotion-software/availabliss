import { expect, test } from 'bun:test';
import { displayName } from './names';

test('displayName prefers the name, then the email, then the fallback', () => {
	expect(displayName({ name: 'Sam Lee', email: 'sam@example.com' })).toBe('Sam Lee');
	expect(displayName({ name: '  ', email: 'sam@example.com' })).toBe('sam@example.com');
	expect(displayName({ name: '', email: '' })).toBe('Someone');
	expect(displayName(undefined, 'New message')).toBe('New message');
});
