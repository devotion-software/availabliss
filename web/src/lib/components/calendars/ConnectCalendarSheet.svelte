<script lang="ts">
	import type { CalendarConnection } from '#lib/types';
	import { caldavSchema } from '#lib/schemas';
	import { connectCalDav, oauthStartUrl } from '#lib/client/calendars';
	import { ApiError } from '#lib/client/api';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/** Pick a provider: Google / Outlook go through OAuth, iCloud uses an app-specific password. */
	interface Props {
		open: boolean;
		/** Path the OAuth flow returns to. */
		redirect: string;
		onconnected: (connection: CalendarConnection) => void;
	}
	let { open = $bindable(), redirect, onconnected }: Props = $props();

	let icloud = $state(false);
	let username = $state('');
	let appPassword = $state('');
	let error = $state<string | null>(null);
	let busy = $state(false);

	$effect(() => {
		if (!open) {
			icloud = false;
			error = null;
			appPassword = '';
		}
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const parsed = caldavSchema.safeParse({ username, appPassword });
		if (!parsed.success) {
			error = 'Enter your Apple ID email and an app-specific password';
			return;
		}
		busy = true;
		error = null;
		try {
			const connection = await connectCalDav(parsed.data);
			onconnected(connection);
			toasts.success('iCloud calendar connected');
			open = false;
		} catch (err) {
			error = err instanceof ApiError ? err.message : null;
			if (!error) toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title={icloud ? 'Connect iCloud' : 'Connect a calendar'} description="Only busy times are shared, never the details.">
	{#if !icloud}
		<div class="providers">
			<a class="provider" href={oauthStartUrl('google', redirect)} data-sveltekit-reload>
				<BrandIcon brand="google" size={22} />
				<span class="label">Google Calendar</span>
				<Icon name="chevron-right" size={18} />
			</a>
			<a class="provider" href={oauthStartUrl('microsoft', redirect)} data-sveltekit-reload>
				<BrandIcon brand="microsoft" size={20} />
				<span class="label">Outlook / Microsoft 365</span>
				<Icon name="chevron-right" size={18} />
			</a>
			<button type="button" class="provider" onclick={() => (icloud = true)}>
				<BrandIcon brand="icloud" size={22} />
				<span class="label">iCloud</span>
				<Icon name="chevron-right" size={18} />
			</button>
		</div>
	{:else}
		<form class="form" onsubmit={submit} novalidate>
			<TextField label="Apple ID email" type="email" autocomplete="username" bind:value={username} placeholder="you@icloud.com" autofocus />
			<TextField
				label="App-specific password"
				type="password"
				autocomplete="off"
				bind:value={appPassword}
				placeholder="xxxx-xxxx-xxxx-xxxx"
				{error}
			/>
			<p class="help">
				Create one at <a href="https://account.apple.com/account/manage" target="_blank" rel="noopener noreferrer">appleid.apple.com</a>
				under Sign-In and Security → App-Specific Passwords. Your normal Apple password won't work.
			</p>
			<div class="actions">
				<Button variant="secondary" onclick={() => (icloud = false)}>Back</Button>
				<Button type="submit" variant="accent" loading={busy}>Connect</Button>
			</div>
		</form>
	{/if}
</Sheet>

<style>
	.providers {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.provider {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 56px;
		padding: 0 14px;
		border-radius: 18px;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		color: var(--ink);
		text-decoration: none;
		font-size: 15px;
		font-weight: 600;
		text-align: left;
		width: 100%;
		transition: transform 0.15s ease;
	}
	.provider:hover {
		color: var(--ink);
		transform: translateY(-1px);
	}
	.label {
		flex: 1;
	}
	.form {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.help {
		font-size: 13px;
		color: var(--muted);
		line-height: 1.45;
	}
	.actions {
		display: flex;
		gap: 8px;
	}
	.actions > :global(*) {
		flex: 1;
	}
</style>
