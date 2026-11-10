import { initializeApp, getApps, getApp } from 'firebase/app';
import { 
  getAuth, 
  GoogleAuthProvider, 
  signInWithPopup, 
  signOut as fbSignOut,
  onAuthStateChanged,
  User 
} from 'firebase/auth';
import { 
  getFirestore, 
  doc, 
  getDocFromServer,
  collection, 
  query, 
  where, 
  getDocs, 
  setDoc, 
  deleteDoc, 
  onSnapshot 
} from 'firebase/firestore';
import firebaseConfig from '../../firebase-applet-config.json';
import { BookmarkItem, HistoryItem, UserPreferences, DownloadItem } from '../types/browser';

// Operation Types as defined in Firebase Skill
export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export interface FirestoreErrorInfo {
  error: string;
  operationType: OperationType;
  path: string | null;
  authInfo: {
    userId?: string | null;
    email?: string | null;
    emailVerified?: boolean | null;
    isAnonymous?: boolean | null;
    tenantId?: string | null;
    providerInfo?: {
      providerId?: string | null;
      email?: string | null;
    }[];
  };
}

export function handleFirestoreError(error: unknown, operationType: OperationType, path: string | null): never {
  const errInfo: FirestoreErrorInfo = {
    error: error instanceof Error ? error.message : String(error),
    authInfo: {
      userId: auth?.currentUser?.uid,
      email: auth?.currentUser?.email,
      emailVerified: auth?.currentUser?.emailVerified,
      isAnonymous: auth?.currentUser?.isAnonymous,
      tenantId: auth?.currentUser?.tenantId,
      providerInfo: auth?.currentUser?.providerData?.map(provider => ({
        providerId: provider.providerId,
        email: provider.email,
      })) || []
    },
    operationType,
    path
  };
  console.error('Firestore Error: ', JSON.stringify(errInfo));
  throw new Error(JSON.stringify(errInfo));
}

// Initialize Firebase
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);
export const auth = getAuth(app);
export const googleProvider = new GoogleAuthProvider();

// Validate Connection on Boot as mandated by Skill
export async function testConnection(): Promise<boolean> {
  try {
    await getDocFromServer(doc(db, 'test', 'connection'));
    return true;
  } catch (error) {
    if (error instanceof Error && error.message.includes('the client is offline')) {
      console.warn('Firebase client is offline. Local cache will be used.');
    }
    return false;
  }
}
testConnection();

// Auth helpers
export async function loginWithGoogle(): Promise<User | null> {
  try {
    const result = await signInWithPopup(auth, googleProvider);
    return result.user;
  } catch (err) {
    console.error('Google Sign-In failed:', err);
    return null;
  }
}

export async function logoutUser(): Promise<void> {
  await fbSignOut(auth);
}

export { onAuthStateChanged };

// Firestore Synchronization Services for Orbit
export async function syncBookmarkToCloud(userId: string, bookmark: BookmarkItem): Promise<void> {
  const docPath = `users/${userId}/bookmarks/${bookmark.id}`;
  try {
    await setDoc(doc(db, docPath), bookmark);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, docPath);
  }
}

export async function removeBookmarkFromCloud(userId: string, bookmarkId: string): Promise<void> {
  const docPath = `users/${userId}/bookmarks/${bookmarkId}`;
  try {
    await deleteDoc(doc(db, docPath));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, docPath);
  }
}

export async function syncHistoryItemToCloud(userId: string, historyItem: HistoryItem): Promise<void> {
  const docPath = `users/${userId}/history/${historyItem.id}`;
  try {
    await setDoc(doc(db, docPath), historyItem);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, docPath);
  }
}

export async function clearCloudHistory(userId: string, historyIds: string[]): Promise<void> {
  for (const id of historyIds) {
    const docPath = `users/${userId}/history/${id}`;
    try {
      await deleteDoc(doc(db, docPath));
    } catch (error) {
      handleFirestoreError(error, OperationType.DELETE, docPath);
    }
  }
}

export async function syncPreferencesToCloud(userId: string, prefs: UserPreferences): Promise<void> {
  const docPath = `users/${userId}/preferences/main`;
  try {
    await setDoc(doc(db, docPath), prefs);
  } catch (error) {
    handleFirestoreError(error, OperationType.WRITE, docPath);
  }
}
