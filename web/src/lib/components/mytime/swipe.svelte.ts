/**
 * Sideways swipes on a touch screen, as in the group calendar: the page follows
 * the finger 1:1 and a long enough drag or a quick flick moves on. `step(1)` is
 * next (finger moving left), `step(-1)` previous. Dragging towards a side that
 * can't move only gives a little.
 */
export function swipe(opts: { step: (by: 1 | -1) => void; can?: (by: 1 | -1) => boolean }) {
	let touch: { x: number; y: number } | null = null;
	// The last two moves, for the flick speed.
	let moves: { x: number; t: number }[] = [];
	let dragX = $state(0);
	const can = (by: 1 | -1) => opts.can?.(by) ?? true;

	return {
		get dragX() {
			return dragX;
		},
		start(e: TouchEvent) {
			touch = e.touches.length === 1 ? { x: e.touches[0].clientX, y: e.touches[0].clientY } : null;
			moves = [];
		},
		move(e: TouchEvent) {
			if (!touch) return;
			const dx = e.touches[0].clientX - touch.x;
			const dy = e.touches[0].clientY - touch.y;
			const blocked = !can(dx < 0 ? 1 : -1);
			dragX = Math.abs(dx) > Math.abs(dy) * 1.5 ? (blocked ? dx * 0.15 : dx) : 0;
			moves = [...moves.slice(-1), { x: e.touches[0].clientX, t: e.timeStamp }];
		},
		end(e: TouchEvent) {
			if (!touch) return;
			const dx = e.changedTouches[0].clientX - touch.x;
			const dy = e.changedTouches[0].clientY - touch.y;
			touch = null;
			dragX = 0;
			const [a, b] = moves;
			const speed = a && b && b.t > a.t ? (b.x - a.x) / (b.t - a.t) : 0; // px per ms
			const sideways = Math.abs(dx) > Math.abs(dy) * 1.5;
			const by = dx < 0 ? 1 : -1;
			if (sideways && (Math.abs(dx) > 60 || (Math.abs(speed) > 0.6 && Math.abs(dx) > 20)) && can(by)) opts.step(by);
		},
		cancel() {
			touch = null;
			dragX = 0;
		}
	};
}

/** Which way the page moved, for sliding the next one in from that side. */
export function pageDirection(key: () => string) {
	let dir = $state<1 | -1>(1);
	let last = '';
	$effect.pre(() => {
		const k = key();
		if (last && k !== last) dir = k > last ? 1 : -1;
		last = k;
	});
	return {
		get current() {
			return dir;
		}
	};
}
