import { ApiError, authed, body, json, notFound } from '#lib/server/http';
import { createInvite, getGroupRow, listInvites, memberIds } from '#lib/server/groups';
import { getMe } from '#lib/server/users';
import { sendInviteEmail } from '#lib/server/email';
import { env } from '#lib/server/env';
import { displayName } from '#lib/server/names';
import { notify } from '#lib/server/notifications';
import { findByUsername } from '#lib/server/usernames';
import { inviteSchema } from '#lib/schemas';
import type { GroupInvite } from '#lib/types';

const inviteUrl = (token: string) => `${env.baseUrl}/invite/${token}`;

/** Live invites: links anyone can use, and emailed invites still waiting for their person. */
export const GET = authed<{ id: string }>(async ({ params, userId }) => {
	const rows = await listInvites(params.id, userId);
	const invites: GroupInvite[] = rows.map((r) => ({
		id: r.id,
		email: r.email,
		username: r.username,
		url: inviteUrl(r.token),
		expiresAt: r.expiresAt.toISOString(),
		createdAt: r.createdAt.toISOString(),
		invitedBy: displayName({ name: r.inviterName, email: r.inviterEmail, nameInGroup: r.inviterNameInGroup })
	}));
	return json({ invites });
});

/**
 * Nothing: a link anyone can use. With `emails` (or `email`): one invite per address, emailed to
 * it, that only that person can accept. With `usernames`: one invite per person, sent to their
 * Activity (and phone) and only they can accept. Returns the first invite's link (as before) plus all of them.
 */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, inviteSchema);
	const emails = [...new Set([...(input.emails ?? []), ...(input.email ? [input.email] : [])].map((e) => e.trim().toLowerCase()))];

	// Everyone named has to exist and not be in the group yet, before anything is sent.
	const usernames = [...new Set(input.usernames ?? [])];
	const members = usernames.length ? new Set(await memberIds(params.id)) : new Set<string>();
	const people = [];
	for (const username of usernames) {
		const person = await findByUsername(username);
		if (!person) throw notFound(`No one has the username @${username}`);
		if (members.has(person.id)) throw new ApiError('conflict', `@${person.username} is already in this group`);
		people.push(person);
	}

	const created = [];
	for (const email of emails.length || people.length ? emails : [null]) {
		const { id, token, expiresAt } = await createInvite(params.id, userId, email);
		created.push({ id, email, username: null as string | null, invitedUserId: null as string | null, token, url: inviteUrl(token), expiresAt: expiresAt.toISOString() });
	}
	for (const person of people) {
		const { id, token, expiresAt } = await createInvite(params.id, userId, null, person.id);
		created.push({ id, email: null, username: person.username, invitedUserId: person.id, token, url: inviteUrl(token), expiresAt: expiresAt.toISOString() });
	}
	if (emails.length || people.length) {
		const [me, group] = await Promise.all([getMe(userId), getGroupRow(params.id)]);
		const inviterName = displayName(me);
		await Promise.all(
			created.filter((c) => c.email).map((c) => sendInviteEmail({ email: c.email!, inviterName, groupName: group.name, url: c.url }))
		);
		for (const c of created.filter((c) => c.invitedUserId)) {
			await notify([c.invitedUserId!], {
				kind: 'invite.received',
				groupId: null,
				title: group.name,
				body: `${inviterName} invited you to join`,
				data: { token: c.token }
			});
		}
	}
	const [first] = created;
	const invites: GroupInvite[] = created.map((c) => ({
		id: c.id,
		email: c.email,
		username: c.username,
		url: c.url,
		expiresAt: c.expiresAt,
		createdAt: new Date().toISOString(),
		invitedBy: ''
	}));
	return json({ token: first.token, url: first.url, expiresAt: first.expiresAt, invites }, { status: 201 });
});
