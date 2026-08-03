#!/usr/bin/env python3
"""Focused old-red guard for native fling physics and uninterrupted streaming."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
CONTROLLER = (
    ROOT
    / "app/src/main/java/com/kaleeb/wezterm/NativeHistoryScrollController.java"
)
DEFAULT_CONTROL = Path(
    "/home/cabule/.ai-controller-repo/configs/scripts/mantis-phone-control-server"
)


def fail(message: str) -> None:
    raise SystemExit(f"native fling/stream contract failed: {message}")


def java_method(source: str, name: str) -> str:
    match = re.search(
        r"\n\s*(?:private|public|protected)\s+[^\n;{]+?\s+"
        + re.escape(name)
        + r"\s*\(",
        source,
    )
    if not match:
        fail(f"missing Java method {name}")
    brace = source.find("{", match.start())
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1 : index]
    fail(f"unterminated Java method {name}")


def package_private_java_method(source: str, name: str) -> str:
    match = re.search(
        r"\n\s*(?:(?:static|final)\s+)*[\w<>\[\]]+\s+"
        + re.escape(name)
        + r"\s*\(",
        source,
    )
    if not match:
        fail(f"missing package-private Java method {name}")
    brace = source.find("{", match.start())
    depth = 0
    for index in range(brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1 : index]
    fail(f"unterminated package-private Java method {name}")


def between(source: str, start: str, end: str) -> str:
    start_at = source.find(start)
    end_at = source.find(end, start_at + len(start))
    if start_at < 0 or end_at < 0:
        fail(f"missing section {start!r}..{end!r}")
    return source[start_at:end_at]


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        fail(message)


def compact(source: str) -> str:
    return re.sub(r"\s+", "", source)


def run_admission_model() -> None:
    def qualifies(distance_px: float, touch_slop_px: float, velocity: float, minimum: float) -> bool:
        return distance_px >= touch_slop_px and abs(velocity) >= minimum

    assert not qualifies(7.9, 8.0, 9_000.0, 50.0)
    assert not qualifies(80.0, 8.0, 49.9, 50.0)
    assert qualifies(8.0, 8.0, 50.0, 50.0)
    assert qualifies(80.0, 8.0, -2_000.0, 50.0)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main-source", type=Path, default=MAIN)
    parser.add_argument("--controller-source", type=Path, default=CONTROLLER)
    parser.add_argument("--control-source", type=Path, default=DEFAULT_CONTROL)
    args = parser.parse_args()

    run_admission_model()
    main_path = args.main_source.resolve()
    controller_path = args.controller_source.resolve()
    main_source = main_path.read_text(encoding="utf-8")
    controller = controller_path.read_text(encoding="utf-8")
    control = args.control_source.resolve().read_text(encoding="utf-8")

    release = java_method(main_source, "dispatchHistoryReleaseFling")
    for token in (
        "HISTORY_DRAG_RELEASE_MOMENTUM_ENABLED = true",
        "terminalTouchExceededTapSlop",
        "absDy < terminalTouchSlop",
        "maximumFlingVelocityPxPerSecond()",
        "computeCurrentVelocity(1000, maximumFlingVelocity)",
        "getYVelocity(event.getPointerId(0))",
        "isFlingVelocity(releaseVelocity)",
        "releaseFlingDirectionMatchesDrag(",
        "bindNativeHistoryMomentumFence()",
    ):
        require(main_source if "HISTORY_DRAG" in token else release, token, f"missing platform fling admission/fence: {token}")
    for token in (
        "HISTORY_DRAG_RELEASE_FLICK_MAX_MS",
        "HISTORY_DRAG_RELEASE_MIN_LINES",
        "HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC",
        "signedReleaseVelocity = totalDy * 1000f / durationMs",
        "skipped-local-history-finger-anchor",
        "scrollTerminalFromTouch(",
    ):
        if token in release:
            fail(f"old guessed or queued release path returned: {token}")

    for token in (
        "new OverScroller(context)",
        "getScaledMinimumFlingVelocity()",
        "getScaledMaximumFlingVelocity()",
        "Math.min(maximumFlingVelocity, magnitude)",
        "scroller.fling(0, 0, 0, velocity, 0, 0, -1_000_000_000, 1_000_000_000);",
        "scroller.computeScrollOffset()",
        "int currentY = scroller.getCurrY();",
        "float deltaY = currentY - lastY;",
        "choreographer.postFrameCallback(this)",
        "pixelSink.applyRelativePixels(deltaY, direction)",
    ):
        require(controller, token, f"native controller lost Android platform physics: {token}")
    # WHY(rationale-durability): the executable assertions are
    # the release veto, but these nearby source WHYs preserve the causal reason
    # for that veto. A future cleanup must not leave native-looking tokens while
    # deleting the warning that slow drag, fast bidirectional release, local-only
    # frames, stream independence, and second-DOWN cancellation are one contract.
    # Alternate --main/--controller inputs are frozen old-red or installed-source
    # fixtures and should be judged on behavior, not on comments added after that
    # APK was built. The mandatory no-argument build-gate path reads the canonical
    # worktree bytes and therefore makes rationale durability a publication veto.
    if main_path == MAIN.resolve() and controller_path == CONTROLLER.resolve():
        for token in (
            "WHY(USER-" "ACCEPTED-V292-SCROLL-2026-08-03)",
            "a short, slow drag stays under the",
            "a fast release coasts naturally in either direction",
            "must never perform tmux, HTTP, cache-fill, or stream-control work",
            "a new DOWN or owner change",
            "mandatory native-fling/stream, released-hold, and",
        ):
            require(controller, token, f"accepted scroll WHY rationale drifted: {token}")
    # WHY(USER-ACCEPTED-V292-SCROLL-2026-08-03): OverScroller.fling converts
    # the measured device-qualified release velocity into the platform spline
    # and default-friction decay the user accepted. The tempting alternatives
    # below reintroduce guessed duration/distance, custom friction, or a second
    # boundary animation owner while leaving enough native-looking tokens for a
    # shallow source check to false-green. Any such change needs a new literal
    # phone acceptance; it may not silently replace the accepted physics.
    for forbidden in (
        ".setFriction(",
        ".startScroll(",
        ".extendDuration(",
        ".setFinalX(",
        ".setFinalY(",
        ".springBack(",
        ".notifyHorizontalEdgeReached(",
        ".notifyVerticalEdgeReached(",
    ):
        if forbidden in controller:
            fail(f"native controller introduced non-accepted fling tuning/owner: {forbidden}")

    # WHY(second-DOWN hard stop): checking only the ACTION_DOWN call false-greens
    # if a later refactor hollows out cancel(). The user's next finger must abort
    # the spline, remove the already-posted Choreographer callback, and publish a
    # terminal state before any old kinetic pixel can reach a replacement owner.
    controller_cancel = package_private_java_method(controller, "cancel")
    cancel_tokens = (
        "scroller.abortAnimation()",
        "choreographer.removeFrameCallback(this)",
        "framePosted = false;",
        'direction = "";',
        "stateSink.onKineticStateChanged(false);",
    )
    for token in cancel_tokens:
        require(
            controller_cancel,
            token,
            f"second-DOWN cancellation stopped terminating native momentum: {token}",
        )
    cancel_positions = [controller_cancel.find(token) for token in cancel_tokens]
    if cancel_positions != sorted(cancel_positions):
        fail("second-DOWN cancellation no longer aborts/removes before terminal state")

    main_cancel = java_method(main_source, "cancelHistoryMomentum")
    for token in (
        "terminalHistoryMomentumActive = false;",
        "nativeHistoryScrollController.cancel();",
        "releaseDeferredCaptureRendererTouchNudgeAfterMomentum();",
        "clearNativeHistoryMomentumFence();",
    ):
        require(
            main_cancel,
            token,
            f"second-DOWN cancellation chain drifted in MainActivity: {token}",
        )

    # WHY(owner fence): the predicate call alone is not protection; a stubbed or
    # dimension-reduced predicate would still pass while a queued frame moved a
    # new touch, session, window, or renderer lease. Freeze both the binding and
    # the four-dimensional comparison that the accepted phone path depends on.
    momentum_bind = compact(java_method(main_source, "bindNativeHistoryMomentumFence"))
    expected_momentum_bind = (
        "nativeHistoryMomentumGestureGeneration=terminalTouchGestureGeneration;"
        'nativeHistoryMomentumActionId=activeTouchScrollActionId==null?"":'
        "activeTouchScrollActionId.trim();"
        'nativeHistoryMomentumWindowId=terminalTouchStableWindowId==null?"":'
        "terminalTouchStableWindowId.trim();"
        "nativeHistoryMomentumViewportOwnerSerial="
        "captureRendererGestureViewportOwnerLeaseSerial;"
    )
    if momentum_bind != expected_momentum_bind:
        fail("momentum fence binding lost exact generation/action/window/owner tuple")

    momentum_match = compact(
        java_method(main_source, "nativeHistoryMomentumFenceMatchesActiveOwner")
    )
    expected_momentum_match = (
        "returnnativeHistoryMomentumGestureGeneration==terminalTouchGestureGeneration"
        "&&nativeHistoryMomentumViewportOwnerSerial>0"
        "&&nativeHistoryMomentumViewportOwnerSerial=="
        "captureRendererGestureViewportOwnerLeaseSerial"
        "&&!nativeHistoryMomentumActionId.isEmpty()"
        "&&nativeHistoryMomentumActionId.equals(activeTouchScrollActionId)"
        "&&hasStableWindowId(nativeHistoryMomentumWindowId)"
        "&&nativeHistoryMomentumWindowId.equals(terminalTouchStableWindowId);"
    )
    if momentum_match != expected_momentum_match:
        fail("momentum fence comparison lost exact generation/action/window/owner tuple")

    native_pixels = java_method(main_source, "applyNativeHistoryScrollPixels")
    for token in (
        "nativeHistoryMomentumFenceMatchesActiveOwner()",
        "activeCaptureRendererGestureViewportOwner()",
    ):
        require(native_pixels, token, f"kinetic pixels are not generation/target/owner fenced: {token}")
    counter_block = re.compile(
        r"if\s*\(nativeHistoryMomentumTelemetryActive\)\s*\{\s*"
        r"long\s+frameAtMs\s*=\s*SystemClock\.elapsedRealtime\(\);"
    )
    if len(counter_block.findall(native_pixels)) != 1:
        fail("kinetic in-memory counter must have exactly one structural owner block")
    if re.search(
        r"if\s*\(nativeHistoryMomentumTelemetryActive\)\s*\{\s*"
        r"if\s*\(nativeHistoryMomentumTelemetryActive\)",
        native_pixels,
    ):
        fail("duplicate nested kinetic telemetry block returned")
    if "Log.i(" in native_pixels:
        fail("kinetic pixel sink must aggregate in memory without per-frame logging")
    # WHY(stream-always-on): this method runs once per Choreographer frame. It may
    # touch only the already-rendered local viewport. A single HTTP/tmux/cache or
    # stream-lifecycle call here couples fling cadence to transport, recreating the
    # freeze/delayed-burst failure even when NativeHistoryScrollController stays pure.
    for forbidden in (
        "getJsonWithRetry(",
        "getJson(",
        "postJson(",
        "scrollTerminalFromTouch(",
        "sendHistoryScrollFromTouch(",
        "prefetchLocalHistoryForTouchScroll(",
        "prewarmLocalHistoryForWindow(",
        "runBottomButtonLiveBottomRecovery(",
        "restoreTouchLiveBottomQuietly(",
        "scheduleTouchLiveBottomReconcile(",
        "webView.evaluateJavascript(",
        "connectTerminalLiveStream(",
        "closeTerminalLiveStream(",
    ):
        if forbidden in native_pixels:
            fail(f"kinetic pixel sink introduced non-local work: {forbidden}")

    nudge_result = java_method(main_source, "runCaptureRendererTouchNudge")
    for token in (
        'truth.optBoolean("liveBottomReached", false)',
        "terminalTouchReachedLiveBottom = true;",
        "clearPendingHistoryScroll();",
        "if (!terminalHistoryDragActive)",
    ):
        require(
            nudge_result,
            token,
            f"downward native momentum lost exact renderer-bottom reconcile: {token}",
        )
    compact_nudge_result = re.sub(r"\s+", "", nudge_result)
    require(
        compact_nudge_result,
        'clearCaptureRendererBlockedFingerDelta("renderer-live-bottom");',
        "downward native momentum no longer clears the renderer-bottom replay delta",
    )
    # WHY(v284/v292 single-owner live bottom): the superseded direct-controller
    # cancel plus scheduled reconcile branch is not an equivalent fallback. It can
    # race a later owner and restage history after the renderer has already proven
    # its exact zero baseline. Only the accepted structured directional branch is
    # permitted to stop momentum and quietly reconcile after the finger is up.
    live_bottom_branch = between(
        nudge_result,
        "if (rendererLiveBottom",
        'if ("TRUE_HISTORY_TOP".equals(status))',
    )
    for token in (
        "&& activeTraversalAction",
        "&& deltaY < -0.5f",
        "terminalTouchReachedLiveBottom = true;",
        "terminalTouchReachedHistoryTop = false;",
        "captureRendererTouchFingerNudgeResidualPx = 0f;",
        "captureRendererHistoryMovementBlocked = false;",
        "captureRendererGestureViewportRefillRequested = false;",
        'cancelHistoryMomentum("renderer-live-bottom");',
        "clearPendingHistoryScroll();",
        "if (!terminalHistoryDragActive)",
        "restoreTouchLiveBottomQuietly();",
    ):
        require(
            live_bottom_branch,
            token,
            f"v292 live-bottom single-owner branch drifted: {token}",
        )
    require(
        compact(live_bottom_branch),
        'clearCaptureRendererBlockedFingerDelta("renderer-live-bottom");',
        "v292 live-bottom branch no longer clears the exact blocked-finger delta",
    )
    for forbidden in (
        "rendererReachedLiveBottom",
        "nativeHistoryScrollController.cancel();",
        "scheduleTouchLiveBottomReconcile(",
        '"native-momentum-renderer-live-bottom"',
    ):
        if forbidden in nudge_result:
            fail(f"superseded live-bottom momentum owner returned: {forbidden}")

    touch = java_method(main_source, "handleTerminalTouch")
    down = between(
        touch,
        "if (action == MotionEvent.ACTION_DOWN)",
        "if (action == MotionEvent.ACTION_MOVE)",
    )
    require(down, 'cancelHistoryMomentum("new-down");', "new DOWN no longer stops momentum")
    require(down, "clearPendingHistoryScroll();", "new DOWN can inherit a delayed row queue")
    down_order = (
        "terminalTouchGestureGeneration++;",
        'cancelHistoryMomentum("new-down");',
        "clearPendingHistoryScroll();",
        "return true;",
    )
    down_positions = [down.find(token) for token in down_order]
    if min(down_positions) < 0 or down_positions != sorted(down_positions):
        fail("new DOWN no longer fences, cancels, clears, then consumes in order")

    move = java_method(main_source, "processHistoryDragSample")
    if "scrollTerminalFromTouch(" in move:
        fail("ACTION_MOVE performs network/tmux scroll I/O")
    require(
        main_source,
        "HISTORY_DRAG_MOVE_NETWORK_ENABLED = false",
        "ACTION_MOVE local-only kill switch drifted",
    )

    transport = between(
        control,
        "function liveStreamTransportHeld(",
        "function liveStreamApplyHeld(",
    )
    for forbidden in (
        "nativeLiveStreamReadHold",
        "touchScrollNudgeInProgress",
        "touchScrollNudgeFingerActive",
        "touchScrollNudgeHeldAfterRelease",
    ):
        if forbidden in transport:
            fail(f"scroll/read state pauses exact stream transport: {forbidden}")
    drain = between(
        control,
        "async function drainTerminalLiveFrame()",
        "function connectTerminalLiveStream(",
    )
    for token in (
        "const heldLivePatchOnly=liveStreamApplyHeld(payload);",
        "heldLivePatchOnly,liveStreamAccepted:true",
        "if(liveFrameDirty&&liveStreamBinding)scheduleLiveFrameDrain();",
    ):
        require(drain, token, f"held stream frames no longer fetch/stage as exact patch-only data: {token}")
    if drain.find("const heldLivePatchOnly=liveStreamApplyHeld(payload);") > drain.find("applyRenderedFrame("):
        fail("held-frame admission moved after visible apply")

    # WHY(stream-always-on): transport/apply holds are not the only possible
    # regression. A scroll helper could explicitly close EventSource and still pass
    # every pause/drain assertion above. Keep every scroll-owned function free of
    # close authority, centralize the sole liveStream.close(), and freeze the exact
    # non-scroll lifecycle reasons allowed to invoke that owner.
    scroll_owned_sections = (
        (
            "clearTouchScrollNudgeWhenIdle",
            between(control, "function clearTouchScrollNudgeWhenIdle()", "function clearTouchScrollNudge()"),
        ),
        (
            "clearTouchScrollNudge",
            between(control, "function clearTouchScrollNudge()", "function releaseTouchScrollNudge()"),
        ),
        (
            "releaseTouchScrollNudge",
            between(control, "function releaseTouchScrollNudge()", "function touchScrollNudgeCap("),
        ),
        (
            "nudgeTouchScroll",
            between(control, "function nudgeTouchScroll(deltaY)", "function installNativePointerScrollInput()"),
        ),
        (
            "refreshTouchScrollEdge",
            between(control, "function refreshTouchScrollEdge(reason)", "function refreshTouchScrollCommit(reason)"),
        ),
        (
            "refreshTouchScrollCommit",
            between(control, "function refreshTouchScrollCommit(reason)", "function refreshTouchScrollReleaseCommit(reason)"),
        ),
        (
            "refreshTouchScrollReleaseCommit",
            between(control, "function refreshTouchScrollReleaseCommit(reason)", "function scheduleSoon("),
        ),
    )
    for owner, section in scroll_owned_sections:
        if "closeTerminalLiveStream(" in section or "liveStream.close(" in section:
            fail(f"scroll-owned function can close SSE transport: {owner}")

    expected_close_reasons = [
        "authorization-rotated",
        "stream-reconnect-timeout",
        "binding-reset",
        "payload-mismatch",
        "frame-fetch-error",
        "rebind",
        "stream-epoch-changed",
        "stream-reset",
        "stream-error",
        "target-change",
    ]
    close_reasons = re.findall(r'closeTerminalLiveStream\("([^"]+)"\)', control)
    if close_reasons != expected_close_reasons:
        fail("SSE close authority drifted from exact non-scroll lifecycle reasons")
    if control.count("closeTerminalLiveStream(") != len(expected_close_reasons) + 1:
        fail("SSE close authority escaped its sole lifecycle function/call allowlist")
    if control.count("liveStream.close();") != 1 or "source.close(" in control:
        fail("live EventSource close escaped its sole centralized lifecycle owner")

    print(
        "native fling/stream contract: green "
        f"(main={main_path}, controller={controller_path}, control={args.control_source.resolve()})"
    )


if __name__ == "__main__":
    main()
