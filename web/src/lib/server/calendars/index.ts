/**
 * Calendar connections: linking providers, choosing which calendars mean busy,
 * and keeping `busy_intervals` up to date. Only start/end times are stored.
 *
 * Busy time is kept from a week back to a horizon at least three years ahead,
 * further when one of the user's groups plans past that (up to ten years).
 * Syncs only move what changed: Google lists changed events, CalDAV calendars
 * whose change tag hasn't moved are skipped, and calendars with no way to see
 * changes (Outlook, some CalDAV servers) re-read the next six months every sync
 * and the rest daily. Writes are diffs against what's stored.
 *
 * The work is split by job:
 * - connections.ts: reading, connecting, settings, "Sync now", removal
 * - scheduling.ts: one sync per connection at a time, background runs
 * - sync.ts: one sync of one connection
 * - coverage.ts: how far ahead busy time is kept
 * - busy-store.ts: writing calendars and busy time as diffs
 * - push.ts: change notifications
 * - caldav-checks.ts: CalDAV change-tag checks between syncs
 * - device.ts: uploads from the phone apps
 */
export { adapters } from './adapters';
export { horizonFor, refreshCoverage, planCoverage, type Coverage } from './coverage';
export { titleContext, openTitle } from './busy-store';
export type { SyncOptions } from './sync';
export { syncConnection, syncStaleConnections } from './scheduling';
export { checkConnection, checkCaldavConnections } from './caldav-checks';
export { syncDevice, type DeviceSyncInput } from './device';
export {
	listConnections,
	getConnection,
	connectOAuth,
	connectCaldav,
	setCalendarBusy,
	SYNC_NOW_GAP_SECONDS,
	syncNow,
	removeConnection
} from './connections';
