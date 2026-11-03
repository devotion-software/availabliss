import { afterEach, beforeEach, describe, expect, spyOn, test } from 'bun:test';
import { assertPublicUrl, isPublicAddress, publicFetch, resolver } from './public-fetch';

describe('isPublicAddress', () => {
	test.each(['8.8.8.8', '93.184.215.14', '17.253.144.10', '2606:4700::1111', '2a00:1450:4009:81f::200e'])('allows %s', (ip) => {
		expect(isPublicAddress(ip)).toBe(true);
	});

	test.each([
		'127.0.0.1',
		'10.1.2.3',
		'172.16.0.1',
		'172.31.255.255',
		'192.168.1.1',
		'169.254.169.254',
		'100.64.0.1',
		'0.0.0.0',
		'224.0.0.1',
		'255.255.255.255',
		'::',
		'::1',
		'fe80::1',
		'fd00::1',
		'fc00::1',
		'ff02::1',
		'::ffff:127.0.0.1',
		'::ffff:a9fe:a9fe',
		'64:ff9b::a9fe:a9fe',
		'2001:db8::1',
		'not-an-ip'
	])('blocks %s', (ip) => {
		expect(isPublicAddress(ip)).toBe(false);
	});
});

describe('assertPublicUrl', () => {
	let dns: ReturnType<typeof spyOn>;
	beforeEach(() => {
		dns = spyOn(resolver, 'lookup').mockImplementation(async (host: string) =>
			host === 'public.example.com' ? ['93.184.215.14'] : host === 'mixed.example.com' ? ['93.184.215.14', '10.0.0.1'] : ['127.0.0.1']
		);
	});
	afterEach(() => dns.mockRestore());

	test('allows public https hosts', async () => {
		await assertPublicUrl(new URL('https://public.example.com/dav/'));
	});

	test.each([
		'http://public.example.com/',
		'file:///etc/passwd',
		'https://localhost/',
		'https://mixed.example.com/',
		'https://127.0.0.1/',
		'https://2130706433/',
		'https://[::1]/',
		'https://169.254.169.254/latest/meta-data/',
		'https://user:pass@public.example.com/'
	])('refuses %s', async (url) => {
		await expect(assertPublicUrl(new URL(url))).rejects.toMatchObject({ status: 400 });
	});
});

describe('publicFetch', () => {
	const realFetch = globalThis.fetch;
	let dns: ReturnType<typeof spyOn>;
	beforeEach(() => {
		dns = spyOn(resolver, 'lookup').mockImplementation(async () => ['93.184.215.14']);
	});
	afterEach(() => {
		globalThis.fetch = realFetch;
		dns.mockRestore();
	});

	test('follows same-origin redirects with credentials and other origins without', async () => {
		const seen: { url: string; auth: string | null }[] = [];
		globalThis.fetch = (async (input: URL | string, init?: RequestInit) => {
			const url = String(input);
			seen.push({ url, auth: new Headers(init?.headers).get('authorization') });
			if (url === 'https://a.example.com/') return new Response(null, { status: 301, headers: { location: '/next' } });
			if (url === 'https://a.example.com/next') return new Response(null, { status: 307, headers: { location: 'https://b.example.com/' } });
			return new Response('ok');
		}) as typeof fetch;
		const { res, url } = await publicFetch('https://a.example.com/', { headers: { authorization: 'Basic x' } });
		expect(await res.text()).toBe('ok');
		expect(url.toString()).toBe('https://b.example.com/');
		expect(seen).toEqual([
			{ url: 'https://a.example.com/', auth: 'Basic x' },
			{ url: 'https://a.example.com/next', auth: 'Basic x' },
			{ url: 'https://b.example.com/', auth: null }
		]);
	});

	test('gives up after too many redirects', async () => {
		globalThis.fetch = (async () => new Response(null, { status: 302, headers: { location: '/again' } })) as unknown as typeof fetch;
		await expect(publicFetch('https://a.example.com/')).rejects.toThrow('Too many redirects');
	});
});
