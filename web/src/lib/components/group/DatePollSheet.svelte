<script lang="ts">
	import { goto } from '$app/navigation';
	import type { Slot } from '#lib/types';
	import { sendPoll } from '#lib/client/chat';
	import { useFormat } from '#lib/client/format';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';

	/**
	 * "Ask the group": the times shortlisted on the calendar as a date poll in the chat
	 * (pick any that work), then off to the chat to see it.
	 */
	interface Props {
		open: boolean;
		groupId: string;
		/** The shortlist (2–5 times); removing one here removes it from the shortlist. */
		slots: Slot[];
		onremove: (slot: Slot) => void;
		/** Posted: the shortlist can be cleared. */
		onposted: () => void;
	}
	let { open = $bindable(), groupId, slots, onremove, onposted }: Props = $props();

	const fmt = useFormat();
	let question = $state('Which of these works?');
	let error = $state<string | null>(null);
	let busy = $state(false);

	$effect.pre(() => {
		if (!open) return;
		question = 'Which of these works?';
		error = null;
	});

	// In time order, as the poll lists them.
	const sorted = $derived([...slots].sort((a, b) => a.start.localeCompare(b.start)));

	/** A time from midnight to midnight (one day or several) is all day. */
	const allDay = (s: Slot) => fmt.current.time(s.start) === '00:00' && fmt.current.time(s.end) === '00:00';

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		if (!question.trim()) {
			error = 'Ask a question';
			return;
		}
		if (sorted.length < 2) {
			error = 'Pick at least two times';
			return;
		}
		busy = true;
		try {
			await sendPoll(groupId, {
				question: question.trim(),
				slots: sorted.map((s) => ({ start: s.start, end: s.end, allDay: allDay(s) })),
				multi: true
			});
			open = false;
			onposted();
			await goto(`/app/groups/${groupId}/chat`);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}
</script>

<Sheet bind:open title="Ask the group" description="Posts a poll in the chat. Everyone can pick all the times that work.">
	<form class="form" onsubmit={submit} novalidate>
		<TextField label="Question" bind:value={question} maxlength={200} />
		<fieldset>
			<legend class="eyebrow">{sorted.length} times</legend>
			<ul>
				{#each sorted as slot (slot.start + slot.end)}
					<li>
						<span class="when">{fmt.current.when(slot.start, slot.end, allDay(slot))}</span>
						{#if sorted.length > 2}
							<IconButton icon="x" label="Remove {slot.label}" variant="line" size={36} iconSize={13} onclick={() => onremove(slot)} />
						{/if}
					</li>
				{/each}
			</ul>
		</fieldset>
		{#if error}<p class="error" role="alert">{error}</p>{/if}
		<Button type="submit" variant="accent" size="lg" block loading={busy} icon="poll">Post poll</Button>
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
	}
	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	li {
		min-height: 46px;
		border-radius: 14px;
		padding: 4px 6px 4px 14px;
		display: flex;
		align-items: center;
		gap: 8px;
		background: var(--surface);
		border: 1px solid var(--glass-border);
	}
	.when {
		flex: 1;
		font-size: 14px;
		font-weight: 600;
	}
	.error {
		font-size: 13px;
		font-weight: 600;
		color: var(--warn-ink);
	}
</style>
