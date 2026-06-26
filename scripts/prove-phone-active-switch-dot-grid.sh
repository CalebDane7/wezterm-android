#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/phone-proof-selection-lock.sh"

ADB_SERIAL="${ADB_SERIAL:-100.77.22.120:5555}"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
ACTIVITY="${WEZTERM_ACTIVITY:-$PACKAGE/.MainActivity}"
CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
DEFAULT_TARGET_TITLE="Phone Crash Restore"
TARGET_TITLE="${WEZTERM_DOT_GRID_TARGET_TITLE:-$DEFAULT_TARGET_TITLE}"
TARGET_WINDOW_ID="${WEZTERM_DOT_GRID_TARGET_WINDOW_ID:-}"
START_WINDOW_ID=""
CREATED_TARGET_WINDOW_ID=""
ALLOW_DISPOSABLE_TARGET="${WEZTERM_DOT_GRID_ALLOW_DISPOSABLE_TARGET:-0}"
STRICT_POST_SWITCH_FOCUS=0
WORK_DIR="${WEZTERM_PROOF_WORK_DIR:-/tmp}"
SCREENSHOT="${WEZTERM_DOT_GRID_SCREENSHOT:-$WORK_DIR/wezterm-active-switch-dot-grid-target.png}"
IMMEDIATE_SCREENSHOT="${WEZTERM_DOT_GRID_IMMEDIATE_SCREENSHOT:-$WORK_DIR/wezterm-active-switch-dot-grid-immediate.png}"
READABLE_SCREENSHOT="${WEZTERM_DOT_GRID_READABLE_SCREENSHOT:-$WORK_DIR/wezterm-active-switch-dot-grid-readable.png}"
PROOF_ID="${WEZTERM_DOT_GRID_PROOF_ID:-$$}"
ADB_SCREENSHOT_TIMEOUT_SECONDS="${ADB_SCREENSHOT_TIMEOUT_SECONDS:-20}"
ADB_UI_DUMP_TIMEOUT_SECONDS="${ADB_UI_DUMP_TIMEOUT_SECONDS:-8}"
ADB_PULL_TIMEOUT_SECONDS="${ADB_PULL_TIMEOUT_SECONDS:-8}"
REMOTE_XML="/sdcard/wezterm-active-switch-dot-grid-${PROOF_ID}.xml"
LOCAL_XML="$WORK_DIR/wezterm-active-switch-dot-grid-${PROOF_ID}.xml"

adb_cmd() {
    adb -s "$ADB_SERIAL" "$@"
}

fail() {
    echo "active-switch dot-grid proof failed: $*" >&2
    exit 1
}

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
}

control_get() {
    phone_proof_curl -fsS "$CONTROL_URL$1"
}

cleanup_disposable_target() {
    if [ -n "$CREATED_TARGET_WINDOW_ID" ]; then
        tmux kill-window -t "$CREATED_TARGET_WINDOW_ID" >/dev/null 2>&1 || true
    fi
}
trap cleanup_disposable_target EXIT

active_window_name() {
    tmux display-message -p -t "$TMUX_SESSION:" '#{window_name}'
}

active_window_id() {
    control_get "/active" | python3 -c 'import json,sys; print((json.load(sys.stdin).get("window") or {}).get("windowId") or "")'
}

create_disposable_active_switch_target() {
    local title="WEzTerm Dot Proof ${PROOF_ID}"
    local created
    # WHY: disposable targets are opt-in only. They are useful for checking the
    # detector itself, but they are not proof of the user's real Active Sessions
    # regression. The normal path must fail when the requested live row is
    # missing so a clean fake tab cannot hide a broken user session switch.
    created="$(tmux new-window -d -P -F '#{window_id}|#{window_name}' \
        -t "$TMUX_SESSION:" \
        -n "$title")" || fail "could not create disposable dot-grid proof target"
    CREATED_TARGET_WINDOW_ID="${created%%|*}"
    TARGET_WINDOW_ID="$CREATED_TARGET_WINDOW_ID"
    tmux send-keys -t "$CREATED_TARGET_WINDOW_ID" \
        "printf 'WEZTERM DOT PROOF CLEAN TARGET\\nactive switch should show Start toolbar and no dotted tail\\n'" C-m
    tmux rename-window -t "$CREATED_TARGET_WINDOW_ID" "$title" >/dev/null 2>&1 || true
    sleep 0.6
    TARGET_TITLE="$title"
    echo "created explicit disposable clean target '$TARGET_TITLE' ($TARGET_WINDOW_ID)" >&2
}

resolve_active_switch_target() {
    local tabs_json resolved
    tabs_json="$(control_get "/tabs?light=1")" || fail "could not read live tabs from control server"
    resolved="$(python3 - "$TARGET_TITLE" "$TARGET_WINDOW_ID" "$tabs_json" <<'PY'
import json
import sys

requested = sys.argv[1]
requested_window_id = sys.argv[2]
payload = json.loads(sys.argv[3])
windows = payload.get("windows") or []
if requested_window_id:
    for window in windows:
        window_id = window.get("windowId") or ""
        if window_id == requested_window_id:
            title = window.get("title") or window.get("name") or requested_window_id
            print(f"{title}\t{window_id}\t{bool(window.get('active'))}")
            sys.exit(0)
    sys.exit(1)
for window in windows:
    title = window.get("title") or window.get("name") or ""
    if title == requested:
        print(f"{title}\t{window.get('windowId') or ''}\t{bool(window.get('active'))}")
        sys.exit(0)
sys.exit(1)
PY
)" || {
        if [ "$ALLOW_DISPOSABLE_TARGET" = "1" ]; then
            echo "target '$TARGET_TITLE' not present in live /tabs; disposable fallback explicitly allowed" >&2
            create_disposable_active_switch_target
            return
        fi
        fail "requested Active Sessions target not present in /tabs?light=1: '$TARGET_TITLE'"
    }
    TARGET_TITLE="${resolved%%$'\t'*}"
    resolved="${resolved#*$'\t'}"
    TARGET_WINDOW_ID="${resolved%%$'\t'*}"
    [ -n "$TARGET_WINDOW_ID" ] || fail "target '$TARGET_TITLE' did not include a stable windowId"
}

wait_for_active_window_id() {
    local expected="$1"
    local context="$2"
    local got=""
    for _ in $(seq 1 30); do
        got="$(active_window_id 2>/dev/null || true)"
        if [ "$got" = "$expected" ]; then
            return 0
        fi
        sleep 0.2
    done
    fail "expected active windowId $expected after $context, got ${got:-<none>}"
}

assert_active_switch_setup() {
    START_WINDOW_ID="$(active_window_id 2>/dev/null || true)"
    [ -n "$START_WINDOW_ID" ] || fail "could not read starting active windowId before Active Sessions proof"
    if [ "$START_WINDOW_ID" = "$TARGET_WINDOW_ID" ]; then
        fail "target '$TARGET_TITLE' ($TARGET_WINDOW_ID) is still active before visible Active Sessions switch"
    fi
}

assert_selected_target_is_active() {
    # WHY: the user complaint is about changing Active Sessions. A clean screenshot alone is not proof:
    # a swallowed tap, stale dialog row, duplicate
    # title, or wrong-row tap can leave the old session active while the detector
    # still passes. Pin the proof to the live @windowId selected from
    # /tabs?light=1, then require /active to report that same id after the
    # visible Android row tap.
    require_window_focus "checking active window after Active Sessions target tap"
    wait_for_active_window_id "$TARGET_WINDOW_ID" "visible Active Sessions switch to '$TARGET_TITLE'"
    require_window_focus "after Active Sessions target became active"
}

assert_no_ime_visible() {
    if adb_cmd shell dumpsys input_method | grep -Eq 'mInputShown=true|mImeWindowVis=[^0]'; then
        adb_cmd shell dumpsys input_method | tail -80 >&2 || true
        fail "Android IME/keyboard visible after passive Active Sessions switch"
    fi
}

has_window_focus() {
    adb_cmd shell dumpsys window | grep -Eq "mCurrentFocus=.*$PACKAGE|mFocusedApp=.*$PACKAGE"
}

require_window_focus() {
    local action="$1"
    if has_window_focus; then
        return 0
    fi
    # WHY: the user's real phone can jump to WhatsApp, ChatGPT, or another app
    # while this proof is waiting on Android UIAutomator. Do not tap stale
    # coordinates after focus leaves WEzTerm; that is how proof automation can
    # create the wrong-app taps and random-session behavior it is supposed to
    # prevent.
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
    fail "WEzTerm lost focus before $action"
}

ensure_window_focus_for_setup() {
    local action="$1"
    if has_window_focus; then
        return 0
    fi
    # WHY: pre-switch setup can safely bring WEzTerm back before tapping the
    # toolbar. The strict post-switch phase still fails on focus loss so Maps,
    # chats, or other apps cannot hide a broken Active Sessions result.
    wake_and_dismiss_overlays
    adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
    wait_for_focus
    require_window_focus "$action"
}

wake_and_dismiss_overlays() {
    # WHY: Samsung can leave WEzterm as the focused app record while keyguard or
    # NotificationShade owns the actual window. UIAutomator then dumps SystemUI
    # instead of the toolbar and the proof fails before testing the dotted canvas.
    adb_cmd shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
    adb_cmd shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_cmd shell cmd statusbar collapse >/dev/null 2>&1 || true
    adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
    # WHY: this proof runs on the same phone the user rotates while working.
    # A hard-coded portrait unlock swipe can land outside the landscape app
    # bounds and scroll the terminal behind the proof, leaving tmux in `[old]`
    # history mode before the Active-switch check even starts.
    adb_cmd shell input swipe 540 1200 540 260 120 >/dev/null 2>&1 || true
    sleep 0.35
}

wait_for_focus() {
    for _ in $(seq 1 20); do
        if has_window_focus; then
            return 0
        fi
        wake_and_dismiss_overlays
        adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        sleep 0.25
    done
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mDreamingLockscreen' >&2 || true
    fail "$PACKAGE did not become the focused app"
}

prepare_non_target_start() {
    if [ "$(active_window_id)" != "$TARGET_WINDOW_ID" ]; then
        return 0
    fi
    local other
    other="$(tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}|#{window_name}' \
        | awk -F'|' -v target="$TARGET_WINDOW_ID" '$1 != target {print $1; exit}')"
    [ -n "$other" ] || fail "target '$TARGET_TITLE' is the only tmux window; cannot prove a real Active switch"
    # WHY: this is proof setup, not the proof action. If the user-reported target
    # is already current, tapping its "Current" row does not dismiss the Active
    # Sessions dialog and the old harness times out before screenshot analysis.
    # Park on a different real window first, then switch back through the
    # visible Android Active picker so the tested path is still the user's path.
    control_get "/select-live?fast=1&windowId=$(urlencode "$other")" >/dev/null
    wait_for_active_window_id "$other" "setup parking away from '$TARGET_TITLE'"
    sleep 0.8
}

dump_ui() {
    if ! has_window_focus; then
        if [ "$STRICT_POST_SWITCH_FOCUS" = "1" ]; then
            require_window_focus "dumping UI during post-switch proof"
        fi
        wake_and_dismiss_overlays
        adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
        wait_for_focus
    fi
    local attempt
    rm -f "$LOCAL_XML"
    for attempt in 1 2 3; do
        if timeout "${ADB_UI_DUMP_TIMEOUT_SECONDS}s" adb -s "$ADB_SERIAL" shell uiautomator dump "$REMOTE_XML" >/dev/null \
                && timeout "${ADB_PULL_TIMEOUT_SECONDS}s" adb -s "$ADB_SERIAL" pull "$REMOTE_XML" "$LOCAL_XML" >/dev/null \
                && [ -s "$LOCAL_XML" ]; then
            break
        fi
        rm -f "$LOCAL_XML"
        wake_and_dismiss_overlays
        sleep 0.35
    done
    [ -s "$LOCAL_XML" ] || fail "UIAutomator dump timed out or UIAutomator dump pull timed out after retries: $REMOTE_XML -> $LOCAL_XML"
}

capture_screenshot() {
    local path="$1"
    # WHY: no-USB ADB can occasionally stall on `exec-out screencap`; the visual
    # proof should fail fast and leave the phone usable instead of hanging or
    # being killed before it reaches the dot-grid analysis.
    if ! timeout "${ADB_SCREENSHOT_TIMEOUT_SECONDS}s" adb -s "$ADB_SERIAL" exec-out screencap -p > "$path"; then
        fail "screenshot capture timed out or failed: $path"
    fi
    [ -s "$path" ] || fail "empty screenshot: $path"
}

text_center() {
    local text="$1"
    python3 - "$LOCAL_XML" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, target = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
target_lower = target.lower()
for node in root.iter("node"):
    values = [node.attrib.get("text", ""), node.attrib.get("content-desc", "")]
    if not any(target_lower in value.lower() for value in values):
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
sys.exit(1)
PY
}

button_center() {
    local text="$1"
    python3 - "$LOCAL_XML" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, target = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("class", "") != "android.widget.Button":
        continue
    if node.attrib.get("text", "") != target:
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
sys.exit(1)
PY
}

dump_has_text() {
    local text="$1"
    python3 - "$LOCAL_XML" "$text" <<'PY'
import sys
import xml.etree.ElementTree as ET

xml_path, target = sys.argv[1], sys.argv[2].lower()
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if target in node.attrib.get("text", "").lower() or target in node.attrib.get("content-desc", "").lower():
        sys.exit(0)
sys.exit(1)
PY
}

dump_has_dialog_title() {
    local text="$1"
    python3 - "$LOCAL_XML" "$text" <<'PY'
import sys
import xml.etree.ElementTree as ET

xml_path, target = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("class", "") == "android.widget.TextView" and node.attrib.get("text", "") == target:
        sys.exit(0)
sys.exit(1)
PY
}

dump_has_exact_button_text() {
    local text="$1"
    python3 - "$LOCAL_XML" "$text" <<'PY'
import sys
import xml.etree.ElementTree as ET

xml_path, target = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    if node.attrib.get("class", "") == "android.widget.Button" and node.attrib.get("text", "") == target:
        sys.exit(0)
sys.exit(1)
PY
}

button_center_from_current_dump() {
    local text="$1"
    button_center "$text"
}

tap_button_text() {
    local text="$1"
    local center
    for _ in $(seq 1 8); do
        ensure_window_focus_for_setup "tapping button '$text'"
        dump_ui
        if center="$(button_center_from_current_dump "$text" 2>/dev/null)"; then
            ensure_window_focus_for_setup "tapping button '$text'"
            adb_cmd shell input tap $center
            return 0
        fi
        sleep 0.25
    done
    fail "button not visible: $text"
}

tap_text() {
    local text="$1"
    ensure_window_focus_for_setup "tapping text '$text'"
    dump_ui
    local center
    center="$(text_center "$text")" || fail "text not visible: $text"
    ensure_window_focus_for_setup "tapping text '$text'"
    adb_cmd shell input tap $center
}

dialog_scroll_swipe_points() {
    local direction="$1"
    python3 - "$LOCAL_XML" "$direction" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, direction = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()

def bounds(node):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        return None
    return tuple(map(int, match.groups()))

scroll_views = [
    b for node in root.iter("node")
    if node.attrib.get("class") == "android.widget.ScrollView"
    for b in [bounds(node)]
    if b
]
if scroll_views:
    x1, y1, x2, y2 = max(scroll_views, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
else:
    root_bounds = bounds(root)
    if not root_bounds:
        # WHY: UIAutomator's top-level <hierarchy> node has no bounds on some
        # Samsung dumps. At this point the proof has already required the exact
        # Active Sessions dialog title, so use the largest bounded child instead
        # of failing before the picker can scroll to the real target row.
        child_bounds = [
            b for node in root.iter("node")
            for b in [bounds(node)]
            if b
        ]
        if not child_bounds:
            raise SystemExit(1)
        root_bounds = max(child_bounds, key=lambda b: (b[2] - b[0]) * (b[3] - b[1]))
    x1, y1, x2, y2 = root_bounds

width = max(1, x2 - x1)
height = max(1, y2 - y1)
x = x1 + width // 2
upper = y1 + max(12, height // 5)
lower = y2 - max(12, height // 5)
if lower <= upper:
    upper = y1 + height // 3
    lower = y1 + (height * 2) // 3

# WHY: swipe inside the current dialog bounds, not at fixed portrait
# coordinates. In landscape, old `540 2450` swipes missed the dialog and
# scrolled the underlying terminal into tmux history, masking the real bug.
if direction == "toward-top":
    print(x, upper, x, lower)
else:
    print(x, lower, x, upper)
PY
}

wait_for_text() {
    local text="$1"
    for _ in $(seq 1 30); do
        dump_ui
        if dump_has_text "$text"; then
            return 0
        fi
        sleep 0.25
    done
    fail "timed out waiting for visible text: $text"
}

wait_for_dialog_title() {
    local text="$1"
    for _ in $(seq 1 30); do
        dump_ui
        if dump_has_dialog_title "$text"; then
            return 0
        fi
        sleep 0.25
    done
    fail "timed out waiting for dialog title: $text"
}

ensure_plain_toolbar() {
    # WHY: this proof must exercise the same normal Active-session path the user
    # taps, not a composer/keyboard state. Use exact toolbar Button nodes here:
    # fuzzy text matching can see "Start" inside terminal output while the real
    # toolbar is still in the native-composer "Send" state. Never tap Send; Back
    # and Escape preserve the draft while returning the toolbar to Start.
    for _ in $(seq 1 12); do
        dump_ui
        if dump_has_exact_button_text "Active" \
                && dump_has_exact_button_text "Start" \
                && ! dump_has_exact_button_text "Send"; then
            return 0
        fi
        adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
        adb_cmd shell input keyevent BACK >/dev/null
        sleep 0.55
    done
    dump_ui
    dump_has_exact_button_text "Active" || fail "Active toolbar button not visible"
    dump_has_exact_button_text "Start" || fail "Start toolbar button not visible"
    if dump_has_exact_button_text "Send"; then
        fail "native composer still visible before Active switch proof"
    fi
}

select_active_target() {
    tap_button_text "Active"
    wait_for_dialog_title "Active Sessions"
    # WHY: v1.81 passed by proving the wrong active row. This proof scrolls the
    # real Active Sessions picker until the user-reported tab is visible, then
    # taps that exact title through Android UIAutomator. The picker pins the
    # current tab first, so the target can be either above or below the initially
    # visible rows depending on which session was active before proof. Search both
    # directions; a one-way swipe let the proof miss real `/tabs` entries and
    # tempted agents to weaken the visual check instead of fixing the harness.
    local direction label attempt swipe_points
    for direction in "toward-top" "toward-bottom"; do
        label="toward-top"
        [ "$direction" = "toward-bottom" ] && label="toward-bottom"
        for attempt in $(seq 0 8); do
            dump_ui
            if dump_has_text "$TARGET_TITLE"; then
                local center
                center="$(text_center "$TARGET_TITLE")" || fail "target became invisible: $TARGET_TITLE"
                require_window_focus "tapping Active Sessions target '$TARGET_TITLE'"
                adb_cmd shell input tap $center
                return 0
            fi
            echo "target '$TARGET_TITLE' not visible yet; scrolling Active Sessions picker $label ($attempt)"
            swipe_points="$(dialog_scroll_swipe_points "$direction")" \
                || fail "could not calculate Active Sessions dialog scroll bounds"
            require_window_focus "scrolling Active Sessions picker for '$TARGET_TITLE'"
            adb_cmd shell input swipe $swipe_points 420 >/dev/null
            sleep 0.35
        done
    done
    fail "could not find target Active Session: $TARGET_TITLE"
}

assert_no_terminal_dot_grid() {
    local screenshot="$1"
    python3 - "$screenshot" "$TARGET_TITLE" <<'PY'
from PIL import Image
import sys

path, target = sys.argv[1], sys.argv[2]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: the regression is not "blank terminal" and not tmux text. The failing
# Phone Crash Restore screenshot showed valid terminal rows above a huge field
# of evenly spaced bright dots in blank canvas cells. Count both thin full-width
# bands and the newer v1.86 repeated short-column dot grid so proof cannot pass
# just because other text is visible somewhere on screen. Repeated columns are
# reported as telemetry, not a standalone failure: real monospaced code also
# creates repeated columns. The user-visible failure is repeated columns paired
# with many narrow blank-tail bands below real text.
left, top, right, bottom = int(width * 0.01), int(height * 0.14), int(width * 0.99), int(height * 0.80)
rows = []
for y in range(top, bottom):
    xs = []
    for x in range(left, right, 3):
        r, g, b = image.getpixel((x, y))
        if r > 115 and g > 115 and b > 115 and max(r, g, b) - min(r, g, b) < 85:
            xs.append(x)
    if len(xs) >= 60 and (max(xs) - min(xs) if xs else 0) > width * 0.75:
        rows.append((y, xs))
bands = []
for y, _ in rows:
    if not bands or y - bands[-1][1] > 3:
        bands.append([y, y])
    else:
        bands[-1][1] = y
narrow_bands = sum(1 for start, end in bands if end - start <= 4)
columns = []
stride = max(1, len(rows) // 80)
for index, (_, xs) in enumerate(rows):
    if index % stride == 0:
        columns.extend(xs)
clusters = []
for x in sorted(columns):
    if not clusters or x - clusters[-1][1] > 4:
        clusters.append([x, x, 1])
    else:
        clusters[-1][1] = x
        clusters[-1][2] += 1
repeated_columns = sum(1 for _, _, count in clusters if count >= 8)
tail_top, tail_bottom = int(height * 0.78), int(height * 0.84)
tail_rows = []
for y in range(tail_top, tail_bottom):
    xs = []
    for x in range(left, right, 2):
        r, g, b = image.getpixel((x, y))
        if r > 110 and g > 110 and b > 110 and max(r, g, b) - min(r, g, b) < 80:
            xs.append(x)
    if len(xs) >= 45 and (max(xs) - min(xs) if xs else 0) > width * 0.65:
        tail_rows.append((y, xs))
tail_bands = []
for y, _ in tail_rows:
    if not tail_bands or y - tail_bands[-1][1] > 3:
        tail_bands.append([y, y])
    else:
        tail_bands[-1][1] = y
tail_narrow_bands = sum(1 for start, end in tail_bands if end - start <= 4)
# WHY: the user-visible APK failure sat below the last prompt and immediately
# above the toolbar. The old detector cropped at 80% screen height, missed that
# lower tail, and falsely passed the known-bad v1.98 screenshot. A healthy
# live-bottom view has real text or a continuous toolbar/control band here, not
# several isolated full-width one/two-pixel dot rows.
lower_tail_dot_grid = tail_narrow_bands >= 3 and len(tail_rows) <= 20
full_view_dot_grid = len(rows) >= 180 and (narrow_bands >= 20 or repeated_columns >= 60)
if (
    lower_tail_dot_grid
    or full_view_dot_grid
    or narrow_bands >= 35
    or (len(rows) >= 90 and narrow_bands >= 18)
    or (len(rows) >= 100 and narrow_bands >= 12 and repeated_columns >= 40)
):
    raise SystemExit(
        f"terminal dotted canvas grid detected "
        f"(rows={len(rows)}, narrowBands={narrow_bands}, repeatedCols={repeated_columns}, "
        f"tailRows={len(tail_rows)}, tailNarrowBands={tail_narrow_bands}, "
        f"fullViewDotGrid={full_view_dot_grid}) "
        f"after selecting {target}: {path}"
    )
print(
    f"terminal dotted canvas grid absent after selecting {target}: "
    f"rows={len(rows)} narrowBands={narrow_bands} repeatedCols={repeated_columns} "
    f"tailRows={len(tail_rows)} tailNarrowBands={tail_narrow_bands} "
    f"fullViewDotGrid={full_view_dot_grid}; "
    "repeatedCols is telemetry unless blank-tail/tail narrowBands also rise"
)
PY
}

assert_no_large_black_terminal_mask() {
    local screenshot="$1"
    python3 - "$screenshot" "$TARGET_TITLE" <<'PY'
from PIL import Image
import sys

path, target = sys.argv[1], sys.argv[2]
image = Image.open(path).convert("RGB")
width, height = image.size

def band_stats(top_ratio, bottom_ratio, step=4):
    left, right = int(width * 0.02), int(width * 0.98)
    top, bottom = int(height * top_ratio), int(height * bottom_ratio)
    total = black = bright = contrast = 0
    for y in range(top, bottom, step):
        for x in range(left, right, step):
            r, g, b = image.getpixel((x, y))
            total += 1
            if r < 25 and g < 25 and b < 25:
                black += 1
            if max(r, g, b) > 90:
                bright += 1
            if max(r, g, b) - min(r, g, b) > 40 or max(r, g, b) > 60:
                contrast += 1
    if total == 0:
        return (0, 0, 0, 0, 0)
    return (
        total,
        black / total,
        bright / total,
        contrast / total,
        (bright + contrast) / total,
    )

upper = band_stats(0.08, 0.42)
middle = band_stats(0.42, 0.62)
lower = band_stats(0.62, 0.82)
upper_has_text = upper[4] >= 0.018
middle_is_black_mask = middle[1] >= 0.985 and middle[4] <= 0.003
lower_is_black_mask = lower[1] >= 0.90 and lower[4] <= 0.006
blank_terminal_body = middle[1] >= 0.985 and lower[1] >= 0.985 and upper[4] <= 0.006 and lower[4] <= 0.006

# WHY: the 2026-06-17 user screenshot was not a dotted grid anymore; it was
# readable text at the top with a huge lower terminal region painted black. That
# is still a failed Active/Bottom render because covering a dotted tail with a black lower rectangle is not a live-bottom render.
# Require the proof to reject that false-green shape separately from all-dot detection.
# WHY: v2.81 briefly passed this proof with an almost entirely black terminal
# body plus a tiny side scrollbar/cursor signal. That screenshot still matched
# the user's complaint: the selected tab strip was alive, but the terminal
# content was not bottom-left readable. Reject both the old readable-top/black-
# bottom mask and the newer blank-terminal-body false positive.
if (upper_has_text and middle_is_black_mask and lower_is_black_mask) or blank_terminal_body:
    raise SystemExit(
        "large black terminal mask detected after selecting "
        f"{target}: upperSignal={upper[4]:.4f} middleBlack={middle[1]:.4f} "
        f"middleSignal={middle[4]:.4f} lowerBlack={lower[1]:.4f} "
        f"lowerSignal={lower[4]:.4f} screenshot={path}"
    )
print(
    "large black terminal mask absent after selecting "
    f"{target}: upperSignal={upper[4]:.4f} middleBlack={middle[1]:.4f} "
    f"middleSignal={middle[4]:.4f} lowerBlack={lower[1]:.4f} "
    f"lowerSignal={lower[4]:.4f}"
)
PY
}

assert_terminal_has_visible_paint() {
    local screenshot="$1"
    python3 - "$screenshot" <<'PY'
from collections import Counter
from PIL import Image
import sys

path = sys.argv[1]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: "no dots" is not enough. The latest screenshot complaint also included an
# all-black bottom after switching/Bottom, and a fully black WebView would have
# zero dot bands. Require visible xterm paint/cursor pixels in the terminal area
# while keeping the threshold low enough for an idle live-bottom prompt. Stream a
# bounded pixel sample instead of materializing a full crop pixel list; the
# phone proof runs over no-USB ADB while other agents are active, and a huge
# Python tuple list was enough to make earlier v1.97 proof runs die with 137.
left, top, right, bottom = int(width * 0.03), int(height * 0.04), int(width * 0.97), int(height * 0.82)
step = 3
background_counts = Counter()
sample_count = 0
for y in range(top, bottom, step):
    for x in range(left, right, step):
        background_counts[image.getpixel((x, y))] += 1
        sample_count += 1
background = background_counts.most_common(1)[0][0]
contrast = 0
for y in range(top, bottom, step):
    for x in range(left, right, step):
        r, g, b = image.getpixel((x, y))
        if abs(r - background[0]) + abs(g - background[1]) + abs(b - background[2]) >= 18:
            contrast += 1
required = max(60, min(500, int(sample_count * 0.0015)))
if contrast < required:
    raise SystemExit(f"terminal screenshot has too little visible xterm paint ({contrast}/{required}); possible blank WebView: {path}")
print(f"terminal screenshot visible xterm paint: {contrast}/{required} sampled={sample_count}")
PY
}

assert_toolbar_after_switch() {
    require_window_focus "checking toolbar after Active Sessions switch"
    wait_for_text "Active"
    dump_ui
    dump_has_exact_button_text "Active" || fail "Active toolbar missing after target switch"
    # WHY: v2.08 keeps the v2.06 Bottom-core dotted-tail repair, but tab-open
    # is passive navigation now. Letting `Send` pass here would allow the exact
    # user-reported regression where a tab switch auto-opens the composer/IME and
    # leaves Backspace unable to edit text that already reached tmux.
    dump_has_exact_button_text "Start" || fail "passive tab switch did not return to the Start toolbar state"
    if dump_has_exact_button_text "Send"; then
        fail "passive tab switch opened the native composer/Send state"
    fi
    assert_no_ime_visible
}

main() {
    if [ "${WEZTERM_DOT_GRID_ANALYZE_ONLY:-0}" = "1" ]; then
        # WHY: foreground phone state can change after a real Active-switch
        # screenshot is captured (for example an incoming call/chat steals focus).
        # Keep a detector-only path so the same screenshot can be rechecked after
        # harness fixes without re-driving the phone or weakening the live proof.
        [ -s "$SCREENSHOT" ] || fail "empty screenshot for analyze-only mode: $SCREENSHOT"
        assert_terminal_has_visible_paint "$SCREENSHOT"
        assert_no_terminal_dot_grid "$SCREENSHOT"
        assert_no_large_black_terminal_mask "$SCREENSHOT"
        echo "active-switch dot-grid screenshot analysis passed for '$TARGET_TITLE'"
        echo "screenshot: $SCREENSHOT"
        return 0
    fi

    phone_proof_require_selection_locks "active-switch dot-grid proof"
    adb_cmd get-state >/dev/null
    adb_cmd shell input keyevent WAKEUP >/dev/null || true
    wake_and_dismiss_overlays
    resolve_active_switch_target
    prepare_non_target_start
    assert_active_switch_setup
    adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || adb_cmd shell monkey -p "$PACKAGE" 1 >/dev/null
    sleep 1.0
    wait_for_focus
    ensure_plain_toolbar
    select_active_target
    STRICT_POST_SWITCH_FOCUS=1
    assert_selected_target_is_active
    # WHY: the user-visible regression is the entry moment after tapping an
    # Active Sessions row, not only the eventual settled state. v2.22 keeps the
    # native full-frame shield below a visible-black threshold; the immediate
    # proof still forbids dot grids, then the short readable proof requires real
    # terminal paint so the old all-black switch cannot pass. The final settled
    # proof also still requires visible terminal paint so a permanently blank WebView cannot pass.
    sleep 0.15
    assert_toolbar_after_switch
    capture_screenshot "$IMMEDIATE_SCREENSHOT"
    assert_terminal_has_visible_paint "$IMMEDIATE_SCREENSHOT"
    assert_no_terminal_dot_grid "$IMMEDIATE_SCREENSHOT"
    assert_no_large_black_terminal_mask "$IMMEDIATE_SCREENSHOT"
    sleep 0.55
    assert_toolbar_after_switch
    capture_screenshot "$READABLE_SCREENSHOT"
    assert_terminal_has_visible_paint "$READABLE_SCREENSHOT"
    assert_no_terminal_dot_grid "$READABLE_SCREENSHOT"
    assert_no_large_black_terminal_mask "$READABLE_SCREENSHOT"
    sleep 1.45
    assert_toolbar_after_switch
    capture_screenshot "$SCREENSHOT"
    assert_terminal_has_visible_paint "$SCREENSHOT"
    assert_no_terminal_dot_grid "$SCREENSHOT"
    assert_no_large_black_terminal_mask "$SCREENSHOT"
    echo "active-switch dot-grid proof passed for '$TARGET_TITLE'"
    echo "immediate screenshot: $IMMEDIATE_SCREENSHOT"
    echo "readable-shortly-after-switch screenshot: $READABLE_SCREENSHOT"
    echo "screenshot: $SCREENSHOT"
}

main "$@"
