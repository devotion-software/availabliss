/**
 * Counts failures of background work per kind and day (calendar syncs per provider, push sends per
 * platform, …) in the shared store, and logs each with today's count, so a provider that keeps
 * failing shows up in the logs ("failuresToday": 240) before users report it.
 */
import { kv } from './redis';
import { log } from './log';

/** Logs a failure of `kind` (e.g. "calendar-sync:google") with how many there have been today. */
export async function recordFailure(scope: string, kind: string, message: string, fields: Record<string, unknown> = {}): Promise<void> {
	let failuresToday: number | undefined;
	try {
		const day = new Date().toISOString().slice(0, 10);
		failuresToday = (await kv.hit(`failures:${kind}:${day}`, 2 * 86_400)).count;
	} catch {
		// The store being down mustn't hide the failure itself.
	}
	log.warn(scope, message, { kind, failuresToday, ...fields });
}
