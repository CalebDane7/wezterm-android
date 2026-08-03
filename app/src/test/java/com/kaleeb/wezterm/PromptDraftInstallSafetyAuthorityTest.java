package com.kaleeb.wezterm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic production-decision guard for the prompt-draft install-safety authority.
 *
 * <p>The test deliberately reaches the production seam through reflection so the frozen
 * pre-change MainActivity still compiles and produces a behavior red when that seam is
 * absent. Once present, every global draft decision below executes the same production
 * implementation used by the live exact-argument dump path.
 */
public final class PromptDraftInstallSafetyAuthorityTest {
    private static final Path MAIN_SOURCE = Paths.get(
            "app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
    );
    private static final String AUTHORITY_CLASS =
            "com.kaleeb.wezterm.MainActivity$PromptDraftInstallSafetyAuthority";
    private static final String PRIVATE_SENTINEL = "SYNTHETIC_PRIVATE_BODY";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final String OTHER_NONCE = "fedcba9876543210fedcba9876543210";

    private static final List<String> failures = new ArrayList<>();
    private static int behaviorCases = 0;
    private static Class<?> authorityClass;
    private static Class<?> resultClass;
    private static Class<?> consumeResultClass;
    private static Method evaluateMethod;
    private static Method finalizeMethod;
    private static Method exactRequestMethod;
    private static Method exactConsumeRequestMethod;
    private static Method markerMethod;
    private static Method reconcileConsumeMethod;
    private static Method consumeMarkerMethod;

    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        checkProductionWiring(source);
        loadProductionSeam();
        if (authorityClass != null) {
            runBehaviorCase("empty", PromptDraftInstallSafetyAuthorityTest::emptyGlobalState);
            runBehaviorCase("hidden-nonempty", PromptDraftInstallSafetyAuthorityTest::hiddenNonempty);
            runBehaviorCase("visible-whitespace", PromptDraftInstallSafetyAuthorityTest::visibleWhitespace);
            runBehaviorCase("multiple-empty", PromptDraftInstallSafetyAuthorityTest::multipleEmpty);
            runBehaviorCase("multiple-nonempty", PromptDraftInstallSafetyAuthorityTest::multipleNonempty);
            runBehaviorCase("malformed-precedence", PromptDraftInstallSafetyAuthorityTest::malformedBeatsNonempty);
            runBehaviorCase("store-unavailable", PromptDraftInstallSafetyAuthorityTest::storeUnavailable);
            runBehaviorCase("revision-race", PromptDraftInstallSafetyAuthorityTest::revisionRace);
            runBehaviorCase("pending-writer", PromptDraftInstallSafetyAuthorityTest::pendingWriter);
            runBehaviorCase("commit-failure", PromptDraftInstallSafetyAuthorityTest::commitFailure);
            runBehaviorCase("post-commit-seal-namespace",
                    PromptDraftInstallSafetyAuthorityTest::postCommitSealNamespace);
            runBehaviorCase("install-safety-state-namespaces",
                    PromptDraftInstallSafetyAuthorityTest::installSafetyStateNamespaces);
            runBehaviorCase("post-commit-mutation", PromptDraftInstallSafetyAuthorityTest::postCommitMutation);
            runBehaviorCase("sealed-empty", PromptDraftInstallSafetyAuthorityTest::sealedEmpty);
            runBehaviorCase("exact-request", PromptDraftInstallSafetyAuthorityTest::exactRequest);
            runBehaviorCase("invalid-request", PromptDraftInstallSafetyAuthorityTest::invalidRequest);
            runBehaviorCase("seal-active-nonce-mismatch",
                    PromptDraftInstallSafetyAuthorityTest::sealActiveNonceMismatch);
            runBehaviorCase("same-nonce-consume",
                    PromptDraftInstallSafetyAuthorityTest::sameNonceConsume);
            runBehaviorCase("same-nonce-replay",
                    PromptDraftInstallSafetyAuthorityTest::sameNonceReplay);
            runBehaviorCase("stale-nonce-sealed",
                    PromptDraftInstallSafetyAuthorityTest::staleNonceStaysSealed);
            runBehaviorCase("consume-nonempty",
                    PromptDraftInstallSafetyAuthorityTest::consumeNonemptyStaysSealed);
            runBehaviorCase("consume-malformed",
                    PromptDraftInstallSafetyAuthorityTest::consumeMalformedStaysSealed);
            runBehaviorCase("consume-race",
                    PromptDraftInstallSafetyAuthorityTest::consumeRaceStaysSealed);
            runBehaviorCase("consume-pending",
                    PromptDraftInstallSafetyAuthorityTest::consumePendingStaysSealed);
            runBehaviorCase("consume-commit-failure",
                    PromptDraftInstallSafetyAuthorityTest::consumeCommitFailureStaysSealed);
            runBehaviorCase("consume-reread-failure",
                    PromptDraftInstallSafetyAuthorityTest::consumeRereadFailureStaysSealed);
            runBehaviorCase("consume-persisted-drift",
                    PromptDraftInstallSafetyAuthorityTest::consumePersistedDriftStaysSealed);
            runBehaviorCase("exact-consume-request",
                    PromptDraftInstallSafetyAuthorityTest::exactConsumeRequest);
            runBehaviorCase("invalid-consume-request",
                    PromptDraftInstallSafetyAuthorityTest::invalidConsumeRequest);
        }

        if (!failures.isEmpty()) {
            System.err.println("PROMPT_DRAFT_INSTALL_SAFETY_AUTHORITY_BEHAVIOR_RED"
                    + " violations=" + failures.size()
                    + " codes=" + joinCodes(failures));
            throw new AssertionError("prompt draft install-safety behavior red");
        }
        System.out.println("PROMPT_DRAFT_INSTALL_SAFETY_AUTHORITY_PASS"
                + " behaviorCases=" + behaviorCases
                + " privacyCases=1"
                + " wiringClauses=14");
    }

    private static void checkProductionWiring(String source) {
        requireSource(
                source.contains("public void dump(")
                        && source.contains("PromptDraftInstallSafetyAuthority.isExactSealRequest(args)")
                        && source.contains("PromptDraftInstallSafetyAuthority.isExactConsumeRequest(args)"),
                "missing_exact_argument_dump_seam"
        );
        requireSource(
                source.contains("prefs.getAll()")
                        && source.contains("PREF_PROMPT_DRAFT_PREFIX"),
                "missing_global_prefix_family_scan"
        );
        requireSource(
                source.contains("promptDraftInstallSafetyRevision")
                        && source.contains("revisionBefore")
                        && source.contains("revisionAfter"),
                "missing_shared_writer_revision"
        );
        requireSource(
                source.contains("PREF_PROMPT_DRAFT_INSTALL_SAFETY_SEAL")
                        && source.contains("PREF_PROMPT_DRAFT_INSTALL_SAFETY_ACTIVE_ID")
                        && source.contains(".commit()")
                        && source.contains("finalizeEmptySeal("),
                "missing_durable_nonce_bound_seal"
        );
        requireSource(
                source.contains("WEZTERM_PROMPT_DRAFT_INSTALL_SAFETY_V1")
                        && source.contains("writer.flush()"),
                "missing_fixed_marker"
        );
        requireSource(
                source.contains("android.os.Process.killProcess(android.os.Process.myPid())")
                        && source.contains("processSealHandoff"),
                "missing_process_seal_handoff"
        );
        int loadSeal = source.indexOf("loadPromptDraftInstallSafetySeal();");
        int buildLayout = source.indexOf("setContentView(buildLayout(webView));");
        requireSource(
                loadSeal >= 0 && buildLayout >= 0 && loadSeal < buildLayout
                        && source.contains("applyPromptDraftInstallSafetySealToComposer();"),
                "missing_restart_writer_block"
        );
        requireSource(
                source.contains("promptDraftInstallSafetyWriterBlocked()")
                        && count(source, "promptDraftInstallSafetyRevision++") >= 2,
                "missing_central_writer_gate"
        );
        requireSource(
                source.contains("--prompt-draft-install-safety-consume-v1")
                        && source.contains("consumePromptDraftInstallSafetyAuthority"),
                "missing_nonce_bound_consume_request"
        );
        requireSource(
                source.contains("PREF_PROMPT_DRAFT_INSTALL_SAFETY_LAST_CONSUMED_ID")
                        && source.contains("persistedLastConsumedInvocationId"),
                "missing_durable_consumed_tombstone"
        );
        requireSource(
                source.contains("promptDraftInstallSafetyActiveInvocationId")
                        && source.contains("promptDraftInstallSafetyLastConsumedInvocationId")
                        && source.contains("NONCE_MISMATCH"),
                "missing_same_nonce_reconcile"
        );
        requireSource(
                source.contains("restorePromptDraftInstallSafetySeal(")
                        && source.contains("DURABILITY_FAILURE"),
                "missing_consume_failure_reseal"
        );
        requireSource(
                source.contains("releasePromptDraftInstallSafetyComposer()")
                        && source.contains("promptComposerInput.setEnabled(true)"),
                "missing_post_consume_writer_release"
        );
        int consumeStart = source.indexOf(
                "consumePromptDraftInstallSafetyAuthority(String invocationId)"
        );
        int consumeCommit = consumeStart < 0 ? -1 : source.indexOf(
                ".putBoolean(PREF_PROMPT_DRAFT_INSTALL_SAFETY_SEAL, false)",
                consumeStart
        );
        int postCommitInspection = consumeCommit < 0 ? -1 : source.indexOf(
                "inspectPromptDraftInstallSafetyAuthority()",
                consumeCommit
        );
        int persistedReread = postCommitInspection < 0 ? -1 : source.indexOf(
                "persistedLastConsumedInvocationId = readPromptDraftInstallSafetyString(",
                postCommitInspection
        );
        int postConsumeRelease = persistedReread < 0 ? -1 : source.indexOf(
                "releasePromptDraftInstallSafetyComposer();",
                persistedReread
        );
        requireSource(
                consumeStart >= 0
                        && consumeCommit > consumeStart
                        && postCommitInspection > consumeCommit
                        && persistedReread > postCommitInspection
                        && postConsumeRelease > persistedReread,
                "missing_commit_reread_before_writer_release"
        );
    }

    private static void loadProductionSeam() {
        try {
            authorityClass = Class.forName(AUTHORITY_CLASS);
            resultClass = Class.forName(AUTHORITY_CLASS + "$Result");
            consumeResultClass = Class.forName(AUTHORITY_CLASS + "$ConsumeResult");
            evaluateMethod = authorityClass.getDeclaredMethod(
                    "evaluate",
                    String.class,
                    Map.class,
                    long.class,
                    long.class,
                    boolean.class,
                    boolean.class,
                    boolean.class
            );
            finalizeMethod = authorityClass.getDeclaredMethod(
                    "finalizeEmptySeal",
                    resultClass,
                    boolean.class,
                    resultClass,
                    boolean.class,
                    String.class,
                    String.class
            );
            exactRequestMethod = authorityClass.getDeclaredMethod(
                    "isExactRequest", String[].class
            );
            markerMethod = authorityClass.getDeclaredMethod(
                    "marker", String.class, resultClass
            );
            exactConsumeRequestMethod = authorityClass.getDeclaredMethod(
                    "isExactConsumeRequest", String[].class
            );
            reconcileConsumeMethod = authorityClass.getDeclaredMethod(
                    "reconcileConsume",
                    String.class,
                    boolean.class,
                    String.class,
                    String.class,
                    resultClass,
                    boolean.class,
                    resultClass,
                    boolean.class,
                    String.class,
                    String.class
            );
            consumeMarkerMethod = authorityClass.getDeclaredMethod(
                    "consumeMarker", String.class, consumeResultClass
            );
            evaluateMethod.setAccessible(true);
            finalizeMethod.setAccessible(true);
            exactRequestMethod.setAccessible(true);
            markerMethod.setAccessible(true);
            exactConsumeRequestMethod.setAccessible(true);
            reconcileConsumeMethod.setAccessible(true);
            consumeMarkerMethod.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            authorityClass = null;
            failures.add("missing_production_authority_seam");
        }
    }

    private static void emptyGlobalState() throws Exception {
        assertResult(evaluate("", new LinkedHashMap<String, Object>(), 9, 9, false, true, true),
                "EMPTY", "OK", false, "empty_global_state");
    }

    private static void hiddenNonempty() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_test_target", PRIVATE_SENTINEL);
        assertResult(evaluate("", values, 2, 2, false, true, true),
                "NONEMPTY", "NOT_EMPTY", false, "hidden_nonempty");
    }

    private static void visibleWhitespace() throws Exception {
        assertResult(evaluate(" ", new LinkedHashMap<String, Object>(), 3, 3, false, true, true),
                "NONEMPTY", "NOT_EMPTY", false, "visible_whitespace_nonempty");
    }

    private static void multipleEmpty() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_first", "");
        values.put("prompt_draft_second", "");
        values.put("unrelated_preference", PRIVATE_SENTINEL);
        assertResult(evaluate("", values, 4, 4, false, true, true),
                "EMPTY", "OK", false, "multiple_empty");
    }

    private static void multipleNonempty() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_first", "");
        values.put("prompt_draft_second", PRIVATE_SENTINEL);
        values.put("prompt_draft_third", "");
        assertResult(evaluate("", values, 5, 5, false, true, true),
                "NONEMPTY", "NOT_EMPTY", false, "multiple_any_nonempty");
    }

    private static void malformedBeatsNonempty() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_nonempty", PRIVATE_SENTINEL);
        values.put("prompt_draft_wrong_type", Integer.valueOf(7));
        assertResult(evaluate("visible", values, 6, 6, false, true, true),
                "UNKNOWN", "STORE_MALFORMED", false, "malformed_beats_nonempty");
        values.put("prompt_draft_wrong_type", null);
        assertResult(evaluate("", values, 6, 6, false, true, true),
                "UNKNOWN", "STORE_MALFORMED", false, "null_is_malformed");
    }

    private static void storeUnavailable() throws Exception {
        assertResult(evaluate("", null, 7, 7, false, true, false),
                "UNKNOWN", "STORE_UNAVAILABLE", false, "store_unavailable");
        assertResult(evaluate("", new LinkedHashMap<String, Object>(), 7, 7, false, false, true),
                "UNKNOWN", "OTHER_REDACTED", false, "composer_unavailable");
    }

    private static void revisionRace() throws Exception {
        assertResult(evaluate("", new LinkedHashMap<String, Object>(), 8, 9, false, true, true),
                "UNKNOWN", "MUTATION_RACE", false, "revision_race");
    }

    private static void pendingWriter() throws Exception {
        assertResult(evaluate("", new LinkedHashMap<String, Object>(), 10, 10, true, true, true),
                "UNKNOWN", "PENDING_WRITER", false, "pending_writer");
    }

    private static void commitFailure() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 11, 11, false, true, true);
        Object after = evaluate("", new LinkedHashMap<String, Object>(), 11, 11, false, true, true);
        assertResult(finalizeSeal(before, false, after, false),
                "UNKNOWN", "DURABILITY_FAILURE", false, "commit_failure");
    }

    private static void postCommitSealNamespace() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 12, 12, false, true, true);
        String sealKey = productionSealKey();
        Map<String, Object> afterCommitValues = new LinkedHashMap<>();
        afterCommitValues.put(sealKey, Boolean.TRUE);
        Object after = evaluate("", afterCommitValues, 12, 12, false, true, true);
        Object sealed = finalizeSeal(before, true, after, true);
        assertResult(sealed, "EMPTY", "OK", true, "post_commit_seal_namespace");
        require(!sealKey.startsWith("prompt_draft_"),
                "seal_key_must_not_overlap_draft_family");
    }

    private static void installSafetyStateNamespaces() throws Exception {
        String activeKey = productionPreferenceKey(
                "PREF_PROMPT_DRAFT_INSTALL_SAFETY_ACTIVE_ID"
        );
        String consumedKey = productionPreferenceKey(
                "PREF_PROMPT_DRAFT_INSTALL_SAFETY_LAST_CONSUMED_ID"
        );
        require(!activeKey.startsWith("prompt_draft_"), "active_key_draft_namespace");
        require(!consumedKey.startsWith("prompt_draft_"), "consumed_key_draft_namespace");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(productionSealKey(), Boolean.TRUE);
        values.put(activeKey, NONCE);
        values.put(consumedKey, OTHER_NONCE);
        assertResult(
                evaluate("", values, 12, 12, false, true, true),
                "EMPTY",
                "OK",
                false,
                "install_safety_state_namespaces"
        );
    }

    private static void postCommitMutation() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 12, 12, false, true, true);
        Map<String, Object> changed = new LinkedHashMap<>();
        changed.put("prompt_draft_changed", PRIVATE_SENTINEL);
        Object after = evaluate("", changed, 12, 13, false, true, true);
        assertResult(finalizeSeal(before, true, after, true),
                "UNKNOWN", "MUTATION_RACE", false, "post_commit_mutation");
    }

    private static void sealedEmpty() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 13, 13, false, true, true);
        Object after = evaluate("", new LinkedHashMap<String, Object>(), 13, 13, false, true, true);
        Object sealed = finalizeSeal(before, true, after, true);
        assertResult(sealed, "EMPTY", "OK", true, "sealed_empty");
        String marker = (String) markerMethod.invoke(null, NONCE, sealed);
        require(!marker.contains(PRIVATE_SENTINEL), "marker_private_value_leak");
        require(!marker.contains("prompt_draft_"), "marker_preference_key_leak");
        require(marker.equals(
                        "WEZTERM_PROMPT_DRAFT_INSTALL_SAFETY_V1|" + NONCE
                                + "|EMPTY|OK|SEALED_EXIT_REQUIRED"),
                "fixed_marker_schema");
    }

    private static void exactRequest() throws Exception {
        boolean exact = ((Boolean) exactRequestMethod.invoke(
                null,
                (Object) new String[]{"--prompt-draft-install-safety-v1", NONCE}
        )).booleanValue();
        require(exact, "exact_request_rejected");
    }

    private static void invalidRequest() throws Exception {
        String[][] invalid = new String[][]{
                null,
                new String[0],
                new String[]{"--prompt-draft-install-safety-v1"},
                new String[]{"--prompt-draft-install-safety-v1", "ABC"},
                new String[]{"--prompt-draft-install-safety-v1", NONCE, "extra"},
                new String[]{"--other", NONCE}
        };
        for (String[] request : invalid) {
            boolean accepted = ((Boolean) exactRequestMethod.invoke(
                    null, (Object) request
            )).booleanValue();
            require(!accepted, "invalid_request_accepted");
        }
        Object empty = evaluate("", new LinkedHashMap<String, Object>(), 14, 14, false, true, true);
        String invalidMarker = (String) markerMethod.invoke(null, "invalid", empty);
        require(invalidMarker.isEmpty(), "invalid_nonce_marker_emitted");
    }

    private static void sealActiveNonceMismatch() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 15, 15, false, true, true);
        Object after = evaluate("", new LinkedHashMap<String, Object>(), 15, 15, false, true, true);
        assertResult(
                finalizeSeal(before, true, after, true, OTHER_NONCE, NONCE),
                "UNKNOWN",
                "DURABILITY_FAILURE",
                false,
                "seal_active_nonce_mismatch"
        );
    }

    private static void sameNonceConsume() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 16, 16, false, true, true);
        Object after = evaluate("", new LinkedHashMap<String, Object>(), 16, 16, false, true, true);
        Object consumed = reconcileConsume(
                NONCE, true, NONCE, "", before, true, after, false, "", NONCE
        );
        assertConsumeResult(consumed, "CONSUMED", "OK", true, "same_nonce_consume");
        String marker = (String) consumeMarkerMethod.invoke(null, NONCE, consumed);
        require(!marker.contains(PRIVATE_SENTINEL), "consume_marker_private_value_leak");
        require(!marker.contains("prompt_draft_"), "consume_marker_preference_key_leak");
        require(marker.equals(
                        "WEZTERM_PROMPT_DRAFT_INSTALL_SAFETY_CONSUME_V1|" + NONCE
                                + "|CONSUMED|OK|WRITER_RELEASED"),
                "consume_marker_schema");
    }

    private static void sameNonceReplay() throws Exception {
        Object replay = reconcileConsume(
                NONCE, false, "", NONCE, null, false, null, false, "", NONCE
        );
        assertConsumeResult(
                replay,
                "ALREADY_CONSUMED",
                "OK",
                true,
                "same_nonce_replay"
        );
        String marker = (String) consumeMarkerMethod.invoke(null, NONCE, replay);
        require(marker.equals(
                        "WEZTERM_PROMPT_DRAFT_INSTALL_SAFETY_CONSUME_V1|" + NONCE
                                + "|ALREADY_CONSUMED|OK|WRITER_RELEASED"),
                "replay_marker_schema");
    }

    private static void staleNonceStaysSealed() throws Exception {
        Object stale = reconcileConsume(
                OTHER_NONCE, true, NONCE, "", null, false, null, true, NONCE, ""
        );
        assertConsumeResult(stale, "SEALED", "NONCE_MISMATCH", false, "stale_nonce");
    }

    private static void consumeNonemptyStaysSealed() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_hidden", PRIVATE_SENTINEL);
        Object before = evaluate("", values, 17, 17, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, false, null, true, NONCE, ""
        );
        assertConsumeResult(rejected, "SEALED", "NOT_EMPTY", false, "consume_nonempty");
    }

    private static void consumeMalformedStaysSealed() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prompt_draft_malformed", Integer.valueOf(5));
        Object before = evaluate("", values, 18, 18, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, false, null, true, NONCE, ""
        );
        assertConsumeResult(
                rejected,
                "SEALED",
                "STORE_MALFORMED",
                false,
                "consume_malformed"
        );
    }

    private static void consumeRaceStaysSealed() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 19, 20, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, false, null, true, NONCE, ""
        );
        assertConsumeResult(rejected, "SEALED", "MUTATION_RACE", false, "consume_race");
    }

    private static void consumePendingStaysSealed() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 21, 21, true, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, false, null, true, NONCE, ""
        );
        assertConsumeResult(rejected, "SEALED", "PENDING_WRITER", false, "consume_pending");
    }

    private static void consumeCommitFailureStaysSealed() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 22, 22, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, false, null, true, NONCE, ""
        );
        assertConsumeResult(
                rejected,
                "SEALED",
                "DURABILITY_FAILURE",
                false,
                "consume_commit_failure"
        );
    }

    private static void consumeRereadFailureStaysSealed() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 23, 23, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, true, null, false, "", NONCE
        );
        assertConsumeResult(
                rejected,
                "SEALED",
                "OTHER_REDACTED",
                false,
                "consume_reread_failure"
        );
    }

    private static void consumePersistedDriftStaysSealed() throws Exception {
        Object before = evaluate("", new LinkedHashMap<String, Object>(), 24, 24, false, true, true);
        Object after = evaluate("", new LinkedHashMap<String, Object>(), 24, 24, false, true, true);
        Object rejected = reconcileConsume(
                NONCE, true, NONCE, "", before, true, after, false, OTHER_NONCE, NONCE
        );
        assertConsumeResult(
                rejected,
                "SEALED",
                "DURABILITY_FAILURE",
                false,
                "consume_persisted_drift"
        );
    }

    private static void exactConsumeRequest() throws Exception {
        boolean exact = ((Boolean) exactConsumeRequestMethod.invoke(
                null,
                (Object) new String[]{"--prompt-draft-install-safety-consume-v1", NONCE}
        )).booleanValue();
        require(exact, "exact_consume_request_rejected");
    }

    private static void invalidConsumeRequest() throws Exception {
        String[][] invalid = new String[][]{
                null,
                new String[0],
                new String[]{"--prompt-draft-install-safety-consume-v1"},
                new String[]{"--prompt-draft-install-safety-consume-v1", "ABC"},
                new String[]{"--prompt-draft-install-safety-consume-v1", NONCE, "extra"},
                new String[]{"--prompt-draft-install-safety-v1", NONCE}
        };
        for (String[] request : invalid) {
            boolean accepted = ((Boolean) exactConsumeRequestMethod.invoke(
                    null, (Object) request
            )).booleanValue();
            require(!accepted, "invalid_consume_request_accepted");
        }
    }

    private static Object evaluate(
            String visible,
            Map<String, Object> values,
            long before,
            long after,
            boolean pending,
            boolean composerAvailable,
            boolean storeAvailable
    ) throws Exception {
        return evaluateMethod.invoke(
                null,
                visible,
                values,
                Long.valueOf(before),
                Long.valueOf(after),
                Boolean.valueOf(pending),
                Boolean.valueOf(composerAvailable),
                Boolean.valueOf(storeAvailable)
        );
    }

    private static Object finalizeSeal(
            Object before,
            boolean commitSucceeded,
            Object after,
            boolean sealPresent
    ) throws Exception {
        return finalizeSeal(
                before,
                commitSucceeded,
                after,
                sealPresent,
                NONCE,
                NONCE
        );
    }

    private static Object finalizeSeal(
            Object before,
            boolean commitSucceeded,
            Object after,
            boolean sealPresent,
            String activeInvocationId,
            String requestedInvocationId
    ) throws Exception {
        return finalizeMethod.invoke(
                null,
                before,
                Boolean.valueOf(commitSucceeded),
                after,
                Boolean.valueOf(sealPresent),
                activeInvocationId,
                requestedInvocationId
        );
    }

    private static Object reconcileConsume(
            String invocationId,
            boolean processSealed,
            String activeInvocationId,
            String lastConsumedInvocationId,
            Object beforeCommit,
            boolean commitSucceeded,
            Object afterCommit,
            boolean persistedSeal,
            String persistedActiveInvocationId,
            String persistedLastConsumedInvocationId
    ) throws Exception {
        return reconcileConsumeMethod.invoke(
                null,
                invocationId,
                Boolean.valueOf(processSealed),
                activeInvocationId,
                lastConsumedInvocationId,
                beforeCommit,
                Boolean.valueOf(commitSucceeded),
                afterCommit,
                Boolean.valueOf(persistedSeal),
                persistedActiveInvocationId,
                persistedLastConsumedInvocationId
        );
    }

    private static String productionSealKey() throws Exception {
        return productionPreferenceKey("PREF_PROMPT_DRAFT_INSTALL_SAFETY_SEAL");
    }

    private static String productionPreferenceKey(String constantName) throws Exception {
        String source = new String(Files.readAllBytes(MAIN_SOURCE), StandardCharsets.UTF_8);
        String declaration = "private static final String " + constantName;
        int declarationOffset = source.indexOf(declaration);
        int valueStart = declarationOffset < 0
                ? -1
                : source.indexOf('"', declarationOffset + declaration.length());
        int valueEnd = valueStart < 0 ? -1 : source.indexOf('"', valueStart + 1);
        require(declarationOffset >= 0 && valueStart >= 0 && valueEnd > valueStart,
                "seal_key_literal_missing");
        return source.substring(valueStart + 1, valueEnd);
    }

    private static void assertResult(
            Object result,
            String outcome,
            String reason,
            boolean handoff,
            String code
    ) throws Exception {
        require(result != null, code + "_null_result");
        Field outcomeField = resultClass.getDeclaredField("outcome");
        Field reasonField = resultClass.getDeclaredField("reason");
        Field handoffField = resultClass.getDeclaredField("processSealHandoff");
        outcomeField.setAccessible(true);
        reasonField.setAccessible(true);
        handoffField.setAccessible(true);
        require(outcome.equals(String.valueOf(outcomeField.get(result))), code + "_outcome");
        require(reason.equals(String.valueOf(reasonField.get(result))), code + "_reason");
        require(handoff == handoffField.getBoolean(result), code + "_handoff");
    }

    private static void assertConsumeResult(
            Object result,
            String status,
            String reason,
            boolean writerReleased,
            String code
    ) throws Exception {
        require(result != null, code + "_null_result");
        Field statusField = consumeResultClass.getDeclaredField("status");
        Field reasonField = consumeResultClass.getDeclaredField("reason");
        Field writerReleasedField = consumeResultClass.getDeclaredField("writerReleased");
        statusField.setAccessible(true);
        reasonField.setAccessible(true);
        writerReleasedField.setAccessible(true);
        require(status.equals(String.valueOf(statusField.get(result))), code + "_status");
        require(reason.equals(String.valueOf(reasonField.get(result))), code + "_reason");
        require(
                writerReleased == writerReleasedField.getBoolean(result),
                code + "_writer_release"
        );
    }

    private static void runBehaviorCase(String name, CheckedCase checkedCase) {
        behaviorCases++;
        try {
            checkedCase.run();
        } catch (Throwable failure) {
            failures.add(name + "_behavior");
        }
    }

    private static void requireSource(boolean condition, String code) {
        if (!condition) {
            failures.add(code);
        }
    }

    private static void require(boolean condition, String code) {
        if (!condition) {
            throw new AssertionError(code);
        }
    }

    private static int count(String value, String needle) {
        int total = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            total++;
            offset += needle.length();
        }
        return total;
    }

    private static String joinCodes(List<String> codes) {
        StringBuilder joined = new StringBuilder();
        for (String code : codes) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(code);
        }
        return joined.toString();
    }

    private interface CheckedCase {
        void run() throws Exception;
    }

    private PromptDraftInstallSafetyAuthorityTest() {
    }
}
