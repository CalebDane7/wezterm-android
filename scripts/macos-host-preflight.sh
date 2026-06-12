#!/usr/bin/env bash
set -euo pipefail

TMUX_SESSION="${PHONE_TMUX_SESSION:-main_phone}"

fail() {
    echo "macOS host preflight failed: $*" >&2
    exit 1
}

need_command() {
    local command_name="$1"
    local install_hint="$2"
    if ! command -v "$command_name" >/dev/null 2>&1; then
        fail "missing ${command_name}. ${install_hint}"
    fi
}

if [ "$(uname -s)" != "Darwin" ]; then
    fail "this script is for a Mac host. Linux/WSL can still run the normal phone-terminal stack, but use the Linux proof scripts there."
fi

need_command brew "Install Homebrew first, then run: brew install tmux ttyd tailscale python"
need_command tmux "Run: brew install tmux"
need_command ttyd "Run: brew install ttyd"
need_command tailscale "Install Tailscale for macOS, then sign in."
need_command python3 "Run: brew install python"

if ! tailscale status >/tmp/wezterm-macos-tailscale-status.$$ 2>&1; then
    rm -f /tmp/wezterm-macos-tailscale-status.$$
    fail "tailscale status failed. Open Tailscale on the Mac and sign in before starting the phone terminal."
fi

tailnet_ip="$(
    tailscale ip -4 2>/dev/null | head -n 1 || true
)"
rm -f /tmp/wezterm-macos-tailscale-status.$$
if [ -z "$tailnet_ip" ]; then
    fail "no Tailscale IPv4 address found. The phone must reach ttyd/control over the Tailnet."
fi

# WHY: tmux is the cross-platform persistence boundary. This preflight creates a
# harmless session if one does not exist so Mac users prove tmux works before
# debugging ttyd, browser, or phone-client layers.
if ! tmux has-session -t "$TMUX_SESSION" 2>/dev/null; then
    tmux new-session -d -s "$TMUX_SESSION" -n shell
fi

tmux display-message -p -t "$TMUX_SESSION:" 'tmux session #{session_name} window #{window_id} ready' >/tmp/wezterm-macos-tmux-proof.$$
tmux_proof="$(cat /tmp/wezterm-macos-tmux-proof.$$)"
rm -f /tmp/wezterm-macos-tmux-proof.$$

cat <<EOF
macOS host preflight passed
tailnet_ip=${tailnet_ip}
tmux=${tmux_proof}

Next host commands:
  ttyd --interface ${tailnet_ip} --port 8088 tmux attach -t ${TMUX_SESSION}
  # Start the WEzterm control server on ${tailnet_ip}:8089 with the same tmux session.

iPhone/iPad client:
  Open Safari to http://${tailnet_ip}:8088/
  Use Share -> Add to Home Screen for a launcher-style entry.
EOF
