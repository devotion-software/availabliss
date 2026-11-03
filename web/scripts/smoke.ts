/**
 * End-to-end smoke test against a running dev server (`make dev`) with Mailpit
 * (`make db-up`). Signs two users in with real email codes, then exercises
 * groups, invites, busy time, availability, proposals, chat, receipts and live
 * updates exactly as the native apps do (bearer tokens).
 *
 *   bun scripts/smoke.ts            # BASE_URL defaults to http://localhost:5190
 */
import { addDays, BASE, call, otpFor, signIn as signInWithCode, todayUtc } from './lib/api';
import { challengeFor, encodeFlow, FLOW_COOKIE } from '#lib/server/native-auth';

const run = Date.now().toString(36);
const today = todayUtc();

let failures = 0;
function check(label: string, ok: boolean, detail?: unknown) {
	console.log(`${ok ? '✓' : '✗'} ${label}${ok || detail === undefined ? '' : `  → ${JSON.stringify(detail).slice(0, 300)}`}`);
	if (!ok) failures++;
}

async function signIn(email: string, name: string) {
	try {
		const user = await signInWithCode(email, name);
		check(`sign in ${email}`, true);
		return user;
	} catch (err) {
		check(`sign in ${email}`, false, String(err));
		process.exit(1);
	}
}

const sam = await signIn(`sam+${run}@example.com`, 'Sam');
const priya = await signIn(`priya+${run}@example.com`, 'Priya');

const me = await call(sam.token, 'GET', '/api/v1/me');
check('GET /me returns profile', me.data.name === 'Sam' && me.data.timezone === 'Europe/London', me.data);
check('unauthenticated request is 401 JSON', (await call(null, 'GET', '/api/v1/me')).data.code === 'unauthorized');
check('validation errors are 400 with issues', (await call(sam.token, 'POST', '/api/v1/groups', {})).data.issues?.length > 0);

// Group + invite
const plan0 = await call(sam.token, 'GET', '/api/v1/me/plan');
check('new accounts are on the free plan', plan0.data.tier === 'free' && plan0.data.groupLimit === 3 && plan0.data.canCreateGroup, plan0.data);
check('webhook rejects requests without the shared secret', (await call(null, 'POST', '/api/v1/billing/revenuecat', { event: { type: 'RENEWAL' } })).status === 401);
const group = await call(sam.token, 'POST', '/api/v1/groups', { name: 'Lake District crew' });
check('create group (default weekend requirement)', group.status === 201 && group.data.requirement.length.type === 'span', group.data);
const groupId = group.data.id;
const preview = await call(sam.token, 'POST', '/api/v1/requirements/preview', { requirement: group.data.requirement });
check('requirement preview counts weekends', preview.data.count >= 12 && preview.data.summary.startsWith('A weekend'), preview.data);
const invite = await call(sam.token, 'POST', `/api/v1/groups/${groupId}/invites`, {});
check('create invite', invite.status === 201 && invite.data.url.includes('/invite/'), invite.data);
check('preview invite', (await call(null, 'GET', `/api/v1/invites/${invite.data.token}`)).data.groupName === 'Lake District crew');
const joined = await call(priya.token, 'POST', `/api/v1/invites/${invite.data.token}/accept`);
check('accept invite', joined.status === 200 && joined.data.memberCount === 2, joined.data);
check('member cannot edit requirement', (await call(priya.token, 'PATCH', `/api/v1/groups/${groupId}`, { name: 'x' })).status === 403);

// Free accounts can own three groups; joining others' groups doesn't count.
const extra = [];
for (const name of ['Spare one', 'Spare two']) extra.push((await call(sam.token, 'POST', '/api/v1/groups', { name })).data.id);
const fourth = await call(sam.token, 'POST', '/api/v1/groups', { name: 'One too many' });
check('a fourth group needs Pro (402 upgrade_required)', fourth.status === 402 && fourth.data.code === 'upgrade_required', fourth.data);
check('plan reports the allowance used', (await call(sam.token, 'GET', '/api/v1/me/plan')).data.canCreateGroup === false);
for (const id of extra) await call(sam.token, 'DELETE', `/api/v1/groups/${id}`);

// Live stream for Priya
const events: any[] = [];
const live = await fetch(`${BASE}/api/v1/live`, { headers: { authorization: `Bearer ${priya.token}` } });
const reader = live.body!.getReader();
(async () => {
	const decoder = new TextDecoder();
	let buffer = '';
	for (;;) {
		const { value, done } = await reader.read();
		if (done) break;
		buffer += decoder.decode(value, { stream: true });
		const chunks = buffer.split('\n\n');
		buffer = chunks.pop()!;
		for (const chunk of chunks) {
			if (!chunk.startsWith('data: ')) continue;
			try {
				events.push(JSON.parse(chunk.slice(6)));
			} catch {
				console.log('  (unparseable live event)', chunk.slice(0, 200));
			}
		}
	}
})();

// Busy time: Priya blocks the first upcoming Saturday by hand; Sam blocks a repeating evening
const avail0 = await call(sam.token, 'GET', `/api/v1/groups/${groupId}/availability?from=${today}&to=${addDays(today, 60)}`);
check('availability before anyone syncs has no synced members', avail0.data.syncedCount === 0 && avail0.data.pendingMemberIds.length === 2, avail0.data);
check('only Fri–Sun columns are visible', JSON.stringify(avail0.data.visibleWeekdays) === '[4,5,6]', avail0.data.visibleWeekdays);

const saturday = avail0.data.days.find((d: any) => new Date(`${d.date}T12:00:00Z`).getUTCDay() === 6 && d.date > addDays(today, 1)).date;
const block = await call(priya.token, 'POST', '/api/v1/blocks', {
	start: `${saturday}T00:00:00Z`,
	end: `${addDays(saturday, 1)}T00:00:00Z`,
	allDay: true,
	note: 'Wedding'
});
check('create manual block', block.status === 201, block.data);
// Choir on Tuesdays: outside every weekend, so it must not block any weekend slot.
const tuesday = [0, 1, 2, 3, 4, 5, 6].map((n) => addDays(today, n)).find((d) => new Date(`${d}T12:00:00Z`).getUTCDay() === 2)!;
const weekly = await call(sam.token, 'POST', '/api/v1/blocks', {
	start: `${tuesday}T18:00:00Z`,
	end: `${tuesday}T20:00:00Z`,
	repeat: 'weekly'
});
check('create repeating block', weekly.status === 201 && weekly.data.repeat === 'weekly', weekly.data);
const myBusy = await call(sam.token, 'GET', `/api/v1/me/busy?from=${today}T00:00:00Z&to=${addDays(today, 21)}T00:00:00Z`);
check('repeating block expands to weekly occurrences', myBusy.data.intervals.length === 3, myBusy.data);

const avail = await call(sam.token, 'GET', `/api/v1/groups/${groupId}/availability?from=${today}&to=${addDays(today, 60)}`);
const satDay = avail.data.days.find((d: any) => d.date === saturday);
check('availability marks the blocked Saturday busy', satDay.status === 'busy' && satDay.busyMemberIds.includes(priya.id), satDay);
check('availability finds working slots', avail.data.slots.length > 0, avail.data.slots);
const day = await call(sam.token, 'GET', `/api/v1/groups/${groupId}/availability/day?date=${saturday}`);
check('day view shows Priya busy (manual)', day.data.members.find((m: any) => m.userId === priya.id).busy[0]?.manual === true, day.data);
check('day view hides block labels from others', !JSON.stringify(day.data).includes('Wedding'));

// Proposal → accept → confirmed
const slot = avail.data.slots[0];
const proposal = await call(sam.token, 'POST', `/api/v1/groups/${groupId}/proposals`, { start: slot.start, end: slot.end, location: 'Keswick' });
check('create proposal (creator auto-accepts)', proposal.status === 201 && proposal.data.status === 'open', proposal.data);
check('a time that works has no clashes', JSON.stringify(proposal.data.busyMemberIds) === '[]', proposal.data.busyMemberIds);

// Proposing a time someone is busy for: allowed, with the clash reported.
const busySat = { start: `${saturday}T18:00:00Z`, end: `${saturday}T21:00:00Z` };
const conflicts = await call(sam.token, 'GET', `/api/v1/groups/${groupId}/conflicts?start=${encodeURIComponent(busySat.start)}&end=${encodeURIComponent(busySat.end)}`);
check('conflicts names who is busy', JSON.stringify(conflicts.data.busyMemberIds) === JSON.stringify([priya.id]), conflicts.data);
const clashing = await call(sam.token, 'POST', `/api/v1/groups/${groupId}/proposals`, { ...busySat, title: 'Despite the wedding' });
check('can propose a clashing time; proposal lists who is busy', clashing.status === 201 && clashing.data.busyMemberIds?.includes(priya.id), clashing.data);
await call(sam.token, 'DELETE', `/api/v1/proposals/${clashing.data.id}`);
const summaries = await call(priya.token, 'GET', '/api/v1/groups');
check('group list says Priya needs to reply', summaries.data.groups[0].status.kind === 'needs_reply', summaries.data.groups[0]);
const accepted = await call(priya.token, 'POST', `/api/v1/proposals/${proposal.data.id}/respond`, { response: 'accepted' });
check('everyone accepted → confirmed with event', accepted.data.status === 'confirmed' && accepted.data.eventId, accepted.data);
const ics = await call(sam.token, 'GET', `/api/v1/events/${accepted.data.eventId}/ics`);
check('ICS download', typeof ics.data === 'string' && ics.data.includes('BEGIN:VEVENT') && ics.data.includes('Keswick'));
check('single event GET', (await call(priya.token, 'GET', `/api/v1/events/${accepted.data.eventId}`)).data.location === 'Keswick');
check('upcoming events include it', (await call(priya.token, 'GET', '/api/v1/me/upcoming')).data.events.length === 1);

// Chat, receipts, polls
const msg = await call(sam.token, 'POST', `/api/v1/groups/${groupId}/messages`, { body: "I'll book somewhere to stay" });
check('send message', msg.status === 201 && msg.data.receipt?.recipients === 1, msg.data);
const poll = await call(sam.token, 'POST', `/api/v1/groups/${groupId}/messages`, {
	poll: { question: 'Where should we stay?', options: ['Keswick cottage', 'Ambleside YHA'] }
});
check('create poll', poll.data.poll?.options.length === 2, poll.data);
const voted = await call(priya.token, 'POST', `/api/v1/polls/${poll.data.poll.id}/vote`, { optionIds: [poll.data.poll.options[0].id] });
check('vote', voted.data.options[0].voterIds.includes(priya.id), voted.data);
const history = await call(priya.token, 'GET', `/api/v1/groups/${groupId}/messages`);
check('message history includes cards and chat', history.data.messages.some((m: any) => m.kind === 'proposal') && history.data.messages.length >= 4, history.data);
await call(priya.token, 'POST', `/api/v1/groups/${groupId}/messages/receipts`, { upTo: msg.data.id, state: 'delivered' });
let info = await call(sam.token, 'GET', `/api/v1/messages/${msg.data.id}/info`);
check('message info: delivered, not seen', info.data.delivered.length === 1 && info.data.seen.length === 0, info.data);
await call(priya.token, 'POST', `/api/v1/groups/${groupId}/messages/receipts`, { upTo: poll.data.id, state: 'seen' });
info = await call(sam.token, 'GET', `/api/v1/messages/${msg.data.id}/info`);
check('message info: seen', info.data.seen[0]?.userId === priya.id && info.data.pending.length === 0, info.data);
check('only the sender can see message info', (await call(priya.token, 'GET', `/api/v1/messages/${msg.data.id}/info`)).status === 403);

// Notifications + live
const notes = await call(priya.token, 'GET', '/api/v1/notifications');
check('notifications recorded', notes.data.notifications.some((n: any) => n.kind === 'proposal.created') && notes.data.unread > 0, notes.data);
await Bun.sleep(300);
const types = new Set(events.map((e) => e.type));
check('live stream delivered events', ['message.created', 'proposal.confirmed', 'poll.updated'].every((t) => types.has(t)), [...types]);
await reader.cancel();

// Calendars (no providers configured locally): device upload
const device = await call(sam.token, 'PUT', '/api/v1/calendars/device', {
	deviceId: `dev-${run}`,
	deviceName: "Sam's iPhone",
	rangeStart: `${today}T00:00:00Z`,
	rangeEnd: `${addDays(today, 30)}T00:00:00Z`,
	calendars: [
		{ externalId: 'work', name: 'Work', color: '#5B8DEF' },
		{ externalId: 'bdays', name: 'Birthdays', color: null }
	],
	busy: [{ calendarExternalId: 'work', start: `${addDays(today, 2)}T09:00:00Z`, end: `${addDays(today, 2)}T17:00:00Z` }]
});
check('device calendar upload', device.data.calendars?.find((c: any) => c.name === 'Birthdays')?.busy === false, device.data);
const calId = device.data.calendars.find((c: any) => c.name === 'Work').id;
const toggled = await call(sam.token, 'PATCH', `/api/v1/calendars/${device.data.id}/calendars/${calId}`, { busy: false });
check('toggle calendar busy', toggled.data.calendars.find((c: any) => c.id === calId).busy === false, toggled.data);
check('well-known AASA served', (await call(null, 'GET', '/.well-known/apple-app-site-association')).data.webcredentials !== undefined);

// Native web-flow sign-in hand-off: a browser session becomes a one-time code, then a bearer token.
// The real flow starts at /auth/native/:provider/start, which needs a configured provider, so the
// flow cookie it would set is signed here with the same BETTER_AUTH_SECRET.
{
	const email = `web+${run}@example.com`;
	const beforeSignIn = Date.now() - 1000;
	await call(null, 'POST', '/api/auth/email-otp/send-verification-otp', { email, type: 'sign-in' });
	const signedIn = await fetch(`${BASE}/api/auth/sign-in/email-otp`, {
		method: 'POST',
		headers: { 'content-type': 'application/json', origin: BASE },
		body: JSON.stringify({ email, otp: await otpFor(email) })
	});
	const session = signedIn.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ');
	const verifier = 'v'.repeat(20) + crypto.randomUUID().replaceAll('-', '');
	const callbackWith = async (flow: string | null) => {
		const cookie = flow ? `${session}; ${FLOW_COOKIE}=${flow}` : session;
		const res = await fetch(`${BASE}/api/v1/auth/native/callback`, { headers: { cookie }, redirect: 'manual' });
		return new URL(res.headers.get('location') ?? 'x://x').searchParams;
	};

	check('native callback refuses a browser that did not start the flow', (await callbackWith(null)).get('error') === 'sign_in_not_started_here');
	const stale = await callbackWith(encodeFlow(challengeFor(verifier), Date.now() + 60_000));
	check('native callback refuses a session older than the flow', stale.get('error') === 'sign_in_not_started_here');

	const code = (await callbackWith(encodeFlow(challengeFor(verifier), beforeSignIn))).get('code');
	check('native callback redirects to the app with a code', Boolean(code), code);
	const wrong = await call(null, 'POST', '/api/v1/auth/native/exchange', { code, code_verifier: 'w'.repeat(43) });
	check('exchange needs the matching verifier', wrong.status === 400, wrong.data);
	check('a code is spent after a wrong verifier', (await call(null, 'POST', '/api/v1/auth/native/exchange', { code, code_verifier: verifier })).status === 400);

	const code2 = (await callbackWith(encodeFlow(challengeFor(verifier), beforeSignIn))).get('code');
	const exchanged = await call(null, 'POST', '/api/v1/auth/native/exchange', { code: code2, code_verifier: verifier });
	check('code exchanges for a working bearer token', (await call(exchanged.data.token, 'GET', '/api/v1/me')).data.email === email, exchanged.data);
	check('codes are single use', (await call(null, 'POST', '/api/v1/auth/native/exchange', { code: code2, code_verifier: verifier })).status === 400);
	const noChallenge = await call(null, 'GET', '/api/v1/auth/native/apple/start');
	check('start requires a code_challenge', noChallenge.status === 400, noChallenge);
	const unconfigured = await call(null, 'GET', `/api/v1/auth/native/apple/start?code_challenge=${challengeFor(verifier)}`);
	check('unconfigured provider returns a JSON error, not a crash', unconfigured.status !== 500 && Boolean(unconfigured.data?.code), unconfigured);
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
