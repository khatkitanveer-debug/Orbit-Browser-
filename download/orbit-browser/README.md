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
