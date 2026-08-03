#!/usr/bin/env python3
"""Guard explicit composer intent without weakening ordinary read holds."""

from __future__ import annotations

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"


def fail(message: str) -> None:
    raise SystemExit(f"composer read-hold release contract failed: {message}")


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


def run_model() -> None:
    def typing_band_height(*, existing_height: int, usable_height: int) -> int:
        return max(existing_height, min(144, usable_height // 4))

    assert typing_band_height(existing_height=52, usable_height=720) == 144
    assert typing_band_height(existing_height=52, usable_height=480) == 120
    assert typing_band_height(existing_height=52, usable_height=160) == 52
    assert typing_band_height(existing_height=52, usable_height=720) <= 720 // 4
    phone_height_px = 2599
    phone_top_guard_px = 84
    phone_bottom_guard_px = 42
    phone_usable_bottom_px = phone_height_px - phone_bottom_guard_px
    phone_usable_height_px = phone_usable_bottom_px - phone_top_guard_px
    phone_band_height_px = max(182, min(504, phone_usable_height_px // 4))
    phone_band_top_px = phone_usable_bottom_px - phone_band_height_px
    assert phone_band_top_px == 2053
    for failed_tap_y in (2084.0, 2263.0, 2290.5, 2320.2, 2325.5, 2335.4, 2351.4):
        assert phone_band_top_px <= failed_tap_y <= phone_usable_bottom_px
    assert 1900.0 < phone_band_top_px

    def native_hold(
        *,
        explicit_live_input: bool,
        released_token: bool,
        local_viewport: bool,
        read_mode: bool,
    ) -> bool:
        if explicit_live_input:
            released_token = False
            local_viewport = False
            read_mode = False
        return released_token or local_viewport or read_mode

    assert not native_hold(
        explicit_live_input=True,
        released_token=True,
        local_viewport=False,
        read_mode=False,
    )
    assert native_hold(
        explicit_live_input=False,
        released_token=False,
        local_viewport=True,
        read_mode=False,
    )
    assert native_hold(
        explicit_live_input=False,
        released_token=True,
        local_viewport=False,
        read_mode=False,
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    args = parser.parse_args()

    run_model()
    source_path = args.source.resolve()
    source = source_path.read_text(encoding="utf-8")

    typing_target = section(
        source,
        "private boolean isTerminalTypingTapTarget(MotionEvent event)",
        "private int terminalBodyToolbarBoundaryGuardPx()",
    )
    for token in (
        "int usableHeight = Math.max(",
        "usableBottom - topGuard",
        "Math.min(dp(144), usableHeight / 4)",
    ):
        require(
            typing_target,
            token,
            f"typing intent band lost bounded lower-prompt geometry: {token}",
        )
    require(
        typing_target,
        "if (event == null || !isTerminalBodyScrollGestureTarget(event))",
        "typing intent must remain scoped to the terminal body",
    )

    touch = section(
        source,
        "private boolean handleTerminalTouch(MotionEvent event)",
        "private boolean shouldHoldLocalHistoryTouchTopEdge(",
    )
    tap_start = touch.find("if (isTopTerminalTap(event))")
    tap_end = touch.find("recycleTerminalViewerDownEvent();", tap_start)
    if tap_start < 0 or tap_end < 0:
        fail("normal terminal-body tap branch is missing")
    tap_branch = touch[tap_start:tap_end]
    leave_call = 'leaveReadModeForLiveInput(false, "composer-tap-up");'
    show_call = 'showDockedPromptComposer("tap-up");'
    leave_at = tap_branch.find(leave_call)
    show_at = tap_branch.find(show_call)
    if leave_at < 0:
        fail("terminal-body composer tap does not leave read mode before opening composer")
    if show_at < 0 or leave_at > show_at:
        fail("terminal-body composer tap opens composer before releasing read ownership")
    between_calls = tap_branch[leave_at + len(leave_call):show_at]
    if between_calls.strip():
        fail("composer live-intent clear must remain immediately before composer open")
    for token in (
        "&& !movedPastTapSlop",
        "continuedReleasedReadHold && !startedInTypingBand",
        "&& (!continuedReleasedReadHold || startedInTypingBand);",
        "if (!startedInTypingBand && shouldKeepReadModeAfterRecentHistoryDragTap())",
    ):
        require(
            touch,
            token,
            f"read-retouch/drag protection was weakened: {token}",
        )

    composer = section(
        source,
        "private void showDockedPromptComposer(String reason)",
        "private void alignViewerForComposerReason(String reason)",
    )
    if "leaveReadModeForLiveInput(" in composer:
        fail("generic composer opens must not duplicate tap-up live recovery")

    leave_live = section(
        source,
        "private long leaveReadModeForLiveInput(\n"
        "            boolean pinAfterOverlay,\n"
        "            String terminalSnapshotReason",
        "private void clearLocalHistoryTouchViewportForLiveBottom(String reason)",
    )
    for token in (
        "terminalHistoryViewportActive = false;",
        "readModeSuppressesKeyboard = false;",
        'clearCaptureRendererTouchNudge("live-input");',
        'syncCaptureRendererReadHold("live-input");',
    ):
        require(leave_live, token, f"explicit live-input cleanup lost {token!r}")
    if leave_live.find('clearCaptureRendererTouchNudge("live-input");') > leave_live.find(
        'syncCaptureRendererReadHold("live-input");'
    ):
        fail("native hold sync runs before released renderer ownership is cleared")

    clear_nudge = section(
        source,
        "private void clearCaptureRendererTouchNudge(String reason)",
        "private void postCaptureRendererPulseFrame(String reason)",
    )
    require(
        clear_nudge,
        "clearCaptureRendererReleasedReadHold(reason);",
        "live-input cleanup no longer revokes the exact released renderer token",
    )

    read_hold = section(
        source,
        "private boolean captureRendererReadHoldActive()",
        "private void syncCaptureRendererReadHold(String reason)",
    )
    for token in (
        "readModeSuppressesKeyboard",
        "terminalHistoryViewportActive",
        "localHistoryTouchViewportOwnsVisibleSurfaceForReadHold()",
        "liveTuiScrollLockActive()",
    ):
        require(read_hold, token, f"genuine read owner lost from native hold: {token}")

    release = section(
        source,
        "private void releaseCaptureRendererTouchNudge(String reason)",
        "private void armCaptureRendererReleasedReadHoldForRelease(String reason)",
    )
    require(
        release,
        "armCaptureRendererReleasedReadHoldForRelease(reason);",
        "ordinary finger release no longer preserves renderer read ownership",
    )

    print(f"composer read-hold release contract: green (source={source_path})")


if __name__ == "__main__":
    main()
