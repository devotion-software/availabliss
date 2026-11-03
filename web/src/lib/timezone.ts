/** Shared by request validation (client and server) and the calendar parsers. */

const valid = new Map<string, boolean>();

/** Whether `zone` is an IANA time zone this runtime's Intl understands. */
export function isValidZone(zone: string): boolean {
	let ok = valid.get(zone);
	if (ok === undefined) {
		try {
			new Intl.DateTimeFormat('en-GB', { timeZone: zone });
			ok = true;
		} catch {
			ok = false;
		}
		if (valid.size < 1000) valid.set(zone, ok);
	}
	return ok;
}
