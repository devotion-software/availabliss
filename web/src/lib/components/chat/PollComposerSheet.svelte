<script lang="ts">
	import { createMessageSchema } from '#lib/schemas';
	import type { PollInput, PollPatch } from '#lib/client/chat';
	import type { Poll } from '#lib/types';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';

	interface Props {
		open: boolean;
		onsubmit?: (poll: PollInput) => Promise<unknown>;
		/** Editing your own poll: starts from it, and saves through `onsave` instead of posting. */
		editing?: Poll | null;
		onsave?: (patch: PollPatch) => Promise<unknown>;
	}
	let { open = $bindable(), onsubmit, editing = null, onsave }: Props = $props();

	const uid = $props.id();
	let question = $state('');
	// Each option keeps its id when editing, so its votes stay.
	let options = $state<{ id?: string; label: string }[]>([{ label: '' }, { label: '' }]);
	let multi = $state(false);
	let error = $state<string | null>(null);
	let busy = $state(false);
	// A date poll's options are times from the calendar: only its question and pick-one / pick-any change.
	const dated = $derived(!!editing?.options.some((o) => o.slot));

	$effect.pre(() => {
		if (!open) return;
		question = editing?.question ?? '';
		options = editing ? editing.options.map((o) => ({ id: o.id, label: o.label })) : [{ label: '' }, { label: '' }];
		multi = editing?.multi ?? false;
		error = null;
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		const labels = options.map((o) => o.label.trim());
		if (editing && onsave) {
			const kept = options.map((o, i) => ({ ...o, label: labels[i] })).filter((o) => o.label);
			if (!question.trim()) return void (error = 'Ask a question');
			if (!dated && kept.length < 2) return void (error = 'Add at least two options');
			busy = true;
			try {
				await onsave({ question: question.trim(), multi, ...(!dated && { options: kept }) });
				open = false;
			} catch {
				// The thread toasts it; the sheet stays open to try again.
			} finally {
				busy = false;
			}
			return;
		}
		const poll = { question, options: labels.filter(Boolean), multi };
		const parsed = createMessageSchema.safeParse({ poll });
		if (!parsed.success) {
			const issue = parsed.error.issues.find((i) => i.path.includes('poll')) ?? parsed.error.issues[0];
			error = issue?.path.includes('options')
				? 'Add at least two options'
				: poll.question.trim()
					? (issue?.message ?? 'Check the poll')
					: 'Ask a question';
			return;
		}
		busy = true;
		try {
			await onsubmit?.(poll);
			open = false;
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title={editing ? 'Edit poll' : 'New poll'}>
	<form class="form" onsubmit={submit} novalidate>
		<TextField label="Question" bind:value={question} placeholder="e.g. Where should we stay?" maxlength={200} autofocus />
		{#if !dated}<fieldset>
				<legend class="eyebrow">Options</legend>
				{#each options as _, i (i)}
					<div class="option">
						<TextField label="Option {i + 1}" hideLabel bind:value={options[i].label} placeholder="Option {i + 1}" maxlength={100} />
						{#if options.length > 2}
							<IconButton icon="x" label="Remove option {i + 1}" variant="line" size={40} iconSize={14} onclick={() => options.splice(i, 1)} />
						{/if}
					</div>
				{/each}
				{#if options.length < 10}
					<Button variant="ghost" size="sm" icon="plus" onclick={() => options.push({ label: '' })}>Add option</Button>
				{/if}
			</fieldset>{/if}
		<div class="row">
			<span id="multi-{uid}">Allow more than one choice</span>
			<Toggle bind:checked={multi} labelledby="multi-{uid}" />
		</div>
		{#if error}<p class="error" role="alert">{error}</p>{/if}
		{#if editing && !dated}<p class="hint">Votes stay on options you rename. Removing an option removes its votes.</p>{/if}
		<Button type="submit" variant="accent" size="lg" block loading={busy}>{editing ? 'Save' : 'Post poll'}</Button>
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
	.hint {
		font-size: 12px;
		color: var(--muted);
	}
</style>
