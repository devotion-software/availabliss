import { json } from '@sveltejs/kit';

export const GET = () => json({ ok: true, version: __APP_VERSION__ });
