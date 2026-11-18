<script lang="ts">
	import type { Group } from '#lib/types';
	import { detailLabel, lengthLabel } from '#lib/client/requirements';
	import Breadcrumbs from '#lib/components/ui/Breadcrumbs.svelte';
	import SegmentedControl from '#lib/components/ui/SegmentedControl.svelte';
	import { CALENDAR_VIEWS, type CalendarView } from './views';

	/** The group page's desktop header: name, timing, members and the Month / Week switch. Hidden on phones. */
	interface Props {
		group: Pick<Group, 'id' | 'name' | 'requirement' | 'members'>;
		isAdmin: boolean;
		view: CalendarView;
	}
	let { group, isAdmin, view = $bindable() }: Props = $props();
	const base = $derived(`/app/groups/${group.id}`);
</script>

<header class="desk-head">
	<div class="titles">
		<Breadcrumbs items={[{ label: 'Groups', href: '/app' }, { label: group.name }]} />
		<h1>{group.name}</h1>
		<span class="sub">
			{lengthLabel(group.requirement)} · {detailLabel(group.requirement)} ·
			<a href="{base}/timing">{isAdmin ? 'Edit timing' : 'See timing'}</a> ·
			<a href="{base}/members">{group.members.length} members</a>
		</span>
	</div>
	<SegmentedControl options={CALENDAR_VIEWS} bind:value={view} label="Calendar view" />
</header>

<style>
	.desk-head {
		display: none;
		align-items: flex-end;
		gap: 14px;
		flex-wrap: wrap;
	}
	.titles {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	h1 {
		font-size: 22px;
		font-weight: 700;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.sub {
		font-size: 12px;
		color: var(--muted);
	}
	@media (min-width: 900px) {
		.desk-head {
			display: flex;
		}
		h1 {
			font-size: 34px;
			font-weight: 750;
			line-height: 1.05;
			white-space: normal;
		}
		.desk-head .sub {
			font-size: 14px;
			margin-top: 4px;
		}
	}
</style>
