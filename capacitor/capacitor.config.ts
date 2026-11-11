import type { CapacitorConfig } from "@capacitor/cli";

/**
 * Orbit Browser — Capacitor shell config (Module 1).
 * The Android app id intentionally matches the legacy native app so the
 * committed orbit.keystore keeps the OTA upgrade chain intact at cutover.
 */
const config: CapacitorConfig = {
  appId: "com.orbit.browser",
  appName: "Orbit Browser",
  webDir: "dist",
  backgroundColor: "#070D19",
  android: {
    // http:// sites must render exactly like Chrome renders them.
    allowMixedContent: true,
    captureInput: true,
    // chrome://inspect QA during development — flip off for the store build.
    webContentsDebuggingEnabled: true,
  },
  plugins: {
    // Keep native networking on the WebView stack — no JS fetch interception,
    // zero latency overhead on every request.
    CapacitorHttp: { enabled: false },
    // Module 4 sync: put your Firebase OAuth **Web client ID** here
    // (Firebase console → Authentication → Sign-in method → Google →
    // Web SDK configuration). No google-services.json is needed.
    // OrbitSync: { webClientId: "1234-abcd.apps.googleusercontent.com" },
  },
};

export default config;
