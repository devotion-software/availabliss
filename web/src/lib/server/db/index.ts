import 'dotenv/config';
import { drizzle } from 'drizzle-orm/mysql2';
import mysql from 'mysql2/promise';
import * as schema from './schema';

const connectionString = process.env.DATABASE_URL;
if (!connectionString) {
	throw new Error('DATABASE_URL is not set. Copy .env.example to .env and run `make db-up`.');
}

// Reuse a single pool across HMR reloads in dev.
const globalForDb = globalThis as unknown as { __availablissPool?: mysql.Pool };

const pool =
	globalForDb.__availablissPool ??
	mysql.createPool({
		uri: connectionString,
		connectionLimit: 10,
		charset: 'utf8mb4',
		// Store and read every timestamp as UTC.
		timezone: 'Z',
		dateStrings: false
	});

if (process.env.NODE_ENV !== 'production') globalForDb.__availablissPool = pool;

export const db = drizzle(pool, { schema, mode: 'default' });
export type DB = typeof db;
export type Tx = Parameters<Parameters<DB['transaction']>[0]>[0];

/** For the health check: the database answers SELECT 1 within the timeout. */
export async function dbHealth(timeoutMs = 2000): Promise<'ok' | 'down'> {
	let timer: ReturnType<typeof setTimeout> | undefined;
	const timeout = new Promise<'down'>((resolve) => (timer = setTimeout(() => resolve('down'), timeoutMs)));
	try {
		return await Promise.race([
			pool.query('SELECT 1').then(() => 'ok' as const, () => 'down' as const),
			timeout
		]);
	} finally {
		clearTimeout(timer);
	}
}
