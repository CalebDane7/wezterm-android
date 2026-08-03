#!/usr/bin/env python3
import argparse
import pathlib
import sys


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def method_slice(source: str, signature: str, next_signature: str) -> str:
    start = source.index(signature)
    end = source.index(next_signature, start + len(signature))
    return source[start:end]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--main", required=True)
    parser.add_argument("--fence", required=True)
    args = parser.parse_args()

    main_path = pathlib.Path(args.main)
    fence_path = pathlib.Path(args.fence)
    require(main_path.is_file(), "MainActivity source missing")
    require(fence_path.is_file(), "SendStatusRevisionFence source missing")

    source = main_path.read_text(encoding="utf-8")
    fence = fence_path.read_text(encoding="utf-8")

    require(
        "private final SendStatusRevisionFence sendStatusRevisionFence" in source,
        "MainActivity must own one per-window Send status fence",
    )
    require(
        "serverConfirmedStatusRevisionByWindow" in source,
        "MainActivity must retain the canonical per-window status revision",
    )

    admission = method_slice(
        source,
        "private void enqueuePromptComposerSubmit(PromptComposerQueuedSubmit submit)",
        "private boolean isDuplicatePromptComposerSubmit",
    )
    post_admission = admission.split("promptComposerSubmitQueue.add(submit);", 1)[1]
    require(
        post_admission.index("beginPromptComposerStatusFence(submit)")
        < post_admission.index("showToolbarControlPending(promptComposerSubmitPendingLabel())"),
        "Send fence must exist before the immediate canonical reclaim poll",
    )

    generic_pending = method_slice(
        source,
        "private void showToolbarControlPending(String label)",
        "private void startStatusDotPulse",
    )
    require(
        "scheduleToolbarStatusDotRefresh(0)" in generic_pending,
        "generic navigation/control reclaim must remain unchanged",
    )

    active_row = method_slice(
        source,
        "private void addTabRow(",
        "private boolean toggleActiveBulkCloseTarget",
    )
    require(
        "sessionStatusForDisplay(window)" in active_row,
        "fresh Active Sessions rows must consume the same Send fence",
    )

    toolbar = method_slice(
        source,
        "private void refreshToolbarStatusDot(long generation)",
        "private Button button(",
    )
    require(
        "sessionStatusForDisplay(window)" in toolbar,
        "selected toolbar dot must consume the Send fence",
    )

    failure = method_slice(
        source,
        "private boolean failPromptComposerSubmit(PromptComposerQueuedSubmit submit)",
        "private boolean failPromptComposerDeliveryBlockOnce",
    )
    success = method_slice(
        source,
        "private boolean completePromptComposerSubmit(PromptComposerQueuedSubmit submit)",
        "private boolean failPromptComposerSubmit",
    )
    require(
        "sendStatusRevisionFence.definitiveFailure" in failure,
        "exact definitive failure must release its fence",
    )
    require(
        "sendStatusRevisionFence.definitiveFailure" not in success,
        "successful delivery must remain fenced until canonical Working then newer terminal",
    )

    revision_reader = method_slice(
        source,
        "private String canonicalStatusRevisionFromPayload(JSONObject payload)",
        "private SendStatusRevisionFence.Status sessionStatusForDisplay(JSONObject window)",
    )
    require(
        'optString("canonicalStatusRevision"' in revision_reader
        and 'optString("@mantis_status_revision"' in revision_reader,
        "revision reader must use canonical publisher fields",
    )
    require(
        'optString("statusRevision"' not in revision_reader,
        "per-response projection statusRevision must not order semantic transitions",
    )

    for required in (
        "isStrictlyOlder",
        "isStrictlyNewer",
        "incoming.isProblem()",
        "incoming.isWorking()",
        "FAST_COMPLETE_FALLBACK_MS",
        "operationIds",
    ):
        require(required in fence, f"fence behavior missing {required}")

    print("SEND_STATUS_REVISION_FENCE_WIRING_GREEN")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, ValueError) as exc:
        print(f"SEND_STATUS_REVISION_FENCE_WIRING_RED: {exc}", file=sys.stderr)
        raise SystemExit(1)
