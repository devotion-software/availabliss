import { expect, test } from '@playwright/test';
import { freshEmail, signUp } from './helpers';

test('sign up, create a group, invite a friend, propose a time and get everyone in', async ({ browser }) => {
	const owner = await browser.newPage();
	await signUp(owner, freshEmail('owner'), 'Olive Owner');

	// Onboarding's group step: name it and keep the suggested timing.
	await owner.getByLabel('Group name').fill('E2E supper club');
	await owner.getByRole('button', { name: 'Create group' }).click();
	await owner.getByRole('button', { name: 'Done' }).click();
	await expect(owner).toHaveURL(/\/app\/groups\/[\w-]+/);
	const groupId = owner.url().match(/groups\/([\w-]+)/)![1];

	// A friend joins with an invite link.
	const invite = await owner.request.post(`/api/v1/groups/${groupId}/invites`, { data: { maxUses: 1 } });
	expect(invite.ok()).toBeTruthy();
	const { url } = (await invite.json()) as { url: string };
	const friend = await browser.newPage();
	await signUp(friend, freshEmail('friend'), 'Fred Friend');
	await friend.getByRole('button', { name: 'Skip for now' }).click();
	await expect(friend).toHaveURL(/\/app/);
	await friend.goto(new URL(url).pathname, { waitUntil: 'networkidle' });
	await friend.getByRole('button', { name: /join/i }).click();
	// "How should E2E supper club see you?": keep their account name.
	await friend.getByRole('button', { name: 'Keep Fred Friend' }).click();
	await expect(friend).toHaveURL(/\/app\/groups\//);

	// The owner proposes the first time that works.
	await owner.goto(`/app/groups/${groupId}`, { waitUntil: 'networkidle' });
	await owner.getByRole('button', { name: 'Propose', exact: true }).first().click();
	await owner.getByRole('button', { name: /Send proposal|Propose anyway/ }).click();
	await expect(owner.getByRole('dialog', { name: 'Propose a time' })).toBeHidden();
	const listed = await owner.request.get(`/api/v1/groups/${groupId}/proposals`);
	const { proposals } = (await listed.json()) as { proposals: { id: string; status: string }[] };
	const open = proposals.find((p) => p.status === 'open');
	expect(open).toBeDefined();
	const proposalPath = `/app/proposals/${open!.id}`;

	// The friend accepts, which confirms it.
	await friend.goto(proposalPath, { waitUntil: 'networkidle' });
	await friend.getByRole('button', { name: 'Accept', exact: true }).click();
	await expect(friend.getByRole('heading', { name: /Everyone’s in\.|Everyone's in\./ })).toBeVisible();
	await owner.reload();
	await expect(owner.getByRole('heading', { name: /Everyone’s in\.|Everyone's in\./ })).toBeVisible();
});
