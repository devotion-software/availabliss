import './setup';
import { afterAll, describe, expect, test } from 'bun:test';
import { db } from '#lib/server/db';
import { requirementTemplates } from '#lib/server/db/schema';
import { createTemplate, deleteTemplate, listTemplates, MAX_TEMPLATES, updateTemplate } from '#lib/server/templates';
import { newId } from '#lib/server/ids';
import { apiError, everyDay, fixture } from './fixtures';

const fx = fixture('templates');
afterAll(() => fx.cleanup());

describe('templates', () => {
	test('create, list by name, update and delete', async () => {
		const me = await fx.user();
		const weekends = await createTemplate(me, { name: 'Weekends', requirement: everyDay() });
		const autumn = await createTemplate(me, { name: 'Autumn evenings', requirement: { ...everyDay('2031-09-01', '2031-11-30'), window: { from: '18:00', to: '22:00' } } });
		expect(weekends).toMatchObject({ name: 'Weekends', requirement: everyDay() });
		expect(Date.parse(weekends.createdAt)).not.toBeNaN();

		expect((await listTemplates(me)).map((t) => t.name)).toEqual(['Autumn evenings', 'Weekends']);

		const renamed = await updateTemplate(me, weekends.id, { name: 'All week' });
		expect(renamed).toMatchObject({ id: weekends.id, name: 'All week', requirement: everyDay() });
		const retimed = await updateTemplate(me, weekends.id, { requirement: everyDay('2032-01-01', '2032-01-31') });
		expect(retimed.requirement.range).toEqual({ from: '2032-01-01', to: '2032-01-31' });
		expect(await updateTemplate(me, autumn.id, {})).toEqual(autumn);

		await deleteTemplate(me, autumn.id);
		expect((await listTemplates(me)).map((t) => t.id)).toEqual([weekends.id]);
	});

	test("templates are private to their owner", async () => {
		const [me, other] = await fx.users(2);
		const t = await createTemplate(me, { name: 'Mine', requirement: everyDay() });
		expect(await listTemplates(other)).toEqual([]);
		expect((await apiError(updateTemplate(other, t.id, { name: 'Theirs' }))).code).toBe('not_found');
		expect((await apiError(deleteTemplate(other, t.id))).code).toBe('not_found');
		expect((await listTemplates(me))[0].name).toBe('Mine');
	});

	test(`at most ${MAX_TEMPLATES} per user`, async () => {
		const [me, other] = await fx.users(2);
		await db.insert(requirementTemplates).values(
			Array.from({ length: MAX_TEMPLATES - 1 }, (_, i) => ({ id: newId(), userId: me, name: `T${i}`, requirement: everyDay() }))
		);
		const last = await createTemplate(me, { name: 'Last one', requirement: everyDay() });
		const err = await apiError(createTemplate(me, { name: 'Too many', requirement: everyDay() }));
		expect(err.code).toBe('validation_error');
		expect(err.message).toContain(String(MAX_TEMPLATES));
		// The limit is per user, and deleting one frees a place.
		await createTemplate(other, { name: 'Fine', requirement: everyDay() });
		await deleteTemplate(me, last.id);
		await createTemplate(me, { name: 'Room again', requirement: everyDay() });
		expect((await listTemplates(me)).length).toBe(MAX_TEMPLATES);
	});
});
