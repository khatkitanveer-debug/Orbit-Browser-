"use client";

import { useEffect, useState } from "react";
import { Settings as SettingsIcon, CloudCog, Database, Download, Upload, Trash2, RefreshCw, ShieldCheck, Monitor } from "lucide-react";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { toast } from "sonner";
import { FirebaseCfg } from "@/lib/browser/types";
import {
  clearAllData,
  exportAllData,
  getSetting,
  listBookmarks,
  listHistory,
  lsClear,
  lsPut,
  setSetting,
} from "@/lib/browser/localStore";
import { bundleToPayload, fbRestore, fbSync, validateCfg } from "@/lib/browser/firebaseSync";

interface SettingsPageProps {
  onThemeChange: (t: "light" | "dark" | "system") => void;
  onNavigate: (url: string) => void;
}

const EMPTY_FB: FirebaseCfg = {
  apiKey: "",
  authDomain: "",
  projectId: "",
  storageBucket: "",
  messagingSenderId: "",
  appId: "",
};

export function SettingsPage({ onThemeChange, onNavigate }: SettingsPageProps) {
  const [theme, setTheme] = useState<"light" | "dark" | "system">("system");
  const [fb, setFb] = useState<FirebaseCfg>(EMPTY_FB);
  const [fbSaved, setFbSaved] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [restoring, setRestoring] = useState(false);
  const [counts, setCounts] = useState({ history: 0, bookmarks: 0 });

  useEffect(() => {
    (async () => {
      const t = await getSetting<"light" | "dark" | "system">("theme", "dark");
      setTheme(t);
      const f = await getSetting<FirebaseCfg | null>("firebase", null);
      if (f) {
        setFb(f);
        setFbSaved(true);
      }
      const [h, b] = await Promise.all([listHistory(), listBookmarks()]);
      setCounts({ history: h.length, bookmarks: b.length });
    })();
  }, []);

  const saveTheme = (t: "light" | "dark" | "system") => {
    setTheme(t);
    onThemeChange(t);
    setSetting("theme", t).catch(() => {});
  };

  const saveFb = async () => {
    const err = validateCfg(fb);
    if (err) {
      toast.error(err);
      return;
    }
    await setSetting("firebase", fb);
    setFbSaved(true);
    toast.success("Firebase config save ho gaya — ye sirf aapke device me rehta hai");
  };

  const doSync = async () => {
    const err = validateCfg(fb);
    if (err) {
      toast.error(err);
      return;
    }
    setSyncing(true);
    try {
      const [h, b] = await Promise.all([listHistory(), listBookmarks()]);
      const msg = await fbSync(fb, { bookmarks: b, history: h, speeddial: [] });
      toast.success(msg);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Sync fail";
      toast.error(`Sync fail: ${msg}. Firestore rules check karo (About page me rules hain).`);
    } finally {
      setSyncing(false);
    }
  };

  const doRestore = async () => {
    const err = validateCfg(fb);
    if (err) {
      toast.error(err);
      return;
    }
    setRestoring(true);
    try {
      const remote = await fbRestore(fb);
      if (!remote) {
        toast.info("Firebase me koi purana data nahi mila");
        return;
      }
      if (Array.isArray(remote.bookmarks)) {
        for (const bm of remote.bookmarks) await lsPut("bookmarks", bm);
      }
      if (Array.isArray(remote.history)) {
        for (const h of remote.history.slice(0, 300)) {
          const { id, ...rest } = h as Record<string, unknown> & { id?: number };
          void id;
          await lsPut("history", { url: String(rest.url ?? ""), title: String(rest.title ?? ""), ts: Number(rest.ts ?? Date.now()) });
        }
      }
      const [h, b] = await Promise.all([listHistory(), listBookmarks()]);
      setCounts({ history: h.length, bookmarks: b.length });
      toast.success("Restore complete — data merge ho gaya");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Restore fail";
      toast.error(`Restore fail: ${msg}`);
    } finally {
      setRestoring(false);
    }
  };

  const doExport = async () => {
    const bundle = await exportAllData();
    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `tez-browser-data-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success("Data export ho gaya — file sirf aapke device par download hui");
  };

  const doClearAll = async () => {
    await clearAllData();
    setCounts({ history: 0, bookmarks: 0 });
    setFb(EMPTY_FB);
    setFbSaved(false);
    toast.success("Saara local data mit gaya");
    setTimeout(() => window.location.reload(), 800);
  };

  return (
    <div className="h-full overflow-y-auto tez-scroll bg-background">
      <div className="max-w-2xl mx-auto px-6 py-8 space-y-6">
        <div className="flex items-center gap-2">
          <SettingsIcon className="w-5 h-5 text-emerald-600" />
          <h1 className="text-lg font-semibold">Settings</h1>
        </div>

        {/* Appearance */}
        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="text-sm font-semibold mb-1 flex items-center gap-2">
            <Monitor className="w-4 h-4 text-emerald-600" /> Appearance
          </h2>
          <p className="text-xs text-muted-foreground mb-3">Theme sirf aapke device me save hoti hai.</p>
          <div className="w-56">
            <Select value={theme} onValueChange={(v) => saveTheme(v as "light" | "dark" | "system")}>
              <SelectTrigger aria-label="Theme">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="light">Light</SelectItem>
                <SelectItem value="dark">Dark</SelectItem>
                <SelectItem value="system">System</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </section>

        {/* Firebase sync */}
        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="text-sm font-semibold mb-1 flex items-center gap-2">
            <CloudCog className="w-4 h-4 text-emerald-600" /> Firebase Cloud Sync (optional)
          </h2>
          <p className="text-xs text-muted-foreground mb-4">
            Apne <b>khud ke Firebase project</b> ka config daalo — data sirf usi me jayega, jise sirf aap access kar sakte ho.
            Config bhi aapke device me hi save hota hai. Khali chhod do to sab local hi rahega.
          </p>
          <div className="grid sm:grid-cols-2 gap-3">
            {(
              [
                ["apiKey", "API Key"],
                ["authDomain", "Auth Domain"],
                ["projectId", "Project ID"],
                ["storageBucket", "Storage Bucket"],
                ["messagingSenderId", "Messaging Sender ID"],
                ["appId", "App ID"],
              ] as Array<[keyof FirebaseCfg, string]>
            ).map(([key, label]) => (
              <div key={key} className="space-y-1">
                <Label htmlFor={key} className="text-xs">
                  {label}
                </Label>
                <Input
                  id={key}
                  value={fb[key]}
                  onChange={(e) => setFb({ ...fb, [key]: e.target.value })}
                  placeholder={key === "projectId" ? "my-private-browser" : ""}
                  className="h-8 text-xs font-mono"
                  type={key === "apiKey" ? "password" : "text"}
                />
              </div>
            ))}
          </div>
          <div className="flex flex-wrap gap-2 mt-4">
            <Button size="sm" onClick={saveFb} className="bg-emerald-600 hover:bg-emerald-700">
              <ShieldCheck className="w-3.5 h-3.5 mr-1" /> Config save karo
            </Button>
            <Button size="sm" variant="outline" onClick={doSync} disabled={syncing || !fbSaved}>
              {syncing ? <RefreshCw className="w-3.5 h-3.5 mr-1 animate-spin" /> : <CloudCog className="w-3.5 h-3.5 mr-1" />}
              Sync Now
            </Button>
            <Button size="sm" variant="outline" onClick={doRestore} disabled={restoring || !fbSaved}>
              {restoring ? <RefreshCw className="w-3.5 h-3.5 mr-1 animate-spin" /> : <Download className="w-3.5 h-3.5 mr-1" />}
              Restore
            </Button>
          </div>
          <p className="text-[11px] text-muted-foreground mt-3">
            Firebase console me Firestore rules set karna zaroori hai — steps <button onClick={() => onNavigate("tez://about")} className="text-emerald-600 underline">About & Privacy</button> page me hain.
          </p>
        </section>

        {/* Data & privacy */}
        <section className="p-5 rounded-xl border border-border bg-card">
          <h2 className="text-sm font-semibold mb-1 flex items-center gap-2">
            <Database className="w-4 h-4 text-emerald-600" /> Data & Privacy
          </h2>
          <p className="text-xs text-muted-foreground mb-4">
            Abhi aapke device me: <b>{counts.history}</b> history entries, <b>{counts.bookmarks}</b> bookmarks.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={doExport}>
              <Download className="w-3.5 h-3.5 mr-1" /> Export (JSON)
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={async () => {
                await lsClear("history");
                setCounts((c) => ({ ...c, history: 0 }));
                toast.success("History mit gayi");
              }}
            >
              <Trash2 className="w-3.5 h-3.5 mr-1" /> History clear
            </Button>
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button size="sm" variant="destructive">
                  <Trash2 className="w-3.5 h-3.5 mr-1" /> Saara data mitao
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Saara data mitana hai?</AlertDialogTitle>
                  <AlertDialogDescription>
                    History, bookmarks, shortcuts, AI chat aur saved Firebase config — sab delete ho jayega. Ye undo nahi ho sakta.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction onClick={doClearAll}>Haan, sab mita do</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
          <Separator className="my-4" />
          <div className="flex items-start gap-2">
            <Upload className="w-4 h-4 text-muted-foreground mt-0.5 shrink-0" />
            <p className="text-[11px] text-muted-foreground">
              Import ke liye export file ke JSON content ko Firebase sync ya manual restore use kar sakte ho. Full import feature
              roadmap par hai.
            </p>
          </div>
        </section>
      </div>
    </div>
  );
}

