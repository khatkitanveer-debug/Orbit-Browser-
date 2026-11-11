/**
 * Discover feed engine (Module 2) — Chrome-Discover-style cards, but honest:
 * 100% generated on-device from the local IndexedDB history. No network
 * fetches, no profiling, no tracking — "Discover" resurfaces what the user
 * actually engaged with.
 *
 *   category = domain classifier (music | video | news | general)
 *   score    = Σ visitCount × 0.5^(hoursSinceVisit / 36)   ← recency decay
 *
 * One card per (category, host): the representative entry is the
 * highest-decay visit for that host (what the user engaged with most), and
 * the card score is the host's total decayed engagement.
 */

import type { HistoryEntry } from "../../core/historyDB";

export type FeedCategory = "music" | "video" | "news" | "general";

export interface FeedCard {
  /** Stable key: `${category}:${host}`. */
  id: string;
  category: Exclude<FeedCategory, "general">;
  host: string;
  title: string;
  /** Representative (hottest) visit — where the card opens. */
  url: string;
  /** Site origin — "More from <host>" target. */
  homeUrl: string;
  lastVisit: number;
  score: number;
  /** Present for YouTube watch/shorts URLs — drives the real thumbnail. */
  videoId?: string;
}

export interface TopSite {
  host: string;
  url: string;
  visitCount: number;
}

export interface FeedResult {
  cards: FeedCard[];
  topSites: TopSite[];
}

/* ----------------------------- classification ---------------------------- */
/* Suffix rules: host === rule OR host endsWith "." + rule.
   Order matters — specific hosts (music.youtube.com) are checked before
   their general parents (youtube.com). */

const MUSIC_HOSTS = [
  "jiosaavn.com",
  "saavn.com",
  "wynk.in",
  "gaana.com",
  "spotify.com",
  "music.youtube.com",
  "soundcloud.com",
  "hungama.com",
  "music.amazon.in",
  "music.apple.com",
  "pandora.com",
  "resso.com",
  "boomplay.com",
  "raaga.com",
] as const;

const NEWS_HOSTS = [
  "news.google.com",
  "ndtv.com",
  "timesofindia.indiatimes.com",
  "indiatoday.in",
  "hindustantimes.com",
  "indianexpress.com",
  "thehindu.com",
  "bbc.com",
  "bbc.co.uk",
  "cnn.com",
  "aajtak.in",
  "amarujala.com",
  "dainikbhaskar.com",
  "livemint.com",
  "economictimes.indiatimes.com",
  "news18.com",
  "republicworld.com",
  "inshorts.com",
  "moneycontrol.com",
  "business-standard.com",
  "financialexpress.com",
  "mid-day.com",
  "india.com",
  "firstpost.com",
] as const;

const VIDEO_HOSTS = [
  "youtube.com",
  "youtu.be",
  "vimeo.com",
  "dailymotion.com",
  "hotstar.com",
  "jiohotstar.com",
  "netflix.com",
  "primevideo.com",
  "sonyliv.com",
  "zee5.com",
  "mxplayer.in",
  "twitch.tv",
  "jiocinema.com",
] as const;

/** Hosts that are already one tap away on the home grid. */
const SHORTCUT_HOSTS = new Set(["youtube.com", "m.youtube.com", "music.youtube.com", "jiosaavn.com"]);

function matches(host: string, rules: readonly string[]): boolean {
  return rules.some((r) => host === r || host.endsWith(`.${r}`));
}

export function classifyHost(host: string): FeedCategory {
  const h = host.toLowerCase();
  if (!h) return "general";
  if (matches(h, MUSIC_HOSTS)) return "music";
  if (matches(h, NEWS_HOSTS)) return "news";
  if (matches(h, VIDEO_HOSTS)) return "video";
  return "general";
}

/* ------------------------------ youtube ids ------------------------------ */

const YT_ID = /(?:youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/|embed\/|live\/)|youtu\.be\/)([\w-]{11})/;

export function youtubeIdOf(url: string): string | undefined {
  return url.match(YT_ID)?.[1];
}

/* --------------------------------- build --------------------------------- */

const HALF_LIFE_HOURS = 36;
const MAX_CARDS = 12;
const MAX_TOP_SITES = 4;

interface Bucket {
  category: FeedCategory;
  host: string;
  best: HistoryEntry;
  bestWeight: number;
  score: number;
  visitCount: number;
  lastVisit: number;
}

function decayedWeight(entry: HistoryEntry, now: number): number {
  const hours = Math.max(0, (now - entry.lastVisit) / 3_600_000);
  return entry.visitCount * Math.pow(0.5, hours / HALF_LIFE_HOURS);
}

/** Brand suffixes that sites bake into <title> — redundant next to the host chip. */
const TITLE_BRANDS =
  "YouTube|JioSaavn|Netflix|Hotstar|JioHotstar|NDTV|BBC News|CNN|Times of India|Hindustan Times|India Today|The Hindu|Indian Express|News18|Moneycontrol|Livemint|Reuters|CNBC TV18|Aaj Tak|Dainik Bhaskar";

function prettyTitle(entry: HistoryEntry): string {
  const t = entry.title.trim();
  if (!t || t === entry.url) return entry.host.replace(/^www\./, "");
  // Strip noisy suffixes like " - YouTube" / " | JioSaavn" — host already shown.
  const stripped = t.replace(new RegExp(`\\s+[-–|]\\s+(${TITLE_BRANDS})\\s*$`, "i"), "").trim();
  return stripped || t;
}

export function buildFeed(entries: HistoryEntry[], now = Date.now()): FeedResult {
  const buckets = new Map<string, Bucket>();

  for (const e of entries) {
    const category = classifyHost(e.host);
    const key = `${category}:${e.host}`;
    const w = decayedWeight(e, now);
    const bucket = buckets.get(key);
    if (bucket) {
      bucket.score += w;
      bucket.visitCount += e.visitCount;
      if (w > bucket.bestWeight) {
        bucket.bestWeight = w;
        bucket.best = e;
      }
      if (e.lastVisit > bucket.lastVisit) bucket.lastVisit = e.lastVisit;
    } else {
      buckets.set(key, {
        category,
        host: e.host,
        best: e,
        bestWeight: w,
        score: w,
        visitCount: e.visitCount,
        lastVisit: e.lastVisit,
      });
    }
  }

  const cards: FeedCard[] = [];
  const topSites: TopSite[] = [];

  for (const b of buckets.values()) {
    if (b.category !== "general") {
      cards.push({
        id: `${b.category}:${b.host}`,
        category: b.category,
        host: b.host,
        title: prettyTitle(b.best),
        url: b.best.url,
        homeUrl: (() => {
          try {
            return new URL(b.best.url).origin + "/";
          } catch {
            return `https://${b.host}/`;
          }
        })(),
        lastVisit: b.lastVisit,
        score: b.score,
        videoId: b.category === "video" ? youtubeIdOf(b.best.url) : undefined,
      });
    } else if (!SHORTCUT_HOSTS.has(b.host)) {
      // Frequently-visited general sites — skip the ones already on the grid.
      topSites.push({ host: b.host, url: b.best.url, visitCount: b.visitCount });
    }
  }

  cards.sort((a, b) => b.score - a.score);
  topSites.sort((a, b) => b.visitCount - a.visitCount || b.host.localeCompare(a.host));

  return {
    cards: cards.slice(0, MAX_CARDS),
    topSites: topSites.slice(0, MAX_TOP_SITES),
  };
}

/* --------------------------------- format -------------------------------- */

/** "Just now", "12m ago", "3h ago", "2d ago", then a short date. */
export function relativeTime(ts: number, now = Date.now()): string {
  const mins = Math.floor((now - ts) / 60_000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(ts).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}
