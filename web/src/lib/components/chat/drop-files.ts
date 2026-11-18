/**
 * Svelte action: files dragged from the desktop and dropped onto `node` go to `ondrop`.
 * Sets `data-dropping` on the node while files are over it, for a highlight.
 */
export function dropFiles(node: HTMLElement, ondrop: (files: File[]) => void) {
	let handler = ondrop;
	let depth = 0;
	const hasFiles = (e: DragEvent) => !!e.dataTransfer?.types.includes('Files');
	const set = (on: boolean) => (on ? node.setAttribute('data-dropping', '') : node.removeAttribute('data-dropping'));

	function onenter(e: DragEvent) {
		if (!hasFiles(e)) return;
		e.preventDefault();
		depth++;
		set(true);
	}
	function onover(e: DragEvent) {
		if (!hasFiles(e)) return;
		e.preventDefault();
		if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
	}
	function onleave(e: DragEvent) {
		if (!hasFiles(e)) return;
		depth = Math.max(0, depth - 1);
		if (!depth) set(false);
	}
	function ondropped(e: DragEvent) {
		if (!hasFiles(e)) return;
		e.preventDefault();
		depth = 0;
		set(false);
		const files = [...(e.dataTransfer?.files ?? [])];
		if (files.length) handler(files);
	}

	node.addEventListener('dragenter', onenter);
	node.addEventListener('dragover', onover);
	node.addEventListener('dragleave', onleave);
	node.addEventListener('drop', ondropped);
	return {
		update(next: (files: File[]) => void) {
			handler = next;
		},
		destroy() {
			node.removeEventListener('dragenter', onenter);
			node.removeEventListener('dragover', onover);
			node.removeEventListener('dragleave', onleave);
			node.removeEventListener('drop', ondropped);
		}
	};
}
