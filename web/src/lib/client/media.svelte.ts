import { MediaQuery } from 'svelte/reactivity';

/** Where the app switches between the phone and desktop layouts. */
export const DESKTOP = '(min-width: 900px)';

/**
 * A media query for choosing what to mount, not just what to show. `matches` is
 * null in SSR and until hydration finishes, so render both variants then (CSS
 * picks one, as before) and only the matching one once it's a boolean.
 */
export function layoutQuery(query: string) {
	const media = new MediaQuery(query);
	let mounted = $state(false);
	$effect(() => {
		mounted = true;
	});
	return {
		get matches(): boolean | null {
			return mounted ? media.current : null;
		}
	};
}
