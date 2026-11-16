<script lang="ts">
	import type { Message } from '#lib/types';
	import type { GroupEvent } from '#lib/types';
	import { cacheEvent, eventIcsUrl, getEventCached, removeEventImage, setEventImage } from '#lib/client/events';
	import { useFormat } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import { takePhoto } from '#lib/client/photo';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Menu, { type MenuItem } from '#lib/components/ui/Menu.svelte';

	/** An event added to the group, posted in the chat, with its cover photo when it has one. */
	interface Props {
		message: Message;
		meId: string;
		/** Admins can change any event's cover; others only their own. */
		admin?: boolean;
		compact?: boolean;
	}
	let { message, meId, admin = false, compact = false }: Props = $props();

	const fmt = useFormat();
	let event = $state<GroupEvent | null>(null);
	$effect(() => {
		const id = message.eventId;
		event = null;
		if (!id) return;
		let alive = true;
		getEventCached(id)
			.then((e) => alive && (event = e))
			.catch(() => {});
		return () => {
			alive = false;
		};
	});
	// Edits (a new cover, a new time) arrive live; keep the card and the shared cache current.
	$effect(() => {
		const id = message.eventId;
		if (!id) return;
		return live.on('event.changed', (e) => {
			if (e.payload.id !== id || 'deleted' in e.payload) return;
			cacheEvent(e.payload);
			event = e.payload;
		});
	});
	const when = $derived(event ? fmt.current.when(event.start, event.end, event.allDay) : null);

	let input: HTMLInputElement | undefined = $state();
	let busy = $state(false);
	// Remembers which URL failed, so a new cover gets a fresh try.
	let failed = $state<string | null>(null);
	const cover = $derived(event?.image && event.image !== failed ? event.image : null);
	const canEdit = $derived(!!event && (event.createdBy === meId || admin));
	const menu = $derived<MenuItem[]>(
		event?.image
			? [
					{ label: 'Change cover photo', icon: 'pencil', onselect: () => input?.click() },
					{ label: 'Remove cover photo', icon: 'trash', onselect: () => save((id) => removeEventImage(id)) }
				]
			: [{ label: 'Add a cover photo', icon: 'plus', onselect: () => input?.click() }]
	);

	async function save(action: (id: string) => Promise<GroupEvent>) {
		if (!event) return;
		busy = true;
		try {
			const updated = await action(event.id);
			cacheEvent(updated);
			event = updated;
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}

	function pick(e: Event) {
		const file = takePhoto(e);
		if (file) save((id) => setEventImage(id, file));
	}
</script>

<div class="card glass" class:compact class:has-cover={cover}>
	{#if cover}<img class="cover" src={cover} alt="" onerror={() => (failed = cover)} />{/if}
	<div class="row">
		<span class="icon"><Icon name="calendar" size={20} /></span>
		<span class="text">
			<span class="title">{event?.title ?? message.body ?? 'An event was added'}</span>
			{#if when}<span class="meta">{[when, event?.location].filter(Boolean).join(' · ')}</span>{/if}
		</span>
		{#if message.eventId}
			<a href={eventIcsUrl(message.eventId)} download aria-label="Add to calendar">
				<Icon name="calendar-plus" size={18} />
			</a>
		{/if}
		{#if canEdit}
			<span class="edit" class:busy><Menu icon="more" label="Event options" variant="plain" items={menu} /></span>
			<input bind:this={input} class="sr-only" type="file" accept="image/*" tabindex="-1" aria-hidden="true" disabled={busy} onchange={pick} />
		{/if}
	</div>
</div>

<style>
	.card {
		align-self: stretch;
		border-radius: 20px;
		padding: 10px 10px 10px 12px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	/* The cover sits 4px in from the card's edge, its corners following the card's. */
	.has-cover {
		padding-top: 4px;
	}
	.cover {
		display: block;
		width: calc(100% + 14px);
		margin: 0 -6px 0 -8px;
		aspect-ratio: 2 / 1;
		max-height: 220px;
		object-fit: cover;
		border-radius: 16px;
	}
	.row {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.busy {
		opacity: 0.5;
		pointer-events: none;
	}
	.compact {
		background: var(--surface);
		box-shadow: none;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		border-radius: 16px;
	}
	.icon {
		width: 38px;
		height: 38px;
		flex: none;
		border-radius: 12px;
		background: var(--ok-bg);
		color: var(--ok-ink);
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.title {
		font-size: 14px;
		font-weight: 600;
	}
	.meta {
		font-size: 12px;
		color: var(--muted);
	}
	a {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--accent-ink);
	}
	a:hover {
		background: var(--accent-soft);
	}
</style>
