/**
 * Orbit bookmarks — local IndexedDB store (Module 4).
 *
 * Same contract as historyDB: fire-and-forget, never throws, push-based
 * change notifications. Entries carry `updatedAt` + soft-delete tombstones
 * so the Module 4 sync engine can reconcile multi-device edits.
 */

import { openOrbitDB as openDB, requestAsPromise } from "./db";

export interface Bookmark {
  /** Primary key — the URL itself. Firestore encoding happens at sync time. */
  id: string;
  url: string;
  title: string;
  addedAt: number;
  updatedAt: number;
  /** Soft delete — tombstone survives until a sync round consumes it. */
  deleted?: boolean;
}

const STORE = "bookmarks";

let changeVersion = 0;
const listeners = new Set<() => void>();

function notifyChanged(): void {
  changeVersion++;
  for (const l of listeners) l();
}

export function subscribeBookmarks(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getBookmarksVersion(): number {
  return changeVersion;
}

export async function addBookmark(url: string, title: string): Promise<void> {
  const id = url;
  const now = Date.now();
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readwrite").objectStore(STORE);
    const existing = await requestAsPromise(store.get(id));
    const entry: Bookmark = existing && !existing.deleted
      ? { ...existing, title: title || existing.title, updatedAt: now }
      : { id, url, title: title || url, addedAt: existing?.addedAt ?? now, updatedAt: now };
    store.put(entry);
    notifyChanged();
  } catch {
    /* storage unavailable */
  }
}

/** Soft-delete — tombstone propagates through sync. */
export async function removeBookmark(id: string): Promise<void> {
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readwrite").objectStore(STORE);
    const existing = await requestAsPromise(store.get(id));
    if (!existing) return;
    store.put({ ...existing, deleted: true, updatedAt: Date.now() } satisfies Bookmark);
    notifyChanged();
  } catch {
    /* storage unavailable */
  }
}

export async function getAllBookmarks(): Promise<Bookmark[]> {
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readonly").objectStore(STORE);
    const all = await requestAsPromise(store.getAll() as IDBRequest<Bookmark[]>);
    return all.sort((a, b) => b.updatedAt - a.updatedAt);
  } catch {
    return [];
  }
}

/** Live (non-deleted) bookmarks, oldest-added first for a stable list. */
export async function listBookmarks(): Promise<Bookmark[]> {
  const all = await getAllBookmarks();
  return all.filter((b) => !b.deleted).sort((a, b) => a.addedAt - b.addedAt);
}

/** Sync seam — raw entries INCLUDING tombstones, keyed for the engine. */
export async function getBookmarkTombstones(): Promise<Map<string, Bookmark>> {
  const all = await getAllBookmarks();
  return new Map(all.map((b) => [b.id, b]));
}

/** Module 4 sync seam — remote winners (tombstone = hard delete locally). */
export async function applyRemoteBookmarks(entries: Bookmark[]): Promise<void> {
  if (entries.length === 0) return;
  try {
    const db = await openDB();
    const store = db.transaction(STORE, "readwrite").objectStore(STORE);
    for (const b of entries) {
      if (b.deleted) {
        store.delete(b.id);
      } else {
        store.put(b);
      }
    }
    notifyChanged();
  } catch {
    /* storage unavailable */
  }
}
