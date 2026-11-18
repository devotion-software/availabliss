<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { fly } from 'svelte/transition';
	import type { Message } from '#lib/types';
	import type { ReplyingTo } from '#lib/client/chat-messages';
	import Icon from '#lib/components/ui/Icon.svelte';
	import { AttachmentDraft } from '#lib/client/attachment-draft.svelte';
	import AttachmentTray from './AttachmentTray.svelte';

	/**
	 * Message box with a + menu for Photos / Files / Poll / Propose a time / Add event (Chat.dc.html).
	 * Photos and files (also pasted, or dropped via `addFiles`) upload straight away into a tray above the field;
	 * once they're up, send sends them with the text as their caption.
	 * While `editing` one of your messages it holds that text, and send becomes Save; cancelling restores your draft.
	 * While `replying`, a "Replying to …" strip with ✕ sits above the field.
	 */
	interface Props {
		groupId: string;
		/** Sends the text, or uploaded attachments (by id) with the text as caption; should throw on failure. */
		onsend: (body: string, attachmentIds: string[]) => Promise<unknown> | unknown;
		/** The + menu's actions (no + menu when `plain`). */
		onpoll?: () => void;
		onpropose?: () => void;
		/** Location in the + menu (not in `plain` threads). */
		onlocation?: () => void;
		compact?: boolean;
		editing?: Message | null;
		/** Saves the edit; should throw on failure (stays in edit mode). */
		onsaveedit?: (body: string) => Promise<unknown> | unknown;
		oncanceledit?: () => void;
		/** The message your next one replies to. */
		replying?: ReplyingTo | null;
		oncancelreply?: () => void;
		/** In a thread: a + menu with just Photos and Files, a different placeholder, ✕ on the reply strip only when `canCancelReply`. */
		plain?: boolean;
		placeholder?: string;
		canCancelReply?: boolean;
		cancelReplyLabel?: string;
		/** Whether a new reply target puts you in the field. */
		focusOnReply?: boolean;
	}
	let {
		groupId,
		onsend,
		onpoll = () => {},
		onpropose = () => {},
		onlocation,
		compact = false,
		editing = null,
		onsaveedit,
		oncanceledit,
		replying = null,
		oncancelreply,
		plain = false,
		placeholder = 'Message the group',
		canCancelReply = true,
		cancelReplyLabel = 'Cancel reply',
		focusOnReply = true
	}: Props = $props();

	const uid = $props.id();
	let body = $state('');
	let menu = $state(false);
	let input: HTMLInputElement | undefined = $state();
	let saving = $state(false);
	let sending = $state(false);
	let photoInput: HTMLInputElement | undefined = $state();
	let fileInput: HTMLInputElement | undefined = $state();

	// Picked attachments belong to this group's chat; switching groups drops them.
	const picked = $derived(new AttachmentDraft(groupId));
	$effect(() => {
		const d = picked;
		return () => d.clear();
	});
	const hasDraft = $derived(picked.items.length > 0);
	/** Editing a message with attachments edits its caption, which may be emptied. */
	const editingCaption = $derived(!!editing?.attachments?.length);
	const canSend = $derived(hasDraft ? picked.ready && !sending : !!body.trim());

	/** Adds files (picked, pasted or dropped onto the chat) to the next message. */
	export function addFiles(files: Iterable<File>) {
		if (editing) return;
		menu = false;
		picked.add(files);
		input?.focus();
	}

	function onpick(e: Event) {
		const el = e.currentTarget as HTMLInputElement;
		const files = [...(el.files ?? [])];
		el.value = '';
		addFiles(files);
	}

	function onpaste(e: ClipboardEvent) {
		const files = [...(e.clipboardData?.files ?? [])];
		if (!files.length || editing) return;
		e.preventDefault();
		addFiles(files);
	}

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
				body = (message.attachments?.length ? message.caption : message.body) ?? '';
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

	// Starting a reply puts you in the field.
	let replyingId: string | null = null;
	$effect(() => {
		const id = replying?.id ?? null;
		untrack(() => {
			if (id === replyingId) return;
			replyingId = id;
			if (id && focusOnReply) {
				menu = false;
				tick().then(() => input?.focus());
			}
		});
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const text = body.trim();
		if (editing) {
			if (!text && !editingCaption) return;
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
		if (hasDraft) {
			// Attachments go once they're all up; the tray and text stay until the server has the message.
			if (!picked.ready || sending) return;
			sending = true;
			try {
				await onsend(text, picked.ids);
				picked.clear();
				body = '';
			} catch {
				// The thread already toasted; keep everything to try again.
			} finally {
				sending = false;
			}
			input?.focus();
			return;
		}
		if (!text) return;
		body = '';
		try {
			await onsend(text, []);
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
		} else if (e.key === 'Escape' && replying && canCancelReply) {
			e.stopPropagation();
			oncancelreply?.();
		}
	}

	function cancelReply() {
		oncancelreply?.();
		input?.focus();
	}

	function cancelEdit() {
		oncanceledit?.();
		input?.focus();
	}
</script>

<div class="composer" class:compact role="presentation" {onkeydown}>
	{#if menu}
		<div class="menu" class:glass={!compact} class:few={plain} id="composer-menu-{uid}" transition:fly={{ y: 10, duration: 220 }}>
			<button type="button" onclick={() => choose(() => photoInput?.click())}>
				<Icon name="image" size={22} />
				Photos
			</button>
			<button type="button" onclick={() => choose(() => fileInput?.click())}>
				<Icon name="paperclip" size={22} />
				Files
			</button>
			{#if !plain && onlocation}
				<button type="button" onclick={() => choose(onlocation)}>
					<Icon name="map-pin" size={22} />
					Location
				</button>
			{/if}
			{#if !plain}
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
			{/if}
		</div>
	{/if}
	<input bind:this={photoInput} type="file" accept="image/*" multiple hidden onchange={onpick} />
	<input bind:this={fileInput} type="file" multiple hidden onchange={onpick} />

	{#if hasDraft && !editing}
		<div transition:fly={{ y: 8, duration: 180 }}>
			<AttachmentTray draft={picked} {compact} />
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

	{#if replying && !editing}
		<div class="replying" transition:fly={{ y: 8, duration: 180 }}>
			<span class="bar-line" aria-hidden="true"></span>
			<span class="replying-text" id="composer-replying-{uid}">
				<span class="replying-label">Replying to {replying.name === 'You' ? 'yourself' : replying.name}</span>
				<span class="replying-snippet">{replying.snippet}</span>
			</span>
			{#if canCancelReply}
				<button type="button" class="cancel-edit" aria-label={cancelReplyLabel} title={cancelReplyLabel} onclick={cancelReply}>
					<Icon name="x" size={16} strokeWidth={2.4} />
				</button>
			{/if}
		</div>
	{/if}

	<form class="bar" class:glass={!compact} onsubmit={submit}>
		<button
			type="button"
			class="plus"
			class:open={menu}
			aria-expanded={menu}
			aria-controls="composer-menu-{uid}"
			aria-label={plain ? 'Attach' : 'Attach, or add a poll, proposal or event'}
			title={plain ? 'Attach' : undefined}
			disabled={!!editing}
			onclick={() => (menu = !menu)}
		>
			<Icon name="plus" size={20} strokeWidth={2.4} />
		</button>
		<label for="composer-{uid}" class="sr-only">Message</label>
		<input
			bind:this={input}
			id="composer-{uid}"
			bind:value={body}
			placeholder={editing ? (editingCaption ? 'Edit the caption' : 'Edit your message') : hasDraft ? 'Add a caption' : placeholder}
			{onpaste}
			autocomplete="off"
			enterkeyhint={editing ? 'done' : 'send'}
			maxlength={4000}
			aria-describedby={editing ? `composer-editing-${uid}` : replying ? `composer-replying-${uid}` : undefined}
		/>
		{#if editing}
			<button type="submit" class="send" aria-label="Save" title="Save" disabled={(!body.trim() && !editingCaption) || saving}>
				<Icon name="check" size={20} strokeWidth={2.4} />
			</button>
		{:else}
			<button type="submit" class="send" aria-label="Send" disabled={!canSend}>
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
	@media (min-width: 560px) {
		.menu:not(.few) {
			grid-template-columns: repeat(6, minmax(0, 1fr));
		}
	}
	.compact .menu:not(.few) {
		grid-template-columns: repeat(3, minmax(0, 1fr));
	}
	.menu.few {
		grid-template-columns: repeat(2, minmax(0, 1fr));
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
	.replying {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 6px 4px 6px 12px;
		border-radius: 14px;
		background: var(--accent-soft);
	}
	.bar-line {
		width: 3px;
		align-self: stretch;
		border-radius: 2px;
		background: var(--accent-ink);
	}
	.replying-text {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.replying-label {
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.replying-label,
	.replying-snippet {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.replying-snippet {
		font-size: 12px;
		color: var(--muted);
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
	.replying:has(.replying-text:last-child) {
		padding-right: 12px;
		min-height: 48px;
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
	.plus:disabled {
		opacity: 0.45;
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
