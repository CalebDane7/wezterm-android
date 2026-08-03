package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Executable guard for exact-bound scroll-owner recovery at a READY seam. */
public final class ScrollSeamOwnerRecoveryTest {
    private static final long MAX_FIRST_FEEDBACK_MS = 30L;

    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 2, "expected MainActivity.java and v227 scroll fixture paths");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );
        String oldRed = new String(
                Files.readAllBytes(Paths.get(args[1])),
                StandardCharsets.UTF_8
        );

        installedV227FailureIsMachineCheckable(oldRed);
        exactReadyOwnerMayReplayHeldFingerPixels();
        foreignOrStaleReadyOwnerCannotReplay();
        noFeedbackReleaseCannotFreezeRealtime();
        productionWiresConsumptionAwareRecovery(source);
        System.out.println("SCROLL_SEAM_OWNER_RECOVERY_PASS");
    }

    private static void installedV227FailureIsMachineCheckable(String log) {
        assertTrue(
                log.contains("actionId=scroll-54875252-19")
                        && log.contains("result=APPLIED appliedPx=-18.0")
                        && log.contains("result=CLAMPED appliedPx=0.0 needsReady=true"),
                "preceding action must retain the applied-then-clamped seam residue"
        );
        assertTrue(
                log.contains("actionId=scroll-54876093-20")
                        && log.contains("startedInHistory=true")
                        && log.contains("readMode=true"),
                "old-red must stay bound to the exact installed v227 follow-up gesture"
        );
        assertTrue(
                log.contains("beforeRows=706 viewportRows=1059")
                        && log.contains("beforeRows=1059 viewportRows=1402")
                        && log.contains("result=READY")
                        && log.contains("generationKey=@74:-74:764744:100000"),
                "old-red must prove exact cache expansion and READY staging"
        );
        assertEquals(
                2,
                countActionLines(log, "scroll-54876093-20", "result=CLAMPED appliedPx=0.0"),
                "every renderer nudge in the failing action must be zero-pixel CLAMPED"
        );
        assertEquals(
                0,
                countActionLines(log, "scroll-54876093-20", "result=APPLIED"),
                "failing action must not contain hidden visible movement"
        );
        assertTrue(
                log.contains("action=up windowId=@74 tapDownAtMs=58487385 firstLocalFeedbackAtMs=0 firstLocalFeedbackElapsedMs=-1"),
                "failing release must retain zero first-local-feedback"
        );
        assertTrue(
                countActionLines(log, "scroll-54876093-20", "deferred-before-local-feedback") >= 2,
                "compositor must remain deferred before the missing local owner"
        );
        assertTrue(
                log.contains("async-paint-deferred-boundary-source-memory-only")
                        && log.contains("activeFeedbackOwner=false")
                        && log.contains("firstLocalFeedbackAtMs=0"),
                "cache growth must remain memory-only instead of being mislabeled as visible feedback"
        );
        assertEquals(
                0,
                countActionLines(log, "scroll-54876093-20", "endpoint=/touch-scroll"),
                "MOVE must not be mislabeled as a network-owned scroll"
        );
        assertTrue(
                log.contains("result=suppressed-read-hold")
                        && log.contains("skipReason=native-read-hold"),
                "old-red must retain the realtime freeze after the zero-owner release"
        );
    }

    private static void exactReadyOwnerMayReplayHeldFingerPixels() {
        RecoveryState state = new RecoveryState("@74", "@74:-74:764744:100000");
        state.hold(13.4f, "lineUp");
        state.hold(768.0f, "lineUp");
        float replay = state.admitReady(
                "@74",
                "@74:-74:764744:100000",
                true,
                12L
        );
        assertTrue(replay > 0.5f, "exact READY owner must replay visible held finger pixels");
        assertTrue(state.firstFeedbackElapsedMs <= MAX_FIRST_FEEDBACK_MS,
                "exact READY replay must own local feedback within 30ms");
    }

    private static void foreignOrStaleReadyOwnerCannotReplay() {
        RecoveryState state = new RecoveryState("@74", "@74:-74:764744:100000");
        state.hold(180.0f, "lineUp");
        assertEquals(
                0f,
                state.admitReady("@75", "@75:-75:764745:100000", true, 8L),
                "foreign READY rows cannot become the visible owner"
        );
        assertEquals(
                0f,
                state.admitReady("@74", "@74:-74:764744:100000", false, 8L),
                "a non-active gesture cannot replay cached pixels"
        );
    }

    private static void noFeedbackReleaseCannotFreezeRealtime() {
        assertFalse(
                retainReadHoldAfterRelease(true, false, false),
                "zero-feedback release must fail safe back to realtime"
        );
        assertTrue(
                retainReadHoldAfterRelease(true, true, false),
                "a visibly moved history gesture must retain read hold"
        );
        assertTrue(
                retainReadHoldAfterRelease(true, false, true),
                "an exact cache-owned paint may retain read hold"
        );
    }

    private static void productionWiresConsumptionAwareRecovery(String source) {
        for (String required : new String[] {
                "captureRendererBlockedFingerDeltaPx",
                "captureRendererBlockedFingerWhere",
                "holdCaptureRendererBlockedFingerDelta",
                "replayCaptureRendererBlockedFingerDelta",
                "exactReadyHistoryOwnerMatchesActiveTouch",
                "result=held-for-ready-owner",
                "result=replayed-ready-owner",
                "result=no-visible-owner-release"
        }) {
            assertTrue(source.contains(required), "production seam recovery missing " + required);
        }
        String apply = methodBody(source, "private boolean applyNativeHistoryScrollPixels(");
        assertTrue(
                apply.contains("holdCaptureRendererBlockedFingerDelta(deltaY, where)"),
                "blocked sink must preserve, not discard, physical finger distance"
        );
        String ready = methodBody(
                source,
                "private void stageReadyLocalHistoryBatchForRenderer(\n"
                        + "            LocalHistoryRequest request,\n"
                        + "            LocalHistoryChunk chunk,\n"
                        + "            String source,\n"
                        + "            ReadyHistoryStageCallback callback"
        );
        assertTrue(
                ready.contains("exactReadyHistoryOwnerMatchesActiveTouch(request, chunk)")
                        && ready.contains("replayCaptureRendererBlockedFingerDelta("),
                "only exact-bound READY rows may replay the held distance"
        );
        String release = methodBody(source, "private boolean handleTerminalTouch(");
        assertTrue(
                release.contains("releaseReadHoldAfterNoVisibleScrollOwner("),
                "ACTION_UP must fail safe when no visible scroll owner ever painted"
        );
        assertFalse(
                source.contains("LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED = true"),
                "fix must not enable the divergent second renderer"
        );
    }

    private static boolean retainReadHoldAfterRelease(
            boolean consumed,
            boolean visibleFeedback,
            boolean exactCachePaint
    ) {
        return consumed && (visibleFeedback || exactCachePaint);
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

    private static final class RecoveryState {
        private final String windowId;
        private final String generationKey;
        private float heldPx;
        private String where = "";
        private long firstFeedbackElapsedMs = Long.MAX_VALUE;

        RecoveryState(String windowId, String generationKey) {
            this.windowId = windowId;
            this.generationKey = generationKey;
        }

        void hold(float deltaPx, String direction) {
            if (!where.isEmpty() && !where.equals(direction)) {
                heldPx = 0f;
            }
            where = direction;
            heldPx += deltaPx;
        }

        float admitReady(
                String readyWindowId,
                String readyGenerationKey,
                boolean activeGesture,
                long elapsedMs
        ) {
            if (!activeGesture
                    || !windowId.equals(readyWindowId)
                    || !generationKey.equals(readyGenerationKey)) {
                return 0f;
            }
            float replay = heldPx;
            heldPx = 0f;
            firstFeedbackElapsedMs = elapsedMs;
            return replay;
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

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
