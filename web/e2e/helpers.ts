import { expect, type Page } from '@playwright/test';

export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8027';

/** A fresh address for this run, so tests never collide with each other or earlier runs. */
export const freshEmail = (who: string) => `e2e-${who}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}@example.com`;

/** The newest 6-digit sign-in code Mailpit received for `email`. */
export async function codeFor(email: string): Promise<string> {
	for (let i = 0; i < 60; i++) {
		const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
		const { messages } = (await res.json()) as { messages: { Snippet: string }[] };
		const code = messages[0]?.Snippet.match(/\b(\d{6})\b/)?.[1];
		if (code) return code;
		await new Promise((r) => setTimeout(r, 250));
	}
	throw new Error(`No sign-in code arrived for ${email}`);
}

/** Signs up through the real screens: email code, About you, Calendars (skipping), ending in onboarding's group step. */
export async function signUp(page: Page, email: string, name: string) {
	await page.goto('/signin', { waitUntil: 'networkidle' });
	await page.getByLabel('Email').fill(email);
	await page.getByRole('button', { name: 'Email me a code' }).click();
	await expect(page.getByText('Check your inbox')).toBeVisible();
	await page.keyboard.type(await codeFor(email));
	await expect(page.getByRole('heading', { name: 'About you' })).toBeVisible({ timeout: 30_000 });
	await page.getByLabel('Your name').fill(name);
	await page.getByRole('button', { name: 'Continue' }).click();
	await page.getByRole('button', { name: 'Continue' }).click();
}
