"use client";

import { Globe, Lock, Plus, X, EyeOff } from "lucide-react";
import { Tab } from "@/lib/browser/types";
import { isExternal, domainOf, letterOf, tileColorFor } from "@/lib/browser/shared";

interface TabBarProps {
  tabs: Tab[];
  activeTabId: string;
  privateMode: boolean;
  onSelect: (id: string) => void;
  onClose: (id: string) => void;
  onNew: () => void;
}

export function TabBar({ tabs, activeTabId, privateMode, onSelect, onClose, onNew }: TabBarProps) {
  return (
    <div className="flex items-center gap-1 bg-muted/70 px-2 pt-1.5 border-b border-border/40" role="tablist" aria-label="Browser tabs">
      <div className="flex items-center gap-1 flex-1 overflow-x-auto tez-scroll">
        {tabs.map((tab) => {
          const active = tab.id === activeTabId;
          const external = isExternal(tab.url);
          const label = external ? domainOf(tab.url) : tab.title;
          return (
            <div
              key={tab.id}
              role="tab"
              aria-selected={active}
              onClick={() => onSelect(tab.id)}
              className={`group flex items-center gap-2 h-9 px-3 rounded-t-lg cursor-pointer select-none min-w-[110px] max-w-[220px] transition-colors ${
                active
                  ? "bg-background text-foreground shadow-[0_-1px_3px_rgba(0,0,0,0.06)]"
                  : "text-muted-foreground hover:bg-background/60"
              }`}
            >
              <span
                className={`flex items-center justify-center w-4 h-4 rounded-full text-[9px] font-bold text-white shrink-0 ${tileColorFor(tab.url || tab.id)}`}
                aria-hidden
              >
                {external ? letterOf(tab.url) : "T"}
              </span>
              <span className="truncate text-xs flex-1">{label || "New Tab"}</span>
              {tab.loading && (
                <span className="w-3 h-3 border-2 border-muted-foreground/40 border-t-emerald-600 rounded-full animate-spin shrink-0" aria-hidden />
              )}
              <button
                aria-label="Tab band karo"
                onClick={(e) => {
                  e.stopPropagation();
                  onClose(tab.id);
                }}
                className="opacity-0 group-hover:opacity-100 hover:bg-muted rounded p-0.5 transition-opacity"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          );
        })}
        <button
          aria-label="Naya tab kholo"
          onClick={onNew}
          className="shrink-0 h-8 w-8 rounded-md hover:bg-background/80 flex items-center justify-center text-muted-foreground transition-colors"
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>
      {privateMode && (
        <span className="ml-2 shrink-0 flex items-center gap-1.5 text-[11px] font-medium bg-violet-600/10 text-violet-600 dark:text-violet-400 px-2.5 py-1 rounded-full">
          <EyeOff className="w-3 h-3" />
          Private
        </span>
      )}
    </div>
  );
}

// Lock icon ko omnibox ke bahar use karne ke liye chhota export
export function SiteGlyph({ url }: { url: string }) {
  return isExternal(url) && url.startsWith("https") ? (
    <Lock className="w-3.5 h-3.5 text-emerald-600" />
  ) : (
    <Globe className="w-3.5 h-3.5 text-muted-foreground" />
  );
}
