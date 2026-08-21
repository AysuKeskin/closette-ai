#!/usr/bin/env bash
# End-to-end smoke test against the running compose stack.
#
# Walks Flow A the way the mobile app does — register, analyze a photo, save the
# item, read it back — so it covers the parts the unit suites cannot: the Flyway
# migrations on real Postgres/pgvector (including the 768-dim embedding column),
# MinIO uploads and presigned URLs, JWT auth through the filter chain, and the
# backend → AI-service hop.
set -euo pipefail

API="${API:-http://localhost:8080}"
AI="${AI:-http://localhost:8000}"

wait_for() {
  local name="$1" url="$2" attempts="${3:-90}"
  echo "waiting for $name at $url"
  for _ in $(seq 1 "$attempts"); do
    if curl -sf -o /dev/null "$url"; then
      echo "  $name is up"
      return 0
    fi
    sleep 2
  done
  echo "::error::$name did not become ready at $url"
  return 1
}

json_field() {
  # Reads stdin, prints one field by dotted path, fails loudly if it is missing.
  python3 -c '
import json, sys
data = json.load(sys.stdin)
for key in sys.argv[1].split("."):
    if data is None:
        break
    data = data[key] if isinstance(data, dict) else data[int(key)]
if data in (None, ""):
    sys.exit(f"missing field: {sys.argv[1]}")
print(data)
' "$1"
}

wait_for "ai-service" "$AI/health"
wait_for "backend" "$API/v3/api-docs"

echo "--- AI service reports its providers ---"
curl -sf "$AI/health"
echo

EMAIL="ci-$(date +%s)-$RANDOM@closette.test"
USERNAME="ci_$(date +%s)$RANDOM"

echo "--- register ---"
TOKEN="$(curl -sf -X POST "$API/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"username\":\"$USERNAME\",\"password\":\"Password123\",\"displayName\":\"CI\"}" \
  | json_field data.accessToken)"
echo "  got an access token"

# A tiny solid-navy PNG: enough for the colour pipeline to return a real answer.
python3 -c '
import base64, zlib, struct
def chunk(tag, data):
    body = tag + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))
w = h = 32
raw = b"".join(b"\x00" + bytes([0x22, 0x31, 0x4E]) * w for _ in range(h))
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw))
       + chunk(b"IEND", b""))
open("item.png", "wb").write(png)
'

echo "--- analyze (backend -> MinIO -> AI service) ---"
ANALYSIS="$(curl -sf -X POST "$API/api/wardrobe/analyze" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@item.png;type=image/png")"
echo "$ANALYSIS"
IMAGE_KEY="$(printf '%s' "$ANALYSIS" | json_field data.imageKey)"
printf '%s' "$ANALYSIS" | json_field data.analysis.category > /dev/null
# Colour comes from the pixels, not the model — a navy square must read as navy.
printf '%s' "$ANALYSIS" | grep -q '"navy"' \
  || { echo "::error::colour pipeline did not recognise the navy image"; exit 1; }

echo "--- save the item (writes the pgvector embedding) ---"
ITEM_ID="$(curl -sf -X POST "$API/api/wardrobe/items" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"CI smoke dress\",\"category\":\"DRESSES\",\"colors\":[\"navy\"],\"pattern\":\"solid\",\"styles\":[\"minimal\"],\"seasons\":[\"spring\"],\"imageKey\":\"$IMAGE_KEY\"}" \
  | json_field data.id)"
echo "  item $ITEM_ID"

echo "--- read it back ---"
curl -sf "$API/api/wardrobe/items" -H "Authorization: Bearer $TOKEN" | grep -q "CI smoke dress" \
  || { echo "::error::the saved item was not returned by the list endpoint"; exit 1; }

echo "--- similarity query (exercises the pgvector index) ---"
curl -sf "$API/api/wardrobe/items/$ITEM_ID/similar" -H "Authorization: Bearer $TOKEN" > /dev/null

echo "--- get ready (rule-based or stylist, both must answer) ---"
curl -sf -X POST "$API/api/outfits/generate" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"occasion":"a CI run"}' | json_field data.rationale > /dev/null

echo "--- auth is actually enforced ---"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "$API/api/wardrobe/items")"
[ "$STATUS" = "401" ] || { echo "::error::unauthenticated request returned $STATUS, expected 401"; exit 1; }

echo "smoke test passed"
{
  echo "### Smoke test passed"
  echo "- registered a user, analyzed a photo, saved and re-read the item"
  echo "- pgvector similarity and Get Ready answered"
  echo "- unauthenticated access rejected with 401"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
