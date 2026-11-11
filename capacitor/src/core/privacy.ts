import { registerPlugin } from "@capacitor/core";

/**
 * OrbitPrivacy — native bridge contract (Module 4, VPN toggle).
 * Kotlin side: android/app/src/main/java/com/orbit/browser/plugins/OrbitPrivacyPlugin.kt
 */
export interface VpnStartResult {
  started: boolean;
  /** false when no routing backend is wired yet — scaffolding only. */
  supported?: boolean;
  /** "consent-denied" | "backend-not-configured" */
  reason?: string;
}

export interface OrbitPrivacyPlugin {
  /** Runs the system VPN consent dialog the first time, then starts the service. */
  vpnStart(): Promise<VpnStartResult>;
  vpnStop(): Promise<void>;
}

export const OrbitPrivacy = registerPlugin<OrbitPrivacyPlugin>("OrbitPrivacy");
