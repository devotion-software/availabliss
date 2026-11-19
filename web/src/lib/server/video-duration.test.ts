import { describe, expect, test } from 'bun:test';
import { videoDurationSeconds } from './video-duration';

/** A minimal MP4: ftyp, then moov with an mvhd (version 0 or 1). */
function mp4(timescale: number, duration: number, version = 0): Uint8Array {
	const box = (type: string, body: Uint8Array) => {
		const out = new Uint8Array(8 + body.length);
		new DataView(out.buffer).setUint32(0, out.length);
		out.set(new TextEncoder().encode(type), 4);
		out.set(body, 8);
		return out;
	};
	const mvhdBody = new Uint8Array(version === 1 ? 32 : 20);
	const v = new DataView(mvhdBody.buffer);
	v.setUint8(0, version);
	if (version === 1) {
		v.setUint32(20, timescale);
		v.setBigUint64(24, BigInt(duration));
	} else {
		v.setUint32(12, timescale);
		v.setUint32(16, duration);
	}
	const ftyp = box('ftyp', new TextEncoder().encode('isom0000'));
	const moov = box('moov', box('mvhd', mvhdBody));
	const out = new Uint8Array(ftyp.length + moov.length);
	out.set(ftyp);
	out.set(moov, ftyp.length);
	return out;
}

describe('video length from the header', () => {
	test('version 0 and 1 movie headers', () => {
		expect(videoDurationSeconds(mp4(600, 600 * 95))).toBe(95);
		expect(videoDurationSeconds(mp4(1000, 1000 * 61.5, 1))).toBe(61.5);
	});
	test('not a video, or a corrupt one', () => {
		expect(videoDurationSeconds(new TextEncoder().encode('%PDF-1.4 hello'))).toBeNull();
		const broken = mp4(600, 6000);
		new DataView(broken.buffer).setUint32(16, 0xffffffff); // moov claims to be huge
		expect(videoDurationSeconds(broken)).toBeNull();
		expect(videoDurationSeconds(new Uint8Array(0))).toBeNull();
	});
});
