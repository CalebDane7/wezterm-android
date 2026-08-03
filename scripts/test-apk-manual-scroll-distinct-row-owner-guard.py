#!/usr/bin/env python3
"""Guard active-buffer ownership and deep stable traversal across gestures."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
CONTROLLER = (
    ROOT
    / "app/src/main/java/com/kaleeb/wezterm/NativeHistoryScrollController.java"
)
V237_OLD_RED = (
    ROOT
    / "app/src/test/resources/scroll-v237-release-frontier-old-red.log"
)


def fail(message: str) -> None:
    raise SystemExit(f"manual scroll distinct-row owner guard failed: {message}")


def require(text: str, token: str, message: str) -> None:
    if token not in text:
        fail(message)


def section(text: str, start: str, end: str) -> str:
    start_at = text.find(start)
    if start_at < 0:
        fail(f"missing section start: {start}")
    end_at = text.find(end, start_at + len(start))
    if end_at < 0:
        fail(f"missing section end: {end}")
    return text[start_at:end_at]


def frozen_frontier(
    owner: tuple[str, str, int] | None,
    window_id: str,
    generation_key: str,
    response_frontier: int,
) -> tuple[str, str, int]:
    if (
        owner is None
        or owner[0] != window_id
        or owner[1] != generation_key
        or response_frontier < owner[2]
    ):
        return (window_id, generation_key, response_frontier)
    return owner


def installed_v237_failure_is_machine_checkable(log: str) -> None:
    for token in (
        "start=4342 end=4841",
        "start=3842 end=4341",
        "background-refill-requested windowId=@74 start=3342",
        "actionId=scroll-72075600-30",
        "precommittedSeams=0 readyHistoryBatchesBefore=3 readyHistoryBatchesAfter=3",
        "result=READY_GAP appliedPx=0.0 visibleDistinctRowDisplacement=43",
        "releaseFlingStarted=false",
    ):
        require(log, token, f"v237 release/frontier old-red is missing: {token}")
    require(
        log,
        "actionId=scroll-72031450-4 deltaPx=-458.85",
        "v237 fixture lacks the post-release fling direction",
    )
    require(
        log,
        "actionId=scroll-72031450-4 deltaPx=25.00",
        "v237 fixture lacks the same-fling direction reversal",
    )


def run_model() -> None:
    # WHY [v241]: the held-after-release corrective re-nudge and its acceptance axiom
    # were removed from the shipped path; only the frozen generation frontier remains a
    # live logic invariant, so the model no longer asserts the retired mechanisms.
    owner = frozen_frontier(None, "@74", "generation-1", 5543)
    assert frozen_frontier(owner, "@74", "generation-1", 5548) == owner
    assert frozen_frontier(owner, "@74", "generation-1", 5551) == owner
    assert frozen_frontier(owner, "@74", "generation-2", 6000) == (
        "@74",
        "generation-2",
        6000,
    )


def main() -> None:
    run_model()
    source = SOURCE.read_text(encoding="utf-8")
    controller = CONTROLLER.read_text(encoding="utf-8")
    old_red = V237_OLD_RED.read_text(encoding="utf-8")
    installed_v237_failure_is_machine_checkable(old_red)

    require(
        source,
        "TOUCH_SCROLL_MIN_VISIBLE_DISTINCT_ROWS = 20",
        "20-row visual traversal threshold is missing",
    )
    require(
        source,
        "TOUCH_SCROLL_READY_SEAM_PRECOMMIT_LIMIT = 3",
        "bounded two-seam renderer precommit budget is missing",
    )
    down = section(
        source,
        "if (action == MotionEvent.ACTION_DOWN) {",
        "if (action == MotionEvent.ACTION_MOVE) {",
    )
    require(
        down,
        "boolean queuedGestureViewportOwner = beginCaptureRendererGestureViewportOwner();",
        "ACTION_DOWN does not queue the visible active-buffer owner before MOVE",
    )
    require(
        down,
        "captureRendererGestureViewportOwnerQueued = queuedGestureViewportOwner;",
        "native MOVE gate is not bound to the ACTION_DOWN owner transaction",
    )

    owner = section(
        source,
        "private boolean beginCaptureRendererGestureViewportOwner()",
        "private boolean beginTerminalSnapshotLease()",
    )
    for token in (
        "r.state()",
        "r.prependReadyHistoryAtBoundary('up',0,0,true)",
        "precommittedSeams",
        "commitStatus!=='COMMITTED'",
        "#screen[data-active=\\\"1\\\"],#screenNext[data-active=\\\"1\\\"]",
        "window.__weztermCanonicalGestureViewportOwner",
        "baselineFirstVisibleRowKey",
        "baselineLastVisibleRowKey",
    ):
        require(owner, token, f"active-buffer viewport acquisition is missing: {token}")
    if "activateTerminalSnapshot" in owner or "beginTerminalSnapshotLease" in owner:
        fail("ACTION_DOWN owner still depends on an asynchronously prepared snapshot")
    if owner.find("r.prependReadyHistoryAtBoundary('up',0,0,true)") > owner.find(
        "var anchors=visibleAnchors();"
    ):
        fail("gesture baseline is captured before staged seams are atomically committed")

    apply_pixels = section(
        source,
        "private boolean applyNativeHistoryScrollPixels(",
        "private void holdCaptureRendererBlockedFingerDelta(",
    )
    require(
        apply_pixels,
        "captureRendererHistoryMovementBlocked && !activeCaptureRendererGestureViewportOwner()",
        "stale pre-gesture seam latch can still block an acquired gesture owner",
    )

    nudge = section(
        source,
        "private String captureRendererTouchNudgeJavascript(",
        "private void runCaptureRendererTouchNudge(",
    )
    # WHY [v241]: the per-MOVE nudge is a CONSTANT-TIME scalar call into the
    # renderer's own O(1) nudgeTouchScroll, gated on the ACTION_DOWN gesture owner.
    # It must NOT re-introduce the removed DOM-wide getBoundingClientRect row scan
    # (visibleAnchors() before/after) on the hot path -- that scan forced a
    # synchronous reflow per finger sample and is the physically-reported scroll
    # lag (v240 red). Row-key/displacement proof now lives on the release latch,
    # not the MOVE path.
    for token in (
        "window.__weztermCanonicalGestureViewportOwner",
        "r.nudgeTouchScroll(",
        "visibleTraversalSameRenderer",
    ):
        require(nudge, token, f"O(1) gesture-owner nudge proof is missing: {token}")
    if "getBoundingClientRect(" in nudge:
        fail("per-MOVE nudge re-introduced a DOM-wide rect scan on the hot path")
    if "traversal={" in nudge:
        fail("MOVE can still create an opportunistic owner after ACTION_DOWN")

    callback = section(
        source,
        "private void runCaptureRendererTouchNudge(",
        "private void clearCaptureRendererTouchNudgeSoon(",
    )
    for token in (
        'truth.optInt("visibleDistinctRowDisplacement", 0)',
        "visibleDistinctRowDisplacement >= TOUCH_SCROLL_MIN_VISIBLE_DISTINCT_ROWS",
        "activeCaptureRendererGestureViewportOwner()",
    ):
        require(callback, token, f"renderer callback lacks strict visual ownership: {token}")
    if 'markTouchScrollFirstLocalFeedback("renderer", "visual-nudge");' in callback:
        fail("one renderer nudge can still claim visible ownership")

    # WHY [v241]: scope this to the ACTIVE release method only (ends where the
    # retired no-op begins) so the check cannot be satisfied by the dead corrective
    # method's tokens, and stays green when that dead method is finally deleted.
    release = section(
        source,
        "private void releaseCaptureRendererTouchNudge(",
        "private void postCaptureRendererReleaseAnchorVerification(",
    )
    for token in (
        "releaseTouchScrollNudge",
        "r.state",
        "heldAfterRelease",
        "actualAnchorHeld",
        "visibleDistinctRowDisplacement",
        "window.__weztermTouchScrollReleaseAnchor",
        "firstVisibleRowTopPx",
    ):
        require(
            release,
            token,
            f"release still trusts logical hold without pixel identity: {token}",
        )
    # WHY [v241 invariant c]: release closes the gesture-scroll commit window the
    # instant the finger is truly up, so no idle live-tail frame re-pins Bottom.
    require(
        release,
        "window.__weztermGestureScroll={activeUntil:0}",
        "release does not close the gesture-scroll commit window",
    )
    # WHY [v241 invariant b]: the post-UP verification the release path invokes is the
    # retired no-op; the corrective re-nudge (verifyCaptureRendererTouchNudgeReleaseAnchor,
    # which still calls r.nudgeTouchScroll(correctionPx)) must stay OFF the release path.
    require(
        release,
        "postCaptureRendererReleaseAnchorVerification(",
        "release path no longer routes to the post-UP verification hook",
    )
    if "verifyCaptureRendererTouchNudgeReleaseAnchor(" in release:
        fail("release path re-armed the retired corrective re-nudge")
    release_verify_noop = section(
        source,
        "private void postCaptureRendererReleaseAnchorVerification(",
        "private void verifyCaptureRendererTouchNudgeReleaseAnchor(",
    )
    if "evaluateJavascript(" in release_verify_noop:
        fail("post-UP release verification re-introduced a corrective renderer pass")

    fling = section(
        source,
        "private boolean dispatchHistoryReleaseFling(",
        "private boolean historyDragReleaseMomentumEnabled()",
    )
    for token in (
        "releaseFlingDirectionMatchesDrag",
        '"opposite-drag-direction"',
        "terminalHistoryDragWhere",
    ):
        require(fling, token, f"release fling direction guard is missing: {token}")

    ready_stage = section(
        source,
        "private void prepareTerminalSnapshotOffMainThread(",
        "private boolean rendererStageClosesBlockedSeam(",
    )
    for token in (
        "freezeCaptureRendererReadyHistoryLiveRowFrontier",
        "frozenLiveRowFrontier",
        '"liveRowFrontier",\n                            frozenLiveRowFrontier',
        "responseLiveRowFrontier",
    ):
        require(
            ready_stage,
            token,
            f"READY staging does not freeze its generation frontier: {token}",
        )

    touch_release = section(
        source,
        "boolean releaseFlingStarted = false;",
        "logTerminalTouchStage(",
    )
    for token in (
        "if (releaseFlingStarted) {",
        "deferCaptureRendererTouchNudgeReleaseForMomentum(",
        "} else {",
        'releaseCaptureRendererTouchNudge("touch-scroll-release");',
    ):
        require(
            touch_release,
            token,
            f"ACTION_UP does not preserve the owner through native fling: {token}",
        )
    # WHY [v241 invariant d]: a non-drag ACTION_UP still releases the gesture viewport
    # owner lease exactly once so its lifetime matches the finger (no leaked owner).
    require(
        touch_release,
        'clearCaptureRendererTouchNudge("gesture-end-nondrag-release");',
        "non-drag ACTION_UP no longer releases the gesture viewport owner lease",
    )

    constructor = section(
        source,
        "nativeHistoryScrollController = new NativeHistoryScrollController(",
        "if (getIntent().getBooleanExtra(",
    )
    require(
        constructor,
        "this::handleNativeHistoryKineticStateChanged",
        "native momentum terminal state is not routed to the viewport owner",
    )

    fling_owner = section(
        source,
        "private void deferCaptureRendererTouchNudgeReleaseForMomentum(",
        "private void cancelHistoryMomentum(String reason)",
    )
    for token in (
        "captureRendererGestureViewportOwnerReleaseDeferredForMomentum = true;",
        "captureRendererGestureViewportOwnerDeferredActionId",
        "captureRendererGestureViewportOwnerDeferredSerial",
        "private void handleNativeHistoryKineticStateChanged(",
        "terminalHistoryMomentumActive = running;",
        "releaseDeferredCaptureRendererTouchNudgeAfterMomentum(",
        "activeCaptureRendererGestureViewportOwner()",
        'releaseCaptureRendererTouchNudge("touch-scroll-fling-terminal");',
    ):
        require(
            fling_owner,
            token,
            f"fling-lifetime viewport ownership is missing: {token}",
        )

    for token in (
        "stateSink.onKineticStateChanged(false);",
        "if (scroller.isFinished()) cancel();",
    ):
        require(
            controller,
            token,
            f"native fling lacks a deterministic terminal event: {token}",
        )

    stage_gate = section(
        source,
        "private boolean rendererStageClosesBlockedSeam(",
        "private JSONObject parseJavascriptObjectResult(",
    )
    if "QUEUED_DUPLICATE" in stage_gate:
        fail("queued duplicate can still close and replay a missing seam")

    require(
        source,
        "LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED = false",
        "divergent inline history renderer was re-enabled",
    )
    print("manual scroll distinct-row owner guard passed")


if __name__ == "__main__":
    main()
