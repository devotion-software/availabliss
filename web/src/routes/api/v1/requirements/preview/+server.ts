import { z } from 'zod';
import { authed, body, json } from '#lib/server/http';
import { previewRequirement } from '#lib/server/availability/service';
import { requirementSummary } from '#lib/server/availability/labels';
import { requirementSchema } from '#lib/schemas';

export const POST = authed(async ({ event, userId }) => {
	const { requirement } = await body(event, z.object({ requirement: requirementSchema }));
	return json({ ...(await previewRequirement(userId, requirement)), summary: requirementSummary(requirement) });
});
