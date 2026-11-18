import { describe, expect, test } from 'bun:test';
import { attachmentSummary } from './attachments';
import { makePass, makeZip } from './wallet-pass.fixture';
import { passColour, passDetails, passTitle, readEntry, readWalletPass, zipEntries } from './wallet-pass';

describe('zip', () => {
	test('reads stored and deflated entries, within a limit', () => {
		const zip = makeZip({ 'a.txt': 'hello hello hello', 'b.txt': 'plain' }, ['b.txt']);
		const entries = zipEntries(zip)!;
		expect(entries.map((e) => e.name)).toEqual(['a.txt', 'b.txt']);
		expect(new TextDecoder().decode(readEntry(zip, entries[0], 100)!)).toBe('hello hello hello');
		expect(new TextDecoder().decode(readEntry(zip, entries[1], 100)!)).toBe('plain');
		expect(readEntry(zip, entries[0], 5)).toBeNull();
	});

	test('anything else is not a zip', () => {
		expect(zipEntries(new TextEncoder().encode('%PDF-1.4 tiny'))).toBeNull();
		expect(zipEntries(new Uint8Array(0))).toBeNull();
		const zip = makeZip({ 'a.txt': 'x'.repeat(1000) });
		// Lies about its size: inflating stops at the limit.
		const [entry] = zipEntries(zip)!;
		expect(readEntry(zip, { ...entry, size: 10 }, 10)).toBeNull();
	});
});

describe('passes', () => {
	test('reads the card details, with English strings', () => {
		const { pass, logo } = readWalletPass(makePass(), false)!;
		expect(logo).toBeNull();
		expect(pass).toMatchObject({
			style: 'eventTicket',
			organizationName: 'Roundhouse',
			description: 'Concert ticket',
			passTypeIdentifier: 'pass.test.availabliss',
			serialNumber: 'SEAT-12A',
			relevantDate: '2026-12-02T00:30:00.000Z',
			eventName: 'Winter Gig',
			venue: 'Roundhouse, London',
			backgroundColor: '#201840',
			foregroundColor: '#ffffff',
			labelColor: '#ffcc00',
			voided: false,
			count: 1
		});
		expect(pass.fields).toEqual([
			{ label: 'Event', value: 'Winter Gig', date: false },
			{ label: 'Doors', value: '2026-12-02T00:30:00.000Z', date: true },
			{ label: 'Seat', value: '12A', date: false },
			{ label: 'Row', value: '7', date: false }
		]);
	});

	test('a bundle shows its first pass and how many', () => {
		const bundle = makeZip({ 'a.pkpass': makePass(), 'b.pkpass': makePass() });
		expect(readWalletPass(bundle, true)?.pass).toMatchObject({ description: 'Concert ticket', count: 2 });
		expect(passTitle(readWalletPass(bundle, true)!.pass)).toBe('2 tickets');
	});

	test('not a pass: null', () => {
		expect(readWalletPass(makeZip({ 'pass.json': '{nope' }), false)).toBeNull();
		expect(readWalletPass(makeZip({ 'pass.json': '{"description":"x"}' }), false)).toBeNull();
		expect(readWalletPass(makeZip({ 'other.json': '{}' }), false)).toBeNull();
		expect(passDetails([1, 2])).toBeNull();
	});

	test('text is tidied and capped', () => {
		const pass = passDetails({
			passTypeIdentifier: 'p',
			serialNumber: 's',
			description: `a\u0000b\n${'x'.repeat(300)}`,
			organizationName: 4,
			generic: {}
		})!;
		expect(pass.description.startsWith('ab x')).toBe(true);
		expect([...pass.description].length).toBe(120);
		expect(pass.organizationName).toBe('4');
		expect(pass.style).toBe('generic');
	});

	test('colours', () => {
		expect(passColour('rgb(0, 128, 255)')).toBe('#0080ff');
		expect(passColour('rgb(300, 0, 0)')).toBeNull();
		expect(passColour('red')).toBeNull();
		expect(passColour(undefined)).toBeNull();
	});

	test('summaries for older apps', () => {
		const pass = readWalletPass(makePass(), false)!.pass;
		expect(attachmentSummary([{ kind: 'pass', name: 'gig.pkpass', pass }])).toBe('🎟️ Concert ticket');
		expect(attachmentSummary([{ kind: 'pass', name: 'gig.pkpass', pass: { ...pass, description: '', style: 'boardingPass' } }])).toBe(
			'🎟️ Boarding pass'
		);
		expect(
			attachmentSummary([
				{ kind: 'pass', name: 'a', pass },
				{ kind: 'pass', name: 'b', pass }
			])
		).toBe('🎟️ 2 passes');
		expect(
			attachmentSummary([
				{ kind: 'pass', name: 'a', pass },
				{ kind: 'image', name: 'b' }
			])
		).toBe('📎 2 attachments');
	});
});
