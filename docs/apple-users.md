# Apple User Support

WEzterm's architecture is not Windows-only. The durable split is:

- **Mac as the terminal host:** supported through the same tmux, ttyd, control
  server, and Tailscale shape used on Windows/WSL.
- **Android phone as the client:** supported by the native WEzterm APK.
- **iPhone or iPad as the client:** supported through Safari pointed at the
  Tailscale-hosted terminal/control web surface, with Add to Home Screen as the
  launcher-style path. A native iOS app is a separate future client, not the
  Android APK.

## Required Mac Host Packages

Install these on the Mac:

```bash
brew install tmux ttyd tailscale python
```

Then sign in to Tailscale and confirm the Mac has a Tailnet address:

```bash
tailscale status
```

Run the preflight from this repo:

```bash
scripts/macos-host-preflight.sh
```

The preflight is intentionally read-only. It confirms the host is macOS, checks
for `tmux`, `ttyd`, `tailscale`, and `python3`, verifies a Tailnet IP is visible,
and starts or reuses a `main_phone` tmux session.

## Fast Control Shape

The fast path stays the same on macOS:

1. `tmux` owns the long-lived terminal sessions.
2. `ttyd` exposes a browser terminal over the Mac's Tailscale IP.
3. The control server maps phone buttons to tmux operations.
4. The phone browser or Android app talks to the Mac over Tailscale.

WHY: tmux is the persistence boundary. A Safari tab, WebView, or SSH session can
drop without killing the terminal work. Keeping scroll/select/stop/paste in tmux
also keeps the Mac path fast, because the phone sends small control requests
instead of streaming a remote desktop.

## iPhone And iPad Client Path

iOS cannot install the Android APK. Use Safari:

```text
http://<mac-tailnet-ip-or-magicdns-name>:8088/
```

For a launcher-like experience, open the terminal URL in Safari and use Share ->
Add to Home Screen.

This gives iPhone/iPad users a usable Apple path today. It will not have every
native Android toolbar affordance unless the control UI is served as web/PWA or
a native iOS client is built later.

## What Not To Regress

- Do not make tmux Linux-only in docs or scripts. tmux runs on macOS.
- Do not tell iPhone users to install the Android APK.
- Do not replace the tmux/ttyd/control-server model with screen sharing for the
  main workflow. Remote desktop is a fallback, not the fast terminal-control
  architecture.
- Do not hardcode a Windows-only host path into future public setup docs. The
  host can be Windows/WSL, Linux, or macOS as long as tmux, ttyd, the control
  server, and Tailscale are available.
