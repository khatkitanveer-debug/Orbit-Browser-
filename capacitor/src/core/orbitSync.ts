import { registerPlugin } from "@capacitor/core";

/**
 * OrbitSync — native bridge contract (Module 4).
 * Kotlin side: android/app/src/main/java/com/orbit/browser/plugins/OrbitSyncPlugin.kt
 *
 * Google Sign-In via Google Identity — needs ONLY plugins.OrbitSync.webClientId
 * in capacitor.config.ts. The idToken is exchanged for a Firebase session by
 * syncClient.ts (no google-services.json required anywhere).
 */
export interface GoogleSignInResult {
  idToken?: string;
  email?: string | null;
  displayName?: string | null;
  photoUrl?: string | null;
  cancelled?: boolean;
}

export interface OrbitSyncPlugin {
  signIn(): Promise<GoogleSignInResult>;
  signOut(): Promise<void>;
}

export const OrbitSync = registerPlugin<OrbitSyncPlugin>("OrbitSync");
