import type { RequestHandler } from './$types';
import { log } from '#lib/server/log';

/** The fields of a CSP violation worth logging, from either report format. */
function summarise(report: Record<string, unknown>) {
	const pick = (...keys: string[]) => keys.map((k) => report[k]).find((v) => typeof v === 'string') as string | undefined;
	const cut = (v: string | undefined) => (v && v.length > 300 ? `${v.slice(0, 300)}…` : v);
	return {
		directive: cut(pick('effective-directive', 'effectiveDirective', 'violated-directive')),
		blocked: cut(pick('blocked-uri', 'blockedURL')),
		page: cut(pick('document-uri', 'documentURL')),
		source: cut(pick('source-file', 'sourceFile')),
		line: report['line-number'] ?? report.lineNumber,
		disposition: cut(pick('disposition'))
	};
}

/**
 * Content-Security-Policy violation reports (see `csp` in vite.config.ts). Browsers send them for
 * the report-only policy; they're logged so the policy can be tightened. Always 204: there's
 * nothing for the browser to do with a reply. Rate limited like the rest of /api/v1.
 */
export const POST: RequestHandler = async ({ request }) => {
	try {
		const text = await request.text();
		if (text.length <= 20_000) {
			const body = JSON.parse(text) as unknown;
			// `application/csp-report`: { "csp-report": {...} }. Reporting API: [{ type, body: {...} }].
			const reports = Array.isArray(body)
				? body.map((r) => (r as { body?: Record<string, unknown> })?.body).filter(Boolean)
				: [(body as { 'csp-report'?: Record<string, unknown> })?.['csp-report']].filter(Boolean);
			for (const report of reports.slice(0, 10)) log.warn('csp', 'violation', summarise(report!));
		}
	} catch {
		// Malformed reports are ignored.
	}
	return new Response(null, { status: 204 });
};
