// ESLint flat config: recommended rules from @eslint/js, typescript-eslint and eslint-plugin-svelte.
// Formatting is Prettier's job (eslint-config-prettier turns off the rules that would fight it).
import js from '@eslint/js';
import { defineConfig } from 'eslint/config';
import prettier from 'eslint-config-prettier';
import svelte from 'eslint-plugin-svelte';
import globals from 'globals';
import ts from 'typescript-eslint';

export default defineConfig(
	{
		ignores: [
			'build/',
			'.svelte-kit/',
			'node_modules/',
			'coverage/',
			'drizzle/',
			'test-results/',
			'playwright-report/',
			'static/',
			'.uploads/',
			'.tmp-*'
		]
	},
	js.configs.recommended,
	...ts.configs.recommended,
	...svelte.configs.recommended,
	prettier,
	...svelte.configs.prettier,
	{
		languageOptions: { globals: { ...globals.browser, ...globals.node, Bun: 'readonly' } }
	},
	{
		files: ['**/*.svelte', '**/*.svelte.ts', '**/*.svelte.js'],
		languageOptions: { parserOptions: { parser: ts.parser, extraFileExtensions: ['.svelte'] } }
	},
	{
		rules: {
			// Unused names are errors, except ones deliberately marked with a leading underscore.
			'@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrors: 'none' }],
			// TypeScript already checks names (and knows DOM types such as ScrollBehavior and Vite's __APP_VERSION__ define);
			// typescript-eslint recommends turning this off for TypeScript code.
			'no-undef': 'off',
			// Wants every href / goto() wrapped in resolve() from $app/paths. The app is served at the root (no `paths.base`),
			// so this would touch ~70 links for no behaviour change.
			'svelte/no-navigation-without-resolve': 'off',
			// Flags every `new Set()` / `new Map()` / `new Date()` in a component or .svelte.ts file, including local
			// temporaries and copy-then-reassign updates (`invitees = next`), which are already reactive. No real cases found.
			'svelte/prefer-svelte-reactivity': 'off',
			// Rewriting these $state + $effect pairs as writable $derived changes when the value resets; not worth the risk.
			'svelte/prefer-writable-derived': 'off',
			// Sheet requires a `children` snippet, so the sheets with only a footer pass an empty one on purpose.
			'svelte/no-useless-children-snippet': 'off'
		}
	},
	{
		// Dev scripts (smoke test, seeding) read loosely typed JSON from the API.
		files: ['scripts/**'],
		rules: { '@typescript-eslint/no-explicit-any': 'off' }
	}
);
