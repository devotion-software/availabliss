/**
 * One shared EventSource on /api/v1/live for the whole tab. Components
 * subscribe by event type and get a typed payload; the stream opens on the
 * first subscription and closes when the last one goes away.
 */
import type {
	AppNotification,
	Group,
	GroupEvent,
	LiveEvent,
	LiveEventType,
	Member,
	Message,
	Poll,
	Proposal
} from '#lib/types';
import { API_BASE } from './api';

/** What each live event carries (docs/API.md "Live updates"). */
export interface ReceiptsPayload {
	userId: string;
	state: 'delivered' | 'seen';
	receipts: { messageId: string; delivered: number; seen: number; recipients: number; seenBy: string[] }[];
}

export interface LivePayloads {
	'message.created': Message;
	'message.receipts': ReceiptsPayload;
	'poll.updated': Poll;
	'proposal.created': Proposal;
	'proposal.updated': Proposal;
	'proposal.confirmed': { proposal: Proposal; event: GroupEvent };
	'event.changed': GroupEvent | { id: string; deleted: true };
	'group.updated': Group;
	'member.changed': Member | { userId: string; removed: true };
	/** Someone's busy time changed: refetch availability. */
	'availability.changed': { userId: string };
	'notification.created': AppNotification;
}

/** The proposal inside any proposal.* payload. */
export function proposalOf(event: TypedLiveEvent<'proposal.created' | 'proposal.updated' | 'proposal.confirmed'>): Proposal {
	return event.type === 'proposal.confirmed' ? (event.payload as LivePayloads['proposal.confirmed']).proposal : (event.payload as Proposal);
}

export interface TypedLiveEvent<T extends LiveEventType> {
	type: T;
	groupId: string | null;
	payload: LivePayloads[T];
}

type Handler = (event: LiveEvent) => void;

const MAX_BACKOFF_MS = 30_000;

class LiveConnection {
	/** True while the stream is open; drives "reconnecting" hints. */
	connected = $state(false);

	#source: EventSource | null = null;
	#handlers = new Map<LiveEventType, Set<Handler>>();
	#reconnectHandlers = new Set<() => void>();
	#count = 0;
	/** Whether the stream has been open before, so the next open is a reconnect. */
	#everOpen = false;
	#retryTimer: ReturnType<typeof setTimeout> | undefined;
	#backoff = 1000;
	#onVisible = () => {
		if (document.visibilityState === 'visible' && this.#count > 0 && !this.#source) this.#retryNow();
	};

	/**
	 * Runs `handler` each time the stream comes back after a drop. Events sent
	 * while it was down are lost (the server keeps no history), so screens refetch
	 * what they show. Doesn't open the stream by itself.
	 */
	onReconnect(handler: () => void): () => void {
		this.#reconnectHandlers.add(handler);
		return () => this.#reconnectHandlers.delete(handler);
	}

	/**
	 * Listen for one or more event types. Returns the unsubscribe function, so it
	 * can be returned straight from `$effect`.
	 */
	on<T extends LiveEventType>(types: T | readonly T[], handler: (event: TypedLiveEvent<T>) => void): () => void {
		const list = (Array.isArray(types) ? types : [types]) as readonly T[];
		const h = handler as unknown as Handler;
		for (const type of list) {
			let set = this.#handlers.get(type);
			if (!set) this.#handlers.set(type, (set = new Set()));
			set.add(h);
		}
		this.#count++;
		this.#open();
		return () => {
			for (const type of list) this.#handlers.get(type)?.delete(h);
			if (--this.#count === 0) this.#close();
		};
	}

	#open() {
		if (this.#source || typeof EventSource === 'undefined') return;
		clearTimeout(this.#retryTimer);
		if (this.#count === 1) document.addEventListener('visibilitychange', this.#onVisible);
		const source = new EventSource(`${API_BASE}/live`, { withCredentials: true });
		source.onopen = () => {
			this.connected = true;
			this.#backoff = 1000;
			if (this.#everOpen) this.#reconnected();
			this.#everOpen = true;
		};
		source.onerror = () => {
			this.connected = false;
			// A network drop leaves it CONNECTING and the browser retries by itself. A
			// non-200 answer (a 502 during a deploy, a 401 after the session expired)
			// closes it for good, so reopen it ourselves, backing off.
			if (source.readyState === EventSource.CLOSED && this.#source === source) {
				this.#source = null;
				this.#retryTimer = setTimeout(() => this.#open(), this.#backoff);
				this.#backoff = Math.min(this.#backoff * 2, MAX_BACKOFF_MS);
			}
		};
		source.onmessage = (e) => this.#dispatch(e.data);
		this.#source = source;
	}

	#retryNow() {
		clearTimeout(this.#retryTimer);
		this.#backoff = 1000;
		this.#open();
	}

	#close() {
		clearTimeout(this.#retryTimer);
		document.removeEventListener('visibilitychange', this.#onVisible);
		this.#source?.close();
		this.#source = null;
		this.connected = false;
		// The next subscriber starts fresh with its own load, so it isn't a reconnect.
		this.#everOpen = false;
	}

	#reconnected() {
		for (const handler of [...this.#reconnectHandlers]) {
			try {
				handler();
			} catch (err) {
				console.error('[live] reconnect handler failed', err);
			}
		}
	}

	#dispatch(raw: string) {
		let event: LiveEvent;
		try {
			event = JSON.parse(raw);
		} catch {
			return;
		}
		const set = this.#handlers.get(event.type);
		if (!set) return;
		for (const handler of [...set]) {
			try {
				handler(event);
			} catch (err) {
				console.error('[live] handler failed', err);
			}
		}
	}
}

export const live = new LiveConnection();

/** A `message.receipts` payload as `{ id, receipt }` pairs for updating your own messages. */
export function receiptUpdates(payload: ReceiptsPayload): { id: string; receipt: NonNullable<Message['receipt']> }[] {
	return (payload?.receipts ?? []).map((r) => ({
		id: r.messageId,
		receipt: { delivered: r.delivered, seen: r.seen, recipients: r.recipients, seenBy: r.seenBy ?? [] }
	}));
}
