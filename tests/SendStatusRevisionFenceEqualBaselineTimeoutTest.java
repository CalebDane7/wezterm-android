package com.kaleeb.wezterm;

/**
 * Old-red guard for a fast Send whose terminal state is sampled at the exact
 * pre-Send canonical revision.
 *
 * WHY: the physical v295 path proved that a successfully completed turn can be
 * Ready while the publisher still exposes the equal baseline revision. The
 * local optimistic Working paint must block that stale Ready briefly, but a
 * strictly-newer-only timeout condition leaves the row green forever. The
 * bounded timeout is the liveness escape hatch; the accepted revision floor
 * still rejects an actually older response after release.
 */
public final class SendStatusRevisionFenceEqualBaselineTimeoutTest {
    private static SendStatusRevisionFence.Status ready(String revision) {
        return new SendStatusRevisionFence.Status(
                "waiting", "healthy", "Ready", false, revision
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        SendStatusRevisionFence fence = new SendStatusRevisionFence();
        long baselineAt = 1_000L;
        long sendAt = baselineAt + 1L;

        fence.present("@116", ready("100"), baselineAt);
        fence.begin("@116", "send-fast-equal", "100", sendAt);

        SendStatusRevisionFence.Status beforeHorizon = fence.present(
                "@116",
                ready("100"),
                sendAt + SendStatusRevisionFence.FAST_COMPLETE_FALLBACK_MS - 1L
        );
        require(beforeHorizon.isWorking(),
                "equal baseline Ready must remain held before the horizon");
        require(fence.hasFence("@116"),
                "equal baseline Ready must not clear the early fence");

        SendStatusRevisionFence.Status atHorizon = fence.present(
                "@116",
                ready("100"),
                sendAt + SendStatusRevisionFence.FAST_COMPLETE_FALLBACK_MS
        );
        require("waiting".equals(atHorizon.state),
                "equal baseline Ready must settle after the bounded horizon");
        require("100".equals(atHorizon.canonicalRevision),
                "timeout release must preserve the accepted canonical floor");
        require(!fence.hasFence("@116"),
                "bounded equal-baseline release must close the Send fence");

        SendStatusRevisionFence.Status lateOlder = fence.present(
                "@116", ready("99"), sendAt + 20_000L
        );
        require("waiting".equals(lateOlder.state)
                        && "100".equals(lateOlder.canonicalRevision),
                "late older Ready must not roll back the settled accepted floor");

        System.out.println("SEND_STATUS_EQUAL_BASELINE_TIMEOUT_GREEN");
    }
}
