<script lang="ts">
	import { page } from '$app/state';
	import Orbs from '#lib/components/ui/Orbs.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Button from '#lib/components/ui/Button.svelte';

	const notFound = $derived(page.status === 404);
</script>

<svelte:head><title>{notFound ? 'Not found' : 'Something went wrong'} · Availabliss</title></svelte:head>

<Orbs layout="c" />
<main class="error">
	<div class="card glass">
		<Logo size={56} />
		<h1>{notFound ? "We couldn't find that" : 'Something went wrong'}</h1>
		<p>{notFound ? "It may have been deleted, or you're not in this group." : (page.error?.message ?? 'Please try again.')}</p>
		<Button href="/app" variant="solid">Back to your groups</Button>
	</div>
</main>

<style>
	.error {
		position: relative;
		z-index: 1;
		min-height: 100dvh;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 20px;
	}
	.card {
		max-width: 420px;
		border-radius: 32px;
		padding: 32px 24px;
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 12px;
	}
	h1 {
		font-size: 28px;
		font-weight: 700;
	}
	p {
		color: var(--muted);
		margin-bottom: 8px;
	}
</style>
