<script lang="ts">
	import { untrack } from 'svelte';
	import type { Group, GroupMediaItem } from '#lib/types';
	import { listGroupMedia } from '#lib/client/chat';
	import { attachmentLabel, bubbleSrc, formatBytes, formatDuration, middleTruncate } from '#lib/client/attachments';
	import { replyAuthorName } from '#lib/client/chat-messages';
	import { memberMap } from '#lib/client/members';
	import { useFormat } from '#lib/client/format';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import AttachmentViewer from './AttachmentViewer.svelte';
	import FileIcon from './FileIcon.svelte';
	import PassCard from './PassCard.svelte';

	/**
	 * Everything sent in the chat: Photos (a grid, newest first, opening the full-screen viewer),
	 * Files (with who sent each and when, downloading on a tap) and, when any have been sent, Passes
	 * (each drawn like the pass, with Add to Wallet). More load as you reach the end.
	 */
	interface Props {
		open: boolean;
		group: Group;
		meId: string;
		/** Goes to the message a photo or file was sent in (the sheet has closed). */
		onshowinchat: (messageId: string) => void;
	}
	let { open = $bindable(), group, meId, onshowinchat }: Props = $props();

	type Kind = 'media' | 'files' | 'passes';
	const fmt = useFormat();
	const members = $derived(memberMap(group));

	let kind = $state<Kind>('media');
	const pages = $state<Record<Kind, { items: GroupMediaItem[]; hasMore: boolean; loaded: boolean; loading: boolean; failed: boolean }>>({
		media: { items: [], hasMore: false, loaded: false, loading: false, failed: false },
		files: { items: [], hasMore: false, loaded: false, loading: false, failed: false },
		passes: { items: [], hasMore: false, loaded: false, loading: false, failed: false }
	});
	/** Whether the chat has any passes (from the first page of any tab): the Passes tab only shows then. */
	let hasPasses = $state(false);
	const page = $derived(pages[kind]);
	let viewing = $state<number | null>(null);
	let sentinel: HTMLElement | undefined = $state();

	async function load(k: Kind, more = false) {
		const p = pages[k];
		if (p.loading || (more && !p.hasMore)) return;
		p.loading = true;
		try {
			const res = await listGroupMedia(group.id, k, more ? p.items.at(-1)?.attachment.id : undefined);
			p.items = more ? [...p.items, ...res.items] : res.items;
			p.hasMore = res.hasMore;
			if (res.hasPasses !== undefined) hasPasses = res.hasPasses;
			p.loaded = true;
			p.failed = false;
		} catch {
			p.failed = true;
		} finally {
			p.loading = false;
		}
	}

	// Each time it opens: fresh from the newest (and each tab the first time it's shown).
	$effect(() => {
		if (!open) return;
		untrack(() => {
			for (const k of ['media', 'files', 'passes'] as const) pages[k].loaded = false;
		});
	});
	$effect(() => {
		const k = kind;
		if (open && !pages[k].loaded) untrack(() => load(k));
	});

	// The next page as the end of the list comes into view.
	$effect(() => {
		const el = sentinel;
		const k = kind;
		if (!el) return;
		const io = new IntersectionObserver((entries) => {
			if (entries.some((e) => e.isIntersecting)) load(k, true);
		});
		io.observe(el);
		return () => io.disconnect();
	});

	const pictures = $derived(pages.media.items.map((i) => i.attachment));
	const viewingItem = $derived(viewing !== null ? pages.media.items[viewing] : null);
	const author = (i: GroupMediaItem) => replyAuthorName(i, meId, members);

	function showInChat(messageId: string) {
		viewing = null;
		open = false;
		onshowinchat(messageId);
	}
</script>

<Sheet bind:open title="Photos and files" width={560} tall>
	<div class="content">
		<SegmentedControl
			options={[
				{ value: 'media', label: 'Photos', icon: 'image' },
				{ value: 'files', label: 'Files', icon: 'paperclip' },
				...(hasPasses || kind === 'passes' ? [{ value: 'passes' as const, label: 'Passes', icon: 'ticket' as const }] : [])
			]}
			bind:value={kind}
			label="Show"
			variant="soft"
			block
		/>

		{#if page.failed && !page.items.length}
			<p class="note">Couldn’t load them. Check your connection and try again.</p>
			<Button variant="secondary" onclick={() => load(kind)}>Try again</Button>
		{:else if page.loaded && !page.items.length}
			<p class="note">
				{kind === 'media'
					? 'No photos sent in this chat yet.'
					: kind === 'files'
						? 'No files sent in this chat yet.'
						: 'No passes sent in this chat yet.'}
			</p>
		{:else if kind === 'media'}
			<ul class="grid">
				{#each page.items as item, i (item.attachment.id)}
					<li>
						<button
							type="button"
							class="pic"
							aria-label="{attachmentLabel(item.attachment)} from {author(item)}, {fmt.current.dayDivider(item.createdAt)}"
							onclick={() => (viewing = i)}
						>
							{#if bubbleSrc(item.attachment)}<img src={bubbleSrc(item.attachment)} alt="" loading="lazy" decoding="async" />{/if}
							{#if item.attachment.kind === 'gif'}<span class="badge">GIF</span>{/if}
							{#if item.attachment.kind === 'video'}<span class="badge">▶ {formatDuration(item.attachment.duration)}</span>{/if}
						</button>
					</li>
				{/each}
			</ul>
		{:else if kind === 'passes'}
			<ul class="passes">
				{#each page.items as item (item.attachment.id)}
					<li>
						<PassCard attachment={item.attachment} />
						<div class="pass-meta">
							<span>{author(item)} · {fmt.current.dayDivider(item.createdAt)}</span>
							<button type="button" class="show" onclick={() => showInChat(item.messageId)}>Show in chat</button>
						</div>
					</li>
				{/each}
			</ul>
		{:else}
			<ul class="files">
				{#each page.items as item (item.attachment.id)}
					{@const f = item.attachment}
					<li>
						<a class="file" href={f.url} download={f.name} title={f.name} aria-label="{f.name}, {formatBytes(f.size)}, from {author(item)}. Download">
							<span class="file-icon"><FileIcon name={f.name} mime={f.mime} /></span>
							<span class="file-text">
								<span class="file-name">{middleTruncate(f.name, 44)}</span>
								<span class="file-meta">{formatBytes(f.size)} · {author(item)} · {fmt.current.dayDivider(item.createdAt)}</span>
							</span>
						</a>
						<button type="button" class="show" onclick={() => showInChat(item.messageId)}>Show in chat</button>
					</li>
				{/each}
			</ul>
		{/if}
		{#if page.loading}<div class="loading"><Spinner size={20} /></div>{/if}
		{#if page.hasMore}<div bind:this={sentinel} class="sentinel"></div>{/if}
	</div>
</Sheet>

{#if viewing !== null}
	<AttachmentViewer
		{pictures}
		bind:index={viewing}
		title={viewingItem ? `${author(viewingItem)}, ${fmt.current.dayDivider(viewingItem.createdAt)}` : 'Photo'}
		onclose={() => (viewing = null)}
		onshowinchat={(i) => showInChat(pages.media.items[i].messageId)}
	/>
{/if}

<style>
	.content {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.grid {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
		gap: 4px;
	}
	.pic {
		position: relative;
		display: block;
		width: 100%;
		aspect-ratio: 1;
		padding: 0;
		border-radius: 10px;
		overflow: hidden;
		background: color-mix(in srgb, var(--ink) 8%, transparent);
		cursor: zoom-in;
	}
	.pic img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}
	.badge {
		position: absolute;
		left: 6px;
		bottom: 6px;
		padding: 1px 6px;
		border-radius: 6px;
		font-size: 11px;
		font-weight: 700;
		color: #fff;
		background: rgba(0, 0, 0, 0.55);
	}
	.passes {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.passes li {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.pass-meta {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		padding-left: 4px;
		font-size: 12px;
		color: var(--muted);
	}
	.files {
		list-style: none;
		margin: 0;
		padding: 0 14px;
		border-radius: 18px;
		background: var(--surface);
	}
	.files li {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.files li + li {
		border-top: 1px solid var(--line);
	}
	.file {
		flex: 1;
		min-width: 0;
		min-height: 60px;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 8px 0;
		color: var(--ink);
		text-decoration: none;
	}
	.file-icon {
		flex: none;
		display: flex;
	}
	.file-text {
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.file-name {
		font-size: 15px;
		font-weight: 600;
		overflow-wrap: anywhere;
	}
	.file:hover .file-name {
		text-decoration: underline;
	}
	.file-meta {
		font-size: 12px;
		color: var(--muted);
	}
	.show {
		flex: none;
		min-height: 36px;
		padding: 0 10px;
		border-radius: 10px;
		font: inherit;
		font-size: 13px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.show:hover {
		background: var(--accent-soft);
	}
	.loading {
		display: flex;
		justify-content: center;
		padding: 8px;
	}
	.sentinel {
		height: 1px;
	}
	.note {
		margin: 0;
		font-size: 14px;
		color: var(--muted);
	}
</style>
