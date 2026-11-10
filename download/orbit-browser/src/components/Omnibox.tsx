import React, { useState, useRef, useEffect } from 'react';
import { Search, Globe, ArrowRight, X, Shield, Lock, Sparkles } from 'lucide-react';

interface OmniboxProps {
  onNavigate: (url: string) => void;
  searchEngine: 'duckduckgo' | 'brave' | 'google' | 'startpage';
  shieldBlockedCount: number;
  autoFocus?: boolean;
}

export const Omnibox: React.FC<OmniboxProps> = ({
  onNavigate,
  searchEngine,
  shieldBlockedCount,
  autoFocus = true
}) => {
  const [query, setQuery] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus && inputRef.current) {
      inputRef.current.focus();
    }
  }, [autoFocus]);

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;

    let targetUrl = '';
    // Check if input is a valid URL or domain
    const isUrl = /^((https?:\/\/)?([a-z0-9-]+\.)+[a-z]{2,}(:\d+)?(\/.*)?)|(localhost(:\d+)?)/i.test(trimmed);

    if (isUrl) {
      targetUrl = trimmed.startsWith('http://') || trimmed.startsWith('https://') 
        ? trimmed 
        : `https://${trimmed}`;
    } else {
      // Route through selected private search engine
      switch (searchEngine) {
        case 'brave':
          targetUrl = `https://search.brave.com/search?q=${encodeURIComponent(trimmed)}`;
          break;
        case 'google':
          targetUrl = `https://www.google.com/search?q=${encodeURIComponent(trimmed)}`;
          break;
        case 'startpage':
          targetUrl = `https://www.startpage.com/sp/search?query=${encodeURIComponent(trimmed)}`;
          break;
        case 'duckduckgo':
        default:
          targetUrl = `https://duckduckgo.com/?q=${encodeURIComponent(trimmed)}`;
          break;
      }
    }

    onNavigate(targetUrl);
  };

  const handleSuggestionClick = (url: string) => {
    onNavigate(url);
  };

  return (
    <div className="w-full max-w-xl mx-auto relative">
      <form onSubmit={handleSubmit} className="relative w-full">
        <div
          className={`relative flex items-center w-full px-5 py-3.5 rounded-full border transition-all duration-300 ${
            isFocused
              ? 'bg-[#162238] border-cyan-400/80 shadow-[0_0_30px_rgba(56,189,248,0.25)]'
              : 'bg-[#162238] hover:bg-[#1C2C47] border-[#243452] hover:border-cyan-500/40 shadow-lg'
          }`}
        >
          {/* Prefix Search / Globe Icon */}
          <div className="flex items-center gap-2 mr-3 text-cyan-400 shrink-0">
            {query.includes('.') ? (
              <Globe className="w-4 h-4" />
            ) : (
              <Search className="w-4 h-4" />
            )}
          </div>

          {/* Omnibox Input */}
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setTimeout(() => setIsFocused(false), 200)}
            placeholder="Search with DuckDuckGo or enter URL"
            className="w-full bg-transparent text-sm text-white placeholder-slate-400 focus:outline-hidden font-normal"
            autoComplete="off"
            spellCheck="false"
          />

          {/* Action Buttons */}
          <div className="flex items-center gap-1.5 ml-2 shrink-0">
            {query && (
              <button
                type="button"
                onClick={() => setQuery('')}
                className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                title="Clear"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}

            {/* Orbit Shield Indicator */}
            <div 
              className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-cyan-950/70 border border-cyan-500/30 text-[11px] text-cyan-300 font-mono"
              title="Orbit Shield: Active Protection"
            >
              <Shield className="w-3 h-3 text-cyan-400 fill-cyan-400/20" />
              <span>{shieldBlockedCount}</span>
            </div>

            <button
              type="submit"
              disabled={!query.trim()}
              className="p-1.5 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 disabled:opacity-30 disabled:hover:bg-cyan-400 transition-colors cursor-pointer"
              title="Navigate"
            >
              <ArrowRight className="w-3.5 h-3.5 stroke-[2.5]" />
            </button>
          </div>
        </div>
      </form>

      {/* Suggested Fast Destinations Dropdown when focused */}
      {isFocused && (
        <div className="absolute top-full left-0 right-0 mt-2 p-2 bg-[#0E1726] border border-cyan-500/20 rounded-2xl shadow-2xl z-30 animate-in fade-in slide-in-from-top-2 duration-150 backdrop-blur-md">
          <div className="text-[11px] font-mono text-slate-400 px-3 py-1.5 flex items-center justify-between">
            <span>Direct Jump / Quick Destination</span>
            <span className="text-cyan-400">Encrypted</span>
          </div>
          <div className="space-y-0.5">
            {[
              { label: 'DuckDuckGo Privacy Search', url: 'https://duckduckgo.com', icon: Lock },
              { label: 'Brave Search Engine', url: 'https://search.brave.com', icon: Shield },
              { label: 'Wikipedia Encyclopedia', url: 'https://wikipedia.org', icon: Globe },
              { label: 'GitHub Open Source Hub', url: 'https://github.com', icon: Sparkles }
            ].map((dest) => {
              const Icon = dest.icon;
              return (
                <button
                  key={dest.url}
                  onClick={() => handleSuggestionClick(dest.url)}
                  className="w-full flex items-center justify-between px-3 py-2 text-xs text-slate-200 hover:text-white hover:bg-white/5 rounded-xl transition-colors cursor-pointer text-left"
                >
                  <div className="flex items-center gap-2.5">
                    <Icon className="w-3.5 h-3.5 text-cyan-400" />
                    <span>{dest.label}</span>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400 truncate max-w-[150px]">
                    {dest.url}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
