#!/usr/bin/env python3
"""Paint-only identity gate guard (review-challenge 2 falsifier, 2026-07-27).

Contract: a paint-only (acceptedFrame=false) retained body may commit only when
its session/thread identity is consistent with the bound switch identity for
that exact window. Both-known mismatch MUST NOT commit; matching or unknown
identity must still paint (switch speed preserved).
"""
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

# --- source-shape assertions -------------------------------------------------
check(
    "identity helper exists",
    "private boolean paintOnlyFrameIdentityConsistent(" in source,
)
check(
    "idle-commit wires the identity gate",
    'skipReason = "paint-only-identity-mismatch";' in source,
)
gate_idx = source.find('skipReason = "paint-only-identity-mismatch";')
commit_idx = source.find(
    "commitCaptureRendererVisualFrame(reason);",
    source.find("private void handleCaptureRendererIdleCommitRequest("),
)
check(
    "gate sits before the visual commit",
    0 < gate_idx < commit_idx,
)
helper = source[
    source.index("private boolean paintOnlyFrameIdentityConsistent(") :
    source.index("private boolean captureRendererFrameTargetsVisibleWindow(")
]
check(
    "helper checks bound switch identity",
    "activeSwitchTitleLifecycle" in helper and ".identity()" in helper,
)
check(
    "helper mismatch requires both-known",
    helper.count(".isEmpty()") >= 6,
    "unknown identity must not block",
)


# --- toy model mirroring the helper -----------------------------------------
def paint_only_ok(frame_sid, frame_tid, exp_win, win, exp_sid, exp_tid):
    frame_sid, frame_tid = frame_sid.strip(), frame_tid.strip()
    if not frame_sid and not frame_tid:
        return True
    if not exp_win or exp_win != win:
        return True
    if frame_sid and exp_sid and frame_sid != exp_sid:
        return False
    if frame_tid and exp_tid and frame_tid != exp_tid:
        return False
    return True


check(
    "both-known session mismatch blocks",
    paint_only_ok("s-OLD", "t1", "@5", "@5", "s-NEW", "t1") is False,
)
check(
    "both-known thread mismatch blocks",
    paint_only_ok("s1", "t-OLD", "@5", "@5", "s1", "t-NEW") is False,
)
check(
    "exact identity match paints",
    paint_only_ok("s1", "t1", "@5", "@5", "s1", "t1") is True,
)
check(
    "unknown frame identity paints (window-gated)",
    paint_only_ok("", "", "@5", "@5", "s1", "t1") is True,
)
check(
    "unbound/other-window expectation paints",
    paint_only_ok("s2", "t2", "@9", "@5", "s1", "t1") is True,
)
check(
    "unknown expected identity paints",
    paint_only_ok("s1", "t1", "@5", "@5", "", "") is True,
)

print(f"{passed + failed} tests, {passed} passed")
sys.exit(1 if failed else 0)
