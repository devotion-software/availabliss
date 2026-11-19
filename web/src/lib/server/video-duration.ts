/**
 * A video's length, read from its header: MP4, M4V and QuickTime (MOV) files are a tree of boxes, and
 * the movie header (`moov` → `mvhd`) holds a timescale and a duration. No ffmpeg needed. Null when the
 * file isn't one of those or the header can't be found.
 */
export function videoDurationSeconds(bytes: Uint8Array): number | null {
	const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
	const moov = findBox(view, 0, bytes.byteLength, 'moov');
	if (!moov) return null;
	const mvhd = findBox(view, moov.start, moov.end, 'mvhd');
	if (!mvhd || mvhd.end - mvhd.start < 20) return null;
	const version = view.getUint8(mvhd.start);
	let timescale: number;
	let duration: number;
	if (version === 1) {
		if (mvhd.end - mvhd.start < 32) return null;
		timescale = view.getUint32(mvhd.start + 20);
		duration = Number(view.getBigUint64(mvhd.start + 24));
	} else {
		timescale = view.getUint32(mvhd.start + 12);
		duration = view.getUint32(mvhd.start + 16);
	}
	if (!timescale || !Number.isFinite(duration)) return null;
	return duration / timescale;
}

/** The first box of `type` between `from` and `to` (its contents, after the header). */
function findBox(view: DataView, from: number, to: number, type: string): { start: number; end: number } | null {
	let offset = from;
	// Bounded: a corrupt file can't make this loop forever (every box is at least 8 bytes).
	for (let i = 0; i < 100_000 && offset + 8 <= to; i++) {
		let size = view.getUint32(offset);
		const name = String.fromCharCode(view.getUint8(offset + 4), view.getUint8(offset + 5), view.getUint8(offset + 6), view.getUint8(offset + 7));
		let header = 8;
		if (size === 1) {
			if (offset + 16 > to) return null;
			const large = view.getBigUint64(offset + 8);
			if (large > BigInt(to - offset)) return null;
			size = Number(large);
			header = 16;
		} else if (size === 0) {
			size = to - offset;
		}
		if (size < header || offset + size > to) return null;
		if (name === type) return { start: offset + header, end: offset + size };
		offset += size;
	}
	return null;
}
