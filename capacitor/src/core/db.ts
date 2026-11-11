/**
 * Single owner of the Orbit IndexedDB database (Module 2 + 4).
 *
 * CRITICAL: every store lives in THIS schema version, opened by THIS
 * module only. Two modules opening the same DB with different versions
 * deadlock (a version-2 upgrade blocks while the version-1 connection
 * stays open) — that bug is exactly what this file exists to prevent.
 * Add new stores here, bump DB_VERSION, extend onupgradeneeded.
 */

export const DB_NAME = "orbit";
export const DB_VERSION = 2; // v1: history — v2: + bookmarks

export function requestAsPromise<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let dbPromise: Promise<IDBDatabase> | null = null;

export function openOrbitDB(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains("history")) {
          const history = db.createObjectStore("history", { keyPath: "url" });
          history.createIndex("lastVisit", "lastVisit");
        }
        if (!db.objectStoreNames.contains("bookmarks")) {
          db.createObjectStore("bookmarks", { keyPath: "id" });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
      req.onblocked = () => reject(new Error("orbit-db: upgrade blocked"));
    });
    dbPromise.catch(() => {
      dbPromise = null; // allow a retry on the next call
    });
  }
  return dbPromise;
}
