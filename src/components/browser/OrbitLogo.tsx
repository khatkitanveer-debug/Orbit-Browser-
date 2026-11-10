// Orbit Browser — OFFICIAL logo (uploaded design ke mutabiq)
// "O" = orbit ring (gradient: light top-right -> dark bottom-left) + satellite dot
// jo ring ke around ghoomta hai. Wordmark ke glyphs Quicksand se outline kiye gaye hain.
// Pure SVG, locally rendered — koi third-party icon service nahi.

import { ORBIT_WORDMARK_D } from "./orbitWordmarkPath";

interface OrbitLogoProps {
  size?: number;
  className?: string;
  /** satellite dot ring ke around ghoomta hai (orbit animation) */
  spin?: boolean;
  /** black rounded square background (app icon look) */
  badge?: boolean;
}

export function OrbitLogo({ size = 64, className = "", spin = false, badge = false }: OrbitLogoProps) {
  const gradId = spin ? "orbGradSpin" : "orbGrad";
  const dotBase = { cx: 47.4, cy: 178.4, r: 13.5 }; // 220° par ring par (bottom-left)
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 256 256"
      className={className}
      role="img"
      aria-label="Orbit logo"
    >
      <defs>
        <linearGradient id={gradId} x1="1" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#a8a8a8" />
        </linearGradient>
      </defs>

      {badge && <rect x="0" y="0" width="256" height="256" rx="58" fill="#0a0a0b" />}

      <circle
        cx="128"
        cy="128"
        r="78"
        fill="none"
        stroke={`url(#${gradId})`}
        strokeWidth="16"
        strokeLinecap="round"
      />

      {/* satellite dot — spin mode me ring ke around orbit karta hai */}
      <g
        className={spin ? "orbit-dot-spin" : undefined}
        style={spin ? { transformOrigin: "128px 128px" } : undefined}
      >
        <circle cx={dotBase.cx} cy={dotBase.cy} r={dotBase.r} fill="#ffffff" />
      </g>
    </svg>
  );
}

/** "Orbit" poora wordmark (official design) — static, white ink */
export function OrbitWordmark({ className = "", height = 40 }: { className?: string; height?: number }) {
  // canvas: 0 0 638 320 — O ring bhi included hai (x: 38-218)
  return (
    <svg
      viewBox="26 40 612 240"
      height={height}
      className={className}
      role="img"
      aria-label="Orbit"
    >
      <defs>
        <linearGradient id="orbWmGrad" x1="1" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#a8a8a8" />
        </linearGradient>
      </defs>
      <circle cx="132" cy="158" r="78" fill="none" stroke="url(#orbWmGrad)" strokeWidth="16" strokeLinecap="round" />
      <circle cx="47.4" cy="178.4" r="13.5" fill="#ffffff" />
      <path d={ORBIT_WORDMARK_D} fill="#ffffff" />
    </svg>
  );
}
