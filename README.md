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
- Terminal URL: `http://100.113.254.7:8088/`
- Control URL: `http://100.113.254.7:8089`
- MagicDNS fallback: `http://kaleeblaptop-1.taildbdeee.ts.net:8088/` and
  `http://kaleeblaptop-1.taildbdeee.ts.net:8089`
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

- Built checkpoint: `versionCode=148`, `versionName=2.47`.
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
  copy-mode then sends one desktop-equivalent Escape.
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
- v1.72 smooths two-finger WebView movement after the v1.71 full-area fix. WHY:
  holding ACTION_DOWN until WEzterm classified the gesture meant native WebView
  pinch/pan received a stale first-finger start point once the second finger
  appeared, and delayed zoomed true-bottom retries could still fire after the
  user started moving the viewer. The fix synthesizes viewer-owned DOWN at the
  current handoff coordinate and cancels stale viewer-bottom retries when native
  pinch or horizontal pan starts, while preserving tmux-owned one-finger history
  scrolling and the v1.71 dotted/full-area refit.
- v1.74 smooths one-finger downward live-bottom return after v1.72. WHY: the
  full 20-line downward batch could queue behind an in-flight touch-scroll
  request and then repaint only after the gesture reached bottom, which feels
  like the phone froze and then appeared at live bottom. v1.73's smaller cap
  alone failed real-phone proof by stalling in copy-mode above bottom, so v1.74
  keeps only the latest bounded pending lineDown step for normal drags and sends
  long fast return flicks through the quiet tmux bottom restore. Upward history
  flicks keep the larger fast-scroll cap, and near-bottom tmux restore still
  exits copy-mode without WebView reloads.
- v1.75 hides the docked native composer after a successful Active Sessions row
  selection. WHY: Active switching is navigation, not typing. Leaving the
  composer focused kept the IME open, relabeled `Start` to `Send`, and exposed a
  dotted xterm canvas after opening an active tab. The switch now hides the
  composer and keyboard without clearing any unsent draft text, preserves the
  no-reload `/select-live` path, and refits ttyd/xterm after the layout returns
  to the plain toolbar.
- v1.76 hides the docked native composer when entering scrollback/read mode.
  WHY: one-finger history scroll and Scroll-menu history actions are reading
  actions, not typing. Keeping the composer visible while tmux is in copy-mode
  shrank the terminal, left the toolbar in `Send` state, and made scrollback look
  improperly displayed. Read mode now hides the composer and keyboard without
  clearing draft text, then lets the existing tmux-owned scroll path continue.
- v1.77 hardens native composer dismissal so the WebView/xterm hidden textarea
  cannot keep the keyboard alive after the composer is hidden. WHY: the toolbar
  can correctly return to `Start` while Android is still serving xterm's hidden
  input, which shrinks the terminal and blocks reliable long-press controls. The
  app now blurs xterm and hides IME from the WebView and decor tokens whenever a
  caller hides the composer without explicitly preserving keyboard state.
- v1.78 hides the docked native composer before opening Active, Old, Scroll, or
  command-palette dialogs. WHY: these controls are navigation/reading controls,
  not typing controls. Opening them while a draft is focused keeps the keyboard
  up, shrinks the terminal/dialog, and makes session rows or scrollback look
  clipped. The draft is preserved and returns on the next deliberate typing tap.
- v1.79 makes passive Active switching and Back/IME dismissal reclaim the full
  terminal area even if Android delivers stale tap/focus callbacks. WHY: the
  movie tab could be selected successfully while an in-flight generation guard
  skipped composer cleanup, leaving the selected tab clipped by the native
  composer or Samsung keyboard. Successful `/select-live` now hides the
  composer before the stale-generation return, and the docked EditText consumes
  Back before the IME can hide only the keyboard. Draft text is preserved.
- v1.80 fixes the live-tab/bottom/send composer race without touching the
  protected one-finger/two-finger gesture system. WHY: Active switching could
  succeed server-side while Android skipped the passive fit that clears ttyd's
  dotted canvas, and Bottom/tap/Send could enqueue repeated zoomed-viewer
  bottom retries that looked like rapid up/down refreshes. The app now passively
  refits every successful `/select-live`, uses one-shot bottom settling for
  Bottom/finger-up/Send, mirrors docked composer draft deltas into the tmux
  prompt through `/draft-delta` so the PC can continue typing, and shows a tiny
  toolbar status dot that reuses the same Working/Ready/Problem/Done color and
  pulse rules as Active Sessions.
- v1.81 fixes the remaining Active-switch dotted-canvas entry state. WHY: a
  successful `/select-live` could still leave xterm's own viewport parked above
  the live buffer bottom, so the phone showed a large field of dotted blank
  canvas below the Codex prompt even though tmux captured no dot characters.
  The app now runs a bounded Active-switch-only xterm viewport-bottom settle
  that does not reload WebView, does not open the composer/keyboard, does not
  call `scrollIntoView`, and does not touch one-finger or two-finger gesture
  ownership.
- v1.82 fixes the `Phone Crash Restore` counterexample that v1.81 missed.
  WHY: proving only KAI tabs let the old hotfix pass while the user-reported
  tab still rendered dotted blank canvas rows. Active switching now forces the
  xterm canvas/theme backing to black, clears xterm's canvas texture atlas when
  the API is available, repaints rows, and includes a targeted real-phone proof
  script for `Phone Crash Restore`. It still does not reload WebView, focus
  xterm's hidden textarea, open the native composer/keyboard, or touch the
  protected one-finger/two-finger scroll/zoom gesture code.
- v1.83 fixes the foreground/resume black-WebView counterexample that v1.82 did
  not cover. WHY: a valid ttyd/xterm DOM and nonzero canvas size can still paint
  as an almost fully black canvas after Android foregrounds the app. The blank
  watchdog now samples same-origin xterm canvas pixels and reloads only the
  WebView transport when there is effectively no painted terminal output,
  matching the manual Refresh recovery without touching tmux windows, titles,
  scroll/zoom gestures, or IME focus.
- v1.84 samples the visible Android WebView bitmap after foreground/load/focus.
  WHY: v1.83's internal xterm-canvas probe still missed the user-visible state
  where the toolbar was alive but the WebView pane itself was black except for
  tiny cursor/scrollbar pixels. The visible-paint watchdog fails open when the
  bitmap cannot be sampled, avoids typing/reading/gesture/file-picker states,
  and reloads only the WebView transport through the same proven Refresh-style
  recovery path.
- v1.85 fixes the composer-open black/cursor-only WebView regression that v1.84
  introduced. WHY: v1.84 skipped visible-paint recovery whenever the native
  composer or keyboard was open, which is exactly when the user's phone could
  show a black terminal body with only one cursor after text briefly appeared.
  The native composer draft lives outside the WebView, so the app can reload
  only the ttyd/WebView transport without clearing typed text or touching tmux.
  The reload path now preserves native composer focus, and the phone proof crops
  the real `android.webkit.WebView` bounds so composer/toolbar/keyboard pixels
  cannot falsely count as terminal output. v1.85 also cancels stale visible-paint
  checks during Active-switch settle because v1.82 intentionally paints xterm's
  blank backing black there; re-arming v1.84's bitmap watchdog in that window
  can mistake a valid dark settle for a blank terminal and start a WebView
  reload loop after text first appears.
- v1.86 removes the unproven automatic visible-paint reload loop that v1.83,
  v1.84, and v1.85 layered onto foreground/load/focus/composer paths. WHY: real
  Codex panes often sit at a mostly blank black live bottom, and the runtime
  bitmap watchdog treated that valid state as a blank terminal, repeatedly
  reloading ttyd until the phone showed only a cursor. Runtime auto-recovery now
  reloads only when the xterm renderer is missing or zero-sized; visible bitmap
  paint checks are enforced in the real-phone proof scripts, and the user-facing
  Refresh button remains the explicit transport reconnect.
- v1.86 also adds no forced-black xterm canvas/theme mutation: it removes the
  passive JavaScript that forced every xterm canvas and theme layer to black
  during layout/Active-switch settle. WHY: the terminal
  background is already black from ttyd/WebView, but forcing canvas/theme black
  during Android repaint can hide glyphs and turn a dotted-canvas fix into the
  black/cursor-only regression. Active switch still pins xterm to live bottom,
  refreshes rows, and preserves the targeted `Phone Crash Restore` proof.
- v1.87 fixes the remaining live-bottom dotted xterm canvas grid seen after
  v1.86. WHY: v1.86 correctly removed the automatic low-paint reload loop, but
  it also left Android keyboard/composer layout refits with only a row refresh.
  The user's real screenshot proved that stale xterm texture-atlas cells can
  still show a repeated dot grid below real terminal text. Layout refits now
  clear xterm's texture atlas before redraw and repeat the redraw on the next
  animation frame, without reloading WebView, focusing xterm, opening IME, or
  forcing every canvas/theme layer black. The proof detector now catches both
  the old full-width dotted bands and the newer repeated short-column dot grid.
- v1.88 delays Active Sessions dialog dismissal until one server-owned
  `/live-bottom` confirmation returns. WHY: the v1.87 proof showed the terminal
  could still display the dotted xterm field during the short "Opening Phone
  Crash Restore" transition even though it settled cleanly afterward. Keeping
  the picker visible until live-bottom confirmation prevents the user from
  seeing stale dotted cells on entry without reloading WebView, focusing xterm,
  forcing black canvas/theme layers, or touching Android scroll/zoom gestures.
- v1.89 adds a read-only local history viewer behind
  `Scroll -> Local history search`. WHY: it uses the C4 `/scrollback/chunk`
  endpoint and keeps cached rows inside the Android dialog for local
  fling/search, so reading history does not enter tmux copy-mode, select
  windows, send keys, reload WebView, or touch one-finger gestures/Bottom.
- v1.90 fixes the remaining Active-switch dotted canvas field with an
  Active-switch-only transparent xterm canvas-layer clear followed by xterm's
  own redraw. WHY: real tmux capture showed blank pane rows where the phone
  screenshot showed dots, so the dots were stale canvas pixels, not session
  output. This does not reload WebView, focus xterm, force black canvas/theme
  layers, or touch Android scroll/zoom gestures.
- v1.91 tightens that fix with an Active-switch-only blank-tail scrub below the
  current xterm cursor row. WHY: the v1.90 proof threshold was too weak and
  visual inspection still showed dots in the blank tail below the prompt. The
  scrub blacks out only rows that should be empty at live bottom, not the whole
  canvas/theme, so it removes stale dot pixels without reviving the old
  black-with-cursor regression.
- v1.92 adds a native Active-switch paint shield over only the terminal frame
  while `/select-live`, `/live-bottom`, and xterm repaint settle. WHY: the
  2026-06-15 user screenshot still showed a full dotted blank canvas when
  opening a stale `Cabule Kaleeblaptop Codex Openai Model` session, before the
  WebView had a clean frame to show. The shield blocks that stale transition
  without reloading WebView, mutating xterm theme/canvas colors, stealing IME
  focus, or touching scroll/zoom behavior. v1.92 also keeps compact Old Sessions titles.
  It derives names from the latest non-coordination user
  prompt and rejects machine labels, stale first-prompt text, and `Codex
  Openai Model` placeholders.
- v1.93 keeps the colored Working/Ready/Problem dots but makes the animation
  dot-only and lifecycle-scoped. WHY: the desktop tmux cursor/typing lag was
  fixed by redrawing only the fixed-width dot instead of the whole label; the
  APK had the same shape of problem because every running row could start an
  infinite native alpha animation and keep invalidating while xterm/WebView was
  trying to paint. Running dots now use one hardware-layer property pulse per
  dot, static rows never animate, and all dot pulses are canceled when the row detaches or the Activity pauses, so background WEzTerm cannot keep making the
  cursor look like it jumps.
- v1.94 fixes the real-phone Active-switch dotted canvas that survived v1.93.
  WHY: the failed screenshot proved transparent `clearRect` could still expose
  stale compositor dot pixels under xterm's canvas. Active switching now fills xterm canvas layers black once before xterm refreshes real rows and the
  blank-tail scrub blacks out only rows below the cursor.
- v1.95 moves the APK WebView terminal to `rendererType=dom`. WHY: the same
  real-phone proof still showed the dotted field after v1.94's canvas fill,
  which proves Android's canvas renderer/compositor could repaint the stale
  layer after our cleanup. The APK now bypasses that canvas layer while keeping tmux, ttyd, Active/Old sessions, and Start/Stop semantics unchanged.
- v1.96 preserves and reasserts native composer focus across Android
  resume/window-focus/layout settle. WHY: the real phone can keep input alive
  while a passive WebView/xterm focus probe steals ownership back toward the
  hidden textarea, making the typing menu look missing or stale. Passive focus
  now keeps the visible native `Type prompt` composer as the single typing owner.
- v1.97 makes navigation toolbar buttons dismiss the native composer before
  running Active, Old, New, Refresh, Bottom, Scroll, Copy/Paste, Upload, or
  Close. WHY: those controls are not text entry; leaving the IME/composer active
  can route taps through a stale keyboard/share state or keep dialogs compressed.
  Plain toolbar taps now fire on ACTION_DOWN before Samsung can cancel the
  ACTION_UP during IME/layout settle. Draft text is preserved, and `Start`/`Send`
  keeps the existing submit path. Active row selection also suppresses the
  fall-through terminal-body ACTION_UP that could reopen the composer after the
  selected session is already visible.
- v1.98 makes APK entry/resume use the same live-bottom ownership as the manual
  Bottom recovery, without opening the composer. WHY: the 2026-06-15 real-phone
  screenshot proved first entry could show dotted stale xterm blank rows until
  the user pressed Bottom. Page-finished/resume now ask the control server for
  `/live-bottom`, then passively resize and repaint xterm at the full toolbar
  viewport. It does not reload WebView, focus the IME, or disturb zoom/pan.
- v1.99 covers the remaining live-bottom blank-tail dots without opening the
  keyboard. WHY: v1.98 moved tmux to live bottom, but real-phone visual proof
  still showed dot rows below the prompt until the manual Bottom path opened the
  composer and keyboard. The APK now installs a WebView-side black blank-tail
  mask below xterm's live cursor after entry/Active settle, updates it from
  xterm mutations, and removes it before read/history mode so real scrollback is
  never hidden.
- v2.00 fixes the current unreachable-root path. WHY: live proof on
  2026-06-16 showed `http://100.113.254.7:8088/` and `:8089` were healthy while
  `http://kaleeblaptop-1.taildbdeee.ts.net:8088/` timed out during DNS
  resolution. The APK now prefers the direct Tailnet IP for terminal and control
  calls, keeps MagicDNS only as fallback, and sends bounded Wake-on-LAN packets
  on app open/resume or terminal/control failure. Active and Old titles remain
  server-owned through `/sessions`, `/tabs`, and the Mantis title organizer so
  the APK cannot cache or invent stale random names locally.
- v2.01 makes the v1.99 live-bottom blank-tail mask bounded and
  interaction-cancelled. WHY: that mask is a short transition cover for stale
  empty DOM rows, not a persistent terminal layer. If delayed settle callbacks
  reinstall it after typing or one-finger scroll starts, the lower half of the terminal can black out.
  The APK now cancels pending mask installs on terminal
  touch, native composer open, and read/history mode, and each mask self-expires
  after `BLANK_TAIL_MASK_MAX_LIFETIME_MS` so real terminal rows stay visible.
  v2.01 also kept the then-current PHONE_PLAN typing guard: the native composer
  remained the typing owner, live draft mirroring stayed separate from
  `/submit-text`, repeated `showSoftInput` settle calls were throttled, and
  password/private IME flags stayed absent so voice dictation did not re-enter
  incognito/private keyboard mode. Superseded by v2.09: normal Android typing
  now stays local until toolbar `Send`.
- v2.02 restores the Bottom-like entry path for Active, Old, New, and Crashed
  session opens. WHY: the old dotted-grid regression can reappear when xterm
  repaints blank DOM rows after the short v2.01 mask expires, especially after
  returning to the app or resuming an old session. The APK now runs the same
  passive live-bottom settle for those opens, adds late 1.6s/2.6s confirmations,
  and keeps a tail-only dotted-row scrubber alive briefly for late xterm DOM
  repaints. It still cancels on typing, touch, read/history mode, or composer
  open so real scrollback and user input are not hidden. v2.02 also adds
  `Clear unsent draft` for the native composer mirror and `Arrow keys / Select`
  controls backed by `/send-key` so Claude/Codex option lists can be selected
  without reattaching Android IME or duplicating draft text.
- v2.03 fixes the wrong-session Close path after slow Active Sessions switches.
  WHY: selecting a row must carry the immutable tmux `@windowId` into the bottom
  Close action. Re-querying dynamic `/active` after a grouped `main_phone` /
  `main_view_*` switch can resolve a different window, and raw `/close?fast=1`
  can kill that wrong target. The APK now remembers the last successfully
  opened stable window id, dismisses Active immediately on row tap, opens Old
  and Crashed session rows directly instead of leaving the picker up, and
  refuses to close without a stable id.
- v2.04 restores colored status dots in the fast Active Sessions picker and
  extends blank-tail cleanup to separator-only rows. WHY: `/tabs?light=1` must
  stay instant, but it must not flatten every tab to grey `Ready`; the control
  server now reads Mantis title-sync's cached Working/Waiting/Problem state
  instead of rescanning every pane. The APK also treats repeated Unicode
  horizontal-rule rows as the same blank-tail artifact as dotted filler because
  Android renders tmux/Codex separator rows as a dot grid after Active/Old opens.
- v2.05 reloads stale launcher re-entry tasks back to the direct Tailnet IP.
- v2.06 makes tab-open completion call the same Bottom-core `/live-bottom` recovery
  that the toolbar Bottom button uses. WHY: the real APK could still show xterm
  dotted blank-tail rows immediately after opening a tab, while a manual Bottom
  tap cleared them. The tab-open path now shares the same server-owned
  live-bottom core.
- v2.07 keeps that Bottom-core repair but makes entry and tab-open passive: no
  automatic native composer, no keyboard, and no hidden xterm typing focus.
  WHY: the real phone showed duplicate words and stuck backspace after tab-open
  Bottom confirmations reopened typing while Android/Gboard composition was
  still settling. v2.07 waits for ttyd/xterm DOM readiness, uses Bottom-core
  for full-page live-bottom paint, keeps the visible Send button as the only
  submit action, tracks draft mirroring by stable `@windowId`, routes empty
  composer Backspace to tmux as a recovery, narrows top-strip WebView taps to
  24dp, smooths slow one-finger scroll coalescing, and exposes variable-length
  CLI keys with Move up/down, Select/Enter, Backspace, Delete, Home, and End
  instead of fixed `1`/`2`/`3` shortcuts.
  WHY: Android `singleTask` can bring the old WEzterm task forward without
  running `onCreate()`, so a pre-v2.00 MagicDNS WebView/control endpoint can
  stay visible even though the installed APK now prefers `100.113.254.7`.
  Launcher re-entry now resets terminal/control ownership to the proven direct
  Tailnet URLs and reloads only when the current WebView is not already on that
  direct endpoint.
- v2.08 color-codes the bottom toolbar by action role and enlarges its status
  dot and labels for faster one-handed use. WHY: v2.05 rendered all 11 buttons
  in identical slate chrome, so `Start`/`Send` (send), `Stop` (interrupt), and
  `Close` (kill window) looked exactly like neutral navigation and forced
  the user to read every label under pressure.
- v2.09 keeps phone typing in the native composer but makes normal typing
  local-only until toolbar `Send`. WHY: the old hidden `/draft-delta` mirror
  was the repeated real-phone duplicate/backspace/wrong-tab regression. It
  could paste partial Gboard/voice composition into tmux before the user sent,
  leave a hidden prompt that Backspace could not edit, and later finish in the
  wrong Active tab. The legacy server `/draft-delta` endpoint remains for older
  tooling, but Android's default TextWatcher must not call it. Toolbar `Send`
  uses the single `/submit-text` paste+Enter path, while empty-composer
  Backspace/Delete and Option keys remain explicit stale-prompt recovery tools.
- v2.10 locks the phone input endpoints to the stable tmux `@windowId`. WHY: the
  real phone could type in one session, then a delayed Send/Paste/Stop/Option
  request would re-resolve the currently active tab and paste or interrupt the
  wrong session. Android now appends `windowId` to submit, paste, send-key,
  send-enter, and Stop requests; the Mantis control server honors that target.
  v2.46 simplifies the phone controls back to the desktop primitives: `Stop`
  sends exactly one Escape to the stable target and does not submit visible
  native-composer drafts; keyboard Enter/IME action submits through the same
  pinned `/submit-text` path as toolbar `Send`. WHY: the phone must not invent a
  Stop-specific state machine or treat Enter differently from Send.
- v2.47 compacts the native composer and keeps the row-level xterm dot scrubber
  alive during composer/keyboard layout refits. WHY: the 2026-06-18 real-phone
  screenshot showed a full dotted terminal field while the native composer and
  keyboard were visible, not only after Active switching. The fix reuses the
  proven no-reload/no-focus scrubber with live-bottom forcing disabled, so
  tap-to-type does not revive hidden xterm IME typing or broad black masks.
- v2.11 fixes the Active Sessions dotted-field regression that returned after
  v2.10. The APK keeps passive tab switching and does not auto-open the
  composer/keyboard, but the xterm scrubber now hides lower-screen blank-backed
  dot rows during transition frames where no meaningful DOM row is visible yet.
  WHY: v2.06 proved Bottom-core clears the field, while v2.10 proved simply
  making the path passive lets the full dotted viewport leak again.
- v2.12 keeps passive Active switching plain even when the previous phone state
  was already `Send` with the native composer/IME visible. After the server
  Bottom-core settle returns, Android re-hides the composer and keyboard on
  short guarded delays, preserving the draft but returning the toolbar to
  `Start`. WHY: the v2.11 proof selected the tab but failed because the visible
  phone state was still the old typing layout.
- v2.13 fixes the real Active Sessions row-change proof for the `Mantis Phone
  Title Fix` tab. The proof now pins the target by stable `@windowId`, fails
  instead of using a clean fake tab unless explicitly allowed, and requires
  `/active` to report the selected row after the visible tap. The APK also hides
  the large lower-screen dot-only filler field that Codex/xterm can render as
  actual glyph rows during the short passive switch settle. WHY: the user's
  complaint is visual readability after changing Active Sessions; a half-screen
  dotted field is still a failure even when the rows exist in the terminal
  buffer as TUI filler. The scrubber remains bounded to sustained lower-screen
  dot-only filler during passive switching and does not focus xterm, reload the
  WebView, or open the keyboard.
- v2.14 fixes the follow-up real-phone failure where v2.13 still showed the
  dotted field after an actual Active Sessions switch. The blank-tail mask now
  treats a sustained lower-screen run of dot-only glyph rows as visual filler
  during the bounded Active/Bottom settle, even when xterm's buffer reports those
  rows as real text; dot-only buffer rows no longer move the mask bottom during
  this settle script, while normal non-dot buffer text still stays meaningful.
  It also blocks only the orphaned `tap-up` path that can
  reopen the docked composer during the passive switch window. WHY: switching
  Active Sessions must behave like a passive Bottom/readability action, not like
  typing; normal terminal taps after the suppression window still open the
  composer so typing/backspace behavior is preserved.
- v2.15 keeps the native session-switch paint shield through the immediate
  Active-switch proof window. WHY: v2.14 still failed on the real phone because
  ADB/control latency let the previous 1200 ms shield fade before the immediate
  screenshot, exposing the dotted xterm field. The shield is still removed before
  the settled screenshot so a black terminal cannot pass as fixed.
- v2.16 adds the immediate dotted-filler shield inside the WebView. WHY: v2.15
  still failed on the real phone because Android's native sibling shield did not
  cover WebView-rendered xterm pixels. The WebView shield covers only the lower
  terminal area during the short Active/Bottom transition and is removed by the
  same typing/read-mode cleanup path so it cannot become the old black-bottom
  regression.
- v2.17 moves that immediate shield to a fixed body-level WebView overlay. WHY:
  v2.16 still failed on the real phone because an overlay appended inside
  `.xterm-screen` could sit below xterm's rendered row/canvas layer. The fixed
  body overlay uses the terminal screen bounds, still covers only the lower
  terminal area, and remains short-lived plus cleanup-bound.
- v2.18 keeps passive Active switching detached from the keyboard/composer but
  adds an independent passive-switch xterm settle train. WHY: v2.17 still showed
  the dotted lower field on real Active-session changes because the older
  blank-tail settle callbacks could be replaced or cancelled before the WebView
  repainted. The new guard survives passive `/live-bottom` confirmations and
  hides only sustained lower-screen dot-only filler runs; real touch, typing, or
  read mode cancels it so it cannot become the old stuck black-bottom mask.
- v2.19 fixes the stable target underneath that passive settle. WHY: v2.18 still
  failed a real Active Sessions switch because Bottom-core was still hitting raw
  `/live-bottom`, and the live control-server route ignored `windowId`; the
  selected tab could remain visually stale while another active target was
  restored. Android now calls `/live-bottom?windowId=@...`, `/select-live`
  passes the selected row into Bottom-core, and the Mantis/legacy control-server
  routes preserve the same stable `@windowId` target.
- v2.20 classifies Braille/dot-block lower-screen filler as the same bounded
  dot-only artifact. WHY: v2.19 still failed the real-phone Active Sessions
  proof because the visible dotted field was not ASCII periods; Codex/xterm can
  render U+2800-style glyph rows that look like the same dot grid but bypass the
  old classifier. The fix only extends `isDotOnlyText` during the existing
  passive scrubber window, so it does not focus xterm, reload WebView, open the
  keyboard, or hide normal non-dot terminal output.
- v2.21 pins visible native-composer drafts to the stable tmux `@windowId`
  where typing started. WHY: v2.20 proved Active-switch dots, but the typing
  audit found visible prompt actions could still recompute the current active
  target before submitting. A draft typed for one session can no longer paste
  into another session after `/active` polling, proof setup, or tab switching;
  Send and Enter keep that pinned submit target while Stop stays a direct
  Escape-only interrupt. Empty-composer forward Delete now mirrors the existing
  Backspace stale-draft recovery path.
- v2.22 shortens the full-frame native Active-switch shield and leaves the
  longer dotted-field protection to the lower-area WebView shield plus v2.20
  Braille/dot-block scrubber. WHY: the old 2200 ms native shield hid the
  terminal as an all-black screen after switching Active Sessions. The proof
  now requires readable terminal paint shortly after a row tap, while still
  rejecting the dotted lower field.
- v2.23 computes the lower dotted-field mask from xterm's backing buffer when
  DOM row cleanup does not expose usable row nodes. WHY: v2.22 installed and
  proved the full-frame black shield was gone, but the real `@14` Active switch
  still showed a persistent lower dot field. The new fallback is still
  passive-switch bounded, lower-screen/run-length gated, and canceled by normal
  typing/read/touch paths.
- v2.24 adds a native lower-terminal Active-switch dot shield above the WebView.
  WHY: v2.23 still failed the real-phone return switch to `@14`; the dots were
  visible after the short full-frame shield and after the WebView-level mask
  should have run. The new shield keeps the full-frame layer short so the old
  all-black screen cannot return, covers only the lower terminal region where
  the dots recur, and is force-removed by real touch, typing, or read mode.
- v2.25 adds a direct WebView-local lower dot shield from the Java
  Active-switch path. WHY: v2.24 installed and still showed the lower dotted
  field on the real phone while tmux capture had no dot rows, which proved the
  native overlay can lose the Android/WebView composition race. This shield is
  separate from title/status naming, bounded to the lower terminal viewport,
  and removed by the same typing/read/touch cleanup as the older masks.
- v2.26 keeps that WebView-local lower shield alive through the real proof
  capture window. WHY: the proof script checks toolbar/UI state before taking
  the first screenshot, so the v2.25 lower shield could expire before the
  supposedly immediate capture while the stale xterm/WebView dotted paint was
  still visible. The full-frame shield remains short; only the lower
  viewport-bounded shield lasts longer, and normal typing/read/touch still
  removes it immediately.
- v2.27 adds an Active-switch-only hard blank-tail clamp below the last readable
  DOM row. WHY: v2.26 passed one direction but the `@0 -> @59` real-phone proof
  still repainted a lower dotted field on the readable frame while tmux capture
  contained no dot rows. The clamp hides only lower unreadable tail rows during
  passive switch settle, restores them on the existing typing/read/touch
  cleanup path, and does not touch title/session naming.
- v2.28 holds the terminal WebView in a software layer only during passive
  session switching. WHY: v2.27 still failed the `@59 -> @0` readable frame,
  proving the dots could survive DOM row hiding and WebView overlays. The
  temporary software layer forces Android to repaint the terminal raster during
  the switch proof window, then restores the default hardware path for normal
  scrolling and typing.
- v2.29 extends the native lower-terminal shield through the readable proof
  window while keeping the full-frame shield short. WHY: v2.28 still passed the
  immediate frame and failed the readable frame, which showed the lower native
  cover was expiring before the real UI-dump/screenshot path finished. The
  lower shield starts below the readable top text and is still removed by
  typing/read/touch cleanup.
- v2.30 adds a lower-area PopupWindow shield during passive Active switching.
  WHY: v2.29 proved the normal native child shield still missed the readable
  proof frame over Android WebView. The popup sits above WebView composition,
  covers only the lower stale dotted raster, and is dismissed by the existing
  typing/read/touch cleanup.
- v2.31 keeps the session-switch lower shields alive through passive
  session-switch cleanup. WHY: the automatic Bottom-like settle calls
  `hideDockedPromptComposerForSessionSwitch`, which previously canceled the
  blank-tail masks and force-hid the lower shield before the readable screenshot.
  Real touch, typing, and read-mode cleanup still remove the shields.
- v2.32 adds a one-shot passive terminal transport refresh after Active
  switching. WHY: v2.31 still left the readable proof frame dotted, but a manual
  Refresh cleared the same real phone state. This refresh keeps the same tmux
  window and title state, does not restart ttyd/control, does not open the
  keyboard, and is not a reload loop.
- v2.33 removes the broad lower black shields from Active/Old session switching
  and makes the proof fail the uploaded black-lower-terminal screenshot. WHY:
  covering a dotted tail with a black lower rectangle is not a live-bottom
  render. Active, Old, New, and Crashed still use the stable `@windowId`
  `/select-live` or resume path plus `/live-bottom`, but success now requires
  real terminal paint instead of native/WebView/PopupWindow lower-mask coverage.
- v2.34 disables xterm `customGlyphs` in both the APK terminal URL and ttyd
  startup options. WHY: the v2.33 real-phone proof showed the lower dotted
  field persisted after broad masks were removed, which points at xterm's
  canvas glyph atlas drawing whitespace/filler cells as visible dots. This fixes
  the renderer root without restoring native lower shields or automatic WebView
  refreshes.
- v2.35 adds a row-level canvas dot scrubber for passive Active/Old switching.
  WHY: `customGlyphs=false` did not clear the real-phone dotted field, so the
  artifact is in xterm's canvas repaint path. The scrubber scans for repeated
  bright dot rows in the lower terminal canvas and paints only those detected
  rows to a near-background color; it does not install a native lower shield,
  a PopupWindow, a WebView lower rectangle, or an automatic WebView reload.
- v2.36 keeps that row-level canvas scrubber alive through the passive switch
  repaint window. WHY: the v2.35 proof still caught dots on the immediate frame,
  which means xterm repainted after the one-shot scrub. The bounded timer runs
  only during passive Active/Old settle and cancels through the existing
  typing/read/touch cleanup path.
- v2.37 makes Old Sessions Resume select the returned `@windowId` through
  `/select-live` before settling. WHY: the real-phone menu proof opened a
  resumed tmux window but left the visible phone view on the previous tab. Old
  saved sessions must behave like Active row taps: close the menu, switch to
  the exact session, and land at live bottom without a keyboard/composer.
- v2.38 lowers the canvas dot-row threshold and adds a full-width span gate.
  WHY: the real-phone Active title proof showed sparse full-width dotted rows
  that the proof detector rejected but the APK scrubber missed because the old
  `width/9` threshold was too high. The span gate keeps normal terminal text
  safe while the scrubber removes only repeated filler rows, never a lower black
  rectangle or WebView reload.
- v2.39 forces xterm `customGlyphs=false` at runtime before passive fit/settle
  redraws. WHY: the bundled xterm renderer checks the option with strict
  boolean false. If ttyd or query parsing leaves it as a string, blank cells can
  still render as dotted custom glyphs even though the URL says
  `customGlyphs=false`. The APK now sets the live xterm option before redraws
  without reloading WebView, opening the keyboard, or using a black lower mask.
- v2.40 calls ttyd's exposed `window.term.fit()` during passive
  session-switch settle and passive layout fit. WHY: the broad Active Sessions
  proof still found a real lower dotted field when switching to an older phone
  session because tmux/pty row count stayed at the old desktop height while the
  phone WebView had many more xterm rows. Fitting through ttyd's real resize
  path aligns the selected pane to the visible phone viewport without reloading
  WebView, focusing the hidden textarea, opening IME, or covering the lower
  terminal with a black mask.
- v2.41 removes per-key toasts from persistent Option Keys. WHY: the real APK
  proof showed Delete could be lost after Backspace while Android toast/focus
  state was settling. The persistent dialog itself is the feedback; only Select
  needs a closing confirmation, so Backspace/Delete/Home/End/Tab/Up/Down stay
  fast and do not create an overlay between key taps.
- v2.42 serializes persistent Option Keys dispatch on the Android UI thread.
  WHY: Backspace could reach tmux while the Android HTTP callback was still
  settling, then the next Delete tap could be lost. The APK now queues
  Backspace/Delete/Home/End/Tab/Up/Down/Select in tap order and sends the next
  key only after the previous `/send-key` callback or failure completes.
- v2.43 pins Option Keys to the selected Active Sessions `@windowId` at enqueue
  time. WHY: background `/active` refresh can update the current window while
  the Option Keys dialog remains open; Backspace and the following Delete must
  not split across two tmux windows.
- v2.44 makes Option Keys prefer the current active phone `@windowId` before the
  remembered Close target. WHY: selected-row memory intentionally protects
  Close, but stale row memory must not send Backspace/Delete into an older tab
  while the visible phone controls are on a newer active session.
- Claude visual polish keeps toolbar actions color-coded without merging
  controls, so users can find the safe action quickly without slowing down to
  read every label under pressure. `Start`/`Send` now use the green plate and
  `Stop`/`Close` the red plate already used by the Resume/Close dialog buttons,
  with dark text for AA contrast; neutral navigation stays slate. `Start` and
  `Stop` remain separate buttons — color reinforces the split and never merges
  them (v1.54). The label floor rises from 10-11sp to 12-13sp (the long
  `Copy/Paste` keeps a one-notch step-down so it never clips), the button
  min-height floor rises from 44dp to 48dp without growing the fixed toolbar,
  and the always-visible status dot grows from 10sp to 14sp. The dot pulse
  stays dot-only/lifecycle-scoped (`View.ALPHA`, cancel-on-detach/pause) per
  v1.93, and the navigation-bar inset already reserved below the toolbar is
  unchanged — the IME inset is never added to toolbar height (v1.65).
- v1.56 also makes the toolbar two rows with ripple/tap feedback, raises the
  default terminal font to 12, shrinks the Scroll menu to scroll-only recovery,
  and adds bounded retry for safe control calls such as Active Sessions,
  Refresh, Needs Attention, and `/select-live`.
- v1.33 also adds fast control-server responses for select/new/close/active
  actions so those controls do not rebuild the full `/tabs` payload and pane
  status dots unless the Active Sessions picker is actually opened.
- Latest no-USB package proof used the Tailscale ADB relay
  `127.0.0.1:5556 -> 100.77.22.120:5555` and reported phone model
  `SM-S938U1`. v2.12 must be installed through that relay after every APK
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
