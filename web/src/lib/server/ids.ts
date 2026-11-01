import { randomUUID } from 'node:crypto';
import { customAlphabet } from 'nanoid';

export const newId = (): string => randomUUID();

/** URL-safe, unguessable token for invite links. */
export const newToken = customAlphabet('0123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ', 32);
