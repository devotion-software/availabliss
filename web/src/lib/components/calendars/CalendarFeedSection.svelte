<script lang="ts">
	import { onMount } from 'svelte';
	import { getCalendarFeed, resetCalendarFeed, type CalendarFeed } from '#lib/client/calendars';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';

	/**
	 * "Subscribe to your plans": a secret webcal link to every group's plans, for Apple Calendar,
	 * Outlook or Google Calendar. Loaded once the page is up (the link is made the first time).
	 */
	let feed = $state<CalendarFeed | null>(null);
	let failed = $state(false);
	let confirmOpen = $state(false);

	const googleHref = $derived(feed ? `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(feed.webcalUrl)}` : undefined);

	onMount(() => {
		getCalendarFeed()
			.then((f) => (feed = f))
			.catch(() => (failed = true));
	});

	async function copy() {
		if (!feed) return;
		try {
			await navigator.clipboard.writeText(feed.url);
			toasts.success('Link copied');
		} catch {
			toasts.error('Couldn’t copy the link. Try again.');
		}
	}

	async function reset() {
		try {
			feed = await resetCalendarFeed();
			toasts.success('New calendar link made. Subscribe again with it.');
		} catch (err) {
			toasts.error(err);
		}
	}
</script>

<section class="glass feed" aria-labelledby="feed-title">
	<h2 id="feed-title">Subscribe to your plans</h2>
	<p>Your plans from every group in your own calendar. Changes and cancellations follow automatically.</p>
	{#if feed}
		<div class="actions">
			<Button variant="secondary" size="sm" icon="calendar" block href={feed.webcalUrl}>Apple Calendar / Outlook</Button>
			<Button variant="secondary" size="sm" icon="external" block href={googleHref} target="_blank" rel="noopener">Google Calendar</Button>
			<Button variant="ghost" size="sm" icon="copy" block onclick={copy}>Copy link</Button>
		</div>
		<button type="button" class="reset" onclick={() => (confirmOpen = true)}>Reset link</button>
	{:else if failed}
		<p class="status">Couldn’t load your calendar link. Refresh to try again.</p>
	{:else}
		<p class="status">Getting your link…</p>
	{/if}
</section>

<ConfirmSheet
	bind:open={confirmOpen}
	title="Reset your calendar link?"
	message="Calendars subscribed with the old link stop updating. You'll need to subscribe again."
	confirmLabel="Reset link"
	destructive
	onconfirm={reset}
/>

<style>
	.feed {
		border-radius: 20px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	h2 {
		font-family: var(--font-body);
		letter-spacing: 0;
		font-size: 14px;
		font-weight: 600;
	}
	p {
		margin-top: -6px;
		font-size: 13px;
		color: var(--muted);
	}
	.status {
		margin-top: 0;
	}
	.actions {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.reset {
		align-self: flex-start;
		padding: 2px 0;
		border: 0;
		background: none;
		font: inherit;
		font-size: 13px;
		color: var(--muted);
		text-decoration: underline;
		text-underline-offset: 2px;
		cursor: pointer;
	}
	.reset:hover {
		color: var(--ink);
	}
</style>
