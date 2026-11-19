<script lang="ts">
	import type { Attachment } from '#lib/types';
	import { attachmentLabel, opensWallet, passCard } from '#lib/client/attachments';
	import Icon from '#lib/components/ui/Icon.svelte';

	/**
	 * A Wallet pass drawn like the pass (its colours, logo, headline, date and fields), with Add to
	 * Apple Wallet or Download. In chat bubbles and the chat's Passes tab.
	 */
	interface Props {
		/** A `pass` attachment (with its `pass` details). */
		attachment: Attachment;
	}
	let { attachment }: Props = $props();

	/** Safari (and any browser on iPhone / iPad) shows a linked .pkpass with Add to Wallet; others download it. */
	const wallet = typeof navigator !== 'undefined' && opensWallet(navigator.userAgent);
</script>

{#if attachment.pass}
	{@const p = attachment.pass}
	{@const card = passCard(p)}
	<div
		class="pass"
		style:--pass-bg={p.backgroundColor}
		style:--pass-fg={p.foregroundColor}
		style:--pass-label={p.labelColor}
		role="group"
		aria-label={attachmentLabel(attachment)}
	>
		<div class="pass-head">
			{#if attachment.thumbnailUrl}<img
					class="pass-logo"
					src={attachment.thumbnailUrl}
					alt=""
					loading="lazy"
					decoding="async"
					draggable="false"
				/>{/if}
			<span class="pass-org">{card.organization}</span>
			{#if p.voided}
				<span class="pass-voided">Voided</span>
			{:else}
				<span class="pass-kind" aria-hidden="true"><Icon name={p.style === 'boardingPass' ? 'plane' : 'ticket'} size={15} /></span>
			{/if}
		</div>
		<div class="pass-main">
			<span class="pass-title">{card.headline}</span>
			{#if card.date}<span class="pass-date">{card.date}</span>{/if}
			{#if card.venue}<span class="pass-venue">{card.venue}</span>{/if}
		</div>
		{#if card.fields.length}
			<dl class="pass-fields">
				{#each card.fields as field, i (i)}
					<div>
						{#if field.label}<dt>{field.label}</dt>{/if}
						<dd>{field.value}</dd>
					</div>
				{/each}
			</dl>
		{/if}
		{#if wallet && p.count === 1}
			<a class="pass-action wallet" href={attachment.url}>
				<Icon name="ticket" size={16} />Add to Apple Wallet
			</a>
		{:else}
			<a class="pass-action" href={attachment.url} download={attachment.name}>
				<Icon name="download" size={16} />{p.count > 1 ? `Download ${p.count} passes` : 'Download pass'}
			</a>
		{/if}
	</div>
{/if}

<style>
	/* A Wallet pass, in its own colours (or the surface and ink when it has none). */
	.pass {
		--bg: var(--pass-bg, var(--surface));
		--fg: var(--pass-fg, var(--ink));
		--label: var(--pass-label, color-mix(in srgb, var(--fg) 72%, transparent));
		display: flex;
		flex-direction: column;
		gap: 10px;
		width: 240px;
		max-width: 100%;
		padding: 12px;
		border-radius: 14px;
		background: var(--bg);
		color: var(--fg);
		box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--ink) 8%, transparent);
	}
	.pass-head {
		display: flex;
		align-items: center;
		gap: 8px;
		min-width: 0;
	}
	.pass-logo {
		max-height: 24px;
		max-width: 100px;
		object-fit: contain;
		object-position: left center;
	}
	.pass-org {
		flex: 1;
		min-width: 0;
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-kind {
		display: flex;
		color: var(--label);
	}
	.pass-voided {
		font-size: 10px;
		font-weight: 700;
		padding: 1px 6px;
		border-radius: 999px;
		box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--fg) 60%, transparent);
	}
	.pass-main {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.pass-title {
		font-size: 16px;
		font-weight: 650;
		line-height: 1.25;
		overflow-wrap: anywhere;
	}
	.pass-date {
		font-size: 13px;
		font-weight: 600;
		color: var(--label);
	}
	.pass-venue {
		font-size: 12px;
		opacity: 0.85;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-fields {
		display: flex;
		gap: 14px;
		margin: 0;
		min-width: 0;
	}
	.pass-fields div {
		min-width: 0;
	}
	.pass-fields dt {
		font-size: 9px;
		font-weight: 600;
		letter-spacing: 0.04em;
		text-transform: uppercase;
		color: var(--label);
	}
	.pass-fields dd {
		margin: 0;
		font-size: 13px;
		font-weight: 600;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pass-action {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		min-height: 40px;
		border-radius: 10px;
		font-size: 14px;
		font-weight: 600;
		text-decoration: none;
		color: var(--fg);
		background: color-mix(in srgb, var(--fg) 14%, transparent);
		-webkit-touch-callout: none;
	}
	.pass-action:hover {
		filter: brightness(0.96);
	}
	.pass-action.wallet {
		background: #000;
		color: #fff;
	}
</style>
