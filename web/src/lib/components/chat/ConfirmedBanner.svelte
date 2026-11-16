<script lang="ts">
	import { goto } from '$app/navigation';
	import type { GroupEvent, Message } from '#lib/types';
	import { cacheEvent, getEventCached } from '#lib/client/events';
	import { claimBurst } from '#lib/client/confirmation';
	import { useFormat } from '#lib/client/format';
	import { live } from '#lib/client/live.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Confetti from '#lib/components/ui/Confetti.svelte';
	import AddToCalendar from '#lib/components/events/AddToCalendar.svelte';

	/**
	 * The chat's "Everyone's in" message (a system message carrying the confirmed event): a celebratory card with
	 * Add to calendar, and a one-off confetti burst the first time a recent one comes into view this session.
	 * Until the event loads (or if it's gone) it reads as the plain system line.
	 */
	interface Props {
		message: Message;
		compact?: boolean;
	}
	let { message, compact = false }: Props = $props();

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
		const off = live.on(['event.changed', 'proposal.confirmed'], (e) => {
			const p = e.payload;
			const changed = 'event' in p ? p.event : p;
			if (changed.id !== id) return;
			if ('deleted' in changed) {
				event = null;
				return;
			}
			cacheEvent(changed);
			event = changed;
		});
		return () => {
			alive = false;
			off();
		};
	});

	const when = $derived(event ? fmt.current.when(event.start, event.end, event.allDay) : '');
	const line = $derived([when, event?.location].filter(Boolean).join(' · '));
	const href = $derived(event?.proposalId ? `/app/proposals/${event.proposalId}` : null);

	// Burst once when the banner is mostly on screen.
	let card: HTMLElement | undefined = $state();
	let bursting = $state(false);
	$effect(() => {
		const el = card;
		if (!el || typeof IntersectionObserver === 'undefined') return;
		const observer = new IntersectionObserver(
			([entry]) => {
				if (!entry.isIntersecting) return;
				observer.disconnect();
				const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
				if (claimBurst(message, { reducedMotion })) bursting = true;
			},
			{ threshold: 0.6 }
		);
		observer.observe(el);
		return () => observer.disconnect();
	});

	// Tapping anywhere on the card outside its buttons opens the proposal, like the proposal card.
	function open(e: MouseEvent) {
		if (!href || (e.target as Element).closest('a, button')) return;
		goto(href);
	}
</script>

{#if event && message.eventId}
	<div class="wrap">
		<!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions (the row is the keyboard-reachable link) -->
		<div class="card glass" class:compact class:linked={!!href} bind:this={card} onclick={open}>
			<span class="bits" aria-hidden="true">
				<i style="--x: 14px; --y: 8px; --r: 24deg; --c: var(--accent)"></i>
				<i class="dot" style="--x: 30px; --y: 20px; --c: var(--warn)"></i>
				<i style="--x: 46px; --y: 6px; --r: -32deg; --c: var(--ok)"></i>
				<i style="--x: 64px; --y: 16px; --r: 58deg; --c: var(--confetti-pink)"></i>
				<i class="dot" style="--x: 80px; --y: 7px; --c: var(--accent)"></i>
				<i style="--x: 96px; --y: 18px; --r: -12deg; --c: var(--confetti-pink)"></i>
				<i class="dot" style="--x: 112px; --y: 10px; --c: var(--ok)"></i>
				<i style="--x: 22px; --y: 32px; --r: 80deg; --c: var(--warn)"></i>
			</span>
			<svelte:element
				this={href ? 'a' : 'div'}
				class="row"
				href={href ?? undefined}
				role={href ? undefined : 'group'}
				aria-label="Everyone's in. {event.title}, {line}"
			>
				<span class="tick" aria-hidden="true"><Icon name="check" size={24} strokeWidth={2.6} /></span>
				<span class="text" aria-hidden="true">
					<span class="eyebrow">Everyone's in</span>
					<span class="title">{event.title}</span>
					{#if line}<span class="meta">{line}</span>{/if}
				</span>
			</svelte:element>
			<AddToCalendar eventId={message.eventId} item={event} size="sm" inline />
		</div>
		{#if bursting}<Confetti onend={() => (bursting = false)} />{/if}
	</div>
{:else}
	<p class="system">{message.body}</p>
{/if}

<style>
	.wrap {
		position: relative;
		align-self: stretch;
		display: flex;
		flex-direction: column;
		--confetti-pink: #f47ab8;
	}
	.card {
		position: relative;
		border-radius: 20px;
		padding: 12px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		color: var(--ink);
		border-color: color-mix(in srgb, var(--ok) 35%, transparent);
		/* The success tint over the glass. */
		background-image: linear-gradient(
			color-mix(in srgb, var(--ok-bg) 72%, transparent),
			color-mix(in srgb, var(--ok-bg) 72%, transparent)
		);
		overflow: hidden;
		transition: transform 0.15s ease;
	}
	.linked {
		cursor: pointer;
	}
	.linked:hover {
		transform: translateY(-1px);
	}
	.compact {
		box-shadow: none;
		backdrop-filter: none;
		-webkit-backdrop-filter: none;
		border-radius: 16px;
		padding: 10px;
	}
	.bits {
		position: absolute;
		top: 0;
		right: 0;
		width: 130px;
		height: 44px;
		pointer-events: none;
		opacity: 0.6;
	}
	.bits i {
		position: absolute;
		right: var(--x);
		top: var(--y);
		width: 5px;
		height: 9px;
		border-radius: 1.5px;
		background: var(--c);
		transform: rotate(var(--r, 0deg));
	}
	.bits i.dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
	}
	.row {
		position: relative;
		display: flex;
		align-items: center;
		gap: 12px;
		text-decoration: none;
		color: var(--ink);
		border-radius: 14px;
	}
	a.row:hover {
		color: var(--ink);
	}
	.tick {
		width: 44px;
		height: 44px;
		flex: none;
		border-radius: 50%;
		background: var(--ok);
		color: var(--on-accent);
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
		/* Keep the title clear of the decoration's lower bits. */
		padding-right: 36px;
	}
	.eyebrow {
		font-size: 12px;
		font-weight: 600;
		color: var(--ok-ink);
	}
	.title {
		font-size: 16px;
		font-weight: 700;
		line-height: 1.25;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
	.meta {
		font-size: 12px;
		color: var(--muted);
	}
	.system {
		align-self: center;
		max-width: 90%;
		text-align: center;
		font-size: 12px;
		color: var(--muted);
	}
</style>
