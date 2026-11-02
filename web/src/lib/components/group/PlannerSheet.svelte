<script lang="ts">
	import type { PlannerResult, Slot } from '#lib/types';
	import { planWithAi } from '#lib/client/planner';
	import { toasts } from '#lib/client/toast.svelte';
	import Sheet from '#lib/components/ui/Sheet.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import Spinner from '#lib/components/ui/Spinner.svelte';

	/**
	 * The optional AI planner (Pro). Asks Claude for the best of the times that
	 * already work, things to do and next steps; one tap turns it into a proposal.
	 */
	interface Props {
		open: boolean;
		groupId: string;
		onpropose: (proposal: { slot: Slot; title: string; note: string }) => void;
	}
	let { open = $bindable(), groupId, onpropose }: Props = $props();

	let notes = $state('');
	let busy = $state(false);
	let result = $state<PlannerResult | null>(null);

	$effect.pre(() => {
		if (open) result = null;
	});

	async function ask(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		try {
			result = await planWithAi(groupId, notes.trim() || null);
		} catch (err) {
			toasts.error(err);
		} finally {
			busy = false;
		}
	}

	function propose(slot: Slot) {
		if (!result) return;
		open = false;
		onpropose({ slot, title: result.proposalTitle, note: result.proposalNote });
	}
</script>

<Sheet bind:open title="Plan with AI" description="Picks the best of the times that work for everyone, with ideas and next steps." width={560}>
	{#if !result}
		<form class="ask" onsubmit={ask}>
			<label class="field">
				<span>Anything to bear in mind? (optional)</span>
				<textarea bind:value={notes} rows="3" maxlength="500" placeholder="e.g. We're around Manchester, two of us have kids, keep it cheap"></textarea>
			</label>
			<p class="privacy">
				<Icon name="shield" size={14} />
				Sends this group's name, timing and free dates, plus your note, to Claude by Anthropic. Never anyone's calendar, busy time or names.
			</p>
			<Button type="submit" variant="accent" size="lg" block icon="sparkle" loading={busy}>Suggest a plan</Button>
			{#if busy}<p class="wait"><Spinner size={14} /> Weighing up the options. This can take a little while.</p>{/if}
		</form>
	{:else}
		<div class="result">
			<section class="best">
				<span class="label">Best time</span>
				<h3>{result.best.slot.label}</h3>
				<p>{result.best.why}</p>
				<Button variant="accent" icon="send" onclick={() => propose(result!.best.slot)}>Propose this</Button>
			</section>

			{#if result.alternatives.length}
				<section>
					<h4>Also works</h4>
					<ul class="alts">
						{#each result.alternatives as alt (alt.slot.start)}
							<li>
								<span class="alt-text"><strong>{alt.slot.label}</strong><span>{alt.why}</span></span>
								<Button size="sm" variant="secondary" onclick={() => propose(alt.slot)}>Propose</Button>
							</li>
						{/each}
					</ul>
				</section>
			{/if}

			{#if result.ideas.length}
				<section>
					<h4>Ideas</h4>
					<ul class="ideas">
						{#each result.ideas as idea (idea.title)}
							<li><strong>{idea.title}</strong> {idea.description}</li>
						{/each}
					</ul>
				</section>
			{/if}

			{#if result.checklist.length}
				<section>
					<h4>Next steps</h4>
					<ol class="steps">
						{#each result.checklist as step, i (i)}<li>{step}</li>{/each}
					</ol>
				</section>
			{/if}

			<Button variant="ghost" size="sm" icon="refresh" onclick={() => (result = null)}>Try again with different notes</Button>
		</div>
	{/if}
</Sheet>

<style>
	.ask,
	.result {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.field {
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 13px;
		font-weight: 600;
		color: var(--muted);
	}
	textarea {
		font: inherit;
		font-weight: 400;
		font-size: 15px;
		color: var(--ink);
		resize: vertical;
		padding: 12px 14px;
		border-radius: 14px;
		border: 1px solid var(--line-2);
		background: var(--surface);
	}
	.privacy {
		display: flex;
		gap: 6px;
		font-size: 12px;
		color: var(--muted);
		line-height: 1.4;
	}
	.privacy :global(svg) {
		flex: none;
		margin-top: 1px;
		color: var(--ok-ink);
	}
	.wait {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		font-size: 13px;
		color: var(--muted);
	}
	.best {
		display: flex;
		flex-direction: column;
		gap: 6px;
		align-items: flex-start;
		padding: 16px;
		border-radius: 20px;
		background: var(--accent-soft);
	}
	.label {
		font-size: 12px;
		font-weight: 600;
		color: var(--accent-ink);
	}
	.best h3 {
		font-size: 22px;
		font-weight: 700;
	}
	.best p {
		font-size: 14px;
		line-height: 1.45;
		margin-bottom: 4px;
	}
	h4 {
		font-size: 14px;
		font-weight: 700;
		margin-bottom: 6px;
	}
	ul,
	ol {
		margin: 0;
		padding: 0;
	}
	.alts {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.alts li {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.alt-text {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		font-size: 13px;
		color: var(--muted);
	}
	.alt-text strong {
		color: var(--ink);
		font-size: 14px;
	}
	.ideas {
		list-style: none;
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 14px;
		line-height: 1.45;
	}
	.steps {
		padding-left: 20px;
		display: flex;
		flex-direction: column;
		gap: 4px;
		font-size: 14px;
	}
</style>
