"use client";

import { useEffect, useRef, useState } from "react";
import { Sparkles, Loader2, ExternalLink, ShieldCheck } from "lucide-react";
import ReactMarkdown from "react-markdown";
import { SearchResult } from "@/lib/browser/types";
import { domainOf } from "@/lib/browser/shared";
import { readSSE } from "@/lib/browser/sse";
import { Skeleton } from "@/components/ui/skeleton";

interface SearchPageProps {
  q: string;
  onNavigate: (url: string) => void;
}

export function SearchPage({ q, onNavigate }: SearchPageProps) {
  const [sources, setSources] = useState<SearchResult[]>([]);
  const [answer, setAnswer] = useState("");
  const [phase, setPhase] = useState<"loading" | "answering" | "done" | "error">("loading");
  const [errMsg, setErrMsg] = useState("");
  const startedRef = useRef("");

  useEffect(() => {
    if (!q || startedRef.current === q) return;
    startedRef.current = q;
    let alive = true;

    const run = async () => {
      setPhase("loading");
      setSources([]);
      setAnswer("");
      setErrMsg("");
      try {
        const res = await fetch("/api/ai/search", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ q }),
        });
        if (!res.ok) throw new Error(`Search API error (${res.status})`);

        await readSSE(res, (data) => {
          if (!alive) return;
          const d = data as { type?: string; results?: SearchResult[]; text?: string; message?: string };
          if (d.type === "sources") {
            setSources(Array.isArray(d.results) ? d.results : []);
            setPhase("answering");
          } else if (d.type === "delta" && d.text) {
            setAnswer((prev) => prev + d.text);
          } else if (d.type === "done") {
            setPhase("done");
          } else if (d.type === "error") {
            setErrMsg(d.message || "Search fail ho gayi");
            setPhase("error");
          }
        });
        if (alive) setPhase((p) => (p === "answering" || p === "loading" ? "done" : p));
      } catch (err) {
        if (alive) {
          setErrMsg(err instanceof Error ? err.message : "Search fail ho gayi");
          setPhase("error");
        }
      }
    };
    run();
    return () => {
      alive = false;
    };
  }, [q]);

  return (
    <div className="h-full overflow-y-auto tez-scroll bg-background">
      <div className="max-w-3xl mx-auto px-6 py-8">
        <div className="flex items-center gap-2 mb-1">
          <span className="flex items-center justify-center w-7 h-7 rounded-lg bg-emerald-600 text-white">
            <Sparkles className="w-4 h-4" />
          </span>
          <h1 className="text-lg font-semibold">Tez AI Search</h1>
          <span className="ml-auto flex items-center gap-1 text-[11px] text-muted-foreground">
            <ShieldCheck className="w-3 h-3 text-emerald-600" />
            Query sirf answer ke liye use hoti hai
          </span>
        </div>
        <div className="text-sm text-muted-foreground mb-6 border-b border-border pb-4">
          <span className="text-foreground">&quot;{q}&quot;</span>
        </div>

        {phase === "loading" && (
          <div className="space-y-4">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-4 w-1/2" />
          </div>
        )}

        {phase === "error" && (
          <div className="p-4 rounded-xl bg-destructive/10 text-destructive text-sm">{errMsg}</div>
        )}

        {sources.length > 0 && (
          <div className="mb-6">
            <div className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              Web sources ({sources.length})
            </div>
            <div className="space-y-2">
              {sources.map((s, i) => (
                <button
                  key={s.url + i}
                  onClick={() => onNavigate(s.url)}
                  className="w-full text-left p-3 rounded-xl border border-border hover:border-emerald-600/40 hover:bg-muted/50 transition-colors group"
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-[10px] font-bold text-emerald-700 dark:text-emerald-400 bg-emerald-600/10 rounded px-1.5 py-0.5">
                      {i + 1}
                    </span>
                    <span className="text-xs text-muted-foreground truncate flex-1">{domainOf(s.url)}</span>
                    <ExternalLink className="w-3 h-3 text-muted-foreground opacity-0 group-hover:opacity-100" />
                  </div>
                  <div className="text-sm font-medium line-clamp-1">{s.name}</div>
                  {s.snippet && <div className="text-xs text-muted-foreground mt-1 line-clamp-2">{s.snippet}</div>}
                </button>
              ))}
            </div>
          </div>
        )}

        {(answer || phase === "answering") && (
          <div className="p-5 rounded-2xl border border-emerald-600/20 bg-emerald-600/[0.04]">
            <div className="flex items-center gap-2 mb-3">
              <Sparkles className="w-4 h-4 text-emerald-600" />
              <span className="text-sm font-semibold">AI Answer</span>
              {phase === "answering" && <Loader2 className="w-3.5 h-3.5 animate-spin text-emerald-600" />}
            </div>
            <div className="text-sm leading-relaxed [&_p]:my-2 [&_ul]:my-2 [&_ol]:my-2 [&_li]:ml-5 [&_h1]:text-base [&_h2]:text-sm [&_h3]:text-sm [&_strong]:font-semibold [&_a]:text-emerald-600 [&_a]:underline">
              <ReactMarkdown>{answer}</ReactMarkdown>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
