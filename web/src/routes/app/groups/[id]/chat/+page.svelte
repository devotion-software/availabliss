<script lang="ts">
	import { untrack } from 'svelte';
	import { ChatThread } from '#lib/client/chat-thread.svelte';
	import { firstName, listNames } from '#lib/client/format';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import MessageList from '#lib/components/chat/MessageList.svelte';
	import Composer from '#lib/components/chat/Composer.svelte';
	import PollComposerSheet from '#lib/components/chat/PollComposerSheet.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';

	let { data } = $props();

	let pollOpen = $state(false);
	let proposeOpen = $state(false);

	const group = $derived(data.group);
	// Keyed on ids only, so refetching group details doesn't throw away the conversation.
	const groupId = $derived(data.group.id);
	const meId = $derived(data.user.id);
	const thread = $derived(
		new ChatThread(groupId, meId, untrack(() => data.group.members.length), untrack(() => data.initial ?? undefined))
	);
	const others = $derived(
		group.members
			.filter((m) => m.userId !== data.user.id)
			.map((m) => firstName(m.name))
			.sort((a, b) => a.localeCompare(b))
	);
	const calendarHref = $derived(`/app/groups/${group.id}`);

	$effect(() => {
		const t = thread;
		if (!t.messages.length && !untrack(() => data.initial)) t.load();
		return t.listen();
	});
</script>

<svelte:head><title>{group.name} · Chat · Availabliss</title></svelte:head>

<div class="chat">
	<header class="head glass">
		<span class="back"><IconButton href={calendarHref} icon="chevron-left" label="Back to calendar" variant="plain" /></span>
		<GroupTile monogram={group.monogram} color={group.color} size={40} />
		<div class="titles">
			<h1>{group.name}</h1>
			<span>{others.length ? listNames(others) : 'Just you so far'}</span>
		</div>
		<IconButton href={calendarHref} icon="calendar" label="Group calendar" variant="plain" />
	</header>

	<MessageList {thread} {group} meId={data.user.id} />

	<div class="composer">
		<Composer
			groupId={group.id}
			onsend={(b) => thread.send(b)}
			onpoll={() => (pollOpen = true)}
			onpropose={() => (proposeOpen = true)}
		/>
	</div>
</div>

<PollComposerSheet bind:open={pollOpen} onsubmit={(poll) => thread.sendPoll(poll)} />
<ProposeSheet bind:open={proposeOpen} groupId={group.id} initial={null} />

<style>
	/* Phone: a full-screen conversation above the orbs (Chat.dc.html). */
	.chat {
		position: fixed;
		inset: 0;
		z-index: 2;
		display: flex;
		flex-direction: column;
		padding-top: max(14px, env(safe-area-inset-top));
	}
	.head {
		margin: 0 12px;
		border-radius: 24px;
		padding: 8px 10px;
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	h1 {
		font-family: var(--font-body);
		letter-spacing: 0;
		font-size: 16px;
		font-weight: 650;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.titles span {
		font-size: 12px;
		color: var(--muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.composer {
		padding: 10px 12px max(18px, env(safe-area-inset-bottom));
	}

	/* Desktop: a tall glass panel in the content column, beside the groups sidebar. */
	@media (min-width: 900px) {
		.chat {
			position: relative;
			inset: auto;
			z-index: auto;
			height: calc(100dvh - 104px);
			min-height: 560px;
			padding: 0;
			border-radius: 28px;
			background: var(--glass);
			-webkit-backdrop-filter: var(--blur);
			backdrop-filter: var(--blur);
			border: 1px solid var(--glass-border);
			box-shadow: var(--glass-shadow);
			overflow: hidden;
		}
		.head {
			margin: 12px 12px 0;
			background: var(--surface);
			box-shadow: none;
			backdrop-filter: none;
			-webkit-backdrop-filter: none;
		}
		.back {
			display: none;
		}
		h1 {
			font-family: var(--font-display);
			letter-spacing: -0.025em;
			font-size: 20px;
		}
		.composer {
			padding: 10px 12px 12px;
		}
	}
</style>
