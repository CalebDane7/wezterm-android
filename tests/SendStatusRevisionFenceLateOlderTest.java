package com.kaleeb.wezterm;

/** Independent dependency check for the isolated v294 Send-status candidate. */
public final class SendStatusRevisionFenceLateOlderTest {
    private static SendStatusRevisionFence.Status ready(String revision) {
        return new SendStatusRevisionFence.Status(
                "waiting", "healthy", "Ready", false, revision
        );
    }

    public static void main(String[] args) {
        SendStatusRevisionFence fence = new SendStatusRevisionFence();
        long start = 1_000L;

        fence.present("@116", ready("100"), start);
        fence.begin("@116", "send-a", "100", start + 1L);

        // WHY: concurrent /active responses are not ordered by arrival. An
        // older pre-Send Ready response must remain behind the active Send
        // fence just like the equal baseline revision; otherwise the toolbar
        // and freshly opened Active Sessions row can flash yellow again before
        // the canonical publisher advances to Working.
        SendStatusRevisionFence.Status presented = fence.present(
                "@116", ready("99"), start + 67L
        );
        if (!presented.isWorking()) {
            throw new AssertionError(
                    "older pre-Send Ready bypassed the active Working fence: "
                            + presented.state + "/" + presented.canonicalRevision
            );
        }

        System.out.println("SEND_STATUS_LATE_OLDER_FENCE_GREEN");
    }
}

