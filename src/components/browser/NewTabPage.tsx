"use client";

import { useState } from "react";
import { Zap, Search, ShieldCheck, Database, CloudCog, Plus, X, EyeOff, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SpeedDialItem } from "@/lib/browser/types";
import { letterOf, tileColorFor, domainOf } from "@/lib/browser/shared";

interface NewTabPageProps {
  privateMode: boolean;
  speedDial: SpeedDialItem[];
  onNavigate: (input: string) => void;
  onAddDial: (label: string, url: string) => void;
  onRemoveDial: (id: number) => void;
}

export function NewTabPage({ privateMode, speedDial, onNavigate, onAddDial, onRemoveDial }: NewTabPageProps) {
  const [q, setQ] = useState("");
  const [adding, setAdding] = useState(false);
  const [dl, setDl] = useState("");
  const [du, setDu] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (q.trim()) onNavigate(q);
  };

  const addDial = () => {
    const url = du.trim();
    if (!url) return;
    const full = /^https?:\/\//i.test(url) ? url : `https://${url}`;
    onAddDial(dl.trim() || domainOf(full), full);
    setDl("");
    setDu("");
    setAdding(false);
  };

  return (
    <div className="h-full overflow-y-auto tez-scroll bg-gradient-to-b from-muted/40 to-background">
      <div className="max-w-2xl mx-auto px-6 py-14 sm:py-20">
        <div className="flex flex-col items-center text-center mb-8">
          <span
            className={`flex items-center justify-center w-16 h-16 rounded-3xl mb-4 text-white shadow-lg ${
              privateMode ? "bg-violet-600 shadow-violet-600/20" : "bg-emerald-600 shadow-emerald-600/20"
            }`}
          >
            {privateMode ? <EyeOff className="w-8 h-8" /> : <Zap className="w-8 h-8" />}
          </span>
          <h1 className="text-3xl font-bold tracking-tight">
            Tez Browser{privateMode && <span className="text-violet-500"> — Private</span>}
          </h1>
          <p className="text-muted-foreground text-sm mt-2">
            Fast &#183; Private &#183; <span className="text-emerald-600 font-medium">Aapka data sirf aapka</span>
          </p>
        </div>

        <form onSubmit={submit} className="relative mb-10">
          <div className="flex items-center gap-3 h-13 pl-4 pr-2 py-2 rounded-2xl bg-card border border-border shadow-sm focus-within:ring-2 focus-within:ring-emerald-600/40 transition-all">
            <Search className="w-5 h-5 text-muted-foreground shrink-0" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Tez AI Search ya URL — Enter dabao"
              aria-label="Search ya URL"
              className="flex-1 bg-transparent outline-none text-[15px] min-w-0"
              autoFocus
            />
            <Button type="submit" size="sm" className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 shrink-0">
              <Sparkles className="w-3.5 h-3.5" />
              AI Search
            </Button>
          </div>
        </form>

        {/* Speed Dial */}
        <div className="mb-10">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Shortcuts</h2>
            <button
              onClick={() => setAdding(!adding)}
              className="text-xs text-emerald-600 hover:underline flex items-center gap-1"
            >
              <Plus className="w-3 h-3" /> Add
            </button>
          </div>

          {adding && (
            <div className="grid grid-cols-2 gap-2 mb-3 p-3 rounded-xl border border-border bg-card">
              <div className="space-y-1">
                <Label htmlFor="dl" className="text-xs">
                  Naam
                </Label>
                <Input id="dl" value={dl} onChange={(e) => setDl(e.target.value)} placeholder="My Site" className="h-8 text-sm" />
              </div>
              <div className="space-y-1">
                <Label htmlFor="du" className="text-xs">
                  URL
                </Label>
                <Input
                  id="du"
                  value={du}
                  onChange={(e) => setDu(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && addDial()}
                  placeholder="example.com"
                  className="h-8 text-sm"
                />
              </div>
              <div className="col-span-2 flex gap-2 justify-end">
                <Button size="sm" variant="ghost" onClick={() => setAdding(false)}>
                  Cancel
                </Button>
                <Button size="sm" onClick={addDial} className="bg-emerald-600 hover:bg-emerald-700">
                  Save
                </Button>
              </div>
            </div>
          )}

          <div className="grid grid-cols-4 sm:grid-cols-6 gap-3">
            {speedDial.map((item) => (
              <div key={item.id} className="group relative">
                <button
                  onClick={() => onNavigate(item.url)}
                  className="w-full flex flex-col items-center gap-2 p-3 rounded-xl hover:bg-muted transition-colors"
                >
                  <span
                    className={`flex items-center justify-center w-11 h-11 rounded-full text-white text-lg font-bold ${tileColorFor(item.url)}`}
                    aria-hidden
                  >
                    {letterOf(item.url)}
                  </span>
                  <span className="text-[11px] text-muted-foreground truncate w-full text-center">{item.label}</span>
                </button>
                {typeof item.id === "number" && (
                  <button
                    aria-label={`${item.label} shortcut hatao`}
                    onClick={() => onRemoveDial(item.id as number)}
                    className="absolute -top-1 -right-1 w-5 h-5 rounded-full bg-background border border-border items-center justify-center hidden group-hover:flex"
                  >
                    <X className="w-3 h-3" />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Privacy cards */}
        <div className="grid sm:grid-cols-3 gap-3">
          <div className="p-4 rounded-xl bg-card border border-border">
            <ShieldCheck className="w-5 h-5 text-emerald-600 mb-2" />
            <div className="text-sm font-medium">Zero tracking</div>
            <div className="text-xs text-muted-foreground mt-1">Koi analytics, koi ads, koi third-party favicon service nahi.</div>
          </div>
          <div className="p-4 rounded-xl bg-card border border-border">
            <Database className="w-5 h-5 text-emerald-600 mb-2" />
            <div className="text-sm font-medium">Local-first data</div>
            <div className="text-xs text-muted-foreground mt-1">History, bookmarks, passwords-format data — sab aapke device ke IndexedDB me.</div>
          </div>
          <div className="p-4 rounded-xl bg-card border border-border">
            <CloudCog className="w-5 h-5 text-emerald-600 mb-2" />
            <div className="text-sm font-medium">Cloud? Sirf aapka</div>
            <div className="text-xs text-muted-foreground mt-1">Sync optional hai — sirf aapke apne Firebase project me, aapki permission se.</div>
          </div>
        </div>
      </div>
    </div>
  );
}
