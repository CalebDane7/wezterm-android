#!/usr/bin/env python3
"""Guard one revision/binding-aware title resolver across APK list and strip."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"


def fail(message: str) -> None:
    raise SystemExit(f"title consume single-authority contract failed: {message}")


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


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8")

    resolver = section(
        source,
        "private String displayTitleOrLastGood(",
        "private String selectLiveDisplayTitle(",
    )
    for token in (
        "centralTitle.trim()",
        "CanonicalTitleSnapshot lastGood",
        "lastGoodCanonicalTitleByWindow.get(stableWindowId)",
        "usableSessionTitle(lastGood.title)",
        "return lastGood.title.trim()",
        "serverConfirmedSessionTitle(stableWindowId)",
        "centralTitleOrPending(centralTitle)",
    ):
        require(resolver, token, f"shared per-window resolver is missing: {token}")

    list_resolver = section(
        source,
        "private String selectLiveDisplayTitle(",
        "private void rememberActivePhoneWindow(",
    )
    require(
        list_resolver,
        "return displayTitleOrLastGood(windowId, centralTitleFromPayload(payload));",
        "Active list rows bypass the shared per-window resolver",
    )

    strip = section(
        source,
        "private void updateSessionTitleStrip(JSONObject window)",
        "private void updateSessionTitleStrip(String title)",
    )
    require(
        strip,
        "String displayTitle = displayTitleOrLastGood(windowId, title);",
        "title strip can downgrade independently from the Active list",
    )

    retained_load = section(
        source,
        "private void loadRetainedActiveSessionsDisplaySnapshot()",
        "private void clearRetainedActiveSessionsDisplaySnapshot(",
    )
    for token in (
        "for (JSONObject warmRow : activeSessionsPayloadRenderedWindows(payload))",
        "centralTitleFromPayload(warmRow);",
    ):
        require(retained_load, token, f"cold-start last-good warm is missing: {token}")

    retained_reconcile = section(
        source,
        "private void reconcileRetainedDisplayTitleForWindow(",
        "private void prewarmListedSessionFramesForSwitch(",
    )
    for token in (
        "retainedRevision < current.revision",
        "!current.matchesBinding(",
        "!current.title.equals(retainedDisplay)",
        '"stale-title-generation-superseded"',
    ):
        require(
            retained_reconcile,
            token,
            f"stale retained generation is not evicted: {token}",
        )

    print(f"title consume single-authority contract: green ({SOURCE})")


if __name__ == "__main__":
    main()
