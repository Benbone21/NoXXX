#!/usr/bin/env bash
# Copies the images NoXXX Dev saved for image blocks and near misses (files/detections, dev builds only)
# from a USB-connected phone, with index.jsonl to match them to the diagnostics log.
#   scripts/pull-detections.sh [--clear] [output dir]   (default ./detections-pulled)
# --clear deletes them on the phone once they're copied. PKG=com.noxxx.guard for a -Pnoxxx.mainId build.
set -euo pipefail
PKG=${PKG:-com.noxxx.guard.debug}
CLEAR=0
OUT=./detections-pulled
for arg in "$@"; do
    case "$arg" in
        --clear) CLEAR=1 ;;
        -h|--help) sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) OUT=$arg ;;
    esac
done

if ! adb shell run-as "$PKG" ls files/detections >/dev/null 2>&1; then
    echo "No detections on the phone (or $PKG isn't an installed dev build)." >&2
    exit 1
fi
mkdir -p "$OUT"
adb exec-out run-as "$PKG" tar cf - files/detections | tar xf - -C "$OUT" --strip-components=1
count=$(find "$OUT/detections" -name '*.jpg' | wc -l)
echo "Pulled $count images to $OUT/detections"

if [ "$CLEAR" = 1 ]; then
    adb shell run-as "$PKG" rm -rf files/detections
    echo "Cleared them on the phone."
fi
