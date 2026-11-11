#!/usr/bin/env python3
"""Layout-cast audit — findViewById type-safety (CI gate).

4 releases (v1.1..v1.2.3) ka launch-crash ek hi wajah se tha:
  XML me btn_tabs FrameLayout tha, Kotlin ne ImageButton me cast kiya
  -> ClassCastException on EVERY device at launch.

Ye script class-of-bug ko build se PEHLE pakadta hai:
  1. res/layout*/**.xml parse karke id -> xml-tag map banata hai
  2. Kotlin me har findViewById<R.id.X> cast nikalta hai
     (explicit generic + property-inferred dono)
  3. Widget-hierarchy table se check karta hai ki xml tag us cast
     type ka instance ho sakta hai ya nahi
  4. Missing ids (NPE risk) bhi pakadta hai

Exit 1 = incompatible cast ya missing id. Unknown tags/types = warn only.
"""
import re
import sys
import glob
import os
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..", "android", "app", "src", "main")

# type -> uske saare ancestors (khud included). Short names (unique hain).
HIER = {
    "View": {"View"},
    "ViewGroup": {"View", "ViewGroup"},
    "TextView": {"View", "TextView"},
    "EditText": {"View", "TextView", "EditText"},
    "Button": {"View", "TextView", "Button"},
    "ImageView": {"View", "ImageView"},
    "ImageButton": {"View", "ImageView", "ImageButton"},
    "ProgressBar": {"View", "ProgressBar"},
    "LinearLayout": {"View", "ViewGroup", "LinearLayout"},
    "FrameLayout": {"View", "ViewGroup", "FrameLayout"},
    "ScrollView": {"View", "ViewGroup", "FrameLayout", "ScrollView"},
    "GridLayout": {"View", "ViewGroup", "GridLayout"},
    "WebView": {"View", "ViewGroup", "AbsoluteLayout", "WebView"},
    "SwipeRefreshLayout": {"View", "ViewGroup", "FrameLayout", "SwipeRefreshLayout"},
    "RecyclerView": {"View", "ViewGroup", "RecyclerView"},
    "CardView": {"View", "ViewGroup", "FrameLayout", "CardView"},
}

ID_ATTR = "{http://schemas.android.com/apk/res/android}id"


def short(name: str) -> str:
    return name.rsplit(".", 1)[-1].split("<")[0].rstrip("?")


def load_layout_ids():
    """id -> {layout_name: xml_tag}"""
    ids = {}
    layouts = sorted(glob.glob(os.path.join(ROOT, "res", "layout*", "*.xml")))
    for path in layouts:
        lname = os.path.splitext(os.path.basename(path))[0]
        try:
            tree = ET.parse(path)
        except ET.ParseError as e:
            print(f"WARN: {path} parse fail: {e}")
            continue
        for el in tree.iter():
            v = el.get(ID_ATTR)
            if v and v.startswith("@+id/"):
                ids.setdefault(v[len("@+id/"):], {})[lname] = short(el.tag)
    return ids


RE_EXPLICIT = re.compile(r"findViewById\s*<\s*([\w.]+)\s*>\s*\(\s*R\.id\.(\w+)\s*\)")
RE_INFERRED = re.compile(r"(\w+)\s*=\s*findViewById\s*\(\s*R\.id\.(\w+)\s*\)")
RE_PROP = re.compile(r"(?:private\s+|internal\s+|protected\s+)?(?:lateinit\s+)?(?:var|val)\s+(\w+)\s*:\s*([\w.<?>\s]+?)\s*(?:=|$)", re.MULTILINE)


def property_type(kt: str, name: str):
    for m in RE_PROP.finditer(kt):
        if m.group(1) == name:
            return short(m.group(2).strip())
    return None


def main() -> int:
    ids = load_layout_ids()
    kt_files = glob.glob(os.path.join(ROOT, "java", "**", "*.kt"), recursive=True)
    fails, warns, checked = [], [], 0

    for path in kt_files:
        kt = open(path, encoding="utf-8").read()
        rel = os.path.relpath(path, ROOT)

        for m in RE_EXPLICIT.finditer(kt):
            checked += 1
            cast, rid = short(m.group(1)), m.group(2)
            line = kt[: m.start()].count("\n") + 1
            audit(f"{rel}:{line}", cast, rid, ids, fails, warns)

        for m in RE_INFERRED.finditer(kt):
            checked += 1
            var, rid = m.group(1), m.group(2)
            line = kt[: m.start()].count("\n") + 1
            cast = property_type(kt, var)
            if cast is None:
                warns.append(f"{rel}:{line}: '{var}' ka declared type nahi mila (R.id.{rid}) — manual check")
                continue
            audit(f"{rel}:{line}", cast, rid, ids, fails, warns)

    for w in sorted(set(warns)):
        print(f"WARN  {w}")
    for f in sorted(set(fails)):
        print(f"FAIL  {f}")
    print(f"\n{checked} findViewById casts checked, {len(ids)} ids in layouts: "
          f"{len(fails)} FAIL, {len(set(warns))} WARN")
    return 1 if fails else 0


def audit(where, cast, rid, ids, fails, warns):
    if rid not in ids:
        fails.append(f"{where}: R.id.{rid} kisi layout me nahi hai -> findViewById null (NPE risk)")
        return
    decls = ids[rid]
    if len(decls) > 1:
        warns.append(f"{where}: R.id.{rid} multiple layouts me hai {decls} — sab compatible hone chahiye")
    if cast not in HIER:
        warns.append(f"{where}: cast type '{cast}' hierarchy table me nahi — manual check (R.id.{rid})")
        return
    for lname, tag in decls.items():
        if tag not in HIER:
            warns.append(f"{where}: xml tag '{tag}' ({lname}) table me nahi — manual check (R.id.{rid})")
            continue
        if cast not in HIER[tag]:
            fails.append(
                f"{where}: R.id.{rid} {lname}.xml me **{tag}** hai, Kotlin **{cast}** me cast kar raha hai "
                f"-> ClassCastException (launch-crash!)"
            )


if __name__ == "__main__":
    sys.exit(main())
