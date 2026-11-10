"use client";

import { ShieldCheck, Database, CloudCog, Github, Zap, Server, Info } from "lucide-react";

export function AboutPage() {
  return (
    <div className="h-full overflow-y-auto orbit-scroll bg-background">
      <div className="max-w-2xl mx-auto px-6 py-8 space-y-5">
        <div className="flex items-center gap-2">
          <Info className="w-5 h-5 text-emerald-600" />
          <h1 className="text-lg font-semibold">About & Privacy</h1>
        </div>

        <section className="p-5 rounded-xl border border-emerald-600/30 bg-emerald-600/[0.04]">
          <h2 className="flex items-center gap-2 text-sm font-semibold mb-2">
            <Zap className="w-4 h-4 text-emerald-600" /> Orbit Browser — Fast. Private. Aapka.
          </h2>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Ye ek Chrome-jaisa, privacy-first browser hai jo <b>GLM 5.3</b> AI ke saath bana hai. Design philosophy simple hai:
            aapka browsing data <b>kabhi kisi company ke server par nahi jata</b> — jab tak aap khud na chaaho.
          </p>
        </section>

        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="flex items-center gap-2 text-sm font-semibold mb-3">
            <ShieldCheck className="w-4 h-4 text-emerald-600" /> Privacy kaise kaam karti hai
          </h2>
          <ul className="text-sm text-muted-foreground space-y-2.5 list-disc ml-5 leading-relaxed">
            <li>
              <b className="text-foreground">History / Bookmarks / AI chat:</b> aapke device ke IndexedDB me — local-first.
              Browser band karo, data phir bhi aapke paas. Koi server, koi account nahi.
            </li>
            <li>
              <b className="text-foreground">Koi tracking nahi:</b> analytics nahi, ads nahi, fingerprinting nahi, third-party
              favicon services nahi — tab icons pure locally generate hote hain (letter tiles).
            </li>
            <li>
              <b className="text-foreground">Search:</b> Orbit AI Search me query sirf answer banane ke liye process hoti hai —
              Google ko nahi jati. Search history sirf aapke device me.
            </li>
            <li>
              <b className="text-foreground">AI chat:</b> GLM 5.3 se baat karte waqt messages Z.ai API se process hote hain
              (yeh AI ka kaam hai) — lekin chat history device par hi save hoti hai, aur private mode me save bhi nahi hoti.
            </li>
            <li>
              <b className="text-foreground">Private mode:</b> toggle on karo (toolbar ka eye-off button) — history/chat
              bilkul save nahi hoti.
            </li>
            <li>
              <b className="text-foreground">Cloud sync optional:</b> default OFF. Agar on karo to data sirf
              <b className="text-foreground"> aapke apne Firebase project</b> me jata hai — Google ke shared Firebase nahi,
              aapka personal project jiska access sirf aapke paas hai.
            </li>
          </ul>
        </section>

        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="flex items-center gap-2 text-sm font-semibold mb-3">
            <CloudCog className="w-4 h-4 text-emerald-600" /> Firebase sync setup (2 minute)
          </h2>
          <ol className="text-sm text-muted-foreground space-y-2 list-decimal ml-5 leading-relaxed">
            <li>console.firebase.google.com par jao → apna project banao (ya existing use karo).</li>
            <li>
              <b className="text-foreground">Build → Firestore Database → Create database</b> (production ya test mode).
            </li>
            <li>
              <b className="text-foreground">Rules</b> tab me ye paste karo aur publish karo:
            </li>
          </ol>
          <pre className="mt-3 p-3 rounded-lg bg-muted text-xs font-mono overflow-x-auto">
{`rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /orbitBrowser/{doc} {
      allow read, write: if true; // personal project — sirf aap use kar rahe ho
    }
  }
}`}
          </pre>
          <ol start={4} className="text-sm text-muted-foreground space-y-2 list-decimal ml-5 mt-3 leading-relaxed">
            <li>
              <b className="text-foreground">Project settings → Your apps → Web app</b> banao → firebaseConfig object me se
              values copy karke Settings → Firebase Cloud Sync me paste karo.
            </li>
            <li>Save → Sync Now dabao. Bas — data ab aapke project me hai, sirf aapke paas.</li>
          </ol>
          <p className="text-[11px] text-amber-600 dark:text-amber-500 mt-3">
            Note: <code>allow read, write: if true</code> sirf personal use ke liye theek hai. Public app banoge to
            Firebase Authentication laga lena.
          </p>
        </section>

        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="flex items-center gap-2 text-sm font-semibold mb-3">
            <Github className="w-4 h-4 text-emerald-600" /> GitHub par apna repo
          </h2>
          <p className="text-sm text-muted-foreground leading-relaxed mb-3">
            Ye project ka poora code aapke paas hai — README.md me step-by-step Hinglish guide hai ki kaise
            GitHub repo bana kar push karein, aur khode (self-host) kaise karein.
          </p>
          <pre className="p-3 rounded-lg bg-muted text-xs font-mono overflow-x-auto">
{`git remote add origin https://github.com/AAPKA-USERNAME/orbit-browser.git
git push -u origin main`}
          </pre>
        </section>

        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="flex items-center gap-2 text-sm font-semibold mb-3">
            <Server className="w-4 h-4 text-emerald-600" /> Honest limitations
          </h2>
          <ul className="text-sm text-muted-foreground space-y-2 list-disc ml-5 leading-relaxed">
            <li>
              Ye ek <b className="text-foreground">web-based browser</b> hai (web app) — sites sandboxed iframe + proxy se
              dikhti hain. Login flows aur kuch heavy sites (banking, Google, YouTube) iframe block karti hain; unke liye
              &quot;Naye tab me kholo&quot; button hai.
            </li>
            <li>
              Full native browser (Chromium) chahiye to is UI ko Electron/Tauri me wrap kar sakte ho — README me hint hai.
            </li>
            <li>
              AI features (search answer, chat, summarize) Z.ai ke GLM 5.3 API ko use karte hain — ye ek third-party
              processing hai jise aap AI answer ke liye chunte ho. Browsing data us API ko kabhi nahi bheja jata.
            </li>
          </ul>
        </section>

        <p className="text-center text-xs text-muted-foreground pb-4">
          Orbit Browser v1.0 &#183; GLM 5.3 &#183; Local-first &#183; Built with Next.js + shadcn/ui
        </p>
      </div>
    </div>
  );
}
