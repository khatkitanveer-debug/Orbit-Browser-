import { useMemo, useState } from "react";
import { OrbitMark, IconSearch, IconCamera, IconIncognito, IconStar, IconClose } from "../../components/chrome/Icons";
import { ShortcutsRow } from "./ShortcutsRow";
import { AddShortcutSheet } from "./AddShortcutSheet";
import { DiscoverFeed } from "./DiscoverFeed";
import { Favicon } from "./Favicon";
import { useHistoryEntries } from "./useHistory";
import {
  DEFAULT_SHORTCUTS,
  MAX_CUSTOM,
  loadCustomShortcuts,
  saveCustomShortcuts,
  type Shortcut,
} from "./shortcuts";
import { buildFeed } from "./feedEngine";
import { useSyncState, requestSync, signInWithGoogle, signOutEverywhere, type SyncState } from "../../core/syncStore";
import type { Bookmark } from "../../core/bookmarksDB";

interface HomeScreenProps {
  /** Opens the app bar omnibox in edit mode. */
  onSearchTap: () => void;
  /** Opens any home target (shortcut / card / chip) in the content slot. */
  onOpen: (url: string) => void;
  /** Module 3: camera → Google Lens → results in the content slot. */
  onLens: () => void;
  lensBusy: boolean;
  /** Module 4: incognito session state. */
  incognito: boolean;
  bookmarks: Bookmark[];
  onExitIncognito: () => void;
  /** Sync comes from the store directly; actions are passed through. */
  sync?: SyncState;
  onSync?: () => void;
  onSignOut?: () => void;
}

/**
 * Module 2 home + Module 4 additions: incognito banner, bookmarks row and
 * a compact sync status line. Fully scrollable; the app bar stays fixed.
 */
export function HomeScreen({ onSearchTap, onOpen, onLens, lensBusy, incognito, bookmarks, onExitIncognito }: HomeScreenProps) {
  const [custom, setCustom] = useState<Shortcut[]>(loadCustomShortcuts);
  const [sheetOpen, setSheetOpen] = useState(false);
  const entries = useHistoryEntries();
  const sync = useSyncState();

  const shortcuts = useMemo(() => [...DEFAULT_SHORTCUTS, ...custom], [custom]);
  const feed = useMemo(() => buildFeed(entries), [entries]);
  const liveBookmarks = useMemo(() => bookmarks.slice(0, 6), [bookmarks]);

  const addShortcut = (s: Shortcut) => {
    const next = [...custom, s];
    setCustom(next);
    saveCustomShortcuts(next);
    setSheetOpen(false);
  };

  const removeShortcut = (id: string) => {
    const next = custom.filter((s) => s.id !== id);
    setCustom(next);
    saveCustomShortcuts(next);
  };

  const isEmpty = entries.length === 0 && shortcuts.length === DEFAULT_SHORTCUTS.length;

  return (
    <main className="home">
      <div className="home__scroll">
        {incognito && (
          <div className="incognito-banner" role="status">
            <IconIncognito size={18} />
            <span className="incognito-banner__text">
              You're browsing incognito — nothing is saved on this device.
            </span>
            <button className="incognito-banner__exit" onClick={onExitIncognito} aria-label="Exit incognito">
              <IconClose size={16} />
            </button>
          </div>
        )}

        <header className="home__brand">
          <OrbitMark size={40} />
          <div>
            <h1 className="home__word">Orbit</h1>
            <p className="home__tag">Fast. Private. Yours.</p>
          </div>
        </header>

        <div className="home__searchrow">
          <button className="home__search" onClick={onSearchTap} aria-label="Search or type URL">
            <IconSearch size={18} />
            Search or type URL
          </button>
          <button
            className="home__lens"
            onClick={onLens}
            disabled={lensBusy}
            aria-label="Search with Lens"
            title="Search with Lens"
          >
            <IconCamera size={22} />
          </button>
        </div>

        {liveBookmarks.length > 0 && (
          <div className="bookmarks" aria-label="Bookmarks">
            {liveBookmarks.map((b) => (
              <button key={b.id} className="bookmarks__chip" onClick={() => onOpen(b.url)}>
                <IconStar size={13} />
                <span className="bookmarks__label">{b.title || b.url}</span>
              </button>
            ))}
          </div>
        )}

        <ShortcutsRow
          shortcuts={shortcuts}
          canAddMore={custom.length < MAX_CUSTOM}
          onOpen={onOpen}
          onAddTap={() => setSheetOpen(true)}
        />

        {feed.topSites.length > 0 && (
          <div className="topsites" aria-label="Frequently visited">
            {feed.topSites.map((t) => (
              <button key={t.host} className="topsites__chip" onClick={() => onOpen(t.url)}>
                <Favicon host={t.host} size={16} />
                {t.host.replace(/^www\./, "")}
              </button>
            ))}
          </div>
        )}

        {isEmpty ? (
          <div className="feed-empty">
            <span className="feed-empty__ring" aria-hidden>
              <IconSearch size={22} />
            </span>
            <h2 className="feed-empty__title">Your feed builds itself</h2>
            <p className="feed-empty__sub">
              Videos, music and news you visit show up here. History never leaves your device.
            </p>
          </div>
        ) : (
          feed.cards.length > 0 && <DiscoverFeed feed={feed} onOpen={onOpen} />
        )}

        <footer className="home__sync">
          {!sync.configured ? (
            <p className="home__sync-line">Sync: add your Firebase config to enable.</p>
          ) : sync.email ? (
            <>
              <p className="home__sync-line">
                Synced as <strong>{sync.email}</strong>
                {sync.lastSyncAt > 0 && <> · {new Date(sync.lastSyncAt).toLocaleTimeString()}</>}
                {sync.status === "syncing" && " · syncing…"}
                {sync.status === "error" && " · sync failed"}
              </p>
              <div className="home__sync-actions">
                <button className="home__sync-btn" onClick={() => requestSync()} disabled={sync.status === "syncing"}>
                  Sync now
                </button>
                <button className="home__sync-btn" onClick={() => void signOutEverywhere()}>
                  Sign out
                </button>
              </div>
            </>
          ) : (
            <>
              <p className="home__sync-line">Sign in to sync history, bookmarks and shortcuts across devices.</p>
              <div className="home__sync-actions">
                <button
                  className="home__sync-btn"
                  onClick={() => void signInWithGoogle().catch(() => undefined)}
                >
                  Sign in with Google
                </button>
              </div>
            </>
          )}
        </footer>
      </div>

      {sheetOpen && (
        <AddShortcutSheet
          existing={custom}
          onClose={() => setSheetOpen(false)}
          onSave={addShortcut}
          onRemove={removeShortcut}
        />
      )}
    </main>
  );
}
