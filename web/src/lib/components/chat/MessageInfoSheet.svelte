<script lang="ts">
	import type { Member, Message, MessageInfo } from '#lib/types';
	import { getMessageInfo } from '#lib/client/chat';
	import { useFormat } from '#lib/client/format';
	import { errorMessage } from '#lib/client/api';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';

	/** Who has seen / received one of your messages. */
	interface Props {
		open: boolean;
		message: Message | null;
		members: Map<string, Member>;
	}
	let { open = $bindable(), message, members }: Props = $props();

	const fmt = useFormat();
	let info = $state<MessageInfo | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		if (!open || !message) return;
		const id = message.id;
		info = null;
		error = null;
		getMessageInfo(id)
			.then((i) => (info = i))
			.catch((err) => (error = errorMessage(err)));
	});

	const name = (id: string) => members.get(id)?.name ?? 'Someone';
	const sentLabel = $derived.by(() => {
		if (!message) return '';
		const day = fmt.current.dayDivider(message.createdAt);
		return `Sent ${day === 'Today' || day === 'Yesterday' ? day.toLowerCase() : day} at ${fmt.current.time(message.createdAt)}`;
	});
	const groups = $derived(
		info
			? [
					{ title: 'Seen by', seen: true, rows: info.seen.map((s) => ({ id: s.userId, t: fmt.current.time(s.at) })) },
					{
						title: 'Delivered to, not seen yet',
						seen: false,
						rows: info.delivered.map((d) => ({ id: d.userId, t: `Delivered ${fmt.current.time(d.at)}` }))
					},
					{ title: 'Not delivered yet', seen: false, rows: info.pending.map((id) => ({ id, t: '' })) }
				].filter((g) => g.rows.length)
			: []
	);
</script>

<Sheet bind:open title="Message info">
	{#if message}
		<div class="bubble">{message.body ?? message.poll?.question ?? ''}</div>
		<span class="sent">{sentLabel}</span>
	{/if}
	{#if error}
		<p class="error" role="alert">{error}</p>
	{:else if !info}
		<div class="loading"><Spinner /></div>
	{:else if !groups.length}
		<p class="muted">Nobody else is in this group yet.</p>
	{:else}
		{#each groups as group (group.title)}
			<section class="group">
				<h3>
					<span class="tick" class:seen={group.seen}><Icon name="double-check" size={16} strokeWidth={2.4} /></span>
					<span class="title">{group.title}</span>
					<span>{group.rows.length}</span>
				</h3>
				<ul>
					{#each group.rows as row (row.id)}
						<li>
							<Avatar name={name(row.id)} id={row.id} image={members.get(row.id)?.image} size={32} />
							<span class="name">{name(row.id)}</span>
							<span class="time">{row.t}</span>
						</li>
					{/each}
				</ul>
			</section>
		{/each}
	{/if}
</Sheet>

<style>
	.bubble {
		align-self: flex-end;
		max-width: 85%;
		border-radius: 18px 18px 6px 18px;
		padding: 9px 12px;
		font-size: 14px;
		line-height: 1.4;
		background: var(--solid);
		color: var(--on-solid);
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}
	.sent {
		align-self: flex-end;
		margin-top: -8px;
		font-size: 11px;
		color: var(--muted);
	}
	.loading {
		display: flex;
		justify-content: center;
		padding: 20px;
		color: var(--muted);
	}
	.error {
		color: var(--warn-ink);
		font-weight: 600;
		font-size: 14px;
	}
	.group {
		border-radius: 22px;
		padding: 4px 14px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	h3 {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 10px 0 6px;
		font-family: var(--font-body);
		letter-spacing: 0;
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
	}
	.title {
		flex: 1;
	}
	.tick.seen {
		color: var(--accent-ink);
	}
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
	}
	li {
		display: flex;
		align-items: center;
		gap: 12px;
		min-height: 50px;
		border-top: 1px solid var(--line);
	}
	.name {
		flex: 1;
		font-size: 15px;
		font-weight: 600;
	}
	.time {
		font-size: 12px;
		color: var(--muted);
	}
</style>
