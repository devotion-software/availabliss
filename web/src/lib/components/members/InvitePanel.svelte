<script lang="ts">
	import { z } from 'zod';
	import type { GroupInvite } from '#lib/types';
	import { createInvite, listInvites, revokeInvite } from '#lib/client/groups';
	import { live } from '#lib/client/live.svelte';
	import { checkUsername } from '#lib/client/usernames';
	import { normalizeUsername, usernameProblem } from '#lib/usernames';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';

	/**
	 * Invite people, two ways: a link anyone can use, or invites for particular people (by email
	 * or username) that only they can accept. Lists the invites still live, with Revoke. Admin-only.
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
	let peopleText = $state('');
	let peopleError = $state<string | null>(null);
	let sending = $state(false);
	const canShare = typeof navigator !== 'undefined' && 'share' in navigator;

	// The newest link anyone can use.
	const link = $derived(invites.find((i) => i.email === null && i.username === null) ?? null);

	$effect(() => {
		const id = groupId;
		const load = () =>
			listInvites(id)
				.then((list) => id === groupId && (invites = list))
				.catch(() => {});
		load();
		// Someone joining uses up their invite: refetch so it leaves the list.
		const off = live.on(['member.changed'], (e) => {
			if (e.groupId === id) load();
		});
		const offReconnect = live.onReconnect(load);
		return () => {
			off();
			offReconnect();
		};
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

	/** An "@" after the first character makes it an email address; anything else is a username. */
	const isEmailish = (p: string) => p.indexOf('@') > 0;

	/** "sam@example.com, @priya" (commas, spaces or new lines) → the emails and usernames, or why one's no good. */
	function parsePeople(text: string): { emails: string[]; usernames: string[]; bad: string | null } {
		const parts = text.split(/[\s,;]+/).map((p) => p.trim()).filter(Boolean);
		const emails = new Set<string>();
		const usernames = new Set<string>();
		for (const p of parts) {
			if (isEmailish(p)) {
				if (!z.email().safeParse(p).success) return { emails: [], usernames: [], bad: `“${p}” isn’t a valid email address` };
				emails.add(p.toLowerCase());
			} else {
				const u = normalizeUsername(p);
				if (usernameProblem(u)) return { emails: [], usernames: [], bad: `“${p}” isn’t an email address or a username` };
				usernames.add(u);
			}
		}
		return { emails: [...emails], usernames: [...usernames], bad: null };
	}

	async function sendInvites(e: SubmitEvent) {
		e.preventDefault();
		const { emails, usernames, bad } = parsePeople(peopleText);
		if (bad) return void (peopleError = bad);
		if (!emails.length && !usernames.length) return void (peopleError = 'Enter an email address or username');
		if (emails.length + usernames.length > 20) return void (peopleError = 'Invite up to 20 people at a time');
		peopleError = null;
		sending = true;
		try {
			// Say who's missing before sending anything.
			const found = await Promise.all(usernames.map((u) => checkUsername(u)));
			const missing = found.find((c) => !c.user);
			if (missing) return void (peopleError = `No one has the username @${missing.username}`);
			const made = await createInvite(groupId, emails, usernames);
			invites = [...made.invites, ...invites.filter((i) => !emails.includes(i.email ?? '') && !usernames.includes(i.username ?? ''))];
			const names = [...emails, ...found.map((c) => c.user!.name.trim() || `@${c.username}`)];
			toasts.success(names.length === 1 ? `Invite sent to ${names[0]}` : `Invites sent to ${names.length} people`);
			peopleText = '';
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
			const who = inviteeLabel(invite);
			toasts.success(who ? `Invite for ${who} revoked` : 'Link revoked: it no longer works');
		} catch (err) {
			toasts.error(err);
		}
	}

	const expiry = (i: GroupInvite) => `expires ${fmt.current.dayLong(i.expiresAt)}`;
	/** Who an invite is for, or null for a link anyone can use. */
	const inviteeLabel = (i: GroupInvite) => i.email ?? (i.username ? `@${i.username}` : null);
</script>

<section class="invite glass" aria-labelledby="invite-h">
	<h2 id="invite-h">Invite people</h2>

	<div class="way">
		<h3>Share a link</h3>
		<p class="lead">One person can join with each link.</p>
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

	<form class="way emails" onsubmit={sendInvites} novalidate>
		<h3>Or invite particular people</h3>
		<p class="lead">Each person gets their own invite, by email or in the app. Only they can join with it.</p>
		<TextField
			label="Emails or usernames"
			type="text"
			autocomplete="off"
			autocapitalize="none"
			spellcheck={false}
			placeholder="sam@example.com, @priya"
			bind:value={peopleText}
			error={peopleError}
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
							<span class="name">{inviteeLabel(i) ?? 'Invite link'}</span>
							<span class="sub">{inviteeLabel(i) ? `Invited · invite ${expiry(i)}` : `Link for one person · ${expiry(i)}`}</span>
						</span>
						<IconButton icon="x" label={inviteeLabel(i) ? `Revoke the invite for ${inviteeLabel(i)}` : 'Revoke this link'} variant="plain" size={36} onclick={() => revoke(i)} />
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
