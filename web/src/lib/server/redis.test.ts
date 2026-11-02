/**
 * The in-memory and Redis stores must behave the same. The Redis half runs when
 * TEST_REDIS_URL is set (CI's smoke job; locally `TEST_REDIS_URL=redis://localhost:6382`).
 */
import { afterAll, describe, expect, test } from 'bun:test';
import { Redis } from 'ioredis';
import { MemoryBus, MemoryStore, RedisBus, RedisStore, type Bus, type KeyValueStore } from './redis';
import { createHub } from './realtime';
import type { LiveEvent } from '#lib/types';

const redisUrl = process.env.TEST_REDIS_URL;
const clients: Redis[] = [];
afterAll(() => clients.forEach((c) => c.disconnect()));

function redisClient() {
	const client = new Redis(redisUrl!);
	clients.push(client);
	return client;
}

const unique = () => `test:${crypto.randomUUID()}`;

function storeContract(name: string, make: () => KeyValueStore) {
	describe(`${name} store`, () => {
		test('set / get / delete', async () => {
			const kv = make();
			const key = unique();
			expect(await kv.get(key)).toBeNull();
			await kv.set(key, 'a', 60);
			expect(await kv.get(key)).toBe('a');
			await kv.delete(key);
			expect(await kv.get(key)).toBeNull();
		});

		test('take reads once', async () => {
			const kv = make();
			const key = unique();
			await kv.set(key, 'code', 60);
			expect(await kv.take(key)).toBe('code');
			expect(await kv.take(key)).toBeNull();
		});

		test('setIfAbsent only wins once', async () => {
			const kv = make();
			const key = unique();
			expect(await kv.setIfAbsent(key, '1', 60)).toBe(true);
			expect(await kv.setIfAbsent(key, '2', 60)).toBe(false);
			expect(await kv.get(key)).toBe('1');
		});

		test('hit counts within a window', async () => {
			const kv = make();
			const key = unique();
			expect((await kv.hit(key, 60)).count).toBe(1);
			expect((await kv.hit(key, 60)).count).toBe(2);
			const third = await kv.hit(key, 60);
			expect(third.count).toBe(3);
			expect(third.resetSeconds).toBeGreaterThan(0);
			expect(third.resetSeconds).toBeLessThanOrEqual(60);
		});
	});
}

storeContract('memory', () => new MemoryStore());
if (redisUrl) storeContract('redis', () => new RedisStore(redisClient()));

describe('memory store expiry', () => {
	test('keys and counters expire', async () => {
		let now = 0;
		const kv = new MemoryStore(() => now);
		await kv.set('k', 'v', 10);
		await kv.hit('c', 10);
		await kv.hit('c', 10);
		now = 9_000;
		expect(await kv.get('k')).toBe('v');
		expect((await kv.hit('c', 10)).resetSeconds).toBe(1);
		now = 10_000;
		expect(await kv.get('k')).toBeNull();
		expect((await kv.hit('c', 10)).count).toBe(1);
		expect(await kv.setIfAbsent('k', 'again', 10)).toBe(true);
	});
});

function hubContract(name: string, makeBuses: () => [Bus, Bus]) {
	describe(`live hub over ${name}`, () => {
		test('delivers to the addressed users on every instance, once each', async () => {
			const [busA, busB] = makeBuses();
			const instanceA = createHub(busA);
			const instanceB = createHub(busB);
			const user = unique();
			const other = unique();
			const seen: string[] = [];
			instanceA.subscribe(user, (e) => seen.push(`A:${e.type}`));
			instanceB.subscribe(user, (e) => seen.push(`B:${e.type}`));
			const unsubscribeOther = instanceB.subscribe(other, (e) => seen.push(`other:${e.type}`));
			unsubscribeOther();
			// Let the Redis SUBSCRIBE commands land before publishing.
			await Bun.sleep(name === 'redis' ? 150 : 0);

			const event: LiveEvent = { type: 'message.created', groupId: 'g', payload: {} };
			await instanceA.publishToUsers([user, user, other], event);
			await Bun.sleep(name === 'redis' ? 150 : 0);

			expect(seen.sort()).toEqual(['A:message.created', 'B:message.created']);
		});
	});
}

hubContract('memory', () => {
	const bus = new MemoryBus();
	return [bus, bus];
});
if (redisUrl) hubContract('redis', () => [new RedisBus(redisClient()), new RedisBus(redisClient())]);
