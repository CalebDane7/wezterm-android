package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Executable guard for behavior-neutral, privacy-safe consecutive-Send lifecycle telemetry. */
public final class PromptComposerConsecutiveSendLifecycleTelemetryTest {
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );

    public static void main(String[] args) throws Exception {
        successfulFirstAttemptThenDistinctSecondAttempt();
        suppressedAndUncertainAttemptsNeverDisappear();
        producedRecordsUseOnlyThePrivacyAllowlist();
        productionWiringCoversExistingSendBranchesWithoutOwningDelivery();
        System.out.println("PROMPT_COMPOSER_CONSECUTIVE_SEND_LIFECYCLE_TELEMETRY_PASS");
    }

    private static void successfulFirstAttemptThenDistinctSecondAttempt() {
        String firstRef = MainActivity.PromptComposerRequestContext.capture(
                "request-action-one", "request-tap-one", "send", "@17", "raw-key-first"
        ).idempotencyRef;
        String secondRef = MainActivity.PromptComposerRequestContext.capture(
                "request-action-two", "request-tap-two", "send", "@17", "raw-key-second"
        ).idempotencyRef;

        MainActivity.PromptComposerSendLifecycle first =
                MainActivity.PromptComposerSendLifecycle.start("@17", 100L);
        List<MainActivity.PromptComposerSendLifecycleRecord> firstRecords = new ArrayList<>();
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                MainActivity.PromptComposerSendLifecycleDisposition.ENTERED,
                false, 0, false, 0, "", 100L);
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                MainActivity.PromptComposerSendLifecycleDisposition.ADMITTED,
                false, 0, false, 1, firstRef, 110L);
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.DRAIN,
                MainActivity.PromptComposerSendLifecycleDisposition.IN_FLIGHT_STARTED,
                false, 1, true, 1, firstRef, 120L);
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.REQUEST_START,
                MainActivity.PromptComposerSendLifecycleDisposition.REQUEST_STARTED,
                true, 1, true, 1, firstRef, 130L);
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.CALLBACK,
                MainActivity.PromptComposerSendLifecycleDisposition.RESPONSE_RECEIVED,
                true, 1, true, 1, firstRef, 140L);
        first = append(first, firstRecords,
                MainActivity.PromptComposerSendLifecyclePhase.FINAL,
                MainActivity.PromptComposerSendLifecycleDisposition.SUCCEEDED,
                true, 1, false, 0, firstRef, 150L);

        MainActivity.PromptComposerSendLifecycle second =
                MainActivity.PromptComposerSendLifecycle.start("@17", 200L);
        List<MainActivity.PromptComposerSendLifecycleRecord> secondRecords = new ArrayList<>();
        second = append(second, secondRecords,
                MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                MainActivity.PromptComposerSendLifecycleDisposition.ENTERED,
                false, 0, false, 0, "", 200L);
        second = append(second, secondRecords,
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                MainActivity.PromptComposerSendLifecycleDisposition.ADMITTED,
                false, 0, false, 1, secondRef, 210L);
        second = append(second, secondRecords,
                MainActivity.PromptComposerSendLifecyclePhase.DRAIN,
                MainActivity.PromptComposerSendLifecycleDisposition.IN_FLIGHT_STARTED,
                false, 1, true, 1, secondRef, 220L);
        second = append(second, secondRecords,
                MainActivity.PromptComposerSendLifecyclePhase.REQUEST_START,
                MainActivity.PromptComposerSendLifecycleDisposition.REQUEST_STARTED,
                true, 1, true, 1, secondRef, 230L);

        validateAdmittedAttempt(first, firstRecords, 1);
        validateAdmittedAttempt(second, secondRecords, 1);
        assertNotEquals(first.actionId, second.actionId, "consecutive action identity");
        assertNotEquals(first.tapId, second.tapId, "consecutive tap identity");
        assertNotEquals(first.traceId, second.traceId, "consecutive trace identity");
        assertMatches("[0-9a-f]{12}", firstRef, "first one-way key reference");
        assertMatches("[0-9a-f]{12}", secondRef, "second one-way key reference");
        assertNotEquals(firstRef, secondRef, "consecutive key reference");
        assertEquals("@17", second.target, "stable consecutive target");

        List<MainActivity.PromptComposerSendLifecycleRecord> missing =
                new ArrayList<>(secondRecords);
        missing.remove(missing.size() - 1);
        assertInvalidAdmittedAttempt(second, missing, "missing request-start must fail");
        List<MainActivity.PromptComposerSendLifecycleRecord> duplicate =
                new ArrayList<>(secondRecords);
        duplicate.add(secondRecords.get(secondRecords.size() - 1));
        assertInvalidAdmittedAttempt(second, duplicate, "duplicate request-start must fail");
        List<MainActivity.PromptComposerSendLifecycleRecord> reordered =
                new ArrayList<>(secondRecords);
        MainActivity.PromptComposerSendLifecycleRecord temporary = reordered.get(1);
        reordered.set(1, reordered.get(2));
        reordered.set(2, temporary);
        assertInvalidAdmittedAttempt(second, reordered, "reordered phases must fail");
    }

    private static void suppressedAndUncertainAttemptsNeverDisappear() {
        assertTerminalFixture(
                MainActivity.PromptComposerSendLifecycleDisposition.CONTROL_DISABLED,
                MainActivity.PromptComposerSendLifecyclePhase.PRE_GATE,
                false, 0, false, 0
        );
        assertTerminalFixture(
                MainActivity.PromptComposerSendLifecycleDisposition.ACTIVE_SWITCH_BLOCKED,
                MainActivity.PromptComposerSendLifecyclePhase.PRE_GATE,
                false, 0, false, 0
        );
        assertTerminalFixture(
                MainActivity.PromptComposerSendLifecycleDisposition.DUPLICATE_REJECTED,
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                true, 1, true, 1
        );

        MainActivity.PromptComposerSendLifecycle queued =
                MainActivity.PromptComposerSendLifecycle.start("@22", 300L);
        List<MainActivity.PromptComposerSendLifecycleRecord> queuedRecords = new ArrayList<>();
        queued = append(queued, queuedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                MainActivity.PromptComposerSendLifecycleDisposition.ENTERED,
                true, 1, true, 1, "", 300L);
        queued = append(queued, queuedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                MainActivity.PromptComposerSendLifecycleDisposition.QUEUED_BEHIND_IN_FLIGHT,
                true, 1, true, 2, "123456abcdef", 310L);
        queued = append(queued, queuedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.DRAIN,
                MainActivity.PromptComposerSendLifecycleDisposition.DRAIN_BLOCKED_IN_FLIGHT,
                true, 2, true, 2, "123456abcdef", 320L);
        assertEquals(3, queuedRecords.size(), "queued-behind attempt remains visible");
        assertRecordState(queued.lastRecord, true, 2, true, 2, "queued drain state");
        assertEquals(MainActivity.PromptComposerSendLifecycleDisposition.DRAIN_BLOCKED_IN_FLIGHT,
                queued.lastRecord.disposition, "queued drain disposition");

        MainActivity.PromptComposerSendLifecycle capped =
                MainActivity.PromptComposerSendLifecycle.start("@23", 400L);
        List<MainActivity.PromptComposerSendLifecycleRecord> cappedRecords = new ArrayList<>();
        capped = append(capped, cappedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                MainActivity.PromptComposerSendLifecycleDisposition.ENTERED,
                false, 0, false, 0, "", 400L);
        capped = append(capped, cappedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                MainActivity.PromptComposerSendLifecycleDisposition.ADMITTED,
                false, 0, false, 1, "abcdef123456", 410L);
        capped = append(capped, cappedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.DRAIN,
                MainActivity.PromptComposerSendLifecycleDisposition.IN_FLIGHT_STARTED,
                false, 1, true, 1, "abcdef123456", 420L);
        capped = append(capped, cappedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.REQUEST_START,
                MainActivity.PromptComposerSendLifecycleDisposition.REQUEST_STARTED,
                true, 1, true, 1, "abcdef123456", 430L);
        capped = append(capped, cappedRecords,
                MainActivity.PromptComposerSendLifecyclePhase.FINAL,
                MainActivity.PromptComposerSendLifecycleDisposition.CAPPED_UNKNOWN,
                true, 1, true, 1, "abcdef123456", 440L);
        assertEquals(MainActivity.PromptComposerSendLifecycleDisposition.CAPPED_UNKNOWN,
                capped.lastRecord.disposition, "capped uncertainty remains explicit");
        assertRecordState(capped.lastRecord, true, 1, true, 1, "capped state is retained");
    }

    private static void producedRecordsUseOnlyThePrivacyAllowlist() {
        String rawKey = "raw-private-key-never-log";
        String ref = MainActivity.PromptComposerRequestContext.capture(
                "a", "t", "send", "@31", rawKey
        ).idempotencyRef;
        MainActivity.PromptComposerSendLifecycle lifecycle =
                MainActivity.PromptComposerSendLifecycle.start("private title body", 500L);
        lifecycle = lifecycle.transition(
                MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                MainActivity.PromptComposerSendLifecycleDisposition.ADMITTED,
                false, 0, false, 1, ref, 510L
        );
        String safe = lifecycle.lastRecord.toSafeLogFields();
        Set<String> expectedKeys = new HashSet<>(Arrays.asList(
                "lifecyclePhase", "lifecycleDisposition", "lifecycleSequence",
                "actionId", "tapId", "lifecycleTraceId", "target", "idempotencyRef",
                "inFlightBefore", "queueSizeBefore", "inFlightAfter", "queueSizeAfter",
                "elapsedMs"
        ));
        Set<String> actualKeys = new HashSet<>();
        for (String token : safe.trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals <= 0) {
                throw new AssertionError("malformed safe lifecycle field: " + token);
            }
            actualKeys.add(token.substring(0, equals));
        }
        assertEquals(expectedKeys, actualKeys, "fixed telemetry allowlist");
        assertEquals("unstable", lifecycle.target, "non-window target is not retained");
        for (String forbidden : new String[]{
                rawKey, "private title body", "prompt text", "draft text", "message text",
                "session value", "thread value", "cwd value", "Authorization", "OAuth",
                "Cookie", "Bearer"
        }) {
            assertFalse(safe.contains(forbidden), "private value leaked: " + forbidden);
        }
        assertMatches("[0-9a-f]{12}", lifecycle.lastRecord.idempotencyRef,
                "record keeps only one-way key reference");
    }

    private static void productionWiringCoversExistingSendBranchesWithoutOwningDelivery()
            throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        require(source, "static final class PromptComposerSendLifecycle", "immutable lifecycle owner");
        require(source, "static final class PromptComposerSendLifecycleRecord", "immutable transition record");
        require(source, "recordPromptComposerSendLifecycle(", "single production record owner");

        String controlState = methodBody(source, "private void updateStartButtonLabel(");
        require(controlState, "CONTROL_DISABLED", "disabled Send control state is explicit");
        String submit = methodBody(source,
                "private void submitSafePrompt(\n            String text,");
        require(submit, "PromptComposerSendLifecycle.start(", "Send entry creates a safe attempt");
        require(submit, "ACTIVE_SWITCH_BLOCKED", "pre-gate block is explicit");
        require(submit, "SESSION_INTERACTION_BACKEND_GATED", "backend-owned gate is explicit");
        String enqueue = methodBody(source, "private void enqueuePromptComposerSubmit(");
        require(enqueue, "DUPLICATE_REJECTED", "duplicate admission is explicit");
        require(enqueue, "QUEUED_BEHIND_IN_FLIGHT", "stacked queue admission is explicit");
        require(enqueue, "ADMITTED", "ordinary queue admission is explicit");
        String drain = methodBody(source, "private void drainPromptComposerSubmitQueue(");
        require(drain, "DRAIN_BLOCKED_IN_FLIGHT", "in-flight drain return is explicit");
        require(drain, "IN_FLIGHT_STARTED", "drain ownership is explicit");
        String post = methodBody(source, "private void postPromptComposerSubmit(");
        require(post, "REQUEST_SUPPRESSED_STATE_MISMATCH", "request precondition return is explicit");
        require(post, "REQUEST_STARTED", "HTTP start is explicit");
        require(post, "RESPONSE_RECEIVED", "callback entry is explicit");
        require(methodBody(source, "private void holdPromptComposerSubmitForDeliveryBlock("),
                "DELIVERY_BLOCKED_RETRYING", "delivery-block retry state is explicit");
        require(methodBody(source, "private void schedulePromptComposerSubmitInProgressRetry("),
                "RETRY_SCHEDULED", "idempotency retry state is explicit");
        require(methodBody(source, "private void keepPromptComposerSubmitChecking("),
                "CAPPED_UNKNOWN", "capped uncertainty is explicit");
        require(methodBody(source, "private void completePromptComposerSubmit("),
                "SUCCEEDED", "success finalization is explicit");
        require(methodBody(source, "private void failPromptComposerSubmit("),
                "FAILED", "failure finalization is explicit");

        String recorder = methodBody(source,
                "private PromptComposerSendLifecycle recordPromptComposerSendLifecycle(");
        require(recorder, "next.lastRecord.toSafeLogFields()", "logs use the immutable record");
        reject(recorder, "submit.value", "lifecycle recorder must not read prompt content");
        reject(recorder, "submitIdempotencyKey", "lifecycle recorder must not read the raw key");
        reject(recorder, "payload", "lifecycle recorder must not retain response bodies");
        require(source, "telemetry never owns delivery or clears/resets the queue",
                "local behavior-freeze WHY");
        require(source, "appendStableWindowQuery(\"/submit-text?resize=0\", stableTargetKey)",
                "protected non-resizing route");
        require(source, "promptComposerSubmitQueue.add(submit)", "protected queue admission");
    }

    private static void assertTerminalFixture(
            MainActivity.PromptComposerSendLifecycleDisposition disposition,
            MainActivity.PromptComposerSendLifecyclePhase phase,
            boolean inFlightBefore,
            int queueBefore,
            boolean inFlightAfter,
            int queueAfter
    ) {
        MainActivity.PromptComposerSendLifecycle lifecycle =
                MainActivity.PromptComposerSendLifecycle.start("@21", 600L);
        List<MainActivity.PromptComposerSendLifecycleRecord> records = new ArrayList<>();
        lifecycle = append(lifecycle, records,
                MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                MainActivity.PromptComposerSendLifecycleDisposition.ENTERED,
                inFlightBefore, queueBefore, inFlightBefore, queueBefore, "", 600L);
        lifecycle = append(lifecycle, records, phase, disposition,
                inFlightBefore, queueBefore, inFlightAfter, queueAfter, "", 610L);
        assertEquals(2, records.size(), disposition + " record count");
        assertEquals(disposition, lifecycle.lastRecord.disposition, disposition + " terminal disposition");
        assertRecordState(lifecycle.lastRecord,
                inFlightBefore, queueBefore, inFlightAfter, queueAfter, disposition + " queue state");
        assertEquals(0, countDisposition(records,
                MainActivity.PromptComposerSendLifecycleDisposition.REQUEST_STARTED),
                disposition + " must not invent a request");
    }

    private static MainActivity.PromptComposerSendLifecycle append(
            MainActivity.PromptComposerSendLifecycle lifecycle,
            List<MainActivity.PromptComposerSendLifecycleRecord> records,
            MainActivity.PromptComposerSendLifecyclePhase phase,
            MainActivity.PromptComposerSendLifecycleDisposition disposition,
            boolean inFlightBefore,
            int queueBefore,
            boolean inFlightAfter,
            int queueAfter,
            String idempotencyRef,
            long nowMs
    ) {
        MainActivity.PromptComposerSendLifecycle next = lifecycle.transition(
                phase, disposition, inFlightBefore, queueBefore, inFlightAfter, queueAfter,
                idempotencyRef, nowMs
        );
        records.add(next.lastRecord);
        return next;
    }

    private static void validateAdmittedAttempt(
            MainActivity.PromptComposerSendLifecycle lifecycle,
            List<MainActivity.PromptComposerSendLifecycleRecord> records,
            int expectedRequestStarts
    ) {
        if (records.size() < 4) {
            throw new AssertionError("admitted attempt lost lifecycle records");
        }
        long sequence = 1;
        for (MainActivity.PromptComposerSendLifecycleRecord record : records) {
            assertEquals(sequence++, record.sequence, "ordered lifecycle sequence");
            assertEquals(lifecycle.actionId, record.actionId, "immutable action identity");
            assertEquals(lifecycle.tapId, record.tapId, "immutable tap identity");
            assertEquals(lifecycle.traceId, record.traceId, "immutable trace identity");
            assertEquals(lifecycle.target, record.target, "immutable stable target");
        }
        assertEquals(MainActivity.PromptComposerSendLifecyclePhase.CONTROL_ENTRY,
                records.get(0).phase, "first phase");
        assertEquals(MainActivity.PromptComposerSendLifecyclePhase.QUEUE_ADMISSION,
                records.get(1).phase, "queue phase");
        assertEquals(MainActivity.PromptComposerSendLifecyclePhase.DRAIN,
                records.get(2).phase, "drain phase");
        assertEquals(MainActivity.PromptComposerSendLifecyclePhase.REQUEST_START,
                records.get(3).phase, "request phase");
        assertEquals(expectedRequestStarts, countDisposition(records,
                MainActivity.PromptComposerSendLifecycleDisposition.REQUEST_STARTED),
                "exact request-start count");
    }

    private static void assertInvalidAdmittedAttempt(
            MainActivity.PromptComposerSendLifecycle lifecycle,
            List<MainActivity.PromptComposerSendLifecycleRecord> records,
            String message
    ) {
        try {
            validateAdmittedAttempt(lifecycle, records, 1);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static int countDisposition(
            List<MainActivity.PromptComposerSendLifecycleRecord> records,
            MainActivity.PromptComposerSendLifecycleDisposition disposition
    ) {
        int count = 0;
        for (MainActivity.PromptComposerSendLifecycleRecord record : records) {
            if (record.disposition == disposition) {
                count += 1;
            }
        }
        return count;
    }

    private static void assertRecordState(
            MainActivity.PromptComposerSendLifecycleRecord record,
            boolean inFlightBefore,
            int queueBefore,
            boolean inFlightAfter,
            int queueAfter,
            String message
    ) {
        assertEquals(inFlightBefore, record.inFlightBefore, message + " in-flight before");
        assertEquals(queueBefore, record.queueSizeBefore, message + " queue before");
        assertEquals(inFlightAfter, record.inFlightAfter, message + " in-flight after");
        assertEquals(queueAfter, record.queueSizeAfter, message + " queue after");
    }

    private static String methodBody(String source, String signature) {
        int nameIndex = source.indexOf(signature);
        if (nameIndex < 0) {
            throw new AssertionError("missing method " + signature);
        }
        int brace = source.indexOf('{', nameIndex);
        if (brace < 0) {
            throw new AssertionError("missing body for " + signature);
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
        throw new AssertionError("unterminated method " + signature);
    }

    private static void require(String haystack, String needle, String message) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(message + ": missing " + needle);
        }
    }

    private static void reject(String haystack, String needle, String message) {
        if (haystack.contains(needle)) {
            throw new AssertionError(message + ": found " + needle);
        }
    }

    private static void assertMatches(String regex, String actual, String message) {
        if (actual == null || !actual.matches(regex)) {
            throw new AssertionError(message + ": actual=" + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (first == null ? second == null : first.equals(second)) {
            throw new AssertionError(message + ": both=" + first);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private PromptComposerConsecutiveSendLifecycleTelemetryTest() {
    }
}
