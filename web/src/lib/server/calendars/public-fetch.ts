/**
 * fetch() for URLs that come from users or from servers they point us at
 * (CalDAV server URLs and the hrefs those servers return). Without this, a
 * "CalDAV server" could make us request cloud metadata, localhost or anything
 * on the internal network, on connect and again on every background sync.
 *
 * Every hop must be https: and every address its host resolves to must be a
 * public unicast address. Redirects are followed by hand so each one is checked,
 * and credentials are dropped when a redirect leaves the origin.
 *
 * (A host could still answer DNS differently between our check and fetch's own
 * lookup; pinning the address would need a custom TLS agent. This closes the
 * direct and redirect routes, which are the practical ones.)
 */
import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';
import { badRequest } from '../http';

const MAX_REDIRECTS = 5;

/** Overridable in tests so they don't need real DNS. */
export const resolver = {
	lookup: async (host: string): Promise<string[]> => (await lookup(host, { all: true, verbatim: true })).map((a) => a.address)
};

// ─── Address classification ───────────────────────────────────────────────

const V4_BLOCKED: [string, number][] = [
	['0.0.0.0', 8], // "this" network
	['10.0.0.0', 8], // private
	['100.64.0.0', 10], // carrier-grade NAT
	['127.0.0.0', 8], // loopback
	['169.254.0.0', 16], // link-local, including cloud metadata
	['172.16.0.0', 12], // private
	['192.0.0.0', 24], // IETF protocol assignments
	['192.0.2.0', 24], // documentation
	['192.88.99.0', 24], // 6to4 relay anycast
	['192.168.0.0', 16], // private
	['198.18.0.0', 15], // benchmarking
	['198.51.100.0', 24], // documentation
	['203.0.113.0', 24], // documentation
	['224.0.0.0', 4], // multicast
	['240.0.0.0', 4] // reserved and broadcast
];

const v4ToInt = (ip: string) => ip.split('.').reduce((n, part) => (n << 8) + Number(part), 0) >>> 0;

function isPublicV4(ip: string): boolean {
	const n = v4ToInt(ip);
	return !V4_BLOCKED.some(([base, bits]) => {
		const mask = bits === 0 ? 0 : (~0 << (32 - bits)) >>> 0;
		return (n & mask) >>> 0 === (v4ToInt(base) & mask) >>> 0;
	});
}

/** Expands an IPv6 address to its eight 16-bit groups. */
function v6Groups(ip: string): number[] {
	let text = ip.split('%')[0].toLowerCase();
	// A trailing dotted IPv4 (::ffff:1.2.3.4) becomes two groups.
	const v4 = text.match(/(\d+\.\d+\.\d+\.\d+)$/)?.[1];
	if (v4) {
		const n = v4ToInt(v4);
		text = text.slice(0, -v4.length) + `${(n >>> 16).toString(16)}:${(n & 0xffff).toString(16)}`;
	}
	const [head, tail] = text.split('::');
	const parse = (s: string | undefined) => (s ? s.split(':').map((g) => parseInt(g, 16)) : []);
	const front = parse(head);
	const back = parse(tail);
	const zeros = tail === undefined ? [] : new Array(8 - front.length - back.length).fill(0);
	return [...front, ...zeros, ...back];
}

function isPublicV6(ip: string): boolean {
	const g = v6Groups(ip);
	// IPv4-mapped (::ffff:a.b.c.d): judge the IPv4 address.
	if (g.slice(0, 5).every((x) => x === 0) && g[5] === 0xffff) {
		return isPublicV4(`${g[6] >> 8}.${g[6] & 0xff}.${g[7] >> 8}.${g[7] & 0xff}`);
	}
	// Only global unicast (2000::/3), minus documentation (2001:db8::/32).
	if ((g[0] & 0xe000) !== 0x2000) return false;
	if (g[0] === 0x2001 && g[1] === 0x0db8) return false;
	return true;
}

/** Whether `ip` is a public unicast address we're happy to connect to. */
export function isPublicAddress(ip: string): boolean {
	const family = isIP(ip);
	if (family === 4) return isPublicV4(ip);
	if (family === 6) return isPublicV6(ip);
	return false;
}

// ─── Fetch ────────────────────────────────────────────────────────────────

/** Throws unless `url` is https: and its host only resolves to public addresses. */
export async function assertPublicUrl(url: URL): Promise<void> {
	if (url.protocol !== 'https:') throw badRequest('Calendar servers must use https');
	if (url.username || url.password) throw badRequest('Put the username and password in their own fields');
	const host = url.hostname.replace(/^\[|\]$/g, '');
	let addresses: string[];
	if (isIP(host)) addresses = [host];
	else {
		try {
			addresses = await resolver.lookup(host);
		} catch {
			throw badRequest(`Couldn't find the server ${host}`);
		}
	}
	if (!addresses.length || !addresses.every(isPublicAddress)) {
		throw badRequest("That server address isn't allowed");
	}
}

/**
 * fetch() restricted to public https servers, re-checking every redirect.
 * Returns the response and the URL it finally came from.
 */
export async function publicFetch(input: URL | string, init: RequestInit = {}): Promise<{ res: Response; url: URL }> {
	let url = new URL(input);
	let headers = new Headers(init.headers);
	for (let hop = 0; ; hop++) {
		await assertPublicUrl(url);
		const res = await fetch(url, { ...init, headers, redirect: 'manual' });
		const location = res.status >= 300 && res.status < 400 ? res.headers.get('location') : null;
		if (!location) return { res, url };
		if (hop >= MAX_REDIRECTS) throw new Error(`Too many redirects from ${url.origin}`);
		await res.body?.cancel().catch(() => {});
		const next = new URL(location, url);
		if (next.origin !== url.origin) {
			// Like browsers: never send credentials on to another origin.
			headers = new Headers(headers);
			headers.delete('authorization');
		}
		url = next;
	}
}
