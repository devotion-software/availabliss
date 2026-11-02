<script lang="ts">
	import { page } from '$app/state';
	import type { CalendarConnection } from '#lib/types';
	import { listConnections } from '#lib/client/calendars';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
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

	$effect(() => {
		if (reportOAuthResult(page.url)) listConnections().then((c) => (connections = c)).catch(() => {});
	});

	const replace = (updated: CalendarConnection) => (connections = connections.map((c) => (c.id === updated.id ? updated : c)));
	const remove = (id: string) => (connections = connections.filter((c) => c.id !== id));
</script>

<svelte:head><title>Calendars · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader
	title="Calendars"
	subtitle="Choose which calendars mean you're busy"
	backHref="/app/settings"
	backLabel="Back to your settings"
	crumbs={[{ label: 'You', href: '/app/settings' }, { label: 'Calendars' }]}
/>

	<div class="layout">
		<div class="list">
			{#each connections as connection (connection.id)}
				<ConnectionSection {connection} manage onchange={replace} onremove={remove} />
			{:else}
				<div class="glass empty">
					<EmptyState
						icon="calendar"
						title="No calendars connected"
						message="Connect one and your busy times fill in automatically. Or block time yourself in My time."
					>
						{#snippet actions()}
							<Button variant="accent" icon="plus" onclick={() => (sheetOpen = true)}>Connect a calendar</Button>
							<Button variant="secondary" href="/app/my-time">Mark busy times yourself</Button>
						{/snippet}
					</EmptyState>
				</div>
			{/each}
			{#if connections.length}
				<ConnectButton onclick={() => (sheetOpen = true)} />
			{/if}
		</div>

		<aside class="glass note">
			<Icon name="shield" size={20} />
			<div>
				<h2>What your groups see</h2>
				<p>Only that you're busy, never event titles, places or who you're with. Switch a calendar off and its events stop counting.</p>
			</div>
		</aside>
	</div>
</div>

<ConnectCalendarSheet bind:open={sheetOpen} redirect="/app/calendars" onconnected={(c) => (connections = [...connections, c])} />

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.layout {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.list {
		display: flex;
		flex-direction: column;
		gap: 12px;
		min-width: 0;
	}
	.empty {
		border-radius: 24px;
	}
	.note {
		border-radius: 20px;
		padding: 14px;
		display: flex;
		gap: 12px;
		color: var(--ok-ink);
	}
	.note h2 {
		font-family: var(--font-body);
		letter-spacing: 0;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
	}
	.note p {
		margin-top: 2px;
		font-size: 13px;
		color: var(--muted);
	}
	@media (min-width: 900px) {
		.layout {
			display: grid;
			grid-template-columns: minmax(0, 640px) minmax(240px, 320px);
			align-items: start;
			gap: 20px;
		}
	}
</style>
