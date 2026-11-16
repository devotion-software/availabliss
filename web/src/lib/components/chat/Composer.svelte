<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { fly } from 'svelte/transition';
	import type { Message } from '#lib/types';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * Message box with a + menu for Poll / Propose a time / Add event (Chat.dc.html).
	 * While `editing` one of your messages it holds that text, and send becomes Save; cancelling restores your draft.
	 */
	interface Props {
		groupId: string;
		onsend: (body: string) => Promise<unknown> | unknown;
		onpoll: () => void;
		onpropose: () => void;
		compact?: boolean;
		editing?: Message | null;
		/** Saves the edit; should throw on failure (stays in edit mode). */
		onsaveedit?: (body: string) => Promise<unknown> | unknown;
		oncanceledit?: () => void;
	}
	let { groupId, onsend, onpoll, onpropose, compact = false, editing = null, onsaveedit, oncanceledit }: Props = $props();

	const uid = $props.id();
	let body = $state('');
	let menu = $state(false);
	let input: HTMLInputElement | undefined = $state();
	let saving = $state(false);

	// Entering edit mode keeps your draft aside and fills in the message; leaving it (save, cancel, deleted) gives the draft back.
	let draft = '';
	let editingId: string | null = null;
	$effect(() => {
		const message = editing;
		const id = message?.id ?? null;
		untrack(() => {
			if (id === editingId) return;
			if (editingId === null) draft = body;
			editingId = id;
			if (message) {
				menu = false;
				body = message.body ?? '';
				tick().then(() => {
					input?.focus();
					input?.setSelectionRange(body.length, body.length);
				});
			} else {
				body = draft;
				draft = '';
			}
		});
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const text = body.trim();
		if (!text) return;
		if (editing) {
			if (saving) return;
			saving = true;
			try {
				await onsaveedit?.(text);
			} catch {
				// The thread already toasted; stay in edit mode with the text.
			} finally {
				saving = false;
			}
			input?.focus();
			return;
		}
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
		} else if (e.key === 'Escape' && editing) {
			e.stopPropagation();
			oncanceledit?.();
		}
	}

	function cancelEdit() {
		oncanceledit?.();
		input?.focus();
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

	{#if editing}
		<div class="editing" transition:fly={{ y: 8, duration: 180 }}>
			<Icon name="pencil" size={16} />
			<span class="editing-label" id="composer-editing-{uid}">Editing message</span>
			<button type="button" class="cancel-edit" aria-label="Cancel editing" title="Cancel editing" onclick={cancelEdit}>
				<Icon name="x" size={16} strokeWidth={2.4} />
			</button>
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
			placeholder={editing ? 'Edit your message' : 'Message the group'}
			autocomplete="off"
			enterkeyhint={editing ? 'done' : 'send'}
			maxlength={4000}
			aria-describedby={editing ? `composer-editing-${uid}` : undefined}
		/>
		{#if editing}
			<button type="submit" class="send" aria-label="Save" title="Save" disabled={!body.trim() || saving}>
				<Icon name="check" size={20} strokeWidth={2.4} />
			</button>
		{:else}
			<button type="submit" class="send" aria-label="Send" disabled={!body.trim()}>
				<Icon name="send" size={18} strokeWidth={2.2} />
			</button>
		{/if}
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
	.editing {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 0 4px 0 14px;
		min-height: 36px;
		border-radius: 14px;
		background: var(--accent-soft);
		color: var(--accent-ink);
		font-size: 13px;
		font-weight: 600;
	}
	.editing-label {
		flex: 1;
	}
	.cancel-edit {
		width: 32px;
		height: 32px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--accent-ink);
	}
	.cancel-edit:hover {
		background: var(--surface);
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
