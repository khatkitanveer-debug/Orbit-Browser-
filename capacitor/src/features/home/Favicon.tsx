import { useState } from "react";

/**
 * Site favicon with an offline-safe monogram fallback.
 * Fetches from Google's favicon service — the same public endpoint Chrome's
 * own NTP uses. If it fails (offline, blocked), a letter tile renders and
 * nothing breaks.
 */
export function Favicon({ host, size = 28 }: { host: string; size?: number }) {
  const [failed, setFailed] = useState(false);
  const letter = (host.replace(/^www\./, "")[0] || "?").toUpperCase();

  if (failed || !host) {
    return (
      <span className="favicon__fallback" style={{ width: size, height: size, fontSize: size * 0.44 }} aria-hidden>
        {letter}
      </span>
    );
  }
  return (
    <img
      className="favicon__img"
      src={`https://www.google.com/s2/favicons?domain=${encodeURIComponent(host)}&sz=${size <= 32 ? 64 : 128}`}
      width={size}
      height={size}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setFailed(true)}
    />
  );
}
