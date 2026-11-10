"use client";

import { useState } from "react";
import { EyeOff } from "lucide-react";
import { OrbitLogo, OrbitWordmark } from "./OrbitLogo";

interface NewTabPageProps {
  privateMode: boolean;
  onNavigate: (input: string) => void;
}

// Orbit ka main screen — bilkul clean: sirf logo + search bar. Kuch aur nahi.
// Theme: pure black (official B&W branding).
export function NewTabPage({ privateMode, onNavigate }: NewTabPageProps) {
  const [q, setQ] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (q.trim()) onNavigate(q);
  };

  return (
    <div className="h-full overflow-y-auto orbit-scroll bg-black">
      <div className="min-h-full flex flex-col items-center justify-center px-6 py-16">
        {/* Private mode badge — sirf tab dikhna chahiye jab on ho */}
        {privateMode && (
          <span className="mb-8 flex items-center gap-1.5 text-[11px] font-medium bg-white/10 text-neutral-300 px-3 py-1.5 rounded-full">
            <EyeOff className="w-3 h-3" />
            Private — kuch save nahi hoga
          </span>
        )}

        {/* Orbit logo — satellite dot ring ke around ghoomta hai */}
        <OrbitLogo size={88} spin className="drop-shadow-[0_8px_40px_rgba(255,255,255,0.14)]" />

        {/* Official wordmark */}
        <OrbitWordmark height={34} className="mt-5 opacity-95" />

        {/* Search bar */}
        <form onSubmit={submit} className="w-full max-w-xl mt-10">
          <div className="flex items-center h-13 pl-5 pr-5 py-2 rounded-full bg-[#0e0e10] border border-neutral-800 shadow-sm focus-within:ring-2 focus-within:ring-white/25 focus-within:border-neutral-600 transition-all">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search karo ya URL likho..."
              aria-label="Search ya URL"
              className="flex-1 bg-transparent outline-none text-[15px] min-w-0 text-center focus:text-left text-neutral-100 placeholder:text-neutral-500"
              autoFocus
              spellCheck={false}
              autoComplete="off"
            />
          </div>
        </form>
      </div>
    </div>
  );
}
