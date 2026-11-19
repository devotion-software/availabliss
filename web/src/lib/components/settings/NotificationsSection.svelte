<script lang="ts">
	import { NOTIFICATION_CATEGORIES, type NotificationCategory, type NotificationSettings } from '#lib/types';
	import {
		CATEGORY_LABELS,
		getNotificationSettings,
		updateNotificationSettings,
		type NotificationSettingsPatch
	} from '#lib/client/notification-settings';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/**
	 * Which pushes you get: a switch per category, and quiet hours (in your own time zone). Saves
	 * straight away; Activity still lists everything. Muting a group lives in the group's More menu.
	 */
	const DEFAULT_QUIET = { from: '22:00', to: '07:00' };

	let settings = $state<NotificationSettings | null>(null);
	let failed = $state(false);
	// The quiet-hours times stay put while the switch is off, so turning it back on keeps them.
	let quietFrom = $state(DEFAULT_QUIET.from);
	let quietTo = $state(DEFAULT_QUIET.to);

	function adopt(next: NotificationSettings) {
		settings = next;
		if (next.quietHours) {
			quietFrom = next.quietHours.from;
			quietTo = next.quietHours.to;
		}
	}

	$effect(() => {
		getNotificationSettings()
			.then(adopt)
			.catch(() => (failed = true));
	});

	/** Applies the change optimistically, then saves; puts it back on error. */
	async function save(patch: NotificationSettingsPatch) {
		if (!settings) return;
		const before = $state.snapshot(settings) as NotificationSettings;
		const beforeTimes = { from: quietFrom, to: quietTo };
		settings = { ...settings, ...patch };
		try {
			adopt(await updateNotificationSettings(patch));
		} catch (err) {
			settings = before;
			quietFrom = beforeTimes.from;
			quietTo = beforeTimes.to;
			toasts.error(err);
		}
	}

	function setCategory(category: NotificationCategory, on: boolean) {
		if (!settings) return;
		const off = on ? settings.off.filter((c) => c !== category) : [...settings.off, category];
		save({ off: NOTIFICATION_CATEGORIES.filter((c) => off.includes(c)) });
	}

	// Like the times, kept while quiet hours are off.
	let catchUp = $state(false);
	$effect(() => {
		if (settings?.quietHours) catchUp = settings.quietHours.catchUp;
	});

	function setQuiet(on: boolean) {
		save({ quietHours: on ? { from: quietFrom || DEFAULT_QUIET.from, to: quietTo || DEFAULT_QUIET.to, catchUp } : null });
	}

	function saveTimes() {
		if (!settings?.quietHours || !quietFrom || !quietTo) return;
		if (quietFrom === settings.quietHours.from && quietTo === settings.quietHours.to) return;
		save({ quietHours: { from: quietFrom, to: quietTo, catchUp: settings.quietHours.catchUp } });
	}

	function setCatchUp(on: boolean) {
		if (!settings?.quietHours) return;
		save({ quietHours: { ...settings.quietHours, catchUp: on } });
	}
</script>

<GlassPanel as="section" padding="16px" gap={12} aria-labelledby="notifications-title">
	<h2 class="eyebrow" id="notifications-title">Notifications</h2>
	<p class="hint">Choose which pushes you get. Activity still shows everything.</p>
	{#if settings}
		<ul class="rows">
			{#each NOTIFICATION_CATEGORIES as category (category)}
				<li class="row">
					<span class="label" id="notify-{category}">{CATEGORY_LABELS[category]}</span>
					<Toggle checked={!settings.off.includes(category)} labelledby="notify-{category}" onchange={(on) => setCategory(category, on)} />
				</li>
			{/each}
		</ul>

		<div class="quiet">
			<div class="row">
				<span class="text">
					<span class="label" id="notify-quiet">Quiet hours</span>
					<span class="hint">No pushes during these hours (they're still in Activity)</span>
				</span>
				<Toggle checked={settings.quietHours !== null} labelledby="notify-quiet" onchange={setQuiet} />
			</div>
			{#if settings.quietHours}
				<div class="times">
					<TextField label="From" type="time" bind:value={quietFrom} onchange={saveTimes} />
					<TextField label="Until" type="time" bind:value={quietTo} onchange={saveTimes} />
				</div>
				<div class="row">
					<span class="text">
						<span class="label" id="notify-catch-up">Catch me up afterwards</span>
						<span class="hint">One push when quiet hours end, if anything came in that you haven't seen</span>
					</span>
					<Toggle checked={settings.quietHours.catchUp} labelledby="notify-catch-up" onchange={setCatchUp} />
				</div>
			{/if}
		</div>
	{:else if failed}
		<p class="hint">Couldn't load your notification settings. Try again later.</p>
	{:else}
		<p class="hint">Loading…</p>
	{/if}
</GlassPanel>

<style>
	.hint {
		font-size: 13px;
		color: var(--muted);
	}
	.rows {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 44px;
	}
	.rows .row + .row {
		border-top: 1px solid var(--line);
	}
	.label {
		font-size: 14px;
		font-weight: 600;
	}
	.text {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.text .hint {
		font-size: 12px;
	}
	.quiet {
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding-top: 10px;
		border-top: 1px solid var(--line);
	}
	.times {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
</style>
