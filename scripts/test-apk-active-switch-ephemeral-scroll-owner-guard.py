#!/usr/bin/env python3
"""Guard cached terminal text from reactivating a stale scroll/read owner."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"


def fail(message: str) -> None:
    raise SystemExit(f"active-switch ephemeral scroll owner guard failed: {message}")


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


def run_switch_model() -> None:
    immutable_text = {
        "@74": ("@74-live", "@74-history"),
        "@4": ("@4-live", "@4-history"),
    }
    owner = {
        "window": "@74",
        "viewport_rows": 4000,
        "viewport_offset": 3904,
        "read_hold": True,
        "touch_nudge": 812.0,
    }

    def activate(window_id: str) -> tuple[str, ...]:
        owner.update(
            window=window_id,
            viewport_rows=0,
            viewport_offset=0,
            read_hold=False,
            touch_nudge=0.0,
        )
        return immutable_text[window_id]

    assert activate("@4") == ("@4-live", "@4-history")
    owner.update(viewport_rows=287, viewport_offset=191, read_hold=True, touch_nudge=44.0)
    assert activate("@74") == ("@74-live", "@74-history")
    assert owner == {
        "window": "@74",
        "viewport_rows": 0,
        "viewport_offset": 0,
        "read_hold": False,
        "touch_nudge": 0.0,
    }


def main() -> None:
    run_switch_model()
    source = SOURCE.read_text(encoding="utf-8")
    require(
        source,
        "RESET_RENDERER_EPHEMERAL_SCROLL_OWNER_JS",
        "renderer ephemeral-owner reset is missing",
    )
    constant = section(
        source,
        "RESET_RENDERER_EPHEMERAL_SCROLL_OWNER_JS",
        "private WebView webView;",
    )
    require(
        constant,
        "resetReadyHistoryForBottom",
        "cached activation preserves stale READY viewport state",
    )
    require(
        constant,
        "clearTouchScrollNudge",
        "cached activation preserves stale read-hold/nudge state",
    )

    paint = section(
        source,
        "private boolean paintTerminalSnapshotFromCache(",
        "private void nudgeCaptureRendererForTouch(",
    )
    require(
        paint,
        "r.activateTerminalSnapshot",
        "cached terminal text activation is missing",
    )
    require(
        paint,
        "RESET_RENDERER_EPHEMERAL_SCROLL_OWNER_JS",
        "snapshot activation rehydrates stale ephemeral owner",
    )

    target = section(
        source,
        "private void setCaptureRendererWindowTarget(\n            String targetKey,\n            String reason,\n            int attempt,\n            long requestedTransitionGeneration",
        "private void markCaptureRendererWindowTargetConfirmed(",
    )
    require(
        target,
        "if (targetChanged) {",
        "snapshot cache activation is not bound to a real target handoff",
    )
    require(
        target,
        "RESET_RENDERER_EPHEMERAL_SCROLL_OWNER_JS",
        "setWindowId cache activation can restore stale ephemeral owner",
    )

    invalidation = section(
        source,
        "private void invalidateTerminalTouchOwnerForTargetHandoff(",
        "private boolean updateCaptureRendererWindowTargetKey(",
    )
    for token in (
        "readModeSuppressesKeyboard = false;",
        "localHistoryTouchViewportRows.clear();",
        "activeHistoryScrollRequestSerial = -1;",
    ):
        require(invalidation, token, f"native ephemeral owner does not clear: {token}")
    for forbidden in (
        "terminalSnapshotCache.clear()",
        "terminalSnapshotByOwner.clear()",
        "latestTerminalSnapshotByWindow.clear()",
        "localHistoryChunkCache.clear()",
    ):
        if forbidden in invalidation:
            fail(f"immutable text/history cache was discarded: {forbidden}")

    print("active-switch ephemeral scroll owner guard passed")


if __name__ == "__main__":
    main()
