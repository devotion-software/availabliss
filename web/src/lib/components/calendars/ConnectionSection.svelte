<script lang="ts">
	import type { CalendarConnection } from '#lib/types';
	import { removeConnection, setCalendarBusy, syncConnection, oauthStartUrl } from '#lib/client/calendars';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import BrandIcon from '#lib/components/ui/BrandIcon.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import ConfirmSheet from '#lib/components/ui/ConfirmSheet.svelte';
	import { FALLBACK_COLORS, PROVIDER_BRAND, PROVIDER_NAME } from './providers';

	/**
	 * One connected account and its calendars, each with a "counts as busy" switch
	 * (Calendars.dc.html). `manage` adds status, Sync now and Remove.
	 */
	interface Props {
		connection: CalendarConnection;
		manage?: boolean;
		/** Where an OAuth reconnect should return to. */
		returnTo?: string;
		onchange: (connection: CalendarConnection) => void;
		onremove?: (id: string) => void;
	}
	let { connection, manage = false, returnTo = '/app/calendars', onchange, onremove }: Props = $props();

	const fmt = useFormat();
	let syncing = $state(false);
	let confirmRemove = $state(false);
	const brand = $derived(PROVIDER_BRAND[connection.provider]);

	async function toggle(calendarId: string, busy: boolean) {
		// Optimistic: flip locally, roll back if the server says no.
		const before = connection;
		onchange({ ...connection, calendars: connection.calendars.map((c) => (c.id === calendarId ? { ...c, busy } : c)) });
		try {
			onchange(await setCalendarBusy(connection.id, calendarId, busy));
		} catch (err) {
			onchange(before);
			toasts.error(err);
		}
	}

	async function sync() {
		syncing = true;
		try {
			onchange(await syncConnection(connection.id));
			toasts.success('Synced');
		} catch (err) {
			toasts.error(err);
		} finally {
			syncing = false;
		}
	}

	async function remove() {
		try {
			await removeConnection(connection.id);
			onremove?.(connection.id);
			toasts.success(`${PROVIDER_NAME[connection.provider]} calendar removed`);
		} catch (err) {
			toasts.error(err);
			throw err;
		}
	}
</script>

<section class="glass conn" aria-label="{PROVIDER_NAME[connection.provider]} · {connection.label}">
	<div class="head">
		{#if brand}<BrandIcon {brand} size={14} />{/if}
		<span>{PROVIDER_NAME[connection.provider]}</span>
		<span class="spacer"></span>
		<span class="account">{connection.label}</span>
	</div>

	{#each connection.calendars as cal, i (cal.id)}
		<div class="row">
			<span class="swatch" aria-hidden="true" style:background={cal.color ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length]}></span>
			<span class="name">{cal.name}</span>
			<Toggle checked={cal.busy} label="{cal.name} counts as busy" onchange={(v) => toggle(cal.id, v)} />
		</div>
	{:else}
		<p class="row empty">No calendars found in this account yet.</p>
	{/each}

	{#if manage}
		<div class="foot">
			{#if connection.status === 'ok'}
				<span class="synced">
					{connection.lastSyncedAt ? `Last synced ${fmt.current.relative(connection.lastSyncedAt)}` : 'Not synced yet'}
				</span>
			{:else if connection.status === 'reauth_required'}
				<StatusPill tone="apricot" icon="alert">Needs reconnecting</StatusPill>
			{:else}
				<StatusPill tone="apricot" icon="alert">Sync failed</StatusPill>
			{/if}
			<span class="spacer"></span>
			{#if connection.status === 'reauth_required' && (connection.provider === 'google' || connection.provider === 'microsoft')}
				<Button size="sm" variant="warn" href={oauthStartUrl(connection.provider, returnTo)} data-sveltekit-reload>Reconnect</Button>
			{:else}
				<Button size="sm" variant="secondary" icon="refresh" loading={syncing} onclick={sync}>Sync now</Button>
			{/if}
			<Button size="sm" variant="ghost" icon="trash" onclick={() => (confirmRemove = true)} aria-label="Remove {connection.label}">Remove</Button>
		</div>
	{/if}
</section>

{#if manage}
	<ConfirmSheet
		bind:open={confirmRemove}
		title="Remove this calendar?"
		message="Busy times from {connection.label} will stop counting. You can connect it again any time."
		confirmLabel="Remove"
		destructive
		onconfirm={remove}
	/>
{/if}

<style>
	.conn {
		border-radius: 24px;
		padding: 6px 14px;
	}
	.head {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 0 6px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.spacer {
		flex: 1;
	}
	.account {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		max-width: 60%;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 50px;
		border-top: 1px solid var(--line);
	}
	.swatch {
		width: 12px;
		height: 12px;
		border-radius: 4px;
		flex: none;
	}
	.name {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 500;
	}
	.empty {
		font-size: 13px;
		color: var(--muted);
	}
	.foot {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 6px;
		padding: 8px 0 10px;
		border-top: 1px solid var(--line);
	}
	.synced {
		font-size: 12px;
		color: var(--muted);
	}
</style>
