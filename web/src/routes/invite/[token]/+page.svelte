<script lang="ts">
	import { goto } from '$app/navigation';
	import { acceptInvite } from '#lib/client/groups';
	import { toasts } from '#lib/client/toast.svelte';
	import { daysBetween, todayIn } from '#lib/client/dates';
	import { useFormat, plural } from '#lib/client/format';
	import Orbs from '#lib/components/ui/Orbs.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';

	let { data } = $props();
	const fmt = useFormat();

	let joining = $state(false);
	const monogram = $derived(
		(data.invite?.groupName ?? '?')
			.split(/\s+/)
			.filter(Boolean)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase())
			.join('')
	);
	const expires = $derived.by(() => {
		if (!data.invite) return '';
		const days = daysBetween(todayIn(fmt.current.timeZone), fmt.current.dayKey(data.invite.expiresAt));
		return days <= 0 ? 'Expires today' : days === 1 ? 'Expires tomorrow' : `Expires ${fmt.current.day(data.invite.expiresAt)}`;
	});

	async function join() {
		joining = true;
		try {
			const group = await acceptInvite(data.token);
			await goto(`/app/groups/${group.id}`, { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			joining = false;
		}
	}
</script>

<svelte:head><title>{data.invite ? `Join ${data.invite.groupName}` : 'Invite'} · Availabliss</title></svelte:head>

<Orbs layout="d" />
<main class="invite">
	<a class="brand" href="/" aria-label="Availabliss home"><Logo size={34} wordmark /></a>

	<section class="glass card">
		{#if data.invite}
			<GroupTile {monogram} color="t1" size={72} />
			<div class="copy">
				<span class="from">{data.invite.inviterName} invited you to</span>
				<h1>{data.invite.groupName}</h1>
				<span class="meta">{plural(data.invite.memberCount, 'member')} · {expires}</span>
			</div>
			<p class="explain">Availabliss finds the times everyone's free. The group only sees when you're busy, never what you're doing.</p>
			{#if data.user}
				<Button variant="accent" size="lg" block loading={joining} onclick={join}>Join group</Button>
			{:else}
				<Button href="/signin?next={encodeURIComponent(`/invite/${data.token}`)}" size="lg" block>Sign in to join</Button>
				<span class="hint">New here? Signing in creates your account.</span>
			{/if}
		{:else}
			<EmptyState
				icon="link"
				title={data.problem === 'gone' ? 'This invite has expired' : "We couldn't open this invite"}
				message={data.problem === 'gone'
					? 'Ask whoever sent it for a fresh link.'
					: 'Check your connection and try again.'}
			>
				{#snippet actions()}
					<Button href={data.user ? '/app' : '/'} variant="secondary">{data.user ? 'Go to your groups' : 'About Availabliss'}</Button>
				{/snippet}
			</EmptyState>
		{/if}
	</section>
</main>

<style>
	.invite {
		position: relative;
		z-index: 1;
		min-height: 100dvh;
		max-width: 460px;
		margin: 0 auto;
		padding: max(28px, env(safe-area-inset-top)) var(--gutter) 32px;
		display: flex;
		flex-direction: column;
		gap: 32px;
	}
	.brand {
		align-self: flex-start;
		text-decoration: none;
		border-radius: 12px;
	}
	.card {
		margin: auto 0;
		border-radius: 32px;
		padding: 28px 22px 22px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 18px;
		text-align: center;
		animation: rise 0.5s var(--ease-spring) both;
	}
	.copy {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.from {
		font-size: 14px;
		color: var(--muted);
	}
	h1 {
		font-size: 32px;
		font-weight: 750;
	}
	.meta {
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.explain {
		font-size: 14px;
		color: var(--muted);
		max-width: 32ch;
	}
	.hint {
		font-size: 12px;
		color: var(--muted);
	}
	@keyframes rise {
		from {
			transform: translateY(24px);
			opacity: 0;
		}
	}
</style>
