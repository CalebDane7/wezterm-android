package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Pure executable guard for immutable Send correlation across asynchronous work.
 *
 * <p>The test has a main entry point so it runs with the repository's existing
 * javac/Android-SDK harness and does not require a Gradle project or APK build.
 */
public final class PromptComposerImmutableRequestContextTest {
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );

    public static void main(String[] args) throws Exception {
        queuedSnapshotSurvivesLaterGlobalActionChanges();
        eachAttemptAddsOnlyItsOwnImmutableTrace();
        idempotencyCorrelationIsShortStableAndOneWay();
        productionSendPathUsesTheQueuedSnapshotAcrossAsyncBoundaries();
        System.out.println("PROMPT_COMPOSER_IMMUTABLE_REQUEST_CONTEXT_PASS");
    }

    private static void queuedSnapshotSurvivesLaterGlobalActionChanges() {
        String mutableActionId = "send-action-original";
        String mutableTapId = "tap-original";
        String mutableActionKind = "send";

        MainActivity.PromptComposerRequestContext queued =
                MainActivity.PromptComposerRequestContext.capture(
                        mutableActionId,
                        mutableTapId,
                        mutableActionKind,
                        "@4",
                        "phone-submit-contract-alpha"
                );

        // This models Active/scroll/background work changing MainActivity's
        // process-global action fields after Send queued but before its thread runs.
        mutableActionId = "scroll-later";
        mutableTapId = "tap-later";
        mutableActionKind = "one-finger-scroll";

        assertEquals("send-action-original", queued.actionId, "queued action id");
        assertEquals("tap-original", queued.tapId, "queued tap id");
        assertEquals("send", queued.actionKind, "queued action kind");
        assertEquals("@4", queued.target, "queued stable target");
        assertEquals("", queued.traceId, "queue snapshot has no attempt trace yet");
        assertFalse(mutableActionId.equals(queued.actionId), "later action must not relabel Send");
        assertFalse(mutableTapId.equals(queued.tapId), "later tap must not relabel Send");
        assertFalse(mutableActionKind.equals(queued.actionKind), "later action kind must not relabel Send");
    }

    private static void eachAttemptAddsOnlyItsOwnImmutableTrace() {
        MainActivity.PromptComposerRequestContext queued =
                MainActivity.PromptComposerRequestContext.capture(
                        "send-action",
                        "tap-send",
                        "send",
                        "@9",
                        "phone-submit-contract-beta"
                );
        MainActivity.PromptComposerRequestContext first = queued.withTraceId("trace-first");
        MainActivity.PromptComposerRequestContext second = queued.withTraceId("trace-second");

        assertEquals("", queued.traceId, "base queue context remains immutable");
        assertEquals("trace-first", first.traceId, "first attempt trace");
        assertEquals("trace-second", second.traceId, "second attempt trace");
        for (MainActivity.PromptComposerRequestContext attempt : new MainActivity.PromptComposerRequestContext[]{first, second}) {
            assertEquals(queued.actionId, attempt.actionId, "attempt action id");
            assertEquals(queued.tapId, attempt.tapId, "attempt tap id");
            assertEquals(queued.actionKind, attempt.actionKind, "attempt action kind");
            assertEquals(queued.target, attempt.target, "attempt target");
            assertEquals(queued.idempotencyRef, attempt.idempotencyRef, "attempt idempotency ref");
        }
    }

    private static void idempotencyCorrelationIsShortStableAndOneWay() {
        String rawAlpha = "phone-submit-contract-alpha";
        String rawBeta = "phone-submit-contract-beta";
        MainActivity.PromptComposerRequestContext alphaOne =
                MainActivity.PromptComposerRequestContext.capture("a", "t", "send", "@1", rawAlpha);
        MainActivity.PromptComposerRequestContext alphaTwo =
                MainActivity.PromptComposerRequestContext.capture("a", "t", "send", "@1", rawAlpha);
        MainActivity.PromptComposerRequestContext beta =
                MainActivity.PromptComposerRequestContext.capture("a", "t", "send", "@1", rawBeta);

        assertMatches("[0-9a-f]{12}", alphaOne.idempotencyRef, "short one-way idempotency ref");
        assertEquals(alphaOne.idempotencyRef, alphaTwo.idempotencyRef, "same key correlation");
        assertFalse(alphaOne.idempotencyRef.equals(beta.idempotencyRef), "different key correlation");
        assertFalse(alphaOne.idempotencyRef.contains(rawAlpha), "raw key must not appear in ref");
        assertFalse(alphaOne.toSafeLogFields().contains(rawAlpha), "raw key must not appear in safe fields");
        assertFalse(alphaOne.toSafeLogFields().contains(rawBeta), "another raw key must not appear in safe fields");
    }

    private static void productionSendPathUsesTheQueuedSnapshotAcrossAsyncBoundaries() throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        require(source, "final PromptComposerRequestContext requestContext;", "queue owns immutable request context");
        require(source, "PromptComposerRequestContext.capture(", "Send captures context at queue creation");
        require(source, "submit.requestContext.withTraceId(", "each Send attempt derives an immutable trace");
        require(source, "appendActionCorrelationToPath(path, requestContext)", "request URL uses captured context");
        require(source, "applyControlRequestHeaders(connection, requestContext)", "request headers use captured context");
        require(source, "logControlRequestWithContext(", "HTTP completion uses captured context");
        require(source, "logPromptSendStage(", "Send phase logging remains present");

        String postBody = methodBody(source, "postPromptComposerSubmit");
        require(postBody, "PromptComposerRequestContext requestContext = submit.requestContext.withTraceId(",
                "async Send attempt must derive from queue snapshot");
        require(postBody, "requestContext", "attempt context must cross POST callback boundary");

        String sendLogBody = methodBody(source, "logPromptSendStage");
        for (String field : new String[]{"requestContext.actionId", "requestContext.tapId",
                "requestContext.traceId", "requestContext.target", "requestContext.idempotencyRef"}) {
            require(sendLogBody, field, "final phase log uses immutable safe field");
        }
        reject(sendLogBody, "currentApkActionIdForLog()", "Send phase log must not reread mutable action id");
        reject(sendLogBody, "currentApkTapIdForLog()", "Send phase log must not reread mutable tap id");
        reject(sendLogBody, "submitIdempotencyKey", "Send phase log must never include the raw key");
    }

    private static String methodBody(String source, String name) {
        int nameIndex = source.indexOf("private void " + name + "(");
        if (nameIndex < 0) {
            throw new AssertionError("missing method " + name);
        }
        int brace = source.indexOf('{', nameIndex);
        if (brace < 0) {
            throw new AssertionError("missing body for " + name);
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
        throw new AssertionError("unterminated method " + name);
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

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private PromptComposerImmutableRequestContextTest() {
    }
}
