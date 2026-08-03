package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Architecture old-red for the one-renderer terminal snapshot owner.
 *
 * <p>The pure model locks the behavior independently of Android/WebView. Source
 * checks intentionally stay red until production removes the v229 gesture-time
 * cache materialization and wires the same prepared snapshot into scroll and
 * Active switching.
 */
public final class TerminalSnapshotLeaseContractTest {
    private static final String WINDOW_74 = "@74";
    private static final String GENERATION_74 = "@74:-74:764744:100000";
    private static final String WINDOW_4 = "@4";
    private static final String GENERATION_4 = "@4:-4:active-prewarm:1086";

    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 2, "expected MainActivity.java and v225 physical log paths");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );
        String knownGoodLog = new String(
                Files.readAllBytes(Paths.get(args[1])),
                StandardCharsets.UTF_8
        );

        v225PhysicalReceiptProvesTheSingleRendererHotPath(knownGoodLog);
        activeSwitchAtomicallyUsesPreparedExactSnapshot();
        scrollMovesTheSameSnapshotAndUpdatesItsRealAnchor();
        readyReplayIsBoundToTheCapturedImmutableLeaseSerial();
        onlyTheLatestFullRemoteFrameReconcilesOnIntentionalRelease();
        everyOwnershipLossAbortsAndUnblocksRealtime();
        bottomUsesOneCentralEndTransition();
        productionWiresTheUnifiedSnapshotArchitecture(source);
        System.out.println("TERMINAL_SNAPSHOT_LEASE_PASS");
    }

    private static void v225PhysicalReceiptProvesTheSingleRendererHotPath(String log) {
        assertTrue(
                log.contains("actionId=scroll-49533716-11 action=down")
                        && log.contains("actionId=scroll-49533716-11 action=history-start"),
                "known-good fixture must retain the physical v225 drag"
        );
        assertTrue(
                log.contains("actionId=scroll-49533716-11")
                        && log.contains("source=touch-scroll-visual-nudge")
                        && log.contains("firstLocalFeedbackElapsedMs=17"),
                "known-good v225 must retain its 17 ms local feedback"
        );
        assertTrue(
                log.contains("stage=touch-nudge endpoint=renderer"
                        + " actionId=scroll-49533716-11")
                        && log.contains("result=APPLIED appliedPx=")
                        && log.contains("needsReady=false")
                        && log.contains("nextStart=13291"),
                "known-good movement must be the accepted renderer nudge over prepared rows"
        );
    }

    private static void activeSwitchAtomicallyUsesPreparedExactSnapshot() {
        TerminalSnapshotOwnerModel model = populatedModel();
        model.show(WINDOW_74, GENERATION_74);

        SwapResult result = model.switchTo(WINDOW_4, GENERATION_4);

        assertTrue(result.usedPreparedSnapshot,
                "Active must use the already prepared exact target snapshot");
        assertTrue(result.feedbackElapsedMs >= 0 && result.feedbackElapsedMs <= 30,
                "Active cached pixels missed the 30 ms local feedback gate");
        assertEquals(WINDOW_4, model.visibleWindowId,
                "Active snapshot swap painted the wrong window");
        assertEquals(GENERATION_4, model.visibleGenerationKey,
                "Active snapshot swap painted the wrong generation");
        assertEquals(0, model.networkCallsOnCriticalPath,
                "Active cached feedback cannot await /select-live or /terminal-frame");
        assertTrue(model.visibleRows().contains("@4-ready-row-1085"),
                "Active swap must publish readable target pixels, not a spinner");
        assertProtectedSurfaces(model);
    }

    private static void scrollMovesTheSameSnapshotAndUpdatesItsRealAnchor() {
        TerminalSnapshotOwnerModel model = populatedModel();
        model.show(WINDOW_74, GENERATION_74);
        Lease lease = model.acquireLease("scroll-unified-snapshot");

        String initialAnchor = lease.anchorKey();
        MoveResult first = model.nudge(37.5f);
        MoveResult second = model.nudge(22.0f);

        assertTrue(first.appliedPx > 0f && second.appliedPx > 0f,
                "prepared snapshot must visibly move on every accepted nudge");
        assertTrue(first.feedbackElapsedMs <= 30 && second.feedbackElapsedMs <= 30,
                "snapshot nudge missed the 30 ms local feedback gate");
        assertTrue(lease.viewportOffsetPx > 0f,
                "lease viewport offset must track real applied renderer movement");
        assertFalse(initialAnchor.equals(lease.anchorKey()),
                "anchorKey cannot remain decorative after renderer movement");
        assertEquals(lease.anchorKey(), model.visibleAnchorKey,
                "visible anchor must be the lease's enforced renderer anchor");
        assertEquals(0, model.networkCallsOnCriticalPath,
                "ACTION_DOWN/MOVE cannot fetch or await transport");
        assertEquals(3, model.constantTimeGestureOperations,
                "gesture path must only acquire and nudge the existing snapshot");
        assertProtectedSurfaces(model);
    }

    private static void readyReplayIsBoundToTheCapturedImmutableLeaseSerial() {
        TerminalSnapshotOwnerModel model = populatedModel();
        model.show(WINDOW_74, GENERATION_74);
        Lease first = model.acquireLease("scroll-ready-serial-1");
        ReadyReplay firstReplay = model.scheduleReadyReplay(first.serial);

        model.endLease("gesture-cancel", EndMode.ABORT);
        Lease second = model.acquireLease("scroll-ready-serial-2");

        assertFalse(model.completeReadyReplay(firstReplay),
                "an old READY callback cannot replay into a replacement lease");
        ReadyReplay secondReplay = model.scheduleReadyReplay(second.serial);
        assertTrue(model.completeReadyReplay(secondReplay),
                "the current immutable lease serial must admit one READY replay");
        assertFalse(model.completeReadyReplay(secondReplay),
                "duplicate READY completion cannot replay twice");
        assertEquals(1, model.readyReplayCount,
                "only the current lease serial may consume the held finger delta");
    }

    private static void onlyTheLatestFullRemoteFrameReconcilesOnIntentionalRelease() {
        TerminalSnapshotOwnerModel model = populatedModel();
        model.show(WINDOW_74, GENERATION_74);
        Lease lease = model.acquireLease("scroll-latest-frame");
        model.nudge(18f);

        RemoteFrame older = frame(lease, 41, "remote-old");
        RemoteFrame latest = frame(lease, 42, "remote-latest");
        assertTrue(model.queueRemoteFrame(older), "first exact remote frame must queue");
        assertTrue(model.queueRemoteFrame(latest), "newer exact remote frame must replace it");
        assertFalse(
                model.queueRemoteFrame(new RemoteFrame(
                        WINDOW_4,
                        GENERATION_4,
                        99,
                        "foreign",
                        "foreign-rendered",
                        Collections.singletonList("<span>foreign</span>"),
                        Collections.singletonList("foreign-key"),
                        "132x54"
                )),
                "foreign target/generation cannot replace the queued frame"
        );
        assertEquals(42L, model.latestQueuedFrame.sequence,
                "queue must retain only the latest valid frame");
        assertEquals("remote-latest", model.latestQueuedFrame.plainRows,
                "queue must retain full frame bytes, not metadata only");

        MoveResult stillLocal = model.nudge(15f);
        assertTrue(stillLocal.appliedPx > 0f,
                "remote arrival cannot pause direct local movement");
        model.endLease("explicit-bottom", EndMode.RECONCILE);

        assertFalse(model.hasLease(), "Bottom must end snapshot ownership");
        assertTrue(model.latestQueuedFrame == null,
                "successful reconcile must consume the queued frame");
        assertEquals("remote-latest", model.appliedRemoteFrame.plainRows,
                "intentional release must actually apply the latest full frame");
        assertEquals(1, model.remoteFrameApplyCount,
                "one end transition may reconcile exactly one frame");
        assertTrue(model.realtimeFrameAcceptedAfterEnd(),
                "realtime commits must resume after intentional release");
    }

    private static void everyOwnershipLossAbortsAndUnblocksRealtime() {
        for (String reason : new String[] {
                "gesture-cancel",
                "viewer-multi-touch-handoff",
                "exact-close",
                "exact-target-invalidation",
                "exact-target-replacement",
                "page-loss"
        }) {
            TerminalSnapshotOwnerModel model = populatedModel();
            model.show(WINDOW_74, GENERATION_74);
            Lease lease = model.acquireLease("abort-" + reason);
            model.nudge(9f);
            model.queueRemoteFrame(frame(lease, 11, "hidden-" + reason));

            model.endLease(reason, EndMode.ABORT);

            assertFalse(model.hasLease(), reason + " leaked the immutable lease");
            assertTrue(model.latestQueuedFrame == null,
                    reason + " leaked a hidden queued frame");
            assertEquals(0, model.remoteFrameApplyCount,
                    reason + " must abort rather than reveal a stale frame");
            assertTrue(model.realtimeFrameAcceptedAfterEnd(),
                    reason + " must admit the next same-window realtime frame");
            assertProtectedSurfaces(model);
        }
    }

    private static void bottomUsesOneCentralEndTransition() {
        TerminalSnapshotOwnerModel model = populatedModel();
        model.show(WINDOW_74, GENERATION_74);
        Lease lease = model.acquireLease("bottom-once");
        model.queueRemoteFrame(frame(lease, 71, "bottom-latest"));

        model.bottom();

        assertEquals(1, model.endCalls,
                "Bottom must not release through restore + live-input + clear three times");
        assertEquals(1, model.remoteFrameApplyCount,
                "Bottom must reconcile the latest frame exactly once");
        assertFalse(model.hasLease(), "Bottom must leave no cached read hold");
    }

    private static TerminalSnapshotOwnerModel populatedModel() {
        TerminalSnapshotOwnerModel model = new TerminalSnapshotOwnerModel();
        model.prepareOffMainThread(new TerminalSnapshot(
                WINDOW_74,
                GENERATION_74,
                rows("@74-ready-row-", 4000),
                "132x54",
                "capture:@74:3904"
        ));
        model.prepareOffMainThread(new TerminalSnapshot(
                WINDOW_4,
                GENERATION_4,
                rows("@4-ready-row-", 1086),
                "132x54",
                "capture:@4:990"
        ));
        return model;
    }

    private static RemoteFrame frame(Lease lease, long sequence, String content) {
        return new RemoteFrame(
                lease.windowId,
                lease.generationKey,
                sequence,
                content,
                "<span>" + content + "</span>",
                Collections.singletonList("<span>" + content + "</span>"),
                Collections.singletonList("frame:" + sequence),
                "132x54"
        );
    }

    private static List<String> rows(String prefix, int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            values.add(prefix + index);
        }
        return values;
    }

    private static void productionWiresTheUnifiedSnapshotArchitecture(String source) {
        assertTrue(
                source.contains("LOCAL_HISTORY_INLINE_GESTURE_PAINT_ENABLED = false"),
                "the divergent inline renderer must remain disabled"
        );
        for (String removedV229Mechanism : new String[] {
                "CachedHistoryGestureLease",
                "exactCachedHistoryChunksForGestureLease(",
                "cached-history-lease-materialize",
                "nudgeCachedHistoryGesture(",
                "pendingRemoteFrameHash",
                "remote-frame-reconciled-hidden"
        }) {
            assertFalse(
                    source.contains(removedV229Mechanism),
                    "v229 gesture-time materialization/metadata lease remains "
                            + removedV229Mechanism
            );
        }

        for (String requiredOwner : new String[] {
                "TerminalSnapshot",
                "TerminalSnapshotLease",
                "prepareTerminalSnapshotOffMainThread(",
                "beginTerminalSnapshotLease(",
                "endTerminalSnapshotLease(",
                "terminalSnapshotLeaseMatchesSerial(",
                "paintTerminalSnapshotFromCache",
                "queueLatestTerminalSnapshotFrame",
                "latestQueuedFrame",
                "terminalSnapshotLeaseSerial",
                "result=terminal-snapshot-swapped",
                "snapshotSwapElapsedMs"
        }) {
            assertTrue(source.contains(requiredOwner),
                    "unified terminal snapshot owner missing " + requiredOwner);
        }

        String prepare = methodBodyContaining(
                source,
                "private void prepareTerminalSnapshotOffMainThread("
        );
        assertTrue(
                prepare.contains("new Thread(") || prepare.contains(".execute("),
                "snapshot serialization/materialization must run off the UI thread"
        );
        assertTrue(
                prepare.contains("uiHandler.post("),
                "only the immutable prepared result may publish back to WebView"
        );

        String begin = methodBodyContaining(
                source,
                "private boolean beginTerminalSnapshotLease("
        );
        for (String forbiddenGestureWork : new String[] {
                "localHistoryChunkCache.values()",
                "exactCachedHistoryChunksForGestureLease",
                "new JSONArray(",
                ".sort(",
                "stageReadyLocalHistoryBatchForRenderer(",
                "/terminal-frame",
                "/scrollback/chunk"
        }) {
            assertFalse(
                    begin.contains(forbiddenGestureWork),
                    "ACTION_DOWN must be O(1); found " + forbiddenGestureWork
            );
        }
        assertTrue(
                begin.contains("terminalSnapshotLeaseSerial")
                        && begin.contains("generationKey")
                        && begin.contains("windowId"),
                "lease must bind immutable serial plus exact window/generation"
        );

        String nudge = methodBodyContaining(
                source,
                "private void nudgeCaptureRendererForTouch("
        );
        assertTrue(
                nudge.contains("nudgeTouchScroll"),
                "scroll must retain the accepted capture renderer's local nudge"
        );
        assertTrue(
                nudge.contains("anchorKey")
                        && nudge.contains("viewportOffsetPx")
                        && nudge.contains("terminalSnapshotLeaseMatchesSerial("),
                "renderer truth must update and enforce the lease's real anchor/offset"
        );
        for (String forbiddenNudgeTransport : new String[] {
                "/terminal-frame",
                "/scrollback/chunk",
                "getJson(",
                "stageReadyLocalHistoryBatchForRenderer("
        }) {
            assertFalse(
                    nudge.contains(forbiddenNudgeTransport),
                    "MOVE cannot depend on " + forbiddenNudgeTransport
            );
        }

        String stageReady = methodBodyContaining(
                source,
                "private void stageReadyLocalHistoryBatchForRenderer("
        );
        assertTrue(
                stageReady.contains("prepareTerminalSnapshotOffMainThread("),
                "READY/cache prewarm must prepare the shared snapshot off-main"
        );
        for (String forbiddenUiSerialization : new String[] {
                "new JSONArray(renderRows.toString())",
                "new JSONArray(batchRowsHtml.toString())",
                "new JSONArray(batchRowKeys.toString())"
        }) {
            assertFalse(
                    stageReady.contains(forbiddenUiSerialization),
                    "READY staging still serializes arrays on the UI thread: "
                            + forbiddenUiSerialization
            );
        }
        assertTrue(
                stageReady.contains("expectedLeaseSerial")
                        && stageReady.contains("terminalSnapshotLeaseMatchesSerial("),
                "READY replay must be bound to the serial captured when staging began"
        );
        assertFalse(
                stageReady.contains("localCachedHistoryOwnsGesture()"),
                "READY replay cannot depend on mutable visible-owner metadata"
        );

        String framePrewarm = methodBodyContaining(
                source,
                "private void prewarmListedSessionFramesForSwitch("
        );
        assertTrue(
                framePrewarm.contains("prepareTerminalSnapshotOffMainThread("),
                "Active prewarm must retain readable per-window snapshots, not discard payloads"
        );

        String touch = methodBodyContaining(source, "private boolean handleTerminalTouch(");
        String pointerAbort =
                "endTerminalSnapshotLease(\"viewer-multi-touch-handoff\", TerminalSnapshotEndMode.ABORT)";
        String cancelAbort =
                "endTerminalSnapshotLease(\"gesture-cancel\", TerminalSnapshotEndMode.ABORT)";
        assertTrue(touch.contains(pointerAbort),
                "one-to-two-finger handoff must abort snapshot ownership");
        assertTrue(
                touch.indexOf(pointerAbort) < touch.indexOf("terminalMultiTouchGesture = true;"),
                "snapshot ownership must end before WebView receives pinch ownership"
        );
        assertTrue(touch.contains(cancelAbort),
                "ACTION_CANCEL must abort even without terminalHistoryDragActive");

        String targetUpdate = methodBodyContaining(
                source,
                "private boolean updateCaptureRendererWindowTargetKey("
        );
        assertTrue(
                targetUpdate.contains(
                        "endTerminalSnapshotLease(\"exact-target-replacement\","
                                + " TerminalSnapshotEndMode.ABORT)"
                ),
                "target replacement must invalidate the old exact snapshot"
        );
        assertTrue(
                targetUpdate.contains(
                        "endTerminalSnapshotLease(\"exact-target-invalidation\","
                                + " TerminalSnapshotEndMode.ABORT)"
                ),
                "empty/invalid target publication must abort the old exact snapshot"
        );

        String pageStarted = methodBodyContaining(source, "public void onPageStarted(");
        assertTrue(
                pageStarted.contains(
                        "endTerminalSnapshotLease(\"page-loss\","
                                + " TerminalSnapshotEndMode.ABORT)"
                ),
                "fresh JavaScript page ownership must abort the old lease"
        );

        String close = methodBodyContaining(
                source,
                "private void dispatchActiveRowCloseTarget("
        );
        assertTrue(
                close.contains(
                        "endTerminalSnapshotLease(\"exact-close\","
                                + " TerminalSnapshotEndMode.ABORT)"
                ),
                "exact Close must invalidate a lease for the closed target"
        );

        String endLease = methodBodyContaining(
                source,
                "private boolean endTerminalSnapshotLease("
        );
        assertTrue(
                endLease.contains("applyLatestQueuedTerminalSnapshotFrame("),
                "intentional release must apply the newest queued full frame"
        );
        assertTrue(
                endLease.contains("TerminalSnapshotEndMode.RECONCILE")
                        && endLease.contains("TerminalSnapshotEndMode.ABORT"),
                "one end owner must distinguish reconcile from abort"
        );

        String bottom = methodBodyContaining(source, "private void restoreLiveForViewing(");
        assertEquals(
                1,
                count(bottom, "\"explicit-bottom\""),
                "Bottom must name one explicit reconcile transition"
        );
        assertTrue(
                bottom.contains("leaveReadModeForLiveInput(false, \"explicit-bottom\")"),
                "Bottom must route through one typed live-mode transition"
        );

        String liveInput = methodBodyContaining(
                source,
                "private long leaveReadModeForLiveInput("
                        + "boolean pinAfterOverlay, String terminalSnapshotReason)"
        );
        assertTrue(
                liveInput.contains("endTerminalSnapshotLease(")
                        && liveInput.contains("TerminalSnapshotEndMode.RECONCILE"),
                "deliberate live input must reconcile the newest valid frame"
        );
        assertEquals(
                1,
                count(liveInput, "endTerminalSnapshotLease("),
                "typed live-mode transition must end the snapshot exactly once"
        );
        String clearLocalHistory = methodBodyContaining(
                source,
                "private void clearLocalHistoryTouchViewportForLiveBottom("
        );
        assertFalse(
                clearLocalHistory.contains("endTerminalSnapshotLease("),
                "local-history cleanup cannot perform a second Bottom release"
        );

        assertTrue(
                source.contains(
                        "originalSetWindowId.apply(this,arguments);"
                                + "paintTerminalSnapshotFromCache(value)"
                ),
                "Active must swap the cached target through the existing renderer after target reset"
        );

        for (String fullFrameField : new String[] {
                "plainRows",
                "rendered",
                "htmlRows",
                "rowKeys",
                "frameGeometry"
        }) {
            assertTrue(
                    source.contains("latestQueuedFrame") && source.contains(fullFrameField),
                    "queued renderer frame must retain full bytes/geometry: " + fullFrameField
            );
        }

        for (String protectedSurface : Arrays.asList(
                "forwardTouchToViewer(event)",
                "validateAndDispatchActiveRowClose(",
                "showDockedPromptComposer(",
                "uploadMediaUrisSequentially(",
                "toolbarNavigationButton(",
                "scheduleCaptureRendererIdleRealtimeRefresh("
        )) {
            assertTrue(
                    source.contains(protectedSurface),
                    "impacted protected surface disappeared: " + protectedSurface
            );
        }
    }

    private static void assertProtectedSurfaces(TerminalSnapshotOwnerModel model) {
        assertTrue(model.pinchPreserved, "pinch/viewer ownership regressed");
        assertTrue(model.activePreserved, "Active switching regressed");
        assertTrue(model.closePreserved, "exact-ID Close regressed");
        assertTrue(model.bottomPreserved, "Bottom regressed");
        assertTrue(model.sendImeUploadPreserved, "Send/IME/upload regressed");
        assertTrue(model.toolbarPreserved, "toolbar regressed");
        assertTrue(model.realtimePreserved, "realtime recovery regressed");
    }

    private enum EndMode {
        ABORT,
        RECONCILE
    }

    private static final class TerminalSnapshot {
        final String windowId;
        final String generationKey;
        final List<String> rows;
        final String geometryKey;
        final String initialAnchorKey;

        TerminalSnapshot(
                String windowId,
                String generationKey,
                List<String> rows,
                String geometryKey,
                String initialAnchorKey
        ) {
            this.windowId = windowId;
            this.generationKey = generationKey;
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            this.geometryKey = geometryKey;
            this.initialAnchorKey = initialAnchorKey;
        }

        String key() {
            return windowId + "|" + generationKey;
        }
    }

    private static final class Lease {
        final long serial;
        final String actionId;
        final String windowId;
        final String generationKey;
        final String initialAnchorKey;
        float viewportOffsetPx;

        Lease(long serial, String actionId, TerminalSnapshot snapshot) {
            this.serial = serial;
            this.actionId = actionId;
            this.windowId = snapshot.windowId;
            this.generationKey = snapshot.generationKey;
            this.initialAnchorKey = snapshot.initialAnchorKey;
        }

        String anchorKey() {
            return initialAnchorKey + "|offsetPx=" + Math.round(viewportOffsetPx * 10f) / 10f;
        }
    }

    private static final class RemoteFrame {
        final String windowId;
        final String generationKey;
        final long sequence;
        final String plainRows;
        final String rendered;
        final List<String> htmlRows;
        final List<String> rowKeys;
        final String frameGeometry;

        RemoteFrame(
                String windowId,
                String generationKey,
                long sequence,
                String plainRows,
                String rendered,
                List<String> htmlRows,
                List<String> rowKeys,
                String frameGeometry
        ) {
            this.windowId = windowId;
            this.generationKey = generationKey;
            this.sequence = sequence;
            this.plainRows = plainRows;
            this.rendered = rendered;
            this.htmlRows = Collections.unmodifiableList(new ArrayList<>(htmlRows));
            this.rowKeys = Collections.unmodifiableList(new ArrayList<>(rowKeys));
            this.frameGeometry = frameGeometry;
        }
    }

    private static final class ReadyReplay {
        final long capturedLeaseSerial;
        boolean consumed;

        ReadyReplay(long capturedLeaseSerial) {
            this.capturedLeaseSerial = capturedLeaseSerial;
        }
    }

    private static final class MoveResult {
        final float appliedPx;
        final int feedbackElapsedMs;

        MoveResult(float appliedPx, int feedbackElapsedMs) {
            this.appliedPx = appliedPx;
            this.feedbackElapsedMs = feedbackElapsedMs;
        }
    }

    private static final class SwapResult {
        final boolean usedPreparedSnapshot;
        final int feedbackElapsedMs;

        SwapResult(boolean usedPreparedSnapshot, int feedbackElapsedMs) {
            this.usedPreparedSnapshot = usedPreparedSnapshot;
            this.feedbackElapsedMs = feedbackElapsedMs;
        }
    }

    private static final class TerminalSnapshotOwnerModel {
        final Map<String, TerminalSnapshot> prepared = new LinkedHashMap<>();
        final boolean pinchPreserved = true;
        final boolean activePreserved = true;
        final boolean closePreserved = true;
        final boolean bottomPreserved = true;
        final boolean sendImeUploadPreserved = true;
        final boolean toolbarPreserved = true;
        final boolean realtimePreserved = true;

        String visibleWindowId = "";
        String visibleGenerationKey = "";
        String visibleAnchorKey = "";
        Lease lease;
        RemoteFrame latestQueuedFrame;
        RemoteFrame appliedRemoteFrame;
        long leaseSerial;
        int networkCallsOnCriticalPath;
        int constantTimeGestureOperations;
        int readyReplayCount;
        int remoteFrameApplyCount;
        int endCalls;
        boolean readHold;

        void prepareOffMainThread(TerminalSnapshot snapshot) {
            assertTrue(snapshot != null, "snapshot required");
            assertTrue(!snapshot.windowId.isEmpty() && !snapshot.generationKey.isEmpty(),
                    "prepared snapshot needs exact identity");
            assertTrue(!snapshot.rows.isEmpty(), "prepared snapshot needs readable rows");
            prepared.put(snapshot.key(), snapshot);
        }

        void show(String windowId, String generationKey) {
            TerminalSnapshot snapshot = snapshot(windowId, generationKey);
            visibleWindowId = snapshot.windowId;
            visibleGenerationKey = snapshot.generationKey;
            visibleAnchorKey = snapshot.initialAnchorKey + "|offsetPx=0.0";
        }

        SwapResult switchTo(String windowId, String generationKey) {
            if (hasLease()) {
                endLease("exact-target-replacement", EndMode.ABORT);
            }
            show(windowId, generationKey);
            return new SwapResult(true, 7);
        }

        Lease acquireLease(String actionId) {
            TerminalSnapshot snapshot = snapshot(visibleWindowId, visibleGenerationKey);
            lease = new Lease(++leaseSerial, actionId, snapshot);
            readHold = true;
            latestQueuedFrame = null;
            constantTimeGestureOperations++;
            visibleAnchorKey = lease.anchorKey();
            return lease;
        }

        MoveResult nudge(float deltaPx) {
            assertTrue(lease != null, "nudge requires a current lease");
            float applied = Math.max(0f, deltaPx);
            lease.viewportOffsetPx += applied;
            visibleAnchorKey = lease.anchorKey();
            constantTimeGestureOperations++;
            return new MoveResult(applied, applied > 0f ? 8 : -1);
        }

        ReadyReplay scheduleReadyReplay(long capturedSerial) {
            return new ReadyReplay(capturedSerial);
        }

        boolean completeReadyReplay(ReadyReplay replay) {
            if (replay == null
                    || replay.consumed
                    || lease == null
                    || lease.serial != replay.capturedLeaseSerial) {
                return false;
            }
            replay.consumed = true;
            readyReplayCount++;
            return true;
        }

        boolean queueRemoteFrame(RemoteFrame frame) {
            if (lease == null
                    || frame == null
                    || !lease.windowId.equals(frame.windowId)
                    || !lease.generationKey.equals(frame.generationKey)
                    || frame.plainRows.isEmpty()
                    || frame.htmlRows.isEmpty()
                    || frame.rowKeys.isEmpty()
                    || frame.frameGeometry.isEmpty()
                    || (latestQueuedFrame != null
                    && frame.sequence <= latestQueuedFrame.sequence)) {
                return false;
            }
            latestQueuedFrame = frame;
            return true;
        }

        void endLease(String reason, EndMode mode) {
            assertTrue(lease != null, "end requires a current lease");
            assertTypedEndReason(reason, mode);
            endCalls++;
            if (mode == EndMode.RECONCILE && latestQueuedFrame != null) {
                appliedRemoteFrame = latestQueuedFrame;
                remoteFrameApplyCount++;
            }
            latestQueuedFrame = null;
            lease = null;
            readHold = false;
        }

        void bottom() {
            endLease("explicit-bottom", EndMode.RECONCILE);
        }

        boolean hasLease() {
            return lease != null;
        }

        boolean realtimeFrameAcceptedAfterEnd() {
            return lease == null && !readHold;
        }

        List<String> visibleRows() {
            return snapshot(visibleWindowId, visibleGenerationKey).rows;
        }

        TerminalSnapshot snapshot(String windowId, String generationKey) {
            TerminalSnapshot snapshot = prepared.get(windowId + "|" + generationKey);
            assertTrue(snapshot != null, "missing exact prepared snapshot "
                    + windowId + "|" + generationKey);
            return snapshot;
        }

        void assertTypedEndReason(String reason, EndMode mode) {
            List<String> abortReasons = Arrays.asList(
                    "gesture-cancel",
                    "viewer-multi-touch-handoff",
                    "exact-close",
                    "exact-target-invalidation",
                    "exact-target-replacement",
                    "page-loss"
            );
            List<String> reconcileReasons = Arrays.asList(
                    "explicit-bottom",
                    "user-live-follow"
            );
            assertTrue(
                    mode == EndMode.ABORT
                            ? abortReasons.contains(reason)
                            : reconcileReasons.contains(reason),
                    "end mode/reason mismatch " + mode + "/" + reason
            );
        }
    }

    private static int count(String text, String token) {
        int count = 0;
        int from = 0;
        while (true) {
            int found = text.indexOf(token, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + token.length();
        }
    }

    private static String methodBodyContaining(String source, String token) {
        int tokenAt = source.indexOf(token);
        assertTrue(tokenAt >= 0, "missing source method containing " + token);
        int brace = source.indexOf('{', tokenAt);
        assertTrue(brace >= 0, "unbounded source method containing " + token);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = brace; index < source.length(); index++) {
            char value = source.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    inString = false;
                }
                continue;
            }
            if (value == '"') {
                inString = true;
            } else if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace + 1, index);
                }
            }
        }
        throw new AssertionError("unterminated source method containing " + token);
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

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
