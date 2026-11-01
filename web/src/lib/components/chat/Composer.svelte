<script lang="ts">
	import { fly } from 'svelte/transition';
	import Icon from '#lib/components/ui/Icon.svelte';

	/** Message box with a + menu for Poll / Propose a time / Add event (Chat.dc.html). */
	interface Props {
		groupId: string;
		onsend: (body: string) => Promise<unknown> | unknown;
		onpoll: () => void;
		onpropose: () => void;
		compact?: boolean;
	}
	let { groupId, onsend, onpoll, onpropose, compact = false }: Props = $props();

	const uid = $props.id();
	let body = $state('');
	let menu = $state(false);
	let input: HTMLInputElement | undefined = $state();

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const text = body.trim();
		if (!text) return;
		body = '';
		try {
			await onsend(text);
		} catch {
			// The thread already toasted; give the text back.
			body = text;
		}
		input?.focus();
	}

	function choose(action: () => void) {
		menu = false;
		action();
	}

	function onkeydown(e: KeyboardEvent) {
		if (e.key === 'Escape' && menu) {
			e.stopPropagation();
			menu = false;
		}
	}
</script>

<div class="composer" class:compact role="presentation" {onkeydown}>
	{#if menu}
		<div class="menu" class:glass={!compact} id="composer-menu-{uid}" transition:fly={{ y: 10, duration: 220 }}>
			<button type="button" onclick={() => choose(onpoll)}>
				<Icon name="poll" size={22} />
				Poll
			</button>
			<button type="button" onclick={() => choose(onpropose)}>
				<Icon name="check-circle" size={22} />
				Propose a time
			</button>
			<a href="/app/groups/{groupId}/events/new" onclick={() => (menu = false)}>
				<Icon name="calendar-plus" size={22} />
				Add event
			</a>
		</div>
	{/if}

	<form class="bar" class:glass={!compact} onsubmit={submit}>
		<button
			type="button"
			class="plus"
			class:open={menu}
			aria-expanded={menu}
			aria-controls="composer-menu-{uid}"
			aria-label="Add poll, proposal or event"
			onclick={() => (menu = !menu)}
		>
			<Icon name="plus" size={20} strokeWidth={2.4} />
		</button>
		<label for="composer-{uid}" class="sr-only">Message</label>
		<input
			bind:this={input}
			id="composer-{uid}"
			bind:value={body}
			placeholder="Message the group"
			autocomplete="off"
			enterkeyhint="send"
			maxlength={4000}
		/>
		<button type="submit" class="send" aria-label="Send" disabled={!body.trim()}>
			<Icon name="send" size={18} strokeWidth={2.2} />
		</button>
	</form>
</div>

<style>
	.composer {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.menu {
		border-radius: 22px;
		padding: 8px;
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: 6px;
	}
	.compact .menu {
		padding: 0;
	}
	.menu button,
	.menu a {
		min-height: 72px;
		border-radius: 16px;
		background: var(--surface);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 600;
		color: var(--ink);
		text-decoration: none;
		text-align: center;
		padding: 6px;
	}
	.menu :global(svg) {
		color: var(--accent-ink);
	}
	.menu button:hover,
	.menu a:hover {
		color: var(--ink);
		background: var(--accent-soft);
	}
	.bar {
		border-radius: 24px;
		padding: 6px;
		display: flex;
		align-items: center;
		gap: 6px;
	}
	.compact .bar {
		border-radius: 16px;
		background: var(--surface);
		padding: 4px;
	}
	.plus,
	.send {
		width: 44px;
		height: 44px;
		flex: none;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
	}
	.compact .plus,
	.compact .send {
		width: 40px;
		height: 40px;
		border-radius: 12px;
	}
	.plus {
		background: var(--surface);
		color: var(--ink);
		transition: background-color 0.2s ease;
	}
	.compact .plus {
		background: transparent;
	}
	.plus :global(svg) {
		transition: transform 0.2s ease;
	}
	.plus.open {
		background: var(--solid);
		color: var(--on-solid);
	}
	.plus.open :global(svg) {
		transform: rotate(45deg);
	}
	input {
		flex: 1;
		min-width: 0;
		height: 44px;
		border: 0;
		background: transparent;
		font-size: 15px;
		padding: 0 4px;
	}
	.compact input {
		height: 40px;
		font-size: 14px;
	}
	input:focus {
		outline: none;
	}
	.bar:focus-within {
		box-shadow:
			var(--glass-shadow),
			0 0 0 3px var(--accent-soft);
	}
	.send {
		background: var(--accent);
		color: var(--on-accent);
		transition: opacity 0.2s ease;
	}
	.send:disabled {
		opacity: 0.45;
	}
</style>
