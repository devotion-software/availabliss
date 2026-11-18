<script lang="ts">
	import { goto, invalidateAll } from '$app/navigation';
	import { deleteMe, removeAvatar, setAvatar, updateMe } from '#lib/client/me';
	import { resetAppearance, signOutAndLeave } from '#lib/client/session';
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
	import RemindersSection from '#lib/components/settings/RemindersSection.svelte';
	import UsernameField from '#lib/components/settings/UsernameField.svelte';
	import { ApiError } from '#lib/client/api';
	import { normalizeUsername } from '#lib/usernames';

	let { data } = $props();

	// Form copies, seeded from the saved account.
	let name = $state((() => data.user.name)());
	let username = $state((() => data.user.username ?? '')());
	let usernameReady = $state(false);
	let usernameError = $state<string | null>(null);
	let region = $state<RegionValue>(regionFromUser((() => data.user)()));
	let savingName = $state(false);
	let savingRegion = $state(false);
	let nameError = $state<string | null>(null);
	let confirmDelete = $state(false);

	const nameDirty = $derived(name.trim() !== data.user.name);
	const usernameDirty = $derived(normalizeUsername(username) !== (data.user.username ?? ''));

	let uploading = $state(false);
	async function pickPhoto(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (!file) return;
		if (file.size > 10 * 1024 * 1024) return void toasts.error('Photos can be up to 10 MB');
		uploading = true;
		try {
			await setAvatar(file);
			await invalidateAll();
			toasts.success('Photo updated');
		} catch (err) {
			toasts.error(err);
		} finally {
			uploading = false;
		}
	}
	async function removePhoto() {
		uploading = true;
		try {
			await removeAvatar();
			await invalidateAll();
			toasts.success('Photo removed');
		} catch (err) {
			toasts.error(err);
		} finally {
			uploading = false;
		}
	}

	async function saveName(e: SubmitEvent) {
		e.preventDefault();
		if (!name.trim()) {
			nameError = 'Your name can’t be empty';
			return;
		}
		if (usernameDirty && !usernameReady) return;
		savingName = true;
		nameError = null;
		usernameError = null;
		try {
			await updateMe({ ...(nameDirty && { name: name.trim() }), ...(usernameDirty && { username: normalizeUsername(username) }) });
			await invalidateAll();
			username = data.user.username ?? '';
			toasts.success('Profile saved');
		} catch (err) {
			if (err instanceof ApiError && err.code === 'conflict') usernameError = err.message;
			else toasts.error(err);
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
			resetAppearance();
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
					<div class="photo">
						<label class="photo-pick" aria-label={data.user.image ? 'Change your photo' : 'Add a photo'}>
							<Avatar name={name || data.user.email} id={data.user.id} image={data.user.image} size={52} />
							<span class="badge" aria-hidden="true">{#if uploading}…{:else}<Icon name="pencil" size={11} />{/if}</span>
							<input class="sr-only" type="file" accept="image/*" disabled={uploading} onchange={pickPhoto} />
						</label>
						{#if data.user.image}<button type="button" class="link small" disabled={uploading} onclick={removePhoto}>Remove</button>{/if}
					</div>
					<TextField label="Name" bind:value={name} autocomplete="name" maxlength={80} error={nameError} />
				</div>
				<UsernameField bind:value={username} current={data.user.username} bind:ready={usernameReady} error={usernameError} />
				{#if nameDirty || usernameDirty}
					<Button type="submit" size="sm" loading={savingName} disabled={usernameDirty && !usernameReady}>Save</Button>
				{/if}
			</GlassPanel>

			<RemindersSection reminders={data.user.eventReminders} />

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
	.photo {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 2px;
	}
	.photo-pick {
		position: relative;
		cursor: pointer;
		border-radius: 50%;
	}
	.photo-pick:focus-within {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
	.badge {
		position: absolute;
		right: -2px;
		bottom: -2px;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		display: grid;
		place-items: center;
		background: var(--solid);
		color: var(--on-solid);
		border: 2px solid var(--bg);
		font-size: 11px;
	}
	.link {
		color: var(--accent-ink);
		font-weight: 600;
	}
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
