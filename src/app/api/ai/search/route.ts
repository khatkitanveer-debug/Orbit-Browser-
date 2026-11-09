// Tez Browser — Tez AI Search (web search + GLM 5.3 answer, SSE stream)
// Protocol: {type:'sources'} -> {type:'delta'}* -> {type:'done'} | {type:'error'}

import { NextRequest } from "next/server";
import ZAI from "z-ai-web-dev-sdk";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

interface RawResult {
  url?: string;
  link?: string;
  name?: string;
  title?: string;
  snippet?: string;
  description?: string;
  content?: string;
}

function normalizeResults(raw: unknown): Array<{ url: string; name: string; snippet: string }> {
  let arr: unknown[] = [];
  if (Array.isArray(raw)) arr = raw;
  else if (raw && typeof raw === "object") {
    const obj = raw as Record<string, unknown>;
    for (const k of ["results", "data", "items", "list"]) {
      if (Array.isArray(obj[k])) {
        arr = obj[k] as unknown[];
        break;
      }
    }
  }
  return arr
    .map((r) => {
      const o = (r ?? {}) as RawResult;
      const url = o.url || o.link || "";
      const name = o.name || o.title || url;
      const snippet = o.snippet || o.description || o.content || "";
      return { url: String(url), name: String(name), snippet: String(snippet).slice(0, 400) };
    })
    .filter((r) => r.url && /^https?:\/\//i.test(r.url))
    .slice(0, 8);
}

function sseChunk(obj: Record<string, unknown>): string {
  return `data: ${JSON.stringify(obj)}\n\n`;
}

export async function POST(req: NextRequest) {
  const encoder = new TextEncoder();
  try {
    const { q } = (await req.json()) as { q?: string };
    const query = (q ?? "").trim();
    if (!query) {
      return Response.json({ error: "Query khali hai" }, { status: 400 });
    }

    const zai = await ZAI.create();

    // 1) Web search (fast)
    let results: Array<{ url: string; name: string; snippet: string }> = [];
    try {
      const raw = await zai.functions.invoke("web_search", { query, num: 8 });
      results = normalizeResults(raw);
    } catch {
      results = [];
    }

    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        const send = (obj: Record<string, unknown>) => controller.enqueue(encoder.encode(sseChunk(obj)));
        try {
          send({ type: "sources", results });

          if (results.length === 0) {
            send({
              type: "delta",
              text: "Web se live results nahi mil sake (network restriction). Lekin main aapki madad kar sakta hoon — apna sawaal thoda detail me likhiye.",
            });
            send({ type: "done" });
            controller.close();
            return;
          }

          const context = results
            .slice(0, 6)
            .map((r, i) => `[${i + 1}] ${r.name}\nURL: ${r.url}\n${r.snippet}`)
            .join("\n\n");

          const prompt = `User ka search: "${query}"

Neeche web search ke live results hain. Inke basis par:
1. Ek clear, useful answer do Hinglish (Roman Hindi) me — 150-250 words, markdown headings/bullets allowed.
2. Jahan fact kisi source se aaye, wahan [1] [2] jaise inline citations do (results ke numbers ke hisaab se).
3. Agar results question ke liye kam padte hain, honestly bata do.

SEARCH RESULTS:
${context}`;

          let streamed = false;
          try {
            const completion = await zai.chat.completions.create({
              model: "glm-5.3",
              messages: [
                {
                  role: "system",
                  content:
                    "You are Tez AI Search inside Tez Browser, powered by GLM 5.3. Summarize web results faithfully with citations. Reply in Hinglish unless the query is in pure English — then reply in simple English. Never invent facts beyond sources.",
                },
                { role: "user", content: prompt },
              ],
              stream: true,
              thinking: { type: "disabled" },
            });

            const direct = completion as unknown;
            const body: ReadableStream<Uint8Array> | null =
              direct instanceof ReadableStream
                ? direct
                : ((direct as { body?: ReadableStream<Uint8Array> | null })?.body ?? null);
            if (body) {
              const reader = body.getReader();
              const decoder = new TextDecoder();
              let buf = "";
              for (;;) {
                const { done, value } = await reader.read();
                if (done) break;
                buf += decoder.decode(value, { stream: true });
                const lines = buf.split("\n");
                buf = lines.pop() ?? "";
                for (const line of lines) {
                  const l = line.trim();
                  if (!l.startsWith("data:")) continue;
                  const payload = l.slice(5).trim();
                  if (!payload || payload === "[DONE]") continue;
                  try {
                    const j = JSON.parse(payload) as {
                      choices?: Array<{ delta?: { content?: string } }>;
                    };
                    const t = j.choices?.[0]?.delta?.content;
                    if (t) {
                      streamed = true;
                      send({ type: "delta", text: t });
                    }
                  } catch {
                    /* skip malformed */
                  }
                }
              }
            } else {
              const full =
                (completion as { choices?: Array<{ message?: { content?: string } }> })?.choices?.[0]?.message
                  ?.content ?? "";
              if (full) {
                streamed = true;
                send({ type: "delta", text: full });
              }
            }
          } catch {
            /* LLM stream fail — fallback below */
          }

          if (!streamed) {
            send({
              type: "delta",
              text: "AI answer is waqt generate nahi ho saka, lekin neeche web sources zaroor check kar sakte hain.",
            });
          }

          send({ type: "done" });
          controller.close();
        } catch (err) {
          const message = err instanceof Error ? err.message : "Search fail ho gayi";
          send({ type: "error", message });
          controller.close();
        }
      },
    });

    return new Response(stream, {
      headers: {
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        Connection: "keep-alive",
      },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : "Search fail ho gayi";
    return Response.json({ error: message }, { status: 500 });
  }
}
