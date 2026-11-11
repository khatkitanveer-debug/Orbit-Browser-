import { useCallback, useEffect, useRef, useState } from "react";
import { IconBack, IconDots, IconForward } from "./Icons";
import { Omnibox } from "./Omnibox";
import { ChromeMenu, type MenuAction } from "./ChromeMenu";
import type { PageState } from "../../core/orbit";

interface AppBarProps {
  session: PageState | null;
  onNavigate: (input: string) => void;
  onReload: () => void;
  onBack: () => void;
  onForward: () => void;
  onHome: () => void;
  /** Reports the bar's bottom edge (CSS px) — the native slot syncs to it. */
  onInsetChange: (bottomCssPx: number) => void;
  focusSignal: number;
}

/**
 * THE app bar. Layout contract (never overlaps, at any width/zoom/font):
 *   grid: [auto nav] [minmax(0,1fr) omnibox] [auto actions]
 *   • icons are flex:none — they can shrink to nothing, never overlap
 *   • all text lives in min-width:0 containers with truncation
 *   • the progress line is transform-only, so it can never push layout
 *   • safe-area top is part of the bar, so the native slot matches exactly
 */
export function AppBar({
  session,
  onNavigate,
  onReload,
  onBack,
  onForward,
  onHome,
  onInsetChange,
  focusSignal,
}: AppBarProps) {
  const barRef = useRef<HTMLElement | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  // Single source of truth for the native slot: the measured DOM edge.
  useEffect(() => {
    const el = barRef.current;
    if (!el) return;
    let raf = 0;
    const report = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => onInsetChange(el.getBoundingClientRect().bottom));
    };
    report();
    const ro = new ResizeObserver(report);
    ro.observe(el);
    window.addEventListener("orientationchange", report);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      window.removeEventListener("orientationchange", report);
    };
  }, [onInsetChange]);

  const onMenuAction = useCallback(
    (action: MenuAction) => {
      setMenuOpen(false);
      if (action === "back") onBack();
      else if (action === "forward") onForward();
      else if (action === "reload") onReload();
      else onHome();
    },
    [onBack, onForward, onReload, onHome],
  );

  return (
    <header className="appbar" ref={barRef}>
      <div className="appbar__row">
        <div className="appbar__nav">
          <button className="iconbtn" aria-label="Back" disabled={!session?.canGoBack} onClick={onBack}>
            <IconBack />
          </button>
          <button
            className="iconbtn appbar__fwd"
            aria-label="Forward"
            disabled={!session?.canGoForward}
            onClick={onForward}
          >
            <IconForward />
          </button>
        </div>

        <Omnibox session={session} onNavigate={onNavigate} onReload={onReload} focusSignal={focusSignal} />

        <div className="appbar__actions">
          <button
            className="iconbtn"
            aria-label="Menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((v) => !v)}
          >
            <IconDots />
          </button>
        </div>
      </div>

      {session && (
        <div
          className="appbar__progress"
          style={{
            transform: `scaleX(${Math.min(100, session.progress) / 100})`,
            opacity: session.loading ? 1 : 0,
          }}
        />
      )}

      {menuOpen && (
        <ChromeMenu
          canBack={!!session?.canGoBack}
          canForward={!!session?.canGoForward}
          canReload={!!session}
          onAction={onMenuAction}
          onClose={() => setMenuOpen(false)}
        />
      )}
    </header>
  );
}
