/** Emoji reactions on chat messages: one per person per message; pure helpers shared by the chat and threads. */
import type { Message, MessageReaction, MessageReactions } from '#lib/types';
import { isSingleEmoji } from '#lib/schemas';

/** The row at the top of the message menu (a double tap / double click is ❤️). */
export const QUICK_REACTIONS = ['❤️', '👍', '😂', '😮', '😢', '🙏'] as const;
export const HEART = '❤️';

/** More to pick from under "+", besides any emoji typed in. */
export const MORE_REACTIONS = [
	'🔥',
	'🎉',
	'👏',
	'🙌',
	'💯',
	'✅',
	'👀',
	'🤔',
	'😍',
	'🥰',
	'😘',
	'😊',
	'😁',
	'🤣',
	'😅',
	'😎',
	'🥳',
	'🤩',
	'😴',
	'🙃',
	'😬',
	'😱',
	'😭',
	'😡',
	'👎',
	'👌',
	'🤞',
	'💪',
	'🫶',
	'🤝',
	'🍻',
	'🥂',
	'☕',
	'🍕',
	'🌮',
	'🎂',
	'⚽',
	'🏃',
	'✈️',
	'🏖️',
	'🌞',
	'🌧️',
	'❄️',
	'⭐',
	'💔',
	'💀',
	'🙈',
	'🤷'
] as const;

/** Your reaction to a message, or null. */
export const myReaction = (reactions: MessageReaction[] | undefined, meId: string) => reactions?.find((r) => r.userIds.includes(meId))?.emoji ?? null;

/** What tapping [emoji] does: reacts with it, or takes it back if it's already yours. */
export const toggled = (reactions: MessageReaction[] | undefined, meId: string, emoji: string): string | null =>
	myReaction(reactions, meId) === emoji ? null : emoji;

/** Reactions with your one reaction set to [emoji] (or removed for null), as the server would order them. */
export function withReaction(reactions: MessageReaction[] | undefined, meId: string, emoji: string | null): MessageReaction[] {
	const next = (reactions ?? []).map((r) => ({ ...r, userIds: r.userIds.filter((id) => id !== meId) })).filter((r) => r.userIds.length);
	if (!emoji) return next;
	const same = next.find((r) => r.emoji === emoji);
	if (same) same.userIds.push(meId);
	else next.push({ emoji, userIds: [meId] });
	return next;
}

/** Applies a `message.reactions` payload (or a PUT/DELETE result); the same array when the message isn't loaded. */
export function applyReactions(messages: Message[], update: MessageReactions): Message[] {
	const i = messages.findIndex((m) => m.id === update.messageId);
	if (i < 0) return messages;
	const next = messages.slice();
	next[i] = { ...messages[i], reactions: update.reactions };
	return next;
}

/** Can be reacted to: anything but group updates, deleted and unsent messages. */
export const canReact = (m: Message) => m.kind !== 'system' && !m.deleted && !m.id.startsWith('temp-');

/** A typed-in reaction: one emoji, else null. */
export function typedEmoji(text: string): string | null {
	const t = text.trim();
	return t && isSingleEmoji(t) ? t : null;
}

/** "Ana, Sam and 2 others" for who reacted with one emoji (you as "You", first). */
export function reactorNames(userIds: string[], meId: string, name: (id: string) => string): string {
	const names = [...userIds].sort((a, b) => Number(b === meId) - Number(a === meId)).map((id) => (id === meId ? 'You' : name(id).split(' ')[0]));
	if (names.length <= 3) return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} and ${names.at(-1)}`;
	return `${names.slice(0, 2).join(', ')} and ${names.length - 2} others`;
}
