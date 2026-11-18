import { authed, json } from '#lib/server/http';
import { nextSuggestion } from '#lib/server/rhythm';

/** The next free time near the group's rhythm: `{ target, slot }`, or null when it has no rhythm. */
export const GET = authed<{ id: string }>(async ({ params, userId }) => json({ suggestion: await nextSuggestion(params.id, userId) }));
