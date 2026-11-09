"use client";

import { useEffect, useMemo, useState } from "react";
import { History as HistoryIcon, Trash2, Search, Database } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { HistoryItem } from "@/lib/browser/types";
import { listHistory, lsDelete, lsClear } from "@/lib/browser/localStore";
import { fmtTime, dayLabel } from "@/lib/browser/shared";

interface HistoryPageProps {
  onNavigate: (url: string) => void;
  refreshKey: number;
}

export function HistoryPage({ onNavigate, refreshKey }: HistoryPageProps) {
  const [items, setItems] = useState<HistoryItem[]>([]);
  const [filter, setFilter] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listHistory().then((r) => {
      setItems(r);
      setLoading(false);
    });
  }, [refreshKey]);

  const filtered = useMemo(() => {
    const f = filter.trim().toLowerCase();
    if (!f) return items;
    return items.filter((i) => i.title.toLowerCase().includes(f) || i.url.toLowerCase().includes(f));
  }, [items, filter]);

  const grouped = useMemo(() => {
    const map = new Map<string, HistoryItem[]>();
    for (const it of filtered) {
      const k = dayLabel(it.ts);
      if (!map.has(k)) map.set(k, []);
      map.get(k)!.push(it);
    }
    return Array.from(map.entries());
  }, [filtered]);

  const removeItem = async (id: number) => {
    await lsDelete("history", id);
    setItems((prev) => prev.filter((i) => i.id !== id));
  };

  const clearAll = async () => {
    await lsClear("history");
    setItems([]);
  };

  return (
    <div className="h-full overflow-y-auto tez-scroll bg-background">
      <div className="max-w-3xl mx-auto px-6 py-8">
        <div className="flex items-center gap-2 mb-4">
          <HistoryIcon className="w-5 h-5 text-emerald-600" />
          <h1 className="text-lg font-semibold">History</h1>
          <span className="ml-auto flex items-center gap-1.5 text-[11px] text-muted-foreground">
            <Database className="w-3 h-3 text-emerald-600" />
            Sirf aapke device me (IndexedDB)
          </span>
        </div>

        <div className="flex gap-2 mb-6">
          <div className="flex items-center gap-2 flex-1 h-9 px-3 rounded-lg bg-muted/70 border border-border">
            <Search className="w-4 h-4 text-muted-foreground" />
            <input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="History me search karo..."
              className="flex-1 bg-transparent outline-none text-sm"
              aria-label="History search"
            />
          </div>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="destructive" size="sm" disabled={items.length === 0}>
                <Trash2 className="w-3.5 h-3.5 mr-1" /> Sab mitao
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Poora history mitana hai?</AlertDialogTitle>
                <AlertDialogDescription>
                  {items.length} entries permanently delete ho jayengi. Ye action undo nahi ho sakta.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancel</AlertDialogCancel>
                <AlertDialogAction onClick={clearAll}>Haan, mita do</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>

        {loading ? (
          <div className="text-sm text-muted-foreground">Load ho raha hai...</div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16 text-muted-foreground">
            <HistoryIcon className="w-10 h-10 mx-auto mb-3 opacity-30" />
            <div className="text-sm">{filter ? "Kuch nahi mila" : "History khali hai — browsing shuru karo!"}</div>
          </div>
        ) : (
          grouped.map(([day, list]) => (
            <div key={day} className="mb-6">
              <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">{day}</div>
              <div className="rounded-xl border border-border overflow-hidden divide-y divide-border/60">
                {list.map((item) => (
                  <div key={item.id ?? item.url + item.ts} className="flex items-center gap-3 px-4 py-2.5 hover:bg-muted/50 group">
                    <span className="text-xs text-muted-foreground w-12 shrink-0">{fmtTime(item.ts)}</span>
                    <button onClick={() => onNavigate(item.url)} className="flex-1 min-w-0 text-left">
                      <div className="text-sm truncate">{item.title || item.url}</div>
                      <div className="text-xs text-muted-foreground truncate">{item.url}</div>
                    </button>
                    <button
                      aria-label="Entry delete karo"
                      onClick={() => typeof item.id === "number" && removeItem(item.id)}
                      className="opacity-0 group-hover:opacity-100 p-1.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-opacity"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
