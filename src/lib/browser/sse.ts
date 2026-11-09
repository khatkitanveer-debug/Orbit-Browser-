// Tez Browser — tiny SSE reader for streaming AI responses

export async function readSSE(res: Response, onEvent: (data: Record<string, unknown>) => void): Promise<void> {
  if (!res.body) throw new Error("Response body empty");
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    const parts = buf.split("\n");
    buf = parts.pop() ?? "";
    for (const line of parts) {
      const l = line.trim();
      if (!l.startsWith("data:")) continue;
      const payload = l.slice(5).trim();
      if (!payload || payload === "[DONE]") continue;
      try {
        onEvent(JSON.parse(payload) as Record<string, unknown>);
      } catch {
        /* ignore malformed chunk */
      }
    }
  }
}
