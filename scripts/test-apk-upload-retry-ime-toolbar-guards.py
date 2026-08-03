#!/usr/bin/env python3
"""Focused source/model guard for v227 upload retry and IME-visible controls."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
PHONE_PROOF = ROOT / "scripts/prove-phone-menu-ui.sh"


def method_body(source: str, signature: str) -> str:
    start = source.find(signature)
    if start < 0:
        raise AssertionError(f"missing production method: {signature}")
    brace = source.find("{", start)
    if brace < 0:
        raise AssertionError(f"missing method body: {signature}")
    depth = 0
    for index in range(brace, len(source)):
        value = source[index]
        if value == "{":
            depth += 1
        elif value == "}":
            depth -= 1
            if depth == 0:
                return source[brace : index + 1]
    raise AssertionError(f"unterminated method body: {signature}")


def class_body(source: str, signature: str) -> str:
    return method_body(source, signature)


def require(haystack: str, needle: str, message: str) -> None:
    if needle not in haystack:
        raise AssertionError(f"{message}: missing {needle!r}")


def forbid(haystack: str, needle: str, message: str) -> None:
    if needle in haystack:
        raise AssertionError(f"{message}: forbidden {needle!r}")


def dotted_version(value: str) -> tuple[int, ...]:
    if not re.fullmatch(r"\d+(?:\.\d+)*", value):
        raise AssertionError(f"invalid dotted version: {value!r}")
    return tuple(int(part) for part in value.split("."))


def main() -> int:
    source = MAIN.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")
    phone_proof = PHONE_PROOF.read_text(encoding="utf-8")

    code_match = re.search(r'android:versionCode="(\d+)"', manifest)
    name_match = re.search(r'android:versionName="([^"]+)"', manifest)
    app_name_match = re.search(
        r'private static final String APP_VERSION_NAME = "([^"]+)";',
        source,
    )
    if not code_match or not name_match or not app_name_match:
        raise AssertionError("missing manifest/MainActivity version authority")
    version_code = int(code_match.group(1))
    version_name = name_match.group(1)
    app_version_name = app_name_match.group(1)
    if version_code < 227:
        raise AssertionError(f"v227 candidate required, found versionCode={version_code}")
    if dotted_version(version_name) < dotted_version("3.15"):
        raise AssertionError(f"v227 versionName must be >= 3.15, found {version_name}")
    if app_version_name != version_name:
        raise AssertionError(
            "MainActivity request identity must match the manifest: "
            f"app={app_version_name} manifest={version_name}"
        )

    bottom_bar = method_body(source, "private LinearLayout bottomBar()")
    for label in ("New", "Old", "Active", "Bottom"):
        require(
            bottom_bar,
            f'toolbarNavigationButton("{label}"',
            f"{label} must remain in the IME-visible navigation row",
        )
    forbid(
        bottom_bar,
        'toolbarNavigationButton("Upload"',
        "the redundant standalone toolbar Upload must stay removed",
    )
    for label in ("Copy/Paste", "Workspace", "Settings", "Close", "Start", "Stop"):
        require(
            bottom_bar,
            f'"{label}"',
            f"lower-row control {label} must remain wired",
        )

    composer = method_body(source, "private LinearLayout buildPromptComposer()")
    require(composer, 'uploadButton.setText("+");', "composer + must remain the upload owner")
    require(composer, 'resendButton.setText("⟲");', "composer Resend must remain present")
    require(
        composer,
        "composer.setBaselineAligned(false);",
        "multiline +/Resend containment must remain protected",
    )

    layout = method_body(source, "private void applyToolbarComposerLayout()")
    require(
        layout,
        "toolbarBaseHeightDp = TOOLBAR_HEIGHT_DP;",
        "typing must retain the full two-row toolbar height",
    )
    require(
        layout,
        "toolbarNavRow.setVisibility(View.VISIBLE);",
        "typing must keep New/Old/Active/Bottom visible",
    )
    forbid(
        layout,
        "composerVisible ? View.GONE",
        "composer visibility must not hide the navigation row",
    )
    forbid(
        layout,
        "TOOLBAR_COMPOSER_FOCUS_HEIGHT_DP",
        "composer focus must not collapse the toolbar to one row",
    )

    require(
        source,
        "private static final int MEDIA_UPLOAD_MAX_ATTEMPTS = 2;",
        "upload retry must be bounded to one retry",
    )
    policy = class_body(source, "static final class MediaUploadRetryPolicy")
    require(policy, "current instanceof EOFException", "EOF response failures must be retryable")
    require(
        policy,
        '"unexpected end of stream"',
        "the observed Android response-header failure must be classified",
    )
    require(
        policy,
        "attempt >= MEDIA_UPLOAD_MAX_ATTEMPTS",
        "retry policy must stop after the bounded attempt",
    )

    batch = method_body(source, "private void uploadMediaUrisSequentially(")
    require(
        batch,
        "performMediaUploadWithRetry(",
        "each URI must own its retry before the batch advances",
    )
    for argument in ("uri,", "fromShare,", "uploadTargetWindowId"):
        require(
            batch,
            argument,
            "the per-item retry owner must retain URI/source/target identity",
        )
    forbid(
        batch,
        "performMediaUploadAttempt(",
        "the batch must not bypass the per-item retry owner",
    )
    if batch.count("showUploadedMediaResult(") != 1:
        raise AssertionError(
            "each successfully resolved URI must have exactly one staging callback"
        )

    retry = method_body(source, "private JSONObject performMediaUploadWithRetry(")
    require(
        retry,
        "MediaUploadRetryPolicy.shouldRetry(attempt, exc)",
        "retry wrapper must use the bounded observed-failure policy",
    )
    require(retry, "upload-retry", "retry attempts must be visible in safe logs")
    require(
        retry,
        "performMediaUploadAttempt(",
        "each retry must create a fresh upload attempt",
    )
    forbid(
        retry,
        "showUploadedMediaResult(",
        "retry resolution must not stage a path more than once",
    )

    attempt = method_body(source, "private JSONObject performMediaUploadAttempt(")
    require(
        attempt,
        'connection.setRequestProperty("Connection", "close");',
        "each streamed POST must opt out of stale socket reuse",
    )
    require(
        attempt,
        "applyControlRequestHeaders(connection);",
        "uploads must use the same native-client request identity as control calls",
    )
    require(
        attempt,
        "try (InputStream responseStream = stream)",
        "the response body must close before disconnect",
    )

    require(
        phone_proof,
        'for label in New Old Active Bottom "Copy/Paste" Workspace Settings Close Start Stop; do',
        "closed-keyboard proof must reflect the removed standalone Upload",
    )
    require(
        phone_proof,
        'for label in New Old Active Bottom "Copy/Paste" Workspace Settings Close Send Stop; do',
        "IME-open proof must require every requested control",
    )

    print(
        "PASS_APK_UPLOAD_RETRY_IME_TOOLBAR_GUARDS "
        f"versionCode={version_code} versionName={version_name} "
        "uploadAttempts=2 retryScope=current-uri staging=exactly-once "
        "freshConnection=true imeControls=New,Old,Active,Bottom,"
        "CopyPaste,Workspace,Settings,Close,Send,Stop standaloneUpload=absent"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as exc:
        print(f"FAIL_APK_UPLOAD_RETRY_IME_TOOLBAR_GUARDS {exc}", file=sys.stderr)
        raise SystemExit(1)
