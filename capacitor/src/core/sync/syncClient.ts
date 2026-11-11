import type { User } from "firebase/auth";
import { FIREBASE_CONFIG, isFirebaseConfigured } from "./firebaseConfig";

/**
 * Firebase JS glue (Module 4) — lazy-loaded so the ~300 kB SDK never
 * touches the initial bundle. Firestore runs over the WebView's network
 * stack like any other site traffic; CapacitorHttp stays off (M1).
 */

type AuthMod = typeof import("firebase/auth");
type FsMod = typeof import("firebase/firestore");

interface FirebaseBundle {
  auth: ReturnType<AuthMod["getAuth"]>;
  db: ReturnType<FsMod["getFirestore"]>;
  authMod: AuthMod;
  fsMod: FsMod;
}

let bundle: Promise<FirebaseBundle> | null = null;

export function loadFirebase(): Promise<FirebaseBundle> {
  if (!isFirebaseConfigured()) {
    return Promise.reject(new Error("Firebase is not configured (core/sync/firebaseConfig.ts)"));
  }
  if (!bundle) {
    bundle = (async () => {
      const appMod = await import("firebase/app");
      const authMod = await import("firebase/auth");
      const fsMod = await import("firebase/firestore");
      const app = appMod.initializeApp({
        apiKey: FIREBASE_CONFIG.apiKey,
        authDomain: FIREBASE_CONFIG.authDomain,
        projectId: FIREBASE_CONFIG.projectId,
        appId: FIREBASE_CONFIG.appId,
      });
      return {
        auth: authMod.getAuth(app),
        db: fsMod.getFirestore(app),
        authMod,
        fsMod,
      };
    })();
    bundle.catch(() => {
      bundle = null; // allow retry
    });
  }
  return bundle;
}

/** Exchange the native Google idToken (OrbitSync plugin) for a Firebase session. */
export async function signInWithGoogleToken(idToken: string): Promise<User> {
  const { auth, authMod } = await loadFirebase();
  const credential = authMod.GoogleAuthProvider.credential(idToken);
  const cred = await authMod.signInWithCredential(auth, credential);
  return cred.user;
}

export async function restoreSession(): Promise<User | null> {
  if (!isFirebaseConfigured()) return null;
  try {
    const { auth, authMod } = await loadFirebase();
    return await new Promise<User | null>((resolve) => {
      const unsub = authMod.onAuthStateChanged(auth, (user) => {
        unsub();
        resolve(user);
      });
    });
  } catch {
    return null;
  }
}

export async function signOutFirebase(): Promise<void> {
  const { auth, authMod } = await loadFirebase();
  await authMod.signOut(auth);
}
