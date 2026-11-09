// Tez Browser — page proxy
// External websites ko sandboxed iframe ke andar dikhane ke liye HTML fetch karke
// rewrite karta hai (CSP/frame-block headers hatata hai, <base> inject karta hai).
// Fail hone par AI page_reader se "Reader Mode" fallback, uske baad error page.

import { NextRequest } from "next/server";

export const dynamic = "force-dynamic";
export const maxDuration = 45;

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 TezBrowser/1.0";

function isPrivateHost(hostname: string): boolean {
  const h = hostname.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return true;
  if (/^127\./.test(h) || /^10\./.test(h) || /^192\.168\./.test(h) || /^169\.254\./.test(h)) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(h)) return true;
  if (h === "0.0.0.0" || h === "[::1]") return true;
  return false;
}

function esc(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function injectedScript(tabId: string, baseUrl: string): string {
  const T = JSON.stringify(tabId);
  const B = JSON.stringify(baseUrl);
  return `<script>(function(){
var T=${T},BASE=${B};
function meta(){try{window.parent.postMessage({type:'tez:meta',tabId:T,title:document.title||'',url:BASE},'*')}catch(e){}}
if(document.readyState==='complete'){setTimeout(meta,0)}else{document.addEventListener('DOMContentLoaded',meta)}
window.addEventListener('load',meta);
document.addEventListener('click',function(e){
var el=e.target;while(el&&el.nodeType===1&&el.tagName!=='A'){el=el.parentElement}
if(!el)return;var href=el.getAttribute('href')||'';
if(!href||href.charAt(0)==='#'||href.indexOf('javascript:')===0)return;
e.preventDefault();e.stopPropagation();
try{var abs=new URL(href,BASE).href;
var nt=(el.target==='_blank')||e.ctrlKey||e.metaKey;
window.parent.postMessage({type:'tez:navigate',tabId:T,url:abs,newTab:nt},'*')}catch(err){}},true);
document.addEventListener('submit',function(e){
var f=e.target;if(!f||f.tagName!=='FORM')return;
var m=(f.getAttribute('method')||'get').toLowerCase();if(m!=='get')return;
e.preventDefault();
try{var act=f.getAttribute('action')||BASE;var u=new URL(act,BASE);
var fd=new FormData(f);var qs=new URLSearchParams();
fd.forEach(function(v,k){if(typeof v==='string')qs.append(k,v)});
u.search=qs.toString();
window.parent.postMessage({type:'tez:navigate',tabId:T,url:u.href},'*')}catch(err){}},true);
})()</script>`;
}

function wrapHtmlPage(title: string, bodyHtml: string, tabId: string, baseUrl: string): string {
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>
body{font-family:system-ui,-apple-system,sans-serif;background:#f7f7f8;color:#1a1a1a;margin:0;padding:24px}
.c{max-width:720px;margin:0 auto}
h1{font-size:1.5rem;margin:0 0 8px}
.m{color:#6b7280;font-size:.85rem;margin-bottom:20px}
article{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:24px;line-height:1.7;font-size:15px}
article p{margin:0 0 14px}
article h2{font-size:1.15rem;margin:20px 0 8px}
.btn{display:inline-block;margin-top:16px;padding:8px 14px;border-radius:8px;background:#059669;color:#fff;text-decoration:none;font-size:14px}
</style></head><body><div class="c">${bodyHtml}${injectedScript(tabId, baseUrl)}</div></body></html>`;
}

function errorPage(url: string, reason: string, tabId: string): string {
  const body = `<h1>Ye page yahan nahi khul saka</h1>
<div class="m">${esc(url)}</div>
<article><p><b>Wajah:</b> ${esc(reason)}</p>
<p>Kai websites (login, banking, heavy apps) iframe ke andar khulne se inkar karti hain — ye har browser ki had hai, sirf Tez ki nahi.</p>
<p>Aap is page ko apne real browser ke naye tab me khol sakte hain:</p>
<p><a class="btn" href="${esc(url)}" target="_blank" rel="noreferrer noopener">Naye tab me kholo &#8599;</a></p></article>`;
  return wrapHtmlPage("Page nahi khula", body, tabId, url);
}

function readerPage(title: string, paragraphs: string[], url: string, tabId: string): string {
  const ps = paragraphs.map((p) => `<p>${esc(p)}</p>`).join("") || "<p>Content extract nahi ho saka.</p>";
  const body = `<h1>${esc(title || "Reader Mode")}</h1>
<div class="m">Reader Mode (AI page reader) &#183; direct connection nahi hui thi</div>
<article>${ps}</article>
<p><a class="btn" href="${esc(url)}" target="_blank" rel="noreferrer noopener">Original site naye tab me kholo &#8599;</a></p>`;
  return wrapHtmlPage(title || "Reader Mode", body, tabId, url);
}

function cleanMarkdown(text: string): string[] {
  const t = text
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[#*_>`]+/g, "")
    .replace(/\r/g, "");
  return t
    .split(/\n{2,}|\.(?=\s+[A-Z\u0900-\u097F])/)
    .map((s) => s.trim())
    .filter((s) => s.length > 2)
    .slice(0, 120);
}

function rewriteHtml(html: string, finalUrl: string, tabId: string): string {
  let out = html;
  // CSP meta tags hatao
  out = out.replace(/<meta[^>]+http-equiv=["']?content-security-policy["']?[^>]*>/gi, "");
  out = out.replace(/<meta[^>]+http-equiv=["']?refresh["']?[^>]*>/gi, "");
  const script = injectedScript(tabId, finalUrl);
  const baseTag = `<base href="${esc(finalUrl)}"><meta name="referrer" content="no-referrer">`;
  if (/<head[^>]*>/i.test(out)) {
    out = out.replace(/<head([^>]*)>/i, `<head$1>${baseTag}${script}`);
  } else if (/<html[^>]*>/i.test(out)) {
    out = out.replace(/<html([^>]*)>/i, `<html$1><head>${baseTag}${script}</head>`);
  } else {
    out = `<!doctype html><html><head>${baseTag}${script}</head><body>${out}</body></html>`;
  }
  return out;
}

async function fetchWithTimeout(url: string, ms: number): Promise<Response> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetch(url, {
      signal: ctrl.signal,
      redirect: "follow",
      headers: {
        "User-Agent": UA,
        Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9,hi;q=0.8",
      },
    });
  } finally {
    clearTimeout(timer);
  }
}

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const rawUrl = searchParams.get("url") ?? "";
  const tabId = searchParams.get("t") ?? "unknown";

  let target: URL;
  try {
    target = new URL(rawUrl);
  } catch {
    return new Response(errorPage(rawUrl || "(empty)", "Invalid URL", tabId), {
      status: 400,
      headers: { "Content-Type": "text/html; charset=utf-8" },
    });
  }

  if (!/^https?:$/.test(target.protocol) || isPrivateHost(target.hostname)) {
    return new Response(errorPage(rawUrl, "Ye URL allowed nahi hai", tabId), {
      status: 403,
      headers: { "Content-Type": "text/html; charset=utf-8" },
    });
  }

  // 1) Direct fetch — https pehle, fail hone par http
  let res: Response | null = null;
  let lastErr = "";
  const tryUrls = target.protocol === "http:" ? [target.href] : [target.href];
  for (const u of tryUrls) {
    try {
      res = await fetchWithTimeout(u, 15000);
      if (res.ok) break;
      lastErr = `HTTP ${res.status}`;
      res = null;
    } catch (err) {
      lastErr = err instanceof Error ? err.message : "Network error";
      res = null;
    }
  }

  if (res) {
    try {
      const ct = res.headers.get("content-type") ?? "text/html";
      if (ct.includes("text/html") || ct.includes("xhtml")) {
        const html = (await res.text()).slice(0, 5_000_000);
        const finalUrl = res.url || target.href;
        return new Response(rewriteHtml(html, finalUrl, tabId), {
          status: 200,
          headers: {
            "Content-Type": "text/html; charset=utf-8",
            "Cache-Control": "private, max-age=30",
            "X-Tez-Mode": "proxy",
          },
        });
      }
      // Non-HTML (image/pdf/css) — passthrough
      return new Response(res.body, {
        status: res.status,
        headers: { "Content-Type": ct, "Cache-Control": "private, max-age=60" },
      });
    } catch (err) {
      lastErr = err instanceof Error ? err.message : "Read error";
    }
  }

  // 2) Reader Mode fallback — AI page_reader
  try {
    const { ZAI } = await import("z-ai-web-dev-sdk");
    const zai = await ZAI.create();
    const page = await zai.functions.invoke("page_reader", { url: target.href });
    let title = "";
    let text = "";
    if (typeof page === "string") {
      text = page;
    } else if (page && typeof page === "object") {
      const p = page as Record<string, unknown>;
      for (const k of ["title", "name"]) {
        if (typeof p[k] === "string" && p[k]) {
          title = p[k] as string;
          break;
        }
      }
      for (const k of ["content", "text", "markdown", "body"]) {
        if (typeof p[k] === "string" && p[k]) {
          text = p[k] as string;
          break;
        }
      }
    }
    if (text.trim()) {
      return new Response(readerPage(title, cleanMarkdown(text.slice(0, 40000)), target.href, tabId), {
        status: 200,
        headers: { "Content-Type": "text/html; charset=utf-8", "X-Tez-Mode": "reader" },
      });
    }
  } catch {
    /* reader fail */
  }

  // 3) Error page
  return new Response(errorPage(target.href, lastErr || "Site tak nahi pahunch sake", tabId), {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8", "X-Tez-Mode": "error" },
  });
}
