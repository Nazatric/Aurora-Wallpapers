#!/usr/bin/env bash
# Optional live checks for the two integrated public APIs. Not part of unit tests.
# Openverse is deliberately called without OAuth credentials to verify anonymous access.
set -euo pipefail

if ! command -v curl >/dev/null || ! command -v python3 >/dev/null; then
  echo "This optional smoke check needs curl and Python 3." >&2
  exit 2
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

printf 'Wallhaven public SFW API: '
curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
  --get 'https://wallhaven.cc/api/v1/search' \
  --data-urlencode 'categories=111' \
  --data-urlencode 'purity=100' \
  --data-urlencode 'sorting=date_added' \
  --data-urlencode 'page=1' > "$work/wallhaven.json"
python3 - "$work/wallhaven.json" <<'PY'
import json, sys

with open(sys.argv[1], encoding="utf-8") as file:
    body = json.load(file)
rows = body.get("data")
assert isinstance(rows, list), "missing Wallhaven data array"
for item in rows:
    assert item.get("purity") == "sfw", f"non-SFW record returned: {item.get('id')}"
    assert item.get("id") and item.get("path", "").startswith("https://w.wallhaven.cc/"), "unexpected result metadata"
print(f"OK ({len(rows)} result records; SFW checked)")
PY

printf 'Openverse anonymous image search: '
curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
  --get 'https://api.openverse.org/v1/images/' \
  --data-urlencode 'q=nature' \
  --data-urlencode 'page=1' \
  --data-urlencode 'page_size=3' \
  --data-urlencode 'filter_dead=true' \
  --data-urlencode 'mature=false' \
  --data-urlencode 'size=medium,large' > "$work/openverse.json"
python3 - "$work/openverse.json" <<'PY'
import json, sys
from urllib.parse import urlparse

with open(sys.argv[1], encoding="utf-8") as file:
    body = json.load(file)
rows = body.get("results")
assert isinstance(rows, list), "missing Openverse results array (anonymous request may have been rejected)"
for item in rows:
    assert item.get("id"), "result is missing its stable id"
    for field in ("url", "foreign_landing_url"):
        parsed = urlparse(item.get(field, ""))
        assert parsed.scheme == "https" and parsed.hostname, f"unsafe or missing {field}"
print(f"OK ({len(rows)} records returned without an Authorization header)")
PY
