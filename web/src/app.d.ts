import type { Session, User } from '#lib/auth';

// See https://svelte.dev/docs/kit/types#app.d.ts
declare global {
	// Injected from package.json by vite.config.ts.
	const __APP_VERSION__: string;

	namespace App {
		interface Error {
			message: string;
			code?: string;
		}
		interface Locals {
			user: User | null;
			session: Session | null;
		}
		interface PageState {
			/** My time: the day open in the week view. */
			myTimeDay?: string;
		}
	}
}

export {};
