/** Inline SVG icon set — zero dependencies, currentColor, crisp at any density. */

interface IconProps {
  className?: string;
  size?: number;
}

function svgProps({ className, size = 20 }: IconProps) {
  return {
    viewBox: "0 0 24 24",
    width: size,
    height: size,
    className,
    "aria-hidden": true as const,
  };
}

export const IconBack = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" />
  </svg>
);

export const IconForward = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path fill="currentColor" d="M4 13h12.17l-5.59 5.59L12 20l8-8-8-8-1.42 1.41L16.17 11H4v2z" />
  </svg>
);

export const IconReload = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path
      fill="currentColor"
      d="M17.65 6.35A8 8 0 1 0 19.73 14h-2.08a6 6 0 1 1-1.41-6.24L13 11h7V4l-2.35 2.35z"
    />
  </svg>
);

export const IconGlobe = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path
      fill="currentColor"
      d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm7.93 9h-3.02a15.7 15.7 0 0 0-1.2-5.06A8.03 8.03 0 0 1 19.93 11zM12 4.07c.83 1.2 1.62 3.06 1.86 5.93h-3.72c.24-2.87 1.03-4.73 1.86-5.93zM4.07 13h3.02c.14 1.86.55 3.57 1.2 5.06A8.03 8.03 0 0 1 4.07 13zm3.02-2H4.07a8.03 8.03 0 0 1 4.22-5.06A15.7 15.7 0 0 0 7.09 11zM12 19.93c-.83-1.2-1.62-3.06-1.86-5.93h3.72c-.24 2.87-1.03 4.73-1.86 5.93zm3.71-.87c.65-1.49 1.06-3.2 1.2-5.06h3.02a8.03 8.03 0 0 1-4.22 5.06z"
    />
  </svg>
);

export const IconLock = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path
      fill="currentColor"
      d="M18 8h-1V6a5 5 0 0 0-10 0v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2zM9 6a3 3 0 0 1 6 0v2H9V6zm3 12a2 2 0 1 1 0-4 2 2 0 0 1 0 4z"
    />
  </svg>
);

export const IconSearch = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path
      fill="currentColor"
      d="M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0a4.5 4.5 0 1 1 0-9 4.5 4.5 0 0 1 0 9z"
    />
  </svg>
);

export const IconDots = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path
      fill="currentColor"
      d="M12 8a2 2 0 1 0 0-4 2 2 0 0 0 0 4zm0 2a2 2 0 1 0 0 4 2 2 0 0 0 0-4zm0 6a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"
    />
  </svg>
);

export const IconHome = (p: IconProps) => (
  <svg {...svgProps(p)}>
    <path fill="currentColor" d="M10 20v-6h4v6h5v-8h3L12 2 2 12h3v8h5z" />
  </svg>
);

/** Orbit brand mark — ring + satellite, rendered from the official geometry. */
export function OrbitMark({ size = 88 }: { size?: number }) {
  return (
    <svg viewBox="0 0 256 256" width={size} height={size} aria-hidden>
      <defs>
        <linearGradient id="orbit-ring" x1="1" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#4C8DFF" />
        </linearGradient>
      </defs>
      <circle cx="128" cy="128" r="88" fill="none" stroke="url(#orbit-ring)" strokeWidth="16" strokeLinecap="round" />
      <circle cx="60.6" cy="184.6" r="19" fill="#4C8DFF" />
    </svg>
  );
}
