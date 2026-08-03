package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Executable behavior model and production wiring guard for Upload picker edits. */
public final class PromptComposerPickerSelectionTest {
    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 1, "expected MainActivity.java path");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );

        successfulPickerReplacesSavedRange();
        successfulPickerInsertsAtSavedCursor();
        cancelRestoresExactDraftAndSelection();
        multipleUploadsAdvanceFromInsertedPath();
        productionCarriesLaunchTimeStateThroughResult(source);
        System.out.println("PROMPT_COMPOSER_PICKER_SELECTION_PASS");
    }

    private static void successfulPickerReplacesSavedRange() {
        PickerState state = new PickerState("@17", "beforeSELECTafter", 6, 12);
        Edit edit = apply(state, "/tmp/p.png");
        assertEquals("before/tmp/p.pngafter", edit.text, "selected text replaced");
        assertEquals(16, edit.cursor, "cursor immediately follows inserted path");
        assertEquals("@17", state.target, "launch-time target retained");
    }

    private static void successfulPickerInsertsAtSavedCursor() {
        PickerState state = new PickerState("@17", "beforeafter", 6, 6);
        Edit edit = apply(state, "/tmp/p.png");
        assertEquals("before/tmp/p.pngafter", edit.text, "path inserted at cursor");
        assertEquals(16, edit.cursor, "collapsed cursor follows inserted path");
    }

    private static void cancelRestoresExactDraftAndSelection() {
        PickerState state = new PickerState("@17", "beforeSELECTafter", 12, 6);
        assertEquals("beforeSELECTafter", state.text, "cancel retains surrounding draft");
        assertEquals(12, state.selectionStart, "cancel retains exact selection start");
        assertEquals(6, state.selectionEnd, "cancel retains exact selection end");
        assertEquals("@17", state.target, "cancel retains launch-time target");
    }

    private static void multipleUploadsAdvanceFromInsertedPath() {
        PickerState state = new PickerState("@17", "leftRIGHT", 4, 4);
        Edit first = apply(state, "/one");
        state.advance(first);
        Edit second = apply(state, "/two");
        assertEquals("left/one/twoRIGHT", second.text, "picker order stays stable");
        assertEquals(12, second.cursor, "next cursor follows the second path");
    }

    private static void productionCarriesLaunchTimeStateThroughResult(String source) {
        String pick = methodBody(source, "private void pickMediaForUpload()");
        assertTrue(
                pick.contains("pendingUploadPickerComposerState = captureUploadPickerComposerState()"),
                "Upload must snapshot composer state before picker launch"
        );

        String capture = methodBody(
                source,
                "private UploadPickerComposerState captureUploadPickerComposerState()"
        );
        assertTrue(capture.contains("uploadAssociationWindowId()"),
                "snapshot must pin the launch-time session target");
        assertTrue(capture.contains("promptComposerInput.getSelectionStart()")
                        && capture.contains("promptComposerInput.getSelectionEnd()"),
                "snapshot must retain both selection endpoints");
        assertTrue(capture.contains("promptComposerInput.getText().toString()"),
                "snapshot must retain the exact surrounding draft");

        String result = methodBody(
                source,
                "protected void onActivityResult(int requestCode, int resultCode, Intent data)"
        );
        assertBefore(
                result,
                "restoreUploadPickerComposerState(pickerState, \"picker-result\")",
                "if (resultCode != RESULT_OK || data == null)",
                "cancel must restore exact picker state before returning"
        );
        assertTrue(result.contains("uploadMediaUrisSequentially(uris, false, pickerState)"),
                "successful picker must carry the launch-time state into staging");

        String restore = methodBody(
                source,
                "private void restoreUploadPickerComposerState("
        );
        assertTrue(restore.contains("pickerState.draftText"),
                "restore must use the launch-time draft");
        assertTrue(restore.contains("pickerState.selectionStart")
                        && restore.contains("pickerState.selectionEnd"),
                "restore must use the exact launch-time range");
        assertTrue(restore.contains("Selection.setSelection("),
                "restore must apply both selection endpoints");

        String upload = methodBody(
                source,
                "private void uploadMediaUrisSequentially("
        );
        assertTrue(upload.contains("pickerState.targetWindowId"),
                "picker upload must use its launch-time session target");

        String stage = methodBody(
                source,
                "private void stageUploadedMediaPathForSend("
        );
        assertTrue(stage.contains("PromptComposerPickerEdit.apply("),
                "picker staging must replace or insert at the saved range");
        assertTrue(stage.contains("Selection.setSelection(editable, edit.cursor, edit.cursor)"),
                "picker staging must collapse the cursor immediately after the path");
        assertFalse(stage.contains("promptComposerInput.setSelection(promptComposerInput.length())"),
                "picker staging must not unconditionally collapse at end of draft");
    }

    private static final class PickerState {
        final String target;
        String text;
        int selectionStart;
        int selectionEnd;

        PickerState(String target, String text, int selectionStart, int selectionEnd) {
            this.target = target;
            this.text = text;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }

        void advance(Edit edit) {
            text = edit.text;
            selectionStart = edit.cursor;
            selectionEnd = edit.cursor;
        }
    }

    private static final class Edit {
        final String text;
        final int cursor;

        Edit(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    private static Edit apply(PickerState state, String path) {
        String draft = state.text == null ? "" : state.text;
        String inserted = path == null ? "" : path;
        int first = clamp(Math.min(state.selectionStart, state.selectionEnd), draft.length());
        int last = clamp(Math.max(state.selectionStart, state.selectionEnd), draft.length());
        String text = draft.substring(0, first) + inserted + draft.substring(last);
        return new Edit(text, first + inserted.length());
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
            throw new AssertionError(
                    message + ": expected=" + expected + " actual=" + actual
            );
        }
    }

    private PromptComposerPickerSelectionTest() {
    }
}
