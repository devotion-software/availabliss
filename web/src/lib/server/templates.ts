/** Saved timing templates: a user's own reusable group timings. */
import { and, asc, count, eq } from 'drizzle-orm';
import type { Requirement, RequirementTemplate } from '#lib/types';
import { db } from './db';
import { requirementTemplates } from './db/schema';
import { badRequest, notFound } from './http';
import { newId } from './ids';

/** Plenty for real use, and keeps the list (and table) from growing without bound. */
export const MAX_TEMPLATES = 50;

type Row = typeof requirementTemplates.$inferSelect;

const toTemplate = (r: Row): RequirementTemplate => ({
	id: r.id,
	name: r.name,
	requirement: r.requirement,
	createdAt: r.createdAt.toISOString()
});

export async function listTemplates(userId: string): Promise<RequirementTemplate[]> {
	const rows = await db
		.select()
		.from(requirementTemplates)
		.where(eq(requirementTemplates.userId, userId))
		.orderBy(asc(requirementTemplates.name));
	return rows.map(toTemplate);
}

async function getRow(userId: string, id: string): Promise<Row> {
	const row = await db.query.requirementTemplates.findFirst({
		where: and(eq(requirementTemplates.id, id), eq(requirementTemplates.userId, userId))
	});
	if (!row) throw notFound('Template not found');
	return row;
}

export async function createTemplate(userId: string, input: { name: string; requirement: Requirement }): Promise<RequirementTemplate> {
	const [{ n }] = await db.select({ n: count() }).from(requirementTemplates).where(eq(requirementTemplates.userId, userId));
	if (n >= MAX_TEMPLATES) throw badRequest(`You can save up to ${MAX_TEMPLATES} templates. Delete one you don't use first.`);
	const id = newId();
	await db.insert(requirementTemplates).values({ id, userId, ...input });
	return toTemplate(await getRow(userId, id));
}

export async function updateTemplate(
	userId: string,
	id: string,
	patch: { name?: string; requirement?: Requirement }
): Promise<RequirementTemplate> {
	await getRow(userId, id);
	if (patch.name !== undefined || patch.requirement !== undefined) {
		await db
			.update(requirementTemplates)
			.set(patch)
			.where(and(eq(requirementTemplates.id, id), eq(requirementTemplates.userId, userId)));
	}
	return toTemplate(await getRow(userId, id));
}

export async function deleteTemplate(userId: string, id: string): Promise<void> {
	await getRow(userId, id);
	await db.delete(requirementTemplates).where(and(eq(requirementTemplates.id, id), eq(requirementTemplates.userId, userId)));
}
