#!/usr/bin/env python3
"""Static veto for bounded, observational-only T5 scroll/stream telemetry."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAIN = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
DEFAULT_CONTROLLER = (
    ROOT / "app/src/main/java/com/kaleeb/wezterm/NativeHistoryScrollController.java"
)


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"missing method: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise AssertionError(f"missing method body: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        char = source[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[brace + 1 : index]
    raise AssertionError(f"unterminated method: {signature}")


def require(haystack: str, needle: str) -> None:
    if needle not in haystack:
        raise AssertionError(f"missing contract token: {needle}")


def reject(haystack: str, needle: str) -> None:
    if needle in haystack:
        raise AssertionError(f"forbidden contract token: {needle}")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main-source", type=Path, default=DEFAULT_MAIN)
    parser.add_argument("--controller-source", type=Path, default=DEFAULT_CONTROLLER)
    args = parser.parse_args()

    main_source = args.main_source.read_text(encoding="utf-8")
    controller_source = args.controller_source.read_text(encoding="utf-8")

    require(controller_source, "int minimumFlingVelocityPxPerSecond()")
    require(controller_source, "return minimumFlingVelocity;")

    release = method_body(
        main_source, "private boolean dispatchHistoryReleaseFling(MotionEvent event)"
    )
    release_log = method_body(
        main_source, "private boolean logHistoryReleaseFlingDecision("
    )
    require(release, "minimumFlingVelocityPxPerSecond()")
    require(release, "computeCurrentVelocity(1000, maximumFlingVelocity)")
    require(release, "getYVelocity(event.getPointerId(0))")
    require(release, "below-device-minimum")
    require(release, "opposite-drag-direction")
    require(release, "live-bottom-boundary")
    require(release, "history-top-boundary")
    if release.count("logHistoryReleaseFlingDecision(") != 7:
        raise AssertionError("each release branch must emit exactly one decision receipt")
    reject(release, "Log.i(")
    require(release_log, '"stage=history-release-fling endpoint=renderer"')
    require(release_log, '" signedVelocityPxPerSecond="')
    require(release_log, '" minimumVelocityPxPerSecond="')
    require(release_log, '" maximumVelocityPxPerSecond="')
    require(release_log, '" velocityQualified="')
    require(release_log, '" monotonicMs="')
    if release_log.count("Log.i(") != 1:
        raise AssertionError("release telemetry must be one bounded log")
    reject(release_log, "/touch-scroll")
    reject(release_log, "/scrollback/chunk")

    kinetic_state = method_body(
        main_source, "private void handleNativeHistoryKineticStateChanged(boolean running)"
    )
    require(kinetic_state, '"stage=history-momentum endpoint=renderer result=started"')
    require(kinetic_state, '"stage=history-momentum endpoint=renderer result="')
    require(kinetic_state, '" firstFrameAtMs="')
    require(kinetic_state, '" lastFrameAtMs="')
    require(kinetic_state, '" frameCount="')
    require(kinetic_state, '" totalAbsPx="')
    if kinetic_state.count("Log.i(") != 2:
        raise AssertionError("momentum telemetry must be start plus one terminal summary")

    pixel_sink = method_body(
        main_source,
        "private boolean applyNativeHistoryScrollPixels(float deltaY, String direction)",
    )
    require(pixel_sink, "nativeHistoryMomentumFrameCount++")
    require(pixel_sink, "nativeHistoryMomentumTotalAbsPx += Math.abs(deltaY)")
    reject(pixel_sink, "Log.i(")
    reject(pixel_sink, "getJson(")
    reject(pixel_sink, "/touch-scroll")
    reject(pixel_sink, "/scrollback/chunk")

    cancel = method_body(
        main_source, "private void cancelHistoryMomentum(String reason)"
    )
    require(cancel, "nativeHistoryMomentumCancelReason")
    require(main_source, 'cancelHistoryMomentum("new-down")')
    require(main_source, 'cancelHistoryMomentum("multi-touch")')
    reject(main_source, "cancelHistoryMomentum()")

    hook = method_body(
        main_source, "private String captureRendererTelemetryHookScript(String reason)"
    )
    marker = "var liveStreamStatus=document.getElementById('status')"
    require(hook, marker)
    observer_start = hook.index(marker)
    observer_end = hook.index("WHY(active switch v227)", observer_start)
    observer = hook[observer_start:observer_end]
    require(observer, "lastLiveStreamState")
    require(observer, "raw===lastLiveStreamState")
    require(observer, "new MutationObserver")
    require(observer, "attributeFilter:['data-live-stream']")
    require(observer, "streamState:state")
    require(observer, "windowId:targetWindowId('')")
    require(observer, "pageMonotonicMs:pageNow")
    for forbidden in (
        "setInterval(",
        "setTimeout(",
        "fetch(",
        "XMLHttpRequest",
        "getJson(",
        "/touch-scroll",
        "/scrollback/chunk",
    ):
        reject(observer, forbidden)

    require(main_source, "private static final boolean HISTORY_DRAG_MOVE_NETWORK_ENABLED = false;")
    flush = method_body(
        main_source,
        "private void flushDeferredHistoryScrollOnRelease(boolean releaseFlingStarted)",
    )
    reject(flush, "dispatchHistoryScroll")
    reject(flush, "getJson(")

    print(
        json.dumps(
            {
                "status": "green",
                "mainSha256": sha256(args.main_source),
                "controllerSha256": sha256(args.controller_source),
                "releaseDecisionLogsPerAction": 1,
                "momentumLogs": "start+terminal-summary",
                "perFrameLoggingAdded": False,
                "streamObserver": "data-live-stream-transitions-only",
                "networkAdded": False,
            },
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
