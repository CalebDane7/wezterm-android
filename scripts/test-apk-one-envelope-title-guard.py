#!/usr/bin/env python3
"""Canonical title generation guard for the installed APK consumer."""
from dataclasses import dataclass
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"

passed = 0
failed = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global passed, failed
    if ok:
        passed += 1
        print(f"PASS: {name}")
    else:
        failed += 1
        print(f"FAIL: {name}{(' (' + detail + ')') if detail else ''}")


source = MAIN.read_text()
start = source.index("private static final class CanonicalTitleSnapshot")
end = source.index("private boolean isResolvedTitleStatus(", start)
section = source[start:end]

check(
    "revision and binding aware retained snapshot",
    "CanonicalTitleSnapshot" in section
    and "lastGoodCanonicalTitleByWindow" in section,
)
check(
    "canonical-only title is renderable",
    "projectedTitle.equals(authority)" in section
    and "return authority;" in section,
)
check(
    "late lower revision cannot repaint",
    "retained.revision > revision" in section
    and "retained.matchesBinding(" in section,
)
check(
    "fresh generation replaces retained snapshot",
    "new CanonicalTitleSnapshot(" in section
    and "lastGoodCanonicalTitleByWindow.put(" in section,
)


@dataclass(frozen=True)
class Snapshot:
    title: str
    revision: int
    binding: tuple[str, str, str, str]


def resolve(authority, revision, binding, display, title, retained):
    if retained and retained.binding == binding and retained.revision > revision:
        return retained
    projected = ""
    malformed = False
    for candidate in (display, title):
        if candidate is None:
            continue
        candidate = candidate.strip()
        if not candidate or (projected and projected != candidate):
            malformed = True
            break
        projected = candidate
    rendered = authority
    prefix = authority + ": "
    if (
        not malformed
        and projected
        and (
            projected == authority
            or (projected.startswith(prefix) and len(projected) > len(prefix))
        )
    ):
        rendered = projected
    return Snapshot(rendered, revision, binding)


BINDING = (
    "@38",
    "019faced-d1eb-7913-8b43-5013b4d96ade",
    "fe3b3e75-a6a8-4c18-b136-e4bcb9b2c48d",
    "c948e2e07b9434ec8615331f91a39e436def1f7a65323726db624ecec53bdd5b",
)
EXACT = "Erebora.org Homepage 10x Overhaul + Booking Terms"
OLD = Snapshot(
    "Erebora Homepage Overhaul: Title Brain Regression",
    2764,
    BINDING,
)

fresh = resolve(EXACT, 2776, BINDING, EXACT, EXACT, OLD)
check(
    "rev2776 canonical-only outranks rev2764 stale composite",
    fresh.title == EXACT and fresh.revision == 2776,
)
late_stale = resolve(
    "Erebora Homepage Overhaul",
    2764,
    BINDING,
    "Erebora Homepage Overhaul: Title Brain Regression",
    "Erebora Homepage Overhaul: Title Brain Regression",
    fresh,
)
check("late rev2764 response cannot repaint", late_stale == fresh)
working = EXACT + ": Implementing Canonical Booking Terms"
same_revision = resolve(EXACT, 2776, BINDING, working, working, fresh)
check(
    "same-revision working-tail repaint remains supported",
    same_revision.title == working,
)
malformed = resolve(EXACT, 2777, BINDING, working, EXACT, same_revision)
check(
    "self-disagreeing projection falls back to verified authority",
    malformed.title == EXACT and malformed.revision == 2777,
)

print(f"{passed + failed} tests, {passed} passed")
sys.exit(1 if failed else 0)
