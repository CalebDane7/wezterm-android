#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
CONTROL_SERVER="${PHONE_CONTROL_SERVER:-$HOME/.local/bin/phone-terminal-control-server}"
PHONE_TERMINAL="${PHONE_TERMINAL:-$HOME/.local/bin/phone-terminal}"

require() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    if ! grep -Fq -- "$pattern" "$file"; then
        echo "Claude-spec Phase 0 guard failed: $message" >&2
        echo "Missing pattern: $pattern" >&2
        echo "File: $file" >&2
        exit 1
    fi
}

require_absent() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    if grep -Fq -- "$pattern" "$file"; then
        echo "Claude-spec Phase 0 guard failed: $message" >&2
        echo "Forbidden pattern: $pattern" >&2
        echo "File: $file" >&2
        exit 1
    fi
}

require_method_contract() {
    local file="$1"
    local method="$2"
    shift 2
    python3 - "$file" "$method" "$@" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
method = sys.argv[2]
checks = sys.argv[3:]
source = path.read_text(encoding="utf-8")
match = re.search(r"(?:public|private|protected)\s+[\w<>\[\], ?]+\s+" + re.escape(method) + r"\s*\(", source)
if match is None:
    raise SystemExit(f"Claude-spec Phase 0 guard failed: missing method {method} in {path}")
brace = source.find("{", match.start())
if brace < 0:
    raise SystemExit(f"Claude-spec Phase 0 guard failed: missing method body for {method} in {path}")
depth = 0
end = None
for index in range(brace, len(source)):
    char = source[index]
    if char == "{":
        depth += 1
    elif char == "}":
        depth -= 1
        if depth == 0:
            end = index + 1
            break
if end is None:
    raise SystemExit(f"Claude-spec Phase 0 guard failed: unterminated method {method} in {path}")
body = source[brace:end]
for raw_check in checks:
    mode, pattern, message = raw_check.split("\t", 2)
    present = pattern in body
    if mode == "has" and not present:
        raise SystemExit(f"Claude-spec Phase 0 guard failed: {message}\nMissing pattern: {pattern}\nMethod: {method}\nFile: {path}")
    if mode == "absent" and present:
        raise SystemExit(f"Claude-spec Phase 0 guard failed: {message}\nForbidden pattern: {pattern}\nMethod: {method}\nFile: {path}")
print(f"{method}: ok")
PY
}

require_python_function_contract() {
    local file="$1"
    local function="$2"
    shift 2
    python3 - "$file" "$function" "$@" <<'PY'
import ast
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
function = sys.argv[2]
checks = sys.argv[3:]
source = path.read_text(encoding="utf-8")
tree = ast.parse(source, filename=str(path))
target = None
for node in ast.walk(tree):
    if isinstance(node, ast.FunctionDef) and node.name == function:
        target = node
        break
if target is None:
    raise SystemExit(f"Claude-spec Phase 0 guard failed: missing function {function} in {path}")
body = ast.get_source_segment(source, target) or ""
for raw_check in checks:
    mode, pattern, message = raw_check.split("\t", 2)
    present = pattern in body
    if mode == "has" and not present:
        raise SystemExit(f"Claude-spec Phase 0 guard failed: {message}\nMissing pattern: {pattern}\nFunction: {function}\nFile: {path}")
    if mode == "absent" and present:
        raise SystemExit(f"Claude-spec Phase 0 guard failed: {message}\nForbidden pattern: {pattern}\nFunction: {function}\nFile: {path}")
print(f"{function}: ok")
PY
}

check_browser_overlay_gate() {
    # WHY: Claude's browser-parity recommendation is a real architecture change,
    # not permission to point users at raw ttyd or bolt unauthenticated buttons
    # onto the current control server. The browser remote is now an authenticated
    # overlay on the control server, so it must keep token, origin, CSRF, and
    # Active-switch paint shielding instead of falling back to raw ttyd behavior.
    if grep -Eq 'WEZTERM_WEB_OVERLAY|phone-web-overlay|parsed.path == "/web"|parsed.path == "/app.js"|parsed.path == "/app.css"' \
        "$CONTROL_SERVER" "$PHONE_TERMINAL" "$MAIN"; then
        require "$CONTROL_SERVER" 'PHONE_CONTROL_TOKEN' "browser overlay must require an explicit control token"
        require "$CONTROL_SERVER" 'Origin' "browser overlay must inspect request Origin/Host before mutating tmux"
        require "$CONTROL_SERVER" 'CSRF' "browser overlay must carry a CSRF guard or equivalent same-origin mutation token"
        require "$CONTROL_SERVER" 'WEZTERM_WEB_OVERLAY_ENABLED' "browser overlay must be feature-flagged until APK/browser parity proof is green"
        require "$CONTROL_SERVER" 'parsed.path == "/web"' "browser overlay must keep the authenticated web shell route"
        require "$CONTROL_SERVER" 'parsed.path == "/web/app.js"' "browser overlay must keep the authenticated web app bundle route"
        require "$CONTROL_SERVER" 'data-web-switch-shield' "browser overlay must shield the terminal during Active-session switches"
        require "$CONTROL_SERVER" '__weztermWebSwitchShieldState' "browser overlay must expose switch-shield state for real browser proof"
        require "$CONTROL_SERVER" 'openActiveWindow' "browser overlay must route Active rows through the shielded switch action"
        require "$CONTROL_SERVER" 'showActive(){const payload=await api("/tabs?light=1")' "browser Active picker must use the fast tabs payload"
        require "$CONTROL_SERVER" 'el.dataset.windowId' "browser overlay must bind Active rows to explicit tmux window ids"
        require_absent "$CONTROL_SERVER" 'Access-Control-Allow-Origin", "*"' "browser overlay cannot keep wildcard CORS on mutating controls"
        echo "browser overlay gate: authenticated web shell contract present"
    else
        echo "browser overlay gate: no shared web overlay implemented yet"
    fi
}

check_local_history_gate() {
    # WHY: local/offline scrollback is the 10x direction, but it must start as a
    # read-only server cache edge before Android renders or stores local history.
    # C4 may add `/scrollback/chunk` by itself; that must be bounded and
    # side-effect-free. Only when Android introduces IndexedDB/ServiceWorker or a
    # native LocalHistory UI do we require the APK-facing cache wiring.
    if grep -Eq 'scrollback/chunk|bounded_scrollback_chunk' "$CONTROL_SERVER"; then
        require "$CONTROL_SERVER" 'def bounded_scrollback_chunk' "server history chunk endpoint must have a dedicated read-only owner"
        require "$CONTROL_SERVER" 'scrollback/chunk' "server history chunk endpoint must keep the planned route"
        require "$CONTROL_SERVER" 'windowId:paneId:generation' "server history chunks must expose the stable cache key contract"
        require_python_function_contract "$CONTROL_SERVER" "bounded_scrollback_chunk" \
            $'has\tcapture-pane\tserver history chunks must come from bounded tmux capture or an explicit transcript source' \
            $'has\tSCROLLBACK_CHUNK_MAX_LINES\tserver history chunks must cap requested row counts' \
            $'has\treadOnly\tserver history chunk payload must advertise read-only semantics' \
            $'has\twindowId:paneId:generation\tserver history chunks must expose the stable cache key contract' \
            $'absent\trun_tmux("select-window"\thistory chunk endpoint must not select tmux windows as a side effect' \
            $'absent\trun_tmux("send-keys"\thistory chunk endpoint must not send tmux keys as a side effect' \
            $'absent\trun_tmux("copy-mode"\thistory chunk endpoint must not enter tmux copy-mode as a side effect' \
            $'absent\tvisible_capture\thistory chunk endpoint must not gather visible proof data' \
            $'absent\tpane_codex_info\thistory chunk endpoint must not inspect Codex process state'
    else
        echo "local history gate: no read-only server history chunk implemented yet"
    fi

    if grep -Eq 'IndexedDB|ServiceWorkerController|WebViewAssetLoader|LocalHistory|read-only local history' "$MAIN"; then
        require "$MAIN" 'windowId:paneId:generation' "local history cache must key chunks by stable window/pane/generation"
        require "$MAIN" 'read-only local history' "local history UI must document that it is not the live PTY input layer"
        require "$MAIN" 'Search cached history' "local history UI must expose local search over cached rows"
        require "$MAIN" '"/scrollback/chunk?windowId="' "local history UI must fetch bounded chunks from the C4 endpoint"
        require "$CONTROL_SERVER" 'scrollback/chunk' "local history must fetch bounded server chunks instead of scraping the live WebView"
        require "$CONTROL_SERVER" '"capture-pane"' "server history chunks must come from bounded tmux capture or an explicit transcript source"
        require_python_function_contract "$CONTROL_SERVER" "bounded_scrollback_chunk" \
            $'absent\trun_tmux("select-window"\thistory chunk endpoint must not select tmux windows as a side effect' \
            $'absent\trun_tmux("send-keys"\thistory chunk endpoint must not send tmux keys as a side effect' \
            $'absent\trun_tmux("copy-mode"\thistory chunk endpoint must not enter tmux copy-mode as a side effect'
        require_method_contract "$MAIN" "showLocalHistoryDialog" \
            $'has\tread-only local history\tlocal history dialog must preserve the read-only WHY boundary' \
            $'has\twindowId:paneId:generation\tlocal history dialog must show the stable cache-key contract' \
            $'has\tSearch cached history\tlocal history dialog must search cached rows locally' \
            $'absent\twebView.reload\tthe local history dialog must not reload WebView' \
            $'absent\tenterReadMode\tthe local history dialog must not enter tmux copy-mode/read mode' \
            $'absent\tscrollTerminal\tthe local history dialog must not route into live scroll controls'
    else
        echo "local history gate: no Android local/offline history UI implemented yet"
    fi
}

require "$PHONE_TERMINAL" '--check-origin' "ttyd must keep websocket origin checking while browser parity is not hardened"
require "$PHONE_TERMINAL" '--client-option "scrollback=$PHONE_SCROLLBACK"' "ttyd must keep the current large browser scrollback cap"
require "$PHONE_TERMINAL" '--client-option smoothScrollDuration=0' "ttyd smooth scrolling must stay disabled until latency baselines prove a change"
require "$PHONE_TERMINAL" 'PHONE_TITLE_SYNC_ENABLED:-1' "mantis-title-sync must stay default-on as the guarded shared title/status authority"
require "$PHONE_TERMINAL" 'shared title/status authority' "title daemon WHY comment must preserve the current stale-cache/title-drift fix"
require_absent "$PHONE_TERMINAL" 'phone title sync refused: set PHONE_TITLE_WINDOW_IDS' "browser/performance proof must not restore the legacy broad-sync allowlist refusal"
require "$PHONE_TERMINAL" 'web_control_url' "phone-terminal url must point at the authenticated web shell, not raw ttyd"
require "$PHONE_TERMINAL" '/web?token=' "phone-terminal browser URL must include the web control token"
require "$PHONE_TERMINAL" 'CONTROL_SERVER="$CONTROL_BIN" python3 <<' "phone-terminal url must import the configured control server for the shared web token"
require "$PHONE_TERMINAL" 'SourceFileLoader("phone_terminal_control_server_web_token", path)' "phone-terminal url must import the control server beside shared helper modules"
require "$PHONE_TERMINAL" 'raw-url' "raw ttyd URL must remain available only as an explicit recovery path"
require "$PHONE_TERMINAL" 'start_web_bridge' "phone-terminal must keep the localhost browser bridge for Windows Chrome proof"
require "$PHONE_TERMINAL" 'socat' "phone-terminal localhost browser bridge must remain explicit and inspectable"

require "$CONTROL_SERVER" 'PHONE_CONTROL_TOKEN' "browser overlay must require an explicit control token"
require "$CONTROL_SERVER" 'Origin' "browser overlay must inspect request Origin/Host before browser control requests"
require "$CONTROL_SERVER" 'CSRF' "browser overlay must carry a CSRF guard or equivalent same-origin mutation token"
require "$CONTROL_SERVER" 'WEZTERM_WEB_OVERLAY_ENABLED' "browser overlay must remain feature-flagged"
require "$CONTROL_SERVER" 'data-web-switch-shield' "browser overlay must shield Active-session switches in the website remote"
require "$CONTROL_SERVER" '__weztermWebSwitchShieldState' "browser overlay must expose switch-shield state for browser proof"
require "$CONTROL_SERVER" 'openActiveWindow' "browser overlay must switch Active rows through the shielded action"
require "$CONTROL_SERVER" 'showActive(){const payload=await api("/tabs?light=1")' "browser Active picker must stay on the fast tabs payload"
require "$CONTROL_SERVER" 'el.dataset.windowId' "browser overlay must bind Active rows to explicit tmux window ids"
require_absent "$CONTROL_SERVER" 'Access-Control-Allow-Origin", "*"' "browser overlay must not leave wildcard CORS on control responses"
require "$CONTROL_SERVER" 'elif parsed.path == "/tabs":' "browser/API contract must keep tabs endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/sessions":' "browser/API contract must keep sessions endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/active":' "browser/API contract must keep active endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/select-live":' "browser/API contract must keep select-live endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/live-bottom":' "browser/API contract must keep live-bottom endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/touch-scroll":' "browser/API contract must keep low-latency touch-scroll endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/stop":' "browser/API contract must keep Stop endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/send-enter":' "browser/API contract must keep Start/Enter endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/copy-visible":' "browser/API contract must keep Copy/Paste copy endpoint"
require "$CONTROL_SERVER" 'if parsed.path == "/paste":' "browser/API contract must keep Copy/Paste paste endpoint"
require "$CONTROL_SERVER" 'if parsed.path == "/upload-media":' "browser/API contract must keep Upload endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/close":' "browser/API contract must keep Close endpoint"
require "$CONTROL_SERVER" 'elif parsed.path == "/new":' "browser/API contract must keep New endpoint"

require "$MAIN" 'toolbarNavigationButton("Active"' "APK toolbar must still expose Active"
require "$MAIN" 'toolbarNavigationButton("Old"' "APK toolbar must still expose Old"
require "$MAIN" 'toolbarNavigationButton("New"' "APK toolbar must still expose New"
require "$MAIN" 'toolbarNavigationButton("Bottom"' "APK toolbar must still expose Bottom"
require "$MAIN" 'toolbarNavigationButton("Settings"' "APK toolbar must expose Settings for secondary recovery actions and viewport mode"
require "$MAIN" 'refreshTerminalTransport();' "APK Settings must keep Refresh reachable"
require "$MAIN" 'showViewControls();' "APK Settings must keep Scroll reachable"
require_absent "$MAIN" '"Viewport mode choices"' "APK Settings must keep viewport mode on the single top switch row"
require "$MAIN" 'Switch to Desktop viewport' "APK Settings must expose an obvious Mobile-to-Desktop viewport switch"
require "$MAIN" 'toolbarNavigationButton("Copy/Paste"' "APK toolbar must still expose Copy/Paste"
require "$MAIN" 'toolbarNavigationButton("Upload"' "APK toolbar must still expose Upload"
require "$MAIN" 'toolbarNavigationButton("Close"' "APK toolbar must still expose Close"
require "$MAIN" 'toolbarButton("Start"' "APK toolbar must still expose Start"
require "$MAIN" 'toolbarButton("Stop"' "APK toolbar must still expose Stop"

require_method_contract "$MAIN" "sendHistoryScrollFromTouch" \
    $'has\t/touch-scroll?where=\tone-finger MOVE must stay on the lightweight touch-scroll endpoint' \
    $'absent\t/scroll?where=\tone-finger MOVE must not call the heavier proof scroll endpoint' \
    $'absent\t/tabs\tone-finger MOVE must not poll tab/session data' \
    $'absent\tgetJsonWithRetry\tone-finger MOVE must not use retry loops that queue stale movement' \
    $'absent\tsampleVisibleWebViewPaint\tone-finger MOVE must not do visible bitmap proof work'

require_method_contract "$MAIN" "touchScrollReachedLiveBottom" \
    $'has\tatLiveBottom\tAndroid must keep the explicit tmux live-bottom edge signal' \
    $'has\ttmux-linedown\tAndroid must only treat tmux lineDown edge data as live-bottom'

require_python_function_contract "$CONTROL_SERVER" "touch_scroll" \
    $'has\tself.tmux_scroll(target, normalized, repeat=repeat)\tserver touch-scroll must remain tmux-only' \
    $'absent\tvisible_capture\tserver touch-scroll must not gather visible proof data on MOVE' \
    $'absent\tpane_codex_info\tserver touch-scroll must not inspect Codex process state on MOVE' \
    $'absent\treader_source_window_id\tserver touch-scroll must not open or inspect reader windows on MOVE' \
    $'absent\tcodex_scroll\tserver touch-scroll must not route finger movement into Codex transcript pager'

check_browser_overlay_gate
check_local_history_gate

echo "Claude-spec Phase 0 guard passed"
