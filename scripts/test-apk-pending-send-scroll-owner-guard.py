#!/usr/bin/env python3
"""Guard touch-scroll ownership while Send confirmation/echo is still pending."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"


def fail(message: str) -> None:
    raise SystemExit(f"pending-send scroll owner guard failed: {message}")


def section(source: str, start: str, end: str) -> str:
    start_at = source.find(start)
    end_at = source.find(end, start_at + len(start))
    if start_at < 0 or end_at < 0:
        fail(f"missing source section: {start}")
    return source[start_at:end_at]


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        fail(message)


def run_model() -> None:
    state = {
        "send_pending": True,
        "terminal_echo_visible": False,
        "native_read_hold": True,
        "cached_viewport_rows": 4000,
        "visible_feedback": False,
    }
    state["native_read_hold"] = False
    state["visible_feedback"] = True
    assert state["send_pending"]
    assert not state["terminal_echo_visible"]
    assert state["cached_viewport_rows"] == 4000
    assert not state["native_read_hold"]
    assert state["visible_feedback"]


def main() -> None:
    run_model()
    source = SOURCE.read_text(encoding="utf-8")

    down = section(
        source,
        "if (action == MotionEvent.ACTION_DOWN) {",
        "if (action == MotionEvent.ACTION_MOVE) {",
    )
    require(
        down,
        "clearNativeReadHoldForTouchScrollActionDown(",
        "ACTION_DOWN does not supersede the pending Send read hold",
    )

    clear_hold = section(
        source,
        "private void clearNativeReadHoldForTouchScrollActionDown(",
        "private String sanitizeJavascriptReason(",
    )
    require(clear_hold, "active:false", "renderer native read hold is not cleared")
    require(
        clear_hold,
        "__weztermGestureScroll={activeUntil:Date.now()+",
        "touch gesture commit permission is not published",
    )
    for forbidden in (
        "resetReadyHistoryForBottom",
        "clearTouchScrollNudge(",
        "localHistoryTouchViewportRows.clear",
        "terminalSnapshotCache.clear",
    ):
        if forbidden in clear_hold:
            fail(f"ACTION_DOWN discarded cached viewport/text: {forbidden}")

    queued = section(
        source,
        "private void hideDockedPromptComposerAfterQueuedSubmit(",
        "private void drainPromptComposerSubmitQueue(",
    )
    if 'showPromptComposerSubmitTouchOverlay("composer-queued-submit")' in queued:
        fail("pending Send still arms a modal touch owner")
    require(
        queued,
        "hideHistoryTouchOverlayQuietly();",
        "queued Send does not release its transient overlay",
    )

    composer = section(
        source,
        "private void refreshCaptureRendererForComposerTransition(",
        "private void refreshCaptureRendererForImmediateBottom(",
    )
    require(
        composer,
        "refreshCaptureRendererPulseIfAsyncObservationCurrent(",
        "composer settle can repaint after touch-scroll supersedes Send",
    )

    layout = section(
        source,
        "private void refreshCaptureRendererForLayoutChange(",
        "private void refreshCaptureRendererForComposerTransition(",
    )
    require(
        layout,
        "refreshCaptureRendererPulseIfAsyncObservationCurrent(",
        "keyboard layout settle can repaint after touch-scroll supersedes Send",
    )

    require(
        source,
        "view.setOnTouchListener((touchedView, event) -> handleTerminalTouch(event));",
        "normal terminal surface does not route touch-scroll",
    )
    require(
        source,
        '"renderer-distinct-rows"',
        "touch scroll lacks canonical distinct-row feedback",
    )
    require(
        source,
        "enqueuePromptComposerSubmit(submit);",
        "async Send delivery was removed",
    )
    print("pending-send scroll owner guard passed")


if __name__ == "__main__":
    main()
