package com.kaleeb.wezterm;

import java.util.ArrayDeque;

public final class PromptComposerLateCallbackOwnershipTest {
    private static final class Submit
            implements MainActivity.PromptComposerRapidOrderedSendBehavior.Submit {
        final String fingerprint;

        Submit(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        @Override public String stableTargetKey() { return "@13"; }
        @Override public String submittedValue() { return fingerprint; }
        @Override public String submitFingerprint() { return fingerprint; }
        @Override public String intentId() { return "intent-" + fingerprint; }
        @Override public String idempotencyRef() { return "ref-" + fingerprint; }
    }

    private static final class Harness {
        final ArrayDeque<Submit> queue = new ArrayDeque<>();
        boolean inFlight;
        String activeFingerprint = "";
        int clearCount;
        int restoreCount;
        int afterSuccessCount;
        int afterFailureCount;

        void enqueue(Submit submit) {
            queue.add(submit);
            drain();
        }

        boolean success(Submit submit) {
            if (!owns(submit)) {
                return false;
            }
            finish(submit);
            clearCount++;
            afterSuccessCount++;
            return true;
        }

        boolean failure(Submit submit) {
            if (!owns(submit)) {
                return false;
            }
            finish(submit);
            restoreCount++;
            afterFailureCount++;
            return true;
        }

        private boolean owns(Submit submit) {
            return MainActivity.PromptComposerRapidOrderedSendBehavior.ownsFinalCallback(
                    inFlight,
                    activeFingerprint,
                    submit,
                    queue.peek()
            );
        }

        private void finish(Submit submit) {
            inFlight = false;
            activeFingerprint = "";
            queue.remove(submit);
            drain();
        }

        private void drain() {
            if (!inFlight && queue.peek() != null) {
                inFlight = true;
                activeFingerprint = queue.peek().fingerprint;
            }
        }
    }

    public static void main(String[] args) {
        failureThenLateSuccessCannotClearOrRunSuccess();
        successThenLateFailureCannotRestoreOrRunFailure();
        duplicateSuccessIsSideEffectOnce();
        normalFifoStillDrains();
        System.out.println("PROMPT_COMPOSER_LATE_CALLBACK_OWNERSHIP_PASS");
    }

    private static void failureThenLateSuccessCannotClearOrRunSuccess() {
        Harness harness = new Harness();
        Submit first = new Submit("first");
        harness.enqueue(first);
        require(harness.failure(first), "current failure must own finalization");
        require(!harness.success(first), "late success must be rejected");
        require(harness.restoreCount == 1, "failure restore must happen once");
        require(harness.clearCount == 0, "late success must not clear restored draft");
        require(harness.afterFailureCount == 1, "afterFailure must happen once");
        require(harness.afterSuccessCount == 0, "late afterSuccess must not run");
    }

    private static void successThenLateFailureCannotRestoreOrRunFailure() {
        Harness harness = new Harness();
        Submit first = new Submit("first");
        harness.enqueue(first);
        require(harness.success(first), "current success must own finalization");
        require(!harness.failure(first), "late failure must be rejected");
        require(harness.clearCount == 1, "success clear must happen once");
        require(harness.restoreCount == 0, "late failure must not restore");
        require(harness.afterSuccessCount == 1, "afterSuccess must happen once");
        require(harness.afterFailureCount == 0, "late afterFailure must not run");
    }

    private static void duplicateSuccessIsSideEffectOnce() {
        Harness harness = new Harness();
        Submit first = new Submit("first");
        harness.enqueue(first);
        require(harness.success(first), "first success must own finalization");
        require(!harness.success(first), "duplicate success must be rejected");
        require(harness.clearCount == 1, "success clear must happen once");
        require(harness.afterSuccessCount == 1, "duplicate afterSuccess must not run");
    }

    private static void normalFifoStillDrains() {
        Harness harness = new Harness();
        Submit first = new Submit("first");
        Submit second = new Submit("second");
        harness.enqueue(first);
        harness.enqueue(second);
        require(!MainActivity.PromptComposerRapidOrderedSendBehavior.ownsFinalCallback(
                harness.inFlight,
                harness.activeFingerprint,
                second,
                harness.queue.peek()
        ), "queued second item must not claim first callback slot");
        require(harness.success(first), "first FIFO success must finalize");
        require(harness.success(second), "second FIFO item must drain normally");
        require(harness.clearCount == 2, "each normal Send must clear exactly once");
        require(harness.afterSuccessCount == 2, "each normal Send callback must run once");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private PromptComposerLateCallbackOwnershipTest() {
    }
}
