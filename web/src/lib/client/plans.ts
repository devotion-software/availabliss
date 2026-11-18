import { api, apiUrl, type Fetch } from './api';
import type { GroupEvent, ResponseKind } from '#lib/types';

/** A plan from any of your groups, with your reply (null: added straight to the calendar, nobody was asked). */
export type MyPlan = GroupEvent & { groupName: string; myResponse: ResponseKind | null };

export interface MyPlans {
	/** Upcoming ones you said yes or maybe to, haven't replied to, or that went straight on the calendar (soonest first). */
	going: MyPlan[];
	/** Upcoming ones you said you can't make. */
	notGoing: MyPlan[];
	/** The last year, newest first. */
	past: MyPlan[];
}

export const getMyPlans = (fetch?: Fetch) => api.get<MyPlans>('/me/plans', { fetch });

/** Download link for every plan in `going` as one .ics ("Add all to calendar"). */
export const myPlansIcsUrl = () => apiUrl('/me/plans.ics');

/** Where tapping a plan goes: its proposal if it came from one, otherwise its group. */
export const planHref = (plan: Pick<GroupEvent, 'proposalId' | 'groupId'>) =>
	plan.proposalId ? `/app/proposals/${plan.proposalId}` : `/app/groups/${plan.groupId}`;
