<script lang="ts">
	import type { Plan, User } from '#lib/types';
	import { buyOnWeb, getPlan, refreshPlan, storeName, webManagementUrl, webPackages, type WebPackage } from '#lib/client/plan';
	import { FREE_GROUP_LIMIT } from '#lib/plans';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';

	/**
	 * Availabliss Pro: what it adds and how to get it. On the web, purchases go
	 * through RevenueCat Web Billing (Stripe); Pro bought in the apps shows here too.
	 */
	interface Props {
		open: boolean;
		user: Pick<User, 'id' | 'email'>;
		reason?: string | null;
	}
	let { open = $bindable(), user, reason = null }: Props = $props();

	const fmt = useFormat();
	let plan = $state<Plan | null>(null);
	let packages = $state<WebPackage[] | null>(null);
	let loadError = $state<string | null>(null);
	let buying = $state<string | null>(null);
	let managing = $state(false);

	$effect(() => {
		if (!open) return;
		let stale = false;
		plan = null;
		packages = null;
		loadError = null;
		(async () => {
			const p = await getPlan();
			if (stale) return;
			plan = p;
			if (p.tier === 'free' && p.purchase.web) {
				packages = await webPackages(p.purchase.web, user.id);
			}
		})().catch((err) => {
			if (!stale) loadError = err instanceof Error ? err.message : 'Couldn’t load prices';
		});
		return () => {
			stale = true;
		};
	});

	async function buy(pkg: WebPackage) {
		if (!plan?.purchase.web) return;
		buying = pkg.id;
		try {
			if ((await buyOnWeb(plan.purchase.web, user.id, pkg, user.email)) === 'cancelled') return;
			plan = await refreshPlan();
			toasts.success(plan.tier === 'pro' ? 'Welcome to Pro' : 'Payment received. Pro will switch on in a moment.');
			open = false;
		} catch (err) {
			toasts.error(err);
		} finally {
			buying = null;
		}
	}

	async function manage() {
		if (!plan?.purchase.web) return;
		managing = true;
		try {
			const url = await webManagementUrl(plan.purchase.web, user.id);
			if (url) window.open(url, '_blank', 'noopener');
			else toasts.info('There’s nothing to manage here yet.');
		} catch (err) {
			toasts.error(err);
		} finally {
			managing = false;
		}
	}

	const PERKS = [
		{ icon: 'groups', title: 'Unlimited groups', text: `Free accounts can create ${FREE_GROUP_LIMIT}. Joining other people's groups is always free.` },
		{ icon: 'sparkle', title: 'Supports Availabliss', text: 'Everything else stays free for everyone in your groups.' }
	] as const;
</script>

<Sheet bind:open title="Availabliss Pro" description={reason ?? 'Plan with as many groups as you like.'}>
	<div class="body">
		<ul class="perks">
			{#each PERKS as perk (perk.title)}
				<li>
					<span class="perk-icon"><Icon name={perk.icon} size={18} /></span>
					<span class="perk-text"><strong>{perk.title}</strong><span>{perk.text}</span></span>
				</li>
			{/each}
		</ul>

		{#if loadError}
			<p class="note warn" role="alert">{loadError}</p>
		{:else if !plan}
			<div class="loading"><Spinner /></div>
		{:else if plan.tier === 'pro'}
			<div class="status">
				<Icon name="check-circle" size={20} />
				<span>
					<strong>You're on Pro.</strong>
					{#if plan.lifetime}
						It never expires.
					{:else if plan.proUntil}
						{plan.willRenew ? 'Renews' : 'Ends'}
						{fmt.current.day(plan.proUntil)}.
					{/if}
				</span>
			</div>
			{#if storeName(plan.store)}
				<p class="note">Manage your subscription in {storeName(plan.store)} on the device you bought it on.</p>
			{:else if plan.purchase.web && (plan.store === 'stripe' || plan.store === 'rc_billing')}
				<Button variant="secondary" block loading={managing} onclick={manage}>Manage subscription</Button>
			{/if}
		{:else if !plan.purchase.web}
			<p class="note">Upgrading on the web isn't available yet. Upgrade from the Availabliss app on iPhone or Android: Pro follows your account everywhere.</p>
		{:else if packages === null}
			<div class="loading"><Spinner /></div>
		{:else if packages.length === 0}
			<p class="note">Pro isn't on sale just now. Check back soon.</p>
		{:else}
			<div class="packages">
				{#each packages as pkg (pkg.id)}
					<Button variant={pkg.id === '$rc_annual' ? 'accent' : 'secondary'} size="lg" block loading={buying === pkg.id} disabled={buying !== null} onclick={() => buy(pkg)}>
						<span class="pkg"><span>{pkg.label}</span><span class="price">{pkg.price}<small>{pkg.per}</small></span></span>
					</Button>
				{/each}
			</div>
			<p class="fine">Cancel any time. Bought in the iPhone or Android app instead? It works here automatically.</p>
		{/if}
	</div>
</Sheet>

<style>
	.body {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.perks {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.perks li {
		display: flex;
		gap: 12px;
		align-items: flex-start;
	}
	.perk-icon {
		flex: none;
		width: 36px;
		height: 36px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.perk-text {
		display: flex;
		flex-direction: column;
		gap: 2px;
		font-size: 13px;
		color: var(--muted);
	}
	.perk-text strong {
		font-size: 15px;
		color: var(--ink);
	}
	.loading {
		display: grid;
		place-items: center;
		padding: 16px;
	}
	.status {
		display: flex;
		gap: 10px;
		align-items: center;
		padding: 12px 14px;
		border-radius: 16px;
		background: var(--ok-bg);
		color: var(--ok-ink);
		font-size: 14px;
	}
	.note {
		font-size: 13px;
		color: var(--muted);
		line-height: 1.45;
	}
	.note.warn {
		color: var(--warn-ink);
	}
	.packages {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.pkg {
		flex: 1;
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 12px;
	}
	.price {
		font-variant-numeric: tabular-nums;
	}
	.price small {
		font-size: 12px;
		font-weight: 500;
		opacity: 0.8;
	}
	.fine {
		font-size: 12px;
		color: var(--muted);
		text-align: center;
	}
</style>
