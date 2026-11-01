<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { detectRegion } from '#lib/client/regions';
	import { updateMe } from '#lib/client/me';
	import { browserTimeZone } from '#lib/client/dates';
	import { toasts } from '#lib/client/toast.svelte';
	import { signOutAndLeave } from '#lib/client/session';
	import Button from '#lib/components/ui/Button.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import RegionFields from '#lib/components/region/RegionFields.svelte';
	import { regionFromUser, regionPatch, type RegionValue } from '#lib/components/region/region';

	let { data } = $props();

	// The form owns its copy; it's seeded once from the saved user.
	let region = $state<RegionValue>(regionFromUser((() => data.user)()));
	let busy = $state(false);

	// First visit: fill in what we can guess from the browser and the request.
	$effect(() => {
		if (data.user.country) return;
		region.timezone = browserTimeZone();
		detectRegion()
			.then((d) => {
				if (region.country) return;
				region.country = d.country ?? '';
				region.subdivision = d.subdivision ?? '';
				if (d.timezone) region.timezone = d.timezone;
			})
			.catch(() => {});
	});

	async function next(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		try {
			await updateMe(regionPatch(region));
			await invalidateAll();
			await goto('/onboarding/calendars');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>Where are you? · Availabliss</title></svelte:head>

<div class="top">
	<Logo size={34} />
	<StepDots step={1} total={2} />
</div>

<div class="intro">
	<h1>Where are you?</h1>
	<p>We use this for bank holidays, school holidays and to show times in your zone.</p>
</div>

<form class="form" onsubmit={next}>
	<GlassPanel as="section" padding="16px">
		<RegionFields bind:value={region} />
	</GlassPanel>
	<span class="spacer"></span>
	<Button type="submit" size="lg" block loading={busy}>Continue</Button>
	<button type="button" class="link" onclick={signOutAndLeave}>Not you? Sign out</button>
</form>

<style>
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 44px;
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
	.form {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.spacer {
		flex: 1;
	}
	.link {
		min-height: 44px;
		font-size: 14px;
		font-weight: 600;
		color: var(--muted);
	}
</style>
