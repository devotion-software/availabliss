<script lang="ts">
	import { inviteSchema } from '#lib/schemas';
	import { createInvite, type Invite } from '#lib/client/groups';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/** Invite people: a shareable link, or an email invite. Admin-only. */
	interface Props {
		groupId: string;
		groupName: string;
	}
	let { groupId, groupName }: Props = $props();

	const fmt = useFormat();
	let invite = $state<Invite | null>(null);
	let creating = $state(false);
	let copied = $state(false);
	let email = $state('');
	let emailError = $state<string | null>(null);
	let sending = $state(false);
	const canShare = typeof navigator !== 'undefined' && 'share' in navigator;

	async function makeLink() {
		creating = true;
		try {
			invite = await createInvite(groupId);
		} catch (err) {
			toasts.error(err);
		} finally {
			creating = false;
		}
	}

	async function copy() {
		if (!invite) return;
		try {
			await navigator.clipboard.writeText(invite.url);
			copied = true;
			setTimeout(() => (copied = false), 2000);
		} catch {
			toasts.error('Couldn’t copy. Select the link and copy it instead.');
		}
	}

	async function share() {
		if (!invite) return;
		try {
			await navigator.share({ title: `Join ${groupName} on Availabliss`, text: `Help us find a time that works: join ${groupName}.`, url: invite.url });
		} catch {
			// Dismissed: nothing to do.
		}
	}

	async function sendEmail(e: SubmitEvent) {
		e.preventDefault();
		const parsed = inviteSchema.safeParse({ email: email.trim() });
		if (!parsed.success || !parsed.data.email) {
			emailError = 'Enter a valid email address';
			return;
		}
		emailError = null;
		sending = true;
		try {
			await createInvite(groupId, parsed.data.email);
			toasts.success(`Invite sent to ${parsed.data.email}`);
			email = '';
		} catch (err) {
			toasts.error(err);
		} finally {
			sending = false;
		}
	}
</script>

<section class="invite glass" aria-labelledby="invite-h">
	<h2 id="invite-h">Invite people</h2>
	<p class="lead">Anyone with the link can join. They'll be asked to connect a calendar or mark busy times.</p>

	{#if invite}
		<div class="link">
			<label class="sr-only" for="invite-url">Invite link</label>
			<input id="invite-url" readonly value={invite.url} onfocus={(e) => e.currentTarget.select()} />
		</div>
		<div class="buttons">
			<Button variant="solid" size="sm" icon={copied ? 'check' : 'copy'} onclick={copy}>{copied ? 'Copied' : 'Copy link'}</Button>
			{#if canShare}<Button variant="secondary" size="sm" icon="share" onclick={share}>Share</Button>{/if}
		</div>
		<span class="expires">Expires {fmt.current.day(invite.expiresAt)}</span>
	{:else}
		<Button variant="accent" icon="link" loading={creating} onclick={makeLink}>Create invite link</Button>
	{/if}

	<form class="email" onsubmit={sendEmail} novalidate>
		<TextField label="Or email an invite" type="email" autocomplete="email" placeholder="friend@example.com" bind:value={email} error={emailError} />
		<Button type="submit" variant="secondary" icon="mail" loading={sending}>Send</Button>
	</form>
</section>

<style>
	.invite {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	h2 {
		font-size: 18px;
		font-weight: 650;
	}
	.lead {
		font-size: 13px;
		color: var(--muted);
	}
	.link input {
		width: 100%;
		height: 48px;
		border-radius: var(--radius-control);
		border: 1px solid var(--line-2);
		background: var(--surface);
		padding: 0 14px;
		font-size: 14px;
		font-weight: 600;
		color: var(--accent-ink);
		text-overflow: ellipsis;
	}
	.buttons {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.buttons :global(.btn) {
		height: 44px;
	}
	.expires {
		font-size: 12px;
		color: var(--muted);
	}
	.email {
		display: flex;
		align-items: flex-start;
		gap: 8px;
		padding-top: 12px;
		border-top: 1px solid var(--line);
	}
	.email > :global(.field) {
		flex: 1;
	}
	.email > :global(.btn) {
		margin-top: 25px;
		height: 48px;
	}
</style>
