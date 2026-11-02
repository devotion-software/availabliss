/**
 * Moves an element to the end of <body>, out of any stacking context it was
 * rendered in. Sheets rendered inside the page shell (z-index 1) otherwise sit
 * below the floating tab bar.
 */
export function portal(node: HTMLElement) {
	document.body.appendChild(node);
	return {
		destroy() {
			node.remove();
		}
	};
}
