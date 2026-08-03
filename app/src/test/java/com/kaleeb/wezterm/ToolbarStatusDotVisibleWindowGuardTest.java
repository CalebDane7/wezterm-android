package com.kaleeb.wezterm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Source-static guard: the TOOLBAR status dot must show the VISIBLE window's
 *  canonical per-window typed status, never a frozen optimistic green. OLD-RED
 *  on the pre-fix source (poll mismatch branch never repaints the dot). */
public final class ToolbarStatusDotVisibleWindowGuardTest {
    public static void main(String[] args) {
        String src = source(args);
        String poll = methodBody(src, "private void refreshToolbarStatusDot(");
        assertContains(poll,
            "updateSessionTitleStripFromVisibleRenderer(\"toolbar-status-visible-target-mismatch\")",
            "poll must still have the active/visible MISMATCH branch");
        assertContains(poll,
            "applySettledActiveSwitchStatusDot(confirmedVisibleTerminalTargetKey())",
            "MISMATCH branch must repaint the toolbar dot from the VISIBLE window's canonical per-window status");
        assertBefore(poll,
            "updateSessionTitleStripFromVisibleRenderer(\"toolbar-status-visible-target-mismatch\")",
            "applySettledActiveSwitchStatusDot(confirmedVisibleTerminalTargetKey())",
            "canonical repaint must live inside the mismatch branch");
        String settled = methodBody(src, "private void applySettledActiveSwitchStatusDot(");
        assertContains(settled, "serverConfirmedSessionByWindow.get(",
            "settled painter must read the canonical per-window typed status cache");
        String pending = methodBody(src, "private void showToolbarControlPending(");
        assertContains(pending, "applySessionStatusDot(toolbarStatusDot, \"running\"",
            "showToolbarControlPending owns the optimistic pending (transient) green");
        assertEquals(1, count(src, "toolbarStatusDot, \"running\""),
            "the optimistic \"running\" toolbar paint must exist in exactly one writer");
        System.out.println("TOOLBAR_STATUS_DOT_VISIBLE_WINDOW_GUARD_PASS");
    }
    private static String source(String[] a) {
        String p = a != null && a.length > 0 && a[0] != null && !a[0].trim().isEmpty()
                ? a[0].trim() : "app/src/main/java/com/kaleeb/wezterm/MainActivity.java";
        try { return new String(Files.readAllBytes(Paths.get(p)), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new AssertionError("cannot read MainActivity.java at " + p, e); }
    }
    private static String methodBody(String s, String sig) {
        int n = s.indexOf(sig);
        if (n < 0) throw new AssertionError("missing production method " + sig);
        int b = s.indexOf('{', n), d = 0;
        for (int i = b; i >= 0 && i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') d++;
            else if (c == '}' && --d == 0) return s.substring(b, i + 1);
        }
        throw new AssertionError("unterminated method " + sig);
    }
    private static int count(String s, String sub) {
        int n = 0, i = 0; while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); } return n; }
    private static void assertContains(String s, String sub, String m) {
        if (s.indexOf(sub) < 0) throw new AssertionError("FAIL: " + m); }
    private static void assertBefore(String s, String f, String g, String m) {
        int fi = s.indexOf(f), gi = s.indexOf(g);
        if (fi < 0 || gi < 0 || fi >= gi) throw new AssertionError("FAIL: " + m); }
    private static void assertEquals(int e, int a, String m) {
        if (e != a) throw new AssertionError("FAIL: " + m + " (expected " + e + ", got " + a + ")"); }
}
