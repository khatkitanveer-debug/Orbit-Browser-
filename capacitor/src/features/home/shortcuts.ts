/**
 * Home shortcuts (Module 2).
 *
 * Ship state = exactly what the spec asked for: YouTube + a free Indian
 * music service (JioSaavn), plus the "+" tile that grows the grid with
 * user shortcuts. Custom list persists in localStorage — a handful of tiny
 * strings does not justify an IndexedDB round-trip on every keystroke.
 * Incognito (Module 4) reads the same list; shortcuts are config, not
 * history, so nothing to suppress here.
 */

export interface Shortcut {
  id: string;
  label: string;
  url: string;
}

export const DEFAULT_SHORTCUTS: readonly Shortcut[] = [
  { id: "youtube", label: "YouTube", url: "https://www.youtube.com/" },
  { id: "jiosaavn", label: "JioSaavn", url: "https://www.jiosaavn.com/" },
] as const;

export const MAX_CUSTOM = 6;

const STORAGE_KEY = "orbit.shortcuts.v1";
const UPDATED_AT_KEY = "orbit.shortcuts.updatedAt";

export function loadCustomShortcuts(): Shortcut[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    const out: Shortcut[] = [];
    for (const item of parsed) {
      if (
        item &&
        typeof item === "object" &&
        typeof (item as Shortcut).id === "string" &&
        typeof (item as Shortcut).label === "string" &&
        /^https?:\/\//i.test((item as Shortcut).url ?? "")
      ) {
        out.push({ id: (item as Shortcut).id, label: (item as Shortcut).label, url: (item as Shortcut).url });
      }
      if (out.length >= MAX_CUSTOM) break;
    }
    return out;
  } catch {
    return [];
  }
}

export function saveCustomShortcuts(list: Shortcut[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, MAX_CUSTOM)));
    localStorage.setItem(UPDATED_AT_KEY, String(Date.now()));
  } catch {
    /* storage unavailable — shortcuts stay in-memory for the session */
  }
}

/** Module 4 sync — wall clock of the last list mutation. */
export function getShortcutsUpdatedAt(): number {
  try {
    return Number(localStorage.getItem(UPDATED_AT_KEY)) || 0;
  } catch {
    return 0;
  }
}

/** Module 4 sync — replace the whole list with a remote winner. */
export function replaceCustomShortcuts(list: Shortcut[]): void {
  saveCustomShortcuts(list);
}

/**
 * Strict shortcut-URL resolver — unlike the omnibox, a shortcut can never
 * become a Google search. "jiosaavn.com" → https://www.jiosaavn.com/,
 * garbage → null.
 */
export function canonicalShortcutUrl(raw: string): string | null {
  const input = raw.trim();
  if (!input || /\s/.test(input)) return null;
  const candidate = /^https?:\/\//i.test(input) ? input : `https://${input}`;
  try {
    const u = new URL(candidate);
    if (u.protocol !== "https:" && u.protocol !== "http:") return null;
    if (!u.hostname.includes(".") || u.hostname.endsWith(".")) return null;
    return u.toString();
  } catch {
    return null;
  }
}

/** "https://www.jiosaavn.com/" → "Jiosaavn" (user can always rename). */
export function suggestLabel(url: string): string {
  try {
    const host = new URL(url).hostname.replace(/^www\./, "");
    const name = host.split(".")[0] || host;
    return name.charAt(0).toUpperCase() + name.slice(1);
  } catch {
    return "";
  }
}

export function newShortcutId(): string {
  return `c${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
}
