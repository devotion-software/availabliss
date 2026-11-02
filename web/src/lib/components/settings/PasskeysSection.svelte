<script lang="ts">
	import { authClient } from '#lib/auth-client';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';

	interface PasskeyRow {
		id: string;
		name?: string | null;
		createdAt: Date | string;
		deviceType?: string;
	}

	const fmt = useFormat();
	let passkeys = $state<PasskeyRow[] | null>(null);
	let adding = $state(false);
	let removing = $state<PasskeyRow | null>(null);
	let confirmOpen = $state(false);

	async function load() {
		const { data, error } = await authClient.passkey.listUserPasskeys();
		if (error) {
			toasts.error(error.message ?? "Couldn't load your passkeys");
			passkeys = [];
			return;
		}
		passkeys = (data ?? []) as PasskeyRow[];
	}

	$effect(() => {
		load();
	});

	async function add() {
		adding = true;
		try {
			// No name here: Better Auth would use it as the credential's user name, so password
			// managers would list "Mac · Firefox" instead of your email. The device name goes on
			// the saved passkey instead, to tell your passkeys apart in the list below.
			const res = await authClient.passkey.addPasskey();
			if (res?.error) {
				const message = addError(res.error);
				if (message) toasts.error(message);
				return;
			}
			const id = (res?.data as { id?: string } | null)?.id;
			if (id) await authClient.passkey.updatePasskey({ id, name: deviceName() });
			toasts.success('Passkey added');
			await load();
		} catch (err) {
			toasts.error(err instanceof Error ? err.message : 'No passkey was added');
		} finally {
			adding = false;
		}
	}

	/** What to tell the user when a passkey wasn't added; null when they cancelled. */
	function addError(error: { code?: string; message?: string; status?: number }): string | null {
		if (error.code === 'ERROR_AUTHENTICATOR_PREVIOUSLY_REGISTERED') return 'This device already has a passkey for your account.';
		if (error.code === 'ERROR_CEREMONY_ABORTED') return null;
		if (error.status && error.status >= 500) return "Couldn't reach Availabliss to set up the passkey. Try again in a moment.";
		return error.message || 'No passkey was added';
	}

	async function remove() {
		if (!removing) return;
		const { error } = await authClient.passkey.deletePasskey({ id: removing.id });
		if (error) {
			toasts.error(error.message ?? "Couldn't remove that passkey");
			return;
		}
		passkeys = passkeys?.filter((p) => p.id !== removing!.id) ?? null;
		toasts.success('Passkey removed');
	}

	/** A friendly default name, e.g. "Mac · Safari". */
	function deviceName(): string {
		const ua = navigator.userAgent;
		const os = /iPhone|iPad/.test(ua) ? 'iPhone' : /Android/.test(ua) ? 'Android' : /Mac/.test(ua) ? 'Mac' : /Windows/.test(ua) ? 'Windows' : 'This device';
		const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : '';
		return browser ? `${os} · ${browser}` : os;
	}
</script>

<GlassPanel as="section" padding="16px" gap={10} aria-labelledby="passkeys-title">
	<div class="head">
		<h2 class="eyebrow" id="passkeys-title">Passkeys</h2>
		<Button size="sm" variant="secondary" icon="plus" loading={adding} onclick={add}>Add a passkey</Button>
	</div>
	<p class="muted small">Sign in with Face ID, Touch ID or your device PIN. No codes needed.</p>
	{#if passkeys === null}
		<div class="loading"><Spinner size={16} /></div>
	{:else}
		{#each passkeys as key, i (key.id)}
			<div class="row" class:first={i === 0}>
				<span class="tile"><Icon name="passkey" size={18} /></span>
				<span class="text">
					<span class="name">{key.name || 'Passkey'}</span>
					<span class="sub">Added {fmt.current.day(new Date(key.createdAt).toISOString())}</span>
				</span>
				<IconButton
					icon="trash"
					variant="plain"
					size={40}
					label="Remove {key.name || 'passkey'}"
					onclick={() => {
						removing = key;
						confirmOpen = true;
					}}
				/>
			</div>
		{:else}
			<p class="muted small none">No passkeys yet.</p>
		{/each}
	{/if}
</GlassPanel>

<ConfirmSheet
	bind:open={confirmOpen}
	title="Remove this passkey?"
	message="You won't be able to sign in with {removing?.name || 'it'} any more. Email codes still work."
	confirmLabel="Remove"
	destructive
	onconfirm={remove}
/>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
	}
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
	}
	.none {
		padding: 4px 0;
	}
</style>
