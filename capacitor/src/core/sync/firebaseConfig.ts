/**
 * Module 4 — Firebase web config.
 *
 * Drop your Firebase project's web app config here (Project settings →
 * General → Your apps → Web). The NATIVE Google sign-in needs only the
 * OAuth **Web client ID** in capacitor.config.ts (plugins.OrbitSync) —
 * no google-services.json anywhere.
 *
 * Empty values = sync stays disabled and the UI says so honestly.
 */
export const FIREBASE_CONFIG = {
  apiKey: "",
  authDomain: "",
  projectId: "",
  appId: "",
} as const;

export function isFirebaseConfigured(): boolean {
  return FIREBASE_CONFIG.apiKey.length > 0 && FIREBASE_CONFIG.projectId.length > 0;
}
