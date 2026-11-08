#!/usr/bin/env python3
"""
Builds app/src/main/assets/scriptures.json: the verses NoXXX shows each day, on block screens and in
the urge-help screen.

Verse text comes from bcbooks/scriptures-json (https://github.com/bcbooks/scriptures-json), which
holds the text of the standard works without the Church's copyrighted footnotes, chapter summaries or
introductions. Download its five JSON files into one folder and pass that folder:

    python3 scripts/build-scriptures.py /path/to/scriptures-json

Each entry gets a link that opens the verse in Gospel Library (or the Church website).
"""
import json
import re
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "scriptures.json"

# (reference, shown when something is blocked or when struggling). Themes: temptation, strength,
# repentance, hope, virtue, the Savior's help.
VERSES = [
    # Book of Mormon
    ("1 Nephi 3:7", False), ("2 Nephi 2:27", True), ("2 Nephi 4:34", False), ("2 Nephi 31:20", False),
    ("2 Nephi 32:9", True), ("Mosiah 3:19", True), ("Mosiah 4:30", True), ("Mosiah 24:14", False),
    ("Mosiah 26:30", False), ("Alma 5:33", False), ("Alma 7:11-12", False), ("Alma 13:28", True),
    ("Alma 26:12", True), ("Alma 34:32", False), ("Alma 36:3", True), ("Alma 37:36", False),
    ("Alma 37:37", False), ("Alma 38:12", True), ("Alma 39:9", True), ("Alma 41:10", True),
    ("Helaman 5:12", True), ("3 Nephi 18:15", True), ("3 Nephi 18:18", True), ("Ether 12:4", False),
    ("Ether 12:27", True), ("Moroni 7:41", False), ("Moroni 10:32", True),
    # Doctrine and Covenants
    ("D&C 6:36", True), ("D&C 10:5", True), ("D&C 19:23", False), ("D&C 58:42", False),
    ("D&C 58:43", False), ("D&C 64:33", False), ("D&C 68:6", False), ("D&C 84:88", False),
    ("D&C 90:24", False), ("D&C 112:10", False), ("D&C 121:7-8", False), ("D&C 121:45", True),
    ("D&C 123:17", False),
    # Pearl of Great Price
    ("Moses 1:20", True), ("Moses 1:39", False), ("Articles of Faith 1:13", True),
    # Old Testament
    ("Joshua 1:9", True), ("Job 31:1", True), ("Psalms 46:1", True), ("Psalms 51:10", True),
    ("Psalms 119:37", True), ("Proverbs 3:5-6", False), ("Isaiah 1:18", False), ("Isaiah 40:31", False),
    ("Isaiah 41:10", True), ("Lamentations 3:22-23", False),
    # New Testament
    ("Matthew 5:8", True), ("Matthew 11:28-30", False), ("Matthew 26:41", True), ("John 14:27", False),
    ("John 16:33", False), ("Romans 12:2", True), ("1 Corinthians 10:13", True), ("2 Corinthians 5:17", False),
    ("2 Corinthians 12:9", True), ("Galatians 5:16", True), ("Ephesians 6:11", True), ("Philippians 4:8", True),
    ("Philippians 4:13", True), ("2 Timothy 2:22", True), ("Hebrews 4:15-16", False), ("James 1:12", True),
    ("James 4:7", True), ("1 John 1:9", False),
]

FILES = ["old-testament", "new-testament", "book-of-mormon", "doctrine-and-covenants", "pearl-of-great-price"]


def index(folder: Path) -> dict[str, tuple[str, str, int, str]]:
    """Maps "Alma 37:36" to (text, url path, chapter, ...) for every verse."""
    verses = {}
    for name in FILES:
        data = json.loads((folder / f"{name}.json").read_text())
        volume = data["lds_slug"]
        if "sections" in data:  # Doctrine and Covenants
            for s in data["sections"]:
                for v in s["verses"]:
                    verses[v["reference"]] = (v["text"], f"{volume}/{s['section']}", v["verse"])
        else:
            for b in data["books"]:
                for c in b["chapters"]:
                    for v in c["verses"]:
                        verses[v["reference"]] = (v["text"], f"{volume}/{b['lds_slug']}/{c['chapter']}", v["verse"])
    return verses


def main() -> int:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    verses = index(Path(sys.argv[1]))
    out, missing = [], []
    for ref, for_struggle in VERSES:
        m = re.fullmatch(r"(.+):(\d+)(?:-(\d+))?", ref)
        head, first, last = m.group(1), int(m.group(2)), int(m.group(3) or m.group(2))
        parts = [verses.get(f"{head}:{n}") for n in range(first, last + 1)]
        if None in parts:
            missing.append(ref)
            continue
        path = parts[0][1]
        anchor = f"p{first}" if first == last else f"p{first}-p{last}"
        out.append({
            "ref": ref,
            "text": " ".join(p[0] for p in parts),
            "url": f"https://www.churchofjesuschrist.org/study/scriptures/{path}?lang=eng&id={anchor}#p{first}",
            "struggle": for_struggle,
        })
    if missing:
        sys.exit(f"not found: {', '.join(missing)}")
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1) + "\n")
    print(f"wrote {len(out)} passages ({sum(e['struggle'] for e in out)} for hard moments) to {OUT.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
