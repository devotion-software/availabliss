import { json } from '@sveltejs/kit';
import { dbHealth } from '#lib/server/db';
import { redisHealth } from '#lib/server/redis';

/**
 * Health for Coolify. Without the database nothing works, so that's a 503.
 * Redis being down degrades live updates but the app still serves, so it stays 200.
 */
export const GET = async () => {
	const [db, redis] = await Promise.all([dbHealth(), redisHealth()]);
	return json({ ok: db === 'ok', version: __APP_VERSION__, db, redis }, { status: db === 'ok' ? 200 : 503 });
};
