<script lang="ts">
	import type { EventReminders } from '#lib/types';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';

	/**
	 * Plan reminders: a push and Activity item the day before and/or an hour before a confirmed
	 * plan you're going to (and the day before an open proposal you haven't replied to). Saves to
	 * the account straight away.
	 */
	interface Props {
		reminders: EventReminders;
	}
	let { reminders }: Props = $props();

	const options: { value: EventReminders; label: string }[] = [
		{ value: 'both', label: 'Both' },
		{ value: 'day', label: 'Day before' },
		{ value: 'hour', label: 'Hour before' },
		{ value: 'off', label: 'Off' }
	];
	const DONE: Record<EventReminders, string> = {
		both: 'You’ll be reminded the day before and an hour before',
		day: 'You’ll be reminded the day before',
		hour: 'You’ll be reminded an hour before',
		off: 'Plan reminders turned off'
	};
	let value = $state<string>((() => reminders)());
	let saved = (() => reminders)();

	async function save(choice: string) {
		const next = choice as EventReminders;
		try {
			await updateMe({ eventReminders: next });
			saved = next;
			toasts.success(DONE[next]);
		} catch (err) {
			value = saved;
			toasts.error(err);
		}
	}
</script>

<GlassPanel as="section" padding="16px" gap={12} aria-labelledby="reminders-title">
	<h2 class="eyebrow" id="reminders-title">Plan reminders</h2>
	<p>Pushes and Activity items before plans you’re going to.</p>
	<SegmentedControl {options} bind:value label="Plan reminders" variant="soft" size="sm" block onchange={save} />
</GlassPanel>

<style>
	p {
		font-size: 13px;
		color: var(--muted);
	}
</style>
