#!/usr/bin/env python3
"""Guard green working and yellow waiting/Done status in the composite APK."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MAIN = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
DEFAULT_CONTROL = Path("/home/cabule/.local/bin/mantis-phone-control-server")


def fail(message: str) -> None:
    raise SystemExit(f"title/status color contract failed: {message}")


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        fail(message)


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


def section(source: str, start: str, end: str) -> str:
    start_at = source.find(start)
    end_at = source.find(end, start_at + len(start))
    if start_at < 0 or end_at < 0:
        fail(f"missing section {start!r}..{end!r}")
    return source[start_at:end_at]


def modeled_color(status: str, tone: str, needs_attention: bool = False) -> str:
    status = status.strip().lower()
    tone = tone.strip().lower()
    if status == "problem" or tone == "problem":
        return "pink"
    if status in {"running", "working"} or tone in {"running", "working"}:
        return "green"
    if needs_attention or tone == "warning" or status == "waiting":
        return "yellow"
    return "neutral"


def run_model() -> None:
    assert modeled_color("running", "working") == "green"
    assert modeled_color("working", "healthy") == "green"
    assert modeled_color("waiting", "healthy") == "yellow"
    assert modeled_color("waiting", "healthy", needs_attention=False) == "yellow"
    assert modeled_color("idle", "healthy") == "neutral"
    assert modeled_color("problem", "problem") == "pink"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main-source", type=Path, default=DEFAULT_MAIN)
    parser.add_argument("--control-source", type=Path, default=DEFAULT_CONTROL)
    args = parser.parse_args()

    run_model()
    main_path = args.main_source.resolve()
    control_path = args.control_source.resolve()
    apk = main_path.read_text(encoding="utf-8")
    control = control_path.read_text(encoding="utf-8")

    working = java_method(apk, "isWorkingSessionStatus")
    for token in (
        '"running".equals(status)',
        '"working".equals(status)',
        '"running".equals(tone)',
        '"working".equals(tone)',
    ):
        require(working, token, f"working authority drifted: {token}")

    colors = java_method(apk, "sessionStatusTextColor")
    for token in (
        "Color.rgb(166, 227, 161)",
        "Color.rgb(249, 226, 175)",
        '"waiting".equals(normalizedStatus)',
        "isWorkingSessionStatus(normalizedStatus, tone)",
    ):
        require(colors, token, f"green/yellow color mapping drifted: {token}")
    if '"healthy".equals(tone)' in working:
        fail("healthy Ready/Done tone can still become working green")

    dot = section(
        apk,
        "private void applySessionStatusDot(TextView dot, String status, boolean needsAttention, String statusLabel, String statusTone, boolean activeSession)",
        "private boolean isWorkingSessionStatus(",
    )
    for token in (
        "else if (working)",
        "startStatusDotPulse(dot);",
        '"waiting".equals(normalizedStatus)',
        "stopStatusDotPulse(dot);",
    ):
        require(dot, token, f"status dot lifecycle drifted: {token}")

    visual_tone = section(
        control,
        "def visual_status_tone(",
        "def tab_status(",
    )
    for token in (
        'state_text in {"running", "working"}',
        'return "working"',
        'state_text in {"waiting", "ready", "idle", "done", "completed", "complete"}',
        'return "healthy"',
    ):
        require(visual_tone, token, f"central status tone mapping drifted: {token}")

    light = section(
        control,
        "def light_tab_status(",
        "def light_tab_title(",
    )
    for token in (
        '"working": "running"',
        '"running": "running"',
        '"waiting": "waiting"',
        '"ready": "waiting"',
        '"idle": "idle"',
        '"done": "idle"',
        '"statusTone": "working"',
    ):
        require(light, token, f"light-tab status projection drifted: {token}")

    print(
        "title/status color contract: green "
        f"(main={main_path}, control={control_path}; "
        "running/working=green, waiting/Done/healthy=yellow)"
    )


if __name__ == "__main__":
    main()
