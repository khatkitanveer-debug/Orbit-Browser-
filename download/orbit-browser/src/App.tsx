import React, { useState, useEffect } from 'react';
import { 
  BrowserTab, 
  BookmarkItem, 
  HistoryItem, 
  DownloadItem, 
  UserPreferences, 
  ShieldStats,
  MediaState 
} from './types/browser';
import { 
  auth, 
  loginWithGoogle, 
  logoutUser, 
  onAuthStateChanged,
  syncBookmarkToCloud,
  removeBookmarkFromCloud,
  syncHistoryItemToCloud,
  syncPreferencesToCloud
} from './lib/firebase';
import { AppBar } from './components/AppBar';
import { OrbitLogo } from './components/OrbitLogo';
import { Omnibox } from './components/Omnibox';
import { ThreeDotMenu } from './components/ThreeDotMenu';
import { OrbitShieldModal } from './components/OrbitShieldModal';
import { BackgroundMediaPlayer } from './components/BackgroundMediaPlayer';
import { LockScreenOverlay } from './components/LockScreenOverlay';
import { TabSwitcherModal } from './components/TabSwitcherModal';
import { BookmarksModal } from './components/BookmarksModal';
import { HistoryModal } from './components/HistoryModal';
import { DownloadsModal } from './components/DownloadsModal';
import { SettingsModal } from './components/SettingsModal';
import { ArchitectureModal } from './components/ArchitectureModal';
import { FindInPageBar } from './components/FindInPageBar';
import { WebViewport } from './components/WebViewport';

export default function App() {
  // Tabs State
  const [tabs, setTabs] = useState<BrowserTab[]>([
    {
      id: 'tab-1',
      title: 'New Tab',
      url: 'orbit://newtab',
      isIncognito: false,
      isDiscarded: false,
      isLoading: false,
      canGoBack: false,
      canGoForward: false,
      historyStack: ['orbit://newtab'],
      currentIndex: 0,
      isDesktopMode: false,
      lastActiveTime: Date.now()
    }
  ]);
  const [activeTabId, setActiveTabId] = useState<string>('tab-1');

  // Modals & Sheets State
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isShieldModalOpen, setIsShieldModalOpen] = useState(false);
  const [isTabSwitcherOpen, setIsTabSwitcherOpen] = useState(false);
  const [isBookmarksOpen, setIsBookmarksOpen] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isDownloadsOpen, setIsDownloadsOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isArchitectureOpen, setIsArchitectureOpen] = useState(false);
  const [isFindInPageOpen, setIsFindInPageOpen] = useState(false);
  const [isLockScreenOpen, setIsLockScreenOpen] = useState(false);
  const [isOmniboxModalOpen, setIsOmniboxModalOpen] = useState(false);

  // User & Firebase State
  const [user, setUser] = useState<any>(null);

  // User Preferences
  const [preferences, setPreferences] = useState<UserPreferences>({
    userId: 'guest',
    searchEngine: 'duckduckgo',
    orbitShieldEnabled: true,
    backgroundPlaybackEnabled: true,
    httpsEverywhere: true,
    doNotTrack: true,
    clearOnExit: false,
    desktopModeDefault: false,
    updatedAt: new Date().toISOString()
  });

  // Orbit Shield Telemetry & Stats
  const [shieldStats, setShieldStats] = useState<ShieldStats>({
    trackersBlocked: 42,
    adsBlocked: 18,
    httpsUpgrades: 7,
    bandwidthSavedKb: 840
  });
  const [fingerprintProtection, setFingerprintProtection] = useState(true);

  // Bookmarks, History & Downloads
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([
    {
      id: 'bm-1',
      userId: 'guest',
      title: 'DuckDuckGo Privacy Search',
      url: 'https://duckduckgo.com',
      createdAt: new Date().toISOString()
    },
    {
      id: 'bm-2',
      userId: 'guest',
      title: 'Chromium Projects & Blink Engine',
      url: 'https://www.chromium.org',
      createdAt: new Date().toISOString()
    }
  ]);

  const [history, setHistory] = useState<HistoryItem[]>([
    {
      id: 'h-1',
      userId: 'guest',
      title: 'Orbit New Tab',
      url: 'orbit://newtab',
      visitedAt: new Date().toISOString(),
      incognito: false
    }
  ]);

  const [downloads, setDownloads] = useState<DownloadItem[]>([
    {
      id: 'dl-1',
      userId: 'guest',
      fileName: 'orbit-chromium-v1.0.apk',
      url: 'https://orbit.browser/releases/orbit-chromium.apk',
      fileSize: '48.2 MB',
      status: 'completed',
      downloadedAt: new Date().toISOString()
    }
  ]);

  // Background Media State (JioSphere / Brave Style)
  const [mediaState, setMediaState] = useState<MediaState>({
    isPlaying: false,
    title: 'Orbit Ambient Chill (YouTube Stream)',
    artist: 'Lofi Focus · Background Engine',
    duration: 180,
    currentTime: 0,
    audioUrl: 'https://commondatastorage.googleapis.com/codeskulptor-demos/DDR_assets/Kangaroo_MusiQue_-_The_Neverwritten_Role_Playing_Game.mp3',
    isBackgroundEnabled: true
  });
  const [showFloatingPlayer, setShowFloatingPlayer] = useState(false);

  // Listen to Firebase Auth
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      if (currentUser) {
        setPreferences(prev => ({ ...prev, userId: currentUser.uid }));
      }
    });
    return () => unsubscribe();
  }, []);

  const activeTab = tabs.find(t => t.id === activeTabId) || tabs[0];
  const isHomepage = activeTab.url === 'orbit://newtab' || !activeTab.url;

  // Navigation handlers
  const handleNavigate = (url: string) => {
    let finalUrl = url.trim();

    // Force HTTPS Everywhere upgrade
    if (preferences.httpsEverywhere && finalUrl.startsWith('http://') && !finalUrl.includes('localhost')) {
      finalUrl = finalUrl.replace('http://', 'https://');
      setShieldStats(s => ({ ...s, httpsUpgrades: s.httpsUpgrades + 1 }));
    }

    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId) {
        const newHistory = [...tab.historyStack.slice(0, tab.currentIndex + 1), finalUrl];
        return {
          ...tab,
          url: finalUrl,
          title: finalUrl.replace(/^https?:\/\//, '').replace(/\/$/, '') || 'Web Page',
          historyStack: newHistory,
          currentIndex: newHistory.length - 1,
          canGoBack: newHistory.length > 1,
          canGoForward: false,
          isLoading: false
        };
      }
      return tab;
    }));

    // Record History (unless incognito)
    if (!activeTab.isIncognito) {
      const historyItem: HistoryItem = {
        id: `h-${Date.now()}`,
        userId: user ? user.uid : 'guest',
        title: finalUrl,
        url: finalUrl,
        visitedAt: new Date().toISOString(),
        incognito: false
      };
      setHistory(prev => [historyItem, ...prev]);

      if (user) {
        syncHistoryItemToCloud(user.uid, historyItem).catch(console.error);
      }
    }

    setIsOmniboxModalOpen(false);
  };

  const handleGoBack = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId && tab.currentIndex > 0) {
        const newIndex = tab.currentIndex - 1;
        const targetUrl = tab.historyStack[newIndex];
        return {
          ...tab,
          url: targetUrl,
          title: targetUrl === 'orbit://newtab' ? 'New Tab' : targetUrl,
          currentIndex: newIndex,
          canGoBack: newIndex > 0,
          canGoForward: true
        };
      }
      return tab;
    }));
  };

  const handleGoForward = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId && tab.currentIndex < tab.historyStack.length - 1) {
        const newIndex = tab.currentIndex + 1;
        const targetUrl = tab.historyStack[newIndex];
        return {
          ...tab,
          url: targetUrl,
          title: targetUrl === 'orbit://newtab' ? 'New Tab' : targetUrl,
          currentIndex: newIndex,
          canGoBack: true,
          canGoForward: newIndex < tab.historyStack.length - 1
        };
      }
      return tab;
    }));
  };

  const handleNavigateHome = () => {
    handleNavigate('orbit://newtab');
  };

  // Tab operations
  const handleNewTab = (incognito = false) => {
    const newId = `tab-${Date.now()}`;
    const newTab: BrowserTab = {
      id: newId,
      title: incognito ? 'Incognito Tab' : 'New Tab',
      url: 'orbit://newtab',
      isIncognito: incognito,
      isDiscarded: false,
      isLoading: false,
      canGoBack: false,
      canGoForward: false,
      historyStack: ['orbit://newtab'],
      currentIndex: 0,
      isDesktopMode: preferences.desktopModeDefault,
      lastActiveTime: Date.now()
    };

    // Enforce intelligent tab discarding when exceeding memory limit
    setTabs(prev => {
      const updated = [...prev, newTab];
      if (updated.length > 5) {
        // Discard least recently used background tab
        const bgTabs = updated.filter(t => t.id !== newId && !t.isDiscarded);
        if (bgTabs.length > 0) {
          bgTabs[0].isDiscarded = true;
        }
      }
      return updated;
    });
    setActiveTabId(newId);
  };

  const handleCloseTab = (id: string) => {
    if (tabs.length === 1) {
      // Keep at least one tab
      handleNewTab(false);
    }
    setTabs(prev => prev.filter(t => t.id !== id));
    if (activeTabId === id) {
      const remaining = tabs.filter(t => t.id !== id);
      if (remaining.length > 0) {
        setActiveTabId(remaining[remaining.length - 1].id);
      }
    }
  };

  const handleCloseAllTabs = () => {
    handleNewTab(false);
    setTabs([
      {
        id: `tab-${Date.now()}`,
        title: 'New Tab',
        url: 'orbit://newtab',
        isIncognito: false,
        isDiscarded: false,
        isLoading: false,
        canGoBack: false,
        canGoForward: false,
        historyStack: ['orbit://newtab'],
        currentIndex: 0,
        isDesktopMode: false,
        lastActiveTime: Date.now()
      }
    ]);
  };

  // Toggle desktop mode
  const handleToggleDesktopMode = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId) {
        return { ...tab, isDesktopMode: !tab.isDesktopMode };
      }
      return tab;
    }));
  };

  // Bookmarks
  const isCurrentBookmarked = bookmarks.some(b => b.url === activeTab.url);
  const handleToggleBookmark = () => {
    if (isCurrentBookmarked) {
      const existing = bookmarks.find(b => b.url === activeTab.url);
      if (existing) {
        setBookmarks(prev => prev.filter(b => b.id !== existing.id));
        if (user) {
          removeBookmarkFromCloud(user.uid, existing.id).catch(console.error);
        }
      }
    } else {
      const newBm: BookmarkItem = {
        id: `bm-${Date.now()}`,
        userId: user ? user.uid : 'guest',
        title: activeTab.title || activeTab.url,
        url: activeTab.url,
        createdAt: new Date().toISOString()
      };
      setBookmarks(prev => [newBm, ...prev]);
      if (user) {
        syncBookmarkToCloud(user.uid, newBm).catch(console.error);
      }
    }
  };

  // Share
  const handleShare = async () => {
    if (navigator.share && activeTab.url) {
      try {
        await navigator.share({
          title: activeTab.title || 'Orbit Mobile Browser',
          url: activeTab.url
        });
      } catch {
        // User cancelled or not supported
      }
    } else {
      navigator.clipboard.writeText(activeTab.url);
      alert(`Link copied to clipboard: ${activeTab.url}`);
    }
  };

  // Sample download
  const handleStartSampleDownload = () => {
    const dl: DownloadItem = {
      id: `dl-${Date.now()}`,
      userId: user ? user.uid : 'guest',
      fileName: 'Orbit-Chromium-SDK-35.zip',
      url: 'https://orbit.browser/sdk/chromium-35.zip',
      fileSize: '12.4 MB',
      status: 'completed',
      downloadedAt: new Date().toISOString()
    };
    setDownloads(prev => [dl, ...prev]);
  };

  // Background Media Handlers
  const handleTogglePlayMedia = () => {
    setMediaState(prev => {
      const nextPlay = !prev.isPlaying;
      if (nextPlay) {
        setShowFloatingPlayer(true);
      }
      return { ...prev, isPlaying: nextPlay };
    });
  };

  const handleNextTrack = () => {
    setMediaState(prev => ({
      ...prev,
      title: 'Orbit Deep Waves (HTML5 Video Stream)',
      artist: 'Night Ambient · Background Engine',
      isPlaying: true
    }));
  };

  const handlePrevTrack = () => {
    setMediaState(prev => ({
      ...prev,
      title: 'Orbit Cyberpunk Synth (YouTube Audio)',
      artist: 'Lo-Fi Chill · Background Engine',
      isPlaying: true
    }));
  };

  return (
    <div className="min-h-screen bg-[#070D19] text-slate-100 flex flex-col font-sans selection:bg-cyan-500/30 selection:text-cyan-200 relative overflow-hidden">
      {/* Background Subtle Celestial Grid Glow */}
      <div className="absolute inset-0 pointer-events-none bg-[radial-gradient(circle_at_50%_40%,rgba(56,189,248,0.06)_0%,transparent_60%)]" />

      {/* Top App-Bar (Current URL, Tab-Switcher Badge, Chrome 3-Dot Button) */}
      <AppBar
        currentTab={activeTab}
        tabCount={tabs.length}
        shieldBlockedCount={shieldStats.trackersBlocked}
        onNavigateHome={handleNavigateHome}
        onGoBack={handleGoBack}
        onGoForward={handleGoForward}
        onReload={() => handleNavigate(activeTab.url)}
        onOpenOmniboxModal={() => setIsOmniboxModalOpen(true)}
        onOpenTabSwitcher={() => setIsTabSwitcherOpen(true)}
        onOpenMenu={() => setIsMenuOpen(true)}
        onOpenShieldModal={() => setIsShieldModalOpen(true)}
      />

      {/* Main Viewport Content Area */}
      <main className="flex-1 flex flex-col relative z-10 w-full h-full">
        {isHomepage ? (
          /* 100% Clutter-Free Minimalist Homepage (Section 2) */
          <div className="flex-1 flex flex-col items-center justify-center px-4 py-8 max-w-xl mx-auto w-full my-auto text-center space-y-8 animate-in fade-in duration-300">
            {/* Vertical & Horizontal Center: Custom Glowing Orbit Minimal Logo */}
            <OrbitLogo size="lg" />

            {/* Directly Beneath Logo: Sleek Pill-Shaped Search & URL Omnibox */}
            <div className="w-full">
              <Omnibox
                onNavigate={handleNavigate}
                searchEngine={preferences.searchEngine}
                shieldBlockedCount={shieldStats.trackersBlocked}
                autoFocus={true}
              />
            </div>

            {/* Subtle Quick Engine Notice */}
            <div className="flex items-center justify-center gap-2 text-xs text-slate-400 font-mono">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              <span>Chromium Blink/V8 Engine</span>
              <span className="text-slate-600">·</span>
              <span>Orbit Shield Active</span>
            </div>
          </div>
        ) : (
          /* Web Page Viewport with Orbit Shield Protection */
          <WebViewport
            currentTab={activeTab}
            shieldStats={shieldStats}
            onNavigateHome={handleNavigateHome}
            onRecordBlockedTracker={() => {
              setShieldStats(s => ({
                ...s,
                trackersBlocked: s.trackersBlocked + 1,
                bandwidthSavedKb: s.bandwidthSavedKb + 18
              }));
            }}
          />
        )}
      </main>

      {/* Omnibox Direct Search Modal when clicking Top Bar */}
      {isOmniboxModalOpen && (
        <div 
          className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-xl flex flex-col p-4 animate-in fade-in duration-150"
          onClick={() => setIsOmniboxModalOpen(false)}
        >
          <div 
            className="max-w-xl w-full mx-auto mt-6 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
              <span>Enter URL or Search Query</span>
              <button 
                onClick={() => setIsOmniboxModalOpen(false)}
                className="text-cyan-400 hover:underline cursor-pointer"
              >
                Cancel
              </button>
            </div>
            <Omnibox
              onNavigate={handleNavigate}
              searchEngine={preferences.searchEngine}
              shieldBlockedCount={shieldStats.trackersBlocked}
              autoFocus={true}
            />
          </div>
        </div>
      )}

      {/* Floating Background Media Player (JioSphere / Brave Style) */}
      {(showFloatingPlayer || mediaState.isPlaying) && (
        <BackgroundMediaPlayer
          mediaState={mediaState}
          onTogglePlay={handleTogglePlayMedia}
          onNextTrack={handleNextTrack}
          onPrevTrack={handlePrevTrack}
          onToggleBackgroundPlayback={() => setMediaState(s => ({ ...s, isBackgroundEnabled: !s.isBackgroundEnabled }))}
          onSimulateLockScreen={() => setIsLockScreenOpen(true)}
          onClosePlayer={() => setShowFloatingPlayer(false)}
        />
      )}

      {/* Chrome-Style 3-Dot (⋮) Overflow Menu */}
      <ThreeDotMenu
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        isDesktopMode={activeTab.isDesktopMode}
        onToggleDesktopMode={handleToggleDesktopMode}
        onNewTab={handleNewTab}
        onOpenHistory={() => setIsHistoryOpen(true)}
        onOpenDownloads={() => setIsDownloadsOpen(true)}
        onOpenBookmarks={() => setIsBookmarksOpen(true)}
        onOpenShield={() => setIsShieldModalOpen(true)}
        onOpenFindInPage={() => setIsFindInPageOpen(true)}
        onShare={handleShare}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onOpenArchitecture={() => setIsArchitectureOpen(true)}
        isBookmarked={isCurrentBookmarked}
        onToggleBookmark={handleToggleBookmark}
      />

      {/* Orbit Shield & Privacy Dashboard Modal */}
      <OrbitShieldModal
        isOpen={isShieldModalOpen}
        onClose={() => setIsShieldModalOpen(false)}
        stats={shieldStats}
        shieldEnabled={preferences.orbitShieldEnabled}
        onToggleShield={() => setPreferences(p => ({ ...p, orbitShieldEnabled: !p.orbitShieldEnabled }))}
        httpsEverywhere={preferences.httpsEverywhere}
        onToggleHttps={() => setPreferences(p => ({ ...p, httpsEverywhere: !p.httpsEverywhere }))}
        fingerprintProtection={fingerprintProtection}
        onToggleFingerprint={() => setFingerprintProtection(!fingerprintProtection)}
      />

      {/* Tab Switcher Grid Modal */}
      <TabSwitcherModal
        isOpen={isTabSwitcherOpen}
        onClose={() => setIsTabSwitcherOpen(false)}
        tabs={tabs}
        activeTabId={activeTabId}
        onSelectTab={setActiveTabId}
        onCloseTab={handleCloseTab}
        onNewTab={handleNewTab}
        onCloseAllTabs={handleCloseAllTabs}
      />

      {/* Bookmarks Modal (Firestore Synced) */}
      <BookmarksModal
        isOpen={isBookmarksOpen}
        onClose={() => setIsBookmarksOpen(false)}
        bookmarks={bookmarks}
        onSelectBookmark={handleNavigate}
        onRemoveBookmark={(id) => {
          setBookmarks(prev => prev.filter(b => b.id !== id));
          if (user) removeBookmarkFromCloud(user.uid, id).catch(console.error);
        }}
        onAddCurrentPage={handleToggleBookmark}
        currentPageTitle={activeTab.title}
        currentPageUrl={activeTab.url}
        isLoggedIn={!!user}
      />

      {/* History Modal (Firestore Synced) */}
      <HistoryModal
        isOpen={isHistoryOpen}
        onClose={() => setIsHistoryOpen(false)}
        history={history}
        onSelectHistory={handleNavigate}
        onClearHistory={() => setHistory([])}
        isLoggedIn={!!user}
      />

      {/* Downloads Modal */}
      <DownloadsModal
        isOpen={isDownloadsOpen}
        onClose={() => setIsDownloadsOpen(false)}
        downloads={downloads}
        onClearDownloads={() => setDownloads([])}
        onStartSampleDownload={handleStartSampleDownload}
      />

      {/* Settings Modal */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        preferences={preferences}
        onUpdatePreferences={(up) => {
          setPreferences(prev => {
            const next = { ...prev, ...up };
            if (user) syncPreferencesToCloud(user.uid, next).catch(console.error);
            return next;
          });
        }}
        user={user}
        onLogin={async () => {
          const loggedIn = await loginWithGoogle();
          if (loggedIn) setUser(loggedIn);
        }}
        onLogout={async () => {
          await logoutUser();
          setUser(null);
        }}
        onClearAllData={() => {
          setBookmarks([]);
          setHistory([]);
          setShieldStats({ trackersBlocked: 0, adsBlocked: 0, httpsUpgrades: 0, bandwidthSavedKb: 0 });
          alert('Local browsing data and cache purged successfully.');
        }}
      />

      {/* Architecture & Native Code Inspector Modal */}
      <ArchitectureModal
        isOpen={isArchitectureOpen}
        onClose={() => setIsArchitectureOpen(false)}
      />

      {/* Find In Page Search Bar */}
      <FindInPageBar
        isOpen={isFindInPageOpen}
        onClose={() => setIsFindInPageOpen(false)}
      />

      {/* Simulated Android Screen Lock Overlay with Background Media Controls */}
      <LockScreenOverlay
        isOpen={isLockScreenOpen}
        onUnlock={() => setIsLockScreenOpen(false)}
        mediaState={mediaState}
        onTogglePlay={handleTogglePlayMedia}
        onNextTrack={handleNextTrack}
        onPrevTrack={handlePrevTrack}
      />
    </div>
  );
}
