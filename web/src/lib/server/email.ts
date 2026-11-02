/**
 * Transactional email.
 *
 * Delivery is chosen at runtime, in order:
 *   1. Resend API — when RESEND_API_KEY is set (production).
 *   2. SMTP       — when SMTP_HOST is set (dev uses Mailpit, see docker-compose.yml).
 *   3. Console    — fallback so flows still work unconfigured.
 */
import { env } from './env';

interface Message {
	to: string;
	subject: string;
	html: string;
	text: string;
}

type OtpType = 'sign-in' | 'email-verification' | 'forget-password' | 'change-email';

export async function sendOtpEmail({ email, otp, type }: { email: string; otp: string; type: OtpType }) {
	const intro = type === 'sign-in' ? 'Use this code to sign in to Availabliss.' : 'Use this code to confirm your email.';
	await deliver({
		to: email,
		subject: type === 'sign-in' ? 'Your Availabliss sign-in code' : 'Confirm your email for Availabliss',
		html: layout(`<p style="${P}">${intro}</p>${codeBlock(otp)}<p style="${SMALL}">It expires in 10 minutes. If you didn't ask for it, ignore this email.</p>`),
		text: `${intro}\n\nYour code is ${otp}. It expires in 10 minutes.`
	});
}

export async function sendInviteEmail(p: { email: string; inviterName: string; groupName: string; url: string }) {
	const inviter = escapeHtml(p.inviterName);
	const group = escapeHtml(p.groupName);
	await deliver({
		to: p.email,
		subject: `${p.inviterName} invited you to ${p.groupName} on Availabliss`,
		html: layout(
			`<p style="${P}"><strong>${inviter}</strong> wants to find a time that works for <strong>${group}</strong>.</p>${button(p.url, 'Join the group')}<p style="${SMALL}">This invite expires in 14 days.</p>`
		),
		text: `${p.inviterName} invited you to ${p.groupName} on Availabliss.\n\nJoin: ${p.url}`
	});
}

export async function sendNudgeEmail(p: { email: string; groupName: string; url: string }) {
	const group = escapeHtml(p.groupName);
	await deliver({
		to: p.email,
		subject: `${p.groupName} is waiting on your calendar`,
		html: layout(
			`<p style="${P}">Connect a calendar or mark your busy times so <strong>${group}</strong> can find a time that works for everyone.</p>${button(p.url, 'Add my busy times')}`
		),
		text: `Connect a calendar or mark your busy times so ${p.groupName} can find a time that works.\n\n${p.url}`
	});
}

export async function sendConfirmedEmail(p: { email: string; groupName: string; when: string; url: string }) {
	const group = escapeHtml(p.groupName);
	await deliver({
		to: p.email,
		subject: `Everyone's in: ${p.groupName}, ${p.when}`,
		html: layout(
			`<p style="${P}">Everyone in <strong>${group}</strong> accepted <strong>${escapeHtml(p.when)}</strong>.</p>${button(p.url, 'Add to calendar')}`
		),
		text: `Everyone in ${p.groupName} accepted ${p.when}.\n\n${p.url}`
	});
}

// ─── Delivery ───────────────────────────────────────────────────────────────

async function deliver(msg: Message): Promise<void> {
	if (env.email.resendApiKey) return sendViaResend(msg);
	if (env.email.smtpHost) return sendViaSmtp(msg);
	console.log(`\n[email] to: ${msg.to}\n[email] ${msg.subject}\n${msg.text}\n`);
}

async function sendViaResend(msg: Message): Promise<void> {
	const res = await fetch('https://api.resend.com/emails', {
		method: 'POST',
		headers: { authorization: `Bearer ${env.email.resendApiKey}`, 'content-type': 'application/json' },
		body: JSON.stringify({ from: env.email.from, to: [msg.to], subject: msg.subject, html: msg.html, text: msg.text })
	});
	if (!res.ok) throw new Error(`Resend failed (${res.status}): ${await res.text().catch(() => '')}`);
}

// nodemailer is only loaded on the SMTP path.
let transport: Promise<import('nodemailer').Transporter> | null = null;

async function sendViaSmtp(msg: Message): Promise<void> {
	transport ??= import('nodemailer').then((nodemailer) =>
		nodemailer.createTransport({
			host: env.email.smtpHost!,
			port: env.email.smtpPort,
			secure: env.email.smtpSecure,
			auth: env.email.smtpUser ? { user: env.email.smtpUser, pass: env.email.smtpPassword ?? '' } : undefined
		})
	);
	await (await transport).sendMail({ from: env.email.from, ...msg });
}

// ─── Templates ──────────────────────────────────────────────────────────────

const INK = '#1A1B3A';
const MUTED = '#4D5072';
const ACCENT = '#5546FF';
const P = `margin:0 0 16px;font-size:15px;line-height:1.5;color:${MUTED};`;
const SMALL = `margin:16px 0 0;font-size:13px;line-height:1.5;color:${MUTED};`;

function layout(content: string): string {
	return `<!doctype html><html><body style="margin:0;background:#ECEEF7;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:${INK};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="padding:32px 16px;"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#FFFFFF;border-radius:24px;">
<tr><td style="padding:32px 32px 8px;font-size:20px;font-weight:700;letter-spacing:-0.02em;">Availabliss</td></tr>
<tr><td style="padding:8px 32px 32px;">${content}</td></tr>
</table></td></tr></table></body></html>`;
}

function codeBlock(code: string): string {
	return `<div style="font-family:ui-monospace,Menlo,Consolas,monospace;font-size:32px;font-weight:700;letter-spacing:0.22em;text-align:center;background:#F3F4FA;border-radius:16px;padding:20px 12px;color:${INK};">${escapeHtml(code)}</div>`;
}

function button(url: string, label: string): string {
	return `<a href="${escapeHtml(url)}" style="display:block;text-align:center;background:${ACCENT};color:#FFFFFF;text-decoration:none;font-weight:600;font-size:15px;padding:14px 20px;border-radius:14px;">${escapeHtml(label)}</a>`;
}

function escapeHtml(s: string): string {
	return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
