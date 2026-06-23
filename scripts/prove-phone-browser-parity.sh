#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone browser parity proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
BROWSER_CONTROL_URL="${PHONE_BROWSER_CONTROL_URL:-http://127.0.0.1:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
CONTROL_SERVER="${PHONE_CONTROL_SERVER:-$HOME/.local/bin/mantis-phone-control-server}"
PHONE_TERMINAL_BIN="${PHONE_TERMINAL_BIN:-$HOME/.local/bin/phone-terminal}"
BROWSER_SESSION="${PHONE_BROWSER_PROOF_SESSION:-wezterm-web-proof}"
SCREENSHOT="${PHONE_BROWSER_PROOF_SCREENSHOT:-/tmp/wezterm-web-parity.png}"
UPLOAD_PROOF_FILE="${PHONE_BROWSER_UPLOAD_PROOF_FILE:-/tmp/wezterm-web-upload-proof.txt}"
TOKEN_FILE="${PHONE_WEB_TOKEN_FILE:-$HOME/.mantis/phone-terminal/web-control-token}"

orig_window=""
proof_window=""
proof_title="WEB Browser Proof $$"
initial_windows_file="$(mktemp /tmp/wezterm-web-initial-windows.XXXXXX)"
current_windows_file="$(mktemp /tmp/wezterm-web-current-windows.XXXXXX)"

cleanup() {
    if [ -n "${proof_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$proof_window"; then
        local current_title
        current_title="$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{window_name}' 2>/dev/null || true)"
        # WHY: Mantis title-sync is allowed to repair display titles while this
        # browser proof is running. The proof owns the exact new `@windowId`
        # discovered after clicking New, so cleanup must use the pre-proof window
        # snapshot instead of a mutable title that can be repaired to Raw Terminal.
        if [ -f "$initial_windows_file" ] && grep -Fxq "$proof_window" "$initial_windows_file"; then
            echo "browser parity cleanup refused to close $proof_window title '$current_title' because it existed before this proof" >&2
            proof_window=""
        fi
    fi
    if [ -n "${proof_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$proof_window"; then
        curl -fsS "$CONTROL_URL/close?fast=1&windowId=${proof_window//@/%40}" >/dev/null || \
            tmux kill-window -t "$TMUX_SESSION:$proof_window" >/dev/null 2>&1 || true
    fi
    if [ -n "${orig_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$orig_window"; then
        curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" >/dev/null || true
    fi
    agent-browser --session "$BROWSER_SESSION" close >/dev/null 2>&1 || true
    rm -f "$initial_windows_file" "$current_windows_file"
}
trap cleanup EXIT

json_assert() {
    local description="$1"
    local expression="$2"
    local payload
    payload="$(cat)"
    python3 - "$description" "$expression" "$payload" <<'PY'
import json
import sys
description, expression, payload = sys.argv[1], sys.argv[2], sys.argv[3]
p = json.loads(payload)
allowed = {"any": any, "all": all, "len": len, "isinstance": isinstance, "list": list}
if not eval(expression, {"__builtins__": allowed}, {"p": p}):
    raise SystemExit(f"browser parity proof failed: {description}: {payload}")
print(f"{description}: ok")
PY
}

browser_eval() {
    agent-browser --session "$BROWSER_SESSION" eval "$1"
}

json_string() {
    python3 -c 'import json, sys; print(json.dumps(sys.argv[1]))' "$1"
}

wait_for_dialog_contains() {
    local needle="$1"
    local output_path="$2"
    local needle_json
    needle_json="$(json_string "$needle")"
    browser_eval "(() => new Promise((resolve, reject) => { const needle=${needle_json}; const deadline=Date.now()+30000; function tick(){ const dialog=document.querySelector('dialog'); const text=dialog?.innerText||''; if(dialog?.open && text.includes(needle)) return resolve(text); if(Date.now()>deadline) return reject(new Error('dialog did not show '+needle+': '+text)); setTimeout(tick,250); } tick(); }))()" > "$output_path"
}

wait_for_web_switch_shield_hidden() {
    local description="$1"
    local output_path="$2"
    local description_json
    description_json="$(json_string "$description")"
    browser_eval "(() => new Promise((resolve, reject) => { const description=${description_json}; const deadline=Date.now()+7000; let last=null; function tick(){ const getState=window.__weztermWebSwitchShieldState; const state=getState&&getState(); last=state; if(state && !state.active && state.visibility==='hidden') return resolve(state); if(Date.now()>deadline) return reject(new Error(description+' did not hide web Active-switch shield: '+JSON.stringify(last))); setTimeout(tick,150); } tick(); }))()" > "$output_path"
}

snapshot_windows() {
    local path="$1"
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | sort > "$path"
}

wait_for_browser_created_window() {
    local before="$1"
    local before_windows="$2"
    for _ in $(seq 1 80); do
        local current
        current="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}' 2>/dev/null || true)"
        snapshot_windows "$current_windows_file"
        local new_windows
        new_windows="$(comm -13 "$before_windows" "$current_windows_file" || true)"
        if [ "$(printf '%s\n' "$new_windows" | sed '/^$/d' | wc -l)" -gt 1 ]; then
            echo "browser parity proof failed: multiple new windows appeared during New proof" >&2
            printf '%s\n' "$new_windows" >&2
            exit 1
        fi
        if [ -n "$current" ] && [ "$current" != "$before" ] && ! grep -Fxq "$current" "$before_windows"; then
            printf '%s\n' "$current"
            return 0
        fi
        sleep 0.25
    done
    echo "browser parity proof failed: no new disposable browser-created window became active" >&2
    exit 1
}

wait_for_pane_text() {
    local window_id="$1"
    local needle="$2"
    for _ in $(seq 1 60); do
        if tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -80 -E - 2>/dev/null | grep -Fq "$needle"; then
            return 0
        fi
        sleep 0.25
    done
    echo "browser parity proof failed: pane $window_id never showed token $needle" >&2
    tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -80 -E - >&2 || true
    exit 1
}

wait_for_active_window_id() {
    local expected="$1"
    local description="$2"
    local active=""

    for _ in $(seq 1 60); do
        active="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}' 2>/dev/null || true)"
        if [ "$active" = "$expected" ]; then
            return 0
        fi
        sleep 0.1
    done

    echo "browser parity proof failed: $description did not select $expected; active window is ${active:-unknown}" >&2
    exit 1
}

ensure_browser_bridge() {
    # WHY: the APK reaches the Tailnet-bound services directly, but this proof
    # runs through Windows Chrome. Keep proof on the real rendered browser path
    # by requiring the launcher-owned localhost bridge before opening `/web`.
    if curl -fsS "$BROWSER_CONTROL_URL/health" >/dev/null 2>&1; then
        return 0
    fi
    "$PHONE_TERMINAL_BIN" start >/dev/null
    for _ in $(seq 1 40); do
        if curl -fsS "$BROWSER_CONTROL_URL/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep 0.25
    done
    echo "browser parity proof failed: localhost browser bridge never reached $BROWSER_CONTROL_URL" >&2
    "$PHONE_TERMINAL_BIN" status >&2 || true
    exit 1
}

web_token() {
    # WHY: the shipped web shell is served by the same Mantis control server as
    # the APK/phone terminal. Reading the older legacy token file makes browser
    # proof fail with 401 while the real Mantis web surface works, recreating the
    # split-brain auth layer that hid phone/web regressions.
    if [ -s "$TOKEN_FILE" ]; then
        tr -d '\r\n' < "$TOKEN_FILE"
        return 0
    fi
    CONTROL_SERVER="$CONTROL_SERVER" python3 <<'PY'
import importlib.machinery
import importlib.util
import os
import sys

path = os.environ["CONTROL_SERVER"]
loader = importlib.machinery.SourceFileLoader("phone_terminal_control_server_web_token", path)
spec = importlib.util.spec_from_loader(loader.name, loader)
module = importlib.util.module_from_spec(spec)
sys.path.insert(0, os.path.dirname(path))
spec.loader.exec_module(module)
print(module.ensure_web_control_token())
PY
}

echo "source guards"
"$(dirname "$0")/prove-phone-claude-spec-phase0.sh"

TOKEN="$(web_token)"
if [ -z "$TOKEN" ]; then
    echo "browser parity proof failed: empty web token" >&2
    exit 1
fi

echo "auth and CORS guards"
curl -fsS -i "$CONTROL_URL/health" > /tmp/wezterm-web-health.headers
if grep -Fqi 'Access-Control-Allow-Origin: *' /tmp/wezterm-web-health.headers; then
    echo "browser parity proof failed: wildcard CORS is still live" >&2
    cat /tmp/wezterm-web-health.headers >&2
    exit 1
fi
curl -sS -o /tmp/wezterm-web-unauth.html -w '%{http_code}' "$CONTROL_URL/web" | grep -Fxq "401"
curl -sS -o /tmp/wezterm-web-browser-no-token.json -w '%{http_code}' -H 'User-Agent: Mozilla/5.0' "$CONTROL_URL/tabs" | grep -Fxq "403"
curl -sS -o /tmp/wezterm-web-live-bottom-no-csrf.json -w '%{http_code}' \
    -H 'User-Agent: Mozilla/5.0' \
    -H "Origin: ${CONTROL_URL}" \
    -H "X-WEzTerm-Token: $TOKEN" \
    "$CONTROL_URL/live-bottom" | grep -Fxq "403"
curl -fsS -H "X-WEzTerm-Token: $TOKEN" "$CONTROL_URL/web/config" \
    | json_assert "web config" "p.get('ok') is True and ':8088' in p.get('terminalUrl', '') and len(p.get('toolbar', [])) == 11"
ensure_browser_bridge
curl -fsS -H "X-WEzTerm-Token: $TOKEN" "$BROWSER_CONTROL_URL/web/config" \
    | json_assert "browser-local web config" "p.get('ok') is True and p.get('terminalUrl', '').startswith('http://127.0.0.1:8088') and len(p.get('toolbar', [])) == 11"

echo "open browser web shell"
agent-browser --session "$BROWSER_SESSION" open "$BROWSER_CONTROL_URL/web?token=$TOKEN" >/dev/null
agent-browser --session "$BROWSER_SESSION" wait 2500 >/dev/null
agent-browser --session "$BROWSER_SESSION" screenshot "$SCREENSHOT" >/dev/null
agent-browser --session "$BROWSER_SESSION" snapshot -i > /tmp/wezterm-web-snapshot.txt
for label in Active Old Workspace New Refresh Bottom Scroll "Copy/Paste" Upload Close Start Stop; do
    grep -Fq "$label" /tmp/wezterm-web-snapshot.txt
done
browser_eval '(() => { const frame = document.querySelector("#terminal-frame"); if (!frame || !frame.src.includes(":8088")) throw new Error("terminal iframe did not load ttyd"); return frame.src; })()' >/tmp/wezterm-web-frame.txt

echo "active and scroll dialogs"
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="active"]' >/dev/null
wait_for_dialog_contains "Active Sessions" /tmp/wezterm-web-active-dom.txt
agent-browser --session "$BROWSER_SESSION" snapshot -i > /tmp/wezterm-web-active.txt
browser_eval '(() => { const text = document.querySelector("dialog")?.innerText || ""; if (!text.includes("Active Sessions") || !text.includes("Open")) throw new Error("Active dialog text missing: " + text); return text; })()' >/tmp/wezterm-web-active-dom.txt
browser_eval 'document.querySelector("dialog .dialog-head button").click()' >/dev/null
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="old"]' >/dev/null
wait_for_dialog_contains "Old Sessions" /tmp/wezterm-web-old-dom.txt
agent-browser --session "$BROWSER_SESSION" snapshot -i > /tmp/wezterm-web-old.txt
browser_eval '(() => { const text = document.querySelector("dialog")?.innerText || ""; if (!text.includes("Old Sessions") || !text.includes("Restore Crashed Sessions")) throw new Error("Old dialog text missing: " + text); return text; })()' >/tmp/wezterm-web-old-dom.txt
browser_eval 'document.querySelector("dialog .dialog-head button").click()' >/dev/null
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="scroll"]' >/dev/null
wait_for_dialog_contains "Go to history top" /tmp/wezterm-web-scroll-dom.txt
agent-browser --session "$BROWSER_SESSION" snapshot -i > /tmp/wezterm-web-scroll.txt
browser_eval '(() => { const text = document.querySelector("dialog")?.innerText || ""; for (const label of ["Go to history top", "Page up", "Page down", "Go to live bottom / type"]) if (!text.includes(label)) throw new Error("Scroll dialog text missing " + label + ": " + text); return text; })()' >/tmp/wezterm-web-scroll-dom.txt
browser_eval 'document.querySelector("dialog .dialog-head button").click()' >/dev/null

echo "disposable browser mutations"
orig_window="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
before_window="$orig_window"
snapshot_windows "$initial_windows_file"
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="new"]' >/dev/null
proof_window="$(wait_for_browser_created_window "$before_window" "$initial_windows_file")"
tmux rename-window -t "$TMUX_SESSION:$proof_window" "$proof_title"
# WHY: title-sync can repair this disposable shell title to Raw Terminal before
# the proof continues. That is fine; web targeting and cleanup are protected by
# the stable tmux `@windowId`, not by mutable display text.

echo "web Active switch shield"
orig_window_json="$(json_string "$orig_window")"
proof_window_json="$(json_string "$proof_window")"
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="active"]' >/dev/null
wait_for_dialog_contains "Active Sessions" /tmp/wezterm-web-shield-active-orig.txt
browser_eval "(() => { const id=${orig_window_json}; const row=Array.from(document.querySelectorAll('dialog .row')).find(r => r.dataset.windowId === id); if (!row) throw new Error('Active row missing for original window ' + id); row.querySelector('button').click(); const state=window.__weztermWebSwitchShieldState&&window.__weztermWebSwitchShieldState(); if(!state||!state.active) throw new Error('web Active-switch shield did not activate: '+JSON.stringify(state)); return state; })()" >/tmp/wezterm-web-shield-open-orig.json
wait_for_active_window_id "$orig_window" "web Active switch to original"
wait_for_web_switch_shield_hidden "web Active-switch shield after original switch" /tmp/wezterm-web-shield-hidden-orig.json
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="active"]' >/dev/null
wait_for_dialog_contains "Active Sessions" /tmp/wezterm-web-shield-active-proof.txt
browser_eval "(() => { const id=${proof_window_json}; const row=Array.from(document.querySelectorAll('dialog .row')).find(r => r.dataset.windowId === id); if (!row) throw new Error('Active row missing for proof window ' + id); row.querySelector('button').click(); const state=window.__weztermWebSwitchShieldState&&window.__weztermWebSwitchShieldState(); if(!state||!state.active) throw new Error('web Active-switch shield did not activate for proof return: '+JSON.stringify(state)); return state; })()" >/tmp/wezterm-web-shield-open-proof.json
wait_for_active_window_id "$proof_window" "web Active switch back to disposable"
wait_for_web_switch_shield_hidden "web Active-switch shield after proof return" /tmp/wezterm-web-shield-hidden-proof.json

tmux send-keys -t "$TMUX_SESSION:$proof_window" "printf 'WEB_HISTORY_%s\\n' {1..90}" Enter
wait_for_pane_text "$proof_window" "WEB_HISTORY_90"
curl -fsS \
    -H 'User-Agent: Mozilla/5.0' \
    -H "Origin: ${CONTROL_URL}" \
    -H "X-WEzTerm-Token: $TOKEN" \
    -H "X-WEzTerm-CSRF: browser-proof" \
    "$CONTROL_URL/live-bottom" \
    | json_assert "browser token csrf live-bottom" "p.get('ok') is True"

agent-browser --session "$BROWSER_SESSION" click 'button[data-action="start"]' >/dev/null
sleep 0.4
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="stop"]' >/dev/null
sleep 0.4
tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | grep -Fxq "$proof_window"

paste_token="WEB_BROWSER_PASTE_$(date +%s)_$$"
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="copy-paste"]' >/dev/null
agent-browser --session "$BROWSER_SESSION" wait 500 >/dev/null
paste_token_json="$(PASTE_TOKEN="$paste_token" python3 - <<'PY'
import json
import os
print(json.dumps(os.environ["PASTE_TOKEN"]))
PY
)"
# WHY: Agent Browser's generic fill can silently miss a textarea inside a
# freshly-opened dialog on this web shell, which made the proof blame Copy/Paste
# even though the `/paste` endpoint itself worked. Set the dialog textarea
# through the rendered DOM and verify the value before clicking Paste so this
# proof checks the same browser UI state a user sees instead of trusting a
# selector side effect.
browser_eval "(() => { const el = document.querySelector('dialog textarea'); if (!el) throw new Error('Copy/Paste textarea missing'); el.value = ${paste_token_json}; el.dispatchEvent(new Event('input', {bubbles: true})); if (el.value !== ${paste_token_json}) throw new Error('Copy/Paste textarea did not keep proof text'); return el.value; })()" >/tmp/wezterm-web-paste-value.txt
browser_eval 'Array.from(document.querySelectorAll("dialog button")).find(b => b.textContent === "Paste").click()' >/dev/null
wait_for_pane_text "$proof_window" "$paste_token"

printf 'WEZTERM WEB UPLOAD PROOF %s\n' "$paste_token" > "$UPLOAD_PROOF_FILE"
browser_eval 'document.querySelector("button[data-action=upload]").click()' >/dev/null
agent-browser --session "$BROWSER_SESSION" wait 500 >/dev/null
upload_payload="WEZTERM WEB UPLOAD PROOF ${paste_token}"
upload_payload_json="$(UPLOAD_PAYLOAD="$upload_payload" python3 - <<'PY'
import json
import os
print(json.dumps(os.environ["UPLOAD_PAYLOAD"]))
PY
)"
browser_eval "(() => { const input = document.querySelector('dialog input[type=file]'); if (!input) throw new Error('Upload input missing'); const file = new File([${upload_payload_json}], 'wezterm-web-upload-proof.txt', {type: 'text/plain'}); const dt = new DataTransfer(); dt.items.add(file); input.files = dt.files; input.dispatchEvent(new Event('change', {bubbles: true})); return Array.from(input.files).map(f => ({name: f.name, size: f.size, type: f.type})); })()" >/tmp/wezterm-web-upload-input.json
browser_eval 'Array.from(document.querySelectorAll("dialog button")).find(b => b.textContent === "Upload").click()' >/dev/null
upload_dir="$HOME/phone-uploads/$(date +%F)"
uploaded_path=""
for _ in $(seq 1 40); do
    uploaded_path="$(find "$upload_dir" -maxdepth 1 -type f -name '*wezterm-web-upload-proof.txt' -mmin -10 -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -n 1 | cut -d' ' -f2- || true)"
    if [ -n "$uploaded_path" ] && grep -Fq "$paste_token" "$uploaded_path"; then
        break
    fi
    sleep 0.25
done
if [ -z "$uploaded_path" ] || ! grep -Fq "$paste_token" "$uploaded_path"; then
    echo "browser parity proof failed: browser Upload did not create expected proof file in $upload_dir" >&2
    ls -lt "$upload_dir" >&2 || true
    exit 1
fi

agent-browser --session "$BROWSER_SESSION" click 'button[data-action="bottom"]' >/dev/null
sleep 0.5
agent-browser --session "$BROWSER_SESSION" click 'button[data-action="close"]' >/dev/null
agent-browser --session "$BROWSER_SESSION" wait 500 >/dev/null
browser_eval 'Array.from(document.querySelectorAll("dialog button")).filter(b => b.textContent === "Close").pop().click()' >/dev/null
for _ in $(seq 1 50); do
    if ! tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | grep -Fxq "$proof_window"; then
        proof_window=""
        break
    fi
    sleep 0.2
done
if [ -n "$proof_window" ]; then
    echo "browser parity proof failed: browser Close did not remove disposable proof window $proof_window" >&2
    exit 1
fi

curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" >/dev/null || true
echo "Browser parity proof passed; screenshot: $SCREENSHOT"
