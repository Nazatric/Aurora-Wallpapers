#!/usr/bin/env bash
# Optional live checks of approved public catalogues. Does not request Reddit/Pixabay credentials.
# APOD rights are never inferred from this smoke test; production items remain view-only.
set -uo pipefail

if ! command -v curl >/dev/null || ! command -v python3 >/dev/null; then
  echo "This optional smoke check needs curl and Python 3." >&2
  exit 2
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
failures=0

check_wallhaven() {
  printf 'Wallhaven official purity=100 API: '
  if ! curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
    --get 'https://wallhaven.cc/api/v1/search' \
    --data-urlencode 'categories=111' --data-urlencode 'purity=100' \
    --data-urlencode 'sorting=date_added' --data-urlencode 'page=1' \
    > "$work/wallhaven.json"; then
    echo 'UNAVAILABLE'
    failures=$((failures + 1))
    return
  fi
  if python3 - "$work/wallhaven.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as file:
    body = json.load(file)
rows = body.get("data")
assert isinstance(rows, list), "missing Wallhaven data array"
for item in rows:
    assert item.get("purity") == "sfw", f"provider returned a non-SFW record: {item.get('id')}"
    assert item.get("id") and item.get("path", "").startswith("https://w.wallhaven.cc/"), "unexpected result metadata"
print(f"OK ({len(rows)} records; every returned purity field checked)")
PY
  then :; else
    echo 'INVALID RESPONSE'
    failures=$((failures + 1))
  fi
}

check_nasa_apod() {
  printf 'NASA APOD Basic public archive: '
  if ! curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
    --get 'https://science.nasa.gov/wp-json/wp/v2/apod-basic' \
    --data-urlencode 'per_page=3' --data-urlencode 'page=1' \
    > "$work/nasa-apod.json"; then
    echo 'UNAVAILABLE'
    failures=$((failures + 1))
    return
  fi
  if python3 - "$work/nasa-apod.json" <<'PY'
import json, sys
from urllib.parse import urlparse
with open(sys.argv[1], encoding="utf-8") as file:
    body = json.load(file)
assert isinstance(body, list), "expected the current APOD Basic API list"
images = [row for row in body if row.get("media_type") == "image"]
for row in images:
    assert row.get("date"), "missing APOD date"
    url = urlparse(row.get("hdurl", ""))
    assert url.scheme == "https" and url.hostname and url.hostname.endswith(".nasa.gov"), "unexpected APOD media URL"
print(f"OK ({len(body)} dated entries, {len(images)} NASA-hosted image entries; all remain view-only)")
PY
  then :; else
    echo 'INVALID RESPONSE'
    failures=$((failures + 1))
  fi
}

check_artic() {
  printf 'Art Institute public-domain/Open Access API: '
  local params
  params=$(python3 - <<'PY'
import json
print(json.dumps({
  "query": {"bool": {"must": [
    {"term": {"is_public_domain": True}},
    {"exists": {"field": "image_id"}},
  ]}},
  "limit": 3,
  "page": 1,
  "fields": "id,title,image_id,is_public_domain,artist_title",
}, separators=(",", ":")))
PY
)
  if ! curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
    --get 'https://api.artic.edu/api/v1/artworks/search' \
    --data-urlencode "params=$params" > "$work/artic.json"; then
    echo 'UNAVAILABLE'
    failures=$((failures + 1))
    return
  fi
  if python3 - "$work/artic.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as file:
    body = json.load(file)
rows = body.get("data")
assert isinstance(rows, list), "missing Art Institute data array"
for row in rows:
    assert row.get("is_public_domain") is True, f"non-public-domain record: {row.get('id')}"
    assert row.get("image_id"), f"missing image identifier: {row.get('id')}"
print(f"OK ({len(rows)} public-domain image records)")
PY
  then :; else
    echo 'INVALID RESPONSE'
    failures=$((failures + 1))
  fi
}

check_wallhaven
check_nasa_apod
check_artic

if (( failures > 0 )); then
  echo "$failures approved-provider check(s) were unavailable or invalid; other checks still ran." >&2
  exit 1
fi
