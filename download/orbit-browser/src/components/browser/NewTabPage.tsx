"use client";

import { useState } from "react";
import { EyeOff } from "lucide-react";
import { OrbitLogo } from "./OrbitLogo";

interface NewTabPageProps {
  privateMode: boolean;
  onNavigate: (input: string) => void;
}

// Orbit ka main screen — bilkul clean: sirf logo + search bar. Kuch aur nahi.
export function NewTabPage({ privateMode, onNavigate }: NewTabPageProps) {
  const [q, setQ] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (q.trim()) onNavigate(q);
  };

  return (
    <div className="h-full overflow-y-auto orbit-scroll bg-gradient-to-b from-muted/30 via-background to-background">
      <div className="min-h-full flex flex-col items-center justify-center px-6 py-16">
        {/* Private mode badge — sirf tab dikhna chahiye jab on ho */}
        {privateMode && (
          <span className="mb-8 flex items-center gap-1.5 text-[11px] font-medium bg-violet-600/10 text-violet-600 dark:text-violet-400 px-3 py-1.5 rounded-full">
            <EyeOff className="w-3 h-3" />
            Private — kuch save nahi hoga
          </span>
        )}

        {/* Orbit logo */}
        <OrbitLogo size={92} spin className="drop-shadow-[0_8px_32px_rgba(16,185,129,0.25)]" />

        {/* Search bar */}
        <form onSubmit={submit} className="w-full max-w-xl mt-10">
          <div className="flex items-center h-13 pl-5 pr-5 py-2 rounded-full bg-card border border-border shadow-sm focus-within:ring-2 focus-within:ring-emerald-600/40 focus-within:border-emerald-600/40 transition-all">
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search karo ya URL likho..."
              aria-label="Search ya URL"
              className="flex-1 bg-transparent outline-none text-[15px] min-w-0 text-center focus:text-left"
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
