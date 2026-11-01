import { api, type Fetch } from './api';
import type { ManualBlock } from '#lib/types';

export type BlockInput = Omit<ManualBlock, 'id'>;

export const listBlocks = (fetch?: Fetch) => api.get<{ blocks: ManualBlock[] }>('/blocks', { fetch }).then((r) => r.blocks);
export const createBlock = (body: BlockInput) => api.post<ManualBlock>('/blocks', body);
export const updateBlock = (id: string, patch: Partial<BlockInput>) => api.patch<ManualBlock>(`/blocks/${id}`, patch);
export const deleteBlock = (id: string) => api.del(`/blocks/${id}`);
