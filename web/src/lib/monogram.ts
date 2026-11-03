/**
 * A group's tile letters: "Lake District crew" → "LD", "Book club" → "BC",
 * "Five" → "FI". Shared so the new-group preview and the invite page show what
 * the server will put on the tile.
 */
export function monogram(name: string): string {
	const words = name.trim().split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w));
	const letters = words.length > 1 ? words.slice(0, 2).map((w) => [...w][0]) : [...(words[0] ?? '?')].slice(0, 2);
	return letters.join('').toUpperCase();
}
