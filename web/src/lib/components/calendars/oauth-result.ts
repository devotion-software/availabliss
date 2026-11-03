import { goto } from '$app/navigation';
import { toasts } from '#lib/client/toast.svelte';

/** Shows the outcome of an OAuth round trip (`?connected=` / `?error=`) once, then cleans the URL. */
export function reportOAuthResult(current: { href: string }): boolean {
	const url = new URL(current.href);
	const connected = url.searchParams.get('connected');
	const error = url.searchParams.get('error');
	if (!connected && !error) return false;
	if (connected) toasts.success('Calendar connected');
	else if (error === 'access_denied') toasts.error('Calendar access was not granted');
	else if (error === 'wrong_account') toasts.error('That connection link was started from a different account. Connect your calendar from here instead.');
	else toasts.error(`Couldn't connect that calendar (${error})`);
	url.searchParams.delete('connected');
	url.searchParams.delete('error');
	goto(url.pathname + url.search, { replace: true, shallow: true });
	return !!connected;
}
