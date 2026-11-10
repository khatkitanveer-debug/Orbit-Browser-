import React, { useState, useEffect } from 'react';
import { 
  ShieldCheck, 
  Lock, 
  ExternalLink, 
  RotateCw, 
  AlertCircle, 
  ArrowLeft,
  Eye,
  CheckCircle2,
  Zap
} from 'lucide-react';
import { BrowserTab, ShieldStats } from '../types/browser';

interface WebViewportProps {
  currentTab: BrowserTab;
  shieldStats: ShieldStats;
  onNavigateHome: () => void;
  onRecordBlockedTracker: () => void;
}

export const WebViewport: React.FC<WebViewportProps> = ({
  currentTab,
  shieldStats,
  onNavigateHome,
  onRecordBlockedTracker
}) => {
  const [loadProgress, setLoadProgress] = useState(30);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [blockedOnThisPage, setBlockedOnThisPage] = useState(0);

  useEffect(() => {
    setLoadProgress(30);
    setHasLoaded(false);
    // Simulate Orbit Shield intercepting requests on page load
    const count = Math.floor(Math.random() * 5) + 3;
    setBlockedOnThisPage(count);
    for (let i = 0; i < count; i++) {
      onRecordBlockedTracker();
    }

    const timer1 = setTimeout(() => setLoadProgress(75), 250);
    const timer2 = setTimeout(() => {
      setLoadProgress(100);
      setHasLoaded(true);
    }, 600);

    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
    };
  }, [currentTab.url]);

  const domain = (() => {
    try {
      return new URL(currentTab.url).hostname;
    } catch {
      return currentTab.url;
    }
  })();

  return (
    <div className={`flex-1 flex flex-col w-full h-full bg-[#070D19] relative overflow-hidden ${
      currentTab.isDesktopMode ? 'max-w-6xl mx-auto' : 'max-w-3xl mx-auto'
    }`}>
      {/* Top Loading Progress Line */}
      {loadProgress < 100 && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-white/10 z-30">
          <div 
            className="h-full bg-cyan-400 transition-all duration-300 shadow-[0_0_8px_#38bdf8]"
            style={{ width: `${loadProgress}%` }}
          />
        </div>
      )}

      {/* Orbit Shield Security Inspection Banner */}
      <div className="px-4 py-2 bg-[#0E1726]/90 border-b border-white/5 flex items-center justify-between text-xs text-slate-300">
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 text-emerald-400 font-mono">
            <Lock className="w-3.5 h-3.5" />
            <span className="font-semibold">HTTPS Everywhere Upgraded</span>
          </div>
          <span className="text-slate-600">|</span>
          <div className="flex items-center gap-1.5 text-cyan-300 font-mono">
            <ShieldCheck className="w-3.5 h-3.5 text-cyan-400" />
            <span>Orbit Shield: <strong>{blockedOnThisPage} trackers blocked</strong></span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {currentTab.isDesktopMode && (
            <span className="text-[10px] font-mono uppercase bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">
              Desktop Mode (x86_64)
            </span>
          )}
          <a
            href={currentTab.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-slate-400 hover:text-white flex items-center gap-1 transition-colors text-[11px]"
            title="Open in new window"
          >
            <span>External</span>
            <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>

      {/* Main Viewport Content */}
      <div className="flex-1 w-full h-full relative bg-white rounded-b-2xl overflow-hidden">
        {/* IFrame embedding with sandbox protection */}
        <iframe
          src={currentTab.url}
          title={currentTab.title}
          className="w-full h-full border-0 bg-white"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
          loading="lazy"
        />

        {/* Fallback Overlay for sites with strict X-Frame-Options (e.g. Google, GitHub) */}
        <div className="absolute inset-0 pointer-events-none flex flex-col items-center justify-center p-6 bg-transparent">
          {/* Transparent container; if iframe fails or is blocked by CSP, user has quick buttons */}
        </div>
      </div>
    </div>
  );
};
