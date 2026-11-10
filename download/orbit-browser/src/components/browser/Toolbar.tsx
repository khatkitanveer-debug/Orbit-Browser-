"use client";

import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  RotateCw,
  Home,
  Star,
  EyeOff,
  Sparkles,
  ExternalLink,
  MoreVertical,
  History,
  Bookmark,
  Settings,
  Info,
  Download,
  Trash2,
  Search,
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tab, Suggestion } from "@/lib/browser/types";
import { NEWTAB } from "@/lib/browser/types";
import { domainOf, isExternal } from "@/lib/browser/shared";
import { SiteGlyph } from "./TabBar";

export type MenuAction =
  | "newtab"
  | "history"
  | "bookmarks"
  | "settings"
  | "about"
  | "export"
  | "clearall";

interface ToolbarProps {
  tab: Tab;
  canBack: boolean;
  canForward: boolean;
  isBookmarked: boolean;
  privateMode: boolean;
  aiOpen: boolean;
  suggestions: (q: string) => Suggestion[];
  onBack: () => void;
  onForward: () => void;
  onReload: () => void;
  onHome: () => void;
  onNavigate: (input: string) => void;
  onToggleBookmark: () => void;
  onTogglePrivate: () => void;
  onToggleAi: () => void;
  onOpenExternal: () => void;
  onMenu: (action: MenuAction) => void;
}

export function Toolbar(props: ToolbarProps) {
  const { tab } = props;
  const [value, setValue] = useState("");
  const [focused, setFocused] = useState(false);
  const [sugOpen, setSugOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const external = isExternal(tab.url);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "l") {
        e.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  const suggestions = focused && value.trim() ? props.suggestions(value.trim().toLowerCase()) : [];
  const showSug = sugOpen && suggestions.length > 0;

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setSugOpen(false);
    inputRef.current?.blur();
    props.onNavigate(value);
  };

  const pick = (url: string) => {
    setSugOpen(false);
    inputRef.current?.blur();
    props.onNavigate(url);
  };

  const display = tab.url === NEWTAB ? "" : tab.url;

  return (
    <div className="flex items-center gap-1.5 px-3 py-2 bg-background border-b border-border/40 relative z-20">
      <button
        aria-label="Peeche jao"
        disabled={!props.canBack}
        onClick={props.onBack}
        className="h-8 w-8 rounded-full hover:bg-muted disabled:opacity-30 disabled:hover:bg-transparent flex items-center justify-center shrink-0"
      >
        <ArrowLeft className="w-4 h-4" />
      </button>
      <button
        aria-label="Aage jao"
        disabled={!props.canForward}
        onClick={props.onForward}
        className="h-8 w-8 rounded-full hover:bg-muted disabled:opacity-30 disabled:hover:bg-transparent flex items-center justify-center shrink-0"
      >
        <ArrowRight className="w-4 h-4" />
      </button>
      <button
        aria-label="Reload karo"
        onClick={props.onReload}
        className="h-8 w-8 rounded-full hover:bg-muted flex items-center justify-center shrink-0"
      >
        {tab.loading ? (
          <span className="w-4 h-4 border-2 border-muted-foreground/40 border-t-emerald-600 rounded-full animate-spin" aria-hidden />
        ) : (
          <RotateCw className="w-4 h-4" />
        )}
      </button>
      <button
        aria-label="Home"
        onClick={props.onHome}
        className="h-8 w-8 rounded-full hover:bg-muted hidden sm:flex items-center justify-center shrink-0"
      >
        <Home className="w-4 h-4" />
      </button>

      {/* Omnibox */}
      <form onSubmit={submit} className="flex-1 relative min-w-0">
        <div
          className={`flex items-center gap-2 h-9 px-3 rounded-full bg-muted/80 transition-all ${
            focused ? "ring-2 ring-emerald-600/50 bg-background" : ""
          }`}
        >
          {props.privateMode ? (
            <EyeOff className="w-3.5 h-3.5 text-violet-500 shrink-0" />
          ) : (
            <SiteGlyph url={tab.url} />
          )}
          <input
            ref={inputRef}
            value={focused ? value : display}
            onChange={(e) => setValue(e.target.value)}
            onFocus={() => {
              setFocused(true);
              setSugOpen(true);
              setValue(display);
              requestAnimationFrame(() => inputRef.current?.select());
            }}
            onBlur={() => {
              setFocused(false);
              setTimeout(() => setSugOpen(false), 150);
            }}
            placeholder="Search karo ya URL type karo — data sirf aapke paas"
            aria-label="Address bar"
            className="flex-1 bg-transparent outline-none text-sm min-w-0"
            spellCheck={false}
            autoComplete="off"
          />
          {display && (
            <span className="hidden md:inline text-[10px] text-muted-foreground/70 shrink-0 truncate max-w-[140px]">
              {external ? domainOf(tab.url) : "orbit://internal"}
            </span>
          )}
          <button
            type="button"
            aria-label="Bookmark toggle"
            onClick={props.onToggleBookmark}
            className="shrink-0 p-1 rounded hover:bg-background/70"
          >
            <Star className={`w-4 h-4 ${props.isBookmarked ? "fill-amber-400 text-amber-400" : "text-muted-foreground"}`} />
          </button>
        </div>

        {showSug && (
          <div className="absolute left-0 right-0 top-11 bg-popover border border-border rounded-xl shadow-lg overflow-hidden z-50">
            {suggestions.map((s, i) => (
              <button
                key={s.url + i}
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(s.url)}
                className="w-full flex items-center gap-3 px-4 py-2.5 hover:bg-muted text-left"
              >
                <Search className="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                <span className="truncate text-sm flex-1">
                  <span className="font-medium">{s.title}</span>
                  <span className="text-muted-foreground ml-2 text-xs">{s.url}</span>
                </span>
              </button>
            ))}
          </div>
        )}
      </form>

      {external && (
        <button
          aria-label="Real browser tab me kholo"
          onClick={props.onOpenExternal}
          title="Naye tab me kholo"
          className="h-8 w-8 rounded-full hover:bg-muted flex items-center justify-center shrink-0"
        >
          <ExternalLink className="w-4 h-4" />
        </button>
      )}
      <button
        aria-label="Private mode toggle"
        title="Private mode — history save nahi hogi"
        onClick={props.onTogglePrivate}
        className={`h-8 w-8 rounded-full flex items-center justify-center shrink-0 transition-colors ${
          props.privateMode ? "bg-violet-600 text-white hover:bg-violet-700" : "hover:bg-muted"
        }`}
      >
        <EyeOff className="w-4 h-4" />
      </button>
      <button
        aria-label="Orbit AI sidebar"
        title="Orbit AI (GLM 5.3)"
        onClick={props.onToggleAi}
        className={`h-8 w-8 rounded-full flex items-center justify-center shrink-0 transition-colors ${
          props.aiOpen ? "bg-emerald-600 text-white hover:bg-emerald-700" : "hover:bg-muted"
        }`}
      >
        <Sparkles className="w-4 h-4" />
      </button>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button aria-label="Menu" className="h-8 w-8 rounded-full hover:bg-muted flex items-center justify-center shrink-0">
            <MoreVertical className="w-4 h-4" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-52">
          <DropdownMenuItem onClick={() => props.onMenu("history")}>
            <History className="w-4 h-4 mr-2" /> History
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => props.onMenu("bookmarks")}>
            <Bookmark className="w-4 h-4 mr-2" /> Bookmarks
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => props.onMenu("settings")}>
            <Settings className="w-4 h-4 mr-2" /> Settings
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => props.onMenu("about")}>
            <Info className="w-4 h-4 mr-2" /> About & Privacy
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => props.onMenu("export")}>
            <Download className="w-4 h-4 mr-2" /> Data export (JSON)
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => props.onMenu("clearall")} className="text-destructive focus:text-destructive">
            <Trash2 className="w-4 h-4 mr-2" /> Saara data mitao
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
