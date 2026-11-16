/**
 * Makes APPLE_CLIENT_SECRET: the client-secret JWT Sign in with Apple needs, signed with your
 * Sign in with Apple key. Apple caps it at 6 months, so run this again before it expires.
 *   bun scripts/apple-secret.ts <AuthKey_XXXXXXXXXX.p8> <Services ID> [Team ID] [Key ID]
 * The Team ID defaults to APPLE_TEAM_ID; the Key ID to the one in Apple's file name.
 * Prints the secret (paste it into the server's APPLE_CLIENT_SECRET) and when it expires.
 */
import { basename } from 'node:path';
import { importPKCS8, SignJWT } from 'jose';

/** Apple's limit is 15777000 seconds (about 6 months); stay a little under it. */
const LIFETIME_DAYS = 180;

const [keyPath, clientId, teamId = process.env.APPLE_TEAM_ID, keyId = basename(keyPath ?? '').match(/^AuthKey_(\w+)\.p8$/)?.[1]] =
	process.argv.slice(2);
if (!keyPath || !clientId || !teamId || !keyId) {
	console.error('Usage: bun scripts/apple-secret.ts <AuthKey_XXXXXXXXXX.p8> <Services ID> [Team ID] [Key ID]');
	if (keyPath && clientId && !teamId) console.error('No Team ID: pass it, or set APPLE_TEAM_ID.');
	if (keyPath && clientId && teamId && !keyId) console.error("No Key ID: pass it (the key's file name doesn't include one).");
	process.exit(1);
}

const file = Bun.file(keyPath);
if (!(await file.exists())) {
	console.error(`No key file at ${keyPath}`);
	process.exit(1);
}
const key = await importPKCS8(await file.text(), 'ES256');
const now = Math.floor(Date.now() / 1000);
const expires = now + LIFETIME_DAYS * 86_400;
const secret = await new SignJWT({})
	.setProtectedHeader({ alg: 'ES256', kid: keyId })
	.setIssuer(teamId)
	.setIssuedAt(now)
	.setExpirationTime(expires)
	.setAudience('https://appleid.apple.com')
	.setSubject(clientId)
	.sign(key);

console.log(secret);
console.error(`\nAPPLE_CLIENT_SECRET for ${clientId} (team ${teamId}, key ${keyId}), valid until ${new Date(expires * 1000).toDateString()}.`);
