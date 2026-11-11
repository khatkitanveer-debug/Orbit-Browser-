/**
 * Orbit history — local IndexedDB store (Module 2).
 *
 * Contract:
 *  • ONLY this module writes history. Nothing leaves the device — feed,
 *    top-sites and shortcuts all read from here. Sync (Module 4) will plug
 *    in later at this exact seam.
 *  • Recording is fire-and-forget and never throws: a failed/degraded
 *    IndexedDB (private-mode quirks, storage pressure) silently disables
 *    persistence instead of touching the browsing session.
 *  • Change notification is push-based (subscribe), so the feed rebuilds
 *    only when data actually changes — zero polling loops.
 */

export interface HistoryEntry {
  /** Canonical URL — primary key. */
  url: string;
  host: string;
  title: string;
  visitCount: number;
  firstVisit: number;
  lastVisit: number;
}

const DB_NAME = "orbit";
const DB_VERSION = 1;
const STORE = "history";
const LAST_VISIT_INDEX = "lastVisit";
/** Hard cap — prune keeps the store O(2000) regardless of usage years. */
const MAX_ENTRIES = 2000;
/** Prune check frequency — a full count() every put would be wasteful. */
const PRUNE_EVERY = 32;

let dbPromise: Promise<IDBDatabase> | null = null;
let putsSincePrune = 0;

/* ------------------------------ change feed ------------------------------ */

let changeVersion = 0;
const listeners = new Set<() => void>();

function notifyChanged(): void {
  changeVersion++;
  for (const l of listeners) l();
}

/** Push-based subscription for React (useSyncExternalStore). */
export function subscribeHistory(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getHistoryVersion(): number {
  return changeVersion;
}

/* ----------------------------- incognito seam ---------------------------- */

let suppressed = false;

/** Module 4 ("True Incognito") flips this for the session — no disk writes. */
export function setHistorySuppressed(value: boolean): void {
  suppressed = value;
}

/* --------------------------------- plumbing ------------------------------ */

function openDB(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(STORE)) {
          const store = db.createObjectStore(STORE, { keyPath: "url" });
          store.createIndex(LAST_VISIT_INDEX, "lastVisit");
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
      req.onblocked = () => reject(new Error("orbit-history: blocked"));
    });
    dbPromise.catch(() => {
      dbPromise = null; // allow a retry on the next call
    });
  }
  return dbPromise;
}

function isRecordableUrl(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return "";
  }
}

function requestAsPromise<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

/** Keeps the store bounded: drops the oldest entries beyond MAX_ENTRIES. */
async function pruneIfNeeded(db: IDBDatabase): Promise<void> {
  putsSincePrune = 0;
  const index = db.transaction(STORE, "readwrite").objectStore(STORE).index(LAST_VISIT_INDEX);
  const total = await requestAsPromise(index.count());
  if (total <= MAX_ENTRIES) return;
  const excess = total - MAX_ENTRIES;
  await new Promise<void>((resolve, reject) => {
    let left = excess;
    const req = index.openCursor(); // ascending lastVisit = oldest first
    req.onsuccess = () => {
      const cursor = req.result;
      if (!cursor || left <= 0) {
        resolve();
        return;
      }
      void cursor.delete();
      left--;
      void cursor.continue();
    };
    req.onerror = () => reject(req.error);
  });
}

/* --------------------------------- public -------------------------------- */

/**
 * Records one real visit. Merges by URL: count++, lastVisit=now, title
 * upgraded when the page gave us a real one. Safe to call on every page
 * event — the URL-keyed merge makes repeats idempotent-ish (a reload IS
 * another visit, exactly like Chrome counts it).
 */
export async function recordVisit(url: string, title: string): Promise<void> {
  if (suppressed || !isRecordableUrl(url)) return;
  const now = Date.now();
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readwrite").objectStore(STORE);
    const existing = await requestAsPromise(store.get(url));
    const entry: HistoryEntry = existing
      ? {
          ...existing,
          title: title || existing.title,
          visitCount: existing.visitCount + 1,
          lastVisit: now,
        }
      : { url, host: hostOf(url), title: title || url, visitCount: 1, firstVisit: now, lastVisit: now };
    store.put(entry);
    if (++putsSincePrune >= PRUNE_EVERY) void pruneIfNeeded(db);
    notifyChanged();
  } catch {
    /* storage unavailable — browsing continues untouched */
  }
}

/**
 * Title-only upgrade (SPA pages settle their <title> after our visit event).
 * Does not bump counts — same URL, same visit.
 */
export async function updateTitle(url: string, title: string): Promise<void> {
  if (suppressed || !title || !isRecordableUrl(url)) return;
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readwrite").objectStore(STORE);
    const existing = await requestAsPromise(store.get(url));
    if (!existing || existing.title === title) return;
    store.put({ ...existing, title });
    notifyChanged();
  } catch {
    /* storage unavailable */
  }
}

export async function getAllEntries(): Promise<HistoryEntry[]> {
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readonly").objectStore(STORE);
    const all = await requestAsPromise(store.getAll() as IDBRequest<HistoryEntry[]>);
    return all.sort((a, b) => b.lastVisit - a.lastVisit);
  } catch {
    return [];
  }
}

export async function clearHistory(): Promise<void> {
  try {
    const db = await openDB();
    db.transaction(STORE, "readwrite").objectStore(STORE).clear();
    notifyChanged();
  } catch {
    /* storage unavailable */
  }
}
