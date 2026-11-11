import type { Firestore } from "firebase/firestore";
import type { User } from "firebase/auth";
import { loadFirebase } from "./syncClient";
import type { SyncBackend } from "./syncEngine";
import type { HistoryDoc, BookmarkDoc, ShortcutsDoc } from "./syncEngine";

/**
 * Firestore backend for the Module 4 sync engine.
 *
 * Layout (per user):
 *   users/{uid}/history/{encodeURIComponent(url)}   — one doc per URL
 *   users/{uid}/bookmarks/{encodeURIComponent(url)} — one doc per URL
 *   users/{uid}/lists/shortcuts                     — whole-list doc
 *
 * URLs contain "/" (illegal in Firestore doc ids) → encodeURIComponent.
 * Delta reads use single-field `updatedAt > cursor` queries — no composite
 * indexes needed, ever.
 *
 * firebase/firestore is imported LAZILY here too (syncClient lazily loads
 * firebase/app + auth). A static import would drag the whole SDK into the
 * startup bundle — the home screen must stay instant.
 */

type FsMod = typeof import("firebase/firestore");

let fsModPromise: Promise<FsMod> | null = null;

function loadFs(): Promise<FsMod> {
  fsModPromise ??= import("firebase/firestore");
  return fsModPromise;
}

async function dbHandle(): Promise<{ fs: FsMod; db: Firestore }> {
  const [fs, bundle] = await Promise.all([loadFs(), loadFirebase()]);
  return { fs, db: bundle.db };
}

export function firestoreBackend(user: User, _cursor: number): SyncBackend {
  const uid = user.uid;

  async function pull<T>(
    sub: string,
    since: number,
    map: (id: string, data: Record<string, unknown>) => T,
  ): Promise<T[]> {
    const { fs, db } = await dbHandle();
    const snap = await fs.getDocs(fs.query(fs.collection(db, "users", uid, sub), fs.where("updatedAt", ">", since)));
    const out: T[] = [];
    snap.forEach((d) => out.push(map(d.id, d.data() as Record<string, unknown>)));
    return out;
  }

  return {
    async pullHistory(since: number) {
      return pull<HistoryDoc>("history", since, (id, data) => ({
        ...(data as unknown as HistoryDoc),
        url: decodeURIComponent(id),
      }));
    },
    async pullBookmarks(since: number) {
      return pull<BookmarkDoc>("bookmarks", since, (id, data) => {
        const d = data as unknown as BookmarkDoc;
        return { ...d, id: decodeURIComponent(id), url: d.url ?? decodeURIComponent(id) };
      });
    },
    async pullShortcuts(since: number): Promise<ShortcutsDoc | null> {
      const { fs, db } = await dbHandle();
      const snap = await fs.getDoc(fs.doc(db, "users", uid, "lists", "shortcuts"));
      if (!snap.exists()) return null;
      const d = snap.data() as ShortcutsDoc;
      if ((d.updatedAt ?? 0) <= since) return null; // delta semantics for the single-doc list
      return d;
    },
    async pushHistory(entries: HistoryDoc[]) {
      const { fs, db } = await dbHandle();
      for (const batch of inChunks(entries)) {
        const wb = fs.writeBatch(db);
        for (const e of batch) {
          wb.set(fs.doc(db, "users", uid, "history", encodeURIComponent(e.url)), {
            ...e,
            deleted: e.deleted ?? false,
          });
        }
        await wb.commit();
      }
    },
    async pushBookmarks(entries: BookmarkDoc[]) {
      const { fs, db } = await dbHandle();
      for (const batch of inChunks(entries)) {
        const wb = fs.writeBatch(db);
        for (const b of batch) {
          wb.set(fs.doc(db, "users", uid, "bookmarks", encodeURIComponent(b.id)), {
            ...b,
            deleted: b.deleted ?? false,
          });
        }
        await wb.commit();
      }
    },
    async pushShortcuts(d: ShortcutsDoc) {
      const { fs, db } = await dbHandle();
      await fs.setDoc(fs.doc(db, "users", uid, "lists", "shortcuts"), d as unknown as Record<string, unknown>);
    },
  };
}

/** Firestore accepts max 500 writes per batch. */
function inChunks<T>(items: T[], size = 450): T[][] {
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}
