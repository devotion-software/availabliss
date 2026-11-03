/**
 * Rate limits for /api/v1 (Better Auth limits its own /api/auth routes, see
 * auth.ts). Fixed windows counted in the shared store, keyed by user when signed
 * in and by IP otherwise, so limits hold across instances.
 *
 * Rules are checked in order and the first match wins. Every request also
 * counts against the general per-user (or per-IP) budget.
 */
import type { KeyValueStore } from './redis';

export interface Rule {
	/** Bucket name, also used in the counter key. */
	name: string;
	method?: string;
	path: RegExp;
	max: number;
	windowSeconds: number;
	/** Count per IP even when signed in (endpoints that guess at secrets). */
	perIp?: boolean;
}

const MINUTE = 60;
const HOUR = 60 * MINUTE;

/** Specific limits for endpoints that are expensive, reach third parties or guess at tokens. */
export const RULES: Rule[] = [
	{ name: 'native-exchange', method: 'POST', path: /^\/api\/v1\/auth\/native\/exchange$/, max: 10, windowSeconds: MINUTE, perIp: true },
	{ name: 'invite', path: /^\/api\/v1\/invites\/[^/]+(\/accept)?$/, max: 30, windowSeconds: 10 * MINUTE, perIp: true },
	{ name: 'live', method: 'GET', path: /^\/api\/v1\/live$/, max: 20, windowSeconds: MINUTE },
	{ name: 'calendar-sync', method: 'POST', path: /^\/api\/v1\/calendars\/[^/]+\/sync$/, max: 6, windowSeconds: MINUTE },
	{ name: 'calendar-connect', method: 'POST', path: /^\/api\/v1\/calendars\/(caldav|oauth\/[^/]+\/(url|complete))$/, max: 10, windowSeconds: 10 * MINUTE },
	{ name: 'device-busy', method: 'PUT', path: /^\/api\/v1\/calendars\/device$/, max: 30, windowSeconds: MINUTE },
	{ name: 'group-create', method: 'POST', path: /^\/api\/v1\/groups$/, max: 20, windowSeconds: HOUR },
	{ name: 'invite-create', method: 'POST', path: /^\/api\/v1\/groups\/[^/]+\/invites$/, max: 30, windowSeconds: HOUR },
	{ name: 'message', method: 'POST', path: /^\/api\/v1\/groups\/[^/]+\/messages$/, max: 30, windowSeconds: MINUTE },
	{ name: 'availability', method: 'GET', path: /^\/api\/v1\/groups\/[^/]+\/availability(\/day)?$/, max: 120, windowSeconds: MINUTE },
	{ name: 'preview', method: 'POST', path: /^\/api\/v1\/requirements\/preview$/, max: 120, windowSeconds: MINUTE },
	{ name: 'ai-planner', method: 'POST', path: /^\/api\/v1\/groups\/[^/]+\/planner$/, max: 20, windowSeconds: HOUR },
	{ name: 'region-detect', method: 'GET', path: /^\/api\/v1\/regions\/detect$/, max: 30, windowSeconds: MINUTE, perIp: true }
];

/** General budgets every /api/v1 request counts against. */
export const GENERAL = {
	read: { max: 600, windowSeconds: MINUTE },
	write: { max: 120, windowSeconds: MINUTE },
	anonymous: { max: 120, windowSeconds: MINUTE }
} as const;

export interface Caller {
	userId: string | null;
	ip: string;
}

export type Decision = { allowed: true } | { allowed: false; retryAfter: number; bucket: string };

export function matchRule(method: string, path: string, rules: Rule[] = RULES): Rule | undefined {
	return rules.find((r) => (!r.method || r.method === method) && r.path.test(path));
}

/** Records the request and decides whether it may proceed. */
export async function checkRateLimit(store: KeyValueStore, method: string, path: string, caller: Caller): Promise<Decision> {
	const who = caller.userId ? `u:${caller.userId}` : `ip:${caller.ip}`;
	const buckets: { name: string; key: string; max: number; windowSeconds: number }[] = [];

	const rule = matchRule(method, path);
	if (rule) {
		const subject = rule.perIp ? `ip:${caller.ip}` : who;
		buckets.push({ name: rule.name, key: `rl:${rule.name}:${subject}`, max: rule.max, windowSeconds: rule.windowSeconds });
	}
	const general = !caller.userId ? GENERAL.anonymous : method === 'GET' || method === 'HEAD' ? GENERAL.read : GENERAL.write;
	const kind = !caller.userId ? 'anonymous' : method === 'GET' || method === 'HEAD' ? 'read' : 'write';
	buckets.push({ name: kind, key: `rl:${kind}:${who}`, ...general });

	for (const bucket of buckets) {
		const { count, resetSeconds } = await store.hit(bucket.key, bucket.windowSeconds);
		if (count > bucket.max) return { allowed: false, retryAfter: resetSeconds, bucket: bucket.name };
	}
	return { allowed: true };
}

/** One sign-in code per email address is plenty; stops someone flooding another person's inbox from many IPs. */
export const OTP_PER_EMAIL = { max: 5, windowSeconds: 15 * MINUTE };

export async function checkOtpEmail(store: KeyValueStore, email: string): Promise<Decision> {
	const { count, resetSeconds } = await store.hit(`rl:otp-email:${email.trim().toLowerCase()}`, OTP_PER_EMAIL.windowSeconds);
	return count > OTP_PER_EMAIL.max ? { allowed: false, retryAfter: resetSeconds, bucket: 'otp-email' } : { allowed: true };
}

function wait(seconds: number): string {
	const [n, unit] = seconds < 60 ? [seconds, 'second'] : [Math.ceil(seconds / 60), 'minute'];
	return `${n} ${unit}${n === 1 ? '' : 's'}`;
}

export function tooManyRequests(retryAfter: number): Response {
	return Response.json(
		{ message: `Too many requests. Try again in ${wait(retryAfter)}.`, code: 'rate_limited' },
		{ status: 429, headers: { 'retry-after': String(retryAfter) } }
	);
}
