<script lang="ts">
	import Orbs from '#lib/components/ui/Orbs.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import SignInForm from '#lib/components/auth/SignInForm.svelte';

	let { data } = $props();
</script>

<svelte:head><title>Sign in · Availabliss</title></svelte:head>

<Orbs layout="a" />
<main class="signin">
	<div class="hero">
		<a class="brand" href="/" aria-label="Availabliss home"><Logo size={40} wordmark /></a>
		<div class="copy">
			<h1>Find the time that works for everyone.</h1>
			<p>Connect your calendar once. We'll find the overlap, and nobody has to say "what about Thursday?" again.</p>
		</div>
	</div>
	<div class="panel">
		<SignInForm next={data.next} />
	</div>
</main>

<style>
	.signin {
		position: relative;
		z-index: 1;
		min-height: 100dvh;
		max-width: 1180px;
		margin: 0 auto;
		padding: max(56px, env(safe-area-inset-top)) 24px max(28px, env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 40px;
	}
	.hero {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 56px;
	}
	.brand {
		align-self: flex-start;
		text-decoration: none;
		border-radius: 12px;
	}
	.brand :global(.word) {
		font-size: 22px;
	}
	.copy {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	h1 {
		font-size: 44px;
		line-height: 1;
		font-weight: 750;
		letter-spacing: -0.03em;
		max-width: 12ch;
	}
	.copy p {
		font-size: 16px;
		line-height: 1.45;
		color: var(--muted);
		max-width: 320px;
	}
	.panel {
		width: 100%;
	}
	@media (min-width: 900px) {
		.signin {
			flex-direction: row;
			align-items: center;
			gap: 64px;
			padding-top: 48px;
		}
		.hero {
			justify-content: space-between;
			align-self: stretch;
			gap: 0;
			padding: 8px 0 24px;
		}
		.copy {
			margin-bottom: 18vh;
		}
		h1 {
			font-size: clamp(56px, 6vw, 84px);
		}
		.copy p {
			font-size: 18px;
			max-width: 420px;
		}
		.panel {
			flex: 0 0 420px;
		}
	}
</style>
