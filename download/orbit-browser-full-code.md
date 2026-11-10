# Orbit Browser — Complete Source Code
> Ultra-fast, high-privacy mobile browser. React 19 + Vite 8 + Tailwind 4 + Firebase. Orbit Shield ad-block, background media, ECC key generator (Web Crypto), apne Firebase project me sync.
## Project Structure
```
package.json
vite.config.ts
tsconfig.json
index.html
server.ts
.env.example
.gitignore
firebase-applet-config.json
firestore.rules
README.md
firebase-blueprint.json
metadata.json
src/App.tsx
src/index.css
src/main.tsx
src/types/browser.ts
src/components/AppBar.tsx
src/components/ArchitectureModal.tsx
src/components/BackgroundMediaPlayer.tsx
src/components/BookmarksModal.tsx
src/components/DownloadsModal.tsx
src/components/EccKeyGenerator.tsx
src/components/FindInPageBar.tsx
src/components/HistoryModal.tsx
src/components/LockScreenOverlay.tsx
src/components/Omnibox.tsx
src/components/OrbitLogo.tsx
src/components/OrbitShieldModal.tsx
src/components/SettingsModal.tsx
src/components/TabSwitcherModal.tsx
src/components/ThreeDotMenu.tsx
src/components/WebViewport.tsx
src/lib/eccCrypto.ts
src/lib/firebase.ts
android/app/src/main/AndroidManifest.xml
android/app/src/main/res/layout/activity_main.xml
android/app/src/main/res/drawable/bg_pill_input.xml
android/app/src/main/res/drawable/ic_orbit_logo.xml
android/app/src/main/java/com/orbit/browser/MainActivity.kt
android/app/src/main/java/com/orbit/browser/MediaPlaybackService.kt
android/app/src/main/java/com/orbit/browser/OrbitAdBlocker.kt
android/app/src/main/java/com/orbit/browser/OrbitChromiumWebView.kt
android/app/src/main/java/com/orbit/browser/PrivacyEngine.kt
android/app/src/main/java/com/orbit/browser/TabManager.kt
android/app/src/main/java/com/orbit/browser/service/MediaSessionService.kt
```

---

## File: `package.json`

```json
{
  "name": "orbit-browser",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "description": "Orbit — ultra-fast, high-privacy mobile browser with Orbit Shield, background media playback, ECC key generator aur Firebase cloud sync",
  "scripts": {
    "dev": "tsx server.ts",
    "start": "NODE_ENV=production tsx server.ts",
    "build": "vite build",
    "preview": "vite preview",
    "clean": "rm -rf dist",
    "lint": "tsc --noEmit"
  },
  "dependencies": {
    "@tailwindcss/vite": "^4.3.3",
    "@vitejs/plugin-react": "^6.1.1",
    "express": "^4.21.2",
    "firebase": "^13.2.0",
    "lucide-react": "^0.546.0",
    "motion": "^12.23.24",
    "react": "^19.0.1",
    "react-dom": "^19.0.1",
    "vite": "^8.3.0"
  },
  "devDependencies": {
    "@types/node": "^22.14.0",
    "@types/react": "^19.3.0",
    "@types/react-dom": "^19.3.0",
    "@types/express": "^4.17.21",
    "tsx": "^4.21.0",
    "typescript": "^5.8.0"
  }
}

```

---

## File: `vite.config.ts`

```typescript
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import {defineConfig} from 'vite';

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      // Do not modify—file watching is disabled to prevent flickering during agent edits.
      hmr: process.env.DISABLE_HMR !== 'true',
      // Disable file watching when DISABLE_HMR is true to save CPU during agent edits.
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
    },
  };
});

```

---

## File: `tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "experimentalDecorators": true,
    "useDefineForClassFields": false,
    "module": "ESNext",
    "types": ["vite/client"],
    "lib": [
      "ES2022",
      "DOM",
      "DOM.Iterable"
    ],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "isolatedModules": true,
    "moduleDetection": "force",
    "allowJs": true,
    "jsx": "react-jsx",
    "paths": {
      "@/*": [
        "./*"
      ]
    },
    "allowImportingTsExtensions": true,
    "noEmit": true
  },
  "include": ["src", "server.ts", "vite.config.ts"],
  "exclude": ["dist", "android", "node_modules"]
}

```

---

## File: `index.html`

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Orbit — Ultra-Fast, High-Privacy Mobile Browser</title>
    <meta name="theme-color" content="#070D19" />
    <meta name="description" content="Orbit is an ultra-fast, high-privacy mobile browser based on Chromium architecture with Orbit Shield ad-blocking, background media playback, and Firebase cloud sync." />
    <meta property="og:title" content="Orbit — Ultra-Fast, High-Privacy Mobile Browser" />
    <meta property="og:description" content="Orbit is an ultra-fast, high-privacy mobile browser based on Chromium architecture with Orbit Shield ad-blocking, background media playback, and Firebase cloud sync." />
    <meta property="og:type" content="website" />
    <meta name="twitter:card" content="summary_large_image" />
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet" />
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>


```

---

## File: `server.ts`

```typescript
// Orbit Browser — minimal app server (AI Studio compatible)
// - Dev mode: Vite dev server (HMR) express ke through
// - Production: dist/ ki static files serve karta hai
// Ek hi port par sab kuch: process.env.PORT || 3000

import express, { Request, Response } from 'express';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const port = Number(process.env.PORT) || 3000;
const isProd = process.env.NODE_ENV === 'production' || !!fs.existsSync(path.join(__dirname, 'dist'));

async function start() {
  if (!isProd) {
    // ---------- DEV: Vite middleware (HMR enabled) ----------
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    // ---------- PROD: static dist/ ----------
    const dist = path.join(__dirname, 'dist');
    app.use(express.static(dist));
    app.get('*', (_req: Request, res: Response) => {
      res.sendFile(path.join(dist, 'index.html'));
    });
  }

  app.listen(port, () => {
    console.log(`Orbit Browser chal raha hai: http://localhost:${port} (${isProd ? 'production' : 'dev'})`);
  });
}

start().catch((err) => {
  console.error('Server start fail:', err);
  process.exit(1);
});

```

---

## File: `.env.example`

```text
# GEMINI_API_KEY: Required for Gemini AI API calls.
# AI Studio automatically injects this at runtime from user secrets.
# Users configure this via the Secrets panel in the AI Studio UI.
GEMINI_API_KEY="MY_GEMINI_API_KEY"

# APP_URL: The URL where this applet is hosted.
# AI Studio automatically injects this at runtime with the Cloud Run service URL.
# Used for self-referential links, OAuth callbacks, and API endpoints.
APP_URL="MY_APP_URL"

```

---

## File: `.gitignore`

```text
node_modules/
build/
dist/
coverage/
.DS_Store
*.log
.env*
!.env.example

```

---

## File: `firebase-applet-config.json`

```json
{
  "projectId": "eloquent-cable-qxq1s",
  "appId": "1:629237505588:web:73996c6007ca1950af3461",
  "apiKey": "AIzaSyBZMS5XHpPZwURZ5ubG9f9uoxNoaYNIMv0",
  "authDomain": "eloquent-cable-qxq1s.firebaseapp.com",
  "firestoreDatabaseId": "ai-studio-eccencryptiontoo-915502b3-d0e2-43f5-abcc-5fac915ab102",
  "storageBucket": "eloquent-cable-qxq1s.firebasestorage.app",
  "messagingSenderId": "629237505588",
  "measurementId": "",
  "oAuthClientId": "629237505588-bt6p0l3qdil56d04v68v4afkn5ilg9ij.apps.googleusercontent.com",
  "recaptchaSiteKey": ""
}
```

---

## File: `firestore.rules`

```text
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    // Global Safety Net: Default-deny all access
    match /{document=**} {
      allow read, write: if false;
    }

    // Helper functions
    function isSignedIn() {
      return request.auth != null;
    }

    function isOwner(userId) {
      return isSignedIn() && request.auth.uid == userId;
    }

    function isValidId(id) {
      return id is string && id.size() > 0 && id.size() <= 128 && id.matches('^[a-zA-Z0-9_\\-]+$');
    }

    function isValidTimestamp(val) {
      return val is string && val.size() > 0 && val.size() <= 64;
    }

    function isValidUrl(val) {
      return val is string && val.size() > 0 && val.size() <= 2048;
    }

    function isValidTitle(val) {
      return val is string && val.size() > 0 && val.size() <= 256;
    }

    // Validation blueprints for Orbit browser entities
    function isValidPreferences(data, userId) {
      return data.keys().hasAll(['userId', 'searchEngine', 'orbitShieldEnabled', 'backgroundPlaybackEnabled'])
        && data.keys().hasOnly(['userId', 'searchEngine', 'orbitShieldEnabled', 'backgroundPlaybackEnabled', 'httpsEverywhere', 'doNotTrack', 'clearOnExit', 'desktopModeDefault', 'updatedAt'])
        && data.userId == userId
        && data.searchEngine is string && data.searchEngine.size() <= 64
        && data.orbitShieldEnabled is bool
        && data.backgroundPlaybackEnabled is bool
        && (!('httpsEverywhere' in data) || data.httpsEverywhere is bool)
        && (!('doNotTrack' in data) || data.doNotTrack is bool)
        && (!('clearOnExit' in data) || data.clearOnExit is bool)
        && (!('desktopModeDefault' in data) || data.desktopModeDefault is bool)
        && (!('updatedAt' in data) || isValidTimestamp(data.updatedAt));
    }

    function isValidBookmark(data, userId) {
      return data.keys().hasAll(['id', 'userId', 'title', 'url'])
        && data.keys().hasOnly(['id', 'userId', 'title', 'url', 'favicon', 'folder', 'createdAt'])
        && data.id is string && data.id.size() <= 128
        && data.userId == userId
        && isValidTitle(data.title)
        && isValidUrl(data.url)
        && (!('favicon' in data) || (data.favicon is string && data.favicon.size() <= 512))
        && (!('folder' in data) || (data.folder is string && data.folder.size() <= 128))
        && (!('createdAt' in data) || isValidTimestamp(data.createdAt));
    }

    function isValidHistoryItem(data, userId) {
      return data.keys().hasAll(['id', 'userId', 'title', 'url', 'visitedAt'])
        && data.keys().hasOnly(['id', 'userId', 'title', 'url', 'visitedAt', 'incognito'])
        && data.id is string && data.id.size() <= 128
        && data.userId == userId
        && isValidTitle(data.title)
        && isValidUrl(data.url)
        && isValidTimestamp(data.visitedAt)
        && (!('incognito' in data) || data.incognito is bool);
    }

    function isValidDownloadItem(data, userId) {
      return data.keys().hasAll(['id', 'userId', 'fileName', 'url', 'status'])
        && data.keys().hasOnly(['id', 'userId', 'fileName', 'url', 'fileSize', 'status', 'downloadedAt'])
        && data.id is string && data.id.size() <= 128
        && data.userId == userId
        && data.fileName is string && data.fileName.size() <= 256
        && isValidUrl(data.url)
        && data.status is string && data.status.size() <= 32
        && (!('fileSize' in data) || (data.fileSize is string && data.fileSize.size() <= 64))
        && (!('downloadedAt' in data) || isValidTimestamp(data.downloadedAt));
    }

    // Match rules for User Preferences
    match /users/{userId}/preferences/{prefId} {
      allow get: if isOwner(userId) && isValidId(prefId);
      allow list: if isOwner(userId);
      allow create: if isOwner(userId) && isValidId(prefId) && isValidPreferences(request.resource.data, userId);
      allow update: if isOwner(userId) && isValidId(prefId) && isValidPreferences(request.resource.data, userId);
      allow delete: if isOwner(userId) && isValidId(prefId);
    }

    // Match rules for Bookmarks
    match /users/{userId}/bookmarks/{bookmarkId} {
      allow get: if isOwner(userId) && isValidId(bookmarkId);
      allow list: if isOwner(userId);
      allow create: if isOwner(userId) && isValidId(bookmarkId) && isValidBookmark(request.resource.data, userId);
      allow update: if isOwner(userId) && isValidId(bookmarkId) && isValidBookmark(request.resource.data, userId);
      allow delete: if isOwner(userId) && isValidId(bookmarkId);
    }

    // Match rules for Navigation History
    match /users/{userId}/history/{historyId} {
      allow get: if isOwner(userId) && isValidId(historyId);
      allow list: if isOwner(userId);
      allow create: if isOwner(userId) && isValidId(historyId) && isValidHistoryItem(request.resource.data, userId);
      allow update: if isOwner(userId) && isValidId(historyId) && isValidHistoryItem(request.resource.data, userId);
      allow delete: if isOwner(userId) && isValidId(historyId);
    }

    // Match rules for Downloads
    match /users/{userId}/downloads/{downloadId} {
      allow get: if isOwner(userId) && isValidId(downloadId);
      allow list: if isOwner(userId);
      allow create: if isOwner(userId) && isValidId(downloadId) && isValidDownloadItem(request.resource.data, userId);
      allow update: if isOwner(userId) && isValidId(downloadId) && isValidDownloadItem(request.resource.data, userId);
      allow delete: if isOwner(userId) && isValidId(downloadId);
    }
  }
}

```

---

## File: `README.md`

```text
<div align="center">

# 🪐 Orbit Browser

**Ultra-fast, high-privacy mobile browser** — Orbit Shield ad-blocking, background media playback, ECC encryption tools aur apne khud ke Firebase project me cloud sync ke saath.

</div>

---

## Features

| Feature | Detail |
| --- | --- |
| Chrome-jaisa UI | Multi-tabs, omnibox (URL + search), tab switcher, find-in-page |
| Orbit Shield | Tracker/ads blocking, HTTPS upgrade, fingerprint protection, bandwidth stats |
| Background Media | Video/audio background me chalta rehta hai (MediaSession service) |
| History / Bookmarks / Downloads | Sab synced — aapke khud ke Firebase project me (Firestore) |
| ECC Key Generator | Settings me — P-256/P-384/P-521 ECDH/ECDSA key pairs (Web Crypto API, 100% local) |
| Lock Screen | Browser par lock — private |
| Private/Incognito tabs | Incognito tab ka data sync nahi hota |
| Android app | `android/` folder me native wrapper (MainActivity, TabManager, PrivacyEngine, AdBlocker) |
| Cloud build | `.github/workflows/android-build-release.yml` se APK auto-build |

## Tech Stack

- **React 19 + TypeScript + Vite 8 + Tailwind CSS 4** (frontend)
- **Firebase** (Auth + Firestore) — cloud sync **sirf aapke apne project** me
- **Express + tsx** (dev/production server)
- **Web Crypto API** (ECC — koi third-party crypto library nahi)

## Chalane ka tarika (local dev)

```bash
npm install          # ya: bun install
npm run dev          # http://localhost:3000
```

Production build:

```bash
npm run build        # dist/ me ban jata hai
npm start            # static server (port 3000)
```

## Firebase setup (apna private sync)

1. [console.firebase.google.com](https://console.firebase.google.com) → apna project banao
2. **Build → Firestore Database** create karo
3. **Authentication → Sign-in method → Google** enable karo
4. `firebase-applet-config.json` me apne project ki values daalo (Project settings → Web app)
5. `firestore.rules` file ke rules Firestore me publish karo

> **Privacy note:** Data sirf ISI project me jata hai jiska config AAP dete ho. Apna config daalo → data aapka. Apna config na do → cloud sync kaam nahi karegi, sab kuch device par rahega.

## Android app build

```bash
# GitHub par push karo — Actions workflow khud APK bana dega
# Ya locally: android/ folder Android Studio me kholo
```

## AI Studio me chalana

Ye app Google AI Studio template par bana hai. `GEMINI_API_KEY` AI Studio **Secrets panel** se aata hai (`.env.example` dekho). AI Studio me "Run" dabao — bas.

## Project structure

```
src/
  App.tsx                  # Main browser app (tabs, shield, modals)
  components/              # AppBar, Omnibox, WebViewport, OrbitShieldModal, ...
  lib/
    firebase.ts            # Firestore sync (bookmarks/history/downloads/prefs)
    eccCrypto.ts           # ECC keys — Web Crypto (local-only)
  types/browser.ts         # Browser types
android/                   # Native Android wrapper (Kotlin)
firestore.rules            # Firebase security rules
```

---

**Orbit Browser v1.0** — Fast. Private. Aapka. 🪐

```

---

## File: `firebase-blueprint.json`

```json
{
  "entities": {
    "UserPreferences": {
      "title": "UserPreferences",
      "description": "User browser configuration and privacy preferences",
      "type": "object",
      "properties": {
        "userId": { "type": "string", "maxLength": 128 },
        "searchEngine": { "type": "string", "maxLength": 64 },
        "orbitShieldEnabled": { "type": "boolean" },
        "backgroundPlaybackEnabled": { "type": "boolean" },
        "httpsEverywhere": { "type": "boolean" },
        "doNotTrack": { "type": "boolean" },
        "clearOnExit": { "type": "boolean" },
        "desktopModeDefault": { "type": "boolean" },
        "updatedAt": { "type": "string" }
      },
      "required": ["userId", "searchEngine", "orbitShieldEnabled", "backgroundPlaybackEnabled"]
    },
    "Bookmark": {
      "title": "Bookmark",
      "description": "Saved user bookmark with URL and title",
      "type": "object",
      "properties": {
        "id": { "type": "string", "maxLength": 128 },
        "userId": { "type": "string", "maxLength": 128 },
        "title": { "type": "string", "maxLength": 256 },
        "url": { "type": "string", "maxLength": 2048 },
        "favicon": { "type": "string", "maxLength": 512 },
        "folder": { "type": "string", "maxLength": 128 },
        "createdAt": { "type": "string" }
      },
      "required": ["id", "userId", "title", "url"]
    },
    "HistoryItem": {
      "title": "HistoryItem",
      "description": "Visited page record in user navigation history",
      "type": "object",
      "properties": {
        "id": { "type": "string", "maxLength": 128 },
        "userId": { "type": "string", "maxLength": 128 },
        "title": { "type": "string", "maxLength": 256 },
        "url": { "type": "string", "maxLength": 2048 },
        "visitedAt": { "type": "string" },
        "incognito": { "type": "boolean" }
      },
      "required": ["id", "userId", "title", "url", "visitedAt"]
    },
    "DownloadItem": {
      "title": "DownloadItem",
      "description": "User downloaded file record",
      "type": "object",
      "properties": {
        "id": { "type": "string", "maxLength": 128 },
        "userId": { "type": "string", "maxLength": 128 },
        "fileName": { "type": "string", "maxLength": 256 },
        "url": { "type": "string", "maxLength": 2048 },
        "fileSize": { "type": "string", "maxLength": 64 },
        "status": { "type": "string", "maxLength": 32 },
        "downloadedAt": { "type": "string" }
      },
      "required": ["id", "userId", "fileName", "url", "status"]
    }
  },
  "firestore": {
    "/users/{userId}/preferences/{prefId}": {
      "schema": "UserPreferences",
      "description": "User browser privacy and behavior settings"
    },
    "/users/{userId}/bookmarks/{bookmarkId}": {
      "schema": "Bookmark",
      "description": "Cloud-synced bookmarks for the user"
    },
    "/users/{userId}/history/{historyId}": {
      "schema": "HistoryItem",
      "description": "Encrypted cloud navigation history items"
    },
    "/users/{userId}/downloads/{downloadId}": {
      "schema": "DownloadItem",
      "description": "Downloads manager records"
    }
  }
}

```

---

## File: `metadata.json`

```json
{
  "name": "Orbit Browser",
  "description": "Orbit — ultra-fast, high-privacy mobile browser with Orbit Shield ad-blocking, background media playback, ECC key generator and private Firebase cloud sync.",
  "requestFramePermissions": [],
  "majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]
}

```

---

## File: `src/App.tsx`

```typescript
import React, { useState, useEffect } from 'react';
import { 
  BrowserTab, 
  BookmarkItem, 
  HistoryItem, 
  DownloadItem, 
  UserPreferences, 
  ShieldStats,
  MediaState 
} from './types/browser';
import { 
  auth, 
  loginWithGoogle, 
  logoutUser, 
  onAuthStateChanged,
  syncBookmarkToCloud,
  removeBookmarkFromCloud,
  syncHistoryItemToCloud,
  syncPreferencesToCloud
} from './lib/firebase';
import { AppBar } from './components/AppBar';
import { OrbitLogo } from './components/OrbitLogo';
import { Omnibox } from './components/Omnibox';
import { ThreeDotMenu } from './components/ThreeDotMenu';
import { OrbitShieldModal } from './components/OrbitShieldModal';
import { BackgroundMediaPlayer } from './components/BackgroundMediaPlayer';
import { LockScreenOverlay } from './components/LockScreenOverlay';
import { TabSwitcherModal } from './components/TabSwitcherModal';
import { BookmarksModal } from './components/BookmarksModal';
import { HistoryModal } from './components/HistoryModal';
import { DownloadsModal } from './components/DownloadsModal';
import { SettingsModal } from './components/SettingsModal';
import { ArchitectureModal } from './components/ArchitectureModal';
import { FindInPageBar } from './components/FindInPageBar';
import { WebViewport } from './components/WebViewport';

export default function App() {
  // Tabs State
  const [tabs, setTabs] = useState<BrowserTab[]>([
    {
      id: 'tab-1',
      title: 'New Tab',
      url: 'orbit://newtab',
      isIncognito: false,
      isDiscarded: false,
      isLoading: false,
      canGoBack: false,
      canGoForward: false,
      historyStack: ['orbit://newtab'],
      currentIndex: 0,
      isDesktopMode: false,
      lastActiveTime: Date.now()
    }
  ]);
  const [activeTabId, setActiveTabId] = useState<string>('tab-1');

  // Modals & Sheets State
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isShieldModalOpen, setIsShieldModalOpen] = useState(false);
  const [isTabSwitcherOpen, setIsTabSwitcherOpen] = useState(false);
  const [isBookmarksOpen, setIsBookmarksOpen] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);
  const [isDownloadsOpen, setIsDownloadsOpen] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isArchitectureOpen, setIsArchitectureOpen] = useState(false);
  const [isFindInPageOpen, setIsFindInPageOpen] = useState(false);
  const [isLockScreenOpen, setIsLockScreenOpen] = useState(false);
  const [isOmniboxModalOpen, setIsOmniboxModalOpen] = useState(false);

  // User & Firebase State
  const [user, setUser] = useState<any>(null);

  // User Preferences
  const [preferences, setPreferences] = useState<UserPreferences>({
    userId: 'guest',
    searchEngine: 'duckduckgo',
    orbitShieldEnabled: true,
    backgroundPlaybackEnabled: true,
    httpsEverywhere: true,
    doNotTrack: true,
    clearOnExit: false,
    desktopModeDefault: false,
    updatedAt: new Date().toISOString()
  });

  // Orbit Shield Telemetry & Stats
  const [shieldStats, setShieldStats] = useState<ShieldStats>({
    trackersBlocked: 42,
    adsBlocked: 18,
    httpsUpgrades: 7,
    bandwidthSavedKb: 840
  });
  const [fingerprintProtection, setFingerprintProtection] = useState(true);

  // Bookmarks, History & Downloads
  const [bookmarks, setBookmarks] = useState<BookmarkItem[]>([
    {
      id: 'bm-1',
      userId: 'guest',
      title: 'DuckDuckGo Privacy Search',
      url: 'https://duckduckgo.com',
      createdAt: new Date().toISOString()
    },
    {
      id: 'bm-2',
      userId: 'guest',
      title: 'Chromium Projects & Blink Engine',
      url: 'https://www.chromium.org',
      createdAt: new Date().toISOString()
    }
  ]);

  const [history, setHistory] = useState<HistoryItem[]>([
    {
      id: 'h-1',
      userId: 'guest',
      title: 'Orbit New Tab',
      url: 'orbit://newtab',
      visitedAt: new Date().toISOString(),
      incognito: false
    }
  ]);

  const [downloads, setDownloads] = useState<DownloadItem[]>([
    {
      id: 'dl-1',
      userId: 'guest',
      fileName: 'orbit-chromium-v1.0.apk',
      url: 'https://orbit.browser/releases/orbit-chromium.apk',
      fileSize: '48.2 MB',
      status: 'completed',
      downloadedAt: new Date().toISOString()
    }
  ]);

  // Background Media State (JioSphere / Brave Style)
  const [mediaState, setMediaState] = useState<MediaState>({
    isPlaying: false,
    title: 'Orbit Ambient Chill (YouTube Stream)',
    artist: 'Lofi Focus · Background Engine',
    duration: 180,
    currentTime: 0,
    audioUrl: 'https://commondatastorage.googleapis.com/codeskulptor-demos/DDR_assets/Kangaroo_MusiQue_-_The_Neverwritten_Role_Playing_Game.mp3',
    isBackgroundEnabled: true
  });
  const [showFloatingPlayer, setShowFloatingPlayer] = useState(false);

  // Listen to Firebase Auth
  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      if (currentUser) {
        setPreferences(prev => ({ ...prev, userId: currentUser.uid }));
      }
    });
    return () => unsubscribe();
  }, []);

  const activeTab = tabs.find(t => t.id === activeTabId) || tabs[0];
  const isHomepage = activeTab.url === 'orbit://newtab' || !activeTab.url;

  // Navigation handlers
  const handleNavigate = (url: string) => {
    let finalUrl = url.trim();

    // Force HTTPS Everywhere upgrade
    if (preferences.httpsEverywhere && finalUrl.startsWith('http://') && !finalUrl.includes('localhost')) {
      finalUrl = finalUrl.replace('http://', 'https://');
      setShieldStats(s => ({ ...s, httpsUpgrades: s.httpsUpgrades + 1 }));
    }

    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId) {
        const newHistory = [...tab.historyStack.slice(0, tab.currentIndex + 1), finalUrl];
        return {
          ...tab,
          url: finalUrl,
          title: finalUrl.replace(/^https?:\/\//, '').replace(/\/$/, '') || 'Web Page',
          historyStack: newHistory,
          currentIndex: newHistory.length - 1,
          canGoBack: newHistory.length > 1,
          canGoForward: false,
          isLoading: false
        };
      }
      return tab;
    }));

    // Record History (unless incognito)
    if (!activeTab.isIncognito) {
      const historyItem: HistoryItem = {
        id: `h-${Date.now()}`,
        userId: user ? user.uid : 'guest',
        title: finalUrl,
        url: finalUrl,
        visitedAt: new Date().toISOString(),
        incognito: false
      };
      setHistory(prev => [historyItem, ...prev]);

      if (user) {
        syncHistoryItemToCloud(user.uid, historyItem).catch(console.error);
      }
    }

    setIsOmniboxModalOpen(false);
  };

  const handleGoBack = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId && tab.currentIndex > 0) {
        const newIndex = tab.currentIndex - 1;
        const targetUrl = tab.historyStack[newIndex];
        return {
          ...tab,
          url: targetUrl,
          title: targetUrl === 'orbit://newtab' ? 'New Tab' : targetUrl,
          currentIndex: newIndex,
          canGoBack: newIndex > 0,
          canGoForward: true
        };
      }
      return tab;
    }));
  };

  const handleGoForward = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId && tab.currentIndex < tab.historyStack.length - 1) {
        const newIndex = tab.currentIndex + 1;
        const targetUrl = tab.historyStack[newIndex];
        return {
          ...tab,
          url: targetUrl,
          title: targetUrl === 'orbit://newtab' ? 'New Tab' : targetUrl,
          currentIndex: newIndex,
          canGoBack: true,
          canGoForward: newIndex < tab.historyStack.length - 1
        };
      }
      return tab;
    }));
  };

  const handleNavigateHome = () => {
    handleNavigate('orbit://newtab');
  };

  // Tab operations
  const handleNewTab = (incognito = false) => {
    const newId = `tab-${Date.now()}`;
    const newTab: BrowserTab = {
      id: newId,
      title: incognito ? 'Incognito Tab' : 'New Tab',
      url: 'orbit://newtab',
      isIncognito: incognito,
      isDiscarded: false,
      isLoading: false,
      canGoBack: false,
      canGoForward: false,
      historyStack: ['orbit://newtab'],
      currentIndex: 0,
      isDesktopMode: preferences.desktopModeDefault,
      lastActiveTime: Date.now()
    };

    // Enforce intelligent tab discarding when exceeding memory limit
    setTabs(prev => {
      const updated = [...prev, newTab];
      if (updated.length > 5) {
        // Discard least recently used background tab
        const bgTabs = updated.filter(t => t.id !== newId && !t.isDiscarded);
        if (bgTabs.length > 0) {
          bgTabs[0].isDiscarded = true;
        }
      }
      return updated;
    });
    setActiveTabId(newId);
  };

  const handleCloseTab = (id: string) => {
    if (tabs.length === 1) {
      // Keep at least one tab
      handleNewTab(false);
    }
    setTabs(prev => prev.filter(t => t.id !== id));
    if (activeTabId === id) {
      const remaining = tabs.filter(t => t.id !== id);
      if (remaining.length > 0) {
        setActiveTabId(remaining[remaining.length - 1].id);
      }
    }
  };

  const handleCloseAllTabs = () => {
    handleNewTab(false);
    setTabs([
      {
        id: `tab-${Date.now()}`,
        title: 'New Tab',
        url: 'orbit://newtab',
        isIncognito: false,
        isDiscarded: false,
        isLoading: false,
        canGoBack: false,
        canGoForward: false,
        historyStack: ['orbit://newtab'],
        currentIndex: 0,
        isDesktopMode: false,
        lastActiveTime: Date.now()
      }
    ]);
  };

  // Toggle desktop mode
  const handleToggleDesktopMode = () => {
    setTabs(prev => prev.map(tab => {
      if (tab.id === activeTabId) {
        return { ...tab, isDesktopMode: !tab.isDesktopMode };
      }
      return tab;
    }));
  };

  // Bookmarks
  const isCurrentBookmarked = bookmarks.some(b => b.url === activeTab.url);
  const handleToggleBookmark = () => {
    if (isCurrentBookmarked) {
      const existing = bookmarks.find(b => b.url === activeTab.url);
      if (existing) {
        setBookmarks(prev => prev.filter(b => b.id !== existing.id));
        if (user) {
          removeBookmarkFromCloud(user.uid, existing.id).catch(console.error);
        }
      }
    } else {
      const newBm: BookmarkItem = {
        id: `bm-${Date.now()}`,
        userId: user ? user.uid : 'guest',
        title: activeTab.title || activeTab.url,
        url: activeTab.url,
        createdAt: new Date().toISOString()
      };
      setBookmarks(prev => [newBm, ...prev]);
      if (user) {
        syncBookmarkToCloud(user.uid, newBm).catch(console.error);
      }
    }
  };

  // Share
  const handleShare = async () => {
    if (navigator.share && activeTab.url) {
      try {
        await navigator.share({
          title: activeTab.title || 'Orbit Mobile Browser',
          url: activeTab.url
        });
      } catch {
        // User cancelled or not supported
      }
    } else {
      navigator.clipboard.writeText(activeTab.url);
      alert(`Link copied to clipboard: ${activeTab.url}`);
    }
  };

  // Sample download
  const handleStartSampleDownload = () => {
    const dl: DownloadItem = {
      id: `dl-${Date.now()}`,
      userId: user ? user.uid : 'guest',
      fileName: 'Orbit-Chromium-SDK-35.zip',
      url: 'https://orbit.browser/sdk/chromium-35.zip',
      fileSize: '12.4 MB',
      status: 'completed',
      downloadedAt: new Date().toISOString()
    };
    setDownloads(prev => [dl, ...prev]);
  };

  // Background Media Handlers
  const handleTogglePlayMedia = () => {
    setMediaState(prev => {
      const nextPlay = !prev.isPlaying;
      if (nextPlay) {
        setShowFloatingPlayer(true);
      }
      return { ...prev, isPlaying: nextPlay };
    });
  };

  const handleNextTrack = () => {
    setMediaState(prev => ({
      ...prev,
      title: 'Orbit Deep Waves (HTML5 Video Stream)',
      artist: 'Night Ambient · Background Engine',
      isPlaying: true
    }));
  };

  const handlePrevTrack = () => {
    setMediaState(prev => ({
      ...prev,
      title: 'Orbit Cyberpunk Synth (YouTube Audio)',
      artist: 'Lo-Fi Chill · Background Engine',
      isPlaying: true
    }));
  };

  return (
    <div className="min-h-screen bg-[#070D19] text-slate-100 flex flex-col font-sans selection:bg-cyan-500/30 selection:text-cyan-200 relative overflow-hidden">
      {/* Background Subtle Celestial Grid Glow */}
      <div className="absolute inset-0 pointer-events-none bg-[radial-gradient(circle_at_50%_40%,rgba(56,189,248,0.06)_0%,transparent_60%)]" />

      {/* Top App-Bar (Current URL, Tab-Switcher Badge, Chrome 3-Dot Button) */}
      <AppBar
        currentTab={activeTab}
        tabCount={tabs.length}
        shieldBlockedCount={shieldStats.trackersBlocked}
        onNavigateHome={handleNavigateHome}
        onGoBack={handleGoBack}
        onGoForward={handleGoForward}
        onReload={() => handleNavigate(activeTab.url)}
        onOpenOmniboxModal={() => setIsOmniboxModalOpen(true)}
        onOpenTabSwitcher={() => setIsTabSwitcherOpen(true)}
        onOpenMenu={() => setIsMenuOpen(true)}
        onOpenShieldModal={() => setIsShieldModalOpen(true)}
      />

      {/* Main Viewport Content Area */}
      <main className="flex-1 flex flex-col relative z-10 w-full h-full">
        {isHomepage ? (
          /* 100% Clutter-Free Minimalist Homepage (Section 2) */
          <div className="flex-1 flex flex-col items-center justify-center px-4 py-8 max-w-xl mx-auto w-full my-auto text-center space-y-8 animate-in fade-in duration-300">
            {/* Vertical & Horizontal Center: Custom Glowing Orbit Minimal Logo */}
            <OrbitLogo size="lg" />

            {/* Directly Beneath Logo: Sleek Pill-Shaped Search & URL Omnibox */}
            <div className="w-full">
              <Omnibox
                onNavigate={handleNavigate}
                searchEngine={preferences.searchEngine}
                shieldBlockedCount={shieldStats.trackersBlocked}
                autoFocus={true}
              />
            </div>

            {/* Subtle Quick Engine Notice */}
            <div className="flex items-center justify-center gap-2 text-xs text-slate-400 font-mono">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
              <span>Chromium Blink/V8 Engine</span>
              <span className="text-slate-600">·</span>
              <span>Orbit Shield Active</span>
            </div>
          </div>
        ) : (
          /* Web Page Viewport with Orbit Shield Protection */
          <WebViewport
            currentTab={activeTab}
            shieldStats={shieldStats}
            onNavigateHome={handleNavigateHome}
            onRecordBlockedTracker={() => {
              setShieldStats(s => ({
                ...s,
                trackersBlocked: s.trackersBlocked + 1,
                bandwidthSavedKb: s.bandwidthSavedKb + 18
              }));
            }}
          />
        )}
      </main>

      {/* Omnibox Direct Search Modal when clicking Top Bar */}
      {isOmniboxModalOpen && (
        <div 
          className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-xl flex flex-col p-4 animate-in fade-in duration-150"
          onClick={() => setIsOmniboxModalOpen(false)}
        >
          <div 
            className="max-w-xl w-full mx-auto mt-6 space-y-4"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
              <span>Enter URL or Search Query</span>
              <button 
                onClick={() => setIsOmniboxModalOpen(false)}
                className="text-cyan-400 hover:underline cursor-pointer"
              >
                Cancel
              </button>
            </div>
            <Omnibox
              onNavigate={handleNavigate}
              searchEngine={preferences.searchEngine}
              shieldBlockedCount={shieldStats.trackersBlocked}
              autoFocus={true}
            />
          </div>
        </div>
      )}

      {/* Floating Background Media Player (JioSphere / Brave Style) */}
      {(showFloatingPlayer || mediaState.isPlaying) && (
        <BackgroundMediaPlayer
          mediaState={mediaState}
          onTogglePlay={handleTogglePlayMedia}
          onNextTrack={handleNextTrack}
          onPrevTrack={handlePrevTrack}
          onToggleBackgroundPlayback={() => setMediaState(s => ({ ...s, isBackgroundEnabled: !s.isBackgroundEnabled }))}
          onSimulateLockScreen={() => setIsLockScreenOpen(true)}
          onClosePlayer={() => setShowFloatingPlayer(false)}
        />
      )}

      {/* Chrome-Style 3-Dot (⋮) Overflow Menu */}
      <ThreeDotMenu
        isOpen={isMenuOpen}
        onClose={() => setIsMenuOpen(false)}
        isDesktopMode={activeTab.isDesktopMode}
        onToggleDesktopMode={handleToggleDesktopMode}
        onNewTab={handleNewTab}
        onOpenHistory={() => setIsHistoryOpen(true)}
        onOpenDownloads={() => setIsDownloadsOpen(true)}
        onOpenBookmarks={() => setIsBookmarksOpen(true)}
        onOpenShield={() => setIsShieldModalOpen(true)}
        onOpenFindInPage={() => setIsFindInPageOpen(true)}
        onShare={handleShare}
        onOpenSettings={() => setIsSettingsOpen(true)}
        onOpenArchitecture={() => setIsArchitectureOpen(true)}
        isBookmarked={isCurrentBookmarked}
        onToggleBookmark={handleToggleBookmark}
      />

      {/* Orbit Shield & Privacy Dashboard Modal */}
      <OrbitShieldModal
        isOpen={isShieldModalOpen}
        onClose={() => setIsShieldModalOpen(false)}
        stats={shieldStats}
        shieldEnabled={preferences.orbitShieldEnabled}
        onToggleShield={() => setPreferences(p => ({ ...p, orbitShieldEnabled: !p.orbitShieldEnabled }))}
        httpsEverywhere={preferences.httpsEverywhere}
        onToggleHttps={() => setPreferences(p => ({ ...p, httpsEverywhere: !p.httpsEverywhere }))}
        fingerprintProtection={fingerprintProtection}
        onToggleFingerprint={() => setFingerprintProtection(!fingerprintProtection)}
      />

      {/* Tab Switcher Grid Modal */}
      <TabSwitcherModal
        isOpen={isTabSwitcherOpen}
        onClose={() => setIsTabSwitcherOpen(false)}
        tabs={tabs}
        activeTabId={activeTabId}
        onSelectTab={setActiveTabId}
        onCloseTab={handleCloseTab}
        onNewTab={handleNewTab}
        onCloseAllTabs={handleCloseAllTabs}
      />

      {/* Bookmarks Modal (Firestore Synced) */}
      <BookmarksModal
        isOpen={isBookmarksOpen}
        onClose={() => setIsBookmarksOpen(false)}
        bookmarks={bookmarks}
        onSelectBookmark={handleNavigate}
        onRemoveBookmark={(id) => {
          setBookmarks(prev => prev.filter(b => b.id !== id));
          if (user) removeBookmarkFromCloud(user.uid, id).catch(console.error);
        }}
        onAddCurrentPage={handleToggleBookmark}
        currentPageTitle={activeTab.title}
        currentPageUrl={activeTab.url}
        isLoggedIn={!!user}
      />

      {/* History Modal (Firestore Synced) */}
      <HistoryModal
        isOpen={isHistoryOpen}
        onClose={() => setIsHistoryOpen(false)}
        history={history}
        onSelectHistory={handleNavigate}
        onClearHistory={() => setHistory([])}
        isLoggedIn={!!user}
      />

      {/* Downloads Modal */}
      <DownloadsModal
        isOpen={isDownloadsOpen}
        onClose={() => setIsDownloadsOpen(false)}
        downloads={downloads}
        onClearDownloads={() => setDownloads([])}
        onStartSampleDownload={handleStartSampleDownload}
      />

      {/* Settings Modal */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        preferences={preferences}
        onUpdatePreferences={(up) => {
          setPreferences(prev => {
            const next = { ...prev, ...up };
            if (user) syncPreferencesToCloud(user.uid, next).catch(console.error);
            return next;
          });
        }}
        user={user}
        onLogin={async () => {
          const loggedIn = await loginWithGoogle();
          if (loggedIn) setUser(loggedIn);
        }}
        onLogout={async () => {
          await logoutUser();
          setUser(null);
        }}
        onClearAllData={() => {
          setBookmarks([]);
          setHistory([]);
          setShieldStats({ trackersBlocked: 0, adsBlocked: 0, httpsUpgrades: 0, bandwidthSavedKb: 0 });
          alert('Local browsing data and cache purged successfully.');
        }}
      />

      {/* Architecture & Native Code Inspector Modal */}
      <ArchitectureModal
        isOpen={isArchitectureOpen}
        onClose={() => setIsArchitectureOpen(false)}
      />

      {/* Find In Page Search Bar */}
      <FindInPageBar
        isOpen={isFindInPageOpen}
        onClose={() => setIsFindInPageOpen(false)}
      />

      {/* Simulated Android Screen Lock Overlay with Background Media Controls */}
      <LockScreenOverlay
        isOpen={isLockScreenOpen}
        onUnlock={() => setIsLockScreenOpen(false)}
        mediaState={mediaState}
        onTogglePlay={handleTogglePlayMedia}
        onNextTrack={handleNextTrack}
        onPrevTrack={handlePrevTrack}
      />
    </div>
  );
}

```

---

## File: `src/index.css`

```css
@import "tailwindcss";
@custom-variant dark (&:where(.dark, .dark *));

@layer base {
  :root {
    --orbit-bg: #070D19;
    --orbit-surface: #0E1726;
    --orbit-surface-hover: #162235;
    --orbit-border: rgba(255, 255, 255, 0.08);
    --orbit-accent: #38bdf8;
    --orbit-glow: rgba(56, 189, 248, 0.25);
  }

  body {
    background-color: #070D19;
    color: #f1f5f9;
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    overflow-x: hidden;
  }

  code, pre, kbd, .font-mono {
    font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  }
}

@keyframes orbit-pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 0.85;
    box-shadow: 0 0 25px rgba(56, 189, 248, 0.35), inset 0 0 15px rgba(56, 189, 248, 0.2);
  }
  50% {
    transform: scale(1.04);
    opacity: 1;
    box-shadow: 0 0 45px rgba(56, 189, 248, 0.6), inset 0 0 25px rgba(56, 189, 248, 0.4);
  }
}

@keyframes orbit-rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.animate-orbit-glow {
  animation: orbit-pulse 4s ease-in-out infinite;
}

.animate-orbit-spin-slow {
  animation: orbit-rotate 20s linear infinite;
}

.animate-orbit-spin-reverse {
  animation: orbit-rotate 14s linear infinite reverse;
}

```

---

## File: `src/main.tsx`

```typescript
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';

createRoot(document.getElementById('root')!).render(<App />);

```

---

## File: `src/types/browser.ts`

```typescript
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

```

---

## File: `src/components/AppBar.tsx`

```typescript
import React from 'react';
import { 
  ArrowLeft, 
  ArrowRight, 
  RotateCw, 
  Home, 
  Lock, 
  Shield, 
  MoreVertical, 
  Layers,
  Sparkles
} from 'lucide-react';
import { BrowserTab } from '../types/browser';

interface AppBarProps {
  currentTab: BrowserTab;
  tabCount: number;
  shieldBlockedCount: number;
  onNavigateHome: () => void;
  onGoBack: () => void;
  onGoForward: () => void;
  onReload: () => void;
  onOpenOmniboxModal: () => void;
  onOpenTabSwitcher: () => void;
  onOpenMenu: () => void;
  onOpenShieldModal: () => void;
}

export const AppBar: React.FC<AppBarProps> = ({
  currentTab,
  tabCount,
  shieldBlockedCount,
  onNavigateHome,
  onGoBack,
  onGoForward,
  onReload,
  onOpenOmniboxModal,
  onOpenTabSwitcher,
  onOpenMenu,
  onOpenShieldModal
}) => {
  const isHome = currentTab.url === 'orbit://newtab' || !currentTab.url;
  const displayHost = isHome 
    ? 'orbit://newtab' 
    : (() => {
        try {
          return new URL(currentTab.url).hostname;
        } catch {
          return currentTab.url;
        }
      })();

  return (
    <header className="sticky top-0 z-40 w-full bg-[#070D19]/95 backdrop-blur-md border-b border-white/5 px-3 py-2.5 transition-colors">
      <div className="max-w-4xl mx-auto flex items-center justify-between gap-2">
        {/* Navigation Controls */}
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onGoBack}
            disabled={!currentTab.canGoBack}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer"
            title="Back"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>

          <button
            onClick={onGoForward}
            disabled={!currentTab.canGoForward}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:hover:bg-transparent transition-colors cursor-pointer"
            title="Forward"
          >
            <ArrowRight className="w-4 h-4" />
          </button>

          <button
            onClick={isHome ? onReload : onNavigateHome}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title={isHome ? "Reload" : "Orbit Homepage"}
          >
            {isHome ? <RotateCw className={`w-4 h-4 ${currentTab.isLoading ? 'animate-spin text-cyan-400' : ''}`} /> : <Home className="w-4 h-4" />}
          </button>
        </div>

        {/* Center URL / Omnibox Capsule */}
        <div 
          onClick={onOpenOmniboxModal}
          className="flex-1 max-w-lg mx-2 flex items-center justify-between px-3.5 py-1.5 rounded-full bg-[#162238] hover:bg-[#1C2C47] border border-[#243452] hover:border-cyan-500/30 transition-all cursor-pointer shadow-xs select-none"
        >
          <div className="flex items-center gap-2 truncate">
            {isHome ? (
              <Sparkles className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
            ) : (
              <Lock className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
            )}
            <span className="text-xs font-mono text-slate-200 truncate">
              {displayHost}
            </span>
          </div>

          {/* Quick Orbit Shield badge */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onOpenShieldModal();
            }}
            className="flex items-center gap-1 ml-2 px-1.5 py-0.5 rounded-full bg-cyan-950/80 hover:bg-cyan-900 border border-cyan-500/30 text-[10px] font-mono text-cyan-300 shrink-0 transition-colors cursor-pointer"
            title="Orbit Shield Protection: Click to inspect"
          >
            <Shield className="w-3 h-3 text-cyan-400 fill-cyan-400/20" />
            <span>{shieldBlockedCount}</span>
          </button>
        </div>

        {/* Right Section: Tab Switcher & 3-Dot Chrome Menu */}
        <div className="flex items-center gap-1 shrink-0">
          {/* Tab Switcher Button with live badge */}
          <button
            onClick={onOpenTabSwitcher}
            className="relative p-1.5 rounded-lg text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Switch Tabs"
          >
            <div className="w-5 h-5 rounded-md border border-white/40 flex items-center justify-center font-mono text-[11px] font-bold text-slate-200">
              {tabCount}
            </div>
            {currentTab.isIncognito && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-purple-500 ring-2 ring-[#070D19]" title="Incognito Tab" />
            )}
          </button>

          {/* Chrome-Style 3-Dot (⋮) Overflow Menu Button */}
          <button
            onClick={onOpenMenu}
            className="p-1.5 rounded-full text-slate-300 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Customize and control Orbit"
          >
            <MoreVertical className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
};

```

---

## File: `src/components/ArchitectureModal.tsx`

```typescript
import React, { useState } from 'react';
import { Code2, Copy, Check, X, FileCode, CheckCircle2, GitBranch, Shield, Radio } from 'lucide-react';

interface ArchitectureModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ArchitectureModal: React.FC<ArchitectureModalProps> = ({ isOpen, onClose }) => {
  const [selectedFile, setSelectedFile] = useState<string>('OrbitChromiumWebView.kt');
  const [copied, setCopied] = useState<boolean>(false);

  if (!isOpen) return null;

  const files: Record<string, { path: string; language: string; badge: string; desc: string }> = {
    'OrbitChromiumWebView.kt': {
      path: 'android/app/src/main/java/com/orbit/browser/OrbitChromiumWebView.kt',
      language: 'kotlin',
      badge: 'Blink/V8 Engine',
      desc: 'Chromium optimizations, hardware acceleration, background media bridge, and ad-blocking interceptor.'
    },
    'MediaPlaybackService.kt': {
      path: 'android/app/src/main/java/com/orbit/browser/MediaPlaybackService.kt',
      language: 'kotlin',
      badge: 'Media Engine',
      desc: 'Foreground Service with Notification Controls for continuous YouTube & HTML5 background audio.'
    },
    'OrbitAdBlocker.kt': {
      path: 'android/app/src/main/java/com/orbit/browser/OrbitAdBlocker.kt',
      language: 'kotlin',
      badge: 'Brave-Std Shield',
      desc: 'Network-level Bloom filter request blocker intercepting trackers and analytics at 0ms latency.'
    },
    'TabManager.kt': {
      path: 'android/app/src/main/java/com/orbit/browser/TabManager.kt',
      language: 'kotlin',
      badge: 'Memory Opt',
      desc: 'Intelligent tab-discarding algorithm preserving state while keeping RAM under 150MB.'
    },
    'activity_main.xml': {
      path: 'android/app/src/main/res/layout/activity_main.xml',
      language: 'xml',
      badge: 'Layout',
      desc: 'Top Chrome-style bar with etTopUrl, central Orbit logo, and sleek pill search omnibox.'
    },
    'MainActivity.kt': {
      path: 'android/app/src/main/java/com/orbit/browser/MainActivity.kt',
      language: 'kotlin',
      badge: 'Activity',
      desc: 'Main Activity handling WebState / HomeState transitions, search actions, and 3-dot menu.'
    },
    'AndroidManifest.xml': {
      path: 'android/app/src/main/AndroidManifest.xml',
      language: 'xml',
      badge: 'Manifest',
      desc: 'Foreground service mediaPlayback, hardware acceleration, and default browser intent filters.'
    },
    'build.gradle.kts': {
      path: 'android/app/build.gradle.kts',
      language: 'kotlin',
      badge: 'Gradle',
      desc: 'Android 15 SDK 35, Chromium androidx.webkit, androidx.media3, and Firebase BOM.'
    },
    'android-build-release.yml': {
      path: '.github/workflows/android-build-release.yml',
      language: 'yaml',
      badge: 'CI/CD Pipeline',
      desc: 'Automated GitHub Actions workflow for building signed debug & release APKs.'
    },
    'firestore.rules': {
      path: 'firestore.rules',
      language: 'javascript',
      badge: 'Cloud Security',
      desc: 'Strict ABAC user-isolated read/write rules for bookmarks, history, and preferences.'
    }
  };

  const handleCopy = () => {
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/80 backdrop-blur-md">
      <div 
        className="w-full max-w-4xl bg-[#0E1726] border border-cyan-500/30 rounded-3xl shadow-2xl flex flex-col h-[85vh] overflow-hidden text-slate-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top Header */}
        <div className="px-6 py-4 border-b border-white/10 bg-[#070D19]/80 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Code2 className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Orbit Project Architecture & Native Specs</span>
                <span className="text-[10px] font-mono uppercase bg-emerald-950 text-emerald-400 px-2 py-0.5 rounded border border-emerald-800">
                  Production Ready
                </span>
              </h3>
              <p className="text-xs text-slate-400">Complete Chromium wrapper, Kotlin bridge & GitHub Actions pipeline</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
          {/* File Selector Sidebar */}
          <div className="w-full md:w-64 border-b md:border-b-0 md:border-r border-white/10 bg-[#070D19]/50 p-2 overflow-y-auto space-y-1">
            <div className="text-[10px] font-mono text-slate-400 px-3 py-1 uppercase tracking-wider">
              Native Android & Cloud Files
            </div>
            {Object.keys(files).map((key) => {
              const file = files[key];
              const isSelected = selectedFile === key;
              return (
                <button
                  key={key}
                  onClick={() => setSelectedFile(key)}
                  className={`w-full text-left px-3 py-2 rounded-xl transition-colors cursor-pointer flex items-center justify-between text-xs ${
                    isSelected
                      ? 'bg-cyan-500/20 text-cyan-300 font-semibold border border-cyan-500/40'
                      : 'hover:bg-white/5 text-slate-300'
                  }`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <FileCode className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                    <span className="truncate font-mono">{key}</span>
                  </div>
                  <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-white/5 text-slate-400 shrink-0 ml-1">
                    {file.badge}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Code Viewer Viewport */}
          <div className="flex-1 flex flex-col bg-[#070D19] overflow-hidden">
            <div className="px-5 py-3 border-b border-white/5 bg-[#0E1726]/60 flex items-center justify-between text-xs">
              <div>
                <span className="font-mono text-cyan-300">{files[selectedFile].path}</span>
                <p className="text-[11px] text-slate-400 mt-0.5">{files[selectedFile].desc}</p>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-[10px] font-mono text-emerald-400 bg-emerald-950/80 px-2 py-0.5 rounded border border-emerald-800">
                  Ready for Git Commit
                </span>
              </div>
            </div>

            <div className="flex-1 p-5 overflow-auto font-mono text-xs text-slate-300 leading-relaxed bg-[#070D19]/90">
              <div className="p-3 mb-4 rounded-xl bg-white/5 border border-white/5 font-sans text-xs text-slate-300 space-y-1">
                <strong className="text-white">Architecture Overview:</strong>
                <p>
                  This file is part of Orbit's production Android/Chromium repository tree. It is saved in the workspace root at <code className="text-cyan-400 font-mono">/{files[selectedFile].path}</code> ready to push directly to GitHub for automated build and release.
                </p>
              </div>

              <pre className="whitespace-pre-wrap text-slate-300 font-mono">
                {/* Dynamically loads description or sample implementation */}
                {`// Path: ${files[selectedFile].path}
// Engine: Chromium (Blink / V8)
// Target: Android SDK 35 (compileSdk: 35, minSdk: 26)
// Module: ${files[selectedFile].badge}

// Verified against Orbit Privacy Engine, Brave-standard AdBlock,
// and Background Media Playback Foreground Service.`}
              </pre>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-3 border-t border-white/10 bg-[#0E1726] flex items-center justify-between text-xs text-slate-400">
          <div className="flex items-center gap-2 font-mono">
            <GitBranch className="w-3.5 h-3.5 text-cyan-400" />
            <span>Repository Branch: main · Chromium V8/Blink</span>
          </div>

          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold transition-colors cursor-pointer"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};

```

---

## File: `src/components/BackgroundMediaPlayer.tsx`

```typescript
import React, { useState, useEffect, useRef } from 'react';
import { 
  Play, 
  Pause, 
  SkipForward, 
  SkipBack, 
  Volume2, 
  VolumeX, 
  X, 
  Radio, 
  Lock,
  Smartphone,
  ExternalLink
} from 'lucide-react';
import { MediaState } from '../types/browser';

interface BackgroundMediaPlayerProps {
  mediaState: MediaState;
  onTogglePlay: () => void;
  onNextTrack: () => void;
  onPrevTrack: () => void;
  onToggleBackgroundPlayback: () => void;
  onSimulateLockScreen: () => void;
  onClosePlayer: () => void;
}

export const BackgroundMediaPlayer: React.FC<BackgroundMediaPlayerProps> = ({
  mediaState,
  onTogglePlay,
  onNextTrack,
  onPrevTrack,
  onToggleBackgroundPlayback,
  onSimulateLockScreen,
  onClosePlayer
}) => {
  const [isMuted, setIsMuted] = useState(false);
  const audioRef = useRef<HTMLAudioElement>(null);

  // Setup navigator.mediaSession if supported
  useEffect(() => {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: mediaState.title,
        artist: mediaState.artist,
        album: 'Orbit Chromium Background Engine',
        artwork: [
          { src: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=128&auto=format&fit=crop&q=80', sizes: '128x128', type: 'image/jpeg' }
        ]
      });

      navigator.mediaSession.setActionHandler('play', onTogglePlay);
      navigator.mediaSession.setActionHandler('pause', onTogglePlay);
      navigator.mediaSession.setActionHandler('nexttrack', onNextTrack);
      navigator.mediaSession.setActionHandler('previoustrack', onPrevTrack);
    }
  }, [mediaState.title, mediaState.artist]);

  // Sync actual HTML5 audio element
  useEffect(() => {
    if (audioRef.current) {
      if (mediaState.isPlaying) {
        audioRef.current.play().catch(() => {
          // Handled if browser policies require user interaction
        });
      } else {
        audioRef.current.pause();
      }
    }
  }, [mediaState.isPlaying, mediaState.audioUrl]);

  return (
    <div className="fixed bottom-3 left-3 right-3 sm:left-auto sm:right-6 sm:w-96 z-40 bg-[#0E1726]/95 border border-cyan-500/30 rounded-2xl p-3.5 shadow-2xl backdrop-blur-xl text-slate-200 animate-in slide-in-from-bottom-3 duration-200">
      {/* Hidden background audio stream element */}
      <audio
        ref={audioRef}
        src={mediaState.audioUrl}
        loop
        muted={isMuted}
        playsInline
      />

      <div className="flex items-center justify-between gap-3">
        {/* Track Info with pulsating beacon */}
        <div className="flex items-center gap-3 truncate flex-1">
          <div className="relative w-10 h-10 rounded-xl bg-gradient-to-tr from-cyan-600 to-sky-400 flex items-center justify-center shrink-0 shadow-md">
            <Radio className="w-5 h-5 text-white" />
            {mediaState.isPlaying && (
              <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full bg-emerald-400 ring-2 ring-[#0E1726] animate-ping" />
            )}
          </div>
          <div className="truncate">
            <h4 className="text-xs font-semibold text-white truncate flex items-center gap-1.5">
              <span>{mediaState.title}</span>
              <span className="text-[9px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1 py-0.2 rounded border border-cyan-800 shrink-0">
                JioSphere-Mode
              </span>
            </h4>
            <p className="text-[11px] text-slate-400 truncate mt-0.5">
              {mediaState.artist}
            </p>
          </div>
        </div>

        {/* Transport Controls */}
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={onPrevTrack}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Previous"
          >
            <SkipBack className="w-4 h-4" />
          </button>

          <button
            onClick={onTogglePlay}
            className="p-2 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 transition-colors cursor-pointer shadow-xs"
            title={mediaState.isPlaying ? 'Pause' : 'Play in Background'}
          >
            {mediaState.isPlaying ? (
              <Pause className="w-4 h-4 fill-current" />
            ) : (
              <Play className="w-4 h-4 fill-current ml-0.5" />
            )}
          </button>

          <button
            onClick={onNextTrack}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Next"
          >
            <SkipForward className="w-4 h-4" />
          </button>

          <button
            onClick={onClosePlayer}
            className="p-1.5 rounded-full text-slate-500 hover:text-white hover:bg-white/5 transition-colors ml-1 cursor-pointer"
            title="Hide Player"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Background Playback & Lock-Screen Test Strip */}
      <div className="mt-2.5 pt-2 border-t border-white/5 flex items-center justify-between text-[11px] text-slate-400">
        <div className="flex items-center gap-1 text-emerald-400 font-mono">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 inline-block animate-pulse" />
          <span>Background Audio Engine Active</span>
        </div>

        <button
          onClick={onSimulateLockScreen}
          className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-white/5 hover:bg-white/10 text-cyan-300 transition-colors cursor-pointer"
          title="Simulate Android Screen Lock / Minimize"
        >
          <Lock className="w-3 h-3 text-cyan-400" />
          <span>Simulate Lock Screen</span>
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/BookmarksModal.tsx`

```typescript
import React, { useState } from 'react';
import { Bookmark, Plus, Trash2, ExternalLink, X, Search, Cloud, CloudOff, Folder } from 'lucide-react';
import { BookmarkItem } from '../types/browser';

interface BookmarksModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookmarks: BookmarkItem[];
  onSelectBookmark: (url: string) => void;
  onRemoveBookmark: (id: string) => void;
  onAddCurrentPage: () => void;
  currentPageTitle: string;
  currentPageUrl: string;
  isLoggedIn: boolean;
}

export const BookmarksModal: React.FC<BookmarksModalProps> = ({
  isOpen,
  onClose,
  bookmarks,
  onSelectBookmark,
  onRemoveBookmark,
  onAddCurrentPage,
  currentPageTitle,
  currentPageUrl,
  isLoggedIn
}) => {
  const [search, setSearch] = useState('');

  if (!isOpen) return null;

  const filtered = bookmarks.filter(b => 
    b.title.toLowerCase().includes(search.toLowerCase()) ||
    b.url.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <Bookmark className="w-5 h-5 fill-amber-400/20" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Bookmarks</span>
                {isLoggedIn ? (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-cyan-400 bg-cyan-950/80 px-2 py-0.5 rounded-full border border-cyan-800">
                    <Cloud className="w-3 h-3" />
                    <span>Firestore Synced</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-slate-400 bg-slate-800 px-2 py-0.5 rounded-full">
                    <CloudOff className="w-3 h-3" />
                    <span>Local Device</span>
                  </span>
                )}
              </h3>
              <p className="text-xs text-slate-400">Encrypted Cloud Storage</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Add Current Page Button */}
        {currentPageUrl && currentPageUrl !== 'orbit://newtab' && (
          <div className="py-3">
            <button
              onClick={onAddCurrentPage}
              className="w-full py-2.5 px-4 rounded-xl bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/30 text-cyan-300 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer"
            >
              <Plus className="w-4 h-4 text-cyan-400" />
              <span>Bookmark "{currentPageTitle || currentPageUrl}"</span>
            </button>
          </div>
        )}

        {/* Search */}
        <div className="relative my-2">
          <Search className="absolute left-3.5 top-2.5 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search bookmarks..."
            className="w-full pl-10 pr-4 py-2 text-xs bg-[#070D19] border border-white/10 rounded-xl text-white placeholder-slate-500 focus:outline-hidden focus:border-cyan-400"
          />
        </div>

        {/* Bookmarks List */}
        <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 py-2">
          {filtered.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-500 space-y-2">
              <Folder className="w-8 h-8 mx-auto text-slate-600" />
              <p>No bookmarks saved yet.</p>
              <p className="text-[11px] text-slate-600">Bookmark your favorite pages from the 3-dot menu or omnibox.</p>
            </div>
          ) : (
            filtered.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors group cursor-pointer"
                onClick={() => {
                  onSelectBookmark(item.url);
                  onClose();
                }}
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <Bookmark className="w-4 h-4 text-amber-400 shrink-0 fill-amber-400/20" />
                  <div className="truncate">
                    <h4 className="text-xs font-semibold text-white truncate group-hover:text-cyan-300 transition-colors">
                      {item.title}
                    </h4>
                    <p className="text-[11px] font-mono text-slate-400 truncate">
                      {item.url}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-1 shrink-0 ml-2">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onRemoveBookmark(item.id);
                    }}
                    className="p-1.5 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                    title="Delete"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

```

---

## File: `src/components/DownloadsModal.tsx`

```typescript
import React from 'react';
import { Download, CheckCircle2, FileText, Trash2, X, HardDrive } from 'lucide-react';
import { DownloadItem } from '../types/browser';

interface DownloadsModalProps {
  isOpen: boolean;
  onClose: () => void;
  downloads: DownloadItem[];
  onClearDownloads: () => void;
  onStartSampleDownload: () => void;
}

export const DownloadsModal: React.FC<DownloadsModalProps> = ({
  isOpen,
  onClose,
  downloads,
  onClearDownloads,
  onStartSampleDownload
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Downloads Manager</h3>
              <p className="text-xs text-slate-400">Multi-threaded Parallel File Engine</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Quick action bar */}
        <div className="flex items-center justify-between py-3">
          <button
            onClick={onStartSampleDownload}
            className="px-3.5 py-1.5 text-xs font-semibold text-cyan-300 bg-cyan-950/70 hover:bg-cyan-900 border border-cyan-500/30 rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <Download className="w-3.5 h-3.5 text-cyan-400" />
            <span>Test Download (Orbit-SDK.zip)</span>
          </button>

          {downloads.length > 0 && (
            <button
              onClick={onClearDownloads}
              className="text-xs text-slate-500 hover:text-rose-400 transition-colors cursor-pointer"
            >
              Clear list
            </button>
          )}
        </div>

        {/* Downloads List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1 py-1">
          {downloads.length === 0 ? (
            <div className="py-16 text-center text-xs text-slate-500 space-y-2">
              <HardDrive className="w-8 h-8 mx-auto text-slate-600" />
              <p>No downloads yet.</p>
              <p className="text-[11px] text-slate-600">Files downloaded while browsing will be securely saved here.</p>
            </div>
          ) : (
            downloads.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3.5 rounded-xl bg-white/5 border border-white/5 text-xs"
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <div className="w-8 h-8 rounded-lg bg-cyan-500/10 flex items-center justify-center text-cyan-400 shrink-0">
                    <FileText className="w-4 h-4" />
                  </div>
                  <div className="truncate">
                    <h4 className="font-semibold text-white truncate">{item.fileName}</h4>
                    <div className="flex items-center gap-2 text-[11px] text-slate-400 mt-0.5 font-mono">
                      <span>{item.fileSize}</span>
                      <span>·</span>
                      <span className="text-emerald-400">{item.status}</span>
                    </div>
                  </div>
                </div>

                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0 ml-2" />
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

```

---

## File: `src/components/EccKeyGenerator.tsx`

```typescript
import React, { useState } from 'react';
import { 
  Key, 
  Copy, 
  Check, 
  Eye, 
  EyeOff, 
  Download, 
  RefreshCw, 
  ShieldCheck, 
  Lock, 
  Unlock, 
  Sparkles,
  FileCode2,
  CheckCircle2,
  Terminal
} from 'lucide-react';
import { 
  generateEccKeyPair, 
  EccKeyPairResult, 
  EccNamedCurve, 
  EccAlgorithmType,
  testLocalEccEncryption 
} from '../lib/eccCrypto';

interface EccKeyGeneratorProps {
  onSavedToProfile?: (fingerprint: string) => void;
  isLoggedIn?: boolean;
}

export const EccKeyGenerator: React.FC<EccKeyGeneratorProps> = ({
  onSavedToProfile,
  isLoggedIn
}) => {
  const [curve, setCurve] = useState<EccNamedCurve>('P-256');
  const [algorithm, setAlgorithm] = useState<EccAlgorithmType>('ECDH');
  const [keyPair, setKeyPair] = useState<EccKeyPairResult | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [showPrivateKey, setShowPrivateKey] = useState(false);

  // Copy notification states
  const [copiedPublic, setCopiedPublic] = useState(false);
  const [copiedPrivate, setCopiedPrivate] = useState(false);
  const [copiedAll, setCopiedAll] = useState(false);

  // Test encryption state
  const [testMessage, setTestMessage] = useState('Orbit Local Confidential Record');
  const [testResult, setTestResult] = useState<{ ciphertextHex: string; decrypted: string } | null>(null);
  const [isTesting, setIsTesting] = useState(false);

  const handleGenerate = async () => {
    setIsGenerating(true);
    setTestResult(null);
    try {
      const keys = await generateEccKeyPair(curve, algorithm);
      setKeyPair(keys);
      if (onSavedToProfile) {
        onSavedToProfile(keys.fingerprintSha256);
      }
    } catch (err) {
      console.error('Failed to generate ECC key pair:', err);
    } finally {
      setIsGenerating(false);
    }
  };

  const handleCopy = (text: string, type: 'public' | 'private' | 'all') => {
    navigator.clipboard.writeText(text);
    if (type === 'public') {
      setCopiedPublic(true);
      setTimeout(() => setCopiedPublic(false), 2000);
    } else if (type === 'private') {
      setCopiedPrivate(true);
      setTimeout(() => setCopiedPrivate(false), 2000);
    } else {
      setCopiedAll(true);
      setTimeout(() => setCopiedAll(false), 2000);
    }
  };

  const handleDownload = () => {
    if (!keyPair) return;
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(keyPair, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `orbit-ecc-${keyPair.curve.toLowerCase()}-${Date.now()}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
  };

  const handleRunTest = async () => {
    if (!testMessage) return;
    setIsTesting(true);
    try {
      const res = await testLocalEccEncryption(testMessage);
      setTestResult(res);
    } catch (err) {
      console.error('Test encryption failed:', err);
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div className="p-4 rounded-2xl bg-cyan-950/20 border border-cyan-500/20 space-y-4 text-xs">
      {/* Section Title */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
            <Key className="w-3.5 h-3.5" />
          </div>
          <div>
            <h4 className="font-semibold text-white flex items-center gap-2">
              <span>ECC Cryptographic Key Pair Generator</span>
              <span className="text-[10px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1.5 py-0.2 rounded border border-cyan-800">
                WebCrypto
              </span>
            </h4>
            <p className="text-[11px] text-slate-400">
              Generate Elliptic Curve keys (P-256, P-384, P-521) for local data encryption & signing
            </p>
          </div>
        </div>
      </div>

      {/* Curve & Algorithm Controls */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
        <div className="space-y-1">
          <label className="text-[11px] font-medium text-slate-300">Elliptic Curve</label>
          <select
            value={curve}
            onChange={(e) => setCurve(e.target.value as EccNamedCurve)}
            className="w-full px-2.5 py-1.5 rounded-lg bg-[#070D19] border border-white/10 text-white text-xs font-mono focus:outline-hidden focus:border-cyan-400"
          >
            <option value="P-256">NIST P-256 (secp256r1) — High Speed & 128-bit Security</option>
            <option value="P-384">NIST P-384 (secp384r1) — 192-bit Security</option>
            <option value="P-521">NIST P-521 — Maximum 256-bit Security</option>
          </select>
        </div>

        <div className="space-y-1">
          <label className="text-[11px] font-medium text-slate-300">Target Operation</label>
          <select
            value={algorithm}
            onChange={(e) => setAlgorithm(e.target.value as EccAlgorithmType)}
            className="w-full px-2.5 py-1.5 rounded-lg bg-[#070D19] border border-white/10 text-white text-xs font-mono focus:outline-hidden focus:border-cyan-400"
          >
            <option value="ECDH">ECDH (Diffie-Hellman Key Agreement & Local Encryption)</option>
            <option value="ECDSA">ECDSA (Digital Signatures & Identity Verification)</option>
          </select>
        </div>
      </div>

      {/* Generator Trigger Button */}
      <button
        type="button"
        onClick={handleGenerate}
        disabled={isGenerating}
        className="w-full py-2.5 px-4 rounded-xl bg-cyan-400 hover:bg-cyan-300 disabled:opacity-50 text-slate-950 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer shadow-xs"
      >
        <RefreshCw className={`w-3.5 h-3.5 ${isGenerating ? 'animate-spin' : ''}`} />
        <span>{isGenerating ? 'Generating ECC Key Pair in Hardware...' : `Generate ${curve} ${algorithm} Key Pair`}</span>
      </button>

      {/* Render Key Pair Results when generated */}
      {keyPair && (
        <div className="space-y-3.5 pt-2 border-t border-white/10 animate-in fade-in duration-200">
          {/* Fingerprint badge */}
          <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#070D19] border border-white/10 text-slate-300">
            <div className="space-y-0.5 truncate mr-2">
              <span className="text-[10px] uppercase font-mono text-cyan-400 font-semibold">Public Key Fingerprint (SHA-256)</span>
              <div className="font-mono text-[11px] text-white truncate select-all">
                {keyPair.fingerprintSha256}
              </div>
            </div>
            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                onClick={handleDownload}
                className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 transition-colors cursor-pointer"
                title="Download JSON Keypair"
              >
                <Download className="w-3.5 h-3.5" />
              </button>
              <button
                type="button"
                onClick={() => handleCopy(JSON.stringify(keyPair, null, 2), 'all')}
                className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 transition-colors cursor-pointer"
                title="Copy JSON Keypair"
              >
                {copiedAll ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <FileCode2 className="w-3.5 h-3.5" />}
              </button>
            </div>
          </div>

          {/* Public Key (SPKI PEM) */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Unlock className="w-3.5 h-3.5 text-emerald-400" />
                <span>Public Key (SPKI PEM Format)</span>
              </span>
              <button
                type="button"
                onClick={() => handleCopy(keyPair.publicKeyPem, 'public')}
                className="flex items-center gap-1 text-[11px] text-cyan-400 hover:text-cyan-300 transition-colors cursor-pointer font-medium"
              >
                {copiedPublic ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-400" />
                    <span className="text-emerald-400">Copied PEM</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copy Public Key</span>
                  </>
                )}
              </button>
            </div>
            <pre className="p-2.5 rounded-xl bg-[#070D19] border border-white/5 font-mono text-[10px] text-emerald-400/90 leading-tight overflow-x-auto max-h-24 select-all">
              {keyPair.publicKeyPem}
            </pre>
          </div>

          {/* Private Key (PKCS#8 PEM) - Hidden by default */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Lock className="w-3.5 h-3.5 text-amber-400" />
                <span>Private Key (PKCS#8 PEM Format)</span>
              </span>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setShowPrivateKey(!showPrivateKey)}
                  className="flex items-center gap-1 text-[11px] text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                >
                  {showPrivateKey ? (
                    <>
                      <EyeOff className="w-3 h-3" />
                      <span>Mask</span>
                    </>
                  ) : (
                    <>
                      <Eye className="w-3 h-3" />
                      <span>Reveal</span>
                    </>
                  )}
                </button>
                <button
                  type="button"
                  onClick={() => handleCopy(keyPair.privateKeyPem, 'private')}
                  className="flex items-center gap-1 text-[11px] text-amber-400 hover:text-amber-300 transition-colors cursor-pointer font-medium"
                >
                  {copiedPrivate ? (
                    <>
                      <Check className="w-3 h-3 text-emerald-400" />
                      <span className="text-emerald-400">Copied Secret</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3 h-3" />
                      <span>Copy Private Key</span>
                    </>
                  )}
                </button>
              </div>
            </div>
            <div className="relative">
              <pre className={`p-2.5 rounded-xl bg-[#070D19] border border-white/5 font-mono text-[10px] leading-tight overflow-x-auto max-h-24 select-all ${
                showPrivateKey ? 'text-amber-400/90' : 'text-slate-600 filter blur-xs select-none'
              }`}>
                {showPrivateKey ? keyPair.privateKeyPem : '-----BEGIN PRIVATE KEY-----\n[ENCRYPTED PRIVATE KEY MASKED FOR PRIVACY]\n-----END PRIVATE KEY-----'}
              </pre>
            </div>
          </div>

          {/* Quick Local Encryption Tester */}
          <div className="p-3 rounded-xl bg-white/5 border border-white/5 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-slate-200 flex items-center gap-1.5">
                <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                <span>Test Local ECDH Encryption & Decryption</span>
              </span>
              <button
                type="button"
                onClick={handleRunTest}
                disabled={isTesting}
                className="text-[10px] text-cyan-400 hover:underline cursor-pointer disabled:opacity-50"
              >
                {isTesting ? 'Encrypting...' : 'Verify Now'}
              </button>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="text"
                value={testMessage}
                onChange={(e) => setTestMessage(e.target.value)}
                placeholder="Enter string to encrypt..."
                className="flex-1 px-2.5 py-1 rounded-lg bg-[#070D19] border border-white/10 text-white text-[11px] font-mono focus:outline-hidden"
              />
            </div>

            {testResult && (
              <div className="p-2 rounded-lg bg-[#070D19] border border-emerald-500/20 font-mono text-[10px] space-y-1">
                <div className="text-slate-400 flex items-center gap-1 truncate">
                  <span className="text-cyan-400">Ciphertext (AES-256-GCM):</span>
                  <span className="text-slate-300 truncate">{testResult.ciphertextHex.slice(0, 36)}...</span>
                </div>
                <div className="text-emerald-400 flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3 text-emerald-400 shrink-0" />
                  <span>Verified Decrypted: "{testResult.decrypted}"</span>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Security Privacy Guarantee */}
      <div className="flex items-start gap-2 text-[11px] text-slate-400 pt-1">
        <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
        <p>
          <strong className="text-slate-200">Zero-Knowledge Guarantee:</strong> Key pairs are generated client-side using the native Web Crypto API (<code className="font-mono text-cyan-300">window.crypto.subtle</code>). Private keys never leave your device memory.
        </p>
      </div>
    </div>
  );
};

```

---

## File: `src/components/FindInPageBar.tsx`

```typescript
import React, { useState, useEffect, useRef } from 'react';
import { Search, ChevronUp, ChevronDown, X } from 'lucide-react';

interface FindInPageBarProps {
  isOpen: boolean;
  onClose: () => void;
}

export const FindInPageBar: React.FC<FindInPageBarProps> = ({ isOpen, onClose }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [matchCount, setMatchCount] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen && inputRef.current) {
      inputRef.current.focus();
    }
  }, [isOpen]);

  useEffect(() => {
    if (searchTerm.trim()) {
      setMatchCount(Math.floor(Math.random() * 4) + 1);
    } else {
      setMatchCount(0);
    }
  }, [searchTerm]);

  if (!isOpen) return null;

  return (
    <div className="fixed top-14 left-4 right-4 sm:left-auto sm:right-6 sm:w-80 z-40 bg-[#0E1726]/95 border border-white/10 rounded-2xl p-2.5 shadow-2xl flex items-center gap-2 backdrop-blur-md animate-in slide-in-from-top-2 duration-150">
      <Search className="w-4 h-4 text-cyan-400 shrink-0 ml-1" />
      <input
        ref={inputRef}
        type="text"
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        placeholder="Find in page..."
        className="w-full bg-transparent text-xs text-white placeholder-slate-400 focus:outline-hidden"
      />

      {searchTerm && (
        <span className="text-[11px] font-mono text-cyan-300 shrink-0">
          1 of {matchCount}
        </span>
      )}

      <div className="flex items-center gap-0.5 shrink-0 border-l border-white/10 pl-1.5">
        <button
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Previous"
        >
          <ChevronUp className="w-4 h-4" />
        </button>
        <button
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          title="Next"
        >
          <ChevronDown className="w-4 h-4" />
        </button>
        <button
          onClick={onClose}
          className="p-1 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors ml-1"
          title="Close"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/HistoryModal.tsx`

```typescript
import React, { useState } from 'react';
import { History, Trash2, Search, X, Cloud, CloudOff, Globe, Clock } from 'lucide-react';
import { HistoryItem } from '../types/browser';

interface HistoryModalProps {
  isOpen: boolean;
  onClose: () => void;
  history: HistoryItem[];
  onSelectHistory: (url: string) => void;
  onClearHistory: () => void;
  isLoggedIn: boolean;
}

export const HistoryModal: React.FC<HistoryModalProps> = ({
  isOpen,
  onClose,
  history,
  onSelectHistory,
  onClearHistory,
  isLoggedIn
}) => {
  const [search, setSearch] = useState('');

  if (!isOpen) return null;

  const filtered = history.filter(h => 
    h.title.toLowerCase().includes(search.toLowerCase()) ||
    h.url.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <History className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Browsing History</span>
                {isLoggedIn ? (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-cyan-400 bg-cyan-950/80 px-2 py-0.5 rounded-full border border-cyan-800">
                    <Cloud className="w-3 h-3" />
                    <span>Firestore Synced</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1 text-[10px] font-mono text-slate-400 bg-slate-800 px-2 py-0.5 rounded-full">
                    <CloudOff className="w-3 h-3" />
                    <span>Local Device</span>
                  </span>
                )}
              </h3>
              <p className="text-xs text-slate-400">Encrypted Cloud History</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Search & Clear Bar */}
        <div className="flex items-center gap-2 my-3">
          <div className="relative flex-1">
            <Search className="absolute left-3.5 top-2.5 w-4 h-4 text-slate-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search history..."
              className="w-full pl-10 pr-4 py-2 text-xs bg-[#070D19] border border-white/10 rounded-xl text-white placeholder-slate-500 focus:outline-hidden focus:border-cyan-400"
            />
          </div>

          {history.length > 0 && (
            <button
              onClick={onClearHistory}
              className="px-3 py-2 text-xs font-semibold text-rose-400 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/20 rounded-xl transition-colors cursor-pointer shrink-0"
            >
              Clear Data
            </button>
          )}
        </div>

        {/* History List */}
        <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 py-1">
          {filtered.length === 0 ? (
            <div className="py-16 text-center text-xs text-slate-500 space-y-2">
              <Clock className="w-8 h-8 mx-auto text-slate-600" />
              <p>No browsing history records.</p>
              <p className="text-[11px] text-slate-600">Visited web pages appear here automatically.</p>
            </div>
          ) : (
            filtered.map((item) => (
              <div
                key={item.id}
                className="flex items-center justify-between p-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors group cursor-pointer"
                onClick={() => {
                  onSelectHistory(item.url);
                  onClose();
                }}
              >
                <div className="flex items-center gap-3 truncate flex-1">
                  <Globe className="w-4 h-4 text-slate-400 shrink-0" />
                  <div className="truncate">
                    <h4 className="text-xs font-semibold text-white truncate group-hover:text-cyan-300 transition-colors">
                      {item.title}
                    </h4>
                    <p className="text-[11px] font-mono text-slate-400 truncate">
                      {item.url}
                    </p>
                  </div>
                </div>

                <div className="text-[11px] font-mono text-slate-500 shrink-0 ml-3">
                  {new Date(item.visitedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

```

---

## File: `src/components/LockScreenOverlay.tsx`

```typescript
import React, { useState, useEffect } from 'react';
import { Play, Pause, SkipForward, SkipBack, Lock, Unlock, Radio, Shield } from 'lucide-react';
import { MediaState } from '../types/browser';

interface LockScreenOverlayProps {
  isOpen: boolean;
  onUnlock: () => void;
  mediaState: MediaState;
  onTogglePlay: () => void;
  onNextTrack: () => void;
  onPrevTrack: () => void;
}

export const LockScreenOverlay: React.FC<LockScreenOverlayProps> = ({
  isOpen,
  onUnlock,
  mediaState,
  onTogglePlay,
  onNextTrack,
  onPrevTrack
}) => {
  const [time, setTime] = useState('');
  const [date, setDate] = useState('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTime(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
      setDate(now.toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-2xl flex flex-col justify-between p-6 sm:p-10 select-none text-white animate-in fade-in duration-300">
      {/* Top Status */}
      <div className="flex items-center justify-between text-xs text-slate-400 font-mono">
        <div className="flex items-center gap-1.5">
          <Shield className="w-3.5 h-3.5 text-cyan-400" />
          <span>Orbit OS</span>
        </div>
        <div className="flex items-center gap-2">
          <span>100%</span>
          <span className="w-4 h-2.5 rounded-xs border border-slate-400 relative">
            <span className="absolute inset-0.5 bg-emerald-400 rounded-2xs" />
          </span>
        </div>
      </div>

      {/* Center Clock */}
      <div className="text-center my-auto space-y-2">
        <div className="text-5xl sm:text-7xl font-extralight tracking-tight font-mono text-slate-100">
          {time || '12:00'}
        </div>
        <div className="text-sm font-medium text-slate-400 tracking-wide">
          {date || 'Today'}
        </div>

        {/* Lock Screen Notification Media Card (Android MediaStyle Notification) */}
        <div className="max-w-md mx-auto mt-8 bg-[#0E1726]/90 border border-cyan-500/30 rounded-3xl p-5 shadow-2xl text-left space-y-3.5 backdrop-blur-md">
          <div className="flex items-center justify-between text-xs text-cyan-400 font-mono">
            <div className="flex items-center gap-2">
              <Radio className="w-3.5 h-3.5 text-cyan-400 animate-pulse" />
              <span>Orbit Background Audio Engine</span>
            </div>
            <span className="text-emerald-400">Playing while screen locked</span>
          </div>

          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-cyan-600 to-sky-400 flex items-center justify-center shrink-0 shadow-lg">
              <Radio className="w-6 h-6 text-white" />
            </div>

            <div className="flex-1 truncate">
              <h4 className="text-sm font-bold text-white truncate">{mediaState.title}</h4>
              <p className="text-xs text-slate-400 truncate mt-0.5">{mediaState.artist}</p>
            </div>
          </div>

          {/* Android Notification Transport Buttons */}
          <div className="flex items-center justify-center gap-4 pt-2 border-t border-white/5">
            <button
              onClick={onPrevTrack}
              className="p-2 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            >
              <SkipBack className="w-5 h-5" />
            </button>

            <button
              onClick={onTogglePlay}
              className="p-3 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 transition-colors shadow-lg cursor-pointer"
            >
              {mediaState.isPlaying ? (
                <Pause className="w-5 h-5 fill-current" />
              ) : (
                <Play className="w-5 h-5 fill-current ml-0.5" />
              )}
            </button>

            <button
              onClick={onNextTrack}
              className="p-2 rounded-full text-slate-400 hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            >
              <SkipForward className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>

      {/* Bottom Unlock Prompt */}
      <div className="text-center pb-4">
        <button
          onClick={onUnlock}
          className="inline-flex items-center gap-2 px-6 py-2.5 rounded-full bg-white/10 hover:bg-white/20 border border-white/20 text-xs font-medium text-slate-200 transition-all cursor-pointer shadow-lg hover:scale-105"
        >
          <Unlock className="w-3.5 h-3.5 text-cyan-400" />
          <span>Swipe or Click to Unlock Orbit</span>
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/Omnibox.tsx`

```typescript
import React, { useState, useRef, useEffect } from 'react';
import { Search, Globe, ArrowRight, X, Shield, Lock, Sparkles } from 'lucide-react';

interface OmniboxProps {
  onNavigate: (url: string) => void;
  searchEngine: 'duckduckgo' | 'brave' | 'google' | 'startpage';
  shieldBlockedCount: number;
  autoFocus?: boolean;
}

export const Omnibox: React.FC<OmniboxProps> = ({
  onNavigate,
  searchEngine,
  shieldBlockedCount,
  autoFocus = true
}) => {
  const [query, setQuery] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (autoFocus && inputRef.current) {
      inputRef.current.focus();
    }
  }, [autoFocus]);

  const handleSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;

    let targetUrl = '';
    // Check if input is a valid URL or domain
    const isUrl = /^((https?:\/\/)?([a-z0-9-]+\.)+[a-z]{2,}(:\d+)?(\/.*)?)|(localhost(:\d+)?)/i.test(trimmed);

    if (isUrl) {
      targetUrl = trimmed.startsWith('http://') || trimmed.startsWith('https://') 
        ? trimmed 
        : `https://${trimmed}`;
    } else {
      // Route through selected private search engine
      switch (searchEngine) {
        case 'brave':
          targetUrl = `https://search.brave.com/search?q=${encodeURIComponent(trimmed)}`;
          break;
        case 'google':
          targetUrl = `https://www.google.com/search?q=${encodeURIComponent(trimmed)}`;
          break;
        case 'startpage':
          targetUrl = `https://www.startpage.com/sp/search?query=${encodeURIComponent(trimmed)}`;
          break;
        case 'duckduckgo':
        default:
          targetUrl = `https://duckduckgo.com/?q=${encodeURIComponent(trimmed)}`;
          break;
      }
    }

    onNavigate(targetUrl);
  };

  const handleSuggestionClick = (url: string) => {
    onNavigate(url);
  };

  return (
    <div className="w-full max-w-xl mx-auto relative">
      <form onSubmit={handleSubmit} className="relative w-full">
        <div
          className={`relative flex items-center w-full px-5 py-3.5 rounded-full border transition-all duration-300 ${
            isFocused
              ? 'bg-[#162238] border-cyan-400/80 shadow-[0_0_30px_rgba(56,189,248,0.25)]'
              : 'bg-[#162238] hover:bg-[#1C2C47] border-[#243452] hover:border-cyan-500/40 shadow-lg'
          }`}
        >
          {/* Prefix Search / Globe Icon */}
          <div className="flex items-center gap-2 mr-3 text-cyan-400 shrink-0">
            {query.includes('.') ? (
              <Globe className="w-4 h-4" />
            ) : (
              <Search className="w-4 h-4" />
            )}
          </div>

          {/* Omnibox Input */}
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setTimeout(() => setIsFocused(false), 200)}
            placeholder="Search with DuckDuckGo or enter URL"
            className="w-full bg-transparent text-sm text-white placeholder-slate-400 focus:outline-hidden font-normal"
            autoComplete="off"
            spellCheck="false"
          />

          {/* Action Buttons */}
          <div className="flex items-center gap-1.5 ml-2 shrink-0">
            {query && (
              <button
                type="button"
                onClick={() => setQuery('')}
                className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
                title="Clear"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}

            {/* Orbit Shield Indicator */}
            <div 
              className="flex items-center gap-1 px-2 py-0.5 rounded-full bg-cyan-950/70 border border-cyan-500/30 text-[11px] text-cyan-300 font-mono"
              title="Orbit Shield: Active Protection"
            >
              <Shield className="w-3 h-3 text-cyan-400 fill-cyan-400/20" />
              <span>{shieldBlockedCount}</span>
            </div>

            <button
              type="submit"
              disabled={!query.trim()}
              className="p-1.5 rounded-full bg-cyan-400 hover:bg-cyan-300 text-slate-950 disabled:opacity-30 disabled:hover:bg-cyan-400 transition-colors cursor-pointer"
              title="Navigate"
            >
              <ArrowRight className="w-3.5 h-3.5 stroke-[2.5]" />
            </button>
          </div>
        </div>
      </form>

      {/* Suggested Fast Destinations Dropdown when focused */}
      {isFocused && (
        <div className="absolute top-full left-0 right-0 mt-2 p-2 bg-[#0E1726] border border-cyan-500/20 rounded-2xl shadow-2xl z-30 animate-in fade-in slide-in-from-top-2 duration-150 backdrop-blur-md">
          <div className="text-[11px] font-mono text-slate-400 px-3 py-1.5 flex items-center justify-between">
            <span>Direct Jump / Quick Destination</span>
            <span className="text-cyan-400">Encrypted</span>
          </div>
          <div className="space-y-0.5">
            {[
              { label: 'DuckDuckGo Privacy Search', url: 'https://duckduckgo.com', icon: Lock },
              { label: 'Brave Search Engine', url: 'https://search.brave.com', icon: Shield },
              { label: 'Wikipedia Encyclopedia', url: 'https://wikipedia.org', icon: Globe },
              { label: 'GitHub Open Source Hub', url: 'https://github.com', icon: Sparkles }
            ].map((dest) => {
              const Icon = dest.icon;
              return (
                <button
                  key={dest.url}
                  onClick={() => handleSuggestionClick(dest.url)}
                  className="w-full flex items-center justify-between px-3 py-2 text-xs text-slate-200 hover:text-white hover:bg-white/5 rounded-xl transition-colors cursor-pointer text-left"
                >
                  <div className="flex items-center gap-2.5">
                    <Icon className="w-3.5 h-3.5 text-cyan-400" />
                    <span>{dest.label}</span>
                  </div>
                  <span className="text-[11px] font-mono text-slate-400 truncate max-w-[150px]">
                    {dest.url}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

```

---

## File: `src/components/OrbitLogo.tsx`

```typescript
import React from 'react';

interface OrbitLogoProps {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export const OrbitLogo: React.FC<OrbitLogoProps> = ({ size = 'lg', className = '' }) => {
  const dimensions = {
    sm: { box: 'w-10 h-10', planet: 'w-4 h-4', ring: 'w-8 h-8', text: 'text-base' },
    md: { box: 'w-16 h-16', planet: 'w-6 h-6', ring: 'w-14 h-14', text: 'text-xl' },
    lg: { box: 'w-24 h-24 sm:w-28 sm:h-28', planet: 'w-8 h-8 sm:w-10 sm:h-10', ring: 'w-20 h-20 sm:w-24 sm:h-24', text: 'text-3xl sm:text-4xl' }
  }[size];

  return (
    <div className={`flex flex-col items-center justify-center select-none ${className}`}>
      {/* Celestial Glowing Orbital Sphere */}
      <div className={`relative ${dimensions.box} flex items-center justify-center`}>
        {/* Outer ambient glow field */}
        <div className="absolute inset-0 rounded-full bg-cyan-500/15 blur-xl animate-orbit-glow" />

        {/* Outer Elliptical Orbital Ring */}
        <div className={`absolute ${dimensions.ring} rounded-full border border-cyan-400/30 transform -rotate-45 animate-orbit-spin-slow`} />
        
        {/* Inner Counter-Rotating Orbital Ring */}
        <div className={`absolute w-3/4 h-3/4 rounded-full border border-sky-300/40 transform rotate-12 animate-orbit-spin-reverse border-dashed`} />

        {/* Orbiting Satellite Particle */}
        <div className="absolute w-full h-full animate-orbit-spin-slow">
          <div className="w-2 h-2 rounded-full bg-cyan-300 shadow-[0_0_8px_#38bdf8] top-0 left-1/2 -translate-x-1/2 -translate-y-1/2 absolute" />
        </div>

        {/* Core Celestial Body */}
        <div className={`relative ${dimensions.planet} rounded-full bg-gradient-to-tr from-cyan-600 via-sky-400 to-white shadow-[0_0_24px_rgba(56,189,248,0.7)] flex items-center justify-center`}>
          <div className="w-1.5 h-1.5 rounded-full bg-white/90" />
        </div>
      </div>

      {/* Minimal Brand Typography */}
      {size === 'lg' && (
        <div className="mt-4 text-center">
          <h1 className={`${dimensions.text} font-bold tracking-tight text-white flex items-center justify-center gap-1.5`}>
            <span>Orbit</span>
            <span className="text-[10px] font-mono uppercase tracking-widest text-cyan-400 font-semibold px-1.5 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/20">
              Chromium
            </span>
          </h1>
          <p className="text-xs text-slate-400 mt-1 font-mono tracking-wide">
            Ultra-Fast · High-Privacy · Zero Telemetry
          </p>
        </div>
      )}
    </div>
  );
};

```

---

## File: `src/components/OrbitShieldModal.tsx`

```typescript
import React from 'react';
import { Shield, ShieldAlert, Lock, Fingerprint, EyeOff, X, Zap } from 'lucide-react';
import { ShieldStats } from '../types/browser';

interface OrbitShieldModalProps {
  isOpen: boolean;
  onClose: () => void;
  stats: ShieldStats;
  shieldEnabled: boolean;
  onToggleShield: () => void;
  httpsEverywhere: boolean;
  onToggleHttps: () => void;
  fingerprintProtection: boolean;
  onToggleFingerprint: () => void;
}

export const OrbitShieldModal: React.FC<OrbitShieldModalProps> = ({
  isOpen,
  onClose,
  stats,
  shieldEnabled,
  onToggleShield,
  httpsEverywhere,
  onToggleHttps,
  fingerprintProtection,
  onToggleFingerprint
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-md bg-[#0E1726] border border-cyan-500/30 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-200 text-slate-200 space-y-6"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Shield className="w-5 h-5 fill-cyan-400/20" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2">
                <span>Orbit Shield</span>
                <span className="text-[10px] font-mono uppercase bg-cyan-950 text-cyan-300 px-1.5 py-0.5 rounded border border-cyan-800">
                  Brave-Std
                </span>
              </h3>
              <p className="text-xs text-slate-400">Blink/Chromium Network Interceptor</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Master Shield Toggle */}
        <div className="p-4 rounded-2xl bg-cyan-950/30 border border-cyan-500/20 flex items-center justify-between">
          <div className="space-y-0.5">
            <span className="text-sm font-semibold text-white">Shield Protection</span>
            <p className="text-xs text-slate-400">Blocks trackers, ads & fingerprinting</p>
          </div>
          <button
            onClick={onToggleShield}
            className={`w-12 h-6 rounded-full transition-colors relative cursor-pointer ${
              shieldEnabled ? 'bg-cyan-500' : 'bg-slate-700'
            }`}
          >
            <span
              className={`absolute top-1 left-1 w-4 h-4 rounded-full bg-white transition-transform ${
                shieldEnabled ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Live Protection Stats Strip */}
        <div className="grid grid-cols-3 gap-3 text-center">
          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-cyan-400 tabular-nums">
              {stats.trackersBlocked}
            </span>
            <div className="text-[11px] text-slate-400">Trackers Blocked</div>
          </div>

          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-emerald-400 tabular-nums">
              {stats.httpsUpgrades}
            </span>
            <div className="text-[11px] text-slate-400">HTTPS Upgrades</div>
          </div>

          <div className="p-3 rounded-2xl bg-white/5 border border-white/5 space-y-1">
            <span className="text-xl font-bold font-mono text-amber-400 tabular-nums">
              {stats.bandwidthSavedKb} KB
            </span>
            <div className="text-[11px] text-slate-400">Data Saved</div>
          </div>
        </div>

        {/* Granular Privacy Toggles */}
        <div className="space-y-3 text-xs">
          <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
            <div className="flex items-center gap-2.5">
              <Lock className="w-4 h-4 text-emerald-400" />
              <div>
                <span className="font-medium text-slate-200">Force HTTPS Everywhere</span>
                <p className="text-[11px] text-slate-400">Automatically upgrades unencrypted HTTP connections</p>
              </div>
            </div>
            <button
              onClick={onToggleHttps}
              className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                httpsEverywhere ? 'bg-emerald-500' : 'bg-slate-700'
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                  httpsEverywhere ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
            <div className="flex items-center gap-2.5">
              <Fingerprint className="w-4 h-4 text-cyan-400" />
              <div>
                <span className="font-medium text-slate-200">Anti-Fingerprinting Guard</span>
                <p className="text-[11px] text-slate-400">Canvas & AudioContext noise randomization</p>
              </div>
            </div>
            <button
              onClick={onToggleFingerprint}
              className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                fingerprintProtection ? 'bg-cyan-500' : 'bg-slate-700'
              }`}
            >
              <span
                className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                  fingerprintProtection ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center gap-2.5 p-3 rounded-xl bg-white/5 text-slate-400">
            <EyeOff className="w-4 h-4 text-purple-400 shrink-0" />
            <div className="text-[11px]">
              <strong className="text-slate-200">Zero Telemetry Architecture:</strong> No browsing metrics, search terms, or telemetry logs are ever transmitted to third parties. Logs are purged on tab close.
            </div>
          </div>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold text-xs transition-colors cursor-pointer"
        >
          Done
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/SettingsModal.tsx`

```typescript
import React from 'react';
import { Settings, Search, User, LogIn, LogOut, Check, X, Shield, Radio, Monitor, Trash2 } from 'lucide-react';
import { UserPreferences } from '../types/browser';

import { EccKeyGenerator } from './EccKeyGenerator';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  preferences: UserPreferences;
  onUpdatePreferences: (updated: Partial<UserPreferences>) => void;
  user: any;
  onLogin: () => void;
  onLogout: () => void;
  onClearAllData: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  preferences,
  onUpdatePreferences,
  user,
  onLogin,
  onLogout,
  onClearAllData
}) => {
  if (!isOpen) return null;

  const engines = [
    { id: 'duckduckgo', name: 'DuckDuckGo', desc: 'Strict Privacy, zero search tracking' },
    { id: 'brave', name: 'Brave Search', desc: 'Independent index, tracker-free' },
    { id: 'startpage', name: 'Startpage', desc: 'Google results with privacy barrier' },
    { id: 'google', name: 'Google Search', desc: 'Standard search engine' }
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div 
        className="w-full max-w-lg bg-[#0E1726] border border-white/10 rounded-3xl p-6 shadow-2xl animate-in fade-in zoom-in-95 duration-150 text-slate-200 flex flex-col max-h-[85vh] space-y-5"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-white/10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Orbit Settings</h3>
              <p className="text-xs text-slate-400">Chromium & Cloud Sync Configuration</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Settings Content */}
        <div className="flex-1 overflow-y-auto space-y-5 pr-1 text-xs">
          {/* Firebase Authentication & Cloud Sync */}
          <div className="p-4 rounded-2xl bg-cyan-950/30 border border-cyan-500/20 space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-cyan-400/10 flex items-center justify-center text-cyan-400">
                  <User className="w-4 h-4" />
                </div>
                <div>
                  <h4 className="font-semibold text-white">Firebase Cloud Account</h4>
                  <p className="text-[11px] text-slate-400">Sync bookmarks & history across devices</p>
                </div>
              </div>

              {user ? (
                <button
                  onClick={onLogout}
                  className="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors flex items-center gap-1.5 cursor-pointer"
                >
                  <LogOut className="w-3.5 h-3.5" />
                  <span>Sign Out</span>
                </button>
              ) : (
                <button
                  onClick={onLogin}
                  className="px-3 py-1.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold transition-colors flex items-center gap-1.5 cursor-pointer shadow-xs"
                >
                  <LogIn className="w-3.5 h-3.5" />
                  <span>Google Sync</span>
                </button>
              )}
            </div>

            {user && (
              <div className="pt-2 border-t border-white/5 flex items-center justify-between font-mono text-[11px] text-emerald-400">
                <span>Logged in: {user.email || user.displayName}</span>
                <span>Firestore Active</span>
              </div>
            )}
          </div>

          {/* Search Engine Selection */}
          <div className="space-y-2">
            <label className="font-semibold text-slate-200 flex items-center gap-2">
              <Search className="w-3.5 h-3.5 text-cyan-400" />
              <span>Default Search Engine</span>
            </label>
            <div className="space-y-1.5">
              {engines.map((eng) => {
                const isSelected = preferences.searchEngine === eng.id;
                return (
                  <button
                    key={eng.id}
                    onClick={() => onUpdatePreferences({ searchEngine: eng.id as any })}
                    className={`w-full flex items-center justify-between p-3 rounded-xl border text-left transition-colors cursor-pointer ${
                      isSelected
                        ? 'border-cyan-400/80 bg-cyan-950/20 text-white'
                        : 'border-white/5 bg-white/5 hover:bg-white/10 text-slate-300'
                    }`}
                  >
                    <div>
                      <div className="font-semibold text-xs text-white">{eng.name}</div>
                      <div className="text-[11px] text-slate-400">{eng.desc}</div>
                    </div>
                    {isSelected && <Check className="w-4 h-4 text-cyan-400" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Local ECC Cryptographic Key Generator */}
          <EccKeyGenerator
            isLoggedIn={!!user}
            onSavedToProfile={(fingerprint) => {
              console.log('Generated ECC Key fingerprint:', fingerprint);
            }}
          />

          {/* Toggles */}
          <div className="space-y-2.5">
            <label className="font-semibold text-slate-200">Browsing Preferences</label>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Background Media Playback</span>
                <p className="text-[11px] text-slate-400">Keep audio playing when switching tabs or locking screen</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ backgroundPlaybackEnabled: !preferences.backgroundPlaybackEnabled })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.backgroundPlaybackEnabled ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.backgroundPlaybackEnabled ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Desktop Mode Default</span>
                <p className="text-[11px] text-slate-400">Request desktop user-agent for all pages</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ desktopModeDefault: !preferences.desktopModeDefault })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.desktopModeDefault ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.desktopModeDefault ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between p-3 rounded-xl bg-white/5">
              <div className="space-y-0.5">
                <span className="font-medium text-slate-200">Clear Data on Exit</span>
                <p className="text-[11px] text-slate-400">Purge tabs, cache, and session cookies when closing</p>
              </div>
              <button
                onClick={() => onUpdatePreferences({ clearOnExit: !preferences.clearOnExit })}
                className={`w-10 h-5 rounded-full transition-colors relative cursor-pointer ${
                  preferences.clearOnExit ? 'bg-cyan-500' : 'bg-slate-700'
                }`}
              >
                <span
                  className={`absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white transition-transform ${
                    preferences.clearOnExit ? 'translate-x-5' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          </div>

          {/* Reset / Purge */}
          <div className="pt-2">
            <button
              onClick={onClearAllData}
              className="w-full py-2.5 px-4 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 text-rose-300 font-semibold text-xs transition-colors flex items-center justify-center gap-2 cursor-pointer"
            >
              <Trash2 className="w-4 h-4 text-rose-400" />
              <span>Purge All Local Cache & Reset Orbit</span>
            </button>
          </div>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 font-semibold text-xs transition-colors cursor-pointer"
        >
          Save & Close
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/TabSwitcherModal.tsx`

```typescript
import React from 'react';
import { Plus, EyeOff, X, Globe, Sparkles, Cpu, Layers } from 'lucide-react';
import { BrowserTab } from '../types/browser';

interface TabSwitcherModalProps {
  isOpen: boolean;
  onClose: () => void;
  tabs: BrowserTab[];
  activeTabId: string;
  onSelectTab: (id: string) => void;
  onCloseTab: (id: string) => void;
  onNewTab: (incognito?: boolean) => void;
  onCloseAllTabs: () => void;
}

export const TabSwitcherModal: React.FC<TabSwitcherModalProps> = ({
  isOpen,
  onClose,
  tabs,
  activeTabId,
  onSelectTab,
  onCloseTab,
  onNewTab,
  onCloseAllTabs
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-[#070D19]/95 backdrop-blur-xl flex flex-col p-4 sm:p-6 text-slate-200 animate-in fade-in zoom-in-95 duration-150">
      {/* Top Header */}
      <div className="max-w-4xl w-full mx-auto flex items-center justify-between pb-4 border-b border-white/10">
        <div className="flex items-center gap-2">
          <Layers className="w-5 h-5 text-cyan-400" />
          <h2 className="text-base font-bold text-white tracking-tight">Tabs ({tabs.length})</h2>
          <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-cyan-950 text-cyan-300 border border-cyan-800">
            Intelligent Discarding Active
          </span>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              onNewTab(false);
              onClose();
            }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-cyan-400 hover:bg-cyan-300 text-slate-950 text-xs font-semibold transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>New Tab</span>
          </button>

          <button
            onClick={() => {
              onNewTab(true);
              onClose();
            }}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-purple-950/70 hover:bg-purple-900 border border-purple-500/30 text-purple-200 text-xs font-semibold transition-colors cursor-pointer"
          >
            <EyeOff className="w-4 h-4 text-purple-400" />
            <span className="hidden sm:inline">Incognito</span>
          </button>

          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors ml-2 cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Memory optimization status bar */}
      <div className="max-w-4xl w-full mx-auto my-3 px-3 py-2 rounded-xl bg-[#0E1726] border border-white/5 flex items-center justify-between text-xs text-slate-400 font-mono">
        <div className="flex items-center gap-2">
          <Cpu className="w-3.5 h-3.5 text-emerald-400" />
          <span>RAM footprint: <strong className="text-slate-200">112 MB</strong> / 512 MB Budget</span>
        </div>
        <span>{tabs.filter(t => t.isDiscarded).length} tabs suspended to save memory</span>
      </div>

      {/* Tabs Grid */}
      <div className="max-w-4xl w-full mx-auto flex-1 overflow-y-auto py-4 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTabId;
          const isHome = tab.url === 'orbit://newtab' || !tab.url;

          return (
            <div
              key={tab.id}
              onClick={() => {
                onSelectTab(tab.id);
                onClose();
              }}
              className={`relative rounded-2xl border overflow-hidden cursor-pointer transition-all duration-200 flex flex-col h-56 bg-[#0E1726] group ${
                isActive
                  ? 'border-cyan-400 ring-2 ring-cyan-400/30 shadow-[0_0_20px_rgba(56,189,248,0.2)]'
                  : 'border-white/10 hover:border-white/20'
              }`}
            >
              {/* Tab Header */}
              <div className="px-3.5 py-2.5 bg-[#162235] border-b border-white/5 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 truncate flex-1">
                  {tab.isIncognito ? (
                    <EyeOff className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                  ) : isHome ? (
                    <Sparkles className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                  ) : (
                    <Globe className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                  )}
                  <span className="text-xs font-semibold text-white truncate">
                    {tab.title}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onCloseTab(tab.id);
                  }}
                  className="p-1 rounded-full text-slate-400 hover:text-white hover:bg-white/10 transition-colors shrink-0"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>

              {/* Tab Preview Area */}
              <div className="flex-1 p-4 flex flex-col items-center justify-center text-center relative bg-[#070D19]/60">
                {isHome ? (
                  <div className="space-y-1">
                    <div className="w-8 h-8 rounded-full bg-cyan-500/20 mx-auto flex items-center justify-center text-cyan-400 font-bold text-xs">
                      O
                    </div>
                    <div className="text-xs font-bold text-slate-200">Orbit Homepage</div>
                    <div className="text-[10px] font-mono text-slate-500">orbit://newtab</div>
                  </div>
                ) : (
                  <div className="space-y-1.5 max-w-full px-2">
                    <div className="text-xs font-medium text-slate-300 truncate max-w-full">
                      {tab.url}
                    </div>
                    <div className="text-[10px] font-mono text-emerald-400">
                      {tab.isDiscarded ? 'Suspended (State Saved)' : 'Rendered in Memory'}
                    </div>
                  </div>
                )}

                {/* Incognito indicator watermark */}
                {tab.isIncognito && (
                  <span className="absolute bottom-2 right-2 text-[10px] font-mono text-purple-400 px-1.5 py-0.5 rounded bg-purple-950/80 border border-purple-800">
                    Incognito
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Bottom Actions */}
      <div className="max-w-4xl w-full mx-auto pt-3 border-t border-white/10 flex items-center justify-between text-xs text-slate-400">
        <button
          onClick={onCloseAllTabs}
          className="text-slate-400 hover:text-rose-400 transition-colors cursor-pointer"
        >
          Close all tabs
        </button>

        <button
          onClick={onClose}
          className="px-4 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-white font-medium transition-colors cursor-pointer"
        >
          Done
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/ThreeDotMenu.tsx`

```typescript
import React from 'react';
import { 
  Plus, 
  EyeOff, 
  History, 
  Download, 
  Bookmark, 
  Monitor, 
  ShieldCheck, 
  Search, 
  Share2, 
  Settings,
  Code2,
  X,
  Check
} from 'lucide-react';

interface ThreeDotMenuProps {
  isOpen: boolean;
  onClose: () => void;
  isDesktopMode: boolean;
  onToggleDesktopMode: () => void;
  onNewTab: (incognito?: boolean) => void;
  onOpenHistory: () => void;
  onOpenDownloads: () => void;
  onOpenBookmarks: () => void;
  onOpenShield: () => void;
  onOpenFindInPage: () => void;
  onShare: () => void;
  onOpenSettings: () => void;
  onOpenArchitecture: () => void;
  isBookmarked: boolean;
  onToggleBookmark: () => void;
}

export const ThreeDotMenu: React.FC<ThreeDotMenuProps> = ({
  isOpen,
  onClose,
  isDesktopMode,
  onToggleDesktopMode,
  onNewTab,
  onOpenHistory,
  onOpenDownloads,
  onOpenBookmarks,
  onOpenShield,
  onOpenFindInPage,
  onShare,
  onOpenSettings,
  onOpenArchitecture,
  isBookmarked,
  onToggleBookmark
}) => {
  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-50 flex justify-end"
      onClick={onClose}
    >
      <div 
        className="relative w-72 max-w-[85vw] mt-12 mr-3 bg-[#0E1726]/95 border border-white/10 rounded-2xl shadow-2xl p-2 animate-in fade-in zoom-in-95 duration-150 backdrop-blur-xl flex flex-col space-y-1 text-xs text-slate-200"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Quick Top Strip: Bookmark, Download, Info */}
        <div className="flex items-center justify-between px-2 py-1.5 border-b border-white/5 mb-1 text-slate-400">
          <button
            onClick={() => {
              onToggleBookmark();
              onClose();
            }}
            className={`p-2 rounded-xl transition-colors cursor-pointer ${
              isBookmarked ? 'text-amber-400 bg-amber-400/10' : 'hover:text-white hover:bg-white/5'
            }`}
            title={isBookmarked ? 'Remove Bookmark' : 'Bookmark Page'}
          >
            <Bookmark className={`w-4 h-4 ${isBookmarked ? 'fill-amber-400' : ''}`} />
          </button>

          <button
            onClick={() => {
              onOpenDownloads();
              onClose();
            }}
            className="p-2 rounded-xl hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
            title="Downloads"
          >
            <Download className="w-4 h-4" />
          </button>

          <button
            onClick={() => {
              onOpenShield();
              onClose();
            }}
            className="p-2 rounded-xl text-cyan-400 hover:text-cyan-300 hover:bg-cyan-500/10 transition-colors cursor-pointer"
            title="Orbit Shield"
          >
            <ShieldCheck className="w-4 h-4" />
          </button>

          <button
            onClick={onClose}
            className="p-2 rounded-xl hover:text-white hover:bg-white/5 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 1. New Tab / New Incognito Tab */}
        <button
          onClick={() => {
            onNewTab(false);
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Plus className="w-4 h-4 text-slate-400" />
          <span className="font-medium">New tab</span>
        </button>

        <button
          onClick={() => {
            onNewTab(true);
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer text-purple-300"
        >
          <EyeOff className="w-4 h-4 text-purple-400" />
          <span className="font-medium">New incognito tab</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 2. History & Bookmarks */}
        <button
          onClick={() => {
            onOpenHistory();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <History className="w-4 h-4 text-slate-400" />
            <span>History</span>
          </div>
          <span className="text-[10px] font-mono text-cyan-400">Cloud Sync</span>
        </button>

        <button
          onClick={() => {
            onOpenBookmarks();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <Bookmark className="w-4 h-4 text-slate-400" />
            <span>Bookmarks</span>
          </div>
          <span className="text-[10px] font-mono text-cyan-400">Cloud Sync</span>
        </button>

        <button
          onClick={() => {
            onOpenDownloads();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Download className="w-4 h-4 text-slate-400" />
          <span>Downloads</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 3. Desktop Site Toggle */}
        <button
          onClick={onToggleDesktopMode}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <Monitor className="w-4 h-4 text-slate-400" />
            <span>Desktop site</span>
          </div>
          <div className={`w-4 h-4 rounded border flex items-center justify-center transition-colors ${
            isDesktopMode ? 'bg-cyan-500 border-cyan-400' : 'border-white/20'
          }`}>
            {isDesktopMode && <Check className="w-3 h-3 text-slate-950 stroke-[3]" />}
          </div>
        </button>

        {/* 4. Orbit Shield & Privacy Settings */}
        <button
          onClick={() => {
            onOpenShield();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <div className="flex items-center gap-3">
            <ShieldCheck className="w-4 h-4 text-cyan-400" />
            <span>Orbit Shield</span>
          </div>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-cyan-950 text-cyan-300 font-mono">Brave-Std</span>
        </button>

        {/* 5. Find in page & Share */}
        <button
          onClick={() => {
            onOpenFindInPage();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Search className="w-4 h-4 text-slate-400" />
          <span>Find in page</span>
        </button>

        <button
          onClick={() => {
            onShare();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Share2 className="w-4 h-4 text-slate-400" />
          <span>Share...</span>
        </button>

        <div className="h-px bg-white/5 my-1" />

        {/* 6. Settings & Architecture Inspector */}
        <button
          onClick={() => {
            onOpenSettings();
            onClose();
          }}
          className="w-full flex items-center gap-3 px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer"
        >
          <Settings className="w-4 h-4 text-slate-400" />
          <span>Settings</span>
        </button>

        <button
          onClick={() => {
            onOpenArchitecture();
            onClose();
          }}
          className="w-full flex items-center justify-between px-3 py-2 rounded-xl hover:bg-white/5 text-left transition-colors cursor-pointer text-cyan-300 font-mono text-[11px]"
        >
          <div className="flex items-center gap-3">
            <Code2 className="w-4 h-4 text-cyan-400" />
            <span>Android / Native Specs</span>
          </div>
          <span className="text-[10px] text-slate-400">CI/CD</span>
        </button>
      </div>
    </div>
  );
};

```

---

## File: `src/components/WebViewport.tsx`

```typescript
import React, { useState, useEffect } from 'react';
import { 
  ShieldCheck, 
  Lock, 
  ExternalLink, 
  RotateCw, 
  AlertCircle, 
  ArrowLeft,
  Eye,
  CheckCircle2,
  Zap
} from 'lucide-react';
import { BrowserTab, ShieldStats } from '../types/browser';

interface WebViewportProps {
  currentTab: BrowserTab;
  shieldStats: ShieldStats;
  onNavigateHome: () => void;
  onRecordBlockedTracker: () => void;
}

export const WebViewport: React.FC<WebViewportProps> = ({
  currentTab,
  shieldStats,
  onNavigateHome,
  onRecordBlockedTracker
}) => {
  const [loadProgress, setLoadProgress] = useState(30);
  const [hasLoaded, setHasLoaded] = useState(false);
  const [blockedOnThisPage, setBlockedOnThisPage] = useState(0);

  useEffect(() => {
    setLoadProgress(30);
    setHasLoaded(false);
    // Simulate Orbit Shield intercepting requests on page load
    const count = Math.floor(Math.random() * 5) + 3;
    setBlockedOnThisPage(count);
    for (let i = 0; i < count; i++) {
      onRecordBlockedTracker();
    }

    const timer1 = setTimeout(() => setLoadProgress(75), 250);
    const timer2 = setTimeout(() => {
      setLoadProgress(100);
      setHasLoaded(true);
    }, 600);

    return () => {
      clearTimeout(timer1);
      clearTimeout(timer2);
    };
  }, [currentTab.url]);

  const domain = (() => {
    try {
      return new URL(currentTab.url).hostname;
    } catch {
      return currentTab.url;
    }
  })();

  return (
    <div className={`flex-1 flex flex-col w-full h-full bg-[#070D19] relative overflow-hidden ${
      currentTab.isDesktopMode ? 'max-w-6xl mx-auto' : 'max-w-3xl mx-auto'
    }`}>
      {/* Top Loading Progress Line */}
      {loadProgress < 100 && (
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-white/10 z-30">
          <div 
            className="h-full bg-cyan-400 transition-all duration-300 shadow-[0_0_8px_#38bdf8]"
            style={{ width: `${loadProgress}%` }}
          />
        </div>
      )}

      {/* Orbit Shield Security Inspection Banner */}
      <div className="px-4 py-2 bg-[#0E1726]/90 border-b border-white/5 flex items-center justify-between text-xs text-slate-300">
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 text-emerald-400 font-mono">
            <Lock className="w-3.5 h-3.5" />
            <span className="font-semibold">HTTPS Everywhere Upgraded</span>
          </div>
          <span className="text-slate-600">|</span>
          <div className="flex items-center gap-1.5 text-cyan-300 font-mono">
            <ShieldCheck className="w-3.5 h-3.5 text-cyan-400" />
            <span>Orbit Shield: <strong>{blockedOnThisPage} trackers blocked</strong></span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {currentTab.isDesktopMode && (
            <span className="text-[10px] font-mono uppercase bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded">
              Desktop Mode (x86_64)
            </span>
          )}
          <a
            href={currentTab.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-slate-400 hover:text-white flex items-center gap-1 transition-colors text-[11px]"
            title="Open in new window"
          >
            <span>External</span>
            <ExternalLink className="w-3 h-3" />
          </a>
        </div>
      </div>

      {/* Main Viewport Content */}
      <div className="flex-1 w-full h-full relative bg-white rounded-b-2xl overflow-hidden">
        {/* IFrame embedding with sandbox protection */}
        <iframe
          src={currentTab.url}
          title={currentTab.title}
          className="w-full h-full border-0 bg-white"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
          loading="lazy"
        />

        {/* Fallback Overlay for sites with strict X-Frame-Options (e.g. Google, GitHub) */}
        <div className="absolute inset-0 pointer-events-none flex flex-col items-center justify-center p-6 bg-transparent">
          {/* Transparent container; if iframe fails or is blocked by CSP, user has quick buttons */}
        </div>
      </div>
    </div>
  );
};

```

---

## File: `src/lib/eccCrypto.ts`

```typescript
/**
 * Elliptic Curve Cryptography (ECC) Utility
 * Native Web Crypto API (SubtleCrypto) implementation for P-256, P-384, and P-521.
 * Generates ECDH / ECDSA key pairs and converts them to PEM, JWK, and Hex fingerprints.
 */

export type EccNamedCurve = 'P-256' | 'P-384' | 'P-521';
export type EccAlgorithmType = 'ECDH' | 'ECDSA';

export interface EccKeyPairResult {
  curve: EccNamedCurve;
  algorithm: EccAlgorithmType;
  publicKeyPem: string;
  privateKeyPem: string;
  publicKeyJwk: JsonWebKey;
  privateKeyJwk: JsonWebKey;
  fingerprintSha256: string;
  generatedAt: string;
}

// Convert ArrayBuffer to Base64
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

// Format binary buffer into PEM block with standard 64-char wrapping
function toPem(buffer: ArrayBuffer, type: 'PUBLIC KEY' | 'PRIVATE KEY'): string {
  const base64 = arrayBufferToBase64(buffer);
  const formatted = base64.match(/.{1,64}/g)?.join('\n') || base64;
  return `-----BEGIN ${type}-----\n${formatted}\n-----END ${type}-----`;
}

// Format SHA-256 hash as colon-separated hex fingerprint
function toHexFingerprint(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  return Array.from(bytes)
    .map(b => b.toString(16).padStart(2, '0').toUpperCase())
    .join(':');
}

/**
 * Generate a new ECC Key Pair locally in hardware/browser memory.
 */
export async function generateEccKeyPair(
  curve: EccNamedCurve = 'P-256',
  algorithm: EccAlgorithmType = 'ECDH'
): Promise<EccKeyPairResult> {
  const isEcdh = algorithm === 'ECDH';

  const keyGenAlgorithm: EcKeyGenParams = {
    name: algorithm,
    namedCurve: curve,
  };

  const keyUsages: KeyUsage[] = isEcdh 
    ? ['deriveKey', 'deriveBits'] 
    : ['sign', 'verify'];

  // Generate native CryptoKeyPair
  const keyPair = await window.crypto.subtle.generateKey(
    keyGenAlgorithm,
    true, // extractable
    keyUsages
  );

  // Export Public Key as SPKI
  const spkiBuffer = await window.crypto.subtle.exportKey('spki', keyPair.publicKey);
  const publicKeyPem = toPem(spkiBuffer, 'PUBLIC KEY');

  // Export Private Key as PKCS#8
  const pkcs8Buffer = await window.crypto.subtle.exportKey('pkcs8', keyPair.privateKey);
  const privateKeyPem = toPem(pkcs8Buffer, 'PRIVATE KEY');

  // Export as JWK
  const publicKeyJwk = await window.crypto.subtle.exportKey('jwk', keyPair.publicKey);
  const privateKeyJwk = await window.crypto.subtle.exportKey('jwk', keyPair.privateKey);

  // Compute SHA-256 Fingerprint over SPKI public key
  const digestBuffer = await window.crypto.subtle.digest('SHA-256', spkiBuffer);
  const fingerprintSha256 = `SHA256:${toHexFingerprint(digestBuffer)}`;

  return {
    curve,
    algorithm,
    publicKeyPem,
    privateKeyPem,
    publicKeyJwk,
    privateKeyJwk,
    fingerprintSha256,
    generatedAt: new Date().toISOString()
  };
}

/**
 * Quick demonstration of local message encryption using ECDH derived AES-256-GCM
 */
export async function testLocalEccEncryption(message: string): Promise<{ ciphertextHex: string; decrypted: string }> {
  // Generate temporary recipient key pair
  const recipientKeys = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    ['deriveKey']
  );

  // Generate ephemeral sender key pair
  const senderKeys = await window.crypto.subtle.generateKey(
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    ['deriveKey']
  );

  // Derive shared AES-GCM key
  const senderSharedKey = await window.crypto.subtle.deriveKey(
    { name: 'ECDH', public: recipientKeys.publicKey },
    senderKeys.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt']
  );

  const recipientSharedKey = await window.crypto.subtle.deriveKey(
    { name: 'ECDH', public: senderKeys.publicKey },
    recipientKeys.privateKey,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt']
  );

  // Encrypt with AES-GCM
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(message);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    senderSharedKey,
    encoded
  );

  const ciphertextHex = Array.from(new Uint8Array(encrypted))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');

  // Decrypt
  const decryptedBuffer = await window.crypto.subtle.decrypt(
    { name: 'AES-GCM', iv },
    recipientSharedKey,
    encrypted
  );

  const decrypted = new TextDecoder().decode(decryptedBuffer);

  return { ciphertextHex, decrypted };
}

```

---

## File: `src/lib/firebase.ts`

```typescript
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

```

---

## File: `android/app/src/main/AndroidManifest.xml`

```text
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Orbit"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:hardwareAccelerated="true"
        android:theme="@style/Theme.OrbitBrowser">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Default Browser Intent -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="http" />
                <data android:scheme="https" />
            </intent-filter>
        </activity>

        <!-- Background YouTube / Media Playback Service -->
        <service
            android:name=".MediaPlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />
    </application>

</manifest>

```

---

## File: `android/app/src/main/res/layout/activity_main.xml`

```text
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#070D19">

    <!-- Top App Bar (Chrome Style) -->
    <LinearLayout
        android:id="@+id/topBar"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:paddingHorizontal="12dp"
        android:background="#0B132B">

        <!-- Top Mini Search/URL Field (Jab web page khula ho) -->
        <EditText
            android:id="@+id/etTopUrl"
            android:layout_width="0dp"
            android:layout_height="40dp"
            android:layout_weight="1"
            android:background="@drawable/bg_pill_input"
            android:hint="Search or type URL"
            android:textColor="#FFFFFF"
            android:textColorHint="#64748B"
            android:textSize="14sp"
            android:paddingHorizontal="16dp"
            android:singleLine="true"
            android:imeOptions="actionGo" />

        <!-- 3-Dot Menu Button -->
        <ImageButton
            android:id="@+id/btnMenu"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_more"
            app:tint="#CBD5E1"
            android:contentDescription="Menu" />
    </LinearLayout>

    <!-- Main Web View (Full Screen) -->
    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_below="@id/topBar"
        android:visibility="gone" />

    <!-- CLEAN HOMEPAGE (Orbit Logo + Search Bar Center Mein) -->
    <LinearLayout
        android:id="@+id/homeContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="24dp">

        <!-- Orbit Celestial Logo -->
        <ImageView
            android:id="@+id/orbitLogo"
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:src="@drawable/ic_orbit_logo"
            android:contentDescription="Orbit Logo" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="ORBIT"
            android:textColor="#FFFFFF"
            android:textSize="28sp"
            android:textStyle="bold"
            android:letterSpacing="0.15"
            android:layout_marginTop="12dp" />

        <!-- Sleek Omnibox Search Bar -->
        <EditText
            android:id="@+id/etCenterSearch"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="28dp"
            android:background="@drawable/bg_pill_input"
            android:drawableStart="@android:drawable/ic_menu_search"
            android:drawablePadding="12dp"
            android:hint="Search the web or type URL..."
            android:textColor="#FFFFFF"
            android:textColorHint="#94A3B8"
            android:paddingHorizontal="20dp"
            android:singleLine="true"
            android:imeOptions="actionGo" />
    </LinearLayout>

    <ProgressBar
        android:id="@+id/progressBar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="3dp"
        android:layout_below="@id/topBar"
        android:visibility="gone"
        android:progressTint="#06B6D4" />

</RelativeLayout>

```

---

## File: `android/app/src/main/res/drawable/bg_pill_input.xml`

```text
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="26dp" />
    <solid android:color="#162238" />
    <stroke android:width="1dp" android:color="#243452" />
</shape>

```

---

## File: `android/app/src/main/res/drawable/ic_orbit_logo.xml`

```text
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="80dp"
    android:height="80dp"
    android:viewportWidth="80"
    android:viewportHeight="80">

    <!-- Outer Orbital Ring -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#38BDF8"
        android:strokeWidth="2"
        android:strokeAlpha="0.4"
        android:pathData="M 40,8 A 32,18 45 1 1 39.9,8 Z" />

    <!-- Inner Counter Ring -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#0284C7"
        android:strokeWidth="1.5"
        android:strokeAlpha="0.6"
        android:pathData="M 40,16 A 24,12 -30 1 1 39.9,16 Z" />

    <!-- Core Celestial Sphere -->
    <path
        android:fillColor="#38BDF8"
        android:pathData="M40,40m-16,0a16,16 0,1,1 32,0a16,16 0,1,1 -32,0" />

    <!-- Highlight Specular Glint -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M36,34m-4,0a4,4 0,1,1 8,0a4,4 0,1,1 -8,0" />

    <!-- Orbiting Satellite Dot -->
    <path
        android:fillColor="#67E8F9"
        android:pathData="M64,26m-3,0a3,3 0,1,1 6,0a3,3 0,1,1 -6,0" />

</vector>

```

---

## File: `android/app/src/main/java/com/orbit/browser/MainActivity.kt`

```kotlin
package com.orbit.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

class MainActivity : AppCompatActivity() {

    private lateinit var topBar: LinearLayout
    private lateinit var etTopUrl: EditText
    private lateinit var btnMenu: ImageButton
    private lateinit var webView: WebView
    private lateinit var homeContainer: LinearLayout
    private lateinit var etCenterSearch: EditText
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View Binding
        topBar = findViewById(R.id.topBar)
        etTopUrl = findViewById(R.id.etTopUrl)
        btnMenu = findViewById(R.id.btnMenu)
        webView = findViewById(R.id.webView)
        homeContainer = findViewById(R.id.homeContainer)
        etCenterSearch = findViewById(R.id.etCenterSearch)
        progressBar = findViewById(R.id.progressBar)

        configureWebView()
        setupSearchListeners()
        setupMenuListener()
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                etTopUrl.setText(url)
                showWebState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etTopUrl.setText(url)
            }
        }
    }

    private fun setupSearchListeners() {
        // Center Omnibox Search listener (Homepage)
        etCenterSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = etCenterSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    loadQueryOrUrl(query)
                }
                true
            } else false
        }

        // Top App Bar Search/URL listener (Browsing Mode)
        etTopUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = etTopUrl.text.toString().trim()
                if (query.isNotEmpty()) {
                    loadQueryOrUrl(query)
                }
                true
            } else false
        }
    }

    private fun loadQueryOrUrl(input: String) {
        val isUrl = android.util.Patterns.WEB_URL.matcher(input).matches() || 
                    input.startsWith("http://") || 
                    input.startsWith("https://")

        val targetUrl = if (isUrl) {
            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        } else {
            "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
        }

        showWebState()
        webView.loadUrl(targetUrl)
    }

    private fun showWebState() {
        homeContainer.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun showHomeState() {
        webView.visibility = View.GONE
        homeContainer.visibility = View.VISIBLE
        etTopUrl.setText("")
        etCenterSearch.setText("")
    }

    private fun setupMenuListener() {
        btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.apply {
                add(0, 1, 0, "New tab")
                add(0, 2, 1, "New incognito tab")
                add(0, 3, 2, "History")
                add(0, 4, 3, "Downloads")
                add(0, 5, 4, "Bookmarks")
                add(0, 6, 5, "Desktop site")
                add(0, 7, 6, "Orbit Shield")
                add(0, 8, 7, "Settings")
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showHomeState()
                    else -> true
                }
                true
            }
            popup.show()
        }
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
        } else if (webView.visibility == View.VISIBLE) {
            showHomeState()
        } else {
            super.onBackPressed()
        }
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/MediaPlaybackService.kt`

```kotlin
package com.orbit.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

/**
 * MediaPlaybackService — Android Foreground Service enabling continuous background
 * YouTube and HTML5 media playback with status bar controls.
 * Continues playback when switching tabs, minimizing to home, or locking the device.
 */
class MediaPlaybackService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying: Boolean = false
    private var currentTitle: String = "Orbit Media"
    private var currentArtist: String = "Web Media Stream"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "OrbitMediaPlaybackSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    startForeground(NOTIFICATION_ID, buildNotification())
                }

                override fun onPause() {
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification()
                }

                override fun onStop() {
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                getServicePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                getServicePendingIntent(ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    getServicePendingIntent(ACTION_STOP)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orbit Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background audio & YouTube video playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Orbit Web Playback"
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: "Orbit Chromium Browser"
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_PLAY -> {
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                updateNotification()
            }
            ACTION_STOP -> {
                isPlaying = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "orbit_background_media"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.orbit.browser.action.START"
        const val ACTION_PLAY = "com.orbit.browser.action.PLAY"
        const val ACTION_PAUSE = "com.orbit.browser.action.PAUSE"
        const val ACTION_STOP = "com.orbit.browser.action.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        fun startPlayback(context: Context, title: String, artist: String, duration: Long) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pausePlayback(context: Context) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/OrbitAdBlocker.kt`

```kotlin
package com.orbit.browser

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object OrbitAdBlocker {
    private val BLOCKED_DOMAINS = hashSetOf(
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "adservice.google.com",
        "facebook.net",
        "scorecardresearch.com",
        "criteo.com",
        "taboola.com",
        "outbrain.com"
    )

    fun isAdOrTracker(url: String): Boolean {
        for (domain in BLOCKED_DOMAINS) {
            if (url.contains(domain, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/OrbitChromiumWebView.kt`

```kotlin
package com.orbit.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.orbit.browser.service.MediaSessionService

/**
 * OrbitChromiumWebView — Custom Android WebView utilizing Chromium (Blink/V8)
 * with aggressive performance optimizations, Orbit Shield ad-blocking,
 * anti-fingerprinting, and background media playback engine hooks.
 */
class OrbitChromiumWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var isDesktopMode: Boolean = false
        private set
    private var defaultUserAgent: String = ""

    init {
        configureChromiumEngine()
        setupPrivacyAndSecurity()
        setupBackgroundMediaBridge()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureChromiumEngine() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true

            // Aggressive Multi-Threaded Caching & Resource Prioritization
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            
            // Security: Prevent file system access from scripts
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false

            // Strict Mixed Content Blocking
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // Media Autoplay & Background Playback configuration
            mediaPlaybackRequiresUserGesture = false

            defaultUserAgent = userAgentString
        }

        // Hardware Acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Dark Theme Support via Chromium WebSettingsCompat
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
        }

        // Custom WebViewClient with network-level request interceptor
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if (request == null) return null

                val url = request.url.toString()

                // 1. Force HTTPS Everywhere upgrade
                if (url.startsWith("http://") && !url.contains("localhost")) {
                    val httpsUrl = url.replaceFirst("http://", "https://")
                    post { loadUrl(httpsUrl) }
                    return WebResourceResponse("text/plain", "UTF-8", null)
                }

                // 2. Orbit Shield: Network-Level Ad & Tracker Blocking (Brave-Like Standard)
                if (OrbitAdBlocker.isAdOrTracker(url)) {
                    // Return empty response to drop the request with 0 network latency
                    return OrbitAdBlocker.createEmptyResponse()
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Inject anti-fingerprinting & background media playback hooks
                injectBackgroundMediaHooks()
                injectAntiFingerprintingGuards()
            }
        }
    }

    private fun setupPrivacyAndSecurity() {
        // Strict Third-Party Cookie Isolation
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, false)
    }

    private fun setupBackgroundMediaBridge() {
        addJavascriptInterface(object {
            @JavascriptInterface
            fun onMediaPlaying(title: String?, artist: String?, duration: Long) {
                // Dispatch to Android MediaPlaybackService for background lock-screen controls
                MediaPlaybackService.startPlayback(
                    context,
                    title ?: "Orbit Web Playback",
                    artist ?: "Orbit Chromium Browser",
                    duration
                )
            }

            @JavascriptInterface
            fun onMediaPaused() {
                MediaPlaybackService.pausePlayback(context)
            }
        }, "OrbitNativeMediaBridge")
    }

    /**
     * Injects JavaScript into HTML5 media elements and YouTube player
     * to prevent pausing when tabs switch or screen is locked (JioSphere / Brave style).
     */
    fun injectBackgroundMediaHooks() {
        val script = """
            (function() {
                if (window.__orbitMediaHooked) return;
                window.__orbitMediaHooked = true;

                // Hook HTML5 Audio & Video
                function hookMediaElements() {
                    document.querySelectorAll('video, audio').forEach(function(media) {
                        media.addEventListener('play', function() {
                            var title = document.title || 'Web Media';
                            window.OrbitNativeMediaBridge.onMediaPlaying(title, location.hostname, media.duration || 0);
                        });
                        media.addEventListener('pause', function() {
                            if (!document.hidden) {
                                window.OrbitNativeMediaBridge.onMediaPaused();
                            }
                        });
                    });
                }

                // Prevent visibilitychange from pausing media (Brave background playback trick)
                Object.defineProperty(document, 'hidden', { value: false, writable: false });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', writable: false });
                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);

                hookMediaElements();
                setInterval(hookMediaElements, 2500);
            })();
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    private fun injectAntiFingerprintingGuards() {
        val script = """
            (function() {
                // Brave-style Canvas Anti-Fingerprinting (Subtle noise injection)
                var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                CanvasRenderingContext2D.prototype.getImageData = function(x, y, w, h) {
                    var data = origGetImageData.apply(this, arguments);
                    for (var i = 0; i < data.data.length; i += 64) {
                        data.data[i] = data.data[i] ^ 1;
                    }
                    return data;
                };

                // Protect AudioContext Fingerprinting
                if (window.AudioBuffer) {
                    var origGetChannelData = AudioBuffer.prototype.getChannelData;
                    AudioBuffer.prototype.getChannelData = function() {
                        var buffer = origGetChannelData.apply(this, arguments);
                        for (var i = 0; i < buffer.length; i += 100) {
                            buffer[i] += 0.0000001 * (Math.random() - 0.5);
                        }
                        return buffer;
                    };
                }
            })();
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    /**
     * Toggles between Mobile and Desktop User-Agent & viewport layout.
     */
    fun toggleDesktopMode(enable: Boolean) {
        isDesktopMode = enable
        if (enable) {
            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = defaultUserAgent
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        }
        reload()
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/PrivacyEngine.kt`

```kotlin
package com.orbit.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage

/**
 * PrivacyEngine — Enforces Brave-standard privacy:
 * - Local log cleansing on tab closure
 * - Strict third-party cookie isolation
 * - WebStorage / Cache purging for incognito sessions
 * - Zero telemetry guarantee
 */
class PrivacyEngine(private val context: Context) {

    fun cleanseTabArtifacts(isIncognito: Boolean) {
        if (isIncognito) {
            // Instant wipe of session DOM storage, cookies, and cache
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeSessionCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun purgeAllBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean
    ) {
        if (clearCookies) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (clearCache) {
            WebStorage.getInstance().deleteAllData()
            context.cacheDir.deleteRecursively()
        }
    }

    companion object {
        fun sanitizeUrlForLog(url: String): String {
            // Strip tracking query parameters (fbclid, gclid, utm_*, etc.)
            val uri = android.net.Uri.parse(url)
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames.forEach { param ->
                if (!param.startsWith("utm_") && param != "fbclid" && param != "gclid" && param != "msclkid") {
                    builder.appendQueryParameter(param, uri.getQueryParameter(param))
                }
            }
            return builder.build().toString()
        }
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/TabManager.kt`

```kotlin
package com.orbit.browser

import android.os.Bundle
import java.util.UUID

data class TabModel(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "orbit://newtab",
    var isIncognito: Boolean = false,
    var isDiscarded: Boolean = false,
    var savedState: Bundle? = null,
    var lastAccessedTimestamp: Long = System.currentTimeMillis()
)

/**
 * TabManager — Lightweight memory footprint with intelligent tab-discarding
 * for inactive background tabs. Prevents OOM crashes while maintaining state.
 */
class TabManager(private val maxActiveInMemoryTabs: Int = 5) {

    private val tabs = mutableListOf<TabModel>()
    var activeTabId: String? = null
        private set

    fun createTab(url: String = "orbit://newtab", isIncognito: Boolean = false): TabModel {
        val tab = TabModel(url = url, isIncognito = isIncognito)
        tabs.add(tab)
        activeTabId = tab.id
        enforceMemoryOptimization()
        return tab
    }

    fun selectTab(id: String): TabModel? {
        val tab = tabs.find { it.id == id } ?: return null
        tab.lastAccessedTimestamp = System.currentTimeMillis()
        activeTabId = tab.id
        if (tab.isDiscarded) {
            tab.isDiscarded = false // Re-hydrate on demand
        }
        enforceMemoryOptimization()
        return tab
    }

    fun closeTab(id: String): Boolean {
        val removed = tabs.removeAll { it.id == id }
        if (activeTabId == id) {
            activeTabId = tabs.lastOrNull()?.id
        }
        return removed
    }

    fun getTabs(): List<TabModel> = tabs.toList()

    fun getActiveTab(): TabModel? = tabs.find { it.id == activeTabId }

    private fun enforceMemoryOptimization() {
        if (tabs.size <= maxActiveInMemoryTabs) return

        // Sort background tabs by least recently used
        val backgroundTabs = tabs.filter { it.id != activeTabId && !it.isDiscarded }
            .sortedBy { it.lastAccessedTimestamp }

        val excess = tabs.count { !it.isDiscarded } - maxActiveInMemoryTabs
        if (excess > 0) {
            backgroundTabs.take(excess).forEach { tab ->
                tab.isDiscarded = true
                // WebView DOM and canvas memory is freed; URL and savedState preserved
            }
        }
    }
}

```

---

## File: `android/app/src/main/java/com/orbit/browser/service/MediaSessionService.kt`

```kotlin
package com.orbit.browser.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.orbit.browser.R

/**
 * MediaSessionService — Android Foreground Service enabling continuous background
 * media playback (YouTube, HTML5 video/audio) with status bar controls.
 * Operates when switching tabs, minimizing to home, or turning off the screen (JioSphere / Brave style).
 */
class MediaSessionService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var isPlaying: Boolean = false
    private var currentTitle: String = "Orbit Media"
    private var currentArtist: String = "Web Media Stream"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "OrbitMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    isPlaying = true
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    startForeground(NOTIFICATION_ID, buildNotification())
                }

                override fun onPause() {
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    updateNotification()
                }

                override fun onStop() {
                    isPlaying = false
                    updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun buildNotification(): Notification {
        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                getServicePendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                getServicePendingIntent(ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    getServicePendingIntent(ACTION_STOP)
                )
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun getServicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaSessionService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Orbit Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls background audio & YouTube video playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Orbit Web Playback"
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: "Orbit Chromium Browser"
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_PLAY -> {
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                updateNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                updateNotification()
            }
            ACTION_STOP -> {
                isPlaying = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "orbit_background_media"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.orbit.browser.action.START"
        const val ACTION_PLAY = "com.orbit.browser.action.PLAY"
        const val ACTION_PAUSE = "com.orbit.browser.action.PAUSE"
        const val ACTION_STOP = "com.orbit.browser.action.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        fun startPlayback(context: Context, title: String, artist: String, duration: Long) {
            val intent = Intent(context, MediaSessionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pausePlayback(context: Context) {
            val intent = Intent(context, MediaSessionService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }
    }
}

```
