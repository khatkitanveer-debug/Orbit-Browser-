import { useMemo, useState } from "react";
import { OrbitMark, IconSearch, IconCamera } from "../../components/chrome/Icons";
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

interface HomeScreenProps {
  /** Opens the app bar omnibox in edit mode. */
  onSearchTap: () => void;
  /** Opens any home target (shortcut / card / chip) in the content slot. */
  onOpen: (url: string) => void;
  /** Module 3: camera → Google Lens → results in the content slot. */
  onLens: () => void;
  lensBusy: boolean;
}

/**
 * Module 2 home: brand header, search, the 2-default + "+" shortcut grid,
 * frequently-visited chips, and the Discover feed generated from local
 * history. Fully scrollable; the app bar above stays fixed and clean.
 */
export function HomeScreen({ onSearchTap, onOpen, onLens, lensBusy }: HomeScreenProps) {
  const [custom, setCustom] = useState<Shortcut[]>(loadCustomShortcuts);
  const [sheetOpen, setSheetOpen] = useState(false);
  const entries = useHistoryEntries();

  const shortcuts = useMemo(() => [...DEFAULT_SHORTCUTS, ...custom], [custom]);
  const feed = useMemo(() => buildFeed(entries), [entries]);

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
