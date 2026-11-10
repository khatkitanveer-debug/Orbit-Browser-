import React from 'react';
import { 
  ArrowLeft, 
  ArrowRight, 
  RotateCw, 
  Home, 
  Lock, 
  Shield, 
  MoreVertical, 
  Layers,
  Sparkles
} from 'lucide-react';
import { BrowserTab } from '../types/browser';

interface AppBarProps {
  currentTab: BrowserTab;
  tabCount: number;
  shieldBlockedCount: number;
  onNavigateHome: () => void;
  onGoBack: () => void;
  onGoForward: () => void;
  onReload: () => void;
  onOpenOmniboxModal: () => void;
  onOpenTabSwitcher: () => void;
  onOpenMenu: () => void;
  onOpenShieldModal: () => void;
}

export const AppBar: React.FC<AppBarProps> = ({
  currentTab,
  tabCount,
  shieldBlockedCount,
  onNavigateHome,
  onGoBack,
  onGoForward,
  onReload,
  onOpenOmniboxModal,
  onOpenTabSwitcher,
  onOpenMenu,
  onOpenShieldModal
}) => {
  const isHome = currentTab.url === 'orbit://newtab' || !currentTab.url;
  const displayHost = isHome 
    ? 'orbit://newtab' 
    : (() => {
        try {
          return new URL(currentTab.url).hostname;
        } catch {
          return currentTab.url;
        }
      })();

  return (
    <header className="sticky top-0 z-40 w-full bg-[#070D19]/95 backdrop-blur-md border-b border-white/5 px-3 py-2.5 transition-colors">
      <div className="max-w-4xl mx-auto flex items-center justify-between gap-2">
        {/* Navigation Controls */}
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onGoBack}
            disabled={!currentTab.canGoBack}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer"
            title="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          <button
            onClick={onGoForward}
            disabled={!currentTab.canGoForward}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer"
            title="Forward"
          >
            <ArrowRight className="w-4 h-4" />
          </button>

          <button
            onClick={isHome ? onReload : onNavigateHome}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title={isHome ? "Reload" : "Orbit Homepage"}
          >
            {isHome ? <RotateCw className={`w-4 h-4 ${currentTab.isLoading ? 'animate-spin text-cyan-400' : ''}`} /> : <Home className="w-4 h-4" />}
          </button>
        </div>

        {/* Center URL / Omnibox Capsule */}
        <div 
          onClick={onOpenOmniboxModal}
          className="flex-1 max-w-lg mx-2 flex items-center justify-between px-3.5 py-1.5 rounded-full bg-[#162238] hover:bg-[#1C2C47] border border-[#243452] hover:border-cyan-500/30 transition-all cursor-pointer shadow-xs select-none"
        >
          <div className="flex items-center gap-2 truncate">
            {isHome ? (
              <Sparkles className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
            ) : (
              <Lock className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
            )}
            <span className="text-xs font-mono text-slate-200 truncate">
              {displayHost}
            </span>
          </div>

          {/* Quick Orbit Shield badge */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onOpenShieldModal();
            }}
            className="flex items-center gap-1 ml-2 px-1.5 py-0.5 rounded-full bg-cyan-950/80 hover:bg-cyan-900 border border-cyan-500/30 text-[10px] font-mono text-cyan-300 shrink-0 transition-colors cursor-pointer"
            title="Orbit Shield Protection: Click to inspect"
          >
            <Shield className="w-3 h-3 text-cyan-400 fill-cyan-400/20" />
            <span>{shieldBlockedCount}</span>
          </button>
        </div>

        {/* Right Section: Tab Switcher & 3-Dot Chrome Menu */}
        <div className="flex items-center gap-1 shrink-0">
          {/* Tab Switcher Button with live badge */}
          <button
            onClick={onOpenTabSwitcher}
            className="relative p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Switch Tabs"
          >
            <div className="w-5 h-5 rounded-md border border-white/40 flex items-center justify-center font-mono text-[11px] font-bold text-slate-200">
              {tabCount}
            </div>
            {currentTab.isIncognito && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-purple-500 ring-2 ring-[#070D19]" title="Incognito Tab" />
            )}
          </button>

          {/* Chrome-Style 3-Dot (⋮) Overflow Menu Button */}
          <button
            onClick={onOpenMenu}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Customize and control Orbit"
          >
            <MoreVertical className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
};
