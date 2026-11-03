/**
 * CalDAV (iCloud and other servers) with an app-specific password. The server
 * expands recurring events for us (`<C:expand>`), so we only parse VEVENT start,
 * end and transparency; summaries are ignored.
 */
import { minutesOf, zonedInstant } from '../availability/time';
import { ReauthRequired, type CaldavCredentials, type CalendarAdapter, type RemoteBusy, type RemoteCalendar } from './types';
import { isValidZone } from '#lib/timezone';
import { publicFetch } from './public-fetch';

export const ICLOUD_CALDAV = 'https://caldav.icloud.com';

// ─── XML (namespace-prefix agnostic) ──────────────────────────────────────

const tag = (name: string) => `(?:[A-Za-z0-9_-]+:)?${name}`;

export function xmlBlocks(xml: string, name: string): string[] {
	const re = new RegExp(`<${tag(name)}\\b[^>]*>([\\s\\S]*?)</${tag(name)}>`, 'g');
	return [...xml.matchAll(re)].map((m) => m[1]);
}

export function xmlText(xml: string, name: string): string | null {
	const block = xmlBlocks(xml, name)[0];
	return block === undefined ? null : decodeEntities(block.replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1').trim());
}

const hasTag = (xml: string, name: string) => new RegExp(`<${tag(name)}\\b`).test(xml);

function decodeEntities(s: string): string {
	return s
		.replace(/&#(\d+);/g, (_, n) => String.fromCharCode(Number(n)))
		.replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCharCode(parseInt(n, 16)))
		.replace(/&lt;/g, '<')
		.replace(/&gt;/g, '>')
		.replace(/&quot;/g, '"')
		.replace(/&apos;/g, "'")
		.replace(/&amp;/g, '&');
}

// ─── iCalendar ────────────────────────────────────────────────────────────

/** Parses `20261113T180000Z`, `20261113T180000` (TZID or floating) and `20261113` (all-day). */
function parseIcsTime(value: string, params: string, timeZone: string): { at: number; date: boolean } | null {
	const m = value.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?$/);
	if (!m) return null;
	const date = `${m[1]}-${m[2]}-${m[3]}`;
	if (!m[4]) return { at: zonedInstant(date, 0, timeZone), date: true };
	if (m[7]) return { at: Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]), date: false };
	const tzid = params.match(/TZID=([^;:]+)/)?.[1] ?? timeZone;
	const zone = isValidZone(tzid) ? tzid : timeZone;
	return { at: zonedInstant(date, minutesOf(`${m[4]}:${m[5]}`), zone) + Number(m[6]) * 1000, date: false };
}


/** ISO 8601 durations like PT1H30M or P1D. */
function parseDuration(value: string): number {
	const m = value.match(/^-?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/);
	if (!m) return 0;
	const [, w, d, h, min, s] = m.map((v) => Number(v ?? 0));
	return ((((w * 7 + d) * 24 + h) * 60 + min) * 60 + s) * 1000;
}

/** Busy spans from the VEVENTs in an iCalendar document. */
export function parseBusyEvents(ics: string, timeZone: string): { start: number; end: number }[] {
	const unfolded = ics.replace(/\r?\n[ \t]/g, '');
	const events = unfolded.split(/BEGIN:VEVENT/).slice(1).map((chunk) => chunk.split(/END:VEVENT/)[0]);
	const out: { start: number; end: number }[] = [];

	for (const body of events) {
		const props = new Map<string, { params: string; value: string }>();
		for (const line of body.split(/\r?\n/)) {
			const m = line.match(/^([A-Z-]+)((?:;[^:]*)?):(.*)$/);
			if (m && !props.has(m[1])) props.set(m[1], { params: m[2], value: m[3].trim() });
		}
		if (props.get('TRANSP')?.value === 'TRANSPARENT' || props.get('STATUS')?.value === 'CANCELLED') continue;

		const dtstart = props.get('DTSTART');
		const start = dtstart && parseIcsTime(dtstart.value, dtstart.params, timeZone);
		if (!start) continue;
		const dtend = props.get('DTEND');
		const duration = props.get('DURATION');
		const end = dtend
			? parseIcsTime(dtend.value, dtend.params, timeZone)?.at
			: duration
				? start.at + parseDuration(duration.value)
				: start.at + (start.date ? 24 * 60 * 60 * 1000 : 0);
		if (end && end > start.at) out.push({ start: start.at, end });
	}
	return out;
}

// ─── HTTP ─────────────────────────────────────────────────────────────────

async function dav(creds: CaldavCredentials, url: string, method: 'PROPFIND' | 'REPORT', depth: '0' | '1', body: string) {
	const { res, url: finalUrl } = await publicFetch(new URL(url, creds.serverUrl), {
		method,
		headers: {
			authorization: `Basic ${Buffer.from(`${creds.username}:${creds.password}`).toString('base64')}`,
			depth,
			'content-type': 'application/xml; charset=utf-8'
		},
		body,
		signal: AbortSignal.timeout(20_000)
	});
	if (res.status === 401 || res.status === 403) throw new ReauthRequired('CalDAV credentials were rejected');
	if (res.status !== 207 && !res.ok) throw new Error(`CalDAV ${method} ${url} → ${res.status}`);
	return { xml: await res.text(), url: finalUrl.toString() };
}

const PROPFIND = (props: string) =>
	`<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/"><d:prop>${props}</d:prop></d:propfind>`;

async function discoverHome(creds: CaldavCredentials): Promise<string> {
	const principal = await dav(creds, creds.serverUrl, 'PROPFIND', '0', PROPFIND('<d:current-user-principal/>'));
	const principalHref = xmlText(xmlBlocks(principal.xml, 'current-user-principal')[0] ?? '', 'href');
	if (!principalHref) throw new Error('CalDAV server did not return a principal');
	const home = await dav(creds, principalHref, 'PROPFIND', '0', PROPFIND('<c:calendar-home-set/>'));
	const homeHref = xmlText(xmlBlocks(home.xml, 'calendar-home-set')[0] ?? '', 'href');
	if (!homeHref) throw new Error('CalDAV server did not return a calendar home');
	return new URL(homeHref, home.url).toString();
}

export const caldav: CalendarAdapter<CaldavCredentials> = {
	async listCalendars(creds) {
		const home = await discoverHome(creds);
		const { xml, url } = await dav(
			creds,
			home,
			'PROPFIND',
			'1',
			PROPFIND('<d:resourcetype/><d:displayname/><a:calendar-color/><c:supported-calendar-component-set/>')
		);
		const calendars: RemoteCalendar[] = [];
		for (const response of xmlBlocks(xml, 'response')) {
			const resourceType = xmlBlocks(response, 'resourcetype')[0] ?? '';
			const components = xmlBlocks(response, 'supported-calendar-component-set')[0];
			if (!hasTag(resourceType, 'calendar') || (components && !components.includes('VEVENT'))) continue;
			const href = xmlText(response, 'href');
			if (!href) continue;
			calendars.push({
				externalId: new URL(href, url).toString(),
				name: xmlText(response, 'displayname') || 'Calendar',
				color: xmlText(response, 'calendar-color')?.slice(0, 7) ?? null
			});
		}
		return calendars;
	},

	async busy(creds, calendars, from, to, timeZone) {
		const stamp = (d: Date) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
		const range = `start="${stamp(from)}" end="${stamp(to)}"`;
		const body = `<?xml version="1.0" encoding="utf-8"?>
<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:prop><c:calendar-data><c:expand ${range}/></c:calendar-data></d:prop>
  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"><c:time-range ${range}/></c:comp-filter></c:comp-filter></c:filter>
</c:calendar-query>`;

		const out: RemoteBusy[] = [];
		for (const cal of calendars) {
			const { xml } = await dav(creds, cal.externalId, 'REPORT', '1', body);
			for (const data of xmlBlocks(xml, 'calendar-data')) {
				for (const span of parseBusyEvents(decodeEntities(data), timeZone)) {
					out.push({ calendarExternalId: cal.externalId, start: new Date(span.start), end: new Date(span.end) });
				}
			}
		}
		return out;
	}
};
