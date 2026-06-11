#!/usr/bin/env bash
set -euo pipefail

CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
INSTALL_URL="${PHONE_INSTALL_URL:-http://100.113.254.7:8091/install.html}"
ADB_SERIAL="${ADB_SERIAL:-127.0.0.1:5556}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
APK="$ROOT/build/WEzterm.apk"
EXPECTED_VERSION_NAME="${EXPECTED_VERSION_NAME:-$(grep -o 'android:versionName="[^"]*"' "$MANIFEST" | head -n 1 | cut -d'"' -f2)}"
EXPECTED_VERSION_CODE="${EXPECTED_VERSION_CODE:-$(grep -o 'android:versionCode="[0-9]*"' "$MANIFEST" | head -n 1 | cut -d'"' -f2)}"
EXPECTED_SHA="${EXPECTED_SHA:-$(sha256sum "$APK" | awk '{print $1}')}"

orig_window=""
proof_window=""

cleanup() {
    # WHY: this script creates a disposable tmux window to prove destructive
    # paths like stable close/select. Always restore the user's original phone
    # window and remove the proof window so a failed proof cannot become the next
    # confusing phone-tab regression.
    if [ -n "${proof_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$proof_window"; then
        curl -fsS "$CONTROL_URL/close?fast=1&windowId=${proof_window//@/%40}" >/dev/null || \
            tmux kill-window -t "$TMUX_SESSION:$proof_window" || true
    fi
    if [ -n "${orig_window:-}" ] && tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$orig_window"; then
        curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" >/dev/null || true
    fi
}
trap cleanup EXIT

json_assert() {
    local description="$1"
    local expression="$2"
    local payload
    payload="$(cat)"
    python3 -c '
import json
import sys

description, expression, raw_payload = sys.argv[1], sys.argv[2], sys.argv[3]
payload = json.loads(raw_payload)
allowed = {"any": any, "all": all, "isinstance": isinstance, "list": list, "len": len}
if not eval(expression, {"__builtins__": allowed}, {"p": payload}):
    raise SystemExit(f"runtime regression proof failed: {description}: {payload}")
print(f"{description}: ok")
' "$description" "$expression" "$payload"
}

echo "health"
curl -fsS "$CONTROL_URL/health" | json_assert "control health" "p.get('ok') is True"

echo "no-usb package"
adb -s "$ADB_SERIAL" get-state | grep -Fxq "device"
adb -s "$ADB_SERIAL" shell dumpsys package com.kaleeb.wezterm > /tmp/wezterm-package-proof.txt
grep -Fq "versionCode=$EXPECTED_VERSION_CODE" /tmp/wezterm-package-proof.txt
grep -Fq "versionName=$EXPECTED_VERSION_NAME" /tmp/wezterm-package-proof.txt

echo "install page"
curl -fsS "$INSTALL_URL" > /tmp/wezterm-install-proof.html
grep -Fq "WEzterm v$EXPECTED_VERSION_NAME" /tmp/wezterm-install-proof.html
grep -Fq "versionCode: <code>$EXPECTED_VERSION_CODE</code>" /tmp/wezterm-install-proof.html
grep -Fq "$EXPECTED_SHA" /tmp/wezterm-install-proof.html

echo "sessions by date"
curl -fsS "$CONTROL_URL/sessions" | json_assert "sessions activity groups" "p.get('ok') is True and p.get('viewSession') == 'main_phone' and any(w.get('activityGroup') for w in p.get('windows', []))"

echo "needs attention"
curl -fsS "$CONTROL_URL/needs-attention" | json_assert "needs-attention endpoint" "p.get('ok') is True and p.get('viewSession') == 'main_phone' and isinstance(p.get('windows'), list)"

orig_window="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
proof_window="$(tmux new-window -d -P -F '#{window_id}' -t "$TMUX_SESSION:" -n phone-runtime-regression "bash -lc 'for i in \$(seq 1 2400); do printf \"PREG_%04d\\n\" \"\$i\"; done; printf \"READY_COPYPASTE\\n\"; exec bash -li'")"
echo "proof window $proof_window from $orig_window"

for _ in $(seq 1 30); do
    if tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -5 | grep -Eq 'READY_COPYPASTE|cabule@'; then
        break
    fi
    sleep 0.2
done

echo "stable select"
curl -fsS "$CONTROL_URL/select?fast=1&windowId=${proof_window//@/%40}" | json_assert "stable windowId select" "p.get('ok') is True"

echo "touch scroll"
curl -fsS "$CONTROL_URL/scroll?where=lineUp&mode=touch&repeat=7" | json_assert "touch line up is tmux-owned" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-lineup' and p.get('paneMode') == 'copy-mode'"
curl -fsS "$CONTROL_URL/scroll?where=lineDown&mode=touch&repeat=3" | json_assert "touch line down is tmux-owned" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-linedown' and p.get('paneMode') == 'copy-mode'"
curl -fsS "$CONTROL_URL/scroll?where=bottom" | json_assert "bottom restores live mode" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-bottom' and not p.get('paneMode')"
curl -fsS "$CONTROL_URL/scroll?where=lineDown&mode=touch&repeat=8" | json_assert "extra down at live bottom stays stopped" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-linedown' and p.get('atLiveBottom') is True and p.get('paneMode') == 'copy-mode'"
curl -fsS "$CONTROL_URL/scroll?where=bottom" | json_assert "finger-up bottom restore exits copy mode" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-bottom' and not p.get('paneMode')"

echo "copy paste"
printf 'PHONE_PASTE_REGRESSION_OK' | curl -fsS -X POST --data-binary @- "$CONTROL_URL/paste" | json_assert "paste endpoint" "p.get('ok') is True and p.get('action') == 'pasted'"
sleep 0.4
tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -20 | grep -Fq 'PHONE_PASTE_REGRESSION_OK'
curl -fsS "$CONTROL_URL/copy-visible" | json_assert "copy-visible endpoint" "p.get('ok') is True and p.get('action') == 'copied-visible' and 'PHONE_PASTE_REGRESSION_OK' in p.get('text', '')"

echo "stable close"
curl -fsS "$CONTROL_URL/close?fast=1&windowId=${proof_window//@/%40}" | json_assert "stable windowId close" "p.get('ok') is True and p.get('action') == 'closed'"
if tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | grep -Fxq "$proof_window"; then
    echo "runtime regression proof failed: proof window survived close: $proof_window" >&2
    exit 1
fi
proof_window=""

echo "restore"
curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" | json_assert "restore original phone tab" "p.get('ok') is True"
tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}:#{pane_in_mode}:#{scroll_position}' | grep -F "${orig_window}:0:"

echo "Phone runtime regression proof passed"
