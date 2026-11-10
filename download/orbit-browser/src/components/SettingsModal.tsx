import React from 'react';
import { Settings, Search, User, LogIn, LogOut, Check, X, Shield, Radio, Monitor, Trash2 } from 'lucide-react';
import { UserPreferences } from '../types/browser';

import { EccKeyGenerator } from './EccKeyGenerator';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  preferences: UserPreferences;
  onUpdatePreferences: (updated: Partial<UserPreferences>) => void;
  user: any;
  onLogin: () => void;
  onLogout: () => void;
  onClearAllData: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  preferences,
  onUpdatePreferences,
  user,
  onLogin,
  onLogout,
  onClearAllData
}) => {
  if (!isOpen) return null;

  const engines = [
    { id: 'duckduckgo', name: 'DuckDuckGo', desc: 'Strict Privacy, zero search tracking' },
    { id: 'brave', name: 'Brave Search', desc: 'Independent index, tracker-free' },
    { id: 'startpage', name: 'Startpage', desc: 'Google results with privacy barrier' },
    { id: 'google', name: 'Google Search', desc: 'Standard search engine' }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh] space-y-5"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Orbit Settings</h3>
              <p className="text-xs text-slate-400">Chromium & Cloud Sync Configuration</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Settings Content */}
        <div className="flex-1 overflow-y-auto space-y-5 pr-1 text-xs">
          {/* Firebase Authentication & Cloud Sync */}
          <div className="p-4 rounded-2xl bg-cyan-950/30 border border-cyan-500/20 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-cyan-400/10 flex items-center justify-center text-cyan-400">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="font-semibold text-white">Firebase Cloud Account</h4>
                  <p className="text-[11px] text-slate-400">Sync bookmarks & history across devices</p>
                </div>
              </div>

              {user ? (
                <button
                  onClick={onLogout}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors flex items-center gap-1.5 cursor-pointer"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Sign Out</span>
                </button>
              ) : (
                <button
                  onClick={onLogin}
                  className="px-3 py-1.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold transition-colors flex items-center gap-1.5 cursor-pointer shadow-xs"
                >
                  <LogIn className="w-3.5 h-3.5" />
                  <span>Google Sync</span>
                </button>
              )}
            </div>

            {user && (
              <div className="pt-2 border-t border-white/5 flex items-center justify-between font-mono text-[11px] text-emerald-400">
                <span>Logged in: {user.email || user.displayName}</span>
                <span>Firestore Active</span>
              </div>
            )}
          </div>

          {/* Search Engine Selection */}
          <div className="space-y-2">
            <label className="font-semibold text-slate-200 flex items-center gap-2">
              <Search className="w-3.5 h-3.5 text-cyan-400" />
              <span>Default Search Engine</span>
            </label>
            <div className="space-y-1.5">
              {engines.map((eng) => {
                const isSelected = preferences.searchEngine === eng.id;
                return (
                  <button
                    key={eng.id}
                    onClick={() => onUpdatePreferences({ searchEngine: eng.id as any })}
                    className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-colors cursor-pointer ${
                      isSelected
                        ? 'border-cyan-400/80 bg-cyan-950/20 text-white'
                        : 'border-white/5 bg-white/5 hover:bg-white/10 text-slate-300'
                    }`}
                  >
                    <div>
                      <div className="font-semibold text-xs text-white">{eng.name}</div>
                      <div className="text-[11px] text-slate-400">{eng.desc}</div>
                    </div>
                    {isSelected && <Check className="w-4 h-4 text-cyan-400" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Local ECC Cryptographic Key Generator */}
          <EccKeyGenerator
            isLoggedIn={!!user}
            onSavedToProfile={(fingerprint) => {
              console.log('Generated ECC Key fingerprint:', fingerprint);
            }}
          />

          {/* Toggles */}
          <div className="space-y-2.5">
            <label className="font-semibold text-slate-200">Browsing Preferences</label>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Background Media Playback</span>
                <p className="text-[11px] text-slate-400">Keep audio playing when switching tabs or locking screen</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ backgroundPlaybackEnabled: !preferences.backgroundPlaybackEnabled })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.backgroundPlaybackEnabled ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.backgroundPlaybackEnabled ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Desktop Mode Default</span>
                <p className="text-[11px] text-slate-400">Request desktop user-agent for all pages</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ desktopModeDefault: !preferences.desktopModeDefault })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.desktopModeDefault ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.desktopModeDefault ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Clear Data on Exit</span>
                <p className="text-[11px] text-slate-400">Purge tabs, cache, and session cookies when closing</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ clearOnExit: !preferences.clearOnExit })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.clearOnExit ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.clearOnExit ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Reset / Purge */}
          <div className="pt-2">
            <button
              onClick={onClearAllData}
              className="w-full py-2.5 px-4 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer"
            >
              <Trash2 className="w-4 h-4 text-rose-400" />
              <span>Purge All Local Cache & Reset Orbit</span>
            </button>
          </div>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold text-xs transition-colors cursor-pointer"
        >
          Save & Close
        </button>
      </div>
    </div>
  );
};
