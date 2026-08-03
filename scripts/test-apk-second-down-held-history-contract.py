#!/usr/bin/env python3
"""Guard the renderer-held release -> next ACTION_DOWN ownership transfer."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def method(source: str, name: str, next_name: str) -> str:
    match = re.search(
        rf"\n\s*private\s+[\w<>\[\]]+\s+{re.escape(name)}\s*\("
        rf"(?P<body>.*?)\n\s*private\s+[\w<>\[\]]+\s+{re.escape(next_name)}\s*\(",
        source,
        flags=re.DOTALL,
    )
    if not match:
        raise AssertionError(f"missing method boundary: {name} -> {next_name}")
    return match.group("body")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source",
        default=(
            Path(__file__).resolve().parents[1]
            / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
        ),
        type=Path,
    )
    args = parser.parse_args()
    source = args.source.read_text(encoding="utf-8")
    touch = method(source, "handleTerminalTouch", "addTerminalMovement")
    release = method(
        source,
        "releaseCaptureRendererTouchNudge",
        "postCaptureRendererReleaseAnchorVerification",
    )
    arm = method(
        source,
        "armCaptureRendererReleasedReadHoldForRelease",
        "settleCaptureRendererReleasedReadHoldFromReleaseResult",
    )
    clear = method(
        source,
        "clearCaptureRendererTouchNudge",
        "postCaptureRendererPulseFrame",
    )

    required_existing = (
        'rebindCaptureRendererTouchNudgeForHeldHistoryStart("touch-start-history")',
        'clearCaptureRendererTouchNudge("touch-start")',
        "clearNativeReadHoldForTouchScrollActionDown",
    )
    for needle in required_existing:
        if needle not in touch:
            raise AssertionError(f"missing protected v243/v260 branch: {needle}")

    # Exact installed-v260 old-red: renderer release is held for this window,
    # while every Java/native history flag and the deliberately reset transport
    # residual are false/zero.
    renderer_held_same_window = True
    native_history = False
    local_history = False
    live_tui_lock = False
    java_residual = 0.0
    has_renderer_token_predicate = (
        "captureRendererReleasedReadHoldTargetsVisibleSurface()" in touch
    )
    started_in_history = (
        native_history
        or local_history
        or live_tui_lock
        or (renderer_held_same_window and has_renderer_token_predicate)
    )
    branch = "rebind" if started_in_history else "clear"
    if branch != "rebind":
        print(
            "OLD_RED ACTION_DOWN classifies renderer-held/native-cleared state "
            "as live and executes touch-start clear"
        )
        return 1

    required_candidate = (
        "armCaptureRendererReleasedReadHoldForRelease(",
        "settleCaptureRendererReleasedReadHoldFromReleaseResult(",
        "consumeCaptureRendererReleasedReadHold(",
        "clearCaptureRendererReleasedReadHold(",
    )
    for needle in required_candidate:
        if needle not in source:
            raise AssertionError(f"missing renderer-held transfer contract: {needle}")

    required_admission = (
        "boolean rendererResidualMatchesDirection =",
        '("lineUp".equals(terminalHistoryDragWhere)',
        "&& captureRendererTouchFingerNudgeResidualPx > 0f)",
        '|| ("lineDown".equals(terminalHistoryDragWhere)',
        "&& captureRendererTouchFingerNudgeResidualPx < 0f);",
        # WHY(v289 catch-up preservation): direction matching is not required
        # to be the final exactVisibleOwner conjunct.  The installed candidate
        # correctly adds !terminalTouchReachedLiveBottom after it so a downward
        # catch-up clears the hold instead of arming a new history token.  The
        # old semicolon-specific guard falsely rejected those proven bytes.
        "&& rendererResidualMatchesDirection",
        "&& !terminalTouchReachedLiveBottom;",
    )
    missing_admission = [needle for needle in required_admission if needle not in arm]
    if (
        missing_admission
        or "activeTouchScrollFirstLocalFeedbackAtMs > 0" in arm
    ):
        print(
            "OLD_RED release token requires asynchronous first-feedback and "
            "does not admit the exact same-action renderer residual; "
            f"missing={missing_admission!r}"
        )
        return 1

    # The O(1) renderer path can release before an asynchronous callback records
    # first feedback. Admission is instead synchronous and direction-specific.
    def admitted(
        *,
        action_up: bool,
        active_owner: bool,
        same_window: bool,
        where: str,
        residual: float,
        reached_live_bottom: bool = False,
        first_feedback_ms: int,
    ) -> bool:
        del first_feedback_ms
        direction_match = (
            (where == "lineUp" and residual > 0)
            or (where == "lineDown" and residual < 0)
        )
        return (
            action_up
            and active_owner
            and same_window
            and direction_match
            and not reached_live_bottom
        )

    assert admitted(
        action_up=True,
        active_owner=True,
        same_window=True,
        where="lineUp",
        residual=18,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=True,
        active_owner=True,
        same_window=True,
        where="lineUp",
        residual=-18,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=True,
        active_owner=True,
        same_window=True,
        where="lineUp",
        residual=0,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=True,
        active_owner=False,
        same_window=True,
        where="lineUp",
        residual=18,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=True,
        active_owner=True,
        same_window=False,
        where="lineUp",
        residual=18,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=False,
        active_owner=True,
        same_window=True,
        where="lineUp",
        residual=18,
        first_feedback_ms=0,
    )
    assert not admitted(
        action_up=True,
        active_owner=True,
        same_window=True,
        where="lineDown",
        residual=-18,
        reached_live_bottom=True,
        first_feedback_ms=0,
    )
    if "if (action == MotionEvent.ACTION_UP && terminalHistoryDragActive)" not in touch:
        raise AssertionError("released hold admission is not fenced from ACTION_CANCEL")

    predicate_start = touch.index("boolean startedInHistoryViewport")
    predicate_region = touch[
        predicate_start
        : touch.index("terminalTouchReachedLiveBottom", predicate_start)
    ]
    if "captureRendererTouchFingerNudgeResidualPx" in predicate_region:
        raise AssertionError(
            "transport residual is reset before the next DOWN and cannot own history"
        )
    if "captureRendererReleasedReadHoldTargetsVisibleSurface()" not in predicate_region:
        raise AssertionError("renderer-held same-window token is not in DOWN predicate")

    rebind_region = touch[
        touch.index("if (startedInHistoryViewport)")
        : touch.index("boolean queuedGestureViewportOwner")
    ]
    if "consumeCaptureRendererReleasedReadHold(" not in rebind_region:
        raise AssertionError("held token is not consumed during exact rebind transfer")
    if rebind_region.index("consumeCaptureRendererReleasedReadHold(") > rebind_region.index(
        "rebindCaptureRendererTouchNudgeForHeldHistoryStart("
    ):
        raise AssertionError("held token must transfer before renderer owner rebinding")

    if "settleCaptureRendererReleasedReadHoldFromReleaseResult(" not in release:
        raise AssertionError("async renderer release result does not settle the token")
    if "clearCaptureRendererReleasedReadHold(" not in clear:
        raise AssertionError("explicit renderer clears do not invalidate the token")
    if "captureRendererReleasedReadHoldWindowId" not in source:
        raise AssertionError("released hold is not bound to an exact window")
    if "captureRendererReleasedReadHoldActionId" not in source:
        raise AssertionError("released hold is not fenced from stale callbacks")

    # Preserve v242's accepted rejection: a new DOWN clears native read hold,
    # while only the renderer-owned visual position is transferred.
    native_clear = method(
        source,
        "clearNativeReadHoldForTouchScrollActionDown",
        "sanitizeJavascriptReason",
    )
    if "window.__weztermNativeReadHold={active:false" not in native_clear:
        raise AssertionError("native read-hold clear was regressed")

    print(
        "second-DOWN held-history contract: green "
        "(same-window renderer hold transfers; explicit clears preserved)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
