<script lang="ts">
	import '#lib/styles/app.css';
	import { provideFormat } from '#lib/client/format';
	import { applyAppearance } from '#lib/client/appearance';
	import { untrack } from 'svelte';
	import Toaster from '#lib/components/ui/Toaster.svelte';

	let { data, children } = $props();

	// Signed-out visitors (the invite page) get UTC: the browser's zone isn't known
	// during SSR, so using it would render a different day on the server near midnight.
	provideFormat(() => data.user?.timezone ?? 'UTC');

	// SSR renders the saved appearance into <html>. Signing in with a code or passkey,
	// or signing out, is a client-side navigation, so apply it here when it changes.
	const appearanceKey = $derived(data.user ? `${data.user.id}:${data.user.themeMode}:${data.user.colorTheme}` : null);
	let appliedKey = untrack(() => appearanceKey);
	$effect(() => {
		const key = appearanceKey;
		if (key === appliedKey) return;
		appliedKey = key;
		const user = untrack(() => data.user);
		applyAppearance(user ? user : { themeMode: 'system', colorTheme: 'dawn' });
	});
</script>

{@render children()}
<Toaster />
