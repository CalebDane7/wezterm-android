package com.kaleeb.wezterm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Executable model and production wiring guard for multiline composer alignment.
 */
public final class PromptComposerLayoutStabilityTest {
    public static void main(String[] args) throws Exception {
        assertTrue(args.length == 1, "expected MainActivity.java path");
        String source = new String(
                Files.readAllBytes(Paths.get(args[0])),
                StandardCharsets.UTF_8
        );

        baselineAlignmentCanOverrideBottomGravity();
        baselineAlignmentCanClipAFixedActionAtTheTopEdge();
        disabledBaselineAlignmentKeepsActionsOnTheBottomEdge();
        productionDisablesBaselineCouplingBeforeAddingChildren(source);
        System.out.println(
                "PROMPT_COMPOSER_LAYOUT_STABILITY_PASS "
                        + "wrappedLines=4 upload=48dp resend=48dp "
                        + "uploadTop=contained resendTop=contained "
                        + "bottomEdge=stable baselineCoupling=disabled"
        );
    }

    private static void baselineAlignmentCanOverrideBottomGravity() {
        Child editor = new Child(88, 24);
        Child action = new Child(48, 32);
        int parentHeight = baselineAlignedParentHeight(editor, action);

        int editorTop = baselineAlignedBottomTop(parentHeight, editor, editor.descent());
        int actionTop = baselineAlignedBottomTop(parentHeight, action, editor.descent());

        assertEquals(96, parentHeight,
                "baseline groups can inflate a multiline composer's row height");
        assertEquals(parentHeight, editorTop + editor.height,
                "the tallest editor still reaches the bottom edge");
        assertTrue(actionTop + action.height < parentHeight,
                "baseline descent adjustment lifts the fixed action above the bottom edge");
    }

    private static void baselineAlignmentCanClipAFixedActionAtTheTopEdge() {
        Child editor = new Child(48, 22);
        Child action = new Child(48, 36);
        int touchSlotHeight = 48;

        int actionTop = baselineAlignedBottomTop(
                touchSlotHeight,
                action,
                editor.descent()
        );

        assertTrue(actionTop < 0,
                "baseline descent adjustment can place a fixed 48dp action above its slot");
        assertTrue(actionTop + action.height < touchSlotHeight,
                "the same adjustment detaches the action from the slot's bottom edge");
    }

    private static void disabledBaselineAlignmentKeepsActionsOnTheBottomEdge() {
        Child editor = new Child(88, 24);
        Child upload = new Child(48, 36);
        Child resend = new Child(48, 32);
        int parentHeight = Math.max(editor.height, Math.max(upload.height, resend.height));

        int editorTop = bottomTop(parentHeight, editor);
        int uploadTop = bottomTop(parentHeight, upload);
        int resendTop = bottomTop(parentHeight, resend);

        assertEquals(parentHeight, editorTop + editor.height,
                "editor remains on the bottom edge");
        assertEquals(parentHeight, uploadTop + upload.height,
                "Upload remains on the same bottom edge");
        assertEquals(parentHeight, resendTop + resend.height,
                "Resend remains on the same bottom edge");
        assertEquals(40, uploadTop,
                "Upload stays wholly inside the multiline row");
        assertEquals(40, resendTop,
                "Resend stays wholly inside the multiline row");
    }

    private static void productionDisablesBaselineCouplingBeforeAddingChildren(String source) {
        String composer = methodBody(
                source,
                "private LinearLayout buildPromptComposer()"
        );
        assertBefore(
                composer,
                "composer.setOrientation(LinearLayout.HORIZONTAL);",
                "composer.setBaselineAligned(false);",
                "horizontal orientation must be established before disabling baseline coupling"
        );
        assertBefore(
                composer,
                "composer.setBaselineAligned(false);",
                "composer.setGravity(android.view.Gravity.BOTTOM);",
                "baseline coupling must be disabled before bottom gravity is selected"
        );
        assertBefore(
                composer,
                "composer.setBaselineAligned(false);",
                "composer.addView(uploadButton, uploadParams);",
                "baseline coupling must be disabled before any composer child is added"
        );
        assertTrue(composer.contains("promptComposerInput.setMaxLines(4);"),
                "multiline growth must remain capped at four lines");
        assertTrue(composer.contains("LinearLayout.LayoutParams.WRAP_CONTENT"),
                "the editor must keep content-driven height");
        assertTrue(composer.contains("uploadParams.gravity = android.view.Gravity.BOTTOM;"),
                "Upload must keep explicit bottom gravity");
        assertTrue(composer.contains("resendParams.gravity = android.view.Gravity.BOTTOM;"),
                "Resend must keep explicit bottom gravity");
        assertTrue(count(composer, "dp(48)") >= 4,
                "Upload and Resend must keep 48dp width and height");
        int uploadIndex = composer.indexOf("composer.addView(uploadButton, uploadParams);");
        int editorIndex = composer.indexOf("composer.addView(promptComposerInput");
        int resendIndex = composer.indexOf("composer.addView(resendButton, resendParams);");
        assertTrue(uploadIndex >= 0 && uploadIndex < editorIndex,
                "Upload must keep its distinct 48dp slot left of the editor");
        assertTrue(resendIndex > editorIndex,
                "Resend must keep its distinct 48dp slot right of the editor");
    }

    private static int baselineAlignedParentHeight(Child... children) {
        int tallest = 0;
        int maxBaseline = 0;
        int maxDescent = 0;
        for (Child child : children) {
            tallest = Math.max(tallest, child.height);
            maxBaseline = Math.max(maxBaseline, child.baseline);
            maxDescent = Math.max(maxDescent, child.descent());
        }
        return Math.max(tallest, maxBaseline + maxDescent);
    }

    private static int baselineAlignedBottomTop(
            int parentHeight,
            Child child,
            int maxDescent
    ) {
        return parentHeight - child.height - (maxDescent - child.descent());
    }

    private static int bottomTop(int parentHeight, Child child) {
        return parentHeight - child.height;
    }

    private static int count(String value, String needle) {
        int matches = 0;
        int cursor = 0;
        while (true) {
            int found = value.indexOf(needle, cursor);
            if (found < 0) {
                return matches;
            }
            matches++;
            cursor = found + needle.length();
        }
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

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static final class Child {
        final int height;
        final int baseline;

        Child(int height, int baseline) {
            this.height = height;
            this.baseline = baseline;
        }

        int descent() {
            return height - baseline;
        }
    }
}
