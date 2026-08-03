package com.kaleeb.wezterm;

public final class SendStatusRevisionFenceTest {
    private static SendStatusRevisionFence.Status status(
            String state,
            String tone,
            String label,
            boolean attention,
            String revision
    ) {
        return new SendStatusRevisionFence.Status(
                state,
                tone,
                label,
                attention,
                revision
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        SendStatusRevisionFence fence = new SendStatusRevisionFence();
        long start = 1_000L;

        SendStatusRevisionFence.Status ready100 = status(
                "waiting", "healthy", "Ready", false, "100"
        );
        require(
                "waiting".equals(fence.present("@116", ready100, start).state),
                "baseline Ready must stay Ready before Send"
        );

        fence.begin("@116", "send-a", "100", start + 1L);
        SendStatusRevisionFence.Status held = fence.present(
                "@116", ready100, start + 67L
        );
        require(held.isWorking(), "pre-Send Ready revision must be held Working");
        require("100".equals(held.canonicalRevision), "hold must retain baseline revision");

        SendStatusRevisionFence.Status working101 = status(
                "working", "working", "Working", false, "101"
        );
        require(
                fence.present("@116", working101, start + 3_314L).isWorking(),
                "canonical Working revision must commit"
        );

        SendStatusRevisionFence.Status lateReady100 = fence.present(
                "@116", ready100, start + 3_400L
        );
        require(lateReady100.isWorking(), "late pre-Send Ready must not regress Working");
        require(
                "101".equals(lateReady100.canonicalRevision),
                "late response must retain accepted Working revision"
        );

        SendStatusRevisionFence.Status ready102 = status(
                "waiting", "healthy", "Ready", false, "102"
        );
        require(
                "waiting".equals(fence.present("@116", ready102, start + 5_000L).state),
                "newer Ready after Working must settle yellow"
        );
        require(!fence.hasFence("@116"), "newer Ready must close the Send fence");
        require(
                "waiting".equals(fence.present("@116", working101, start + 5_100L).state),
                "late older Working must not regress settled Ready"
        );

        fence.present("@99", status("waiting", "healthy", "Ready", false, "200"), start);
        fence.begin("@99", "send-b", "200", start + 1L);
        fence.begin("@99", "send-c", "200", start + 2L);
        fence.definitiveFailure("@99", "send-b");
        require(
                fence.present("@99", status("waiting", "healthy", "Ready", false, "200"), start + 20L).isWorking(),
                "one stacked failure must not clear another admitted Send"
        );
        fence.definitiveFailure("@99", "send-c");
        require(!fence.hasFence("@99"), "all exact pre-working failures must clear the fence");

        fence.present("@18", status("waiting", "healthy", "Ready", false, "300"), start);
        fence.begin("@18", "send-fast", "300", start + 1L);
        SendStatusRevisionFence.Status fastReady301 = status(
                "waiting", "healthy", "Ready", false, "301"
        );
        require(
                fence.present("@18", fastReady301, start + 5_000L).isWorking(),
                "newer terminal revision before the fast-complete horizon must stay Working"
        );
        require(
                "waiting".equals(fence.present(
                        "@18",
                        fastReady301,
                        start + SendStatusRevisionFence.FAST_COMPLETE_FALLBACK_MS + 2L
                ).state),
                "newer terminal revision after the horizon must settle a sample-skipping turn"
        );

        fence.present("@39", status("waiting", "healthy", "Ready", false, "400"), start);
        fence.begin("@39", "send-problem", "400", start + 1L);
        SendStatusRevisionFence.Status problem401 = status(
                "problem", "problem", "Problem", true, "401"
        );
        SendStatusRevisionFence.Status problem = fence.present(
                "@39", problem401, start + 10L
        );
        require(problem.isProblem(), "real newer problem must never be masked green");
        require(problem.needsAttention, "real problem must retain attention state");

        require(
                "waiting".equals(fence.present("@116", ready102, start + 6_000L).state),
                "other-window fences must never contaminate a settled lane"
        );

        System.out.println("SEND_STATUS_REVISION_FENCE_TEST_GREEN");
    }
}
