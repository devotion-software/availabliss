import { error } from '@sveltejs/kit';
import { ApiError } from './api';

/** Turns API failures inside load functions into SvelteKit error pages with the right status. */
export function toPageError(err: unknown): never {
	if (err instanceof ApiError) error(err.status, { message: err.message, code: err.code });
	throw err;
}
