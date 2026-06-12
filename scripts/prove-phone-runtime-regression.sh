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
orig_mode=""
orig_scroll=""
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
    local payload_file
    payload="$(cat)"
    payload_file="$(mktemp)"
    printf '%s' "$payload" > "$payload_file"
    python3 - "$description" "$expression" "$payload_file" <<'PY'
import json
import sys

description, expression, payload_file = sys.argv[1], sys.argv[2], sys.argv[3]
# WHY: /sessions now carries old parent Codex sessions with full readable names.
# Passing that JSON as argv eventually hit the shell's argument-size limit, so
# payloads are read from a temp file and the proof remains valid as history grows.
with open(payload_file, "r", encoding="utf-8") as handle:
    payload = json.load(handle)
allowed = {"any": any, "all": all, "isinstance": isinstance, "list": list, "len": len}
if not eval(expression, {"__builtins__": allowed}, {"p": payload}):
    raise SystemExit(f"runtime regression proof failed: {description}: {payload}")
print(f"{description}: ok")
PY
    rm -f "$payload_file"
}

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
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
sessions_payload="$(curl -fsS "$CONTROL_URL/sessions")"
sessions_payload_file="$(mktemp)"
printf '%s' "$sessions_payload" > "$sessions_payload_file"
printf '%s' "$sessions_payload" | json_assert "sessions activity groups" "p.get('ok') is True and p.get('viewSession') == 'main_phone' and any(w.get('activityGroup') for w in p.get('windows', []))"
printf '%s' "$sessions_payload" | json_assert "old parent sessions by date" "p.get('ok') is True and isinstance(p.get('oldSessions'), list) and len(p.get('oldSessions')) > 0 and all(s.get('id') and s.get('title') and s.get('dateLabel') for s in p.get('oldSessions'))"
printf '%s' "$sessions_payload" | json_assert "old sessions exclude subagents" "all(s.get('threadSource') == 'user' and s.get('source') == 'cli' and not s.get('agentNickname') for s in p.get('oldSessions', []))"

echo "old session JSONL fallback filter"
CONTROL_SERVER="${CONTROL_SERVER:-/home/cabule/.local/bin/phone-terminal-control-server}" python3 <<'PY'
import importlib.machinery
import importlib.util
import json
import os
import pathlib
import tempfile
import time

server_path = os.environ["CONTROL_SERVER"]
loader = importlib.machinery.SourceFileLoader("phone_terminal_control_server", server_path)
spec = importlib.util.spec_from_loader(loader.name, loader)
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)

with tempfile.TemporaryDirectory() as home:
    sessions_dir = pathlib.Path(home) / ".codex" / "sessions" / "2026" / "06" / "12"
    sessions_dir.mkdir(parents=True)
    normal_id = "11111111-1111-4111-8111-111111111111"
    worker_id = "22222222-2222-4222-8222-222222222222"
    normal_path = sessions_dir / f"rollout-2026-06-12T00-00-00-{normal_id}.jsonl"
    worker_path = sessions_dir / f"rollout-2026-06-12T00-00-01-{worker_id}.jsonl"
    normal_path.write_text(json.dumps({
        "payload": {
            "id": normal_id,
            "timestamp": "2026-06-12T00:00:00Z",
            "cwd": "/home/cabule",
            "title": "Parent proof session",
            "thread_source": "user",
            "source": "cli",
        }
    }) + "\n", encoding="utf-8")
    worker_path.write_text(json.dumps({
        "payload": {
            "id": worker_id,
            "timestamp": "2026-06-12T00:00:01Z",
            "cwd": "/home/cabule",
            "title": "Worker proof session",
            "thread_source": "user",
            "source": "cli",
            "agent_nickname": "Explorer",
        }
    }) + "\n", encoding="utf-8")
    now = time.time()
    os.utime(normal_path, (now, now))
    os.utime(worker_path, (now + 1, now + 1))
    old_home = os.environ.get("HOME", "")
    os.environ["HOME"] = home
    try:
        sessions = server.recent_codex_sessions(limit=10)
    finally:
        os.environ["HOME"] = old_home

ids = {session["id"] for session in sessions}
if normal_id not in ids or worker_id in ids:
    raise SystemExit(f"JSONL fallback filter failed: {sessions}")
if any(session.get("agentNickname") for session in sessions):
    raise SystemExit(f"JSONL fallback leaked agent nickname: {sessions}")
print("old session JSONL fallback filter: ok")
PY

echo "needs attention"
curl -fsS "$CONTROL_URL/needs-attention" | json_assert "needs-attention endpoint" "p.get('ok') is True and p.get('viewSession') == 'main_phone' and isinstance(p.get('windows'), list)"

orig_window="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}')"
orig_mode="$(tmux display-message -p -t "$TMUX_SESSION:" '#{pane_in_mode}')"
orig_scroll="$(tmux display-message -p -t "$TMUX_SESSION:" '#{scroll_position}')"

echo "old session resume"
IFS=$'\t' read -r old_session_id old_session_cwd < <(python3 - "$sessions_payload_file" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)
for session in payload.get("oldSessions", []):
    if session.get("threadSource") == "user" and session.get("source") == "cli" and session.get("id"):
        print(session.get("id", "") + "\t" + session.get("cwd", ""))
        break
else:
    raise SystemExit("no old parent session found")
PY
)
rm -f "$sessions_payload_file"
curl -fsS "$CONTROL_URL/resume-session?fast=1&dryRun=1&sessionId=$(urlencode "$old_session_id")&cwd=$(urlencode "$old_session_cwd")" | json_assert "resume-session dry-run" "p.get('ok') is True and p.get('action') == 'resume-dry-run' and p.get('sessionId')"
resume_payload="$(curl -fsS "$CONTROL_URL/resume-session?fast=1&sessionId=$(urlencode "$old_session_id")&cwd=$(urlencode "$old_session_cwd")")"
proof_window="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("windowId",""))' "$resume_payload")"
printf '%s' "$resume_payload" | json_assert "resume-session opens active session" "p.get('ok') is True and p.get('action') == 'resume-session-opened' and p.get('windowId', '').startswith('@')"
tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | grep -Fxq "$proof_window"
curl -fsS "$CONTROL_URL/close?fast=1&windowId=${proof_window//@/%40}" | json_assert "resume-session cleanup close" "p.get('ok') is True and p.get('action') == 'closed'"
proof_window=""
curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" | json_assert "restore after resume-session proof" "p.get('ok') is True"

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
curl -fsS "$CONTROL_URL/scroll?where=top&mode=touch" | json_assert "touch top reaches tmux history top" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-top' and p.get('paneMode') == 'copy-mode' and ('PREG_0001' in p.get('visible', {}).get('copyCursorLine', '') or any('PREG_0001' in line for line in p.get('visible', {}).get('head', []) + p.get('visible', {}).get('tail', [])))"
curl -fsS "$CONTROL_URL/scroll?where=bottom" | json_assert "bottom restores live mode" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-bottom' and not p.get('paneMode')"
curl -fsS "$CONTROL_URL/scroll?where=lineDown&mode=touch&repeat=8" | json_assert "extra down at live bottom stays stopped" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-linedown' and p.get('atLiveBottom') is True and p.get('paneMode') == 'copy-mode'"
curl -fsS "$CONTROL_URL/scroll?where=bottom" | json_assert "finger-up bottom restore exits copy mode" "p.get('ok') is True and p.get('layer') == 'tmux' and p.get('action') == 'tmux-bottom' and not p.get('paneMode')"

echo "copy paste"
printf 'PHONE_PASTE_REGRESSION_OK' | curl -fsS -X POST --data-binary @- "$CONTROL_URL/paste" | json_assert "paste endpoint" "p.get('ok') is True and p.get('action') == 'pasted'"
sleep 0.4
tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -20 | grep -Fq 'PHONE_PASTE_REGRESSION_OK'
curl -fsS "$CONTROL_URL/copy-visible" | json_assert "copy-visible endpoint" "p.get('ok') is True and p.get('action') == 'copied-visible' and 'PHONE_PASTE_REGRESSION_OK' in p.get('text', '')"

echo "media upload"
media_payload="$(printf 'PHONE_MEDIA_UPLOAD_REGRESSION_OK\n' | curl -fsS -X POST -H 'Content-Type: image/png' -H 'X-WEzTerm-Filename: proof screenshot../bad.png' --data-binary @- "$CONTROL_URL/upload-media?filename=proof%20screenshot..%2Fbad.png")"
media_payload_file="$(mktemp)"
printf '%s' "$media_payload" > "$media_payload_file"
printf '%s' "$media_payload" | json_assert "media upload endpoint" "p.get('ok') is True and p.get('action') == 'uploaded-media' and p.get('path', '').startswith('/home/cabule/phone-uploads/') and '..' not in p.get('filename', '') and '/' not in p.get('filename', '')"
uploaded_media_path="$(python3 - "$media_payload_file" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as handle:
    print(json.load(handle).get("path", ""))
PY
)"
rm -f "$media_payload_file"
grep -Fq 'PHONE_MEDIA_UPLOAD_REGRESSION_OK' "$uploaded_media_path"
rm -f "$uploaded_media_path"

chunked_media_payload="$(printf 'PHONE_MEDIA_CHUNKED_UPLOAD_REGRESSION_OK\n' | curl -fsS -X POST -H 'Content-Type: video/mp4' -H 'Transfer-Encoding: chunked' -H 'X-WEzTerm-Filename: proof-video.mp4' --data-binary @- "$CONTROL_URL/upload-media?filename=proof-video.mp4")"
chunked_media_payload_file="$(mktemp)"
printf '%s' "$chunked_media_payload" > "$chunked_media_payload_file"
printf '%s' "$chunked_media_payload" | json_assert "chunked media upload endpoint" "p.get('ok') is True and p.get('action') == 'uploaded-media' and p.get('path', '').startswith('/home/cabule/phone-uploads/') and p.get('contentType') == 'video/mp4'"
chunked_uploaded_media_path="$(python3 - "$chunked_media_payload_file" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as handle:
    print(json.load(handle).get("path", ""))
PY
)"
rm -f "$chunked_media_payload_file"
grep -Fq 'PHONE_MEDIA_CHUNKED_UPLOAD_REGRESSION_OK' "$chunked_uploaded_media_path"
rm -f "$chunked_uploaded_media_path"

echo "stable close"
curl -fsS "$CONTROL_URL/close?fast=1&windowId=${proof_window//@/%40}" | json_assert "stable windowId close" "p.get('ok') is True and p.get('action') == 'closed'"
if tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | grep -Fxq "$proof_window"; then
    echo "runtime regression proof failed: proof window survived close: $proof_window" >&2
    exit 1
fi
proof_window=""

echo "restore"
curl -fsS "$CONTROL_URL/select?fast=1&windowId=${orig_window//@/%40}" | json_assert "restore original phone tab" "p.get('ok') is True"
# WHY: the phone may already be reading history in the user's real active
# session. The proof must not turn that window into live-bottom mode just to
# satisfy the harness; it should restore the exact original tmux mode/position.
tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}:#{pane_in_mode}:#{scroll_position}' | grep -F "${orig_window}:${orig_mode}:${orig_scroll}"

echo "Phone runtime regression proof passed"
