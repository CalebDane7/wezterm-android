package com.kaleeb.wezterm;

/** Verifies a second admitted Send cannot inherit the first Send's terminal edge. */
public final class SendStatusRevisionFenceStackedAfterWorkingTest {
    private static SendStatusRevisionFence.Status status(
            String state, String tone, String label, String revision
    ) {
        return new SendStatusRevisionFence.Status(
                state, tone, label, false, revision
        );
    }

    public static void main(String[] args) {
        SendStatusRevisionFence fence = new SendStatusRevisionFence();
        long start = 1_000L;

        fence.present(
                "@116",
                status("waiting", "healthy", "Ready", "100"),
                start
        );
        fence.begin("@116", "send-a", "100", start + 1L);
        fence.present(
                "@116",
                status("working", "working", "Working", "101"),
                start + 100L
        );

        // WHY: the composer can admit another exact Send while the first turn
        // is still Working. The first turn's newer terminal revision must not
        // release the second operation before that operation gets its own
        // canonical Working transition.
        fence.begin("@116", "send-b", "101", start + 200L);
        SendStatusRevisionFence.Status firstTerminal = fence.present(
                "@116",
                status("waiting", "healthy", "Ready", "102"),
                start + 300L
        );
        if (!firstTerminal.isWorking() || !fence.hasFence("@116")) {
            throw new AssertionError(
                    "first terminal revision released the later Send: "
                            + firstTerminal.state + "/"
                            + firstTerminal.canonicalRevision
            );
        }

        fence.present(
                "@116",
                status("working", "working", "Working", "103"),
                start + 400L
        );
        SendStatusRevisionFence.Status secondTerminal = fence.present(
                "@116",
                status("waiting", "healthy", "Ready", "104"),
                start + 500L
        );
        if (!"waiting".equals(secondTerminal.state) || fence.hasFence("@116")) {
            throw new AssertionError("second terminal revision did not settle");
        }

        System.out.println("SEND_STATUS_STACKED_AFTER_WORKING_GREEN");
    }
}

