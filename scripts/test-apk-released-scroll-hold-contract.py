#!/usr/bin/env python3
"""Guard the coupled APK/renderer contract for a released streaming scroll."""

from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
DEFAULT_CONTROL_SOURCE = Path("/home/cabule/.local/bin/mantis-phone-control-server")


def fail(message: str) -> None:
    raise SystemExit(f"released scroll hold contract failed: {message}")


def section(text: str, start: str, end: str) -> str:
    start_at = text.find(start)
    if start_at < 0:
        fail(f"missing section start: {start}")
    end_at = text.find(end, start_at + len(start))
    if end_at < 0:
        fail(f"missing section end: {end}")
    return text[start_at:end_at]


def require(text: str, token: str, message: str) -> None:
    if token not in text:
        fail(message)


def released_hold(nudge_px: float) -> bool:
    """The reader's local pixel offset—not server history metadata—owns the hold."""

    return abs(nudge_px) >= 0.5


def run_model() -> None:
    assert released_hold(84.0)
    assert released_hold(-18.0)
    assert not released_hold(0.49)
    assert not released_hold(0.0)
    # WHY(task #21): historySize is deliberately absent from this model. A
    # streaming alt-screen pane and a history pane must retain the same nonzero
    # finger-up offset; the offset self-releases only when it reaches zero.


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--control-source",
        type=Path,
        default=DEFAULT_CONTROL_SOURCE,
        help="Exact control-server source or staged release payload to verify.",
    )
    args = parser.parse_args()

    run_model()
    apk = APK_SOURCE.read_text(encoding="utf-8")
    control = args.control_source.resolve().read_text(encoding="utf-8")

    apk_release = section(
        apk,
        "private void releaseReadHoldAfterNoVisibleScrollOwner(String reason)",
        "private long leaveReadModeForLiveInput()",
    )
    # WHY(task #21): Java resets its transport residual before the asynchronous
    # renderer release result arrives. That zero cannot disprove the renderer's
    # accepted pixel hold, so idle stale-latch cleanup must never clear it.
    if "clearCaptureRendererTouchNudge(" in apk_release:
        fail("APK no-owner release can wipe the renderer-owned reading position")
    require(
        apk_release,
        "syncCaptureRendererReadHold(reason);",
        "APK no-owner release lost native/renderer hold reconciliation",
    )

    renderer_release = section(
        control,
        "function releaseTouchScrollNudge()",
        "function touchScrollNudgeCap(",
    )
    require(
        renderer_release,
        "touchScrollNudgeHeldAfterRelease=(Math.abs(touchScrollNudgePx)>=0.5);",
        "renderer finger-up hold is not derived from its actual local pixel offset",
    )
    if "lastKnownHistorySize" in renderer_release:
        fail("renderer finger-up hold still depends on server historySize")

    apply_hold = section(
        control,
        "function liveStreamApplyHeld(",
        "function clearNormalPollTimer()",
    )
    require(
        apply_hold,
        "||touchScrollNudgeInProgress()",
        "frame-apply hold can expire while released geometry remains nonzero",
    )
    if "readerHolds&&touchScrollNudgeHeldAfterRelease" in apply_hold:
        fail("frame-apply release hold still requires historySize > 0")
    if "payload&&payload.scrollPosition" in apply_hold:
        fail("server frontier scrollPosition still acts as a second release-hold authority")

    transport_hold = section(
        control,
        "function liveStreamTransportHeld(",
        "function liveStreamApplyHeld(",
    )
    for forbidden in (
        "touchScrollNudgeHeldAfterRelease",
        "touchScrollNudgeInProgress",
        "touchScrollNudgeFingerActive",
        "nativeLiveStreamReadHold",
        "lastKnownHistorySize",
    ):
        if forbidden in transport_hold:
            fail(f"released reading hold incorrectly stops stream transport: {forbidden}")

    live_drain = section(
        control,
        "async function drainTerminalLiveFrame()",
        "function connectTerminalLiveStream(",
    )
    for token in (
        "const heldLivePatchOnly=liveStreamApplyHeld(payload);",
        "heldLivePatchOnly,liveStreamAccepted:true",
        "if(liveFrameDirty&&liveStreamBinding)scheduleLiveFrameDrain();",
    ):
        require(
            live_drain,
            token,
            f"exact-bound live fetch/staging lost held-frame patch-only continuity: {token}",
        )
    if live_drain.find("const heldLivePatchOnly=liveStreamApplyHeld(payload);") > live_drain.find("applyRenderedFrame("):
        fail("read-hold admission must be decided after fetch/staging and before visible apply")

    frame_commit = section(
        control,
        "const releaseCommit=touchScrollNudgeHeldAfterRelease&&touchScrollReleaseCommitAllowed;",
        "let settledTouchFrame=settleTouchScrollNudgeForFrame(",
    )
    require(
        frame_commit,
        "if(touchScrollNudgeHeldAfterRelease&&!releaseCommit)",
        "a later live frame can replace the released reading frame",
    )

    clear_nudge = section(
        control,
        "function clearTouchScrollNudge()",
        "function releaseTouchScrollNudge()",
    )
    for token in (
        "touchScrollNudgePx=0",
        "touchScrollNudgeHeldAfterRelease=false",
        "applyScreenTransform()",
    ):
        require(clear_nudge, token, f"explicit renderer clear lost invariant: {token}")

    for token in (
        'clearCaptureRendererTouchNudge("touch-bottom")',
        'clearCaptureRendererTouchNudge("live-input")',
        'clearCaptureRendererTouchNudge("gesture-end-nondrag-release")',
        "clearCaptureRendererTouchNudge(reason);",
        "liveTuiScrollLockActive = false;",
    ):
        require(apk, token, f"explicit return-to-live cleanup is missing: {token}")

    print(
        "released scroll hold contract: green "
        f"(apk={APK_SOURCE}, control={args.control_source.resolve()})"
    )


if __name__ == "__main__":
    main()
