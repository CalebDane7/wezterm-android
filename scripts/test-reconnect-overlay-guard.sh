#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/app/src/main/java/com/kaleeb/wezterm/MainActivity.java"

require() {
    local pattern="$1"
    local message="$2"
    if ! grep -Fq "$pattern" "$SOURCE"; then
        echo "Reconnect overlay regression guard failed: $message" >&2
        exit 1
    fi
}

# WHY: this guard exists because the phone app previously regressed from
# automatic ttyd reconnect back to the user-facing "Press Enter to Reconnect"
# state. These checks are deliberately simple and source-level so they run
# before every APK build without requiring Android instrumentation.
require "terminalFocusAndReconnectProbeScript" "focus path must keep the reconnect overlay probe"
require "Press ↵ to Reconnect" "comments must preserve the exact observed ttyd overlay text"
require "needsReconnect:overlay" "JavaScript probe must return a reconnect decision"
require ".xterm-rows,.xterm-screen,.xterm-helper-textarea" "probe must exclude terminal output rows to avoid broad reconnect-text reloads"
require "handleTerminalFocusProbe" "focus probe callback must stay wired"
require "reloadTerminalForReconnect();" "detected overlay must reload the WebView transport automatically"
require "Pressing Enter here would send a real key" "intent comment must preserve why synthetic Enter is forbidden"

echo "Reconnect overlay regression guard passed"
