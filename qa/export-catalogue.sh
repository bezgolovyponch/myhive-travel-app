#!/bin/sh
# Saves one destination's public catalogue for the planner fuzz test: acts.json and pk.json in $2.
# Usage: sh qa/export-catalogue.sh https://www.trivlu.com/api /tmp/trivlu-catalogue [prague]
set -e
API="${1:?api base, e.g. https://www.trivlu.com/api or http://localhost:8080}"
OUT="${2:?output directory}"
SLUG="${3:-prague}"
mkdir -p "$OUT"
ID=$(curl -fsS -A curl "$API/destinations" | python3 -c "import sys,json; print([d['id'] for d in json.load(sys.stdin) if d['slug']=='$SLUG'][0])")
curl -fsS -A curl "$API/activities?destinationId=$ID" -o "$OUT/acts.json"
curl -fsS -A curl "$API/packages?destinationId=$ID" -o "$OUT/pk.json"
echo "$SLUG: $(python3 -c "import json;print(len(json.load(open('$OUT/acts.json'))),'activities,',len(json.load(open('$OUT/pk.json'))),'packages')") -> $OUT"
