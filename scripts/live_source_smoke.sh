#!/usr/bin/env bash
# Optional live smoke checks for upstreams. Not part of unit tests: it needs real network access.
# It never prints the user's Alpha Coders API key. No Unsplash API request is made.
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
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert isinstance(body.get("data"), list), "missing Wallhaven data array"
for item in body["data"]:
    assert item.get("purity") == "sfw", f"non-SFW record returned: {item.get('id')}"
    assert item.get("id") and item.get("path", "").startswith("https://w.wallhaven.cc/"), "unexpected result metadata"
print(f"OK ({len(body['data'])} result records; SFW checked)")
PY

if [[ -z "${ALPHA_CODERS_API_KEY:-}" ]]; then
  echo "Wallpaper Abyss: SKIP (set ALPHA_CODERS_API_KEY to your own active Alpha Coders subscription key)."
else
  printf 'Wallpaper Abyss official API: '
  curl --fail --silent --show-error --connect-timeout 10 --max-time 25 \
    --get 'https://api.alphacoders.com/3.0' \
    --data-urlencode "auth=${ALPHA_CODERS_API_KEY}" \
    --data-urlencode 'method=newest' \
    --data-urlencode 'type=phone' \
    --data-urlencode 'page=1' > "$work/abyss.json"
  python3 - "$work/abyss.json" <<'PY'
import json, sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
assert body.get("success") is True, f"Alpha Coders returned an error: {body.get('error', 'unknown')}"
assert isinstance(body.get("wallpapers"), list), "missing wallpapers array"
for item in body["wallpapers"]:
    assert item.get("id") is not None
    assert item.get("url_page", "").startswith("https://wall.alphacoders.com/")
print(f"OK ({len(body['wallpapers'])} phone wallpaper records)")
PY
fi

echo "Unsplash: SKIP by policy. The app intentionally makes no Unsplash API request; see BLOCKERS.md."
