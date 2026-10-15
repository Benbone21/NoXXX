#!/usr/bin/env bash
# Rebuilds app/src/main/assets/blocklist.txt from the latest StevenBlack/hosts adult-content extension (MIT).
set -euo pipefail
cd "$(dirname "$0")/.."
URL="https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
curl -fsSL "$URL" \
  | awk '$1=="0.0.0.0" && NF>=2 { d=tolower($2); if (d!="0.0.0.0" && d ~ /\./) print d }' \
  | sort -u > app/src/main/assets/blocklist.txt
echo "Domains: $(wc -l < app/src/main/assets/blocklist.txt)"
