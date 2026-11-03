// Production migration runner. Applies ./drizzle/*.sql using drizzle-orm's
// migrator (a runtime dependency) so the prod image does NOT need drizzle-kit.
// Env vars are injected by the host (Coolify) — no dotenv here on purpose.
import mysql from 'mysql2/promise';
import { drizzle } from 'drizzle-orm/mysql2';
import { migrate } from 'drizzle-orm/mysql2/migrator';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL is not set');

// Every instance runs this on start. The migrator takes no lock and MySQL commits
// DDL implicitly, so instances starting together could apply the same migration
// twice. A named lock (held by this connection) makes them take turns; the ones
// that wait then find nothing left to apply.
const LOCK = 'availabliss_migrate';
const LOCK_WAIT_SECONDS = 300;

const connection = await mysql.createConnection({ uri: url, multipleStatements: true });
try {
	const [[{ got }]] = await connection.query('SELECT GET_LOCK(?, ?) AS got', [LOCK, LOCK_WAIT_SECONDS]);
	if (got !== 1) throw new Error(`Timed out after ${LOCK_WAIT_SECONDS}s waiting for the migration lock`);
	try {
		await migrate(drizzle(connection), { migrationsFolder: './drizzle' });
		console.log('✓ migrations applied');
	} finally {
		await connection.query('SELECT RELEASE_LOCK(?)', [LOCK]);
	}
} finally {
	await connection.end();
}
