package com.kaleeb.wezterm;

/** Executable guard for title-neutral tmux controls during model usage limits. */
public final class RateLimitControlIndependenceTest {
    public static void main(String[] args) throws Exception {
        titleValidationSkipStillConfirmsExactActiveWindow();
        malformedOrMissingTitleProjectionCannotBlockExactTmuxIdentity();
        explicitSendableSessionLimitKeepsNativeComposerUsable();
        System.out.println("RATE_LIMIT_CONTROL_INDEPENDENCE_PASS");
    }

    private static void titleValidationSkipStillConfirmsExactActiveWindow() {
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy
                        .validationDispositionAllows(
                                false,
                                "active-validated-title-skipped"
                        ),
                "active validation is sufficient when only title validation is skipped"
        );
        assertFalse(
                MainActivity.ActiveSelectionRateLimitPolicy
                        .validationDispositionAllows(
                                true,
                                "active-and-title-validation-skipped"
                        ),
                "skipped active validation must remain fail-closed"
        );
    }

    private static void malformedOrMissingTitleProjectionCannotBlockExactTmuxIdentity()
            throws Exception {
        MainActivity.TitleTransportBinding transport = null;
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy
                        .titleTransportDoesNotContradict(
                                "@66",
                                "thread-66",
                                transport
                        ),
                "an unresolved title envelope is display state, not tmux navigation authority"
        );
    }

    private static void explicitSendableSessionLimitKeepsNativeComposerUsable() {
        assertFalse(
                MainActivity.ActiveSelectionRateLimitPolicy.interactionBlocksSend(
                        true,
                        true,
                        "session-limit",
                        ""
                ),
                "tmux input must remain usable while Codex reports an account limit"
        );
        assertFalse(
                MainActivity.ActiveSelectionRateLimitPolicy.targetReadyBlocksSend(
                        true,
                        false,
                        true,
                        true,
                        "session-limit",
                        ""
                ),
                "strict account readiness cannot override explicit limited-pane tmux sendability"
        );
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy.interactionBlocksSend(
                        true,
                        false,
                        "auth-action-required",
                        "auth-action-required"
                ),
                "stale-auth and transcript safety blocks remain unchanged"
        );
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy.interactionBlocksSend(
                        true,
                        true,
                        "session-limit",
                        "auth-action-required"
                ),
                "a mixed stale-auth reason must outrank an older sendable limit field"
        );
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy.targetReadyBlocksSend(
                        true,
                        false,
                        true,
                        true,
                        "auth-action-required",
                        "session-limit"
                ),
                "target readiness must still fail closed for a mixed stale-auth state"
        );
        assertTrue(
                MainActivity.ActiveSelectionRateLimitPolicy.interactionBlocksSend(
                        true,
                        true,
                        "transcript-only",
                        "session-limit"
                ),
                "a mixed transcript-only state must outrank an older limit reason"
        );
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }
}
