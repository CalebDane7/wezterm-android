# WEzterm Android

Native Android launcher for the phone terminal workflow.

WEzterm opens a Tailscale-hosted `ttyd` terminal that attaches to the desktop
`main_phone` tmux view. The Android app adds phone-first controls so the user
does not have to remember SSH hosts, tmux shortcuts, or terminal key chords.

## What It Does

- Opens as a real Android app named `WEzterm`, not a Chrome shortcut.
- Connects to the desktop terminal over Tailscale.
- Shows a bottom toolbar with the primary work controls: `Tabs`, `New Tab`,
  `Refresh`, `Scroll`, `Copy/Paste`, `Steer`, and `Close Tab`.
- Uses stable tmux window IDs for selecting and closing tabs.
- Orders the tab picker newest-first by tmux activity and snaps the picker to
  the top when opened.
- Shows a green pulsing dot for tabs with visible active work and a grey dot
  for idle/done tabs.
- Groups the session picker by `Needs Attention`, `Today`, `Yesterday`,
  `This Week`, and `Older` so old work can be found without closing the app.
- Keeps `Refresh` visible so the current tmux window can reattach to ttyd after
  upgrades or stale WebView state without closing the Android task or losing the
  selected session.
- Keeps live-bottom recovery visible under `Scroll` and automatic after tab
  switching, so a bad gesture/read-mode state never traps typing.
- Provides a visible `Copy/Paste` menu so phone clipboard text can be pasted
  into the active desktop pane and visible terminal text can be copied back to
  the phone clipboard.
- Keeps `Steer` visible so a running Codex task can be interrupted and steered
  from the phone without opening a secondary menu.
- Uses a read-only Codex session reader for true full-session history when tmux
  scrollback is not enough.
- Respects Android status, navigation, and keyboard insets so toolbar buttons
  stay tappable on the S25 Ultra.
- Detects a blank/stuck WebView after resume and reloads only that ttyd page.
  This reattaches the browser client without killing tmux, Codex, or any tab.
- Reopens terminal typing on ordinary live terminal taps after the keyboard has
  been hidden, without consuming two-finger zoom or history/read gestures.
- Leaves one-finger horizontal pan to WebView so long lines do not snap back
  left while reading.
- Keeps Android/WebView pinch zoom and horizontal/two-finger viewer panning as
  the viewer layer while one-finger vertical drag sends tmux copy-mode line
  scrolling, matching the desktop mouse-wheel model.
- Brings the live xterm input back into view after focus/keyboard events so
  zoomed-in typing is not hidden below the Android keyboard.

## Architecture

- Android package: `com.kaleeb.wezterm`
- Android source: `app/src/main/java/com/kaleeb/wezterm/MainActivity.java`
- Terminal URL: `http://kaleeblaptop-1.taildbdeee.ts.net:8088/`
- Control URL: `http://kaleeblaptop-1.taildbdeee.ts.net:8089`
- Desktop terminal service: `~/.local/bin/phone-terminal`
- Control server: `~/.local/bin/phone-terminal-control-server`
- Title sync service: `~/phone-title-sync/main.py`
- tmux base session: `main`
- tmux phone view session: `main_phone`

The Android app is intentionally thin. The control server owns tmux selection,
scrolling, reader generation, close behavior, status fields, and stop behavior.
The title-sync service continuously renames tmux windows from live pane evidence.

## Current Checkpoint

- Built checkpoint: `versionCode=51`, `versionName=1.50`.
- v1.29 fixes the black-screen resume case where Android focused WEzterm but
  the WebView never opened a fresh ttyd HTTP/WebSocket connection.
- The fix is a delayed xterm/DOM watchdog. It avoids blind reloads because a
  reload on every resume would disconnect normal app switches and make tabs
  jump while typing.
- v1.30 fixes tap-to-type after the first keyboard cycle by explicitly
  refocusing WebView and xterm's hidden textarea on normal live terminal taps.
- v1.31/v1.32 narrows that tap refocus to confirmed taps, removes the broad
  terminal-text `reconnect` reload heuristic, prevents blank-watchdog reloads
  while touching/reading, coalesces one-finger history scroll requests, and
  adds newest-first tab order plus running/done status dots.
- v1.34 restores a visible `Scroll` toolbar entry after v1.33 stranded the
  proven live-bottom, history-top, page, zoom, and full-session-reader controls
  behind an uncalled internal method.
- v1.34 keeps the main bar simpler than the old seven-button layout while
  preserving the escape hatch required when one-finger gestures, Codex pager
  state, or keyboard focus regress.
- v1.35 adds a visible `Copy/Paste` toolbar entry. Paste reads Android's
  foreground clipboard and posts it to the control server, which injects it via
  a tmux paste buffer into the active pane after live-bottom recovery. Copy
  captures the currently visible tmux pane and writes it to Android's clipboard.
- v1.36 smooths one-finger history scrolling by sending smaller page batches
  with a slightly larger gesture threshold. The intent is to make deep scrollback
  feel steady instead of jumping several pages per tiny thumb movement or
  piling up delayed requests that look like freezing/restarting.
- v1.37 adds a transparent read-mode touch overlay over the terminal pane. It
  appears only while history/read mode is active so center, left, and right
  swipes all hit the app's smoother history-scroll path instead of being
  swallowed inconsistently by WebView/xterm.
- v1.38 makes history drag speed-sensitive. Slow drags stay readable at one
  page per movement step, while fast flicks can jump several pages, capped so a
  bad flick cannot queue a long delayed runaway scroll.
- v1.39 tightens fast-flick detection by combining Android `VelocityTracker`
  with the actual elapsed time between dispatched history-scroll steps. This
  keeps slow drags precise while making a deliberate flick jump much farther,
  like normal phone app scrolling.
- v1.40 pins the Android WebView document viewport so old/open tabs cannot
  scroll the browser page below the xterm text area. The only intended history
  movement is the tmux/Codex control-server path; WebView document scroll is
  forced back to `0,0` and html/body overflow is hidden after load, live-bottom,
  read-mode exit, and accidental WebView scroll events.
- v1.41 lowers the vertical-intent threshold so WEzterm captures deliberate
  one-finger vertical swipes before WebView/xterm can page-scroll an old tab.
  Horizontal pan still remains WebView-owned for long-line reading.
- v1.42 restores the protected auto-reconnect behavior for ttyd's
  `Press ↵ to Reconnect` overlay. The detector is intentionally limited to the
  small overlay text node outside xterm rows so future terminal output
  mentioning reconnect does not trigger random reloads.
- v1.43 adds a main-toolbar `Refresh` button that preserves the current tmux
  window, calls `/fix-view`, reloads only the WebView/ttyd transport, re-pins
  the viewport, and re-focuses xterm. This is the upgrade/reconnect path so the
  app does not need to be closed and reopened just to pick up a fresh terminal
  connection.
- v1.43 also adds a command palette, session/date grouping, `Needs attention`,
  bug report capture, and a Tailscale install/update link. These are guarded so
  future APK builds fail if the recovery controls disappear.
- v1.44 fixes the zoom/scroll regression caused by treating every WebView
  scroll offset as accidental drift. The viewport pin now yields while the
  viewer is zoomed, in two-finger positioning, or in a deliberate horizontal
  pan, so Android/WebView remains the zoom and left/right positioning layer.
- v1.44 also changes phone one-finger touch scroll from `mode=history` to
  `mode=touch`, and the control server routes that path through tmux copy-mode
  line scrolling even on older Codex panes. This prevents one-finger scroll from
  opening or moving Codex's transcript/prompt history when the user expects
  mouse-wheel-style tmux scrollback.
- v1.44 keeps bottom/live recovery on tmux when tmux copy-mode is active, so a
  touch-scroll followed by live bottom exits copy-mode instead of jumping into a
  Codex overlay.
- v1.45 kept the v1.44 routing model but physical slow-vs-fast proof still
  failed because a fast flick produced fewer Android MOVE samples than a slow
  drag. That failure is recorded so future scroll work starts from real proof
  instead of assuming the velocity model is enough.
- v1.46 adds a bounded ACTION_UP fling burst, including a short delayed second
  burst, so a deliberate fast flick travels farther than slow finger movement
  while slow drag remains line-sized and readable. This protects the phone
  expectation that one-finger vertical drag behaves like a desktop mouse wheel
  in tmux copy-mode, not Codex prompt history or WebView page scroll.
- v1.47 fixes the bottom-edge bounce after downward scrolling reaches live
  output. The control server now marks `atLiveBottom`, and Android exits
  history/read mode and swallows continued downward MOVE events until the finger
  lifts or reverses upward. This prevents repeated tmux copy-mode enter/cancel
  cycles from looking like a WebView refresh loop when there is no more content
  below.
- v1.48 collapses stacked focus/IME retry bursts and generation-cancels stale
  focus callbacks. WHY: resume, page-finished, Refresh, live-bottom restore,
  paste, and tap-up can all request terminal focus close together. Letting each
  caller enqueue its own long delayed retry chain can refocus xterm's hidden
  textarea after the user has started composing text in Samsung Keyboard, which
  recreates the old duplicate-typing bug without the old explicit `click()`.
  The focus script now no-ops when the helper textarea is already active and
  uses `preventScroll` so keeping typing alive does not jump the WebView.
- v1.49 marks every WebView load/reload as a new transport generation. WHY:
  Refresh, reconnect reload, and the blank-terminal watchdog all replace the
  ttyd DOM without killing tmux. Any old watchdog/focus callback from the
  previous page must be invalidated first or it can reload/refocus after the user
  has started typing again. The visibility helper now scrolls/pins only; the
  deliberate focus path is the single owner of xterm textarea and IME focus.
- v1.50 makes session titles wrap to full text in the phone picker. WHY: the
  titles are the main way to identify old work, and the old fixed 72dp row plus
  `setSingleLine(true)`/ellipsis hid the part the user needed. The open row now
  grows vertically inside the dialog ScrollView, and long-pressing a title copies
  the full session title to the phone clipboard.
- v1.33 also adds fast control-server responses for select/new/close/active
  actions so those controls do not rebuild the full `/tabs` payload and pane
  status dots unless the Tabs picker is actually opened.
- Latest no-USB package proof used the Tailscale ADB relay
  `127.0.0.1:5556 -> 100.77.22.120:5555` and reported phone model
  `SM-S938U1`. v1.50 must be installed through that relay after every APK
  rebuild; the generated install page carries the current APK SHA-256.
  Future builds must use that no-USB relay unless the relay is
  unavailable and the user explicitly permits USB fallback.

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

Run the live runtime proof when checking the full phone regression matrix:

```bash
scripts/prove-phone-runtime-regression.sh
```

This creates and closes a disposable tmux tab, then restores the original phone
tab. It proves the live control server, no-USB package state, sessions/date
data, Needs Attention endpoint, tmux-owned touch scroll, bottom/live recovery,
Copy/Paste endpoints, and stable `windowId` close/select. It does not replace
unlocked on-phone visual proof for toolbar dialogs, pinch gestures, or keyboard
layout.

## Install To Phone

Default path is no USB. Keep the Tailscale ADB relay online, then install from
Linux ADB:

```bash
phone-adb-connect home-on
adb connect 127.0.0.1:5556
adb -s 127.0.0.1:5556 install --no-incremental -r build/WEzterm.apk
```

The relay target is `127.0.0.1:5556 -> 100.77.22.120:5555`. Windows USB ADB is
only a bootstrap/fallback when the relay is not already online.

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
