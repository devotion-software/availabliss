/**
 * Seeds a realistic demo dataset through the API (needs `make dev` + `make db-up`):
 * five friends, three groups, busy time that leaves one working weekend, an open
 * proposal, chat with a poll and read receipts, and a confirmed book club evening.
 * Dates are relative to today, so it works whenever it's run.
 *
 *   bun scripts/seed-demo.ts     # prints the main user's email; state in .demo.json
 */
import { addDays, call, nextWeekday, signIn, todayUtc, type SignedIn } from './lib/api';

const STATE_FILE = new URL('../.demo.json', import.meta.url);
const run = Date.now().toString(36);
const at = (date: string, time: string) => `${date}T${time}:00.000Z`;

const people = {
	sam: await signIn(`sam+${run}@example.com`, 'Sam Taylor'),
	priya: await signIn(`priya+${run}@example.com`, 'Priya Shah'),
	tom: await signIn(`tom+${run}@example.com`, 'Tom Becker'),
	ana: await signIn(`ana+${run}@example.com`, 'Ana Lopes'),
	leo: await signIn(`leo+${run}@example.com`, 'Leo Park')
};
const { sam, priya, tom, ana, leo } = people;

async function createGroup(owner: SignedIn, name: string, color: string, members: SignedIn[]) {
	const group = (await call(owner.token, 'POST', '/api/v1/groups', { name, color })).data;
	const invite = (await call(owner.token, 'POST', `/api/v1/groups/${group.id}/invites`, {})).data;
	for (const member of members) await call(member.token, 'POST', `/api/v1/invites/${invite.token}/accept`);
	return group;
}

// Groups: a weekend away (Leo never syncs), an evening book club, a family birthday.
const lakes = await createGroup(sam, 'Lake District crew', 't1', [priya, tom, ana, leo]);
await call(sam.token, 'PATCH', `/api/v1/groups/${lakes.id}/members/${priya.id}`, { role: 'admin' });
const bookClub = await createGroup(sam, 'Book club', 't2', [ana, priya]);
await call(sam.token, 'PATCH', `/api/v1/groups/${bookClub.id}`, {
	requirement: { ...bookClub.requirement, length: { type: 'days', count: 1 }, window: { from: '18:00', to: '23:59' }, days: [1, 2, 3] }
});
await createGroup(priya, "Mum's 60th", 't4', [sam]);

// Busy time: the first three weekends each break for someone; the fourth works.
const friday = nextWeekday(addDays(todayUtc(), 2), 5);
const weekend = (n: number) => {
	const fri = addDays(friday, 7 * n);
	return { fri, sat: addDays(fri, 1), sun: addDays(fri, 2), mon: addDays(fri, 3) };
};
const [w1, w2, w3] = [weekend(0), weekend(1), weekend(2)];
const nextMonday = nextWeekday(addDays(todayUtc(), 1), 1);
const nextTuesday = nextWeekday(addDays(todayUtc(), 1), 2);

const blocks: [SignedIn, Record<string, unknown>][] = [
	[sam, { start: at(nextTuesday, '19:00'), end: at(nextTuesday, '21:30'), repeat: 'weekly', note: 'Choir' }],
	[sam, { start: at(nextMonday, '09:00'), end: at(nextMonday, '17:30'), repeat: 'weekly', note: 'Work' }],
	[priya, { start: at(w1.sat, '00:00'), end: at(w1.sun, '00:00'), allDay: true }],
	[priya, { start: at(w2.fri, '08:30'), end: at(w2.fri, '17:00') }],
	[ana, { start: at(w2.fri, '09:00'), end: at(w2.fri, '12:00') }],
	[tom, { start: at(w2.sun, '22:00'), end: at(w2.mon, '00:00') }],
	[tom, { start: at(w3.fri, '17:00'), end: at(w3.fri, '23:59') }]
];
for (const [person, block] of blocks) await call(person.token, 'POST', '/api/v1/blocks', block);

// An open proposal for the weekend that works, accepted by some.
const lakesAvailability = (
	await call(sam.token, 'GET', `/api/v1/groups/${lakes.id}/availability?from=${todayUtc()}&to=${addDays(todayUtc(), 60)}`)
).data;
const slot = lakesAvailability.slots[0];
const proposal = slot
	? (await call(ana.token, 'POST', `/api/v1/groups/${lakes.id}/proposals`, { start: slot.start, end: slot.end, location: 'Keswick' })).data
	: null;
if (proposal) for (const person of [priya, leo]) await call(person.token, 'POST', `/api/v1/proposals/${proposal.id}/respond`, { response: 'accepted' });

// Chat with a poll and read receipts.
const say = (person: SignedIn, body: string) => call(person.token, 'POST', `/api/v1/groups/${lakes.id}/messages`, { body });
await say(priya, 'Has everyone put their calendar in yet? Leo…');
await say(tom, "Saturday's fine. Just checking I can get off early on the Friday");
await say(sam, "I'll book somewhere to stay. Vote below");
const poll = (
	await call(sam.token, 'POST', `/api/v1/groups/${lakes.id}/messages`, {
		poll: { question: 'Where should we stay?', options: ['Keswick cottage', 'Ambleside YHA', 'Camping at Wasdale'] }
	})
).data.poll;
const votes: [SignedIn, number][] = [[priya, 0], [ana, 0], [tom, 1]];
for (const [person, option] of votes) await call(person.token, 'POST', `/api/v1/polls/${poll.id}/vote`, { optionIds: [poll.options[option].id] });
await say(ana, 'Cottage. It has a wood burner, case closed');
const latest = (await call(priya.token, 'GET', `/api/v1/groups/${lakes.id}/messages?limit=50`)).data.messages.at(-1);
for (const person of [priya, ana, tom]) {
	await call(person.token, 'POST', `/api/v1/groups/${lakes.id}/messages/receipts`, { upTo: latest.id, state: 'seen' });
}

// A confirmed book club evening, so the home screen has an "Up next" card.
const bookAvailability = (
	await call(sam.token, 'GET', `/api/v1/groups/${bookClub.id}/availability?from=${todayUtc()}&to=${addDays(todayUtc(), 30)}`)
).data;
const evening = bookAvailability.slots[3] ?? bookAvailability.slots[0];
if (evening) {
	const booked = (
		await call(sam.token, 'POST', `/api/v1/groups/${bookClub.id}/proposals`, { start: evening.start, end: evening.end, title: 'Book club', location: "Hana's" })
	).data;
	for (const person of [ana, priya]) await call(person.token, 'POST', `/api/v1/proposals/${booked.id}/respond`, { response: 'accepted' });
}

await Bun.write(STATE_FILE, JSON.stringify({ run, users: people, groups: { lakes: lakes.id, bookClub: bookClub.id } }, null, 2));
console.log(`Seeded demo data. Sign in as: ${sam.email}`);
