import { api } from './api';
import type { UsernameCheck } from '#lib/types';

/** Whether a username can be yours, and who has it (to invite them). */
export const checkUsername = (username: string) => api.get<UsernameCheck>(`/usernames/${encodeURIComponent(username)}`);
