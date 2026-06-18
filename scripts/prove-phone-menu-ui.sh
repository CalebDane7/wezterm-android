#!/usr/bin/env bash
set -euo pipefail
trap 'echo "phone menu UI proof failed at line $LINENO while running: $BASH_COMMAND" >&2' ERR

ADB_SERIAL="${ADB_SERIAL:-127.0.0.1:5556}"
ADB_TIMEOUT_SECONDS="${ADB_TIMEOUT_SECONDS:-20}"
CONTROL_URL="${PHONE_CONTROL_URL:-http://100.113.254.7:8089}"
TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
ACTIVITY="${WEZTERM_ACTIVITY:-com.kaleeb.wezterm/.MainActivity}"
EXPECTED_VERSION_NAME="${EXPECTED_VERSION_NAME:-$(grep -o 'android:versionName="[^"]*"' "$MANIFEST" | head -n 1 | cut -d'"' -f2)}"
EXPECTED_VERSION_CODE="${EXPECTED_VERSION_CODE:-$(grep -o 'android:versionCode="[0-9]*"' "$MANIFEST" | head -n 1 | cut -d'"' -f2)}"
DUMP_REMOTE="${WEZTERM_UI_DUMP_REMOTE:-/sdcard/wezterm-window-$$.xml}"
DUMP_LOCAL="${WEZTERM_UI_DUMP_LOCAL:-/tmp/wezterm-window-$$.xml}"
SCREENSHOT_DIR="${WEZTERM_SCREENSHOT_DIR:-/tmp}"
OLD_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-old-sessions.png"
BUTTON_SCREENSHOT="$SCREENSHOT_DIR/wezterm-v151-button-proof.png"
PROOF_NAME="WEzterm UI Button Proof"
CLI_KEYS_TITLE="Phone Keys Test"
COPY_FILE="/tmp/wezterm-ui-copy-paste-proof.$$"
STOP_FILE="/tmp/wezterm-ui-stop-proof.$$"
COPY_TOKEN="PHONE_UI_COPY_SOURCE_$(date +%s)_$$"
COPY_SENTENCE="PHONE UI COPY FULL TEXT OK $(date +%s) $$"
TYPE_FILE="/tmp/wezterm-ui-keyboard-proof.$$"
TYPE_TOKEN="PHONE_UI_TYPE_$(date +%s)_$$"
TYPE_BAD_SUFFIX="BAD"
DRIFT_FILE="/tmp/wezterm-ui-draft-target-proof.$$"
DRIFT_OTHER_FILE="/tmp/wezterm-ui-draft-target-other.$$"
DRIFT_TOKEN="PHONE_UI_DRAFT_TARGET_$(date +%s)_$$"
REFRESH_TOKEN="PHONE_UI_REFRESH_$(date +%s)_$$"
CLI_KEYS_FILE="/tmp/wezterm-ui-cli-keys-proof.$$"
CLI_KEYS_SCRIPT="/tmp/wezterm-ui-cli-picker-proof.$$.py"
STOP_VISIBLE_DRAFT_TOKEN="PHONE_UI_STOP_DRAFT_$(date +%s)_$$"

orig_window=""
orig_mode=""
orig_scroll=""
proof_window=""
drift_window=""
cli_keys_window=""
resume_window=""
reader_window=""
reader_source_window=""
initial_windows_file=""

adb_cmd() {
    adb -s "$ADB_SERIAL" "$@"
}

assert_installed_package_version() {
    local package_dump="/tmp/wezterm-menu-ui-package-proof.txt"
    # WHY: this proof exercises the real installed APK, so it must reject stale
    # phone builds. Otherwise a source-only v2.09 guard can pass while the phone
    # is still running the older build that duplicated typing or opened Send on
    # tab switches.
    if ! timeout "${ADB_TIMEOUT_SECONDS}s" adb -s "$ADB_SERIAL" shell dumpsys package "$PACKAGE" > "$package_dump"; then
        echo "phone menu UI proof blocked: ADB package proof timed out after ${ADB_TIMEOUT_SECONDS}s" >&2
        exit 3
    fi
    if ! grep -Fq "versionCode=$EXPECTED_VERSION_CODE" "$package_dump" \
            || ! grep -Fq "versionName=$EXPECTED_VERSION_NAME" "$package_dump"; then
        echo "phone menu UI proof failed: installed $PACKAGE does not match source version ${EXPECTED_VERSION_NAME}/${EXPECTED_VERSION_CODE}" >&2
        grep -E 'versionCode=|versionName=' "$package_dump" >&2 || true
        exit 1
    fi
    echo "Installed APK version matches source: $EXPECTED_VERSION_NAME ($EXPECTED_VERSION_CODE)"
}

adb_pull_dump() {
    local timeout_seconds="${WEZTERM_ADB_PULL_TIMEOUT_SECONDS:-8}"
    # WHY: real-phone UIAutomator sometimes writes the XML successfully but the
    # following `adb pull` hangs indefinitely. Bound the pull so this proof lane
    # fails with retry diagnostics instead of freezing while the APK bug remains
    # unproven.
    timeout "${timeout_seconds}s" adb -s "$ADB_SERIAL" pull "$DUMP_REMOTE" "$DUMP_LOCAL"
}

urlencode() {
    python3 -c 'from urllib.parse import quote; import sys; print(quote(sys.argv[1], safe=""))' "$1"
}

control_get() {
    curl -fsS "$CONTROL_URL$1"
}

assert_title_sync_stopped() {
    local pids
    pids="$(pgrep -f '/home/cabule/phone-title-sync/main.py' || true)"
    if [ -n "$pids" ]; then
        echo "phone menu UI proof blocked: phone-title-sync is running: $pids" >&2
        echo "WHY: proof must not run while another process can rename real tmux tabs and hide session-title regressions." >&2
        exit 2
    fi
}

assert_exclusive_phone_proof() {
    if [ "${WEZTERM_UI_EXCLUSIVE_PHONE_PROOF:-0}" != "1" ]; then
        echo "phone menu UI proof blocked: WEZTERM_UI_EXCLUSIVE_PHONE_PROOF=1 is required." >&2
        echo "WHY: this broad proof taps, types, switches tabs, presses Bottom, and creates proof windows. Running it against the user's live phone while they are typing can reproduce the exact random tab switch, wrong paste, dots, and duplicate-text regressions it is supposed to catch. Use narrow/non-mutating proofs unless the phone is explicitly reserved for proof." >&2
        exit 2
    fi
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

tmux_window_pane_height() {
    local window_id="$1"
    tmux display-message -p -t "$TMUX_SESSION:$window_id" '#{pane_height}' 2>/dev/null || true
}

tmux_window_count() {
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}' | wc -l
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
        if [ "$count" -gt "$before_count" ] \
            && [ -n "$active" ] \
            && [ "$active" != "$forbidden_window" ] \
            && ! window_was_initial "$active"; then
            printf '%s\n' "$active"
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: no new active tmux window appeared" >&2
    exit 1
}

wait_for_reader_window() {
    local source_window="$1"
    local before_count="$2"
    for _ in $(seq 1 80); do
        local row window_id name count
        count="$(tmux_window_count)"
        while IFS='|' read -r window_id name; do
            [ -n "$window_id" ] || continue
            if [ "$count" -gt "$before_count" ] \
                && ! window_was_initial "$window_id" \
                && [[ "$name" == "READ $source_window"* ]]; then
                # WHY: the reader action creates a tmux window, then the Android
                # proof watches the phone view catch up. On the real phone the
                # READ window can exist before the grouped view reports it as active,
                # so wait for the exact stable READ @source window and
                # select it if activation lags instead of leaving a reader tab
                # behind and falsely failing "no new active window".
                if [ "$(tmux_active_window)" != "$window_id" ]; then
                    control_get "/select?fast=1&windowId=${window_id//@/%40}" >/dev/null || true
                    wait_for_active_window_id "$window_id"
                fi
                printf '%s\n' "$window_id"
                return 0
            fi
        done < <(tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id}|#{window_name}' 2>/dev/null || true)
        sleep 0.25
    done
    echo "phone menu UI proof failed: reader window did not appear for source $source_window" >&2
    tmux list-windows -t "$TMUX_SESSION:" -F '#{window_id} #{window_name}' >&2 || true
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

wait_for_active_window_id_after_visible_tap() {
    local expected="$1"
    local retry_text="$2"
    for _ in $(seq 1 30); do
        if [ "$(tmux_active_window)" = "$expected" ]; then
            return 0
        fi
        dump_ui
        if dump_has_text "$retry_text"; then
            # WHY: Samsung/ADB occasionally swallows a dialog row tap while the
            # native composer/IME is settling. Retap only while the same verified
            # row is still visible, so this remains a real UI proof instead of
            # substituting a direct server call.
            tap_visible_text_from_current_dump "$retry_text"
        fi
        sleep 0.5
    done
    echo "phone menu UI proof failed: expected active tmux window $expected after tapping $retry_text, got $(tmux_active_window)" >&2
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
    dump_ui
    python3 - "$screenshot" "$DUMP_LOCAL" <<'PY'
from PIL import Image
import re
import sys
import xml.etree.ElementTree as ET

path, dump_path = sys.argv[1], sys.argv[2]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: xterm renders as canvas, so UIAutomator cannot read terminal text, but
# the screenshot proof must still crop the actual Android WebView. v1.84 falsely
# sampled down to 82% of the full screen, which included the native
# composer/toolbar/keyboard and passed a black terminal body with one cursor.
# Use the current android.webkit.WebView bounds and require multiple painted
# rows inside that crop; native composer/toolbar/keyboard pixels are evidence of
# controls, not terminal output.
root = ET.parse(dump_path).getroot()
chosen = None
for node in root.iter("node"):
    if node.attrib.get("class", "") != "android.webkit.WebView":
        continue
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    l, t, r, b = map(int, match.groups())
    area = (r - l) * (b - t)
    if r - l <= 100 or b - t <= 100:
        continue
    if chosen is None or area > chosen[0]:
        chosen = (area, l, t, r, b)
if chosen is None:
    raise SystemExit("terminal WebView bounds missing; refusing to sample composer/toolbar/keyboard as terminal paint")
_, left, top, right, bottom = chosen
left, top = max(0, left), max(0, top)
right, bottom = min(width, right), min(height, bottom)
bright = 0
samples = 0
row_hits = 0
for y in range(top, bottom, 8):
    row_bright = 0
    for x in range(left, right, 8):
        r, g, b = image.getpixel((x, y))
        samples += 1
        if r + g + b > 120:
            bright += 1
            row_bright += 1
    if row_bright >= 3:
        row_hits += 1
ratio = 0.0 if samples == 0 else bright / samples
if bright < 120 or ratio < 0.006 or row_hits < 4:
    raise SystemExit(
        "terminal WebView visible paint too low "
        f"(bright={bright}, ratio={ratio:.4f}, rowHits={row_hits}, "
        f"bounds=[{left},{top}][{right},{bottom}]); possible black WebView/cursor-only: {path}"
    )
print(f"terminal WebView visible paint pixels: bright={bright} ratio={ratio:.4f} rowHits={row_hits} bounds=[{left},{top}][{right},{bottom}]")
PY
}

assert_no_terminal_dot_grid() {
    local screenshot="$1"
    python3 - "$screenshot" <<'PY'
from PIL import Image
import sys

path = sys.argv[1]
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: the v1.80 proof only checked that some terminal text existed. The real
# regression still showed valid text above a huge field of evenly-spaced dotted
# xterm canvas cells after entering Active Sessions. Detect that specific visual
# failure by looking for many narrow, full-width bright dot bands and for the
# newer v1.86 regression shape: repeated short dot columns across many xterm
# rows. Normal terminal text can span a few rows; the dotted-grid failure creates
# dozens of thin bands or hundreds of repeated dot-cell runs.
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
full_view_dot_grid = len(rows) >= 180 and (narrow_bands >= 20 or repeated_columns >= 60)
if narrow_bands >= 35 or full_view_dot_grid or (len(rows) >= 100 and repeated_columns >= 40):
    raise SystemExit(
        f"terminal dotted canvas grid detected "
        f"(rows={len(rows)}, narrowBands={narrow_bands}, repeatedCols={repeated_columns}, "
        f"fullViewDotGrid={full_view_dot_grid}): {path}"
    )
print(f"terminal dotted canvas grid absent: rows={len(rows)} narrowBands={narrow_bands} repeatedCols={repeated_columns} fullViewDotGrid={full_view_dot_grid}")
PY
}

assert_no_large_black_terminal_mask() {
    local screenshot="$1"
    python3 - "$screenshot" <<'PY'
from PIL import Image
import sys

path = sys.argv[1]
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
    return (total, black / total, bright / total, contrast / total, (bright + contrast) / total)

upper = band_stats(0.08, 0.42)
middle = band_stats(0.42, 0.62)
lower = band_stats(0.62, 0.82)
upper_has_text = upper[4] >= 0.018
middle_is_black_mask = middle[1] >= 0.985 and middle[4] <= 0.003
lower_is_black_mask = lower[1] >= 0.90 and lower[4] <= 0.006

# WHY: broad menu proof must reject the same false-green shape as the targeted
# Active proof: readable rows at top with a huge lower terminal painted black.
# Covering the dotted field with black is not a live-bottom render.
if upper_has_text and middle_is_black_mask and lower_is_black_mask:
    raise SystemExit(
        "large black terminal mask detected: "
        f"upperSignal={upper[4]:.4f} middleBlack={middle[1]:.4f} "
        f"middleSignal={middle[4]:.4f} lowerBlack={lower[1]:.4f} "
        f"lowerSignal={lower[4]:.4f} screenshot={path}"
    )
print(
    "large black terminal mask absent: "
    f"upperSignal={upper[4]:.4f} middleBlack={middle[1]:.4f} "
    f"middleSignal={middle[4]:.4f} lowerBlack={lower[1]:.4f} "
    f"lowerSignal={lower[4]:.4f}"
)
PY
}

wait_for_terminal_screenshot_text_pixels() {
    local screenshot="$1"
    for _ in $(seq 1 12); do
        adb_cmd exec-out screencap -p > "$screenshot"
        if assert_terminal_screenshot_has_text_pixels "$screenshot"; then
            return 0
        fi
        sleep 0.5
    done
    adb_cmd exec-out screencap -p > "$screenshot"
    assert_terminal_screenshot_has_text_pixels "$screenshot"
}

wait_for_terminal_screenshot_settled_without_dots() {
    local screenshot="$1"
    local label="${2:-terminal}"
    local last_status="/tmp/wezterm-visual-settle-$$_last.log"
    rm -f "$last_status"
    for _ in $(seq 1 12); do
        adb_cmd exec-out screencap -p > "$screenshot"
        if assert_terminal_screenshot_has_text_pixels "$screenshot" >"$last_status" 2>&1 \
            && assert_no_terminal_dot_grid "$screenshot" >>"$last_status" 2>&1 \
            && assert_no_large_black_terminal_mask "$screenshot" >>"$last_status" 2>&1; then
            cat "$last_status"
            echo "$label visual settled without dots or black mask: $screenshot"
            return 0
        fi
        sleep 0.35
    done
    adb_cmd exec-out screencap -p > "$screenshot"
    assert_terminal_screenshot_has_text_pixels "$screenshot"
    assert_no_terminal_dot_grid "$screenshot"
    assert_no_large_black_terminal_mask "$screenshot"
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

dismiss_known_system_modal() {
    local foreground
    foreground="$(focused_foreground_package || true)"
    case "$foreground" in
        com.samsung.android.app.telephonyui)
            # WHY: the real-phone proof can be interrupted by Samsung's data
            # roaming warning while WEzterm remains visible underneath. Relaunching
            # WEzterm from this state loses transient dialogs such as Old Sessions
            # -> Resume confirmation, creating a false APK failure. Press Back only
            # to dismiss the modal; never change roaming/settings and never
            # force-stop the phone process.
            adb_cmd shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
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
    dismiss_known_system_modal
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
        # WHY: long real-phone UI proof runs can briefly drop focus to Launcher,
        # Recents, NotificationShade, or an app that surfaced during UIAutomator
        # idle waits. When this proof explicitly owns foreground control, recover
        # focus through a bounded guarded launch loop before trusting any dump.
        # A single collapse/back was not enough on the real S25 when
        # mCurrentFocus stayed NotificationShade while WEzterm remained
        # mFocusedApp. It also is not enough if a foreground app like WhatsApp
        # takes focus during a long proof: Back can stay inside that app. Use
        # Home before relaunch as the nondestructive recovery path, then retry.
        for _recover_focus in 1 2 3 4; do
            wake_and_dismiss_overlays
            has_window_focus && break
            adb_cmd shell cmd statusbar collapse >/dev/null 2>&1 || true
            sleep 0.2
            has_window_focus && break
            adb_cmd shell input keyevent KEYCODE_ESCAPE >/dev/null 2>&1 || true
            sleep 0.2
            has_window_focus && break
            adb_cmd shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
            sleep 0.2
            adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
            sleep 0.5
            has_window_focus && break
        done
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
        # WHY: a live terminal with blinking cursor/status-dot animation may
        # never reach Android's UIAutomator idle state. If the normal dump says
        # "could not get idle state" and writes no XML, retry with
        # `--compressed`; that still reads the real installed phone UI but skips
        # the idle wait that made the proof fail before it could test the app.
        if { adb_cmd shell uiautomator dump "$DUMP_REMOTE" >"$dump_log" 2>&1 \
                || adb_cmd shell uiautomator dump --compressed "$DUMP_REMOTE" >>"$dump_log" 2>&1; } \
            && adb_pull_dump >>"$dump_log" 2>&1 \
            && [ -s "$DUMP_LOCAL" ]; then
            if grep -Fq "package=\"$PACKAGE\"" "$DUMP_LOCAL"; then
                if python3 - "$DUMP_LOCAL" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
nodes = list(root.iter("node"))
# WHY: Samsung UIAutomator can transiently return a package-correct but
# root-only FrameLayout dump while an AlertDialog is visible. v2.40 long proof
# saw that exact empty hierarchy for Restore Crashed Sessions even though the
# next real dump contained the dialog. Treat root-only dumps as not fresh, so
# text assertions measure the visible phone UI instead of an accessibility race.
if len(nodes) <= 1:
    raise SystemExit(1)
raise SystemExit(0)
PY
                then
                    return 0
                fi
                echo "ignored root-only FrameLayout UIAutomator dump for $PACKAGE; retrying" >>"$dump_log"
            fi
            if [ "${WEZTERM_UI_ALLOW_FOCUS_STEAL:-0}" = "1" ]; then
                # WHY: Samsung can leave WEzterm as mFocusedApp while
                # NotificationShade owns mCurrentFocus, and it can also report
                # WEzterm focus while UIAutomator dumps another foreground app
                # after the user briefly opens WhatsApp/keyboard during this
                # long proof. Never tap a non-WEzterm hierarchy. Go Home,
                # relaunch WEzterm, and retry from a fresh toolbar dump so proof
                # automation cannot send accidental actions into another app.
                wake_and_dismiss_overlays
                adb_cmd shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
                sleep 0.25
                adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null 2>&1 || true
                wait_for_focus
                sleep 0.5
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

dump_has_button_text() {
    local text="$1"
    python3 - "$DUMP_LOCAL" "$text" <<'PY'
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("class") == "android.widget.Button" and node.attrib.get("text") == wanted:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

ensure_toolbar() {
    for _ in $(seq 1 8); do
        dump_ui
        # WHY: dialog titles, hints, and command-palette rows can contain the same
        # words as toolbar actions. The proof must normalize to the real two-row
        # toolbar before testing labels, otherwise a stale dialog/composer state can
        # either hide the buttons or falsely satisfy the check.
        if dump_has_button_text "Active" && dump_has_button_text "Old" \
                && dump_has_button_text "Refresh" && dump_has_button_text "Close"; then
            return 0
        fi
        if dump_has_text "Active Sessions" \
            || dump_has_text "Old Sessions" \
            || dump_has_text "Scroll" \
            || dump_has_text "Copy / Paste" \
            || dump_has_text "Copy/Paste" \
            || dump_has_text "Option keys" \
            || dump_has_text "Command palette" \
            || dump_has_text "Restore Crashed Sessions" \
            || dump_has_text "Resume old session?" \
            || dump_has_text "Restore crashed session?" \
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

wait_for_visible_text() {
    local text="$1"
    local description="${2:-$text}"
    for _ in $(seq 1 30); do
        dump_ui
        if grep -Fq "text=\"$text\"" "$DUMP_LOCAL"; then
            echo "visible: $description"
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: missing visible text after wait: $description" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

assert_toolbar_button_text() {
    local text="$1"
    ensure_plain_toolbar
    dump_ui
    if ! dump_has_button_text "$text"; then
        echo "phone menu UI proof failed: missing toolbar button: $text" >&2
        echo "UI dump: $DUMP_LOCAL" >&2
        exit 1
    fi
    echo "visible toolbar button: $text"
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

terminal_webview_height() {
    dump_ui
    python3 - "$DUMP_LOCAL" <<'PY'
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
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    width = right - left
    height = bottom - top
    area = width * height
    if width <= 100 or height <= 100:
        continue
    candidate = (resource_id == "terminal-container", area, height)
    if chosen is None or candidate > chosen:
        chosen = candidate
if chosen is None:
    # WHY: the native composer can make Samsung UIAutomator omit the WebView
    # accessibility node while the terminal still visibly occupies the remaining
    # content band. Use the same visible-content fallback as terminal_swipe() so
    # the zoom/refit proof can still compare the user-visible terminal height
    # before and after the composer opens.
    root_bounds = None
    excluded = []
    for node in root.iter("node"):
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        klass = node.attrib.get("class", "")
        rid = node.attrib.get("resource-id", "")
        if rid == "android:id/content":
            root_bounds = (left, top, right, bottom)
        if rid == "android:id/navigationBarBackground":
            continue
        if klass in {"android.widget.Button", "android.widget.EditText"}:
            excluded.append((top, bottom))
    if root_bounds is None:
        raise SystemExit("could not find terminal WebView height")
    _, top, _, bottom = root_bounds
    cursor = top
    gaps = []
    for start, end in sorted(excluded):
        if start - cursor > 100:
            gaps.append(start - cursor)
        cursor = max(cursor, end)
    if bottom - cursor > 100:
        gaps.append(bottom - cursor)
    if not gaps:
        raise SystemExit("could not find terminal WebView height")
    print(max(gaps) - 24)
else:
    print(chosen[2])
PY
}

assert_composer_terminal_refit() {
    local before_height="$1"
    local before_rows="$2"
    local current_height=""
    local current_rows=""
    local expected_rows=""
    # WHY: this pins the user-visible dotted/full-area regression. UIAutomator
    # cannot see xterm canvas text or automate a reliable public multi-touch
    # pinch, but it can prove the Android WebView shrinks for the native composer
    # and tmux receives the corresponding ttyd/xterm row resize. Without that
    # resize, the old row/canvas height is exposed as dotted blank space and a
    # zoomed viewer cannot scroll to the true bottom.
    for _ in $(seq 1 40); do
        if input_method_visible; then
            current_height="$(terminal_webview_height)"
            current_rows="$(tmux_window_pane_height "$proof_window")"
            if [[ "$before_height" =~ ^[0-9]+$ && "$before_rows" =~ ^[0-9]+$ \
                    && "$current_height" =~ ^[0-9]+$ && "$current_rows" =~ ^[0-9]+$ \
                    && "$current_height" -lt "$before_height" \
                    && "$current_rows" -lt "$before_rows" ]]; then
                expected_rows="$(python3 - "$before_height" "$before_rows" "$current_height" <<'PY'
import math
import sys

before_height, before_rows, current_height = map(int, sys.argv[1:])
print(max(1, math.ceil(before_rows * current_height / before_height)))
PY
)"
                if [ "$current_rows" -le $((expected_rows + 14)) ]; then
                    echo "Zoomed/native composer terminal refit kept the true bottom viewport usable: before_height=$before_height current_height=$current_height before_rows=$before_rows current_rows=$current_rows expected_rows=$expected_rows"
                    return 0
                fi
            fi
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: native composer did not refit terminal rows; before_height=$before_height current_height=${current_height:-?} before_rows=$before_rows current_rows=${current_rows:-?} expected_rows=${expected_rows:-?}" >&2
    exit 1
}

composer_draft_text_from_dump() {
    python3 - "$DUMP_LOCAL" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.EditText":
        continue
    hint = node.attrib.get("hint", "")
    if node.attrib.get("content-desc") == "Type prompt" or hint.startswith("Type prompt"):
        text = node.attrib.get("text", "")
        # UIAutomator reports the hint as text for an empty EditText on Samsung.
        # Treat that as empty so the proof can dismiss an empty restored composer,
        # while still refusing to clear actual unsent user text.
        if text == hint or text.startswith("Type prompt"):
            text = ""
        print(text)
        break
PY
}

terminal_input_draft_from_dump() {
    python3 - "$DUMP_LOCAL" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.EditText":
        continue
    if node.attrib.get("hint") != "Terminal input":
        continue
    text = node.attrib.get("text", "")
    # UIAutomator reports the hidden ttyd/xterm input separately from the native
    # composer. Empty hidden input is normal; non-empty text is a real unsent
    # draft that the proof must preserve before any foreground reset.
    print(text)
    break
PY
}

dismiss_terminal_input_draft_if_present() {
    dump_ui
    if ! grep -Eq 'class="android.widget.EditText"[^>]*hint="Terminal input"' "$DUMP_LOCAL"; then
        return 0
    fi
    local draft
    draft="$(terminal_input_draft_from_dump)"
    if [ -z "$draft" ]; then
        return 0
    fi
    local draft_file="$SCREENSHOT_DIR/wezterm-preserved-terminal-input-draft-$$.txt"
    printf '%s\n' "$draft" > "$draft_file"
    if [ "${WEZTERM_UI_ALLOW_CLEAR_DRAFT:-0}" != "1" ]; then
        echo "phone menu UI proof blocked: hidden terminal input contains unsent text; saved draft to $draft_file and refused to clear it" >&2
        echo "WHY: xterm's hidden Terminal input is not the native composer, so Back cannot reliably dismiss it. Clearing it without an explicit opt-in could lose a real prompt." >&2
        exit 1
    fi
    # WHY: a hidden ttyd/xterm textarea with a long unsent draft can keep the IME
    # and non-plain toolbar latched even though tmux is safe. For proof runs that
    # explicitly opt in, preserve the draft above, then restart only the Android
    # client process. tmux, ttyd, Codex panes, scrollback, and crash-restore state
    # stay untouched; this just clears client-side unsent WebView input so the
    # real installed UI can be proven from a clean toolbar.
    adb_cmd shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
    sleep 0.4
    adb_cmd shell am start -W -n "$ACTIVITY" >/dev/null
    wait_for_focus
    sleep 1.0
    echo "Hidden terminal input draft preserved to $draft_file and Android client relaunched for proof"
}

dismiss_native_composer_if_open() {
    dump_ui
    if grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL"; then
        local draft
        draft="$(composer_draft_text_from_dump)"
        if [ -n "$draft" ]; then
            local draft_file="$SCREENSHOT_DIR/wezterm-preserved-composer-draft-$$.txt"
            printf '%s\n' "$draft" > "$draft_file"
            if [ "${WEZTERM_UI_ALLOW_CLEAR_DRAFT:-0}" = "1" ]; then
                echo "Native composer draft preserved to $draft_file before explicit proof clear"
            fi
        fi
        if [ -n "$draft" ] && [ "${WEZTERM_UI_ALLOW_CLEAR_DRAFT:-0}" != "1" ]; then
            echo "phone menu UI proof blocked: native composer contains unsent text; saved draft to $draft_file and refused to clear it" >&2
            exit 1
        fi
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

ensure_plain_toolbar() {
    local stable_plain=0
    for _ in $(seq 1 8); do
        ensure_toolbar
        dump_ui
        dismiss_terminal_input_draft_if_present
        dump_ui
        if dump_has_button_text "Start" \
                && ! grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL" \
                && ! input_method_visible; then
            stable_plain=$((stable_plain + 1))
            if [ "$stable_plain" -ge 2 ]; then
                return 0
            fi
            sleep 0.25
            continue
        fi
        stable_plain=0
        # WHY: after APK install/reopen, Android can restore the native composer
        # slightly after the first toolbar dump. The label then becomes Send, so
        # the proof would falsely report that Start regressed. Require two stable
        # plain-toolbar dumps before the toolbar-label assertions so a late
        # restored composer cannot slip between label and geometry checks.
        dismiss_native_composer_if_open
        sleep 0.25
    done
    echo "phone menu UI proof failed: could not reach the plain toolbar with Start visible" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

wait_for_native_composer_open() {
    for _ in $(seq 1 20); do
        dump_ui
        if grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL" \
                && dump_has_button_text "Send"; then
            echo "Native composer is open"
            return 0
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: native composer did not open before Active switch reproduction" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

assert_active_switch_plain_state() {
    local stable_plain=0
    for _ in $(seq 1 24); do
        dump_ui
        if dump_has_button_text "Start" \
                && ! dump_has_button_text "Send" \
                && ! grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL" \
                && ! input_method_visible; then
            stable_plain=$((stable_plain + 1))
            if [ "$stable_plain" -ge 2 ]; then
                echo "Active switch hid native composer and keyboard"
                return 0
            fi
            sleep 0.25
            continue
        fi
        stable_plain=0
        sleep 0.25
    done
    echo "phone menu UI proof failed: Active switch left composer/IME/Send toolbar visible" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    adb_cmd shell dumpsys input_method | tail -80 >&2 || true
    exit 1
}

assert_active_picker_hid_composer() {
    dump_ui
    if grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL" \
            || input_method_visible; then
        echo "phone menu UI proof failed: Active picker opened while composer/IME stayed visible" >&2
        echo "UI dump: $DUMP_LOCAL" >&2
        adb_cmd shell dumpsys input_method | tail -80 >&2 || true
        exit 1
    fi
    echo "Active picker hid native composer before dialog"
}

assert_read_mode_hid_composer() {
    local stable_plain=0
    for _ in $(seq 1 24); do
        dump_ui
        if dump_has_button_text "Start" \
                && ! dump_has_button_text "Send" \
                && ! grep -Eq 'class="android.widget.EditText"[^>]*(content-desc|hint)="Type prompt"' "$DUMP_LOCAL" \
                && ! input_method_visible; then
            stable_plain=$((stable_plain + 1))
            if [ "$stable_plain" -ge 2 ]; then
                echo "Scrollback/read mode hid native composer and keyboard"
                return 0
            fi
            sleep 0.25
            continue
        fi
        stable_plain=0
        sleep 0.25
    done
    echo "phone menu UI proof failed: scrollback/read mode left composer/IME/Send toolbar visible" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    adb_cmd shell dumpsys input_method | tail -80 >&2 || true
    exit 1
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
    # WHY: Samsung's UIAutomator dumps do not always mark AlertDialog content
    # scrollable, especially after rotation or while the IME has just changed
    # state. The proof should still exercise a real finger swipe in the visible
    # app/dialog area instead of failing on a missing accessibility flag.
    for node in root.iter("node"):
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
    raise SystemExit("no swipable node found")
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

scroll_found_text=""
scroll_until_text_any() {
    local text
    for _ in $(seq 1 6); do
        dump_ui
        for text in "$@"; do
            if dump_has_text "$text"; then
                scroll_found_text="$text"
                echo "visible: $text"
                return 0
            fi
        done
        swipe_first_scrollable_down
    done
    echo "phone menu UI proof failed: could not scroll to any visible text: $*" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

ensure_active_picker_open() {
    dump_ui
    if dump_has_text "Active Sessions"; then
        return 0
    fi
    # WHY: opening Active while the native composer/IME is being dismissed can
    # briefly show the picker, pass the label checks, then Android may return to
    # the main terminal before the proof scrolls to an off-screen row. Reopen the
    # same picker through the visible Active button instead of replacing the UI
    # proof with a direct control-server select.
    ensure_plain_toolbar
    dump_ui
    tap_visible_text_from_current_dump "Active"
    assert_text "Active Sessions"
    assert_text "Rename"
}

open_active_picker() {
    for _ in $(seq 1 5); do
        ensure_plain_toolbar
        dump_ui
        tap_visible_text_from_current_dump "Active"
        for __ in $(seq 1 10); do
            dump_ui
            if dump_has_text "Active Sessions"; then
                assert_text "Active Sessions"
                assert_text "Rename"
                return 0
            fi
            sleep 0.25
        done
        # WHY: after an Active-session switch the APK may still be settling the
        # WebView live-bottom viewport. A single visible Active tap can be
        # swallowed in that frame. Retry only from verified toolbar state so this
        # remains a real installed-APK UI proof, not a control-server shortcut.
        press_back
    done
    echo "phone menu UI proof failed: Active button did not open Active Sessions" >&2
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

tap_visible_text_from_current_dump() {
    local text="$1"
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
    raise SystemExit(f"missing text {wanted} in current dump")
PY
)"
    read -r x y <<<"$coords"
    adb_cmd shell input tap "$x" "$y"
    sleep 0.8
}

tap_visible_text_above_toolbar_from_current_dump() {
    local text="$1"
    local coords
    coords="$(python3 - "$DUMP_LOCAL" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
matches = []
for node in root.iter("node"):
    if node.attrib.get("text") != wanted:
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        continue
    left, top, right, bottom = map(int, match.groups())
    center_y = (top + bottom) // 2
    if center_y < 2500:
        matches.append(((left + right) // 2, center_y, top))
if not matches:
    raise SystemExit(f"missing dialog text {wanted} above toolbar in current dump")
matches.sort(key=lambda item: item[2])
print(matches[0][0], matches[0][1])
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
    # On this Samsung phone, 900ms repeatedly returned to the toolbar instead of
    # opening the palette; 1500ms opened the palette and the Restore action.
    adb_cmd shell input swipe "$x" "$y" "$x" "$y" 1500
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
    # WHY: when the native composer is open, Samsung UIAutomator can expose only
    # the composer + toolbar and hide the WebView accessibility node, even though
    # the terminal is visibly present and still receives the one-finger gesture.
    # This fallback computes the largest visible content band not occupied by
    # buttons/EditTexts/navigation bars, then swipes there. It keeps the proof as
    # a real physical phone gesture without touching the app's frozen scroll/zoom
    # implementation or bypassing the UI through a server call.
    root_bounds = None
    excluded = []
    right_limit = None
    for node in root.iter("node"):
        bounds = node.attrib.get("bounds", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if not match:
            continue
        l, t, r, b = map(int, match.groups())
        klass = node.attrib.get("class", "")
        rid = node.attrib.get("resource-id", "")
        if rid == "android:id/content":
            root_bounds = (l, t, r, b)
        if rid == "android:id/navigationBarBackground":
            right_limit = min(right_limit or l, l)
            continue
        if klass in {"android.widget.Button", "android.widget.EditText"}:
            excluded.append((t, b))
    if root_bounds is None:
        raise SystemExit("could not find terminal WebView bounds")
    left, top, right, bottom = root_bounds
    if right_limit:
        right = min(right, right_limit)
    cursor = top
    gaps = []
    for start, end in sorted(excluded):
        if start - cursor > 100:
            gaps.append((start - cursor, cursor, start))
        cursor = max(cursor, end)
    if bottom - cursor > 100:
        gaps.append((bottom - cursor, cursor, bottom))
    if not gaps:
        raise SystemExit("could not find terminal WebView bounds")
    _, top, bottom = max(gaps)
    top += 12
    bottom -= 12
else:
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
    if [ "$direction" = "history-slow" ]; then
        # WHY: v2.40 proved Samsung's separate `input touchscreen motionevent`
        # commands can fail the slow-drag proof by delivering no intermediate
        # MOVE events, then one large release jump (`0,0,0,0,0 final=47`). A
        # single `input swipe ... 1200` process now produces bounded cadence
        # samples on the same real Android one-finger path, so keep the strict
        # multi-sample assertions below and use the injector that actually emits
        # intermediate movement on this device.
        adb_cmd shell input swipe "$x1" "$y1" "$x2" "$y2" "$duration_ms"
        return
    fi
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

tap_button_text_from_current_dump() {
    local text="$1"
    local coords
    coords="$(python3 - "$DUMP_LOCAL" "$text" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, wanted = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("class") != "android.widget.Button":
        continue
    if node.attrib.get("text") != wanted:
        continue
    bounds = node.attrib.get("bounds", "")
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not match:
        raise SystemExit(f"no parsable button bounds for {wanted}: {bounds}")
    left, top, right, bottom = map(int, match.groups())
    print((left + right) // 2, (top + bottom) // 2)
    break
else:
    raise SystemExit(f"missing button text {wanted} in current dump")
PY
)"
    read -r x y <<<"$coords"
    adb_cmd shell input tap "$x" "$y"
    sleep 0.5
}

open_option_keys_menu() {
    ensure_toolbar
    tap_text "Copy/Paste"
    assert_text_any "Copy/Paste" "Copy / Paste"
    tap_text "Option keys"
    assert_text "Option keys"
}

tap_option_key_from_open_dialog() {
    local key_label="$1"
    # WHY: this proof must use the same discoverable Android UI surface a user
    # sees on the phone. Do not replace these taps with direct `/send-key` calls:
    # the regression is that CLI option pickers, Backspace, and Delete were not
    # reachable/intuitive from the installed APK.
    dump_ui
    tap_button_text_from_current_dump "$key_label"
    if [ "$key_label" != "Select" ] && [ "$key_label" != "Escape" ]; then
        assert_text "Option keys"
    fi
    sleep 0.5
}

open_scroll_menu() {
    for _ in $(seq 1 4); do
        ensure_plain_toolbar
        dump_ui
        if ! dump_has_button_text "Scroll"; then
            dismiss_native_composer_if_open
            sleep 0.25
            continue
        fi
        # WHY: this helper is used immediately after reader-window creation and
        # Android layout changes. Taking another blind dump inside tap_text can
        # catch a transient composer/dialog frame and fail even though the real
        # toolbar is visible. Tap the Scroll button from the dump we already
        # validated, then retry from toolbar state if the menu animation misses.
        tap_visible_text_from_current_dump "Scroll"
        for __ in $(seq 1 8); do
            dump_ui
            if dump_has_text "Read current session" && dump_has_text "Go to live bottom / type"; then
                echo "Scroll menu is open"
                return 0
            fi
            sleep 0.2
        done
        # WHY: proof runs can inherit a half-dismissed native composer or a stale
        # Android dialog animation from the previous step. A single blind Scroll
        # tap then leaves the harness looking for menu text on the toolbar screen.
        # Retry from a known toolbar state instead of weakening the app assertion.
        press_back
        dismiss_native_composer_if_open
    done
    echo "phone menu UI proof failed: Scroll button did not open the scroll-only menu" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

open_command_palette() {
    for _ in $(seq 1 4); do
        ensure_plain_toolbar
        long_press_text "Scroll"
        for __ in $(seq 1 8); do
            dump_ui
            if dump_has_text "Active Sessions" \
                    && dump_has_text "Restore Crashed Sessions" \
                    && dump_has_text "Refresh current session"; then
                echo "Command palette is open"
                return 0
            fi
            sleep 0.2
        done
        # WHY: after several AlertDialog opens/dismissals, Samsung can swallow a
        # synthetic long-press and leave the phone on the plain toolbar. Retrying
        # from a verified toolbar keeps this a real UI proof while preventing a
        # one-off missed long-press from being reported as missing command text.
        press_back
        dismiss_native_composer_if_open
    done
    echo "phone menu UI proof failed: Scroll long-press did not open the command palette" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
}

tap_command_palette_action_and_wait() {
    local action_text="$1"
    local expected_text="${2:-$action_text}"
    for _ in $(seq 1 3); do
        open_command_palette
        dump_ui
        tap_visible_text_above_toolbar_from_current_dump "$action_text"
        for __ in $(seq 1 12); do
            dump_ui
            if dump_has_text "$expected_text"; then
                echo "visible: $expected_text"
                return 0
            fi
            sleep 0.25
        done
        # WHY: real-phone proof showed Samsung can accept a command-palette row
        # tap, dismiss the palette, and land back on the toolbar without opening
        # the requested AlertDialog. Reopen the visible palette and retry the same
        # user path instead of replacing the action with a server shortcut.
        press_back
        dismiss_native_composer_if_open
    done
    echo "phone menu UI proof failed: command-palette action '$action_text' did not show '$expected_text'" >&2
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

wait_for_light_active_window_title() {
    local window_id="$1"
    local tabs_file
    local title
    for _ in $(seq 1 80); do
        tabs_file="$(mktemp)"
        if control_get "/tabs?light=1" > "$tabs_file"; then
            title="$(python3 - "$tabs_file" "$window_id" <<'PY' || true
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
wanted = sys.argv[2]
for window in payload.get("windows", []):
    if window.get("windowId") == wanted and window.get("active"):
        print(window.get("title") or window.get("name") or wanted)
        raise SystemExit(0)
PY
)"
            rm -f "$tabs_file"
            if [ -n "$title" ]; then
                printf '%s\n' "$title"
                return 0
            fi
        else
            rm -f "$tabs_file"
        fi
        sleep 0.25
    done
    echo "phone menu UI proof failed: /tabs?light=1 never reported $window_id as the active phone-visible window" >&2
    exit 1
}

pick_active_switch_target() {
    local avoid_window="$1"
    local proof_created_window="${2:-}"
    local tabs_file
    tabs_file="$(mktemp)"
    control_get "/tabs" > "$tabs_file"
    python3 - "$tabs_file" "$avoid_window" "$proof_created_window" <<'PY'
import json
import sys

tabs_file, avoid_window, proof_created_window = sys.argv[1], sys.argv[2], sys.argv[3]
payload = json.load(open(tabs_file, encoding="utf-8"))
generic = {"", "~", "shell", "bash", "node"}
for window in payload.get("windows", []):
    window_id = window.get("windowId", "")
    title = window.get("title") or window.get("name") or ""
    if window_id in {avoid_window, proof_created_window}:
        continue
    if title.strip().lower() in generic:
        continue
    if title.startswith("READ "):
        continue
    print(f"{window_id}\t{title}")
    raise SystemExit(0)
raise SystemExit("no non-generic Active Sessions target found")
PY
    rm -f "$tabs_file"
}

cleanup_window() {
    local window_id="$1"
    local expected_title="${2:-}"
    if [ -n "$window_id" ] && tmux_window_exists "$window_id"; then
        if window_was_initial "$window_id"; then
            echo "phone menu UI proof cleanup: refusing to close pre-existing user window $window_id" >&2
            return 0
        fi
        if [ -n "$expected_title" ]; then
            local actual_title
            actual_title="$(tmux display-message -p -t "$TMUX_SESSION:$window_id" '#{window_name}' 2>/dev/null || true)"
            case "$actual_title" in
                "$expected_title"*) ;;
                "~"|"shell"|"bash")
                    if [ "$window_id" = "${proof_window:-}" ] && [ "$expected_title" = "$PROOF_NAME" ]; then
                        # WHY: the New-button proof captures the exact tmux
                        # window id created by the phone UI, then renames it for
                        # safety. On a failed run tmux/control title repair can
                        # still show the brand-new shell as "~" before the proof
                        # title sticks. Because this id was not present in the
                        # initial window snapshot and belongs to proof_window,
                        # closing it is safer than leaving debris tabs behind.
                        :
                    else
                        echo "phone menu UI proof cleanup: refusing to close $window_id titled '$actual_title'; expected proof title does not match '$expected_title'" >&2
                        return 0
                    fi
                    ;;
                *)
                    echo "phone menu UI proof cleanup: refusing to close $window_id titled '$actual_title'; expected proof title does not match '$expected_title'" >&2
                    return 0
                    ;;
            esac
        fi
        # WHY: this proof creates disposable windows, but it runs in the same
        # grouped tmux session as real user work. Never kill a window that
        # existed before the proof or whose expected proof title does not match;
        # otherwise a stale variable or concurrent selection can delete a real
        # lane such as the user's movie/subtitle tab.
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
    cleanup_window "$reader_window" "READ "
    cleanup_window "$resume_window"
    cleanup_window "$drift_window" "$PROOF_NAME Drift"
    cleanup_window "$cli_keys_window" "$CLI_KEYS_TITLE"
    cleanup_window "$proof_window" "$PROOF_NAME"
    rm -f "$COPY_FILE" "$STOP_FILE" "$TYPE_FILE" "$DRIFT_FILE" "$DRIFT_OTHER_FILE" \
        "$CLI_KEYS_FILE" "$CLI_KEYS_FILE.backspace" "$CLI_KEYS_FILE.delete" \
        "$CLI_KEYS_FILE.home" "$CLI_KEYS_FILE.end" "$CLI_KEYS_FILE.escape" \
        "$CLI_KEYS_SCRIPT"
    rm -f "$initial_windows_file"
    restore_original_tmux_state
}
trap cleanup EXIT

echo "phone menu UI proof: adb/control state"
assert_exclusive_phone_proof
assert_title_sync_stopped
adb_cmd get-state | grep -Fxq "device"
assert_installed_package_version
control_get "/health" | json_assert "control health" "p.get('ok') is True"

orig_window="$(tmux_active_window)"
orig_mode="$(tmux_pane_mode)"
orig_scroll="$(tmux_scroll_position)"
record_initial_windows

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
ensure_plain_toolbar

echo "phone menu UI proof: toolbar labels"
for label in Active Old New Refresh Bottom Scroll "Copy/Paste" Upload Close Start Stop; do
    assert_toolbar_button_text "$label"
done
assert_absent "Tabs"
assert_absent "New Tab"
assert_absent "Close Tab"
assert_terminal_toolbar_geometry

echo "phone menu UI proof: Read current session opens reader and live bottom returns"
reader_count="$(tmux_window_count)"
ensure_toolbar
reader_source_window="$(tmux_active_window)"
open_scroll_menu
tap_visible_text_from_current_dump "Read current session"
reader_window="$(wait_for_reader_window "$reader_source_window" "$reader_count")"
reader_name="$(tmux display-message -p -t "$TMUX_SESSION:$reader_window" '#{window_name}')"
case "$reader_name" in
    "READ $reader_source_window"*) ;;
    *)
        echo "phone menu UI proof failed: reader window did not name its selected source: selected=$reader_source_window reader=$reader_window name=$reader_name" >&2
        exit 1
        ;;
esac
echo "Read current session opened reader $reader_window for selected source $reader_source_window"
ensure_toolbar
open_scroll_menu
tap_visible_text_from_current_dump "Go to live bottom / type"
wait_for_active_window_id_after_visible_tap "$reader_source_window" "Go to live bottom / type"
echo "Reader live-bottom returned to $reader_source_window"
dismiss_native_composer_if_open
cleanup_window "$reader_window"
reader_window=""
reopen_wezterm
ensure_toolbar
bottom_source_window="$(tmux_active_window)"
tap_text "Bottom"
wait_for_active_window_id "$bottom_source_window"
wait_for_window_pane_mode "$bottom_source_window" "0"
echo "Direct Bottom button kept current session at live typing"
dismiss_native_composer_if_open

echo "phone menu UI proof: New button creates disposable session"
before_count="$(tmux_window_count)"
tap_text "New"
proof_window="$(wait_for_active_new_window "$before_count" "$orig_window")"
tmux rename-window -t "$TMUX_SESSION:$proof_window" "$PROOF_NAME"
wait_for_shell "$proof_window"
echo "New button created $proof_window"

echo "phone menu UI proof: Active button opens active session picker"
ensure_plain_toolbar
for __active_attempt in $(seq 1 4); do
    dump_ui
    tap_visible_text_from_current_dump "Active"
    for __wait_attempt in $(seq 1 12); do
        dump_ui
        if dump_has_text "Active Sessions"; then
            echo "visible: Active Sessions"
            break 2
        fi
        sleep 0.2
    done
    # WHY: on the real S25 Ultra proof path a tap can be swallowed while Android
    # is settling from the New-window creation and native composer dismissal.
    # Retry only from a verified toolbar state; do not fall back to control-server
    # endpoints because this section is proving the visible phone button.
    press_back
    ensure_plain_toolbar
done
dump_ui
if ! dump_has_text "Active Sessions"; then
    echo "phone menu UI proof failed: missing visible text: Active Sessions" >&2
    echo "UI dump: $DUMP_LOCAL" >&2
    exit 1
fi
assert_text "New"
assert_text "Rename"
assert_text "Cancel"
assert_regex 'text="Current:' "current active session row"
assert_absent "Sessions by date"
press_back

echo "phone menu UI proof: Active row title switches with one tap"
select_window "$orig_window"
reopen_wezterm
ensure_plain_toolbar
IFS=$'\t' read -r active_switch_window active_switch_title < <(pick_active_switch_target "$orig_window" "$proof_window")
open_active_picker
# WHY: Active Sessions is intentionally crowded on the real phone because it
# includes current-first rows plus every live tmux window. Use a real existing
# non-generic tab title for switch proof instead of the disposable New-button
# tab: the New proof window can briefly display "~" before any title repair,
# and selecting a generic "~" row would risk the old wrong-tab regression.
scroll_until_text_any "$active_switch_title"
tap_visible_text_from_current_dump "$scroll_found_text"
wait_for_active_window_id "$active_switch_window"
# WHY: v2.22 shortened the native full-frame Active-switch shield. Wait under
# one second, then require real terminal paint so a returned 2200 ms all-black
# shield cannot be mistaken for a fixed one-tap session switch.
wait_for_terminal_screenshot_settled_without_dots /tmp/wezterm-v157-active-title-one-tap.png "Active row title switch"
echo "Active row title switched with one tap to $active_switch_window ($active_switch_title)"

echo "phone menu UI proof: Active switch hides native composer and IME"
select_window "$orig_window"
reopen_wezterm
ensure_plain_toolbar
terminal_tap_for_typing
wait_for_native_composer_open
dump_ui
tap_visible_text_from_current_dump "Active"
assert_active_picker_hid_composer
ensure_active_picker_open
scroll_until_text_any "$active_switch_title"
tap_visible_text_from_current_dump "$scroll_found_text"
wait_for_active_window_id "$active_switch_window"
assert_active_switch_plain_state
# WHY: keep this paired with the title-switch screenshot above. Composer/IME
# suppression is not enough; the terminal must also be readable shortly after
# the Active row tap, without the old full-frame black shield.
wait_for_terminal_screenshot_settled_without_dots /tmp/wezterm-v175-active-composer-hidden.png "Active composer-hidden switch"

echo "phone menu UI proof: Old button and Resume action"
ensure_plain_toolbar
old_count="$(tmux_window_count)"
dump_ui
tap_visible_text_from_current_dump "Old"
wait_for_visible_text "Old Sessions" "Old Sessions"
wait_for_visible_text "Resume" "Resume"
assert_text_any "Crashed" "CRASHED"
assert_regex 'text="[0-9]{4}-[0-9]{2}-[0-9]{2}' "old-session date header"
assert_old_sessions_without_agent_labels
adb_cmd exec-out screencap -p > "$OLD_SCREENSHOT"
echo "old sessions screenshot: $OLD_SCREENSHOT"
# WHY: Android renders the Old Sessions footer command as an uppercased
# `CRASHED` button. Tap the footer button class specifically, not a generic
# CRASHED text row, then prove it opens the server-backed Restore Crashed
# Sessions dialog.
dump_ui
tap_button_text_from_current_dump "CRASHED"
wait_for_visible_text "Restore Crashed Sessions" "Restore Crashed Sessions"
press_back
ensure_plain_toolbar
dump_ui
tap_visible_text_from_current_dump "Old"
wait_for_visible_text "Old Sessions" "Old Sessions"
wait_for_visible_text "Resume" "Resume"
tap_text "Resume"
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
wait_for_terminal_screenshot_text_pixels /tmp/wezterm-v151-refresh-proof.png
echo "Refresh button restored live mode"

echo "phone menu UI proof: Scroll menu buttons"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "for i in \$(seq 1 140); do printf 'UIBTN_%04d\\n' \"\$i\"; done; printf '${COPY_TOKEN}\\n'" Enter
wait_for_capture_text "$proof_window" "$COPY_TOKEN"

echo "phone menu UI proof: scrollback hides native composer and IME"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "0"
ensure_plain_toolbar
terminal_tap_for_typing
wait_for_native_composer_open
dump_ui
# WHY: in the native-composer layout, Samsung UIAutomator may hide the terminal
# WebView node entirely, and the composer can consume a blind physical swipe.
# The protected behavior here is that entering scrollback/read mode from a
# typing state hides the composer and IME. Exercise that through the visible
# Scroll -> Page up path, then keep the separate slow/fast physical one-finger
# swipe proof below for the terminal gesture subsystem.
tap_visible_text_from_current_dump "Scroll"
assert_text "Scroll"
assert_text "Page up"
tap_visible_text_from_current_dump "Page up"
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "1"
assert_read_mode_hid_composer
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_window_pane_mode "$proof_window" "0"
ensure_plain_toolbar

echo "phone menu UI proof: physical one-finger slow drag and fast flick"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "0"
ensure_toolbar
# WHY: the user's core regression was physical one-finger scroll feeling delayed,
# jumpy, and sometimes routed to Codex prompt history. ADB's `input swipe`
# injects the same single-pointer path the app receives from a finger; slow
# movement must produce multiple small bounded cadence samples instead of one
# backend-sized jump, while a deliberate fast flick must move materially farther
# through tmux copy-mode history.
# WHY: WEzterm intentionally maps phone finger movement like normal scrollback:
# dragging the finger down moves into older tmux history (`lineUp`), and dragging
# the finger up returns toward the live bottom (`lineDown`). Use the live
# WebView bounds instead of hardcoded portrait coordinates so the proof still
# tests the real terminal if the phone is in landscape or the IME resized it.
slow_scroll_samples=""
slow_scroll_positive_samples=0
slow_scroll_max_delta=0
slow_scroll_prev=0
slow_swipe_log="$(mktemp)"
terminal_swipe history-slow 1200 >"$slow_swipe_log" 2>&1 &
slow_swipe_pid=$!
# WHY: terminal_swipe performs UIAutomator dump/parsing before it can emit the
# real ADB gesture. Sampling during that setup recorded `0,0,0,0,0 final=12`
# and blamed the app for a cadence failure that was actually proof timing. Wait
# for the exact "terminal swipe history-slow" line, which is printed immediately
# before `adb input swipe`, so the samples below measure the gesture itself.
slow_swipe_started=0
for _ in $(seq 1 80); do
    if grep -Fq "terminal swipe history-slow:" "$slow_swipe_log"; then
        cat "$slow_swipe_log"
        slow_swipe_started=1
        break
    fi
    if ! kill -0 "$slow_swipe_pid" 2>/dev/null; then
        cat "$slow_swipe_log" >&2 || true
        wait "$slow_swipe_pid"
        echo "phone menu UI proof failed: slow one-finger gesture ended before cadence sampling started" >&2
        exit 1
    fi
    sleep 0.05
done
if [ "$slow_swipe_started" != "1" ]; then
    cat "$slow_swipe_log" >&2 || true
    echo "phone menu UI proof failed: slow one-finger gesture did not start before cadence sampling timeout" >&2
    exit 1
fi
for _ in $(seq 1 5); do
    sleep 0.28
    wait_for_active_window_id "$proof_window"
    slow_sample="$(tmux_window_scroll_position "$proof_window")"
    if ! [[ "$slow_sample" =~ ^[0-9]+$ ]]; then
        echo "phone menu UI proof failed: slow one-finger cadence sample was not numeric: $slow_sample" >&2
        exit 1
    fi
    if [ "$slow_sample" -lt "$slow_scroll_prev" ]; then
        echo "phone menu UI proof failed: slow one-finger drag moved backward during cadence samples: samples=$slow_scroll_samples,$slow_sample" >&2
        exit 1
    fi
    slow_delta=$(( slow_sample - slow_scroll_prev ))
    if [ "$slow_delta" -gt 36 ]; then
        echo "phone menu UI proof failed: slow one-finger drag jumped too far between cadence samples: delta=$slow_delta samples=$slow_scroll_samples,$slow_sample" >&2
        exit 1
    fi
    if [ "$slow_delta" -gt 0 ]; then
        slow_scroll_positive_samples=$(( slow_scroll_positive_samples + 1 ))
        if [ "$slow_delta" -gt "$slow_scroll_max_delta" ]; then
            slow_scroll_max_delta="$slow_delta"
        fi
    fi
    slow_scroll_samples="${slow_scroll_samples}${slow_scroll_samples:+,}$slow_sample"
    slow_scroll_prev="$slow_sample"
done
wait "$slow_swipe_pid"
sleep 0.45
wait_for_active_window_id "$proof_window"
slow_scroll="$(tmux_window_scroll_position "$proof_window")"
if ! [[ "$slow_scroll" =~ ^[0-9]+$ ]]; then
    echo "phone menu UI proof failed: slow one-finger final sample was not numeric: $slow_scroll" >&2
    exit 1
fi
if [ "$slow_scroll" -lt "$slow_scroll_prev" ]; then
    echo "phone menu UI proof failed: slow one-finger drag moved backward after cadence samples: samples=$slow_scroll_samples final=$slow_scroll" >&2
    exit 1
fi
slow_final_delta=$(( slow_scroll - slow_scroll_prev ))
if [ "$slow_final_delta" -gt 36 ]; then
    echo "phone menu UI proof failed: slow one-finger drag jumped too far after cadence samples: delta=$slow_final_delta samples=$slow_scroll_samples final=$slow_scroll" >&2
    exit 1
fi
if [ "$slow_final_delta" -gt 0 ]; then
    slow_scroll_positive_samples=$(( slow_scroll_positive_samples + 1 ))
    if [ "$slow_final_delta" -gt "$slow_scroll_max_delta" ]; then
        slow_scroll_max_delta="$slow_final_delta"
    fi
fi
if [ "$slow_scroll_positive_samples" -lt 2 ] || [ "$slow_scroll" -le 0 ] || [ "$slow_scroll" -gt 96 ]; then
    echo "phone menu UI proof failed: slow one-finger drag should produce multiple small bounded deltas, samples=$slow_scroll_samples final=$slow_scroll positives=$slow_scroll_positive_samples max_delta=$slow_scroll_max_delta" >&2
    exit 1
fi
echo "Slow one-finger cadence samples stayed bounded: samples=$slow_scroll_samples final=$slow_scroll max_delta=$slow_scroll_max_delta"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "0"
ensure_toolbar
terminal_swipe history-fast 120
sleep 1.6
wait_for_active_window_id "$proof_window"
fast_scroll="$(tmux_window_scroll_position "$proof_window")"
fast_min_scroll=$(( slow_scroll + 20 ))
if [ "$fast_scroll" -lt "$fast_min_scroll" ]; then
    echo "phone menu UI proof failed: fast one-finger flick should move materially farther than slow drag cadence, slow=$slow_scroll max_delta=$slow_scroll_max_delta min_fast=$fast_min_scroll fast=$fast_scroll" >&2
    exit 1
fi
echo "Physical one-finger slow/fast scroll proved slow=$slow_scroll samples=$slow_scroll_samples max_delta=$slow_scroll_max_delta fast=$fast_scroll"
control_get "/touch-scroll?where=lineUp&repeat=8" >/dev/null || true
wait_for_active_window_id "$proof_window"
wait_for_window_pane_mode "$proof_window" "1"
ensure_toolbar
terminal_swipe live-return 250
wait_for_active_window_id "$proof_window"
live_return_start_ms="$(date +%s%3N)"
live_return_mode=""
live_return_scroll=""
for _ in $(seq 1 24); do
    live_return_mode="$(tmux_window_pane_mode "$proof_window")"
    live_return_scroll="$(tmux_window_scroll_position "$proof_window")"
    if [ "$live_return_mode" = "0" ] && { [ -z "$live_return_scroll" ] || [ "$live_return_scroll" = "0" ]; }; then
        break
    fi
    sleep 0.05
done
live_return_elapsed_ms=$(( $(date +%s%3N) - live_return_start_ms ))
if [ "$live_return_mode" != "0" ] || { [ -n "$live_return_scroll" ] && [ "$live_return_scroll" != "0" ]; }; then
    echo "phone menu UI proof failed: physical one-finger down swipe did not return to live bottom quietly; mode=$live_return_mode scroll=$live_return_scroll" >&2
    exit 1
fi
if [ "$live_return_elapsed_ms" -gt 1200 ]; then
    echo "phone menu UI proof failed: physical one-finger live-bottom return was too delayed; elapsed_ms=$live_return_elapsed_ms mode=$live_return_mode scroll=$live_return_scroll" >&2
    exit 1
fi
echo "Physical one-finger live-bottom exited copy-mode quietly and smoothly in ${live_return_elapsed_ms}ms"
control_get "/scroll?where=bottom" >/dev/null || true
wait_for_pane_mode "0"

ensure_toolbar
open_scroll_menu
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
press_back
ensure_toolbar
open_scroll_menu
tap_visible_text_from_current_dump "Page up"
wait_for_pane_mode "1"
ensure_toolbar
open_scroll_menu
tap_visible_text_from_current_dump "Page down"
wait_for_pane_mode "1"
ensure_toolbar
open_scroll_menu
tap_visible_text_from_current_dump "Go to live bottom / type"
wait_for_pane_mode "0"

echo "phone menu UI proof: command palette duplicate actions"
open_command_palette
assert_text "Active Sessions"
assert_text "Old Sessions"
assert_text "Restore Crashed Sessions"
assert_text "Needs Attention"
assert_text "Refresh current session"
assert_absent "Previous Sessions"
assert_absent "Open full session reader"
tap_visible_text_above_toolbar_from_current_dump "Needs Attention"
wait_for_visible_text "Needs Attention"
press_back

tap_command_palette_action_and_wait "Active Sessions"
press_back

tap_command_palette_action_and_wait "Old Sessions"
press_back

tap_command_palette_action_and_wait "Restore Crashed Sessions"
press_back

tap_command_palette_action_and_wait "Copy/Paste" "Paste phone clipboard into terminal"
assert_text "Upload media from phone"
press_back
assert_text "Upload"

open_command_palette
tap_text "Refresh current session"
sleep 1.0
if [ "$(tmux_active_window)" != "$proof_window" ]; then
    echo "phone menu UI proof failed: command-palette Refresh switched sessions" >&2
    exit 1
fi

open_command_palette
scroll_until_text "Rename current session"
tap_visible_text_from_current_dump "Rename current session"
assert_text "Rename current session"
press_back

bug_dir="${XDG_RUNTIME_DIR:-/tmp}/phone-terminal/bug-reports"
mkdir -p "$bug_dir"
bug_before="$(find "$bug_dir" -maxdepth 1 -type f -name 'report-*.json' | wc -l)"
open_command_palette
scroll_until_text "Create bug report"
tap_visible_text_from_current_dump "Create bug report"
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

open_command_palette
scroll_until_text "Install/update over Tailscale"
tap_visible_text_from_current_dump "Install/update over Tailscale"
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
before_composer_terminal_height="$(terminal_webview_height)"
before_composer_pane_height="$(tmux_window_pane_height "$proof_window")"
# WHY: the repeated real-phone regression was caused by normal typing touching
# tmux before the user pressed Send. Gboard/voice composition could duplicate
# words, leave an invisible tmux draft that Backspace could not edit, or paste
# into a different Active tab after a late callback. Prove the draft stays local
# before Send, then count the file output after Send so duplicate-submit
# regressions still fail.
terminal_tap_for_typing
sleep 0.8
assert_regex '(text|content-desc|hint)="Type prompt"' "native composer opened from terminal tap"
echo "Native composer opened from terminal tap"
assert_composer_terminal_refit "$before_composer_terminal_height" "$before_composer_pane_height"
assert_absent "Cancel"
adb_cmd shell dumpsys input_method > /tmp/wezterm-ime-proof.txt || true
adb_cmd exec-out screencap -p > /tmp/wezterm-v166-native-composer-proof.png
adb_cmd shell input text "${TYPE_TOKEN}${TYPE_BAD_SUFFIX}"
if tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -20 -E - | grep -Fq "$TYPE_TOKEN"; then
    echo "phone menu UI proof failed: native composer text reached tmux before toolbar Send" >&2
    tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -20 -E - >&2 || true
    exit 1
fi
echo "Native composer stayed local before Send; no pre-send tmux draft was created"
# WHY: the user specifically reported that text reached tmux/Codex while the
# native composer looked empty or could not be edited. The durable fix is the
# opposite: the bad suffix must be removable locally before Send, and the final
# file must contain the intended token once with no hidden pre-send draft.
for _ in $(seq 1 "${#TYPE_BAD_SUFFIX}"); do
    adb_cmd shell input keyevent KEYCODE_DEL
    sleep 0.15
done
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
if grep -Fq "$TYPE_BAD_SUFFIX" "$TYPE_FILE"; then
    echo "phone menu UI proof failed: native composer Backspace did not remove bad suffix before Send" >&2
    cat "$TYPE_FILE" >&2 || true
    exit 1
fi
if [ "$after_tap_mode" != "0" ] || { [ -n "$after_tap_scroll" ] && [ "$after_tap_scroll" != "0" ]; }; then
    echo "phone menu UI proof failed: native composer submit changed mode/scroll unexpectedly; before mode=$before_tap_mode scroll=$before_tap_scroll after mode=$after_tap_mode scroll=$after_tap_scroll" >&2
    exit 1
fi
echo "Native composer Backspace edited local text before Send"
grep -Fq 'TYPE_TEXT_VARIATION_NORMAL' "$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
if grep -Eq 'TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|IME_FLAG_NO_PERSONALIZED_LEARNING' "$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"; then
    echo "phone menu UI proof failed: private/password IME flags returned" >&2
    exit 1
fi
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
echo "Tap-to-type delivered one visible token through native composer"
echo "Native composer stayed local until Send and delivered one visible token; screenshot: /tmp/wezterm-v166-native-composer-proof.png; IME dump: /tmp/wezterm-ime-proof.txt"
echo "Tap-to-type did not trigger page-finished or scroll bursts"
echo "IME flags stayed normal for voice input"

echo "phone menu UI proof: native composer pins draft target across active drift"
drift_window="$(tmux new-window -d -P -F '#{window_id}' -t "$TMUX_SESSION:" -n "$PROOF_NAME Drift")"
wait_for_shell "$drift_window"
tmux send-keys -t "$TMUX_SESSION:$drift_window" "rm -f '$DRIFT_OTHER_FILE'; cat > '$DRIFT_OTHER_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$drift_window" '#{pane_current_command}')" = "cat" ]; then
        break
    fi
    sleep 0.2
done
select_window "$proof_window"
wait_for_active_window_id "$proof_window"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$DRIFT_FILE'; cat > '$DRIFT_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "cat" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
terminal_tap_for_typing
sleep 0.8
assert_regex '(text|content-desc|hint)="Type prompt"' "native composer opened for draft-target drift proof"
adb_cmd shell input text "$DRIFT_TOKEN"
if tmux capture-pane -p -t "$TMUX_SESSION:$proof_window" -S -20 -E - | grep -Fq "$DRIFT_TOKEN"; then
    echo "phone menu UI proof failed: draft-target proof text reached original tmux before Send" >&2
    exit 1
fi
# WHY: this recreates the user-reported wrong-session paste class. The visible
# draft was typed while `proof_window` owned the composer, then the live tmux
# active target is moved to a different disposable window before tapping Send.
# Send must still use the pinned draft `@windowId`, not the later `/active`.
select_window "$drift_window"
wait_for_active_window_id "$drift_window"
sleep 1.5
tap_text "Send"
wait_for_file_text "$DRIFT_FILE" "$DRIFT_TOKEN"
if [ -f "$DRIFT_OTHER_FILE" ] && grep -Fq "$DRIFT_TOKEN" "$DRIFT_OTHER_FILE"; then
    echo "phone menu UI proof failed: pinned native draft was pasted into drift target $drift_window" >&2
    cat "$DRIFT_OTHER_FILE" >&2 || true
    exit 1
fi
# WHY: the proof_window intentionally runs `cat > $DRIFT_FILE` while the native
# composer draft is sent. Once the file contains the pinned draft, stop that cat
# before asserting shell recovery; otherwise the harness falsely blames the APK
# for the disposable pane still doing exactly what the proof asked it to do.
tmux send-keys -t "$TMUX_SESSION:$proof_window" C-c
wait_for_shell "$proof_window"
tmux send-keys -t "$TMUX_SESSION:$drift_window" C-c
wait_for_shell "$drift_window"
select_window "$proof_window"
wait_for_shell "$proof_window"
echo "Native composer pinned draft target survived active-window drift"

echo "phone menu UI proof: CLI keys support variable-length option prompts"
# WHY: Option Keys uses the APK's remembered stable `@windowId`, not a direct
# control-server `/select` call. Use a dedicated keys window, wait until the
# phone's fast `/tabs?light=1` picker reports that same immutable id as active,
# then tap the pinned Current row before sending Backspace/Delete/Home/End.
# Without this, the proof can pass against tmux's active window while the APK's
# remembered Option Keys target still points at an older user tab.
cli_keys_window="$(tmux new-window -d -P -F '#{window_id}' -t "$TMUX_SESSION:" -n "$CLI_KEYS_TITLE")"
wait_for_shell "$cli_keys_window"
select_window "$cli_keys_window"
wait_for_active_window_id "$cli_keys_window"
cli_keys_visible_title="$(wait_for_light_active_window_title "$cli_keys_window")"
cli_keys_visible_row="Current: $cli_keys_visible_title"
ensure_plain_toolbar
open_active_picker
assert_text "$cli_keys_visible_row"
tap_visible_text_from_current_dump "$cli_keys_visible_row"
wait_for_active_window_id_after_visible_tap "$cli_keys_window" "$cli_keys_visible_row"
cat > "$CLI_KEYS_SCRIPT" <<'PY'
import pathlib
import select
import sys
import termios
import tty

out = pathlib.Path(sys.argv[1])
items = ["alpha", "bravo", "charlie", "delta", "echo"]
selected = 0

def marker(suffix, text):
    out.with_suffix(out.suffix + suffix).write_text(text, encoding="utf-8")

def render():
    sys.stdout.write("\r\nCLI_KEYS_PICKER five options\r\n")
    for index, item in enumerate(items):
        marker = ">" if index == selected else " "
        sys.stdout.write(f"{marker} {index + 1}. {item}\r\n")
    sys.stdout.flush()

def read_escape_sequence(fd):
    sequence = b""
    while True:
        ready, _, _ = select.select([fd], [], [], 0.06 if not sequence else 0.02)
        if not ready:
            return sequence
        chunk = sys.stdin.buffer.read(1)
        sequence += chunk
        if chunk in b"~ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz":
            return sequence
        if len(sequence) >= 8:
            return sequence

fd = sys.stdin.fileno()
old = termios.tcgetattr(fd)
tty.setraw(fd)
try:
    render()
    while True:
        data = sys.stdin.buffer.read(1)
        if data == b"\x1b":
            seq = read_escape_sequence(fd)
            if seq in (b"[A", b"OA"):
                selected = max(0, selected - 1)
                render()
            elif seq in (b"[B", b"OB"):
                selected = min(len(items) - 1, selected + 1)
                render()
            elif seq in (b"[H", b"OH", b"[1~", b"[7~"):
                selected = 0
                marker(".home", "home")
                render()
            elif seq in (b"[F", b"OF", b"[4~", b"[8~"):
                selected = len(items) - 1
                marker(".end", "end")
                render()
            elif seq == b"[3~":
                marker(".delete", "delete")
            elif seq == b"":
                marker(".escape", "escape")
                sys.stdout.write("\r\nescaped\r\n")
                sys.stdout.flush()
                break
        elif data in (b"\x7f", b"\b"):
            marker(".backspace", "backspace")
        elif data in (b"\r", b"\n"):
            out.write_text(items[selected], encoding="utf-8")
            sys.stdout.write(f"\r\nselected {items[selected]}\r\n")
            sys.stdout.flush()
            break
finally:
    termios.tcsetattr(fd, termios.TCSADRAIN, old)
PY
tmux send-keys -t "$TMUX_SESSION:$cli_keys_window" "rm -f '$CLI_KEYS_FILE' '$CLI_KEYS_FILE.backspace' '$CLI_KEYS_FILE.delete' '$CLI_KEYS_FILE.home' '$CLI_KEYS_FILE.end' '$CLI_KEYS_FILE.escape'; python3 '$CLI_KEYS_SCRIPT' '$CLI_KEYS_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$cli_keys_window" '#{pane_current_command}')" = "python3" ]; then
        break
    fi
    sleep 0.2
done
wait_for_capture_text "$cli_keys_window" "CLI_KEYS_PICKER five options"
open_option_keys_menu
tap_option_key_from_open_dialog "Backspace"
wait_for_file_text "$CLI_KEYS_FILE.backspace" "backspace"
tap_option_key_from_open_dialog "Delete"
wait_for_file_text "$CLI_KEYS_FILE.delete" "delete"
# WHY: do not prove CLI prompts through fixed `1`/`2`/`3` shortcuts. Real
# Claude/Codex prompts can contain four, five, or more options, so the installed
# APK must move through the highlighted list item with Up/Down and select it
# with Enter. This single persistent dialog open sends editing, Home/End, and
# list movement keys before selecting `delta` with End then Up.
tap_option_key_from_open_dialog "Home"
wait_for_file_text "$CLI_KEYS_FILE.home" "home"
tap_option_key_from_open_dialog "Move down"
tap_option_key_from_open_dialog "Move down"
tap_option_key_from_open_dialog "End"
wait_for_file_text "$CLI_KEYS_FILE.end" "end"
tap_option_key_from_open_dialog "Move up"
tap_option_key_from_open_dialog "Select"
wait_for_file_text "$CLI_KEYS_FILE" "delta"
if [ "$(tr -d '\r\n' < "$CLI_KEYS_FILE")" != "delta" ]; then
    echo "phone menu UI proof failed: CLI keys selected wrong option; expected delta, got $(cat "$CLI_KEYS_FILE")" >&2
    exit 1
fi
wait_for_shell "$cli_keys_window"
tmux send-keys -t "$TMUX_SESSION:$cli_keys_window" "rm -f '$CLI_KEYS_FILE.escape'; python3 '$CLI_KEYS_SCRIPT' '$CLI_KEYS_FILE'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$cli_keys_window" '#{pane_current_command}')" = "python3" ]; then
        break
    fi
    sleep 0.2
done
wait_for_capture_text "$cli_keys_window" "CLI_KEYS_PICKER five options"
open_option_keys_menu
# WHY: Escape is a deliberate close/cancel key, so it is proven in a second
# dialog open after Select closed the first prompt. The primary dialog above is
# still the persistent path: Backspace, Delete, Home, End, Up/Down, and Select
# were all tapped without reopening.
tap_option_key_from_open_dialog "Escape"
wait_for_file_text "$CLI_KEYS_FILE.escape" "escape"
dump_ui
if dump_has_text "Option keys"; then
    echo "phone menu UI proof failed: Escape did not close the option-key dialog" >&2
    exit 1
fi
wait_for_shell "$cli_keys_window"
echo "Backspace, Delete, Home, End, and Escape key controls reached tmux while composer stayed phone-owned"
echo "CLI keys selected variable-length option through persistent Up/Down/Home/End/Enter controls"
cleanup_window "$cli_keys_window" "$CLI_KEYS_TITLE"
cli_keys_window=""
select_window "$proof_window"
wait_for_active_window_id "$proof_window"
wait_for_shell "$proof_window"

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

echo "phone menu UI proof: Stop sends one Escape and does not submit a visible native draft"
tmux send-keys -t "$TMUX_SESSION:$proof_window" "rm -f '$STOP_FILE'; python3 -c 'import sys,pathlib,termios,tty; fd=sys.stdin.fileno(); old=termios.tcgetattr(fd); tty.setraw(fd); data=sys.stdin.buffer.read(1); termios.tcsetattr(fd, termios.TCSADRAIN, old); pathlib.Path(\"$STOP_FILE\").write_bytes(data)'" Enter
for _ in $(seq 1 40); do
    if [ "$(tmux display-message -p -t "$TMUX_SESSION:$proof_window" '#{pane_current_command}')" = "python3" ]; then
        break
    fi
    sleep 0.2
done
ensure_toolbar
terminal_tap_for_typing
sleep 0.8
assert_regex '(text|content-desc|hint)="Type prompt"' "native composer opened for Stop Escape proof"
adb_cmd shell input text "$STOP_VISIBLE_DRAFT_TOKEN"
tap_text "Stop"
wait_for_stop_escape
wait_for_shell "$proof_window"
echo "Stop button delivered one Escape without submitting the visible native draft"

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
