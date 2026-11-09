// Tez Browser — OPTIONAL cloud sync sirf AAPKE apne Firebase project me.
// Jab tak aap apna Firebase config nahi dete, ek bhi byte cloud par nahi jata.
// Firebase SDK yahan sirf tab load hota hai jab sync chalta hai (dynamic import).

import { Bookmark, FirebaseCfg, HistoryItem, SpeedDialItem } from "./types";
import { ExportBundle } from "./localStore";

export interface SyncPayload {
  bookmarks: Bookmark[];
  history: HistoryItem[];
  speeddial: SpeedDialItem[];
}

async function fbApp(cfg: FirebaseCfg) {
  const { initializeApp, getApps } = await import("firebase/app");
  return getApps().length ? getApps()[0] : initializeApp(cfg);
}

export function validateCfg(cfg: Partial<FirebaseCfg>): string | null {
  if (!cfg.apiKey || !cfg.projectId || !cfg.appId) {
    return "apiKey, projectId aur appId zaroori hain";
  }
  return null;
}

export async function fbSync(cfg: FirebaseCfg, data: SyncPayload): Promise<string> {
  const app = await fbApp(cfg);
  const { getFirestore, doc, setDoc, serverTimestamp } = await import("firebase/firestore");
  const payload = {
    ...data,
    history: data.history.slice(0, 300),
    updatedAt: serverTimestamp(),
    device: "tez-browser",
  };
  await setDoc(doc(getFirestore(app), "tezBrowser", "sync"), payload);
  return "Sync complete — data aapke Firebase project me save ho gaya";
}

export async function fbRestore(cfg: FirebaseCfg): Promise<Partial<SyncPayload> | null> {
  const app = await fbApp(cfg);
  const { getFirestore, doc, getDoc } = await import("firebase/firestore");
  const snap = await getDoc(doc(getFirestore(app), "tezBrowser", "sync"));
  if (!snap.exists()) return null;
  return snap.data() as Partial<SyncPayload>;
}

export function bundleToPayload(bundle: ExportBundle): SyncPayload {
  return {
    bookmarks: bundle.bookmarks,
    history: bundle.history,
    speeddial: bundle.speeddial,
  };
}
