import { useSyncExternalStore } from "react";
import { Capacitor } from "@capacitor/core";
import { OrbitSync } from "./orbitSync";
import { isFirebaseConfigured } from "./sync/firebaseConfig";
import { restoreSession, signInWithGoogleToken, signOutFirebase, loadFirebase } from "./sync/syncClient";
import { firestoreBackend } from "./sync/firestoreBackend";
import { reconcileHistory, reconcileBookmarks, reconcileShortcuts } from "./sync/syncEngine";
import {
  getAllEntries,
  applyRemoteHistory,
  subscribeHistory,
  setHistorySuppressed,
} from "./historyDB";
import { getBookmarkTombstones, applyRemoteBookmarks, subscribeBookmarks } from "./bookmarksDB";
import {
  loadCustomShortcuts,
  replaceCustomShortcuts,
  getShortcutsUpdatedAt,
} from "../features/home/shortcuts";

/**
 * Module 4 sync — React-facing store + orchestration.
 *
 *   native Google idToken (OrbitSync) → Firebase Auth session → Firestore
 *   delta rounds driven by: sign-in, manual "Sync now", local data changes
 *   (debounced — no polling), and app resume.
 */

export type SyncStatus = "disabled" | "signed-out" | "syncing" | "ok" | "error";

export interface SyncState {
  configured: boolean;
  status: SyncStatus;
  email: string | null;
  displayName: string | null;
  lastSyncAt: number;
  error: string | null;
}

let state: SyncState = {
  configured: isFirebaseConfigured(),
  status: isFirebaseConfigured() ? "signed-out" : "disabled",
  email: null,
  displayName: null,
  lastSyncAt: 0,
  error: null,
};

const listeners = new Set<() => void>();

function setState(patch: Partial<SyncState>): void {
  state = { ...state, ...patch };
  for (const l of listeners) l();
}

function subscribeSync(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSyncState(): SyncState {
  return state;
}

/* --------------------------------- cursors -------------------------------- */

const cursorKey = (uid: string) => `orbit.sync.${uid}.lastSyncAt`;

function readCursor(uid: string): number {
  try {
    return Number(localStorage.getItem(cursorKey(uid))) || 0;
  } catch {
    return 0;
  }
}

function writeCursor(uid: string, value: number): void {
  try {
    localStorage.setItem(cursorKey(uid), String(value));
  } catch {
    /* storage unavailable */
  }
}

/* -------------------------------- the round ------------------------------- */

let syncInFlight: Promise<void> | null = null;
let debounceTimer: ReturnType<typeof setTimeout> | null = null;

async function runSyncRound(): Promise<void> {
  if (!state.configured) return;
  const bundle = await loadFirebase();
  const user = bundle.auth.currentUser;
  if (!user) {
    setState({ status: "signed-out", email: null, displayName: null });
    return;
  }

  setState({ status: "syncing", error: null });
  try {
    const uid = user.uid;
    const cursor = readCursor(uid);

    const [localHistory, localBookmarks, remoteHistory, remoteBookmarks, remoteShortcuts] = await Promise.all([
      getAllEntries(),
      getBookmarkTombstones(),
      firestoreBackend(user, cursor).pullHistory(cursor),
      firestoreBackend(user, cursor).pullBookmarks(cursor),
      firestoreBackend(user, cursor).pullShortcuts(cursor),
    ]);

    const backend = firestoreBackend(user, cursor);
    const historyRound = reconcileHistory(localHistory, remoteHistory);
    const bookmarkRound = reconcileBookmarks(Array.from(localBookmarks.values()), remoteBookmarks);
    const shortcutsRound = reconcileShortcuts(loadCustomShortcuts(), getShortcutsUpdatedAt(), remoteShortcuts);

    // 1. Apply remote winners locally.
    await applyRemoteHistory(historyRound.applyLocal);
    await applyRemoteBookmarks(bookmarkRound.applyLocal);
    if (shortcutsRound.mode === "apply-remote") {
      replaceCustomShortcuts(shortcutsRound.doc.items);
    }

    // 2. Push local winners (tombstones included).
    await backend.pushHistory(historyRound.toPush);
    await backend.pushBookmarks(bookmarkRound.toPush);
    if (shortcutsRound.mode === "push-local") {
      await backend.pushShortcuts(shortcutsRound.doc);
    }

    // 3. Advance the cursor past both clocks — a skewed device can never
    //    skip another device's writes.
    const newestRemote = Math.max(
      0,
      ...remoteHistory.map((d) => d.updatedAt ?? 0),
      ...remoteBookmarks.map((d) => d.updatedAt ?? 0),
      remoteShortcuts?.updatedAt ?? 0,
    );
    const next = Math.max(Date.now(), newestRemote);
    writeCursor(uid, next);

    setState({ status: "ok", lastSyncAt: next, email: user.email, displayName: user.displayName });
  } catch (e) {
    setState({ status: "error", error: e instanceof Error ? e.message : "Sync failed" });
  }
}

export function requestSync(): void {
  if (syncInFlight) return;
  syncInFlight = runSyncRound().finally(() => {
    syncInFlight = null;
  });
}

/** Local data changed → debounced delta round (4s). No polling loops. */
function scheduleDebouncedSync(): void {
  if (!state.configured || !state.email) return;
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    requestSync();
  }, 4000);
}

subscribeHistory(() => scheduleDebouncedSync());
subscribeBookmarks(() => scheduleDebouncedSync());

/* --------------------------------- actions -------------------------------- */

export async function signInWithGoogle(): Promise<{ cancelled?: boolean }> {
  if (!state.configured) throw new Error("Firebase is not configured");
  const result = await OrbitSync.signIn();
  if (result.cancelled || !result.idToken) return { cancelled: true };
  const user = await signInWithGoogleToken(result.idToken);
  setState({ email: user.email, displayName: user.displayName, status: "syncing" });
  void requestSync();
  return {};
}

export async function signOutEverywhere(): Promise<void> {
  try {
    if (state.configured) {
      await signOutFirebase();
      if (Capacitor.isNativePlatform()) await OrbitSync.signOut();
    }
  } finally {
    setState({ status: "signed-out", email: null, displayName: null, error: null });
  }
}

/** App start — restore an existing Firebase session and pull deltas. */
export async function restoreSyncSession(): Promise<void> {
  if (!state.configured) return;
  const user = await restoreSession();
  if (user) {
    setState({ email: user.email, displayName: user.displayName });
    void requestSync();
  }
}

/* ------------------------------- React hook ------------------------------- */

export function useSyncState(): SyncState {
  return useSyncExternalStore(subscribeSync, getSyncState);
}

/** Incognito must never write history — Module 4 calls this on mode switch. */
export function setIncognitoActive(active: boolean): void {
  setHistorySuppressed(active);
}
