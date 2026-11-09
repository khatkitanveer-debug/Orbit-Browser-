// Tez Browser — GLM 5.3 AI chat (backend proxy)
// Client ki history yahan store NAHI hoti — sirf relay hoti hai.

import { NextRequest } from "next/server";
import ZAI from "z-ai-web-dev-sdk";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

const SYSTEM_PROMPT = `You are "Tez AI", the built-in assistant of Tez Browser — a fast, privacy-first browser.
You are powered by GLM 5.3. Rules:
- Reply in the user's language. If the user writes Hinglish/Hindi, reply in Hinglish (Roman script Hindi).
- Be concise, helpful and use markdown when useful.
- You never ask for personal data. Privacy is a core value.
- Current date context: 2026.`;

interface IncomingMsg {
  role: "user" | "assistant" | "system";
  content: string;
}

function extractPageText(page: unknown): string {
  if (typeof page === "string") return page;
  if (page && typeof page === "object") {
    const p = page as Record<string, unknown>;
    const candidates = ["content", "text", "markdown", "body", "summary"];
    for (const k of candidates) {
      const v = p[k];
      if (typeof v === "string" && v.trim()) return v;
    }
    if (Array.isArray(p.results)) return JSON.stringify(p.results);
  }
  return JSON.stringify(page);
}

export async function POST(req: NextRequest) {
  try {
    const body = (await req.json()) as {
      messages?: IncomingMsg[];
      summarize?: boolean;
      pageUrl?: string;
    };

    const incoming = Array.isArray(body.messages) ? body.messages.slice(-20) : [];
    if (incoming.length === 0) {
      return Response.json({ error: "messages khali hain" }, { status: 400 });
    }

    const zai = await ZAI.create();

    let msgs: Array<{ role: "system" | "user" | "assistant"; content: string }> = [
      { role: "system", content: SYSTEM_PROMPT },
      ...incoming.map((m) => ({ role: m.role, content: String(m.content).slice(0, 8000) })),
    ];

    // Page summarize mode: page_reader se content nikalo, phir GLM ko do
    if (body.summarize && body.pageUrl && /^https?:\/\//i.test(body.pageUrl)) {
      try {
        const page = await zai.functions.invoke("page_reader", { url: body.pageUrl });
        const text = extractPageText(page).slice(0, 12000);
        msgs = [
          { role: "system", content: SYSTEM_PROMPT },
          {
            role: "user",
            content: `Ye ek webpage ka content hai. Iska saaf, structured summary Hinglish me do — headings, bullet points aur important numbers ke saath.\n\nURL: ${body.pageUrl}\n\nPAGE CONTENT:\n${text}`,
          },
        ];
      } catch {
        // page_reader fail → normal chat hi rehne do
      }
    }

    const completion = await zai.chat.completions.create({
      model: "glm-5.3",
      messages: msgs,
      stream: true,
      thinking: { type: "disabled" },
    });

    const sseHeaders = {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    };

    // Streaming path — SDK ReadableStream (ya Response.body) passthrough
    const direct = completion as unknown;
    const stream: ReadableStream<Uint8Array> | null =
      direct instanceof ReadableStream
        ? direct
        : ((direct as { body?: ReadableStream<Uint8Array> | null })?.body ?? null);
    if (stream) {
      return new Response(stream, { headers: sseHeaders });
    }

    // Fallback — non-streaming response ko ek SSE event bana do
    const full =
      (completion as { choices?: Array<{ message?: { content?: string } }> })?.choices?.[0]?.message?.content ?? "";
    const payload = `data: ${JSON.stringify({ choices: [{ delta: { content: full } }] })}\n\ndata: [DONE]\n\n`;
    return new Response(payload, { headers: sseHeaders });
  } catch (err) {
    const message = err instanceof Error ? err.message : "AI request fail ho gayi";
    return Response.json({ error: message }, { status: 500 });
  }
}
