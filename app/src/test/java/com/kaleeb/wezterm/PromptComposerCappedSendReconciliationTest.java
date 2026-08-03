package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Behavioral old-red for a capped outstanding-idempotency Send.
 *
 * <p>The fixture executes the production queue/identity decisions and freezes
 * the production capped callback wiring. Eight conflicts for one logical Send
 * must not strand a distinct second Send behind permanent in-flight ownership.
 */
public final class PromptComposerCappedSendReconciliationTest {
    private static final int MAX_CONFLICTS = 8;
    private static final String TARGET = "@14";
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );
    private static final Pattern RECONCILIATION_CALL = Pattern.compile(
            "\\b(?:postPromptComposerSubmit|schedule\\w*PromptComposer\\w*)"
                    + "\\s*\\(\\s*submit\\b"
    );
    private static final Pattern SAFE_RELEASE_CALL = Pattern.compile(
            "\\b(?:completePromptComposerSubmit|failPromptComposerSubmit)"
                    + "\\s*\\(\\s*submit\\s*\\)"
    );

    public static void main(String[] args) throws Exception {
        try {
            cappedUnknownMustReconcileOrReleaseBeforeSecondSend();
            System.out.println("PROMPT_COMPOSER_CAPPED_SEND_RECONCILIATION_PASS");
        } catch (AssertionError failure) {
            System.err.println("PROMPT_COMPOSER_CAPPED_SEND_RECONCILIATION_RED"
                    + " invariant=" + safeFailure(failure.getMessage()));
            throw failure;
        }
    }

    private static void cappedUnknownMustReconcileOrReleaseBeforeSecondSend() throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        String cappedBody = methodBody(
                source,
                "private void keepPromptComposerSubmitChecking("
        );
        require(
                cappedBody,
                "PromptComposerSendLifecycleDisposition.CAPPED_UNKNOWN",
                "production capped disposition"
        );

        LogicalSend first = new LogicalSend(
                "first-intent",
                "first-private-draft",
                "first-private-idempotency-key",
                "send-action-first",
                "send-tap-first"
        );
        LogicalSend second = new LogicalSend(
                "second-intent",
                "second-private-draft",
                "second-private-idempotency-key",
                "send-action-second",
                "send-tap-second"
        );
        assertFalse(
                first.idempotencyRef().equals(second.idempotencyRef()),
                "distinct Sends need distinct one-way idempotency attribution"
        );

        Scenario scenario = new Scenario();
        scenario.admit(first);
        scenario.admit(second);
        assertEquals(1, scenario.requestStarts(first), "first initial request start");
        assertEquals(0, scenario.requestStarts(second), "second waits behind first");

        for (int conflict = 1; conflict <= MAX_CONFLICTS; conflict++) {
            scenario.observeConflict(first, conflict);
            assertEquals(first.originalDraft, first.currentDraft,
                    "first immutable draft after conflict " + conflict);
            assertEquals(TARGET, first.stableTargetKey(),
                    "first stable target after conflict " + conflict);
            assertEquals(0, scenario.requestStarts(second),
                    "second remains queued before capped resolution");
            if (conflict < MAX_CONFLICTS) {
                scenario.retrySameLogicalSend(first, "first-retry-" + conflict);
            }
        }

        assertEquals(MAX_CONFLICTS, scenario.requestStarts(first),
                "eight conflicts use one initial attempt plus seven same-key retries");
        assertEquals(1, scenario.distinctRawKeys(first),
                "every first-Send retry keeps one raw idempotency key");
        assertEquals(0, scenario.requestStarts(second),
                "second has not started while first remains uncertain");

        CappedAction action = detectCappedAction(cappedBody);
        if (action == CappedAction.RECONCILE_SAME_SEND) {
            scenario.scheduleCappedReconciliation(first);
            scenario.resolve(first);
        } else if (action == CappedAction.SAFE_TERMINAL_RELEASE) {
            scenario.safeTerminalRelease(first);
        } else {
            throw new AssertionError(
                    "capped-unknown-stuck"
                            + " conflicts=" + scenario.conflicts
                            + " firstRequestStarts=" + scenario.requestStarts(first)
                            + " secondRequestStarts=" + scenario.requestStarts(second)
                            + " reconciliationScheduled=" + scenario.reconciliations
                            + " safeRelease=" + scenario.safeReleases
            );
        }

        assertEquals(1, scenario.requestStarts(second),
                "second distinct Send starts exactly once after first resolves");
        assertEquals(second.submitFingerprint, scenario.activeFingerprint,
                "second owns the active request after drain");
        assertTrue(scenario.inFlight, "second remains the sole active request");
        assertEquals(1, scenario.queue.size(), "only second remains queued/in flight");
        assertSame(second, scenario.queue.peek(), "queue head is the second Send");
        assertEquals(1, scenario.distinctRawKeys(second),
                "second starts with only its own idempotency key");
        assertAttributionProtected(first, second, scenario);
    }

    private static CappedAction detectCappedAction(String cappedBody) {
        if (RECONCILIATION_CALL.matcher(cappedBody).find()) {
            return CappedAction.RECONCILE_SAME_SEND;
        }
        if (SAFE_RELEASE_CALL.matcher(cappedBody).find()) {
            return CappedAction.SAFE_TERMINAL_RELEASE;
        }
        return CappedAction.STUCK;
    }

    private static void assertAttributionProtected(
            LogicalSend first,
            LogicalSend second,
            Scenario scenario
    ) {
        assertEquals("send-action-first", first.context.actionId, "first immutable action");
        assertEquals("send-tap-first", first.context.tapId, "first immutable tap");
        assertEquals("send-action-second", second.context.actionId, "second immutable action");
        assertEquals("send-tap-second", second.context.tapId, "second immutable tap");
        assertFalse(first.context.actionId.equals(second.context.actionId),
                "consecutive Sends retain distinct action attribution");
        assertFalse(first.context.tapId.equals(second.context.tapId),
                "consecutive Sends retain distinct tap attribution");
        assertEquals(MAX_CONFLICTS, scenario.conflicts, "all conflicts attributed");
        assertEquals(scenario.requestStarts(first), scenario.distinctTraces(first),
                "every first attempt has one immutable trace");
        assertEquals(1, scenario.distinctTraces(second),
                "second has exactly one immutable trace");
        for (LogicalSend send : new LogicalSend[]{first, second}) {
            String safeFields = send.context.toSafeLogFields();
            assertFalse(safeFields.contains(send.rawIdempotencyKey),
                    "safe attribution excludes raw idempotency key");
            assertFalse(safeFields.contains(send.originalDraft),
                    "safe attribution excludes private draft");
            assertEquals(TARGET, send.context.target, "stable target attribution");
            assertEquals(send.idempotencyRef(), send.context.idempotencyRef,
                    "stable one-way idempotency attribution");
        }
    }

    private enum CappedAction {
        RECONCILE_SAME_SEND,
        SAFE_TERMINAL_RELEASE,
        STUCK
    }

    private static final class LogicalSend
            implements MainActivity.PromptComposerRapidOrderedSendBehavior.Submit {
        final String intentId;
        final String originalDraft;
        final String rawIdempotencyKey;
        final String submitFingerprint;
        final MainActivity.PromptComposerRequestContext context;
        String currentDraft;

        LogicalSend(
                String intentId,
                String draft,
                String rawIdempotencyKey,
                String actionId,
                String tapId
        ) {
            this.intentId = intentId;
            this.originalDraft = draft;
            this.currentDraft = draft;
            this.rawIdempotencyKey = rawIdempotencyKey;
            this.context = MainActivity.PromptComposerRequestContext.capture(
                    actionId,
                    tapId,
                    "send",
                    TARGET,
                    rawIdempotencyKey
            );
            this.submitFingerprint =
                    MainActivity.PromptComposerRapidOrderedSendBehavior.intentFingerprint(
                            TARGET,
                            draft,
                            context.idempotencyRef
                    );
        }

        @Override
        public String stableTargetKey() {
            return TARGET;
        }

        @Override
        public String submittedValue() {
            return originalDraft;
        }

        @Override
        public String submitFingerprint() {
            return submitFingerprint;
        }

        @Override
        public String intentId() {
            return intentId;
        }

        @Override
        public String idempotencyRef() {
            return context.idempotencyRef;
        }
    }

    private static final class Scenario {
        final ArrayDeque<LogicalSend> queue = new ArrayDeque<>();
        final Map<LogicalSend, Integer> requestStarts = new HashMap<>();
        final Map<LogicalSend, Set<String>> traces = new HashMap<>();
        final Map<LogicalSend, Set<String>> rawKeys = new HashMap<>();
        boolean inFlight;
        String activeFingerprint = "";
        int conflicts;
        int reconciliations;
        int safeReleases;

        void admit(LogicalSend send) {
            assertFalse(
                    MainActivity.PromptComposerRapidOrderedSendBehavior.isDuplicate(
                            send,
                            inFlight,
                            activeFingerprint,
                            queue
                    ),
                    "distinct Send must be admitted"
            );
            queue.add(send);
            drain();
        }

        void drain() {
            LogicalSend next =
                    MainActivity.PromptComposerRapidOrderedSendBehavior.nextToDrain(
                            inFlight,
                            queue
                    );
            if (next == null) {
                return;
            }
            inFlight = true;
            activeFingerprint = next.submitFingerprint;
            recordRequestStart(next, next.intentId + "-initial");
        }

        void observeConflict(LogicalSend expected, int conflict) {
            assertOwned(expected, "conflict " + conflict);
            conflicts += 1;
        }

        void retrySameLogicalSend(LogicalSend expected, String traceId) {
            assertOwned(expected, "retry");
            recordRequestStart(expected, traceId);
        }

        void scheduleCappedReconciliation(LogicalSend expected) {
            assertOwned(expected, "capped reconciliation");
            reconciliations += 1;
            recordRequestStart(expected, "first-capped-reconciliation");
        }

        void resolve(LogicalSend expected) {
            finishAndDrain(expected, "resolved");
        }

        void safeTerminalRelease(LogicalSend expected) {
            safeReleases += 1;
            finishAndDrain(expected, "safe terminal release");
        }

        void finishAndDrain(LogicalSend expected, String stage) {
            assertOwned(expected, stage);
            inFlight = false;
            activeFingerprint = "";
            assertTrue(queue.remove(expected), stage + " removes first queue entry");
            drain();
        }

        void assertOwned(LogicalSend expected, String stage) {
            assertTrue(
                    MainActivity.PromptComposerRapidOrderedSendBehavior.ownsCompletion(
                            inFlight,
                            activeFingerprint,
                            expected
                    ),
                    stage + " preserves first request ownership"
            );
            assertSame(expected, queue.peek(), stage + " preserves first queue head");
            assertEquals(expected.originalDraft, expected.currentDraft,
                    stage + " preserves immutable draft");
            assertEquals(TARGET, expected.stableTargetKey(), stage + " preserves target");
        }

        void recordRequestStart(LogicalSend send, String traceId) {
            MainActivity.PromptComposerRequestContext attempt =
                    send.context.withTraceId(traceId);
            assertEquals(send.context.actionId, attempt.actionId, "attempt action");
            assertEquals(send.context.tapId, attempt.tapId, "attempt tap");
            assertEquals(send.context.target, attempt.target, "attempt target");
            assertEquals(send.context.idempotencyRef, attempt.idempotencyRef,
                    "attempt idempotency ref");
            requestStarts.put(send, requestStarts(send) + 1);
            traces.computeIfAbsent(send, ignored -> new HashSet<>()).add(attempt.traceId);
            rawKeys.computeIfAbsent(send, ignored -> new HashSet<>()).add(send.rawIdempotencyKey);
        }

        int requestStarts(LogicalSend send) {
            Integer count = requestStarts.get(send);
            return count == null ? 0 : count;
        }

        int distinctTraces(LogicalSend send) {
            Set<String> values = traces.get(send);
            return values == null ? 0 : values.size();
        }

        int distinctRawKeys(LogicalSend send) {
            Set<String> values = rawKeys.get(send);
            return values == null ? 0 : values.size();
        }
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        if (method < 0) {
            throw new AssertionError("missing production method");
        }
        int brace = source.indexOf('{', method);
        if (brace < 0) {
            throw new AssertionError("missing production method body");
        }
        int depth = 0;
        for (int index = brace; index < source.length(); index++) {
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
        throw new AssertionError("unterminated production method");
    }

    private static void require(String haystack, String needle, String message) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(message + " missing");
        }
    }

    private static String safeFailure(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._:=@-]", "-");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
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

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private PromptComposerCappedSendReconciliationTest() {
    }
}
