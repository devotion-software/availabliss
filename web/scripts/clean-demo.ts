/**
 * Deletes the accounts created by seed-demo.ts (and with them, their groups and data).
 *
 *   bun scripts/clean-demo.ts
 */
import { unlink } from 'node:fs/promises';
import { call, type SignedIn } from './lib/api';

const STATE_FILE = new URL('../.demo.json', import.meta.url);
const file = Bun.file(STATE_FILE);
if (!(await file.exists())) {
	console.log('No demo data to clean up.');
	process.exit(0);
}

const { users } = (await file.json()) as { users: Record<string, SignedIn> };
for (const user of Object.values(users)) {
	const res = await call(user.token, 'DELETE', '/api/v1/me');
	console.log(`${res.status === 204 ? 'deleted' : `failed (${res.status})`}  ${user.email}`);
}
await unlink(STATE_FILE);
