/**
 * Apple Wallet passes (.pkpass, and .pkpasses bundles of several) sent in chat. A pass is a
 * signed ZIP; we keep the bytes exactly as sent (changing them would break the signature) and
 * only read pass.json (plus its English strings and logo) to show a card without downloading
 * it. Nothing inside is trusted or run: every read is size-limited and anything odd just
 * means "no details", and the upload is then kept as a plain file.
 */
import { inflateRawSync } from 'node:zlib';
import type { WalletPass, WalletPassField, WalletPassStyle } from '#lib/types';

/** Largest pass.json / pass.strings we read. */
const MAX_JSON_BYTES = 512 * 1024;
/** Largest logo we read (it is shrunk before it is stored). */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
/** Largest single pass inside a .pkpasses bundle. */
const MAX_INNER_PASS_BYTES = 25 * 1024 * 1024;
const MAX_ENTRIES = 2000;
const MAX_TEXT = 120;
const MAX_FIELDS = 4;

export const PASS_STYLES: readonly WalletPassStyle[] = ['boardingPass', 'eventTicket', 'coupon', 'storeCard', 'generic'];

// ─── ZIP ──────────────────────────────────────────────────────────────────

interface ZipEntry {
	name: string;
	method: number;
	compressedSize: number;
	size: number;
	localOffset: number;
}

/** The entries of a ZIP from its central directory, or null if it isn't one we can read. */
export function zipEntries(zip: Uint8Array): ZipEntry[] | null {
	const view = new DataView(zip.buffer, zip.byteOffset, zip.byteLength);
	// End of central directory: 22 bytes plus a comment of up to 64 KB, at the very end.
	let eocd = -1;
	for (let i = zip.byteLength - 22; i >= Math.max(0, zip.byteLength - 22 - 0xffff); i--) {
		if (view.getUint32(i, true) === 0x06054b50) {
			eocd = i;
			break;
		}
	}
	if (eocd < 0) return null;
	const count = view.getUint16(eocd + 10, true);
	let at = view.getUint32(eocd + 16, true);
	if (count > MAX_ENTRIES) return null;
	const entries: ZipEntry[] = [];
	const decoder = new TextDecoder();
	for (let n = 0; n < count; n++) {
		if (at + 46 > zip.byteLength || view.getUint32(at, true) !== 0x02014b50) return null;
		const nameLength = view.getUint16(at + 28, true);
		const extraLength = view.getUint16(at + 30, true);
		const commentLength = view.getUint16(at + 32, true);
		if (at + 46 + nameLength > zip.byteLength) return null;
		entries.push({
			name: decoder.decode(zip.subarray(at + 46, at + 46 + nameLength)),
			method: view.getUint16(at + 10, true),
			compressedSize: view.getUint32(at + 20, true),
			size: view.getUint32(at + 24, true),
			localOffset: view.getUint32(at + 42, true)
		});
		at += 46 + nameLength + extraLength + commentLength;
	}
	return entries;
}

/** One entry's bytes (stored or deflated), or null if it is too big, damaged or compressed some other way. */
export function readEntry(zip: Uint8Array, entry: ZipEntry, limit: number): Uint8Array | null {
	if (entry.size > limit || entry.compressedSize > zip.byteLength) return null;
	const view = new DataView(zip.buffer, zip.byteOffset, zip.byteLength);
	const local = entry.localOffset;
	if (local + 30 > zip.byteLength || view.getUint32(local, true) !== 0x04034b50) return null;
	const start = local + 30 + view.getUint16(local + 26, true) + view.getUint16(local + 28, true);
	const end = start + entry.compressedSize;
	if (end > zip.byteLength) return null;
	const raw = zip.subarray(start, end);
	try {
		if (entry.method === 0) return raw.byteLength <= limit ? raw : null;
		if (entry.method === 8) return new Uint8Array(inflateRawSync(raw, { maxOutputLength: limit }));
	} catch {
		// Damaged, or bigger than it said: no details.
	}
	return null;
}

// ─── pass.json ────────────────────────────────────────────────────────────

/** "rgb(23, 187, 82)" or "#17bb52" → "#17bb52"; anything else → null. */
export function passColour(value: unknown): string | null {
	if (typeof value !== 'string') return null;
	const hex = value.trim().match(/^#([0-9a-f]{6})$/i);
	if (hex) return `#${hex[1].toLowerCase()}`;
	const rgb = value.trim().match(/^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*[\d.]+\s*)?\)$/i);
	if (!rgb) return null;
	const parts = rgb.slice(1, 4).map(Number);
	if (parts.some((p) => p > 255)) return null;
	return `#${parts.map((p) => p.toString(16).padStart(2, '0')).join('')}`;
}

/** `"key" = "value";` lines of a .strings file (UTF-16 with a byte-order mark, or UTF-8). */
export function parseStrings(bytes: Uint8Array): Map<string, string> {
	const encoding = bytes[0] === 0xff && bytes[1] === 0xfe ? 'utf-16le' : bytes[0] === 0xfe && bytes[1] === 0xff ? 'utf-16be' : 'utf-8';
	const text = new TextDecoder(encoding).decode(bytes);
	const map = new Map<string, string>();
	const unescape = (s: string) => s.replace(/\\n/g, '\n').replace(/\\(["\\])/g, '$1');
	for (const m of text.matchAll(/"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;/g)) {
		if (map.size >= 5000) break;
		map.set(unescape(m[1]), unescape(m[2]));
	}
	return map;
}

const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

function cleanText(value: unknown, strings: Map<string, string>): string | null {
	if (typeof value === 'number' && Number.isFinite(value)) return String(value);
	if (typeof value !== 'string') return null;
	const text = (strings.get(value) ?? value)
		// eslint-disable-next-line no-control-regex
		.replace(/[\u0000-\u0009\u000b-\u001f\u007f]/g, '')
		.replace(/\s*\n\s*/g, ' ')
		.trim();
	if (!text) return null;
	const chars = [...text];
	return chars.length > MAX_TEXT ? `${chars.slice(0, MAX_TEXT - 1).join('')}…` : text;
}

/** An ISO 8601 date-time as UTC ("2026-12-01T19:30-05:00" → "2026-12-02T00:30:00.000Z"), or null. */
function isoDate(value: unknown): string | null {
	if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return null;
	const time = Date.parse(value);
	return Number.isNaN(time) ? null : new Date(time).toISOString();
}

/** The card details from a pass.json (already parsed), or null if it isn't a pass. */
export function passDetails(json: unknown, strings: Map<string, string> = new Map()): WalletPass | null {
	if (!isRecord(json)) return null;
	const style = PASS_STYLES.find((s) => isRecord(json[s])) ?? null;
	const passTypeIdentifier = typeof json.passTypeIdentifier === 'string' ? json.passTypeIdentifier.slice(0, 255) : null;
	const serialNumber = typeof json.serialNumber === 'string' ? json.serialNumber.slice(0, 255) : null;
	if (!style || !passTypeIdentifier || !serialNumber) return null;
	const structure = json[style] as Record<string, unknown>;
	const semantics = isRecord(json.semantics) ? json.semantics : {};

	const fields: WalletPassField[] = [];
	for (const group of ['primaryFields', 'secondaryFields', 'auxiliaryFields', 'headerFields']) {
		const list = structure[group];
		if (!Array.isArray(list)) continue;
		for (const field of list.slice(0, 20)) {
			if (fields.length >= MAX_FIELDS || !isRecord(field)) break;
			const date = 'dateStyle' in field || 'timeStyle' in field ? isoDate(field.value) : null;
			const value = date ?? cleanText(field.value, strings);
			if (!value) continue;
			fields.push({ label: cleanText(field.label, strings), value, date: date !== null });
		}
	}

	const relevantDates = Array.isArray(json.relevantDates) ? json.relevantDates.filter(isRecord) : [];
	const relevantDate =
		isoDate(json.relevantDate) ??
		isoDate(relevantDates[0]?.startDate) ??
		isoDate(relevantDates[0]?.date) ??
		isoDate(semantics.eventStartDate) ??
		isoDate(semantics.originalDepartureDate) ??
		isoDate(semantics.currentDepartureDate);

	return {
		style,
		organizationName: cleanText(json.organizationName, strings) ?? '',
		description: cleanText(json.description, strings) ?? '',
		logoText: cleanText(json.logoText, strings),
		passTypeIdentifier,
		serialNumber,
		relevantDate,
		expirationDate: isoDate(json.expirationDate),
		voided: json.voided === true,
		eventName: cleanText(semantics.eventName, strings),
		venue: cleanText(semantics.venueName, strings),
		fields,
		backgroundColor: passColour(json.backgroundColor),
		foregroundColor: passColour(json.foregroundColor),
		labelColor: passColour(json.labelColor),
		count: 1
	};
}

// ─── Reading a pass ───────────────────────────────────────────────────────

export interface ReadPass {
	pass: WalletPass;
	/** The pass's logo (or icon) PNG, to shrink into the card's thumbnail; null if it has none. */
	logo: Uint8Array | null;
}

function readSinglePass(zip: Uint8Array): ReadPass | null {
	const entries = zipEntries(zip);
	if (!entries) return null;
	const find = (name: string) => entries.find((e) => e.name === name);
	const jsonEntry = find('pass.json');
	const jsonBytes = jsonEntry && readEntry(zip, jsonEntry, MAX_JSON_BYTES);
	if (!jsonBytes) return null;
	let json: unknown;
	try {
		// Some passes start with a byte-order mark.
		json = JSON.parse(new TextDecoder().decode(jsonBytes).replace(/^\uFEFF/, ''));
	} catch {
		return null;
	}
	// Labels and values can be keys into a language's pass.strings: English, else the first one.
	const stringsEntry = find('en.lproj/pass.strings') ?? entries.find((e) => /^[^/]+\.lproj\/pass\.strings$/.test(e.name));
	const stringsBytes = stringsEntry && readEntry(zip, stringsEntry, MAX_JSON_BYTES);
	const pass = passDetails(json, stringsBytes ? parseStrings(stringsBytes) : new Map());
	if (!pass) return null;
	const logoEntry = ['logo@2x.png', 'logo@3x.png', 'logo.png', 'icon@2x.png', 'icon.png'].map(find).find(Boolean);
	return { pass, logo: (logoEntry && readEntry(zip, logoEntry, MAX_IMAGE_BYTES)) || null };
}

/**
 * The details of a .pkpass, or of the first pass in a .pkpasses bundle (with `count` set to
 * how many it holds). Null if it can't be read as a pass.
 */
export function readWalletPass(bytes: Uint8Array, bundle: boolean): ReadPass | null {
	if (!bundle) return readSinglePass(bytes);
	const entries = zipEntries(bytes);
	const inner = entries?.filter((e) => e.name.toLowerCase().endsWith('.pkpass') && !e.name.includes('/')) ?? [];
	if (!inner.length || inner.length > 10) return null;
	const first = readEntry(bytes, inner[0], MAX_INNER_PASS_BYTES);
	const read = first && readSinglePass(first);
	return read ? { ...read, pass: { ...read.pass, count: inner.length } } : null;
}

const STYLE_NAMES: Record<WalletPassStyle, string> = {
	boardingPass: 'Boarding pass',
	eventTicket: 'Ticket',
	coupon: 'Coupon',
	storeCard: 'Card',
	generic: 'Pass'
};

/** What a pass is, in a few words: its description, else "Ticket", "Boarding pass"… */
export function passTitle(pass: Pick<WalletPass, 'description' | 'style' | 'count'> | null): string {
	if (!pass) return 'Ticket';
	if (pass.count > 1)
		return `${pass.count} ${pass.style === 'boardingPass' ? 'boarding passes' : pass.style === 'eventTicket' ? 'tickets' : 'passes'}`;
	return pass.description || STYLE_NAMES[pass.style] || 'Ticket';
}
