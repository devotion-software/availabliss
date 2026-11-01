<script lang="ts">
	import { untrack } from 'svelte';
	import type { Group, User } from '#lib/types';
	import { ChatThread } from '#lib/client/chat-thread.svelte';
	import MessageList from './MessageList.svelte';
	import Composer from './Composer.svelte';
	import PollComposerSheet from './PollComposerSheet.svelte';

	/** Compact chat beside the desktop group calendar (Web.dc.html). */
	interface Props {
		group: Group;
		me: User;
		onpropose: () => void;
	}
	let { group, me, onpropose }: Props = $props();

	let pollOpen = $state(false);

	// One thread per group; recreated if you switch groups.
	// Keyed on ids only, so refetching group details doesn't throw away the conversation.
	const groupId = $derived(group.id);
	const meId = $derived(me.id);
	const thread = $derived(new ChatThread(groupId, meId, untrack(() => group.members.length)));

	$effect(() => {
		const t = thread;
		t.load();
		return t.listen();
	});
</script>

<section class="panel glass" aria-labelledby="chat-panel-{group.id}">
	<header>
		<h2 id="chat-panel-{group.id}">Chat</h2>
		{#if thread.unseen}<span class="new">{thread.unseen} new</span>{/if}
		<a href="/app/groups/{group.id}/chat">Open<span class="sr-only"> full chat</span></a>
	</header>
	<div class="messages">
		<MessageList {thread} {group} meId={me.id} compact />
	</div>
	<Composer groupId={group.id} compact onsend={(b) => thread.send(b)} onpoll={() => (pollOpen = true)} {onpropose} />
</section>

<PollComposerSheet bind:open={pollOpen} onsubmit={(poll) => thread.sendPoll(poll)} />

<style>
	.panel {
		border-radius: 28px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	header {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	h2 {
		flex: 1;
		font-size: 17px;
		font-weight: 650;
	}
	.new {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	header a {
		min-height: 32px;
		display: inline-flex;
		align-items: center;
		padding: 0 8px;
		border-radius: 10px;
		font-size: 13px;
		font-weight: 600;
		text-decoration: none;
	}
	header a:hover {
		background: var(--accent-soft);
		color: var(--accent-ink);
	}
	.messages {
		height: 420px;
		display: flex;
		flex-direction: column;
		min-height: 0;
		/* Soften messages scrolling out under the header. */
		-webkit-mask-image: linear-gradient(to bottom, transparent 0, #000 28px);
		mask-image: linear-gradient(to bottom, transparent 0, #000 28px);
	}
</style>
