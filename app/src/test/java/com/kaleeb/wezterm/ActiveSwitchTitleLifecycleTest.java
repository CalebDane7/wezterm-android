package com.kaleeb.wezterm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Pure executable guard for the native Active-session title/body transition.
 *
 * <p>This deliberately has a main entry point so the repository's real javac
 * toolchain can run it without adding a second build system. The production
 * reducer lives in MainActivity.java but has no Android/View/Looper dependency.
 */
public final class ActiveSwitchTitleLifecycleTest {
    private static final long START_MS = 1_000L;
    private static final String BODY_SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_BODY_SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    public static void main(String[] args) {
        deadlineBlocksAndKeepsLastGood();
        deadlineRetainsGenerationForLateConfirmationAndLocalRetapReplaces();
        confirmedSameWindowLocalRetapBeginsNewGenerationAndRejectsOldEvidence();
        replacementIgnoresLateGeneration();
        sameWindowCurrentGenerationOmittedEventIdentityCommits();
        reusedWindowRequiresExactCurrentSessionThread();
        errorsCannotLeaveVisiblePrefix();
        centralTitleHoldCannotCommit();
        exactFrameAndDomCommitAtomically();
        collisionResistantBodyReceiptRequired();
        confirmedFieldsPublishOnlyAfterAtomicCommit();
        unresolvedSameTargetTitleDoesNotDemote();
        stableComposerControlStaysEnabledWhileDeliverySettles();
        productionWiresStableComposerControl();
        offTargetCallbacksCannotMutateVisibleDraftOwner();
        lateFailureCannotOverwriteNewerSameTargetDraft();
        exactDraftRevisionOwnsAcknowledgement();
        queuedDraftPlaceholderCannotEraseDurableRevision();
        localFeedbackPrecedesNetworkAndSuccessOwnsClear();
        commitOnIdentityUnstucksSwitchOnUnacceptedBody();
        unacceptedCommitSchedulesFullFrameRefill();
        inProgress409IsClassifiedAsIdempotencyRetryNotFailure();
        System.out.println("ACTIVE_SWITCH_TITLE_LIFECYCLE_PASS");
    }

    private static void deadlineBlocksAndKeepsLastGood() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);

        assertDisposition(
                "frame-rejected-status",
                onFrame(lifecycle, generation, b, 409, false, 0, 0, "")
        );
        assertDisposition(
                "frame-rejected-empty",
                onFrame(lifecycle, generation, b, 200, true, 0, 0, "")
        );
        assertDisposition(
                "event-identity-mismatch",
                onFrame(
                        lifecycle,
                        generation,
                        identity("@9", "session-b", "thread-b"),
                        200,
                        true,
                        10,
                        1,
                        "hash-b"
                )
        );
        assertDisposition(
                "frame-candidate",
                onFrame(lifecycle, generation, b, 200, true, 10, 1, "hash-b")
        );
        assertDisposition(
                "dom-rejected-hash",
                onDom(
                        lifecycle,
                        generation,
                        b,
                        true,
                        10,
                        1,
                        "hash-b",
                        "different-dom-hash",
                        "B"
                )
        );
        assertDisposition(
                "deadline-not-reached",
                lifecycle.onDeadline(
                        generation,
                        START_MS + MainActivity.ACTIVE_SWITCH_COMMIT_DEADLINE_MS - 1
                )
        );
        assertEquals("A", lifecycle.lastGoodTitle(), "last-good title before deadline");
        assertEquals("hash-a", lifecycle.committedFrameHash(), "last-good body hash before deadline");
        assertTrue(lifecycle.visibleTitle().startsWith("Switching to "), "pending prefix before deadline");

        assertDisposition(
                "commit-deadline-no-frame-dom",
                lifecycle.onDeadline(
                        generation,
                        START_MS + MainActivity.ACTIVE_SWITCH_COMMIT_DEADLINE_MS
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.BLOCKED_KEEP_LAST_GOOD,
                lifecycle.phase(),
                "deadline phase"
        );
        assertEquals("A", lifecycle.visibleTitle(), "deadline keeps last-good title");
        assertFalse(lifecycle.visibleTitle().startsWith("Switching to "), "deadline clears prefix");
        assertTrue(lifecycle.blocksSendFor("@2"), "uncommitted B must not enable Send");
        assertDisposition(
                "central-title-held",
                lifecycle.onCentralTitle(generation, b, "B central after deadline")
        );
        assertEquals("A", lifecycle.visibleTitle(), "blocked central refresh cannot replace last-good title");
    }

    private static void replacementIgnoresLateGeneration() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        MainActivity.ActiveSwitchIdentity c = identity("@3", "session-c", "thread-c");
        long generationB = lifecycle.begin(b, "B", START_MS);
        long generationC = lifecycle.begin(c, "C", START_MS + 20);

        assertDisposition(
                "late-generation-ignored",
                onFrame(lifecycle, generationB, b, 200, true, 10, 1, "hash-b")
        );
        assertDisposition(
                "late-generation-ignored",
                onDom(
                        lifecycle,
                        generationB,
                        b,
                        true,
                        10,
                        1,
                        "hash-b",
                        "hash-b",
                        "B"
                )
        );
        assertDisposition(
                "late-generation-ignored",
                lifecycle.onDeadline(
                        generationB,
                        START_MS + MainActivity.ACTIVE_SWITCH_COMMIT_DEADLINE_MS + 20
                )
        );
        assertEquals(generationC, lifecycle.generation(), "C generation remains current");
        assertEquals(MainActivity.ActiveSwitchPhase.PENDING_FRAME, lifecycle.phase(), "C remains pending");
        assertEquals("Switching to C", lifecycle.visibleTitle(), "C transition remains visible");
    }

    private static void deadlineRetainsGenerationForLateConfirmationAndLocalRetapReplaces() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(
                "commit-deadline-no-frame-dom",
                lifecycle.onDeadline(
                        generation,
                        START_MS + MainActivity.ACTIVE_SWITCH_COMMIT_DEADLINE_MS
                )
        );

        assertTrue(
                MainActivity.ActiveSwitchTitleLifecycle
                        .keepsGenerationForSameTargetConfirmation("select-live"),
                "late same-target server confirmation must retain the tap generation"
        );
        assertFalse(
                MainActivity.ActiveSwitchTitleLifecycle
                        .keepsGenerationForSameTargetConfirmation("select-live-local"),
                "a fresh local row tap must be allowed to replace the generation"
        );
        assertDisposition(
                "central-title-held",
                lifecycle.onCentralTitle(generation, b, "B confirmed late")
        );
        assertEquals(generation, lifecycle.generation(), "late confirmation retains generation");
        long replacement = lifecycle.begin(b, "B retapped", START_MS + 10_000);
        assertTrue(replacement > generation, "fresh local retap replaces blocked generation");

        String source = mainActivitySource();
        String deadline = methodBody(
                source,
                "private void finishPendingActiveSwitchTitleDeadline("
        );
        assertFalse(
                deadline.contains("clearPendingActiveSwitchTitleFields();"),
                "deadline must retain target/generation ownership for late confirmation"
        );
        String remember = methodBody(
                source,
                "private void rememberSelectedPhoneWindow(\n"
                        + "            int index,\n"
                        + "            String windowId,\n"
                        + "            String title,\n"
                        + "            String reason,\n"
                        + "            String sessionId,\n"
                        + "            String threadId"
        );
        assertTrue(
                remember.contains(
                        "keepsGenerationForSameTargetConfirmation(reason)"
                ),
                "production must distinguish server confirmation from a new local tap"
        );
    }

    private static void confirmedSameWindowLocalRetapBeginsNewGenerationAndRejectsOldEvidence() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity firstIdentity =
                identity("@2", "session-old", "thread-old");
        long firstGeneration = lifecycle.begin(firstIdentity, "B", START_MS);
        assertDisposition(
                "frame-candidate",
                onFrame(
                        lifecycle,
                        firstGeneration,
                        firstIdentity,
                        200,
                        true,
                        10,
                        1,
                        "hash-b-old"
                )
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                onDom(
                        lifecycle,
                        firstGeneration,
                        firstIdentity,
                        true,
                        10,
                        1,
                        "hash-b-old",
                        "hash-b-old",
                        "B old"
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.COMMITTED,
                lifecycle.phase(),
                "first same-window selection is already confirmed"
        );

        MainActivity.ActiveSwitchIdentity retapIdentity =
                identity("@2", "session-new", "thread-new");
        long retapGeneration = lifecycle.begin(retapIdentity, "B retapped", START_MS + 20);
        assertTrue(
                retapGeneration > firstGeneration,
                "fresh same-window local retap must begin a new lifecycle generation"
        );
        assertDisposition(
                "late-generation-ignored",
                onFrame(
                        lifecycle,
                        firstGeneration,
                        firstIdentity,
                        200,
                        true,
                        10,
                        1,
                        "hash-b-old"
                )
        );
        assertDisposition(
                "event-identity-mismatch",
                onFrame(
                        lifecycle,
                        retapGeneration,
                        firstIdentity,
                        200,
                        true,
                        10,
                        1,
                        "hash-b-old"
                )
        );
        assertDisposition(
                "frame-candidate",
                onFrame(
                        lifecycle,
                        retapGeneration,
                        retapIdentity,
                        200,
                        true,
                        10,
                        1,
                        "hash-b-new"
                )
        );

        String source = mainActivitySource();
        String remember = methodBody(
                source,
                "private void rememberSelectedPhoneWindow(\n"
                        + "            int index,\n"
                        + "            String windowId,\n"
                        + "            String title,\n"
                        + "            String reason,\n"
                        + "            String sessionId,\n"
                        + "            String threadId"
        );
        assertTrue(
                remember.contains(
                        "if (isSelectLiveTitleCommitReason(reason)\n"
                                + "                && (!ActiveSwitchTitleLifecycle."
                                + "keepsGenerationForSameTargetConfirmation(reason)\n"
                                + "                || !selectedPhoneWindowId.equals("
                                + "confirmedCaptureRendererFrameWindowTargetKey)))"
                ),
                "fresh local retap must bypass the already-confirmed same-window short circuit"
        );
        String begin = methodBody(
                source,
                "private void beginPendingActiveSwitchTitleCommit(\n"
                        + "            String windowId,"
        );
        assertTrue(
                begin.contains("confirmedCaptureRendererWindowTargetKey = \"\";")
                        && begin.contains(
                                "confirmedCaptureRendererFrameWindowTargetKey = \"\";"
                        ),
                "fresh generation must revoke both previously published target proofs"
        );
    }

    private static void sameWindowCurrentGenerationOmittedEventIdentityCommits() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity selected = identity(
                "@6",
                "session-selected",
                "thread-selected"
        );
        MainActivity.ActiveSwitchIdentity omittedResponseIdentity = identity("@6", "", "");
        long generation = lifecycle.begin(selected, "Selected", START_MS);

        assertDisposition(
                "late-generation-ignored",
                onFrame(
                        lifecycle,
                        generation - 1,
                        omittedResponseIdentity,
                        200,
                        true,
                        2_508,
                        88,
                        "hash-selected"
                )
        );
        assertDisposition(
                "event-identity-mismatch",
                onFrame(
                        lifecycle,
                        generation,
                        identity("@7", "", ""),
                        200,
                        true,
                        2_508,
                        88,
                        "hash-selected"
                )
        );
        assertDisposition(
                "event-identity-mismatch",
                lifecycle.bindIdentity(
                        generation,
                        identity("@6", "session-conflict", "thread-selected")
                )
        );
        assertDisposition(
                "event-identity-mismatch",
                lifecycle.bindIdentity(
                        generation,
                        identity("@6", "session-selected", "thread-conflict")
                )
        );

        // The live /terminal-frame and DOM callbacks carry generation + stable
        // window identity but omit optional session/thread fields. Bind those
        // omissions to the already-pinned selected identity; never treat them
        // as a conflicting reused-window identity.
        assertDisposition(
                "select-identity-bound",
                lifecycle.bindIdentity(generation, omittedResponseIdentity)
        );
        assertEquals(
                "session-selected",
                lifecycle.identity().sessionId,
                "omitted response keeps pinned session"
        );
        assertEquals(
                "thread-selected",
                lifecycle.identity().threadId,
                "omitted response keeps pinned thread"
        );
        MainActivity.ActiveSwitchIdentity normalizedResponseIdentity = lifecycle.identity();
        assertDisposition(
                "frame-candidate",
                onFrame(
                        lifecycle,
                        generation,
                        normalizedResponseIdentity,
                        200,
                        true,
                        2_508,
                        88,
                        "hash-selected"
                )
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                onDom(
                        lifecycle,
                        generation,
                        normalizedResponseIdentity,
                        true,
                        2_508,
                        88,
                        "hash-selected",
                        "hash-selected",
                        "Selected canonical"
                )
        );
        assertEquals("Selected canonical", lifecycle.visibleTitle(), "omitted identity commits title");
        assertEquals("hash-selected", lifecycle.committedFrameHash(), "omitted identity commits body");
        assertFalse(lifecycle.blocksSendFor("@6"), "coherent omitted-identity commit enables Send");
    }

    private static void reusedWindowRequiresExactCurrentSessionThread() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity oldIdentity = identity("@2", "session-old", "thread-old");
        MainActivity.ActiveSwitchIdentity newIdentity = identity("@2", "session-new", "thread-new");
        long oldGeneration = lifecycle.begin(oldIdentity, "Old B", START_MS);
        long newGeneration = lifecycle.begin(newIdentity, "New B", START_MS + 25);

        assertDisposition(
                "late-generation-ignored",
                onFrame(lifecycle, oldGeneration, oldIdentity, 200, true, 10, 1, "hash-old")
        );
        assertDisposition(
                "event-identity-mismatch",
                onFrame(lifecycle, newGeneration, oldIdentity, 200, true, 10, 1, "hash-old")
        );
        assertDisposition(
                "frame-candidate",
                onFrame(lifecycle, newGeneration, newIdentity, 200, true, 10, 1, "hash-new")
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                onDom(
                        lifecycle,
                        newGeneration,
                        newIdentity,
                        true,
                        10,
                        1,
                        "hash-new",
                        "hash-new",
                        "New B"
                )
        );
        assertEquals("New B", lifecycle.visibleTitle(), "only current reused-window identity commits");
    }

    private static void errorsCannotLeaveVisiblePrefix() {
        assertFailureDisposition("select-error", MainActivity.ActiveSwitchPhase.ROLLED_BACK);
        assertFailureDisposition("select-unreachable", MainActivity.ActiveSwitchPhase.ROLLED_BACK);
        assertFailureDisposition("reader-invalid-source", MainActivity.ActiveSwitchPhase.BLOCKED_KEEP_LAST_GOOD);
        assertFailureDisposition("reader-error", MainActivity.ActiveSwitchPhase.BLOCKED_KEEP_LAST_GOOD);
        assertFailureDisposition("reader-missing-window", MainActivity.ActiveSwitchPhase.BLOCKED_KEEP_LAST_GOOD);
        assertFailureDisposition("reader-unreachable", MainActivity.ActiveSwitchPhase.BLOCKED_KEEP_LAST_GOOD);
    }

    private static void centralTitleHoldCannotCommit() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(
                "central-title-held",
                lifecycle.onCentralTitle(generation, b, "B from central")
        );
        assertEquals(MainActivity.ActiveSwitchPhase.PENDING_FRAME, lifecycle.phase(), "central title is not commit");
        assertEquals("A", lifecycle.lastGoodTitle(), "central hold cannot replace last-good title");
        assertEquals("Switching to B from central", lifecycle.visibleTitle(), "central copy may refresh pending display");
    }

    private static void exactFrameAndDomCommitAtomically() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(
                "frame-candidate",
                onFrame(lifecycle, generation, b, 200, true, 10, 1, "hash-b")
        );
        assertEquals("A", lifecycle.lastGoodTitle(), "frame alone cannot commit title");
        assertEquals("hash-a", lifecycle.committedFrameHash(), "frame alone cannot commit body hash");
        assertDisposition(
                "same-generation-frame-dom-commit",
                onDom(
                        lifecycle,
                        generation,
                        b,
                        true,
                        10,
                        1,
                        "hash-b",
                        "hash-b",
                        "B canonical"
                )
        );
        assertEquals(MainActivity.ActiveSwitchPhase.COMMITTED, lifecycle.phase(), "commit phase");
        assertEquals("B canonical", lifecycle.visibleTitle(), "title commits with DOM");
        assertEquals("hash-b", lifecycle.committedFrameHash(), "body hash commits with title");
        assertFalse(lifecycle.blocksSendFor("@2"), "committed B enables Send");
        assertFalse(lifecycle.visibleTitle().startsWith("Switching to "), "commit clears prefix");
    }

    private static void collisionResistantBodyReceiptRequired() {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(
                "frame-rejected-empty",
                lifecycle.onFrame(
                        generation,
                        b,
                        200,
                        true,
                        18,
                        1,
                        "0d101e4e",
                        "too-short"
                )
        );
        assertDisposition(
                "frame-candidate",
                lifecycle.onFrame(
                        generation,
                        b,
                        200,
                        true,
                        18,
                        1,
                        "0d101e4e",
                        BODY_SHA
                )
        );
        assertDisposition(
                "dom-rejected-hash",
                lifecycle.onDom(
                        generation,
                        b,
                        true,
                        18,
                        1,
                        "0d101e4e",
                        "0d101e4e",
                        OTHER_BODY_SHA,
                        "FNV collision must not commit"
                )
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.PENDING_FRAME,
                lifecycle.phase(),
                "equal FNV/length/row metadata cannot bypass body SHA equality"
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                lifecycle.onDom(
                        generation,
                        b,
                        true,
                        18,
                        1,
                        "0d101e4e",
                        "0d101e4e",
                        BODY_SHA,
                        "B exact"
                )
        );
    }

    private static void confirmedFieldsPublishOnlyAfterAtomicCommit() {
        String source = mainActivitySource();
        String commit = methodBody(
                source,
                "private void commitPendingActiveSwitchTitleFromFrame("
        );
        String rejectionGate =
                "if (!\"same-generation-frame-dom-commit\".equals(domResult.disposition)\n"
                        + "                    || !legacyAtomicPairMatches)";
        int rejectionIndex = commit.indexOf(rejectionGate);
        int configuredPublish = commit.indexOf(
                "confirmedCaptureRendererWindowTargetKey = windowId;"
        );
        int framePublish = commit.indexOf(
                "confirmedCaptureRendererFrameWindowTargetKey = windowId;"
        );
        assertTrue(rejectionIndex >= 0, "atomic commit must retain its rejection gate");
        assertTrue(
                configuredPublish > rejectionIndex,
                "configured target cannot publish before accepted matching frame+DOM"
        );
        assertTrue(
                framePublish > rejectionIndex,
                "frame target cannot publish before accepted matching frame+DOM"
        );

        String acceptedFrame = methodBody(
                source,
                "private void rememberAcceptedCaptureRendererFrame("
        );
        assertTrue(
                acceptedFrame.contains(
                        "if (!hasStableWindowId(pendingActiveSwitchTitleWindowId)\n"
                                + "                && hasStableWindowId(visibleWindowId))"
                ),
                "accepted frame alone must not publish a pending Active target"
        );
        String targetConfigured = methodBody(
                source,
                "private void markCaptureRendererWindowTargetConfirmed("
        );
        assertTrue(
                targetConfigured.contains(
                        "boolean published = "
                                + "!hasStableWindowId(pendingActiveSwitchTitleWindowId);"
                ),
                "setWindowId acknowledgement must not bypass the atomic switch gate"
        );
        String urlLoaded = methodBody(
                source,
                "private void markCaptureRendererUrlTargetLoaded("
        );
        assertTrue(
                urlLoaded.contains(
                        "boolean published = "
                                + "!hasStableWindowId(pendingActiveSwitchTitleWindowId);"
                ),
                "URL load must not bypass the atomic switch gate"
        );
    }

    private static void unresolvedSameTargetTitleDoesNotDemote() {
        String source = mainActivitySource();
        String remember = methodBody(
                source,
                "private void rememberSelectedPhoneWindow(\n"
                        + "            int index,\n"
                        + "            String windowId,\n"
                        + "            String title,\n"
                        + "            String reason,\n"
                        + "            String sessionId,\n"
                        + "            String threadId"
        );
        assertBefore(
                remember,
                "String previousSelectedWindowId = selectedPhoneWindowId;",
                "selectedPhoneWindowId = windowId.trim();",
                "same-target comparison must snapshot the previous target before assignment"
        );
        assertTrue(
                remember.contains(
                        "previousSelectedWindowId.trim().equals(selectedPhoneWindowId)"
                ),
                "unresolved title may retain only the exact same target's usable title"
        );
        assertTrue(
                remember.contains(
                        "selectedPhoneWindowTitle = previousSelectedTitle.trim();"
                ),
                "unresolved same-target confirmation must preserve the usable title"
        );
    }

    private static void stableComposerControlStaysEnabledWhileDeliverySettles() {
        assertTrue(
                MainActivity.ActiveSwitchTitleLifecycle.promptComposerLocalSendControlEnabled(
                        true,
                        true
                ),
                "stable composer target must accept local Send while delivery settles"
        );
        assertFalse(
                MainActivity.ActiveSwitchTitleLifecycle.promptComposerLocalSendControlEnabled(
                        true,
                        false
                ),
                "unstable composer target must remain blocked"
        );
        assertTrue(
                MainActivity.ActiveSwitchTitleLifecycle.promptComposerLocalSendControlEnabled(
                        false,
                        false
                ),
                "hidden composer keeps Start available"
        );
    }

    private static void productionWiresStableComposerControl() {
        String source = mainActivitySource();
        assertTrue(
                source.contains(
                        "boolean sendControlEnabled = "
                                + "ActiveSwitchTitleLifecycle.promptComposerLocalSendControlEnabled("
                ),
                "production must wire the stable-target local admission helper"
        );
        assertFalse(
                source.contains(
                        "boolean sendControlEnabled = !composerVisible\n"
                                + "                || !activeSwitchTitleLifecycle.blocksSendFor(targetKey);"
                ),
                "production must not disable local Send while switch delivery settles"
        );
    }

    private static void offTargetCallbacksCannotMutateVisibleDraftOwner() {
        String source = mainActivitySource();
        String success = methodBody(
                source,
                "private boolean clearPromptComposerAfterSuccessfulSubmit("
        );
        String failure = methodBody(
                source,
                "private void restorePromptComposerAfterFailedSubmit("
        );
        int failureTargetCheck = failure.indexOf(
                "if (!stableTargetKey.equals(promptComposerTargetKey()))"
        );
        int failureOwnerWrite = failure.indexOf(
                "promptComposerDraftTargetKey = stableTargetKey;"
        );
        assertTrue(failureTargetCheck >= 0, "failure callback must compare the visible target");
        assertTrue(
                failureOwnerWrite > failureTargetCheck,
                "off-target failure must return before rebinding the visible draft owner"
        );
        assertTrue(
                success.contains(
                        "if (!stableTargetKey.equals(visibleTargetKey)) {\n"
                                + "            return false;\n"
                                + "        }"
                ),
                "success callback must return before touching a different visible target"
        );
        assertFalse(
                success.contains("forgetPromptComposerDraft(visibleTargetKey);"),
                "success callback must never delete another target's equal-body draft"
        );
    }

    private static void lateFailureCannotOverwriteNewerSameTargetDraft() {
        assertTrue(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "",
                        "A1"
                ),
                "empty target slot may restore the failed draft"
        );
        assertTrue(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "A1",
                        "A1"
                ),
                "same failed value may be restored idempotently"
        );
        assertFalse(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "A2",
                        "A1"
                ),
                "late A1 failure must preserve newer A2 on the same target"
        );
        assertTrue(
                methodBody(
                        mainActivitySource(),
                        "private void restorePromptComposerAfterFailedSubmit("
                ).contains("PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft("),
                "production failure callback must wire the atomic draft-owner guard"
        );
    }

    private static void exactDraftRevisionOwnsAcknowledgement() {
        assertTrue(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldForgetSuccessfulDraft(
                        "same body",
                        7L,
                        "same body",
                        7L
                ),
                "acknowledged success may clear its exact persisted draft revision"
        );
        assertFalse(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldForgetSuccessfulDraft(
                        "same body",
                        8L,
                        "same body",
                        7L
                ),
                "older success must preserve a later identical draft"
        );
        assertTrue(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "same body",
                        7L,
                        "same body",
                        7L
                ),
                "terminal failure may restore its exact persisted draft revision"
        );
        assertFalse(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "same body",
                        8L,
                        "same body",
                        7L
                ),
                "older failure must preserve a later identical draft"
        );
        assertFalse(
                MainActivity.PromptComposerRapidOrderedSendBehavior.shouldRestoreFailedDraft(
                        "",
                        8L,
                        "same body",
                        7L
                ),
                "explicitly cleared newer revision must not be resurrected"
        );

        String source = mainActivitySource();
        assertTrue(
                source.contains("final long draftRevision;"),
                "queued Send must retain immutable draft ownership"
        );
        String enqueue = methodBody(source, "private void enqueuePromptComposerSubmit(");
        assertBefore(
                enqueue,
                "if (isDuplicatePromptComposerSubmit(submit))",
                "long submitDraftRevision = "
                        + "rememberPromptComposerDraftForSubmit("
                        + "submit.stableTargetKey, submit.value);",
                "duplicate rejection must happen before allocating a draft revision"
        );
        assertBefore(
                enqueue,
                "submit = submit.withDraftRevision(submitDraftRevision);",
                "promptComposerSubmitQueue.add(submit);",
                "only the revision-bound immutable submit may enter the queue"
        );
        String submitPersistence = methodBody(
                source,
                "private long rememberPromptComposerDraftForSubmit("
        );
        assertTrue(
                submitPersistence.contains("currentRevision >= Long.MAX_VALUE - 1L"),
                "admission must reserve a terminal callback revision instead of wrapping"
        );
        assertTrue(
                methodBody(
                        source,
                        "private long rememberedPromptComposerDraftRevision("
                ).contains("catch (ClassCastException"),
                "a malformed persisted revision must fail closed"
        );
        assertTrue(
                methodBody(
                        source,
                        "private boolean clearPromptComposerAfterSuccessfulSubmit("
                ).contains("submittedDraftRevision"),
                "success callback must compare the submitted draft revision"
        );
        assertTrue(
                methodBody(
                        source,
                        "private void restorePromptComposerAfterFailedSubmit("
                ).contains("submittedDraftRevision"),
                "failure callback must compare the submitted draft revision"
        );
    }

    private static void queuedDraftPlaceholderCannotEraseDurableRevision() {
        String source = mainActivitySource();
        String reset = methodBody(
                source,
                "private void resetPromptComposerLocalDraftForCurrentTarget("
        );
        assertTrue(
                reset.contains(
                        "long targetDraftRevision = "
                                + "rememberedPromptComposerDraftRevision(targetKey);"
                ),
                "queued placeholder suppression must snapshot the persisted revision"
        );
        String alreadyQueued = methodBody(
                source,
                "private boolean promptComposerDraftIsAlreadyQueued("
        );
        assertTrue(
                alreadyQueued.contains("submit.draftRevision == targetDraftRevision"),
                "later identical text must not be mistaken for the older queued draft"
        );

        String save = methodBody(source, "private void saveVisiblePromptComposerDraft(");
        assertBefore(
                save,
                "promptComposerDraftIsAlreadyQueued(",
                "rememberPromptComposerDraft(",
                "a suppressed empty placeholder must be rejected before persistence"
        );
        assertTrue(
                save.contains("visibleDraft.isEmpty()"),
                "only the empty queued placeholder may bypass a normal visible save"
        );
        assertBefore(
                save,
                "if (TextUtils.equals(visibleDraft, persistedDraft))",
                "promptComposerDraftIsAlreadyQueued(",
                "unchanged lifecycle saves must not manufacture a newer revision"
        );
    }

    private static void localFeedbackPrecedesNetworkAndSuccessOwnsClear() {
        String source = mainActivitySource();
        String submit = methodBody(
                source,
                "private void submitSafePrompt(\n"
                        + "            String text,\n"
                        + "            String successToast,\n"
                        + "            String targetKey,\n"
                        + "            Runnable afterSuccess,\n"
                        + "            Runnable afterFailure"
        );
        assertBefore(
                submit,
                "markApkFirstLocalFeedback(\"send-local-queue\", stableTargetKey);",
                "enqueuePromptComposerSubmit(submit);",
                "tap feedback must be recorded before queue admission"
        );

        String enqueue = methodBody(source, "private void enqueuePromptComposerSubmit(");
        assertBefore(
                enqueue,
                "hideDockedPromptComposerAfterQueuedSubmit(",
                "drainPromptComposerSubmitQueue();",
                "visible local feedback must happen before request drain"
        );
        String localHide = methodBody(
                source,
                "private void hideDockedPromptComposerAfterQueuedSubmit("
        );
        assertFalse(
                localHide.contains("forgetPromptComposerDraft("),
                "local feedback must not delete the durable draft"
        );

        String post = methodBody(source, "private void postPromptComposerSubmit(");
        assertBefore(
                post,
                "if (!isSuccessfulPromptSubmitResponse(payload))",
                "clearPromptComposerAfterSuccessfulSubmit(",
                "only acknowledged server success may clear the submitted draft"
        );
    }

    private static String mainActivitySource() {
        try {
            return new String(
                    Files.readAllBytes(Paths.get(
                            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
                    )),
                    StandardCharsets.UTF_8
            );
        } catch (IOException error) {
            throw new AssertionError("cannot inspect MainActivity Send-control wiring", error);
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

    private static void assertFailureDisposition(
            String reason,
            MainActivity.ActiveSwitchPhase expectedPhase
    ) {
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(reason, lifecycle.onFailure(generation, reason));
        assertEquals(expectedPhase, lifecycle.phase(), reason + " phase");
        assertEquals("A", lifecycle.visibleTitle(), reason + " keeps last-good title");
        assertFalse(lifecycle.visibleTitle().startsWith("Switching to "), reason + " clears prefix");
    }

    private static MainActivity.ActiveSwitchTitleLifecycle lifecycleA() {
        return new MainActivity.ActiveSwitchTitleLifecycle("A", "hash-a");
    }

    private static MainActivity.ActiveSwitchIdentity identity(
            String windowId,
            String sessionId,
            String threadId
    ) {
        return new MainActivity.ActiveSwitchIdentity(windowId, sessionId, threadId);
    }

    private static MainActivity.ActiveSwitchEventResult onFrame(
            MainActivity.ActiveSwitchTitleLifecycle lifecycle,
            long generation,
            MainActivity.ActiveSwitchIdentity identity,
            int status,
            boolean acceptedFrame,
            int bodyLength,
            int rowCount,
            String frameHash
    ) {
        return lifecycle.onFrame(
                generation,
                identity,
                status,
                acceptedFrame,
                bodyLength,
                rowCount,
                frameHash,
                BODY_SHA
        );
    }

    private static MainActivity.ActiveSwitchEventResult onDom(
            MainActivity.ActiveSwitchTitleLifecycle lifecycle,
            long generation,
            MainActivity.ActiveSwitchIdentity identity,
            boolean acceptedFrame,
            int bodyLength,
            int rowCount,
            String frameHash,
            String domHash,
            String title
    ) {
        return lifecycle.onDom(
                generation,
                identity,
                acceptedFrame,
                bodyLength,
                rowCount,
                frameHash,
                domHash,
                BODY_SHA,
                title
        );
    }

    private static void assertDisposition(
            String expected,
            MainActivity.ActiveSwitchEventResult actual
    ) {
        assertEquals(expected, actual.disposition, "disposition");
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
        assertTrue(!value, message);
    }

    private static void commitOnIdentityUnstucksSwitchOnUnacceptedBody() {
        // WHY(stuck-switch guard 2026-07-28): v248 commit-on-identity is UNGUARDED by the
        // acceptedFrame=true commit tests (114/435/523), so a wholesale revert would pass every
        // existing test yet silently reintroduce the hide-controls/stuck-switch bug. Lock it in:
        // an identity-proven body whose frame+DOM stay acceptedFrame=FALSE must still COMMIT and
        // enable Send, never hang in BLOCKED_KEEP_LAST_GOOD. Forcing identityProvenBody=false
        // (the pure revert) makes this RED -- proving the black-box fix keeps stuck-switch working.
        MainActivity.ActiveSwitchTitleLifecycle lifecycle = lifecycleA();
        MainActivity.ActiveSwitchIdentity b = identity("@2", "session-b", "thread-b");
        long generation = lifecycle.begin(b, "B", START_MS);
        assertDisposition(
                "frame-candidate",
                onFrame(lifecycle, generation, b, 200, false, 18, 1, "hash-b")
        );
        assertDisposition(
                "same-generation-frame-dom-commit",
                onDom(lifecycle, generation, b, false, 18, 1, "hash-b", "hash-b", "B")
        );
        assertEquals(
                MainActivity.ActiveSwitchPhase.COMMITTED,
                lifecycle.phase(),
                "identity-proven unaccepted body must COMMIT (stuck-switch fix), not hang blocked"
        );
        assertFalse(
                lifecycle.blocksSendFor("@2"),
                "committed identity-proven switch must enable Send / show controls"
        );
    }

    private static void unacceptedCommitSchedulesFullFrameRefill() {
        // WHY(black-box refill guard 2026-07-28): commit-on-identity can paint an empty/mid-load
        // acceptedFrame=false body and, by committing before the deadline, skip the deadline's
        // r.refresh(true) recovery -> a persistent empty black region at the bottom. The commit
        // path MUST therefore force a full-frame refill on an acceptedFrame=false commit only.
        // RED before the fix (no refill call); GREEN after. Keeps commit-on-identity intact.
        String source = mainActivitySource();
        String commit = methodBody(source, "private void commitPendingActiveSwitchTitleFromFrame(");
        assertTrue(
                commit.indexOf("scheduleActiveSwitchRetainedCommitFullFrameRefill(windowId);") >= 0,
                "acceptedFrame=false commit must schedule a full-frame refill for the black-box bottom"
        );
        assertBefore(
                commit,
                "if (!acceptedFrame) {",
                "scheduleActiveSwitchRetainedCommitFullFrameRefill(windowId);",
                "the refill must be gated on an acceptedFrame=false (retained-body) commit"
        );
        String refetch = methodBody(source, "private void activeSwitchRetainedCommitFullFrameRefetch(");
        assertTrue(
                refetch.indexOf("r.refresh(true,'") >= 0,
                "the refill must force-fetch r.refresh(true) so a native read-hold cannot silently drop it"
        );
        String onFrameBody = methodBody(source, "ActiveSwitchEventResult onFrame(");
        assertTrue(
                onFrameBody.indexOf("!identity.sessionId.isEmpty() || !identity.threadId.isEmpty()") >= 0,
                "onFrame must keep commit-on-identity (identityProvenBody expression), not a hardcoded false"
        );
    }

    private static void inProgress409IsClassifiedAsIdempotencyRetryNotFailure() {
        // WHY(409 in-progress guard 2026-07-28): the control server returns HTTP 409
        // {inProgress:true, retryable:true} when /submit-text exceeds its sync budget -- the send
        // WAS accepted and is still executing under the same Idempotency-Key. The APK must poll
        // that key (never treat it as a failure that restores the draft -> double-send). The old
        // classifier matched only the legacy message text; this asserts the STRUCTURED 409 signal
        // is recognized (RED before the fix), while keeping the legacy text for older servers.
        String body = methodBody(
                mainActivitySource(),
                "private boolean isPromptSubmitIdempotencyInProgress("
        );
        assertTrue(
                body.indexOf("payload.optInt(\"httpStatus\", -1) == 409") >= 0
                        && body.indexOf("payload.optBoolean(\"inProgress\", false)") >= 0,
                "409 {inProgress:true} must be classified as idempotency-in-progress (poll same key), not a failure"
        );
        assertTrue(
                body.indexOf("already in progress") >= 0 && body.indexOf("idempotency") >= 0,
                "legacy message-text idempotency match must remain for older servers"
        );
    }

    private ActiveSwitchTitleLifecycleTest() {
    }
}
