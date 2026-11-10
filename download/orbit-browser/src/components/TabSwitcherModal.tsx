import React from 'react';
import { Plus, EyeOff, X, Globe, Sparkles, Cpu, Layers } from 'lucide-react';
import { BrowserTab } from '../types/browser';

interface TabSwitcherModalProps {
  isOpen: boolean;
  onClose: () => void;
  tabs: BrowserTab[];
  activeTabId: string;
  onSelectTab: (id: string) => void;
  onCloseTab: (id: string) => void;
  onNewTab: (incognito?: boolean) => void;
  onCloseAllTabs: () => void;
}

export const TabSwitcherModal: React.FC<TabSwitcherModalProps> = ({
  isOpen,
  onClose,
  tabs,
  activeTabId,
  onSelectTab,
  onCloseTab,
  onNewTab,
  onCloseAllTabs
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-xl flex flex-col p-4 sm:p-6 text-slate-200 animate-in fade-in zoom-in-95 duration-150">
      {/* Top Header */}
      <div className="max-w-4xl w-full mx-auto flex items-center justify-between pb-4 border-b border-white/10">
        <div className="flex items-center gap-2">
          <Layers className="w-5 h-5 text-cyan-400" />
          <h2 className="text-base font-bold text-white tracking-tight">Tabs ({tabs.length})</h2>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-300 border border-cyan-800">
            Intelligent Discarding Active
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              onNewTab(false);
              onClose();
            }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 text-xs font-semibold transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>New Tab</span>
          </button>

          <button
            onClick={() => {
              onNewTab(true);
              onClose();
            }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-950/70 hover:bg-purple-900 border border-purple-500/30 text-purple-200 text-xs font-semibold transition-colors cursor-pointer"
          >
            <EyeOff className="w-4 h-4 text-purple-400" />
            <span className="hidden sm:inline">Incognito</span>
          </button>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors ml-2 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Memory optimization status bar */}
      <div className="max-w-4xl w-full mx-auto my-3 px-3 py-2 rounded-xl bg-[#0E1726] border border-white/5 flex items-center justify-between text-xs text-slate-400 font-mono">
        <div className="flex items-center gap-2">
          <Cpu className="w-3.5 h-3.5 text-emerald-400" />
          <span>RAM footprint: <strong className="text-slate-200">112 MB</strong> / 512 MB Budget</span>
        </div>
        <span>{tabs.filter(t => t.isDiscarded).length} tabs suspended to save memory</span>
      </div>

      {/* Tabs Grid */}
      <div className="max-w-4xl w-full mx-auto flex-1 overflow-y-auto py-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTabId;
          const isHome = tab.url === 'orbit://newtab' || !tab.url;

          return (
            <div
              key={tab.id}
              onClick={() => {
                onSelectTab(tab.id);
                onClose();
              }}
              className={`relative rounded-2xl border overflow-hidden cursor-pointer transition-all duration-200 flex flex-col h-56 bg-[#0E1726] group ${
                isActive
                  ? 'border-cyan-400 ring-2 ring-cyan-400/30 shadow-[0_0_20px_rgba(56,189,248,0.2)]'
                  : 'border-white/10 hover:border-white/20'
              }`}
            >
              {/* Tab Header */}
              <div className="px-3.5 py-2.5 bg-[#162235] border-b border-white/5 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 truncate flex-1">
                  {tab.isIncognito ? (
                    <EyeOff className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                  ) : isHome ? (
                    <Sparkles className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  ) : (
                    <Globe className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  )}
                  <span className="text-xs font-semibold text-white truncate">
                    {tab.title}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onCloseTab(tab.id);
                  }}
                  className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors shrink-0"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Tab Preview Area */}
              <div className="flex-1 p-4 flex flex-col items-center justify-center text-center relative bg-[#070D19]/60">
                {isHome ? (
                  <div className="space-y-1">
                    <div className="w-8 h-8 rounded-full bg-cyan-500/20 mx-auto flex items-center justify-center text-cyan-400 font-bold text-xs">
                      O
                    </div>
                    <div className="text-xs font-bold text-slate-200">Orbit Homepage</div>
                    <div className="text-[10px] font-mono text-slate-500">orbit://newtab</div>
                  </div>
                ) : (
                  <div className="space-y-1.5 max-w-full px-2">
                    <div className="text-xs font-medium text-slate-300 truncate max-w-full">
                      {tab.url}
                    </div>
                    <div className="text-[10px] font-mono text-emerald-400">
                      {tab.isDiscarded ? 'Suspended (State Saved)' : 'Rendered in Memory'}
                    </div>
                  </div>
                )}

                {/* Incognito indicator watermark */}
                {tab.isIncognito && (
                  <span className="absolute bottom-2 right-2 text-[10px] font-mono text-purple-400 px-1.5 py-0.5 rounded bg-purple-950/80 border border-purple-800">
                    Incognito
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Bottom Actions */}
      <div className="max-w-4xl w-full mx-auto pt-3 border-t border-white/10 flex items-center justify-between text-xs text-slate-400">
        <button
          onClick={onCloseAllTabs}
          className="text-slate-400 hover:text-rose-400 transition-colors cursor-pointer"
        >
          Close all tabs
        </button>

        <button
          onClick={onClose}
          className="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-white font-medium transition-colors cursor-pointer"
        >
          Done
        </button>
      </div>
    </div>
  );
};
