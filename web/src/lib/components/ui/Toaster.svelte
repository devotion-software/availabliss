<script lang="ts">
	import { fly, fade } from 'svelte/transition';
	import { flip } from 'svelte/animate';
	import { toasts } from '#lib/client/toast.svelte';
	import Icon, { type IconName } from './Icon.svelte';

	const ICON: Record<string, IconName> = { success: 'check-circle', error: 'alert', info: 'info' };
</script>

<div class="toaster" role="region" aria-live="polite" aria-label="Notifications">
	{#each toasts.items as toast (toast.id)}
		<div
			class="toast glass {toast.kind}"
			role={toast.kind === 'error' ? 'alert' : 'status'}
			animate:flip={{ duration: 220 }}
			in:fly={{ y: 16, duration: 260 }}
			out:fade={{ duration: 160 }}
		>
			<span class="icon"><Icon name={ICON[toast.kind]} size={18} /></span>
			<span class="msg">{toast.message}</span>
			{#if toast.action}
				{@const action = toast.action}
				<button
					class="action"
					onclick={() => {
						toasts.dismiss(toast.id);
						action.run();
					}}>{action.label}</button
				>
			{/if}
			<button class="close" aria-label="Dismiss" onclick={() => toasts.dismiss(toast.id)}>
				<Icon name="x" size={14} strokeWidth={2.6} />
			</button>
		</div>
	{/each}
</div>

<style>
	.toaster {
		position: fixed;
		left: 50%;
		bottom: max(24px, env(safe-area-inset-bottom));
		transform: translateX(-50%);
		z-index: 200;
		display: flex;
		flex-direction: column;
		gap: 8px;
		width: min(calc(100vw - 24px), 420px);
		pointer-events: none;
	}
	.toast {
		pointer-events: auto;
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 52px;
		padding: 8px 8px 8px 14px;
		border-radius: 20px;
		font-size: 14px;
		font-weight: 500;
		background: color-mix(in srgb, var(--bg) 80%, transparent);
	}
	.icon {
		color: var(--accent-ink);
	}
	.success .icon {
		color: var(--ok-ink);
	}
	.error .icon {
		color: var(--warn-ink);
	}
	.msg {
		flex: 1;
		line-height: 1.35;
	}
	.action {
		height: 36px;
		padding: 0 12px;
		border-radius: 12px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.action:hover {
		background: var(--accent-soft);
	}
	.close {
		width: 36px;
		height: 36px;
		border-radius: 50%;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
	}
	.close:hover {
		background: var(--line);
	}
	@media (min-width: 900px) {
		.toaster {
			left: auto;
			right: 24px;
			transform: none;
		}
	}
</style>
