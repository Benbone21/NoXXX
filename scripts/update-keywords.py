#!/usr/bin/env python3
"""
Builds app/src/main/assets/keywords.txt, the default blocked-word list.

Sources (downloaded by --fetch, or pass local paths):
  - dsojevic/profanity-list en.json (MIT): entries tagged by category and rated by severity.
  - first20hours/google-10000-english "no swears" list: the most common ordinary English words.

Rules, chosen to avoid false positives:
  - Only entries tagged "sexual" with severity >= 2 (moderate or stronger).
  - "*" in a pattern (previous letter repeats) is expanded to 1-3 repeats.
  - Hyphens and dots become word breaks, so "a-b" is matched as the phrase "a b" (and "ab").
  - Single words that are also one of the 10,000 most common ordinary English words are dropped,
    since those usually have an innocent meaning.
  - With --exclude-corpus, any word or phrase that appears anywhere in a body of ordinary text
    (e.g. /usr/share/doc on Linux: licences, changelogs, manuals) is dropped too, because it
    evidently has an everyday meaning. The shipped list was built this way.
  - The app matches whole words and phrases only, never parts of words.

The output is never printed; only counts are reported.
"""
import argparse
import gzip
import itertools
import json
import re
import sys
import urllib.request
from pathlib import Path

PROFANITY_URL = "https://raw.githubusercontent.com/dsojevic/profanity-list/main/en.json"
COMMON_URL = "https://raw.githubusercontent.com/first20hours/google-10000-english/master/google-10000-english-no-swears.txt"
OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "keywords.txt"
MIN_SEVERITY = 2


def expand_repeats(pattern: str) -> list[str]:
    """'lo*ng' -> ['long', 'loong', 'looong']."""
    parts = re.findall(r"(.)(\*?)", pattern)
    options = [[c * n for n in (1, 2, 3)] if star else [c] for c, star in parts]
    return ["".join(p) for p in itertools.product(*options)]


def normalize(term: str) -> str:
    return " ".join(re.split(r"[^a-z0-9]+", term.lower())).strip()


def build(entries: list[dict], common: set[str]) -> tuple[set[str], dict[str, int]]:
    stats = {"entries": 0, "variants": 0, "dropped_common": 0, "dropped_short": 0}
    terms: set[str] = set()
    for e in entries:
        if "sexual" not in e.get("tags", []) or e.get("severity", 0) < MIN_SEVERITY:
            continue
        stats["entries"] += 1
        for raw in e["match"].split("|"):
            for variant in expand_repeats(raw.strip()):
                stats["variants"] += 1
                forms = {normalize(variant)}
                if re.search(r"[-.]", variant):
                    forms.add(re.sub(r"[^a-z0-9]", "", variant.lower()))
                for form in forms:
                    if not form:
                        continue
                    if " " not in form:
                        if len(form) < 3:
                            stats["dropped_short"] += 1
                            continue
                        if form in common:
                            stats["dropped_common"] += 1
                            continue
                    terms.add(form)
    return terms, stats


def tokens(text: str) -> list[str]:
    return [t for t in re.split(r"[^a-z0-9]+", text.lower()) if t]


def fires_in(toks: list[str], words: set[str], by_first: dict[str, list[list[str]]]) -> set[str]:
    """Terms the app's whole-word matcher would match in these tokens (same rules as KeywordMatcher)."""
    found = set()
    for i, t in enumerate(toks):
        if t in words:
            found.add(t)
        if len(t) >= 5 and t.endswith("s") and t[:-1] in words:
            found.add(t[:-1])
        for p in by_first.get(t, []):
            if toks[i:i + len(p)] == p:
                found.add(" ".join(p))
    return found


def exclude_corpus(terms: set[str], dirs: list[Path]) -> tuple[set[str], int]:
    words = {t for t in terms if " " not in t}
    by_first: dict[str, list[list[str]]] = {}
    for t in terms:
        if " " in t:
            p = t.split()
            by_first.setdefault(p[0], []).append(p)
    seen: set[str] = set()
    scanned = 0
    for d in dirs:
        for f in d.rglob("*"):
            if not f.is_file() or f.stat().st_size > 5_000_000:
                continue
            try:
                raw = gzip.open(f).read() if f.suffix == ".gz" else f.read_bytes()
            except Exception:
                continue
            if b"\0" in raw[:1024]:
                continue  # binary
            toks = tokens(raw.decode("utf-8", "ignore"))
            scanned += len(toks)
            seen |= fires_in(toks, words, by_first)
    return terms - seen, scanned


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true", help="download both sources")
    ap.add_argument("--profanity", type=Path, help="local en.json")
    ap.add_argument("--common", type=Path, help="local common-words list")
    ap.add_argument("--exclude-corpus", type=Path, nargs="*", default=[], help="dirs of ordinary text")
    args = ap.parse_args()

    if args.fetch:
        entries = json.loads(fetch(PROFANITY_URL))
        common_text = fetch(COMMON_URL).decode()
    elif args.profanity and args.common:
        entries = json.loads(args.profanity.read_text())
        common_text = args.common.read_text()
    else:
        ap.error("use --fetch or give --profanity and --common")

    common = {w.strip().lower() for w in common_text.splitlines() if w.strip()}
    terms, stats = build(entries, common)
    if args.exclude_corpus:
        before = len(terms)
        terms, scanned = exclude_corpus(terms, args.exclude_corpus)
        print(f"scanned {scanned:,} words of ordinary text; dropped {before - len(terms)} terms found there")
    OUT.write_text("\n".join(sorted(terms)) + "\n")
    phrases = sum(1 for t in terms if " " in t)
    print(f"source entries used: {stats['entries']}, variants: {stats['variants']}")
    print(f"dropped as common words: {stats['dropped_common']}, too short: {stats['dropped_short']}")
    print(f"wrote {len(terms)} terms ({len(terms) - phrases} words, {phrases} phrases) to {OUT.relative_to(OUT.parents[3])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
