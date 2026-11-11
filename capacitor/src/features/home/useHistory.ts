import { useEffect, useState, useSyncExternalStore } from "react";
import {
  getAllEntries,
  getHistoryVersion,
  subscribeHistory,
  type HistoryEntry,
} from "../../core/historyDB";

/**
 * History → React seam. Re-fetches only when the store signals a change
 * (push, not polling) and keeps the last good snapshot while revalidating,
 * so opening/closing tabs never flashes the feed.
 */
export function useHistoryEntries(): HistoryEntry[] {
  const version = useSyncExternalStore(subscribeHistory, getHistoryVersion);
  const [entries, setEntries] = useState<HistoryEntry[]>([]);

  useEffect(() => {
    let live = true;
    void getAllEntries().then((e) => {
      if (live) setEntries(e);
    });
    return () => {
      live = false;
    };
  }, [version]);

  return entries;
}
