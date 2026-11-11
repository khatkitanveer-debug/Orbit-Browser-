import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";

/**
 * OrbitRender — native bridge contract (Module 1).
 * Kotlin side: android/app/src/main/java/com/orbit/browser/plugins/OrbitRenderPlugin.kt
 * Keep both signatures in lockstep; this file is the source of truth.
 */
export interface PageState {
  url: string;
  title: string;
  /** 0..100, native WebView progress */
  progress: number;
  loading: boolean;
  canGoBack: boolean;
  canGoForward: boolean;
  /** renderer process died — shell shows an error state and the slot self-destructs */
  crashed?: boolean;
}

export interface OrbitRenderPlugin {
  /**
   * Open (or navigate) the native content WebView in the slot below the app bar.
   * topPx = current app bar bottom in PHYSICAL pixels — sent with the first open
   * so content never flashes under the chrome.
   * ephemeral = Module 4 incognito: no HTTP cache, no DOM storage, no 3P cookies.
   */
  open(options: { url: string; topPx?: number; ephemeral?: boolean }): Promise<void>;
  /** Re-sync the slot after rotation / keyboard / font changes. */
  setSlotInset(options: { topPx: number }): Promise<void>;
  goBack(): Promise<{ consumed: boolean }>;
  goForward(): Promise<{ consumed: boolean }>;
  reload(): Promise<void>;
  /** Destroy the content WebView cleanly (used by Home). */
  close(): Promise<void>;
  getState(): Promise<PageState | undefined>;
  /**
   * Module 4 — proxy override for Tor/Orbot routing ("127.0.0.1:8118").
   * App-wide for the WebView stack; pass [] to clear. Rejects when the
   * WebView lacks the feature.
   */
  setProxy(options: { rules: string[] }): Promise<void>;
  addListener(eventName: "page", listener: (state: PageState) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

export const OrbitRender = registerPlugin<OrbitRenderPlugin>("OrbitRender");
