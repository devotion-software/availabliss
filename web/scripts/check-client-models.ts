/**
 * Checks the iOS and Android API models against docs/openapi.json, field by field, so a model that
 * drifts from the server fails CI instead of showing up as a bug on one platform.
 *
 *   bun run models:check              check both apps (exit 1 on a new problem)
 *   bun run models:check --baseline   write the current problems to client-models.baseline.json
 *   bun run models:check --verbose    also list what the apps leave out and types not checked
 *
 * A client type is matched to the spec schema of the same name (or the name in client-models.json).
 * For each matched type it checks names, optional and nullable:
 * - a field the app reads that the spec doesn't have (a typo, or a field the server never sends);
 * - responses: a field the app needs (non-optional in Swift; non-null without a default in Kotlin)
 *   that the server may leave out or send as null, which would fail to decode;
 * - request bodies: a field the app sends that the spec doesn't accept.
 * Types aren't compared (Date vs string and the like are fine), and fields the app ignores are
 * only listed with --verbose. Known problems live in client-models.baseline.json, so only new ones fail.
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const WEB = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ROOT = resolve(WEB, '..');
const SPEC = resolve(ROOT, 'docs/openapi.json');
const CONFIG = resolve(WEB, 'scripts/client-models.json');
const BASELINE = resolve(WEB, 'scripts/client-models.baseline.json');

type Platform = 'ios' | 'android';
interface Field {
	/** The JSON key. */
	key: string;
	/** May be missing or null when decoding (Swift optional; Kotlin nullable or with a default). */
	tolerant: boolean;
	/** Kotlin: has a default (coerceInputValues turns an explicit null into it too). */
	line: number;
}
interface ClientType {
	platform: Platform;
	name: string;
	file: string;
	line: number;
	decodes: boolean;
	encodes: boolean;
	/** Swift with a hand-written init(from:): only its keys are checked. */
	custom: boolean;
	fields: Field[];
}
interface Config {
	/** Client type name → spec schema name, or null to skip the type. Per platform. */
	names: Record<Platform, Record<string, string | null>>;
	/** "Type.key" fields an app keeps for its own use (never in the JSON). */
	localFields: Record<Platform, string[]>;
	/** Spec schema → other schemas the same app type also decodes (fields may come from any of them). */
	alsoDecodes: Record<string, string[]>;
}

// ── Spec ─────────────────────────────────────────────────────────────────────

type Schema = {
	$ref?: string;
	type?: string | string[];
	properties?: Record<string, Schema>;
	required?: string[];
	anyOf?: Schema[];
	oneOf?: Schema[];
	allOf?: Schema[];
	items?: Schema;
	additionalProperties?: Schema | boolean;
	nullable?: boolean;
};
const spec = JSON.parse(readFileSync(SPEC, 'utf8')) as {
	paths: Record<
		string,
		Record<
			string,
			{ requestBody?: { content: Record<string, { schema: Schema }> }; responses?: Record<string, { content?: Record<string, { schema: Schema }> }> }
		>
	>;
	components: { schemas: Record<string, Schema> };
};
const schemas = spec.components.schemas;
const refName = (ref: string) => ref.replace('#/components/schemas/', '');

/** Schema names reachable from request bodies and from responses. */
function reachable(roots: Schema[]): Set<string> {
	const seen = new Set<string>();
	const walk = (s: unknown) => {
		if (!s || typeof s !== 'object') return;
		if (Array.isArray(s)) return s.forEach(walk);
		const ref = (s as Schema).$ref;
		if (ref) {
			const name = refName(ref);
			if (seen.has(name)) return;
			seen.add(name);
			walk(schemas[name]);
		}
		Object.values(s).forEach(walk);
	};
	roots.forEach(walk);
	return seen;
}
const operations = Object.values(spec.paths).flatMap((p) => Object.values(p));
const requestSchemas = reachable(operations.flatMap((o) => Object.values(o.requestBody?.content ?? {}).map((c) => c.schema)));
const responseSchemas = reachable(
	operations.flatMap((o) => Object.values(o.responses ?? {}).flatMap((r) => Object.values(r.content ?? {}).map((c) => c.schema)))
);
// Live events and similar types aren't behind an operation but are still sent to the apps.
const isRequestOnly = (name: string) => requestSchemas.has(name) && !responseSchemas.has(name);

const nullable = (s: Schema): boolean =>
	s.nullable === true ||
	(Array.isArray(s.type) && s.type.includes('null')) ||
	s.type === 'null' ||
	!!s.anyOf?.some(nullable) ||
	!!s.oneOf?.some(nullable);

/** An object schema's properties and which are required (allOf merged); null when it isn't an object. */
function objectShape(s: Schema): { properties: Record<string, Schema>; required: Set<string> } | null {
	if (s.$ref) return objectShape(schemas[refName(s.$ref)] ?? {});
	if (s.allOf) {
		const parts = s.allOf.map(objectShape).filter((p) => p !== null);
		if (!parts.length) return null;
		return { properties: Object.assign({}, ...parts.map((p) => p.properties)), required: new Set(parts.flatMap((p) => [...p.required])) };
	}
	if (!s.properties) return null;
	return { properties: s.properties, required: new Set(s.required ?? []) };
}

// ── Source helpers ───────────────────────────────────────────────────────────

function files(dir: string, ext: string): string[] {
	return readdirSync(dir).flatMap((name) => {
		const path = join(dir, name);
		return statSync(path).isDirectory() ? files(path, ext) : path.endsWith(ext) ? [path] : [];
	});
}

/** Blanks out comments and string contents (keeping line breaks and quotes), so braces in them don't count. */
function stripCode(src: string): string {
	let out = '';
	let i = 0;
	const blank = (n = 1) => {
		for (let k = 0; k < n && i < src.length; k++, i++) out += src[i] === '\n' ? '\n' : ' ';
	};
	const keep = (n: number) => {
		out += src.slice(i, i + n);
		i += n;
	};
	while (i < src.length) {
		if (src.startsWith('//', i)) {
			while (i < src.length && src[i] !== '\n') blank();
		} else if (src.startsWith('/*', i)) {
			let depth = 0;
			do {
				if (src.startsWith('/*', i)) {
					depth++;
					blank(2);
				} else if (src.startsWith('*/', i)) {
					depth--;
					blank(2);
				} else blank();
			} while (depth > 0 && i < src.length);
		} else if (src.startsWith('"""', i)) {
			keep(3);
			while (i < src.length && !src.startsWith('"""', i)) blank();
			keep(3);
		} else if (src[i] === '"') {
			keep(1);
			while (i < src.length && src[i] !== '"' && src[i] !== '\n') blank(src[i] === '\\' ? 2 : 1);
			if (src[i] === '"') keep(1);
		} else keep(1);
	}
	return out;
}

const lineAt = (src: string, index: number) => src.slice(0, index).split('\n').length;

/** Index of the brace closing the one at `open`. */
function closing(code: string, open: number, pair = '{}'): number {
	let depth = 0;
	for (let i = open; i < code.length; i++) {
		if (code[i] === pair[0]) depth++;
		else if (code[i] === pair[1] && --depth === 0) return i;
	}
	return code.length;
}

/** The text at brace depth 0 inside `[start, end)`, other nested blocks blanked. */
function topLevel(code: string, start: number, end: number): string {
	let out = '';
	let depth = 0;
	for (let i = start; i < end; i++) {
		const c = code[i];
		if (c === '}') depth--;
		out += depth === 0 || c === '\n' ? c : ' ';
		if (c === '{') depth++;
	}
	return out;
}

// ── Swift ────────────────────────────────────────────────────────────────────

function swiftTypes(dir: string): ClientType[] {
	const out: ClientType[] = [];
	for (const file of files(dir, '.swift')) {
		const src = readFileSync(file, 'utf8');
		const code = stripCode(src);
		const decl = /\b(struct|class)\s+(\w+)\s*(?:<[^>{]*>)?\s*:\s*([^{]+)\{/g;
		for (const m of code.matchAll(decl)) {
			const conformances = m[3];
			const decodes = /\b(Codable|Decodable)\b/.test(conformances);
			const encodes = /\b(Codable|Encodable)\b/.test(conformances);
			if (!decodes && !encodes) continue;
			const open = m.index + m[0].length - 1;
			const close = closing(code, open);
			const body = topLevel(code, open + 1, close);
			const bodyStart = open + 1;
			const custom = decodes && /\binit\s*\(\s*from\s+decoder\b/.test(code.slice(open, close));
			// CodingKeys (nested enum) rename or limit the keys.
			const keysMatch = /enum\s+CodingKeys\s*:\s*[^{]*\{/.exec(code.slice(open, close));
			let codingKeys: Map<string, string> | null = null;
			if (keysMatch) {
				const kOpen = open + keysMatch.index + keysMatch[0].length - 1;
				const kBody = src.slice(kOpen + 1, closing(code, kOpen));
				codingKeys = new Map();
				for (const c of stripLineComments(kBody).matchAll(/\bcase\s+([^\n]+)/g)) {
					for (const part of c[1].split(',')) {
						const [name, raw] = part.split('=').map((s) => s.trim());
						if (name) codingKeys.set(name, raw ? raw.replace(/^"|"$/g, '') : name);
					}
				}
			}
			const fields: Field[] = [];
			const prop =
				/^[ \t]*((?:@\w+(?:\([^)]*\))?\s+|(?:public|internal|fileprivate|private(?:\(set\))?|nonisolated)\s+)*)(let|var)\s+(\w+)\s*:\s*([^={\n]+?)\s*(=[^\n]*)?(\{)?[ \t]*$/gm;
			for (const p of body.matchAll(prop)) {
				const [, modifiers, kind, name, type, initial, computed] = p;
				if (computed || /\bstatic\b|\blazy\b/.test(modifiers) || /@(?:ObservationIgnored|Transient)/.test(modifiers)) continue;
				// `let x: T = v` is never decoded.
				if (kind === 'let' && initial && decodes && !custom) continue;
				const key = codingKeys ? codingKeys.get(name) : name;
				if (key === undefined) continue;
				fields.push({ key, tolerant: /\?\s*$|^Optional</.test(type.trim()), line: lineAt(code, bodyStart + p.index) });
			}
			if (custom) {
				// Only the keys are known; whether each is optional is up to the hand-written decoder.
				const keys = codingKeys ? [...codingKeys.values()] : fields.map((f) => f.key);
				out.push({
					platform: 'ios',
					name: m[2],
					file,
					line: lineAt(code, m.index),
					decodes,
					encodes,
					custom,
					fields: keys.map((key) => ({ key, tolerant: true, line: lineAt(code, m.index) }))
				});
			} else {
				out.push({ platform: 'ios', name: m[2], file, line: lineAt(code, m.index), decodes, encodes, custom, fields });
			}
		}
	}
	return out;
}

const stripLineComments = (s: string) => s.replace(/\/\/[^\n]*/g, '');

// ── Kotlin ───────────────────────────────────────────────────────────────────

function kotlinTypes(dir: string): ClientType[] {
	const out: ClientType[] = [];
	for (const file of files(dir, '.kt')) {
		const src = readFileSync(file, 'utf8');
		const code = stripCode(src);
		// Hand-written serializers (`@Serializable(with = …)`) aren't checked.
		const decl = /@Serializable(?!\s*\(\s*with)\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:(?:internal|private|public)\s+)?data\s+class\s+(\w+)\s*\(/g;
		for (const m of code.matchAll(decl)) {
			const open = m.index + m[0].length - 1;
			const close = closing(code, open, '()');
			const params = splitTopLevel(code.slice(open + 1, close));
			const fields: Field[] = [];
			let offset = open + 1;
			for (const param of params) {
				const p = /^\s*((?:@[\w.]+(?:\([^)]*\))?\s*)*)(?:(?:private|internal|override)\s+)*(val|var)\s+(\w+)\s*:\s*([^=]+?)\s*(=[\s\S]*)?$/.exec(
					param
				);
				if (p) {
					const [, annotations, , name, type, initial] = p;
					if (/@Transient\b/.test(annotations)) {
						offset += param.length + 1;
						continue;
					}
					// The string's contents were blanked, so read the name from the source.
					const serial = /@SerialName\(\s*"/.exec(annotations);
					let key = name;
					if (serial) {
						const at = offset + param.indexOf(serial[0]) + serial[0].length;
						key = src.slice(at, src.indexOf('"', at));
					}
					fields.push({ key, tolerant: type.trim().endsWith('?') || !!initial, line: lineAt(code, offset + param.search(/\S/)) });
				}
				offset += param.length + 1;
			}
			out.push({ platform: 'android', name: m[1], file, line: lineAt(code, m.index), decodes: true, encodes: true, custom: false, fields });
		}
	}
	return out;
}

/** Splits on commas outside (), [], {}, <>. */
function splitTopLevel(s: string): string[] {
	const parts: string[] = [];
	let depth = 0;
	let current = '';
	for (let i = 0; i < s.length; i++) {
		const c = s[i];
		if ('([{<'.includes(c)) depth++;
		else if (')]}'.includes(c) || (c === '>' && s[i - 1] !== '-')) depth--;
		if (c === ',' && depth === 0) {
			parts.push(current);
			current = '';
		} else current += c;
	}
	if (current.trim()) parts.push(current);
	return parts;
}

// ── Check ────────────────────────────────────────────────────────────────────

const config = JSON.parse(readFileSync(CONFIG, 'utf8')) as Config;
const types = [
	...swiftTypes(resolve(ROOT, 'ios/Availabliss')),
	...kotlinTypes(resolve(ROOT, 'android/app/src/main/java/dev/joeherbert/availabliss/core/network'))
];

const problems: { id: string; where: string; message: string }[] = [];
const notes: string[] = [];
const unchecked: string[] = [];
let checkedFields = 0;
const matched: string[] = [];

for (const t of types) {
	const mapped = Object.hasOwn(config.names[t.platform], t.name) ? config.names[t.platform][t.name] : t.name;
	if (mapped === null) continue;
	const schema = schemas[mapped];
	const shape = schema && objectShape(schema);
	if (shape) shape.properties = { ...shape.properties };
	if (!shape) {
		if (Object.hasOwn(config.names[t.platform], t.name)) {
			problems.push({
				id: `${t.platform}:${t.name}:mapping`,
				where: `${relative(ROOT, t.file)}:${t.line}`,
				message: `${t.name} is mapped to ${mapped}, which isn't an object schema in the spec`
			});
		} else unchecked.push(`${t.platform} ${t.name}`);
		continue;
	}
	// One app type can stand for several schemas (an event, and an event with its group's name).
	for (const other of config.alsoDecodes[mapped] ?? []) {
		const extra = schemas[other] && objectShape(schemas[other]);
		if (extra) shape.properties = { ...extra.properties, ...shape.properties };
	}
	checkedFields += t.fields.filter((f) => !f.tolerant).length;
	matched.push(`${t.platform} ${t.name}${mapped === t.name ? '' : ` → ${mapped}`}`);
	const requestOnly = isRequestOnly(mapped);
	const local = new Set(config.localFields[t.platform]);
	const keys = new Set<string>();
	for (const f of t.fields) {
		keys.add(f.key);
		const where = `${relative(ROOT, t.file)}:${f.line}`;
		if (local.has(`${t.name}.${f.key}`)) continue;
		const property = shape.properties[f.key];
		if (!property) {
			problems.push({
				id: `${t.platform}:${t.name}.${f.key}:unknown`,
				where,
				message: requestOnly
					? `${t.name}.${f.key} is sent, but ${mapped} doesn't accept it`
					: `${t.name}.${f.key} isn't in ${mapped}: the server never sends it`
			});
			continue;
		}
		if (requestOnly || t.custom || f.tolerant) continue;
		const missing = !shape.required.has(f.key);
		const isNull = nullable(property);
		if (missing || isNull) {
			problems.push({
				id: `${t.platform}:${t.name}.${f.key}:${missing ? 'optional' : 'nullable'}`,
				where,
				message: `${t.name}.${f.key} is required by the app, but ${mapped} ${missing && isNull ? 'may leave it out or send null' : missing ? 'may leave it out' : 'may send null'}`
			});
		}
	}
	const ignored = Object.keys(shape.properties).filter((k) => !keys.has(k));
	if (ignored.length && !requestOnly) notes.push(`${t.platform} ${t.name} leaves out ${ignored.join(', ')}`);
}

const args = new Set(process.argv.slice(2));
if (args.has('--baseline')) {
	writeFileSync(BASELINE, JSON.stringify(problems.map((p) => p.id).sort(), null, '\t') + '\n');
	console.log(`Wrote ${problems.length} known problems to ${relative(ROOT, BASELINE)}`);
	process.exit(0);
}

const known = new Set(JSON.parse(readFileSync(BASELINE, 'utf8')) as string[]);
const fresh = problems.filter((p) => !known.has(p.id));
const fixed = [...known].filter((id) => !problems.some((p) => p.id === id));
const checked = types.length - unchecked.length;

if (args.has('--verbose')) {
	for (const n of notes) console.log(`  note: ${n}`);
	console.log(`  checked: ${matched.join(', ')}`);
	console.log(`  not in the spec (not checked): ${unchecked.join(', ')}`);
	for (const p of problems.filter((p) => known.has(p.id))) console.log(`  known: ${p.where} ${p.message}`);
}
console.log(
	`Checked ${checked} app models (${checkedFields} required fields) against docs/openapi.json; ${unchecked.length} have no schema to check against.`
);
if (fixed.length) console.log(`No longer a problem (remove from the baseline with --baseline): ${fixed.join(', ')}`);
if (fresh.length) {
	console.error(`\n${fresh.length} new mismatch${fresh.length === 1 ? '' : 'es'} between the apps and the API:`);
	for (const p of fresh) console.error(`  ${p.where}  ${p.message}`);
	console.error('\nFix the model (or the API), map the type in web/scripts/client-models.json, or accept it with --baseline.');
	process.exit(1);
}
console.log(known.size ? `OK (${known.size - fixed.length} known problems in the baseline).` : 'OK.');
