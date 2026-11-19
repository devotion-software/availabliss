import { describe, expect, test } from 'bun:test';
import type { Attachment, WalletPass } from '#lib/types';
import {
	attachmentLabel,
	attachmentSummary,
	bubbleSrc,
	downloadUrl,
	extensionOf,
	fileIconKind,
	fitImage,
	formatBytes,
	gridTiles,
	messageText,
	middleTruncate,
	opensWallet,
	passCard,
	passTitle,
	pickProblem,
	pickedKind,
	formatDuration
} from './attachments';

const att = (over: Partial<Attachment> = {}): Attachment => ({
	id: 'a1',
	kind: 'image',
	name: 'photo.jpg',
	mime: 'image/jpeg',
	size: 1000,
	width: 1200,
	height: 900,
	url: 'https://x.test/attachments/a1?e=1&s=2',
	thumbnailUrl: 'https://x.test/attachments/a1/thumb?e=1&s=2',
	pass: null,
	...over
});

describe('formatBytes', () => {
	test('bytes, KB, MB, GB', () => {
		expect(formatBytes(1)).toBe('1 byte');
		expect(formatBytes(512)).toBe('512 bytes');
		expect(formatBytes(1536)).toBe('1.5 KB');
		expect(formatBytes(340 * 1024)).toBe('340 KB');
		expect(formatBytes(1.24 * 1024 * 1024)).toBe('1.2 MB');
		expect(formatBytes(25 * 1024 * 1024)).toBe('25 MB');
		expect(formatBytes(3 * 1024 ** 3)).toBe('3 GB');
	});
	test('rounding up to the next unit', () => {
		expect(formatBytes(1024 * 1024 - 1)).toBe('1 MB');
	});
	test('nonsense is blank', () => {
		expect(formatBytes(-1)).toBe('');
		expect(formatBytes(NaN)).toBe('');
	});
});

describe('names', () => {
	test('extensionOf', () => {
		expect(extensionOf('Plan.PDF')).toBe('pdf');
		expect(extensionOf('archive.tar.zip')).toBe('zip');
		expect(extensionOf('.bashrc')).toBe('');
		expect(extensionOf('README')).toBe('');
		expect(extensionOf('trailing.')).toBe('');
	});
	test('middleTruncate keeps short names and the extension of long ones', () => {
		expect(middleTruncate('Plan.pdf')).toBe('Plan.pdf');
		const long = 'A very long name for the summer holiday plan final v2.pdf';
		const out = middleTruncate(long, 32);
		expect(out.length).toBe(32);
		expect(out).toContain('…');
		expect(out.endsWith('v2.pdf')).toBe(true);
		expect(out.startsWith('A very')).toBe(true);
	});
	test('fileIconKind', () => {
		expect(fileIconKind('Plan.pdf')).toBe('pdf');
		expect(fileIconKind('notes.docx')).toBe('doc');
		expect(fileIconKind('budget.xlsx')).toBe('sheet');
		expect(fileIconKind('deck.key')).toBe('slides');
		expect(fileIconKind('all.zip')).toBe('zip');
		expect(fileIconKind('song.m4a')).toBe('audio');
		expect(fileIconKind('clip.mov')).toBe('video');
		expect(fileIconKind('thing', 'audio/ogg')).toBe('audio');
		expect(fileIconKind('thing.bin')).toBe('file');
	});
});

describe('labels and summaries', () => {
	test('attachmentLabel', () => {
		expect(attachmentLabel(att())).toBe('Photo');
		expect(attachmentLabel(att({ kind: 'gif' }))).toBe('GIF');
		expect(attachmentLabel(att({ kind: 'file', name: 'Plan.pdf', size: 1.2 * 1024 * 1024 }))).toBe('File: Plan.pdf, 1.2 MB');
	});
	test('attachmentSummary', () => {
		expect(attachmentSummary([])).toBe('');
		expect(attachmentSummary([att()])).toBe('Photo');
		expect(attachmentSummary([att({ kind: 'gif' })])).toBe('GIF');
		expect(attachmentSummary([att({ kind: 'file', name: 'Plan.pdf' })])).toBe('Plan.pdf');
		expect(attachmentSummary([att(), att(), att()])).toBe('3 photos');
		expect(attachmentSummary([att({ kind: 'gif' }), att({ kind: 'gif' })])).toBe('2 GIFs');
		expect(attachmentSummary([att(), att({ kind: 'gif' })])).toBe('2 photos');
		expect(attachmentSummary([att({ kind: 'file' }), att({ kind: 'file' })])).toBe('2 files');
		expect(attachmentSummary([att(), att({ kind: 'file' })])).toBe('2 attachments');
	});
	test('messageText shows the caption with attachments, the body otherwise', () => {
		expect(messageText({ body: 'hi', caption: null, attachments: [] })).toBe('hi');
		expect(messageText({ body: '📷 Photo', caption: null, attachments: [att()] })).toBeNull();
		expect(messageText({ body: 'Look', caption: 'Look', attachments: [att()] })).toBe('Look');
	});
});

describe('urls', () => {
	test('bubbleSrc: GIFs animate from the original, photos use the preview', () => {
		expect(bubbleSrc(att())).toContain('/thumb');
		expect(bubbleSrc(att({ thumbnailUrl: null }))).not.toContain('/thumb');
		expect(bubbleSrc(att({ kind: 'gif' }))).not.toContain('/thumb');
	});
	test('downloadUrl', () => {
		expect(downloadUrl('https://x.test/a?e=1')).toBe('https://x.test/a?e=1&download=1');
		expect(downloadUrl('/a')).toBe('/a?download=1');
	});
});

describe('layout', () => {
	test('fitImage keeps the shape within the box', () => {
		expect(fitImage(1200, 900)).toEqual({ width: 240, height: 180 });
		expect(fitImage(900, 1600)).toEqual({ width: 180, height: 320 });
		expect(fitImage(100, 50)).toEqual({ width: 100, height: 80 });
	});
	test('fitImage gives extreme shapes a usable tile and unknown sizes a default', () => {
		expect(fitImage(4000, 100)).toEqual({ width: 240, height: 80 });
		expect(fitImage(null, null)).toEqual({ width: 240, height: 180 });
	});
	test('gridTiles', () => {
		expect(gridTiles(2).map((t) => t.wide)).toEqual([false, false]);
		expect(gridTiles(3).map((t) => t.wide)).toEqual([false, false, true]);
		expect(gridTiles(4).map((t) => t.more)).toEqual([0, 0, 0, 0]);
		const seven = gridTiles(7);
		expect(seven.length).toBe(4);
		expect(seven.map((t) => t.more)).toEqual([0, 0, 0, 3]);
		expect(seven.some((t) => t.wide)).toBe(false);
	});
});

describe('picking', () => {
	const file = (name: string, type: string, size = 1000) => ({ name, type, size });
	test('pickedKind', () => {
		expect(pickedKind(file('a.gif', 'image/gif'))).toBe('gif');
		expect(pickedKind(file('a.jpg', 'image/jpeg'))).toBe('image');
		expect(pickedKind(file('IMG_1.HEIC', ''))).toBe('image');
		expect(pickedKind(file('a.pdf', 'application/pdf'))).toBe('file');
	});
	test('pickProblem', () => {
		expect(pickProblem(file('a.jpg', 'image/jpeg'))).toBeNull();
		// Big photos are shrunk before uploading; big GIFs can't be.
		expect(pickProblem(file('a.jpg', 'image/jpeg', 16 * 1024 * 1024))).toBeNull();
		expect(pickProblem(file('a.gif', 'image/gif', 16 * 1024 * 1024))).toContain('15 MB');
		expect(pickProblem(file('a.pdf', 'application/pdf'))).toBeNull();
		expect(pickProblem(file('a.pdf', 'application/pdf', 26 * 1024 * 1024))).toContain('25 MB');
		expect(pickProblem(file('setup.exe', 'application/octet-stream'))).toContain('can’t be sent');
	});
});

describe('Wallet passes', () => {
	const pass: WalletPass = {
		style: 'eventTicket',
		organizationName: 'Roundhouse',
		description: 'Concert ticket',
		logoText: null,
		passTypeIdentifier: 'p',
		serialNumber: 's',
		relevantDate: '2026-12-02T00:30:00.000Z',
		expirationDate: null,
		voided: false,
		eventName: 'Winter Gig',
		venue: 'Roundhouse, London',
		fields: [
			{ label: 'Event', value: 'Winter Gig', date: false },
			{ label: 'Doors', value: '2026-12-02T00:30:00.000Z', date: true },
			{ label: 'Seat', value: '12A', date: false }
		],
		backgroundColor: '#201840',
		foregroundColor: '#ffffff',
		labelColor: null,
		count: 1
	};

	test("the card: event, date in the reader's zone, the details that add something", () => {
		expect(passCard(pass, 'en-GB', 'Europe/London')).toEqual({
			organization: 'Roundhouse',
			headline: 'Winter Gig',
			date: 'Wed 2 Dec · 00:30',
			venue: 'Roundhouse, London',
			fields: [{ label: 'Seat', value: '12A' }]
		});
		expect(passCard({ ...pass, count: 3 }).headline).toBe('3 tickets');
	});

	test('titles and summaries', () => {
		expect(passTitle(pass)).toBe('Concert ticket');
		expect(passTitle({ ...pass, description: '', style: 'boardingPass' })).toBe('Boarding pass');
		expect(passTitle(null)).toBe('Ticket');
		const a = att({ kind: 'pass', name: 'gig.pkpass', mime: 'application/vnd.apple.pkpass', pass });
		expect(attachmentSummary([a])).toBe('Concert ticket');
		expect(attachmentSummary([a, a])).toBe('2 passes');
		expect(attachmentSummary([a, att({ kind: 'file', name: 'Plan.pdf' })])).toBe('2 files');
		expect(attachmentLabel(a)).toBe('Wallet pass: Concert ticket');
		expect(fileIconKind('gig.pkpass')).toBe('pass');
		expect(pickProblem({ name: 'gig.pkpass', type: 'application/vnd.apple.pkpass', size: 2000 })).toBeNull();
	});

	test('which browsers hand passes to Wallet', () => {
		expect(
			opensWallet(
				'Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/140 Mobile/15E148 Safari/604.1'
			)
		).toBe(true);
		expect(opensWallet('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15')).toBe(
			true
		);
		expect(opensWallet('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36')).toBe(
			false
		);
		expect(opensWallet('Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36')).toBe(false);
	});
});

describe('videos', () => {
	test('picked as videos, labelled with their length', () => {
		expect(pickedKind({ name: 'clip.MOV', type: 'video/quicktime' })).toBe('video');
		expect(pickedKind({ name: 'clip.mp4', type: 'video/mp4' })).toBe('video');
		expect(formatDuration(95)).toBe('1:35');
		expect(formatDuration(7)).toBe('0:07');
		expect(attachmentSummary([{ kind: 'video', name: 'a.mp4' }])).toBe('Video');
		expect(
			attachmentSummary([
				{ kind: 'video', name: 'a.mp4' },
				{ kind: 'image', name: 'b.jpg' }
			])
		).toBe('2 photos and videos');
	});
});
