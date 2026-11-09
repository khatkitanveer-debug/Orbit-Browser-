// Orbit Browser — shared client helpers

import { NEWTAB } from "./types";

export function uid(): string {
  return Math.random().toString(36).slice(2) + Date.now().toString(36);
}

export function titleFor(url: string): string {
  if (url === NEWTAB) return "New Tab";
  if (url.startsWith("orbit://search")) {
    const q = decodeURIComponent(url.replace("orbit://search?q=", "")).trim();
    return q ? `Orbit AI: ${q.slice(0, 28)}` : "Orbit AI Search";
  }
  if (url.startsWith("orbit://history")) return "History";
  if (url.startsWith("orbit://bookmarks")) return "Bookmarks";
  if (url.startsWith("orbit://settings")) return "Settings";
  if (url.startsWith("orbit://about")) return "About Orbit";
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url.slice(0, 40) || "New Tab";
  }
}

export function isExternal(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

export function domainOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url.slice(0, 24);
  }
}

const TILE_COLORS = [
  "bg-emerald-600",
  "bg-teal-600",
  "bg-cyan-700",
  "bg-rose-600",
  "bg-orange-600",
  "bg-violet-600",
  "bg-fuchsia-600",
  "bg-lime-700",
];

export function tileColorFor(seed: string): string {
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  return TILE_COLORS[h % TILE_COLORS.length];
}

export function letterOf(seed: string): string {
  const d = domainOf(seed);
  return (d.charAt(0) || "T").toUpperCase();
}

// Omnibox input -> URL (Chrome-jaisa logic: URL hai ya search?)
export function normalizeInput(input: string): string {
  const t = input.trim();
  if (!t) return NEWTAB;
  if (/^orbit:\/\//i.test(t)) return t;
  if (/^tez:\/\//i.test(t)) return t.replace(/^tez:\/\//i, "orbit://"); // purana scheme
  if (/^https?:\/\//i.test(t)) return t;
  if (/^localhost(:\d+)?([\/?#].*)?$/i.test(t)) return "http://" + t;
  if (/^[a-z0-9-]+(\.[a-z0-9-]+)+(:\d+)?([\/?#].*)?$/i.test(t)) return "https://" + t;
  return "orbit://search?q=" + encodeURIComponent(t);
}

export function searchQueryOf(url: string): string {
  try {
    const u = new URL(url.replace(/^orbit:\/\//i, "http://orbit.internal/").replace(/^tez:\/\//i, "http://orbit.internal/"));
    return u.searchParams.get("q") ?? "";
  } catch {
    return "";
  }
}

export function proxyUrl(url: string, tabId: string): string {
  return `/api/proxy?url=${encodeURIComponent(url)}&t=${encodeURIComponent(tabId)}`;
}

export function fmtTime(ts: number): string {
  return new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function dayLabel(ts: number): string {
  const d = new Date(ts);
  const today = new Date();
  const yest = new Date();
  yest.setDate(today.getDate() - 1);
  const same = (a: Date, b: Date) => a.toDateString() === b.toDateString();
  if (same(d, today)) return "Aaj";
  if (same(d, yest)) return "Kal";
  return d.toLocaleDateString([], { day: "numeric", month: "short", year: "numeric" });
}
