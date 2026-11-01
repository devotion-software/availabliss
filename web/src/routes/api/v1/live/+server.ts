import { authed } from '#lib/server/http';
import { subscribe } from '#lib/server/realtime';
import type { LiveEvent } from '#lib/types';

const HEARTBEAT_MS = 25_000;

/** Server-Sent Events covering every group the user belongs to. */
export const GET = authed(({ userId }) => {
	const encoder = new TextEncoder();
	let cleanup = () => {};

	const stream = new ReadableStream({
		start(controller) {
			const write = (chunk: string) => {
				try {
					controller.enqueue(encoder.encode(chunk));
				} catch {
					cleanup();
				}
			};
			write(': connected\n\n');
			const unsubscribe = subscribe(userId, (event: LiveEvent) => write(`data: ${JSON.stringify(event)}\n\n`));
			// Keeps proxies from closing idle connections.
			const heartbeat = setInterval(() => write(': ping\n\n'), HEARTBEAT_MS);
			cleanup = () => {
				clearInterval(heartbeat);
				unsubscribe();
			};
		},
		cancel() {
			cleanup();
		}
	});

	return new Response(stream, {
		headers: {
			'content-type': 'text/event-stream',
			'cache-control': 'no-cache, no-transform',
			connection: 'keep-alive',
			'x-accel-buffering': 'no'
		}
	});
});
