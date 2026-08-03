package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic production-decision guard for one normal and three rapid Sends.
 *
 * <p>The fixture supplies transport, draft, renderer, and fake-time ports only.
 * Admission, drain, callback ownership, completion/failure ordering, and draft
 * clearing execute {@link MainActivity.PromptComposerRapidOrderedSendBehavior},
 * whose same decisions are called by the live MainActivity Send owner.
 */
public final class PromptComposerRapidOrderedSendBehaviorTest {
    private static final String TARGET = "@17";
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );

    public static void main(String[] args) throws Exception {
        List<String> diagnosticLedger = new ArrayList<>();
        AssertionError firstFailure = null;
        firstFailure = runCase("normal", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::oneNormalSend);
        firstFailure = runCase("rapid-fifo", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::threeRapidHeldCallbackSends);
        firstFailure = runCase("rapid-a-b-a-exact-once", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::rapidAtoBtoACommitsEachIntentOnce);
        firstFailure = runCase("intent-identity", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::identicalBodiesRemainDistinctIntents);
        firstFailure = runCase("failure-draft", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::interleavedFailureDraftSurvivesLaterSuccess);
        firstFailure = runCase("ledger-recreation", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::failedMiddleIntentSurvivesRecreation);
        firstFailure = runCase("ledger-success-atomic-crash", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::successCommitHasNoSplitCrashState);
        firstFailure = runCase("production-wiring", diagnosticLedger, firstFailure,
                PromptComposerRapidOrderedSendBehaviorTest::productionUsesTheExecutedDecisions);

        if (firstFailure != null) {
            System.err.println("PROMPT_COMPOSER_RAPID_ORDERED_SEND_BEHAVIOR_RED"
                    + " firstInvariant=" + safeFailure(firstFailure.getMessage()));
            for (String event : diagnosticLedger) {
                System.err.println(event);
            }
            throw firstFailure;
        }
        System.out.println("PROMPT_COMPOSER_RAPID_ORDERED_SEND_BEHAVIOR_PASS");
    }

    private static AssertionError runCase(
            String caseName,
            List<String> diagnosticLedger,
            AssertionError firstFailure,
            CheckedCase checkedCase
    ) throws Exception {
        try {
            checkedCase.run();
            diagnosticLedger.add("CASE case=" + caseName + " disposition=pass");
        } catch (AssertionError failure) {
            diagnosticLedger.add("CASE case=" + caseName
                    + " disposition=violation invariant=" + safeFailure(failure.getMessage()));
            if (firstFailure == null) {
                return failure;
            }
        }
        return firstFailure;
    }

    private static void oneNormalSend() {
        FakePort port = new FakePort("normal");
        QueueScenario scenario = new QueueScenario(port);
        TestIntent send = intent("I0", "T0", "111111111111", "S0");

        port.compose(send);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.ADMITTED,
                scenario.admit(send),
                "normal intent admitted"
        );
        assertEquals("", port.visibleDraft(), "normal local visible acknowledgement");
        assertEquals("S0", port.persistedDraft(TARGET),
                "normal exact admitted draft remains durable until server truth");
        assertEquals(list("I0"), port.requestStarts, "normal one request start");
        assertEquals(0, port.closeCalls, "normal no Close");
        assertEquals(0, port.reselectCalls, "normal no reselection");

        scenario.heartbeat();
        port.respondSuccess(scenario, send);
        scenario.heartbeat();
        port.respondSuccess(scenario, send);

        assertEquals(list("I0"), port.serverAccepts, "normal exact server acceptance");
        assertEquals(list("I0"), port.finalSuccesses, "normal exact final success");
        assertEquals(1, port.markerCount("I0"), "normal exact terminal occurrence");
        assertEquals(0, scenario.queueSize(), "normal queue empty");
        assertFalse(scenario.inFlight(), "normal in-flight empty");
        assertEquals("", port.visibleDraft(), "normal visible draft empty after success");
        assertEquals("", port.persistedDraft(TARGET), "normal persisted draft empty after success");
        assertEquals(1, port.rendererCommitOpportunities, "normal renderer commit opportunity");
        assertRendererAndOwnershipProtected(port, 2, "normal");
    }

    private static void threeRapidHeldCallbackSends() {
        FakePort port = new FakePort("rapid-fifo");
        QueueScenario scenario = new QueueScenario(port);
        TestIntent r1 = intent("R1", "T1", "211111111111", "R1-body");
        TestIntent r2 = intent("R2", "T2", "222222222222", "R2-body");
        TestIntent r3 = intent("R3", "T3", "233333333333", "R3-body");

        port.compose(r1);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.ADMITTED,
                scenario.admit(r1),
                "rapid R1 admitted"
        );
        scenario.heartbeat();
        port.compose(r2);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.QUEUED_BEHIND_IN_FLIGHT,
                scenario.admit(r2),
                "rapid R2 queued"
        );
        scenario.heartbeat();
        port.compose(r3);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.QUEUED_BEHIND_IN_FLIGHT,
                scenario.admit(r3),
                "rapid R3 queued"
        );
        scenario.heartbeat();

        assertEquals(3, scenario.queueSize(), "rapid queue retains all held intents");
        assertEquals(list("R1"), port.requestStarts, "only R1 starts before held response");
        assertEquals(3, port.localFeedbackCount, "each rapid tap receives local acknowledgement");
        assertEquals(3, port.nextTapReadyCount, "each rapid tap leaves next-tap readiness");
        assertDistinctRefs(r1, r2, r3);

        port.respondSuccess(scenario, r1);
        scenario.heartbeat();
        port.respondSuccess(scenario, r2);
        scenario.heartbeat();
        port.respondSuccess(scenario, r3);
        scenario.heartbeat();

        List<String> order = list("R1", "R2", "R3");
        assertEquals(order, port.requestStarts, "rapid FIFO request starts");
        assertEquals(order, port.serverAccepts, "rapid FIFO server acceptance");
        assertEquals(order, port.finalSuccesses, "rapid FIFO final success");
        for (String intentId : order) {
            assertEquals(1, port.markerCount(intentId),
                    "rapid exact terminal occurrence " + intentId);
        }
        assertEquals(0, scenario.queueSize(), "rapid queue empty");
        assertFalse(scenario.inFlight(), "rapid in-flight empty");
        assertRendererAndOwnershipProtected(port, 6, "rapid");
    }

    private static void rapidAtoBtoACommitsEachIntentOnce() {
        FakePort port = new FakePort("rapid-a-b-a-exact-once");
        QueueScenario scenario = new QueueScenario(port);
        TestIntent a1 = intentAt("@17", "ABA1", "TABA1", "711111111111", "A-first");
        TestIntent b = intentAt("@18", "ABB", "TABB", "722222222222", "B-middle");
        TestIntent a2 = intentAt("@17", "ABA2", "TABA2", "733333333333", "A-return");

        port.compose(a1);
        scenario.admit(a1);
        port.compose(b);
        scenario.admit(b);
        port.compose(a2);
        scenario.admit(a2);

        for (TestIntent intent : new TestIntent[]{a1, b, a2}) {
            assertEquals(intent.intentId(), scenario.active().intentId(),
                    "A-B-A exact FIFO head");
            // First HTTP attempt reaches the central idempotency owner but its
            // Android callback is lost. The retry is intentionally a second
            // HTTP attempt with the same key, not a second user intent.
            port.acceptWithoutCallback(intent);
            scenario.retryActive(intent);
            port.respondSuccess(scenario, intent);
            port.respondSuccess(scenario, intent);
        }

        assertEquals(
                list("ABA1", "ABA1", "ABB", "ABB", "ABA2", "ABA2"),
                port.requestStarts,
                "A-B-A may retry HTTP while retaining per-intent order"
        );
        assertEquals(list("ABA1", "ABB", "ABA2"), port.serverAccepts,
                "A-B-A one central acceptance per intent");
        assertEquals(list("ABA1", "ABB", "ABA2"), port.finalSuccesses,
                "A-B-A one Android final commit per intent");
        for (TestIntent intent : new TestIntent[]{a1, b, a2}) {
            assertEquals(2, port.requestAttemptCount(intent.idempotencyKey()),
                    "A-B-A two HTTP attempts for " + intent.intentId());
            assertEquals(1, port.serverAcceptanceCount(intent.idempotencyKey()),
                    "A-B-A one server acceptance for " + intent.intentId());
            assertEquals(1, port.finalCommitCount(intent.idempotencyKey()),
                    "A-B-A one final commit for " + intent.intentId());
            assertEquals(1, port.markerCount(intent.intentId()),
                    "A-B-A one terminal marker for " + intent.intentId());
        }
        assertEquals(0, scenario.queueSize(), "A-B-A queue empty");
        assertFalse(scenario.inFlight(), "A-B-A in-flight empty");
        assertRendererAndOwnershipProtected(port, 0, "rapid A-B-A");
    }

    private static void identicalBodiesRemainDistinctIntents() {
        FakePort port = new FakePort("intent-identity");
        QueueScenario scenario = new QueueScenario(port);
        TestIntent first = intent("D1", "TD1", "311111111111", "identical-body");
        TestIntent second = intent("D2", "TD2", "322222222222", "identical-body");

        port.compose(first);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.ADMITTED,
                scenario.admit(first),
                "first identical-body user intent admitted"
        );
        port.compose(second);
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.QUEUED_BEHIND_IN_FLIGHT,
                scenario.admit(second),
                "distinct identical-body user intent must not be merged"
        );
        assertEquals(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.DUPLICATE_REJECTED,
                scenario.admit(second),
                "same intent/key duplicate admission rejected once"
        );

        port.respondSuccess(scenario, first);
        assertEquals("D2", scenario.active().intentId(), "second identical-body intent drains next");
        port.respondSuccess(scenario, first);
        assertEquals("D2", scenario.active().intentId(),
                "late duplicate callback cannot complete the next identical body");
        port.respondSuccess(scenario, second);

        assertEquals(list("D1", "D2"), port.requestStarts, "identical-body FIFO starts");
        assertEquals(list("D1", "D2"), port.serverAccepts, "identical-body exact server accepts");
        assertEquals(list("D1", "D2"), port.finalSuccesses, "identical-body exact finals");
        assertEquals(1, port.markerCount("D1"), "first identical-body terminal occurrence");
        assertEquals(1, port.markerCount("D2"), "second identical-body terminal occurrence");
        assertNotEquals(first.idempotencyRef(), second.idempotencyRef(),
                "identical bodies keep distinct one-way key refs");
        assertRendererAndOwnershipProtected(port, 0, "identical-body");
    }

    private static void interleavedFailureDraftSurvivesLaterSuccess() {
        FakePort port = new FakePort("failure-draft");
        QueueScenario scenario = new QueueScenario(port);
        TestIntent r1 = intent("F1", "TF1", "411111111111", "F1-body");
        TestIntent r2 = intent("F2", "TF2", "422222222222", "F2-body");
        TestIntent r3 = intent("F3", "TF3", "433333333333", "F3-body");

        port.compose(r1);
        scenario.admit(r1);
        port.compose(r2);
        scenario.admit(r2);
        port.compose(r3);
        scenario.admit(r3);
        scenario.heartbeat();

        port.respondSuccess(scenario, r1);
        scenario.heartbeat();
        port.respondFailure(scenario, r2);
        scenario.heartbeat();
        assertEquals("", port.visibleDraft(),
                "failed R2 waits while newer pending R3 owns visible draft");
        assertEquals("F3-body", port.persistedDraft(TARGET),
                "failed R2 cannot overwrite newer pending R3 durable draft");
        assertEquals("F3", scenario.active().intentId(), "R3 drains after R2 failure");

        port.respondSuccess(scenario, r3);
        scenario.heartbeat();
        port.respondSuccess(scenario, r3);
        port.respondSuccess(scenario, r1);

        assertEquals("F2-body", port.visibleDraft(),
                "later R3 success preserves failure-restored visible R2 draft");
        assertEquals("F2-body", port.persistedDraft(TARGET),
                "later R3 success preserves failure-restored persisted R2 draft");
        assertEquals(list("F1", "F2", "F3"), port.requestStarts,
                "failure interleave keeps FIFO starts");
        assertEquals(list("F1", "F3"), port.serverAccepts,
                "failure before acceptance creates no R2 terminal acceptance");
        assertEquals(list("F1", "F3"), port.finalSuccesses,
                "failure interleave exact successes");
        assertEquals(list("F2"), port.finalFailures, "failure interleave exact failure");
        assertEquals(1, port.markerCount("F1"), "accepted R1 retry uses same key once");
        assertEquals(0, port.markerCount("F2"), "failed R2 has no terminal occurrence");
        assertEquals(1, port.markerCount("F3"), "accepted R3 exact terminal occurrence");
        assertRendererAndOwnershipProtected(port, 4, "failure interleave");
    }

    private static void failedMiddleIntentSurvivesRecreation() {
        TestIntent r1 = intent("L1", "TL1", "811111111111", "L1-body");
        TestIntent r2 = intent("L2", "TL2", "822222222222", "L2-body");
        TestIntent r3 = intent("L3", "TL3", "833333333333", "L3-body");
        ArrayList<MainActivity.PromptComposerSubmitLedgerEntry> ledger = new ArrayList<>();
        ledger = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                ledger,
                ledgerEntry(r1, 11L)
        );
        ledger = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                ledger,
                ledgerEntry(r2, 12L)
        );
        ledger = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                ledger,
                ledgerEntry(r3, 13L)
        );
        ledger = MainActivity.PromptComposerSubmitLedgerBehavior.removeExactSuccess(ledger, r1);
        ledger = MainActivity.PromptComposerSubmitLedgerBehavior.markExactFailure(ledger, r2);

        // This copy is the process-recreation boundary: no Activity queue or
        // callback object survives, only the immutable persisted operations.
        ArrayList<MainActivity.PromptComposerSubmitLedgerEntry> restored =
                MainActivity.PromptComposerSubmitLedgerBehavior.copy(ledger);
        assertNotNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        restored,
                        r2,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_FAILED
                ),
                "failed R2 remains durable after recreation"
        );
        assertNotNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        restored,
                        r3,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
                ),
                "pending R3 remains durable after recreation"
        );
        assertNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                        restored,
                        TARGET,
                        ""
                ),
                "failed R2 waits while newer pending R3 owns the same target"
        );

        restored = MainActivity.PromptComposerSubmitLedgerBehavior.removeExactSuccess(
                restored,
                r3
        );
        MainActivity.PromptComposerSubmitLedgerEntry restorable =
                MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                        restored,
                        TARGET,
                        ""
                );
        assertNotNull(restorable, "failed R2 becomes restorable after R3 final commit");
        assertEquals("L2", restorable.submitIntentId,
                "restored failure retains exact intentId");
        assertEquals(r2.idempotencyKey(), restorable.submitIdempotencyKey,
                "restored failure retains exact idempotency key");

        TestIntent resend = intent("L2-resend", "TL2-resend", "844444444444", "L2-body");
        restored = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                restored,
                ledgerEntry(resend, 14L)
        );
        assertNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        restored,
                        r2,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_FAILED
                ),
                "explicit resend consumes only its matching failed recovery"
        );
        assertNotNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        restored,
                        resend,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
                ),
                "explicit resend is a new immutable pending intent"
        );
    }

    private static void successCommitHasNoSplitCrashState() {
        TestIntent r2 = intent("C2", "TC2", "922222222222", "C2-body");
        TestIntent r3 = intent("C3", "TC3", "933333333333", "C3-body");
        ArrayList<MainActivity.PromptComposerSubmitLedgerEntry> beforeCommit =
                new ArrayList<>();
        beforeCommit = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                beforeCommit,
                ledgerEntry(r2, 22L)
        );
        beforeCommit = MainActivity.PromptComposerSubmitLedgerBehavior.markExactFailure(
                beforeCommit,
                r2
        );
        beforeCommit = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                beforeCommit,
                ledgerEntry(r3, 23L)
        );

        // A process death before the one preference commit retains both the R3
        // pending operation and its exact draft; recreation retries only R3's
        // original key, so R2 correctly remains blocked.
        assertNotNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        beforeCommit,
                        r3,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
                ),
                "pre-commit crash retains pending R3"
        );
        assertNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                        beforeCommit,
                        TARGET,
                        r3.submittedValue()
                ),
                "pre-commit crash cannot overwrite pending R3 draft with failed R2"
        );

        assertTrue(
                MainActivity.PromptComposerRapidOrderedSendBehavior
                        .shouldForgetSuccessfulDraft(
                                r3.submittedValue(),
                                23L,
                                r3.submittedValue(),
                                23L
                        ),
                "R3 success owns its exact durable draft in the atomic commit"
        );
        ArrayList<MainActivity.PromptComposerSubmitLedgerEntry> afterCommit =
                MainActivity.PromptComposerSubmitLedgerBehavior.removeExactSuccess(
                        beforeCommit,
                        r3
                );
        assertNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.exactEntry(
                        afterCommit,
                        r3,
                        MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
                ),
                "post-commit crash removes exact R3 pending entry"
        );
        assertNotNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                        afterCommit,
                        TARGET,
                        ""
                ),
                "post-commit crash sees empty R3 draft and can restore failed R2"
        );

        // This is the forbidden split state the production source guard below
        // prevents: ledger removal committed while the async R3 draft remained.
        assertNull(
                MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                        afterCommit,
                        TARGET,
                        r3.submittedValue()
                ),
                "split state would strand R2 and must not be externally observable"
        );
    }

    private static void productionUsesTheExecutedDecisions() throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        require(source,
                "PromptComposerRapidOrderedSendBehavior.intentFingerprint(",
                "live admission fingerprint delegates to production seam");
        require(source,
                "PromptComposerRapidOrderedSendBehavior.isDuplicate(",
                "live duplicate admission delegates to production seam");
        require(source,
                "PromptComposerRapidOrderedSendBehavior.nextToDrain(",
                "live FIFO drain delegates to production seam");
        require(source,
                "PromptComposerRapidOrderedSendBehavior.ownsCompletion(",
                "live callback completion delegates to production seam");
        require(source,
                "PromptComposerRapidOrderedSendBehavior.shouldForgetSuccessfulDraft(",
                "live successful draft decision delegates to production seam");
        require(source,
                "appendStableWindowQuery(\"/submit-text?resize=0\", stableTargetKey)",
                "live Send retains non-resizing stable-window route");
        require(source,
                "restorePromptComposerSubmitLedger()",
                "live Activity restores the durable per-intent ledger");
        require(source,
                "persistPromptComposerSubmitLedger(",
                "live admission and finalization persist the per-intent ledger");
        String persist = methodBody(
                source,
                "private boolean persistPromptComposerSubmitLedger("
        );
        require(persist, "editor.commit()",
                "write-ahead ledger must synchronously cross the durability boundary");
        reject(persist, "editor.apply()",
                "write-ahead ledger may not defer its durability boundary");
        for (String required : new String[]{
                "PromptComposerRapidOrderedSendBehavior.shouldForgetSuccessfulDraft(",
                "editor.remove(promptComposerDraftPrefsKey(stableTargetKey))",
                "promptComposerDraftSelectionStartPrefsKey(",
                "promptComposerDraftSelectionEndPrefsKey(",
                "promptComposerDraftSelectionRevisionPrefsKey(",
                "promptComposerDraftRevisionPrefsKey(stableTargetKey)",
                "successfulSubmit.draftClearedInLedgerCommit = draftClearedInCommit"
        }) {
            require(persist, required,
                    "success commit must atomically bind ledger and draft through " + required);
        }
        String restore = methodBody(
                source,
                "private void restorePromptComposerSubmitLedger("
        );
        for (String required : new String[]{
                "stored.optString(\"idempotencyKey\", \"\")",
                "stored.optString(\"intentId\", \"\")",
                "stored.optString(\"fingerprint\", \"\")",
                "PromptComposerRequestContext.capture(",
                "entry.submitIdempotencyKey",
                "promptComposerSubmitQueue.add("
        }) {
            require(restore, required,
                    "recreation must retain the exact immutable Send through " + required);
        }

        String enqueue = methodBody(source, "private void enqueuePromptComposerSubmit(");
        require(enqueue, "promptComposerSubmitQueue.add(submit)", "live queue append remains FIFO");
        int persistAdmission = enqueue.indexOf("persistPromptComposerSubmitLedger(");
        int queueAdmission = enqueue.indexOf("promptComposerSubmitQueue.add(submit)");
        assertTrue(persistAdmission >= 0 && persistAdmission < queueAdmission,
                "write-ahead ledger must commit before the HTTP-draining queue");
        require(enqueue, "hideDockedPromptComposerAfterQueuedSubmit",
                "live local acknowledgement remains before drain");
        String complete = methodBody(source, "private boolean completePromptComposerSubmit(");
        require(complete,
                "PromptComposerSubmitLedgerBehavior.removeExactSuccess(",
                "success removes only the exact intent/key ledger entry");
        require(complete,
                "persistPromptComposerSubmitLedger(nextLedger, submit)",
                "success commits exact ledger removal with owned draft cleanup");
        String fail = methodBody(source, "private boolean failPromptComposerSubmit(");
        require(fail,
                "PromptComposerSubmitLedgerBehavior.markExactFailure(",
                "failure preserves only the exact intent/key ledger entry");
        String clear = methodBody(
                source,
                "private boolean clearPromptComposerAfterSuccessfulSubmit("
        );
        reject(clear, "forgetPromptComposerDraft(",
                "post-commit UI cleanup may not reopen split durable draft removal");
        reject(clear, ".apply()",
                "post-commit UI cleanup may not mutate durable draft state asynchronously");
        String post = methodBody(source, "private void postPromptComposerSubmit(");
        reject(post, "webView.reload", "Send must not clear/reload renderer");
        reject(post, "closeCurrent", "Send must not call Close");
        reject(post, "showActiveSessions", "Send must not reselect Active session");
        reject(post, "live-bottom", "Send must not take the resizing live-bottom route");
    }

    private static TestIntent intent(
            String intentId,
            String tapId,
            String idempotencyRef,
            String body
    ) {
        return intentAt(TARGET, intentId, tapId, idempotencyRef, body);
    }

    private static TestIntent intentAt(
            String target,
            String intentId,
            String tapId,
            String idempotencyRef,
            String body
    ) {
        return new TestIntent(intentId, tapId, target, idempotencyRef, body);
    }

    private static MainActivity.PromptComposerSubmitLedgerEntry ledgerEntry(
            TestIntent intent,
            long draftRevision
    ) {
        return new MainActivity.PromptComposerSubmitLedgerEntry(
                intent.stableTargetKey(),
                intent.submittedValue(),
                draftRevision,
                intent.idempotencyKey(),
                intent.intentId(),
                intent.submitFingerprint(),
                "action-" + intent.intentId(),
                intent.tapId(),
                "send",
                MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
        );
    }

    private static void assertRendererAndOwnershipProtected(
            FakePort port,
            int minimumHeartbeats,
            String label
    ) {
        assertTrue(port.lastAcceptedFrameNonblank, label + " retains last accepted nonblank frame");
        assertEquals(0, port.rendererClearCalls, label + " no renderer clear");
        assertTrue(port.readyFrameCallbacks >= minimumHeartbeats,
                label + " renderer ready callbacks continue");
        assertEquals(0, port.closeCalls, label + " no Close");
        assertEquals(0, port.reselectCalls, label + " no reselection");
        assertEquals(0, port.targetChanges, label + " no hidden target change");
    }

    private static void assertDistinctRefs(TestIntent... intents) {
        Set<String> refs = new HashSet<>();
        for (TestIntent intent : intents) {
            assertTrue(intent.idempotencyRef().matches("[0-9a-f]{12}"),
                    "one-way key ref shape for " + intent.intentId());
            refs.add(intent.idempotencyRef());
            assertEquals(TARGET, intent.stableTargetKey(),
                    "stable target for " + intent.intentId());
        }
        assertEquals(intents.length, refs.size(), "distinct one-way key refs");
    }

    private static String methodBody(String source, String signature) {
        int nameIndex = source.indexOf(signature);
        if (nameIndex < 0) {
            throw new AssertionError("missing production method " + signature);
        }
        int brace = source.indexOf('{', nameIndex);
        int depth = 0;
        for (int index = brace; index >= 0 && index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth += 1;
            } else if (value == '}') {
                depth -= 1;
                if (depth == 0) {
                    return source.substring(brace, index + 1);
                }
            }
        }
        throw new AssertionError("unterminated production method " + signature);
    }

    private static String safeFailure(String value) {
        String safe = value == null ? "unspecified" : value;
        safe = safe.replaceAll("[^A-Za-z0-9._:@ -]", "-").trim();
        return safe.length() > 160 ? safe.substring(0, 160) : safe;
    }

    private static List<String> list(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }

    private static void require(String haystack, String needle, String message) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(message + " missing=" + needle);
        }
    }

    private static void reject(String haystack, String needle, String message) {
        if (haystack.contains(needle)) {
            throw new AssertionError(message + " found=" + needle);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (first == null ? second == null : first.equals(second)) {
            throw new AssertionError(message + " values-matched");
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " actual=" + value);
        }
    }

    private static void assertNotNull(Object value, String message) {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private interface CheckedCase {
        void run() throws Exception;
    }

    /**
     * Test orchestration only. Every nontrivial admission, FIFO head,
     * callback-ownership, and successful-draft choice delegates to the exact
     * production functions called by MainActivity.
     */
    private static final class QueueScenario {
        private final ArrayDeque<MainActivity.PromptComposerRapidOrderedSendBehavior.Submit> queue =
                new ArrayDeque<>();
        private ArrayList<MainActivity.PromptComposerSubmitLedgerEntry> ledger =
                new ArrayList<>();
        private final FakePort port;
        private MainActivity.PromptComposerRapidOrderedSendBehavior.Submit active;

        QueueScenario(FakePort port) {
            this.port = port;
        }

        MainActivity.PromptComposerRapidOrderedSendBehavior.Admission admit(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit
        ) {
            boolean wasInFlight = active != null;
            if (MainActivity.PromptComposerRapidOrderedSendBehavior.isDuplicate(
                    submit,
                    wasInFlight,
                    activeFingerprint(),
                    queue
            )) {
                port.onEvent("duplicate-rejected", submit, queue.size(), wasInFlight);
                return MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.DUPLICATE_REJECTED;
            }
            queue.add(submit);
            MainActivity.PromptComposerRapidOrderedSendBehavior.Admission admission =
                    wasInFlight
                            ? MainActivity.PromptComposerRapidOrderedSendBehavior.Admission
                                    .QUEUED_BEHIND_IN_FLIGHT
                            : MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.ADMITTED;
            port.onEvent(
                    admission == MainActivity.PromptComposerRapidOrderedSendBehavior.Admission.ADMITTED
                            ? "admitted"
                            : "queued-behind-in-flight",
                    submit,
                    queue.size(),
                    wasInFlight
            );
            port.onLocalFeedback(submit);
            ledger = MainActivity.PromptComposerSubmitLedgerBehavior.admit(
                    ledger,
                    new MainActivity.PromptComposerSubmitLedgerEntry(
                            submit.stableTargetKey(),
                            submit.submittedValue(),
                            port.admittedDraftRevision(submit),
                            submit.idempotencyKey(),
                            submit.intentId(),
                            submit.submitFingerprint(),
                            "action-" + submit.intentId(),
                            "tap-" + submit.intentId(),
                            "send",
                            MainActivity.PROMPT_COMPOSER_SUBMIT_LEDGER_PENDING
                    )
            );
            drain();
            return admission;
        }

        boolean complete(MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit) {
            if (!MainActivity.PromptComposerRapidOrderedSendBehavior.ownsCompletion(
                    active != null,
                    activeFingerprint(),
                    submit
            )) {
                port.onEvent("duplicate-callback-ignored", submit, queue.size(), active != null);
                return false;
            }
            ledger = MainActivity.PromptComposerSubmitLedgerBehavior.removeExactSuccess(
                    ledger,
                    submit
            );
            queue.remove(submit);
            active = null;
            port.onEvent("final-success", submit, queue.size(), false);
            drain();
            String target = submit.stableTargetKey();
            if (MainActivity.PromptComposerRapidOrderedSendBehavior.shouldForgetSuccessfulDraft(
                    port.persistedDraft(target),
                    port.persistedDraftRevision(target),
                    submit.submittedValue(),
                    port.admittedDraftRevision(submit)
            )) {
                port.forgetPersistedDraft(target);
            }
            if (target.equals(port.visibleTargetKey())
                    && submit.submittedValue().equals(port.visibleDraft())) {
                port.clearVisibleDraft(target);
            }
            restoreFailedLedgerDraftIfAvailable(target);
            port.onRendererCommitOpportunity(submit);
            return true;
        }

        void retryActive(MainActivity.PromptComposerRapidOrderedSendBehavior.Submit expected) {
            if (active != expected) {
                throw new AssertionError("retry must retain exact active intent");
            }
            port.onEvent("request-retry", active, queue.size(), true);
            port.onTransportStart(active);
        }

        boolean fail(MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit) {
            if (!MainActivity.PromptComposerRapidOrderedSendBehavior.ownsCompletion(
                    active != null,
                    activeFingerprint(),
                    submit
            )) {
                port.onEvent("duplicate-callback-ignored", submit, queue.size(), active != null);
                return false;
            }
            ledger = MainActivity.PromptComposerSubmitLedgerBehavior.markExactFailure(
                    ledger,
                    submit
            );
            queue.remove(submit);
            active = null;
            port.onEvent("final-failure", submit, queue.size(), false);
            drain();
            String target = submit.stableTargetKey();
            if (MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                    port.persistedDraft(target),
                    port.persistedDraftRevision(target),
                    submit.submittedValue(),
                    port.admittedDraftRevision(submit)
            )) {
                port.restoreFailedDraft(target, submit.submittedValue());
            }
            restoreFailedLedgerDraftIfAvailable(target);
            return true;
        }

        private void restoreFailedLedgerDraftIfAvailable(String target) {
            MainActivity.PromptComposerSubmitLedgerEntry restorable =
                    MainActivity.PromptComposerSubmitLedgerBehavior.firstRestorableFailure(
                            ledger,
                            target,
                            port.persistedDraft(target)
                    );
            if (restorable != null) {
                port.restoreFailedDraft(target, restorable.value);
            }
        }

        void heartbeat() {
            port.onRendererHeartbeat();
        }

        int queueSize() {
            return queue.size();
        }

        boolean inFlight() {
            return active != null;
        }

        MainActivity.PromptComposerRapidOrderedSendBehavior.Submit active() {
            return active;
        }

        private void drain() {
            MainActivity.PromptComposerRapidOrderedSendBehavior.Submit next =
                    MainActivity.PromptComposerRapidOrderedSendBehavior.nextToDrain(
                            active != null,
                            queue
                    );
            if (next == null) {
                if (active != null) {
                    port.onEvent("drain-held-in-flight", active, queue.size(), true);
                }
                return;
            }
            active = next;
            port.onEvent("request-start", active, queue.size(), true);
            port.onTransportStart(active);
        }

        private String activeFingerprint() {
            return active == null ? "" : active.submitFingerprint();
        }
    }

    private static final class TestIntent
            implements MainActivity.PromptComposerRapidOrderedSendBehavior.Submit {
        private final String intentId;
        private final String tapId;
        private final String stableTargetKey;
        private final String idempotencyRef;
        private final String body;
        private final String fingerprint;

        TestIntent(
                String intentId,
                String tapId,
                String stableTargetKey,
                String idempotencyRef,
                String body
        ) {
            this.intentId = intentId;
            this.tapId = tapId;
            this.stableTargetKey = stableTargetKey;
            this.idempotencyRef = idempotencyRef;
            this.body = body;
            this.fingerprint =
                    MainActivity.PromptComposerRapidOrderedSendBehavior.intentFingerprint(
                            stableTargetKey,
                            body,
                            intentId
                    );
        }

        @Override
        public String stableTargetKey() {
            return stableTargetKey;
        }

        @Override
        public String submittedValue() {
            return body;
        }

        @Override
        public String submitFingerprint() {
            return fingerprint;
        }

        @Override
        public String intentId() {
            return intentId;
        }

        String tapId() {
            return tapId;
        }

        @Override
        public String idempotencyRef() {
            return idempotencyRef;
        }

        @Override
        public String idempotencyKey() {
            return idempotencyRef;
        }
    }

    private static final class FakePort {
        private final String caseName;
        private final Map<String, String> persistedDrafts = new HashMap<>();
        private final Map<String, Long> persistedDraftRevisions = new HashMap<>();
        private final Map<String, Long> admittedDraftRevisions = new HashMap<>();
        private final Set<String> acceptedKeyRefs = new HashSet<>();
        private final Map<String, Integer> serverAcceptanceCounts = new LinkedHashMap<>();
        private final Map<String, Integer> requestAttemptCounts = new LinkedHashMap<>();
        private final Map<String, Integer> finalCommitCounts = new LinkedHashMap<>();
        private final Map<String, Integer> markerCounts = new LinkedHashMap<>();
        private final List<String> requestStarts = new ArrayList<>();
        private final List<String> serverAccepts = new ArrayList<>();
        private final List<String> finalSuccesses = new ArrayList<>();
        private final List<String> finalFailures = new ArrayList<>();
        private String visibleTarget = TARGET;
        private String visibleDraft = "";
        private long fakeTimeMs = 0;
        private int localFeedbackCount = 0;
        private int nextTapReadyCount = 0;
        private int rendererCommitOpportunities = 0;
        private int rendererClearCalls = 0;
        private int readyFrameCallbacks = 0;
        private int closeCalls = 0;
        private int reselectCalls = 0;
        private int targetChanges = 0;
        private boolean lastAcceptedFrameNonblank = true;

        FakePort(String caseName) {
            this.caseName = caseName;
        }

        void compose(TestIntent intent) {
            visibleTarget = intent.stableTargetKey();
            visibleDraft = intent.submittedValue();
            persistedDrafts.put(visibleTarget, visibleDraft);
            advancePersistedDraftRevision(visibleTarget);
            fakeTimeMs += 1;
        }

        void respondSuccess(
                QueueScenario scenario,
                TestIntent intent
        ) {
            fakeTimeMs += 10;
            recordServerAcceptance(intent);
            scenario.complete(intent);
        }

        void acceptWithoutCallback(TestIntent intent) {
            fakeTimeMs += 10;
            recordServerAcceptance(intent);
        }

        void respondFailure(
                QueueScenario scenario,
                TestIntent intent
        ) {
            fakeTimeMs += 10;
            scenario.fail(intent);
        }

        int markerCount(String intentId) {
            Integer count = markerCounts.get(intentId);
            return count == null ? 0 : count;
        }

        int requestAttemptCount(String idempotencyKey) {
            return count(requestAttemptCounts, idempotencyKey);
        }

        int serverAcceptanceCount(String idempotencyKey) {
            return count(serverAcceptanceCounts, idempotencyKey);
        }

        int finalCommitCount(String idempotencyKey) {
            return count(finalCommitCounts, idempotencyKey);
        }

        private void recordServerAcceptance(TestIntent intent) {
            if (acceptedKeyRefs.add(intent.idempotencyKey())) {
                serverAccepts.add(intent.intentId());
                increment(serverAcceptanceCounts, intent.idempotencyKey());
                markerCounts.put(intent.intentId(), markerCount(intent.intentId()) + 1);
            }
        }

        public void onEvent(
                String disposition,
                MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit,
                int queueSize,
                boolean inFlight
        ) {
            fakeTimeMs += 1;
            if ("final-success".equals(disposition)) {
                finalSuccesses.add(submit.intentId());
                increment(finalCommitCounts, submit.idempotencyKey());
            } else if ("final-failure".equals(disposition)) {
                finalFailures.add(submit.intentId());
            }
            // Deliberately do not retain prompt bodies, raw keys, titles, thread
            // IDs, OAuth values, or response bodies in this fixture.
            String safeEvent = "EVENT case=" + caseName
                    + " intent=" + submit.intentId()
                    + " target=" + submit.stableTargetKey()
                    + " keyRef=" + submit.idempotencyRef()
                    + " queue=" + Math.max(0, queueSize)
                    + " inFlight=" + inFlight
                    + " disposition=" + disposition
                    + " t=" + fakeTimeMs;
            if (safeEvent.contains(submit.submittedValue())) {
                throw new AssertionError("privacy-safe event ledger retained a prompt body");
            }
        }

        public void onLocalFeedback(MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit) {
            localFeedbackCount += 1;
            persistedDrafts.put(submit.stableTargetKey(), submit.submittedValue());
            long admittedRevision = advancePersistedDraftRevision(submit.stableTargetKey());
            admittedDraftRevisions.put(submit.intentId(), admittedRevision);
            if (submit.stableTargetKey().equals(visibleTarget)
                    && submit.submittedValue().equals(visibleDraft)) {
                visibleDraft = "";
            }
            nextTapReadyCount += 1;
        }

        public void onTransportStart(MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit) {
            requestStarts.add(submit.intentId());
            increment(requestAttemptCounts, submit.idempotencyKey());
        }

        public String visibleTargetKey() {
            return visibleTarget;
        }

        public String visibleDraft() {
            return visibleDraft;
        }

        public String persistedDraft(String stableTargetKey) {
            String value = persistedDrafts.get(stableTargetKey);
            return value == null ? "" : value;
        }

        public long persistedDraftRevision(String stableTargetKey) {
            Long value = persistedDraftRevisions.get(stableTargetKey);
            return value == null ? 0L : value;
        }

        public long admittedDraftRevision(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit
        ) {
            Long value = admittedDraftRevisions.get(submit.intentId());
            return value == null ? 0L : value;
        }

        public void clearVisibleDraft(String stableTargetKey) {
            if (stableTargetKey.equals(visibleTarget)) {
                visibleDraft = "";
            }
        }

        public void forgetPersistedDraft(String stableTargetKey) {
            persistedDrafts.remove(stableTargetKey);
            advancePersistedDraftRevision(stableTargetKey);
        }

        public void restoreFailedDraft(String stableTargetKey, String submittedValue) {
            persistedDrafts.put(stableTargetKey, submittedValue);
            advancePersistedDraftRevision(stableTargetKey);
            visibleTarget = stableTargetKey;
            visibleDraft = submittedValue;
        }

        private long advancePersistedDraftRevision(String stableTargetKey) {
            long next = persistedDraftRevision(stableTargetKey) + 1L;
            persistedDraftRevisions.put(stableTargetKey, next);
            return next;
        }

        private static int count(Map<String, Integer> counts, String key) {
            Integer value = counts.get(key);
            return value == null ? 0 : value;
        }

        private static void increment(Map<String, Integer> counts, String key) {
            counts.put(key, count(counts, key) + 1);
        }

        public void onRendererCommitOpportunity(
                MainActivity.PromptComposerRapidOrderedSendBehavior.Submit submit
        ) {
            rendererCommitOpportunities += 1;
            lastAcceptedFrameNonblank = true;
        }

        public void onRendererHeartbeat() {
            readyFrameCallbacks += 1;
            lastAcceptedFrameNonblank = true;
            fakeTimeMs += 5;
        }
    }

    private PromptComposerRapidOrderedSendBehaviorTest() {
    }
}
