// Tez Browser — 100% local storage (IndexedDB).
// History / bookmarks / settings / speed-dial / AI chat:
// SAB kuch aapke device par hi rehta hai, kisi company ke server par nahi jata.

import { Bookmark, ChatMsg, HistoryItem, SpeedDialItem } from "./types";

const DB_NAME = "tez-browser";
const DB_VERSION = 1;

export type StoreName = "history" | "bookmarks" | "speeddial" | "settings" | "aichat";

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (typeof indexedDB === "undefined") {
    return Promise.reject(new Error("IndexedDB unavailable"));
  }
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("history")) {
        db.createObjectStore("history", { keyPath: "id", autoIncrement: true });
      }
      if (!db.objectStoreNames.contains("bookmarks")) {
        db.createObjectStore("bookmarks", { keyPath: "url" });
      }
      if (!db.objectStoreNames.contains("speeddial")) {
        db.createObjectStore("speeddial", { keyPath: "id", autoIncrement: true });
      }
      if (!db.objectStoreNames.contains("settings")) {
        db.createObjectStore("settings");
      }
      if (!db.objectStoreNames.contains("aichat")) {
        db.createObjectStore("aichat", { keyPath: "id" });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function tx<T>(store: StoreName, mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(store, mode);
        const req = fn(t.objectStore(store));
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      })
  );
}

export function lsGetAll<T>(store: StoreName): Promise<T[]> {
  return tx<unknown[]>(store, "readonly", (s) => s.getAll()).then((r) => (r as T[]) ?? []);
}

export function lsGet<T>(store: StoreName, key: IDBValidKey): Promise<T | undefined> {
  return tx<unknown>(store, "readonly", (s) => s.get(key)).then((r) => r as T | undefined);
}

export function lsPut(store: StoreName, value: unknown, key?: IDBValidKey): Promise<IDBValidKey> {
  return tx<IDBValidKey>(store, "readwrite", (s) => (key === undefined ? s.put(value) : s.put(value, key)));
}

export function lsDelete(store: StoreName, key: IDBValidKey): Promise<undefined> {
  return tx<undefined>(store, "readwrite", (s) => s.delete(key));
}

export function lsClear(store: StoreName): Promise<undefined> {
  return tx<undefined>(store, "readwrite", (s) => s.clear());
}

// ---------- settings ----------

export async function getSetting<T>(key: string, def: T): Promise<T> {
  try {
    const v = await lsGet<T>("settings", key);
    return v === undefined ? def : v;
  } catch {
    return def;
  }
}

export async function setSetting(key: string, value: unknown): Promise<void> {
  await lsPut("settings", value, key);
}

// ---------- history ----------

export async function addHistory(url: string, title: string): Promise<number | null> {
  try {
    const item: Omit<HistoryItem, "id"> = { url, title, ts: Date.now() };
    const key = await lsPut("history", item);
    return typeof key === "number" ? key : null;
  } catch {
    return null;
  }
}

export async function updateHistoryTitle(id: number, title: string): Promise<void> {
  try {
    const item = await lsGet<HistoryItem>("history", id);
    if (item && item.title !== title) {
      item.title = title;
      await lsPut("history", item);
    }
  } catch {
    /* ignore */
  }
}

export function listHistory(): Promise<HistoryItem[]> {
  return lsGetAll<HistoryItem>("history").then((r) => r.sort((a, b) => b.ts - a.ts));
}

// ---------- bookmarks ----------

export function listBookmarks(): Promise<Bookmark[]> {
  return lsGetAll<Bookmark>("bookmarks").then((r) => r.sort((a, b) => b.ts - a.ts));
}

export async function isBookmarked(url: string): Promise<boolean> {
  try {
    return (await lsGet("bookmarks", url)) !== undefined;
  } catch {
    return false;
  }
}

export async function toggleBookmark(url: string, title: string): Promise<boolean> {
  const exists = await isBookmarked(url);
  if (exists) {
    await lsDelete("bookmarks", url);
    return false;
  }
  await lsPut("bookmarks", { url, title, ts: Date.now() });
  return true;
}

// ---------- speed dial ----------

export function listSpeedDial(): Promise<SpeedDialItem[]> {
  return lsGetAll<SpeedDialItem>("speeddial");
}

export async function addSpeedDial(label: string, url: string): Promise<void> {
  await lsPut("speeddial", { label, url });
}

export async function removeSpeedDial(id: number): Promise<void> {
  await lsDelete("speeddial", id);
}

export async function ensureDefaultSpeedDial(): Promise<void> {
  const items = await listSpeedDial();
  if (items.length > 0) return;
  const defaults: Array<[string, string]> = [
    ["Wikipedia", "https://en.wikipedia.org"],
    ["GitHub", "https://github.com"],
    ["MDN Docs", "https://developer.mozilla.org"],
    ["Hacker News", "https://news.ycombinator.com"],
    ["Wayback", "https://web.archive.org"],
    ["Example", "https://example.com"],
  ];
  for (const [label, url] of defaults) {
    await addSpeedDial(label, url);
  }
}

// ---------- AI chat (local-only) ----------

export async function saveChatMessages(messages: ChatMsg[]): Promise<void> {
  await lsPut("aichat", { id: "messages", messages: messages.slice(-80) });
}

export async function loadChatMessages(): Promise<ChatMsg[]> {
  const rec = await lsGet<{ id: string; messages: ChatMsg[] }>("aichat", "messages");
  return rec?.messages ?? [];
}

// ---------- export / clear ----------

export interface ExportBundle {
  exportedAt: string;
  app: string;
  history: HistoryItem[];
  bookmarks: Bookmark[];
  speeddial: SpeedDialItem[];
  aiChat: ChatMsg[];
}

export async function exportAllData(): Promise<ExportBundle> {
  const [history, bookmarks, speeddial, aiChat] = await Promise.all([
    listHistory(),
    listBookmarks(),
    listSpeedDial(),
    loadChatMessages(),
  ]);
  return {
    app: "Tez Browser",
    exportedAt: new Date().toISOString(),
    history,
    bookmarks,
    speeddial,
    aiChat,
  };
}

export async function clearAllData(): Promise<void> {
  await Promise.all([
    lsClear("history"),
    lsClear("bookmarks"),
    lsClear("speeddial"),
    lsClear("aichat"),
    lsDelete("settings", "session"),
    lsDelete("settings", "firebase"),
  ]);
}
