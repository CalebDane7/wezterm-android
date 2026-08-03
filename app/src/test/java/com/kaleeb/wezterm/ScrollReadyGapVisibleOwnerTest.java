package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Executable guard that exact cached ownership bypasses v228's READY_GAP replay. */
public final class ScrollReadyGapVisibleOwnerTest {
    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 2, "expected MainActivity.java and v228 fixture paths");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );
        String oldRed = new String(
                Files.readAllBytes(Paths.get(args[1])),
                StandardCharsets.UTF_8
        );

        installedV228FailureIsMachineCheckable(oldRed);
        positiveReadyGapConsumesOnlyAppliedPixels();
        duplicateStageCannotReopenBlockedReplay();
        productionWiresVisibleConsumption(source);
        System.out.println("SCROLL_READY_GAP_VISIBLE_OWNER_PASS");
    }

    private static void installedV228FailureIsMachineCheckable(String log) {
        assertTrue(
                log.contains("actionId=scroll-57051891-1 action=down")
                        && log.contains("y=1332.0")
                        && log.contains("action=history-start"),
                "fixture must retain the exact installed v228 gesture"
        );
        assertTrue(
                log.contains("result=held-for-ready-owner")
                        && log.contains("result=replayed-ready-owner")
                        && log.contains("generationKey=@74:-74:764744:100000"),
                "v228 must prove the residual mechanism really executed"
        );
        assertTrue(
                log.contains("result=READY_GAP appliedPx=11.1")
                        && log.contains("result=READY_GAP appliedPx=150.5")
                        && log.contains("deltaPx=181.07 firstLocalFeedbackElapsedMs=-1 result=READY_GAP appliedPx=54.7")
                        && log.contains("result=READY_GAP appliedPx=0.0"),
                "old-red must retain positive, partial, and zero READY_GAP consumption"
        );
        assertTrue(
                countActionLines(log, "scroll-57051891-1", "firstLocalFeedbackElapsedMs=-1 result=READY_GAP appliedPx=") >= 10,
                "Java must be proven to reject every positive renderer consumption"
        );
        assertTrue(
                log.contains("result=deferred-before-local-feedback")
                        && log.contains("action=up windowId=@74")
                        && log.contains("firstLocalFeedbackAtMs=0 firstLocalFeedbackElapsedMs=-1")
                        && log.contains("result=no-visible-owner-release"),
                "positive applied pixels must still lead to the installed zero-owner release"
        );
        assertTrue(
                log.contains("beforeRows=4000 viewportRows=4000")
                        && log.contains("activeFeedbackOwner=false")
                        && log.contains("localViewportOffset=3904")
                        && log.contains("localHistoryCachedRows=4000"),
                "fixture must retain the cache race caused by repeated replay"
        );
        assertEquals(
                0,
                countActionLines(log, "scroll-57051891-1", "endpoint=/touch-scroll"),
                "the v228 failure is the local renderer owner, not network scrolling"
        );
    }

    private static void positiveReadyGapConsumesOnlyAppliedPixels() {
        Consumption first = consume(181.07f, 54.7f, "READY_GAP");
        assertTrue(first.visibleOwner, "positive READY_GAP must own visible feedback");
        assertNear(126.37f, first.heldPx, 0.02f,
                "only the unconsumed residual may remain held");

        Consumption fullyApplied = consume(70.57f, 70.6f, "READY_GAP");
        assertTrue(fullyApplied.visibleOwner, "fully applied READY_GAP is visible");
        assertNear(0f, fullyApplied.heldPx, 0.05f,
                "fully applied pixels cannot be replayed again");
    }

    private static void duplicateStageCannotReopenBlockedReplay() {
        assertTrue(stageClosesGap("READY"), "new contiguous READY may reopen replay");
        assertFalse(stageClosesGap("QUEUED_DUPLICATE"),
                "queued duplicate does not prove the missing seam closed");
        assertFalse(stageClosesGap("COMMITTED_DUPLICATE"),
                "committed duplicate does not prove the missing seam closed");
        assertFalse(stageClosesGap("PENDING_GAP"),
                "out-of-order pending data cannot own immediate replay");
    }

    private static void productionWiresVisibleConsumption(String source) {
        for (String required : new String[] {
                "CachedHistoryGestureLease",
                "localCachedHistoryOwnsGesture()",
                "nudgeCachedHistoryGesture(cachedDeltaY)",
                "rendererStageClosesBlockedSeam(status)",
                "if (!localCachedHistoryOwnsGesture()"
        }) {
            assertTrue(source.contains(required), "production owner contract missing " + required);
        }
        String nudge = methodBody(source, "private void nudgeCaptureRendererForTouch(");
        assertTrue(
                nudge.indexOf("if (localCachedHistoryOwnsGesture())")
                        < nudge.indexOf("pendingTouchVisualNudgePx += deltaY"),
                "exact cached ownership must bypass the v228 residual replay runner"
        );
        String stage = methodBody(
                source,
                "private void stageReadyLocalHistoryBatchForRenderer(\n"
                        + "            LocalHistoryRequest request,\n"
                        + "            LocalHistoryChunk chunk,\n"
                        + "            String source,\n"
                        + "            ReadyHistoryStageCallback callback"
        );
        assertFalse(
                stage.contains("\"QUEUED_DUPLICATE\".equals(status)\n"
                        + "                                || \"COMMITTED_DUPLICATE\".equals(status)"),
                "duplicate staging cannot repeatedly reopen blocked replay"
        );
        assertTrue(
                stage.contains("rendererStageClosesBlockedSeam(status)")
                        && stage.contains("!localCachedHistoryOwnsGesture()"),
                "only a new READY seam may replay, and never while the cache lease owns"
        );
    }

    private static Consumption consume(float requestedPx, float appliedPx, String status) {
        boolean visible = Math.abs(appliedPx) >= 0.5f;
        float held = requestedPx - appliedPx;
        if (Math.signum(held) != Math.signum(requestedPx) || Math.abs(held) < 0.5f) {
            held = 0f;
        }
        return new Consumption(visible && "READY_GAP".equals(status), held);
    }

    private static boolean stageClosesGap(String status) {
        return "READY".equals(status);
    }

    private static int countActionLines(String log, String actionId, String token) {
        int count = 0;
        for (String line : log.split("\\R")) {
            if (line.contains("actionId=" + actionId) && line.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing source method " + signature);
        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, "unbounded source method " + signature);
        int depth = 0;
        for (int index = brace; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace + 1, index);
                }
            }
        }
        throw new AssertionError("unterminated source method " + signature);
    }

    private static final class Consumption {
        final boolean visibleOwner;
        final float heldPx;

        Consumption(boolean visibleOwner, float heldPx) {
            this.visibleOwner = visibleOwner;
            this.heldPx = heldPx;
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNear(float expected, float actual, float tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
