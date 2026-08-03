package com.kaleeb.wezterm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Executable old-red/green guard for fresh-WebView Active-switch ownership.
 *
 * <p>The installed v217 failure kept native generation 3 pending while the
 * replacement JavaScript document emitted generation 0. The first accepted
 * frame and changed DOM were therefore rejected, and later same-hash polls
 * could never supply the missing changed-DOM event.
 */
public final class ActiveSwitchRendererDocumentGenerationTest {
    private static final String BODY_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    public static void main(String[] args) throws Exception {
        generationZeroPoisonsPendingSameTargetUntilCurrentEvidenceArrives();
        deadlineReleasesRendererLeaseAndRecoversExactGenerationZeroPair();
        onlyExactSameTargetCurrentGenerationMayReplay();
        productionPublishesGenerationBeforeTargetRefresh();
        System.out.println("ACTIVE_SWITCH_RENDERER_DOCUMENT_GENERATION_PASS");
    }

    private static void generationZeroPoisonsPendingSameTargetUntilCurrentEvidenceArrives() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle =
                new MainActivity.ActiveSwitchTitleLifecycle("A", "hash-a");
        MainActivity.ActiveSwitchIdentity target =
                new MainActivity.ActiveSwitchIdentity("@74", "session-74", "thread-74");
        lifecycle.begin(target, "B1", 1_000L);
        lifecycle.begin(target, "B2", 2_000L);
        long generation = lifecycle.begin(target, "B3", 3_000L);
        assertEquals(3L, generation, "fixture must reproduce pending generation 3");

        assertDisposition(
                "late-generation-ignored",
                lifecycle.onFrame(
                        0L,
                        target,
                        200,
                        true,
                        120,
                        3,
                        "frame-3",
                        BODY_SHA
                )
        );
        assertDisposition(
                "late-generation-ignored",
                lifecycle.onDom(
                        0L,
                        target,
                        true,
                        120,
                        3,
                        "frame-3",
                        "frame-3",
                        BODY_SHA,
                        "B3"
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.PENDING_FRAME,
                lifecycle.phase(),
                "generation-0 replacement-document evidence must not commit generation 3"
        );

        assertDisposition(
                "frame-candidate",
                lifecycle.onFrame(
                        generation,
                        target,
                        200,
                        true,
                        120,
                        3,
                        "frame-3",
                        BODY_SHA
                )
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                lifecycle.onDom(
                        generation,
                        target,
                        true,
                        120,
                        3,
                        "frame-3",
                        "frame-3",
                        BODY_SHA,
                        "B3"
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.COMMITTED,
                lifecycle.phase(),
                "same-generation frame plus changed DOM must still commit atomically"
        );
    }

    private static void deadlineReleasesRendererLeaseAndRecoversExactGenerationZeroPair() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle =
                new MainActivity.ActiveSwitchTitleLifecycle("A", "hash-a");
        MainActivity.ActiveSwitchIdentity target =
                new MainActivity.ActiveSwitchIdentity("@74", "session-74", "thread-74");
        long generation = lifecycle.begin(target, "B", 1_000L);
        assertEquals(
                generation,
                lifecycle.leasedRendererGeneration(),
                "pending transition owns its JavaScript renderer generation"
        );
        assertDisposition(
                "commit-deadline-no-frame-dom",
                lifecycle.onDeadline(
                        generation,
                        1_000L + MainActivity.ACTIVE_SWITCH_COMMIT_DEADLINE_MS
                )
        );
        assertEquals(
                0L,
                lifecycle.leasedRendererGeneration(),
                "deadline must expire the old document's renderer generation lease"
        );
        assertEquals(
                0L,
                lifecycle.rendererEventGeneration(0L, "@75"),
                "generation zero from a foreign target must remain stale"
        );
        assertEquals(
                2L,
                lifecycle.rendererEventGeneration(2L, "@74"),
                "a nonzero stale generation must not inherit deadline recovery"
        );

        long recoveredGeneration = lifecycle.rendererEventGeneration(0L, "@74");
        assertEquals(
                generation,
                recoveredGeneration,
                "only exact-target generation zero may rejoin the retained native lifecycle"
        );
        assertDisposition(
                "frame-candidate",
                lifecycle.onFrame(
                        recoveredGeneration,
                        target,
                        200,
                        true,
                        120,
                        3,
                        "frame-recovered",
                        BODY_SHA
                )
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                lifecycle.onDom(
                        recoveredGeneration,
                        target,
                        true,
                        120,
                        3,
                        "frame-recovered",
                        "frame-recovered",
                        BODY_SHA,
                        "B"
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.COMMITTED,
                lifecycle.phase(),
                "recovered exact frame/DOM pair must still commit atomically"
        );
    }

    private static void onlyExactSameTargetCurrentGenerationMayReplay() {
        assertEquals(
                3L,
                MainActivity.ActiveSwitchRendererDocumentGeneration.generationToReplay(
                        "@74",
                        "@74",
                        3L,
                        3L
                ),
                "fresh document must inherit the still-current same-target generation"
        );
        assertEquals(
                0L,
                MainActivity.ActiveSwitchRendererDocumentGeneration.generationToReplay(
                        "@75",
                        "@74",
                        3L,
                        3L
                ),
                "foreign document must not inherit another target's generation"
        );
        assertEquals(
                0L,
                MainActivity.ActiveSwitchRendererDocumentGeneration.generationToReplay(
                        "@74",
                        "@74",
                        4L,
                        3L
                ),
                "a delayed retry must not replay a replaced generation"
        );
        assertEquals(
                0L,
                MainActivity.ActiveSwitchRendererDocumentGeneration.generationToReplay(
                        "74",
                        "@74",
                        3L,
                        3L
                ),
                "unstable document identity must fail closed"
        );
    }

    private static void productionPublishesGenerationBeforeTargetRefresh() throws IOException {
        String source = new String(
                Files.readAllBytes(mainActivitySourcePath()),
                StandardCharsets.UTF_8
        );
        String setter = slice(
                source,
                "private void setCaptureRendererWindowTarget(\n"
                        + "            String targetKey,\n"
                        + "            String reason,\n"
                        + "            int attempt,\n"
                        + "            long requestedTransitionGeneration",
                "private void markCaptureRendererWindowTargetConfirmed("
        );
        assertBefore(
                setter,
                "window.__weztermActiveSwitchGeneration=transitionGeneration;",
                "r.setWindowId(target);",
                "fresh-document generation must publish before target binding"
        );
        assertBefore(
                setter,
                "r.setWindowId(target);",
                "r.refresh(true,'apk-target-change');",
                "target binding must precede its first owned frame refresh"
        );
        assertTrue(
                setter.contains(
                        "ActiveSwitchRendererDocumentGeneration.generationToReplay("
                ),
                "every delayed target retry must revalidate exact target/generation ownership"
        );
        assertTrue(
                setter.contains(
                        "activeSwitchTitleLifecycle.leasedRendererGeneration()"
                ),
                "delayed retries must not republish an expired renderer lease"
        );

        String rebind = slice(
                source,
                "private void rebindCaptureRendererWindowTargetAfterMainFrameLoad(",
                "private void focusTerminalInputSoon()"
        );
        assertTrue(
                rebind.contains(
                        "ActiveSwitchRendererDocumentGeneration.generationToReplay("
                ),
                "main-frame replacement must derive a safe generation replay"
        );
        assertTrue(
                rebind.contains(
                        "setCaptureRendererWindowTarget(\n"
                                + "                stableTarget,\n"
                                + "                reason + \"-fresh-js-context\",\n"
                                + "                0,\n"
                                + "                transitionGenerationToPublish"
                ),
                "main-frame replacement must bind target and generation in one script"
        );

        String deadline = slice(
                source,
                "private void finishPendingActiveSwitchTitleDeadline(",
                "private void finishPendingActiveSwitchTitleFailure("
        );
        assertTrue(
                deadline.contains(
                        "releasePendingActiveSwitchRendererGenerationLease("
                ),
                "the bounded Active deadline must release the renderer generation lease"
        );
        String release = slice(
                source,
                "private void releasePendingActiveSwitchRendererGenerationLease(",
                "private void finishPendingActiveSwitchTitleFailure("
        );
        assertBefore(
                release,
                "window.__weztermActiveSwitchGeneration=0;",
                "r.refresh(true,'active-switch-generation-lease-expired');",
                "lease reset and recovery refresh must be one ordered JavaScript transaction"
        );
        assertTrue(
                source.contains("activeSwitchRendererEventGeneration(payload)"),
                "telemetry and visual commit gates must normalize only eligible recovery events"
        );
    }

    private static Path mainActivitySourcePath() {
        String override = System.getProperty("wezterm.main.source", "").trim();
        return override.isEmpty()
                ? Paths.get("app/src/main/java/com/kaleeb/wezterm/MainActivity.java")
                : Paths.get(override);
    }

    private static String slice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        if (start < 0) {
            throw new AssertionError("missing production marker: " + startMarker);
        }
        int end = source.indexOf(endMarker, start + startMarker.length());
        if (end < 0) {
            throw new AssertionError("missing production marker: " + endMarker);
        }
        return source.substring(start, end);
    }

    private static void assertBefore(
            String source,
            String first,
            String second,
            String message
    ) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue(firstIndex >= 0, message + " (missing first marker)");
        assertTrue(secondIndex >= 0, message + " (missing second marker)");
        assertTrue(firstIndex < secondIndex, message);
    }

    private static void assertDisposition(
            String expected,
            MainActivity.ActiveSwitchEventResult actual
    ) {
        assertEquals(expected, actual.disposition, "disposition");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private ActiveSwitchRendererDocumentGenerationTest() {
    }
}
