// Orbit Browser — Orbit logo (planet + tilted orbit ring + satellite)
// Pure SVG, locally rendered — koi third-party icon service nahi.

interface OrbitLogoProps {
  size?: number;
  className?: string;
  /** ring + satellite ghoomte hain (subtle orbit animation) */
  spin?: boolean;
  /** rounded gradient square background (app icon look) */
  badge?: boolean;
}

export function OrbitLogo({ size = 64, className = "", spin = false, badge = false }: OrbitLogoProps) {
  const gradId = spin ? "orbitGradSpin" : "orbitGrad";
  const ink = badge ? "#ffffff" : `url(#${gradId})`;
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" className={className} role="img" aria-label="Orbit logo">
      <defs>
        <linearGradient id={gradId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#10b981" />
          <stop offset="1" stopColor="#0d9488" />
        </linearGradient>
      </defs>

      {badge && <rect x="0" y="0" width="64" height="64" rx="14" fill={`url(#${gradId})`} />}

      {badge ? (
        // badge: poora mark ko chhota karke center me (white ink)
        <g transform="translate(6.4 6.4) scale(0.8)">
          <ellipse cx="32" cy="32" rx="26" ry="14" transform="rotate(-18 32 32)" fill="none" stroke={ink} strokeWidth="3.4" strokeLinecap="round" opacity="0.95" />
          <circle cx="32" cy="32" r="11" fill={ink} />
          <circle cx="48.2" cy="17.3" r="4.5" fill={ink} />
        </g>
      ) : (
        <>
          {/* orbit ring + satellite — spin mode me ghoomta hai */}
          <g
            className={spin ? "orbit-spin" : undefined}
            style={spin ? { transformOrigin: "32px 32px" } : { transform: "rotate(-18deg)", transformOrigin: "32px 32px" }}
          >
            <ellipse cx="32" cy="32" rx="26" ry="14" fill="none" stroke={ink} strokeWidth="3.2" strokeLinecap="round" />
            <circle cx="48.2" cy="17.3" r="4.5" fill={ink} />
          </g>
          {/* planet */}
          <circle cx="32" cy="32" r="11" fill={ink} />
        </>
      )}
    </svg>
  );
}
