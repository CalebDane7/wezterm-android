package com.kaleeb.wezterm;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a locally admitted Send from being repainted by the unchanged
 * pre-Send canonical Ready revision.
 *
 * <p>The class is deliberately presentation-only. It never invents or writes a
 * canonical revision. It holds Working until the sole status publisher emits a
 * Working revision and then a newer terminal revision. The same monotonic
 * revision floor also rejects late HTTP responses after the fence settles.</p>
 */
final class SendStatusRevisionFence {
    static final long FAST_COMPLETE_FALLBACK_MS = 15_000L;

    static final class Status {
        final String state;
        final String tone;
        final String label;
        final boolean needsAttention;
        final String canonicalRevision;

        Status(
                String state,
                String tone,
                String label,
                boolean needsAttention,
                String canonicalRevision
        ) {
            this.state = normalized(state);
            this.tone = normalized(tone);
            this.label = label == null ? "" : label.trim();
            this.needsAttention = needsAttention;
            this.canonicalRevision = canonicalRevision == null
                    ? ""
                    : canonicalRevision.trim();
        }

        boolean isWorking() {
            return "running".equals(state)
                    || "working".equals(state)
                    || "running".equals(tone)
                    || "working".equals(tone);
        }

        boolean isProblem() {
            return "problem".equals(state) || "problem".equals(tone);
        }

        Status asSendWorking(String revision) {
            return new Status("running", "working", "Working", false, revision);
        }
    }

    private static final class Lane {
        final String windowId;
        final Set<String> operationIds = new LinkedHashSet<>();
        long startedAtMs;
        String baselineRevision;
        String workingRevision = "";

        Lane(String windowId, String operationId, String baselineRevision, long startedAtMs) {
            this.windowId = windowId;
            this.startedAtMs = Math.max(0L, startedAtMs);
            this.baselineRevision = clean(baselineRevision);
            this.operationIds.add(operationId);
        }
    }

    private final Map<String, Lane> lanes = new LinkedHashMap<>();
    private final Map<String, Status> acceptedByWindow = new LinkedHashMap<>();

    void begin(
            String windowId,
            String operationId,
            String baselineRevision,
            long startedAtMs
    ) {
        String stableWindowId = clean(windowId);
        if (stableWindowId.isEmpty()) {
            return;
        }
        String stableOperationId = clean(operationId);
        if (stableOperationId.isEmpty()) {
            stableOperationId = "local-send-" + Math.max(0L, startedAtMs);
        }
        String baseline = clean(baselineRevision);
        Status accepted = acceptedByWindow.get(stableWindowId);
        if (accepted != null) {
            baseline = maxRevision(baseline, accepted.canonicalRevision);
        }
        Lane lane = lanes.get(stableWindowId);
        if (lane == null) {
            lanes.put(
                    stableWindowId,
                    new Lane(stableWindowId, stableOperationId, baseline, startedAtMs)
            );
            return;
        }
        boolean addedOperation = lane.operationIds.add(stableOperationId);
        lane.baselineRevision = maxRevision(lane.baselineRevision, baseline);
        if (addedOperation && !lane.workingRevision.isEmpty()) {
            // WHY: a later admitted Send needs its own Working->terminal
            // sequence. Reusing the prior operation's Working watermark lets
            // that prior operation's Ready revision release the new fence.
            lane.workingRevision = "";
        }
        if (addedOperation && startedAtMs > 0L) {
            lane.startedAtMs = startedAtMs;
        } else if (lane.startedAtMs <= 0L) {
            lane.startedAtMs = Math.max(0L, startedAtMs);
        }
    }

    void definitiveFailure(String windowId, String operationId) {
        String stableWindowId = clean(windowId);
        Lane lane = lanes.get(stableWindowId);
        if (lane == null) {
            return;
        }
        lane.operationIds.remove(clean(operationId));
        // A canonical Working observation outranks a late/ambiguous local
        // failure callback: keep the fence until a newer terminal revision.
        if (lane.operationIds.isEmpty() && lane.workingRevision.isEmpty()) {
            lanes.remove(stableWindowId);
        }
    }

    Status present(String windowId, Status incoming, long observedAtMs) {
        if (incoming == null) {
            return null;
        }
        String stableWindowId = clean(windowId);
        if (stableWindowId.isEmpty()) {
            return incoming;
        }

        Lane lane = lanes.get(stableWindowId);
        Status accepted = acceptedByWindow.get(stableWindowId);
        if (lane == null) {
            if (isStrictlyOlder(
                    incoming.canonicalRevision,
                    revisionOf(accepted)
            )) {
                return accepted;
            }
            return accept(stableWindowId, incoming);
        }

        if (isStrictlyOlder(
                incoming.canonicalRevision,
                revisionOf(accepted)
        )) {
            if (accepted != null && accepted.isWorking()) {
                return accepted;
            }
            // WHY: the monotonic accepted floor must not bypass an active Send
            // lane. HTTP responses can complete out of order; returning the
            // accepted pre-Send Ready tuple here reopened the exact yellow
            // frame before the canonical publisher advanced to Working.
            return incoming.asSendWorking(maxRevision(
                    lane.baselineRevision,
                    lane.workingRevision
            ));
        }

        if (incoming.isProblem()) {
            // Problems are never masked by an optimistic Send presentation.
            lanes.remove(stableWindowId);
            return accept(stableWindowId, incoming);
        }

        if (incoming.isWorking()) {
            lane.workingRevision = maxRevision(
                    lane.workingRevision,
                    incoming.canonicalRevision
            );
            return accept(stableWindowId, incoming);
        }

        String watermark = maxRevision(
                lane.baselineRevision,
                lane.workingRevision
        );
        if (!lane.workingRevision.isEmpty()
                && isStrictlyNewer(incoming.canonicalRevision, watermark)) {
            lanes.remove(stableWindowId);
            return accept(stableWindowId, incoming);
        }

        long elapsedMs = Math.max(0L, observedAtMs - lane.startedAtMs);
        if (lane.workingRevision.isEmpty()
                && elapsedMs >= FAST_COMPLETE_FALLBACK_MS) {
            // WHY FAST-COMPLETE LIVENESS (physical v295 old-red): a valid Send
            // can finish between publisher samples, and the accepted terminal
            // tuple can therefore still carry the exact pre-Send revision. The
            // old strictly-newer gate converted this bounded 15-second escape
            // hatch into an infinite green/Working lie when the 33,212-byte
            // lifecycle feed stopped advancing. Do not restore that gate.
            //
            // This branch is safe only while no canonical Working revision has
            // been observed. Problems already escape above, an actually older
            // response is held by the accepted-floor check above, accept()
            // preserves the monotonic revision floor, and begin() restarts the
            // horizon for every later stacked Send. Once Working is observed,
            // only its strictly newer terminal revision may release this lane.
            lanes.remove(stableWindowId);
            return accept(stableWindowId, incoming);
        }

        // WHY: this is the old-red boundary. The unchanged pre-Send Ready
        // revision must not reclaim the local Working paint while the accepted
        // Send is waiting for the sole canonical publisher to advance.
        return incoming.asSendWorking(watermark);
    }

    String lastAcceptedRevision(String windowId) {
        return revisionOf(acceptedByWindow.get(clean(windowId)));
    }

    boolean hasFence(String windowId) {
        return lanes.containsKey(clean(windowId));
    }

    private Status accept(String windowId, Status incoming) {
        Status previous = acceptedByWindow.get(windowId);
        if (previous != null) {
            if (isStrictlyOlder(
                    incoming.canonicalRevision,
                    previous.canonicalRevision
            )) {
                return previous;
            }
            if (incoming.canonicalRevision.isEmpty()
                    && !previous.canonicalRevision.isEmpty()
                    && !incoming.isProblem()) {
                return previous;
            }
        }
        acceptedByWindow.put(windowId, incoming);
        return incoming;
    }

    private static String revisionOf(Status status) {
        return status == null ? "" : status.canonicalRevision;
    }

    private static String normalized(String value) {
        return clean(value).toLowerCase(Locale.US);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String maxRevision(String first, String second) {
        String a = clean(first);
        String b = clean(second);
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty() || a.equals(b)) {
            return a;
        }
        Integer comparison = numericRevisionComparison(a, b);
        return comparison != null && comparison < 0 ? b : a;
    }

    private static boolean isStrictlyNewer(String candidate, String prior) {
        String a = clean(candidate);
        String b = clean(prior);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return false;
        }
        Integer comparison = numericRevisionComparison(a, b);
        return comparison != null && comparison > 0;
    }

    private static boolean isStrictlyOlder(String candidate, String accepted) {
        String a = clean(candidate);
        String b = clean(accepted);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return false;
        }
        Integer comparison = numericRevisionComparison(a, b);
        return comparison != null && comparison < 0;
    }

    private static Integer numericRevisionComparison(String first, String second) {
        if (!digitsOnly(first) || !digitsOnly(second)) {
            return null;
        }
        return new BigInteger(first).compareTo(new BigInteger(second));
    }

    private static boolean digitsOnly(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
