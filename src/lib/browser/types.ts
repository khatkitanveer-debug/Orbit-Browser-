// Tez Browser — shared types

export const NEWTAB = "tez://newtab";

export interface Tab {
  id: string;
  url: string; // 'tez://newtab' | 'tez://search?q=...' | 'tez://settings' | https URL
  title: string;
  loading: boolean;
  history: string[];
  historyIndex: number;
  nonce: number; // used to force iframe reload
}

export interface HistoryItem {
  id?: number;
  url: string;
  title: string;
  ts: number;
}

export interface Bookmark {
  url: string;
  title: string;
  ts: number;
}

export interface SpeedDialItem {
  id?: number;
  label: string;
  url: string;
}

export interface FirebaseCfg {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
}

export interface TezSettingsData {
  theme?: "light" | "dark" | "system";
  searchEngine?: "tez-ai" | "duckduckgo";
  firebase?: FirebaseCfg | null;
  session?: { tabs: Tab[]; activeTabId: string } | null;
}

export interface ChatMsg {
  role: "user" | "assistant" | "system";
  content: string;
  ts?: number;
}

export interface Suggestion {
  title: string;
  url: string;
}

export interface SearchResult {
  url: string;
  name: string;
  snippet: string;
}
