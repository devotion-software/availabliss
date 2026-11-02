import { json } from '@sveltejs/kit';
import { redisHealth } from '#lib/server/redis';

/** Liveness for Coolify. Redis being down degrades live updates but the app still serves, so it stays 200. */
export const GET = async () => json({ ok: true, version: __APP_VERSION__, redis: await redisHealth() });
