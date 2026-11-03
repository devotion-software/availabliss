/** Minimal RFC 5545 calendar file for "Add to calendar" on the web. */
import type { GroupEvent } from '#lib/types';
import { dateOf } from './availability/time';

// Any line break (\r\n, \r or \n) becomes the escaped \n, so text can't start a new property.
const escape = (s: string) => s.replace(/\\/g, '\\\\').replace(/\r\n?|\n/g, '\\n').replace(/[,;]/g, (c) => `\\${c}`);

/** Folds lines longer than 75 octets as the spec requires. */
function fold(line: string): string {
	const out: string[] = [];
	let rest = line;
	while (Buffer.byteLength(rest) > 75) {
		let cut = 75;
		while (Buffer.byteLength(rest.slice(0, cut)) > 75) cut--;
		out.push(rest.slice(0, cut));
		rest = ` ${rest.slice(cut)}`;
	}
	out.push(rest);
	return out.join('\r\n');
}

const utcStamp = (iso: string) => iso.replace(/[-:]/g, '').replace(/\.\d{3}/, '');
/** All-day events are calendar dates in the group's timezone, not UTC. */
const dateStamp = (iso: string, timeZone: string) => dateOf(Date.parse(iso), timeZone).replace(/-/g, '');

export function buildIcs(event: GroupEvent, groupName: string, host: string, timeZone: string): string {
	const timing = event.allDay
		? [`DTSTART;VALUE=DATE:${dateStamp(event.start, timeZone)}`, `DTEND;VALUE=DATE:${dateStamp(event.end, timeZone)}`]
		: [`DTSTART:${utcStamp(event.start)}`, `DTEND:${utcStamp(event.end)}`];
	const lines = [
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//Availabliss//EN',
		'CALSCALE:GREGORIAN',
		'METHOD:PUBLISH',
		'BEGIN:VEVENT',
		`UID:${event.id}@${host}`,
		`DTSTAMP:${utcStamp(new Date().toISOString())}`,
		...timing,
		`SUMMARY:${escape(event.title)}`,
		event.location && `LOCATION:${escape(event.location)}`,
		`DESCRIPTION:${escape([event.note, `Planned with ${groupName} on Availabliss`].filter(Boolean).join('\n\n'))}`,
		'END:VEVENT',
		'END:VCALENDAR'
	].filter((l): l is string => Boolean(l));
	return lines.map(fold).join('\r\n') + '\r\n';
}
