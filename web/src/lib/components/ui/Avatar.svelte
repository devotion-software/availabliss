<script lang="ts">
	import { initials } from '#lib/client/format';
	import { tileFor } from './avatar';

	interface Props {
		name: string;
		/** Seeds the tile colour; defaults to the name. */
		id?: string;
		image?: string | null;
		size?: number;
		/** Ring that separates stacked avatars from what's behind. */
		ring?: boolean;
		/** Announce the name (when the avatar stands alone). */
		labelled?: boolean;
	}
	let { name, id, image, size = 32, ring = false, labelled = false }: Props = $props();

	let failed = $state(false);
	const tile = $derived(tileFor(id ?? name));
</script>

<span
	class="avatar"
	class:ring
	style:--size="{size}px"
	style:background="var(--{tile})"
	role={labelled ? 'img' : undefined}
	aria-label={labelled ? name : undefined}
	aria-hidden={labelled ? undefined : 'true'}
>
	{#if image && !failed}
		<img src={image} alt="" referrerpolicy="no-referrer" onerror={() => (failed = true)} />
	{:else}
		{size >= 30 ? initials(name) : initials(name).slice(0, 1)}
	{/if}
</span>

<style>
	.avatar {
		flex: none;
		width: var(--size);
		height: var(--size);
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		color: var(--ink);
		font-weight: 700;
		font-size: calc(var(--size) * 0.4);
		line-height: 1;
		letter-spacing: -0.01em;
	}
	.ring {
		border: 1.5px solid var(--glass-border);
	}
	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
