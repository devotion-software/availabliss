<script lang="ts">
	import type { Plan } from '#lib/types';
	import { getPlan } from '#lib/client/plan';
	import { upgradeSheet } from '#lib/client/ui-state.svelte';
	import { useFormat } from '#lib/client/format';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';

	/** Your plan and how many groups you've made on it. */
	const fmt = useFormat();
	let plan = $state<Plan | null>(null);

	// Reload whenever the upgrade sheet closes, in case something was bought.
	$effect(() => {
		if (upgradeSheet.open) return;
		getPlan()
			.then((p) => (plan = p))
			.catch(() => {});
	});

	const detail = $derived.by(() => {
		if (!plan) return '';
		if (plan.tier === 'free') return `${plan.groupsOwned} of ${plan.groupLimit} groups created`;
		if (plan.lifetime) return 'Unlimited groups, for life';
		if (plan.proUntil) return `Unlimited groups · ${plan.willRenew ? 'renews' : 'ends'} ${fmt.current.day(plan.proUntil)}`;
		return 'Unlimited groups';
	});
</script>

<GlassPanel padding="16px" gap={12} aria-labelledby="plan-title">
	<div class="head">
		<h2 class="eyebrow" id="plan-title">Plan</h2>
		{#if plan}<StatusPill tone={plan.tier === 'pro' ? 'indigo' : 'neutral'}>{plan.tier === 'pro' ? 'Pro' : 'Free'}</StatusPill>{/if}
	</div>
	<p class="detail">{detail || ' '}</p>
	{#if plan}
		<Button size="sm" variant={plan.tier === 'pro' ? 'secondary' : 'accent'} icon={plan.tier === 'pro' ? undefined : 'sparkle'} onclick={() => upgradeSheet.showFor()}>
			{plan.tier === 'pro' ? 'Manage Pro' : 'Upgrade to Pro'}
		</Button>
	{/if}
</GlassPanel>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.detail {
		font-size: 14px;
		color: var(--muted);
		min-height: 1.4em;
	}
</style>
