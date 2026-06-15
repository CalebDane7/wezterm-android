#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone scrollback chunk proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
CONTROL_SERVER="${PHONE_CONTROL_SERVER:-$HOME/.local/bin/phone-terminal-control-server}"
TOKEN_FILE="${PHONE_WEB_TOKEN_FILE:-$HOME/.local/share/phone-terminal/web-control-token}"

orig_window=""
proof_window=""

cleanup() {
    if [ -n "${proof_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$proof_window"; then
        tmux kill-window -t "$TMUX_SESSION:$proof_window" >/dev/null 2>&1 || true
    fi
    if [ -n "${orig_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$orig_window"; then
        tmux select-window -t "$TMUX_SESSION:$orig_window" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

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

json_assert_file() {
    local description="$1"
    local expression="$2"
    local payload_file="$3"
    python3 - "$description" "$expression" "$payload_file" <<'PY'
import json
import sys

description, expression, payload_file = sys.argv[1], sys.argv[2], sys.argv[3]
with open(payload_file, "r", encoding="utf-8") as handle:
    p = json.load(handle)
allowed = {"any": any, "all": all, "len": len, "isinstance": isinstance, "list": list}
if not eval(expression, {"__builtins__": allowed}, {"p": p}):
    raise SystemExit(f"scrollback chunk proof failed: {description}: {p}")
print(f"{description}: ok")
PY
}

wait_for_pane_text() {
    local window_id="$1"
    local needle="$2"
    for _ in $(seq 1 60); do
        if tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -220 -E - 2>/dev/null | grep -Fq "$needle"; then
            return 0
        fi
        sleep 0.25
    done
    echo "scrollback chunk proof failed: pane $window_id never showed $needle" >&2
    tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -80 -E - >&2 || true
    exit 1
}

echo "phase0 source guard"
"$(dirname "$0")/prove-phone-claude-spec-phase0.sh"

orig_window="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
proof_window="$(tmux new-window -d -P -F '#{window_id}' -t "$TMUX_SESSION:" -n 'SCROLLBACK Chunk Proof' 'bash --noprofile --norc')"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "printf 'SCROLLBACK_CHUNK_PROOF_%03d\\n' {1..180}" Enter
wait_for_pane_text "$proof_window" "SCROLLBACK_CHUNK_PROOF_180"

encoded_window="$(urlencode "$proof_window")"
before_active="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
before_mode="$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_in_mode}')"

echo "auth guard"
curl -sS -o /tmp/wezterm-scrollback-chunk-no-token.json -w '%{http_code}' \
    -H 'User-Agent: Mozilla/5.0' \
    "$CONTROL_URL/scrollback/chunk?windowId=$encoded_window&lines=4" | grep -Fxq "403"

TOKEN="$(web_token)"
curl -fsS \
    -H 'User-Agent: Mozilla/5.0' \
    -H "X-WEzTerm-Token: $TOKEN" \
    "$CONTROL_URL/scrollback/chunk?windowId=$encoded_window&lines=4" \
    > /tmp/wezterm-scrollback-chunk-browser.json
json_assert_file "browser token read-only chunk without csrf" \
    "p.get('ok') is True and p.get('readOnly') is True and p.get('rowCount', 0) <= 4" \
    /tmp/wezterm-scrollback-chunk-browser.json

echo "tail chunk"
curl -fsS "$CONTROL_URL/scrollback/chunk?windowId=$encoded_window&lines=80" > /tmp/wezterm-scrollback-chunk-tail.json
json_assert_file "tail chunk contract" \
    "p.get('ok') is True and p.get('action') == 'scrollback-chunk' and p.get('readOnly') is True and p.get('cacheKeyContract') == 'windowId:paneId:generation' and p.get('windowId') == '$proof_window' and p.get('rowCount', 0) <= 80 and any('SCROLLBACK_CHUNK_PROOF_180' in row for row in p.get('rows', []))" \
    /tmp/wezterm-scrollback-chunk-tail.json

echo "oldest bounded chunk"
curl -fsS "$CONTROL_URL/scrollback/chunk?windowId=$encoded_window&start=0&lines=60" > /tmp/wezterm-scrollback-chunk-start.json
json_assert_file "oldest chunk contract" \
    "p.get('ok') is True and p.get('start') == 0 and p.get('rowCount', 0) <= 60 and p.get('generationKey', '').startswith('$proof_window:') and any('SCROLLBACK_CHUNK_PROOF_001' in row for row in p.get('rows', []))" \
    /tmp/wezterm-scrollback-chunk-start.json

after_active="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
after_mode="$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_in_mode}')"
if [ "$before_active" != "$after_active" ] || [ "$orig_window" != "$after_active" ]; then
    echo "scrollback chunk proof failed: endpoint changed active window from $before_active to $after_active" >&2
    exit 1
fi
if [ "$before_mode" != "$after_mode" ]; then
    echo "scrollback chunk proof failed: endpoint changed pane mode from $before_mode to $after_mode" >&2
    exit 1
fi

echo "Phone scrollback chunk proof passed"
