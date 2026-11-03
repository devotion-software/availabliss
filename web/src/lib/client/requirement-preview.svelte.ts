import type { Requirement } from '#lib/types';
import { requirementSchema } from '#lib/schemas';
import { previewRequirement, type RequirementPreview } from './requirements';

/**
 * "13 weekends fit this timing": the live preview for a timing being edited,
 * recomputed 350 ms after the last change. Call during component init.
 */
export function createPreview(requirement: () => Requirement, timeZone: () => string) {
	let preview = $state<RequirementPreview | null>(null);
	let loading = $state(false);

	$effect(() => {
		const snapshot = $state.snapshot(requirement()) as Requirement;
		const req = { ...snapshot, timezone: snapshot.timezone || timeZone() };
		if (!requirementSchema.safeParse(req).success) {
			preview = null;
			// A request from before the timing went invalid was cancelled below.
			loading = false;
			return;
		}
		loading = true;
		let cancelled = false;
		const timer = setTimeout(() => {
			previewRequirement(req)
				.then((p) => !cancelled && (preview = p))
				.catch(() => !cancelled && (preview = null))
				.finally(() => !cancelled && (loading = false));
		}, 350);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	return {
		get preview() {
			return preview;
		},
		get loading() {
			return loading;
		}
	};
}
