<script lang="ts">
	import { createMessageSchema } from '#lib/schemas';
	import type { PollInput } from '#lib/client/chat';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';

	interface Props {
		open: boolean;
		onsubmit: (poll: PollInput) => Promise<unknown>;
	}
	let { open = $bindable(), onsubmit }: Props = $props();

	const uid = $props.id();
	let question = $state('');
	let options = $state(['', '']);
	let multi = $state(false);
	let error = $state<string | null>(null);
	let busy = $state(false);

	$effect.pre(() => {
		if (!open) return;
		question = '';
		options = ['', ''];
		multi = false;
		error = null;
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const poll = { question, options: options.map((o) => o.trim()).filter(Boolean), multi };
		const parsed = createMessageSchema.safeParse({ poll });
		if (!parsed.success) {
			const issue = parsed.error.issues.find((i) => i.path.includes('poll')) ?? parsed.error.issues[0];
			error = issue?.path.includes('options') ? 'Add at least two options' : poll.question.trim() ? (issue?.message ?? 'Check the poll') : 'Ask a question';
			return;
		}
		busy = true;
		try {
			await onsubmit(poll);
			open = false;
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title="New poll">
	<form class="form" onsubmit={submit} novalidate>
		<TextField label="Question" bind:value={question} placeholder="e.g. Where should we stay?" maxlength={200} autofocus />
		<fieldset>
			<legend class="eyebrow">Options</legend>
			{#each options as _, i (i)}
				<div class="option">
					<TextField label="Option {i + 1}" hideLabel bind:value={options[i]} placeholder="Option {i + 1}" maxlength={100} />
					{#if options.length > 2}
						<IconButton icon="x" label="Remove option {i + 1}" variant="line" size={40} iconSize={14} onclick={() => options.splice(i, 1)} />
					{/if}
				</div>
			{/each}
			{#if options.length < 10}
				<Button variant="ghost" size="sm" icon="plus" onclick={() => options.push('')}>Add option</Button>
			{/if}
		</fieldset>
		<div class="row">
			<span id="multi-{uid}">Allow more than one choice</span>
			<Toggle bind:checked={multi} labelledby="multi-{uid}" />
		</div>
		{#if error}<p class="error" role="alert">{error}</p>{/if}
		<Button type="submit" variant="accent" size="lg" block loading={busy}>Post poll</Button>
	</form>
</Sheet>

<style>
	.form {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	fieldset {
		border: 0;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
		align-items: stretch;
	}
	fieldset :global(.btn) {
		align-self: flex-start;
	}
	.option {
		display: flex;
		align-items: center;
		gap: 8px;
	}
	.option :global(.field) {
		flex: 1;
	}
	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 44px;
		font-size: 15px;
		font-weight: 600;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
