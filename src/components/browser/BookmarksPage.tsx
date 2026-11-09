"use client";

import { useEffect, useState } from "react";
import { Bookmark as BookmarkIcon, Trash2 } from "lucide-react";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Bookmark } from "@/lib/browser/types";
import { listBookmarks, lsDelete, lsClear } from "@/lib/browser/localStore";
import { fmtTime } from "@/lib/browser/shared";

interface BookmarksPageProps {
  onNavigate: (url: string) => void;
  refreshKey: number;
}

export function BookmarksPage({ onNavigate, refreshKey }: BookmarksPageProps) {
  const [items, setItems] = useState<Bookmark[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listBookmarks().then((r) => {
      setItems(r);
      setLoading(false);
    });
  }, [refreshKey]);

  const removeItem = async (url: string) => {
    await lsDelete("bookmarks", url);
    setItems((prev) => prev.filter((i) => i.url !== url));
  };

  const clearAll = async () => {
    await lsClear("bookmarks");
    setItems([]);
  };

  return (
    <div className="h-full overflow-y-auto tez-scroll bg-background">
      <div className="max-w-3xl mx-auto px-6 py-8">
        <div className="flex items-center gap-2 mb-6">
          <BookmarkIcon className="w-5 h-5 text-emerald-600" />
          <h1 className="text-lg font-semibold">Bookmarks</h1>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="destructive" size="sm" className="ml-auto" disabled={items.length === 0}>
                <Trash2 className="w-3.5 h-3.5 mr-1" /> Sab mitao
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Saare bookmarks mitana hai?</AlertDialogTitle>
                <AlertDialogDescription>{items.length} bookmarks permanently delete ho jayenge.</AlertDialogDescription>
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
        ) : items.length === 0 ? (
          <div className="text-center py-16 text-muted-foreground">
            <BookmarkIcon className="w-10 h-10 mx-auto mb-3 opacity-30" />
            <div className="text-sm">Abhi koi bookmark nahi hai — omnibox ke star button se add karo</div>
          </div>
        ) : (
          <div className="rounded-xl border border-border overflow-hidden divide-y divide-border/60">
            {items.map((item) => (
              <div key={item.url} className="flex items-center gap-3 px-4 py-3 hover:bg-muted/50 group">
                <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-emerald-600/10 text-emerald-600 shrink-0">
                  <BookmarkIcon className="w-4 h-4" />
                </span>
                <button onClick={() => onNavigate(item.url)} className="flex-1 min-w-0 text-left">
                  <div className="text-sm truncate">{item.title || item.url}</div>
                  <div className="text-xs text-muted-foreground truncate">
                    {item.url} &#183; {fmtTime(item.ts)}
                  </div>
                </button>
                <button
                  aria-label="Bookmark delete karo"
                  onClick={() => removeItem(item.url)}
                  className="opacity-0 group-hover:opacity-100 p-1.5 rounded hover:bg-destructive/10 text-muted-foreground hover:text-destructive transition-opacity"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
