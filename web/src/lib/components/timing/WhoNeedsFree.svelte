<script lang="ts">
	import type { Member, Requirement } from '#lib/types';
	import Avatar from '#lib/components/ui/Avatar.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import Toggle from '#lib/components/ui/Toggle.svelte';

	/**
	 * "Who needs to be free": everyone, or at least N of the members who aren't optional
	 * (`quorum`), plus members marked optional, who never stop a time from working.
	 */
	interface Props {
		requirement: Requirement;
		members: Member[];
		meId?: string;
		readonly?: boolean;
	}
	let { requirement = $bindable(), members, meId, readonly = false }: Props = $props();
	const uid = $props.id();

	type Mode = 'everyone' | 'most';

	const optional = $derived(new Set(requirement.optionalMemberIds ?? []));
	/** Members who count towards the quorum (not optional). */
	const required = $derived(members.filter((m) => !optional.has(m.userId)).length);
	const mode = $derived<Mode>(requirement.quorum == null ? 'everyone' : 'most');
	const quorum = $derived(requirement.quorum ?? required);

	const modes = $derived([
		{ value: 'everyone' as const, label: 'Everyone' },
		{ value: 'most' as const, label: `At least ${mode === 'most' ? quorum : Math.max(1, required - 1)} of ${required}` }
	]);

	function setMode(next: Mode) {
		// Left out rather than null, so switching back to Everyone leaves the timing as it was.
		if (next === 'everyone') delete requirement.quorum;
		else requirement.quorum = Math.max(1, required - 1);
	}
	function step(by: number) {
		requirement.quorum = Math.min(Math.max(1, quorum + by), Math.max(1, required));
	}
	function setOptional(userId: string, on: boolean) {
		const next = new Set(optional);
		if (on) next.add(userId);
		else next.delete(userId);
		// Keep only people still in the group, in member order.
		const ids = members.map((m) => m.userId).filter((id) => next.has(id));
		if (ids.length) requirement.optionalMemberIds = ids;
		else delete requirement.optionalMemberIds;
		// The quorum can't ask for more people than are required.
		const left = members.length - ids.length;
		if (requirement.quorum != null && requirement.quorum > left) requirement.quorum = Math.max(1, left);
	}
</script>

<section class="glass card">
	<h2 class="eyebrow">Who needs to be free</h2>
	<SegmentedControl
		options={modes}
		value={mode}
		label="Who needs to be free"
		variant="soft"
		size="sm"
		block
		onchange={setMode}
	/>
	{#if mode === 'most'}
		<div class="stepper" role="group" aria-labelledby="q-{uid}">
			<span id="q-{uid}" class="q-label">
				<strong>{quorum}</strong> of {required} people
			</span>
			<div class="buttons">
				<IconButton icon="minus" label="Fewer people" variant="line" size={36} disabled={readonly || quorum <= 1} onclick={() => step(-1)} />
				<IconButton icon="plus" label="More people" variant="line" size={36} disabled={readonly || quorum >= required} onclick={() => step(1)} />
			</div>
		</div>
		<p class="plain">A time works when at least {quorum} of the {required} {required === 1 ? 'person' : 'people'} who aren't optional {quorum === 1 ? 'is' : 'are'} free.</p>
	{:else}
		<p class="plain">A time only works when everyone who isn't optional is free.</p>
	{/if}

	<ul class="members">
		{#each members as m (m.userId)}
			<li class="member">
				<Avatar name={m.name} id={m.userId} image={m.image} size={32} />
				<span class="name" id="opt-{uid}-{m.userId}">{m.name}{m.userId === meId ? ' (you)' : ''}</span>
				<span class="tag" class:on={optional.has(m.userId)}>Optional</span>
				<Toggle
					checked={optional.has(m.userId)}
					label="{m.name} is optional"
					disabled={readonly}
					onchange={(on) => setOptional(m.userId, on)}
				/>
			</li>
		{/each}
	</ul>
	<p class="plain">Optional people never stop a time from working.</p>
</section>

<style>
	.card {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.stepper {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		min-height: 44px;
	}
	.q-label {
		font-size: 15px;
		font-weight: 600;
	}
	.buttons {
		display: flex;
		gap: 8px;
	}
	.plain {
		font-size: 13px;
		color: var(--muted);
	}
	.members {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
	}
	.member {
		display: flex;
		align-items: center;
		gap: 10px;
		min-height: 48px;
	}
	.member + .member {
		border-top: 1px solid var(--line);
	}
	.name {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.tag {
		font-size: 12px;
		color: var(--muted);
		visibility: hidden;
	}
	.tag.on {
		visibility: visible;
	}
</style>
