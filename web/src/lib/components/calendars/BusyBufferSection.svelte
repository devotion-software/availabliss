<script lang="ts">
	import { BUSY_BUFFER_MINUTES, type BusyBufferMinutes } from '#lib/types';
	import { busyBufferLabel } from '#lib/busy-buffer';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';

	/**
	 * "Time around busy time": minutes counted as busy before and after everything in your
	 * calendars and My time, so a free slot doesn't start the minute a meeting ends. Saves to the
	 * account straight away; it only changes which times look free to your groups.
	 */
	interface Props {
		minutes: BusyBufferMinutes;
	}
	let { minutes }: Props = $props();

	const SHORT: Record<BusyBufferMinutes, string> = { 0: 'None', 15: '15 min', 30: '30 min', 60: '1 hr', 120: '2 hr' };
	// The segmented control works in strings.
	const options = BUSY_BUFFER_MINUTES.map((m) => ({ value: String(m), label: SHORT[m] }));
	let value = $state(String((() => minutes)()));
	let saved = (() => minutes)();

	async function save(choice: string) {
		const next = Number(choice) as BusyBufferMinutes;
		try {
			await updateMe({ busyBufferMinutes: next });
			saved = next;
			toasts.success(next ? `Groups will see you busy ${busyBufferLabel(next)} either side` : 'No time added around busy times');
		} catch (err) {
			value = String(saved);
			toasts.error(err);
		}
	}
</script>

<section class="glass buffer" aria-labelledby="buffer-title">
	<h2 id="buffer-title">Time around busy times</h2>
	<p>Counts you as busy for a little longer before and after each event, so nobody plans something the minute you're free.</p>
	<SegmentedControl {options} bind:value label="Time around busy times" variant="soft" size="sm" block onchange={save} />
</section>

<style>
	.buffer {
		border-radius: 20px;
		padding: 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	h2 {
		font-family: var(--font-body);
		letter-spacing: 0;
		font-size: 14px;
		font-weight: 600;
	}
	p {
		margin-top: -6px;
		font-size: 13px;
		color: var(--muted);
	}
</style>
