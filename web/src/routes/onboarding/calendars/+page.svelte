<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import type { CalendarConnection } from '#lib/types';
	import { updateMe } from '#lib/client/me';
	import { listConnections } from '#lib/client/calendars';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import ConnectionSection from '#lib/components/calendars/ConnectionSection.svelte';
	import ConnectButton from '#lib/components/calendars/ConnectButton.svelte';
	import ConnectCalendarSheet from '#lib/components/calendars/ConnectCalendarSheet.svelte';
	import { reportOAuthResult } from '#lib/components/calendars/oauth-result';

	let { data } = $props();

	let connections = $state<CalendarConnection[]>([]);
	$effect.pre(() => {
		connections = data.connections;
	});
	let sheetOpen = $state(false);
	let finishing = $state<'continue' | 'manual' | null>(null);

	$effect(() => {
		// Newly connected calendars may take a moment to list; refetch after the round trip.
		if (reportOAuthResult(page.url)) listConnections().then((c) => (connections = c)).catch(() => {});
	});

	function replace(updated: CalendarConnection) {
		connections = connections.map((c) => (c.id === updated.id ? updated : c));
	}

	async function finish(kind: 'continue' | 'manual') {
		finishing = kind;
		try {
			await updateMe({ onboarded: true });
			await goto(kind === 'manual' ? '/app/my-time' : '/app', { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			finishing = null;
		}
	}
</script>

<svelte:head><title>Your calendars · Availabliss</title></svelte:head>

<div class="top">
	<IconButton href="/onboarding" icon="chevron-left" label="Back to your region" />
	<StepDots step={2} total={2} />
</div>

<div class="intro">
	<h1>Which calendars mean you're busy?</h1>
	<p>Events in these calendars block out your time. Only "busy" is shared, never the details.</p>
</div>

{#each connections as connection (connection.id)}
	<ConnectionSection {connection} returnTo="/onboarding/calendars" onchange={replace} />
{/each}

<ConnectButton first={connections.length === 0} onclick={() => (sheetOpen = true)} />

<div class="glass region">
	<Icon name="map-pin" size={22} class="pin" />
	<div class="text">
		<span class="place">{data.regionLabel?.place ?? 'No region set'}</span>
		<span class="school">{data.regionLabel?.school ? `School holidays: ${data.regionLabel.school}` : 'No school holidays chosen'}</span>
	</div>
	<Button href="/onboarding" variant="ghost" size="sm">Change</Button>
</div>

<span class="spacer"></span>
<Button size="lg" block loading={finishing === 'continue'} disabled={!!finishing} onclick={() => finish('continue')}>Continue</Button>
<button class="manual" disabled={!!finishing} onclick={() => finish('manual')}>No calendar? Mark busy times yourself</button>

<ConnectCalendarSheet bind:open={sheetOpen} redirect="/onboarding/calendars" onconnected={(c) => (connections = [...connections, c])} />

<style>
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.intro {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	h1 {
		font-size: 30px;
		font-weight: 700;
		line-height: 1.05;
	}
	.intro p {
		font-size: 14px;
		line-height: 1.45;
		color: var(--muted);
	}
	.region {
		border-radius: 20px;
		padding: 12px 8px 12px 14px;
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.region :global(.pin) {
		color: var(--ok-ink);
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.place {
		font-size: 14px;
		font-weight: 600;
	}
	.school {
		font-size: 12px;
		color: var(--muted);
	}
	.spacer {
		flex: 1;
	}
	.manual {
		margin-top: -6px;
		min-height: 44px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
	}
	.manual:hover {
		color: var(--accent-ink);
	}
</style>
