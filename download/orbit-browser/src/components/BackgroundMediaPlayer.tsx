import React, { useState, useEffect, useRef } from 'react';
import { 
  Play, 
  Pause, 
  SkipForward, 
  SkipBack, 
  Volume2, 
  VolumeX, 
  X, 
  Radio, 
  Lock,
  Smartphone,
  ExternalLink
} from 'lucide-react';
import { MediaState } from '../types/browser';

interface BackgroundMediaPlayerProps {
  mediaState: MediaState;
  onTogglePlay: () => void;
  onNextTrack: () => void;
  onPrevTrack: () => void;
  onToggleBackgroundPlayback: () => void;
  onSimulateLockScreen: () => void;
  onClosePlayer: () => void;
}

export const BackgroundMediaPlayer: React.FC<BackgroundMediaPlayerProps> = ({
  mediaState,
  onTogglePlay,
  onNextTrack,
  onPrevTrack,
  onToggleBackgroundPlayback,
  onSimulateLockScreen,
  onClosePlayer
}) => {
  const [isMuted, setIsMuted] = useState(false);
  const audioRef = useRef<HTMLAudioElement>(null);

  // Setup navigator.mediaSession if supported
  useEffect(() => {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: mediaState.title,
        artist: mediaState.artist,
        album: 'Orbit Chromium Background Engine',
        artwork: [
          { src: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=128&auto=format&fit=crop&q=80', sizes: '128x128', type: 'image/jpeg' }
        ]
      });

      navigator.mediaSession.setActionHandler('play', onTogglePlay);
      navigator.mediaSession.setActionHandler('pause', onTogglePlay);
      navigator.mediaSession.setActionHandler('nexttrack', onNextTrack);
      navigator.mediaSession.setActionHandler('previoustrack', onPrevTrack);
    }
  }, [mediaState.title, mediaState.artist]);

  // Sync actual HTML5 audio element
  useEffect(() => {
    if (audioRef.current) {
      if (mediaState.isPlaying) {
        audioRef.current.play().catch(() => {
          // Handled if browser policies require user interaction
        });
      } else {
        audioRef.current.pause();
      }
    }
  }, [mediaState.isPlaying, mediaState.audioUrl]);

  return (
    <div className="fixed bottom-3 left-3 right-3 sm:left-auto sm:right-6 sm:w-96 z-40 bg-[#0E1726]/95 border border-cyan-500/30 rounded-2xl p-3.5 shadow-2xl backdrop-blur-xl text-slate-200 animate-in slide-in-from-bottom-3 duration-200">
      {/* Hidden background audio stream element */}
      <audio
        ref={audioRef}
        src={mediaState.audioUrl}
        loop
        muted={isMuted}
        playsInline
      />

      <div className="flex items-center justify-between gap-3">
        {/* Track Info with pulsating beacon */}
        <div className="flex items-center gap-3 truncate flex-1">
          <div className="relative w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-600 to-sky-400 flex items-center justify-center shrink-0 shadow-md">
            <Radio className="w-5 h-5 text-white" />
            {mediaState.isPlaying && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-emerald-400 ring-2 ring-[#0E1726] animate-ping" />
            )}
          </div>
          <div className="truncate">
            <h4 className="text-xs font-semibold text-white truncate flex items-center gap-1.5">
              <span>{mediaState.title}</span>
              <span className="text-[9px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1 py-0.2 rounded border border-cyan-800 shrink-0">
                JioSphere-Mode
              </span>
            </h4>
            <p className="text-[11px] text-slate-400 truncate mt-0.5">
              {mediaState.artist}
            </p>
          </div>
        </div>

        {/* Transport Controls */}
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onPrevTrack}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Previous"
          >
            <SkipBack className="w-4 h-4" />
          </button>

          <button
            onClick={onTogglePlay}
            className="p-2 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 transition-colors cursor-pointer shadow-xs"
            title={mediaState.isPlaying ? 'Pause' : 'Play in Background'}
          >
            {mediaState.isPlaying ? (
              <Pause className="w-4 h-4 fill-current" />
            ) : (
              <Play className="w-4 h-4 fill-current ml-0.5" />
            )}
          </button>

          <button
            onClick={onNextTrack}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Next"
          >
            <SkipForward className="w-4 h-4" />
          </button>

          <button
            onClick={onClosePlayer}
            className="p-1.5 rounded-full text-slate-500 hover:text-white hover:bg-white/5 transition-colors ml-1 cursor-pointer"
            title="Hide Player"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Background Playback & Lock-Screen Test Strip */}
      <div className="mt-2.5 pt-2 border-t border-white/5 flex items-center justify-between text-[11px] text-slate-400">
        <div className="flex items-center gap-1 text-emerald-400 font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block animate-pulse" />
          <span>Background Audio Engine Active</span>
        </div>

        <button
          onClick={onSimulateLockScreen}
          className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-white/5 hover:bg-white/10 text-cyan-300 transition-colors cursor-pointer"
          title="Simulate Android Screen Lock / Minimize"
        >
          <Lock className="w-3 h-3 text-cyan-400" />
          <span>Simulate Lock Screen</span>
        </button>
      </div>
    </div>
  );
};
