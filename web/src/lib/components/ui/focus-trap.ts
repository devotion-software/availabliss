/** Svelte attachment-style action: keeps Tab focus inside `node`, focuses it on mount and restores focus after. */
const FOCUSABLE =
	'a[href], button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function focusTrap(node: HTMLElement) {
	const previous = document.activeElement as HTMLElement | null;
	const focusables = () => [...node.querySelectorAll<HTMLElement>(FOCUSABLE)].filter((el) => el.offsetParent !== null);

	// Prefer an explicit [autofocus] target, else the first field, else the dialog itself.
	queueMicrotask(() => {
		const target = node.querySelector<HTMLElement>('[autofocus]') ?? focusables().find((el) => el.matches('input, select, textarea')) ?? node;
		target.focus({ preventScroll: true });
	});

	function onkeydown(e: KeyboardEvent) {
		if (e.key !== 'Tab') return;
		const items = focusables();
		if (!items.length) {
			e.preventDefault();
			return;
		}
		const first = items[0];
		const last = items[items.length - 1];
		if (e.shiftKey && (document.activeElement === first || document.activeElement === node)) {
			e.preventDefault();
			last.focus();
		} else if (!e.shiftKey && document.activeElement === last) {
			e.preventDefault();
			first.focus();
		}
	}

	node.addEventListener('keydown', onkeydown);
	return {
		destroy() {
			node.removeEventListener('keydown', onkeydown);
			if (previous?.isConnected) previous.focus({ preventScroll: true });
		}
	};
}
