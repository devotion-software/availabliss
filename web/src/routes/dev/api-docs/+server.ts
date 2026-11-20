import { error } from '@sveltejs/kit';
import type { RequestHandler } from './$types';

/**
 * The API reference, rendered from docs/openapi.json with Redoc. Only while developing
 * (`make dev`, then http://localhost:5190/dev/api-docs): a production build answers 404, so the
 * API's shape isn't published. A plain HTML response rather than a page, so the app's
 * Content-Security-Policy (which only allows our own scripts) doesn't block Redoc.
 */
export const GET: RequestHandler = () => {
	if (!import.meta.env.DEV) error(404, 'Not found');
	return new Response(
		`<!doctype html>
<html lang="en">
	<head>
		<meta charset="utf-8" />
		<meta name="viewport" content="width=device-width, initial-scale=1" />
		<title>Availabliss API</title>
		<!-- Redoc only has a light theme; without this a dark browser darkens the page under its dark text. -->
		<meta name="color-scheme" content="light" />
		<style>html, body { margin: 0; background: #fff; color-scheme: light; }</style>
	</head>
	<body>
		<redoc spec-url="/dev/api-docs/openapi.json"></redoc>
		<script src="https://cdn.jsdelivr.net/npm/redoc@2.1.5/bundles/redoc.standalone.js"></script>
	</body>
</html>`,
		{ headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } }
	);
};
