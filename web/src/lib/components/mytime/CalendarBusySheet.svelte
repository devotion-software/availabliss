<script lang="ts">
	import type { BusyInterval } from '#lib/types';
	import { useFormat } from '#lib/client/format';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { ignoreBusy, unignoreBusy } from '#lib/client/busy-ignores';
	import { toasts } from '#lib/client/toast.svelte';

	/**
	 * Busy time synced from a calendar. Only the busy range is ever read (never
	 * the event itself), so it can't be edited here. It can be marked not busy (this
	 * occurrence, or every occurrence of a repeating event), or its calendar can stop
	 * counting as busy.
	 */
	interface Props {
		open: boolean;
		interval: BusyInterval | null;
		/** After marking it (not) busy. */
		onchanged?: () => void;
	}
	let { open = $bindable(), interval, onchanged }: Props = $props();
	let working = $state<'one' | 'all' | 'undo' | null>(null);

	async function ignore(series: boolean) {
		if (!interval?.calendarId) return;
		working = series ? 'all' : 'one';
		try {
			await ignoreBusy({ calendarId: interval.calendarId, eventKey: interval.eventKey ?? '', start: interval.start, end: interval.end, series });
			toasts.success(series ? 'Not busy every time it repeats' : 'Marked not busy');
			open = false;
			onchanged?.();
		} catch (err) {
			toasts.error(err);
		} finally {
			working = null;
		}
	}

	async function undo() {
		if (!interval?.ignoreId) return;
		working = 'undo';
		try {
			await unignoreBusy(interval.ignoreId);
			toasts.success('Counts as busy again');
			open = false;
			onchanged?.();
		} catch (err) {
			toasts.error(err);
		} finally {
			working = null;
		}
	}

	const fmt = useFormat();
	const allDay = $derived(
		!!interval && fmt.current.time(interval.start) === '00:00' && fmt.current.time(interval.end) === '00:00' && interval.start !== interval.end
	);
</script>

<Sheet bind:open title={interval?.ignoreId ? 'Marked not busy' : 'Busy from your calendar'}>
	{#if interval}
		<div class="body">
			<div class="card">
				<span class="icon"><Icon name="calendar-sync" size={20} /></span>
				<span class="text">
					<strong>{interval.label ?? 'A connected calendar'}</strong>
					<span>{fmt.current.when(interval.start, interval.end, allDay)}</span>
				</span>
			</div>
			{#if interval.ignoreId}
				<p class="note">You said this doesn't make you busy, so your groups see you as free then.</p>
				<Button block loading={working === 'undo'} disabled={!!working} onclick={undo}>Count as busy again</Button>
			{:else}
				<p class="note">Doesn't really make you busy? Your groups will see you as free then.</p>
				<Button block loading={working === 'one'} disabled={!!working} onclick={() => ignore(false)}
					>{interval.recurring ? 'Not busy: just this one' : 'Mark as not busy'}</Button
				>
				{#if interval.recurring}
					<Button variant="secondary" block loading={working === 'all'} disabled={!!working} onclick={() => ignore(true)}
						>Not busy: every time it repeats</Button
					>
				{/if}
			{/if}
			<p class="note">
				Availabliss only reads when you're busy, never what the event is, so it can't be edited here. Change it in your calendar and it updates
				here automatically, or choose which calendars count as busy.
			</p>
			<Button href="/app/calendars" variant="secondary" block icon="calendar-sync" onclick={() => (open = false)}>Calendar settings</Button>
		</div>
	{/if}
</Sheet>

<style>
	.body {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.card {
		display: flex;
		gap: 12px;
		align-items: center;
		padding: 12px 14px;
		border-radius: 16px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.icon {
		flex: none;
		width: 38px;
		height: 38px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--busy-bg);
		border: 1px solid var(--busy-line);
	}
	.text {
		display: flex;
		flex-direction: column;
		gap: 2px;
		font-size: 13px;
		color: var(--muted);
	}
	.text strong {
		font-size: 15px;
		color: var(--ink);
	}
	.note {
		font-size: 13px;
		line-height: 1.45;
		color: var(--muted);
	}
</style>
