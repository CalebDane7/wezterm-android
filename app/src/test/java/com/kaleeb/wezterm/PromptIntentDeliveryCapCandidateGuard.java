package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure executable guard for one visible composer intent and bounded delivery.
 *
 * <p>The model has no Android dependency. Source-shape checks keep the candidate
 * wiring tied to the same invariants exercised by the state-machine scenarios.
 */
public final class PromptIntentDeliveryCapCandidateGuard {
    private static final int MAX_DELIVERY_BLOCK_RETRIES = 3;
    private static final long DELIVERY_BLOCK_RETRY_MS = 1_700L;
    private static final long ACTIVE_SWITCH_TITLE_DEADLINE_MS = 5_000L;

    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 1, "expected MainActivity.java path");
        Path mainActivity = Paths.get(args[0]);
        String source = new String(Files.readAllBytes(mainActivity), StandardCharsets.UTF_8);

        visibleActivationOwnsOneStableIdentity();
        deliveryBlockCapsAndReleasesFifoOnce();
        productionWiresThePureContract(source);

        System.out.println("C06_PROMPT_INTENT_DELIVERY_CAP_PASS");
        System.out.println("sourceSha256=" + sha256(mainActivity));
        System.out.println("hiddenComposerRejected=true");
        System.out.println("duplicateCallbacksOneIntent=true");
        System.out.println("laterDraftGenerationDistinct=true");
        System.out.println("deliveryAttempts=4");
        System.out.println("sameKeyRetries=3");
        System.out.println("terminalReleaseAtMs=5100");
        System.out.println("terminalRestoreExactRevisionOnce=true");
        System.out.println("fifoReleaseOnce=true");
    }

    private static void visibleActivationOwnsOneStableIdentity() {
        VisibleIntentAuthority authority = new VisibleIntentAuthority();
        authority.show("@41");
        authority.edit();

        UiIntent first = authority.activate();
        UiIntent duplicateCallback = authority.activate();
        assertSame(first, duplicateCallback, "duplicate callback must reuse one UI intent");
        assertEquals(first.idempotencyKey, duplicateCallback.idempotencyKey,
                "duplicate callback must reuse one idempotency key");

        authority.hide();
        assertEquals(null, authority.activate(), "hidden composer callback must be rejected");

        authority.show("@41");
        authority.edit();
        UiIntent laterDraft = authority.activate();
        assertNotEquals(first.intentId, laterDraft.intentId,
                "later deliberate draft generation needs a new UI intent");
        assertNotEquals(first.idempotencyKey, laterDraft.idempotencyKey,
                "later deliberate draft generation needs a new idempotency key");
    }

    private static void deliveryBlockCapsAndReleasesFifoOnce() {
        VisibleIntentAuthority authority = new VisibleIntentAuthority();
        authority.show("@7");
        authority.edit();
        UiIntent firstIntent = authority.activate();

        DeliveryQueue queue = new DeliveryQueue();
        Submit first = new Submit(firstIntent, 71L);
        assertTrue(queue.admit(first), "first intent must be admitted");
        assertFalse(queue.admit(new Submit(firstIntent, 71L)),
                "duplicate callback must not create a second queue item");

        authority.show("@8");
        authority.edit();
        UiIntent secondIntent = authority.activate();
        Submit second = new Submit(secondIntent, 72L);
        assertTrue(queue.admit(second), "later draft generation must remain distinct");
        assertSame(first, queue.active, "FIFO head must remain the first submit");

        assertEquals(DeliveryDisposition.RETRY_SCHEDULED, queue.deliveryBlocked(first),
                "first block schedules retry one");
        assertEquals(
                DeliveryDisposition.RETRY_SCHEDULED,
                queue.deliveryBlocked(first),
                "second block schedules retry two");
        assertEquals(
                DeliveryDisposition.RETRY_SCHEDULED,
                queue.deliveryBlocked(first),
                "third block schedules retry three");
        long terminalReleaseAtMs = DELIVERY_BLOCK_RETRY_MS * MAX_DELIVERY_BLOCK_RETRIES;
        assertTrue(
                terminalReleaseAtMs >= ACTIVE_SWITCH_TITLE_DEADLINE_MS,
                "bounded retry horizon must not precede the Active-switch title deadline"
        );
        assertSame(first, queue.active, "5000ms cannot precede the scheduled 5100ms retry");
        assertEquals(0, first.restoreCount, "5000ms cannot restore before the final callback");
        assertEquals(0, first.releaseCount, "5000ms cannot release before the final callback");
        assertEquals(0, second.attempts, "5000ms cannot start the next FIFO item");
        assertEquals(
                DeliveryDisposition.TERMINAL_FAILED,
                queue.deliveryBlocked(first),
                "fourth block terminates at the scheduled 5100ms horizon"
        );

        assertEquals(4, first.attempts, "initial attempt plus three retries");
        assertEquals(3, first.deliveryBlockRetries, "retry cap");
        assertEquals(1, first.restoreCount, "exact draft restored once");
        assertEquals(71L, first.restoredRevision, "exact admission revision restored");
        assertEquals(1, first.releaseCount, "FIFO head released once");
        assertSame(second, queue.active, "second submit starts only after terminal release");
        assertEquals(1, second.attempts, "next FIFO item starts once");

        Set<String> firstKeys = new HashSet<>(first.transmittedKeys);
        assertEquals(1, firstKeys.size(), "all retry attempts must reuse one key");
        assertTrue(firstKeys.contains(firstIntent.idempotencyKey), "retry key identity");
        assertNotEquals(firstIntent.idempotencyKey, secondIntent.idempotencyKey,
                "later generation must not inherit failed key");

        assertEquals(
                DeliveryDisposition.STALE_IGNORED,
                queue.deliveryBlocked(first),
                "late duplicate terminal callback must be ignored");
        assertEquals(1, first.restoreCount, "late callback cannot restore twice");
        assertEquals(1, first.releaseCount, "late callback cannot release twice");
        assertEquals(1, second.attempts, "late callback cannot redrain FIFO");
    }

    private static void productionWiresThePureContract(String source) {
        assertTrue(
                source.contains("PROMPT_COMPOSER_DELIVERY_BLOCK_MAX_RETRIES = 3"),
                "production must declare the three-retry cap"
        );
        assertTrue(
                source.contains("PROMPT_COMPOSER_DELIVERY_BLOCK_RETRY_MS = 1700"),
                "production must space three retries to a 5100ms terminal horizon"
        );
        assertTrue(
                source.contains("ACTIVE_SWITCH_OPTIMISTIC_TITLE_MAX_AGE_MS = 5000"),
                "production guard must remain tied to the 5000ms switch-title deadline"
        );

        String docked = methodBody(source, "private void submitDockedPrompt()");
        assertBefore(
                docked,
                "promptComposerInput == null || !isDockedPromptComposerVisible()",
                "promptComposerInput.getText().toString()",
                "hidden composer must be rejected before reading/submitting its editor"
        );
        assertTrue(
                docked.contains("visiblePromptComposerSubmitIntent(stableTargetKey)"),
                "visible submit must bind an intent before queueing"
        );

        String visibleIntent = methodBody(
                source,
                "private PromptComposerVisibleSubmitIntent visiblePromptComposerSubmitIntent("
        );
        assertTrue(
                visibleIntent.contains("promptComposerVisibleSubmitIntent.owns("),
                "same visible draft generation must reuse its intent"
        );
        assertTrue(
                visibleIntent.contains("createPromptComposerSubmitIntent("),
                "a new draft generation must allocate a new intent"
        );

        String safeSubmit = methodBody(
                source,
                "private void submitSafePrompt(\n"
                        + "            String text,\n"
                        + "            String successToast,\n"
                        + "            String targetKey,\n"
                        + "            Runnable afterSuccess,\n"
                        + "            Runnable afterFailure"
        );
        assertTrue(
                safeSubmit.contains("resolvedSubmitIntent.idempotencyKey"),
                "queue item must carry the visible intent's idempotency key"
        );
        assertTrue(
                safeSubmit.contains("resolvedSubmitIntent.intentId"),
                "duplicate fingerprint must carry the visible UI intent"
        );

        String deliveryBlock = methodBody(
                source,
                "private void holdPromptComposerSubmitForDeliveryBlock("
        );
        assertBefore(
                deliveryBlock,
                "submit.deliveryBlockRetryCount\n"
                        + "                >= PROMPT_COMPOSER_DELIVERY_BLOCK_MAX_RETRIES",
                "submit.deliveryBlockRetryCount += 1;",
                "cap must be checked before scheduling another retry"
        );
        assertTrue(
                deliveryBlock.contains("failPromptComposerDeliveryBlockOnce(submit)"),
                "retry exhaustion must use the once-only terminal owner"
        );
        assertEquals(
                1,
                occurrences(deliveryBlock, "postPromptComposerSubmit(submit);"),
                "delivery block owns one same-submit retry callback"
        );

        String terminalFail = methodBody(
                source,
                "private boolean failPromptComposerDeliveryBlockOnce("
        );
        assertTrue(
                terminalFail.contains("promptComposerSubmitQueue.peek() != submit"),
                "terminal callback must own the exact FIFO head"
        );
        assertTrue(
                terminalFail.contains("restorePromptComposerAfterFailedSubmit(")
                        && terminalFail.contains("submit.draftRevision"),
                "terminal delivery block must restore the exact draft revision"
        );
        assertBefore(
                terminalFail,
                "restorePromptComposerAfterFailedSubmit(",
                "failPromptComposerSubmit(submit);",
                "exact-revision recovery must happen before FIFO release"
        );
        String fail = methodBody(source, "private void failPromptComposerSubmit(");
        assertEquals(
                1,
                occurrences(fail, "drainPromptComposerSubmitQueue();"),
                "one owned terminal transition must redrain FIFO once"
        );
    }

    private enum DeliveryDisposition {
        RETRY_SCHEDULED,
        TERMINAL_FAILED,
        STALE_IGNORED
    }

    private static final class UiIntent {
        final long draftGeneration;
        final String target;
        final String intentId;
        final String idempotencyKey;

        UiIntent(long draftGeneration, String target, int sequence) {
            this.draftGeneration = draftGeneration;
            this.target = target;
            this.intentId = "intent-" + sequence;
            this.idempotencyKey = "key-" + sequence;
        }

        boolean owns(long currentGeneration, String currentTarget) {
            return draftGeneration == currentGeneration && target.equals(currentTarget);
        }
    }

    private static final class VisibleIntentAuthority {
        private boolean visible;
        private long draftGeneration;
        private String target = "";
        private int sequence;
        private UiIntent cached;

        void show(String stableTarget) {
            visible = true;
            target = stableTarget;
            draftGeneration += 1;
        }

        void edit() {
            assertTrue(visible, "edit requires visible composer");
            draftGeneration += 1;
        }

        void hide() {
            visible = false;
        }

        UiIntent activate() {
            if (!visible) {
                return null;
            }
            if (cached != null && cached.owns(draftGeneration, target)) {
                return cached;
            }
            cached = new UiIntent(draftGeneration, target, ++sequence);
            return cached;
        }
    }

    private static final class Submit {
        final UiIntent intent;
        final long draftRevision;
        final List<String> transmittedKeys = new ArrayList<>();
        int attempts;
        int deliveryBlockRetries;
        int restoreCount;
        long restoredRevision = -1L;
        int releaseCount;

        Submit(UiIntent intent, long draftRevision) {
            this.intent = intent;
            this.draftRevision = draftRevision;
        }

        String fingerprint() {
            return intent.target + "\n" + intent.intentId;
        }
    }

    private static final class DeliveryQueue {
        final ArrayDeque<Submit> queue = new ArrayDeque<>();
        Submit active;

        boolean admit(Submit submit) {
            if (submit == null || duplicate(submit)) {
                return false;
            }
            queue.add(submit);
            drainOnce();
            return true;
        }

        DeliveryDisposition deliveryBlocked(Submit submit) {
            if (submit == null || active != submit || queue.peek() != submit) {
                return DeliveryDisposition.STALE_IGNORED;
            }
            if (submit.deliveryBlockRetries >= MAX_DELIVERY_BLOCK_RETRIES) {
                return terminalFail(submit);
            }
            submit.deliveryBlockRetries += 1;
            transmit(submit);
            return DeliveryDisposition.RETRY_SCHEDULED;
        }

        private DeliveryDisposition terminalFail(Submit submit) {
            submit.restoreCount += 1;
            submit.restoredRevision = submit.draftRevision;
            active = null;
            Submit released = queue.poll();
            assertSame(submit, released, "terminal release must poll exact FIFO head");
            submit.releaseCount += 1;
            drainOnce();
            return DeliveryDisposition.TERMINAL_FAILED;
        }

        private boolean duplicate(Submit candidate) {
            if (active != null && active.fingerprint().equals(candidate.fingerprint())) {
                return true;
            }
            for (Submit queued : queue) {
                if (queued.fingerprint().equals(candidate.fingerprint())) {
                    return true;
                }
            }
            return false;
        }

        private void drainOnce() {
            if (active != null || queue.isEmpty()) {
                return;
            }
            active = queue.peek();
            transmit(active);
        }

        private void transmit(Submit submit) {
            submit.attempts += 1;
            submit.transmittedKeys.add(submit.intent.idempotencyKey);
        }
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

    private static int occurrences(String source, String needle) {
        int count = 0;
        int offset = 0;
        while (true) {
            int found = source.indexOf(needle, offset);
            if (found < 0) {
                return count;
            }
            count += 1;
            offset = found + needle.length();
        }
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

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        StringBuilder value = new StringBuilder(hash.length * 2);
        for (byte item : hash) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (first == null ? second == null : first.equals(second)) {
            throw new AssertionError(message + ": both=" + first);
        }
    }

    private PromptIntentDeliveryCapCandidateGuard() {
    }
}
