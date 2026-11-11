import { useCallback, useEffect, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { AppBar } from "../../components/chrome/AppBar";
import { HomeScreen } from "../home/HomeScreen";
import { OrbitRender, type PageState } from "../../core/orbit";
import { OrbitLens } from "../../core/lens";
import { normalizeInput } from "../../core/url";
import { recordVisit, updateTitle } from "../../core/historyDB";
import { isOverlayOpen } from "../../core/overlay";

const isNative = Capacitor.isNativePlatform();

/**
 * Owns the browsing session and the app bar ↔ native slot sync.
 * Web preview: native calls are skipped and a note shows where the
 * content WebView renders on device — layout is identical for QA.
 */
export function BrowserScreen() {
  const [session, setSession] = useState<PageState | null>(null);
  const [focusSignal, setFocusSignal] = useState(0);
  const [lensBusy, setLensBusy] = useState(false);
  const lastInsetRef = useRef(0);
  // Module 2: dedupe key for history recording (last finished page).
  const lastVisitRef = useRef<{ url: string; title: string }>({ url: "", title: "" });

  const hasSession = !!session;

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
      if (s.crashed) return;
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
  }, []);

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

  const navigate = useCallback(async (raw: string) => {
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
      });
    } else {
      // Web preview has no page events — record the visit here so the
      // home feed still reflects QA traffic. On device the real (final)
      // URL+title arrives via the page listener instead.
      void recordVisit(url, url);
    }
  }, []);

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
        />
      )}
    </div>
  );
}
