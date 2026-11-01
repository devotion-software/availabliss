/**
 * End-to-end smoke test against a running dev server (`make dev`) with Mailpit
 * (`make db-up`). Signs two users in with real email codes, then exercises
 * groups, invites, busy time, availability, proposals, chat, receipts and live
 * updates exactly as the native apps do (bearer tokens).
 *
 *   bun scripts/smoke.ts            # BASE_URL defaults to http://localhost:5190
 */
const BASE = process.env.BASE_URL ?? 'http://localhost:5190';
const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8027';
const run = Date.now().toString(36);

let failures = 0;
function check(label: string, ok: boolean, detail?: unknown) {
	console.log(`${ok ? '✓' : '✗'} ${label}${ok || detail === undefined ? '' : `  → ${JSON.stringify(detail).slice(0, 300)}`}`);
	if (!ok) failures++;
}

async function call<T = any>(token: string | null, method: string, path: string, body?: unknown): Promise<{ status: number; data: T; headers: Headers }> {
	const res = await fetch(`${BASE}${path}`, {
		method,
		headers: {
			...(token && { authorization: `Bearer ${token}` }),
			...(body !== undefined && { 'content-type': 'application/json' }),
			origin: BASE
		},
		body: body === undefined ? undefined : JSON.stringify(body)
	});
	const text = await res.text();
	let data: any = text;
	try {
		data = text ? JSON.parse(text) : null;
	} catch {
		/* not JSON */
	}
	return { status: res.status, data, headers: res.headers };
}

async function otpFor(email: string): Promise<string> {
	for (let i = 0; i < 20; i++) {
		const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
		const { messages } = (await res.json()) as { messages: { Subject: string }[] };
		const code = messages[0]?.Subject.match(/^(\d{6})/)?.[1];
		if (code) return code;
		await Bun.sleep(250);
	}
	throw new Error(`No code emailed to ${email}`);
}

async function signIn(email: string, name: string): Promise<{ token: string; id: string }> {
	await call(null, 'POST', '/api/auth/email-otp/send-verification-otp', { email, type: 'sign-in' });
	const res = await call(null, 'POST', '/api/auth/sign-in/email-otp', { email, otp: await otpFor(email) });
	const token = res.headers.get('set-auth-token');
	check(`sign in ${email}`, res.status === 200 && Boolean(token), res.data);
	await call(token!, 'PATCH', '/api/v1/me', { name, timezone: 'Europe/London', country: 'GB', subdivision: 'GB-ENG', onboarded: true });
	return { token: token!, id: res.data.user.id };
}

const addDays = (date: string, n: number) => new Date(Date.parse(`${date}T00:00:00Z`) + n * 86_400_000).toISOString().slice(0, 10);
const today = new Date().toISOString().slice(0, 10);

const sam = await signIn(`sam+${run}@example.com`, 'Sam');
const priya = await signIn(`priya+${run}@example.com`, 'Priya');

const me = await call(sam.token, 'GET', '/api/v1/me');
check('GET /me returns profile', me.data.name === 'Sam' && me.data.timezone === 'Europe/London', me.data);
check('unauthenticated request is 401 JSON', (await call(null, 'GET', '/api/v1/me')).data.code === 'unauthorized');
check('validation errors are 400 with issues', (await call(sam.token, 'POST', '/api/v1/groups', {})).data.issues?.length > 0);

// Group + invite
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
{
	const email = `web+${run}@example.com`;
	await call(null, 'POST', '/api/auth/email-otp/send-verification-otp', { email, type: 'sign-in' });
	const signedIn = await fetch(`${BASE}/api/auth/sign-in/email-otp`, {
		method: 'POST',
		headers: { 'content-type': 'application/json', origin: BASE },
		body: JSON.stringify({ email, otp: await otpFor(email) })
	});
	const cookie = signedIn.headers.getSetCookie().map((c) => c.split(';')[0]).join('; ');
	const callback = await fetch(`${BASE}/api/v1/auth/native/callback`, { headers: { cookie }, redirect: 'manual' });
	const code = new URL(callback.headers.get('location') ?? 'x://x').searchParams.get('code');
	check('native callback redirects to the app with a code', callback.status === 302 && Boolean(code), callback.headers.get('location'));
	const exchanged = await call(null, 'POST', '/api/v1/auth/native/exchange', { code });
	check('code exchanges for a working bearer token', (await call(exchanged.data.token, 'GET', '/api/v1/me')).data.email === email, exchanged.data);
	check('codes are single use', (await call(null, 'POST', '/api/v1/auth/native/exchange', { code })).status === 400);
	const unconfigured = await call(null, 'GET', '/api/v1/auth/native/apple/start');
	check('unconfigured provider returns a JSON error, not a crash', unconfigured.status !== 500 && Boolean(unconfigured.data?.code), unconfigured);
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
