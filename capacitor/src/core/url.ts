const URL_LIKE = /^[^\s]+\.[^\s]{2,}$/;

/**
 * Chrome-style omnibox resolution:
 *   "youtube.com"        → https://youtube.com
 *   "best songs 2026"    → Google search
 *   "http://neverssl.com"→ untouched
 */
export function normalizeInput(raw: string): string {
  const input = raw.trim();
  if (!input) return "";
  if (/^(https?|about|file):/i.test(input)) return input;
  if (!input.includes(" ") && URL_LIKE.test(input)) return `https://${input}`;
  return `https://www.google.com/search?q=${encodeURIComponent(input)}`;
}

/** Split for the omnibox display: bold host + dim path, Chrome-like. */
export function splitUrl(url: string): { host: string; rest: string } {
  try {
    const u = new URL(url);
    return { host: u.host, rest: `${u.pathname}${u.search}`.replace(/\/$/, "") };
  } catch {
    return { host: url, rest: "" };
  }
}
