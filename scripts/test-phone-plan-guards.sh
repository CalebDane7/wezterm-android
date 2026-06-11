#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN="$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
CONTROL_SERVER="${PHONE_CONTROL_SERVER:-$HOME/.local/bin/phone-terminal-control-server}"
PHONE_TERMINAL="${PHONE_TERMINAL:-$HOME/.local/bin/phone-terminal}"
PHONE_ADB_CONNECT="${PHONE_ADB_CONNECT:-$HOME/.local/bin/phone-adb-connect}"
README="$ROOT/README.md"
INSTALL_PAGE="$ROOT/build/install.html"
INSTALL_INDEX="$ROOT/build/index.html"
RUNTIME_PROOF="$ROOT/scripts/prove-phone-runtime-regression.sh"

require() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    if ! grep -Fq -- "$pattern" "$file"; then
        echo "Phone plan regression guard failed: $message" >&2
        echo "Missing pattern: $pattern" >&2
        echo "File: $file" >&2
        exit 1
    fi
}

require_absent() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    if grep -Fq -- "$pattern" "$file"; then
        echo "Phone plan regression guard failed: $message" >&2
        echo "Forbidden pattern: $pattern" >&2
        echo "File: $file" >&2
        exit 1
    fi
}

# WHY: this source-level audit is intentionally broad. The phone plan records a
# pattern of fixes regressing older fixes: scroll removing recovery, reconnect
# removing auto-reload, swipe fixes breaking typing, and toolbar cleanup hiding
# required controls. These cheap guards run before every APK build so a future
# edit cannot silently remove the protected behavior again.
require "$MANIFEST" 'android:versionName="1.50"' "current APK version must be bumped for the full-title session picker fix"
if [ -f "$README" ]; then
    # WHY: the APK can be correct while the public handoff still serves stale
    # install/proof text. Guard the docs that the phone actually opens so future
    # work cannot pass source checks while advertising an old build again.
    require "$README" 'Built checkpoint: `versionCode=51`, `versionName=1.50`.' "README checkpoint must match the installed v1.50 APK"
    require "$README" 'v1.46 adds a bounded ACTION_UP fling burst' "README must document the fast-flick physical-proof fix"
    require "$README" 'v1.48 collapses stacked focus/IME retry bursts' "README must document the duplicate-typing focus regression fix"
    require "$README" 'v1.49 marks every WebView load/reload as a new transport generation' "README must document the stale watchdog/reload regression fix"
    require "$README" 'v1.50 makes session titles wrap to full text' "README must document the full-title session picker fix"
    require "$README" 'Latest no-USB package proof used the Tailscale ADB relay' "README must document the no-USB proof path"
fi
if [ -f "$INSTALL_PAGE" ]; then
    require "$INSTALL_PAGE" 'WEzterm v1.50' "install page must advertise the current v1.50 APK"
    require "$INSTALL_PAGE" 'versionCode: <code>51</code>' "install page versionCode must match the manifest"
    require "$INSTALL_PAGE" 'SHA-256: <code>' "install page must publish the built APK checksum"
    require "$INSTALL_PAGE" 'fast-flick fling bursts' "install page must mention the current scroll fix"
fi
if [ -f "$INSTALL_INDEX" ]; then
    require "$INSTALL_INDEX" 'WEzterm v1.50 Install' "install redirect page must not point users at a stale version label"
fi
if [ -f "$RUNTIME_PROOF" ]; then
    require "$RUNTIME_PROOF" 'sessions activity groups' "runtime proof must check session/date picker data"
    require "$RUNTIME_PROOF" 'needs-attention endpoint' "runtime proof must check Needs Attention data"
    require "$RUNTIME_PROOF" 'touch line up is tmux-owned' "runtime proof must check one-finger scroll routing"
    require "$RUNTIME_PROOF" 'extra down at live bottom stays stopped' "runtime proof must check bottom-edge down-scroll does not bounce"
    require "$RUNTIME_PROOF" 'paste endpoint' "runtime proof must check Copy/Paste paste route"
    require "$RUNTIME_PROOF" 'copy-visible endpoint' "runtime proof must check Copy/Paste copy route"
    require "$RUNTIME_PROOF" 'stable windowId close' "runtime proof must check exact disposable-tab close"
    require "$RUNTIME_PROOF" 'restore original phone tab' "runtime proof must restore the user's tab"
fi
require "$MAIN" 'toolbarButton("Tabs"' "Tabs must remain on the main toolbar"
require "$MAIN" 'toolbarButton("New Tab"' "New Tab must remain on the main toolbar"
require "$MAIN" 'toolbarButton("Refresh", v -> refreshTerminalTransport())' "Refresh must remain on the main toolbar"
require "$MAIN" 'toolbarButton("Scroll", v -> showViewControls())' "Scroll recovery menu must remain visible"
require "$MAIN" 'toolbarButton("Copy/Paste", v -> showCopyPasteControls())' "Copy/Paste must remain visible"
require "$MAIN" 'toolbarButton("Steer", v -> stopAndSteer())' "Steer/stop must remain visible"
require "$MAIN" 'toolbarButton("Close Tab", v -> confirmClose())' "Close Tab must remain visible"
require_absent "$MAIN" 'toolbarButton("Live"' "Live must not be reintroduced as a main toolbar button without a new proven plan receipt"
require_absent "$MAIN" 'toolbarButton("Read"' "Read must not replace the visible Scroll recovery entry"
require_absent "$MAIN" 'toolbarButton("View"' "View must not replace the visible Scroll recovery entry"

require "$MAIN" '"Go to live bottom / type"' "Scroll menu must keep live-bottom/type recovery"
require "$MAIN" '"Command palette"' "Scroll menu must expose the command palette"
require "$MAIN" 'private void showCommandPalette()' "command palette method must remain implemented"
require "$MAIN" '"Refresh current session"' "command palette must expose refresh"
require "$MAIN" '"Sessions by date"' "command palette must expose session picker"
require "$MAIN" '"Needs attention"' "command palette must expose needs-attention"
require "$MAIN" '"Create bug report"' "command palette must expose bug-report"
require "$MAIN" '"Install/update over Tailscale"' "command palette must expose no-USB install page"
require "$MAIN" '"Go to history top"' "Scroll menu must keep true-top recovery"
require "$MAIN" '"Open full session reader"' "Scroll menu must keep full-session reader"
require "$MAIN" '"Page up"' "Scroll menu must keep page up"
require "$MAIN" '"Page down"' "Scroll menu must keep page down"
require "$MAIN" '"Stop current task"' "Scroll menu must keep stop fallback"
require "$MAIN" 'restoreLiveForTyping("At live bottom")' "live-bottom action must force the terminal back to typing"
require "$MAIN" 'restoreLiveForTyping("Opened " + title)' "tab selection must auto-return to live typing"
require "$MAIN" '"/select?fast=1&windowId="' "tab selection must target stable windowId"
require "$MAIN" '"/close?fast=1&windowId="' "close must target stable windowId"
require "$MAIN" 'getJson("/active"' "main Close Tab must use active phone tab metadata"
require "$MAIN" 'getJson("/sessions"' "Tabs must use the session/date picker endpoint"
require "$MAIN" 'getJson("/needs-attention"' "Needs Attention must use the server endpoint"
require "$MAIN" 'payload.optString("viewSession"' "sessions UI must read phone view-session metadata"
require "$MAIN" 'activityGroup' "sessions UI must show date grouping metadata"
require "$MAIN" 'titleText.setSingleLine(false)' "session picker titles must wrap instead of truncating"
require "$MAIN" 'titleText.setMaxLines(Integer.MAX_VALUE)' "session picker titles must allow full title height"
require "$MAIN" 'titleText.setEllipsize(null)' "session picker titles must not ellipsize the title"
require "$MAIN" 'LinearLayout.LayoutParams.WRAP_CONTENT' "session picker rows must be allowed to grow with wrapped titles"
require "$MAIN" 'ClipData.newPlainText("WEzterm session title", title)' "full session title must be copyable from the phone"

require "$MAIN" 'ClipboardManager' "Copy/Paste must use Android clipboard APIs"
require "$MAIN" '"Paste phone clipboard into terminal"' "paste dialog option must remain"
require "$MAIN" '"Copy visible terminal text"' "copy dialog option must remain"
require "$MAIN" 'postText("/paste"' "paste must go through the control-server paste endpoint"
require "$MAIN" 'getJson("/copy-visible"' "copy must go through the control-server copy endpoint"

require "$MAIN" 'VelocityTracker' "slow-vs-fast flick behavior must keep Android velocity tracking"
require "$MAIN" 'HISTORY_DRAG_THROTTLE_MS = 28' "touch scroll must stay responsive enough to track a finger"
require "$MAIN" 'HISTORY_DRAG_LINE_THRESHOLD_DP = 10' "touch scroll must use line-sized movement threshold"
require "$MAIN" 'HISTORY_DRAG_PAGES_PER_STEP = 1' "slow scroll must stay controlled"
require "$MAIN" 'HISTORY_DRAG_MAX_PAGES_PER_STEP = 20' "fast flick must stay capped at the server-supported touch burst"
require "$MAIN" 'HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC' "fast flick threshold must remain explicit"
require "$MAIN" 'sendHistoryScrollFromTouch(where, boundedRepeats)' "touch scrolling must batch through bounded server repeats"
require "$MAIN" 'String where = step > 0 ? "lineUp" : "lineDown"' "touch scroll must use line-sized server movement, not page jumps"
require "$MAIN" 'scrollTerminalFromTouch(where, repeats)' "touch scroll must dispatch the explicit line-sized direction"
require "$MAIN" 'dispatchHistoryReleaseFling(event)' "fast flick must add a bounded ACTION_UP burst so it moves farther than slow drag"
require "$MAIN" 'one bounded release burst' "release-fling WHY comment must remain"
require "$MAIN" 'HISTORY_DRAG_RELEASE_FLING_BURSTS = 2' "full fling must keep the delayed second burst"
require "$MAIN" 'A second short-delay burst gives real fling velocity' "delayed fling-burst WHY comment must remain"
require "$MAIN" 'terminalTouchReachedLiveBottom' "touch scroll must remember the live-bottom edge during a drag"
require "$MAIN" 'touchScrollReachedLiveBottom' "touch scroll responses must exit read mode at the live bottom"
require "$MAIN" 'extra downward' "live-bottom edge WHY comment must prevent down-scroll bounce regressions"
require "$MAIN" '+ "&mode=touch&repeat="' "one-finger touch scroll must identify itself as tmux-style touch scroll"
require "$MAIN" 'showHistoryTouchOverlay()' "history/read mode overlay must keep swipes deterministic"
require "$MAIN" 'hideHistoryTouchOverlay()' "live typing must hide the history overlay"

require "$MAIN" 'pinTerminalViewportSoon' "old-tab viewport pin must remain"
require "$MAIN" 'document.scrollingElement' "WebView document scrolling must be pinned"
require "$MAIN" 'window.scrollTo(0,0)' "WebView viewport must be reset to the top-left"
require "$MAIN" 'view.setScrollContainer(false)' "WebView must not become the terminal scroll container"
require "$MAIN" 'view.setOverScrollMode(View.OVER_SCROLL_NEVER)' "WebView overscroll refresh effect must stay disabled"
require "$MAIN" 'WEBVIEW_ZOOMED_SCALE_THRESHOLD' "WebView zoom state must remain explicit"
require "$MAIN" 'private boolean isViewerPanAllowed()' "zoomed WebView panning must not be pinned away"
require "$MAIN" 'allowViewerPanBriefly()' "two-finger and horizontal viewer pan must unlock the WebView viewport"
require "$MAIN" 'public void onScaleChanged(WebView view, float oldScale, float newScale)' "Android/WebView zoom changes must be tracked"
require "$MAIN" 'Do not translate this' "WebView zoom must not be converted into tmux/font resize behavior"

require "$MAIN" 'terminalFocusAndReconnectProbeScript' "focus path must keep narrow reconnect overlay detection"
require "$MAIN" 'Press ↵ to Reconnect' "exact observed reconnect overlay must stay documented"
require "$MAIN" 'needsReconnect:overlay' "reconnect probe must return a machine-readable reconnect decision"
require "$MAIN" 'reloadTerminalForReconnect();' "detected reconnect overlay must auto-reload"
require "$MAIN" 'Pressing Enter here would send a real key' "synthetic Enter must remain forbidden"
require "$MAIN" 'TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS' "focus retries must remain burst-collapsed"
require "$MAIN" 'terminalFocusGeneration' "stale focus callbacks must remain generation-cancelled"
require "$MAIN" 'generation != terminalFocusGeneration' "stale terminal focus callbacks must not run after newer focus"
require "$MAIN" 'LIVE_INPUT_VISIBILITY_BURST_MIN_INTERVAL_MS' "live-input visibility retries must remain burst-collapsed"
require "$MAIN" 'liveInputVisibilityGeneration' "stale live-input visibility callbacks must remain generation-cancelled"
require "$MAIN" 'KEYBOARD_SHOW_MIN_INTERVAL_MS' "Android IME show calls must remain throttled"
require "$MAIN" 'document.activeElement!==el' "xterm textarea focus must be a no-op when already active"
require "$MAIN" 'focus({preventScroll:true})' "xterm textarea focus must avoid WebView scroll jumps"
require "$MAIN" 'markTerminalLoadStarted()' "every WebView reload must invalidate stale focus/watchdog callbacks"
require "$MAIN" 'terminalFocusGeneration++' "WebView reloads must cancel stale focus callbacks"
require "$MAIN" 'liveInputVisibilityGeneration++' "WebView reloads must cancel stale visibility callbacks"
require "$MAIN" 'lastTerminalLoadAtMs = now' "blank-terminal watchdog must use explicit load generations"
require_absent "$MAIN" 'el.click()' "hidden xterm textarea click must not return; it duplicated input before"
require_absent "$MAIN" 'uiHandler.postDelayed(() -> focusTerminalInput(), 2400)' "old 2.4s unguarded focus retry must not return"

require "$MAIN" 'settings.setSupportZoom(true)' "pinch/zoom support must stay enabled"
require "$MAIN" 'settings.setBuiltInZoomControls(true)' "built-in WebView zoom must stay enabled"
require "$MAIN" 'settings.setDisplayZoomControls(false)' "old visible zoom controls must stay hidden"
require "$MAIN" 'WindowInsets.Type.ime()' "IME inset must be considered"
require "$MAIN" 'Math.max(bars.bottom, ime.bottom)' "toolbar must stay above nav bar or keyboard"
require "$MAIN" 'keepLiveInputVisibleSoon' "live typing must keep the xterm input visible above the keyboard"
require "$MAIN" 'scrollViewerToTypingPosition' "zoomed WebView must move down to the live input above the IME"
require_absent "$MAIN" 'Smaller text / zoom out' "terminal text-size controls must not masquerade as Android viewer zoom"
require_absent "$MAIN" 'Larger text / zoom in' "terminal text-size controls must not masquerade as Android viewer zoom"
require "$MAIN" 'private void refreshTerminalTransport()' "Refresh implementation must remain"
require "$MAIN" 'getJson("/fix-view"' "Refresh must ask the server to preserve/fix the active tmux view"
require "$MAIN" 'webView.loadUrl(currentUrl == null ? TERMINAL_URL : currentUrl)' "Refresh must reload only the WebView transport"
require "$MAIN" 'pinTerminalViewportSoon(reason)' "Refresh must repin WebView viewport"
require "$MAIN" 'scheduleBlankTerminalWatchdog(reason)' "Refresh must reuse blank-terminal watchdog"
require "$MAIN" 'private void openInstallPage()' "no-USB install page opener must remain"
require "$MAIN" 'new Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_URL))' "install page must open through Android ACTION_VIEW"
require "$MAIN" 'private void createBugReport()' "bug-report UI must remain implemented"
require "$MAIN" 'postText("/bug-report"' "bug-report UI must call the server endpoint"

if [ -f "$CONTROL_SERVER" ]; then
    require "$CONTROL_SERVER" 'parser.add_argument("--view-session", default="main_phone")' "control server must default to the phone view session"
    require "$CONTROL_SERVER" 'target = f"{self.active_target_session()}:{stable_id}"' "select must qualify stable windowId in the phone view"
    require "$CONTROL_SERVER" 'run_tmux("kill-window", "-t", target)' "close must kill the tmux window/process"
    require "$CONTROL_SERVER" 'repeat = max(1, min(repeat, 20))' "server scroll repeats must be capped"
    require "$CONTROL_SERVER" 'where in {"lineup", "upone"}' "server must preserve line-up touch scrolling"
    require "$CONTROL_SERVER" 'where in {"linedown", "downone"}' "server must preserve line-down touch scrolling"
    require "$CONTROL_SERVER" '"scroll-up"' "tmux touch scroll must use real copy-mode line scrolling"
    require "$CONTROL_SERVER" '"scroll-down"' "tmux touch scroll must use real copy-mode line scrolling"
    require "$CONTROL_SERVER" 'phone_touch_scroll = (request_mode or "").strip().lower() == "touch"' "one-finger phone touch scroll must bypass Codex transcript routing"
    require "$CONTROL_SERVER" '"atLiveBottom"] = True' "server must expose the tmux live-bottom edge to Android touch scroll"
    require "$CONTROL_SERVER" 'pane_in_copy_mode = run_tmux("display-message", "-p", "-t", target, "#{pane_in_mode}").strip() == "1"' "bottom after tmux touch-scroll must exit tmux copy-mode before Codex transcript routing"
    require "$CONTROL_SERVER" 'a desktop mouse wheel in tmux copy-mode' "touch-scroll intent comment must prevent Codex-history regressions"
    require "$CONTROL_SERVER" '"codexNoAltScreen"' "scroll layer must keep no-alt-screen Codex routing evidence"
    require "$CONTROL_SERVER" '"activityAt": activity_at' "tabs must expose activity ordering data"
    require "$CONTROL_SERVER" '"activityGroup": activity_group(activity_at)' "tabs must expose date grouping data"
    require "$CONTROL_SERVER" '"statusLabel": status["label"]' "tabs must expose running/done status labels"
    require "$CONTROL_SERVER" '"needsAttention": status["needsAttention"]' "tabs must expose needs-attention data"
    require "$CONTROL_SERVER" 'def sessions(self):' "server sessions endpoint must remain implemented"
    require "$CONTROL_SERVER" 'def needs_attention(self):' "server needs-attention endpoint must remain implemented"
    require "$CONTROL_SERVER" 'def fix_view(self):' "server fix-view endpoint must remain implemented"
    require "$CONTROL_SERVER" 'def bug_report(self, body=""):' "server bug-report endpoint must remain implemented"
    require "$CONTROL_SERVER" 'elif parsed.path == "/sessions":' "GET /sessions route must remain"
    require "$CONTROL_SERVER" 'elif parsed.path == "/needs-attention":' "GET /needs-attention route must remain"
    require "$CONTROL_SERVER" 'elif parsed.path == "/fix-view":' "GET /fix-view route must remain"
    require "$CONTROL_SERVER" 'elif parsed.path == "/bug-report":' "bug-report route must remain"
    require "$CONTROL_SERVER" 'def paste_text(self, text):' "server paste endpoint must remain implemented"
    require "$CONTROL_SERVER" 'load-buffer", "-b", "phone-paste", "-"' "paste must load tmux buffer from stdin"
    require "$CONTROL_SERVER" 'paste-buffer", "-d", "-p", "-b", "phone-paste"' "paste must use tmux paste-buffer"
    require "$CONTROL_SERVER" 'def copy_visible(self):' "server copy-visible endpoint must remain implemented"
    require "$CONTROL_SERVER" 'elif parsed.path == "/copy-visible":' "GET /copy-visible route must remain"
    require "$CONTROL_SERVER" 'if parsed.path == "/paste":' "POST /paste route must remain"
    require "$CONTROL_SERVER" 'def stop(self):' "server stop endpoint must remain implemented"
    require "$CONTROL_SERVER" 'run_tmux("send-keys", "-t", target, "Escape")' "Stop/Steer must still send Escape"
fi

if [ -f "$PHONE_TERMINAL" ]; then
    require "$PHONE_TERMINAL" 'PHONE_TITLE_SYNC_ENABLED:-1' "title sync should remain enabled by default"
    require "$PHONE_TERMINAL" 'scrollback=$PHONE_SCROLLBACK' "ttyd scrollback must use the configurable large cap"
    require "$PHONE_TERMINAL" '--client-option rendererType=canvas' "ttyd must keep canvas renderer"
    require "$PHONE_TERMINAL" '--client-option scrollOnUserInput=true' "ttyd must keep scroll-on-input behavior"
fi

if [ -f "$PHONE_ADB_CONNECT" ]; then
    require "$PHONE_ADB_CONNECT" 'Tailscale relay is online: ${RELAY_HOST}:${RELAY_PORT} -> ${PHONE_TAILSCALE_IP}:5555' "no-USB Tailscale relay proof path must remain"
    require "$PHONE_ADB_CONNECT" 'phone-adb-connect install [ADB_SERIAL] [APK_PATH]' "no-USB install helper must remain documented"
    require "$PHONE_ADB_CONNECT" 'install_apk()' "no-USB install helper must remain implemented"
    require "$PHONE_ADB_CONNECT" '127.0.0.1' "relay must keep localhost ADB target"
    require "$PHONE_ADB_CONNECT" 'PHONE_TAILSCALE_IP' "relay must remain Tailscale-backed"
fi

require "$ROOT/build-apk.sh" 'sha256sum build/WEzterm.apk' "build must derive the install page checksum from the signed APK"
require "$ROOT/build-apk.sh" 'cat > build/install.html' "build must regenerate the public install page after signing"
require "$ROOT/build-apk.sh" 'WEzterm v${VERSION_NAME}' "build-generated install page must use the manifest version"

echo "Phone plan regression guard passed"
