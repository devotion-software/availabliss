<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { updateMe, deleteMe } from '#lib/client/me';
	import { signOutAndLeave } from '#lib/client/session';
	import { toasts } from '#lib/client/toast.svelte';
	import PageHeader from '#lib/components/ui/PageHeader.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import RegionFields from '#lib/components/region/RegionFields.svelte';
	import { regionFromUser, regionPatch, type RegionValue } from '#lib/components/region/region';
	import AppearanceSection from '#lib/components/settings/AppearanceSection.svelte';
	import PasskeysSection from '#lib/components/settings/PasskeysSection.svelte';
	import SignInMethodsSection from '#lib/components/settings/SignInMethodsSection.svelte';
	import PlanSection from '#lib/components/settings/PlanSection.svelte';
	import TemplatesSection from '#lib/components/settings/TemplatesSection.svelte';

	let { data } = $props();

	// Form copies, seeded from the saved account.
	let name = $state((() => data.user.name)());
	let region = $state<RegionValue>(regionFromUser((() => data.user)()));
	let savingName = $state(false);
	let savingRegion = $state(false);
	let nameError = $state<string | null>(null);
	let confirmDelete = $state(false);

	const nameDirty = $derived(name.trim() !== data.user.name);

	async function saveName(e: SubmitEvent) {
		e.preventDefault();
		if (!name.trim()) {
			nameError = 'Your name can’t be empty';
			return;
		}
		savingName = true;
		nameError = null;
		try {
			await updateMe({ name: name.trim() });
			await invalidateAll();
			toasts.success('Name saved');
		} catch (err) {
			toasts.error(err);
		} finally {
			savingName = false;
		}
	}

	async function saveRegion(e: SubmitEvent) {
		e.preventDefault();
		savingRegion = true;
		try {
			await updateMe(regionPatch(region));
			await invalidateAll();
			toasts.success('Region saved');
		} catch (err) {
			toasts.error(err);
		} finally {
			savingRegion = false;
		}
	}

	async function removeAccount() {
		try {
			await deleteMe();
			await goto('/', { invalidateAll: true });
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}

	async function signOut() {
		try {
			await signOutAndLeave();
		} catch (err) {
			toasts.error(err);
		}
	}
</script>

<svelte:head><title>Settings · Availabliss</title></svelte:head>

<div class="page">
	<PageHeader title="You" subtitle={data.user.email} backHref="/app" backLabel="Back to groups" backOnPhoneOnly />

	<div class="columns">
		<div class="col">
			<GlassPanel as="form" padding="16px" gap={12} onsubmit={saveName} aria-labelledby="profile-title">
				<h2 class="eyebrow" id="profile-title">Profile</h2>
				<div class="profile">
					<Avatar name={name || data.user.email} id={data.user.id} image={data.user.image} size={52} />
					<TextField label="Name" bind:value={name} autocomplete="name" maxlength={80} error={nameError} />
				</div>
				{#if nameDirty}<Button type="submit" size="sm" loading={savingName}>Save name</Button>{/if}
			</GlassPanel>

			<div class="appearance">
				<h2 class="section-title">Appearance</h2>
				<AppearanceSection themeMode={data.user.themeMode} colorTheme={data.user.colorTheme} />
			</div>
		</div>

		<div class="col">
			<PlanSection />

			<GlassPanel as="form" padding="16px" gap={12} onsubmit={saveRegion} aria-labelledby="region-title">
				<h2 class="eyebrow" id="region-title">Region & school holidays</h2>
				<p class="small muted">Bank holidays and school term dates come from here.</p>
				<RegionFields bind:value={region} />
				<Button type="submit" size="sm" variant="secondary" loading={savingRegion}>Save region</Button>
			</GlassPanel>

			<TemplatesSection />

			<a class="glass link-row" href="/app/calendars">
				<span class="tile"><Icon name="calendar-sync" size={18} /></span>
				<span class="text">
					<span class="title">Calendars</span>
					<span class="sub">Choose which calendars mean you're busy</span>
				</span>
				<Icon name="chevron-right" size={18} />
			</a>
			<a class="glass link-row" href="/app/my-time">
				<span class="tile"><Icon name="calendar" size={18} /></span>
				<span class="text">
					<span class="title">My time</span>
					<span class="sub">Block busy times by hand</span>
				</span>
				<Icon name="chevron-right" size={18} />
			</a>

			<SignInMethodsSection email={data.user.email} />

			<PasskeysSection />

			<GlassPanel as="section" padding="16px" gap={10} aria-labelledby="account-title">
				<h2 class="eyebrow" id="account-title">Account</h2>
				<Button variant="secondary" icon="logout" block onclick={signOut}>Sign out</Button>
				<Button variant="danger" icon="trash" block onclick={() => (confirmDelete = true)}>Delete account</Button>
			</GlassPanel>

			<p class="version muted">Availabliss v{__APP_VERSION__}</p>
		</div>
	</div>
</div>

<ConfirmSheet
	bind:open={confirmDelete}
	title="Delete your account?"
	message="This removes you from every group and deletes your calendars, busy times and messages. It can't be undone."
	confirmLabel="Delete account"
	destructive
	onconfirm={removeAccount}
/>

<style>
	.page {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.columns,
	.col,
	.appearance {
		display: flex;
		flex-direction: column;
		gap: 14px;
		min-width: 0;
	}
	.appearance {
		gap: 12px;
	}
	.section-title {
		font-size: 20px;
		font-weight: 650;
		padding: 6px 2px 0;
	}
	.profile {
		display: flex;
		align-items: flex-end;
		gap: 12px;
	}
	.profile :global(.field) {
		flex: 1;
	}
	.small {
		font-size: 13px;
	}
	.version {
		font-size: 12px;
		text-align: center;
		padding: 2px 0 8px;
	}
	.link-row {
		border-radius: 20px;
		padding: 10px 14px 10px 10px;
		min-height: 60px;
		display: flex;
		align-items: center;
		gap: 12px;
		color: var(--ink);
		text-decoration: none;
		transition: transform 0.15s ease;
	}
	.link-row:hover {
		color: var(--ink);
		transform: translateY(-1px);
	}
	.tile {
		width: 34px;
		height: 34px;
		border-radius: 11px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		display: flex;
		align-items: center;
		justify-content: center;
		flex: none;
	}
	.text {
		flex: 1;
		display: flex;
		flex-direction: column;
	}
	.title {
		font-size: 14px;
		font-weight: 600;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	@media (min-width: 900px) {
		.columns {
			display: grid;
			grid-template-columns: repeat(2, minmax(0, 1fr));
			gap: 20px;
			align-items: start;
		}
	}
</style>
