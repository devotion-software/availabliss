<script lang="ts">
	import { untrack } from 'svelte';
	import type { Group, User } from '#lib/types';
	import { ChatThread } from '#lib/client/chat-thread.svelte';
	import { replyingTo } from '#lib/client/chat-messages';
	import { memberMap } from '#lib/client/members';
	import MessageList from './MessageList.svelte';
	import Composer from './Composer.svelte';
	import PollComposerSheet from './PollComposerSheet.svelte';
	import LocationSheet from './LocationSheet.svelte';
	import { dropFiles } from './drop-files';

	/** Compact chat beside the desktop group calendar (Web.dc.html). */
	interface Props {
		group: Group;
		me: User;
		onpropose: () => void;
	}
	let { group, me, onpropose }: Props = $props();

	let pollOpen = $state(false);
	let locationOpen = $state(false);
	let composer: ReturnType<typeof Composer> | undefined = $state();

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

<section class="panel glass" use:dropFiles={(files) => composer?.addFiles(files)} aria-labelledby="chat-panel-{group.id}">
	<header>
		<h2 id="chat-panel-{group.id}">Chat</h2>
		{#if thread.unseen}<span class="new">{thread.unseen} new</span>{/if}
		<a href="/app/groups/{group.id}/chat">Open<span class="sr-only"> full chat</span></a>
	</header>
	<div class="messages">
		<MessageList {thread} {group} meId={me.id} compact />
	</div>
	<Composer bind:this={composer} groupId={group.id} compact onsend={(b, ids) => thread.send(b, ids)} onpoll={() => (pollOpen = true)}
		{onpropose}
		onlocation={() => (locationOpen = true)}
		editing={thread.editing}
		onsaveedit={(b) => thread.saveEdit(b)}
		oncanceledit={() => thread.cancelEdit()}
		replying={replyingTo(thread.replyingTo, me.id, memberMap(group))}
		oncancelreply={() => thread.cancelReply()}
	/>
</section>

<PollComposerSheet bind:open={pollOpen} onsubmit={(poll) => thread.sendPoll(poll)} />
<LocationSheet bind:open={locationOpen} onsend={(location) => thread.sendLocation(location)} />

<style>
	.panel {
		border-radius: 28px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.panel:global([data-dropping]) {
		outline: 2px dashed var(--accent);
		outline-offset: -2px;
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
