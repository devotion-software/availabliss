/** App-wide toasts. Rendered once by <Toaster /> in the root layout. */
import { errorMessage } from './api';

export type ToastKind = 'info' | 'success' | 'error';

export interface ToastItem {
	id: number;
	kind: ToastKind;
	message: string;
	action?: { label: string; run: () => void };
}

let seq = 0;

class Toasts {
	items = $state<ToastItem[]>([]);

	push(message: string, kind: ToastKind = 'info', opts: { timeout?: number; action?: ToastItem['action'] } = {}) {
		const id = ++seq;
		this.items.push({ id, kind, message, action: opts.action });
		const timeout = opts.timeout ?? (kind === 'error' ? 6000 : 3800);
		if (timeout > 0) setTimeout(() => this.dismiss(id), timeout);
		return id;
	}

	dismiss(id: number) {
		this.items = this.items.filter((t) => t.id !== id);
	}

	success = (message: string) => this.push(message, 'success');
	info = (message: string) => this.push(message, 'info');
	/** Accepts any thrown value and shows its readable message. */
	error = (err: unknown) => this.push(typeof err === 'string' ? err : errorMessage(err), 'error');
}

export const toasts = new Toasts();
