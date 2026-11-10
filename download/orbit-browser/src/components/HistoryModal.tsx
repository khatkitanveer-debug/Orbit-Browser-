import React, { useState } from 'react';
import { History, Trash2, Search, X, Cloud, CloudOff, Globe, Clock } from 'lucide-react';
import { HistoryItem } from '../types/browser';

interface HistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  history: HistoryItem[];
  onSelectHistory: (url: string) => void;
  onClearHistory: () => void;
  isLoggedIn: boolean;
}

export const HistoryModal: React.FC<HistoryModalProps> = ({
  isOpen,
  onClose,
  history,
  onSelectHistory,
  onClearHistory,
  isLoggedIn
}) => {
  const [search, setSearch] = useState('');

  if (!isOpen) return null;

  const filtered = history.filter(h => 
    h.title.toLowerCase().includes(search.toLowerCase()) ||
    h.url.toLowerCase().includes(search.toLowerCase())
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
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <History className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Browsing History</span>
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
              <p className="text-xs text-slate-400">Encrypted Cloud History</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Search & Clear Bar */}
        <div className="flex items-center gap-2 my-3">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-2.5 w-4 h-4 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search history..."
              className="w-full pl-10 pr-4 py-2 text-xs bg-[#070D19] border border-white/10 rounded-xl text-white placeholder-slate-500 focus:outline-hidden focus:border-cyan-400"
            />
          </div>

          {history.length > 0 && (
            <button
              onClick={onClearHistory}
              className="px-3 py-2 text-xs font-semibold text-rose-400 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 rounded-xl transition-colors cursor-pointer shrink-0"
            >
              Clear Data
            </button>
          )}
        </div>

        {/* History List */}
        <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 py-1">
          {filtered.length === 0 ? (
            <div className="py-16 text-center text-xs text-slate-500 space-y-2">
              <Clock className="w-8 h-8 mx-auto text-slate-600" />
              <p>No browsing history records.</p>
              <p className="text-[11px] text-slate-600">Visited web pages appear here automatically.</p>
            </div>
          ) : (
            filtered.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors group cursor-pointer"
                onClick={() => {
                  onSelectHistory(item.url);
                  onClose();
                }}
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <Globe className="w-4 h-4 text-slate-400 shrink-0" />
                  <div className="truncate">
                    <h4 className="text-xs font-semibold text-white truncate group-hover:text-cyan-300 transition-colors">
                      {item.title}
                    </h4>
                    <p className="text-[11px] font-mono text-slate-400 truncate">
                      {item.url}
                    </p>
                  </div>
                </div>

                <div className="text-[11px] font-mono text-slate-500 shrink-0 ml-3">
                  {new Date(item.visitedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
