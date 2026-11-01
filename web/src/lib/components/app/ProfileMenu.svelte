<script lang="ts">
	import { page } from '$app/state';
	import type { User } from '#lib/types';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';
	import { signOutAndLeave } from '#lib/client/session';
	import { toasts } from '#lib/client/toast.svelte';

	interface Props {
		user: User;
	}
	let { user }: Props = $props();

	let open = $state(false);
	let root: HTMLElement | undefined = $state();
	let items: HTMLElement[] = $state([]);

	const links: { href: string; label: string; icon: IconName }[] = [
		{ href: '/app/my-time', label: 'My time', icon: 'calendar' },
		{ href: '/app/calendars', label: 'Calendars', icon: 'link' },
		{ href: '/app/settings', label: 'Settings', icon: 'settings' }
	];

	// Close when navigating or clicking elsewhere.
	$effect(() => {
		void page.url.pathname;
		open = false;
	});
	$effect(() => {
		if (!open) return;
		const onPointer = (e: PointerEvent) => {
			if (root && !root.contains(e.target as Node)) open = false;
		};
		document.addEventListener('pointerdown', onPointer);
		return () => document.removeEventListener('pointerdown', onPointer);
	});

	function toggle() {
		open = !open;
		if (open) queueMicrotask(() => items[0]?.focus());
	}

	function onkeydown(e: KeyboardEvent) {
		if (!open) return;
		const i = items.indexOf(document.activeElement as HTMLElement);
		if (e.key === 'Escape') {
			open = false;
			root?.querySelector<HTMLElement>('.trigger')?.focus();
		} else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
			e.preventDefault();
			const next = (i + (e.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
			items[next]?.focus();
		}
	}

	async function signOut() {
		try {
			await signOutAndLeave();
		} catch (err) {
			toasts.error(err);
		}
	}
</script>

<div class="profile" bind:this={root} role="presentation" {onkeydown}>
	<button class="trigger glass" aria-label="Your profile" aria-haspopup="menu" aria-expanded={open} onclick={toggle}>
		<Avatar name={user.name || user.email} id={user.id} image={user.image} size={42} />
	</button>
	{#if open}
		<div class="menu glass" role="menu" aria-label="Profile">
			<div class="who">
				<span class="name">{user.name || 'You'}</span>
				<span class="email">{user.email}</span>
			</div>
			{#each links as link, i (link.href)}
				<a bind:this={items[i]} role="menuitem" href={link.href}><Icon name={link.icon} size={18} />{link.label}</a>
			{/each}
			<button bind:this={items[links.length]} role="menuitem" onclick={signOut}><Icon name="logout" size={18} />Sign out</button>
		</div>
	{/if}
</div>

<style>
	.profile {
		position: relative;
	}
	.trigger {
		width: 44px;
		height: 44px;
		border-radius: 50%;
		padding: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}
	.menu {
		position: absolute;
		right: 0;
		top: calc(100% + 8px);
		z-index: 60;
		min-width: 240px;
		border-radius: 22px;
		padding: 8px;
		display: flex;
		flex-direction: column;
		gap: 2px;
		background: color-mix(in srgb, var(--bg) 82%, transparent);
		animation: pop 0.2s var(--ease-out) both;
	}
	.who {
		display: flex;
		flex-direction: column;
		padding: 8px 12px 10px;
		border-bottom: 1px solid var(--line);
		margin-bottom: 4px;
	}
	.name {
		font-weight: 650;
	}
	.email {
		font-size: 12px;
		color: var(--muted);
		overflow: hidden;
		text-overflow: ellipsis;
	}
	a,
	button {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 44px;
		padding: 0 12px;
		border-radius: 14px;
		font-size: 14px;
		font-weight: 600;
		color: var(--ink);
		text-decoration: none;
		text-align: left;
	}
	a:hover,
	button:hover,
	a:focus-visible,
	button:focus-visible {
		background: var(--surface);
		color: var(--ink);
	}
	@keyframes pop {
		from {
			transform: translateY(-6px) scale(0.98);
			opacity: 0;
		}
	}
</style>
