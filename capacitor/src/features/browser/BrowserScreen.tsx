import { useCallback, useEffect, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { AppBar } from "../../components/chrome/AppBar";
import { HomeScreen } from "../home/HomeScreen";
import { OrbitRender, type PageState } from "../../core/orbit";
import { normalizeInput } from "../../core/url";

const isNative = Capacitor.isNativePlatform();

/**
 * Owns the browsing session and the app bar ↔ native slot sync.
 * Web preview: native calls are skipped and a note shows where the
 * content WebView renders on device — layout is identical for QA.
 */
export function BrowserScreen() {
  const [session, setSession] = useState<PageState | null>(null);
  const [focusSignal, setFocusSignal] = useState(0);
  const lastInsetRef = useRef(0);

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

  // Native page events → session state.
  useEffect(() => {
    if (!isNative) return;
    let active = true;
    const sub = OrbitRender.addListener("page", (s) => {
      if (active) setSession(s);
    });
    return () => {
      active = false;
      void sub.then((h) => h.remove());
    };
  }, []);

  const endSession = useCallback(async () => {
    if (isNative) await OrbitRender.close();
    setSession(null);
  }, []);

  // Hardware back: content back → close content → exit. Chrome behavior.
  useEffect(() => {
    if (!isNative) return;
    const sub = CapApp.addListener("backButton", async () => {
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
    }
  }, []);

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
        <HomeScreen onSearchTap={() => setFocusSignal((n) => n + 1)} />
      )}
    </div>
  );
}
