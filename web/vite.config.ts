import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import pkg from './package.json' with { type: 'json' };

export default defineConfig({
	define: {
		__APP_VERSION__: JSON.stringify(pkg.version)
	},
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter()
		})
	],
	server: {
		// IPv4 loopback: the Android emulator reaches the host only via 10.0.2.2 → 127.0.0.1,
		// and Node would otherwise bind "localhost" to ::1 alone. Browsers and the iOS
		// simulator still reach it as localhost. Use `bun run dev --host` for real devices.
		host: '127.0.0.1',
		// Fixed port keeps BETTER_AUTH_URL / OAuth callbacks stable across restarts.
		port: 5190,
		strictPort: true
	}
});
