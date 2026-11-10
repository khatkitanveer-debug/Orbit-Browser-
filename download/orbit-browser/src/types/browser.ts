export interface BrowserTab {
  id: string;
  title: string;
  url: string;
  favicon?: string;
  isIncognito: boolean;
  isDiscarded: boolean;
  isLoading: boolean;
  canGoBack: boolean;
  canGoForward: boolean;
  historyStack: string[];
  currentIndex: number;
  isDesktopMode: boolean;
  lastActiveTime: number;
}

export interface BookmarkItem {
  id: string;
  userId: string;
  title: string;
  url: string;
  favicon?: string;
  folder?: string;
  createdAt: string;
}

export interface HistoryItem {
  id: string;
  userId: string;
  title: string;
  url: string;
  visitedAt: string;
  incognito: boolean;
}

export interface DownloadItem {
  id: string;
  userId: string;
  fileName: string;
  url: string;
  fileSize: string;
  status: 'completed' | 'downloading' | 'paused' | 'failed';
  downloadedAt: string;
}

export interface UserPreferences {
  userId: string;
  searchEngine: 'duckduckgo' | 'brave' | 'google' | 'startpage';
  orbitShieldEnabled: boolean;
  backgroundPlaybackEnabled: boolean;
  httpsEverywhere: boolean;
  doNotTrack: boolean;
  clearOnExit: boolean;
  desktopModeDefault: boolean;
  updatedAt: string;
}

export interface ShieldStats {
  trackersBlocked: number;
  adsBlocked: number;
  httpsUpgrades: number;
  bandwidthSavedKb: number;
}

export interface MediaState {
  isPlaying: boolean;
  title: string;
  artist: string;
  duration: number;
  currentTime: number;
  audioUrl: string;
  isBackgroundEnabled: boolean;
}
