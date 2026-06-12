# WEzterm Android

Native Android launcher for the phone terminal workflow.

WEzterm opens a Tailscale-hosted `ttyd` terminal that attaches to the desktop
`main_phone` tmux view. The Android app adds phone-first controls so the user
does not have to remember SSH hosts, tmux shortcuts, or terminal key chords.

The terminal host is not Windows-only. tmux/ttyd/Tailscale can also run on
macOS, so Mac users can host the same persistent terminal workflow. The native
APK is Android-only; iPhone and iPad users should use Safari/Add to Home Screen
against the Tailscale terminal URL unless a separate native iOS client is built.
See [Apple User Support](docs/apple-users.md).

## What It Does

- Opens as a real Android app named `WEzterm`, not a Chrome shortcut.
- Connects to the desktop terminal over Tailscale.
- Shows a bottom toolbar with the primary work controls: `Active`, `Old`,
  `New`, `Refresh`, `Scroll`, `Copy/Paste`, `Upload`, `Close`, `Start`, and
  `Stop`.
- Uses stable tmux window IDs for selecting and closing active sessions.
- Orders the Active Sessions picker newest-first by tmux activity and snaps it to
  the top when opened.
- Shows a green pulsing dot for active sessions with visible active work and a
  grey dot for idle/done sessions.
- Shows `Active Sessions` for currently open desktop/phone sessions.
- Shows `Old Sessions` as saved parent Codex sessions grouped by exact date,
  with subagent sessions filtered out so helper agents do not pollute the list.
- Keeps `Refresh` visible so the current tmux window can reattach to ttyd after
  upgrades or stale WebView state without closing the Android task or losing the
  selected session.
- Keeps live-bottom recovery visible under `Scroll` and automatic after tab
  switching, so a bad gesture/read-mode state never traps typing.
- Provides a visible `Copy/Paste` menu so phone clipboard text can be pasted
  into the active desktop pane and visible terminal text can be copied back to
  the phone clipboard.
- Lets the phone upload screenshots/media to the desktop over Tailscale through
  a direct `Upload` toolbar button, Android's file picker, or the system Share
  sheet, then copies the desktop path so it can be pasted into the active
  terminal.
- Keeps separate thumb-side `Start` and `Stop` controls so sending Enter and
  interrupting with Escape are explicit, not a hidden smart-button guess.
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

## Apple Users

tmux runs on macOS as well as Linux/BSD-style hosts. For a Mac host, install
`tmux`, `ttyd`, `tailscale`, and `python` with Homebrew, then run:

```bash
scripts/macos-host-preflight.sh
```

WHY: Apple support should not fork the architecture. The fast path is still
tmux for persistence, ttyd for browser terminal transport, the control server
for phone buttons, and Tailscale for private reachability. iPhone/iPad users use
Safari/Add to Home Screen today; Android users use the native APK.

## Current Checkpoint

- Built checkpoint: `versionCode=72`, `versionName=1.71`.
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
- v1.51 separates Active Sessions from Old Sessions. WHY: the phone UI used
  `Tabs` and `Sessions by date` for open tmux windows while the server's saved
  Codex-session metadata was never rendered, so the old-session picker felt
  missing. The toolbar now has `Active` for open sessions and `Old` for saved
  parent Codex sessions by date/name only. The server filters old sessions to
  `thread_source=user`, `source=cli`, and no agent nickname so subagent sessions
  cannot appear in the phone picker.
- v1.52 adds no-USB phone media uploads. WHY: screenshots and reference media
  need to reach the desktop where Codex can read them without USB, cloud
  detours, or broad Android storage permissions. `Copy/Paste` now includes
  `Upload media from phone`, WEzterm appears as an Android Share target for
  media, and the control server saves files under `~/phone-uploads/YYYY-MM-DD/`
  with sanitized filenames before returning a pasteable desktop path.
- v1.53 smooths the one-finger down-scroll return path and adds a direct
  `Upload` toolbar button. WHY: upward flicks need to move quickly through old
  output, but downward flicks approach the live-bottom edge where large delayed
  bursts can hit tmux bottom before the WebView repaint catches up, which looks
  like a refresh/jump. Downward touch batches are now smaller, stale scroll
  replies are gesture-generation ignored, and only upward full flings keep the
  delayed second burst. The direct Upload button uses the same all-media
  Tailscale upload path as the Copy/Paste menu and Android Share target. Media
  uploads now stream from Android's selected URI to the desktop instead of
  buffering the whole file in APK memory, and the desktop server streams to disk with a 2 GB cap
  so phone videos are a supported path rather than a screenshot-only shortcut.
- v1.54 removes the ambiguous combined interrupt/send action and makes the final two
  thumb-side toolbar buttons `Start` and `Stop`. WHY: the user sometimes needed
  one Escape to leave a terminal/history surface and a second Escape to stop the
  running Codex turn, while the same button was also expected to submit prompts.
  `Start` now always sends Enter, long-pressing `Start` opens a native safe
  prompt composer that sends one tmux paste+Enter, and `Stop` cancels tmux
  copy-mode then sends a small double-Escape.
- v1.54 also moves one-finger MOVE and bottom-edge finger-up restore onto a
  lightweight `/touch-scroll` server path. WHY: the full `/scroll` endpoint is
  still correct for toolbar proof/recovery because it gathers Codex/process and
  visible pane evidence, but that work is too heavy for every finger movement
  and made scrolling feel delayed. The gesture route now stays tmux-owned while
  skipping process scans and visible capture payloads.
- v1.54 opens new phone-created sessions in `/home/cabule` and adds a
  server-side `/select-live` endpoint so Active Sessions switching does one
  select+bottom restore round trip instead of a slow select followed by a second
  Android bottom request.
- v1.55 restores phone voice input by removing the Android
  visible-password/private-learning IME flags from both the terminal WebView and
  native safe prompt. WHY: those flags stopped some keyboards from learning or
  showing suggestions, but they also made the keyboard enter private/incognito
  mode and hid dictation. The safe prompt still submits through the controlled
  tmux paste+Enter route, so long dictated prompts do not depend on fragile live
  terminal composition.
- v1.55 tried to make Active Sessions switching repaint the phone immediately by
  reloading only the WebView transport after `/select-live` succeeds.
  v1.57 removes the WebView reload from Active Sessions switching because real
  phone use showed it looked like a refresh, delayed row taps, and restarted
  focus/scroll helpers. `/select-live` remains the server-side select+bottom
  path; Android now keeps the WebSocket transport alive, lets ttyd paint the
  newly selected tmux window, and makes the title/detail/status text itself use
  the same one-tap open action so a long-press title-copy target cannot swallow
  normal Active row taps.
- v1.56 removed the automatic gesture-end live-bottom restore that made
  one-finger down-scroll look like a rapid refresh/reset loop at the bottom.
  v1.57 exits tmux copy-mode quietly at one-finger live bottom through the
  lightweight `/touch-scroll?where=bottom` path. WHY: stopping at tmux
  `scroll_position=0` still leaves the user at the `[0/N]` copy-mode footer; the
  quiet path returns to the real live composer without WebView reload,
  xterm.scrollToBottom, scrollIntoView, or IME focus bursts.
- v1.57 also moves normal `Scroll -> Go to live bottom / type` and tap-to-type
  history return onto the server's fast `/live-bottom` route. WHY: the full
  `/scroll?where=bottom` endpoint remains useful for proof because it collects
  visible pane evidence, but using that proof path for everyday typing recovery
  caused the rapid top/bottom refresh loop. The phone now asks the server to
  return tmux/Codex/reader state to live bottom, then focuses the terminal
  without running xterm.scrollToBottom or scrollIntoView bursts.
- v1.57 makes the visible `Scroll` dialog scroll-only again: live bottom, true
  history top, current-session reader, page up, and page down. Command Palette
  stays available on long-press `Scroll`, but it no longer appears inside the
  scroll fallback. WHY: a large mixed controls menu made the emergency scroll
  fallback slow, confusing, and easy to regress into the old Terminal Controls
  dialog.
- v1.57 makes passive Android lifecycle callbacks passive. `onResume`, window
  focus, and `onPageFinished` may check for reconnect state, but they do not
  request the keyboard or run live-input visibility bursts. Returning from the
  media picker also suppresses the blank-terminal watchdog briefly so Upload
  does not trigger a reload/focus loop while Android hands the app back.
- v1.58 makes tap-to-type a single keyboard focus burst. WHY: v1.57 still
  allowed one tap to run immediate focus plus delayed keyboard retries, and
  Android/Gboard/Samsung composition could be restarted while the user was
  typing or dictating. Deliberate typing focus now cancels queued
  scroll-to-bottom/visibility retries, shows the IME only after the xterm focus
  probe, skips repeated native WebView focus when it is already focused, and
  cancels stale delayed tap focus if a new finger gesture starts.
- v1.58 also makes tap-to-type live-bottom recovery hide the history overlay
  quietly until `/live-bottom` succeeds. WHY: running the full html/body
  viewport pin before the server finished returning tmux to live bottom made the
  phone look like it was refreshing or scrolling up/down when the user tapped
  the cursor area.
- v1.59 increases the one-finger downward return-to-live cap while keeping it
  single-burst and tmux-only. WHY: physical proof showed slow and fast upward
  history movement worked, but a real finger-up return swipe could stall in
  tmux copy-mode several lines above the prompt. Downward MOVE batches now cap
  at 8 lines and release bursts at 16 lines, still without the delayed second
  downward fling that previously caused bottom-edge bounce/refresh.
- v1.60 raises the downward return cap to the control server's supported
  20-line touch batch. WHY: landscape real-device proof showed the app received
  physical swipes and fast upward scrollback reached deep history, but one
  return flick still stopped dozens of lines above live bottom. Downward return
  now has the same server-supported batch size as upward flicking while still
  forbidding the delayed second downward burst that caused old refresh loops.
- v1.61 treats a tiny tmux `lineDown` remainder as the live-bottom edge. WHY:
  real-device proof showed the return flick could still land four lines above
  the prompt, leaving copy-mode active even though the user had visually reached
  the bottom area. Android now uses the server's `scrollPosition` for tmux
  `lineDown` and quietly exits copy-mode when the remaining distance is within
  six lines; it still does not reload WebView or run xterm scroll bursts.
- v1.62 widens that quiet near-bottom restore band to 16 lines. WHY: the same
  landscape physical proof still landed 12 lines above bottom after a fast
  return flick, which looked like the bottom area but stayed trapped in
  copy-mode. The wider band is still restricted to tmux `lineDown` responses
  with explicit `scrollPosition`, and still uses the quiet copy-mode exit path.
- v1.63 removes the unconditional delayed keyboard retry from tap-to-type. WHY:
  a live tap that already focused xterm's helper textarea must not run another
  editor-focus/showSoftInput cycle 180 ms later while Samsung/Gboard/voice input
  is composing, because that can recommit the same text and look like the user
  typed every word twice. The only remaining keyboard-path retry is conditional:
  it runs once only when the first probe proves the helper textarea did not exist
  yet.
- v1.64 removes activity-start keyboard requests and guards late generic control callbacks.
  WHY: Android's `stateVisible` asks the system to show the IME when
  the activity starts, which is not a deliberate terminal typing action. WEzterm
  now keeps `adjustResize` for keyboard layout but only asks for the IME through
  explicit typing flows. Generic async controls also verify their mode generation
  before refocusing, and `showSoftInput` is skipped when the keyboard is already
  visible for the focused WebView so a stale callback cannot recommit composing
  Samsung/Gboard/voice text.
- v1.65 makes terminal-body taps single-owner and keeps keyboard height out of
  the toolbar's own layout height. WHY: v1.64 could still forward a body tap to
  xterm and then run a delayed native focus/showSoftInput fallback, which gave
  Samsung/Gboard two editor-focus transitions and duplicated words. It also
  added the IME inset to the two-row toolbar itself, creating a huge blank bottom
  bar that made live-bottom content look covered by the buttons. Top tmux/status
  taps still forward to WebView so existing top-tab switching is preserved.
- v1.66 moves normal phone typing into a docked native composer and adds a
  direct `Bottom` toolbar button. WHY: real Android Chrome/WebView+xterm typing
  still duplicated Samsung/Gboard/voice text after v1.65, which means normal
  body taps must not reopen xterm's hidden textarea at all. Terminal-body taps
  now open a native `EditText`; text stays local until Send/Start posts one
  `/submit-text` paste+Enter to the control server. The new Bottom button calls
  the fast `/live-bottom` path directly so the user is not forced through the
  Scroll fallback menu when one-finger return-to-bottom misses the prompt.
  v1.66 also extends the WebView viewer-pan grace window after two-finger zoom
  so delayed viewport cleanup does not snap the zoomed section away.
- v1.67 removes the stale hidden composer action row from the live APK and
  guards full multi-word phone clipboard paste. WHY: the earlier v1.66 build
  still showed duplicate-looking Send/Cancel controls below the toolbar, and
  Copy/Paste proof only used a single-token string. The corrected build keeps
  one visible action path by relabeling the existing Start button to Send while
  the native composer is open, and the paste path now joins every Android
  ClipData text item before sending the complete clipboard through the tmux
  paste-buffer endpoint.
- v1.68 refits ttyd/xterm after Android WebView layout changes and retries only
  the zoomed Android viewer's true-bottom position after native composer or
  one-finger live-bottom recovery. WHY: the v1.67 proof showed tmux content was
  healthy while the phone could still expose dotted/blank canvas below the
  prompt and stop above the real bottom when zoomed. The fix dispatches a
  lightweight WebView resize/redraw and preserves the prior no-reload,
  no-xterm-IME, no-duplicate-typing, Copy/Paste, Upload, Active/Old, Start/Stop,
  and Scroll-menu behavior instead of reviving the old refresh loop.
- v1.69 makes plain toolbar buttons consume their own in-bounds tap and call
  `performClick()` exactly once. WHY: real phone proof showed the center of the
  visible `Active` button could miss while an offset tap inside that same button
  opened Active Sessions. Scroll, Copy/Paste, and Start keep normal Button
  handling because their long-press actions are protected behavior.
- v1.70 pins the current active tmux window at the top of Active Sessions before
  the grouped rows. WHY: the grouped needs-attention/date order can push the
  active row below the phone viewport, making the picker look like it opened but
  did not identify or switch the current session. The current row is rendered
  once, then skipped from the grouped sections below.
- v1.71 moves the Active Sessions actions into a compact in-dialog row. WHY:
  Android's stock three-button AlertDialog footer stacked and clipped New
  session/Rename/Cancel on the phone, so users could not see all actions at once.
  The row lives inside the scrollable dialog content and preserves the visible
  current-first session list.
- v1.56 also makes the toolbar two rows with ripple/tap feedback, raises the
  default terminal font to 12, shrinks the Scroll menu to scroll-only recovery,
  and adds bounded retry for safe control calls such as Active Sessions,
  Refresh, Needs Attention, and `/select-live`.
- v1.33 also adds fast control-server responses for select/new/close/active
  actions so those controls do not rebuild the full `/tabs` payload and pane
  status dots unless the Active Sessions picker is actually opened.
- Latest no-USB package proof used the Tailscale ADB relay
  `127.0.0.1:5556 -> 100.77.22.120:5555` and reported phone model
  `SM-S938U1`. v1.71 must be installed through that relay after every APK
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

This creates and closes a disposable tmux session window, then restores the
original phone session. It proves the live control server, no-USB package state, Active Sessions,
Old Sessions parent-only date data, Needs Attention endpoint, lightweight
tmux-owned touch scroll, bottom/live recovery, Start/Stop server actions,
safe prompt submit, Copy/Paste endpoints, phone media upload endpoint, safe
old-session resume, `/home/cabule` new-session cwd, and stable `windowId`
close/select.

Run the installed-phone menu proof after installing an APK:

```bash
scripts/prove-phone-menu-ui.sh
```

Run it with WEzterm already foregrounded, or explicitly allow the proof to take
foreground focus:

```bash
WEZTERM_UI_ALLOW_FOCUS_STEAL=1 scripts/prove-phone-menu-ui.sh
```

It uses UIAutomator and ADB input against the real phone UI. It verifies the
bottom toolbar labels, opens the `Read current session` reader, opens `Old
Sessions` and `Active Sessions`, checks date-grouped old sessions with visible
`Resume` actions, confirms subagent/explorer/worker wording is absent, proves
physical one-finger slow drag versus fast flick, pixel-checks Refresh repaint,
opens the command palette, round-trips `Copy/Paste`, proves tap-to-type sends
one token without duplication, verifies `Start` and `Stop`, and closes only a
disposable session.

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
