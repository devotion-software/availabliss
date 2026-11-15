<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import InvitePanel from '#lib/components/members/InvitePanel.svelte';

	/** Step 4 of the sign-up wizard: invite people to the group you just made. */
	let { data } = $props();
	const groupId = $derived(data.group?.id ?? page.url.searchParams.get('group') ?? '');
	let finishing = $state(false);

	async function done() {
		finishing = true;
		try {
			await updateMe({ onboarded: true });
			await goto(groupId ? `/app/groups/${groupId}` : '/app', { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			finishing = false;
		}
	}
</script>

<svelte:head><title>Invite people · Availabliss</title></svelte:head>

<div class="top">
	<StepDots step={4} total={data.steps} />
</div>

<div class="intro">
	<h1>Invite people{data.group ? ` to ${data.group.name}` : ''}</h1>
	<p>Once they've added their busy times, you'll see when everyone's free.</p>
</div>

{#if groupId}
	<InvitePanel {groupId} groupName={data.group?.name ?? 'my group'} />
{/if}

<span class="spacer"></span>
<Button size="lg" block loading={finishing} onclick={done}>Done</Button>

<style>
	.top {
		display: flex;
		justify-content: flex-end;
		min-height: 44px;
		align-items: center;
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
		color: var(--muted);
	}
	.spacer {
		flex: 1;
	}
</style>
