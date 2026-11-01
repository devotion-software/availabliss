import { handler, json } from '#lib/server/http';
import { previewInvite } from '#lib/server/groups';

export const GET = handler<{ token: string }>(async ({ params }) => json(await previewInvite(params.token)));
