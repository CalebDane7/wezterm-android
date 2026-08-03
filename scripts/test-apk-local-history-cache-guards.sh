#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"

node "$ROOT/scripts/test-scroll-anchor-prepend-contract.mjs"

python3 - "$MAIN" "${1:-}" <<'PY'
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1]).read_text()
log_path = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2] else None


def fail(message):
    print(f"APK local history cache guard failed: {message}", file=sys.stderr)
    sys.exit(1)


def method_body(name):
    match = re.search(r"\n\s*private\s+[^{;\n]+?\s+" + re.escape(name) + r"\(", source)
    if not match:
        fail(f"missing method {name}")
    brace = source.find("{", match.start())
    if brace == -1:
        fail(f"unbounded method {name}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1:index]
    fail(f"unterminated method {name}")


if "LOCAL_HISTORY_TOUCH_VIEWPORT_MAX_ROWS = LOCAL_HISTORY_CHUNK_LINES * 8" not in source:
    fail("finger-owned local-history cache must keep enough adjacent chunks to avoid boundary clamp jumps")
if "forced tail eviction and clamped the finger-owned viewport" not in source:
    fail("local-history cache size must keep a WHY comment for the boundary clamp jump")

# The installed 2026-07-11 T5 failure proved that the cached history `<pre>` is
# not the capture renderer: it forces a different row count, rewraps text, owns a
# partial ANSI palette, and rewrites geometry on the first MOVE. Cache may stay as
# target-bound data, but it must not replace pixels or claim gesture/read-hold
# ownership. The server capture renderer remains the single visible owner.
if "LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED = false" not in source:
    fail("cached history must be data-only; inline gesture paint must be fail-closed")

visible_surface = method_body("localHistoryTouchViewportTargetsVisibleSurface")
visible_gate = visible_surface.find("if (!LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED)")
visible_stop = visible_surface.find("return false;", visible_gate)
visible_rows = visible_surface.find("localHistoryTouchViewportRows.isEmpty()")
if -1 in (visible_gate, visible_stop, visible_rows) or not (visible_gate < visible_stop < visible_rows):
    fail("cached rows must fail closed before they can claim the visible gesture/read-hold surface")

viewport_update = method_body("updateLocalHistoryTouchViewportBeforeBackend")
update_gate = viewport_update.find("if (!LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED)")
update_stop = viewport_update.find("return false;", update_gate)
update_request = viewport_update.find("LocalHistoryRequest request = localHistoryRequestForTouchTarget(targetKey);")
if -1 in (update_gate, update_stop, update_request) or not (update_gate < update_stop < update_request):
    fail("cached offsets must remain data-only instead of moving before backend/capture reconciliation")

inline_paint_owner = method_body("paintInlineLocalHistoryRowsInRenderer")
paint_gate = inline_paint_owner.find("if (!LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED)")
paint_log = inline_paint_owner.find("result=data-only-single-renderer", paint_gate)
paint_stop = inline_paint_owner.find("return;", paint_log)
paint_mutation = inline_paint_owner.find("JSONArray rowArray = new JSONArray();")
if -1 in (paint_gate, paint_log, paint_stop, paint_mutation) or not (
        paint_gate < paint_log < paint_stop < paint_mutation):
    fail("inline cached DOM must stop before row, ANSI, wrap, color, or geometry mutation")

prewarm_render_owner = method_body("maybeRenderPrewarmedLocalHistoryForActiveTouch")
prewarm_gate = prewarm_render_owner.find("if (!LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED)")
prewarm_stop = prewarm_render_owner.find("return;", prewarm_gate)
prewarm_request = prewarm_render_owner.find("activeHistoryScrollRequestSerial <= 0")
if -1 in (prewarm_gate, prewarm_stop, prewarm_request) or not (
        prewarm_gate < prewarm_stop < prewarm_request):
    fail("prewarmed cache must remain data-only during an active capture-renderer gesture")


def parse_fields(line):
    fields = {}
    for match in re.finditer(r"([A-Za-z][A-Za-z0-9_]*)=([^ ]*)", line):
        fields[match.group(1)] = match.group(2)
    return fields


def int_field(fields, *names):
    for name in names:
        value = fields.get(name)
        if value is None or value == "":
            continue
        try:
            return int(value)
        except ValueError:
            continue
    return None


def validate_scroll_anchor_log(path):
    text = path.read_text(errors="replace")
    actions = {}
    for line_index, line in enumerate(text.splitlines(), 1):
        if "actionId=scroll-" not in line:
            continue
        fields = parse_fields(line)
        action_id = fields.get("actionId", "")
        if not action_id.startswith("scroll-"):
            continue
        action = actions.setdefault(action_id, {
            "before_backend": True,
            "local_events": [],
            "dom_apply_changed": [],
            "terminal_frame_changed": [],
            "bottom_after_read_hold": False,
            "saw_bottom": False,
            "saw_boundary_prepend": False,
            "cache_paint_after_boundary": [],
            "surface_painted": False,
            "nudge_missing_before_surface": [],
            "offset_after_missing_before_surface": [],
            "continuity_prefetch_skip": [],
            "down_at": None,
            "history_start_at": None,
            "physical_upward_latch_at": None,
            "first_local_feedback_at": None,
            "live_pixel_nudge_at": None,
            "local_history_pixel_nudge_at": None,
            "cache_handoff_at": None,
            "cache_offset_at": None,
            "cache_prewarm_at": None,
            "cache_inline_paint_at": None,
            "touch_response_at": None,
        })
        if "stage=terminal-touch" in line and fields.get("action") == "down":
            action["down_at"] = action["down_at"] or line_index
        if "stage=terminal-touch" in line and fields.get("action") == "history-start":
            action["history_start_at"] = action["history_start_at"] or line_index
        if "stage=touch-scroll-physical-upward-history" in line:
            action["physical_upward_latch_at"] = action["physical_upward_latch_at"] or line_index
        if "stage=touch-scroll-local-feedback" in line and fields.get("result") == "first-local-feedback":
            action["first_local_feedback_at"] = action["first_local_feedback_at"] or line_index
        if "stage=touch-nudge" in line and fields.get("result") == "-touch-nudge-":
            action["live_pixel_nudge_at"] = action["live_pixel_nudge_at"] or line_index
        # WHY: once cached local-history rows own the viewport, the live
        # capture-renderer nudge is intentionally redirected to the inline
        # local-history finger transform. Treat both logs as the same gesture
        # physics path, and fail only when neither pixel path appears.
        if (
            "stage=touch-nudge" in line
            and fields.get("result") == "redirected-local-history-owner"
        ):
            action["local_history_pixel_nudge_at"] = action["local_history_pixel_nudge_at"] or line_index
        if (
            "stage=local-history-finger-nudge" in line
            and (
                "local-history-finger-nudge:applied" in line
                or fields.get("result") == "flushed-before-row-paint"
            )
        ):
            action["local_history_pixel_nudge_at"] = action["local_history_pixel_nudge_at"] or line_index
        if "stage=local-history-handoff" in line and fields.get("result") == "transferred-capture-nudge":
            action["cache_handoff_at"] = action["cache_handoff_at"] or line_index
        if "stage=local-history-viewport" in line and fields.get("result") == "offset-updated-before-touch-scroll":
            action["cache_offset_at"] = action["cache_offset_at"] or line_index
        if (
            "stage=local-history-cache" in line
            and fields.get("result") in {
                "prefetch-allowed",
                "prefetch-hit",
                "prefetch-scheduled",
                "prefetch-stored",
                "prewarm-hit",
                "prewarm-scheduled",
                "prewarm-stored",
            }
        ):
            action["cache_prewarm_at"] = action["cache_prewarm_at"] or line_index
        if "stage=local-history-inline" in line and fields.get("result") in {"painted", "first-new-readable-row"}:
            action["cache_inline_paint_at"] = action["cache_inline_paint_at"] or line_index
        if "endpoint=/live-bottom" in line or fields.get("reason", "").startswith("live-bottom"):
            action["saw_bottom"] = True
        if action["saw_bottom"] and "native-read-hold" in line:
            action["bottom_after_read_hold"] = True
        if "stage=local-history-viewport" in line and "result=boundary-prepended" in line:
            action["saw_boundary_prepend"] = True
        if (
            action["saw_boundary_prepend"]
            and "stage=local-history-inline" in line
            and "result=painted" in line
            and fields.get("source") != "inline-local-history-viewport"
            and fields.get("rawSource") != "inline-local-history-viewport"
        ):
            action["cache_paint_after_boundary"].append(line)
        if (
            "stage=local-history-inline" in line
            and "result=painted" in line
            and (
                fields.get("source") == "inline-local-history-viewport"
                or fields.get("rawSource") == "inline-local-history-viewport"
            )
        ):
            action["surface_painted"] = True
        if (
            "stage=local-history-finger-nudge" in line
            and "local-history-finger-nudge-missing" in line
            and not action["surface_painted"]
        ):
            action["nudge_missing_before_surface"].append(line)
        if (
            action["nudge_missing_before_surface"]
            and not action["surface_painted"]
            and "stage=local-history-viewport" in line
            and "result=offset-updated-before-touch-scroll" in line
        ):
            action["offset_after_missing_before_surface"].append(line)
        if (
            "stage=local-history-cache" in line
            and "result=prefetch-skipped" in line
            and fields.get("reason") == "target-body-continuity-unproven"
        ):
            action["continuity_prefetch_skip"].append(line)
        if "stage=touch-scroll-response" in line and "endpoint=/touch-scroll" in line:
            action["touch_response_at"] = action["touch_response_at"] or line_index
            action["before_backend"] = False
            continue
        if not action["before_backend"]:
            continue
        if (
            "stage=capture-renderer" in line
            and "endpoint=dom-apply" in line
            and "result=changed" in line
            and fields.get("actionKind") == "one-finger-scroll"
        ):
            action["dom_apply_changed"].append(line)
        if (
            "stage=capture-renderer" in line
            and "endpoint=/terminal-frame" in line
            and "result=response" in line
            and fields.get("actionKind") == "one-finger-scroll"
            and fields.get("skipReason") == "new-hash"
        ):
            action["terminal_frame_changed"].append(line)
        local_stage = (
            "stage=local-history-inline" in line
            or "stage=local-history-owner" in line
            or "stage=local-history-viewport" in line
        )
        if not local_stage:
            continue
        offset = int_field(fields, "localViewportOffset", "viewportOffset", "currentOffset")
        track_rows = int_field(fields, "localHistoryCachedRows", "viewportRows")
        visible_rows = int_field(fields, "inlineFeedbackRows", "localViewportRows", "rows", "viewportRows", "localHistoryCachedRows")
        meaningful = int_field(fields, "meaningfulRows")
        where = fields.get("where", "")
        generation = (
            fields.get("generationKey", "")
            or fields.get("canonicalGenerationKey", "")
            or fields.get("chunkGenerationKey", "")
        )
        result = fields.get("result", "")
        if offset is None or track_rows is None or not where:
            continue
        if meaningful is not None and visible_rows is not None and visible_rows >= 48:
            minimum = max(8, int(visible_rows * 0.08 + 0.999))
            if meaningful < minimum:
                fail(
                    f"log {path} action {action_id} painted mostly blank local-history viewport "
                    f"meaningfulRows={meaningful} rows={visible_rows}"
                )
        if result in (
            "painted",
            "first-new-readable-row",
            "viewport-moved",
            "offset-updated-before-touch-scroll",
            "canonical-viewport-preserved",
        ):
            action["local_events"].append({
                "line": line,
                "offset": offset,
                "rows": track_rows,
                "where": where,
                "generation": generation,
                "track": (generation or "<empty-generation>", track_rows),
            })

    if not actions:
        fail(f"log {path} had no action-correlated one-finger scroll events")
    for action_id, action in actions.items():
        if action["down_at"] and action["history_start_at"]:
            if action["first_local_feedback_at"] is None:
                fail(f"log {path} action {action_id} started history drag without first local feedback")
            if action["live_pixel_nudge_at"] is None and action["local_history_pixel_nudge_at"] is None:
                fail(
                    f"log {path} action {action_id} did not use the shared pixel-nudge "
                    "movement path before cached rows"
                )
            if action["cache_prewarm_at"] is None:
                fail(f"log {path} action {action_id} had no target-bound cache prewarm/prefetch event for the scroll gesture")
            cache_owner_at = min(
                value for value in (
                    action["cache_handoff_at"],
                    action["cache_offset_at"],
                    action["cache_inline_paint_at"],
                )
                if value is not None
            ) if any(value is not None for value in (
                action["cache_handoff_at"],
                action["cache_offset_at"],
                action["cache_inline_paint_at"],
            )) else None
            if cache_owner_at is None:
                fail(f"log {path} action {action_id} never handed movement to cached local-history rows")
            response_at = action["touch_response_at"]
            if response_at is not None:
                if action["first_local_feedback_at"] > response_at:
                    fail(f"log {path} action {action_id} waited for /touch-scroll before first local feedback")
                if cache_owner_at > response_at:
                    fail(f"log {path} action {action_id} waited for /touch-scroll before cached local-history ownership")
                if action["cache_prewarm_at"] > response_at:
                    fail(f"log {path} action {action_id} waited for /touch-scroll before cache prewarm/prefetch")
            if action["physical_upward_latch_at"] is not None and action["cache_offset_at"] is None:
                fail(f"log {path} action {action_id} latched physical-upward old-history but never moved the cache viewport")
        if action["dom_apply_changed"]:
            fail(f"log {path} action {action_id} had capture-renderer DOM apply during one-finger scroll before backend reconciliation")
        if action["terminal_frame_changed"]:
            fail(f"log {path} action {action_id} accepted new /terminal-frame hash during one-finger scroll before backend reconciliation")
        if action["bottom_after_read_hold"]:
            fail(f"log {path} action {action_id} kept native read-hold after Bottom/live-bottom")
        if action["cache_paint_after_boundary"]:
            fail(f"log {path} action {action_id} repainted async cache rows after a boundary prepend during touch")
        if action["offset_after_missing_before_surface"]:
            fail(
                f"log {path} action {action_id} advanced local-history row offset after "
                "finger nudge reported a missing DOM surface and before cached surface paint"
            )
        if action["continuity_prefetch_skip"]:
            fail(
                f"log {path} action {action_id} skipped local-history prefetch for "
                "continuity-only body uncertainty instead of using safe target-bound cache"
            )
        events = action["local_events"]
        if len(events) < 2:
            continue
        tracks = [event["track"] for event in events]
        unique_tracks = []
        for track in tracks:
            if track not in unique_tracks:
                unique_tracks.append(track)
        if len(unique_tracks) > 1:
            fail(f"log {path} action {action_id} painted multiple local-history generation/row-count tracks: {unique_tracks}")
        for direction in ("lineUp", "lineDown"):
            offsets = [event["offset"] for event in events if event["where"] == direction]
            if len(offsets) < 2:
                continue
            pairs = zip(offsets, offsets[1:])
            if direction == "lineUp" and any(curr < prev for prev, curr in pairs):
                fail(f"log {path} action {action_id} had non-monotonic lineUp offsets: {offsets[:12]}")
            pairs = zip(offsets, offsets[1:])
            if direction == "lineDown" and any(curr > prev for prev, curr in pairs):
                fail(f"log {path} action {action_id} had non-monotonic lineDown offsets: {offsets[:12]}")


read_hold_owner = method_body("localHistoryTouchViewportOwnsVisibleSurfaceForReadHold")
for required in (
    "localHistoryTouchViewportTargetsVisibleSurface()",
    "readModeSuppressesKeyboard",
    "terminalHistoryViewportActive",
    "terminalHistoryDragActive",
    "terminalHistoryMomentumActive",
    "historyScrollRequestInFlight",
    "!pendingHistoryScrollWhere.isEmpty()",
    "Idle `/terminal-frame` may update caches behind this state",
):
    if required not in read_hold_owner:
        fail(f"local-history read-hold owner missing {required!r}")
if "localHistoryTouchViewportAlreadyRenderedOnly" in read_hold_owner:
    fail("local-history read-hold owner must include already-rendered seed rows until explicit live recovery")

read_hold = method_body("captureRendererReadHoldActive")
for required in (
    "terminalHistoryMomentumActive",
    "!pendingHistoryScrollWhere.isEmpty()",
    "touchScrollAwaitingFirstLocalFeedback()",
    "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
):
    if required not in read_hold:
        fail(f"capture renderer read-hold predicate missing {required!r}")

idle_refresh = method_body("shouldSkipCaptureRendererIdleRealtimeRefresh")
for required in (
    "touchScrollAwaitingFirstLocalFeedback()",
    'return "pre-local-feedback-hold";',
    "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
    'return "local-history-read-hold";',
    "terminalHistoryMomentumActive",
    "!pendingHistoryScrollWhere.isEmpty()",
):
    if required not in idle_refresh:
        fail(f"idle realtime refresh must not run over local-history read-hold via {required!r}")

idle_commit = method_body("handleCaptureRendererIdleCommitRequest")
for required in (
    "boolean preLocalFeedbackHold = touchScrollAwaitingFirstLocalFeedback();",
    "boolean localHistoryReadHold = localHistoryTouchViewportOwnsVisibleSurfaceForReadHold();",
    "boolean readModeHold = captureRendererReadHoldActive();",
    'skipReason = "pre-local-feedback-hold";',
    'skipReason = "local-history-read-hold";',
    "idle-frame commits are not a valid first feedback source",
    "pendingHistoryScrollWhere.isEmpty()",
    "delayed idle `/terminal-frame` and DOM-apply callbacks",
):
    if required not in idle_commit:
        fail(f"idle visual commit must be suppressed during local-history read-hold via {required!r}")
if idle_commit.find("localHistoryReadHold") > idle_commit.find("!noActiveTouchOrScroll"):
    fail("local-history read-hold must beat generic touch-scroll idle commit handling")

hook = method_body("captureRendererTelemetryHookScript")
terminal_idle_gate = (
    "!gestureScrollActive()&&(!nativeReadHold()||"
    "(Number(payload&&payload.historySize)===0&&!tuiReadHold()))"
)
for required in (
    "function readHoldCommitAllowed(endpoint,reason)",
    "nativeReadHold()",
    "if(endpoint==='dom-apply'){return false;}",
    terminal_idle_gate,
    "commitPayload.stage='suppressed-read-hold'",
    "commitPayload.skipReason='native-read-hold'",
    "!readHoldCommitAllowed('dom-apply',lastReason||'poll')",
    "stage:'suppressed-read-hold',endpoint:'dom-apply'",
    "skipReason:'native-read-hold'",
):
    if required not in hook:
        fail(f"capture telemetry hook must suppress idle DOM/frame commits during native read-hold via {required!r}")
terminal_gate_index = hook.find(terminal_idle_gate)
terminal_commit_index = hook.find("bridge(commitPayload,true)")
if terminal_gate_index == -1 or terminal_commit_index == -1 or terminal_gate_index > terminal_commit_index:
    fail("idle /terminal-frame visual commit must be gated by readHoldCommitAllowed before bridge(..., true)")
dom_gate_index = hook.find("!readHoldCommitAllowed('dom-apply',lastReason||'poll')")
dom_commit_index = hook.find("if(changed&&acceptedDomFrame&&!gestureScrollActive()){/* WHY")
if dom_gate_index == -1 or dom_commit_index == -1 or dom_gate_index > dom_commit_index:
    fail("idle DOM apply visual commit must be gated by native read-hold suppression before bridge(..., true)")

native_read_hold_telemetry = method_body("captureRendererTelemetryAllowedDuringReadHold")
for required in (
    '"telemetry-hook".equals(endpoint)',
    '"dom-apply".equals(endpoint)',
    "return false;",
    "DOM observer telemetry can carry a stale touch-bottom settle",
):
    if required not in native_read_hold_telemetry:
        fail(f"native capture renderer read-hold telemetry gate missing {required!r}")

inline_gate = method_body("allowInlineLocalHistoryPaintFromRenderer")
for required in (
    "allowMonotonicLocalHistoryViewportPaintFromRenderer(",
    "isLocalHistoryBoundaryCacheSource(rawSource)",
):
    if required not in inline_gate:
        fail(f"inline local-history renderer gate must reject stale/boundary paints via {required!r}")

monotonic_gate = method_body("allowMonotonicLocalHistoryViewportPaintFromRenderer")
for required in (
    "queuedOffset < ownerOffset",
    "queuedOffset > ownerOffset",
    "rewindsOwner",
    "rewindsLastAccepted",
    "result=non-monotonic-viewport-paint-suppressed",
    "localHistoryTouchLastPaintOffset",
    "must not rewind the",
):
    if required not in monotonic_gate:
        fail(f"inline local-history viewport paints must be monotonic during an active drag via {required!r}")

for required in (
    'syncCaptureRendererReadHold("local-history-boundary-merge")',
    'syncCaptureRendererReadHold("local-history-viewport-replaced")',
    'syncCaptureRendererReadHold("local-history-viewport-offset-updated")',
    'syncCaptureRendererReadHold("local-history-inline-viewport-moved")',
):
    if required not in source:
        fail(f"local-history viewport owner must sync native read-hold state via {required!r}")


show = method_body("showLocalHistoryViewer")
if 'getJsonWithRetry("/active"' in show or 'getJsonWithRetry("/active?readOnly=1"' in show:
    fail("local history viewer must not start from process-global /active")
if "LocalHistoryRequest request = localHistoryRequestForVisibleTarget();" not in show:
    fail("local history viewer must bind to the confirmed/visible renderer target")
if 'fetchLocalHistoryChunk(request, "");' not in show:
    fail("local history viewer must fetch through the target-bound request object")

fetch = method_body("fetchLocalHistoryChunk")
for required in (
    "findCachedLocalHistoryChunk(request, start)",
    '"/scrollback/chunk?windowId=" + urlEncode(request.windowId)',
    "rememberLocalHistoryChunk(request, start, payload)",
    "showLocalHistoryDialog(request,",
):
    if required not in fetch:
        fail(f"fetchLocalHistoryChunk missing cache/target contract {required!r}")
if 'getJsonWithRetry("/active"' in fetch:
    fail("fetchLocalHistoryChunk must not recover identity from /active")

touch = method_body("sendHistoryScrollFromTouch")
for required in (
    "updateLocalHistoryTouchViewportBeforeBackend(",
    "prefetchLocalHistoryForTouchScroll(targetKey, where, repeats, requestSerial)",
    "getJson(path, payload ->",
    "localHistoryTouchViewportOwnsVisibleSurfaceForNudge()",
    "result=preserved-local-history-owner",
    "result=preserved-local-history-owner-at-bottom-edge",
    "result=preserved-local-history-owner-at-top-edge",
):
    if required not in touch:
        fail(f"one-finger touch-scroll must warm local history cache before backend row commits via {required!r}")
local_viewport_index = touch.find("updateLocalHistoryTouchViewportBeforeBackend(")
prefetch_index = touch.find("prefetchLocalHistoryForTouchScroll(targetKey, where, repeats, requestSerial)")
dispatch_index = touch.find("getJson(path, payload ->")
if local_viewport_index == -1 or prefetch_index == -1 or dispatch_index == -1 or not (local_viewport_index < prefetch_index < dispatch_index):
    fail("one-finger touch-scroll must move the local viewport, then prefetch local history, before dispatching /touch-scroll")
for required in (
    "activeTouchScrollActionId",
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
    "firstLocalFeedbackElapsedMs=\" + touchScrollFirstLocalFeedbackElapsedMs()",
):
    if required not in touch:
        fail(f"touch-scroll dispatch must correlate action id and first local feedback before network waits via {required!r}")
if "markTouchScrollFirstLocalFeedback(where, \"visual-nudge\")" in touch:
    fail("touch-scroll dispatch must not mark visual-nudge as local feedback before a visible surface actually moves")
for needle in (
    "refreshCaptureRendererTouchCommit(\"touch-scroll-response\")",
    "refreshCaptureRendererTouchEdge(\"touch-scroll-bottom-edge\")",
    "refreshCaptureRendererTouchEdge(\"touch-scroll-top-edge\")",
):
    owner_guard = touch.find("localHistoryTouchViewportOwnsVisibleSurfaceForNudge()")
    repaint_index = touch.find(needle)
    if owner_guard == -1 or repaint_index == -1 or owner_guard > repaint_index:
        fail(f"cache-owned finger-down history must reject live capture repaint before {needle}")
local_viewport_index = touch.find("updateLocalHistoryTouchViewportBeforeBackend(")
prefetch_index = touch.find("prefetchLocalHistoryForTouchScroll(targetKey, where, repeats, requestSerial)")
dispatch_index = touch.find("getJson(path, payload ->")
if local_viewport_index == -1 or prefetch_index == -1 or dispatch_index == -1 or not (local_viewport_index < prefetch_index < dispatch_index):
    fail("old-history one-finger scroll must update local viewport, prefetch, then dispatch /touch-scroll")

handle_touch = method_body("handleTerminalTouch")
for required in (
    "terminalTouchStartedInTerminalBody = isTerminalBodyScrollGestureTarget(event);",
    "terminalBody=",
    "boolean verticalReadingIntent = terminalTouchStartedInTerminalBody",
    "if (!terminalTouchStartedInTerminalBody) {",
    "suppressCopyPasteControlsForTerminalScroll(\"history-drag-start\")",
    "terminalTouchPhysicalUpwardHistoryGesture = false;",
    "rare inversion root",
    "identical next gesture",
    "mapping: finger down reads older; finger up returns liveward",
    "requestCaptureRendererTouchVisualFrame(\"touch-scroll-down\")",
    "defers it until MOVE feedback",
):
    if required not in handle_touch:
        # WHY(v283 one direction owner): the pre-v283 guard required the removed
        # live-bottom-only sign override. That made otherwise identical gestures
        # reverse according to prior state—the exact installed complaint. Require
        # the current fail-closed reset and its canonical natural-mapping rationale;
        # later MOVE/visual/release checks still prove both semantic directions.
        fail(f"one-finger history start must preserve the canonical direction owner via {required!r}")

# WHY(v283 single direction owner): v270-v282 could arm a second renderer-pointer
# sign convention only for a fresh live-bottom gesture. The next identical drag
# then used Java's natural mapping and reversed/slowed. These compatibility fields
# may remain for rollback analysis, but every assignment must fail closed and the
# alternate pointer path must stay disconnected.
for selector in (
    "terminalTouchPhysicalUpwardHistoryGesture",
    "terminalTouchContinuesReleasedReadHoldPhysicalUpwardHistoryGesture",
    "captureRendererReleasedReadHoldPhysicalUpwardHistoryGesture",
):
    assignments = re.findall(
        rf"\b{re.escape(selector)}\s*=\s*(true|false)\s*;",
        source,
    )
    if not assignments or set(assignments) != {"false"}:
        fail(f"retired split-brain direction selector can re-arm: {selector}={assignments}")
for forbidden_call in (
    "beginCaptureRendererPointerForwarding(event);",
    "forwardCaptureRendererPointerMove(event);",
):
    if forbidden_call in source:
        fail(f"retired renderer-pointer gesture owner reconnected via {forbidden_call!r}")

touch_visual_frame = method_body("requestCaptureRendererTouchVisualFrame")
for required in (
    "currentApkActionOwnsActiveTouchScroll()",
    "touchScrollFirstLocalFeedbackElapsedMs() < 0",
    "result=deferred-before-local-feedback",
    "stationary",
    "commitCaptureRendererVisualFrame(safeReason)",
):
    if required not in touch_visual_frame:
        fail(f"scroll visual frames must defer until local feedback owns the first pixel via {required!r}")
defer_index = touch_visual_frame.find("result=deferred-before-local-feedback")
commit_index = touch_visual_frame.find("commitCaptureRendererVisualFrame(safeReason)")
if defer_index == -1 or commit_index == -1 or defer_index > commit_index:
    fail("touch visual frame commit must be gated by deferred-before-local-feedback first")

terminal_bounds = method_body("isTerminalBodyScrollGestureTarget")
for required in (
    "TERMINAL_BODY_TOUCH_TOP_CHROME_GUARD_DP",
    "terminalBodyToolbarBoundaryGuardPx()",
    "height - bottomGuard",
):
    if required not in terminal_bounds:
        fail(f"terminal body gesture bounds must stay separated from toolbar controls via {required!r}")

toolbar_boundary = method_body("terminalBodyToolbarBoundaryGuardPx")
for required in (
    "failed installed T5 gesture",
    "bottom toolbar/Copy/Paste controls",
    "Math.max(dp(TERMINAL_BODY_TOUCH_BOTTOM_TOOLBAR_GUARD_DP), terminalTouchSlop)",
):
    if required not in toolbar_boundary:
        fail(f"terminal toolbar boundary guard missing regression WHY or slop guard via {required!r}")

long_press = method_body("scheduleTerminalLongPressCopy")
if "!terminalTouchStartedInTerminalBody" not in long_press:
    fail("terminal long-press Copy/Paste must not arm outside the terminal-body scroll target")

show_copy = method_body("showCopyPasteControls")
for required in (
    "isCopyPasteSuppressedByTerminalScroll()",
    "result=suppressed-terminal-scroll",
):
    if required not in show_copy:
        fail(f"Copy/Paste controls must be suppressed during terminal scroll classification via {required!r}")

touch_sample = method_body("processHistoryDragSample")
for required in (
    "nudgeCaptureRendererForHistorySample(y, lineThreshold);",
    'String where = step > 0 ? "lineUp" : "lineDown";',
    "natural touch scrollback",
    "dragging the finger down moves into older tmux history",
    "dragging up returns toward live bottom",
    "settleLocalHistoryFingerNudge",
    "Skipping the",
    "small finger movement jumps massive amounts",
):
    if required not in touch_sample:
        fail(f"one-finger MOVE must keep the single natural direction owner via {required!r}")
if "terminalTouchPhysicalUpwardHistoryGesture" in touch_sample:
    fail("one-finger MOVE reintroduced the retired state-dependent direction override")
if "prefetchLocalHistoryForTouchScroll(targetKey, where, repeats, requestSerial)" not in touch:
    fail("physical upward old-history route must still pass through sendHistoryScrollFromTouch prefetch")
nudge_index = touch_sample.find("nudgeCaptureRendererForHistorySample(y, lineThreshold);")
step_index = touch_sample.find("float step = y - terminalLastHistoryDragY;")
if nudge_index == -1 or step_index == -1 or nudge_index > step_index:
    fail("one-finger MOVE must schedule the v2.8/v2.96 pixel nudge before row-threshold dispatch")
for forbidden in (
    "localHistoryRowCommitOwnsThisSample",
    "row-qualified-local-history-owner",
    "row-qualified MOVE must not also schedule a CSS",
):
    if forbidden in touch_sample:
        fail(f"one-finger cache movement must not bypass the fine-grained finger nudge via {forbidden!r}")

paint_inline = method_body("paintInlineLocalHistoryRowsInRenderer")
for required in (
    "allowInlineLocalHistoryPaint(",
    "localViewportOffset",
    "localHistoryCachedRows",
    "flushLocalHistoryInlineTouchNudgeBeforeRowPaint(where, source);",
    "function settleLocalHistoryFingerNudge",
    "parity.fingerNudgePx=settleLocalHistoryFingerNudge(parity);",
    "fingerNudgePx=",
    "visualCommitRows",
    "initialFingerNudge",
    "clearCaptureNudgeOnPaint",
    "r.clearTouchScrollNudge()",
    "data-wezterm-local-history-handoff-nudge-px",
    "data-wezterm-local-history-visual-commit-rows",
    "localHistoryTouchCommitVisualRowsForPaint(repeats, source)",
    "&& (\"lineUp\".equals(where) || \"lineDown\".equals(where))) ? \"true\" : \"false\"",
):
    if required not in paint_inline:
        fail(f"inline local-history row paint must settle the active finger nudge instead of replacing movement via {required!r}")

visual_sample = method_body("nudgeCaptureRendererForHistorySample")
for required in (
    'String visualWhere = visualStep > 0 ? "lineUp" : "lineDown";',
    "one semantic direction",
    'float rendererStep = "lineUp".equals(visualWhere)',
    "? Math.abs(visualStep)",
    ": -Math.abs(visualStep);",
    "nativeHistoryScrollController.dragBy(rendererStep, visualWhere)",
    "nudgeCaptureRendererForTouch(rendererStep, visualWhere)",
):
    if required not in visual_sample:
        fail(f"visual MOVE must follow the single natural direction owner via {required!r}")
if "terminalTouchPhysicalUpwardHistoryGesture" in visual_sample:
    fail("visual MOVE reintroduced the retired state-dependent direction override")
for forbidden in (
    "nativeHistoryScrollController.dragBy(visualStep, visualWhere)",
    "nudgeCaptureRendererForTouch(visualStep, visualWhere)",
):
    if forbidden in visual_sample:
        fail(f"semantic direction must not forward a conflicting raw Android Y delta via {forbidden!r}")

release_fling = method_body("dispatchHistoryReleaseFling")
if "HISTORY_DRAG_RELEASE_MOMENTUM_ENABLED = true" not in source:
    fail("velocity-qualified ACTION_UP flicks must use native release momentum")
if "HISTORY_DRAG_MOVE_NETWORK_ENABLED = false" not in source:
    fail("ACTION_MOVE must stay local and must not wait on tmux/network/cache I/O")
move_sample = method_body("processHistoryDragSample")
if "scrollTerminalFromTouch(" in move_sample:
    fail("ACTION_MOVE still dispatches tmux/network scroll")
for required in ("deferHistoryScrollUntilRelease(where, repeats);", "flushDeferredHistoryScrollOnRelease"):
    if required not in source:
        fail(f"local MOVE/release reconciliation contract missing {required!r}")
for required in (
    "terminalTouchExceededTapSlop",
    "absDy < terminalTouchSlop",
    "maximumFlingVelocityPxPerSecond()",
    "computeCurrentVelocity(1000, maximumFlingVelocity)",
    "getYVelocity(event.getPointerId(0))",
    "isFlingVelocity(releaseVelocity)",
    "bindNativeHistoryMomentumFence()",
    'String where = signedReleaseVelocity > 0 ? "lineUp" : "lineDown";',
    "finger-down flicks",
    "finger-up flicks return toward live bottom",
):
    if required not in release_fling:
        fail(f"release momentum must preserve the single natural direction owner via {required!r}")
if "terminalTouchPhysicalUpwardHistoryGesture" in release_fling:
    fail("release momentum reintroduced the retired state-dependent direction override")
for forbidden in (
    "skipped-local-history-finger-anchor",
    "HISTORY_DRAG_RELEASE_FLICK_MAX_MS",
    "HISTORY_DRAG_RELEASE_MIN_LINES",
    "HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC",
    "signedReleaseVelocity = totalDy * 1000f / durationMs",
    "scrollTerminalFromTouch(",
):
    if forbidden in release_fling:
        fail(f"release fling must use platform velocity/touch thresholds and local pixels, not the old guessed/queued path {forbidden!r}")

flush_local_nudge = method_body("flushLocalHistoryInlineTouchNudgeBeforeRowPaint")
for required in (
    "flushed-before-row-paint",
    "pendingLocalHistoryTouchVisualNudgePx",
    "runLocalHistoryInlineTouchNudge(localHistoryTouchVisualNudgeGeneration, where)",
    "row/chunk",
):
    if required not in flush_local_nudge:
        fail(f"cached row paint must flush pending finger delta before repaint via {required!r}")

threshold = method_body("historyDragLineThresholdPx")
for required in (
    "lastAcceptedCaptureRendererRows.size()",
    "lastAcceptedCaptureRendererPaintRows.size()",
    "terminalFrame.getHeight()",
    "fallbackRowHeight",
    "terminal scrollback row commits must be paced by terminal cell",
):
    if required not in threshold:
        fail(f"one-finger row commits must be gated by measured terminal row height via {required!r}")
if "Math.max(terminalTouchSlop, dp(HISTORY_DRAG_LINE_THRESHOLD_DP))" in touch_sample:
    fail("processHistoryDragSample must not gate row commits by tiny dp/touch-slop threshold")

prefetch = method_body("prefetchLocalHistoryForTouchScroll")
for required in (
    "LocalHistoryRequest request = localHistoryRequestForTouchTarget(targetKey);",
    "shouldBlockLocalHistoryTouchForRequest(request)",
    "findCachedLocalHistoryChunk(request, \"\")",
    "renderInlineLocalHistoryRowsForTouch(",
    "inline-local-history-cache-fill",
    "seedInlineLocalHistoryRowsForTouch(request, where, repeats, requestSerial)",
    '"/scrollback/chunk?windowId=" + urlEncode(request.windowId)',
    "rememberLocalHistoryChunk(finalRequest, \"\", payload)",
    "stage=local-history-cache endpoint=/scrollback/chunk result=prefetch-scheduled",
    "stage=local-history-cache endpoint=/scrollback/chunk result=prefetch-stored",
    "stage=local-history-cache endpoint=/scrollback/chunk result=prefetch-hit",
    "localHistoryAdjacentPrefetchSourceChunk(request, cached)",
    "localHistoryAdjacentPrefetchSourceChunk(finalRequest, chunk)",
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
):
    if required not in prefetch:
        fail(f"touch-scroll prefetch must use/log target-bound local history cache via {required!r}")
for forbidden in (
    "showLocalHistoryDialog",
    "toast(",
    "getJsonWithRetry",
    "showDockedPromptComposer",
    "postPromptComposerSubmit",
    "goLiveBottom",
    "/live-bottom",
    "/terminal-frame",
):
    if forbidden in prefetch:
        fail(f"touch-scroll prefetch must not mutate live UI/backend owner {forbidden!r}")
cache_miss_index = prefetch.find("LocalHistoryChunk cached = findCachedLocalHistoryChunk(request, \"\")")
seed_index = prefetch.find("seedInlineLocalHistoryRowsForTouch(request, where, repeats, requestSerial)")
network_index = prefetch.find("getJson(path, payload ->")
if cache_miss_index == -1 or seed_index == -1 or network_index == -1 or not (cache_miss_index < seed_index < network_index):
    fail("cold one-finger scroll must seed readable local rows after cache miss and before /scrollback/chunk network fetch")
stored_index = prefetch.find("result=prefetch-stored")
fill_index = prefetch.find("inline-local-history-cache-fill")
adjacent_index = prefetch.find("prefetchAdjacentLocalHistoryChunkForTouch(finalRequest, adjacentSource.payload, requestSerial)")
if stored_index == -1 or fill_index == -1 or adjacent_index == -1 or not (stored_index < fill_index < adjacent_index):
    fail("cold /scrollback/chunk fill must immediately paint the fetched chunk before adjacent background prefetch")

prewarm = method_body("prewarmLocalHistoryForRequest")
for required in (
    "findCachedLocalHistoryChunk(request, \"\")",
    "localHistoryTouchPrefetchInFlight.containsKey(prewarmKey)",
    '"/scrollback/chunk?windowId=" + urlEncode(request.windowId)',
    "stage=local-history-cache endpoint=/scrollback/chunk result=prewarm-scheduled",
    "stage=local-history-cache endpoint=/scrollback/chunk result=prewarm-stored",
    "rememberLocalHistoryChunk(finalRequest, \"\", payload)",
    "maybeRenderPrewarmedLocalHistoryForActiveTouch(finalRequest, chunk, safeReason)",
    "prefetchAdjacentLocalHistoryChunkForTouch(finalRequest, adjacentSource.payload, activeHistoryScrollRequestSerial)",
):
    if required not in prewarm:
        fail(f"session/accepted-frame prewarm must preload target-bound scrollback before touch transition via {required!r}")
for forbidden in (
    "showLocalHistoryDialog",
    "toast(",
    "getJsonWithRetry",
    "/terminal-frame",
    "/touch-scroll",
):
    if forbidden in prewarm:
        fail(f"prewarm must not depend on dialog/live frame/touch-scroll owner {forbidden!r}")

prewarm_render = method_body("maybeRenderPrewarmedLocalHistoryForActiveTouch")
for required in (
    "activeHistoryScrollRequestSerial <= 0",
    "terminalHistoryDragWhere",
    "pendingHistoryScrollWhere",
    "localHistoryRequestForTouchTarget(terminalTouchStableWindowId)",
    "inline-local-history-cache-prewarm-fill-",
    "activeHistoryScrollRequestSerial",
):
    if required not in prewarm_render:
        fail(f"prewarmed scrollback must be able to paint into the current active touch gesture via {required!r}")

scroll_start = source.find("private void scrollTerminalFromTouch(String where, int repeats, boolean fromMomentum, String targetKey)")
scroll_end = source.find("private void sendHistoryScrollFromTouch", scroll_start)
if scroll_start == -1 or scroll_end == -1:
    fail("missing full scrollTerminalFromTouch overload")
scroll_from_touch = source[scroll_start:scroll_end]
for required in (
    "updateLocalHistoryTouchViewportBeforeBackend(",
    "touch-scroll-in-flight",
    "prefetchLocalHistoryForTouchScroll(",
    "sendHistoryScrollFromTouch(where, boundedRepeats, gestureGeneration, stableTargetKey, directionGeneration)",
):
    if required not in scroll_from_touch:
        fail(f"scrollTerminalFromTouch must keep the APK-local viewport moving during in-flight backend requests via {required!r}")
inflight_update = scroll_from_touch.find("updateLocalHistoryTouchViewportBeforeBackend(")
inflight_prefetch = scroll_from_touch.find("prefetchLocalHistoryForTouchScroll(")
if inflight_update == -1 or inflight_prefetch == -1 or inflight_update > inflight_prefetch:
    fail("in-flight one-finger scroll must update local viewport before background cache prefetch")

viewport_before_backend = method_body("updateLocalHistoryTouchViewportBeforeBackend")
if "private boolean updateLocalHistoryTouchViewportBeforeBackend" not in source:
    fail("local viewport update must return whether prefetch/inline cache work should be suppressed")
for required in (
    "LocalHistoryRequest request = localHistoryRequestForTouchTarget(targetKey);",
    "shouldBlockLocalHistoryTouchForRequest(request)",
    "findCachedLocalHistoryChunk(request, \"\")",
    "shouldHoldLocalHistoryTouchTopEdge(request, where)",
    "recordLocalHistoryTouchTopEdgeHold(request, where, repeats, requestSerial, source)",
    "seedLocalHistoryTouchViewportFromAcceptedFrame(",
    "logLocalHistoryTouchViewportNoop(",
    "unsupported-where",
    "missing-window",
    "no-cached-scrollback-chunk",
    "empty-or-target-mismatched-viewport",
    "offset-unchanged-after-clamp",
    "needs-local-history-cache-seed",
    "localHistoryEstimatedVisualRowsBetweenOffsets(previousOffset, nextOffset)",
    "shouldHoldLocalHistoryLogicalRowCommitForFingerResidual(",
    "paintHeldLocalHistoryViewportBeforeRowCommit(",
    "transferCaptureRendererTouchNudgeToLocalHistoryHandoff(",
    "localHistoryTouchViewportPaintFingerNudgePx = localHistoryTouchFingerNudgeResidualPx",
    "localHistoryTouchViewportOffset = nextOffset",
    "localHistoryTouchViewportLastVisualRowsConsumed = visualRowsConsumed",
    "stage=local-history-viewport endpoint=local-cache result=offset-updated-before-touch-scroll",
    "visualRowsConsumed=",
    "generationKey=",
    "beforeTouchScrollResponse=true",
    "beforeScrollbackChunkResponse=true",
    "beforeTerminalFrameResponse=true",
    "hasMoreBefore=",
    "copyModeViewport=",
    "currentOffset=",
    "maxOffset=",
    "paintLocalHistoryTouchViewport(",
):
    if required not in viewport_before_backend:
        fail(f"local viewport owner must update cached history before backend response via {required!r}")
hold_index = viewport_before_backend.find("shouldHoldLocalHistoryLogicalRowCommitForFingerResidual(")
hold_paint_index = viewport_before_backend.find("paintHeldLocalHistoryViewportBeforeRowCommit(")
transfer_index = viewport_before_backend.find("transferCaptureRendererTouchNudgeToLocalHistoryHandoff(")
paint_nudge_index = viewport_before_backend.find("localHistoryTouchViewportPaintFingerNudgePx = localHistoryTouchFingerNudgeResidualPx")
if -1 in (hold_index, hold_paint_index, transfer_index, paint_nudge_index) or not (hold_index < hold_paint_index < transfer_index < paint_nudge_index):
    fail("bottom-to-local-history handoff must hold visual rows before transferring/clearing the live capture nudge")
if "hold-before-transfer/no-clear-before-hold" not in viewport_before_backend:
    fail("visual-row hold must keep a WHY comment for no-clear-before-hold ownership")
hold_branch = viewport_before_backend[hold_index:transfer_index]
if "paintHeldLocalHistoryViewportBeforeRowCommit(" not in hold_branch or "return false;" not in hold_branch:
    fail("visual-row hold must paint the cached DOM surface before suppressing the logical row commit")
if "previousOffset == localHistoryTouchViewportOffset" not in viewport_before_backend:
    fail("local viewport owner must not report success when clamp leaves the offset unchanged")

hold_surface_paint = method_body("paintHeldLocalHistoryViewportBeforeRowCommit")
for required in (
    "local-history-finger-nudge-missing",
    "before advancing the logical row offset",
    "localHistoryTouchFingerNudgeResidualPx += captureRendererTouchFingerNudgeResidualPx",
    "clearCaptureRendererTouchNudgeForLocalHistoryHandoff(",
    "localHistoryTouchViewportPaintFingerNudgePx = localHistoryTouchFingerNudgeResidualPx",
    "result=hold-surface-painted-before-row-commit",
    "paintLocalHistoryTouchViewport(",
    "\"inline-local-history-viewport\"",
):
    if required not in hold_surface_paint:
        fail(f"visual-row hold surface paint must preserve clean transition behavior via {required!r}")
visual_hold = method_body("shouldHoldLocalHistoryLogicalRowCommitForFingerResidual")
for required in (
    "result=visual-row-hold",
    "visualRowsConsumed",
    "localHistoryTouchFingerNudgeResidualPx",
    "captureRendererTouchFingerNudgeResidualPx",
    "projectedResidualPx",
    "captureResidualPx=",
    "handoff=no-clear-before-hold",
    "without clearing it",
    "requiredPx",
    "logical tmux/cache rows",
    "wrapped visual height",
):
    if required not in visual_hold:
        fail(f"local-history logical row commits must be held until the finger covers wrapped visual rows via {required!r}")
for forbidden in (
    "localHistoryTouchViewportOffset += step",
    "localHistoryTouchViewportOffset -= step",
    "commitPx=lineHeight*Math.max(1,n(commitRepeats))",
):
    if forbidden in viewport_before_backend or forbidden in paint_inline:
        fail(f"cached scrollback must not settle logical rows as visible lines via {forbidden!r}")
if "result=offset-updated-before-touch-scroll" in viewport_before_backend.split("previousOffset == localHistoryTouchViewportOffset", 1)[0]:
    fail("local viewport owner must check offset unchanged before logging offset-updated success")

# `/scrollback/chunk` rows are ordered oldest-to-newest. The live/current slice
# is therefore the maximum offset, lineUp moves toward zero, and lineDown moves
# back toward max. Reversing this coordinate system makes the first MOVE jump
# from current output to the oldest row in the cached chunk.
line_up_offset = viewport_before_backend.find('if ("lineUp".equals(where))')
offset_decrement = viewport_before_backend.find("nextOffset -= step;", line_up_offset)
line_down_offset = viewport_before_backend.find("} else {", offset_decrement)
offset_increment = viewport_before_backend.find("nextOffset += step;", line_down_offset)
if -1 in (line_up_offset, offset_decrement, line_down_offset, offset_increment) or not (
        line_up_offset < offset_decrement < line_down_offset < offset_increment):
    fail("cached history coordinates must move lineUp toward offset zero and lineDown toward the live max offset")

safe_cache_gate = method_body("shouldBlockLocalHistoryTouchForRequest")
for required in (
    "targetHasConfirmedTranscriptBodyBlock(request.windowId)",
    "hasSafeTargetBoundLocalHistoryCache(request)",
    "canPrefetchLocalHistoryBeforeAcceptedFrame(request)",
    "Termux/Jackpal-style scrollback",
    "safe target-bound APK-local rows",
    "transcript/proof/source rejection still happens before",
    "shouldBlockLocalHistoryTouchForTarget(request.windowId)",
):
    if required not in safe_cache_gate:
        fail(f"local viewport must bypass continuity-only body uncertainty only for safe target-bound cached rows via {required!r}")
gate_confirmed_idx = safe_cache_gate.find("targetHasConfirmedTranscriptBodyBlock(request.windowId)")
gate_cache_idx = safe_cache_gate.find("hasSafeTargetBoundLocalHistoryCache(request)")
gate_prefetch_idx = safe_cache_gate.find("canPrefetchLocalHistoryBeforeAcceptedFrame(request)")
gate_fallback_idx = safe_cache_gate.find("shouldBlockLocalHistoryTouchForTarget(request.windowId)")
if -1 in (gate_confirmed_idx, gate_cache_idx, gate_prefetch_idx, gate_fallback_idx) or not (gate_confirmed_idx < gate_cache_idx < gate_prefetch_idx < gate_fallback_idx):
    fail("confirmed transcript blocks must run before safe-cache/pre-accepted-frame bypasses, which must run before the legacy body-continuity fallback")

prefetch_before_frame = method_body("canPrefetchLocalHistoryBeforeAcceptedFrame")
for required in (
    "!targetHasConfirmedTranscriptBodyBlock(request.windowId)",
    "targetHasUsableAcceptedBodyFrame(request.windowId)",
    "isStableVisibleLocalHistoryTouchTarget(request.windowId)",
    "stable visible/current target",
):
    if required not in prefetch_before_frame:
        fail(f"local history cache prefetch must allow same visible target before first accepted frame without bypassing confirmed transcript blocks via {required!r}")

stable_visible_target = method_body("isStableVisibleLocalHistoryTouchTarget")
for required in (
    "visibleTerminalTargetKey()",
    "confirmedVisibleTerminalTargetKey()",
    "currentWebViewWindowIdTargetKey()",
    "captureRendererWindowTargetKey",
    "selectedPhoneWindowId",
):
    if required not in stable_visible_target:
        fail(f"pre-accepted-frame local history prefetch must be limited to stable visible target evidence via {required!r}")

prefetch_method = method_body("prefetchLocalHistoryForTouchScroll")
for required in (
    "canPrefetchLocalHistoryBeforeAcceptedFrame(request)",
    "result=prefetch-allowed",
    "stable-visible-target-before-accepted-frame",
):
    if required not in prefetch_method:
        fail(f"scrollback prefetch must log when same-visible-target cache fetch is unblocked before accepted frame via {required!r}")

safe_cache = method_body("hasSafeTargetBoundLocalHistoryCache")
for required in (
    "hasSafeCachedLocalHistoryChunk(request)",
    "hasSafeLocalHistoryTouchViewportRows(request)",
):
    if required not in safe_cache:
        fail(f"safe local-history cache gate missing {required!r}")

safe_chunk = method_body("hasSafeCachedLocalHistoryChunk")
for required in (
    "findCachedLocalHistoryChunk(request, \"\")",
    "localHistoryChunkHasSafePaintRows(cached)",
):
    if required not in safe_chunk:
        fail(f"safe chunk gate must reuse target-bound cache identity and row rejection via {required!r}")

safe_viewport = method_body("hasSafeLocalHistoryTouchViewportRows")
for required in (
    "request.windowId.equals(localHistoryTouchViewportWindowId)",
    "localHistoryTouchViewportGenerationKey.isEmpty()",
    "localHistoryTouchViewportAlreadyRenderedOnly",
    "localHistoryPaintRowsRejectReason(",
    "new ArrayList<>(localHistoryTouchViewportRows)",
    "true",
):
    if required not in safe_viewport:
        fail(f"safe viewport gate must require cached target identity and ANSI/source rejection via {required!r}")

safe_rows = method_body("localHistoryChunkHasSafePaintRows")
for required in (
    "localHistoryPaintRowsFromPayload(chunk.payload, true)",
    "localHistoryPaintRowsRejectReason(rows, true).isEmpty()",
):
    if required not in safe_rows:
        fail(f"safe cached chunk rows must be parsed as ANSI and pass transcript/proof/source rejection via {required!r}")

confirmed_block = method_body("targetHasConfirmedTranscriptBodyBlock")
for required in (
    "lastSessionInteractionState",
    "lastSessionInteractionBlockedReason",
    "isConfirmedTranscriptBodyBlockValue(state)",
    "isConfirmedTranscriptBodyBlockValue(reason)",
):
    if required not in confirmed_block:
        fail(f"confirmed transcript body block must remain a hard local-history stop via {required!r}")

confirmed_value = method_body("isConfirmedTranscriptBodyBlockValue")
for required in (
    '"transcript-only".equals(value)',
    "it is not proof that the",
    "safe target-bound `/scrollback/chunk` prewarm",
):
    if required not in confirmed_value:
        fail(f"confirmed transcript block values must distinguish transcript-only from continuity-only uncertainty: {required!r}")
if '"fresh-codex-shell-no-transcript-continuity".equals(value)' in confirmed_value:
    fail("fresh-codex-shell-no-transcript-continuity must not be treated as confirmed transcript-only for local scrollback prewarm")

viewport_seed = method_body("seedLocalHistoryTouchViewportFromAcceptedFrame")
for required in (
    "logLocalHistoryTouchViewportNoop(",
    "missing-window",
    "accepted-frame-mismatch",
    "no-accepted-frame-rows",
    "needs-local-history-cache-seed",
    "localHistoryPaintRowsRejectReason(rows, true)",
    "inline-local-history-viewport-seed",
):
    if required not in viewport_seed:
        fail(f"accepted-frame viewport seed must log action-correlated no-op reason via {required!r}")

viewport_noop = method_body("logLocalHistoryTouchViewportNoop")
for required in (
    "stage=local-history-viewport endpoint=local-cache",
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
    'tapId=" + safeLogToken(currentApkTapIdForLog())',
    "cacheRows=",
    "viewportRows=",
    "renderRows=",
    "currentOffset=",
    "maxOffset=",
    "hasMoreBefore=",
    "copyModeViewport=",
    "scrollPosition=",
    "historySize=",
    "paneInMode=",
    "targetBodyContinuityProven=",
    "state=",
    "blockedReason=",
    "beforeTouchScrollResponse=true",
    "beforeScrollbackChunkResponse=true",
    "beforeTerminalFrameResponse=true",
):
    if required not in viewport_noop:
        fail(f"local viewport no-op log must expose boundary/action context via {required!r}")

for helper in (
    "localHistoryBoundaryEvidenceForRequest",
    "localHistoryBoundaryHasMoreBeforeForRequest",
    "localHistoryBoundaryCopyModeForRequest",
    "localHistoryTouchViewportMaxOffset",
):
    method_body(helper)

read_hold = method_body("captureRendererReadHoldActive")
for required in (
    "readModeSuppressesKeyboard",
    "terminalHistoryViewportActive",
    "terminalHistoryDragActive",
    "terminalHistoryMomentumActive",
    "historyScrollRequestInFlight",
    "!pendingHistoryScrollWhere.isEmpty()",
    "terminalBottomRestoreInFlight",
):
    if required not in read_hold:
        fail(f"native read hold must cover touch/read/in-flight state before renderer refresh via {required!r}")

sync_read_hold = method_body("syncCaptureRendererReadHold")
for required in (
    "window.__weztermNativeReadHold",
    "captureRendererReadHoldActive()",
    "generic live/idle refreshes",
    "accepted-frame seed metadata",
):
    if required not in sync_read_hold:
        fail(f"native read hold sync must expose why/state into WebView via {required!r}")

telemetry = method_body("handleCaptureRendererTelemetry")
suppression_index = telemetry.find("shouldSuppressCaptureRendererTelemetryForReadHold(payload)")
remember_index = telemetry.find("rememberAcceptedCaptureRendererFrame(payload)")
readable_index = telemetry.find("maybeMarkApkFirstReadableFrame(payload")
if suppression_index == -1 or remember_index == -1 or readable_index == -1:
    fail("capture renderer telemetry must explicitly gate read-hold suppression, accepted-frame memory, and readable-frame logging")
if not (suppression_index < remember_index and suppression_index < readable_index):
    fail("read-hold telemetry suppression must run before accepted-frame memory or readable-frame success logging")

suppression = method_body("shouldSuppressCaptureRendererTelemetryForReadHold")
for required in (
    "captureRendererReadHoldActive()",
    "captureRendererTelemetryAllowedDuringReadHold(endpoint, reason)",
    '"/terminal-frame".equals(endpoint)',
    '"dom-apply".equals(endpoint)',
    '"visual-commit-request".equals(endpoint)',
):
    if required not in suppression:
        fail(f"read-hold telemetry suppression missing endpoint/hold guard {required!r}")

hook = method_body("captureRendererTelemetryHookScript")
for required in (
    "function nativeReadHold()",
    "window.__weztermNativeReadHold",
    "function refreshAllowedDuringNativeReadHold(forceActiveTouchCommit,refreshReason)",
    "function bridgeNativeReadHoldSkip(endpoint,reason)",
    "skipReason:'native-read-hold'",
    "if(!refreshAllowedDuringNativeReadHold(!!forceActiveTouchCommit,lastReason))",
    "if(!refreshAllowedDuringNativeReadHold(false,lastReason))",
):
    if required not in hook:
        fail(f"capture renderer telemetry hook must block generic refresh under native read hold via {required!r}")
refresh_guard_index = hook.find("if(!refreshAllowedDuringNativeReadHold(!!forceActiveTouchCommit,lastReason))")
original_refresh_index = hook.find("return originalRefresh.apply(this,arguments)")
idle_guard_index = hook.find("if(!refreshAllowedDuringNativeReadHold(false,lastReason))")
original_idle_index = hook.find("return originalRefreshIfIdle.apply(this,arguments)")
if -1 in (refresh_guard_index, original_refresh_index, idle_guard_index, original_idle_index):
    fail("capture renderer read-hold hook missing refresh guard or original refresh call")
if not (refresh_guard_index < original_refresh_index and idle_guard_index < original_idle_index):
    fail("native read-hold guards must run before original renderer refresh/fetch work")

seed = method_body("seedInlineLocalHistoryRowsForTouch")
for required in (
    "lastAcceptedCaptureRendererPaintRowsForLocalHistorySeed()",
    "localHistoryPaintRowsRejectReason(paintRows, true)",
    "paintInlineLocalHistoryRowsInRenderer(",
    "source=inline-local-history-seed",
    "result=seeded-before-network",
    "firstNewReadableRow=false",
    "alreadyRenderedOnly=true",
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
):
    if required not in seed:
        fail(f"cold-cache seed must create/log already-rendered local continuity before network via {required!r}")
for forbidden in (
    "getJson(",
    "getJsonWithRetry(",
    "showLocalHistoryDialog",
    "toast(",
    "/touch-scroll",
    "/scrollback/chunk",
    "/terminal-frame",
    "webView.reload",
    "loadUrl(",
):
    if forbidden in seed:
        fail(f"cold-cache seed must not depend on network/dialog/reload {forbidden!r}")

inline_render = method_body("renderInlineLocalHistoryRowsForTouch")
for required in (
    "shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)",
    "localHistoryPaintRowsFromPayload(chunk.payload, true)",
    "localHistoryPaintRowsRejectReason(paintRows, true)",
    "logInlineLocalHistoryRejected(request, source, rejectReason, where, repeats, requestSerial)",
    "localHistoryViewportSliceForTouchPaint(",
    "paintInlineLocalHistoryRowsInRenderer(",
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
):
    if required not in inline_render:
        fail(f"inline local-history render must consume/reject structured ansiRows via {required!r}")
if inline_render.find("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)") > inline_render.find("localHistoryPaintRowsFromPayload(chunk.payload, true)"):
    fail("async local-history chunk paint must be deferred before parsing/painting under an active touch owner")
if inline_render.count("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)") < 2:
    fail("async local-history chunk paint needs both early and final pre-WebView defer checks")
if inline_render.rfind("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)") > inline_render.find("paintInlineLocalHistoryRowsInRenderer("):
    fail("async local-history chunk paint must re-check active-touch deferral immediately before renderer mutation")
if "older prepared slice cannot repaint after" not in inline_render:
    fail("final local-history async paint defer must explain the prepared-slice boundary race")
for forbidden in (
    "rowsFromPayload(chunk.payload)",
    "inlineLocalHistoryRowsForTouch(",
):
    if forbidden in inline_render:
        fail(f"inline local-history render must not consume rows-only payloads via {forbidden!r}")
for forbidden in (
    "visual-nudge",
    "getJson(",
    "getJsonWithRetry(",
    "showLocalHistoryDialog",
    "toast(",
    "/touch-scroll",
    "/scrollback/chunk",
    "/terminal-frame",
):
    if forbidden in inline_render:
        fail(f"inline local-history render must not depend on network/dialog/visual-nudge {forbidden!r}")

paint_inline = method_body("paintInlineLocalHistoryRowsInRenderer")
for required in (
    "shouldSuppressStaleLocalHistoryInlinePaint(request, where, requestSerial, source)",
    'rowObject.put("ansi"',
    'rowObject.put("kind", row != null && row.hasAnsi() ? "ansiRows" : "structuredTextRows")',
	    "appendAnsiWrapped(fragment,row.ansi,row.text,paintCols)",
	    "function xtermColor(",
	    "code===38||code===48",
	    "mode===2",
	    "mode===5",
	    "measuredWrapCols()",
	    "paintRowsForCols(paintCols)",
	    "overflowAdjustedCols",
	    "wrappedRows+=appendAnsiWrapped(",
	    "data-wezterm-local-history-wrap-cols",
	    "data-wezterm-local-history-wrapped-rows",
	    "pinLocalHistoryHorizontalViewport()",
	    "visualOffsetLeftPx",
	    "visualPageLeftPx",
	    "windowScrollXPx",
	    "rectRightClipPx",
	    "overflowRightClipPx",
	    "horizontalPinned",
	    "html.style.overflowX='hidden'",
	    "active.replaceChildren(fragment)",
	    "data-wezterm-local-history-paint",
	    "paint=ansiRows",
    "window.__weztermInlineLocalHistory",
    "inlineLocalHistorySource(",
    "data-wezterm-inline-local-history",
    "inlineLocalHistoryRendererPainted(result)",
    "stopCaptureRendererIdleRealtimeRefresh(",
    '"inline-local-history-" + inlineLocalHistorySource(source)',
    "markInlineLocalHistoryFirstReadableRows(",
    '" result=" + (painted ? "painted" : "paint-rejected")',
    "source=\" + inlineLocalHistorySource(",
    "beforeScrollbackChunkResponse=\" + beforeScrollback",
    "alreadyRenderedOnly",
    "firstNewReadableRow=\" + (painted && !alreadyRenderedOnly)",
    "localViewportOffset=",
    "localHistoryCachedRows=",
	    "meaningfulRows=",
	    "beforeTouchScrollResponse=true",
	    "beforeTerminalFrameResponse=true",
	    "return 'inline-local-history-painted:viewport-parity:rightClipPx='",
	    ":rightClipPx=",
	    ":leftClipPx=",
	    ":visualOffsetLeftPx=",
	    ":visualPageLeftPx=",
	    ":windowScrollXPx=",
	    ":rectRightClipPx=",
	    ":overflowRightClipPx=",
	    ":horizontalPinned=",
	    "leftClipPx:leftClipPx",
	):
	    if required not in paint_inline:
	        fail(f"inline renderer paint must use ansiRows/structured capture-surface paint via {required!r}")
for required in (
    "function cellsText(cells)",
    "function trimCellsRight(cells)",
    "function trimCellsLeft(cells)",
    "function appendCells(parent,cells)",
    "function hardSplitIndex(cells,available)",
    "if(remaining[wi].ch===' '){splitAt=wi;}",
    "continuationPrefix",
):
    if required not in paint_inline:
        fail(f"inline renderer wrap must use word-boundary cell wrapping, not old hard slices, via {required!r}")
for forbidden in (
    "appendWrappedSegment",
    "remaining.slice(0,wrapCols)",
    "text.slice(0,wrapCols)",
):
    if forbidden in paint_inline:
        fail(f"inline renderer wrap must not use legacy hard character wrapping via {forbidden!r}")
if "code===38||code===48" not in paint_inline or "xtermColor(idx)" not in paint_inline:
    fail("inline renderer must preserve 38/48 truecolor and 256-color ANSI escapes")
stop_idle_index = paint_inline.find("stopCaptureRendererIdleRealtimeRefresh(")
stop_idle_reason_index = paint_inline.find('"inline-local-history-" + inlineLocalHistorySource(source)')
first_rows_index = paint_inline.find("markInlineLocalHistoryFirstReadableRows(")
if -1 in (stop_idle_index, stop_idle_reason_index, first_rows_index) or stop_idle_index > first_rows_index:
    fail("inline local-history paint must stop generic idle refresh before recording readable old-history rows")
for forbidden in (
    "active.textContent=rows.join",
    "active.textContent = rows.join",
):
    if forbidden in paint_inline:
        fail(f"inline renderer paint must not recreate raw joined transcript body via {forbidden!r}")
if "active.textContent=rows.join" in paint_inline or "active.textContent = rows.join" in paint_inline:
    fail("inline renderer paint must not write rows.join into active.textContent")
if "localHistoryPaintRowsRejectReason" not in source or "result=rejected-transcript-surface" not in source:
    fail("inline local-history must reject transcript/proof/source/read-session bodies instead of painting success")
for required in (
    "codex session reader",
    "end of readable transcript",
    "source-receipt.md",
    "worker-receipt.md",
    "target-visible-body-not-proven",
    "fresh-codex-shell-no-transcript-continuity",
    "body-continuity-unproven",
):
    if required not in source:
        fail(f"transcript/proof/source rejection guard missing marker {required!r}")
reject_helper = method_body("localHistoryPaintRowsRejectReason")
for required in (
    "boolean ansiBackedRows = requireAnsiRows && hasAnsi;",
    "meaningfulLocalHistoryPaintRowCount(rows)",
    "mostly-blank-ansiRows",
    "if (!ansiBackedRows && (lower.contains(\"codex session reader\")",
    "if (!ansiBackedRows && (receiptPath || receiptFields))",
):
    if required not in reject_helper:
        fail(f"proof/source rejection must not reject ANSI-backed terminal scrollback via {required!r}")
if "if (receiptPath || receiptFields)" in reject_helper:
    fail("proof/source rejection must not reject ANSI-backed terminal rows solely by content markers")
if "var ansiBacked=" not in paint_inline or "&&( !ansiBacked)" in paint_inline:
    fail("inline renderer must compute ansiBacked before rejecting transcript/source markers")
if "if((receiptLike||transcriptLike)&&!ansiBacked)" not in paint_inline:
    fail("inline renderer must reject transcript/source markers only for non-ANSI fallback bodies")
for forbidden in (
    "webView.reload",
    "loadUrl(",
    "r.refresh(",
    "r.refreshIfIdle(",
    "window.__mantisCaptureRenderer.refresh",
):
    if forbidden in paint_inline:
        fail(f"inline renderer paint must not reload or poll backend renderer {forbidden!r}")
if "data-mantis-capture-renderer" in paint_inline and "data-wezterm-inline-local-history" in paint_inline:
    for match in re.finditer(r"max-height\s*:\s*([0-9]+(?:\.[0-9]+)?)%", paint_inline):
        if float(match.group(1)) < 100:
            fail("inline local-history feedback must not use a partial max-height over the capture renderer")
    if "border-bottom" in paint_inline:
        fail("inline local-history feedback must not draw a visible bottom seam over the capture renderer")
    for match in re.finditer(r"z-index\s*:\s*([0-9]+)", paint_inline):
        if int(match.group(1)) >= 100000:
            fail("inline local-history feedback must not use an extreme z-index overlay above the capture renderer")
    absolute_top = "position:absolute" in paint_inline and "top:0" in paint_inline
    full_surface = (
        "bottom:0" in paint_inline
        or "height:100%" in paint_inline
        or "max-height:100%" in paint_inline
    )
    if absolute_top and not full_surface:
        fail("inline local-history feedback over the capture renderer must be full-surface, not a top-only overlay")

mark_inline = method_body("markInlineLocalHistoryFirstReadableRows")
for required in (
    "stage=local-history-inline endpoint=local-cache result=first-new-readable-row",
    "source=\" + inlineLocalHistorySource(",
    "firstNewReadableRow=true",
    "alreadyRenderedOnly=false",
    "beforeTouchScrollResponse=true",
    "beforeScrollbackChunkResponse=\" + beforeScrollback",
    "beforeTerminalFrameResponse=true",
    "inlineFeedbackRows=",
    "localViewportOffset=",
    "localHistoryCachedRows=",
    "localHistoryChunkLines=",
    "meaningfulRows=",
):
    if required not in mark_inline:
        fail(f"inline first readable row log missing {required!r}")

canonical_slice = method_body("localHistoryViewportSliceForTouchPaint")
for required in (
    "canonical-viewport-preserved",
    "chunk-local-paint-suppressed",
    "localHistoryTouchViewportCanOwnChunk(request, chunk)",
    "localHistoryTouchViewportHasCanonicalRowsFor(request)",
    "inlineLocalHistoryPaintRowsForTouch(",
):
    if required not in canonical_slice:
        fail(f"local-history touch paint must keep one canonical viewport owner via {required!r}")

for helper in (
    "localHistoryTouchViewportHasCanonicalRowsFor",
    "localHistoryTouchViewportTargetsRequest",
    "localHistoryTouchViewportCanOwnChunk",
    "localHistoryPaintGenerationKey",
    "meaningfulLocalHistoryPaintRowCount",
):
    method_body(helper)

local_viewport = method_body("inlineLocalHistoryPaintRowsForTouch")
for required in (
    "LocalHistoryViewportSlice",
    "localHistoryTouchViewportKey(request, chunk, source, alreadyRenderedOnly)",
    "localHistoryTouchViewportOffset",
    "shouldPreserveLocalHistoryTouchViewportTrackForAction(",
    "stage=local-history-owner endpoint=local-cache result=viewport-moved",
    "beforeTouchScrollResponse=true",
    "alreadyRenderedOnly=",
    "firstNewReadableRow=",
    "localViewportOffset=",
    "localHistoryCachedRows=",
):
    if required not in local_viewport:
        fail(f"local history scroll owner must move target-bound cached viewport via {required!r}")
if "int maxRows = 36" in local_viewport or re.search(r"maxRows\s*=\s*36\b", local_viewport):
    fail("local history scroll owner must not revive the old 36-row one-shot preview")

preserve_track = method_body("shouldPreserveLocalHistoryTouchViewportTrackForAction")
for required in (
    "track-switch-suppressed",
    "currentViewportKey=",
    "nextViewportKey=",
    "localHistoryTouchViewportActionId",
    "same-action",
):
    if required not in preserve_track:
        fail(f"same-action local-history track switch guard missing {required!r}")

paint_viewport = method_body("paintLocalHistoryTouchViewport")
for required in (
    "LocalHistoryViewportSlice viewportSlice = localHistoryTouchViewportSlice()",
    "paintInlineLocalHistoryRowsInRenderer(",
):
    if required not in paint_viewport:
        fail(f"local viewport paint must pass a LocalHistoryViewportSlice into the renderer via {required!r}")
if "paintInlineLocalHistoryRowsInRenderer(\n                rows," in paint_viewport:
    fail("local viewport paint must not pass raw LocalHistoryPaintRow lists to the renderer")

adjacent_prefetch = method_body("prefetchAdjacentLocalHistoryChunkForTouch")
for required in (
    "localHistoryPreviousStartFromPayload(payload)",
    "LocalHistoryChunk cachedBoundary = findCachedLocalHistoryChunk(request, start);",
    "mergeLocalHistoryTouchViewportBoundary(",
    "inline-local-history-cache-boundary-hit",
    "inline-local-history-cache-boundary-fill",
):
    if required not in adjacent_prefetch:
        fail(f"/scrollback/chunk adjacent fills must merge into the APK-local viewport via {required!r}")

adjacent_source = method_body("localHistoryAdjacentPrefetchSourceChunk")
for required in (
    "prefetch-adjacent-source",
    "localHistoryTouchViewportStart",
    "findCachedLocalHistoryChunk(request, start)",
    "current oldest cached boundary",
    "nextPrevStart=",
):
    if required not in adjacent_source:
        fail(f"adjacent prefetch must chain from the current cached boundary via {required!r}")

top_edge = method_body("recordLocalHistoryTouchTopEdgeHold")
for required in (
    "local-cache-top-edge-hold",
    "suppressPrefetch=true",
    "suppressInlinePaint=true",
    "currentOffset=",
    "maxOffset=",
):
    if required not in top_edge:
        fail(f"top-edge hold must visibly suppress stale cache work via {required!r}")

stale_paint = method_body("shouldSuppressStaleLocalHistoryInlinePaint")
for required in (
    "stale-paint-suppressed",
    "staleSerial",
    "staleDirection",
    "heldTopEdgePaint",
    "activeHistoryScrollRequestSerial",
    "terminalHistoryDragWhere",
):
    if required not in stale_paint:
        fail(f"stale local-history inline paint suppression missing {required!r}")

boundary_merge = method_body("mergeLocalHistoryTouchViewportBoundary")
for required in (
    "localHistoryPaintRowsFromPayload(chunk.payload, true)",
    "`/scrollback/chunk` is a cache-boundary fill",
    "ensureLocalHistoryTouchViewportRows(",
    "clearLocalHistoryTouchTopEdgeHold(request, \"boundary-extended\", requestSerial)",
    "shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)",
    "paintLocalHistoryTouchViewport(",
    "prependBoundary",
):
    if required not in boundary_merge:
        fail(f"local viewport boundary merge must preserve ANSI cache ownership via {required!r}")
if boundary_merge.find("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)") > boundary_merge.find("paintLocalHistoryTouchViewport("):
    fail("cache boundary merge must defer active-touch async paint before repainting local-history rows")

async_paint_defer = method_body("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch")
for required in (
    "activeTouchScrollLocalFeedbackOwner()",
    '"inline-local-history-viewport".equals(rawSource)',
    "isLocalHistoryBoundaryCacheSource(rawSource)",
    "result=async-paint-deferred-boundary-source-memory-only",
    "terminalHistoryDragActive",
    "activeFeedbackOwner",
    "activeDragOwner",
    "releaseSettleOwner",
    "releaseSettleOwner = localHistoryBoundaryMergedDuringScrollAction();",
    "localHistoryTouchViewportOwnsVisibleSurfaceForNudge()",
    "inlineLocalHistorySource(source)",
    "rawSource",
    "boundarySource",
    "boundaryMergedDuringTouch",
    "localHistoryBoundaryMergedDuringScrollAction()",
    "activeTouchScrollLocalHistoryBoundaryRows",
    "result=async-paint-deferred-active-touch",
    "1 -> 397 -> 805 -> 1193",
    "before the JS paint callback",
    "local feedback pixel",
    "cache",
    "fills may update memory only",
    "boundary fills are preloaded cache rows, not the compositor",
    "controlled viewport source",
    "firstInlineLocalHistoryPainted=",
    "firstLocalFeedbackAtMs=",
    "action id resets on the next touch/bottom/live owner",
    "visible paint",
    "controlled",
    "viewport commit",
):
    if required not in async_paint_defer:
        fail(f"active-touch async cache paint deferral missing {required!r}")
if '"inline-local-history-seed-canonical".equals(rawSource)' in async_paint_defer:
    fail("seed-canonical paint must not bypass the active-touch async cache deferral owner")
boundary_cache_source = method_body("isLocalHistoryBoundaryCacheSource")
for required in (
    "source == null ? \"\" : source.trim()",
    "rawSource.indexOf(\"boundary\") >= 0",
):
    if required not in boundary_cache_source:
        fail(f"boundary cache source helper missing {required!r}")
local_feedback_owner = method_body("activeTouchScrollLocalFeedbackOwner")
for required in (
    "currentApkActionOwnsActiveTouchScroll()",
    "activeTouchScrollDownAtMs > 0",
    "activeTouchScrollFirstLocalFeedbackAtMs > 0",
    "terminalTouchStartedInTerminalBody",
    "terminalHistoryDragActive",
    "terminalTouchExceededTapSlop",
    "terminalTouchStartedInHistoryViewport",
):
    if required not in local_feedback_owner:
        fail(f"active-touch local feedback owner gate missing {required!r}")
renderer_paint_gate = method_body("allowInlineLocalHistoryPaintFromRenderer")
for required in (
    '"inline-local-history-viewport".equals(rawSource)',
    "isLocalHistoryBoundaryCacheSource(rawSource)",
    "js-paint-deferred-stale-action",
    "localHistoryBoundaryMergedDuringScrollAction()",
    "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
    "shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(rawSource)",
):
    if required not in renderer_paint_gate:
        fail(f"renderer JS inline-history paint gate missing {required!r}")
if "public boolean allowInlineLocalHistoryPaint(" not in source or "@JavascriptInterface" not in source:
    fail("WebView bridge must expose allowInlineLocalHistoryPaint as a JavascriptInterface")
for required in (
    "private long activeTouchScrollLocalHistoryBoundaryMergedAtMs",
    "activeTouchScrollLocalHistoryBoundaryMergedAtMs = 0;",
    "activeTouchScrollLocalHistoryBoundaryMergedAtMs = localHistoryTouchViewportUpdatedAtMs;",
    "fills may update memory only",
    "before the JS paint callback",
    "ACTION_UP must stay visually stopped",
    "activeTouchBoundaryMerged=true",
):
    if required not in source:
        fail(f"active-touch boundary merge state missing {required!r}")

bottom_clear = method_body("clearLocalHistoryTouchViewportForLiveBottom")
for required in (
    "historyScrollRequestInFlight = false",
    "activeHistoryScrollRequestSerial = -1",
    "activeTouchScrollActionId = newTouchScrollActionId()",
    "terminalHistoryDragActive = false",
    "terminalHistoryMomentumActive = false",
    "localHistoryTouchViewportRows.clear()",
    "syncCaptureRendererReadHold(",
    "clear-inline-local-history",
    "data-wezterm-inline-local-history",
    "window.__weztermLiveCaptureSnapshot",
    "snapshot.html",
    "delete window.__weztermLiveCaptureSnapshot",
):
    if required not in bottom_clear:
        fail(f"Bottom must locally clear local-history/read-hold before network confirmation via {required!r}")

for required in (
    "window.__weztermLiveCaptureSnapshot",
    "active.innerHTML",
    "data-wezterm-inline-local-history",
):
    if required not in paint_inline:
        fail(f"inline cache paint must preserve the live DOM for immediate Bottom restoration via {required!r}")

immediate_bottom = method_body("refreshCaptureRendererForImmediateBottom")
for required in (
    "r.refresh(true",
    "commitCaptureRendererVisualFrame(reason)",
):
    if required not in immediate_bottom:
        fail(f"explicit Bottom must force a renderer apply even when the live frame hash is unchanged via {required!r}")

tail_anchor = method_body("localHistoryBaseChunkAnchorsAcceptedTail")
for required in (
    'chunk.payload.optInt("end", -1)',
    "lastAcceptedCaptureRendererHistorySize",
    "base-tail-stale",
    "chunkEnd >= acceptedHistorySize",
):
    if required not in tail_anchor:
        fail(f"cached base history must prove overlap with the accepted live tail via {required!r}")

cache_lookup = method_body("findCachedLocalHistoryChunk")
if "localHistoryBaseChunkAnchorsAcceptedTail(request, chunk)" not in cache_lookup:
    fail("frame-hash fallback must not accept an unanchored stale base cache chunk")

replace_viewport = method_body("replaceLocalHistoryTouchViewportRows")
if "localHistoryTouchViewportOffset = localHistoryTouchViewportMaxOffset()" not in replace_viewport:
    fail("new oldest-to-newest cache owners must start from the newest/live max offset")

inline_viewport = method_body("inlineLocalHistoryPaintRowsForTouch")
for required in (
    "localHistoryTouchViewportOffset = maxOffset",
    "localHistoryTouchViewportOffset - deltaRows",
    "localHistoryTouchViewportOffset + deltaRows",
):
    if required not in inline_viewport:
        fail(f"inline oldest-to-newest cache navigation missing {required!r}")

top_edge_gate = method_body("shouldHoldLocalHistoryTouchTopEdge")
if "localHistoryTouchViewportOffset > 0" not in top_edge_gate:
    fail("oldest-to-newest cache top edge must be offset zero, not maxOffset")

monotonic_paint = method_body("allowMonotonicLocalHistoryViewportPaintFromRenderer")
for required in (
    '"lineUp".equals(safeWhere) && queuedOffset > ownerOffset',
    '"lineDown".equals(safeWhere) && queuedOffset < ownerOffset',
    '"lineUp".equals(safeWhere) && queuedOffset > lastOffset',
    '"lineDown".equals(safeWhere) && queuedOffset < lastOffset',
):
    if required not in monotonic_paint:
        fail(f"stale paint rejection must use oldest-to-newest offset direction via {required!r}")

restore_viewing = method_body("restoreLiveForViewing")
for required in (
    "clearLocalHistoryTouchViewportForLiveBottom(",
    "pinTerminalViewportLocal()",
    "runBottomButtonLiveBottomRecovery(",
):
    if required not in restore_viewing:
        fail(f"Bottom must locally restore live-bottom before slow /live-bottom confirmation via {required!r}")

ensure_viewport = method_body("ensureLocalHistoryTouchViewportRows")
for required in (
    "prependLocalHistoryTouchViewportRows(rows)",
    "appendLocalHistoryTouchViewportRows(rows)",
    "boundary-prepended",
    "boundary-appended",
    "localHistoryTouchViewportAlreadyRenderedOnly = alreadyRenderedOnly",
):
    if required not in ensure_viewport:
        fail(f"local viewport cache must support prepend/append boundaries via {required!r}")

last_rows = method_body("rememberAcceptedCaptureRendererFrame")
for required in (
    "lastAcceptedCaptureRendererRows",
    "lastAcceptedCaptureRendererPaintRows",
    "rowsFromPayload(payload)",
    "localHistoryPaintRowsFromPayload(payload, false)",
    "lastAcceptedCaptureRendererFrameWindowId",
):
    if required not in last_rows:
        fail(f"accepted frame rows must be retained for cold-cache local seed via {required!r}")

fallback_rows = method_body("lastAcceptedCaptureRendererRowsForLocalHistorySeed")
for required in (
    "lastAcceptedCaptureRendererRows",
    "new ArrayList<>(lastAcceptedCaptureRendererRows)",
):
    if required not in fallback_rows:
        fail(f"cold-cache local seed must copy last accepted frame rows via {required!r}")

paint_fallback_rows = method_body("lastAcceptedCaptureRendererPaintRowsForLocalHistorySeed")
for required in (
    "lastAcceptedCaptureRendererPaintRows",
    "return new ArrayList<>(lastAcceptedCaptureRendererPaintRows);",
):
    if required not in paint_fallback_rows:
        fail(f"cold-cache local seed must use structured paint rows via {required!r}")
if "plainLocalHistoryPaintRows(lastAcceptedCaptureRendererRows)" in paint_fallback_rows:
    fail("cold-cache touch seed must not fall back to plain transcript rows")

remember_frame = method_body("rememberAcceptedCaptureRendererFrame")
for required in (
    "lastAcceptedCaptureRendererHasMoreBefore = payload.optBoolean(\"hasMoreBefore\", false)",
    "lastAcceptedCaptureRendererCopyModeViewport = payload.optBoolean(\"copyModeViewport\", false)",
    "lastAcceptedCaptureRendererScrollPosition = payload.optInt(\"scrollPosition\", -1)",
    "lastAcceptedCaptureRendererHistorySize = payload.optInt(\"historySize\", -1)",
    "lastAcceptedCaptureRendererPaneInMode = payload.optInt(\"paneInMode\", -1)",
):
    if required not in remember_frame:
        fail(f"accepted terminal-frame metadata must feed local boundary logs via {required!r}")

feedback_rows = method_body("localHistoryTouchInlineFeedbackRows")
for required in (
    "lastAcceptedCaptureRendererRows.size()",
    "lastAcceptedCaptureRendererPaintRows.size()",
    "LOCAL_HISTORY_TOUCH_VIEWPORT_MAX_ROWS",
    "old fixed 96-row cache slice can cover only a bottom strip",
):
    if required not in feedback_rows:
        fail(f"cached scrollback row count must follow the live capture-renderer viewport contract via {required!r}")

for name in (
    "localHistoryTouchViewportMaxOffset",
    "localHistoryTouchViewportSlice",
    "clampLocalHistoryTouchViewportOffset",
    "inlineLocalHistoryPaintRowsForTouch",
):
    body = method_body(name)
    if "localHistoryTouchInlineFeedbackRows()" not in body:
        fail(f"{name} must use dynamic live-renderer row count for cached scrollback")
    if "LOCAL_HISTORY_TOUCH_INLINE_FEEDBACK_ROWS" in body and name != "localHistoryTouchInlineFeedbackRows":
        fail(f"{name} must not use the fixed 96-row fallback directly")

inline_paint = method_body("paintInlineLocalHistoryRowsInRenderer")
for required in (
    "shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)",
    "flushLocalHistoryInlineTouchNudgeBeforeRowPaint(where, source);",
    "last Java owner before a WebView JS mutation",
    "rawSourceJson",
    "dispatchActionIdJson",
    "var rawSource=",
    "var nativeAllowed=(rawSource==='inline-local-history-viewport')",
    "WeztermCaptureBridge.allowInlineLocalHistoryPaint",
    "catch(e){nativeAllowed=(rawSource==='inline-local-history-viewport');}",
    "inline-local-history-deferred-active-touch-js",
    "mutation owner must ask native at JS execution time",
    '" rawSource=" + safeLogToken(source == null ? "" : source.trim())',
    "r.measure()",
    "screenScale",
    "screenStack",
    "document.getElementById('screenScale')",
    "document.getElementById('screenStack')",
	    "logicalWidth=Math.ceil(Math.max(",
	    "paintCols=measuredWrapCols()",
	    "wrappedRows",
	    "applyBufferGeometry(active,logicalWidth)",
	    "applyBufferGeometry(inactive,logicalWidth)",
    "el.style.whiteSpace='pre'",
    "el.style.overflow='hidden'",
    "doc.style.width=contentWidth+'px'",
    "terminal.style.width=contentWidth+'px'",
    "scale.style.width=logicalWidth+'px'",
    "scale.style.transformOrigin='bottom left'",
    "scale.style.transform=fitScale<0.999",
    "stack.style.width=logicalWidth+'px'",
    "stack.style.overflow='hidden'",
    "stack.style.transform='none'",
    "stack.style.top='0px'",
    "scale.style.height=contentHeight+'px'",
    "stack.style.height=contentHeight+'px'",
    "logicalWidth:logicalWidth",
	    "activeScrollWidth:n(active.scrollWidth)",
	    "rightClipPx:rightClipPx",
	    "scaleStyleWidth:scale?px(scale.style.width):0",
	    "stackStyleWidth:stack?px(stack.style.width):0",
	    ":logicalWidth=",
	    ":wrapCols=",
	    ":rightClipPx=",
	    "viewport-parity",
	    "viewportParity:parity",
        "settleLocalHistoryFingerNudge(parity)",
        "visualCommitRows",
        "data-wezterm-local-history-visual-commit-rows",
        "localHistoryTouchCommitVisualRowsForPaint(repeats, source)",
        "&& (\"lineUp\".equals(where) || \"lineDown\".equals(where))) ? \"true\" : \"false\"",
        "lineHeight:lineHeight",
        "fingerNudgePx",
	):
	    if required not in inline_paint:
	        fail(f"inline cached scrollback paint must inherit live capture-renderer geometry via {required!r}")
paint_guard_idx = inline_paint.find("shouldDeferLocalHistoryAsyncPaintDuringActiveTouch(source)")
paint_flush_idx = inline_paint.find("flushLocalHistoryInlineTouchNudgeBeforeRowPaint(where, source);")
paint_js_idx = inline_paint.find("webView.evaluateJavascript(")
if -1 in (paint_guard_idx, paint_flush_idx, paint_js_idx) or not (paint_guard_idx < paint_flush_idx < paint_js_idx):
    fail("paintInlineLocalHistoryRowsInRenderer must reject active-touch async cache paint before nudge flush and WebView JS mutation")
js_native_gate_idx = inline_paint.find("WeztermCaptureBridge.allowInlineLocalHistoryPaint")
js_rows_idx = inline_paint.find("\"var rows=\"")
js_replace_idx = inline_paint.find("active.replaceChildren")
if -1 in (js_native_gate_idx, js_rows_idx, js_replace_idx) or not (paint_js_idx < js_native_gate_idx < js_rows_idx < js_replace_idx):
    fail("inline cache JS must ask native for paint permission before rows are read or DOM is mutated")

nudge_touch = method_body("nudgeCaptureRendererForTouch")
for required in (
    "localHistoryTouchViewportOwnsVisibleSurfaceForNudge()",
    "nudgeLocalHistoryInlineForTouch(deltaY, nudgeWhere)",
    "result=redirected-local-history-owner",
    "cached local-history rows own the viewport",
    "same-frame",
    "pendingTouchVisualNudgePx = 0f",
    "captureRendererTouchFingerNudgeResidualPx = 0f",
):
    if required not in nudge_touch:
        fail(f"live visual nudge must stop once cached scrollback owns the viewport via {required!r}")

handoff = method_body("transferCaptureRendererTouchNudgeToLocalHistoryHandoff")
for required in (
    "localHistoryTouchViewportTargetsRequest(request)",
    "captureRendererTouchFingerNudgeResidualPx",
    "localHistoryTouchFingerNudgeResidualPx += transferredPx",
    "clearCaptureRendererTouchNudgeForLocalHistoryHandoff(",
    "stage=local-history-handoff endpoint=renderer",
    "result=transferred-capture-nudge",
    "transferredPx=",
):
    if required not in handoff:
        fail(f"live-to-cache handoff must preserve the active finger pixel anchor via {required!r}")

local_nudge = method_body("nudgeLocalHistoryInlineForTouch")
for required in (
    "pendingLocalHistoryTouchVisualNudgePx += deltaY",
    "localHistoryTouchFingerNudgeResidualPx += deltaY",
    "runLocalHistoryInlineTouchNudge(visualGeneration, visualWhere)",
):
    if required not in local_nudge:
        fail(f"cached scrollback must schedule local finger motion via {required!r}")

local_nudge_runner = method_body("runLocalHistoryInlineTouchNudge")
for required in (
    "local-history-finger-nudge:applied",
    "data-wezterm-local-history-finger-nudge-px",
    "active.style.transform",
    "current+delta",
    "scale=unchanged",
    "nudgeWhere==='lineUp'",
    "markTouchScrollFirstLocalFeedback(where, \"local-history-finger-nudge\")",
    "stage=local-history-finger-nudge endpoint=renderer",
):
    if required not in local_nudge_runner:
        fail(f"cached scrollback first feedback must be a real finger-anchored transform via {required!r}")
for forbidden in (
    "r.measure()",
    "screenScale",
    "document.getElementById('screenScale')",
    "getComputedStyle(scaleEl)",
    "delta/scale",
):
    if forbidden in local_nudge_runner:
        fail(f"cached scrollback finger nudge must not mutate or remeasure zoom/scale during drag via {forbidden!r}")

local_nudge_clear = method_body("clearLocalHistoryInlineTouchNudge")
for required in (
    "localHistoryTouchVisualNudgeGeneration++",
    "localHistoryTouchFingerNudgeResidualPx = 0f",
    "data-wezterm-local-history-finger-nudge-px",
    "local-history-finger-nudge-clear",
):
    if required not in local_nudge_clear:
        fail(f"cached scrollback finger nudge must clear on owner changes via {required!r}")

release_nudge = method_body("releaseCaptureRendererTouchNudge")
for required in (
    "localHistoryTouchViewportOwnsVisibleSurfaceForNudge()",
    "local-history-finger-nudge",
    "clearLocalHistoryInlineTouchNudge(reason + \"-local-history-release\")",
):
    if required not in release_nudge:
        fail(f"cached scrollback residual nudge must clear on ACTION_UP/release via {required!r}")

nudge_owner = method_body("localHistoryTouchViewportOwnsVisibleSurfaceForNudge")
for required in (
    "localHistoryTouchViewportAlreadyRenderedOnly",
    "localHistoryTouchViewportTargetsVisibleSurface()",
):
    if required not in nudge_owner:
        fail(f"cached scrollback nudge ownership helper missing {required!r}")

visible_owner = method_body("localHistoryTouchViewportTargetsVisibleSurface")
for required in (
    "localHistoryTouchViewportRows.isEmpty()",
    "hasStableWindowId(localHistoryTouchViewportWindowId)",
    "confirmedVisibleTerminalTargetKey()",
    "visibleTerminalTargetKey()",
    "currentWebViewWindowIdTargetKey()",
):
    if required not in visible_owner:
        fail(f"cached scrollback visible-surface ownership helper missing {required!r}")

paint_from_payload = method_body("localHistoryPaintRowsFromPayload")
for required in (
    'payload.optJSONArray("ansiRows")',
    'payload.optJSONArray("rows")',
    "requireAnsiRows",
    "new LocalHistoryPaintRow(text, ansi",  # WHY: widened to a prefix — the scroll rearchitecture uses the 3-arg LocalHistoryPaintRow(text,ansi,html) ctor; still REQUIRES text+ansi are consumed, so the ansi/structured-row protection is preserved (matches both 2-arg and 3-arg)
):
    if required not in paint_from_payload:
        fail(f"local history paint rows must consume ansiRows/structured rows via {required!r}")

cache_hit = method_body("findCachedLocalHistoryChunk")
for required in (
    "frameHashFallback = true",
    "sameLocalHistoryGeneration",
    "result=hit-framehash-fallback",
    "frameHashFallback=true",
):
    if required not in cache_hit:
        fail(f"cache lookup must allow/log same-generation frame hash fallback via {required!r}")
if "continue;" not in cache_hit[cache_hit.find("!request.visibleFrameHash.isEmpty()"):cache_hit.find("best = chunk;")]:
    fail("cache lookup must still reject unsafe non-fallback frame-hash mismatches before selecting a chunk")

touch_feedback = method_body("markTouchScrollFirstLocalFeedback")
for required in (
    "alreadyRenderedOnly=",
    "firstNewReadableRow=false",
    "visual-nudge",
    "local-history-finger-nudge",
):
    if required not in touch_feedback:
        fail(f"visual nudge must remain separate from first new readable row via {required!r}")

dialog = method_body("showLocalHistoryDialog")
for required in (
    "fetchLocalHistoryChunk(request, String.valueOf(prevStart))",
    'fetchLocalHistoryChunk(request, "")',
):
    if required not in dialog:
        fail(f"dialog paging must preserve the original target-bound request via {required!r}")

identity = method_body("localHistoryIdentityForPayload")
for required in (
    "windowId",
    "paneId",
    "panePid",
    "generation",
    "generationKey",
    "sessionId",
    "threadId",
    "visibleFrameHash",
    "requestedWindowId",
):
    if required not in identity:
        fail(f"cache identity must include {required}")
if "payloadWindowId.equals(request.windowId)" not in identity:
    fail("cache identity must reject payloads for the wrong requested window")

request = method_body("localHistoryRequestForVisibleTarget")
for required in (
    "confirmedVisibleTerminalTargetKey()",
    "visibleTerminalTargetKey()",
    "lastAcceptedCaptureRendererFrameHash",
):
    if required not in request:
        fail(f"request binding must use {required}")
if '"/active"' in request:
    fail("request binding must not use /active")

for required in (
    "private volatile String activeTouchScrollActionId = \"\";",
    "private String newTouchScrollActionId()",
    "private void markTouchScrollFirstLocalFeedback(String where, String feedbackKind)",
    "private long touchScrollFirstLocalFeedbackElapsedMs()",
    "private boolean touchScrollAwaitingFirstLocalFeedback()",
    "activeTouchScrollActionId = newTouchScrollActionId();",
    "stage=touch-scroll-local-feedback endpoint=local-cache result=first-local-feedback",
):
    if required not in source:
        fail(f"one-finger scroll action trace guard missing {required!r}")

touch_stage = method_body("logTerminalTouchStage")
for required in (
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
    "tapDownAtMs=\" + activeTouchScrollDownAtMs",
    "firstLocalFeedbackAtMs=\" + activeTouchScrollFirstLocalFeedbackAtMs",
):
    if required not in touch_stage:
        fail(f"terminal touch logs must carry action timing field {required!r}")

visual = method_body("requestCaptureRendererTouchVisualFrame")
for required in (
    'actionId=" + safeLogToken(activeTouchScrollActionId)',
    "firstLocalFeedbackElapsedMs=\" + touchScrollFirstLocalFeedbackElapsedMs()",
):
    if required not in visual:
        fail(f"visual commit telemetry must carry action correlation {required!r}")

for name in (
    "LocalHistoryRequest",
    "LocalHistoryChunkIdentity",
    "LocalHistoryChunk",
):
    if f"private static final class {name}" not in source:
        fail(f"missing local cache structure {name}")

cache_section = source[
    source.find("private LocalHistoryRequest localHistoryRequestForVisibleTarget("):
    source.find("private void showCommandPalette(")
]
for forbidden in (
    "webView.reload",
    "loadTerminal(",
    "submitSafePrompt",
    "postPromptComposerSubmit",
    "showDockedPromptComposer",
    "setCaptureRendererWindowTarget(",
    "goLiveBottom",
    "/live-bottom",
    "/submit-text",
):
    if forbidden in cache_section:
        fail(f"local history cache path must not mutate live/send/composer owner {forbidden!r}")

if log_path is not None:
    validate_scroll_anchor_log(log_path)
    print(f"APK local history cache identity guard passed with log guard for {log_path}")
else:
    print("APK local history cache identity guard passed")
PY
