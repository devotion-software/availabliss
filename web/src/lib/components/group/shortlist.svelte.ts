import type { Slot } from '#lib/types';
import { toasts } from '#lib/client/toast.svelte';

/** A date poll ("Ask the group") has 2–5 times. */
export const POLL_MAX = 5;

const sameSlot = (a: Slot, b: Slot) => a.start === b.start && a.end === b.end;

/** Times shortlisted on a group's calendar for a date poll. */
export class Shortlist {
	slots = $state<Slot[]>([]);

	has = (slot: Slot) => this.slots.some((s) => sameSlot(s, slot));

	toggle = (slot: Slot) => {
		if (this.has(slot)) this.remove(slot);
		else if (this.slots.length >= POLL_MAX) toasts.info(`A poll can have up to ${POLL_MAX} times.`);
		else this.slots = [...this.slots, slot];
	};

	remove = (slot: Slot) => {
		this.slots = this.slots.filter((s) => !sameSlot(s, slot));
	};

	clear = () => {
		this.slots = [];
	};
}
