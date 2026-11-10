import React from 'react';
import { Shield, ShieldAlert, Lock, Fingerprint, EyeOff, X, Zap } from 'lucide-react';
import { ShieldStats } from '../types/browser';

interface OrbitShieldModalProps {
  isOpen: boolean;
  onClose: () => void;
  stats: ShieldStats;
  shieldEnabled: boolean;
  onToggleShield: () => void;
  httpsEverywhere: boolean;
  onToggleHttps: () => void;
  fingerprintProtection: boolean;
  onToggleFingerprint: () => void;
}

export const OrbitShieldModal: React.FC<OrbitShieldModalProps> = ({
  isOpen,
  onClose,
  stats,
  shieldEnabled,
  onToggleShield,
  httpsEverywhere,
  onToggleHttps,
  fingerprintProtection,
  onToggleFingerprint
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-md bg-[#0E1726] border border-cyan-500/30 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-200 text-slate-200 space-y-6"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Shield className="w-5 h-5 fill-cyan-400/20" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Orbit Shield</span>
                <span className="text-[10px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1.5 py-0.5 rounded border border-cyan-800">
                  Brave-Std
                </span>
              </h3>
              <p className="text-xs text-slate-400">Blink/Chromium Network Interceptor</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Master Shield Toggle */}
        <div className="p-4 rounded-2xl bg-cyan-950/30 border border-cyan-500/20 flex items-center justify-between">
          <div className="space-y-0.5">
            <span className="text-sm font-semibold text-white">Shield Protection</span>
            <p className="text-xs text-slate-400">Blocks trackers, ads & fingerprinting</p>
          </div>
          <button
            onClick={onToggleShield}
            className={`w-12 h-6 rounded-full transition-colors relative cursor-pointer ${
              shieldEnabled ? 'bg-cyan-500' : 'bg-slate-700'
            }`}
          >
            <span
              className={`absolute top-1 left-1 w-4 h-4 rounded-full bg-white transition-transform ${
                shieldEnabled ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Live Protection Stats Strip */}
        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-cyan-400 tabular-nums">
              {stats.trackersBlocked}
            </span>
            <div className="text-[11px] text-slate-400">Trackers Blocked</div>
          </div>

          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-emerald-400 tabular-nums">
              {stats.httpsUpgrades}
            </span>
            <div className="text-[11px] text-slate-400">HTTPS Upgrades</div>
          </div>

          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-amber-400 tabular-nums">
              {stats.bandwidthSavedKb} KB
            </span>
            <div className="text-[11px] text-slate-400">Data Saved</div>
          </div>
        </div>

        {/* Granular Privacy Toggles */}
        <div className="space-y-3 text-xs">
          <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
            <div className="flex items-center gap-2.5">
              <Lock className="w-4 h-4 text-emerald-400" />
              <div>
                <span className="font-medium text-slate-200">Force HTTPS Everywhere</span>
                <p className="text-[11px] text-slate-400">Automatically upgrades unencrypted HTTP connections</p>
              </div>
            </div>
            <button
              onClick={onToggleHttps}
              className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                httpsEverywhere ? 'bg-emerald-500' : 'bg-slate-700'
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                  httpsEverywhere ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
            <div className="flex items-center gap-2.5">
              <Fingerprint className="w-4 h-4 text-cyan-400" />
              <div>
                <span className="font-medium text-slate-200">Anti-Fingerprinting Guard</span>
                <p className="text-[11px] text-slate-400">Canvas & AudioContext noise randomization</p>
              </div>
            </div>
            <button
              onClick={onToggleFingerprint}
              className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                fingerprintProtection ? 'bg-cyan-500' : 'bg-slate-700'
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                  fingerprintProtection ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center gap-2.5 p-3 rounded-xl bg-white/5 text-slate-400">
            <EyeOff className="w-4 h-4 text-purple-400 shrink-0" />
            <div className="text-[11px]">
              <strong className="text-slate-200">Zero Telemetry Architecture:</strong> No browsing metrics, search terms, or telemetry logs are ever transmitted to third parties. Logs are purged on tab close.
            </div>
          </div>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold text-xs transition-colors cursor-pointer"
        >
          Done
        </button>
      </div>
    </div>
  );
};
