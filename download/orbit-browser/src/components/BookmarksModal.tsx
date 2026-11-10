import React, { useState } from 'react';
import { Bookmark, Plus, Trash2, ExternalLink, X, Search, Cloud, CloudOff, Folder } from 'lucide-react';
import { BookmarkItem } from '../types/browser';

interface BookmarksModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookmarks: BookmarkItem[];
  onSelectBookmark: (url: string) => void;
  onRemoveBookmark: (id: string) => void;
  onAddCurrentPage: () => void;
  currentPageTitle: string;
  currentPageUrl: string;
  isLoggedIn: boolean;
}

export const BookmarksModal: React.FC<BookmarksModalProps> = ({
  isOpen,
  onClose,
  bookmarks,
  onSelectBookmark,
  onRemoveBookmark,
  onAddCurrentPage,
  currentPageTitle,
  currentPageUrl,
  isLoggedIn
}) => {
  const [search, setSearch] = useState('');

  if (!isOpen) return null;

  const filtered = bookmarks.filter(b => 
    b.title.toLowerCase().includes(search.toLowerCase()) ||
    b.url.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <Bookmark className="w-5 h-5 fill-amber-400/20" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Bookmarks</span>
                {isLoggedIn ? (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-cyan-400 bg-cyan-950/80 px-2 py-0.5 rounded-full border border-cyan-800">
                    <Cloud className="w-3 h-3" />
                    <span>Firestore Synced</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-slate-400 bg-slate-800 px-2 py-0.5 rounded-full">
                    <CloudOff className="w-3 h-3" />
                    <span>Local Device</span>
                  </span>
                )}
              </h3>
              <p className="text-xs text-slate-400">Encrypted Cloud Storage</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Add Current Page Button */}
        {currentPageUrl && currentPageUrl !== 'orbit://newtab' && (
          <div className="py-3">
            <button
              onClick={onAddCurrentPage}
              className="w-full py-2.5 px-4 rounded-xl bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/30 text-cyan-300 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer"
            >
              <Plus className="w-4 h-4 text-cyan-400" />
              <span>Bookmark "{currentPageTitle || currentPageUrl}"</span>
            </button>
          </div>
        )}

        {/* Search */}
        <div className="relative my-2">
          <Search className="absolute left-3.5 top-2.5 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search bookmarks..."
            className="w-full pl-10 pr-4 py-2 text-xs bg-[#070D19] border border-white/10 rounded-xl text-white placeholder-slate-500 focus:outline-hidden focus:border-cyan-400"
          />
        </div>

        {/* Bookmarks List */}
        <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 py-2">
          {filtered.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-500 space-y-2">
              <Folder className="w-8 h-8 mx-auto text-slate-600" />
              <p>No bookmarks saved yet.</p>
              <p className="text-[11px] text-slate-600">Bookmark your favorite pages from the 3-dot menu or omnibox.</p>
            </div>
          ) : (
            filtered.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors group cursor-pointer"
                onClick={() => {
                  onSelectBookmark(item.url);
                  onClose();
                }}
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <Bookmark className="w-4 h-4 text-amber-400 shrink-0 fill-amber-400/20" />
                  <div className="truncate">
                    <h4 className="text-xs font-semibold text-white truncate group-hover:text-cyan-300 transition-colors">
                      {item.title}
                    </h4>
                    <p className="text-[11px] font-mono text-slate-400 truncate">
                      {item.url}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-1 shrink-0 ml-2">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onRemoveBookmark(item.id);
                    }}
                    className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                    title="Delete"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
