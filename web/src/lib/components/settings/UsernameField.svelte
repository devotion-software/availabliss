<script lang="ts">
	import { checkUsername } from '#lib/client/usernames';
	import { normalizeUsername, usernameProblem } from '#lib/usernames';
	import TextField from '#lib/components/ui/TextField.svelte';

	/**
	 * Your username, checked as you type: the rules first, then (after a pause) whether
	 * someone else has it. `ready` is true when it can be saved as it is.
	 */
	interface Props {
		value: string;
		/** Your current username: no lookup needed for it. */
		current: string | null;
		ready?: boolean;
		/** A save that failed (e.g. taken in the meantime). */
		error?: string | null;
	}
	let { value = $bindable(), current, ready = $bindable(false), error = null }: Props = $props();

	let status = $state<{ for: string; ok: boolean; message: string } | null>(null);
	const normalized = $derived(normalizeUsername(value));
	const problem = $derived(usernameProblem(normalized));

	$effect(() => {
		const u = normalized;
		if (problem || u === current) return;
		const timer = setTimeout(() => {
			checkUsername(u)
				.then((c) => {
					if (normalized !== u) return;
					status = c.available ? { for: u, ok: true, message: `@${u} is free` } : { for: u, ok: false, message: c.problem ?? 'Taken. Try another.' };
				})
				// Can't check right now: let the save decide.
				.catch(() => normalized === u && (status = { for: u, ok: true, message: '' }));
		}, 400);
		return () => clearTimeout(timer);
	});

	const checked = $derived(status?.for === normalized ? status : null);
	$effect(() => {
		ready = !problem && (normalized === current || !!checked?.ok);
	});

	const shownError = $derived(error ?? (normalized && problem ? problem : checked && !checked.ok ? checked.message : null));
	const hint = $derived(
		normalized === current
			? 'People can invite you by this instead of your email'
			: checked?.ok
				? checked.message
				: normalized && !problem
					? 'Checking…'
					: undefined
	);
</script>

<TextField
	label="Username"
	prefix="@"
	bind:value
	autocomplete="username"
	autocapitalize="none"
	spellcheck={false}
	maxlength={21}
	error={shownError}
	hint={hint || undefined}
/>
