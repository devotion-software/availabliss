<script lang="ts">
	import type { Group, Message } from '#lib/types';
	import { searchMessages } from '#lib/client/chat';
	import { replyAuthorName, searchSnippet } from '#lib/client/chat-messages';
	import { memberMap } from '#lib/client/members';
	import { useFormat } from '#lib/client/format';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';

	/**
	 * Search in the chat: matches as you type (from 2 characters), newest first, each with who sent
	 * it, when, and the matching words picked out. Tapping one goes to it in the chat.
	 */
	interface Props {
		open: boolean;
		group: Group;
		meId: string;
		/** Goes to the message in the chat (the sheet has closed). */
		onpick: (messageId: string) => void;
	}
	let { open = $bindable(), group, meId, onpick }: Props = $props();

	const uid = $props.id();
	const fmt = useFormat();
	const members = $derived(memberMap(group));

	let query = $state('');
	let results = $state<Message[] | null>(null);
	let hasMore = $state(false);
	let searching = $state(false);
	let failed = $state(false);
	let input: HTMLInputElement | undefined = $state();

	$effect(() => {
		if (open) setTimeout(() => input?.focus(), 50);
	});

	// As you type: a moment after you stop, cancelling the search before.
	$effect(() => {
		const q = query.trim();
		const groupId = group.id;
		if (q.length < 2) {
			results = null;
			searching = false;
			return;
		}
		const ctrl = new AbortController();
		searching = true;
		const timer = setTimeout(async () => {
			try {
				const page = await searchMessages(groupId, q, { signal: ctrl.signal });
				results = page.messages;
				hasMore = page.hasMore;
				failed = false;
			} catch {
				if (ctrl.signal.aborted) return;
				failed = true;
			}
			searching = false;
		}, 300);
		return () => {
			clearTimeout(timer);
			ctrl.abort();
		};
	});

	const author = (m: Message) => replyAuthorName(m, meId, members);

	function pick(m: Message) {
		open = false;
		onpick(m.id);
	}
</script>

<Sheet bind:open title="Search chat" width={480} tall>
	<div class="content">
		<form class="search" role="search" onsubmit={(e) => e.preventDefault()}>
			<Icon name="search" size={18} />
			<label for="chat-search-{uid}" class="sr-only">Search messages</label>
			<input
				id="chat-search-{uid}"
				bind:this={input}
				type="search"
				bind:value={query}
				placeholder="Messages, polls, places, files"
				autocomplete="off"
				enterkeyhint="search"
				maxlength={100}
			/>
			{#if searching}
				<Spinner size={16} />
			{:else if query}
				<button type="button" class="clear" aria-label="Clear search" onclick={() => (query = '')}>
					<Icon name="x" size={14} strokeWidth={2.4} />
				</button>
			{/if}
		</form>

		<div aria-live="polite">
			{#if failed}
				<p class="note">Couldn’t search. Check your connection and try again.</p>
			{:else if results && !results.length}
				<p class="note">No messages with “{query.trim()}”.</p>
			{:else if results}
				<ul class="results">
					{#each results as m (m.id)}
						{@const s = searchSnippet(m, query)}
						<li>
							<button type="button" onclick={() => pick(m)}>
								<span class="meta"><span class="name">{author(m)}</span><span>{fmt.current.dayDivider(m.createdAt)}</span></span>
								<span class="text">
									{#if m.kind === 'poll'}<Icon name="poll" size={13} />{/if}{s.before}<mark>{s.match}</mark>{s.after}
								</span>
							</button>
						</li>
					{/each}
				</ul>
				{#if hasMore}<p class="note">Showing the newest {results.length}. Search for something more specific to narrow it down.</p>{/if}
			{:else}
				<p class="note">Search for at least 2 characters.</p>
			{/if}
		</div>
	</div>
</Sheet>

<style>
	.content {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.search {
		display: flex;
		align-items: center;
		gap: 8px;
		height: 46px;
		padding: 0 12px 0 14px;
		border-radius: 14px;
		border: 1px solid var(--line);
		background: var(--surface);
		color: var(--muted);
	}
	.search:focus-within {
		border-color: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
	.search input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--ink);
		font: inherit;
		font-size: 16px;
		outline: none;
	}
	.search input::-webkit-search-cancel-button {
		display: none;
	}
	.clear {
		width: 28px;
		height: 28px;
		border-radius: 50%;
		display: flex;
		align-items: center;
		justify-content: center;
		color: var(--muted);
	}
	.clear:hover {
		background: var(--accent-soft);
	}
	.results {
		list-style: none;
		margin: 0;
		padding: 0 14px;
		border-radius: 18px;
		background: var(--surface);
	}
	.results li + li {
		border-top: 1px solid var(--line);
	}
	.results button {
		width: 100%;
		min-height: 56px;
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: 10px 0;
		text-align: left;
		font: inherit;
		color: var(--ink);
	}
	.meta {
		display: flex;
		justify-content: space-between;
		gap: 8px;
		font-size: 12px;
		color: var(--muted);
	}
	.meta .name {
		font-weight: 600;
		color: var(--ink);
	}
	.text {
		font-size: 15px;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		overflow-wrap: anywhere;
	}
	.text :global(svg) {
		vertical-align: -1px;
		margin-right: 4px;
		color: var(--muted);
	}
	mark {
		background: var(--accent-soft);
		color: var(--accent-ink);
		border-radius: 4px;
		padding: 0 1px;
	}
	.note {
		margin: 0;
		font-size: 13px;
		color: var(--muted);
	}
</style>
