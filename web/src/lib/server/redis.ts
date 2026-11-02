/**
 * Shared state that must survive restarts and be visible to every instance:
 * short-lived keys (sign-in hand-off codes, rate-limit counters, locks) and the
 * pub/sub bus behind live updates.
 *
 * With REDIS_URL set everything goes through Redis. Without it (unit tests, a
 * quick local run) an in-process implementation is used, which only works for a
 * single instance, so production should always set REDIS_URL.
 *
 * Redis being down must never hang requests (every request records a rate-limit
 * hit): commands fail fast instead of queueing, and while Redis is unreachable
 * the in-process implementation stands in until it reconnects.
 */
import { Redis } from 'ioredis';
import { env } from './env';

export interface KeyValueStore {
	get(key: string): Promise<string | null>;
	/** Sets `key`, expiring after `ttlSeconds` when given. */
	set(key: string, value: string, ttlSeconds?: number): Promise<void>;
	/** Sets `key` only if it doesn't exist. Returns whether it was set. */
	setIfAbsent(key: string, value: string, ttlSeconds: number): Promise<boolean>;
	/** Reads and deletes `key` atomically. */
	take(key: string): Promise<string | null>;
	delete(key: string): Promise<void>;
	/** Increments a counter, starting a `windowSeconds` expiry when it is created. Returns the new count and its remaining lifetime. */
	hit(key: string, windowSeconds: number): Promise<{ count: number; resetSeconds: number }>;
}

export interface Bus {
	publish(channel: string, message: string): Promise<void>;
	/** Calls `handler` for every message on `channel`, from any instance (including this one). */
	subscribe(channel: string, handler: (message: string) => void): Promise<void>;
}

// ── In-process fallback ──────────────────────────────────────────────────────

export class MemoryStore implements KeyValueStore {
	private entries = new Map<string, { value: string; expires: number }>();

	constructor(private now: () => number = Date.now) {}

	private live(key: string) {
		const entry = this.entries.get(key);
		if (entry && entry.expires <= this.now()) {
			this.entries.delete(key);
			return undefined;
		}
		return entry;
	}

	private sweep() {
		// Keeps the map from growing without bound; cheap enough at this scale.
		if (this.entries.size < 1000) return;
		for (const key of [...this.entries.keys()]) this.live(key);
	}

	async get(key: string) {
		return this.live(key)?.value ?? null;
	}

	async set(key: string, value: string, ttlSeconds?: number) {
		this.sweep();
		this.entries.set(key, { value, expires: ttlSeconds ? this.now() + ttlSeconds * 1000 : Infinity });
	}

	async setIfAbsent(key: string, value: string, ttlSeconds: number) {
		if (this.live(key)) return false;
		await this.set(key, value, ttlSeconds);
		return true;
	}

	async take(key: string) {
		const value = this.live(key)?.value ?? null;
		this.entries.delete(key);
		return value;
	}

	async delete(key: string) {
		this.entries.delete(key);
	}

	async hit(key: string, windowSeconds: number) {
		const entry = this.live(key);
		if (!entry) {
			await this.set(key, '1', windowSeconds);
			return { count: 1, resetSeconds: windowSeconds };
		}
		entry.value = String(Number(entry.value) + 1);
		return { count: Number(entry.value), resetSeconds: Math.max(1, Math.ceil((entry.expires - this.now()) / 1000)) };
	}
}

export class MemoryBus implements Bus {
	private handlers = new Map<string, Set<(message: string) => void>>();

	async publish(channel: string, message: string) {
		for (const handler of this.handlers.get(channel) ?? []) handler(message);
	}

	async subscribe(channel: string, handler: (message: string) => void) {
		let set = this.handlers.get(channel);
		if (!set) this.handlers.set(channel, (set = new Set()));
		set.add(handler);
	}
}

// ── Redis ────────────────────────────────────────────────────────────────────

const PREFIX = 'av:';

/** Atomically INCR and set the expiry on first hit; returns [count, ttl]. */
const HIT_SCRIPT = `
local count = redis.call('INCR', KEYS[1])
if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
return { count, redis.call('TTL', KEYS[1]) }`;

export class RedisStore implements KeyValueStore {
	constructor(private client: Redis) {}

	get(key: string) {
		return this.client.get(PREFIX + key);
	}

	async set(key: string, value: string, ttlSeconds?: number) {
		if (ttlSeconds) await this.client.set(PREFIX + key, value, 'EX', Math.max(1, Math.ceil(ttlSeconds)));
		else await this.client.set(PREFIX + key, value);
	}

	async setIfAbsent(key: string, value: string, ttlSeconds: number) {
		return (await this.client.set(PREFIX + key, value, 'EX', Math.max(1, Math.ceil(ttlSeconds)), 'NX')) === 'OK';
	}

	take(key: string) {
		return this.client.getdel(PREFIX + key);
	}

	async delete(key: string) {
		await this.client.del(PREFIX + key);
	}

	async hit(key: string, windowSeconds: number) {
		const [count, ttl] = (await this.client.eval(HIT_SCRIPT, 1, PREFIX + key, Math.max(1, Math.ceil(windowSeconds)))) as [number, number];
		return { count, resetSeconds: ttl > 0 ? ttl : windowSeconds };
	}
}

export class RedisBus implements Bus {
	private subscriber: Redis;
	private handlers = new Map<string, Set<(message: string) => void>>();

	constructor(private client: Redis) {
		// A connection in subscriber mode can't run other commands, so it gets its own.
		// It queues SUBSCRIBE while offline, so subscriptions made during an outage
		// take effect once Redis is back (ioredis re-subscribes after reconnects).
		this.subscriber = client.duplicate({ enableOfflineQueue: true, maxRetriesPerRequest: null });
		this.subscriber.on('error', () => {});
		this.subscriber.on('message', (channel: string, message: string) => {
			for (const handler of this.handlers.get(channel) ?? []) {
				try {
					handler(message);
				} catch (err) {
					console.warn('[redis] subscriber handler failed', err);
				}
			}
		});
	}

	async publish(channel: string, message: string) {
		await this.client.publish(PREFIX + channel, message);
	}

	async subscribe(channel: string, handler: (message: string) => void) {
		const key = PREFIX + channel;
		let set = this.handlers.get(key);
		if (!set) {
			this.handlers.set(key, (set = new Set()));
			await this.subscriber.subscribe(key);
		}
		set.add(handler);
	}

	/** Both connections are up, so a publish reaches every instance, this one included. */
	get ready() {
		return this.client.status === 'ready' && this.subscriber.status === 'ready';
	}
}

// ── Fallback while Redis is down ─────────────────────────────────────────────

/** Logs once when Redis drops out and once when it's back, not on every call. */
function outageLog() {
	let down = false;
	return {
		failed(err?: unknown) {
			if (down) return;
			down = true;
			console.warn('[redis] unavailable, using in-memory state until it reconnects', err instanceof Error ? err.message : '');
		},
		ok() {
			if (!down) return;
			down = false;
			console.warn('[redis] reconnected');
		}
	};
}

/**
 * Uses Redis when it's connected and the in-memory store otherwise (or when a
 * command fails). State written during an outage stays local to this instance.
 */
export class FallbackStore implements KeyValueStore {
	private log = outageLog();

	constructor(
		private primary: KeyValueStore,
		private fallback: KeyValueStore,
		private isReady: () => boolean
	) {}

	private async run<T>(op: (store: KeyValueStore) => Promise<T>): Promise<T> {
		if (!this.isReady()) {
			this.log.failed();
			return op(this.fallback);
		}
		try {
			const result = await op(this.primary);
			this.log.ok();
			return result;
		} catch (err) {
			this.log.failed(err);
			return op(this.fallback);
		}
	}

	get(key: string) {
		return this.run((s) => s.get(key));
	}
	set(key: string, value: string, ttlSeconds?: number) {
		return this.run((s) => s.set(key, value, ttlSeconds));
	}
	setIfAbsent(key: string, value: string, ttlSeconds: number) {
		return this.run((s) => s.setIfAbsent(key, value, ttlSeconds));
	}
	take(key: string) {
		return this.run((s) => s.take(key));
	}
	delete(key: string) {
		return this.run((s) => s.delete(key));
	}
	hit(key: string, windowSeconds: number) {
		return this.run((s) => s.hit(key, windowSeconds));
	}
}

/**
 * Publishes through Redis when it's up; otherwise delivers on this instance only.
 * Handlers are registered on both, and only one of them carries each message.
 */
export class FallbackBus implements Bus {
	private log = outageLog();

	constructor(
		private primary: Bus,
		private fallback: Bus,
		private isReady: () => boolean
	) {}

	async publish(channel: string, message: string) {
		if (this.isReady()) {
			try {
				await this.primary.publish(channel, message);
				this.log.ok();
				return;
			} catch (err) {
				this.log.failed(err);
			}
		} else this.log.failed();
		await this.fallback.publish(channel, message);
	}

	async subscribe(channel: string, handler: (message: string) => void) {
		await this.fallback.subscribe(channel, handler);
		// Don't wait on Redis: the subscription completes whenever it connects.
		void this.primary.subscribe(channel, handler).catch((err) => this.log.failed(err));
	}
}

// ── Process-wide instances ───────────────────────────────────────────────────

interface Shared {
	client: Redis | null;
	store: KeyValueStore;
	bus: Bus;
}

function connect(): Shared {
	if (!env.redisUrl) {
		if (process.env.NODE_ENV === 'production') {
			console.warn('[redis] REDIS_URL is not set: using in-memory state, which only works with a single instance');
		}
		return { client: null, store: new MemoryStore(), bus: new MemoryBus() };
	}
	const client = new Redis(env.redisUrl, {
		// Fail fast rather than queue commands while disconnected; FallbackStore takes over.
		enableOfflineQueue: false,
		maxRetriesPerRequest: 1,
		connectTimeout: 5_000,
		commandTimeout: 1_000,
		// Keep trying to reconnect in the background, at most every 5 s.
		retryStrategy: (times) => Math.min(times * 200, 5_000)
	});
	// Connection errors repeat on every retry; FallbackStore/FallbackBus log the outage once.
	client.on('error', () => {});
	const redisBus = new RedisBus(client);
	return {
		client,
		store: new FallbackStore(new RedisStore(client), new MemoryStore(), () => client.status === 'ready'),
		bus: new FallbackBus(redisBus, new MemoryBus(), () => redisBus.ready)
	};
}

// Reuse connections across HMR reloads in dev.
const globalForRedis = globalThis as unknown as { __availablissRedis?: Shared };
const shared = (globalForRedis.__availablissRedis ??= connect());

export const kv: KeyValueStore = shared.store;
export const bus: Bus = shared.bus;

/** 'ok' / 'down' when Redis is configured, 'off' when running in-memory. */
export async function redisHealth(): Promise<'ok' | 'down' | 'off'> {
	if (!shared.client) return 'off';
	if (shared.client.status !== 'ready') return 'down';
	try {
		return (await shared.client.ping()) === 'PONG' ? 'ok' : 'down';
	} catch {
		return 'down';
	}
}
