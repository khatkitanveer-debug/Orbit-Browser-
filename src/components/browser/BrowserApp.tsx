"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Tab, Suggestion, NEWTAB } from "@/lib/browser/types";
import {
  addHistory,
  ensureDefaultSpeedDial,
  getSetting,
  listBookmarks,
  listHistory,
  lsClear,
  setSetting,
  toggleBookmark,
  exportAllData,
  updateHistoryTitle,
} from "@/lib/browser/localStore";
import { SpeedDialItem } from "@/lib/browser/types";
import { toast } from "sonner";
import { lsDelete, addSpeedDial, removeSpeedDial, listSpeedDial } from "@/lib/browser/localStore";
import { isExternal, normalizeInput, proxyUrl, searchQueryOf, titleFor, uid } from "@/lib/browser/shared";
import { TabBar } from "./TabBar";
import { Toolbar, MenuAction } from "./Toolbar";
import { NewTabPage } from "./NewTabPage";
import { SearchPage } from "./SearchPage";
import { HistoryPage } from "./HistoryPage";
import { BookmarksPage } from "./BookmarksPage";
import { SettingsPage } from "./SettingsPage";
import { AboutPage } from "./AboutPage";
import { AiSidebar } from "./AiSidebar";
import { Zap } from "lucide-react";

function makeTab(url: string = NEWTAB): Tab {
  return {
    id: uid(),
    url,
    title: titleFor(url),
    loading: isExternal(url),
    history: [url],
    historyIndex: 0,
    nonce: 0,
  };
}

export function BrowserApp() {
  const [ready, setReady] = useState(false);
  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activeTabId, setActiveTabId] = useState("");
  const [privateMode, setPrivateMode] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [speedDial, setSpeedDial] = useState<SpeedDialItem[]>([]);
  const [bookmarksRefresh, setBookmarksRefresh] = useState(0);
  const [historyRefresh, setHistoryRefresh] = useState(0);
  const [bookmarkUrls, setBookmarkUrls] = useState<Set<string>>(new Set());

  const historyIdMap = useRef<Map<string, number>>(new Map());
  const persistTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const activeTab = tabs.find((t) => t.id === activeTabId) ?? tabs[0];

  // ---------- init ----------
  useEffect(() => {
    (async () => {
      try {
        await ensureDefaultSpeedDial();
        const dial = await listSpeedDial();
        setSpeedDial(dial);
        const bms = await listBookmarks();
        setBookmarkUrls(new Set(bms.map((b) => b.url)));
        const session = await getSetting<{ tabs: Tab[]; activeTabId: string } | null>("session", null);
        if (session && Array.isArray(session.tabs) && session.tabs.length > 0) {
          const restored = session.tabs.map((t) => ({
            ...t,
            loading: isExternal(t.url),
            nonce: 0,
          }));
          setTabs(restored);
          setActiveTabId(restored.find((t) => t.id === session.activeTabId)?.id ?? restored[0].id);
        } else {
          const t = makeTab();
          setTabs([t]);
          setActiveTabId(t.id);
        }
      } catch {
        const t = makeTab();
        setTabs([t]);
        setActiveTabId(t.id);
      }
      setReady(true);
    })();
  }, []);

  // ---------- session persist (private me nahi) ----------
  useEffect(() => {
    if (!ready || privateMode || tabs.length === 0) return;
    if (persistTimer.current) clearTimeout(persistTimer.current);
    persistTimer.current = setTimeout(() => {
      setSetting("session", { tabs, activeTabId }).catch(() => {});
    }, 600);
    return () => {
      if (persistTimer.current) clearTimeout(persistTimer.current);
    };
  }, [tabs, activeTabId, ready, privateMode]);

  const currentBookmarked = activeTab ? bookmarkUrls.has(activeTab.url) : false;

  // ---------- tab ops ----------
  const patchTab = useCallback((id: string, patch: Partial<Tab>) => {
    setTabs((prev) => prev.map((t) => (t.id === id ? { ...t, ...patch } : t)));
  }, []);

  const recordHistory = useCallback(
    (url: string, title: string) => {
      if (privateMode) return;
      addHistory(url, title).then((id) => {
        if (id !== null) historyIdMap.current.set(url, id);
        setHistoryRefresh((n) => n + 1);
      });
    },
    [privateMode]
  );

  const navigate = useCallback(
    (tabId: string, rawInput: string) => {
      const url = normalizeInput(rawInput);
      if (!url) return;
      setTabs((prev) =>
        prev.map((t) => {
          if (t.id !== tabId) return t;
          const hist = [...t.history.slice(0, t.historyIndex + 1), url];
          return {
            ...t,
            url,
            title: titleFor(url),
            loading: isExternal(url),
            history: hist,
            historyIndex: hist.length - 1,
          };
        })
      );
      if (isExternal(url)) recordHistory(url, titleFor(url));
    },
    [recordHistory]
  );

  const newTab = useCallback(() => {
    const t = makeTab();
    setTabs((prev) => [...prev, t]);
    setActiveTabId(t.id);
  }, []);

  const closeTab = useCallback(
    (id: string) => {
      setTabs((prev) => {
        const idx = prev.findIndex((t) => t.id === id);
        const next = prev.filter((t) => t.id !== id);
        if (next.length === 0) {
          const t = makeTab();
          setActiveTabId(t.id);
          return [t];
        }
        if (id === activeTabId) {
          const fall = next[Math.min(idx, next.length - 1)];
          setActiveTabId(fall.id);
        }
        return next;
      });
    },
    [activeTabId]
  );

  const goBack = useCallback(() => {
    if (!activeTab || activeTab.historyIndex <= 0) return;
    const idx = activeTab.historyIndex - 1;
    const url = activeTab.history[idx];
    patchTab(activeTab.id, { historyIndex: idx, url, title: titleFor(url), loading: isExternal(url) });
  }, [activeTab, patchTab]);

  const goForward = useCallback(() => {
    if (!activeTab || activeTab.historyIndex >= activeTab.history.length - 1) return;
    const idx = activeTab.historyIndex + 1;
    const url = activeTab.history[idx];
    patchTab(activeTab.id, { historyIndex: idx, url, title: titleFor(url), loading: isExternal(url) });
  }, [activeTab, patchTab]);

  const reload = useCallback(() => {
    if (!activeTab) return;
    patchTab(activeTab.id, { nonce: (activeTab.nonce ?? 0) + 1, loading: isExternal(activeTab.url) });
  }, [activeTab, patchTab]);

  const goHome = useCallback(() => {
    if (activeTab) navigate(activeTab.id, NEWTAB);
  }, [activeTab, navigate]);

  // ---------- postMessage from proxied pages ----------
  useEffect(() => {
    const handler = (e: MessageEvent) => {
      const d = e.data as
        | { type?: string; tabId?: string; title?: string; url?: string; newTab?: boolean }
        | undefined;
      if (!d || typeof d !== "object" || !d.type || !d.tabId) return;

      if (d.type === "tez:meta" && d.title) {
        setTabs((prev) =>
          prev.map((t) => {
            if (t.id !== d.tabId || !d.title) return t;
            const hid = historyIdMap.current.get(t.url);
            if (hid) updateHistoryTitle(hid, d.title).catch(() => {});
            return { ...t, title: d.title };
          })
        );
      }
      if (d.type === "tez:navigate" && d.url) {
        if (d.newTab) {
          const t = makeTab(d.url);
          setTabs((prev) => [...prev, t]);
          setActiveTabId(t.id);
          if (isExternal(d.url)) recordHistory(d.url, titleFor(d.url));
        } else {
          navigate(d.tabId, d.url);
        }
      }
    };
    window.addEventListener("message", handler);
    return () => window.removeEventListener("message", handler);
  }, [navigate, recordHistory]);

  // ---------- bookmarks ----------
  const handleToggleBookmark = useCallback(() => {
    if (!activeTab || !isExternal(activeTab.url)) return;
    const url = activeTab.url;
    toggleBookmark(url, activeTab.title).then((added) => {
      setBookmarkUrls((prev) => {
        const next = new Set(prev);
        if (added) next.add(url);
        else next.delete(url);
        return next;
      });
      setBookmarksRefresh((n) => n + 1);
      toast.success(added ? "Bookmark save ho gaya (local)" : "Bookmark hat gaya");
    });
  }, [activeTab]);

  // ---------- keyboard shortcuts ----------
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      if (!mod) return;
      const k = e.key.toLowerCase();
      if (k === "t" && !e.shiftKey) {
        e.preventDefault();
        newTab();
      } else if (k === "w") {
        e.preventDefault();
        if (activeTab) closeTab(activeTab.id);
      } else if (k === "h") {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, "tez://history");
      } else if (k === "d") {
        e.preventDefault();
        handleToggleBookmark();
      } else if (k === "i" && e.shiftKey) {
        e.preventDefault();
        setPrivateMode((p) => !p);
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [activeTab, newTab, closeTab, navigate, handleToggleBookmark]);

  // ---------- suggestions ----------
  const [histCache, setHistCache] = useState<Suggestion[]>([]);
  useEffect(() => {
    Promise.all([listHistory(), listBookmarks()]).then(([h, b]) => {
      const seen = new Set<string>();
      const out: Suggestion[] = [];
      for (const bm of b) {
        if (!seen.has(bm.url)) {
          seen.add(bm.url);
          out.push({ title: bm.title || bm.url, url: bm.url });
        }
      }
      for (const it of h) {
        if (!seen.has(it.url) && out.length < 200) {
          seen.add(it.url);
          out.push({ title: it.title || it.url, url: it.url });
        }
      }
      setHistCache(out);
    });
  }, [historyRefresh, bookmarksRefresh]);

  const getSuggestions = useCallback(
    (q: string): Suggestion[] => {
      const t = q.toLowerCase();
      return histCache
        .filter((s) => s.title.toLowerCase().includes(t) || s.url.toLowerCase().includes(t))
        .slice(0, 6);
    },
    [histCache]
  );

  // ---------- menu ----------
  const handleMenu = useCallback(
    (action: MenuAction) => {
      if (!activeTab) return;
      switch (action) {
        case "newtab":
          newTab();
          break;
        case "history":
          navigate(activeTab.id, "tez://history");
          break;
        case "bookmarks":
          navigate(activeTab.id, "tez://bookmarks");
          break;
        case "settings":
          navigate(activeTab.id, "tez://settings");
          break;
        case "about":
          navigate(activeTab.id, "tez://about");
          break;
        case "export":
          exportAllData().then((bundle) => {
            const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" });
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = `tez-browser-data-${new Date().toISOString().slice(0, 10)}.json`;
            a.click();
            URL.revokeObjectURL(url);
            toast.success("Data export ho gaya");
          });
          break;
        case "clearall":
          lsClear("history").then(() => {
            setHistoryRefresh((n) => n + 1);
            toast.success("History mit gayi");
          });
          break;
      }
    },
    [activeTab, newTab, navigate]
  );

  // ---------- speed dial ----------
  const handleAddDial = useCallback((label: string, url: string) => {
    addSpeedDial(label, url).then(() => listSpeedDial().then(setSpeedDial));
  }, []);

  const handleRemoveDial = useCallback((id: number) => {
    removeSpeedDial(id).then(() => listSpeedDial().then(setSpeedDial));
  }, []);

  // ---------- theme ----------
  const applyTheme = useCallback((t: "light" | "dark" | "system") => {
    if (t === "dark") {
      document.documentElement.classList.add("dark");
      document.documentElement.style.colorScheme = "dark";
    } else if (t === "light") {
      document.documentElement.classList.remove("dark");
      document.documentElement.style.colorScheme = "light";
    } else {
      const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
      document.documentElement.classList.toggle("dark", prefersDark);
      document.documentElement.style.colorScheme = prefersDark ? "dark" : "light";
    }
  }, []);

  useEffect(() => {
    getSetting<"light" | "dark" | "system">("theme", "dark").then(applyTheme);
  }, [applyTheme]);

  // ---------- render helpers ----------
  const renderInternal = (tab: Tab) => {
    if (tab.url === NEWTAB) {
      return (
        <NewTabPage
          privateMode={privateMode}
          speedDial={speedDial}
          onNavigate={(input) => navigate(tab.id, input)}
          onAddDial={handleAddDial}
          onRemoveDial={handleRemoveDial}
        />
      );
    }
    if (tab.url.startsWith("tez://search")) {
      return <SearchPage q={searchQueryOf(tab.url)} onNavigate={(url) => navigate(tab.id, url)} />;
    }
    if (tab.url.startsWith("tez://history")) {
      return <HistoryPage onNavigate={(url) => navigate(tab.id, url)} refreshKey={historyRefresh} />;
    }
    if (tab.url.startsWith("tez://bookmarks")) {
      return <BookmarksPage onNavigate={(url) => navigate(tab.id, url)} refreshKey={bookmarksRefresh} />;
    }
    if (tab.url.startsWith("tez://settings")) {
      return <SettingsPage onThemeChange={applyTheme} onNavigate={(url) => navigate(tab.id, url)} />;
    }
    if (tab.url.startsWith("tez://about")) {
      return <AboutPage />;
    }
    return null;
  };

  if (!ready) {
    return (
      <div className="h-screen flex flex-col items-center justify-center bg-background gap-4">
        <span className="flex items-center justify-center w-16 h-16 rounded-3xl bg-emerald-600 text-white shadow-lg shadow-emerald-600/20">
          <Zap className="w-8 h-8" />
        </span>
        <div className="text-lg font-bold">Tez Browser</div>
        <div className="text-xs text-muted-foreground">Local data load ho raha hai...</div>
      </div>
    );
  }

  if (!activeTab) {
    return (
      <div className="h-screen flex items-center justify-center bg-background">
        <button onClick={newTab} className="text-emerald-600 underline">
          Naya tab kholo
        </button>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-background text-foreground overflow-hidden">
      <TabBar
        tabs={tabs}
        activeTabId={activeTab.id}
        privateMode={privateMode}
        onSelect={setActiveTabId}
        onClose={closeTab}
        onNew={newTab}
      />
      <Toolbar
        tab={activeTab}
        canBack={activeTab.historyIndex > 0}
        canForward={activeTab.historyIndex < activeTab.history.length - 1}
        isBookmarked={currentBookmarked}
        privateMode={privateMode}
        aiOpen={aiOpen}
        suggestions={getSuggestions}
        onBack={goBack}
        onForward={goForward}
        onReload={reload}
        onHome={goHome}
        onNavigate={(input) => navigate(activeTab.id, input)}
        onToggleBookmark={handleToggleBookmark}
        onTogglePrivate={() => setPrivateMode((p) => !p)}
        onToggleAi={() => setAiOpen((o) => !o)}
        onOpenExternal={() => window.open(activeTab.url, "_blank", "noopener,noreferrer")}
        onMenu={handleMenu}
      />

      <div className="flex-1 flex min-h-0 relative">
        <main className="flex-1 relative min-w-0 bg-background" aria-label="Browser content">
          {tabs.map((tab) => {
            const active = tab.id === activeTab.id;
            if (tab.url.startsWith("tez://")) {
              if (!active) return null;
              return (
                <div key={tab.id} className="absolute inset-0">
                  {renderInternal(tab)}
                </div>
              );
            }
            return (
              <iframe
                key={`${tab.id}#${tab.nonce}`}
                src={proxyUrl(tab.url, tab.id)}
                title={tab.title}
                onLoad={() => patchTab(tab.id, { loading: false })}
                sandbox="allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox"
                referrerPolicy="no-referrer"
                className={`absolute inset-0 w-full h-full border-0 ${active ? "" : "hidden"}`}
              />
            );
          })}
        </main>

        <AiSidebar
          open={aiOpen}
          onClose={() => setAiOpen(false)}
          pageUrl={activeTab.url}
          pageTitle={activeTab.title}
          privateMode={privateMode}
        />
      </div>
    </div>
  );
}
