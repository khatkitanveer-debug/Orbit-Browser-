#!/usr/bin/env python3
"""Orbit Browser ka poora code ek single markdown file me combine karta hai
(taaki Google AI / Gemini ko easily de sako)."""
import os

ROOT = "/home/z/my-project/download/orbit-browser"
OUT = "/home/z/my-project/download/orbit-browser-full-code.md"

# Root-level text files (order matters — pehle config, phir code)
ROOT_FILES = [
    "package.json",
    "vite.config.ts",
    "tsconfig.json",
    "index.html",
    "server.ts",
    ".env.example",
    ".gitignore",
    "firebase-applet-config.json",
    "firestore.rules",
]

SKIP_DIRS = {"node_modules", "dist", ".git", ".github"}
TEXT_EXT = {".ts", ".tsx", ".js", ".jsx", ".json", ".css", ".html", ".xml", ".kt", ".md", ".rules", ".example"}


def is_text(path: str) -> bool:
    ext = os.path.splitext(path)[1].lower()
    return ext in TEXT_EXT


def main() -> None:
    chunks = []
    chunks.append("# Orbit Browser — Complete Source Code\n")
    chunks.append(
        "> Ultra-fast, high-privacy mobile browser. React 19 + Vite 8 + Tailwind 4 + Firebase."
        " Orbit Shield ad-block, background media, ECC key generator (Web Crypto), apne Firebase project me sync.\n"
    )
    chunks.append("## Project Structure\n")
    chunks.append("```\n")

    all_files = []
    for rf in ROOT_FILES:
        p = os.path.join(ROOT, rf)
        if os.path.exists(p):
            all_files.append(os.path.relpath(p, ROOT))
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in sorted(filenames):
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, ROOT)
            if rel in ROOT_FILES:
                continue
            if not is_text(p):
                continue
            all_files.append(rel)
    chunks.append("\n".join(all_files))
    chunks.append("\n```\n")

    for rel in all_files:
        p = os.path.join(ROOT, rel)
        try:
            with open(p, "r", encoding="utf-8") as f:
                content = f.read()
        except Exception:
            continue
        lang = "typescript" if rel.endswith((".ts", ".tsx")) else (
            "kotlin" if rel.endswith(".kt") else (
                "css" if rel.endswith(".css") else (
                    "html" if rel.endswith(".html") else (
                        "json" if rel.endswith(".json") else "text"
                    )
                )
            )
        )
        chunks.append(f"\n---\n\n## File: `{rel}`\n\n```{lang}\n{content}\n```\n")

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("".join(chunks))

    size = os.path.getsize(OUT)
    print(f"OK: {OUT} ({size/1024:.0f} KB, {len(all_files)} files)")


if __name__ == "__main__":
    main()
