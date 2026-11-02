<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		n: number;
		title: string;
		text: string;
		/** Small, playable illustration above the copy. */
		art: Snippet;
	}
	let { n, title, text, art }: Props = $props();
</script>

<li class="step glass">
	<div class="art" role="group" aria-label="Try it: {title}">{@render art()}</div>
	<span class="n" aria-hidden="true">{n}</span>
	<h3>{title}</h3>
	<p>{text}</p>
</li>

<style>
	.step {
		position: relative;
		list-style: none;
		border-radius: 28px;
		padding: 18px 18px 22px;
		display: flex;
		flex-direction: column;
		gap: 8px;
		overflow: hidden;
	}
	.art {
		min-height: 120px;
		border-radius: 20px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
		padding: 14px;
		display: flex;
		flex-direction: column;
		justify-content: center;
		gap: 8px;
		margin-bottom: 8px;
	}
	.n {
		font-family: var(--font-display);
		font-size: 13px;
		font-weight: 700;
		color: var(--accent-ink);
		letter-spacing: 0;
	}
	h3 {
		font-size: 22px;
		font-weight: 650;
	}
	p {
		font-size: 15px;
		color: var(--muted);
	}
</style>
