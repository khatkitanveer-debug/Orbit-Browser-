/**
 * Module 4 — Chrome-Sync-style delta engine.
 *
 * Design (what "merge-by-updatedAt with tombstones" means here):
 *   • every mutation stamps `updatedAt` (wall clock of the local write);
 *   • a per-user cursor (`lastSyncAt`) bounds every round: pull remote docs
 *     with updatedAt > cursor, push local entries with updatedAt > cursor;
 *   • for each conflicting id, the NEWER updatedAt wins outright — no field
 *     juggling, predictable on every device;
 *   • deletions are tombstones (`deleted: true`) so they survive until
 *     every device has pulled them;
 *   • the cursor advances to max(local now, newest remote updatedAt) so a
 *     skewed clock on one device can never skip another device's writes.
 *
 * reconcile*() are PURE — scripts/syncSanity.ts runs them offline against
 * a fake backend. syncNow() is the only place that touches I/O.
 */

import type { HistoryEntry } from "../historyDB";
import type { Bookmark } from "../bookmarksDB";

export interface HistoryDoc extends HistoryEntry {
  deleted?: boolean;
}

export type BookmarkDoc = Bookmark; // already carries updatedAt + deleted

export interface ShortcutItem {
  id: string;
  label: string;
  url: string;
}

export interface ShortcutsDoc {
  items: ShortcutItem[];
  updatedAt: number;
}

export interface ReconciledHistory {
  /** Remote winners to write into the local IndexedDB (incl. deletions to remove). */
  applyLocal: HistoryDoc[];
  /** Local entries that beat (or miss) their remote twin — must be pushed. */
  toPush: HistoryEntry[];
}

export interface ReconciledBookmarks {
  applyLocal: BookmarkDoc[];
  toPush: Bookmark[];
}

export type ShortcutsVerdict =
  | { mode: "apply-remote"; doc: ShortcutsDoc }
  | { mode: "push-local"; doc: ShortcutsDoc }
  | { mode: "in-sync" };

/** Server documents newer than the cursor beat stale locals; vice versa. */
export function reconcileHistory(local: HistoryEntry[], remote: HistoryDoc[]): ReconciledHistory {
  const byUrl = new Map(local.map((e) => [e.url, e]));
  const applyLocal: HistoryDoc[] = [];
  const toPush: HistoryEntry[] = [];
  const touchedRemote = new Set<string>();

  for (const doc of remote) {
    touchedRemote.add(doc.url);
    const mine = byUrl.get(doc.url);
    if (!mine) {
      if (doc.deleted) continue; // tombstone for something we never had
      applyLocal.push(doc);
      continue;
    }
    const remoteAt = doc.updatedAt ?? doc.lastVisit ?? 0;
    const localAt = mine.updatedAt ?? mine.lastVisit;
    if (remoteAt > localAt) {
      applyLocal.push(doc); // may be a deletion — applyLocal handles both
    } else if (remoteAt < localAt) {
      toPush.push(mine);
    }
  }

  // Locals the pull never mentioned but the cursor says changed locally:
  // they are missing remotely → push.
  for (const e of local) {
    if (touchedRemote.has(e.url)) continue;
    toPush.push(e);
  }
  return { applyLocal, toPush };
}

export function reconcileBookmarks(local: Bookmark[], remote: BookmarkDoc[]): ReconciledBookmarks {
  const byId = new Map(local.map((b) => [b.id, b]));
  const applyLocal: BookmarkDoc[] = [];
  const toPush: Bookmark[] = [];
  const touchedRemote = new Set<string>();

  for (const doc of remote) {
    touchedRemote.add(doc.id);
    const mine = byId.get(doc.id);
    if (!mine) {
      if (doc.deleted) continue;
      applyLocal.push(doc);
      continue;
    }
    const remoteAt = doc.updatedAt ?? 0;
    const localAt = mine.updatedAt ?? 0;
    if (remoteAt > localAt) {
      applyLocal.push(doc);
    } else if (remoteAt < localAt) {
      toPush.push(mine); // includes tombstones — deletions must propagate
    }
  }
  for (const b of local) {
    if (touchedRemote.has(b.id)) continue;
    toPush.push(b);
  }
  return { applyLocal, toPush };
}

/** Whole-list, last-writer-wins — the shortcut list is tiny and atomic. */
export function reconcileShortcuts(
  localItems: ShortcutItem[],
  localUpdatedAt: number,
  remote: ShortcutsDoc | null,
): ShortcutsVerdict {
  if (!remote) {
    return { mode: "push-local", doc: { items: localItems, updatedAt: localUpdatedAt } };
  }
  if (remote.updatedAt > localUpdatedAt) {
    return { mode: "apply-remote", doc: remote };
  }
  if (localUpdatedAt > remote.updatedAt) {
    return { mode: "push-local", doc: { items: localItems, updatedAt: localUpdatedAt } };
  }
  return { mode: "in-sync" };
}

/** Firestore caps batched writes at 500 — stay comfortably under. */
export function chunk<T>(items: T[], size = 450): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

/** Transport seam — Firestore implements it; tests inject a fake. */
export interface SyncBackend {
  pullHistory(since: number): Promise<HistoryDoc[]>;
  pullBookmarks(since: number): Promise<BookmarkDoc[]>;
  pullShortcuts(since: number): Promise<ShortcutsDoc | null>;
  pushHistory(entries: HistoryDoc[]): Promise<void>;
  pushBookmarks(entries: BookmarkDoc[]): Promise<void>;
  pushShortcuts(doc: ShortcutsDoc): Promise<void>;
}
