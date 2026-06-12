#!/usr/bin/env bash
set -euo pipefail

ADB_SERIAL="${ADB_SERIAL:-127.0.0.1:5556}"
CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
ACTIVITY="${WEZTERM_ACTIVITY:-com.kaleeb.wezterm/.MainActivity}"
DUMP_REMOTE="${WEZTERM_UI_DUMP_REMOTE:-/sdcard/wezterm-window.xml}"
DUMP_LOCAL="${WEZTERM_UI_DUMP_LOCAL:-/tmp/wezterm-window.xml}"
SCREENSHOT_DIR="${WEZTERM_SCREENSHOT_DIR:-/tmp}"
OLD_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-old-sessions.png"
BUTTON_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-button-proof.png"
PROOF_NAME="WEzterm UI Button Proof"
COPY_FILE="/tmp/wezterm-ui-copy-paste-proof.$$"
STEER_FILE="/tmp/wezterm-ui-steer-proof.$$"
COPY_TOKEN="PHONE_UI_COPY_SOURCE_$(date +%s)_$$"
TYPE_FILE="/tmp/wezterm-ui-keyboard-proof.$$"
TYPE_TOKEN="PHONE_UI_TYPE_$(date +%s)_$$"
REFRESH_TOKEN="PHONE_UI_REFRESH_$(date +%s)_$$"

orig_window=""
orig_mode=""
orig_scroll=""
proof_window=""
resume_window=""
reader_window=""

adb_cmd() {
    adb -s "$ADB_SERIAL" "$@"
}

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
}

control_get() {
    curl -fsS "$CONTROL_URL$1"
}

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
with open(payload_file, "r", encoding="utf-8") as handle:
    payload = json.load(handle)
allowed = {"any": any, "all": all, "isinstance": isinstance, "list": list, "len": len}
if not eval(expression, {"__builtins__": allowed}, {"p": payload}):
    raise SystemExit(f"phone menu UI proof failed: {description}: {payload}")
print(f"{description}: ok")
PY
    rm -f "$payload_file"
}

tmux_active_window() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}'
}

tmux_pane_mode() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{pane_in_mode}'
}

tmux_scroll_position() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{scroll_position}'
}

tmux_window_scroll_position() {
    local window_id="$1"
    local value
    value="$(tmux display-message -p -t "$TMUX_SESSION:$window_id" '#{scroll_position}' 2>/dev/null || true)"
    if [[ "$value" =~ ^[0-9]+$ ]]; then
        printf '%s\n' "$value"
    else
        printf '0\n'
    fi
}

tmux_window_count() {
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | wc -l
}

tmux_window_exists() {
    local window_id="$1"
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' 2>/dev/null | grep -Fxq "$window_id"
}

wait_for_shell() {
    local window_id="$1"
    for _ in $(seq 1 40); do
        local cmd
        cmd="$(tmux display-message -p -t "$TMUX_SESSION:$window_id" '#{pane_current_command}' 2>/dev/null || true)"
        if [ "$cmd" = "bash" ] || [ "$cmd" = "sh" ]; then
            return 0
        fi
        sleep 0.2
    done
    echo "phone menu UI proof failed: disposable window did not reach shell: $window_id" >&2
    exit 1
}

wait_for_active_new_window() {
    local before_count="$1"
    local forbidden_window="${2:-}"
    for _ in $(seq 1 60); do
        local active count
        active="$(tmux_active_window)"
        count="$(tmux_window_count)"
        if [ "$count" -gt "$before_count" ] && [ -n "$active" ] && [ "$active" != "$forbidden_window" ]; then
            printf '%s\n' "$active"
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: no new active tmux window appeared" >&2
    exit 1
}

wait_until_window_gone() {
    local window_id="$1"
    for _ in $(seq 1 60); do
        if ! tmux_window_exists "$window_id"; then
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: window survived close: $window_id" >&2
    exit 1
}

wait_for_pane_mode() {
    local expected="$1"
    for _ in $(seq 1 40); do
        if [ "$(tmux_pane_mode)" = "$expected" ]; then
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: expected pane mode $expected, got $(tmux_pane_mode)" >&2
    exit 1
}

wait_for_active_window_id() {
    local expected="$1"
    for _ in $(seq 1 60); do
        if [ "$(tmux_active_window)" = "$expected" ]; then
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: expected active tmux window $expected, got $(tmux_active_window)" >&2
    exit 1
}

wait_for_capture_text() {
    local window_id="$1"
    local text="$2"
    for _ in $(seq 1 60); do
        if tmux capture-pane -p -t "$TMUX_SESSION:$window_id" -S -80 2>/dev/null | grep -Fq "$text"; then
            return 0
        fi
        sleep 0.2
    done
    echo "phone menu UI proof failed: missing tmux capture text '$text' in $window_id" >&2
    exit 1
}

wait_for_file_text() {
    local path="$1"
    local text="$2"
    for _ in $(seq 1 60); do
        if [ -f "$path" ] && grep -Fq "$text" "$path"; then
            return 0
        fi
        sleep 0.2
    done
    echo "phone menu UI proof failed: missing file text '$text' in $path" >&2
    exit 1
}

assert_terminal_screenshot_has_text_pixels() {
    local screenshot="$1"
    python3 - "$screenshot" <<'PY'
from PIL import Image
import sys

path = sys.argv[1]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: xterm renders as canvas, so UIAutomator cannot see terminal text. Crop
# away status/nav bars and the bottom toolbar, then require enough light pixels
# to prove the phone is not showing the black/blank/dotted repaint failure the
# user reported after Refresh and scroll.
left, top, right, bottom = int(width * 0.02), int(height * 0.08), int(width * 0.98), int(height * 0.82)
bright = 0
for r, g, b in image.crop((left, top, right, bottom)).getdata():
    if max(r, g, b) >= 130 and (r + g + b) >= 260:
        bright += 1
if bright < 350:
    raise SystemExit(f"terminal screenshot has too few text pixels ({bright}); possible blank WebView: {path}")
print(f"terminal screenshot text pixels: {bright}")
PY
}

wait_for_steer_escape() {
    for _ in $(seq 1 60); do
        if [ -f "$STEER_FILE" ] && python3 - "$STEER_FILE" <<'PY'
import pathlib
import sys

data = pathlib.Path(sys.argv[1]).read_bytes()
raise SystemExit(0 if data == b"\x1b" else 1)
PY
        then
            return 0
        fi
        sleep 0.2
    done
    echo "phone menu UI proof failed: Steer did not deliver Escape to the active pane" >&2
    [ -f "$STEER_FILE" ] && xxd -p "$STEER_FILE" >&2 || true
    exit 1
}

has_window_focus() {
    adb_cmd shell dumpsys window | grep -Eq "mCurrentFocus=.*$PACKAGE"
}

has_activity_focus() {
    adb_cmd shell dumpsys window | grep -Eq "mFocusedApp=.*$PACKAGE"
}

focused_foreground_package() {
    adb_cmd shell dumpsys window | python3 -c 'import re,sys
text=sys.stdin.read()
for pattern in (r"mCurrentFocus=Window\{[^ ]+ u0 ([^/ ]+)/", r"mFocusedApp=ActivityRecord\{[^ ]+ u0 ([^/ ]+)/"):
    match=re.search(pattern,text)
    if match:
        print(match.group(1))
        raise SystemExit(0)'
}

dismiss_known_distractor_app() {
    [ "${WEZTERM_UI_FORCE_STOP_DISTRACTORS:-0}" = "1" ] || return 0
    local foreground
    foreground="$(focused_foreground_package || true)"
    case "$foreground" in
        com.sec.android.app.camera|com.sec.android.gallery3d|com.google.android.apps.maps)
            # WHY: real proof must fail on WEzterm bugs, not on Camera/Gallery/Maps
            # re-taking focus while UIAutomator dumps the wrong package. This is
            # opt-in because closing a foreground user app is disruptive.
            adb_cmd shell am force-stop "$foreground" >/dev/null 2>&1 || true
            sleep 0.4
            ;;
    esac
}

wake_and_dismiss_overlays() {
    # WHY: Samsung can report WEzterm as the focused app while AOD/keyguard or
    # notification shade owns mCurrentFocus. UIAutomator then dumps SystemUI, not
    # WEzterm. Wake/dismiss only before a proof run that already opted into
    # foreground control, then still require the real WEzterm window focus.
    adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_cmd shell cmd statusbar collapse >/dev/null 2>&1 || true
    dismiss_known_distractor_app
    # WHY: On the S25 Ultra, the shade can keep mCurrentFocus after collapse
    # while the app record is already WEzterm. ESCAPE is the least destructive
    # extra dismissal we observed to transfer focus back to the activity before
    # the final BACK fallbacks.
    adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
    # WHY: AOD/expanded shade sometimes captures a black screen even after the
    # shell collapse command. The short upward swipe mirrors the manual gesture
    # that returns to the app without selecting terminal text.
    adb_cmd shell input swipe 540 2100 540 500 120 >/dev/null 2>&1 || true
    if ! has_window_focus; then
        adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    fi
    if ! has_window_focus; then
        adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    fi
    sleep 0.4
}

dump_ui() {
    # WHY: source grep proved the labels exist in Java, but the user's repeated
    # regression is the installed phone UI not matching the plan. UIAutomator is
    # the cheapest real-device check that the visible menu text and dialogs are
    # actually on screen after install.
    if ! has_window_focus && [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
        wake_and_dismiss_overlays
        adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        sleep 0.6
    fi
    if ! has_window_focus; then
        echo "phone menu UI proof failed: $PACKAGE is not the current focused window; refusing to trust a stale UI dump" >&2
        adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
        exit 1
    fi
    adb_cmd shell rm -f "$DUMP_REMOTE" >/dev/null 2>&1 || true
    rm -f "$DUMP_LOCAL"
    for _ in $(seq 1 5); do
        if adb_cmd shell uiautomator dump "$DUMP_REMOTE" >/dev/null 2>&1; then
            adb_cmd pull "$DUMP_REMOTE" "$DUMP_LOCAL" >/dev/null
            if grep -Fq "package=\"$PACKAGE\"" "$DUMP_LOCAL"; then
                return 0
            fi
        fi
        sleep 0.4
    done
    echo "phone menu UI proof failed: could not capture a fresh $PACKAGE UI dump" >&2
    exit 1
}

wait_for_focus() {
    for _ in $(seq 1 20); do
        if has_window_focus; then
            return 0
        fi
        if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
            wake_and_dismiss_overlays
            adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: $PACKAGE did not become the focused app" >&2
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
    exit 1
}

dump_has_text() {
    local text="$1"
    grep -Fq "text=\"$text\"" "$DUMP_LOCAL"
}

ensure_toolbar() {
    for _ in $(seq 1 8); do
        dump_ui
        if dump_has_text "Active" && dump_has_text "Old" && dump_has_text "Refresh" && dump_has_text "Close"; then
            return 0
        fi
        if dump_has_text "Active Sessions" \
            || dump_has_text "Old Sessions" \
            || dump_has_text "Terminal Controls" \
            || dump_has_text "Copy / Paste" \
            || dump_has_text "Copy/Paste" \
            || dump_has_text "Command palette" \
            || dump_has_text "Resume old session?" \
            || grep -Eq 'text="Close .*\?"' "$DUMP_LOCAL"; then
            press_back
            wait_for_focus
            continue
        fi
        sleep 0.3
    done
    echo "phone menu UI proof failed: could not return WEzterm to the toolbar" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

assert_text() {
    local text="$1"
    dump_ui
    if ! grep -Fq "text=\"$text\"" "$DUMP_LOCAL"; then
        echo "phone menu UI proof failed: missing visible text: $text" >&2
        echo "UI dump: $DUMP_LOCAL" >&2
        exit 1
    fi
    echo "visible: $text"
}

assert_text_any() {
    for text in "$@"; do
        dump_ui
        if grep -Fq "text=\"$text\"" "$DUMP_LOCAL"; then
            echo "visible: $text"
            return 0
        fi
    done
    echo "phone menu UI proof failed: none of these texts were visible: $*" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

assert_regex() {
    local pattern="$1"
    local description="$2"
    dump_ui
    if ! grep -Eq "$pattern" "$DUMP_LOCAL"; then
        echo "phone menu UI proof failed: missing $description" >&2
        echo "Pattern: $pattern" >&2
        echo "UI dump: $DUMP_LOCAL" >&2
        exit 1
    fi
    echo "visible: $description"
}

assert_absent() {
    local text="$1"
    dump_ui
    if grep -Fiq "$text" "$DUMP_LOCAL"; then
        echo "phone menu UI proof failed: forbidden visible text: $text" >&2
        echo "UI dump: $DUMP_LOCAL" >&2
        exit 1
    fi
    echo "absent: $text"
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

tap_text_any() {
    for text in "$@"; do
        dump_ui
        if grep -Fq "text=\"$text\"" "$DUMP_LOCAL"; then
            tap_text "$text"
            return 0
        fi
    done
    echo "phone menu UI proof failed: none of these texts were visible: $*" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

press_back() {
    adb_cmd shell input keyevent KEYCODE_BACK
    sleep 0.4
}

reopen_wezterm() {
    adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null
    wait_for_focus
    sleep 1.0
    ensure_toolbar
}

select_window() {
    local window_id="$1"
    control_get "/select?fast=1&windowId=$(urlencode "$window_id")" | json_assert "select $window_id" "p.get('ok') is True"
}

cleanup_window() {
    local window_id="$1"
    if [ -n "$window_id" ] && tmux_window_exists "$window_id"; then
        control_get "/close?fast=1&windowId=$(urlencode "$window_id")" >/dev/null || \
            tmux kill-window -t "$TMUX_SESSION:$window_id" || true
    fi
}

restore_original_tmux_state() {
    if [ -z "${orig_window:-}" ] || ! tmux_window_exists "$orig_window"; then
        return 0
    fi
    control_get "/select?fast=1&windowId=$(urlencode "$orig_window")" >/dev/null || true
    local target="$TMUX_SESSION:$orig_window"
    if [ "${orig_mode:-0}" = "1" ]; then
        tmux copy-mode -e -t "$target" >/dev/null 2>&1 || true
        tmux send-keys -t "$target" -X history-bottom >/dev/null 2>&1 || true
        if [[ "${orig_scroll:-0}" =~ ^[0-9]+$ ]] && [ "${orig_scroll:-0}" -gt 0 ]; then
            tmux send-keys -t "$target" -X -N "$orig_scroll" scroll-up >/dev/null 2>&1 || true
        fi
    else
        control_get "/scroll?where=bottom" >/dev/null || true
    fi
}

cleanup() {
    set +e
    cleanup_window "$reader_window"
    cleanup_window "$resume_window"
    cleanup_window "$proof_window"
    rm -f "$COPY_FILE" "$STEER_FILE" "$TYPE_FILE"
    restore_original_tmux_state
}
trap cleanup EXIT

echo "phone menu UI proof: adb/control state"
adb_cmd get-state | grep -Fxq "device"
control_get "/health" | json_assert "control health" "p.get('ok') is True"

orig_window="$(tmux_active_window)"
orig_mode="$(tmux_pane_mode)"
orig_scroll="$(tmux_scroll_position)"

echo "phone menu UI proof: launch"
if ! has_window_focus; then
    if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" != "1" ]; then
        echo "phone menu UI proof blocked: $PACKAGE is not foregrounded." >&2
        echo "Open WEzterm on the phone, or rerun with WEZTERM_UI_ALLOW_FOCUS_STEAL=1 if foreground takeover is allowed." >&2
        adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
        exit 2
    fi
    # WHY: the user often needs the phone while development continues. Only
    # steal foreground focus when the proof run explicitly opts into it.
    wake_and_dismiss_overlays
    adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null
fi
wait_for_focus
sleep 1.0
ensure_toolbar

echo "phone menu UI proof: toolbar labels"
for label in Active Old New Refresh Scroll "Copy/Paste" Upload Steer Close; do
    assert_text "$label"
done
assert_absent "Tabs"
assert_absent "New Tab"
assert_absent "Close Tab"

echo "phone menu UI proof: Read current session opens reader and live bottom returns"
reader_count="$(tmux_window_count)"
ensure_toolbar
tap_text "Scroll"
tap_text "Read current session"
reader_window="$(wait_for_active_new_window "$reader_count" "$orig_window")"
reader_name="$(tmux display-message -p -t "$TMUX_SESSION:$reader_window" '#{window_name}')"
case "$reader_name" in
    "READ $orig_window"*) ;;
    *)
        echo "phone menu UI proof failed: reader window did not name its source: $reader_window $reader_name" >&2
        exit 1
        ;;
esac
echo "Read current session opened reader $reader_window"
ensure_toolbar
tap_text "Scroll"
tap_text "Go to live bottom / type"
wait_for_active_window_id "$orig_window"
echo "Reader live-bottom returned to $orig_window"
cleanup_window "$reader_window"
reader_window=""
reopen_wezterm

echo "phone menu UI proof: New button creates disposable session"
before_count="$(tmux_window_count)"
tap_text "New"
proof_window="$(wait_for_active_new_window "$before_count" "$orig_window")"
tmux rename-window -t "$TMUX_SESSION:$proof_window" "$PROOF_NAME"
wait_for_shell "$proof_window"
echo "New button created $proof_window"

echo "phone menu UI proof: Active button opens active session picker"
ensure_toolbar
tap_text "Active"
assert_text "Active Sessions"
assert_regex 'text="Current:' "current active session row"
assert_absent "Sessions by date"
press_back

echo "phone menu UI proof: Old button and Resume action"
ensure_toolbar
old_count="$(tmux_window_count)"
tap_text "Old"
assert_text "Old Sessions"
assert_text "Resume"
assert_regex 'text="[0-9]{4}-[0-9]{2}-[0-9]{2}' "old-session date header"
assert_absent "subagent"
assert_absent "explorer"
assert_absent "worker"
adb_cmd exec-out screencap -p > "$OLD_SCREENSHOT"
echo "old sessions screenshot: $OLD_SCREENSHOT"
tap_text "Resume"
assert_text "Resume old session?"
tap_text_any "RESUME" "Resume"
resume_window="$(wait_for_active_new_window "$old_count" "$proof_window")"
echo "Old Resume button opened $resume_window"
cleanup_window "$resume_window"
resume_window=""
select_window "$proof_window"
reopen_wezterm

echo "phone menu UI proof: Refresh button restores live tmux mode without switching sessions"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "printf '${REFRESH_TOKEN}\\n'" Enter
wait_for_capture_text "$proof_window" "$REFRESH_TOKEN"
control_get "/scroll?where=lineUp&mode=touch&repeat=3" | json_assert "pre-refresh enters copy mode" "p.get('ok') is True and p.get('paneMode') == 'copy-mode'"
wait_for_pane_mode "1"
ensure_toolbar
tap_text "Refresh"
sleep 1.5
if [ "$(tmux_active_window)" != "$proof_window" ]; then
    echo "phone menu UI proof failed: Refresh switched away from proof window" >&2
    exit 1
fi
wait_for_pane_mode "0"
adb_cmd exec-out screencap -p > /tmp/wezterm-v151-refresh-proof.png
assert_terminal_screenshot_has_text_pixels /tmp/wezterm-v151-refresh-proof.png
echo "Refresh button restored live mode"

echo "phone menu UI proof: Scroll menu buttons"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "for i in \$(seq 1 140); do printf 'UIBTN_%04d\\n' \"\$i\"; done; printf '${COPY_TOKEN}\\n'" Enter
wait_for_capture_text "$proof_window" "$COPY_TOKEN"

echo "phone menu UI proof: physical one-finger slow drag and fast flick"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"
ensure_toolbar
# WHY: the user's core regression was physical one-finger scroll feeling delayed,
# jumpy, and sometimes routed to Codex prompt history. ADB's `input swipe`
# injects the same single-pointer path the app receives from a finger; slow
# short movement must stay bounded, while a deliberate fast flick must move
# materially farther through tmux copy-mode history.
adb_cmd shell input swipe 540 1450 540 1240 900
sleep 1.2
slow_scroll="$(tmux_window_scroll_position "$proof_window")"
if [ "$slow_scroll" -le 0 ] || [ "$slow_scroll" -gt 80 ]; then
    echo "phone menu UI proof failed: slow one-finger drag should move a small readable amount, got scroll_position=$slow_scroll" >&2
    exit 1
fi
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"
ensure_toolbar
adb_cmd shell input swipe 540 1740 540 440 120
sleep 1.6
fast_scroll="$(tmux_window_scroll_position "$proof_window")"
if [ "$fast_scroll" -le "$slow_scroll" ]; then
    echo "phone menu UI proof failed: fast one-finger flick should move farther than slow drag, slow=$slow_scroll fast=$fast_scroll" >&2
    exit 1
fi
echo "Physical one-finger slow/fast scroll proved slow=$slow_scroll fast=$fast_scroll"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"

ensure_toolbar
tap_text "Scroll"
assert_text "Terminal Controls"
assert_text "Go to live bottom / type"
assert_text "Read current session"
assert_text "Command palette"
tap_text "Page up"
wait_for_pane_mode "1"
ensure_toolbar
tap_text "Scroll"
tap_text "Page down"
wait_for_pane_mode "1"
ensure_toolbar
tap_text "Scroll"
tap_text "Go to live bottom / type"
wait_for_pane_mode "0"

echo "phone menu UI proof: command palette duplicate actions"
ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
assert_text "Active Sessions"
assert_text "Old Sessions"
assert_text "Needs Attention"
assert_text "Refresh current session"
assert_text "Rename current session"
assert_absent "Previous Sessions"
assert_absent "Open full session reader"
tap_text "Needs Attention"
assert_text "Needs Attention"
press_back

ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
tap_text "Active Sessions"
assert_text "Active Sessions"
press_back

ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
tap_text "Old Sessions"
assert_text "Old Sessions"
press_back

ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
tap_text "Copy/Paste"
assert_text_any "Copy / Paste" "Copy/Paste"
assert_text "Upload media from phone"
press_back
assert_text "Upload"

ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
tap_text "Refresh current session"
sleep 1.0
if [ "$(tmux_active_window)" != "$proof_window" ]; then
    echo "phone menu UI proof failed: command-palette Refresh switched sessions" >&2
    exit 1
fi

ensure_toolbar
tap_text "Scroll"
tap_text "Command palette"
tap_text "Rename current session"
assert_text "Rename current session"
press_back

bug_dir="${XDG_RUNTIME_DIR:-/tmp}/phone-terminal/bug-reports"
mkdir -p "$bug_dir"
bug_before="$(find "$bug_dir" -maxdepth 1 -type f -name 'report-*.json' | wc -l)"
ensure_toolbar
tap_text "Scroll"
tap_text "Create bug report"
for _ in $(seq 1 30); do
    bug_after="$(find "$bug_dir" -maxdepth 1 -type f -name 'report-*.json' | wc -l)"
    if [ "$bug_after" -gt "$bug_before" ]; then
        echo "Create bug report button created report"
        break
    fi
    sleep 0.2
done
if [ "${bug_after:-0}" -le "$bug_before" ]; then
    echo "phone menu UI proof failed: Create bug report did not create a report file" >&2
    exit 1
fi

ensure_toolbar
tap_text "Scroll"
tap_text "Install/update over Tailscale"
sleep 1.5
if has_window_focus; then
    echo "phone menu UI proof failed: install page button did not leave WEzterm for ACTION_VIEW" >&2
    exit 1
fi
echo "Install/update button launched external install surface"
reopen_wezterm
select_window "$proof_window"
reopen_wezterm

echo "phone menu UI proof: Copy/Paste buttons round-trip Android clipboard through tmux"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$COPY_FILE'; printf '${COPY_TOKEN}\\n'; cat > '$COPY_FILE'" Enter
wait_for_capture_text "$proof_window" "$COPY_TOKEN"
ensure_toolbar
tap_text "Copy/Paste"
assert_text_any "Copy / Paste" "Copy/Paste"
tap_text "Copy visible terminal text"
sleep 0.8
ensure_toolbar
tap_text "Copy/Paste"
tap_text "Paste phone clipboard into terminal"
wait_for_file_text "$COPY_FILE" "$COPY_TOKEN"
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Copy/Paste buttons round-tripped $COPY_TOKEN"

echo "phone menu UI proof: tap-to-type remains visible and sends once"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$TYPE_FILE'; cat > '$TYPE_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "cat" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
# WHY: adb shell input is not Samsung Keyboard itself, but it does exercise the
# same focused xterm textarea path that previously hid or duplicated typed text
# after zoom/scroll/focus recovery. The proof must write exactly one token to
# the active pane without needing a second send.
adb_cmd shell input tap 540 1860
sleep 0.8
adb_cmd shell dumpsys input_method > /tmp/wezterm-ime-proof.txt || true
adb_cmd exec-out screencap -p > /tmp/wezterm-v151-keyboard-proof.png
adb_cmd shell input text "$TYPE_TOKEN"
adb_cmd shell input keyevent ENTER
wait_for_file_text "$TYPE_FILE" "$TYPE_TOKEN"
if [ "$(grep -F "$TYPE_TOKEN" "$TYPE_FILE" | wc -l)" -ne 1 ]; then
    echo "phone menu UI proof failed: keyboard/input token duplicated in $TYPE_FILE" >&2
    cat "$TYPE_FILE" >&2 || true
    exit 1
fi
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Tap-to-type delivered one visible token; screenshot: /tmp/wezterm-v151-keyboard-proof.png; IME dump: /tmp/wezterm-ime-proof.txt"

echo "phone menu UI proof: Steer sends Escape to the active pane"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$STEER_FILE'; python3 -c 'import sys,pathlib; pathlib.Path(\"$STEER_FILE\").write_bytes(sys.stdin.buffer.read(1))'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "python3" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
tap_text "Steer"
wait_for_steer_escape
wait_for_shell "$proof_window"
echo "Steer button delivered Escape"

echo "phone menu UI proof: Close button closes only disposable session"
select_window "$proof_window"
reopen_wezterm
tap_text "Close"
assert_regex 'text="Close .*\?"' "Close confirmation title"
tap_text_any "CLOSE" "Close"
wait_until_window_gone "$proof_window"
proof_window=""
echo "Close button removed disposable session"

echo "phone menu UI proof: restore original session"
select_window "$orig_window"
if [ "$(tmux_active_window)" != "$orig_window" ]; then
    echo "phone menu UI proof failed: original window was not restored" >&2
    exit 1
fi
tmux_state="$(tmux display-message -p -t "$TMUX_SESSION:" '#{window_id}:#{pane_in_mode}:#{scroll_position}')"
printf '%s\n' "$tmux_state" | grep -F "${orig_window}:${orig_mode}:${orig_scroll}"
adb_cmd exec-out screencap -p > "$BUTTON_SCREENSHOT"
echo "button proof screenshot: $BUTTON_SCREENSHOT"
echo "Phone menu UI button proof passed"
