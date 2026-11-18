import { expect, test } from '@playwright/test';
import { freshEmail, signUp } from './helpers';

test('block a time in My time', async ({ page }) => {
	await signUp(page, freshEmail('blocker'), 'Bea Blocker');
	await page.getByRole('button', { name: 'Skip for now' }).click();
	await page.goto('/app/my-time', { waitUntil: 'networkidle' });
	await page.getByRole('button', { name: 'Block time' }).click();
	await expect(page.getByRole('dialog', { name: 'Block a time' })).toBeVisible();
	await page.getByLabel('Note (only you see this)').fill('E2E dentist');
	await page.getByRole('button', { name: 'Block this time' }).click();
	await expect(page.getByRole('dialog', { name: 'Block a time' })).toBeHidden();
	// The agenda lists the block with its note (only you see it).
	await page.getByRole('button', { name: 'Show as a list' }).click();
	await expect(page.getByText('E2E dentist').first()).toBeVisible();
});
