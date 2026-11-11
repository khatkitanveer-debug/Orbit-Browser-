import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { AppBar } from "../../components/chrome/AppBar";
import { HomeScreen } from "../home/HomeScreen";
import { OrbitRender, type PageState } from "../../core/orbit";
import { OrbitLens } from "../../core/lens";
import { OrbitPrivacy, type VpnStartResult } from "../../core/privacy";
import { normalizeInput } from "../../core/url";
import { recordVisit, updateTitle, setHistorySuppressed } from "../../core/historyDB";
import { addBookmark, listBookmarks, subscribeBookmarks, getBookmarksVersion, type Bookmark } from "../../core/bookmarksDB";
import {
  useSyncState,
  signInWithGoogle,
  signOutEverywhere,
  requestSync,
  restoreSyncSession,
} from "../../core/syncStore";
import { isOverlayOpen } from "../../core/overlay";

const isNative = Capacitor.isNativePlatform();

/**
 * Owns the browsing session and the app bar ↔ native slot sync.
 * Web preview: native calls are skipped and a note shows where the
 * content WebView renders on device — layout is identical for QA.
 * Module 4: incognito mode (ephemeral render path + suppressed history),
 * bookmarks, sync actions and the VPN toggle.
 */
export function BrowserScreen() {
  const [session, setSession] = useState<PageState | null>(null);
  const [focusSignal, setFocusSignal] = useState(0);
  const [lensBusy, setLensBusy] = useState(false);
  const [incognito, setIncognito] = useState(false);
  const [bookmarks, setBookmarks] = useState<Bookmark[]>([]);
  const [vpnLabel, setVpnLabel] = useState("VPN (beta)");
  const [vpnBusy, setVpnBusy] = useState(false);
  const lastInsetRef = useRef(0);
  // Module 2: dedupe key for history recording (last finished page).
  const lastVisitRef = useRef<{ url: string; title: string }>({ url: "", title: "" });

  const sync = useSyncState();
  const hasSession = !!session;

  const refreshBookmarks = useCallback(() => {
    void listBookmarks().then(setBookmarks);
  }, []);

  // Bookmarks row updates live with the store (push-based, like the feed).
  const bookmarksVersion = useSyncExternalStore(subscribeBookmarks, getBookmarksVersion);

  // Incognito must never touch history (Module 2 seam consumed here).
  useEffect(() => {
    setHistorySuppressed(incognito);
  }, [incognito]);

  useEffect(() => {
    refreshBookmarks();
  }, [refreshBookmarks, bookmarksVersion]);

  // Module 4: restore an existing Firebase session once at startup.
  useEffect(() => {
    void restoreSyncSession();
  }, []);

  const currentUrl = session?.url ?? "";
  const bookmarked = useMemo(
    () => !!currentUrl && bookmarks.some((b) => b.url === currentUrl),
    [bookmarks, currentUrl],
  );

  // App bar bottom → native slot inset (physical px).
  const handleInset = useCallback(
    (bottomCssPx: number) => {
      lastInsetRef.current = bottomCssPx;
      if (isNative && hasSession) {
        void OrbitRender.setSlotInset({
          topPx: Math.round(bottomCssPx * (window.devicePixelRatio || 1)),
        });
      }
    },
    [hasSession],
  );

  // Native page events → session state + Module 2 history recording.
  // doUpdateVisitedHistory fires for SPA/pushState navs too, so a settled
  // event with a NEW url is a real visit; same url + new title is just a
  // late <title> settle (no count bump).
  useEffect(() => {
    if (!isNative) return;
    let active = true;
    const sub = OrbitRender.addListener("page", (s) => {
      if (!active) return;
      setSession(s);
      if (s.crashed || incognito) return;
      const visitable = /^https?:\/\//i.test(s.url);
      if (visitable && !s.loading && s.url !== lastVisitRef.current.url) {
        lastVisitRef.current = { url: s.url, title: s.title };
        void recordVisit(s.url, s.title);
      } else if (visitable && s.url === lastVisitRef.current.url && s.title && s.title !== lastVisitRef.current.title) {
        lastVisitRef.current.title = s.title;
        void updateTitle(s.url, s.title);
      }
    });
    return () => {
      active = false;
      void sub.then((h) => h.remove());
    };
  }, [incognito]);

  const endSession = useCallback(async () => {
    if (isNative) await OrbitRender.close();
    lastVisitRef.current = { url: "", title: "" };
    setSession(null);
  }, []);

  // Hardware back: content back → close content → exit. Chrome behavior.
  useEffect(() => {
    if (!isNative) return;
    const sub = CapApp.addListener("backButton", async () => {
      // Sheets/dialogs close themselves via their own backButton listener;
      // the browser must not exit or navigate behind them.
      if (isOverlayOpen()) return;
      if (!hasSession) {
        CapApp.exitApp();
        return;
      }
      const { consumed } = await OrbitRender.goBack();
      if (!consumed) void endSession();
    });
    return () => {
      void sub.then((h) => h.remove());
    };
  }, [hasSession, endSession]);

  const navigate = useCallback(
    async (raw: string) => {
      const url = normalizeInput(raw);
      if (!url) return;
      setSession({
        url,
        title: url,
        progress: 5,
        loading: true,
        canGoBack: false,
        canGoForward: false,
      });
      if (isNative) {
        await OrbitRender.open({
          url,
          topPx: Math.round(lastInsetRef.current * (window.devicePixelRatio || 1)),
          ephemeral: incognito,
        });
      } else {
        // Web preview has no page events — record the visit here so the
        // home feed still reflects QA traffic. On device the real (final)
        // URL+title arrives via the page listener instead.
        if (!incognito) void recordVisit(url, url);
      }
    },
    [incognito],
  );

  const onLens = useCallback(async () => {
    if (lensBusy) return;
    setLensBusy(true);
    try {
      const result = await OrbitLens.scan();
      if (result.url) await navigate(result.url);
    } catch {
      /* no camera app / cancelled — stay where we are */
    } finally {
      setLensBusy(false);
    }
  }, [lensBusy, navigate]);

  /* ------------------------------ Module 4 ------------------------------ */

  const onBookmark = useCallback(() => {
    if (!session) return;
    void addBookmark(session.url, session.title || session.url).then(refreshBookmarks);
  }, [session, refreshBookmarks]);

  const onIncognito = useCallback(() => {
    // Mode switch always lands on the (incognito) home — the native host
    // is torn down first so cache/cookies never leak across modes.
    void endSession().then(() => setIncognito((v) => !v));
  }, [endSession]);

  const onSync = useCallback(() => {
    if (sync.email) {
      requestSync();
    } else {
      void signInWithGoogle().catch(() => {
        /* surfaced through sync state */
      });
    }
  }, [sync.email]);

  const onVpn = useCallback(() => {
    if (vpnBusy) return;
    setVpnBusy(true);
    void OrbitPrivacy.vpnStart()
      .then((result: VpnStartResult) => {
        if (result.started) {
          setVpnLabel("VPN active");
        } else if (result.reason === "consent-denied") {
          setVpnLabel("VPN (beta)");
        } else {
          // backend-not-configured — scaffolding is in place, routing is not.
          setVpnLabel("VPN — backend pending");
        }
      })
      .catch(() => setVpnLabel("VPN (beta)"))
      .finally(() => setVpnBusy(false));
  }, [vpnBusy]);

  const syncItem = useMemo(() => {
    if (!sync.configured) return { label: "Sync — setup pending", enabled: false };
    if (sync.status === "syncing") return { label: "Syncing…", enabled: false };
    if (sync.email) return { label: "Sync now", enabled: true };
    return { label: "Sign in & sync", enabled: true };
  }, [sync.configured, sync.status, sync.email]);

  const vpnMenuItem = useMemo(
    () => ({ label: vpnLabel, enabled: vpnLabel !== "VPN active" && !vpnBusy }),
    [vpnLabel, vpnBusy],
  );

  const onBack = useCallback(async () => {
    if (isNative && hasSession) {
      const { consumed } = await OrbitRender.goBack();
      if (!consumed) void endSession();
    }
  }, [hasSession, endSession]);

  const onForward = useCallback(() => {
    if (isNative && hasSession) void OrbitRender.goForward();
  }, [hasSession]);

  const onReload = useCallback(() => {
    if (isNative && hasSession) void OrbitRender.reload();
  }, [hasSession]);

  return (
    <div className="shell">
      <AppBar
        session={session}
        onNavigate={(input) => void navigate(input)}
        onReload={onReload}
        onBack={() => void onBack()}
        onForward={onForward}
        onHome={() => void endSession()}
        onLens={() => void onLens()}
        lensBusy={lensBusy}
        incognito={incognito}
        canBookmark={hasSession && !incognito}
        bookmarked={bookmarked}
        syncItem={syncItem}
        vpnItem={vpnMenuItem}
        onBookmark={onBookmark}
        onIncognito={onIncognito}
        onSync={onSync}
        onVpn={onVpn}
        onInsetChange={handleInset}
        focusSignal={focusSignal}
      />
      {hasSession ? (
        <div className="browser-body">
          {!isNative && (
            <div className="web-note">
              {session.loading ? "Loading…" : "On device, the site renders in the native WebView slot."}
            </div>
          )}
        </div>
      ) : (
        <HomeScreen
          onSearchTap={() => setFocusSignal((n) => n + 1)}
          onOpen={(url) => void navigate(url)}
          onLens={() => void onLens()}
          lensBusy={lensBusy}
          incognito={incognito}
          bookmarks={bookmarks}
          onExitIncognito={onIncognito}
          sync={sync}
          onSync={onSync}
          onSignOut={() => void signOutEverywhere()}
        />
      )}
    </div>
  );
}
