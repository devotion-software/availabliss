<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import { replaceState } from '$app/navigation';
	import { authClient } from '#lib/auth-client';
	import { api } from '#lib/client/api';
	import { toasts } from '#lib/client/toast.svelte';
	import type { LinkedAccounts, LinkProvider } from '#lib/types';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';

	/**
	 * Email, Apple and Google sign-in on this account. Linking a sign-in that has
	 * its own account offers to merge that account into this one.
	 */
	interface Props {
		email: string;
	}
	let { email }: Props = $props();

	const LABEL: Record<LinkProvider, string> = { apple: 'Apple', google: 'Google' };
	const HERE = '/app/settings';

	let accounts = $state<LinkedAccounts | null>(null);
	let busy = $state<LinkProvider | null>(null);
	let mergeProvider = $state<LinkProvider | null>(null);
	let confirmMerge = $state(false);

	const isLinked = (p: LinkProvider) => !!accounts?.linked.some((a) => a.provider === p);

	async function load() {
		try {
			accounts = await api.get<LinkedAccounts>('/me/accounts');
		} catch (err) {
			accounts = { linked: [], available: [] };
			toasts.error(err);
		}
	}

	onMount(load);

	// Coming back from the provider (always a full page load): say what happened once, then tidy the URL.
	// onMount, not $effect: the toasts below would otherwise re-trigger it.
	onMount(() => {
		const q = page.url.searchParams;
		const provider = (q.get('linked') ?? q.get('merged') ?? q.get('link_provider')) as LinkProvider | null;
		const label = provider ? LABEL[provider] : 'That sign-in';
		const error = q.get('error') ?? q.get('link_error');
		if (!provider && !error) return;
		if (q.get('merged')) toasts.success(`Accounts merged. You can sign in with ${label} or your email.`);
		else if (q.get('linked')) toasts.success(`${label} linked. You can use it to sign in.`);
		else if (error === 'account_already_linked_to_different_user' && provider) {
			mergeProvider = provider;
			confirmMerge = true;
		} else if (error) toasts.error(linkErrorMessage(error, label));
		const clean = new URL(page.url.href);
		for (const k of ['linked', 'merged', 'link_provider', 'error', 'error_description', 'link_error']) clean.searchParams.delete(k);
		replaceState(clean, page.state);
	});

	function linkErrorMessage(code: string, label: string): string {
		switch (code) {
			case 'merge_expired':
				return 'That took too long. Try linking again.';
			case 'merge_wrong_account':
				return `That wasn't the ${label} account you started with. Nothing was merged.`;
			case 'merge_not_signed_in':
				return `Sign in with ${label} didn't finish, so nothing was merged.`;
			case 'merge_failed':
				return "Couldn't merge the accounts. Nothing was changed; try again.";
			default:
				return `Couldn't link ${label}. Try again.`;
		}
	}

	async function link(provider: LinkProvider) {
		busy = provider;
		const res = await authClient.linkSocial({
			provider,
			callbackURL: `${HERE}?linked=${provider}`,
			errorCallbackURL: `${HERE}?link_provider=${provider}`
		});
		// On success the browser goes to the provider.
		if (res?.error) {
			toasts.error(res.error.message ?? `Couldn't start ${LABEL[provider]} sign-in.`);
			busy = null;
		}
	}

	async function merge() {
		const provider = mergeProvider;
		if (!provider) return;
		busy = provider;
		try {
			await api.post('/me/accounts/merge-intent', { provider });
			// Signing in to the other account proves it's yours; /link/complete merges it into this one.
			const res = await authClient.signIn.social({ provider, callbackURL: '/link/complete', errorCallbackURL: `${HERE}?link_error=merge_not_signed_in&link_provider=${provider}` });
			if (res?.error) throw new Error(res.error.message ?? `Couldn't start ${LABEL[provider]} sign-in.`);
		} catch (err) {
			toasts.error(err);
			busy = null;
		}
	}
</script>

<GlassPanel as="section" padding="16px" gap={10} aria-labelledby="signin-methods-title">
	<h2 class="eyebrow" id="signin-methods-title">Sign-in methods</h2>
	<p class="muted small">Link Apple or Google to sign in with them. If one already has its own Availabliss account, you can merge it into this one.</p>
	<div class="row first">
		<span class="tile"><Icon name="mail" size={18} /></span>
		<span class="text">
			<span class="name">Email code</span>
			<span class="sub">{email}</span>
		</span>
		<span class="on"><Icon name="check" size={14} strokeWidth={3} />On</span>
	</div>
	{#if accounts === null}
		<div class="loading"><Spinner size={16} /></div>
	{:else}
		{#each accounts.available as provider (provider)}
			<div class="row">
				<span class="tile brand"><BrandIcon brand={provider} /></span>
				<span class="text">
					<span class="name">{LABEL[provider]}</span>
					<span class="sub">{isLinked(provider) ? 'Linked' : 'Not linked'}</span>
				</span>
				{#if isLinked(provider)}
					<span class="on"><Icon name="check" size={14} strokeWidth={3} />Linked</span>
				{:else}
					<Button size="sm" variant="secondary" loading={busy === provider} disabled={!!busy} onclick={() => link(provider)}>Link</Button>
				{/if}
			</div>
		{/each}
	{/if}
</GlassPanel>

<ConfirmSheet
	bind:open={confirmMerge}
	title="Merge accounts?"
	message="That {mergeProvider ? LABEL[mergeProvider] : ''} sign-in already has its own Availabliss account. Merging moves its groups, busy times, chats and Pro into this account, then deletes the other one. You'll sign in with {mergeProvider
		? LABEL[mergeProvider]
		: 'it'} once more to confirm. This can't be undone."
	confirmLabel="Merge into this account"
	onconfirm={merge}
/>

<style>
	.small {
		font-size: 13px;
	}
	.loading {
		display: flex;
		justify-content: center;
		padding: 10px;
		color: var(--muted);
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 54px;
		border-top: 1px solid var(--line);
	}
	.tile {
		width: 34px;
		height: 34px;
		border-radius: 11px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		display: flex;
		align-items: center;
		justify-content: center;
		flex: none;
	}
	.tile.brand {
		background: var(--surface);
		color: var(--ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.name {
		font-size: 14px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.on {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: 12px;
		font-weight: 600;
		color: var(--ok-ink);
	}
</style>
