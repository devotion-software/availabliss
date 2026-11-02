<script lang="ts">
	import { onMount } from 'svelte';
	import Logo from '#lib/components/ui/Logo.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import SyncRing from '#lib/components/ui/SyncRing.svelte';
	import { COLUMNS, COLUMN_NAMES, PEOPLE, busyLabel, grid, slots, type Mode } from './demo';

	/**
	 * The hero: a group calendar you can play with. On load, the crew syncs one
	 * by one and the times that work light up; then tap someone to plan without
	 * them, or switch what you're planning, and it works the answer out again.
	 */
	let mode = $state<Mode>('weekend');
	let excluded = $state(new Set<string>());
	/** How many people have synced so far in the opening sequence. */
	let synced = $state(0);
	let introDone = $state(false);

	const included = $derived(PEOPLE.filter((p) => !excluded.has(p.id)));
	const countedPeople = $derived(included.slice(0, synced));
	// While calendars are still syncing nothing can be called a match yet: the
	// times that work all light up together once everyone's in.
	const cells = $derived(
		grid(mode, introDone ? included : countedPeople).map((c) => (!introDone && c.kind === 'match' ? { ...c, kind: 'free' as const, band: null } : c))
	);
	const found = $derived(introDone ? slots(mode, included) : []);
	const noun = $derived(mode === 'weekend' ? 'weekend' : 'evening');

	const headline = $derived.by(() => {
		if (!introDone) return '';
		if (!found.length) return `No ${noun} works for everyone. Try planning without someone.`;
		const n = found.length;
		return n === 1 ? `1 ${noun} works for everyone: ${found[0].label}` : `${n} ${noun}s work for everyone. First up: ${found[0].label}`;
	});

	onMount(() => {
		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			finishIntro();
			return;
		}
		const timer = setInterval(() => {
			synced += 1;
			if (synced >= PEOPLE.length) {
				clearInterval(timer);
				setTimeout(finishIntro, 350);
			}
		}, 380);
		return () => clearInterval(timer);
	});

	function finishIntro() {
		synced = PEOPLE.length;
		introDone = true;
	}

	function toggle(id: string) {
		if (!introDone) finishIntro();
		const next = new Set(excluded);
		if (next.has(id)) next.delete(id);
		else next.add(id);
		excluded = next;
	}

	function setMode(m: Mode) {
		if (!introDone) finishIntro();
		mode = m;
	}

	const syncedCount = $derived(Math.min(synced, included.length));
</script>

<div class="demo">
	<div class="card glass">
		<div class="top">
			<span class="title">November 2026</span>
			<div class="modes" role="radiogroup" aria-label="What you're planning">
				{#each [['weekend', 'A weekend'], ['evening', 'An evening']] as [value, label] (value)}
					<button type="button" role="radio" aria-checked={mode === value} onclick={() => setMode(value as Mode)}>{label}</button>
				{/each}
			</div>
		</div>

		<div class="cols" aria-hidden="true">
			{#each COLUMNS[mode] as c (c)}<span>{COLUMN_NAMES[c]}</span>{/each}
		</div>
		<div class="grid" aria-hidden="true">
			{#each cells as cell, i (`${mode}-${i}`)}
				<div class="cell {cell.kind} {cell.band ?? ''}" class:counting={!introDone} style:--i={i}>
					{#if cell.day}
						<span class="d">{cell.day}</span>
						<span class="note">
							{#if cell.kind === 'match'}
								{cell.band === 'start' || cell.band === 'single' ? 'Free from 18:00' : 'Everyone free'}
							{:else}
								{busyLabel(cell.busyNames)}
							{/if}
						</span>
					{/if}
				</div>
			{/each}
		</div>
		<p class="result" aria-live="polite">{headline || `Syncing calendars… ${syncedCount} of ${included.length}`}</p>
	</div>

	{#if introDone && found.length}
		{#key `${mode}-${found[0].label}`}
			<div class="notif glass" aria-hidden="true">
				<Logo size={30} />
				<div>
					<b>Lake District crew <span>· now</span></b>
					<span>{found[0].label} works for everyone. Propose it?</span>
				</div>
			</div>
		{/key}
	{/if}

	<div class="crew glass" role="group" aria-label="Who's in the plan. Tap someone to plan without them.">
		<span class="crew-head">
			<SyncRing value={syncedCount} total={included.length} size={18} />
			{introDone ? 'Tap someone to plan without them' : `${syncedCount} of ${included.length} synced`}
		</span>
		<div class="people">
			{#each PEOPLE as p, i (p.id)}
				{@const out = excluded.has(p.id)}
				<button
					type="button"
					class="person"
					class:out
					class:waiting={!introDone && i >= synced}
					aria-pressed={!out}
					aria-label="{p.name}, {out ? 'not in the plan' : 'in the plan'}"
					onclick={() => toggle(p.id)}
				>
					<Avatar name={p.name} id={p.id} size={30} ring />
					<span>{p.name}</span>
				</button>
			{/each}
		</div>
	</div>
</div>

<style>
	.demo {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.card {
		border-radius: 28px;
		padding: 18px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.top {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 10px;
		flex-wrap: wrap;
	}
	.title {
		font-family: var(--font-display);
		letter-spacing: -0.025em;
		font-size: 20px;
		font-weight: 650;
	}
	.modes {
		display: flex;
		padding: 3px;
		border-radius: 999px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.modes button {
		height: 30px;
		padding: 0 12px;
		border-radius: 999px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
		transition:
			background-color 0.2s ease,
			color 0.2s ease;
	}
	.modes button[aria-checked='true'] {
		background: var(--solid);
		color: var(--on-solid);
	}
	.cols,
	.grid {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 6px;
	}
	.cols {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.cols span {
		padding-left: 6px;
	}
	.cell {
		min-height: 62px;
		padding: 10px;
		border-radius: 16px;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
		border: 1px solid transparent;
		transition:
			background-color 0.35s ease,
			opacity 0.35s ease,
			border-radius 0.35s ease,
			box-shadow 0.35s ease,
			color 0.35s ease;
		transition-delay: calc(var(--i) * 18ms);
	}
	.blank {
		visibility: hidden;
	}
	.d {
		font-size: 15px;
		font-weight: 650;
	}
	.note {
		font-size: 11px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		min-height: 1.2em;
	}
	.free {
		background: var(--surface);
		border-color: var(--glass-border);
	}
	.busy {
		background: var(--busy-bg);
		border-color: var(--glass-border);
		opacity: 0.5;
	}
	.match {
		background: var(--accent);
		color: var(--on-accent);
		box-shadow: 0 14px 28px -16px var(--accent);
		border-radius: 6px;
		animation: light 0.5s var(--ease-spring) both;
		animation-delay: calc(var(--i) * 40ms);
	}
	.match.start {
		border-radius: 18px 6px 6px 18px;
	}
	.match.end {
		border-radius: 6px 18px 18px 6px;
	}
	.match.single {
		border-radius: 16px;
	}
	.result {
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
		min-height: 2.6em;
	}

	.notif {
		position: absolute;
		top: -60px;
		pointer-events: none;
		right: 0;
		max-width: 300px;
		border-radius: 20px;
		padding: 10px 14px;
		display: flex;
		align-items: center;
		gap: 10px;
		font-size: 12px;
		background: color-mix(in srgb, var(--bg) 88%, transparent);
		z-index: 2;
		animation: drop 0.6s 0.25s var(--ease-spring) both;
	}
	.notif div {
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.notif b {
		font-weight: 700;
	}
	.notif b span {
		font-weight: 500;
		color: var(--muted);
	}

	.crew {
		border-radius: 22px;
		padding: 12px 14px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		background: color-mix(in srgb, var(--bg) 88%, transparent);
	}
	.crew-head {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.people {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}
	.person {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 40px;
		padding: 4px 12px 4px 5px;
		border-radius: 999px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
		font-size: 13px;
		font-weight: 600;
		transition:
			opacity 0.25s ease,
			transform 0.15s ease,
			filter 0.25s ease;
	}
	.person:hover {
		border-color: var(--line-3);
	}
	.person:active {
		transform: scale(0.95);
	}
	.person.out {
		opacity: 0.45;
		filter: grayscale(1);
	}
	.person.out span {
		text-decoration: line-through;
	}
	.person.waiting {
		opacity: 0.35;
	}

	@keyframes light {
		from {
			transform: scale(0.92);
			filter: brightness(1.4);
		}
	}
	@keyframes drop {
		from {
			transform: translateY(-16px);
			opacity: 0;
		}
	}
	@media (max-width: 480px) {
		.cell {
			min-height: 52px;
			padding: 8px;
		}
		.note {
			font-size: 10px;
		}
		.notif {
			position: static;
			max-width: none;
			order: -1;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.cell,
		.match,
		.notif,
		.person {
			transition: none;
			animation: none;
		}
	}
</style>
