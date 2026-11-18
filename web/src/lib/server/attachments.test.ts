import { describe, expect, test } from 'bun:test';
import { attachmentSummary, cleanName, signedUrl, verifySignature } from './attachments';

describe('attachmentSummary', () => {
	test('names what was sent', () => {
		expect(attachmentSummary([{ kind: 'image', name: 'a.jpg' }])).toBe('📷 Photo');
		expect(attachmentSummary([{ kind: 'gif', name: 'a.gif' }])).toBe('GIF');
		expect(attachmentSummary([{ kind: 'file', name: 'Plan.pdf' }])).toBe('📎 Plan.pdf');
		expect(attachmentSummary([{ kind: 'image', name: 'a' }, { kind: 'gif', name: 'b' }])).toBe('📷 2 photos');
		expect(attachmentSummary([{ kind: 'file', name: 'a' }, { kind: 'file', name: 'b' }])).toBe('📎 2 files');
		expect(attachmentSummary([{ kind: 'image', name: 'a' }, { kind: 'file', name: 'b' }])).toBe('📎 2 attachments');
		expect(attachmentSummary([])).toBe('');
	});
});

describe('cleanName', () => {
	test('drops folders and control characters', () => {
		expect(cleanName('../../etc/passwd')).toBe('passwd');
		expect(cleanName('C:\\Users\\me\\Plan.pdf')).toBe('Plan.pdf');
		expect(cleanName('a\u0000b\n.txt')).toBe('ab.txt');
		expect(cleanName('   ')).toBe('file');
	});

	test('long names keep their extension', () => {
		const out = cleanName(`${'x'.repeat(300)}.docx`);
		expect([...out].length).toBe(120);
		expect(out.endsWith('.docx')).toBe(true);
	});
});

describe('signed links', () => {
	const now = Date.parse('2026-11-18T10:00:00Z');
	const parts = (url: string) => {
		const u = new URL(url);
		return [u.searchParams.get('e'), u.searchParams.get('s')] as const;
	};

	test('a link verifies for its attachment and variant only', () => {
		const [e, s] = parts(signedUrl('att1', 'file', now));
		expect(verifySignature('att1', 'file', e, s, now)).toBe(true);
		expect(verifySignature('att2', 'file', e, s, now)).toBe(false);
		expect(verifySignature('att1', 'thumb', e, s, now)).toBe(false);
		expect(verifySignature('att1', 'file', String(Number(e) + 86_400), s, now)).toBe(false);
		expect(verifySignature('att1', 'file', e, null, now)).toBe(false);
	});

	test('links stay the same all day and expire after about a week', () => {
		expect(signedUrl('att1', 'file', now)).toBe(signedUrl('att1', 'file', now + 3_600_000));
		const [e, s] = parts(signedUrl('att1', 'file', now));
		expect(verifySignature('att1', 'file', e, s, now + 7 * 86_400_000)).toBe(true);
		expect(verifySignature('att1', 'file', e, s, now + 9 * 86_400_000)).toBe(false);
		expect(signedUrl('att1', 'thumb', now)).toContain('/api/v1/attachments/att1/thumb?');
	});
});
