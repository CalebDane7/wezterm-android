#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone local history UI proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

ADB_SERIAL="${ADB_SERIAL:-100.77.22.120:5555}"
CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
ACTIVITY="${WEZTERM_ACTIVITY:-com.kaleeb.wezterm/.MainActivity}"
DUMP_REMOTE="${WEZTERM_LOCAL_HISTORY_DUMP_REMOTE:-/sdcard/wezterm-local-history-$$.xml}"
DUMP_LOCAL="${WEZTERM_LOCAL_HISTORY_DUMP_LOCAL:-/tmp/wezterm-local-history-$$.xml}"
SCREENSHOT="${WEZTERM_LOCAL_HISTORY_SCREENSHOT:-/tmp/wezterm-local-history-ui.png}"
TOKEN="C5LOCALHISTORYPROOF_120"
SEARCH_MISS="NOMATCHC5"

orig_window=""
orig_mode=""
proof_window=""
initial_windows_file=""

adb_cmd() {
    adb -s "$ADB_SERIAL" "$@"
}

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
}

control_get() {
    curl -fsS "$CONTROL_URL$1"
}

tmux_active_window() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}'
}

tmux_pane_mode() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{pane_in_mode}'
}

tmux_window_exists() {
    local window_id="$1"
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$window_id"
}

record_initial_windows() {
    initial_windows_file="$(mktemp)"
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' > "$initial_windows_file"
}

window_was_initial() {
    local window_id="$1"
    [ -n "${initial_windows_file:-}" ] && [ -f "$initial_windows_file" ] && grep -Fxq "$window_id" "$initial_windows_file"
}

cleanup() {
    set +e
    if [ -n "${orig_window:-}" ] && tmux_window_exists "$orig_window"; then
        control_get "/select?fast=1&windowId=$(urlencode "$orig_window")" >/dev/null 2>&1 || true
    fi
    if [ -n "${proof_window:-}" ] && tmux_window_exists "$proof_window"; then
        if window_was_initial "$proof_window"; then
            echo "phone local history UI proof cleanup refused to close pre-existing user window: $proof_window" >&2
        else
            tmux kill-window -t "$TMUX_SESSION:$proof_window" >/dev/null 2>&1 || true
        fi
    fi
    rm -f "$DUMP_LOCAL" "$initial_windows_file"
}
trap cleanup EXIT

has_window_focus() {
    adb_cmd shell dumpsys window 2>/dev/null \
        | grep -E 'mCurrentFocus|mFocusedApp' \
        | grep -Fq "$PACKAGE"
}

wake_and_dismiss_overlays() {
    # WHY: Samsung can keep NotificationShade or keyguard in front of WEzTerm
    # even after the app is the focused task. The C5 proof owns foreground only
    # when WEZTERM_UI_ALLOW_FOCUS_STEAL=1, then still requires a fresh WEzTerm
    # UIAutomator dump before trusting any visible text.
    adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_cmd shell cmd statusbar collapse >/dev/null 2>&1 || true
    adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
    adb_cmd shell input swipe 540 2100 540 500 120 >/dev/null 2>&1 || true
    if ! has_window_focus; then
        adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    fi
    if ! has_window_focus; then
        adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    fi
    sleep 0.4
}

wait_for_focus() {
    for _ in $(seq 1 24); do
        if has_window_focus; then
            return 0
        fi
        if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
            wake_and_dismiss_overlays
            adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        fi
        sleep 0.25
    done
    echo "phone local history UI proof failed: $PACKAGE did not become the focused app" >&2
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
    exit 1
}

dump_ui() {
    if ! has_window_focus && [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
        wake_and_dismiss_overlays
        adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        wait_for_focus
    fi
    if ! has_window_focus; then
        echo "phone local history UI proof failed: $PACKAGE is not focused; refusing stale UI dump" >&2
        adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
        exit 1
    fi
    local dump_log="/tmp/wezterm-local-history-uia-$$.log"
    adb_cmd shell rm -f "$DUMP_REMOTE" >/dev/null 2>&1 || true
    for _ in $(seq 1 5); do
        rm -f "$DUMP_LOCAL" "$dump_log"
        if adb_cmd shell uiautomator dump "$DUMP_REMOTE" >"$dump_log" 2>&1 \
            && adb_cmd pull "$DUMP_REMOTE" "$DUMP_LOCAL" >>"$dump_log" 2>&1 \
            && [ -s "$DUMP_LOCAL" ] \
            && grep -Fq "package=\"$PACKAGE\"" "$DUMP_LOCAL"; then
            return 0
        fi
        if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
            wake_and_dismiss_overlays
            adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
            wait_for_focus
        fi
        sleep 0.4
    done
    echo "phone local history UI proof failed: could not capture a fresh $PACKAGE UI dump" >&2
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
    tail -40 "$dump_log" >&2 || true
    exit 1
}

dump_has_text_contains() {
    local text="$1"
    grep -Fq "$text" "$DUMP_LOCAL"
}

tap_text() {
    local text="$1"
    dump_ui
    local coords
    coords="$(python3 - "$DUMP_LOCAL" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == wanted:
        bounds = node.attrib.get("bounds", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            raise SystemExit(f"no parsable bounds for {wanted}: {bounds}")
        left, top, right, bottom = map(int, match.groups())
        print((left + right) // 2, (top + bottom) // 2)
        break
else:
    raise SystemExit(f"missing text {wanted}")
PY
)"
    read -r x y <<<"$coords"
    adb_cmd shell input tap "$x" "$y"
    sleep 0.8
}

tap_text_if_visible() {
    local text="$1"
    dump_ui
    local coords
    if ! coords="$(python3 - "$DUMP_LOCAL" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == wanted:
        bounds = node.attrib.get("bounds", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        print((left + right) // 2, (top + bottom) // 2)
        raise SystemExit(0)
raise SystemExit(1)
PY
)"; then
        return 1
    fi
    read -r x y <<<"$coords"
    adb_cmd shell input tap "$x" "$y"
    sleep 0.8
}

wait_for_text_contains() {
    local text="$1"
    for _ in $(seq 1 24); do
        dump_ui
        if dump_has_text_contains "$text"; then
            echo "visible: $text"
            return 0
        fi
        sleep 0.4
    done
    echo "phone local history UI proof failed: missing visible text containing: $text" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

type_text_into_focused_field() {
    local text="$1"
    adb_cmd shell input text "$text"
    sleep 0.8
}

wait_for_capture_text() {
    local window_id="$1"
    local text="$2"
    for _ in $(seq 1 60); do
        if tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -200 2>/dev/null | grep -Fq "$text"; then
            return 0
        fi
        sleep 0.2
    done
    echo "phone local history UI proof failed: missing tmux capture text '$text' in $window_id" >&2
    exit 1
}

record_initial_windows
orig_window="$(tmux_active_window)"
orig_mode="$(tmux_pane_mode)"
proof_window="$(tmux new-window -d -P -F '#{window_id}' -t "$TMUX_SESSION:" -n 'C5 Local History Proof' 'bash --noprofile --norc')"
tmux send-keys -t "$TMUX_SESSION:$proof_window" \
    'for i in $(seq -w 1 120); do printf "C5LOCALHISTORYPROOF_%s cached local history row\n" "$i"; done' Enter
wait_for_capture_text "$proof_window" "$TOKEN"
control_get "/select-live?fast=1&windowId=$(urlencode "$proof_window")" >/dev/null

before_window="$(tmux_active_window)"
before_mode="$(tmux_pane_mode)"
chunk_payload="$(control_get "/scrollback/chunk?windowId=$(urlencode "$proof_window")&lines=20")"
python3 - "$TOKEN" "$chunk_payload" <<'PY'
import json
import sys

token, raw = sys.argv[1], sys.argv[2]
payload = json.loads(raw)
if not payload.get("ok"):
    raise SystemExit(f"scrollback/chunk failed: {payload}")
if not payload.get("readOnly"):
    raise SystemExit(f"scrollback/chunk was not readOnly: {payload}")
rows = "\n".join(payload.get("rows") or [])
if token not in rows:
    raise SystemExit(f"scrollback/chunk did not return proof token {token}")
print("scrollback/chunk returned cached proof rows")
PY
after_window="$(tmux_active_window)"
after_mode="$(tmux_pane_mode)"
if [ "$before_window" != "$after_window" ]; then
    echo "phone local history UI proof failed: endpoint changed active window ($before_window -> $after_window)" >&2
    exit 1
fi
if [ "$before_mode" != "$after_mode" ]; then
    echo "phone local history UI proof failed: endpoint changed pane mode ($before_mode -> $after_mode)" >&2
    exit 1
fi

if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
    wake_and_dismiss_overlays
fi
adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null
wait_for_focus
wait_for_text_contains "Scroll"
tap_text "Scroll"
wait_for_text_contains "Local history search"
tap_text "Local history search"
wait_for_text_contains "Local history"
wait_for_text_contains "$TOKEN"
tap_text "Search cached history"
type_text_into_focused_field "$SEARCH_MISS"
# WHY: do not press Back here. On Samsung, Back can dismiss the AlertDialog
# itself instead of only hiding the keyboard, which made proof leave the viewer
# before checking the cached-search result. UIAutomator can still see the
# read-only result text with IME focus active.
wait_for_text_contains "No cached history matches"

ui_window="$(tmux_active_window)"
ui_mode="$(tmux_pane_mode)"
if [ "$ui_window" != "$before_window" ]; then
    echo "phone local history UI proof failed: UI viewer changed active window ($before_window -> $ui_window)" >&2
    exit 1
fi
if [ "$ui_mode" != "$before_mode" ]; then
    echo "phone local history UI proof failed: UI viewer changed pane mode ($before_mode -> $ui_mode)" >&2
    exit 1
fi

adb_cmd exec-out screencap -p > "$SCREENSHOT"
# WHY: close the dialog through its real button after the proof screenshot.
# The search EditText can keep Samsung IME open, hiding the footer buttons; hide
# only the keyboard first, then tap CLOSE. This keeps the proof from leaving a
# modal overlay that blocks the next phone proof from seeing the toolbar.
adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
sleep 0.5
if ! tap_text_if_visible "CLOSE"; then
    adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
fi
sleep 0.4
echo "Phone local history UI proof passed; screenshot: $SCREENSHOT"
