package com.kaleeb.wezterm;

public final class WindowTitleBindingLastGoodTest {
    private static final String PANE_DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static MainActivity.CanonicalTitleEnvelope envelope(
            String threadId,
            long revision,
            String digest,
            String displayTitle
    ) {
        return new MainActivity.CanonicalTitleEnvelope(
                threadId,
                revision,
                "mantis-title-sync",
                displayTitle,
                digest,
                null
        );
    }

    private static MainActivity.CanonicalTitleBinding binding(
            String windowId,
            String threadId,
            long revision,
            String digest,
            String displayTitle
    ) {
        MainActivity.CanonicalTitleEnvelope envelope =
                envelope(threadId, revision, digest, displayTitle);
        MainActivity.TitleTransportBinding transport =
                new MainActivity.TitleTransportBinding(
                        windowId,
                        "main",
                        threadId,
                        "%30",
                        PANE_DIGEST,
                        "goal-30",
                        digest,
                        revision
                );
        return new MainActivity.CanonicalTitleBinding(envelope, transport);
    }

    public static void main(String[] args) {
        MainActivity.CanonicalTitleBinding previous = binding(
                "@30",
                "thread-30",
                78L,
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "TradingBot Gold Scalper Setup: checking inputs"
        );
        MainActivity.CanonicalTitleBinding refreshed = binding(
                "@30",
                "thread-30",
                79L,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                "TradingBot Gold Scalper Setup: restoring inputs"
        );
        MainActivity.CanonicalTitleBinding stale = binding(
                "@30",
                "thread-30",
                77L,
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "TradingBot Gold Scalper Setup: older cache"
        );
        MainActivity.CanonicalTitleBinding otherWindow = binding(
                "@39",
                "thread-39",
                4115L,
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                "Seenstage KAI Audio Boundary: testing phone path"
        );

        assert previous.envelope != null;
        assert previous.transport.references(previous.envelope);
        assert "TradingBot Gold Scalper Setup: checking inputs".equals(
                previous.envelope.displayTitle
        );
        assert refreshed.canReplaceVisibleBinding(previous);
        assert !stale.canReplaceVisibleBinding(previous);
        assert !otherWindow.canReplaceVisibleBinding(previous);
        assert refreshed.transport.sameSlotAs(previous.transport);
        assert refreshed.canReplaceSlotLkg(previous);
        assert !stale.canReplaceSlotLkg(previous);
        assert !otherWindow.canReplaceSlotLkg(previous);

        System.out.println("WINDOW_TITLE_BINDING_LAST_GOOD_PASS");
    }
}
