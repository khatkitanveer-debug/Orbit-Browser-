import { Favicon } from "./Favicon";
import { IconPlus } from "../../components/chrome/Icons";
import type { Shortcut } from "./shortcuts";

interface ShortcutsRowProps {
  shortcuts: Shortcut[];
  canAddMore: boolean;
  onOpen: (url: string) => void;
  onAddTap: () => void;
}

/**
 * The home grid: defaults + user shortcuts + the "+" tile.
 * 4-column grid like Chrome's NTP — with only 3 items the remaining slots
 * stay empty, exactly the shipped state the spec asks for.
 */
export function ShortcutsRow({ shortcuts, canAddMore, onOpen, onAddTap }: ShortcutsRowProps) {
  return (
    <nav className="shortcuts" aria-label="Shortcuts">
      {shortcuts.map((s) => (
        <button key={s.id} className="shortcut" onClick={() => onOpen(s.url)}>
          <span className="shortcut__icon">
            <Favicon host={hostOf(s.url)} size={28} />
          </span>
          <span className="shortcut__label">{s.label}</span>
        </button>
      ))}
      {canAddMore && (
        <button className="shortcut" onClick={onAddTap} aria-label="Add shortcut">
          <span className="shortcut__icon shortcut__icon--add">
            <IconPlus size={22} />
          </span>
          <span className="shortcut__label">Add</span>
        </button>
      )}
    </nav>
  );
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname;
  } catch {
    return "";
  }
}
