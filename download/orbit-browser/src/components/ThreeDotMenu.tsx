import React from 'react';
import { 
  Plus, 
  EyeOff, 
  History, 
  Download, 
  Bookmark, 
  Monitor, 
  ShieldCheck, 
  Search, 
  Share2, 
  Settings,
  Code2,
  X,
  Check
} from 'lucide-react';

interface ThreeDotMenuProps {
  isOpen: boolean;
  onClose: () => void;
  isDesktopMode: boolean;
  onToggleDesktopMode: () => void;
  onNewTab: (incognito?: boolean) => void;
  onOpenHistory: () => void;
  onOpenDownloads: () => void;
  onOpenBookmarks: () => void;
  onOpenShield: () => void;
  onOpenFindInPage: () => void;
  onShare: () => void;
  onOpenSettings: () => void;
  onOpenArchitecture: () => void;
  isBookmarked: boolean;
  onToggleBookmark: () => void;
}

export const ThreeDotMenu: React.FC<ThreeDotMenuProps> = ({
  isOpen,
  onClose,
  isDesktopMode,
  onToggleDesktopMode,
  onNewTab,
  onOpenHistory,
  onOpenDownloads,
  onOpenBookmarks,
  onOpenShield,
  onOpenFindInPage,
  onShare,
  onOpenSettings,
  onOpenArchitecture,
  isBookmarked,
  onToggleBookmark
}) => {
  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-50 flex justify-end"
      onClick={onClose}
    >
      <div 
        className="relative w-72 max-w-[85vw] mt-12 mr-3 bg-[#0E1726]/95 border border-white/10 rounded-2xl shadow-2xl p-2 animate-in fade-in zoom-in-95 duration-150 backdrop-blur-xl flex flex-col space-y-1 text-xs text-slate-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Quick Top Strip: Bookmark, Download, Info */}
        <div className="flex items-center justify-between px-2 py-1.5 border-b border-white/5 mb-1 text-slate-400">
          <button
            onClick={() => {
              onToggleBookmark();
              onClose();
            }}
            className={`p-2 rounded-xl transition-colors cursor-pointer ${
              isBookmarked ? 'text-amber-400 bg-amber-400/10' : 'hover:text-white hover:bg-white/5'
            }`}
            title={isBookmarked ? 'Remove Bookmark' : 'Bookmark Page'}
          >
            <Bookmark className={`w-4 h-4 ${isBookmarked ? 'fill-amber-400' : ''}`} />
          </button>

          <button
            onClick={() => {
              onOpenDownloads();
              onClose();
            }}
            className="p-2 rounded-xl hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Downloads"
          >
            <Download className="w-4 h-4" />
          </button>

          <button
            onClick={() => {
              onOpenShield();
              onClose();
            }}
            className="p-2 rounded-xl text-cyan-400 hover:text-cyan-300 hover:bg-cyan-500/10 transition-colors cursor-pointer"
            title="Orbit Shield"
          >
            <ShieldCheck className="w-4 h-4" />
          </button>

          <button
            onClick={onClose}
            className="p-2 rounded-xl hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 1. New Tab / New Incognito Tab */}
        <button
          onClick={() => {
            onNewTab(false);
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Plus className="w-4 h-4 text-slate-400" />
          <span className="font-medium">New tab</span>
        </button>

        <button
          onClick={() => {
            onNewTab(true);
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer text-purple-300"
        >
          <EyeOff className="w-4 h-4 text-purple-400" />
          <span className="font-medium">New incognito tab</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 2. History & Bookmarks */}
        <button
          onClick={() => {
            onOpenHistory();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <History className="w-4 h-4 text-slate-400" />
            <span>History</span>
          </div>
          <span className="text-[10px] font-mono text-cyan-400">Cloud Sync</span>
        </button>

        <button
          onClick={() => {
            onOpenBookmarks();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <Bookmark className="w-4 h-4 text-slate-400" />
            <span>Bookmarks</span>
          </div>
          <span className="text-[10px] font-mono text-cyan-400">Cloud Sync</span>
        </button>

        <button
          onClick={() => {
            onOpenDownloads();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Download className="w-4 h-4 text-slate-400" />
          <span>Downloads</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 3. Desktop Site Toggle */}
        <button
          onClick={onToggleDesktopMode}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <Monitor className="w-4 h-4 text-slate-400" />
            <span>Desktop site</span>
          </div>
          <div className={`w-4 h-4 rounded border flex items-center justify-center transition-colors ${
            isDesktopMode ? 'bg-cyan-500 border-cyan-400' : 'border-white/20'
          }`}>
            {isDesktopMode && <Check className="w-3 h-3 text-slate-950 stroke-[3]" />}
          </div>
        </button>

        {/* 4. Orbit Shield & Privacy Settings */}
        <button
          onClick={() => {
            onOpenShield();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <ShieldCheck className="w-4 h-4 text-cyan-400" />
            <span>Orbit Shield</span>
          </div>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-cyan-950 text-cyan-300 font-mono">Brave-Std</span>
        </button>

        {/* 5. Find in page & Share */}
        <button
          onClick={() => {
            onOpenFindInPage();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Search className="w-4 h-4 text-slate-400" />
          <span>Find in page</span>
        </button>

        <button
          onClick={() => {
            onShare();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Share2 className="w-4 h-4 text-slate-400" />
          <span>Share...</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 6. Settings & Architecture Inspector */}
        <button
          onClick={() => {
            onOpenSettings();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Settings className="w-4 h-4 text-slate-400" />
          <span>Settings</span>
        </button>

        <button
          onClick={() => {
            onOpenArchitecture();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer text-cyan-300 font-mono text-[11px]"
        >
          <div className="flex items-center gap-3">
            <Code2 className="w-4 h-4 text-cyan-400" />
            <span>Android / Native Specs</span>
          </div>
          <span className="text-[10px] text-slate-400">CI/CD</span>
        </button>
      </div>
    </div>
  );
};
