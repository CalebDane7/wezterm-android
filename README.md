# WEzterm Android

Native Android launcher for the phone terminal workflow.

WEzterm opens a Tailscale-hosted `ttyd` terminal that attaches to the desktop
`main_phone` tmux view. The Android app adds phone-first controls so the user
does not have to remember SSH hosts, tmux shortcuts, or terminal key chords.

## What It Does

- Opens as a real Android app named `WEzterm`, not a Chrome shortcut.
- Connects to the desktop terminal over Tailscale.
- Shows a bottom toolbar with `Tabs`, `New Tab`, `Live`, `Stop`, `Read`,
  `Close Tab`, and `View`.
- Uses stable tmux window IDs for selecting and closing tabs.
- Keeps `Live` as the recovery path for returning to the bottom/input prompt.
- Keeps `Stop` visible so a running Codex task can be interrupted and steered
  from the phone.
- Uses a read-only Codex session reader for true full-session history when tmux
  scrollback is not enough.
- Respects Android status, navigation, and keyboard insets so toolbar buttons
  stay tappable on the S25 Ultra.
- Detects a blank/stuck WebView after resume and reloads only that ttyd page.
  This reattaches the browser client without killing tmux, Codex, or any tab.

## Architecture

- Android package: `com.kaleeb.wezterm`
- Android source: `app/src/main/java/com/kaleeb/wezterm/MainActivity.java`
- Terminal URL: `http://kaleeblaptop-1.taildbdeee.ts.net:8088/`
- Control URL: `http://kaleeblaptop-1.taildbdeee.ts.net:8089`
- Desktop terminal service: `~/.local/bin/phone-terminal`
- Control server: `~/.local/bin/phone-terminal-control-server`
- tmux base session: `main`
- tmux phone view session: `main_phone`

The Android app is intentionally thin. The control server owns tmux selection,
scrolling, reader generation, close behavior, and stop behavior.

## Current Checkpoint

- Installed checkpoint: `versionCode=30`, `versionName=1.29`.
- v1.29 fixes the black-screen resume case where Android focused WEzterm but
  the WebView never opened a fresh ttyd HTTP/WebSocket connection.
- The fix is a delayed xterm/DOM watchdog. It avoids blind reloads because a
  reload on every resume would disconnect normal app switches and make tabs
  jump while typing.
- Latest phone proof used Tailscale ADB `127.0.0.1:5556`, cold-launched the app,
  verified terminal content plus toolbar labels, then hot-resumed from Home and
  verified terminal content stayed visible.

## Build

The build script expects an Android SDK at `~/.local/share/android-sdk` with
build tools `35.0.0`, platform `android-35`, and the WEzterm debug keystore.

```bash
./build-apk.sh
```

Output:

```text
build/WEzterm.apk
```

## Install To Phone

Preferred current path is the Linux adb client through the Tailscale relay:

```bash
adb connect 127.0.0.1:5556
adb -s 127.0.0.1:5556 install --no-incremental -r build/WEzterm.apk
```

Use `--no-incremental`. Android incremental install kept an old native toolbar
process during testing, which made package version proof disagree with the UI.

## Safety

The terminal path uses Tailscale. The local ADB relay is for home/trusted
development only.

At home:

```bash
phone-adb-connect home-on
```

Before leaving home or using public Wi-Fi:

```bash
phone-adb-connect safe-off
```

## Proof Discipline

Do not call a phone-terminal change done from source proof alone. Required proof
is: build, install on the actual phone, launch the app, verify toolbar labels,
prove live bottom and typing, prove top/reader access, and prove disposable tab
close without disturbing real work tabs.
