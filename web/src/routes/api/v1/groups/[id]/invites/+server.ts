import { authed, body, json } from '#lib/server/http';
import { createInvite, getGroupRow } from '#lib/server/groups';
import { getMe } from '#lib/server/users';
import { sendInviteEmail } from '#lib/server/email';
import { env } from '#lib/server/env';
import { inviteSchema } from '#lib/schemas';

export const POST = authed<{ id: string }>(async ({ event, params, userId }) => {
	const { email } = await body(event, inviteSchema);
	const { token, expiresAt } = await createInvite(params.id, userId, email ?? null);
	const url = `${env.baseUrl}/invite/${token}`;
	if (email) {
		const [me, group] = await Promise.all([getMe(userId), getGroupRow(params.id)]);
		await sendInviteEmail({ email, inviterName: me.name, groupName: group.name, url });
	}
	return json({ token, url, expiresAt: expiresAt.toISOString() }, { status: 201 });
});
