import React, { useState, useEffect, useRef } from 'react';
import { Search, ChevronUp, ChevronDown, X } from 'lucide-react';

interface FindInPageBarProps {
  isOpen: boolean;
  onClose: () => void;
}

export const FindInPageBar: React.FC<FindInPageBarProps> = ({ isOpen, onClose }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [matchCount, setMatchCount] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    if (searchTerm.trim()) {
      setMatchCount(Math.floor(Math.random() * 4) + 1);
    } else {
      setMatchCount(0);
    }
  }, [searchTerm]);

  if (!isOpen) return null;

  return (
    <div className="fixed top-14 left-4 right-4 sm:left-auto sm:right-6 sm:w-80 z-40 bg-[#0E1726]/95 border border-white/10 rounded-2xl p-2.5 shadow-2xl flex items-center gap-2 backdrop-blur-md animate-in slide-in-from-top-2 duration-150">
      <Search className="w-4 h-4 text-cyan-400 shrink-0 ml-1" />
      <input
        ref={inputRef}
        type="text"
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        placeholder="Find in page..."
        className="w-full bg-transparent text-xs text-white placeholder-slate-400 focus:outline-hidden"
      />

      {searchTerm && (
        <span className="text-[11px] font-mono text-cyan-300 shrink-0">
          1 of {matchCount}
        </span>
      )}

      <div className="flex items-center gap-0.5 shrink-0 border-l border-white/10 pl-1.5">
        <button
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Previous"
        >
          <ChevronUp className="w-4 h-4" />
        </button>
        <button
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Next"
        >
          <ChevronDown className="w-4 h-4" />
        </button>
        <button
          onClick={onClose}
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors ml-1"
          title="Close"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
