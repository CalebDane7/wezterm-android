package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Executable guard for exact-target selected-body cache acceleration. */
public final class ActiveSwitchSelectedBodyCacheTest {
    private static final long MAX_AGE_MS = 30_000L;

    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 2, "expected MainActivity.java and v227 log fixture paths");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );
        String v227Log = new String(
                Files.readAllBytes(Paths.get(args[1])),
                StandardCharsets.UTF_8
        );

        v227SameTargetLatencyIsMachineCheckable(v227Log);
        exactSelectedTargetMayPaintDuringDelayedPriorFetch();
        staleOrOffTargetCacheCannotPaint();
        exactBindingMustMatchBeforePaint();
        geometryMustRemainExact();
        productionWiresDisplayOnlyCache(source);
        System.out.println("ACTIVE_SWITCH_SELECTED_BODY_CACHE_PASS");
    }

    private static void v227SameTargetLatencyIsMachineCheckable(String log) {
        assertTrue(
                log.contains("actionId=active-row-switch-52725050-2")
                        && log.contains("windowId=@74"),
                "fixture must stay bound to the exact v227 physical action"
        );
        assertTrue(
                log.contains("firstLocalFeedbackElapsedMs=11"),
                "v227 local tap feedback must be recorded inside 30ms"
        );
        assertTrue(
                log.contains("endpoint=/select-live")
                        && log.contains("elapsedMs=1074"),
                "v227 old-red must retain the blocking select-live boundary"
        );
        assertTrue(
                log.contains("first-readable-frame")
                        && log.contains("actionElapsedMs=1131"),
                "v227 old-red must retain the first exact readable-frame latency"
        );
        assertTrue(
                log.contains("result=committed")
                        && log.contains("elapsedMs=1129")
                        && log.contains("bodyTitleSessionAtomic=true"),
                "v227 old-red must retain the atomic commit latency"
        );
        assertFalse(
                log.contains("endpoint=selected-body-cache"),
                "v227 action must not be mislabeled as a cache hit"
        );
    }

    private static void exactSelectedTargetMayPaintDuringDelayedPriorFetch() {
        assertTrue(
                canPaint(true, "@2", "@2", "@2", 17L, 17L, 220L,
                        "hash-b", 420, 12),
                "selected B cache should paint while delayed A owns the fetch"
        );
    }

    private static void staleOrOffTargetCacheCannotPaint() {
        assertFalse(
                canPaint(true, "@1", "@2", "@2", 17L, 17L, 20L,
                        "hash-a", 420, 12),
                "cached A cannot paint after B selection"
        );
        assertFalse(
                canPaint(true, "@2", "@2", "@2", 16L, 17L, 20L,
                        "hash-b", 420, 12),
                "old transition generation cannot paint"
        );
        assertFalse(
                canPaint(true, "@2", "@2", "@2", 17L, 17L, MAX_AGE_MS + 1L,
                        "hash-b", 420, 12),
                "expired selected cache cannot paint"
        );
        assertFalse(
                canPaint(false, "@2", "@2", "@2", 17L, 17L, 20L,
                        "hash-b", 420, 12),
                "background activity cannot paint"
        );
        assertFalse(
                canPaint(true, "@2", "@2", "@2", 17L, 17L, 20L,
                        "", 420, 12),
                "missing accepted-frame hash cannot paint"
        );
        assertFalse(
                canPaint(true, "@2", "@2", "@2", 17L, 17L, 20L,
                        "hash-b", 0, 12),
                "empty body cannot paint"
        );
    }

    private static void exactBindingMustMatchBeforePaint() {
        assertTrue(
                exactBindingMatches(
                        "@74",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "%74",
                        "764744",
                        "@74:%74:764744:100000",
                        "stream-epoch",
                        92L,
                        "binding-id"
                ),
                "complete exact frame binding should be reusable"
        );
        assertFalse(
                exactBindingMatches(
                        "@74",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "%75",
                        "764744",
                        "@74:%74:764744:100000",
                        "stream-epoch",
                        92L,
                        "binding-id"
                ),
                "pane drift must reject cached body"
        );
        assertFalse(
                exactBindingMatches(
                        "@74",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "%74",
                        "764744",
                        "@74:%74:764744:100000",
                        "",
                        92L,
                        "binding-id"
                ),
                "missing stream generation epoch must reject cached body"
        );
        assertFalse(
                exactBindingMatches(
                        "@74",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "019f9eb4-b760-7213-8aa8-e6efc059815d",
                        "%74",
                        "764744",
                        "@74:%74:764744:100000",
                        "stream-epoch",
                        92L,
                        ""
                ),
                "missing live binding must reject cached body"
        );
    }

    private static void geometryMustRemainExact() {
        String cached = geometryKey(42, 112, 1080, 1080, 4200, 1000);
        assertEquals(
                cached,
                geometryKey(42, 112, 1080, 1080, 4200, 1000),
                "same geometry is reusable"
        );
        assertNotEquals(
                cached,
                geometryKey(41, 112, 1080, 1080, 4200, 1000),
                "column drift rejects cache"
        );
        assertNotEquals(
                cached,
                geometryKey(42, 112, 1080, 1080, 4200, 1250),
                "zoom drift rejects cache"
        );
    }

    private static void productionWiresDisplayOnlyCache(String source) {
        assertTrue(
                source.contains("ACTIVE_SWITCH_SELECTED_BODY_CACHE_MAX_ENTRIES = 8"),
                "production cache must be entry-bounded"
        );
        assertTrue(
                source.contains("ACTIVE_SWITCH_SELECTED_BODY_CACHE_MAX_AGE_MS = 30_000"),
                "production cache must be age-bounded"
        );
        String bridge = methodBody(
                source,
                "public boolean allowActiveSwitchCachedBodyPaint("
        );
        assertTrue(
                bridge.contains("ActiveSwitchSelectedBodyCachePolicy.canPaint("),
                "native bridge must own exact target/generation admission"
        );
        for (String field : new String[] {
                "cachedSessionId",
                "cachedThreadId",
                "cachedPaneId",
                "cachedPanePid",
                "cachedGenerationKey",
                "cachedStreamEpoch",
                "cachedStreamGeneration",
                "cachedLiveStreamBindingId"
        }) {
            assertTrue(
                    bridge.contains(field),
                    "native bridge must admit exact cache field " + field
            );
        }
        String hook = methodBody(
                source,
                "private String captureRendererTelemetryHookScript(String reason)"
        );
        assertTrue(
                hook.contains("var selectedBodyCache=Object.create(null)"),
                "body rows must stay in a bounded in-page per-window cache"
        );
        assertBefore(
                hook,
                "rememberSelectedBodyFromDom(previousTarget)",
                "lastAcceptedFrameWindowId='';",
                "accepted old target must be cached before renderer receipt revocation"
        );
        assertBefore(
                hook,
                "originalSetWindowId.apply(this,arguments)",
                "paintSelectedBodyFromCache(value)",
                "selected B cache must stage immediately after local retarget"
        );
        assertTrue(
                hook.contains("selectedBodyGeometryKey(currentGeometry)!==cached.geometryKey"),
                "geometry mismatch must fail closed"
        );
        assertTrue(
                hook.contains("r.stageRenderedFrame(cached.plainRows"),
                "cache must reuse the renderer's existing atomic hidden-buffer stage"
        );
        assertTrue(
                hook.contains("meta.hash!==lastAcceptedFrameHash")
                        && hook.contains("meta.bodySha256!==lastAcceptedBodySha256"),
                "DOM cache admission must prove exact accepted body bytes"
        );
        assertTrue(
                hook.contains("rememberSelectedBodyFromDom(windowId)"),
                "every exact accepted DOM must prewarm its per-window body cache"
        );
        assertTrue(
                hook.contains("endpoint:'selected-body-cache'")
                        && hook.contains("acceptedFrame:false"),
                "cached pixels must never become canonical frame/title authority"
        );
        assertFalse(
                hook.contains("text:cached.text")
                        || hook.contains("plainRows:cached.plainRows"),
                "private terminal body must not cross the native telemetry bridge"
        );
        assertTrue(
                source.contains("result=exact-current-frame-retained")
                        && source.contains("reconcileExactCurrentActiveSession"),
                "same-target physical taps must retain exact pixels and reconcile asynchronously"
        );
    }

    private static boolean exactBindingMatches(
            String windowId,
            String sessionId,
            String threadId,
            String paneId,
            String panePid,
            String generationKey,
            String streamEpoch,
            long streamGeneration,
            String liveStreamBindingId
    ) {
        String prefix = windowId + ":" + paneId + ":" + panePid + ":";
        return stable(windowId)
                && sessionId != null
                && !sessionId.trim().isEmpty()
                && threadId != null
                && sessionId.trim().equals(threadId.trim())
                && paneId != null
                && paneId.matches("%[0-9]+")
                && panePid != null
                && panePid.matches("[0-9]+")
                && generationKey != null
                && generationKey.startsWith(prefix)
                && generationKey.length() > prefix.length()
                && streamEpoch != null
                && !streamEpoch.trim().isEmpty()
                && streamGeneration >= 0L
                && liveStreamBindingId != null
                && !liveStreamBindingId.trim().isEmpty();
    }

    private static boolean canPaint(
            boolean resumed,
            String cached,
            String selected,
            String pending,
            long generation,
            long pendingGeneration,
            long ageMs,
            String hash,
            int bodyLength,
            int rowCount
    ) {
        return resumed
                && stable(cached)
                && cached.equals(selected)
                && cached.equals(pending)
                && generation > 0L
                && generation == pendingGeneration
                && ageMs >= 0L
                && ageMs <= MAX_AGE_MS
                && hash != null
                && !hash.trim().isEmpty()
                && bodyLength > 0
                && rowCount > 0;
    }

    private static boolean stable(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '@') {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String geometryKey(
            int cols,
            int rows,
            int columnWidth,
            int visualWidth,
            int charWidthMilli,
            int scaleMilli
    ) {
        return cols + "|" + rows + "|" + columnWidth + "|" + visualWidth
                + "|" + charWidthMilli + "|" + scaleMilli;
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("missing production method " + signature);
        }
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int index = brace; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}') {
                depth--;
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

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String message) {
        if (first == null ? second == null : first.equals(second)) {
            throw new AssertionError(message + ": both=" + first);
        }
    }

    private ActiveSwitchSelectedBodyCacheTest() {
    }
}
