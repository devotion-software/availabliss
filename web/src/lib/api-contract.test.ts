import { describe, expect, test } from 'bun:test';
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { endpointKey, endpoints } from './api-contract';

const ROUTES = join(import.meta.dir, '..', 'routes');
const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'] as const;

function serverFiles(dir: string): string[] {
	return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
		const path = join(dir, entry.name);
		if (entry.isDirectory()) return serverFiles(path);
		return entry.name === '+server.ts' ? [path] : [];
	});
}

/** The HTTP methods a +server.ts exports (`export const GET = …` or `export async function GET`). */
function exportedMethods(file: string): string[] {
	const source = readFileSync(file, 'utf8');
	return METHODS.filter((m) => new RegExp(`^export\\s+(?:const\\s+${m}\\b|(?:async\\s+)?function\\s+${m}\\b)`, 'm').test(source));
}

/** `routes/api/v1/groups/[id]/+server.ts` → `/groups/{id}` (relative to `base`). */
function openApiPath(file: string, base: string): string {
	const dir = relative(base, file).split(sep).slice(0, -1);
	return '/' + dir.map((part) => part.replace(/^\[(?:\.\.\.)?(\w+)\]$/, '{$1}')).join('/');
}

/** Every `METHOD /path` the routes serve: /api/v1 relative to it, the other API routes in full. */
function routeKeys(): Set<string> {
	const keys = new Set<string>();
	const add = (base: string, prefix: string) => {
		for (const file of serverFiles(base)) {
			for (const method of exportedMethods(file)) keys.add(`${method} ${prefix}${openApiPath(file, base)}`);
		}
	};
	add(join(ROUTES, 'api', 'v1'), '');
	add(join(ROUTES, 'calendar'), '/calendar');
	add(join(ROUTES, 'media'), '/media');
	return keys;
}

describe('api-contract', () => {
	const routes = routeKeys();
	const contract = new Set(endpoints.filter((e) => !e.path.startsWith('/api/auth/')).map(endpointKey));

	test('finds the routes', () => {
		expect(routes.size).toBeGreaterThan(50);
		expect(routes.has('GET /groups/{id}')).toBe(true);
		expect(routes.has('GET /media/{key}')).toBe(true);
	});

	test('every route is in the contract (add new endpoints to src/lib/api-contract.ts, then `bun run openapi`)', () => {
		expect([...routes].filter((key) => !contract.has(key)).sort()).toEqual([]);
	});

	test('every endpoint in the contract still exists', () => {
		expect([...contract].filter((key) => !routes.has(key)).sort()).toEqual([]);
	});

	test('Better Auth endpoints use methods its catch-all route serves', () => {
		const served = exportedMethods(join(ROUTES, 'api', 'auth', '[...all]', '+server.ts'));
		const stray = endpoints.filter((e) => e.path.startsWith('/api/auth/') && (!e.root || !served.includes(e.method)));
		expect(stray.map(endpointKey)).toEqual([]);
	});

	test('no endpoint is listed twice, and only root ones sit outside /api/v1', () => {
		const keys = endpoints.map((e) => `${e.root ? 'root' : 'v1'} ${endpointKey(e)}`);
		expect(keys.filter((k, i) => keys.indexOf(k) !== i)).toEqual([]);
		const roots = endpoints.filter((e) => e.root).map((e) => e.path.split('/')[1]);
		expect([...new Set(roots)].sort()).toEqual(['api', 'calendar', 'media']);
	});

	test('responses: every success status but 202, 204 and 302 has a shape', () => {
		const missing = endpoints.filter((e) => ![202, 204, 302].includes(e.status ?? 200) && !e.response);
		expect(missing.map(endpointKey)).toEqual([]);
	});
});
