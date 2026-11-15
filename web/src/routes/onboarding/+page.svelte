<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { detectRegion } from '#lib/client/regions';
	import { updateMe } from '#lib/client/me';
	import { browserTimeZone } from '#lib/client/dates';
	import { toasts } from '#lib/client/toast.svelte';
	import { signOutAndLeave } from '#lib/client/session';
	import Button from '#lib/components/ui/Button.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import StepDots from '#lib/components/onboarding/StepDots.svelte';
	import RegionFields from '#lib/components/region/RegionFields.svelte';
	import { regionFromUser, regionPatch, type RegionValue } from '#lib/components/region/region';

	let { data } = $props();

	// The form owns its copy; it's seeded once from the saved user.
	let region = $state<RegionValue>(regionFromUser((() => data.user)()));
	// Email sign-up creates accounts without a name, so ask for one here.
	let name = $state((() => data.user.name)());
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
		if (!name.trim()) return;
		busy = true;
		try {
			await updateMe({ ...regionPatch(region), name: name.trim() });
			await invalidateAll();
			await goto('/onboarding/calendars');
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>About you · Availabliss</title></svelte:head>

<div class="top">
	<Logo size={34} />
	<StepDots step={1} total={data.steps} />
</div>

<div class="intro">
	<h1>About you</h1>
	<p>Your name is how your groups see you. Your region sets bank holidays, school holidays and the time zone we show times in.</p>
</div>

<form class="form" onsubmit={next}>
	<GlassPanel as="section" padding="16px">
		<TextField label="Your name" placeholder="First and last name" bind:value={name} autocomplete="name" maxlength={80} required />
	</GlassPanel>
	<GlassPanel as="section" padding="16px">
		<RegionFields bind:value={region} />
	</GlassPanel>
	<span class="spacer"></span>
	<Button type="submit" size="lg" block loading={busy} disabled={!name.trim()}>Continue</Button>
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
