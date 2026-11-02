<script lang="ts">
	import Orbs from '#lib/components/ui/Orbs.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import SyncRing from '#lib/components/ui/SyncRing.svelte';
	import HeroDemo from '#lib/components/landing/HeroDemo.svelte';
	import StepCard from '#lib/components/landing/StepCard.svelte';

	const year = new Date().getFullYear();
</script>

<svelte:head>
	<title>Availabliss · Find the time that works for everyone</title>
	<meta name="description" content="Connect your calendar once. Availabliss finds when everyone in your group is free, so nobody has to ask 'what about Thursday?' again." />
</svelte:head>

<Orbs layout="a" />

<div class="landing">
	<nav class="nav" aria-label="Main">
		<a href="/" class="brand" aria-label="Availabliss home"><Logo size={34} wordmark /></a>
		<span class="spacer"></span>
		<a class="signin" href="/signin">Sign in</a>
		<Button href="/signin" size="sm" class="nav-cta">Get started</Button>
	</nav>

	<main id="main">
		<section class="hero" aria-labelledby="hero-title">
			<div class="copy">
				<span class="kicker"><span class="pulse" aria-hidden="true"></span>Group plans, minus the group chat chaos</span>
				<h1 id="hero-title">
					Find the time that works for <span class="every">everyone<Logo size={58} animated /></span>
				</h1>
				<p class="lede">Connect your calendar once. We'll find the overlap, and nobody has to say "what about Thursday?" again.</p>
				<div class="ctas">
					<Button href="/signin" variant="accent" size="lg" iconAfter="arrow-right">Get started, it's free</Button>
					<a class="how-link" href="#how">See how it works</a>
				</div>
				<p class="trust"><Icon name="shield" size={16} />Your group only ever sees when you're busy, never what you're doing.</p>
			</div>

			<div class="stage" role="region" aria-label="Try it: an example group calendar">
				<div class="tilt"><HeroDemo /></div>
			</div>
		</section>

		<section id="how" class="how" aria-labelledby="how-title">
			<h2 id="how-title">How it works</h2>
			<ol class="steps">
				<StepCard n={1} title="Connect your calendar once" text="Google, Outlook or iCloud. Pick which calendars mean busy. No calendar? Tap to block times yourself.">
					{#snippet art()}
						{#each [['Personal', 'var(--warn)', true], ['Work', '#5B8DEF', true], ['Birthdays', '#C9A6F5', false]] as [name, color, on] (name)}
							<div class="art-row">
								<i style:background={color as string}></i><span>{name}</span>
								<Toggle checked={on as boolean} label="{name} counts as busy" disabled />
							</div>
						{/each}
					{/snippet}
				</StepCard>
				<StepCard n={2} title="Make a group, say what you're planning" text="Two hours, an evening, a whole weekend, a week away. Bank holidays and school holidays understood.">
					{#snippet art()}
						<div class="art-chips">
							<span>2 hours</span><span>An evening</span><span class="on">A weekend</span><span>A week</span>
						</div>
						<span class="art-sub">Fri from 18:00 → Sun · Nov – Jan</span>
					{/snippet}
				</StepCard>
				<StepCard n={3} title="Pick a time. Everyone's in." text="Only the times that work for everyone show up. Propose one, people tap accept, and it lands in every calendar.">
					{#snippet art()}
						<div class="art-accept">
							<SyncRing value={6} total={6} size={58} stroke={6} tone="ok">
								<b>6/6</b>
							</SyncRing>
							<div>
								<b class="art-when">Fri 13 – Sun 15 Nov</b>
								<span class="art-ok"><Icon name="check" size={12} strokeWidth={3} />Everyone's in</span>
							</div>
						</div>
					{/snippet}
				</StepCard>
			</ol>
		</section>

		<section class="privacy glass" aria-labelledby="privacy-title">
			<span class="shield"><Icon name="lock" size={24} /></span>
			<div>
				<h2 id="privacy-title">Busy, not your business.</h2>
				<p>Availabliss only shares that you're busy. Event names, places and who you're with never leave your calendar. Notes you add to blocked time are for you alone.</p>
			</div>
		</section>

		<section class="final" aria-labelledby="final-title">
			<Logo size={96} animated />
			<h2 id="final-title">Stop asking "what about Thursday?"</h2>
			<p>Start a group in under a minute. Invite with a link.</p>
			<Button href="/signin" size="lg" iconAfter="arrow-right">Get started</Button>
		</section>
	</main>

	<footer class="footer">
		<Logo size={22} wordmark />
		<span>© {year} · Made for people with too many group chats.</span>
		<a href="/signin">Sign in</a>
	</footer>
</div>

<style>
	.landing {
		position: relative;
		z-index: 1;
		max-width: 1240px;
		margin: 0 auto;
		padding: max(20px, env(safe-area-inset-top)) var(--gutter) 28px;
		display: flex;
		flex-direction: column;
		gap: 24px;
		overflow-x: clip;
	}
	.nav {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.brand {
		text-decoration: none;
		border-radius: 12px;
	}
	.spacer {
		flex: 1;
	}
	.signin {
		min-height: 44px;
		display: inline-flex;
		align-items: center;
		padding: 0 12px;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
		text-decoration: none;
	}
	.signin:hover {
		background: var(--surface);
	}
	.nav :global(.nav-cta) {
		display: none;
	}
	main {
		display: flex;
		flex-direction: column;
		gap: 72px;
	}

	/* Hero */
	.hero {
		display: flex;
		flex-direction: column;
		gap: 44px;
		padding-top: 28px;
	}
	.copy {
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.kicker {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 6px 12px;
		border-radius: 999px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font-size: 12px;
		font-weight: 600;
	}
	.pulse {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--accent);
		animation: pulse 2.4s ease-in-out infinite;
	}
	h1 {
		font-size: clamp(44px, 11vw, 92px);
		line-height: 0.96;
		font-weight: 750;
		letter-spacing: -0.035em;
		max-width: 11ch;
	}
	.every {
		white-space: nowrap;
		display: inline-flex;
		align-items: center;
		gap: 0.08em;
	}
	.every :global(.logo) {
		display: inline-flex;
		width: 0.62em;
		height: 0.62em;
		transform: translateY(0.04em);
	}
	.every :global(svg) {
		width: 100%;
		height: 100%;
	}
	.lede {
		font-size: clamp(16px, 2.2vw, 20px);
		line-height: 1.5;
		color: var(--muted);
		max-width: 40ch;
	}
	.ctas {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 10px 18px;
		margin-top: 4px;
	}
	.how-link {
		min-height: 44px;
		display: inline-flex;
		align-items: center;
		font-size: 15px;
		font-weight: 600;
	}
	.trust {
		display: flex;
		align-items: flex-start;
		gap: 8px;
		font-size: 13px;
		color: var(--muted);
		max-width: 44ch;
	}
	.trust :global(svg) {
		margin-top: 2px;
		color: var(--ok-ink);
	}

	.stage {
		position: relative;
		padding: 64px 0 40px;
	}
	.tilt {
		transform: perspective(1600px) rotateX(4deg) rotateY(-6deg) rotateZ(0.6deg);
		transform-origin: center;
		animation: settle 1s var(--ease-out) both;
	}
	/* How it works */
	.how {
		display: flex;
		flex-direction: column;
		gap: 20px;
		scroll-margin-top: 24px;
	}
	.how h2,
	.final h2 {
		font-size: clamp(32px, 6vw, 52px);
		font-weight: 750;
		letter-spacing: -0.03em;
	}
	.steps {
		margin: 0;
		padding: 0;
		display: grid;
		gap: 14px;
	}
	.art-row {
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 14px;
		font-weight: 500;
	}
	.art-row i {
		width: 12px;
		height: 12px;
		border-radius: 4px;
	}
	.art-row span {
		flex: 1;
	}
	.art-row :global(.toggle) {
		transform: scale(0.8);
		transform-origin: right;
		opacity: 1;
	}
	.art-chips {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.art-chips span {
		height: 34px;
		padding: 0 12px;
		border-radius: 999px;
		display: inline-flex;
		align-items: center;
		font-size: 13px;
		font-weight: 600;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.art-chips .on {
		background: var(--solid);
		color: var(--on-solid);
		border-color: var(--solid);
	}
	.art-sub {
		font-size: 12px;
		color: var(--muted);
	}
	.art-accept {
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.art-accept b {
		font-family: var(--font-display);
		font-size: 15px;
	}
	.art-accept div {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.art-when {
		font-size: 18px !important;
		letter-spacing: -0.02em;
	}
	.art-ok {
		align-self: flex-start;
		display: inline-flex;
		align-items: center;
		gap: 5px;
		padding: 4px 9px;
		border-radius: 999px;
		background: var(--ok-bg);
		color: var(--ok-ink);
		font-size: 12px;
		font-weight: 600;
	}

	/* Privacy */
	.privacy {
		border-radius: 32px;
		padding: 24px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.shield {
		width: 52px;
		height: 52px;
		border-radius: 18px;
		background: var(--ok-bg);
		color: var(--ok-ink);
		display: flex;
		align-items: center;
		justify-content: center;
		flex: none;
	}
	.privacy h2 {
		font-size: clamp(26px, 4vw, 36px);
		font-weight: 700;
	}
	.privacy p {
		margin-top: 8px;
		font-size: 16px;
		color: var(--muted);
		max-width: 60ch;
	}

	/* Final CTA */
	.final {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		gap: 16px;
		padding: 24px 0 12px;
	}
	.final p {
		font-size: 16px;
		color: var(--muted);
		margin-bottom: 6px;
	}

	.footer {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 10px 16px;
		padding-top: 20px;
		border-top: 1px solid var(--line-2);
		font-size: 13px;
		color: var(--muted);
	}
	.footer span {
		flex: 1;
	}
	.footer a {
		min-height: 44px;
		display: inline-flex;
		align-items: center;
		font-weight: 600;
	}

	@keyframes pulse {
		50% {
			transform: scale(1.6);
			opacity: 0.45;
		}
	}
	@keyframes drop {
		from {
			transform: translateY(-20px);
			opacity: 0;
		}
	}
	@keyframes rise {
		from {
			transform: translateY(20px);
			opacity: 0;
		}
	}
	@keyframes settle {
		from {
			transform: perspective(1600px) rotateX(14deg) rotateY(-14deg) translateY(30px);
			opacity: 0;
		}
	}

	@media (min-width: 720px) {
		.nav :global(.nav-cta) {
			display: inline-flex;
		}
		.steps {
			grid-template-columns: repeat(3, minmax(0, 1fr));
		}
		.privacy {
			flex-direction: row;
			align-items: center;
			padding: 32px 36px;
			gap: 24px;
		}
	}
	@media (min-width: 1000px) {
		main {
			gap: 112px;
		}
		.hero {
			flex-direction: row;
			align-items: center;
			gap: 48px;
			padding-top: 56px;
			min-height: min(78vh, 760px);
		}
		.copy {
			flex: 1.05;
		}
		.stage {
			flex: 1;
			min-width: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.tilt {
			transform: none;
		}
	}
</style>
