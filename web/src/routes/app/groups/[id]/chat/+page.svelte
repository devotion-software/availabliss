<script lang="ts">
	import { untrack } from 'svelte';
	import { page } from '$app/state';
	import { ChatThread } from '#lib/client/chat-thread.svelte';
	import { replyingTo } from '#lib/client/chat-messages';
	import { memberMap } from '#lib/client/members';
	import { firstName, listNames } from '#lib/client/format';
	import GroupTile from '#lib/components/ui/GroupTile.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import MessageList from '#lib/components/chat/MessageList.svelte';
	import Composer from '#lib/components/chat/Composer.svelte';
	import PollComposerSheet from '#lib/components/chat/PollComposerSheet.svelte';
	import LocationSheet from '#lib/components/chat/LocationSheet.svelte';
	import ChatSearchSheet from '#lib/components/chat/ChatSearchSheet.svelte';
	import ChatMediaSheet from '#lib/components/chat/ChatMediaSheet.svelte';
	import ProposeSheet from '#lib/components/events/ProposeSheet.svelte';
	import { dropFiles } from '#lib/components/chat/drop-files';

	let { data } = $props();

	let pollOpen = $state(false);
	let proposeOpen = $state(false);
	let locationOpen = $state(false);
	let searchOpen = $state(false);
	let mediaOpen = $state(false);
	let list: ReturnType<typeof MessageList> | undefined = $state();
	let composer: ReturnType<typeof Composer> | undefined = $state();

	const group = $derived(data.group);
	// Keyed on ids only, so refetching group details doesn't throw away the conversation.
	const groupId = $derived(data.group.id);
	const meId = $derived(data.user.id);
	const thread = $derived(
		new ChatThread(
			groupId,
			meId,
			untrack(() => data.group.members.length),
			untrack(() => data.initial ?? undefined)
		)
	);
	const others = $derived(
		group.members
			.filter((m) => m.userId !== data.user.id)
			.map((m) => firstName(m.name))
			.sort((a, b) => a.localeCompare(b))
	);
	const calendarHref = $derived(`/app/groups/${group.id}`);
	// Opened from Activity: back returns there, and the group's name opens its calendar.
	const fromActivity = $derived(page.url.searchParams.get('from') === 'activity');

	$effect(() => {
		const t = thread;
		if (!t.messages.length && !untrack(() => data.initial)) t.load();
		return t.listen();
	});
</script>

<svelte:head><title>{group.name} · Chat · Availabliss</title></svelte:head>

<div class="chat" use:dropFiles={(files) => composer?.addFiles(files)}>
	<header class="head glass">
		<span class="back" class:keep={fromActivity}>
			<IconButton
				href={fromActivity ? '/app/activity' : calendarHref}
				icon="chevron-left"
				label={fromActivity ? 'Back to activity' : 'Back to calendar'}
				variant="plain"
			/>
		</span>
		{#if fromActivity}
			<a class="group-link" href={calendarHref} aria-label="{group.name}, open the group">
				<GroupTile monogram={group.monogram} color={group.color} image={group.image} size={40} />
				<div class="titles">
					<h1>{group.name}</h1>
					<span>{others.length ? listNames(others) : 'Just you so far'}</span>
				</div>
				<Icon name="chevron-right" size={16} />
			</a>
		{:else}
			<GroupTile monogram={group.monogram} color={group.color} image={group.image} size={40} />
			<div class="titles">
				<h1>{group.name}</h1>
				<span>{others.length ? listNames(others) : 'Just you so far'}</span>
			</div>
		{/if}
		<IconButton icon="image" label="Photos and files" variant="plain" onclick={() => (mediaOpen = true)} />
		<IconButton icon="search" label="Search chat" variant="plain" onclick={() => (searchOpen = true)} />
		<span class="cal-link"><IconButton href={calendarHref} icon="calendar" label="Group calendar" variant="plain" /></span>
	</header>

	<MessageList bind:this={list} {thread} {group} meId={data.user.id} />

	<div class="composer">
		<Composer
			bind:this={composer}
			groupId={group.id}
			onsend={(b, ids) => thread.send(b, ids)}
			onpoll={() => (pollOpen = true)}
			onpropose={() => (proposeOpen = true)}
			onlocation={() => (locationOpen = true)}
			editing={thread.editing}
			onsaveedit={(b) => thread.saveEdit(b)}
			oncanceledit={() => thread.cancelEdit()}
			replying={replyingTo(thread.replyingTo, data.user.id, memberMap(group))}
			oncancelreply={() => thread.cancelReply()}
		/>
	</div>
</div>

<PollComposerSheet bind:open={pollOpen} onsubmit={(poll) => thread.sendPoll(poll)} />
<LocationSheet bind:open={locationOpen} onsend={(location) => thread.sendLocation(location)} />
<ChatMediaSheet bind:open={mediaOpen} {group} meId={data.user.id} onshowinchat={(id) => list?.showInChat(id)} />
<ChatSearchSheet bind:open={searchOpen} {group} meId={data.user.id} onpick={(id) => list?.showInChat(id)} />
<ProposeSheet bind:open={proposeOpen} {group} meId={data.user.id} initial={null} />

<style>
	/* Phone: its own screen over the calendar; the conversation fills the space down to the main tab bar. */
	.chat {
		height: calc(100dvh - max(20px, env(safe-area-inset-top)) - var(--tabbar-space) - env(safe-area-inset-bottom));
		min-height: 320px;
		display: flex;
		flex-direction: column;
	}
	/* Files dragged over the chat. */
	.chat:global([data-dropping]) {
		outline: 2px dashed var(--accent);
		outline-offset: -2px;
	}
	/* Back already goes to the calendar on phones. */
	.cal-link {
		display: none;
	}
	.head {
		margin: 0 0 10px;
		border-radius: 24px;
		padding: 8px 10px;
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.group-link {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 10px;
		color: inherit;
		text-decoration: none;
		border-radius: 14px;
	}
	.group-link :global(svg) {
		color: var(--muted);
		flex: none;
	}
	.group-link:hover h1 {
		text-decoration: underline;
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
		padding: 10px 0 0;
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
		.cal-link {
			display: contents;
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
		/* From Activity, back still leads there. */
		.back.keep {
			display: block;
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
