"use client";

import { useEffect, useRef, useState } from "react";
import { Sparkles, X, Send, FileText, Loader2, Trash2, EyeOff } from "lucide-react";
import ReactMarkdown from "react-markdown";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ChatMsg } from "@/lib/browser/types";
import { isExternal } from "@/lib/browser/shared";
import { loadChatMessages, saveChatMessages } from "@/lib/browser/localStore";
import { readSSE } from "@/lib/browser/sse";

interface AiSidebarProps {
  open: boolean;
  onClose: () => void;
  pageUrl: string;
  pageTitle: string;
  privateMode: boolean;
}

export function AiSidebar({ open, onClose, pageUrl, pageTitle, privateMode }: AiSidebarProps) {
  const [messages, setMessages] = useState<ChatMsg[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [error, setError] = useState("");
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    loadChatMessages().then((m) => {
      if (m.length) setMessages(m);
    });
  }, [open]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, open]);

  const persist = (msgs: ChatMsg[]) => {
    if (!privateMode) {
      saveChatMessages(msgs.filter((m) => m.role !== "system")).catch(() => {});
    }
  };

  const send = async (rawText: string, opts?: { summarize?: boolean }) => {
    const text = rawText.trim();
    if ((!text && !opts?.summarize) || streaming) return;
    setError("");

    const userMsg: ChatMsg = {
      role: "user",
      content: opts?.summarize ? "Is page ka summary do" : text,
      ts: Date.now(),
    };
    const history = [...messages, userMsg];
    setMessages([...history, { role: "assistant", content: "", ts: Date.now() }]);
    setInput("");
    setStreaming(true);

    try {
      const res = await fetch("/api/ai/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          messages: history.filter((m) => m.role !== "system").map((m) => ({ role: m.role, content: m.content })),
          summarize: opts?.summarize ?? false,
          pageUrl: opts?.summarize ? pageUrl : undefined,
        }),
      });

      if (!res.ok) {
        const j = (await res.json().catch(() => null)) as { error?: string } | null;
        throw new Error(j?.error || `AI error (${res.status})`);
      }

      let acc = "";
      await readSSE(res, (data) => {
        const delta = (data as { choices?: Array<{ delta?: { content?: string } }> })?.choices?.[0]?.delta?.content;
        if (delta) {
          acc += delta;
          setMessages((prev) => {
            const next = [...prev];
            next[next.length - 1] = { role: "assistant", content: acc, ts: Date.now() };
            return next;
          });
        }
      });

      if (!acc) {
        setMessages((prev) => {
          const next = [...prev];
          next[next.length - 1] = {
            role: "assistant",
            content: "*(AI ne khali response bheja — dobara try karo)*",
            ts: Date.now(),
          };
          return next;
        });
      }
      persist([...history, { role: "assistant", content: acc, ts: Date.now() }]);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "AI se connect nahi ho saka";
      setError(msg);
      setMessages((prev) => prev.slice(0, -1)); // empty assistant hatao
    } finally {
      setStreaming(false);
    }
  };

  const clearChat = () => {
    setMessages([]);
    saveChatMessages([]).catch(() => {});
  };

  if (!open) return null;
  const canSummarize = isExternal(pageUrl);

  return (
    <aside
      className="fixed inset-y-0 right-0 z-40 w-full sm:w-[380px] lg:relative lg:w-[380px] lg:shrink-0 bg-background border-l border-border flex flex-col shadow-xl lg:shadow-none"
      role="complementary"
      aria-label="Orbit AI assistant"
    >
      <div className="flex items-center gap-2 px-4 h-12 border-b border-border/40 shrink-0">
        <span className="flex items-center justify-center w-6 h-6 rounded-md bg-emerald-600 text-white">
          <Sparkles className="w-3.5 h-3.5" />
        </span>
        <div className="flex-1 min-w-0">
          <div className="text-sm font-semibold leading-none">Orbit AI</div>
          <div className="text-[10px] text-muted-foreground mt-0.5">GLM 5.3 &#183; private assistant</div>
        </div>
        <button aria-label="Chat clear karo" onClick={clearChat} className="p-1.5 rounded hover:bg-muted">
          <Trash2 className="w-4 h-4 text-muted-foreground" />
        </button>
        <button aria-label="AI sidebar band karo" onClick={onClose} className="p-1.5 rounded hover:bg-muted">
          <X className="w-4 h-4" />
        </button>
      </div>

      {privateMode && (
        <div className="mx-3 mt-2 flex items-center gap-2 text-[11px] bg-violet-600/10 text-violet-600 dark:text-violet-400 rounded-lg px-3 py-2">
          <EyeOff className="w-3.5 h-3.5 shrink-0" />
          Private mode — ye chat device par save NAHI hogi
        </div>
      )}

      <div ref={scrollRef} className="flex-1 overflow-y-auto orbit-scroll px-3 py-3 space-y-3">
        {messages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-center px-4 gap-3">
            <span className="flex items-center justify-center w-12 h-12 rounded-2xl bg-emerald-600/10 text-emerald-600">
              <Sparkles className="w-6 h-6" />
            </span>
            <div>
              <div className="font-medium text-sm">Orbit AI se pucho kuch bhi</div>
              <div className="text-xs text-muted-foreground mt-1">
                Page summarize, koi sawaal, research — sab kuch. Chat sirf aapke device me rehti hai.
              </div>
            </div>
            {canSummarize && (
              <Button size="sm" variant="outline" onClick={() => send("", { summarize: true })} className="gap-2">
                <FileText className="w-3.5 h-3.5" />
                &quot;{pageTitle.slice(0, 30)}&quot; summarize karo
              </Button>
            )}
          </div>
        )}

        {messages.map((m, i) =>
          m.role === "user" ? (
            <div key={i} className="flex justify-end">
              <div className="bg-emerald-600 text-white text-sm rounded-2xl rounded-br-sm px-3.5 py-2 max-w-[85%] whitespace-pre-wrap break-words">
                {m.content}
              </div>
            </div>
          ) : (
            <div key={i} className="flex justify-start">
              <div className="bg-muted text-sm rounded-2xl rounded-bl-sm px-3.5 py-2.5 max-w-[92%] prose-sm dark:prose-invert break-words [&_p]:my-1 [&_ul]:my-1 [&_ol]:my-1 [&_li]:ml-4 [&_h1]:text-base [&_h2]:text-sm [&_h3]:text-sm [&_code]:text-xs [&_pre]:bg-background [&_pre]:p-2 [&_pre]:rounded-md [&_a]:text-emerald-600">
                <ReactMarkdown>{m.content || "..."}</ReactMarkdown>
              </div>
            </div>
          )
        )}

        {streaming && messages[messages.length - 1]?.content === "" && (
          <div className="flex items-center gap-2 text-muted-foreground text-xs px-2">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            Orbit AI soch raha hai...
          </div>
        )}
      </div>

      {error && (
        <div className="mx-3 mb-2 text-[11px] text-destructive bg-destructive/10 rounded-lg px-3 py-2">{error}</div>
      )}

      <div className="p-3 border-t border-border/40 shrink-0">
        <div className="flex gap-2 items-end">
          <Textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                send(input);
              }
            }}
            placeholder="Orbit AI se pucho..."
            aria-label="AI message"
            className="min-h-[44px] max-h-32 resize-none text-sm"
            rows={1}
          />
          <Button
            size="icon"
            onClick={() => send(input)}
            disabled={streaming || !input.trim()}
            aria-label="Message bhejo"
            className="bg-emerald-600 hover:bg-emerald-700 shrink-0"
          >
            {streaming ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          </Button>
        </div>
        <div className="text-[10px] text-muted-foreground mt-1.5 text-center">
          AI queries Z.ai API se process hoti hain &#183; chat history sirf aapke device me
        </div>
      </div>
    </aside>
  );
}
