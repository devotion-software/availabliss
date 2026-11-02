/**
 * Dev / support helper: gives an account Pro (or takes it away) without a purchase.
 *   bun scripts/set-plan.ts <email> pro [days]   # default 30 days, "lifetime" for no expiry
 *   bun scripts/set-plan.ts <email> free
 */
import { eq } from 'drizzle-orm';
import { db } from '../src/lib/server/db';
import { user } from '../src/lib/server/db/schema';
import { saveEntitlement } from '../src/lib/server/billing';

const [email, tier = 'pro', length = '30'] = process.argv.slice(2);
if (!email || !['pro', 'free'].includes(tier)) {
	console.error('Usage: bun scripts/set-plan.ts <email> pro|free [days|lifetime]');
	process.exit(1);
}
const row = await db.query.user.findFirst({ where: eq(user.email, email.toLowerCase()), columns: { id: true } });
if (!row) {
	console.error(`No account for ${email}`);
	process.exit(1);
}
const lifetime = tier === 'pro' && length === 'lifetime';
await saveEntitlement(row.id, {
	proUntil: tier === 'pro' && !lifetime ? new Date(Date.now() + Number(length) * 86_400_000) : null,
	lifetime,
	store: tier === 'pro' ? 'promotional' : null,
	productId: null,
	willRenew: false
});
console.log(`${email} is now ${tier}${tier === 'pro' ? (lifetime ? ' (lifetime)' : ` for ${length} days`) : ''}`);
process.exit(0);
