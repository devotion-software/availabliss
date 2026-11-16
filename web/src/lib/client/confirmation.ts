/** Pure helpers for the "Everyone's in" chat banner: which messages are confirmations, and when one bursts. */
import type { Message } from '#lib/types';

/** The server posts a system message carrying the event's id when a proposal is confirmed. */
export const isConfirmationMessage = (m: Pick<Message, 'kind' | 'eventId'>) => m.kind === 'system' && !!m.eventId;

/** Only confirmations from the last day celebrate; older ones just show the banner. */
export const BURST_WINDOW_MS = 24 * 60 * 60_000;

/** Whether a confirmation message is recent enough to burst (a future time from clock skew counts as recent). */
export function isRecentConfirmation(createdAt: string, now: number = Date.now()): boolean {
	const t = new Date(createdAt).getTime();
	return Number.isFinite(t) && now - t < BURST_WINDOW_MS;
}

/** Message ids that have already burst in this tab, so scrolling back or reopening the chat doesn't repeat it. */
const burst = new Set<string>();

/**
 * Whether the banner for `m` should play its confetti now: a recent confirmation that hasn't burst yet this session,
 * and the viewer hasn't asked for reduced motion. Claims the burst when it returns true.
 */
export function claimBurst(
	m: Pick<Message, 'id' | 'kind' | 'eventId' | 'createdAt'>,
	opts: { now?: number; reducedMotion?: boolean; seen?: Set<string> } = {}
): boolean {
	const seen = opts.seen ?? burst;
	if (opts.reducedMotion || !isConfirmationMessage(m) || m.id.startsWith('temp-')) return false;
	if (seen.has(m.id) || !isRecentConfirmation(m.createdAt, opts.now)) return false;
	seen.add(m.id);
	return true;
}
