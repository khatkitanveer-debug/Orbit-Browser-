import React, { useState, useEffect } from 'react';
import { Play, Pause, SkipForward, SkipBack, Lock, Unlock, Radio, Shield } from 'lucide-react';
import { MediaState } from '../types/browser';

interface LockScreenOverlayProps {
  isOpen: boolean;
  onUnlock: () => void;
  mediaState: MediaState;
  onTogglePlay: () => void;
  onNextTrack: () => void;
  onPrevTrack: () => void;
}

export const LockScreenOverlay: React.FC<LockScreenOverlayProps> = ({
  isOpen,
  onUnlock,
  mediaState,
  onTogglePlay,
  onNextTrack,
  onPrevTrack
}) => {
  const [time, setTime] = useState('');
  const [date, setDate] = useState('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
      setDate(now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-2xl flex flex-col justify-between p-6 sm:p-10 select-none text-white animate-in fade-in duration-300">
      {/* Top Status */}
      <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
        <div className="flex items-center gap-1.5">
          <Shield className="w-3.5 h-3.5 text-cyan-400" />
          <span>Orbit OS</span>
        </div>
        <div className="flex items-center gap-2">
          <span>100%</span>
          <span className="w-4 h-2.5 rounded-xs border border-slate-400 relative">
            <span className="absolute inset-0.5 bg-emerald-400 rounded-2xs" />
          </span>
        </div>
      </div>

      {/* Center Clock */}
      <div className="text-center my-auto space-y-2">
        <div className="text-5xl sm:text-7xl font-extralight tracking-tight font-mono text-slate-100">
          {time || '12:00'}
        </div>
        <div className="text-sm font-medium text-slate-400 tracking-wide">
          {date || 'Today'}
        </div>

        {/* Lock Screen Notification Media Card (Android MediaStyle Notification) */}
        <div className="max-w-md mx-auto mt-8 bg-[#0E1726]/90 border border-cyan-500/30 rounded-3xl p-5 shadow-2xl text-left space-y-3.5 backdrop-blur-md">
          <div className="flex items-center justify-between text-xs text-cyan-400 font-mono">
            <div className="flex items-center gap-2">
              <Radio className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
              <span>Orbit Background Audio Engine</span>
            </div>
            <span className="text-emerald-400">Playing while screen locked</span>
          </div>

          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-cyan-600 to-sky-400 flex items-center justify-center shrink-0 shadow-lg">
              <Radio className="w-6 h-6 text-white" />
            </div>

            <div className="flex-1 truncate">
              <h4 className="text-sm font-bold text-white truncate">{mediaState.title}</h4>
              <p className="text-xs text-slate-400 truncate mt-0.5">{mediaState.artist}</p>
            </div>
          </div>

          {/* Android Notification Transport Buttons */}
          <div className="flex items-center justify-center gap-4 pt-2 border-t border-white/5">
            <button
              onClick={onPrevTrack}
              className="p-2 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            >
              <SkipBack className="w-5 h-5" />
            </button>

            <button
              onClick={onTogglePlay}
              className="p-3 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 transition-colors shadow-lg cursor-pointer"
            >
              {mediaState.isPlaying ? (
                <Pause className="w-5 h-5 fill-current" />
              ) : (
                <Play className="w-5 h-5 fill-current ml-0.5" />
              )}
            </button>

            <button
              onClick={onNextTrack}
              className="p-2 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            >
              <SkipForward className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* Bottom Unlock Prompt */}
      <div className="text-center pb-4">
        <button
          onClick={onUnlock}
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 text-xs font-medium text-slate-200 transition-all cursor-pointer shadow-lg hover:scale-105"
        >
          <Unlock className="w-3.5 h-3.5 text-cyan-400" />
          <span>Swipe or Click to Unlock Orbit</span>
        </button>
      </div>
    </div>
  );
};
