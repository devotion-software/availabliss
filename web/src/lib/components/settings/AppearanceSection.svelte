<script lang="ts">
	import type { ColorTheme, ThemeMode } from '#lib/types';
	import { applyAppearance, MODE_OPTIONS, THEME_SWATCHES } from '#lib/client/appearance';
	import { updateMe } from '#lib/client/me';
	import { toasts } from '#lib/client/toast.svelte';
	import GlassPanel from '#lib/components/ui/GlassPanel.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import StatusPill from '#lib/components/ui/StatusPill.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import ThemeSwatch from './ThemeSwatch.svelte';

	/** Appearance.dc.html: mode, colour theme and a live preview. Applies instantly, saves to the account. */
	interface Props {
		themeMode: ThemeMode;
		colorTheme: ColorTheme;
	}
	let { themeMode, colorTheme }: Props = $props();

	let mode = $state<ThemeMode>((() => themeMode)());
	let theme = $state<ColorTheme>((() => colorTheme)());
	let saved = $state<{ themeMode: ThemeMode; colorTheme: ColorTheme }>({ themeMode: (() => themeMode)(), colorTheme: (() => colorTheme)() });
	let swatches: HTMLButtonElement[] = $state([]);

	async function save(patch: { themeMode?: ThemeMode; colorTheme?: ColorTheme }) {
		applyAppearance(patch);
		try {
			await updateMe(patch);
			saved = { ...saved, ...patch };
		} catch (err) {
			// Put things back the way the account has them.
			mode = saved.themeMode;
			theme = saved.colorTheme;
			applyAppearance(saved);
			toasts.error(err);
		}
	}

	function pickTheme(value: ColorTheme) {
		if (value === theme) return;
		theme = value;
		save({ colorTheme: value });
	}

	function onSwatchKey(e: KeyboardEvent, i: number) {
		const step = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0;
		if (!step) return;
		e.preventDefault();
		const next = (i + step + THEME_SWATCHES.length) % THEME_SWATCHES.length;
		pickTheme(THEME_SWATCHES[next].value);
		swatches[next]?.focus();
	}
</script>

<GlassPanel as="section" padding="14px" gap={10} aria-labelledby="mode-title">
	<h2 class="eyebrow" id="mode-title">Mode</h2>
	<SegmentedControl options={MODE_OPTIONS} bind:value={mode} label="Mode" variant="soft" block onchange={(v) => save({ themeMode: v })} />
</GlassPanel>

<GlassPanel as="section" padding="14px" gap={10} aria-labelledby="theme-title">
	<h2 class="eyebrow" id="theme-title">Colour theme</h2>
	<div class="themes" role="radiogroup" aria-labelledby="theme-title">
		{#each THEME_SWATCHES as sw, i (sw.value)}
			<ThemeSwatch
				bind:ref={swatches[i]}
				label={sw.label}
				accent={sw.accent}
				orbs={sw.orbs}
				selected={theme === sw.value}
				tabindex={theme === sw.value ? 0 : -1}
				onselect={() => pickTheme(sw.value)}
				onkeydown={(e) => onSwatchKey(e, i)}
			/>
		{/each}
	</div>
</GlassPanel>

<GlassPanel as="section" padding="14px" gap={12} aria-label="Preview">
	<div class="preview-head">
		<h2 class="eyebrow">Preview</h2>
		<StatusPill tone="mint">Works</StatusPill>
	</div>
	<span class="pname">Lake District crew</span>
	<div class="cells" aria-hidden="true">
		<span class="cell match start">13<small>from 18:00</small></span>
		<span class="cell match mid">14<small>all free</small></span>
		<span class="cell match end">15<small>all free</small></span>
		<span class="cell free">20<small>&nbsp;</small></span>
		<span class="cell busy">21<small>Tom busy</small></span>
		<span class="cell free">22<small>&nbsp;</small></span>
	</div>
	<div class="buttons" aria-hidden="true">
		<span class="b accent">Propose</span>
		<span class="b solid">Add to calendar</span>
	</div>
</GlassPanel>

<p class="saved">
	<Icon name="cloud-check" size={16} />
	Saved to your account · syncs to every device
</p>

<style>
	.themes {
		display: grid;
		grid-template-columns: repeat(5, minmax(0, 1fr));
		gap: 8px;
	}
	.preview-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.pname {
		font-family: var(--font-display);
		letter-spacing: -0.025em;
		font-size: 18px;
		font-weight: 650;
	}
	.cells {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 4px;
	}
	.cell {
		height: 48px;
		border-radius: 14px;
		display: flex;
		flex-direction: column;
		justify-content: center;
		padding: 0 10px;
		font-size: 15px;
		font-weight: 650;
	}
	.cell small {
		font-size: 10px;
		font-weight: 600;
	}
	.match {
		background: var(--accent);
		color: var(--on-accent);
		transition: background-color 0.4s ease;
	}
	.start {
		border-radius: 14px 5px 5px 14px;
	}
	.mid {
		border-radius: 5px;
	}
	.end {
		border-radius: 5px 14px 14px 5px;
	}
	.free {
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.busy {
		background: var(--busy-bg);
		border: 1px solid var(--glass-border);
		opacity: 0.5;
	}
	.buttons {
		display: flex;
		gap: 8px;
	}
	.b {
		flex: 1;
		height: 42px;
		border-radius: 13px;
		font-size: 14px;
		font-weight: 600;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.b.accent {
		background: var(--accent);
		color: var(--on-accent);
	}
	.b.solid {
		background: var(--solid);
		color: var(--on-solid);
	}
	.saved {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		font-size: 13px;
		color: var(--muted);
	}
	.saved :global(svg) {
		color: var(--ok-ink);
	}
</style>
