import { createHash } from 'node:crypto';

/**
 * Opaque key for a provider's event or recurring series id: a truncated SHA-256, so the id itself
 * (which can carry an email address or title on some providers) is never stored.
 */
export const eventKey = (id: string) => createHash('sha256').update(id).digest('base64url').slice(0, 22);
