<script lang="ts">
	import type { Group, GroupPreference, Weekday } from '#lib/types';
	import { addPreference, listPreferences, removePreference, updatePreference } from '#lib/client/groups';
	import { memberMap, memberLabel } from '#lib/client/members';
	import { live } from '#lib/client/live.svelte';
	import { toasts } from '#lib/client/toast.svelte';
	import Button from '#lib/components/ui/Button.svelte';
	import IconButton from '#lib/components/ui/IconButton.svelte';
	import Icon from '#lib/components/ui/Icon.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import TextField from '#lib/components/ui/TextField.svelte';
	import DateRangeCalendar from '#lib/components/ui/DateRangeCalendar.svelte';
	import DayPicker from '#lib/components/timing/DayPicker.svelte';

	/**
	 * Preferred times: each member can say when they'd rather meet ("weekends after 16:00",
	 * "12 – 14 Dec"). Guidelines only: nothing is blocked; the calendar marks and ranks the
	 * times that work and fit someone's preference.
	 */
	interface Props {
		group: Pick<Group, 'id' | 'members'>;
		meId: string;
	}
	let { group, meId }: Props = $props();

	const members = $derived(memberMap(group));
	let prefs = $state<GroupPreference[]>([]);
	let adding = $state(false);
	/** The preference being changed (the form is open for it), or null when adding a new one. */
	let editing = $state<GroupPreference | null>(null);
	let kind = $state<'weekly' | 'dates'>('weekly');
	let days = $state<Weekday[]>([5, 6]);
	let timed = $state<'any' | 'after' | 'between'>('any');
	let fromTime = $state('16:00');
	let toTime = $state('22:00');
	let fromDate = $state('');
	let toDate = $state('');
	let saving = $state(false);

	function load() {
		listPreferences(group.id)
			.then((p) => (prefs = p))
			.catch(() => {});
	}
	$effect(() => {
		void group.id;
		load();
		return live.on(['availability.changed'], (e) => {
			if (e.groupId === group.id) load();
		});
	});

	const mine = $derived(prefs.filter((p) => p.userId === meId));
	const others = $derived(prefs.filter((p) => p.userId !== meId));

	async function save(e: SubmitEvent) {
		e.preventDefault();
		saving = true;
		try {
			const body =
				kind === 'dates'
					? { kind, from: fromDate, to: toDate || fromDate }
					: { kind, days, window: timed === 'any' ? null : { from: fromTime, to: timed === 'after' ? '23:59' : toTime } };
			const input = body as Parameters<typeof addPreference>[1];
			if (editing) {
				const changed = await updatePreference(group.id, editing.id, input);
				prefs = prefs.map((p) => (p.id === changed.id ? changed : p));
				toasts.success(`Changed to: ${changed.label}`);
			} else {
				const made = await addPreference(group.id, input);
				prefs = [...prefs, made];
				toasts.success(`Added: ${made.label}`);
			}
			close();
		} catch (err) {
			toasts.error(err);
		} finally {
			saving = false;
		}
	}

	/** Opens the form for a new preference, with the usual starting values. */
	function startAdding() {
		editing = null;
		kind = 'weekly';
		days = [5, 6];
		timed = 'any';
		fromTime = '16:00';
		toTime = '22:00';
		fromDate = '';
		toDate = '';
		adding = true;
	}

	/** Opens the form filled in with one of your preferences, to change it in place. */
	function startEditing(p: GroupPreference) {
		editing = p;
		kind = p.kind;
		days = [...(p.days ?? [])] as Weekday[];
		timed = !p.window ? 'any' : p.window.to === '23:59' || p.window.to === '00:00' ? 'after' : 'between';
		fromTime = p.window?.from ?? '16:00';
		toTime = p.window && timed === 'between' ? p.window.to : '22:00';
		fromDate = p.from ?? '';
		toDate = p.to ?? '';
		adding = true;
	}

	function close() {
		adding = false;
		editing = null;
	}

	async function remove(p: GroupPreference) {
		try {
			await removePreference(group.id, p.id);
			prefs = prefs.filter((x) => x.id !== p.id);
		} catch (err) {
			toasts.error(err);
		}
	}
</script>

<section class="prefs glass" aria-labelledby="prefs-h">
	<div class="head">
		<h2 id="prefs-h"><Icon name="heart" size={16} /> Preferred times</h2>
		{#if !adding}<Button size="sm" variant="secondary" icon="plus" onclick={startAdding}>Add</Button>{/if}
	</div>
	<p class="lead">
		Say when you'd rather meet. It never rules a date out: times that work and fit someone's preference are marked and suggested first.
	</p>

	{#if adding}
		<form class="form" onsubmit={save}>
			<SegmentedControl
				options={[
					{ value: 'weekly', label: 'Repeating' },
					{ value: 'dates', label: 'Specific dates' }
				]}
				bind:value={kind}
				label="Kind of preference"
				size="sm"
			/>
			{#if kind === 'weekly'}
				<DayPicker bind:days label="Days you prefer" />
				<SegmentedControl
					options={[
						{ value: 'any', label: 'Any time' },
						{ value: 'after', label: 'After' },
						{ value: 'between', label: 'Between' }
					]}
					bind:value={timed}
					label="Time of day"
					size="sm"
				/>
				{#if timed !== 'any'}
					<div class="pair">
						<TextField label={timed === 'after' ? 'After' : 'From'} type="time" bind:value={fromTime} />
						{#if timed === 'between'}<TextField label="Until" type="time" bind:value={toTime} />{/if}
					</div>
				{/if}
			{:else}
				<DateRangeCalendar bind:from={fromDate} bind:to={toDate} label="Dates you prefer" />
			{/if}
			<div class="actions">
				<Button type="button" variant="ghost" size="sm" onclick={close}>Cancel</Button>
				<Button type="submit" size="sm" loading={saving} disabled={kind === 'dates' && !fromDate}>
					{editing ? 'Save changes' : 'Save preference'}
				</Button>
			</div>
		</form>
	{/if}

	{#if mine.length}
		<h3>Yours</h3>
		<ul>
			{#each mine as p (p.id)}
				<li>
					<span>{p.label}</span>
					<span class="row-actions">
						<IconButton icon="pencil" label="Change {p.label}" variant="plain" size={36} onclick={() => startEditing(p)} />
						<IconButton icon="x" label="Remove {p.label}" variant="plain" size={36} onclick={() => remove(p)} />
					</span>
				</li>
			{/each}
		</ul>
	{/if}
	{#if others.length}
		<h3>Everyone else</h3>
		<ul>
			{#each others as p (p.id)}
				<li><span>{p.label}</span><span class="who">{memberLabel(members, p.userId, meId, { first: true })}</span></li>
			{/each}
		</ul>
	{/if}
</section>

<style>
	.prefs {
		border-radius: 24px;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.row-actions {
		display: flex;
		flex: none;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	h2 {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 17px;
		font-weight: 650;
	}
	h2 :global(svg) {
		color: var(--accent-ink);
	}
	h3 {
		font-size: 12px;
		font-weight: 600;
		color: var(--muted);
		margin-top: 4px;
	}
	.lead {
		font-size: 13px;
		color: var(--muted);
	}
	.form {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 12px;
		border-radius: 18px;
		background: var(--surface);
	}
	.pair {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 8px;
	}
	.actions {
		display: flex;
		justify-content: flex-end;
		gap: 8px;
	}
	ul {
		list-style: none;
		display: flex;
		flex-direction: column;
	}
	li {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		min-height: 40px;
		font-size: 14px;
		font-weight: 500;
	}
	li + li {
		border-top: 1px solid var(--line);
	}
	.who {
		font-size: 12px;
		color: var(--muted);
	}
</style>
