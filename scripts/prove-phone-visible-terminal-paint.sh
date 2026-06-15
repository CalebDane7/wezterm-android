#!/usr/bin/env bash
set -euo pipefail

ADB_SERIAL="${ADB_SERIAL:-100.77.22.120:5555}"
PACKAGE="${WEZTERM_PACKAGE:-com.kaleeb.wezterm}"
SCREENSHOT="${WEZTERM_VISIBLE_PAINT_SCREENSHOT:-}"
XML_DUMP="${WEZTERM_VISIBLE_PAINT_XML:-}"
OUT_SCREENSHOT="${WEZTERM_VISIBLE_PAINT_OUT:-/tmp/wezterm-visible-terminal-paint.png}"
OUT_XML="${WEZTERM_VISIBLE_PAINT_XML_OUT:-/tmp/wezterm-visible-terminal-paint.xml}"
MIN_RATIO="${WEZTERM_VISIBLE_PAINT_MIN_RATIO:-0.008}"
MIN_BRIGHT="${WEZTERM_VISIBLE_PAINT_MIN_BRIGHT:-350}"
MIN_ROWS="${WEZTERM_VISIBLE_PAINT_MIN_ROWS:-18}"

adb_cmd() {
    adb -s "$ADB_SERIAL" "$@"
}

if [ -z "$SCREENSHOT" ]; then
    adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | grep -F "$PACKAGE" >/dev/null || {
        echo "visible terminal paint proof failed: $PACKAGE is not foreground" >&2
        adb_cmd shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' >&2 || true
        exit 1
    }
    adb_cmd shell rm -f /sdcard/wezterm-visible-terminal-paint.xml >/dev/null 2>&1 || true
    adb_cmd shell uiautomator dump /sdcard/wezterm-visible-terminal-paint.xml >/tmp/wezterm-visible-terminal-paint-dump.log 2>&1
    adb_cmd pull /sdcard/wezterm-visible-terminal-paint.xml "$OUT_XML" >/tmp/wezterm-visible-terminal-paint-pull.log 2>&1
    XML_DUMP="$OUT_XML"
    adb_cmd exec-out screencap -p > "$OUT_SCREENSHOT"
    SCREENSHOT="$OUT_SCREENSHOT"
fi

python3 - "$SCREENSHOT" "$XML_DUMP" "$PACKAGE" "$MIN_RATIO" "$MIN_BRIGHT" "$MIN_ROWS" <<'PY'
from PIL import Image
import sys
import re

path = sys.argv[1]
xml_path = sys.argv[2]
package = sys.argv[3]
min_ratio = float(sys.argv[4])
min_bright = int(sys.argv[5])
min_rows = int(sys.argv[6])
image = Image.open(path).convert("RGB")
width, height = image.size
# WHY: the old screenshot proof passed a toolbar-alive/all-black WebView because
# it counted native composer/toolbar/keyboard pixels, tiny cursor pixels, or the
# scrollbar as "terminal text." The user-visible contract is that the terminal
# pane itself must show enough tmux/xterm paint that output is readable. Prefer
# UIAutomator WebView bounds when Android exposes them. On this Samsung/WebView
# stack the custom TerminalWebView can draw while UIAutomator omits the
# android.webkit.WebView node whenever the native composer/keyboard owns input,
# so the fallback is intentionally narrow: it is allowed only when the XML still
# contains WEzTerm package nodes, and it crops the upper terminal band only so
# toolbar/composer/keyboard pixels cannot make an all-black terminal pass.
left, top, right, bottom = 0, int(height * 0.04), width, int(height * 0.50)
if xml_path:
    try:
        text = open(xml_path, "r", encoding="utf-8", errors="replace").read()
        if package and f'package="{package}"' not in text:
            raise SystemExit(
                f"{package} package nodes missing from {xml_path}; "
                "refusing launcher/keyboard/toolbar false-positive proof"
            )
        candidates = []
        for node in re.findall(r"<node\b[^>]*>", text):
            if 'class="android.webkit.WebView"' not in node:
                continue
            if package and f'package="{package}"' not in node:
                continue
            match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            if not match:
                continue
            x1, y1, x2, y2 = map(int, match.groups())
            if x2 > x1 and y2 > y1:
                candidates.append(((x2 - x1) * (y2 - y1), x1, y1, x2, y2))
        if candidates:
            _, left, top, right, bottom = max(candidates)
        else:
            # Fall back to the upper terminal band only. Do not include the
            # toolbar/composer/keyboard region, because those native controls are
            # exactly what made earlier black-screen proofs lie.
            left, top, right, bottom = 0, int(height * 0.04), width, int(height * 0.50)
    except Exception as exc:
        raise SystemExit(f"could not parse WebView bounds from {xml_path}: {exc}")

bright = 0
samples = 0
row_hits = 0
max_row_hits = 0
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
    max_row_hits = max(max_row_hits, row_bright)
ratio = 0.0 if samples == 0 else bright / samples
if bright < min_bright or ratio < min_ratio or row_hits < min_rows:
    raise SystemExit(
        "visible terminal paint too low: "
        f"bright={bright} samples={samples} ratio={ratio:.4f} "
        f"rowHits={row_hits} maxRowHits={max_row_hits} "
        f"bounds=[{left},{top}][{right},{bottom}] screenshot={path}"
    )
print(
    f"visible terminal paint ok: bright={bright} samples={samples} "
    f"ratio={ratio:.4f} rowHits={row_hits} maxRowHits={max_row_hits} "
    f"bounds=[{left},{top}][{right},{bottom}] screenshot={path}"
)
PY
