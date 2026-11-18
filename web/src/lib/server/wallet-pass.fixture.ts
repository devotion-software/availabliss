/** Test helpers: minimal ZIPs and Wallet passes (unsigned, so only good for reading). */
import { deflateRawSync } from 'node:zlib';

/** A ZIP of the given files, deflated unless `store` (or for names in `stored`). */
export function makeZip(files: Record<string, string | Uint8Array>, stored: string[] = []): Uint8Array<ArrayBuffer> {
	const encoder = new TextEncoder();
	const locals: Uint8Array[] = [];
	const centrals: Uint8Array[] = [];
	let offset = 0;
	for (const [name, content] of Object.entries(files)) {
		const data = typeof content === 'string' ? encoder.encode(content) : content;
		const store = stored.includes(name);
		const body = store ? data : new Uint8Array(deflateRawSync(data));
		const nameBytes = encoder.encode(name);
		const crc = Bun.hash.crc32(data);
		const local = new Uint8Array(30 + nameBytes.length);
		const lv = new DataView(local.buffer);
		lv.setUint32(0, 0x04034b50, true);
		lv.setUint16(4, 20, true);
		lv.setUint16(8, store ? 0 : 8, true);
		lv.setUint32(14, crc, true);
		lv.setUint32(18, body.length, true);
		lv.setUint32(22, data.length, true);
		lv.setUint16(26, nameBytes.length, true);
		local.set(nameBytes, 30);
		const central = new Uint8Array(46 + nameBytes.length);
		const cv = new DataView(central.buffer);
		cv.setUint32(0, 0x02014b50, true);
		cv.setUint16(4, 20, true);
		cv.setUint16(6, 20, true);
		cv.setUint16(10, store ? 0 : 8, true);
		cv.setUint32(16, crc, true);
		cv.setUint32(20, body.length, true);
		cv.setUint32(24, data.length, true);
		cv.setUint16(28, nameBytes.length, true);
		cv.setUint32(42, offset, true);
		central.set(nameBytes, 46);
		locals.push(local, body);
		centrals.push(central);
		offset += local.length + body.length;
	}
	const centralSize = centrals.reduce((n, c) => n + c.length, 0);
	const end = new Uint8Array(22);
	const ev = new DataView(end.buffer);
	ev.setUint32(0, 0x06054b50, true);
	ev.setUint16(8, centrals.length, true);
	ev.setUint16(10, centrals.length, true);
	ev.setUint32(12, centralSize, true);
	ev.setUint32(16, offset, true);
	const out = new Uint8Array(offset + centralSize + 22);
	let at = 0;
	for (const part of [...locals, ...centrals, end]) {
		out.set(part, at);
		at += part.length;
	}
	return out;
}

export const concertPassJson = {
	formatVersion: 1,
	passTypeIdentifier: 'pass.test.availabliss',
	serialNumber: 'SEAT-12A',
	teamIdentifier: 'ABCDE12345',
	organizationName: 'Roundhouse',
	description: 'EVENT_DESCRIPTION',
	logoText: 'Roundhouse',
	relevantDate: '2026-12-01T19:30-05:00',
	backgroundColor: 'rgb(32, 24, 64)',
	foregroundColor: 'rgb(255, 255, 255)',
	labelColor: '#FFCC00',
	semantics: { eventName: 'Winter Gig', venueName: 'Roundhouse, London' },
	eventTicket: {
		primaryFields: [{ key: 'event', label: 'EVENT', value: 'Winter Gig' }],
		secondaryFields: [
			{ key: 'doors', label: 'Doors', value: '2026-12-01T19:30-05:00', dateStyle: 'PKDateStyleMedium', timeStyle: 'PKDateStyleShort' },
			{ key: 'seat', label: 'Seat', value: '12A' }
		],
		auxiliaryFields: [{ key: 'row', label: 'Row', value: 7 }, { key: 'x', label: 'Extra', value: 'dropped (only four)' }]
	}
};

/** A small .pkpass for a concert (pass.json, English strings and, optionally, a logo). */
export function makePass(json: object = concertPassJson, logo: Uint8Array | null = null): Uint8Array<ArrayBuffer> {
	const strings = '﻿"EVENT_DESCRIPTION" = "Concert ticket";\n"EVENT" = "Event";\n';
	const utf16 = new Uint8Array([0xff, 0xfe, ...new Uint8Array(Buffer.from(strings.slice(1), 'utf16le'))]);
	return makeZip({
		'pass.json': JSON.stringify(json),
		'en.lproj/pass.strings': utf16,
		manifest: '{}',
		...(logo ? { 'logo@2x.png': logo } : {})
	});
}
