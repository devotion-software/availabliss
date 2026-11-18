import { ApiError, authed, body, json, notFound } from '#lib/server/http';
import { createInvite, getGroupRow, requireMember, listInvites, memberIds, peopleInAGroupWith, usersByIds } from '#lib/server/groups';
import { getMe } from '#lib/server/users';
import { sendInviteEmail } from '#lib/server/email';
import { env } from '#lib/server/env';
import { displayName } from '#lib/server/names';
import { notify } from '#lib/server/notifications';
import { findByUsername } from '#lib/server/usernames';
import { inviteSchema } from '#lib/schemas';
import type { GroupInvite } from '#lib/types';
import { db } from '#lib/server/db';
import { user } from '#lib/server/db/schema';
import { inArray } from 'drizzle-orm';

const inviteUrl = (token: string) => `${env.baseUrl}/invite/${token}`;

/** Live invites: links anyone can use, and emailed invites still waiting for their person. */
export const GET = authed<{ id: string }>(async ({ params, userId }) => {
	const rows = await listInvites(params.id, userId);
	const invites: GroupInvite[] = rows.map((r) => ({
		id: r.id,
		email: r.email,
		username: r.username,
		name: r.inviteeEmail ? displayName({ name: r.inviteeName, email: r.inviteeEmail }) : null,
		url: inviteUrl(r.token),
		maxUses: r.maxUses,
		uses: r.uses,
		expiresAt: r.expiresAt.toISOString(),
		createdAt: r.createdAt.toISOString(),
		invitedBy: displayName({ name: r.inviterName, email: r.inviterEmail, nameInGroup: r.inviterNameInGroup })
	}));
	return json({ invites });
});

/**
 * Nothing: a link anyone can use, by `maxUses` people (null: no limit; left out: one). With `emails` (or `email`): one invite per address, emailed to
 * it, that only that person can accept. With `usernames`: one invite per person, sent to their
 * Activity (and phone) and only they can accept. With `userIds`: the same, for people who are already
 * in a group with you (a new group with the same people), whether or not they have a username.
 * Returns the first invite's link (as before) plus all of them.
 */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, inviteSchema);
	await requireMember(params.id, userId, 'admin');
	const emails = [...new Set([...(input.emails ?? []), ...(input.email ? [input.email] : [])].map((e) => e.trim().toLowerCase()))];

	// Everyone named has to exist and not be in the group yet, before anything is sent.
	const usernames = [...new Set(input.usernames ?? [])];
	const userIds = [...new Set(input.userIds ?? [])];
	const members = usernames.length || userIds.length ? new Set(await memberIds(params.id)) : new Set<string>();
	const people: { id: string; username: string | null; name: string }[] = [];
	for (const username of usernames) {
		const person = await findByUsername(username);
		if (!person) throw notFound(`No one has the username @${username}`);
		if (members.has(person.id)) throw new ApiError('conflict', `@${person.username} is already in this group`);
		people.push({ id: person.id, username: person.username, name: displayName({ name: person.name, email: null }, `@${person.username}`) });
	}
	// By id: only people you already share a group with, so ids can't be used to reach strangers.
	if (userIds.length) {
		const [known, found] = await Promise.all([peopleInAGroupWith(userId, userIds), usersByIds(userIds)]);
		for (const id of userIds) {
			const person = found.find((u) => u.id === id);
			if (!person || !known.has(id)) throw notFound("You can only invite people you're in a group with");
			if (members.has(id)) throw new ApiError('conflict', `${displayName(person)} is already in this group`);
			if (people.some((p) => p.id === id)) continue;
			people.push({ id, username: null, name: displayName(person) });
		}
		const named = await db.select({ id: user.id, username: user.username }).from(user).where(inArray(user.id, userIds));
		for (const p of people) p.username ??= named.find((n) => n.id === p.id)?.username ?? null;
	}

	const created = [];
	for (const email of emails.length || people.length ? emails : [null]) {
		const { id, token, expiresAt, maxUses } = await createInvite(params.id, userId, email, null, input.maxUses === undefined ? 1 : input.maxUses);
		created.push({ id, maxUses, email, username: null as string | null, name: null as string | null, invitedUserId: null as string | null, token, url: inviteUrl(token), expiresAt: expiresAt.toISOString() });
	}
	for (const person of people) {
		const { id, token, expiresAt, maxUses } = await createInvite(params.id, userId, null, person.id);
		created.push({ id, maxUses, email: null, username: person.username, name: person.name, invitedUserId: person.id, token, url: inviteUrl(token), expiresAt: expiresAt.toISOString() });
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
		name: c.name,
		url: c.url,
		maxUses: c.maxUses,
		uses: 0,
		expiresAt: c.expiresAt,
		createdAt: new Date().toISOString(),
		invitedBy: ''
	}));
	return json({ token: first.token, url: first.url, expiresAt: first.expiresAt, invites }, { status: 201 });
});
