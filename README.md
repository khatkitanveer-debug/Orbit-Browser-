# 🪐 Orbit Browser — Fast & Private (Chrome-jaisa, GLM 5.3 AI ke saath)

**Fast. Private. Aapka.** — Ek privacy-first browser jo Chrome jaisa lagta hai, built-in **GLM 5.3 AI** search + assistant deta hai, aur aapka saara data **sirf aapke paas** rakhta hai.

Main screen bilkul clean hai — sirf **Orbit ka logo** aur **ek search bar**. Bas.

---

## Kya-kya milta hai

| Feature | Detail |
| --- | --- |
| Chrome-jaisa UI | Multi-tabs, omnibox (URL + search), back/forward/reload, bookmarks |
| Clean main screen | Sirf Orbit logo + search bar — kuch aur nahi |
| Orbit AI Search | Web search + GLM 5.3 ka streaming AI answer, citations ke saath |
| Orbit AI Sidebar | Chat, page summarize — GLM 5.3 powered |
| Private Mode | Toolbar ke eye-off button se — history/chat kuch bhi save nahi hoti |
| Local-first data | History, bookmarks, AI chat — sab **IndexedDB** me (aapka device), koi server nahi |
| Zero tracking | Koi analytics/ads/third-party favicon service nahi |
| Optional Cloud Sync | Sirf **aapke apne Firebase project** me — aapki permission se |
| Data export | Poora data JSON me download — aapka data, aapki file |
| Keyboard shortcuts | Ctrl+T (naya tab), Ctrl+W (band), Ctrl+L (omnibox), Ctrl+H (history), Ctrl+D (bookmark), Ctrl+Shift+I (private) |

## Privacy ka sach (honest breakdown)

- **Kabhi nahi jata kahin:** history, bookmarks, saved Firebase config, AI chat history — ye sab aapke device ke IndexedDB me hi rehte hain.
- **AI answer ke liye process hota hai:** Orbit AI Search ki query aur AI chat ke messages, GLM 5.3 API (Z.ai) se process hote hain — bina iske AI answer possible nahi. Browsing history is API ko **kabhi nahi** bheji jati.
- **Cloud sync 100% optional:** default OFF. On karo to data sirf us Firebase project me jata hai jiska API key AAP dete ho — matlab sirf aap access kar sakte ho.

## Chalane ka tarika (local dev)

```bash
bun install     # dependencies
bun run dev     # http://localhost:3000
```

## GitHub par push karna (step-by-step)

1. GitHub par naya repo banao (jaise `orbit-browser`) — **Private** rakhna best hai.
2. Phir terminal me:

```bash
git init
git add .
git commit -m "Orbit Browser v1.0 — fast & private"
git branch -M main
git remote add origin https://github.com/AAPKA-USERNAME/orbit-browser.git
git push -u origin main
```

3. (Optional) Vercel pe deploy karo — Vercel me bas repo import karo, Next.js auto-detect ho jayega.

> **Zaroori:** `.gitignore` me `.z-ai-config`, `.env*`, `dev.log` already hain — ye secrets wali files repo me kabhi nahi jani chahiye. Push se pehle `git status` me in files ka dikhna **nahi** chahiye.

## Android APK (native) 📱

Orbit sirf web app nahi hai — **native Android app** bhi hai (`android/` folder):

- **Real browser (Chromium engine)**: har site DIRECT khulti hai — cookies, JS, login, redirects sab native browser jaisa
- **Google search built-in**: omnibox/newtab me kuch bhi likho → Google results. AI search optional hai (menu -> AI Search)
- **100% local data**: history/bookmarks device ke andar (JSON files), koi cloud nahi
- **AI search (optional)**: Settings me apna Orbit web backend URL daalo (jaise Vercel deployment) — query sirf answer ke liye process hoti hai
- **OTA updates**: app khud GitHub se `ota/update.json` check karti hai → APK BACKGROUND me download hota hai → Install button khud khul jata hai → install par purana version REPLACE (data safe)

### APK build karna

```bash
cd android
gradle assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
```

### APK download (sabse aasan)

**[Releases page](https://github.com/khatkitanveer-debug/Orbit-Browser-/releases)** se latest `orbit-browser-<version>.apk` download karo → install karo (Unknown sources allow karna hoga pehli baar).

Waise hi milega:
- Repo ke `ota/` folder me APK + `update.json` (yehi link app ki OTA pipeline check karti hai)
- GitHub Actions artifacts (push par auto-build hota hai)

### OTA update kaise kaam karta hai

1. `ota/update.json` me versionCode/versionName/apkUrl hota hai
2. Naya version release karna: APK build karke `ota/` me daalo, update.json me versionCode badhao, push karo
3. Installed app agle launch par update dikhayegi → download → install prompt

## Firebase Cloud Sync setup (2 minute)

1. [console.firebase.google.com](https://console.firebase.google.com) → apna project banao.
2. **Build → Firestore Database → Create database.**
3. **Rules** tab me ye paste karke Publish karo:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /orbitBrowser/{doc} {
      allow read, write: if true; // personal project ke liye
    }
  }
}
```

4. **Project Settings → Your apps → Web app** banao → `firebaseConfig` ke values copy karo.
5. Orbit Browser → Settings → **Firebase Cloud Sync** me paste karke **Config save karo → Sync Now**.

Data ab `orbitBrowser/sync` document me hai — sirf aapke project me. Restore button se kisi bhi device par wapas la sakte ho.

## Tech stack

- **Next.js + TypeScript + Tailwind CSS 4 + shadcn/ui** — UI framework
- **GLM 5.3** (z-ai-web-dev-sdk) — AI search, chat, summarize (backend API routes me, safe)
- **IndexedDB** — local data store (zero server)
- **Firebase JS SDK (optional)** — sirf aapke apne project me sync
- **Page proxy** — sites sandboxed iframe me render hoti hain; block hone par AI **Reader Mode** fallback

## Honest limitations

- Ye ek **web-based browser** hai — sites proxy + sandboxed iframe se dikhti hain. Login/banking/Google jaisi sites iframe block karti hain (har web-browser-ke-andar-browser ki yahi had hai) — unke liye "naye tab me kholo" button hai.
- Full native browser chahiye (real Chrome engine) to is UI ko **Electron** ya **Tauri** me wrap karna hoga — structure already component-based hai, port karna aasan hai.
- Proxied pages me kuch site scripts opaque-origin sandbox ki wajah se weak ho sakti hain — Reader Mode fallback rakha gaya hai.
