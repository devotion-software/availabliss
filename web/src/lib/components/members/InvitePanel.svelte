<script lang="ts">
	import { z } from 'zod';
	import type { GroupInvite } from '#lib/types';
	import { createInvite, listInvites, revokeInvite } from '#lib/client/groups';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/**
	 * Invite people, two ways: a link anyone can use, or invites emailed to particular people that
	 * only they can accept. Lists the invites still live, with Revoke. Admin-only.
	 */
	interface Props {
		groupId: string;
		groupName: string;
	}
	let { groupId, groupName }: Props = $props();

	const fmt = useFormat();
	let invites = $state<GroupInvite[]>([]);
	let creating = $state(false);
	let copied = $state(false);
	let emailsText = $state('');
	let emailError = $state<string | null>(null);
	let sending = $state(false);
	const canShare = typeof navigator !== 'undefined' && 'share' in navigator;

	// The newest link anyone can use.
	const link = $derived(invites.find((i) => i.email === null) ?? null);

	$effect(() => {
		const id = groupId;
		listInvites(id)
			.then((list) => id === groupId && (invites = list))
			.catch(() => {});
	});

	async function makeLink() {
		creating = true;
		try {
			const made = await createInvite(groupId);
			invites = [...made.invites, ...invites];
		} catch (err) {
			toasts.error(err);
		} finally {
			creating = false;
		}
	}

	async function copy() {
		if (!link) return;
		try {
			await navigator.clipboard.writeText(link.url);
			copied = true;
			setTimeout(() => (copied = false), 2000);
		} catch {
			toasts.error('Couldn’t copy. Select the link and copy it instead.');
		}
	}

	async function share() {
		if (!link) return;
		try {
			await navigator.share({ title: `Join ${groupName} on Availabliss`, text: `Help us find a time that works: join ${groupName}.`, url: link.url });
		} catch {
			// Dismissed: nothing to do.
		}
	}

	/** "a@x.com, b@y.com" (commas, spaces or new lines) → the addresses, or the first bad one. */
	function parseEmails(text: string): { emails: string[]; bad: string | null } {
		const parts = text.split(/[\s,;]+/).map((p) => p.trim()).filter(Boolean);
		const bad = parts.find((p) => !z.email().safeParse(p).success) ?? null;
		return { emails: [...new Set(parts.map((p) => p.toLowerCase()))], bad };
	}

	async function sendEmails(e: SubmitEvent) {
		e.preventDefault();
		const { emails, bad } = parseEmails(emailsText);
		if (bad) return void (emailError = `“${bad}” isn’t a valid email address`);
		if (!emails.length) return void (emailError = 'Enter at least one email address');
		if (emails.length > 20) return void (emailError = 'Invite up to 20 people at a time');
		emailError = null;
		sending = true;
		try {
			const made = await createInvite(groupId, emails);
			invites = [...made.invites, ...invites.filter((i) => !emails.includes(i.email ?? ''))];
			toasts.success(emails.length === 1 ? `Invite sent to ${emails[0]}` : `Invites sent to ${emails.length} people`);
			emailsText = '';
		} catch (err) {
			toasts.error(err);
		} finally {
			sending = false;
		}
	}

	async function revoke(invite: GroupInvite) {
		try {
			await revokeInvite(groupId, invite.id);
			invites = invites.filter((i) => i.id !== invite.id);
			toasts.success(invite.email ? `Invite for ${invite.email} revoked` : 'Link revoked: it no longer works');
		} catch (err) {
			toasts.error(err);
		}
	}

	const expiry = (i: GroupInvite) => `expires ${fmt.current.dayLong(i.expiresAt)}`;
</script>

<section class="invite glass" aria-labelledby="invite-h">
	<h2 id="invite-h">Invite people</h2>

	<div class="way">
		<h3>Share a link</h3>
		<p class="lead">Anyone with the link can join.</p>
		{#if link}
			<div class="link">
				<label class="sr-only" for="invite-url">Invite link</label>
				<input id="invite-url" readonly value={link.url} onfocus={(e) => e.currentTarget.select()} />
			</div>
			<div class="buttons">
				<Button variant="solid" size="sm" icon={copied ? 'check' : 'copy'} onclick={copy}>{copied ? 'Copied' : 'Copy link'}</Button>
				{#if canShare}<Button variant="secondary" size="sm" icon="share" onclick={share}>Share</Button>{/if}
			</div>
		{:else}
			<Button variant="accent" icon="link" loading={creating} onclick={makeLink}>Create invite link</Button>
		{/if}
	</div>

	<form class="way emails" onsubmit={sendEmails} novalidate>
		<h3>Or limit to these emails</h3>
		<p class="lead">We'll email each person their own invite. Only someone signed in with that address can join.</p>
		<TextField
			label="Email addresses"
			type="text"
			autocomplete="off"
			placeholder="sam@example.com, priya@example.com"
			bind:value={emailsText}
			error={emailError}
		/>
		<Button type="submit" variant="secondary" icon="mail" loading={sending}>Send invites</Button>
	</form>

	{#if invites.length}
		<div class="pending">
			<h3>Invites</h3>
			<ul>
				{#each invites as i (i.id)}
					<li>
						<span class="who">
							<span class="name">{i.email ?? 'Invite link'}</span>
							<span class="sub">{i.email ? `Invited · invite ${expiry(i)}` : `Anyone with the link · ${expiry(i)}`}</span>
						</span>
						<IconButton icon="x" label={i.email ? `Revoke the invite for ${i.email}` : 'Revoke this link'} variant="plain" size={36} onclick={() => revoke(i)} />
					</li>
				{/each}
			</ul>
		</div>
	{/if}
</section>

<style>
	.invite {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	h2 {
		font-size: 18px;
		font-weight: 650;
	}
	h3 {
		font-size: 14px;
		font-weight: 650;
	}
	.way {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.emails {
		padding-top: 14px;
		border-top: 1px solid var(--line);
	}
	.emails > :global(.btn) {
		align-self: flex-start;
	}
	.lead {
		font-size: 13px;
		color: var(--muted);
		margin-top: -6px;
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
	.pending {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding-top: 14px;
		border-top: 1px solid var(--line);
	}
	ul {
		list-style: none;
		display: flex;
		flex-direction: column;
	}
	li {
		display: flex;
		align-items: center;
		gap: 8px;
		min-height: 52px;
	}
	li + li {
		border-top: 1px solid var(--line);
	}
	.who {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.name {
		font-size: 14px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
</style>
