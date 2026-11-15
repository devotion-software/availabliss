import { authed, body, json } from '#lib/server/http';
import { createInvite, getGroupRow, listInvites } from '#lib/server/groups';
import { getMe } from '#lib/server/users';
import { sendInviteEmail } from '#lib/server/email';
import { env } from '#lib/server/env';
import { displayName } from '#lib/server/names';
import { inviteSchema } from '#lib/schemas';
import type { GroupInvite } from '#lib/types';

const inviteUrl = (token: string) => `${env.baseUrl}/invite/${token}`;

/** Live invites: links anyone can use, and emailed invites still waiting for their person. */
export const GET = authed<{ id: string }>(async ({ params, userId }) => {
	const rows = await listInvites(params.id, userId);
	const invites: GroupInvite[] = rows.map((r) => ({
		id: r.id,
		email: r.email,
		url: inviteUrl(r.token),
		expiresAt: r.expiresAt.toISOString(),
		createdAt: r.createdAt.toISOString(),
		invitedBy: displayName({ name: r.inviterName, email: r.inviterEmail })
	}));
	return json({ invites });
});

/**
 * No email: a link anyone can use. With `emails` (or `email`): one invite per address, emailed to
 * it, that only that person can accept. Returns the first invite's link (as before) plus all of them.
 */
export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const input = await body(event, inviteSchema);
	const emails = [...new Set([...(input.emails ?? []), ...(input.email ? [input.email] : [])].map((e) => e.trim().toLowerCase()))];
	const created = [];
	for (const email of emails.length ? emails : [null]) {
		const { id, token, expiresAt } = await createInvite(params.id, userId, email);
		created.push({ id, email, token, url: inviteUrl(token), expiresAt: expiresAt.toISOString() });
	}
	if (emails.length) {
		const [me, group] = await Promise.all([getMe(userId), getGroupRow(params.id)]);
		const inviterName = displayName(me);
		await Promise.all(created.map((c) => sendInviteEmail({ email: c.email!, inviterName, groupName: group.name, url: c.url })));
	}
	const [first] = created;
	const invites: GroupInvite[] = created.map((c) => ({
		id: c.id,
		email: c.email,
		url: c.url,
		expiresAt: c.expiresAt,
		createdAt: new Date().toISOString(),
		invitedBy: ''
	}));
	return json({ token: first.token, url: first.url, expiresAt: first.expiresAt, invites }, { status: 201 });
});
