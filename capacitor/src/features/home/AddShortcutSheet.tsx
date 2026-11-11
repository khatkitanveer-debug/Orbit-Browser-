import { useEffect, useId, useRef, useState } from "react";
import { Capacitor } from "@capacitor/core";
import { App as CapApp } from "@capacitor/app";
import { IconClose, IconTrash } from "../../components/chrome/Icons";
import { isOverlayOpen, overlayPush } from "../../core/overlay";
import {
  canonicalShortcutUrl,
  newShortcutId,
  suggestLabel,
  type Shortcut,
} from "./shortcuts";

interface AddShortcutSheetProps {
  existing: Shortcut[];
  onClose: () => void;
  onSave: (shortcut: Shortcut) => void;
  onRemove: (id: string) => void;
}

/**
 * Bottom sheet for the "+" tile: add a named shortcut, remove existing
 * customs. Hardware back and scrim tap close it first — the browser's back
 * path yields while isOverlayOpen().
 */
export function AddShortcutSheet({ existing, onClose, onSave, onRemove }: AddShortcutSheetProps) {
  const [url, setUrl] = useState("");
  const [label, setLabel] = useState("");
  const [labelTouched, setLabelTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const urlRef = useRef<HTMLInputElement>(null);
  const titleId = useId();

  // Overlay accounting + hardware back closes the sheet, not the browser.
  useEffect(() => {
    const release = overlayPush();
    const isNative = Capacitor.isNativePlatform();
    let sub: Promise<import("@capacitor/core").PluginListenerHandle> | null = null;
    if (isNative) {
      sub = CapApp.addListener("backButton", () => {
        if (isOverlayOpen()) onClose();
      });
    }
    return () => {
      release();
      void sub?.then((h) => h.remove());
    };
  }, [onClose]);

  useEffect(() => {
    urlRef.current?.focus();
  }, []);

  const onUrlChange = (value: string) => {
    setUrl(value);
    setError(null);
    if (!labelTouched) setLabel(suggestLabel(canonicalShortcutUrl(value) ?? ""));
  };

  const submit = () => {
    const canonical = canonicalShortcutUrl(url);
    if (!canonical) {
      setError("Enter a valid site address, e.g. wikipedia.org");
      return;
    }
    if (existing.some((s) => normalizeHost(s.url) === normalizeHost(canonical))) {
      setError("This site is already on your home screen");
      return;
    }
    const name = (label || suggestLabel(canonical)).trim() || normalizeHost(canonical);
    onSave({ id: newShortcutId(), label: name.slice(0, 24), url: canonical });
  };

  return (
    <div className="sheet" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <button className="sheet__scrim" onClick={onClose} aria-label="Close" />
      <div className="sheet__panel">
        <header className="sheet__head">
          <h2 id={titleId} className="sheet__title">
            Add shortcut
          </h2>
          <button className="iconbtn" onClick={onClose} aria-label="Close">
            <IconClose size={20} />
          </button>
        </header>

        <label className="sheet__field">
          <span className="sheet__label">Address</span>
          <input
            ref={urlRef}
            className="sheet__input"
            value={url}
            onChange={(e) => onUrlChange(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            placeholder="example.com"
            inputMode="url"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
          />
        </label>

        <label className="sheet__field">
          <span className="sheet__label">Name (optional)</span>
          <input
            className="sheet__input"
            value={label}
            onChange={(e) => {
              setLabel(e.target.value);
              setLabelTouched(true);
            }}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            placeholder="Auto from address"
            maxLength={24}
          />
        </label>

        {error && (
          <p className="sheet__error" role="alert">
            {error}
          </p>
        )}

        <button className="sheet__save" onClick={submit}>
          Add to home
        </button>

        {existing.length > 0 && (
          <>
            <div className="sheet__sep" />
            <p className="sheet__sub">Your shortcuts</p>
            <ul className="sheet__list">
              {existing.map((s) => (
                <li key={s.id} className="sheet__row">
                  <span className="sheet__row-label">{s.label}</span>
                  <button
                    className="iconbtn sheet__remove"
                    onClick={() => onRemove(s.id)}
                    aria-label={`Remove ${s.label}`}
                  >
                    <IconTrash size={18} />
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  );
}

function normalizeHost(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "").toLowerCase();
  } catch {
    return url;
  }
}
