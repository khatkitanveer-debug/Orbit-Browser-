import { registerPlugin } from "@capacitor/core";

/**
 * OrbitLens — native bridge contract (Module 3).
 * Kotlin side: android/app/src/main/java/com/orbit/browser/plugins/OrbitLensPlugin.kt
 * Keep both signatures in lockstep; this file is the source of truth.
 */
export interface LensScanResult {
  /** Lens results URL — open it in the content slot. Always present unless cancelled. */
  url?: string;
  /** true when the upload endpoint drifted — url points at the manual Lens UI. */
  fallback?: boolean;
  /** User closed the camera without taking a photo. */
  cancelled?: boolean;
}

export interface OrbitLensPlugin {
  /**
   * Camera → re-encode → Google Lens upload. Rejects when no camera app
   * exists or the platform misbehaves; resolves { cancelled } on user cancel.
   */
  scan(): Promise<LensScanResult>;
}

export const OrbitLens = registerPlugin<OrbitLensPlugin>("OrbitLens");
