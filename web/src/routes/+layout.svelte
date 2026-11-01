<script lang="ts">
	import '#lib/styles/app.css';
	import { provideFormat } from '#lib/client/format';
	import { browserTimeZone } from '#lib/client/dates';
	import Toaster from '#lib/components/ui/Toaster.svelte';

	let { data, children } = $props();

	provideFormat(() => data.user?.timezone ?? browserTimeZone());
</script>

{@render children()}
<Toaster />
