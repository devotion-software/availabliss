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

/** One VEVENT. `sequence` goes up whenever the plan changes, so subscribed calendars update it. */
function eventLines(event: GroupEvent, groupName: string, host: string, timeZone: string, sequence?: number): string[] {
	const timing = event.allDay
		? [`DTSTART;VALUE=DATE:${dateStamp(event.start, timeZone)}`, `DTEND;VALUE=DATE:${dateStamp(event.end, timeZone)}`]
		: [`DTSTART:${utcStamp(event.start)}`, `DTEND:${utcStamp(event.end)}`];
	return [
		'BEGIN:VEVENT',
		`UID:${event.id}@${host}`,
		`DTSTAMP:${utcStamp(new Date().toISOString())}`,
		sequence !== undefined ? `SEQUENCE:${sequence}` : null,
		...timing,
		`SUMMARY:${escape(event.title)}`,
		event.location ? `LOCATION:${escape(event.location)}` : null,
		`DESCRIPTION:${escape([event.note, `Planned with ${groupName} on Availabliss`].filter(Boolean).join('\n\n'))}`,
		'END:VEVENT'
	].filter((l): l is string => Boolean(l));
}

const finish = (lines: string[]) => lines.map(fold).join('\r\n') + '\r\n';

export function buildIcs(event: GroupEvent, groupName: string, host: string, timeZone: string): string {
	return finish([
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//Availabliss//EN',
		'CALSCALE:GREGORIAN',
		'METHOD:PUBLISH',
		...eventLines(event, groupName, host, timeZone),
		'END:VCALENDAR'
	]);
}

export interface FeedItem {
	event: GroupEvent;
	groupName: string;
	timeZone: string;
	/** When the plan last changed: becomes its SEQUENCE. */
	updatedAt: Date;
}

/**
 * A subscribable calendar (webcal): every plan in `items`, with stable UIDs and a SEQUENCE that
 * rises with each change. Plans that are cancelled simply drop out, and calendar apps remove them.
 */
export function buildFeed(name: string, items: FeedItem[], host: string): string {
	return finish([
		'BEGIN:VCALENDAR',
		'VERSION:2.0',
		'PRODID:-//Availabliss//EN',
		'CALSCALE:GREGORIAN',
		'METHOD:PUBLISH',
		`X-WR-CALNAME:${escape(name)}`,
		'X-PUBLISHED-TTL:PT1H',
		'REFRESH-INTERVAL;VALUE=DURATION:PT1H',
		...items.flatMap((i) => eventLines(i.event, i.groupName, host, i.timeZone, Math.floor(i.updatedAt.getTime() / 1000) - 1_700_000_000)),
		'END:VCALENDAR'
	]);
}
