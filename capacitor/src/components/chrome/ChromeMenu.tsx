import { useEffect, useRef } from "react";
import {
  IconBack,
  IconCamera,
  IconForward,
  IconHome,
  IconReload,
  IconStar,
  IconIncognito,
  IconShield,
} from "./Icons";

export type MenuAction =
  | "back"
  | "forward"
  | "reload"
  | "home"
  | "lens"
  | "bookmark"
  | "incognito"
  | "sync"
  | "vpn";

interface MenuItem {
  label: string;
  enabled: boolean;
}

interface ChromeMenuProps {
  canBack: boolean;
  canForward: boolean;
  canReload: boolean;
  /** Module 3: Lens scan in flight. */
  lensBusy?: boolean;
  /** Module 4: session state flags for honest labels. */
  incognito: boolean;
  canBookmark: boolean;
  bookmarked: boolean;
  syncItem: MenuItem;
  vpnItem: MenuItem;
  onAction: (action: MenuAction) => void;
  onClose: () => void;
}

/**
 * 3-dot menu. Positioned inside the app bar stacking context, above the
 * content slot via --z-menu. Only live actions — no stubs.
 */
export function ChromeMenu({
  canBack,
  canForward,
  canReload,
  lensBusy,
  incognito,
  canBookmark,
  bookmarked,
  syncItem,
  vpnItem,
  onAction,
  onClose,
}: ChromeMenuProps) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onPointer = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("pointerdown", onPointer, true);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer, true);
      document.removeEventListener("keydown", onKey);
    };
  }, [onClose]);

  return (
    <div className="menu" ref={ref} role="menu" aria-label="Browser menu">
      <button className="menu__item" role="menuitem" disabled={!canBack} onClick={() => onAction("back")}>
        <IconBack size={18} /> Back
      </button>
      <button className="menu__item" role="menuitem" disabled={!canForward} onClick={() => onAction("forward")}>
        <IconForward size={18} /> Forward
      </button>
      <div className="menu__sep" />
      <button className="menu__item" role="menuitem" disabled={!canReload} onClick={() => onAction("reload")}>
        <IconReload size={18} /> Reload
      </button>
      <button className="menu__item" role="menuitem" onClick={() => onAction("home")}>
        <IconHome size={18} /> Home
      </button>
      <button
        className="menu__item"
        role="menuitem"
        disabled={!canBookmark}
        onClick={() => onAction("bookmark")}
      >
        <IconStar size={18} /> {bookmarked ? "Bookmarked" : "Bookmark this page"}
      </button>
      <div className="menu__sep" />
      <button className="menu__item" role="menuitem" disabled={lensBusy} onClick={() => onAction("lens")}>
        <IconCamera size={18} /> {lensBusy ? "Scanning…" : "Search with Lens"}
      </button>
      <button className="menu__item" role="menuitem" onClick={() => onAction("incognito")}>
        <IconIncognito size={18} /> {incognito ? "Exit incognito" : "New incognito tab"}
      </button>
      <button
        className="menu__item"
        role="menuitem"
        disabled={!syncItem.enabled}
        onClick={() => onAction("sync")}
      >
        <IconReload size={18} /> {syncItem.label}
      </button>
      <button
        className="menu__item"
        role="menuitem"
        disabled={!vpnItem.enabled}
        onClick={() => onAction("vpn")}
      >
        <IconShield size={18} /> {vpnItem.label}
      </button>
    </div>
  );
}
