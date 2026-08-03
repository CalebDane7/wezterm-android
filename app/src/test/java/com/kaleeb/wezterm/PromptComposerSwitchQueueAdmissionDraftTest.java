package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Source-level old-red for switch-time Send admission and target-local draft durability.
 *
 * <p>This guard deliberately excludes capped idempotency reconciliation, which has a
 * separate owner. It freezes the client boundary before that mechanism: a tap with a
 * stable selected target must receive local queue feedback immediately, while the
 * immutable queued operation and durable draft survive until a definitive callback.
 */
public final class PromptComposerSwitchQueueAdmissionDraftTest {
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );

    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        List<String> diagnosticLedger = new ArrayList<>();
        AssertionError firstFailure = null;

        firstFailure = runCase(
                "switch-local-admission",
                diagnosticLedger,
                firstFailure,
                () -> switchSettlingCannotPreemptLocalAdmission(source)
        );
        firstFailure = runCase(
                "draft-durability",
                diagnosticLedger,
                firstFailure,
                () -> queuedDraftRemainsDurableUntilDefinitiveCallback(source)
        );
        firstFailure = runCase(
                "immutable-send",
                diagnosticLedger,
                firstFailure,
                () -> queuedOperationOwnsTargetBodyAndIdempotency(source)
        );
        firstFailure = runCase(
                "tap-and-callback",
                diagnosticLedger,
                firstFailure,
                () -> tapAndCallbackOwnersRemainConnected(source)
        );

        if (firstFailure != null) {
            System.err.println(
                    "PROMPT_COMPOSER_SWITCH_QUEUE_ADMISSION_DRAFT_RED"
                            + " firstInvariant=" + safeFailure(firstFailure.getMessage())
            );
            for (String event : diagnosticLedger) {
                System.err.println(event);
            }
            throw firstFailure;
        }
        System.out.println("PROMPT_COMPOSER_SWITCH_QUEUE_ADMISSION_DRAFT_PASS");
    }

    private static void switchSettlingCannotPreemptLocalAdmission(String source) {
        String submit = methodBody(
                source,
                "private void submitSafePrompt(\n"
                        + "            String text,\n"
                        + "            String successToast,\n"
                        + "            String targetKey,\n"
                        + "            Runnable afterSuccess,\n"
                        + "            Runnable afterFailure"
        );

        // WHY: delivery/body settling may delay drain, but must not reject the user's
        // local Send tap before feedback, immutable intent creation, and FIFO admission.
        reject(
                submit,
                "activeSwitchTitleLifecycle.blocksSendFor(stableTargetKey)",
                "switch-settling-preempts-local-admission"
        );
        assertOrdered(
                submit,
                "markApkFirstLocalFeedback(\"send-local-queue\"",
                "PromptComposerRequestContext.capture(",
                "local-feedback-before-immutable-capture"
        );
        assertOrdered(
                submit,
                "PromptComposerRequestContext.capture(",
                "enqueuePromptComposerSubmit(submit)",
                "immutable-capture-before-queue-admission"
        );
    }

    private static void queuedDraftRemainsDurableUntilDefinitiveCallback(String source) {
        String localFeedback = methodBody(
                source,
                "private void hideDockedPromptComposerAfterQueuedSubmit("
        );
        String success = methodBody(
                source,
                "private boolean clearPromptComposerAfterSuccessfulSubmit("
        );
        String failure = methodBody(
                source,
                "private void restorePromptComposerAfterFailedSubmit("
        );

        // WHY: clearing the visible editor is immediate feedback; deleting the
        // SharedPreferences draft before server truth turns process death into data loss.
        reject(
                localFeedback,
                "forgetPromptComposerDraft(",
                "queue-feedback-forgets-durable-draft-before-success"
        );
        require(
                success,
                "forgetPromptComposerDraft(stableTargetKey)",
                "definitive-success-owns-durable-draft-clear"
        );
        require(
                failure,
                "rememberPromptComposerDraft(stableTargetKey, submittedValue)",
                "terminal-failure-restores-target-local-draft"
        );
    }

    private static void queuedOperationOwnsTargetBodyAndIdempotency(String source) {
        String queuedSubmit = classBody(source, "private static final class PromptComposerQueuedSubmit");
        require(queuedSubmit, "final String stableTargetKey", "queued-target-is-immutable");
        require(queuedSubmit, "final String value", "queued-body-is-immutable");
        require(queuedSubmit, "final String submitIdempotencyKey", "queued-key-is-immutable");
        require(queuedSubmit, "final PromptComposerRequestContext requestContext",
                "queued-request-context-is-immutable");

        String post = methodBody(source, "private void postPromptComposerSubmit(");
        require(post, "String stableTargetKey = submit.stableTargetKey",
                "callback-uses-queued-target");
        require(post, "String value = submit.value", "callback-uses-queued-body");
        require(post, "String submitIdempotencyKey = submit.submitIdempotencyKey",
                "callback-uses-queued-key");
        require(post, "postTextWithIdempotency(", "request-uses-idempotent-transport");
        require(
                post,
                "appendStableWindowQuery(\"/submit-text?resize=0\", stableTargetKey)",
                "request-keeps-stable-selected-target"
        );
    }

    private static void tapAndCallbackOwnersRemainConnected(String source) {
        String touch = methodBody(source, "private boolean handleTerminalTouch(");
        require(touch, "showDockedPromptComposer(\"tap-up\")",
                "terminal-body-tap-opens-native-composer");

        String show = methodBody(source, "private void showDockedPromptComposer(");
        require(show, "resetPromptComposerLocalDraftForCurrentTarget()",
                "composer-restores-target-local-draft");
        require(show, "restoreDockedPromptComposerFocus(reason)",
                "composer-provides-immediate-native-focus");

        String complete = methodBody(source, "private void completePromptComposerSubmit(");
        String fail = methodBody(source, "private void failPromptComposerSubmit(");
        require(complete, "drainPromptComposerSubmitQueue()",
                "success-releases-next-normal-send");
        require(fail, "drainPromptComposerSubmitQueue()",
                "failure-releases-next-normal-send");
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
            diagnosticLedger.add(
                    "CASE case=" + caseName
                            + " disposition=violation invariant="
                            + safeFailure(failure.getMessage())
            );
            if (firstFailure == null) {
                return failure;
            }
        }
        return firstFailure;
    }

    private static String methodBody(String source, String marker) {
        return blockBody(source, marker, "method");
    }

    private static String classBody(String source, String marker) {
        return blockBody(source, marker, "class");
    }

    private static String blockBody(String source, String marker, String kind) {
        int markerIndex = source.indexOf(marker);
        if (markerIndex < 0) {
            throw new AssertionError(kind + "-marker-missing-" + safeFailure(marker));
        }
        int openBrace = source.indexOf('{', markerIndex + marker.length());
        if (openBrace < 0) {
            throw new AssertionError(kind + "-open-brace-missing-" + safeFailure(marker));
        }
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, index);
                }
            }
        }
        throw new AssertionError(kind + "-close-brace-missing-" + safeFailure(marker));
    }

    private static void assertOrdered(
            String value,
            String firstNeedle,
            String secondNeedle,
            String invariant
    ) {
        int firstIndex = value.indexOf(firstNeedle);
        int secondIndex = value.indexOf(secondNeedle);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(invariant);
        }
    }

    private static void require(String value, String needle, String invariant) {
        if (!value.contains(needle)) {
            throw new AssertionError(invariant);
        }
    }

    private static void reject(String value, String needle, String invariant) {
        if (value.contains(needle)) {
            throw new AssertionError(invariant);
        }
    }

    private static String safeFailure(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "unknown";
        }
        return message.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    @FunctionalInterface
    private interface CheckedCase {
        void run() throws Exception;
    }

    private PromptComposerSwitchQueueAdmissionDraftTest() {
    }
}
