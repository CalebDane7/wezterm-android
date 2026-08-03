package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Executable model and source guard for draft selection/composition ownership. */
public final class PromptComposerImeStatePreservationTest {
    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 1, "expected MainActivity.java path");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );

        sameTargetSameBodyIsStrictNoOp();
        crossTargetRestoreWaitsForCompositionOwner();
        lateTargetOrRevisionCallbackCannotRestore();
        targetSelectionRangeIsRestoredAndClamped();
        productionWiresRealInputConnectionBoundary(source);
        System.out.println("PROMPT_COMPOSER_IME_STATE_PRESERVATION_PASS");
    }

    private static void sameTargetSameBodyIsStrictNoOp() {
        Editor editor = new Editor("@1", "compose me", 11L, 2, 7, true);
        Restore restore = new Restore("@1", "compose me", 11L, 0, 0);
        assertEquals(Disposition.NO_OP, editor.request(restore), "same draft must be no-op");
        assertEquals(2, editor.selectionStart, "selection start preserved");
        assertEquals(7, editor.selectionEnd, "selection end preserved");
        assertTrue(editor.composing, "composition remains owned by IME");
    }

    private static void crossTargetRestoreWaitsForCompositionOwner() {
        Editor editor = new Editor("@1", "draft A", 11L, 3, 6, true);
        Restore restore = new Restore("@2", "draft B", 22L, 1, 4);
        assertEquals(Disposition.DEFERRED, editor.request(restore), "cross-target restore defers");
        assertEquals("draft A", editor.body, "active composing buffer remains untouched");
        assertEquals("@1", editor.visibleTarget, "old target owns composing editor");

        assertEquals(
                Disposition.APPLIED,
                editor.finishComposition("@2", 22L),
                "real finishComposing callback releases exact B revision"
        );
        assertEquals("@2", editor.visibleTarget, "B becomes editor owner");
        assertEquals("draft B", editor.body, "B draft restored");
        assertEquals(1, editor.selectionStart, "B selection start restored");
        assertEquals(4, editor.selectionEnd, "B selection end restored");
    }

    private static void lateTargetOrRevisionCallbackCannotRestore() {
        Editor targetDrift = new Editor("@1", "draft A", 11L, 2, 2, true);
        targetDrift.request(new Restore("@2", "draft B", 22L, 1, 1));
        assertEquals(
                Disposition.STALE_IGNORED,
                targetDrift.finishComposition("@3", 22L),
                "late B callback cannot overwrite selected C"
        );

        Editor revisionDrift = new Editor("@1", "draft A", 11L, 2, 2, true);
        revisionDrift.request(new Restore("@2", "draft B", 22L, 1, 1));
        assertEquals(
                Disposition.STALE_IGNORED,
                revisionDrift.finishComposition("@2", 23L),
                "late callback cannot overwrite newer B revision"
        );
    }

    private static void targetSelectionRangeIsRestoredAndClamped() {
        Editor editor = new Editor("@1", "A", 1L, 0, 0, false);
        assertEquals(
                Disposition.APPLIED,
                editor.request(new Restore("@2", "four", 2L, 99, 2)),
                "composition-free target restore applies"
        );
        assertEquals(4, editor.selectionStart, "selection start clamped to length");
        assertEquals(2, editor.selectionEnd, "selection direction/end preserved");
    }

    private static void productionWiresRealInputConnectionBoundary(String source) {
        String reset = methodBody(
                source,
                "private void resetPromptComposerLocalDraftForCurrentTarget()"
        );
        assertTrue(
                reset.contains("applyPromptComposerDraftRestore(restore, \"target-reset\")"),
                "target reset must use the IME-safe restore owner"
        );
        assertFalse(reset.contains("promptComposerInput.setText("),
                "target reset must not replace the Editable");

        String apply = methodBody(
                source,
                "private boolean applyPromptComposerDraftRestore("
        );
        assertBefore(
                apply,
                "if (sameTargetAndBody)",
                "promptComposerHasActiveComposition(editable)",
                "same target/body must exit before any composition handling"
        );
        assertTrue(apply.contains("editable.replace(0, editable.length(), restore.displayDraft)"),
                "safe restore must mutate the existing Editable");
        assertTrue(apply.contains("promptComposerInput.beginBatchEdit()")
                        && apply.contains("promptComposerInput.endBatchEdit()"),
                "safe restore must be one editor batch");
        assertTrue(apply.contains("Selection.setSelection(editable, selectionStart, selectionEnd)"),
                "target-local range must be restored");
        assertFalse(apply.contains("promptComposerInput.setText("),
                "safe restore must not replace the Editable");

        String focus = methodBody(
                source,
                "private void restoreDockedPromptComposerFocus(String reason)"
        );
        assertFalse(focus.contains("setSelection("),
                "resume and delayed focus callbacks must not collapse selection");

        String connection = methodBody(
                source,
                "public InputConnection onCreateInputConnection(EditorInfo outAttrs)"
        );
        assertTrue(connection.contains("public boolean finishComposingText()")
                        && connection.contains("public boolean commitText("),
                "real InputConnection completion paths must release deferred restore");
        assertTrue(connection.contains("releasePendingPromptComposerDraftRestoreAfterIme("),
                "InputConnection callbacks must post the pending restore owner");
        assertFalse(connection.contains("new BaseInputConnection"),
                "production must wrap the real connection, never synthesize one");

        assertTrue(source.contains("BaseInputConnection.getComposingSpanStart(editable)")
                        && source.contains("BaseInputConnection.getComposingSpanEnd(editable)"),
                "production must use public composing-span ownership checks");
        assertTrue(source.contains("PREF_PROMPT_DRAFT_SELECTION_REVISION_PREFIX")
                        && source.contains("rememberPromptComposerDraftSelection("),
                "selection must be bound to the target draft revision");
    }

    private enum Disposition {
        NO_OP,
        DEFERRED,
        APPLIED,
        STALE_IGNORED
    }

    private static final class Restore {
        final String target;
        final String body;
        final long revision;
        final int selectionStart;
        final int selectionEnd;

        Restore(String target, String body, long revision, int selectionStart, int selectionEnd) {
            this.target = target;
            this.body = body;
            this.revision = revision;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }
    }

    private static final class Editor {
        String visibleTarget;
        String body;
        long revision;
        int selectionStart;
        int selectionEnd;
        boolean composing;
        Restore pending;

        Editor(
                String visibleTarget,
                String body,
                long revision,
                int selectionStart,
                int selectionEnd,
                boolean composing
        ) {
            this.visibleTarget = visibleTarget;
            this.body = body;
            this.revision = revision;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
            this.composing = composing;
        }

        Disposition request(Restore restore) {
            if (visibleTarget.equals(restore.target) && body.equals(restore.body)) {
                pending = null;
                return Disposition.NO_OP;
            }
            if (composing) {
                pending = restore;
                return Disposition.DEFERRED;
            }
            apply(restore);
            return Disposition.APPLIED;
        }

        Disposition finishComposition(String currentTarget, long currentRevision) {
            composing = false;
            Restore restore = pending;
            pending = null;
            if (restore == null
                    || !restore.target.equals(currentTarget)
                    || restore.revision != currentRevision) {
                return Disposition.STALE_IGNORED;
            }
            apply(restore);
            return Disposition.APPLIED;
        }

        private void apply(Restore restore) {
            visibleTarget = restore.target;
            body = restore.body;
            revision = restore.revision;
            selectionStart = clamp(restore.selectionStart, body.length());
            selectionEnd = clamp(restore.selectionEnd, body.length());
        }
    }

    private static int clamp(int value, int length) {
        return Math.max(0, Math.min(Math.max(0, length), value));
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

    private PromptComposerImeStatePreservationTest() {
    }
}
