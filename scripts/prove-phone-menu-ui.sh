#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone menu UI proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

ADB_SERIAL="${ADB_SERIAL:-127.0.0.1:5556}"
CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
ACTIVITY="${WEZTERM_ACTIVITY:-com.kaleeb.wezterm/.MainActivity}"
DUMP_REMOTE="${WEZTERM_UI_DUMP_REMOTE:-/sdcard/wezterm-window-$$.xml}"
DUMP_LOCAL="${WEZTERM_UI_DUMP_LOCAL:-/tmp/wezterm-window-$$.xml}"
SCREENSHOT_DIR="${WEZTERM_SCREENSHOT_DIR:-/tmp}"
OLD_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-old-sessions.png"
BUTTON_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-button-proof.png"
PROOF_NAME="WEzterm UI Button Proof"
COPY_FILE="/tmp/wezterm-ui-copy-paste-proof.$$"
STOP_FILE="/tmp/wezterm-ui-stop-proof.$$"
COPY_TOKEN="PHONE_UI_COPY_SOURCE_$(date +%s)_$$"
COPY_SENTENCE="PHONE UI COPY FULL TEXT OK $(date +%s) $$"
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

tmux_window_pane_mode() {
    local window_id="$1"
    tmux display-message -p -t "$TMUX_SESSION:$window_id" '#{pane_in_mode}' 2>/dev/null || true
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

wait_for_window_pane_mode() {
    local window_id="$1"
    local expected="$2"
    for _ in $(seq 1 40); do
        if [ "$(tmux_window_pane_mode "$window_id")" = "$expected" ]; then
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: expected pane $window_id mode $expected, got $(tmux_window_pane_mode "$window_id")" >&2
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
from collections import Counter
from PIL import Image
import sys

path = sys.argv[1]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: xterm renders as canvas, so UIAutomator cannot see terminal text. Crop
# away status/nav bars and the bottom toolbar, but keep the first xterm rows.
# The terminal text can be dim at phone scale, so count pixels with meaningful
# contrast against the dominant terminal background instead of requiring bright
# white text. This still fails on the blank/dotted repaint state while not
# rejecting valid dark-theme terminal text at the top of the pane.
left, top, right, bottom = int(width * 0.02), int(height * 0.04), int(width * 0.98), int(height * 0.82)
crop = image.crop((left, top, right, bottom))
pixels = list(crop.getdata())
background = Counter(pixels).most_common(1)[0][0]
contrast = 0
for r, g, b in pixels:
    if abs(r - background[0]) + abs(g - background[1]) + abs(b - background[2]) >= 18:
        contrast += 1
if contrast < 350:
    raise SystemExit(f"terminal screenshot has too few text pixels ({contrast}); possible blank WebView: {path}")
print(f"terminal screenshot text pixels: {contrast}")
PY
}

wait_for_stop_escape() {
    for _ in $(seq 1 60); do
        if [ -f "$STOP_FILE" ] && python3 - "$STOP_FILE" <<'PY'
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
    echo "phone menu UI proof failed: Stop did not deliver Escape to the active pane" >&2
    [ -f "$STOP_FILE" ] && xxd -p "$STOP_FILE" >&2 || true
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
    local dump_log="/tmp/wezterm-uia-dump-$$.log"
    for _ in $(seq 1 5); do
        rm -f "$DUMP_LOCAL" "$dump_log"
        # WHY: Samsung/SystemUI overlays and UIAutomator idle timeouts can make
        # `uiautomator dump` return without a usable XML file. Keep the whole
        # dump/pull/nonempty/package check inside the retry condition so `set -e`
        # cannot abort before the intended fresh-dump diagnostics run.
        if adb_cmd shell uiautomator dump "$DUMP_REMOTE" >"$dump_log" 2>&1 \
            && adb_cmd pull "$DUMP_REMOTE" "$DUMP_LOCAL" >>"$dump_log" 2>&1 \
            && [ -s "$DUMP_LOCAL" ]; then
            if grep -Fq "package=\"$PACKAGE\"" "$DUMP_LOCAL"; then
                return 0
            fi
        fi
        sleep 0.4
    done
    echo "phone menu UI proof failed: could not capture a fresh $PACKAGE UI dump" >&2
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
    tail -40 "$dump_log" >&2 || true
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
            || dump_has_text "Scroll" \
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

input_method_visible() {
    adb_cmd shell dumpsys input_method | grep -Eq 'mInputShown=true|mImeWindowVis=[^0]'
}

assert_terminal_toolbar_geometry() {
    dump_ui
    local ime_state="hidden"
    if input_method_visible; then
        ime_state="visible"
    fi
    python3 - "$DUMP_LOCAL" "$ime_state" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, ime_state = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
terminal = None
buttons = []
nav_top = None
screen_bottom = 0
toolbar_labels = {"Active", "Old", "New", "Refresh", "Bottom", "Scroll", "Copy/Paste", "Upload", "Close", "Start", "Stop"}
composer_top = None

def parse_bounds(value):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value or "")
    if not match:
        return None
    return tuple(map(int, match.groups()))

for node in root.iter("node"):
    bounds = parse_bounds(node.attrib.get("bounds", ""))
    if not bounds:
        continue
    left, top, right, bottom = bounds
    screen_bottom = max(screen_bottom, bottom)
    resource_id = node.attrib.get("resource-id", "")
    klass = node.attrib.get("class", "")
    text = node.attrib.get("text", "")
    if resource_id == "terminal-container" or klass == "android.webkit.WebView":
        area = max(0, right - left) * max(0, bottom - top)
        prefer = resource_id == "terminal-container"
        candidate = (prefer, area, left, top, right, bottom)
        if terminal is None or candidate > terminal:
            terminal = candidate
    if text in toolbar_labels and klass == "android.widget.Button":
        buttons.append((left, top, right, bottom, text))
    if klass == "android.widget.EditText" and (
        text == "Type prompt"
        or node.attrib.get("content-desc", "") == "Type prompt"
        or node.attrib.get("hint", "") == "Type prompt"
    ):
        composer_top = top if composer_top is None else min(composer_top, top)
    if resource_id == "android:id/navigationBarBackground":
        nav_top = top if nav_top is None else min(nav_top, top)

if terminal is None:
    raise SystemExit("could not find terminal-container/WebView bounds")
if len(buttons) < len(toolbar_labels):
    found = sorted({item[4] for item in buttons})
    raise SystemExit(f"not all toolbar buttons were present: {found}")

_, _, t_left, t_top, t_right, t_bottom = terminal
button_top = min(item[1] for item in buttons)
button_bottom = max(item[3] for item in buttons)
button_band = button_bottom - button_top
content_bottom = nav_top if nav_top is not None else screen_bottom
if composer_top is not None and composer_top > button_bottom:
    content_bottom = min(content_bottom, composer_top)
gap_above_buttons = button_top - t_bottom
blank_below_buttons = content_bottom - button_bottom

if t_bottom > button_top:
    raise SystemExit(f"terminal overlaps toolbar buttons: terminal_bottom={t_bottom} button_top={button_top}")
if gap_above_buttons > 96:
    raise SystemExit(f"large gap between terminal and toolbar buttons: gap={gap_above_buttons}")
if ime_state == "hidden" and composer_top is None and blank_below_buttons > max(180, button_band):
    raise SystemExit(
        "toolbar is consuming hidden-keyboard space: "
        f"blank_below_buttons={blank_below_buttons} button_band={button_band} nav_top={nav_top}"
    )
print(
    "Terminal and toolbar bounds do not overlap: "
    f"terminal=[{t_left},{t_top}][{t_right},{t_bottom}] "
    f"buttons_y={button_top}..{button_bottom} "
    f"blank_below_buttons={blank_below_buttons} ime={ime_state}"
)
PY
}

dismiss_native_composer_if_open() {
    dump_ui
    if grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL"; then
        press_back
        sleep 0.2
        dump_ui
        if grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL"; then
            press_back
            sleep 0.3
        fi
        echo "Native composer dismissed before proof step"
    fi
}

assert_old_sessions_without_agent_labels() {
    dump_ui
    local forbidden
    for forbidden in subagent explorer worker; do
        if grep -Fiq "$forbidden" "$DUMP_LOCAL"; then
            echo "phone menu UI proof failed: forbidden old-session agent label visible: $forbidden" >&2
            echo "UI dump: $DUMP_LOCAL" >&2
            exit 1
        fi
    done
    echo "absent: subagent/explorer/worker"
}

swipe_first_scrollable_down() {
    dump_ui
    local coords
    coords="$(python3 - "$DUMP_LOCAL" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
chosen = None
for node in root.iter("node"):
    if node.attrib.get("scrollable") != "true":
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    width = right - left
    height = bottom - top
    if width < 100 or height < 100:
        continue
    area = width * height
    if chosen is None or area > chosen[0]:
        chosen = (area, left, top, right, bottom)
if chosen is None:
    raise SystemExit("no scrollable node found")
_, left, top, right, bottom = chosen
x = (left + right) // 2
height = bottom - top
y1 = int(top + height * 0.78)
y2 = int(top + height * 0.28)
print(x, y1, x, y2)
PY
)"
    read -r x1 y1 x2 y2 <<<"$coords"
    adb_cmd shell input swipe "$x1" "$y1" "$x2" "$y2" 450
    sleep 0.5
}

scroll_until_text() {
    local text="$1"
    for _ in $(seq 1 6); do
        dump_ui
        if dump_has_text "$text"; then
            echo "visible: $text"
            return 0
        fi
        swipe_first_scrollable_down
    done
    echo "phone menu UI proof failed: could not scroll to visible text: $text" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
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

long_press_text() {
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
    # WHY: Command Palette intentionally lives behind a Scroll long-press now.
    # A long `input swipe` with identical start/end coordinates is the standard
    # ADB way to synthesize a long press without using brittle absolute bounds.
    adb_cmd shell input swipe "$x" "$y" "$x" "$y" 900
    sleep 0.8
}

terminal_swipe() {
    local direction="$1"
    local duration_ms="$2"
    dump_ui
    local coords
    coords="$(python3 - "$DUMP_LOCAL" "$direction" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, direction = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
chosen = None
for node in root.iter("node"):
    resource_id = node.attrib.get("resource-id", "")
    klass = node.attrib.get("class", "")
    if resource_id != "terminal-container" and klass != "android.webkit.WebView":
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    width = right - left
    height = bottom - top
    area = width * height
    if width <= 100 or height <= 100:
        continue
    if chosen is None or (resource_id == "terminal-container", area) > (chosen[0], chosen[1]):
        chosen = (resource_id == "terminal-container", area, left, top, right, bottom)
if chosen is None:
    raise SystemExit("could not find terminal WebView bounds")
_, _, left, top, right, bottom = chosen
height = bottom - top
x = (left + right) // 2
def y(frac):
    return max(top + 4, min(bottom - 4, int(top + height * frac)))
if direction == "history-slow":
    y1, y2 = y(0.45), y(0.75)
elif direction == "history-fast":
    y1, y2 = y(0.20), y(0.86)
elif direction == "live-return":
    y1, y2 = y(0.86), y(0.20)
else:
    raise SystemExit(f"unknown direction {direction}")
print(x, y1, x, y2)
PY
)"
    read -r x1 y1 x2 y2 <<<"$coords"
    echo "terminal swipe $direction: $x1,$y1 -> $x2,$y2 duration=${duration_ms}ms"
    adb_cmd shell input swipe "$x1" "$y1" "$x2" "$y2" "$duration_ms"
}

terminal_tap_for_typing() {
    dump_ui
    local coords
    coords="$(python3 - "$DUMP_LOCAL" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
chosen = None
for node in root.iter("node"):
    resource_id = node.attrib.get("resource-id", "")
    klass = node.attrib.get("class", "")
    if resource_id != "terminal-container" and klass != "android.webkit.WebView":
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    area = (right - left) * (bottom - top)
    if area <= 10000:
        continue
    if chosen is None or (resource_id == "terminal-container", area) > (chosen[0], chosen[1]):
        chosen = (resource_id == "terminal-container", area, left, top, right, bottom)
if chosen is None:
    raise SystemExit("could not find terminal WebView bounds")
_, _, left, top, right, bottom = chosen
x = (left + right) // 2
y = max(top + 4, min(bottom - 8, int(top + (bottom - top) * 0.92)))
print(x, y)
PY
)"
    read -r x y <<<"$coords"
    echo "terminal tap for typing: $x,$y"
    adb_cmd shell input tap "$x" "$y"
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
    rm -f "$COPY_FILE" "$STOP_FILE" "$TYPE_FILE"
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
dismiss_native_composer_if_open

echo "phone menu UI proof: toolbar labels"
for label in Active Old New Refresh Bottom Scroll "Copy/Paste" Upload Close Start Stop; do
    assert_text "$label"
done
assert_absent "Tabs"
assert_absent "New Tab"
assert_absent "Close Tab"
assert_terminal_toolbar_geometry

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
dismiss_native_composer_if_open
cleanup_window "$reader_window"
reader_window=""
reopen_wezterm
ensure_toolbar
tap_text "Bottom"
wait_for_active_window_id "$orig_window"
echo "Direct Bottom button returned to live typing"
dismiss_native_composer_if_open

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

echo "phone menu UI proof: Active row title switches with one tap"
select_window "$orig_window"
reopen_wezterm
tap_text "Active"
assert_text "Active Sessions"
tap_text "$PROOF_NAME"
wait_for_active_window_id "$proof_window"
adb_cmd exec-out screencap -p > /tmp/wezterm-v157-active-title-one-tap.png
assert_terminal_screenshot_has_text_pixels /tmp/wezterm-v157-active-title-one-tap.png
echo "Active row title switched with one tap"

echo "phone menu UI proof: Old button and Resume action"
ensure_toolbar
old_count="$(tmux_window_count)"
tap_text "Old"
assert_text "Old Sessions"
assert_text "Resume"
assert_regex 'text="[0-9]{4}-[0-9]{2}-[0-9]{2}' "old-session date header"
assert_old_sessions_without_agent_labels
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
control_get "/touch-scroll?where=lineUp&repeat=3" | json_assert "pre-refresh enters copy mode" "p.get('ok') is True and p.get('paneMode') == 'copy-mode'"
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
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "0"
ensure_toolbar
# WHY: the user's core regression was physical one-finger scroll feeling delayed,
# jumpy, and sometimes routed to Codex prompt history. ADB's `input swipe`
# injects the same single-pointer path the app receives from a finger; slow
# short movement must stay bounded, while a deliberate fast flick must move
# materially farther through tmux copy-mode history.
# WHY: WEzterm intentionally maps phone finger movement like normal scrollback:
# dragging the finger down moves into older tmux history (`lineUp`), and dragging
# the finger up returns toward the live bottom (`lineDown`). Use the live
# WebView bounds instead of hardcoded portrait coordinates so the proof still
# tests the real terminal if the phone is in landscape or the IME resized it.
terminal_swipe history-slow 900
sleep 1.2
wait_for_active_window_id "$proof_window"
slow_scroll="$(tmux_window_scroll_position "$proof_window")"
if [ "$slow_scroll" -le 0 ] || [ "$slow_scroll" -gt 80 ]; then
    echo "phone menu UI proof failed: slow one-finger drag should move a small readable amount, got scroll_position=$slow_scroll" >&2
    exit 1
fi
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "0"
ensure_toolbar
terminal_swipe history-fast 120
sleep 1.6
wait_for_active_window_id "$proof_window"
fast_scroll="$(tmux_window_scroll_position "$proof_window")"
if [ "$fast_scroll" -le "$slow_scroll" ]; then
    echo "phone menu UI proof failed: fast one-finger flick should move farther than slow drag, slow=$slow_scroll fast=$fast_scroll" >&2
    exit 1
fi
echo "Physical one-finger slow/fast scroll proved slow=$slow_scroll fast=$fast_scroll"
control_get "/touch-scroll?where=lineUp&repeat=8" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "1"
ensure_toolbar
terminal_swipe live-return 250
sleep 1.4
wait_for_active_window_id "$proof_window"
live_return_mode="$(tmux_window_pane_mode "$proof_window")"
live_return_scroll="$(tmux_window_scroll_position "$proof_window")"
if [ "$live_return_mode" != "0" ] || { [ -n "$live_return_scroll" ] && [ "$live_return_scroll" != "0" ]; }; then
    echo "phone menu UI proof failed: physical one-finger down swipe did not return to live bottom quietly; mode=$live_return_mode scroll=$live_return_scroll" >&2
    exit 1
fi
echo "Physical one-finger live-bottom exited copy-mode quietly"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"

ensure_toolbar
tap_text "Scroll"
assert_text "Scroll"
assert_text "Go to live bottom / type"
assert_text "Go to history top"
assert_text "Read current session"
assert_text "Page up"
assert_text "Page down"
assert_absent "Command palette"
assert_absent "Active Sessions"
assert_absent "Old Sessions"
assert_absent "Needs Attention"
assert_absent "Refresh current session"
assert_absent "Upload media from phone"
assert_absent "Type prompt safely"
echo "Scroll menu is scroll-only"
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
long_press_text "Scroll"
assert_text "Active Sessions"
assert_text "Old Sessions"
assert_text "Needs Attention"
assert_text "Refresh current session"
assert_absent "Previous Sessions"
assert_absent "Open full session reader"
tap_text "Needs Attention"
assert_text "Needs Attention"
press_back

ensure_toolbar
long_press_text "Scroll"
tap_text "Active Sessions"
assert_text "Active Sessions"
press_back

ensure_toolbar
long_press_text "Scroll"
tap_text "Old Sessions"
assert_text "Old Sessions"
press_back

ensure_toolbar
long_press_text "Scroll"
tap_text "Copy/Paste"
assert_text_any "Copy / Paste" "Copy/Paste"
assert_text "Upload media from phone"
press_back
assert_text "Upload"

ensure_toolbar
long_press_text "Scroll"
tap_text "Refresh current session"
sleep 1.0
if [ "$(tmux_active_window)" != "$proof_window" ]; then
    echo "phone menu UI proof failed: command-palette Refresh switched sessions" >&2
    exit 1
fi

ensure_toolbar
long_press_text "Scroll"
scroll_until_text "Rename current session"
tap_text "Rename current session"
assert_text "Rename current session"
press_back

bug_dir="${XDG_RUNTIME_DIR:-/tmp}/phone-terminal/bug-reports"
mkdir -p "$bug_dir"
bug_before="$(find "$bug_dir" -maxdepth 1 -type f -name 'report-*.json' | wc -l)"
ensure_toolbar
long_press_text "Scroll"
scroll_until_text "Create bug report"
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
long_press_text "Scroll"
scroll_until_text "Install/update over Tailscale"
tap_text "Install/update over Tailscale"
sleep 1.5
if has_window_focus; then
    echo "phone menu UI proof failed: install page button did not leave WEzterm for ACTION_VIEW" >&2
    exit 1
fi
echo "Install/update button launched external install surface"
reopen_wezterm
select_window "$proof_window"
wait_for_active_window_id "$proof_window"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"
ensure_toolbar

echo "phone menu UI proof: Copy/Paste buttons round-trip Android clipboard through tmux"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$COPY_FILE'; printf '%s\\n' '$COPY_SENTENCE'; cat > '$COPY_FILE'" Enter
wait_for_capture_text "$proof_window" "$COPY_SENTENCE"
ensure_toolbar
tap_text "Copy/Paste"
assert_text_any "Copy / Paste" "Copy/Paste"
tap_text "Copy visible terminal text"
sleep 0.8
ensure_toolbar
tap_text "Copy/Paste"
tap_text "Paste phone clipboard into terminal"
wait_for_file_text "$COPY_FILE" "$COPY_SENTENCE"
grep -Fq "$COPY_SENTENCE" "$COPY_FILE"
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Copy/Paste buttons round-tripped full multi-word text"

echo "phone menu UI proof: native composer owns phone typing"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$TYPE_FILE'; cat > '$TYPE_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "cat" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
before_tap_mode="$(tmux_pane_mode)"
before_tap_scroll="$(tmux_scroll_position)"
# WHY: v1.67 deliberately keeps v1.66's stop using xterm/WebView's hidden textarea for
# normal phone typing. This proof taps the terminal body, verifies the native
# composer owns input, proves the token does not reach tmux before Send, then
# counts the submitted token so same-line duplication still fails.
terminal_tap_for_typing
sleep 0.8
assert_regex '(text|content-desc|hint)="Type prompt"' "native composer opened from terminal tap"
echo "Native composer opened from terminal tap"
assert_absent "Cancel"
adb_cmd shell dumpsys input_method > /tmp/wezterm-ime-proof.txt || true
adb_cmd exec-out screencap -p > /tmp/wezterm-v166-native-composer-proof.png
adb_cmd shell input text "$TYPE_TOKEN"
sleep 0.5
if [ -f "$TYPE_FILE" ] && grep -Fq "$TYPE_TOKEN" "$TYPE_FILE"; then
    echo "phone menu UI proof failed: native composer leaked pre-send text into xterm" >&2
    cat "$TYPE_FILE" >&2 || true
    exit 1
fi
echo "Native composer did not leak pre-send text into xterm"
tap_text "Send"
wait_for_file_text "$TYPE_FILE" "$TYPE_TOKEN"
after_tap_mode="$(tmux_pane_mode)"
after_tap_scroll="$(tmux_scroll_position)"
token_occurrences="$(grep -Fo "$TYPE_TOKEN" "$TYPE_FILE" | wc -l | tr -d ' ')"
if [ "$token_occurrences" -ne 1 ]; then
    echo "phone menu UI proof failed: native composer token duplicated or missing in $TYPE_FILE; occurrences=$token_occurrences" >&2
    cat "$TYPE_FILE" >&2 || true
    exit 1
fi
if [ "$after_tap_mode" != "0" ] || { [ -n "$after_tap_scroll" ] && [ "$after_tap_scroll" != "0" ]; }; then
    echo "phone menu UI proof failed: native composer submit changed mode/scroll unexpectedly; before mode=$before_tap_mode scroll=$before_tap_scroll after mode=$after_tap_mode scroll=$after_tap_scroll" >&2
    exit 1
fi
grep -Fq 'TYPE_TEXT_VARIATION_NORMAL' "$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
if grep -Eq 'TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|IME_FLAG_NO_PERSONALIZED_LEARNING' "$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"; then
    echo "phone menu UI proof failed: private/password IME flags returned" >&2
    exit 1
fi
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Tap-to-type delivered one visible token through native composer"
echo "Native composer delivered one visible token; screenshot: /tmp/wezterm-v166-native-composer-proof.png; IME dump: /tmp/wezterm-ime-proof.txt"
echo "Tap-to-type did not trigger page-finished or scroll bursts"
echo "IME flags stayed normal for voice input"

echo "phone menu UI proof: Start sends Enter to the active pane"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$STOP_FILE'; cat > '$STOP_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "cat" ]; then
        break
    fi
    sleep 0.2
done
tmux send-keys -t "$TMUX_SESSION:$proof_window" -l "PHONE_START_BUTTON_OK"
ensure_toolbar
tap_text "Start"
wait_for_file_text "$STOP_FILE" "PHONE_START_BUTTON_OK"
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Start button sent Enter"

echo "phone menu UI proof: Stop sends Escape to the active pane"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$STOP_FILE'; python3 -c 'import sys,pathlib,termios,tty; fd=sys.stdin.fileno(); old=termios.tcgetattr(fd); tty.setraw(fd); data=sys.stdin.buffer.read(1); termios.tcsetattr(fd, termios.TCSADRAIN, old); pathlib.Path(\"$STOP_FILE\").write_bytes(data)'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "python3" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
tap_text "Stop"
wait_for_stop_escape
wait_for_shell "$proof_window"
echo "Stop button delivered Escape"

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
