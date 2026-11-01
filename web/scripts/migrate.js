// Production migration runner. Applies ./drizzle/*.sql using drizzle-orm's
// migrator (a runtime dependency) so the prod image does NOT need drizzle-kit.
// Env vars are injected by the host (Coolify) — no dotenv here on purpose.
import mysql from 'mysql2/promise';
import { drizzle } from 'drizzle-orm/mysql2';
import { migrate } from 'drizzle-orm/mysql2/migrator';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL is not set');

const connection = await mysql.createConnection({ uri: url, multipleStatements: true });
try {
	await migrate(drizzle(connection), { migrationsFolder: './drizzle' });
	console.log('✓ migrations applied');
} finally {
	await connection.end();
}
