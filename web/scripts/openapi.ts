/**
 * Builds docs/openapi.json (OpenAPI 3.1) from src/lib/api-contract.ts:
 * - request bodies and queries from the routes' zod schemas (z.toJSONSchema, input side),
 * - response types from src/lib/types.ts and src/lib/api-types.ts (ts-json-schema-generator).
 *
 *   bun run openapi           write docs/openapi.json
 *   bun run openapi --check   exit 1 if docs/openapi.json is out of date (CI)
 *
 * Either way it checks the document first (every $ref resolves, path parameters match, …).
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import { createGenerator } from 'ts-json-schema-generator';
import { endpoints, endpointKey, type Endpoint, type JsonSchema, type RawContent } from '../src/lib/api-contract';
import * as schemas from '../src/lib/schemas';

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = resolve(WEB, '../docs/openapi.json');
const TYPE_FILES = ['src/lib/types.ts', 'src/lib/api-types.ts'];
const API_BASE = '/api/v1';

type Json = Record<string, unknown>;

// ── Response types: TypeScript → JSON Schema ─────────────────────────────────

function typeSchemas(): Record<string, JsonSchema> {
	const out: Record<string, JsonSchema> = {};
	for (const file of TYPE_FILES) {
		const path = resolve(WEB, file);
		const generator = createGenerator({
			path,
			tsconfig: resolve(WEB, 'tsconfig.json'),
			expose: 'export',
			topRef: false,
			jsDoc: 'extended',
			skipTypeCheck: true,
			// Responses can gain fields; clients ignore ones they don't know.
			additionalProperties: true,
			sortProps: true
		});
		// `type: '*'` leaves interfaces out, so ask for each exported type by name.
		const names = [...readFileSync(path, 'utf8').matchAll(/^export (?:interface|type) (\w+)/gm)].map((m) => m[1]);
		for (const name of names) {
			const { definitions = {}, $schema: _, ...root } = generator.createSchema(name) as Json & { definitions?: Json };
			for (const [key, value] of Object.entries({ ...definitions, [name]: root })) out[key] ??= value as JsonSchema;
		}
	}
	return rewriteRefs(out, '#/definitions/', '#/components/schemas/') as Record<string, JsonSchema>;
}

function rewriteRefs(value: unknown, from: string, to: string): unknown {
	if (Array.isArray(value)) return value.map((v) => rewriteRefs(v, from, to));
	if (value && typeof value === 'object') {
		return Object.fromEntries(
			Object.entries(value).map(([k, v]) => [k, k === '$ref' && typeof v === 'string' ? v.replace(from, to) : rewriteRefs(v, from, to)])
		);
	}
	return value;
}

// ── Requests: zod → JSON Schema ──────────────────────────────────────────────

/** Named request bodies: each schemas.ts export used as a body becomes `<Name>Body` in components. */
const schemaNames = new Map<z.ZodType, string>(
	Object.entries(schemas)
		.filter((entry): entry is [string, z.ZodType] => entry[1] instanceof z.ZodType)
		.map(([name, schema]) => [schema, name.replace(/Schema$/, '').replace(/^./, (c) => c.toUpperCase()) + 'Body'])
);

function zodSchema(schema: z.ZodType): JsonSchema {
	const { $schema: _, ...json } = z.toJSONSchema(schema, { io: 'input', unrepresentable: 'any', target: 'draft-2020-12' }) as Json;
	return json;
}

const isRaw = (value: unknown): value is RawContent => !!value && typeof value === 'object' && 'contentType' in value && !(value instanceof z.ZodType);

function queryParameters(schema: z.ZodType): Json[] {
	const json = zodSchema(schema);
	if (json.type !== 'object' || !json.properties) throw new Error('Query schemas must be objects');
	const required = new Set((json.required as string[] | undefined) ?? []);
	return Object.entries(json.properties as Record<string, Json>).map(([name, { description, ...prop }]) => ({
		name,
		in: 'query',
		required: required.has(name),
		...(description ? { description } : {}),
		schema: prop
	}));
}

// ── Operations ───────────────────────────────────────────────────────────────

const ERROR_TEXT: Record<number, string> = {
	400: 'validation_error: the body or query is invalid',
	401: 'unauthorized: not signed in',
	402: 'upgrade_required: needs Pro',
	403: 'forbidden',
	404: 'not_found',
	409: 'conflict',
	429: 'rate_limited (see Retry-After)',
	503: 'Unavailable'
};
const AUTH_TEXT = { none: 'No session needed.', user: 'Signed in.', admin: 'Signed in as a group admin or the owner.', owner: "Signed in as the group's owner." };

const pathParams = (path: string) => [...path.matchAll(/\{(\w+)\}/g)].map((m) => m[1]);
const operationId = (e: Endpoint) =>
	e.method.toLowerCase() +
	e.path
		.replace(/^\/api\//, '/')
		.split(/[/.-]/)
		.filter(Boolean)
		.map((part) => {
			const param = part.match(/^\{(\w+)\}$/);
			const word = param ? `By${param[1].charAt(0).toUpperCase()}${param[1].slice(1)}` : part;
			return word.charAt(0).toUpperCase() + word.slice(1);
		})
		.join('');

function content(body: Exclude<Endpoint['body'] | Endpoint['response'], undefined>, components: Record<string, JsonSchema>): Json {
	if (isRaw(body)) return { [body.contentType]: { schema: body.schema ?? {} } };
	if (body instanceof z.ZodType) {
		const name = schemaNames.get(body);
		if (!name) return { 'application/json': { schema: zodSchema(body) } };
		components[name] ??= zodSchema(body);
		return { 'application/json': { schema: { $ref: `#/components/schemas/${name}` } } };
	}
	return { 'application/json': { schema: typeof body === 'string' ? { $ref: `#/components/schemas/${body}` } : body } };
}

function operation(e: Endpoint, components: Record<string, JsonSchema>): Json {
	const status = e.status ?? 200;
	const responses: Json = {};
	if (status === 204 || (status === 202 && !e.response)) responses[status] = { description: status === 202 ? 'Accepted' : 'No content' };
	else if (status === 302) responses[302] = { description: 'Redirect', headers: { Location: { schema: { type: 'string' } } } };
	else {
		if (!e.response) throw new Error(`${endpointKey(e)}: a ${status} response needs a shape`);
		responses[status] = { description: isRaw(e.response) ? e.response.description : 'OK', content: content(e.response, components) };
	}
	const errors: Record<number, string> = {};
	if (e.body || e.query) errors[400] = ERROR_TEXT[400];
	if (e.auth !== 'none') errors[401] = ERROR_TEXT[401];
	if (e.auth === 'admin' || e.auth === 'owner') errors[403] = e.auth === 'admin' ? 'Not a group admin' : 'Not the group owner';
	if (!e.root || e.path.startsWith('/api/')) errors[429] = ERROR_TEXT[429];
	Object.assign(errors, e.errors);
	for (const [code, text] of Object.entries(errors).sort(([a], [b]) => Number(a) - Number(b))) {
		responses[code] = { description: text, content: { 'application/json': { schema: { $ref: '#/components/schemas/ApiErrorBody' } } } };
	}

	const description = [e.description, AUTH_TEXT[e.auth], e.rateLimit && `Rate limit: ${e.rateLimit}.`].filter(Boolean).join('\n\n');
	return {
		operationId: operationId(e),
		tags: [e.tag],
		summary: e.summary,
		description,
		...(e.auth === 'none' ? { security: [] } : {}),
		...(e.auth === 'admin' || e.auth === 'owner' ? { 'x-group-role': e.auth } : {}),
		...(e.rateLimit ? { 'x-rate-limit': e.rateLimit } : {}),
		...(pathParams(e.path).length || e.query
			? {
					parameters: [
						...pathParams(e.path).map((name) => ({ name, in: 'path', required: true, schema: { type: 'string' } })),
						...(e.query ? queryParameters(e.query) : [])
					]
				}
			: {}),
		...(e.body ? { requestBody: { required: true, content: content(e.body, components) } } : {}),
		responses
	};
}

export function buildDocument(): Json {
	const components: Record<string, JsonSchema> = typeSchemas();
	const paths: Record<string, Json> = {};
	for (const e of endpoints) {
		const path = e.root ? e.path : API_BASE + e.path;
		const item = (paths[path] ??= {});
		const method = e.method.toLowerCase();
		if (item[method]) throw new Error(`${endpointKey(e)} is in the contract twice`);
		item[method] = operation(e, components);
	}
	const sortedPaths = Object.fromEntries(Object.entries(paths).sort(([a], [b]) => a.localeCompare(b)));
	const sortedSchemas = Object.fromEntries(Object.entries(components).sort(([a], [b]) => a.localeCompare(b)));
	return {
		openapi: '3.1.0',
		info: {
			title: 'Availabliss API',
			version: '1',
			description:
				'Generated from web/src/lib/api-contract.ts by `bun run openapi` (do not edit by hand). docs/API.md is the prose guide: errors, rate limits, live events and the rules behind each endpoint.'
		},
		servers: [{ url: '/' }],
		security: [{ bearer: [] }, { cookie: [] }],
		tags: [...new Set(endpoints.map((e) => e.tag))].map((name) => ({ name })),
		paths: sortedPaths,
		components: {
			securitySchemes: {
				bearer: { type: 'http', scheme: 'bearer', description: 'Native apps: the token from the `set-auth-token` header of a sign-in.' },
				cookie: { type: 'apiKey', in: 'cookie', name: 'better-auth.session_token', description: 'The web: the Better Auth session cookie (`__Secure-better-auth.session_token` over https).' }
			},
			schemas: sortedSchemas
		}
	};
}

// ── Checks ───────────────────────────────────────────────────────────────────

/** Problems with the document: refs that don't resolve, path parameters that don't match, … */
export function problems(doc: Json): string[] {
	const found: string[] = [];
	const resolveRef = (ref: string) =>
		ref.startsWith('#/')
			? ref
					.slice(2)
					.split('/')
					.reduce<unknown>((node, key) => (node && typeof node === 'object' ? (node as Json)[key.replace(/~1/g, '/').replace(/~0/g, '~')] : undefined), doc)
			: undefined;
	const walk = (node: unknown, where: string) => {
		if (Array.isArray(node)) node.forEach((n, i) => walk(n, `${where}[${i}]`));
		else if (node && typeof node === 'object') {
			for (const [key, value] of Object.entries(node)) {
				if (key === '$ref' && (typeof value !== 'string' || resolveRef(value) === undefined)) found.push(`${where}: $ref ${String(value)} doesn't resolve`);
				walk(value, `${where}/${key}`);
			}
		}
	};
	walk(doc, '#');

	const ids = new Set<string>();
	for (const [path, item] of Object.entries(doc.paths as Record<string, Record<string, Json>>)) {
		for (const [method, op] of Object.entries(item)) {
			const where = `${method.toUpperCase()} ${path}`;
			if (ids.has(op.operationId as string)) found.push(`${where}: duplicate operationId ${op.operationId}`);
			ids.add(op.operationId as string);
			const declared = ((op.parameters as Json[] | undefined) ?? []).filter((p) => p.in === 'path').map((p) => p.name);
			if (declared.join() !== pathParams(path).join()) found.push(`${where}: path parameters ${declared.join()} don't match the path`);
			if (!Object.keys(op.responses as Json).some((code) => code.startsWith('2') || code.startsWith('3'))) found.push(`${where}: no success response`);
			if (!(op.summary as string)?.trim()) found.push(`${where}: no summary`);
		}
	}
	return found;
}

export const render = (doc: Json) => JSON.stringify(doc, null, '\t') + '\n';

if (import.meta.main) {
	const doc = buildDocument();
	const issues = problems(doc);
	if (issues.length) {
		console.error(`docs/openapi.json would be invalid:\n  ${issues.join('\n  ')}`);
		process.exit(1);
	}
	const text = render(doc);
	const paths = Object.keys(doc.paths as Json).length;
	if (process.argv.includes('--check')) {
		let current = '';
		try {
			current = readFileSync(OUT, 'utf8');
		} catch {
			// Missing counts as out of date.
		}
		if (current !== text) {
			console.error('docs/openapi.json is out of date: run `bun run openapi` (or `make openapi`) and commit it.');
			process.exit(1);
		}
		console.log(`docs/openapi.json is up to date (${endpoints.length} operations on ${paths} paths).`);
	} else {
		writeFileSync(OUT, text);
		console.log(`Wrote docs/openapi.json (${endpoints.length} operations on ${paths} paths).`);
	}
}
