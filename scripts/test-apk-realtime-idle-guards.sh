#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"

require() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    if ! grep -Fq -- "$pattern" "$file"; then
        echo "APK realtime idle guard failed: $message" >&2
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
        echo "APK realtime idle guard failed: $message" >&2
        echo "Forbidden pattern: $pattern" >&2
        echo "File: $file" >&2
        exit 1
    fi
}

python3 - "$MAIN" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text()


def method_body(name):
    marker = f"private void {name}("
    start = source.find(marker)
    if start == -1:
        marker = f"protected void {name}("
        start = source.find(marker)
    if start == -1:
        print(f"APK realtime idle guard failed: missing method {name}", file=sys.stderr)
        sys.exit(1)
    brace = source.find("{", start)
    if brace == -1:
        print(f"APK realtime idle guard failed: unbounded method {name}", file=sys.stderr)
        sys.exit(1)
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    print(f"APK realtime idle guard failed: unterminated method {name}", file=sys.stderr)
    sys.exit(1)


on_create = method_body("onCreate")
load_terminal = method_body("loadTerminalAtIndex")
renderer_load = method_body("loadTerminalRendererUrl")
reload_transport = method_body("reloadTerminalTransportOnly")
reload_reconnect = method_body("reloadTerminalForReconnect")
reload_visible_blank = method_body("reloadTerminalForVisibleBlank")
blank_probe_reload = method_body("handleTerminalPaintProbe")

set_content = on_create.find("setContentView(buildLayout(webView));")
load_call = on_create.find("loadTerminal();")
if set_content == -1 or load_call == -1 or not set_content < load_call:
    print(
        "APK realtime idle guard failed: cold start must publish the native shell before loading the terminal WebView",
        file=sys.stderr,
    )
    sys.exit(1)

before_load_call = on_create[:load_call]
forbidden_before_native_shell = (
    "getJson(",
    "getJsonWithRetry(",
    '"/tabs',
    '"/active',
    '"/terminal-frame',
    "showActiveSessions(",
    "scheduleCaptureRendererIdleRealtimeRefresh",
)
for forbidden in forbidden_before_native_shell:
    if forbidden in before_load_call:
        print(
            f"APK realtime idle guard failed: cold start must not wait on control endpoint before first native shell, found {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

load_url = load_terminal.find(
    "loadTerminalRendererUrl(terminalUrlWithOptions(activeTerminalBaseUrl, fontSize));"
)
if load_url == -1:
    print(
        "APK realtime idle guard failed: startup WebView load must keep the direct capture-renderer URL path",
        file=sys.stderr,
    )
    sys.exit(1)

before_webview_load = load_terminal[:load_url]
for forbidden in (
    "getJson(",
    "getJsonWithRetry(",
    '"/tabs',
    '"/active',
    '"/terminal-frame',
    "scheduleCaptureRendererIdleRealtimeRefresh",
):
    if forbidden in before_webview_load:
        print(
            f"APK realtime idle guard failed: WebView startup load must not block on control endpoint {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

if re.search(r"webView\.loadUrl\s*\(\s*controlUrlForPath", load_terminal):
    print(
        "APK realtime idle guard failed: startup must load the terminal renderer, not a control endpoint",
        file=sys.stderr,
    )
    sys.exit(1)

# WHY: the renderer now mints its authenticated SSE ticket from a non-secret
# APK client identity. Every initial/fallback/recovery navigation must pass the
# same header; a bare reload silently drops that bootstrap contract.
if 'private static final String CONTROL_CLIENT_VALUE = "wezterm-android";' not in source:
    print(
        "APK realtime idle guard failed: native renderer client identity drifted from wezterm-android",
        file=sys.stderr,
    )
    sys.exit(1)
for required in (
    'headers.put("X-Mantis-Client", CONTROL_CLIENT_VALUE)',
    "webView.loadUrl(url, headers)",
):
    if required not in renderer_load:
        print(
            f"APK realtime idle guard failed: renderer load helper lost {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)
for forbidden in ("Authorization", "Cookie", "Bearer", "token"):
    if forbidden in renderer_load:
        print(
            f"APK realtime idle guard failed: renderer bootstrap may not add secret-bearing {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)
if renderer_load.count("headers.put(") != 1:
    print(
        "APK realtime idle guard failed: renderer bootstrap helper must add only the non-secret client header",
        file=sys.stderr,
    )
    sys.exit(1)
for forbidden in ('"?token=', '"&token=', "webToken", "access_token"):
    if forbidden in source:
        print(
            f"APK realtime idle guard failed: APK renderer navigation regained forbidden web credential {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for name, body in (
    ("loadTerminalAtIndex", load_terminal),
    ("reloadTerminalTransportOnly", reload_transport),
    ("reloadTerminalForReconnect", reload_reconnect),
    ("reloadTerminalForVisibleBlank", reload_visible_blank),
    ("handleTerminalPaintProbe", blank_probe_reload),
):
    if "loadTerminalRendererUrl(" not in body:
        print(
            f"APK realtime idle guard failed: {name} bypasses the renderer client-header helper",
            file=sys.stderr,
        )
        sys.exit(1)
    if "webView.loadUrl(" in body or "webView.reload()" in body:
        print(
            f"APK realtime idle guard failed: {name} retains a bare ticket-minting load/reload",
            file=sys.stderr,
        )
        sys.exit(1)
if source.count("webView.loadUrl(") != 1 or "webView.reload()" in source:
    print(
        "APK realtime idle guard failed: every renderer navigation must funnel through one header-bearing loadUrl",
        file=sys.stderr,
    )
    sys.exit(1)

print("APK cold-start native-shell nonblocking guard passed")
PY

require "$MAIN" 'addJavascriptInterface(new CaptureRendererBridge(), "WeztermCaptureBridge")' "APK must install a native bridge for capture-renderer idle telemetry"
require "$MAIN" 'private final class CaptureRendererBridge' "APK must keep the capture-renderer bridge scoped inside MainActivity"
require "$MAIN" '@JavascriptInterface' "capture-renderer bridge methods must be exposed to WebView JavaScript"
require "$MAIN" 'installCaptureRendererTelemetryHook("page-finished")' "page-finished must install the passive renderer telemetry hook"
require "$MAIN" 'installCaptureRendererTelemetryHook("target-" + reason)' "target changes must reinstall the passive renderer telemetry hook"
require "$MAIN" 'handleCaptureRendererTelemetry' "APK must log privacy-safe passive renderer telemetry"
require "$MAIN" 'handleCaptureRendererIdleCommitRequest' "APK must route idle frame changes through a bounded visual commit request"
require "$MAIN" 'private static final long ANDROID_FRAME_BUDGET_MS = 16;' "active idle/live visual commits must stay at Android frame cadence"
require "$MAIN" 'CAPTURE_RENDERER_ACTIVE_VISIBLE_REPAINT_MAX_MS =' "active idle/live repaint cadence must stay explicitly guarded"
require "$MAIN" 'CAPTURE_RENDERER_IDLE_VISUAL_COMMIT_MIN_MS' "idle visual commits must be rate bounded"
require "$MAIN" 'CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_BASE_MS' "idle realtime watchdog must have an explicit base cadence"
require "$MAIN" 'CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_MAX_FAILURE_MS' "idle realtime watchdog must have a bounded failure backoff"
require "$MAIN" 'CAPTURE_RENDERER_IDLE_REALTIME_IN_FLIGHT_STALE_MS' "idle realtime watchdog must have a bounded stale-request fail-open"
require "$MAIN" 'CAPTURE_RENDERER_IDLE_REFRESH_REASON = "apk-idle-realtime"' "idle realtime refresh must use a distinct renderer reason"
require "$MAIN" 'lastIdleCaptureRendererVisualCommitAtMs' "idle visual commits must track the last commit time"
require "$MAIN" 'captureRendererIdleRealtimeRefreshGeneration' "idle realtime refresh must be generation-cancellable"
require "$MAIN" 'captureRendererIdleRealtimeRefreshInFlight' "idle realtime refresh must avoid overlapping renderer fetches"
require "$MAIN" 'captureRendererIdleRealtimeInFlightTelemetryGeneration' "idle realtime singleflight must bind the renderer telemetry generation"
require "$MAIN" 'captureRendererIdleRealtimeInFlightRequestedWindowId' "idle realtime singleflight must bind the requested target"
require "$MAIN" 'captureRendererIdleRealtimeInFlightFrameWindowId' "idle realtime singleflight must bind the returned frame target"
require "$MAIN" 'handleCaptureRendererIdleRealtimeTelemetry' "terminal-frame scheduled/response telemetry must own native watchdog state"
require "$MAIN" 'invalidateCaptureRendererIdleRealtimeInFlight' "target changes must revoke old-target native watchdog state"
require "$MAIN" 'reason=idle-frame-change' "idle DOM frame changes must request a distinct visual commit reason"
require "$MAIN" 'reason=idle-realtime-tick' "idle realtime tick fallback must have a distinct reason"
require "$MAIN" 'reason=apk-idle-realtime' "native idle refreshes must leave a distinct reason in logs"
require "$MAIN" 'stage=capture-renderer endpoint=/terminal-frame result=scheduled' "passive polling must log scheduled delay without terminal text"
require "$MAIN" 'stage=capture-renderer endpoint=/terminal-frame result=response' "terminal-frame fetch responses must log status/elapsed/safe frame metadata"
require "$MAIN" 'stage=capture-renderer endpoint=dom-apply result=changed' "DOM frame application must log safe hash/count metadata"
require "$MAIN" 'stage=capture-renderer endpoint=visual-commit-request result=requested' "idle frame changes must log Java-side visual commit requests"
require "$MAIN" 'stage=capture-renderer endpoint=idle-refresh result=scheduled' "native idle refresh heartbeat must log scheduling"
require "$MAIN" 'stage=capture-renderer endpoint=idle-refresh result=complete' "native idle refresh heartbeat must log completion"
require "$MAIN" 'stage=capture-renderer endpoint=visual-state result=complete' "visual-state completion logging must remain present"
require "$MAIN" 'skipReason=read-mode-hold' "APK idle/capture refresh must leave evidence when read mode blocks snap-back commits"
require "$MAIN" 'shouldHoldCaptureRendererPulseForReadMode' "APK touch-scroll pulses must not run generic renderer refreshes after the finger stops in read mode"
require "$MAIN" 'A background' "APK read-mode heartbeat WHY comment must preserve the scroll snap-back root cause"
require "$MAIN" 'generic idle telemetry can commit a fresh live-prompt frame after' "APK idle visual commit WHY comment must preserve the post-release snap-back root cause"
require "$MAIN" 'CAPTURE_RENDERER_TOUCH_VISUAL_FRAME_MIN_MS' "active touch visual commits must be frame-paced separately from idle fetch cadence"
require "$MAIN" 'requestCaptureRendererTouchVisualFrame' "active touch must invalidate the Android WebView compositor without fetching rows every frame"
require "$MAIN" 'stage=capture-renderer endpoint=touch-visual-frame result=requested' "active touch visual commits must leave safe frame-timing telemetry"
require "$MAIN" 'confirmedVisibleTerminalTargetKey()' "idle commits must verify the current confirmed visible target"
require "$MAIN" 'retargetedFrameAccepted(status,payload,requested,windowId,meta)' "terminal-frame telemetry must accept server-proven alias/replacement frames instead of strict request equality"
require "$MAIN" 'touchScrollTargetKey()' "one-finger touch-scroll must target the resolved interactive window when the selected display row is an alias"
require "$MAIN" 'stage=touch-scroll-target endpoint=/touch-scroll result=retargeted' "retargeted touch-scroll dispatch must leave safe target telemetry"
require "$MAIN" 'APK-SCROLL-ONE-FINGER-DELAY-BOUNDARY-1537-A' "reopened one-finger delayed-boundary row must be named in the fragile touch/bounds source comments"
require "$MAIN" 'terminalHistoryDragActive' "idle commits must stay quiet while a one-finger scroll is active"
require "$MAIN" 'terminalMultiTouchGesture' "idle commits must stay quiet while multi-touch owns the WebView"
require "$MAIN" 'terminalHorizontalPanActive' "idle commits must stay quiet while horizontal pan owns the WebView"
require "$MAIN" 'forceCaptureRendererVisualCommit("submit-text-success")' "Send success forced visual commit must stay intact"
require "$MAIN" 'forceCaptureRendererVisualCommit("target-" + reason)' "session target forced visual commit must stay intact"
require "$MAIN" 'forceCaptureRendererVisualCommit("resume")' "resume forced visual commit must stay intact"
require "$MAIN" 'forceCaptureRendererVisualCommit("page-finished")' "page-finished forced visual commit must stay intact"
require "$MAIN" 'scheduleCaptureRendererIdleRealtimeRefresh("resume")' "resume must start idle realtime refresh without requiring Send/session switch"
require "$MAIN" 'scheduleCaptureRendererIdleRealtimeRefresh("page-finished")' "page-finished must start idle realtime refresh without requiring Send/session switch"
require "$MAIN" 'scheduleCaptureRendererIdleRealtimeRefresh("target-" + reason)' "target changes must restart idle realtime refresh for the confirmed window"
require "$MAIN" 'stopCaptureRendererIdleRealtimeRefresh("pause")' "pause must stop idle realtime refresh callbacks"

python3 - "$MAIN" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text()
try:
    hook_start = source.index("private String captureRendererTelemetryHookScript(")
    hook_end = source.index("private void setCaptureRendererWindowTarget(", hook_start)
except ValueError as exc:
    print(f"APK realtime idle guard failed: could not locate telemetry hook body: {exc}", file=sys.stderr)
    sys.exit(1)
hook_body = source[hook_start:hook_end]
for required in (
    "acceptedWindowIds",
    "acceptedWindowIdSet(payload)",
    "acceptedIds.__present",
    "acceptedIds[requested]",
    "acceptedIds[windowId]",
    "acceptedWindowIds:payload&&payload.acceptedWindowIds",
):
    if required not in hook_body:
        print(
            f"APK realtime idle guard failed: retargeted frame acceptance must honor acceptedWindowIds metadata, missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

def accepts(payload, requested, window_id, body="ok\nrow"):
    status = payload.get("status", 200)
    if status < 200 or status >= 300:
        return False
    if not (window_id or "").startswith("@"):
        return False
    if not body:
        return False
    if not requested or requested == window_id:
        return True
    accepted_ids = {value for value in payload.get("acceptedWindowIds", []) if isinstance(value, str) and value.startswith("@")}
    display = payload.get("displayWindowId") or payload.get("requestedWindowId") or payload.get("selectedWindowId")
    resolved = (
        payload.get("frameWindowId")
        or payload.get("resolvedWindowId")
        or payload.get("replacementWindowId")
        or payload.get("interactiveTargetWindowId")
        or payload.get("targetWindowId")
        or payload.get("windowId")
    )
    marked = bool(
        payload.get("retargeted")
        or payload.get("replacementWindowId")
        or payload.get("interactiveTargetWindowId")
        or payload.get("resolvedWindowId")
        or accepted_ids
    )
    if accepted_ids:
        return marked and requested in accepted_ids and window_id in accepted_ids
    links_visible = requested == display or requested in accepted_ids
    links_frame = window_id == resolved or window_id in accepted_ids
    return marked and links_visible and links_frame

good = {
    "status": 200,
    "displayWindowId": "@48",
    "requestedWindowId": "@48",
    "frameWindowId": "@57",
    "resolvedWindowId": "@57",
    "replacementWindowId": "@57",
    "acceptedWindowIds": ["@48", "@57"],
}
wrong = {
    "status": 200,
    "displayWindowId": "@48",
    "requestedWindowId": "@48",
    "frameWindowId": "@99",
    "resolvedWindowId": "@99",
    "replacementWindowId": "@99",
    "acceptedWindowIds": ["@48", "@57"],
}
if not accepts(good, "@48", "@57"):
    print("APK realtime idle guard failed: @48 display/request frames retargeted to accepted @57 must be accepted", file=sys.stderr)
    sys.exit(1)
if accepts(wrong, "@48", "@99"):
    print("APK realtime idle guard failed: random wrong-session @99 frame must stay rejected", file=sys.stderr)
    sys.exit(1)
print("APK acceptedWindowIds retarget acceptance guard passed")
PY

python3 - "$MAIN" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text()
definitions = {}
for name, expr in re.findall(r"private static final (?:long|int) (\w+)\s*=\s*([^;]+);", source):
    definitions[name] = re.sub(r"\s+", " ", expr).strip()

def resolve(name, seen=None):
    seen = set() if seen is None else seen
    if name in seen:
        raise ValueError(f"circular constant reference: {name}")
    seen.add(name)
    expr = definitions.get(name)
    if expr is None:
        raise KeyError(name)
    if re.fullmatch(r"\d+[lL]?", expr):
        return int(expr.rstrip("lL"))
    if re.fullmatch(r"\w+", expr):
        return resolve(expr, seen)
    raise ValueError(f"unsupported expression for {name}: {expr}")

if "CAPTURE_RENDERER_ACTIVE_VISIBLE_REPAINT_MAX_MS" in definitions:
    max_ms = resolve("CAPTURE_RENDERER_ACTIVE_VISIBLE_REPAINT_MAX_MS")
    if max_ms >= 100:
        print(
            f"APK realtime idle guard failed: active visible repaint max must stay under 100ms, found {max_ms}",
            file=sys.stderr,
        )
        sys.exit(1)

for name in (
    "CAPTURE_RENDERER_IDLE_VISUAL_COMMIT_MIN_MS",
):
    value = resolve(name)
    if value >= 100:
        print(
            f"APK realtime idle guard failed: {name} must stay under 100ms, found {value}",
            file=sys.stderr,
        )
        sys.exit(1)

touch_pulse = resolve("TOUCH_SCROLL_RENDER_PULSE_MS")
if touch_pulse != 16:
    print(
        f"APK realtime idle guard failed: touch scroll pulse must remain 16ms, found {touch_pulse}",
        file=sys.stderr,
    )
    sys.exit(1)

touch_visual = resolve("CAPTURE_RENDERER_TOUCH_VISUAL_FRAME_MIN_MS")
if touch_visual > 33:
    print(
        f"APK realtime idle guard failed: touch visual compositor cadence must be 33ms or faster, found {touch_visual}",
        file=sys.stderr,
    )
    sys.exit(1)

watchdog_base = resolve("CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_BASE_MS")
watchdog_failure_max = resolve("CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_MAX_FAILURE_MS")
watchdog_stale = resolve("CAPTURE_RENDERER_IDLE_REALTIME_IN_FLIGHT_STALE_MS")
if watchdog_base != 2000:
    print(
        f"APK realtime idle guard failed: native watchdog base must stay 2000ms, found {watchdog_base}",
        file=sys.stderr,
    )
    sys.exit(1)
if watchdog_failure_max != 4000:
    print(
        f"APK realtime idle guard failed: native watchdog failure backoff must cap at 4000ms, found {watchdog_failure_max}",
        file=sys.stderr,
    )
    sys.exit(1)
if watchdog_stale <= 5000:
    print(
        "APK realtime idle guard failed: stale fail-open must not duplicate one "
        f"still-unresolved 5s terminal-frame request, found {watchdog_stale}ms",
        file=sys.stderr,
    )
    sys.exit(1)

# Old 96ms code produced 53 native timer opportunities during the observed 5s
# unresolved fetch. Model those exact opportunities: telemetry from the first
# actual request must keep all remaining opportunities singleflight until its
# matching response. Compositor feedback remains frame-rate-owned above.
old_timer_opportunities = 53
old_timer_step_ms = 96
request_count = 0
request_in_flight = False
request_started_ms = -1
for opportunity in range(old_timer_opportunities):
    now_ms = opportunity * old_timer_step_ms
    unresolved_and_current = request_in_flight and now_ms - request_started_ms < watchdog_stale
    if unresolved_and_current:
        continue
    request_count += 1
    request_in_flight = True
    request_started_ms = now_ms
if request_count != 1:
    print(
        "APK realtime idle guard failed: 53 native timer opportunities during "
        f"one unresolved fetch produced {request_count} terminal-frame requests",
        file=sys.stderr,
    )
    sys.exit(1)

print("APK 53-to-1 telemetry singleflight cadence guard passed")
PY

python3 - "$MAIN" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text()

def section(start_marker, end_markers):
    try:
        start = source.index(start_marker)
    except ValueError as exc:
        print(f"APK realtime idle guard failed: missing section {start_marker!r}: {exc}", file=sys.stderr)
        sys.exit(1)
    ends = [source.find(marker, start + len(start_marker)) for marker in end_markers]
    ends = [pos for pos in ends if pos != -1]
    if not ends:
        print(f"APK realtime idle guard failed: could not bound section {start_marker!r}", file=sys.stderr)
        sys.exit(1)
    return source[start:min(ends)]

idle = section(
    "private void handleCaptureRendererIdleCommitRequest(",
    ["private void handleCaptureRendererTelemetry(", "private void installCaptureRendererTelemetryHook("],
)
hook = section(
    "private String captureRendererTelemetryHookScript(",
    ["private void setCaptureRendererWindowTarget(", "private void focusTerminalInputSoon("],
)
idle_refresh = section(
    "private void runCaptureRendererIdleRealtimeRefresh(",
    ["private void commitCaptureRendererVisualFrame("],
)
idle_run = section(
    "private void runCaptureRendererIdleRealtimeRefresh(",
    ["private String shouldSkipCaptureRendererIdleRealtimeRefresh("],
)
idle_telemetry = section(
    "private void handleCaptureRendererIdleRealtimeTelemetry(",
    ["private void clearCaptureRendererIdleRealtimeInFlight("],
)
idle_clear = section(
    "private void clearCaptureRendererIdleRealtimeInFlight(",
    ["private void invalidateCaptureRendererIdleRealtimeInFlight("],
)
idle_invalidate = section(
    "private void invalidateCaptureRendererIdleRealtimeInFlight(",
    ["private void handleCaptureRendererIdleCommitRequest("],
)
telemetry_handler = section(
    "private void handleCaptureRendererTelemetry(",
    ["private JSONObject parseCaptureRendererBridgePayload("],
)
target_setter = section(
    "private void setCaptureRendererWindowTarget(String targetKey, String reason, int attempt)",
    ["private void markCaptureRendererWindowTargetConfirmed("],
)
target_key_owner = section(
    "private boolean updateCaptureRendererWindowTargetKey(",
    ["private void setCaptureRendererWindowTarget(String targetKey, String reason)"],
)
url_target_loader = section(
    "private void markCaptureRendererUrlTargetLoaded(",
    ["private void focusTerminalInputSoon("],
)
active_status_owner = section(
    "private void rememberActivePhoneWindow(",
    ["private boolean activeStatusMatchesVisibleTarget("],
)
idle_telemetry_compact = " ".join(idle_telemetry.split())
# WHY: read-hold ownership intentionally moved behind captureRendererReadHoldActive();
# this guard must protect that owner contract instead of restoring the stale literal.
read_hold_owner = section(
    "private boolean captureRendererReadHoldActive(",
    ["private void syncCaptureRendererReadHold("],
)
touch_bottom_restore = section(
    "private void restoreTouchLiveBottomQuietly(",
    ["private void leaveReadModeAfterTouchBottom("],
)
touch_bottom_commit = section(
    "private void refreshCaptureRendererForTouchBottomRestore(",
    ["private void refreshCaptureRendererSoon("],
)
touch_nudge = section(
    "private void runCaptureRendererTouchNudge(",
    ["private void clearCaptureRendererTouchNudgeSoon("],
)
touch_pulse = section(
    "private void refreshCaptureRendererPulse(",
    ["private void refreshCaptureRendererTouchEdge("],
)
touch_visual_frame = section(
    "private void requestCaptureRendererTouchVisualFrame(",
    ["private void scheduleCaptureRendererIdleRealtimeRefresh("],
)
touch_response = section(
    "private void sendHistoryScrollFromTouch(",
    ["private boolean touchScrollReachedLiveBottom("],
)
post_release_accept = section(
    "private boolean shouldAcceptPostReleaseTouchScrollResponse(",
    ["private void supersedeHistoryScrollInFlightForDirectionChange("],
)
handle_touch = section(
    "private boolean handleTerminalTouch(",
    ["private boolean shouldKeepReadModeAfterRecentHistoryDragTap("],
)
touch_sample = section(
    "private void processHistoryDragSample(float y, long eventTimeMs)",
    ["private void markUpwardHistoryIntentIfNeeded("],
)
touch_upward_intent = section(
    "private void markUpwardHistoryIntentIfNeeded(",
    ["private void nudgeCaptureRendererForHistorySample("],
)
touch_visual_sample = section(
    "private void nudgeCaptureRendererForHistorySample(float y, int lineThreshold)",
    ["private int historyDragRepeats("],
)
release_fling = section(
    "private boolean dispatchHistoryReleaseFling(MotionEvent event)",
    ["private boolean historyDragReleaseMomentumEnabled()"],
)
scroll_touch = section(
    "private void scrollTerminalFromTouch(String where, int repeats, boolean fromMomentum, String targetKey)",
    ["private void sendHistoryScrollFromTouch("],
)
touch_bounds = section(
    "private boolean touchScrollReachedLiveBottom(",
    ["private void drainPendingHistoryScroll("],
)
typing_bottom = section(
    "private void scrollViewerToTypingPosition(String reason, long generation, boolean blockDuringGestureRecovery)",
    ["private int visibleWebViewHeightForBottomAnchor("],
)
visible_bottom_anchor = section(
    "private int visibleWebViewHeightForBottomAnchor(",
    ["private String terminalFocusAndReconnectProbeScript("],
)
send_submit = section(
    "private void submitSafePrompt(\n            String text,",
    ["private void postPromptComposerSubmit("],
)
send_enqueue = section(
    "private void enqueuePromptComposerSubmit(",
    ["private boolean isDuplicatePromptComposerSubmit("],
)
send_local_hide = section(
    "private void hideDockedPromptComposerAfterQueuedSubmit(",
    ["private void drainPromptComposerSubmitQueue("],
)
send_drain = section(
    "private void drainPromptComposerSubmitQueue(",
    ["private boolean completePromptComposerSubmit("],
)
send_touch_overlay = section(
    "private void showPromptComposerSubmitTouchOverlay(",
    ["private boolean isPromptComposerSubmitTouchOverlayActive("],
)
stop_current = section(
    "private void stopCurrentTask(",
    ["private void sendEnterToTerminal("],
)

# WHY: `/active` is status telemetry. Re-entering the target setter for the
# already configured and confirmed window turns every poll into a forced
# fetch/commit/settle/watchdog train and starves visible streaming.
for required in (
    "!hasStableWindowId(selectedPhoneWindowId)",
    "!currentPhoneWindowId.equals(",
    "captureRendererWindowTargetKey",
    "confirmedCaptureRendererWindowTargetKey",
    "setCaptureRendererWindowTarget(currentPhoneWindowId, reason);",
):
    if required not in active_status_owner:
        print(
            f"APK realtime idle guard failed: /active target no-op gate lost {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

def target_setter_calls(selected, current, configured, confirmed):
    stable = lambda value: isinstance(value, str) and value.startswith("@") and value[1:].isdigit()
    return int(
        stable(current)
        and not stable(selected)
        and (current != configured or current != confirmed)
    )

if sum(target_setter_calls("", "@8", "@8", "@8") for _ in range(10)) != 0:
    print(
        "APK realtime idle guard failed: ten steady /active polls must produce zero target setter calls",
        file=sys.stderr,
    )
    sys.exit(1)
if target_setter_calls("", "@8", "@8", "") != 1:
    print(
        "APK realtime idle guard failed: an unconfirmed current target must retry exactly once per status observation",
        file=sys.stderr,
    )
    sys.exit(1)
if target_setter_calls("", "@9", "@8", "@8") != 1:
    print(
        "APK realtime idle guard failed: a real current-target pivot must enter the setter",
        file=sys.stderr,
    )
    sys.exit(1)

# Each intercepted request freezes and consumes the reason that scheduled it.
# A sticky transition reason on later normal polls hid request amplification and
# made unrelated DOM updates look like repeated target-settle work.
if "var fetchReason=String(lastReason||'poll');lastReason='poll';" not in hook:
    print(
        "APK realtime idle guard failed: terminal-frame request reason is not frozen and reset per request",
        file=sys.stderr,
    )
    sys.exit(1)
if hook.count("reason:fetchReason") < 4:
    print(
        "APK realtime idle guard failed: all scheduled/success/error terminal-frame telemetry must use the frozen request reason",
        file=sys.stderr,
    )
    sys.exit(1)

for forbidden in (
    "webView.reload",
    "submitSafePrompt",
    "postTextWithIdempotency",
    "clearPromptComposer",
    "restorePromptComposer",
    "promptComposerInput",
    "scrollTerminal",
    "/touch-scroll",
    "mantis_title",
    "account-switch",
    "OAuth",
):
    if forbidden in idle:
        print(
            f"APK realtime idle guard failed: idle visual commit path must not touch forbidden owner {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for forbidden in (
    "webView.reload",
    "submitSafePrompt",
    "postTextWithIdempotency",
    "clearPromptComposer",
    "restorePromptComposer",
    "promptComposerInput",
    "scrollTerminal",
    "/touch-scroll",
    "mantis_title",
    "account-switch",
    "OAuth",
):
    if forbidden in idle_refresh:
        print(
            f"APK realtime idle guard failed: native idle refresh path must not touch forbidden owner {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for forbidden in (
    "text:",
    "terminalText",
    "plainText:",
    "body:",
    "bodyText",
    "rawText",
):
    if forbidden in hook:
        print(
            f"APK realtime idle guard failed: telemetry hook must not send/log terminal text field {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "activityResumed",
    "hasStableWindowId(windowId)",
    "captureRendererFrameTargetsVisibleWindow(payload, windowId, confirmedVisibleWindowId)",
    "boolean localHistoryReadHold = localHistoryTouchViewportOwnsVisibleSurfaceForReadHold();",
    "boolean readModeHold = captureRendererReadHoldActive();",
    "!terminalHistoryDragActive",
    "!terminalMultiTouchGesture",
    "!terminalHorizontalPanActive",
    "!historyScrollRequestInFlight",
    "!terminalHistoryMomentumActive",
    "pendingHistoryScrollWhere.isEmpty()",
    "!terminalBottomRestoreInFlight",
    'skipReason = "local-history-read-hold";',
    'skipReason = "touch-scroll-hold";',
    'skipReason = "read-mode-hold";',
    "now - lastIdleCaptureRendererVisualCommitAtMs < CAPTURE_RENDERER_IDLE_VISUAL_COMMIT_MIN_MS",
    "commitCaptureRendererVisualFrame(reason)",
):
    if required not in idle:
        print(
            f"APK realtime idle guard failed: idle visual commit path missing guard/action {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

run_order = [
    idle_run.index("shouldSkipCaptureRendererIdleRealtimeRefresh()"),
    idle_run.index("installCaptureRendererTelemetryHook(\"idle-refresh\")"),
    idle_run.index("r.refresh(false,'apk-idle-realtime')"),
]
if run_order != sorted(run_order):
    print(
        "APK realtime idle guard failed: native watchdog must consult telemetry singleflight before requesting renderer refresh",
        file=sys.stderr,
    )
    sys.exit(1)
if "captureRendererIdleRealtimeRefreshInFlight = true;" in idle_run:
    print(
        "APK realtime idle guard failed: JavaScript evaluation cannot claim network in-flight ownership before terminal-frame scheduled telemetry",
        file=sys.stderr,
    )
    sys.exit(1)

telemetry_order = [
    telemetry_handler.index("handleCaptureRendererIdleRealtimeTelemetry(payload);"),
    telemetry_handler.index("shouldSuppressCaptureRendererTelemetryForReadHold(payload)"),
    telemetry_handler.index("late-active-switch-generation"),
    telemetry_handler.index("rememberAcceptedCaptureRendererFrame(payload);"),
]
if telemetry_order != sorted(telemetry_order):
    print(
        "APK realtime idle guard failed: network state must settle before paint suppression, while old-response paint rejection remains before frame acceptance",
        file=sys.stderr,
    )
    sys.exit(1)

target_change_order = [
    target_setter.index("updateCaptureRendererWindowTargetKey("),
    target_setter.index('confirmedCaptureRendererWindowTargetKey = "";'),
    target_setter.index("scheduleCaptureRendererIdleRealtimeRefresh(\"target-\" + reason)"),
]
if target_change_order != sorted(target_change_order):
    print(
        "APK realtime idle guard failed: target change must revoke old in-flight state before scheduling the new target watchdog",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    "if (targetChanged || forceTelemetryEpoch)",
    "invalidateCaptureRendererIdleRealtimeInFlight(reason);",
    "captureRendererWindowTargetKey = stableTarget;",
    "captureRendererIdleRealtimeExpectedWindowId = stableTarget;",
):
    if required not in target_key_owner:
        print(
            f"APK realtime idle guard failed: centralized renderer/watchdog target owner missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

target_key_writes = [
    line for line in source.splitlines()
    if "captureRendererWindowTargetKey =" in line
    and "private String captureRendererWindowTargetKey" not in line
    and "captureRendererWindowTargetKey ==" not in line
]
expected_target_writes = [
    line for line in source.splitlines()
    if "captureRendererIdleRealtimeExpectedWindowId =" in line
    and "private String captureRendererIdleRealtimeExpectedWindowId" not in line
    and "captureRendererIdleRealtimeExpectedWindowId ==" not in line
]
if len(target_key_writes) != 1 or len(expected_target_writes) != 1:
    print(
        "APK realtime idle guard failed: renderer target/watchdog expectation has a direct-write bypass "
        f"(target={len(target_key_writes)}, expected={len(expected_target_writes)})",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    "updateCaptureRendererWindowTargetKey(",
    'reason + "-url-loaded"',
    "true",
):
    if required not in url_target_loader:
        print(
            f"APK realtime idle guard failed: main-frame telemetry generation reset missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "readModeSuppressesKeyboard",
    "terminalHistoryViewportActive",
    "terminalHistoryDragActive",
    "terminalHistoryMomentumActive",
    "historyScrollRequestInFlight",
    "!pendingHistoryScrollWhere.isEmpty()",
    "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
    "terminalBottomRestoreInFlight",
):
    if required not in read_hold_owner:
        print(
            f"APK realtime idle guard failed: capture-renderer read-hold owner missing contract {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "activityResumed",
    "String watchdogTarget = captureRendererIdleRealtimeWatchdogTargetKey();",
    "hasStableWindowId(watchdogTarget)",
    "return captureRendererIdleRealtimeExpectedWindowId.trim();",
    "captureRendererIdleRealtimeRequestBlocksWatchdog",
    "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
    'return "local-history-read-hold";',
    "terminalHistoryDragActive",
    "terminalHistoryMomentumActive",
    "terminalMultiTouchGesture",
    "terminalHorizontalPanActive",
    "historyScrollRequestInFlight",
    "!pendingHistoryScrollWhere.isEmpty()",
    "terminalBottomRestoreInFlight",
    "terminalHistoryViewportActive",
    "readModeSuppressesKeyboard",
    'return "read-mode-hold";',
    "sessionSwitchInFlight",
    "CAPTURE_RENDERER_SESSION_SWITCH_REFRESH_GRACE_MS",
    "reason=slow-switch-repaint",
    "r.refresh(false,'apk-idle-realtime')",
    "installCaptureRendererTelemetryHook(\"idle-refresh\")",
    "scheduleNextCaptureRendererIdleRealtimeRefresh(generation, reason)",
):
    if required not in idle_refresh:
        print(
            f"APK realtime idle guard failed: native idle refresh path missing guard/action {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    '"scheduled".equals(stage)',
    '"response".equals(stage)',
    "captureRendererIdleRealtimeInFlightTelemetryGeneration",
    "captureRendererIdleRealtimeInFlightRequestedWindowId",
    "captureRendererIdleRealtimeInFlightFrameWindowId",
    "captureRendererFrameTargetsVisibleWindow(",
    "frameWindowId, requestedWindowId",
    "telemetryGeneration != captureRendererIdleRealtimeInFlightTelemetryGeneration",
    "requestedWindowId.equals(",
    "clearCaptureRendererIdleRealtimeInFlight",
    "scheduleCaptureRendererIdleRealtimeRefreshAfterTelemetry",
    "CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_MAX_FAILURE_MS",
):
    if required not in idle_telemetry_compact:
        print(
            f"APK realtime idle guard failed: terminal-frame telemetry state machine missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    'captureRendererIdleRealtimeInFlightRequestedWindowId = "";',
    'captureRendererIdleRealtimeInFlightFrameWindowId = "";',
    "captureRendererIdleRealtimeInFlightTelemetryGeneration = -1;",
    "captureRendererIdleRealtimeInFlightStartedAtMs = 0;",
):
    if required not in idle_clear:
        print(
            f"APK realtime idle guard failed: terminal-frame singleflight clear missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "clearCaptureRendererIdleRealtimeInFlight(reason)",
    "captureRendererIdleRealtimeRefreshDelayMs = CAPTURE_RENDERER_IDLE_REALTIME_REFRESH_BASE_MS;",
):
    if required not in idle_invalidate:
        print(
            f"APK realtime idle guard failed: target-change singleflight invalidation missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

# A response for an older target/generation cannot clear a newer request, while
# explicit marked alias/replacement metadata remains valid for the matching one.
def accepted_pair(payload, requested, frame):
    if requested == frame:
        return True
    accepted = {value for value in payload.get("acceptedWindowIds", []) if isinstance(value, str)}
    if accepted:
        return requested in accepted and frame in accepted
    marked = bool(
        payload.get("retargeted")
        or payload.get("replacementWindowId")
        or payload.get("resolvedWindowId")
        or payload.get("interactiveTargetWindowId")
    )
    display = payload.get("displayWindowId") or payload.get("requestedWindowId")
    resolved = payload.get("frameWindowId") or payload.get("resolvedWindowId") or payload.get("replacementWindowId")
    return marked and requested == display and frame == resolved

state = {"target": "@11", "generation": 101, "in_flight": True}
state = {"target": "", "generation": -1, "in_flight": False}  # target change invalidates @11
# In-place Active switching leaves the WebView URL on A while setWindowId points
# the renderer at expected B. Expected B must be watchdog authority immediately;
# requiring URL/frame confirmation here deadlocks the telemetry that confirms B.
url_window = "@11"
expected_window = "@22"
watchdog_window = expected_window or url_window
if watchdog_window != "@22":
    print("APK realtime idle guard failed: in-place B target became missing-window while URL remained A", file=sys.stderr)
    sys.exit(1)
if "@22" != watchdog_window or "@11" == watchdog_window:
    print("APK realtime idle guard failed: B scheduled telemetry was rejected or stale A telemetry was admitted", file=sys.stderr)
    sys.exit(1)
state = {"target": watchdog_window, "generation": 102, "in_flight": True}
old_response_clears = state["in_flight"] and state["target"] == "@11" and state["generation"] == 101
if old_response_clears or not state["in_flight"]:
    print("APK realtime idle guard failed: old target response cleared newer request", file=sys.stderr)
    sys.exit(1)
marked = {
    "retargeted": True,
    "displayWindowId": "@22",
    "frameWindowId": "@57",
    "acceptedWindowIds": ["@22", "@57"],
}
if not accepted_pair(marked, "@22", "@57"):
    print("APK realtime idle guard failed: marked acceptedWindowIds response no longer clears matching alias request", file=sys.stderr)
    sys.exit(1)

for required in (
    "private void cancelPassiveLiveBottomCallbacksForReadScroll(String reason)",
    'cancelPassiveLiveBottomCallbacksForReadScroll("terminal-touch-down")',
    'cancelPassiveLiveBottomCallbacksForReadScroll("history-drag-start")',
    'cancelPassiveLiveBottomCallbacksForReadScroll("touch-scroll-upward-intent")',
    'cancelPassiveLiveBottomCallbacksForReadScroll("enter-read-mode")',
    "entryLiveBottomSettleGeneration++",
    "entryBottomCoreGeneration++",
    "liveInputVisibilityGeneration++",
    "terminalFitGeneration++",
    "viewerTypingPositionGeneration++",
    "sessionSwitchLiveViewportGeneration++",
    "terminalFocusGeneration++",
    "stage=read-scroll-guard endpoint=apk result=passive-bottom-cancelled",
):
    if required not in source:
        print(
            f"APK realtime idle guard failed: installed read-scroll snap-back regression lock missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "requestCaptureRendererTouchVisualFrame(\"touch-nudge\")",
    "runCaptureRendererTouchNudge",
):
    if required not in touch_nudge:
        print(
            f"APK realtime idle guard failed: touch nudge path missing frame-paced visual commit {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "requestCaptureRendererTouchVisualFrame(reason)",
    "return;",
):
    if required not in touch_pulse:
        print(
            f"APK realtime idle guard failed: active touch pulse gate missing compositor-only frame commit {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for forbidden in (
    "evaluateJavascript",
    "r.refresh",
    "refreshCaptureRendererNow",
    "refreshCaptureRendererSoon",
    "refreshCaptureRendererPulse",
):
    if forbidden in touch_visual_frame:
        print(
            f"APK realtime idle guard failed: touch visual frame path must stay compositor-only, not fetch rows via {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "commitCaptureRendererVisualFrame(safeReason)",
    "CAPTURE_RENDERER_TOUCH_VISUAL_FRAME_MIN_MS",
    "stage=capture-renderer endpoint=touch-visual-frame result=requested",
    "backend/tmux row",
):
    if required not in touch_visual_frame:
        print(
            f"APK realtime idle guard failed: touch visual frame path missing compositor-only guard/action {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "postReleaseCommitAllowed",
    "refreshCaptureRendererTouchCommit(\"touch-scroll-response\")",
    "refreshCaptureRendererTouchReleaseCommit(\"touch-scroll-release-response\")",
    "scheduleTouchLiveBottomReconcile(\"touch-scroll-release-lineDown\")",
):
    if required not in touch_response:
        print(
            f"APK realtime idle guard failed: down-scroll readable continuity path missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    'requestSerial == postReleaseTouchScrollCommitRequestSerial',
    'gestureGeneration == postReleaseTouchScrollCommitGestureGeneration',
    'directionGeneration == postReleaseTouchScrollCommitDirectionGeneration',
    '"lineDown".equals(where)',
    'stableTargetKey.equals(postReleaseTouchScrollCommitTargetKey)',
):
    if required not in post_release_accept:
        print(
            f"APK realtime idle guard failed: post-release down-scroll frame accept gate missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

try:
    pre_scroll = handle_touch[:handle_touch.index("processHistoryDragEventSamples(event);")]
except ValueError:
    print(
        "APK realtime idle guard failed: one-finger touch path must process ACTION_MOVE samples through processHistoryDragEventSamples",
        file=sys.stderr,
    )
    sys.exit(1)

for forbidden in (
    "getJson(",
    "getJsonWithRetry(",
    "postTextWithIdempotency",
    "controlUrlForPath",
    "submitSafePrompt",
    "drainPromptComposerSubmitQueue",
    "stopCurrentTask",
    "showActiveSessions",
    "showToolbarControlPending",
    "refreshToolbarStatusDot",
    "updateSessionTitleStrip",
    '"/active',
    '"/tabs',
    '"/terminal-frame',
    '"/submit-text',
    '"/stop',
    "mantis_title",
):
    if forbidden in pre_scroll:
        print(
            f"APK realtime idle guard failed: one-finger touch handling must not enter network/title/send/stop owner path before scroll dispatch {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "if (action == MotionEvent.ACTION_DOWN)",
    "terminalTouchStableWindowId = touchScrollTargetKey();",
    "terminalTouchGestureGeneration++;",
    "return true;",
    "if (action == MotionEvent.ACTION_MOVE)",
    "terminalHistoryDragActive = true;",
    "enterReadMode();",
    'keepCaptureRendererPulsingDuringTouch("touch-scroll-start")',
    "processHistoryDragEventSamples(event);",
):
    if required not in handle_touch:
        print(
            f"APK realtime idle guard failed: one-finger touch path missing immediate scroll-owner step {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    'cancelPassiveLiveBottomCallbacksForReadScroll("terminal-touch-down")',
    'cancelPassiveLiveBottomCallbacksForReadScroll("history-drag-start")',
    'cancelPassiveLiveBottomCallbacksForReadScroll("touch-scroll-upward-intent")',
):
    if required not in handle_touch and required not in touch_sample and required not in touch_upward_intent:
        print(
            f"APK realtime idle guard failed: one-finger touch path missing passive snap-back cancellation {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for section_name, block, required in (
    (
        "processHistoryDragSample",
        touch_sample,
        (
            'String where = step > 0 ? "lineUp" : "lineDown";',
            'if (terminalTouchReachedLiveBottom && "lineDown".equals(where))',
            'if ("lineUp".equals(where))',
            "terminalTouchReachedLiveBottom = false;",
            'if (terminalTouchReachedHistoryTop && "lineUp".equals(where))',
            "deferHistoryScrollUntilRelease(where, repeats);",
        ),
    ),
    (
        "nudgeCaptureRendererForHistorySample",
        touch_visual_sample,
        (
            'String visualWhere = visualStep > 0 ? "lineUp" : "lineDown";',
            'if (terminalTouchReachedLiveBottom && "lineDown".equals(visualWhere))',
            'if ("lineUp".equals(visualWhere))',
            'float rendererStep = "lineUp".equals(visualWhere)',
            "nativeHistoryScrollController.dragBy(rendererStep, visualWhere);",
            "nudgeCaptureRendererForTouch(rendererStep, visualWhere);",
        ),
    ),
    (
        "dispatchHistoryReleaseFling",
        release_fling,
        (
            'String where = signedReleaseVelocity > 0 ? "lineUp" : "lineDown";',
            "nativeHistoryScrollController.fling(releaseVelocity, where)",
        ),
    ),
):
    for needle in required:
        if needle not in block:
            print(
                f"APK realtime idle guard failed: {section_name} missing one-finger delayed-boundary protection {needle!r}",
                file=sys.stderr,
            )
            sys.exit(1)

for forbidden in (
    'String where = step < 0 ? "lineUp" : "lineDown";',
    'String visualWhere = visualStep < 0 ? "lineUp" : "lineDown";',
    'String where = signedReleaseVelocity < 0 ? "lineUp" : "lineDown";',
    "nativeHistoryScrollController.dragBy(visualStep, visualWhere);",
    "nudgeCaptureRendererForTouch(visualStep, visualWhere);",
):
    if forbidden in source:
        print(
            f"APK realtime idle guard failed: one-finger scroll direction mapping regressed to the delayed-boundary signature {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for forbidden in (
    "scrollTerminalFromTouch(where, repeats, true, targetKey);",
    "historyMomentumRepeats(where, releaseVelocity)",
    "runHistoryMomentumFrame()",
):
    if forbidden in release_fling or forbidden in source:
        print(
            f"APK realtime idle guard failed: release regressed to row-burst momentum {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "if (historyScrollRequestInFlight)",
    "!fromMomentum && boundedRepeats <= HISTORY_DRAG_READING_MOVE_REPEATS",
    "HISTORY_DRAG_SLOW_MOVE_MAX_REPEATS",
    "pendingHistoryScrollRepeats + HISTORY_DRAG_READING_MOVE_REPEATS",
    "same-direction in-flight failure",
    "old delayed burst/page-scroll jump",
):
    if required not in scroll_touch:
        print(
            f"APK realtime idle guard failed: in-flight slow one-finger scroll must keep bounded physical-sync catch-up: missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "return scrollPosition == 0;",
    "return scrollPosition >= 0 && scrollPosition <= TOUCH_SCROLL_LIVE_BOTTOM_SNAP_LINES;",
    "return historySize > 0 && scrollPosition >= historySize;",
):
    if required not in touch_bounds:
        print(
            f"APK realtime idle guard failed: tmux scrollPosition bounds must stay exact and not clamp from WebView geometry: missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

if "boolean releaseOnlyNearBottom = !terminalHistoryDragActive && isNearTmuxLiveBottom(payload);" not in touch_response:
    print(
        "APK realtime idle guard failed: near-bottom snap band must remain release-only, not active one-finger drag clamp",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    "readModeSuppressesKeyboard",
    "terminalHistoryViewportActive",
    "isTerminalGestureRecoveryActive()",
    "visibleWebViewHeightForBottomAnchor()",
    "int visibleHeightPx = visibleWebViewHeightForBottomAnchor();",
    "int maxY = Math.max(0, contentHeightPx - visibleHeightPx);",
    "webView.scrollTo(webView.getScrollX(), maxY);",
):
    if required not in typing_bottom:
        print(
            f"APK realtime idle guard failed: typing bottom clamp must not run during read/touch and must use the visible WebView height: missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "Math.max(1, webView.getHeight())",
    "lastKeyboardReserveBottom > 0",
    "Math.max(dp(96), height - lastKeyboardReserveBottom)",
    "return height;",
):
    if required not in visible_bottom_anchor:
        print(
            f"APK realtime idle guard failed: visible bottom anchor must clamp to the visible WebView/keyboard reserve without inventing a scroll boundary: missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "PromptComposerQueuedSubmit submit = new PromptComposerQueuedSubmit(",
    "enqueuePromptComposerSubmit(submit);",
):
    if required not in send_submit:
        print(
            f"APK realtime idle guard failed: Send immediate visual feedback path missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "PromptComposerQueuedSubmit",
    "promptComposerSubmitQueue.add(submit)",
    "queued-stacked",
    "hideDockedPromptComposerAfterQueuedSubmit",
    "duplicate-ignored",
    "Distinct stacked prompts are accepted by enqueuePromptComposerSubmit()",
    "draft survives failed sends without keeping the phone visually stuck",
):
    if required not in source:
        print(
            f"APK realtime idle guard failed: stacked Send queue path missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

if "Prompt send in progress" in source:
    print(
        "APK realtime idle guard failed: distinct stacked sends must not regress to Prompt send in progress",
        file=sys.stderr,
    )
    sys.exit(1)

queue_start = send_enqueue.index("promptComposerSubmitQueue.add(submit)")
# The duplicate-submit branch also shows pending feedback before returning.
# Guard the real non-duplicate queue branch from the enqueue point forward so
# future edits still fail if local-hide is moved behind the /submit-text drain.
queued_branch = send_enqueue[queue_start:]
send_order = [
    queued_branch.index("promptComposerSubmitQueue.add(submit)"),
    queued_branch.index("hideDockedPromptComposerAfterQueuedSubmit"),
    queued_branch.index("logPromptSendStage(\"local-hide\""),
    queued_branch.index("showToolbarControlPending(promptComposerSubmitPendingLabel())"),
    queued_branch.index("drainPromptComposerSubmitQueue();"),
]
if send_order != sorted(send_order):
    print(
        "APK realtime idle guard failed: Send must acknowledge/hide locally before draining the /submit-text queue",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    "PROMPT_COMPOSER_SUBMIT_TOUCH_OWNER_MS",
    "promptComposerSubmitTouchOwnerGeneration",
    "showPromptComposerSubmitTouchOverlay(",
    "hidePromptComposerSubmitTouchOverlayIfCurrent(",
    "isPromptComposerSubmitTouchOverlayActive()",
):
    if required not in source:
        print(
            f"APK realtime idle guard failed: send-pending terminal touch owner guard missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

# WHY: Send delivery is asynchronous data work, not a modal touch owner. The
# accepted v250 path removes the old temporary overlay so a new terminal scroll
# can supersede Send immediately while the POST/echo settles.
if "showPromptComposerSubmitTouchOverlay(" in send_local_hide:
    print(
        "APK realtime idle guard failed: queued Send must not restore the stale modal touch overlay",
        file=sys.stderr,
    )
    sys.exit(1)
local_hide_order = [
    send_local_hide.index("promptComposerBar.setVisibility(View.GONE);"),
    send_local_hide.index("hideHistoryTouchOverlayQuietly();"),
    send_local_hide.index("hideTerminalKeyboardQuietly(\"composer-queued-submit\")"),
]
if local_hide_order != sorted(local_hide_order):
    print(
        "APK realtime idle guard failed: local Send hide must release the overlay before IME/POST completion can block scroll",
        file=sys.stderr,
    )
    sys.exit(1)

if "showPromptComposerSubmitTouchOverlay(\"stop-current-task\")" not in stop_current:
    print(
        "APK realtime idle guard failed: Stop must share the non-modal touch-owner guard before /stop waits on backend completion",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    "historyTouchOverlay.setVisibility(View.VISIBLE);",
    "historyTouchOverlay.bringToFront();",
    "historyTouchOverlay.setOnTouchListener((touchedView, event) -> handleTerminalTouch(event));",
):
    if required not in send_touch_overlay and required not in source:
        print(
            f"APK realtime idle guard failed: Send/Stop touch overlay must keep routing touches through the terminal scroll owner: missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

if "postPromptComposerSubmit(submit)" not in send_drain:
    print(
        "APK realtime idle guard failed: stacked Send queue must drain into one idempotent submit at a time",
        file=sys.stderr,
    )
    sys.exit(1)

for required in (
    'refreshCaptureRendererForTouchBottomRestore("touch-bottom")',
    "leaveReadModeAfterTouchBottom()",
):
    if required not in touch_bottom_restore:
        print(
            f"APK realtime idle guard failed: quiet touch-bottom restore missing action {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "clearCaptureRendererTouchNudge(reason)",
    "refreshCaptureRendererNow(reason)",
    "commitCaptureRendererVisualFrame(reason)",
    'refreshCaptureRendererNow(reason + "-settle")',
    'commitCaptureRendererVisualFrame(reason + "-settle")',
):
    if required not in touch_bottom_commit:
        print(
            f"APK realtime idle guard failed: touch-bottom restore visual commit missing {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for forbidden in (
    "webView.reload",
    "submitSafePrompt",
    "postTextWithIdempotency",
    "promptComposerInput",
    "mantis_title",
    "account-switch",
    "OAuth",
):
    if forbidden in touch_bottom_commit:
        print(
            f"APK realtime idle guard failed: touch-bottom restore visual commit must not touch forbidden owner {forbidden!r}",
            file=sys.stderr,
        )
        sys.exit(1)

for required in (
    "fnv1a32",
    "rowCount",
    "bodyLength",
    "hash",
    "fetchStartedAt",
    "scheduledDelayMs",
    "WeztermCaptureBridge.onCaptureRendererTelemetry",
    "WeztermCaptureBridge.requestIdleVisualCommit",
    "MutationObserver",
):
    if required not in hook:
        print(
            f"APK realtime idle guard failed: telemetry hook missing safe metadata/hook {required!r}",
            file=sys.stderr,
        )
        sys.exit(1)

if "endpoint=/terminal-frame" not in source:
    print("APK realtime idle guard failed: terminal-frame telemetry endpoint label missing", file=sys.stderr)
    sys.exit(1)

print("APK touch owner independence and viewport-bound clamp guard passed")
print("APK realtime idle invalidation guard passed")
PY

require_absent "$MAIN" 'webView.reload(); // idle-frame-change' "idle realtime path must never reload WebView"
require_absent "$MAIN" 'webView.reload(); // idle-realtime-tick' "idle realtime fallback must never reload WebView"

echo "APK realtime idle guards passed"
