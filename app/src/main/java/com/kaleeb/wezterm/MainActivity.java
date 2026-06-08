package com.kaleeb.wezterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends Activity {
    private static final String TERMINAL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8088/";
    private static final String CONTROL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8089";
    private static final String PREFS = "wezterm";
    private static final String PREF_PIN_REQUESTED = "pin_requested";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final int DEFAULT_FONT_SIZE = 11;
    private static final int MIN_FONT_SIZE = 4;
    private static final int MAX_FONT_SIZE = 18;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final long HISTORY_DRAG_THROTTLE_MS = 70;
    private static final int HISTORY_DRAG_PAGES_PER_STEP = 8;
    private WebView webView;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long lastReconnectReloadAtMs = 0;
    private boolean readModeSuppressesKeyboard = false;
    private boolean terminalHistoryViewportActive = false;
    private boolean terminalTouchStartedInHistoryViewport = false;
    private boolean liveRestoreInFlight = false;
    private long terminalModeGeneration = 0;
    private int terminalTouchSlop = 8;
    private float terminalTouchStartX = 0;
    private float terminalTouchStartY = 0;
    private float terminalLastHistoryDragY = 0;
    private boolean terminalHistoryDragActive = false;
    private boolean terminalMultiTouchGesture = false;
    private long lastHistoryDragAtMs = 0;

    private interface JsonCallback {
        void onResult(JSONObject payload) throws Exception;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureWindow();
        webView = new WebView(this);
        configureWebView(webView);
        setContentView(buildLayout(webView));
        if (getIntent().getBooleanExtra("pin_shortcut", false)) {
            requestHomeShortcutOnce();
        }
        loadTerminal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            focusTerminalInputSoon();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && webView != null) {
            focusTerminalInputSoon();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private View buildLayout(WebView view) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(30, 30, 46));
        LinearLayout toolbar = bottomBar();
        applySystemBarPadding(root, toolbar);

        root.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)
        ));
        return root;
    }

    private LinearLayout bottomBar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(6), dp(5), dp(6), dp(5));
        toolbar.setMinimumHeight(dp(TOOLBAR_HEIGHT_DP));
        toolbar.setBackgroundColor(Color.rgb(24, 24, 37));
        toolbar.addView(toolbarButton("Tabs", v -> showTabs()));
        toolbar.addView(toolbarButton("New Tab", v -> control("/new", "New tab opened")));
        toolbar.addView(toolbarButton("Live", v -> goLiveBottom()));
        // WHY: Stop/steer is not a secondary action. The user needs to interrupt
        // a running Codex turn and immediately type a new prompt from the phone.
        // Do not hide Stop under View, and do not remove Live to make room for
        // Stop. Live is the proven recovery path for keyboard/bottom regressions;
        // losing it recreated the exact loop where one fix broke another.
        toolbar.addView(toolbarButton("Stop", v -> stopAndSteer()));
        toolbar.addView(toolbarButton("Read", v -> openFullSessionReader()));
        toolbar.addView(toolbarButton("Close Tab", v -> confirmClose()));
        toolbar.addView(toolbarButton("View", v -> showViewControls()));
        return toolbar;
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        button.setTextSize(9);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(2), 0, dp(2), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(205, 214, 244));
        button.setBackgroundColor(Color.rgb(49, 50, 68));
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        );
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.rgb(16, 16, 20));
            window.setNavigationBarColor(Color.rgb(16, 16, 20));
        }
    }

    private void configureWebView(WebView view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundColor(Color.rgb(16, 16, 20));
        view.setWebViewClient(new TerminalWebViewClient());
        view.setWebChromeClient(new WebChromeClient());
        terminalTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        view.setOnTouchListener((touchedView, event) -> handleTerminalTouch(event));

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setTextZoom(100);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setNeedInitialFocus(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
    }

    private boolean handleTerminalTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN) {
            // WHY: two-finger gestures belong to WebView/xterm for pinch zoom and
            // panning. The app must not consume them while trying to fix history.
            terminalMultiTouchGesture = true;
            terminalHistoryDragActive = false;
            return false;
        }

        if (action == MotionEvent.ACTION_DOWN) {
            terminalTouchStartX = event.getX();
            terminalTouchStartY = event.getY();
            terminalLastHistoryDragY = terminalTouchStartY;
            terminalHistoryDragActive = false;
            terminalMultiTouchGesture = false;
            terminalTouchStartedInHistoryViewport = terminalHistoryViewportActive || readModeSuppressesKeyboard;
            lastHistoryDragAtMs = 0;
            // WHY: when the terminal is in history/reader mode, a plain tap means
            // "return me to live input." If this ACTION_DOWN is allowed through to
            // WebView, xterm can open Samsung's keyboard before the server has
            // scrolled tmux/Codex back to the live bottom, recreating the bug where
            // the keyboard covers the composer and the user cannot see typing.
            return terminalTouchStartedInHistoryViewport;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (terminalMultiTouchGesture) {
                return false;
            }
            float dx = event.getX() - terminalTouchStartX;
            float dy = event.getY() - terminalTouchStartY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            if (!terminalHistoryDragActive) {
                if (absDy < terminalTouchSlop * 2 || absDy < absDx * 1.35f) {
                    return terminalTouchStartedInHistoryViewport;
                }
                terminalHistoryDragActive = true;
                terminalLastHistoryDragY = terminalTouchStartY;
                enterReadMode();
            }

            float step = event.getY() - terminalLastHistoryDragY;
            long now = System.currentTimeMillis();
            int pageThreshold = Math.max(terminalTouchSlop * 3, dp(44));
            if (Math.abs(step) >= pageThreshold && now - lastHistoryDragAtMs >= HISTORY_DRAG_THROTTLE_MS) {
                terminalLastHistoryDragY = event.getY();
                lastHistoryDragAtMs = now;
                scrollTerminalFromTouch(step > 0 ? "pageUp" : "pageDown", HISTORY_DRAG_PAGES_PER_STEP);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = terminalHistoryDragActive;
            boolean startedInHistoryViewport = terminalTouchStartedInHistoryViewport;
            boolean shouldRestoreTyping = action == MotionEvent.ACTION_UP
                    && startedInHistoryViewport
                    && !terminalHistoryDragActive;
            terminalHistoryDragActive = false;
            terminalMultiTouchGesture = false;
            terminalTouchStartedInHistoryViewport = false;
            if (shouldRestoreTyping) {
                restoreLiveForTyping("Typing ready");
                return true;
            }
            return consumed || startedInHistoryViewport;
        }

        return false;
    }

    private void scrollTerminalFromTouch(String where, int repeats) {
        // WHY: normal WebView scrolling only moves ttyd's browser viewport a tiny
        // amount. The actual terminal history lives in tmux/Codex, so deliberate
        // one-finger vertical drags must call the same server history path as the
        // explicit buttons, with mode=history to avoid the old ignored-live guard.
        // WHY: v1.24 fired several separate HTTP requests per swipe. That made
        // Android history feel glitchy and let old page responses race against
        // tap-to-type. A single batched request keeps the drag fast while giving
        // the keyboard/live-bottom guard one response to reason about.
        String path = "/scroll?where=" + urlEncode(where)
                + "&mode=history&repeat=" + Math.max(1, repeats);
        control(path, "", false);
    }

    private void loadTerminal() {
        int fontSize = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
        // WHY: Android WebView can be sluggish with ttyd's default WebGL xterm
        // renderer. ttyd documents `rendererType=canvas` as a client option,
        // and URL options outrank server defaults, so the app keeps this even
        // when the server is still running an older command line.
        webView.loadUrl(TERMINAL_URL
                + "?fontSize=" + fontSize
                + "&disableLeaveAlert=true"
                + "&rendererType=canvas"
                + "&scrollOnUserInput=true");
        focusTerminalInputSoon();
    }

    private void adjustFont(int delta) {
        int current = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
        int next = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, current + delta));
        if (next == current) {
            toast("Font size " + next);
            return;
        }
        prefs.edit().putInt(PREF_FONT_SIZE, next).apply();
        loadTerminal();
        toast("Font size " + next);
    }

    private void resetFont() {
        prefs.edit().putInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE).apply();
        loadTerminal();
        toast("Font size " + DEFAULT_FONT_SIZE);
    }

    private void scrollTerminal(String where, String message) {
        scrollTerminal(where, message, true);
    }

    private void scrollTerminal(String where, String message, boolean refocusTerminal) {
        // WHY: The phone WebView is only the transport. The control server now
        // chooses the correct scroll layer: the read-only full-session reader,
        // Codex transcript-pager keys for old alt-screen panes, or tmux
        // copy-mode for plain shell panes. Both toolbar buttons and one-finger
        // drags use this server path now; do not replace it with WebView scroll
        // because that only moves the browser viewport and brought back the
        // "can't reach the real top" loop.
        String path = "/scroll?where=" + urlEncode(where);
        control(path, message, refocusTerminal);
    }

    private void openFullSessionReader() {
        enterReadMode();
        // WHY: the user needs the true beginning of a Codex session, not just
        // whatever tmux scrollback retained. The server opens a generated
        // read-only transcript tab from Codex's persisted JSONL and places it
        // at the top, while Live returns to the original running tab.
        control("/read-session", "Session reader", false);
    }

    private void goLiveBottom() {
        restoreLiveForTyping("At live bottom");
    }

    private void stopAndSteer() {
        // WHY: after interrupting a running Codex task, the next expected user
        // action is typing a steering prompt. Keep this as one visible toolbar
        // path and refocus xterm after `/stop`; otherwise the user has to hunt
        // through View controls while the task keeps running.
        control("/stop", "Stop sent", true);
    }

    private long enterReadMode() {
        long generation = ++terminalModeGeneration;
        terminalHistoryViewportActive = true;
        readModeSuppressesKeyboard = true;
        hideKeyboardForReadMode();
        uiHandler.postDelayed(() -> {
            if (generation == terminalModeGeneration && readModeSuppressesKeyboard) {
                hideKeyboardForReadMode();
            }
        }, 150);
        uiHandler.postDelayed(() -> {
            if (generation == terminalModeGeneration && readModeSuppressesKeyboard) {
                hideKeyboardForReadMode();
            }
        }, 600);
        return generation;
    }

    private void keepReadModeIfCurrent(long generation) {
        if (generation != terminalModeGeneration || !readModeSuppressesKeyboard) {
            return;
        }
        hideKeyboardForReadMode();
        uiHandler.postDelayed(() -> {
            if (generation == terminalModeGeneration && readModeSuppressesKeyboard) {
                hideKeyboardForReadMode();
            }
        }, 150);
        uiHandler.postDelayed(() -> {
            if (generation == terminalModeGeneration && readModeSuppressesKeyboard) {
                hideKeyboardForReadMode();
            }
        }, 600);
    }

    private void restoreLiveForTyping(String message) {
        if (liveRestoreInFlight) {
            return;
        }
        // WHY: history paging and one-finger swipe requests are asynchronous HTTP
        // calls. A stale pageUp/pageDown response used to arrive after Live/tap and
        // put the app back into keyboard-suppressed read mode. Bumping this
        // generation invalidates those stale read callbacks before focusing xterm.
        long generation = leaveReadModeForLiveInput();
        liveRestoreInFlight = true;
        getJson("/scroll?where=bottom", payload -> {
            liveRestoreInFlight = false;
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "Command failed");
                toast(error);
                return;
            }
            if (message != null && !message.isEmpty()) {
                toast(message);
            }
            focusTerminalInputSoon();
        });
    }

    private long leaveReadModeForLiveInput() {
        long generation = ++terminalModeGeneration;
        terminalHistoryViewportActive = false;
        terminalTouchStartedInHistoryViewport = false;
        readModeSuppressesKeyboard = false;
        return generation;
    }

    private void hideKeyboardForReadMode() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (webView != null) {
            // WHY: reader/page controls are navigation, not input. Delayed xterm
            // focus retries can otherwise reopen Samsung's keyboard and cover the
            // transcript. This blur path must stay paired with the touch handler's
            // two-finger pass-through and tap-to-Live restore; otherwise fixing
            // scrollback reintroduces the older "cannot see what I am typing" bug.
            webView.evaluateJavascript(
                    "(function(){"
                            + "try{"
                            + "var el=document.querySelector('.xterm-helper-textarea, .xterm textarea, textarea');"
                            + "if(el&&typeof el.blur==='function'){el.blur();}"
                            + "if(document.activeElement&&typeof document.activeElement.blur==='function'){document.activeElement.blur();}"
                            + "return 'blurred';"
                            + "}catch(e){return 'err';}"
                    + "})()",
                    null
            );
            webView.clearFocus();
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(webView.getWindowToken(), 0);
            }
        }
        View decor = getWindow().getDecorView();
        if (inputMethodManager != null && decor != null) {
            inputMethodManager.hideSoftInputFromWindow(decor.getWindowToken(), 0);
        }
    }

    private void showViewControls() {
        String current = String.valueOf(prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE));
        String[] labels = new String[]{
                "Stop current task",
                "Smaller text / zoom out",
                "Larger text / zoom in",
                "Reset text size (" + DEFAULT_FONT_SIZE + ")",
                "Go to live bottom",
                "Go to scrollback top",
                "Open full session reader",
                "Page down",
                "Page up",
                "Current text size: " + current
        };
        new AlertDialog.Builder(this)
                .setTitle("View")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        control("/stop", "Stop sent");
                    } else if (which == 1) {
                        adjustFont(-1);
                    } else if (which == 2) {
                        adjustFont(1);
                    } else if (which == 3) {
                        resetFont();
                    } else if (which == 4) {
                        goLiveBottom();
                    } else if (which == 5) {
                        enterReadMode();
                        scrollTerminal("top", "Scrollback top", false);
                    } else if (which == 6) {
                        enterReadMode();
                        control("/read-session", "Session reader", false);
                    } else if (which == 7) {
                        enterReadMode();
                        scrollTerminal("pageDown", "Page down", false);
                    } else if (which == 8) {
                        enterReadMode();
                        scrollTerminal("pageUp", "Page up", false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTabs() {
        getJson("/tabs", payload -> {
            JSONArray windows = payload.getJSONArray("windows");
            String session = payload.optString("session", "main");
            ScrollView scrollView = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(8), dp(6), dp(8), dp(6));
            scrollView.addView(list);

            TextView header = new TextView(this);
            header.setText("Session: " + session);
            header.setTextSize(14);
            header.setTextColor(Color.rgb(205, 214, 244));
            header.setPadding(0, 0, 0, dp(8));
            list.addView(header);

            final AlertDialog[] dialogRef = new AlertDialog[1];
            for (int i = 0; i < windows.length(); i++) {
                JSONObject window = windows.getJSONObject(i);
                int index = window.getInt("index");
                String windowId = window.optString("windowId", "");
                String title = window.optString("title", window.optString("name", "shell"));
                String detail = tabDetail(window, session);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dp(3), 0, dp(3));

                LinearLayout openPanel = new LinearLayout(this);
                openPanel.setOrientation(LinearLayout.VERTICAL);
                openPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);
                openPanel.setPadding(dp(10), dp(6), dp(10), dp(6));
                openPanel.setBackgroundColor(window.optBoolean("active", false)
                        ? Color.rgb(69, 71, 90)
                        : Color.rgb(49, 50, 68));
                openPanel.setClickable(true);
                openPanel.setOnClickListener(v -> {
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                    selectTabForTyping(index, windowId, title);
                });

                TextView titleText = new TextView(this);
                titleText.setText((window.optBoolean("active", false) ? "Current: " : "") + title);
                titleText.setTextSize(15);
                titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                titleText.setTextColor(Color.rgb(255, 96, 112));
                titleText.setSingleLine(true);
                titleText.setEllipsize(TextUtils.TruncateAt.END);
                titleText.setIncludeFontPadding(false);

                TextView detailText = new TextView(this);
                detailText.setText(detail);
                detailText.setTextSize(11);
                detailText.setTextColor(Color.rgb(166, 173, 200));
                detailText.setSingleLine(true);
                detailText.setEllipsize(TextUtils.TruncateAt.END);
                detailText.setIncludeFontPadding(false);

                // WHY: the old tab list squeezed title, session, command, path,
                // and active state into one button. On Android that looked like
                // unreadable terminal text and hid the actual conversation name.
                // Keeping the useful title as a red first line makes the work
                // identity scannable without changing the stable windowId close
                // path that already proved exact tmux tab cleanup.
                openPanel.addView(titleText, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));
                openPanel.addView(detailText, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

                Button close = new Button(this);
                close.setText("X Close");
                close.setAllCaps(false);
                close.setTextSize(12);
                close.setTextColor(Color.rgb(30, 30, 46));
                close.setBackgroundColor(Color.rgb(243, 139, 168));
                close.setPadding(dp(3), 0, dp(3), 0);
                close.setOnClickListener(v -> {
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                    confirmClose(index, windowId, title);
                });

                row.addView(openPanel, new LinearLayout.LayoutParams(
                        0,
                        dp(72),
                        1
                ));
                LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                        dp(92),
                        dp(72)
                );
                closeParams.setMargins(dp(6), 0, 0, 0);
                row.addView(close, closeParams);
                list.addView(row);
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Tabs")
                    .setView(scrollView)
                    .setPositiveButton("New tab", (d, which) -> control("/new", "New tab"))
                    .setNeutralButton("Rename current tab", (d, which) -> showRenameCurrentTab())
                    .setNegativeButton("Cancel", null)
                    .show();
            dialogRef[0] = dialog;
        });
    }

    private void confirmClose() {
        getJson("/tabs", payload -> {
            JSONArray windows = payload.getJSONArray("windows");
            for (int i = 0; i < windows.length(); i++) {
                JSONObject window = windows.getJSONObject(i);
                if (window.getBoolean("active")) {
                    confirmClose(window.getInt("index"), window.optString("windowId", ""), window.optString("title", "current tab"));
                    return;
                }
            }
            confirmClose(-1, "", "current tab");
        });
    }

    private void confirmClose(int index, String windowId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Close " + title + "?")
                .setMessage("This kills that tmux tab and whatever is running inside it.")
                .setPositiveButton("Close", (dialog, which) -> {
                    String path = index >= 0
                            ? "/close?windowId=" + urlEncode(windowId) + "&index=" + index
                            : "/close";
                    control(path, "Closed tab");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRenameCurrentTab() {
        getJson("/tabs", payload -> {
            JSONArray windows = payload.getJSONArray("windows");
            for (int i = 0; i < windows.length(); i++) {
                JSONObject window = windows.getJSONObject(i);
                if (!window.getBoolean("active")) {
                    continue;
                }
                int index = window.getInt("index");
                String windowId = window.optString("windowId", "");
                String current = window.optString("name", window.optString("title", ""));
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(current);
                input.setSelectAllOnFocus(true);
                int pad = dp(16);
                input.setPadding(pad, pad / 2, pad, pad / 2);
                new AlertDialog.Builder(this)
                        .setTitle("Rename current tab")
                        .setMessage("Use a short name that describes the work in this tab.")
                        .setView(input)
                        .setPositiveButton("Save", (dialog, which) -> {
                            String name = input.getText().toString().trim();
                            control("/rename?windowId=" + urlEncode(windowId) + "&index=" + index + "&name=" + urlEncode(name), "Renamed tab");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            toast("No current tab found");
        });
    }

    private String tabLabel(JSONObject window, String session) throws Exception {
        String state = window.getBoolean("active") ? "Current: " : "Open: ";
        String title = window.optString("title", window.optString("name", "shell"));
        String detail = window.optString("detail", window.optString("command", ""));
        String path = window.optString("shortPath", "");
        return state + title
                + "\nSession " + session
                + " · Tab " + window.getInt("index")
                + " · " + detail
                + " · " + path;
    }

    private String tabDetail(JSONObject window, String session) throws Exception {
        String state = window.getBoolean("active") ? "Current tab" : "Tap to open";
        String detail = window.optString("detail", window.optString("command", ""));
        String path = window.optString("shortPath", "");
        return state
                + " - Session " + session
                + " - Tab " + window.getInt("index")
                + " - " + detail
                + (path.isEmpty() ? "" : " - " + path);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception exc) {
            return "";
        }
    }

    private void control(String path, String message) {
        control(path, message, true);
    }

    private void selectTabForTyping(int index, String windowId, String title) {
        // WHY: opening a tab from the phone should never require a separate Live
        // tap or Enter press. The select happens first, then the selected tab is
        // forced back to the live bottom and xterm is focused so the next prompt
        // can be typed immediately.
        long generation = leaveReadModeForLiveInput();
        String path = "/select?windowId=" + urlEncode(windowId) + "&index=" + index;
        getJson(path, payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "Command failed");
                toast(error);
                return;
            }
            restoreLiveForTyping("Opened " + title);
        });
    }

    private void control(String path, String message, boolean refocusTerminal) {
        long readModeGeneration = refocusTerminal ? leaveReadModeForLiveInput() : enterReadMode();
        getJson(path, payload -> {
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "Command failed");
                toast(error);
                return;
            }
            if (message != null && !message.isEmpty()) {
                toast(message);
            }
            if (refocusTerminal) {
                focusTerminalInputSoon();
            } else {
                keepReadModeIfCurrent(readModeGeneration);
            }
        });
    }

    private void focusTerminalInputSoon() {
        // WHY: ttyd connects asynchronously and Android may drop programmatic
        // focus during WebView paint/IME setup. A few short retries make the
        // terminal ready for typing without requiring the user to press Enter
        // just to wake the hidden xterm textarea.
        focusTerminalInput();
        uiHandler.postDelayed(() -> focusTerminalInput(), 150);
        uiHandler.postDelayed(() -> focusTerminalInput(), 600);
        uiHandler.postDelayed(() -> focusTerminalInput(), 1200);
        uiHandler.postDelayed(() -> focusTerminalInput(), 2400);
    }

    private void focusTerminalInput() {
        if (webView == null) {
            return;
        }
        if (readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            hideKeyboardForReadMode();
            return;
        }
        webView.requestFocusFromTouch();
        webView.requestFocus(View.FOCUS_DOWN);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        // WHY: Android IMEs do not reliably emit desktop-style key events, and
        // xterm accepts input through its hidden textarea. Re-focusing that
        // textarea on resume/control actions keeps the phone tab connected
        // without changing tmux sessions or restarting the terminal process.
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var t=window.term||window.terminal;"
                        + "if(t&&typeof t.focus==='function'){t.focus();}"
                        + "var root=document.querySelector('.xterm');"
                        + "if(root&&typeof root.focus==='function'){root.focus();}"
                        + "var el=document.querySelector('.xterm-helper-textarea, .xterm textarea, textarea');"
                        + "var body=(document.body&&document.body.innerText||'').toLowerCase();"
                        + "if(body.indexOf('reconnect')>=0){return 'reconnect';}"
                        + "if(el){"
                        + "el.setAttribute('autocapitalize','none');"
                        + "el.setAttribute('autocomplete','off');"
                        + "el.setAttribute('autocorrect','off');"
                        + "el.setAttribute('spellcheck','false');"
                        + "el.focus();"
                        + "if(typeof el.click==='function'){el.click();}"
                        + "}"
                        + "return el?'focused':'no-input';"
                        + "}catch(e){return 'err';}"
                + "})()",
                value -> {
                    if (value != null && value.toLowerCase().contains("reconnect")) {
                        reloadTerminalForReconnect();
                    }
                }
        );
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void reloadTerminalForReconnect() {
        long now = System.currentTimeMillis();
        if (now - lastReconnectReloadAtMs < 5000) {
            return;
        }
        lastReconnectReloadAtMs = now;
        // WHY: ttyd's closed-socket overlay asks for Enter before reconnecting.
        // On the phone that looks like typing is broken. Reloading the WebView
        // only reattaches the ttyd client to the existing tmux session; it does
        // not kill tmux, Codex, or any tab.
        webView.reload();
        uiHandler.postDelayed(() -> focusTerminalInputSoon(), 800);
    }

    private void getJson(String path, JsonCallback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(CONTROL_URL + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String body = readAll(stream);
                JSONObject payload = new JSONObject(body);
                uiHandler.post(() -> {
                    try {
                        callback.onResult(payload);
                    } catch (Exception exc) {
                        toast(exc.getMessage());
                    }
                });
            } catch (Exception exc) {
                uiHandler.post(() -> toast("WEzterm control is not reachable"));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "{}";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void applySystemBarPadding(View root, View toolbar) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                );
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            // WHY: Android status, navigation, and keyboard IME bars all steal
            // real pixels on the S25 Ultra. If the toolbar ignores the IME inset,
            // ADB and real fingers can hit Samsung keyboard keys where Stop/Close
            // appear to be, which is exactly how Stop recorded a space byte (0x20)
            // instead of Escape. Padding/resizing only the toolbar keeps xterm's
            // viewport honest while keeping the command buttons physically above
            // the nav bar or keyboard.
            view.setPadding(0, top, 0, 0);
            toolbar.setPadding(dp(6), dp(5), dp(6), dp(5) + bottom);
            ViewGroup.LayoutParams params = toolbar.getLayoutParams();
            if (params != null) {
                params.height = dp(TOOLBAR_HEIGHT_DP) + bottom;
                toolbar.setLayoutParams(params);
            }
            return insets;
        });
    }

    private void requestHomeShortcutOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        if (prefs.getBoolean(PREF_PIN_REQUESTED, false)) {
            return;
        }

        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            return;
        }

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.setData(Uri.parse(TERMINAL_URL));

        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "wezterm-terminal-main")
                .setShortLabel("WEzterm")
                .setLongLabel("WEzterm")
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build();

        PendingIntent callback = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );
        manager.requestPinShortcut(shortcut, callback.getIntentSender());
        prefs.edit().putBoolean(PREF_PIN_REQUESTED, true).apply();
    }

    private class TerminalWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            focusTerminalInputSoon();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri != null && uri.toString().startsWith(TERMINAL_URL)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return url == null || !url.startsWith(TERMINAL_URL);
        }
    }
}
