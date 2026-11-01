<script lang="ts">
	import { firstName, useFormat } from '#lib/client/format';
	import { newGroupSheet } from '#lib/client/ui-state.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import EmptyState from '#lib/components/ui/EmptyState.svelte';
	import ProfileMenu from '#lib/components/app/ProfileMenu.svelte';
	import GroupCard from '#lib/components/groups/GroupCard.svelte';
	import UpNextCard from '#lib/components/groups/UpNextCard.svelte';

	let { data } = $props();
	const fmt = useFormat();
	const next = $derived(data.upcoming[0]);
</script>

<svelte:head><title>Your groups · Availabliss</title></svelte:head>

<div class="home">
	<header class="hello">
		<div class="titles">
			<span class="date">{fmt.current.dayLong(new Date())}</span>
			<h1>Hi, {firstName(data.user.name || 'there')}</h1>
		</div>
		<span class="phone-only"><ProfileMenu user={data.user} /></span>
	</header>

	<div class="layout">
		{#if next}
			<div class="side"><UpNextCard event={next} /></div>
		{/if}

		<section class="groups" aria-labelledby="groups-heading">
			<div class="groups-head">
				<h2 id="groups-heading">Your groups</h2>
				<Button variant="glass" size="sm" icon="plus" class="new" onclick={newGroupSheet.show}>New group</Button>
			</div>
			{#if data.groups.length}
				<div class="list">
					{#each data.groups as group (group.id)}
						<GroupCard {group} timeZone={data.user.timezone} />
					{/each}
				</div>
			{:else}
				<div class="glass empty">
					<EmptyState
						title="No groups yet"
						message="Make a group for the people you're trying to get together, then share the invite link."
					>
						{#snippet actions()}
							<Button variant="accent" icon="plus" onclick={newGroupSheet.show}>New group</Button>
						{/snippet}
					</EmptyState>
				</div>
			{/if}
		</section>
	</div>
</div>

<style>
	.home {
		display: flex;
		flex-direction: column;
		gap: 18px;
		padding-top: 8px;
	}
	.hello {
		display: flex;
		align-items: flex-end;
		justify-content: space-between;
	}
	.titles {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.date {
		font-size: 13px;
		font-weight: 500;
		color: var(--muted);
	}
	h1 {
		font-size: 32px;
		font-weight: 700;
		line-height: 1.05;
	}
	.layout {
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.groups {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.groups-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	h2 {
		font-size: 20px;
		font-weight: 650;
	}
	.groups-head :global(.new) {
		height: 36px;
		border-radius: 999px;
		padding: 0 14px;
	}
	.list {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.empty {
		border-radius: 28px;
	}
	@media (min-width: 900px) {
		.phone-only {
			display: none;
		}
		.home {
			padding-top: 12px;
			gap: 24px;
		}
		h1 {
			font-size: 44px;
			font-weight: 750;
		}
		.date {
			font-size: 15px;
		}
		.layout {
			display: grid;
			grid-template-columns: minmax(0, 1fr) 360px;
			align-items: start;
			gap: 24px;
		}
		.side {
			grid-column: 2;
			grid-row: 1;
			position: sticky;
			top: 20px;
		}
		.groups {
			grid-column: 1;
			grid-row: 1;
		}
		.list {
			display: grid;
			grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
			gap: 14px;
		}
		.groups-head :global(.new) {
			display: none;
		}
	}
</style>
