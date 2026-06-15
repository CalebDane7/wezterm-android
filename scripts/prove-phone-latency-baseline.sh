#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone latency baseline proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
CONTROL_SERVER="${PHONE_CONTROL_SERVER:-$HOME/.local/bin/phone-terminal-control-server}"
TOKEN_FILE="${PHONE_WEB_TOKEN_FILE:-$HOME/.local/share/phone-terminal/web-control-token}"
SAMPLES="${PHONE_LATENCY_SAMPLES:-9}"
MAX_P95_MS="${PHONE_LATENCY_MAX_P95_MS:-3000}"
OUTPUT="${PHONE_LATENCY_OUTPUT:-/tmp/wezterm-phone-latency-baseline.json}"

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
}

web_token() {
    if [ -s "$TOKEN_FILE" ]; then
        tr -d '\r\n' < "$TOKEN_FILE"
        return 0
    fi
    CONTROL_SERVER="$CONTROL_SERVER" python3 <<'PY'
import importlib.machinery
import importlib.util
import os

path = os.environ["CONTROL_SERVER"]
loader = importlib.machinery.SourceFileLoader("phone_terminal_control_server_web_token", path)
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
print(module.ensure_web_control_token())
PY
}

measure_endpoint() { # name url header_mode
    local name="$1" url="$2" header_mode="${3:-plain}" output_file="$4"
    local i code timing
    for i in $(seq 1 "$SAMPLES"); do
        if [ "$header_mode" = "token" ]; then
            code="$(curl -sS -o /tmp/wezterm-latency-body.json -w '%{http_code}' -H "X-WEzTerm-Token: $TOKEN" "$url")"
            timing="$(curl -sS -o /tmp/wezterm-latency-body.json -w '%{time_total}' -H "X-WEzTerm-Token: $TOKEN" "$url")"
        else
            code="$(curl -sS -o /tmp/wezterm-latency-body.json -w '%{http_code}' "$url")"
            timing="$(curl -sS -o /tmp/wezterm-latency-body.json -w '%{time_total}' "$url")"
        fi
        printf '%s\t%s\t%s\n' "$name" "$code" "$timing" >> "$output_file"
    done
}

summarize() {
    local raw_file="$1"
    python3 - "$raw_file" "$OUTPUT" "$SAMPLES" "$MAX_P95_MS" <<'PY'
import json
import math
import statistics
import sys
import time
from collections import defaultdict

raw_file, output_file, samples, max_p95_ms = sys.argv[1], sys.argv[2], int(sys.argv[3]), float(sys.argv[4])
groups = defaultdict(list)
codes = defaultdict(list)
with open(raw_file, "r", encoding="utf-8") as handle:
    for line in handle:
        name, code, seconds = line.rstrip("\n").split("\t")
        groups[name].append(float(seconds) * 1000.0)
        codes[name].append(code)

summary = {
    "ok": True,
    "samplesPerEndpoint": samples,
    "maxP95Ms": max_p95_ms,
    "capturedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    "endpoints": {},
}
for name, values in groups.items():
    ordered = sorted(values)
    p95_index = min(len(ordered) - 1, math.ceil(len(ordered) * 0.95) - 1)
    endpoint = {
        "count": len(values),
        "codes": codes[name],
        "minMs": round(min(values), 2),
        "p50Ms": round(statistics.median(values), 2),
        "p95Ms": round(ordered[p95_index], 2),
        "maxMs": round(max(values), 2),
    }
    endpoint["ok"] = len(values) == samples and all(code == "200" for code in codes[name]) and endpoint["p95Ms"] <= max_p95_ms
    if not endpoint["ok"]:
        summary["ok"] = False
    summary["endpoints"][name] = endpoint

with open(output_file, "w", encoding="utf-8") as handle:
    json.dump(summary, handle, indent=2, sort_keys=True)
    handle.write("\n")
print(json.dumps(summary, indent=2, sort_keys=True))
if not summary["ok"]:
    raise SystemExit("latency baseline failed: HTTP status or p95 sanity threshold failed")
PY
}

echo "phase0 source guard"
"$(dirname "$0")/prove-phone-claude-spec-phase0.sh"

TOKEN="$(web_token)"
window_id="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
encoded_window="$(urlencode "$window_id")"
raw="$(mktemp)"
trap 'rm -f "$raw"' EXIT
: > "$raw"

measure_endpoint "health" "$CONTROL_URL/health" plain "$raw"
measure_endpoint "scrollback_chunk_80" "$CONTROL_URL/scrollback/chunk?windowId=$encoded_window&lines=80" plain "$raw"
measure_endpoint "copy_visible" "$CONTROL_URL/copy-visible" plain "$raw"
measure_endpoint "web_config_token" "$CONTROL_URL/web/config" token "$raw"

summarize "$raw"
echo "Phone latency baseline proof passed; output: $OUTPUT"
