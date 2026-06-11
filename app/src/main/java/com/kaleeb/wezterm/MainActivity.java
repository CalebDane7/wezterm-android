package com.kaleeb.wezterm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TERMINAL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8088/";
    private static final String CONTROL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8089";
    private static final String INSTALL_URL = "http://100.113.254.7:8091/install.html";
    private static final String PREFS = "wezterm";
    private static final String PREF_PIN_REQUESTED = "pin_requested";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final int DEFAULT_FONT_SIZE = 11;
    private static final int MIN_FONT_SIZE = 4;
    private static final int MAX_FONT_SIZE = 18;
    private static final int TOOLBAR_HEIGHT_DP = 56;
    private static final long HISTORY_DRAG_THROTTLE_MS = 28;
    private static final int HISTORY_DRAG_LINE_THRESHOLD_DP = 10;
    private static final int HISTORY_DRAG_PAGES_PER_STEP = 1;
    private static final int HISTORY_DRAG_MAX_PAGES_PER_STEP = 20;
    private static final int HISTORY_DRAG_RELEASE_FLING_BURSTS = 2;
    private static final float HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC = 1200f;
    private static final float HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC = 2600f;
    private static final float WEBVIEW_ZOOMED_SCALE_THRESHOLD = 1.02f;
    private static final long TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS = 700;
    private static final long LIVE_INPUT_VISIBILITY_BURST_MIN_INTERVAL_MS = 220;
    private static final long KEYBOARD_SHOW_MIN_INTERVAL_MS = 450;
    private WebView webView;
    private View historyTouchOverlay;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long lastReconnectReloadAtMs = 0;
    private long lastBlankTerminalReloadAtMs = 0;
    private long lastTerminalLoadAtMs = 0;
    private long lastLiveTapFocusAtMs = 0;
    private long lastViewportPinAtMs = 0;
    private long lastTerminalFocusBurstAtMs = 0;
    private long lastTerminalFocusBurstModeGeneration = -1;
    private long terminalFocusGeneration = 0;
    private long lastLiveInputVisibilityBurstAtMs = 0;
    private long lastLiveInputVisibilityBurstModeGeneration = -1;
    private long liveInputVisibilityGeneration = 0;
    private long lastKeyboardShowAtMs = 0;
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
    private boolean terminalTouchExceededTapSlop = false;
    private boolean terminalHorizontalPanActive = false;
    private boolean terminalTouchReachedLiveBottom = false;
    private boolean historyScrollRequestInFlight = false;
    private String pendingHistoryScrollWhere = "";
    private int pendingHistoryScrollRepeats = 0;
    private long lastHistoryDragAtMs = 0;
    private long terminalLastHistoryDragEventAtMs = 0;
    private float webViewScale = 1.0f;
    private long viewerPanUnlockedUntilMs = 0;
    private int lastImeInsetBottom = 0;
    private VelocityTracker terminalVelocityTracker;

    private interface JsonCallback {
        void onResult(JSONObject payload) throws Exception;
    }

    private interface FailureCallback {
        void onFailure(Exception exc);
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
            scheduleBlankTerminalWatchdog("resume");
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
            scheduleBlankTerminalWatchdog("window-focus");
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

        FrameLayout terminalFrame = new FrameLayout(this);
        terminalFrame.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        historyTouchOverlay = new View(this);
        historyTouchOverlay.setBackgroundColor(Color.TRANSPARENT);
        historyTouchOverlay.setVisibility(View.GONE);
        historyTouchOverlay.setOnTouchListener((touchedView, event) -> handleTerminalTouch(event));
        terminalFrame.addView(historyTouchOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        root.addView(terminalFrame, new LinearLayout.LayoutParams(
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
        toolbar.addView(toolbarButton("New Tab", v -> control("/new?fast=1", "New tab opened")));
        // WHY: during upgrades, the user should not have to close the Android
        // task or hunt for the same tmux tab just to refresh the terminal
        // transport. This button preserves the current tmux window, returns it
        // to live bottom, reloads only the WebView/ttyd connection, and focuses
        // xterm again.
        toolbar.addView(toolbarButton("Refresh", v -> refreshTerminalTransport()));
        // WHY: v1.33 removed visible Live/Read/View access and stranded the
        // proven scrollback/top/reader recovery paths behind an uncalled method.
        // Keep one plain Scroll entry on the main bar so one-finger gesture bugs,
        // Codex transcript pager drift, or keyboard focus failures never leave
        // the phone with no way back to history top or live typing.
        toolbar.addView(toolbarButton("Scroll", v -> showViewControls()));
        // WHY: phone paste must be a first-class action, not a Samsung keyboard
        // long-press trick. The button opens explicit copy/paste controls backed
        // by Android clipboard APIs and tmux paste buffers, so prompts can be
        // moved between phone apps and the exact active desktop pane.
        toolbar.addView(toolbarButton("Copy/Paste", v -> showCopyPasteControls()));
        // WHY: the phone surface must stay simple under Android's small
        // keyboard/status/nav real estate, but simplicity cannot delete recovery.
        // The visible actions remain switch tabs, create tab, scroll/recover,
        // copy/paste, steer/stop, and close the current tab.
        toolbar.addView(toolbarButton("Steer", v -> stopAndSteer()));
        toolbar.addView(toolbarButton("Close Tab", v -> confirmClose()));
        return toolbar;
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        button.setTextSize(label.length() > 9 ? 8 : 9);
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
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        view.setScrollContainer(false);
        // WHY: Android's edge effects can make a WebView pan look like the page
        // is refreshing or fighting the user's finger. The terminal already has
        // explicit Live/Read controls, so native overscroll feedback is noise.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.setOnScrollChangeListener((changedView, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollX != 0 || scrollY != 0) {
                if (isViewerPanAllowed()) {
                    // WHY: Android/WebView zoom is the viewer zoom layer. v1.41's
                    // unconditional scrollTo(0,0) fixed old blank-space drift, but
                    // it also erased zoomed left/right positioning and made pinch
                    // panning look broken. While zoomed, in a two-finger gesture, or
                    // in a deliberate horizontal pan, WebView owns positioning; one
                    // finger vertical history movement is still handled by tmux.
                    return;
                }
                // WHY: the visible terminal is xterm inside ttyd. Android
                // WebView document scroll is never the intended scroll layer and
                // creates the "below the text area" blank-space bug on older
                // tabs. Keep the browser viewport pinned while tmux/Codex owns
                // history movement.
                changedView.scrollTo(0, 0);
                pinTerminalViewportSoon("webview-scroll");
            }
        });
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
            // WHY: two-finger gestures belong to Android/WebView for viewer zoom
            // and positioning. The app must not consume them while trying to fix
            // tmux history, and the viewport pin must not snap them back to 0,0.
            terminalMultiTouchGesture = true;
            terminalHistoryDragActive = false;
            allowViewerPanBriefly();
            return false;
        }
        pinTerminalViewportLocal();

        if (action == MotionEvent.ACTION_DOWN) {
            resetTerminalVelocityTracker(event);
            terminalTouchStartX = event.getX();
            terminalTouchStartY = event.getY();
            terminalLastHistoryDragY = terminalTouchStartY;
            terminalLastHistoryDragEventAtMs = event.getEventTime();
            terminalHistoryDragActive = false;
            terminalMultiTouchGesture = false;
            terminalTouchExceededTapSlop = false;
            terminalHorizontalPanActive = false;
            terminalTouchReachedLiveBottom = false;
            terminalTouchStartedInHistoryViewport = terminalHistoryViewportActive || readModeSuppressesKeyboard;
            lastHistoryDragAtMs = 0;
            // WHY: v1.30 fixed tap-to-type by refocusing xterm on live taps,
            // but doing it on ACTION_DOWN races with a user's horizontal pan.
            // Samsung's keyboard/WebView focus can then snap the terminal back
            // left while the finger is trying to read a long line. Only refocus
            // after ACTION_UP proves this was a tap, not a pan or history drag.
            // WHY: when the terminal is in history/reader mode, a plain tap means
            // "return me to live input." If this ACTION_DOWN is allowed through to
            // WebView, xterm can open Samsung's keyboard before the server has
            // scrolled tmux/Codex back to the live bottom, recreating the bug where
            // the keyboard covers the composer and the user cannot see typing.
            return terminalTouchStartedInHistoryViewport;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            addTerminalMovement(event);
            if (terminalMultiTouchGesture) {
                return false;
            }
            float dx = event.getX() - terminalTouchStartX;
            float dy = event.getY() - terminalTouchStartY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            if (absDx > terminalTouchSlop || absDy > terminalTouchSlop) {
                terminalTouchExceededTapSlop = true;
            }
            if (!terminalHistoryDragActive
                    && absDx >= terminalTouchSlop * 2
                    && absDx > absDy * 1.25f) {
                // WHY: one-finger horizontal movement is the user's line-reading
                // pan inside ttyd/WebView. The app must not treat it as a live
                // tap or a server history gesture, or xterm focus will recenter
                // the viewport and recreate the "snaps back left" bug.
                terminalHorizontalPanActive = true;
                allowViewerPanBriefly();
                return false;
            }
            if (!terminalHistoryDragActive) {
                if (absDy < terminalTouchSlop || absDy < absDx * 1.2f) {
                    return terminalTouchStartedInHistoryViewport;
                }
                // WHY: once a one-finger gesture is clearly vertical, WEzterm
                // must take ownership immediately. Letting WebView/xterm handle
                // the early part of a vertical swipe is what made old tabs page
                // scroll below the terminal text area before the tmux/Codex
                // history layer could take over.
                terminalHistoryDragActive = true;
                terminalLastHistoryDragY = terminalTouchStartY;
                enterReadMode();
            }

            float step = event.getY() - terminalLastHistoryDragY;
            long now = System.currentTimeMillis();
            // WHY: v1.42 used page-sized HTTP scrolls. That could not paint
            // continuously under a finger, so the screen appeared frozen and
            // then jumped to a random-looking page. Use line-sized tmux
            // copy-mode movement for drag; the explicit Scroll menu still owns
            // jump-to-top, page-up/down, reader, and live-bottom recovery.
            int lineThreshold = Math.max(terminalTouchSlop, dp(HISTORY_DRAG_LINE_THRESHOLD_DP));
            if (Math.abs(step) >= lineThreshold && now - lastHistoryDragAtMs >= HISTORY_DRAG_THROTTLE_MS) {
                int repeats = historyDragRepeats(step, lineThreshold, event);
                terminalLastHistoryDragY = event.getY();
                terminalLastHistoryDragEventAtMs = event.getEventTime();
                lastHistoryDragAtMs = now;
                String where = step > 0 ? "lineUp" : "lineDown";
                if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
                    // WHY: once tmux has hit the live bottom, extra downward
                    // finger motion has nowhere meaningful to go. Sending more
                    // lineDown requests would re-enter/cancel copy-mode on every
                    // MOVE and looks like a page refresh/bounce at the bottom.
                    // Swallow only the continued downward edge; reversing upward
                    // still immediately re-enters tmux history.
                    terminalLastHistoryDragY = event.getY();
                    terminalLastHistoryDragEventAtMs = event.getEventTime();
                    lastHistoryDragAtMs = now;
                    return true;
                }
                if ("lineUp".equals(where)) {
                    terminalTouchReachedLiveBottom = false;
                }
                scrollTerminalFromTouch(where, repeats);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = terminalHistoryDragActive;
            boolean startedInHistoryViewport = terminalTouchStartedInHistoryViewport;
            boolean wasMultiTouch = terminalMultiTouchGesture;
            boolean wasHorizontalPan = terminalHorizontalPanActive;
            boolean movedPastTapSlop = terminalTouchExceededTapSlop;
            boolean reachedLiveBottom = terminalTouchReachedLiveBottom;
            if (action == MotionEvent.ACTION_UP && terminalHistoryDragActive) {
                dispatchHistoryReleaseFling(event);
            }
            boolean shouldRestoreTyping = action == MotionEvent.ACTION_UP
                    && startedInHistoryViewport
                    && !terminalHistoryDragActive;
            terminalHistoryDragActive = false;
            terminalMultiTouchGesture = false;
            terminalTouchExceededTapSlop = false;
            terminalHorizontalPanActive = false;
            terminalTouchReachedLiveBottom = false;
            terminalTouchStartedInHistoryViewport = false;
            if (wasMultiTouch || wasHorizontalPan) {
                allowViewerPanBriefly();
            }
            recycleTerminalVelocityTracker();
            if (shouldRestoreTyping) {
                restoreLiveForTyping("Typing ready");
                return true;
            }
            if (action == MotionEvent.ACTION_UP && reachedLiveBottom) {
                // WHY: while the finger is still dragging, reaching live bottom is
                // only a stop condition. Restoring live typing mid-drag makes the
                // terminal look like it refreshed/jumped before the user arrived
                // at the bottom. On finger-up, do the single explicit bottom
                // restore so typing returns after the scroll gesture is over.
                restoreLiveForTyping("At live bottom");
                return true;
            }
            if (action == MotionEvent.ACTION_UP
                    && !startedInHistoryViewport
                    && !consumed
                    && !wasMultiTouch
                    && !wasHorizontalPan
                    && !movedPastTapSlop) {
                scheduleLiveTapFocus("tap-up");
            }
            return consumed || startedInHistoryViewport;
        }

        return false;
    }

    private void resetTerminalVelocityTracker(MotionEvent event) {
        recycleTerminalVelocityTracker();
        terminalVelocityTracker = VelocityTracker.obtain();
        terminalVelocityTracker.addMovement(event);
    }

    private void addTerminalMovement(MotionEvent event) {
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.addMovement(event);
        }
    }

    private void recycleTerminalVelocityTracker() {
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.recycle();
            terminalVelocityTracker = null;
        }
    }

    private int historyDragRepeats(float step, int lineThreshold, MotionEvent event) {
        float velocity = 0f;
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.computeCurrentVelocity(1000);
            velocity = Math.abs(terminalVelocityTracker.getYVelocity());
        }
        long eventDeltaMs = Math.max(1, event.getEventTime() - terminalLastHistoryDragEventAtMs);
        float segmentVelocity = Math.abs(step) * 1000f / eventDeltaMs;
        velocity = Math.max(velocity, segmentVelocity);
        float distanceLines = Math.abs(step) / Math.max(1f, lineThreshold);
        // WHY: this intentionally behaves like normal phone scrolling. A slow
        // drag should advance a few lines at a time so text remains readable,
        // while a fast flick should batch more lines so the user can move
        // through a long Codex/session history quickly. The cap prevents bad flicks
        // from queuing huge delayed jumps that look like freezing or restarting.
        // Android's VelocityTracker can under-report synthetic and WebView-routed
        // gestures, so this also checks the segment velocity since the last
        // dispatched history step. That preserves slow-drag precision while
        // making a real fast flick feel like normal app inertia.
        int repeats = HISTORY_DRAG_PAGES_PER_STEP;
        if (velocity >= HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC || distanceLines >= 10f) {
            repeats = 8;
        } else if (velocity >= HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC || distanceLines >= 5f) {
            repeats = 4;
        } else if (distanceLines >= 2f) {
            repeats = 2;
        }
        return Math.max(1, Math.min(HISTORY_DRAG_MAX_PAGES_PER_STEP, repeats));
    }

    private void dispatchHistoryReleaseFling(MotionEvent event) {
        float totalDy = event.getY() - terminalTouchStartY;
        float absDy = Math.abs(totalDy);
        long durationMs = Math.max(1, event.getEventTime() - event.getDownTime());
        float releaseVelocity = absDy * 1000f / durationMs;
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.computeCurrentVelocity(1000);
            releaseVelocity = Math.max(releaseVelocity, Math.abs(terminalVelocityTracker.getYVelocity()));
        }
        int lineThreshold = Math.max(terminalTouchSlop, dp(HISTORY_DRAG_LINE_THRESHOLD_DP));
        if (absDy < lineThreshold * 4f || releaseVelocity < HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC) {
            return;
        }
        if (terminalTouchReachedLiveBottom && totalDy < 0) {
            // WHY: a release fling in the same downward direction after tmux has
            // already reported live bottom would queue extra lineDown requests at
            // the edge. That is the exact "refresh/bounce before I get to the
            // bottom" symptom; wait for finger-up restore instead.
            return;
        }
        boolean fullFling = releaseVelocity >= HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC;
        int repeats = fullFling
                ? HISTORY_DRAG_MAX_PAGES_PER_STEP
                : Math.max(8, HISTORY_DRAG_MAX_PAGES_PER_STEP / 2);
        // WHY: real fast flicks produce fewer ACTION_MOVE samples than slow drags,
        // especially through WebView and ADB input. v1.44 therefore moved fewer
        // lines for a fast flick than for a slow drag of the same distance. Add
        // one bounded release burst based on total gesture velocity so fast flicks
        // jump farther while slow finger movement remains line-by-line.
        String where = totalDy > 0 ? "lineUp" : "lineDown";
        scrollTerminalFromTouch(where, repeats);
        if (fullFling) {
            // WHY: if a fast flick releases while a MOVE request is still in
            // flight, the first release burst is intentionally coalesced into one
            // pending batch. A second short-delay burst gives real fling velocity
            // the extra distance users expect without affecting slow drags.
            uiHandler.postDelayed(() -> {
                if (terminalHistoryViewportActive && readModeSuppressesKeyboard) {
                    scrollTerminalFromTouch(where, repeats);
                }
            }, 140);
        }
    }

    private void scrollTerminalFromTouch(String where, int repeats) {
        // WHY: normal WebView scrolling moves ttyd/xterm's browser scrollback,
        // which records tmux redraw artifacts instead of the real pane history
        // visible to Codex. Deliberate one-finger vertical drags use the server
        // history path, but now as small lineUp/lineDown commands so the screen
        // tracks the finger instead of jumping by whole pages.
        // WHY: keep one request in flight and coalesce the newest direction so
        // stale responses cannot fight the user's finger. If the user keeps
        // dragging in the same direction, accumulate a small capped batch.
        int boundedRepeats = Math.max(1, Math.min(HISTORY_DRAG_MAX_PAGES_PER_STEP, repeats));
        if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
            return;
        }
        if ("lineUp".equals(where)) {
            terminalTouchReachedLiveBottom = false;
        }
        if (historyScrollRequestInFlight) {
            if (where.equals(pendingHistoryScrollWhere)) {
                pendingHistoryScrollRepeats = Math.min(
                        HISTORY_DRAG_MAX_PAGES_PER_STEP,
                        pendingHistoryScrollRepeats + boundedRepeats
                );
            } else {
                pendingHistoryScrollWhere = where;
                pendingHistoryScrollRepeats = boundedRepeats;
            }
            return;
        }
        sendHistoryScrollFromTouch(where, boundedRepeats);
    }

    private void sendHistoryScrollFromTouch(String where, int repeats) {
        long readModeGeneration = terminalHistoryViewportActive
                ? terminalModeGeneration
                : enterReadMode();
        historyScrollRequestInFlight = true;
        String path = "/scroll?where=" + urlEncode(where)
                + "&mode=touch&repeat=" + Math.max(1, repeats);
        getJson(path, payload -> {
            historyScrollRequestInFlight = false;
            if ("lineDown".equals(where) && touchScrollReachedLiveBottom(payload)) {
                // WHY: the server has reached tmux's real live bottom. During
                // ACTION_MOVE this must remain a stop signal, not a live-typing
                // restore, or the screen flips to the prompt before the user's
                // finger has finished scrolling down. Clear queued down-scrolls
                // and keep read-mode until ACTION_UP performs one explicit bottom
                // restore.
                terminalTouchReachedLiveBottom = true;
                pendingHistoryScrollWhere = "";
                pendingHistoryScrollRepeats = 0;
                if (readModeGeneration == terminalModeGeneration) {
                    keepReadModeIfCurrent(readModeGeneration);
                }
                return;
            }
            if (readModeGeneration == terminalModeGeneration) {
                keepReadModeIfCurrent(readModeGeneration);
            }
            drainPendingHistoryScroll();
        }, exc -> {
            historyScrollRequestInFlight = false;
            toast("WEzterm control is not reachable");
            drainPendingHistoryScroll();
        });
    }

    private boolean touchScrollReachedLiveBottom(JSONObject payload) {
        if (payload == null) {
            return false;
        }
        if (payload.optBoolean("atLiveBottom", false)) {
            return true;
        }
        return false;
    }

    private void drainPendingHistoryScroll() {
        if (pendingHistoryScrollWhere.isEmpty()
                || historyScrollRequestInFlight
                || !terminalHistoryViewportActive
                || !readModeSuppressesKeyboard) {
            return;
        }
        String nextWhere = pendingHistoryScrollWhere;
        int nextRepeats = pendingHistoryScrollRepeats;
        pendingHistoryScrollWhere = "";
        pendingHistoryScrollRepeats = 0;
        sendHistoryScrollFromTouch(nextWhere, Math.max(1, nextRepeats));
    }

    private void loadTerminal() {
        int fontSize = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
        markTerminalLoadStarted();
        // WHY: Android WebView can be sluggish with ttyd's default WebGL xterm
        // renderer. ttyd documents `rendererType=canvas` as a client option,
        // and URL options outrank server defaults, so the app keeps this even
        // when the server is still running an older command line.
        webView.loadUrl(TERMINAL_URL
                + "?fontSize=" + fontSize
                + "&disableLeaveAlert=true"
                + "&rendererType=canvas"
                + "&scrollOnUserInput=true");
        pinTerminalViewportSoon("load");
        focusTerminalInputSoon();
        keepLiveInputVisibleSoon("load");
        scheduleBlankTerminalWatchdog("load");
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

    private void refreshTerminalTransport() {
        // WHY: Android package updates and ttyd reconnects can leave a stale
        // WebView transport even while the tmux session is still healthy. This
        // is deliberately not Activity.recreate(), not tab close/open, and not
        // a tmux restart. The current window stays selected; only the browser
        // connection is reloaded and then focused again.
        long generation = leaveReadModeForLiveInput();
        getJson("/fix-view", payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Refresh failed"));
                return;
            }
            reloadTerminalTransportOnly("fix-view");
            toast("Refreshed current session");
        }, exc -> {
            reloadTerminalTransportOnly("fix-view");
            toast("Refreshed terminal transport");
        });
    }

    private void reloadTerminalTransportOnly(String reason) {
        if (webView == null) {
            return;
        }
        String currentUrl = webView.getUrl();
        markTerminalLoadStarted();
        webView.loadUrl(currentUrl == null ? TERMINAL_URL : currentUrl);
        pinTerminalViewportSoon(reason);
        focusTerminalInputSoon();
        keepLiveInputVisibleSoon(reason);
        scheduleBlankTerminalWatchdog(reason);
    }

    private void openInstallPage() {
        // WHY: future APK updates must not depend on USB. The install page is
        // served from the desktop's Tailscale IP, so the phone can fetch the
        // current APK while it is away from the cable.
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(INSTALL_URL)));
        } catch (Exception exc) {
            toast("Could not open install page");
        }
    }

    private void createBugReport() {
        try {
            JSONObject client = new JSONObject();
            client.put("appVersion", "1.50");
            client.put("webViewUrl", webView == null ? "" : webView.getUrl());
            client.put("readMode", readModeSuppressesKeyboard);
            client.put("historyViewport", terminalHistoryViewportActive);
            postText("/bug-report", client.toString(), payload -> {
                if (!payload.optBoolean("ok", false)) {
                    toast(payload.optString("error", "Bug report failed"));
                    return;
                }
                String path = payload.optString("path", "");
                ClipboardManager clipboardManager =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboardManager != null && !path.isEmpty()) {
                    clipboardManager.setPrimaryClip(
                            ClipData.newPlainText("WEzterm bug report", path)
                    );
                }
                toast(path.isEmpty() ? "Bug report created" : "Bug report path copied");
            }, exc -> toast("WEzterm control is not reachable"));
        } catch (Exception exc) {
            toast("Bug report failed");
        }
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
        showHistoryTouchOverlay();
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
            pinTerminalViewportSoon("live-bottom");
            focusTerminalInputSoon();
            keepLiveInputVisibleSoon("live-bottom");
        }, exc -> {
            liveRestoreInFlight = false;
            toast("WEzterm control is not reachable");
        });
    }

    private long leaveReadModeForLiveInput() {
        long generation = ++terminalModeGeneration;
        terminalHistoryViewportActive = false;
        terminalTouchStartedInHistoryViewport = false;
        readModeSuppressesKeyboard = false;
        pendingHistoryScrollWhere = "";
        pendingHistoryScrollRepeats = 0;
        hideHistoryTouchOverlay();
        return generation;
    }

    private void showHistoryTouchOverlay() {
        if (historyTouchOverlay == null) {
            return;
        }
        // WHY: Android WebView/xterm can swallow synthetic and real touch
        // gestures in different horizontal zones while the terminal is in
        // history mode. A transparent overlay only during read/history mode
        // makes swiping deterministic across the whole terminal pane without
        // interfering with normal live typing, link taps, or the xterm textarea.
        historyTouchOverlay.setVisibility(View.VISIBLE);
        historyTouchOverlay.bringToFront();
    }

    private void hideHistoryTouchOverlay() {
        if (historyTouchOverlay != null) {
            historyTouchOverlay.setVisibility(View.GONE);
        }
        pinTerminalViewportSoon("leave-read-mode");
    }

    private void pinTerminalViewportLocal() {
        if (webView == null) {
            return;
        }
        if (webView.getScrollX() != 0 || webView.getScrollY() != 0) {
            if (isViewerPanAllowed()) {
                return;
            }
            webView.scrollTo(0, 0);
        }
    }

    private void pinTerminalViewportSoon(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (isViewerPanAllowed()) {
            // WHY: this pin exists only to clean up accidental document scroll.
            // During zoomed/two-finger/horizontal positioning, native WebView
            // scroll is the user's viewport, so pinning here recreates the exact
            // "I can zoom but cannot move left/right" regression.
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastViewportPinAtMs < 80) {
            return;
        }
        lastViewportPinAtMs = now;
        pinTerminalViewportLocal();
        // WHY: ttyd/xterm has its own scrollback, but the surrounding HTML
        // document can still acquire a scroll offset after tab switches, older
        // sessions, IME resizes, or WebView gesture handoff. If the document
        // scrolls, the phone shows blank space below the prompt and it feels
        // like the page is refreshing. Pin html/body to the viewport and reset
        // document scroll without touching xterm's internal history buffer.
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var html=document.documentElement;"
                        + "var body=document.body;"
                        + "if(html){html.style.overflow='hidden';html.style.height='100%';html.style.overscrollBehavior='none';html.scrollTop=0;html.scrollLeft=0;}"
                        + "if(body){body.style.overflow='hidden';body.style.height='100%';body.style.margin='0';body.style.overscrollBehavior='none';body.scrollTop=0;body.scrollLeft=0;}"
                        + "var scrolling=document.scrollingElement;"
                        + "if(scrolling){scrolling.scrollTop=0;scrolling.scrollLeft=0;}"
                        + "window.scrollTo(0,0);"
                        + "return 'pinned';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
        uiHandler.postDelayed(() -> {
            if (webView != null) {
                pinTerminalViewportLocal();
            }
        }, 120);
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
                "Command palette",
                "Refresh current session",
                "Sessions by date",
                "Needs attention",
                "Create bug report",
                "Install/update over Tailscale",
                "Go to live bottom / type",
                "Go to history top",
                "Open full session reader",
                "Page up",
                "Page down",
                "Smaller terminal text",
                "Larger terminal text",
                "Reset text size (" + DEFAULT_FONT_SIZE + ")",
                "Stop current task",
                "Current text size: " + current
        };
        new AlertDialog.Builder(this)
                .setTitle("Scroll")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        showCommandPalette();
                    } else if (which == 1) {
                        refreshTerminalTransport();
                    } else if (which == 2) {
                        showTabs();
                    } else if (which == 3) {
                        showNeedsAttention();
                    } else if (which == 4) {
                        createBugReport();
                    } else if (which == 5) {
                        openInstallPage();
                    } else if (which == 6) {
                        goLiveBottom();
                    } else if (which == 7) {
                        enterReadMode();
                        scrollTerminal("top", "History top", false);
                    } else if (which == 8) {
                        enterReadMode();
                        control("/read-session", "Session reader", false);
                    } else if (which == 9) {
                        enterReadMode();
                        scrollTerminal("pageUp", "Page up", false);
                    } else if (which == 10) {
                        enterReadMode();
                        scrollTerminal("pageDown", "Page down", false);
                    } else if (which == 11) {
                        adjustFont(-1);
                    } else if (which == 12) {
                        adjustFont(1);
                    } else if (which == 13) {
                        resetFont();
                    } else if (which == 14) {
                        control("/stop", "Stop sent");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCommandPalette() {
        String[] labels = new String[]{
                "Refresh current session",
                "Sessions by date",
                "Needs attention",
                "Copy/Paste",
                "Go to live bottom / type",
                "Go to history top",
                "Open full session reader",
                "Page up",
                "Page down",
                "Install/update over Tailscale",
                "Create bug report",
                "Stop current task",
                "Rename current tab"
        };
        new AlertDialog.Builder(this)
                .setTitle("Command palette")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        refreshTerminalTransport();
                    } else if (which == 1) {
                        showTabs();
                    } else if (which == 2) {
                        showNeedsAttention();
                    } else if (which == 3) {
                        showCopyPasteControls();
                    } else if (which == 4) {
                        goLiveBottom();
                    } else if (which == 5) {
                        enterReadMode();
                        scrollTerminal("top", "History top", false);
                    } else if (which == 6) {
                        openFullSessionReader();
                    } else if (which == 7) {
                        enterReadMode();
                        scrollTerminal("pageUp", "Page up", false);
                    } else if (which == 8) {
                        enterReadMode();
                        scrollTerminal("pageDown", "Page down", false);
                    } else if (which == 9) {
                        openInstallPage();
                    } else if (which == 10) {
                        createBugReport();
                    } else if (which == 11) {
                        control("/stop", "Stop sent");
                    } else if (which == 12) {
                        showRenameCurrentTab();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCopyPasteControls() {
        String[] labels = new String[]{
                "Paste phone clipboard into terminal",
                "Copy visible terminal text"
        };
        new AlertDialog.Builder(this)
                .setTitle("Copy/Paste")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        pasteClipboardIntoTerminal();
                    } else if (which == 1) {
                        copyVisibleTerminalToClipboard();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pasteClipboardIntoTerminal() {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            toast("Phone clipboard is empty");
            return;
        }
        ClipData clip = clipboardManager.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("Phone clipboard is empty");
            return;
        }
        CharSequence clipboardText = clip.getItemAt(0).coerceToText(this);
        if (clipboardText == null || clipboardText.length() == 0) {
            toast("Phone clipboard is empty");
            return;
        }
        // WHY: pasting is a live-input action. Clear local read-mode flags before
        // the server forces tmux/Codex to live bottom, or delayed read-mode focus
        // guards can hide the keyboard immediately after the paste.
        long generation = leaveReadModeForLiveInput();
        postText("/paste", clipboardText.toString(), payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Paste failed"));
                return;
            }
            toast("Pasted");
            focusTerminalInputSoon();
            keepLiveInputVisibleSoon("paste");
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void copyVisibleTerminalToClipboard() {
        getJson("/copy-visible", payload -> {
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Copy failed"));
                return;
            }
            String text = payload.optString("text", "");
            if (text.isEmpty()) {
                toast("Nothing visible to copy");
                return;
            }
            ClipboardManager clipboardManager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                toast("Phone clipboard is not available");
                return;
            }
            clipboardManager.setPrimaryClip(
                    ClipData.newPlainText("WEzterm visible terminal", text)
            );
            toast("Copied visible terminal");
        });
    }

    private void showTabs() {
        getJson("/sessions", payload -> showTabsDialog(payload, "Sessions", true), exc ->
                getJson("/tabs", payload -> showTabsDialog(payload, "Tabs", false))
        );
    }

    private void showNeedsAttention() {
        getJson("/needs-attention", payload -> showTabsDialog(payload, "Needs Attention", false));
    }

    private void showTabsDialog(JSONObject payload, String title, boolean preferGroups) throws Exception {
        String session = payload.optString("session", "main");
        String viewSession = payload.optString("viewSession", "main_phone");
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFocusable(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setFocusable(false);
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        scrollView.addView(list);

        TextView header = new TextView(this);
        header.setText("Session: " + viewSession + " (" + session + ")");
        header.setTextSize(14);
        header.setTextColor(Color.rgb(205, 214, 244));
        header.setPadding(0, 0, 0, dp(8));
        list.addView(header);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        JSONArray groups = payload.optJSONArray("groups");
        if (preferGroups && groups != null && groups.length() > 0) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                JSONArray windows = group.optJSONArray("windows");
                if (windows == null || windows.length() == 0) {
                    continue;
                }
                addSectionHeader(list, group.optString("label", "Sessions"), windows.length());
                addTabRows(list, sortedWindows(windows), session, dialogRef);
            }
        } else {
            JSONArray windows = payload.getJSONArray("windows");
            if (windows.length() == 0) {
                addSectionHeader(list, "Nothing needs attention", 0);
            } else {
                addTabRows(list, sortedWindows(windows), session, dialogRef);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton("New tab", (d, which) -> control("/new?fast=1", "New tab"))
                .setNeutralButton("Rename current tab", (d, which) -> showRenameCurrentTab())
                .setNegativeButton("Cancel", null)
                .show();
        dialogRef[0] = dialog;
        // WHY: Android AlertDialog can focus a child row or preserve a measured
        // scroll position, which made Tabs open in the middle. The phone picker
        // is always a "start from the newest/attention section" surface.
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private List<JSONObject> sortedWindows(JSONArray windows) throws Exception {
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < windows.length(); i++) {
            sorted.add(windows.getJSONObject(i));
        }
        // WHY: tmux window indexes are old-to-new and shift after closes. The
        // phone picker should start with the work touched most recently. Use
        // server-provided `window_activity`, then fall back to higher indexes
        // only when tmux reports equal activity timestamps.
        sorted.sort((left, right) -> {
            long leftActivity = left.optLong("activityAt", 0);
            long rightActivity = right.optLong("activityAt", 0);
            if (leftActivity != rightActivity) {
                return Long.compare(rightActivity, leftActivity);
            }
            return Integer.compare(right.optInt("index", 0), left.optInt("index", 0));
        });
        return sorted;
    }

    private void addSectionHeader(LinearLayout list, String label, int count) {
        TextView section = new TextView(this);
        section.setText(count > 0 ? label + " (" + count + ")" : label);
        section.setTextSize(12);
        section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        section.setTextColor(Color.rgb(249, 226, 175));
        section.setPadding(0, dp(8), 0, dp(4));
        list.addView(section);
    }

    private void addTabRows(
            LinearLayout list,
            List<JSONObject> windows,
            String session,
            AlertDialog[] dialogRef
    ) throws Exception {
        for (int i = 0; i < windows.size(); i++) {
            addTabRow(list, windows.get(i), session, dialogRef);
        }
    }

    private void addTabRow(
            LinearLayout list,
            JSONObject window,
            String session,
            AlertDialog[] dialogRef
    ) throws Exception {
        int index = window.getInt("index");
        String windowId = window.optString("windowId", "");
        String title = window.optString("title", window.optString("name", "shell"));
        String detail = tabDetail(window, session);
        String status = window.optString("status", "idle");
        String statusLabel = window.optString("statusLabel", "Done");

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

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView statusDot = new TextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(14);
        statusDot.setGravity(android.view.Gravity.CENTER);
        statusDot.setIncludeFontPadding(false);
        statusDot.setContentDescription(statusLabel);
        if ("running".equals(status)) {
            statusDot.setTextColor(Color.rgb(166, 227, 161));
            AlphaAnimation pulse = new AlphaAnimation(0.35f, 1.0f);
            pulse.setDuration(650);
            pulse.setRepeatMode(Animation.REVERSE);
            pulse.setRepeatCount(Animation.INFINITE);
            statusDot.startAnimation(pulse);
        } else if ("problem".equals(status)) {
            statusDot.setTextColor(Color.rgb(243, 139, 168));
        } else if (window.optBoolean("needsAttention", false)) {
            statusDot.setTextColor(Color.rgb(249, 226, 175));
        } else {
            statusDot.setTextColor(Color.rgb(127, 132, 156));
        }

        TextView titleText = new TextView(this);
        titleText.setText((window.optBoolean("active", false) ? "Current: " : "") + title);
        titleText.setTextSize(15);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.rgb(255, 96, 112));
        titleText.setSingleLine(false);
        titleText.setMaxLines(Integer.MAX_VALUE);
        titleText.setEllipsize(null);
        titleText.setIncludeFontPadding(false);
        titleText.setHorizontallyScrolling(false);
        titleText.setOnLongClickListener(v -> {
            ClipboardManager clipboardManager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(
                        ClipData.newPlainText("WEzterm session title", title)
                );
                toast("Session title copied");
            }
            return true;
        });

        TextView detailText = new TextView(this);
        detailText.setText(detail);
        detailText.setTextSize(11);
        detailText.setTextColor(Color.rgb(166, 173, 200));
        detailText.setSingleLine(true);
        detailText.setEllipsize(TextUtils.TruncateAt.END);
        detailText.setIncludeFontPadding(false);

        // WHY: users need to know whether a Codex tab is still actively working
        // before switching or closing it. The dot is driven by control-server
        // pane evidence, not by the mutable title string, so scanability improves
        // without changing the stable windowId close/select target.
        titleRow.addView(statusDot, new LinearLayout.LayoutParams(
                dp(18),
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        titleRow.addView(titleText, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        // WHY: session titles are the user's main recovery signal. A fixed 72dp
        // card plus single-line ellipsis made long Codex titles unreadable on
        // the phone. Let the title row and card grow vertically so the full title
        // is readable, while the dialog ScrollView absorbs the extra height.
        openPanel.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        openPanel.addView(detailText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                dp(92),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        close.setMinHeight(dp(72));
        closeParams.setMargins(dp(6), 0, 0, 0);
        row.addView(close, closeParams);
        list.addView(row);
    }

    private void confirmClose() {
        // WHY: closing the active tab should not rebuild the whole Tabs dialog
        // payload. `/tabs` now includes per-window status checks, which are useful
        // for the picker but made the main Close button feel disconnected.
        getJson("/active", payload -> {
            JSONObject window = payload.optJSONObject("window");
            if (window == null) {
                confirmClose(-1, "", "current tab");
                return;
            }
            confirmClose(window.getInt("index"), window.optString("windowId", ""), window.optString("title", "current tab"));
        });
    }

    private void confirmClose(int index, String windowId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Close " + title + "?")
                .setMessage("This kills that tmux tab and whatever is running inside it.")
                .setPositiveButton("Close", (dialog, which) -> {
                    String path = index >= 0
                            ? "/close?fast=1&windowId=" + urlEncode(windowId) + "&index=" + index
                            : "/close?fast=1";
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
        String status = window.optString("statusLabel", "Done");
        String attention = window.optString("attentionReason", "");
        String activity = window.optString("activityGroup", "");
        String detail = window.optString("detail", window.optString("command", ""));
        String path = window.optString("shortPath", "");
        return status
                + (attention.isEmpty() ? "" : " - " + attention)
                + " - " + state
                + (activity.isEmpty() ? "" : " - " + activity)
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
        // WHY: switching tabs used to wait for the server to rebuild the full
        // tab list, including pane-tail status reads for every Codex window,
        // before returning to live typing. The picker already has the data; the
        // switch path only needs an OK so it can restore live bottom immediately.
        String path = "/select?fast=1&windowId=" + urlEncode(windowId) + "&index=" + index;
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
        if (webView == null) {
            return;
        }
        if (readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            hideKeyboardForReadMode();
            return;
        }
        long now = System.currentTimeMillis();
        if (lastTerminalFocusBurstModeGeneration == terminalModeGeneration
                && now - lastTerminalFocusBurstAtMs < TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS) {
            // WHY: this method is called by resume, window-focus, page-finished,
            // Refresh, live-bottom restore, paste, and tap-up. Letting every caller
            // enqueue its own 2+ second retry chain reintroduced the old Samsung
            // IME bug where stale focus callbacks fired after typing began and text
            // appeared duplicated. Collapse near-simultaneous callers into the
            // current burst; the current generation still keeps the terminal awake.
            keepLiveInputVisibleSoon("focus-collapsed");
            return;
        }
        lastTerminalFocusBurstAtMs = now;
        lastTerminalFocusBurstModeGeneration = terminalModeGeneration;
        long generation = ++terminalFocusGeneration;
        // WHY: ttyd connects asynchronously and Android may drop programmatic
        // focus during WebView paint/IME setup. A short generation-guarded burst
        // keeps the terminal ready for typing, but stale callbacks must be unable
        // to refocus the hidden xterm textarea after the user has already started
        // composing text in Samsung Keyboard.
        focusTerminalInput(generation);
        postTerminalFocusRetry(generation, 150);
        postTerminalFocusRetry(generation, 600);
        postTerminalFocusRetry(generation, 950);
        keepLiveInputVisibleSoon("focus");
    }

    private void postTerminalFocusRetry(long generation, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (generation != terminalFocusGeneration) {
                return;
            }
            focusTerminalInput(generation);
        }, delayMs);
    }

    private void focusTerminalInput(long generation) {
        if (webView == null) {
            return;
        }
        if (generation != terminalFocusGeneration) {
            return;
        }
        if (readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            hideKeyboardForReadMode();
            return;
        }
        webView.requestFocusFromTouch();
        webView.requestFocus(View.FOCUS_DOWN);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            showKeyboardIfLive(generation);
            return;
        }
        webView.evaluateJavascript(
                terminalFocusAndReconnectProbeScript(),
                value -> handleTerminalFocusProbe(value, generation)
        );
        showKeyboardIfLive(generation);
    }

    private boolean isViewerZoomed() {
        return webViewScale > WEBVIEW_ZOOMED_SCALE_THRESHOLD;
    }

    private boolean isViewerPanAllowed() {
        return isViewerZoomed()
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive
                || System.currentTimeMillis() < viewerPanUnlockedUntilMs;
    }

    private void allowViewerPanBriefly() {
        viewerPanUnlockedUntilMs = System.currentTimeMillis() + 900;
    }

    private void keepLiveInputVisibleSoon(String reason) {
        long now = System.currentTimeMillis();
        if (lastLiveInputVisibilityBurstModeGeneration == terminalModeGeneration
                && now - lastLiveInputVisibilityBurstAtMs < LIVE_INPUT_VISIBILITY_BURST_MIN_INTERVAL_MS) {
            return;
        }
        lastLiveInputVisibilityBurstAtMs = now;
        lastLiveInputVisibilityBurstModeGeneration = terminalModeGeneration;
        long generation = ++liveInputVisibilityGeneration;
        // WHY: focusing xterm and showing the Android keyboard are not enough when
        // WebView is zoomed. The visual viewport can be panned so the live Codex
        // composer is physically under Samsung's IME. These retries run after the
        // keyboard inset arrives and move xterm to its live bottom without changing
        // tmux's font size or destroying the user's horizontal zoom position.
        keepLiveInputVisible(reason, generation);
        postLiveInputVisibilityRetry(reason, generation, 220);
        postLiveInputVisibilityRetry(reason, generation, 650);
        postLiveInputVisibilityRetry(reason, generation, 1300);
    }

    private void postLiveInputVisibilityRetry(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (generation != liveInputVisibilityGeneration) {
                return;
            }
            keepLiveInputVisible(reason, generation);
        }, delayMs);
    }

    private void keepLiveInputVisible(String reason, long generation) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (generation != liveInputVisibilityGeneration) {
            return;
        }
        if (readModeSuppressesKeyboard
                || terminalHistoryViewportActive
                || terminalHistoryDragActive
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive) {
            return;
        }
        webView.evaluateJavascript(liveInputVisibilityScript(isViewerPanAllowed()), null);
        scrollViewerToTypingPosition();
    }

    private String liveInputVisibilityScript(boolean preserveViewerPan) {
        String resetDocumentScroll = preserveViewerPan
                ? ""
                : "var scrolling=document.scrollingElement;"
                        + "if(scrolling){scrolling.scrollTop=0;scrolling.scrollLeft=0;}"
                        + "if(document.documentElement){document.documentElement.scrollTop=0;document.documentElement.scrollLeft=0;}"
                        + "if(document.body){document.body.scrollTop=0;document.body.scrollLeft=0;}"
                        + "window.scrollTo(0,0);";
        return "(function(){"
                + "try{"
                + "var t=window.term||window.terminal;"
                + "if(t&&typeof t.scrollToBottom==='function'){t.scrollToBottom();}"
                + "var viewport=document.querySelector('.xterm-viewport');"
                + "if(viewport){viewport.scrollTop=viewport.scrollHeight;}"
                + "var screen=document.querySelector('.xterm-screen,.xterm');"
                + "if(screen&&typeof screen.scrollIntoView==='function'){screen.scrollIntoView({block:'end',inline:'nearest'});}"
                + "var el=document.querySelector('.xterm-helper-textarea, .xterm textarea, textarea');"
                + "if(el){"
                + "el.setAttribute('autocapitalize','none');"
                + "el.setAttribute('autocomplete','off');"
                + "el.setAttribute('autocorrect','off');"
                + "el.setAttribute('spellcheck','false');"
                + "}"
                + resetDocumentScroll
                + "return 'visible';"
                + "}catch(e){return 'err';}"
                + "})()";
    }

    private void scrollViewerToTypingPosition() {
        if (webView == null || !isViewerZoomed()) {
            return;
        }
        webView.postDelayed(() -> {
            if (webView == null
                    || !isViewerZoomed()
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || terminalHistoryDragActive
                    || terminalMultiTouchGesture
                    || terminalHorizontalPanActive) {
                return;
            }
            int contentHeightPx = Math.round(webView.getContentHeight() * webViewScale);
            int maxY = Math.max(0, contentHeightPx - webView.getHeight());
            if (maxY > 0 && webView.getScrollY() < maxY) {
                // WHY: preserve horizontal pan while moving the zoomed Android
                // viewer down enough that the live xterm input sits above the IME.
                // Resetting X here would regress long-line reading; doing nothing
                // leaves the composer hidden below the keyboard when zoomed in.
                webView.scrollTo(webView.getScrollX(), maxY);
            }
        }, 80);
    }

    private String terminalFocusAndReconnectProbeScript() {
        // WHY: this script is intentionally narrow. Earlier broad scans for the
        // word "reconnect" caused random reloads when terminal output merely
        // mentioned reconnecting. The actual ttyd failure is a small overlay
        // text node like "Press ↵ to Reconnect" outside xterm's terminal rows.
        // Detect only that overlay shape, then let Android reload the WebView so
        // the phone reconnects without the user pressing Enter.
        return "(function(){"
                + "try{"
                + "var t=window.term||window.terminal;"
                + "var el=document.querySelector('.xterm-helper-textarea, .xterm textarea, textarea');"
                + "var root=document.querySelector('.xterm');"
                + "var already=!!(el&&document.activeElement===el);"
                + "if(!already&&t&&typeof t.focus==='function'){t.focus();}"
                + "if(!already&&root&&typeof root.focus==='function'){root.focus();}"
                + "if(el){"
                + "el.setAttribute('autocapitalize','none');"
                + "el.setAttribute('autocomplete','off');"
                + "el.setAttribute('autocorrect','off');"
                + "el.setAttribute('spellcheck','false');"
                + "if(document.activeElement!==el){try{el.focus({preventScroll:true});}catch(e){el.focus();}}"
                + "}"
                + "function reconnectText(s){"
                + "s=(s||'').replace(/\\s+/g,' ').trim();"
                + "return s.length>0&&s.length<80&&/press/i.test(s)&&/reconnect/i.test(s)&&(/enter/i.test(s)||/[↵⏎↩]/.test(s));"
                + "}"
                + "var overlay=false;"
                + "var overlayText='';"
                + "var nodes=document.querySelectorAll('body *');"
                + "for(var i=0;i<nodes.length;i++){"
                + "var node=nodes[i];"
                + "if(node.closest&&node.closest('.xterm-rows,.xterm-screen,.xterm-helper-textarea')){continue;}"
                + "var text=(node.innerText||node.textContent||'').replace(/\\s+/g,' ').trim();"
                + "if(reconnectText(text)){overlay=true;overlayText=text;break;}"
                + "}"
                + "return ({status:el?(document.activeElement===el?'focused':'focus-requested'):'no-input',needsReconnect:overlay,overlayText:overlayText});"
                + "}catch(e){return ({status:'err',needsReconnect:false,error:String(e)});}"
                + "})()";
    }

    private void handleTerminalFocusProbe(String value, long generation) {
        if (generation != terminalFocusGeneration) {
            return;
        }
        showKeyboardIfLive(generation);
        JSONObject probe = parseJavascriptObject(value);
        if (probe == null || !probe.optBoolean("needsReconnect", false)) {
            return;
        }
        // WHY: the user-facing contract is that WEzterm reconnects when opened
        // or focused. Pressing Enter here would send a real key into whichever
        // Codex/tmux pane is active after reconnect, so reload the WebView
        // transport instead of synthesizing keyboard input.
        reloadTerminalForReconnect();
    }

    private void scheduleLiveTapFocus(String reason) {
        long generation = terminalModeGeneration;
        long now = System.currentTimeMillis();
        if (now - lastLiveTapFocusAtMs < 180) {
            return;
        }
        lastLiveTapFocusAtMs = now;
        uiHandler.postDelayed(() -> {
            if (generation != terminalModeGeneration
                    || terminalHistoryDragActive
                    || terminalMultiTouchGesture
                    || terminalHistoryViewportActive
                    || readModeSuppressesKeyboard) {
                return;
            }
            // WHY: A normal live tap used to be passed through to ttyd/xterm but
            // did not always reopen Samsung's keyboard after the first hidden
            // keyboard cycle. Android's IME API needs a focused native view, while
            // xterm also needs its hidden textarea focused. Keep this delayed and
            // generation-guarded so a swipe into history/read mode can still hide
            // the keyboard and two-finger pinch zoom remains WebView-owned.
            focusTerminalInputSoon();
        }, 140);
    }

    private void showKeyboardIfLive(long generation) {
        if (webView == null || readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            return;
        }
        if (generation != terminalFocusGeneration) {
            return;
        }
        if (terminalHistoryDragActive || terminalMultiTouchGesture || terminalHorizontalPanActive) {
            return;
        }
        if (!webView.hasWindowFocus()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeyboardShowAtMs < KEYBOARD_SHOW_MIN_INTERVAL_MS) {
            return;
        }
        lastKeyboardShowAtMs = now;
        webView.requestFocusFromTouch();
        webView.requestFocus(View.FOCUS_DOWN);
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
        markTerminalLoadStarted();
        webView.reload();
        uiHandler.postDelayed(() -> focusTerminalInputSoon(), 800);
        scheduleBlankTerminalWatchdog("reconnect-reload");
    }

    private void scheduleBlankTerminalWatchdog(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = lastTerminalLoadAtMs;
        uiHandler.postDelayed(() -> verifyTerminalPainted(reason, generation), 2600);
    }

    private void verifyTerminalPainted(String reason, long loadGeneration) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (terminalHistoryDragActive
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive
                || terminalHistoryViewportActive
                || readModeSuppressesKeyboard) {
            return;
        }
        if (loadGeneration != lastTerminalLoadAtMs) {
            return;
        }
        // WHY: On 2026-06-08 the phone resumed from a Chrome Custom Tab/blank
        // WebView layer. Android focus was on this Activity and ttyd/tmux were
        // healthy, but the WebView never made a fresh HTTP/WebSocket request, so
        // the user saw a black terminal until the whole app was force-stopped.
        // This watchdog only reloads when the ttyd/xterm renderer is absent
        // after a real load delay. Do not replace it with a blind onResume
        // reload: that would disconnect every normal app switch and recreate
        // the older "tab jumps while I type" regression.
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var term=document.querySelector('.xterm');"
                        + "var rows=document.querySelector('.xterm-rows');"
                        + "var text=(rows&&rows.innerText||document.body&&document.body.innerText||'').trim();"
                        + "var canvas=document.querySelector('canvas');"
                        + "return ({hasTerm:!!term,hasCanvas:!!canvas,canvasWidth:canvas?canvas.width:0,canvasHeight:canvas?canvas.height:0,textLength:text.length});"
                        + "}catch(e){return ({error:String(e)});}"
                + "})()",
                value -> handleTerminalPaintProbe(value, reason, loadGeneration)
        );
    }

    private void handleTerminalPaintProbe(String value, String reason, long loadGeneration) {
        if (loadGeneration != lastTerminalLoadAtMs) {
            return;
        }
        if (terminalHistoryDragActive
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive
                || terminalHistoryViewportActive
                || readModeSuppressesKeyboard) {
            return;
        }
        JSONObject probe = parseJavascriptObject(value);
        boolean missingTerminal = true;
        if (probe != null) {
            boolean hasTerm = probe.optBoolean("hasTerm", false);
            boolean hasCanvas = probe.optBoolean("hasCanvas", false);
            int canvasWidth = probe.optInt("canvasWidth", 0);
            int canvasHeight = probe.optInt("canvasHeight", 0);
            boolean hasError = probe.has("error");
            // WHY: ttyd is forced to xterm's canvas renderer for Android
            // performance. Canvas output can be visibly painted while DOM text is
            // empty, so `textLength == 0` must never trigger a reload by itself.
            // That false positive caused random "refresh" while reading output.
            missingTerminal = hasError || (!hasTerm && !hasCanvas)
                    || (hasTerm && hasCanvas && (canvasWidth <= 0 || canvasHeight <= 0));
        }
        if (!missingTerminal) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBlankTerminalReloadAtMs < 12000) {
            return;
        }
        lastBlankTerminalReloadAtMs = now;
        long reloadGeneration = markTerminalLoadStarted();
        toast("Reconnecting terminal");
        webView.loadUrl(webView.getUrl() == null ? TERMINAL_URL : webView.getUrl());
        uiHandler.postDelayed(() -> focusTerminalInputSoon(), 900);
        uiHandler.postDelayed(() -> verifyTerminalPainted("blank-watchdog-" + reason, reloadGeneration), 3200);
    }

    private long markTerminalLoadStarted() {
        long now = System.currentTimeMillis();
        if (now <= lastTerminalLoadAtMs) {
            now = lastTerminalLoadAtMs + 1;
        }
        lastTerminalLoadAtMs = now;
        // WHY: WebView load/reload creates a new ttyd DOM and xterm helper
        // textarea. Any delayed focus, visibility, or watchdog callback from the
        // previous page can now target stale DOM and reopen/resize Samsung IME
        // while the user is already typing in the new page. Bump all generations
        // and clear burst timers before every real load so only callbacks tied to
        // the newest transport can move focus or trigger another watchdog reload.
        terminalFocusGeneration++;
        liveInputVisibilityGeneration++;
        lastTerminalFocusBurstAtMs = 0;
        lastTerminalFocusBurstModeGeneration = -1;
        lastLiveInputVisibilityBurstAtMs = 0;
        lastLiveInputVisibilityBurstModeGeneration = -1;
        return now;
    }

    private JSONObject parseJavascriptObject(String value) {
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            return null;
        }
        try {
            return new JSONObject(value);
        } catch (Exception ignored) {
            try {
                String unquoted = new JSONArray("[" + value + "]").getString(0);
                return new JSONObject(unquoted);
            } catch (Exception nested) {
                return null;
            }
        }
    }

    private void getJson(String path, JsonCallback callback) {
        getJson(path, callback, null);
    }

    private void getJson(String path, JsonCallback callback, FailureCallback failureCallback) {
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
                uiHandler.post(() -> {
                    if (failureCallback != null) {
                        failureCallback.onFailure(exc);
                    } else {
                        toast("WEzterm control is not reachable");
                    }
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private void postText(
            String path,
            String text,
            JsonCallback callback,
            FailureCallback failureCallback
    ) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(CONTROL_URL + path);
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bytes);
                }
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
                uiHandler.post(() -> {
                    if (failureCallback != null) {
                        failureCallback.onFailure(exc);
                    } else {
                        toast("WEzterm control is not reachable");
                    }
                });
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
                lastImeInsetBottom = ime.bottom;
                bottom = Math.max(bars.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                lastImeInsetBottom = 0;
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
            keepLiveInputVisibleSoon("page-finished");
        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            super.onScaleChanged(view, oldScale, newScale);
            webViewScale = newScale;
            allowViewerPanBriefly();
            // WHY: WebView scale is the Android viewer zoom. Do not translate this
            // into ttyd/tmux font changes or tmux resize commands; one-finger
            // history remains tmux-owned and two-finger positioning stays viewer-owned.
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
