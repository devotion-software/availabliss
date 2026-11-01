<script lang="ts">
	/** Six single-digit boxes: auto-advance, backspace to previous, paste fills all. */
	interface Props {
		length?: number;
		disabled?: boolean;
		oncomplete: (code: string) => void;
	}
	let { length = 6, disabled = false, oncomplete }: Props = $props();

	let digits = $state<string[]>(Array(6).fill(''));
	let inputs: HTMLInputElement[] = $state([]);

	$effect(() => {
		digits = Array(length).fill('');
		queueMicrotask(() => inputs[0]?.focus());
	});

	/** Clears the boxes and focuses the first (after a wrong code). */
	export function reset() {
		digits = Array(length).fill('');
		queueMicrotask(() => inputs[0]?.focus());
	}

	function fill(from: number, text: string) {
		const chars = text.replace(/\D/g, '').slice(0, length - from).split('');
		chars.forEach((c, i) => (digits[from + i] = c));
		const next = Math.min(from + chars.length, length - 1);
		inputs[next]?.focus();
		if (digits.every((d) => d)) oncomplete(digits.join(''));
	}

	function oninput(e: Event, i: number) {
		const el = e.currentTarget as HTMLInputElement;
		const value = el.value;
		digits[i] = '';
		if (value) fill(i, value);
		el.value = digits[i];
	}

	function onkeydown(e: KeyboardEvent, i: number) {
		if (e.key === 'Backspace' && !digits[i] && i > 0) {
			e.preventDefault();
			digits[i - 1] = '';
			inputs[i - 1]?.focus();
		} else if (e.key === 'ArrowLeft' && i > 0) {
			e.preventDefault();
			inputs[i - 1]?.focus();
		} else if (e.key === 'ArrowRight' && i < length - 1) {
			e.preventDefault();
			inputs[i + 1]?.focus();
		}
	}

	function onpaste(e: ClipboardEvent, i: number) {
		const text = e.clipboardData?.getData('text') ?? '';
		if (!/\d/.test(text)) return;
		e.preventDefault();
		fill(i, text);
	}
</script>

<div class="otp" role="group" aria-label="One-time code">
	{#each digits as digit, i (i)}
		<input
			bind:this={inputs[i]}
			value={digit}
			aria-label="Digit {i + 1}"
			inputmode="numeric"
			pattern="[0-9]*"
			autocomplete={i === 0 ? 'one-time-code' : 'off'}
			maxlength={i === 0 ? length : 1}
			{disabled}
			oninput={(e) => oninput(e, i)}
			onkeydown={(e) => onkeydown(e, i)}
			onpaste={(e) => onpaste(e, i)}
			onfocus={(e) => (e.currentTarget as HTMLInputElement).select()}
		/>
	{/each}
</div>

<style>
	.otp {
		display: grid;
		grid-template-columns: repeat(6, minmax(0, 1fr));
		gap: 8px;
	}
	input {
		width: 100%;
		height: 54px;
		border-radius: 14px;
		border: 1px solid var(--line-2);
		background: var(--surface);
		text-align: center;
		font-size: 22px;
		font-weight: 700;
		color: var(--ink);
		transition:
			border-color 0.2s ease,
			box-shadow 0.2s ease;
	}
	input:focus {
		outline: none;
		border-color: var(--accent);
		box-shadow: 0 0 0 3px var(--accent-soft);
	}
	input:disabled {
		opacity: 0.6;
	}
</style>
