import { useEffect, useRef, useState } from "react";
import { IconGlobe, IconLock, IconReload, IconSearch } from "./Icons";
import { splitUrl } from "../../core/url";
import type { PageState } from "../../core/orbit";

interface OmniboxProps {
  session: PageState | null;
  onNavigate: (input: string) => void;
  onReload: () => void;
  /** HomeScreen search pill taps this to open edit mode (0 = idle). */
  focusSignal: number;
}

/**
 * The omnibox is the ONLY flexible element in the app bar and it owns
 * min-width:0 — hosts and paths truncate, icons never move.
 * Unfocused browsing shows the Chrome-style split: bold host + dim path.
 */
export function Omnibox({ session, onNavigate, onReload, focusSignal }: OmniboxProps) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const url = session?.url ?? "";
  const secure = url.startsWith("https://");
  const { host, rest } = splitUrl(url);
  const showInput = editing || !session;

  // External focus requests (home search pill) — never autofocus on launch.
  useEffect(() => {
    if (focusSignal > 0) {
      setValue("");
      setEditing(true);
    }
  }, [focusSignal]);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setEditing(false);
    inputRef.current?.blur();
    onNavigate(value);
  };

  return (
    <form className="omnibox" onSubmit={submit} role="search">
      <div className="omnibox__pill" data-focused={editing || undefined}>
        {session && !editing ? (
          secure ? (
            <IconLock className="omnibox__icon" size={16} />
          ) : (
            <IconGlobe className="omnibox__icon" size={16} />
          )
        ) : (
          <IconSearch className="omnibox__icon" size={16} />
        )}

        {showInput ? (
          <input
            ref={inputRef}
            className="omnibox__input"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onFocus={(e) => requestAnimationFrame(() => e.target.select())}
            onBlur={() => setTimeout(() => setEditing(false), 120)}
            placeholder="Search or type URL"
            aria-label="Search or type URL"
            dir="ltr"
            enterKeyHint="go"
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
          />
        ) : (
          <button
            type="button"
            className="omnibox__text"
            onClick={() => {
              setValue(url);
              setEditing(true);
            }}
            aria-label={`Address: ${host}${rest}`}
          >
            <span className="omnibox__host">{host}</span>
            {rest && <span className="omnibox__rest">{rest}</span>}
          </button>
        )}

        {session && !editing && (
          <button type="button" className="iconbtn omnibox__reload" aria-label="Reload" onClick={onReload}>
            <IconReload size={17} />
          </button>
        )}
      </div>
    </form>
  );
}
