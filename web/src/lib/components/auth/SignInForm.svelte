<script lang="ts">
	import { goto } from '$app/navigation';
	import { authClient } from '#lib/auth-client';
	import Icon from '#lib/components/ui/Icon.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import OtpInput from './OtpInput.svelte';

	/** Email one-time code, Apple, Google and passkey (SignIn.dc.html). */
	interface Props {
		/** Where to go once signed in. */
		next: string;
	}
	let { next }: Props = $props();

	let step = $state<'email' | 'code'>('email');
	let email = $state('');
	let error = $state<string | null>(null);
	let providerError = $state<string | null>(null);
	let sending = $state(false);
	let verifying = $state(false);
	let busyProvider = $state<'apple' | 'google' | 'passkey' | null>(null);
	let otp: OtpInput | undefined = $state();

	const emailValid = $derived(/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()));

	async function sendCode(e?: SubmitEvent) {
		e?.preventDefault();
		if (!emailValid) {
			error = 'Enter a valid email address';
			return;
		}
		sending = true;
		error = null;
		const { error: err } = await authClient.emailOtp.sendVerificationOtp({ email: email.trim(), type: 'sign-in' });
		sending = false;
		if (err) {
			error = err.message ?? "We couldn't send a code. Try again.";
			return;
		}
		step = 'code';
	}

	async function verify(code: string) {
		verifying = true;
		error = null;
		const { error: err } = await authClient.signIn.emailOtp({ email: email.trim(), otp: code });
		if (err) {
			verifying = false;
			error = err.message ?? "That code didn't work. Check it and try again.";
			otp?.reset();
			return;
		}
		await goto(next, { invalidateAll: true });
	}

	async function social(provider: 'apple' | 'google') {
		busyProvider = provider;
		providerError = null;
		const res = await authClient.signIn.social({ provider, callbackURL: next });
		// On success the browser is redirected to the provider.
		if (res?.error) {
			providerError = res.error.message ?? `Couldn't start ${provider === 'apple' ? 'Apple' : 'Google'} sign-in.`;
			busyProvider = null;
		}
	}

	async function passkey() {
		busyProvider = 'passkey';
		providerError = null;
		const res = await authClient.signIn.passkey();
		if (res?.error) {
			providerError = res.error.message ?? 'No passkey was used.';
			busyProvider = null;
			return;
		}
		await goto(next, { invalidateAll: true });
	}

	function back() {
		step = 'email';
		error = null;
	}
</script>

<form class="glass form" onsubmit={sendCode} novalidate>
	{#if step === 'email'}
		<TextField
			label="Email"
			type="email"
			autocomplete="email webauthn"
			placeholder="you@example.com"
			bind:value={email}
			{error}
		/>
		<Button type="submit" size="md" block loading={sending}>Email me a code</Button>
	{:else}
		<div class="check">
			<span class="title">Check your inbox</span>
			<span class="sub">Enter the 6-digit code we sent to <strong>{email.trim()}</strong>.</span>
		</div>
		<OtpInput bind:this={otp} disabled={verifying} oncomplete={verify} />
		{#if verifying}
			<span class="verifying"><Spinner size={14} /> Signing you in…</span>
		{:else if error}
			<p class="error" role="alert">{error}</p>
		{/if}
		<div class="code-actions">
			<button type="button" class="link" onclick={back}>Use a different email</button>
			<button type="button" class="link" disabled={sending} onclick={() => sendCode()}>Send again</button>
		</div>
	{/if}

	<div class="or" aria-hidden="true"><span></span>or<span></span></div>

	<div class="providers">
		<button type="button" class="provider" disabled={!!busyProvider} onclick={() => social('apple')}>
			{#if busyProvider === 'apple'}<Spinner size={16} />{:else}<BrandIcon brand="apple" />{/if}
			Apple
		</button>
		<button type="button" class="provider" disabled={!!busyProvider} onclick={() => social('google')}>
			{#if busyProvider === 'google'}<Spinner size={16} />{:else}<BrandIcon brand="google" />{/if}
			Google
		</button>
		<button type="button" class="provider" disabled={!!busyProvider} onclick={passkey}>
			{#if busyProvider === 'passkey'}<Spinner size={16} />{:else}<Icon name="passkey" size={18} />{/if}
			Passkey
		</button>
	</div>

	{#if providerError}<p class="error" role="alert">{providerError}</p>{/if}

	<p class="privacy">
		<Icon name="shield" size={14} />
		Your group only ever sees when you're busy, never what you're doing.
	</p>
</form>

<style>
	.form {
		border-radius: 30px;
		padding: 20px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.form :global(label) {
		color: var(--ink);
	}
	.check {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.title {
		font-size: 15px;
		font-weight: 600;
	}
	.sub {
		font-size: 13px;
		color: var(--muted);
		overflow-wrap: anywhere;
	}
	.sub strong {
		color: var(--ink);
		font-weight: 600;
	}
	.verifying {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 13px;
		color: var(--muted);
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
	.code-actions {
		display: flex;
		justify-content: space-between;
	}
	.link {
		min-height: 44px;
		padding: 0 4px;
		font-size: 14px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.link:disabled {
		opacity: 0.5;
	}
	.or {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 12px;
		color: var(--muted);
	}
	.or span {
		flex: 1;
		height: 1px;
		background: var(--line-2);
	}
	.providers {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 8px;
	}
	.provider {
		height: 50px;
		border-radius: 16px;
		border: 1px solid var(--glass-border);
		background: var(--surface);
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
		transition: transform 0.15s ease;
	}
	.provider:hover:not(:disabled) {
		transform: translateY(-1px);
	}
	.provider:disabled {
		opacity: 0.6;
	}
	.privacy {
		margin-top: 2px;
		display: flex;
		gap: 8px;
		align-items: flex-start;
		font-size: 12px;
		line-height: 1.4;
		color: var(--muted);
	}
	.privacy :global(svg) {
		margin-top: 1px;
	}
</style>
