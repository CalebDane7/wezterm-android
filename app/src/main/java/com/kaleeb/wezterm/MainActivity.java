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
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
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
    private static final String APP_VERSION_NAME = "1.67";
    private static final int TERMINAL_INPUT_TYPE = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_NORMAL
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    private static final int TERMINAL_IME_OPTIONS = EditorInfo.IME_ACTION_SEND
            | EditorInfo.IME_FLAG_NO_FULLSCREEN;
    private static final int DEFAULT_FONT_SIZE = 12;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 18;
    private static final int TOOLBAR_HEIGHT_DP = 108;
    private static final long HISTORY_DRAG_THROTTLE_MS = 16;
    private static final int HISTORY_DRAG_LINE_THRESHOLD_DP = 8;
    private static final int HISTORY_DRAG_PAGES_PER_STEP = 1;
    private static final int HISTORY_DRAG_MAX_PAGES_PER_STEP = 20;
    private static final int HISTORY_DRAG_DOWN_MAX_REPEATS = 20;
    private static final int HISTORY_DRAG_DOWN_RELEASE_MAX_REPEATS = 20;
    private static final int HISTORY_DRAG_RELEASE_FLING_BURSTS = 2;
    private static final int TOUCH_SCROLL_LIVE_BOTTOM_SNAP_LINES = 16;
    private static final float HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC = 1200f;
    private static final float HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC = 2600f;
    private static final float WEBVIEW_ZOOMED_SCALE_THRESHOLD = 1.02f;
    private static final long VIEWER_PAN_UNLOCK_MS = 2500;
    private static final long TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS = 700;
    private static final long LIVE_INPUT_VISIBILITY_BURST_MIN_INTERVAL_MS = 220;
    private static final int CONTROL_SAFE_RETRY_ATTEMPTS = 3;
    private static final int CONTROL_SAFE_RETRY_DELAY_MS = 140;
    private static final int REQUEST_UPLOAD_MEDIA = 5201;
    private static final long MAX_MEDIA_UPLOAD_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MEDIA_UPLOAD_STREAM_CHUNK_BYTES = 1024 * 1024;
    private WebView webView;
    private View historyTouchOverlay;
    private LinearLayout promptComposerBar;
    private EditText promptComposerInput;
    private Button startToolbarButton;
    private SharedPreferences prefs;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long lastReconnectReloadAtMs = 0;
    private long lastBlankTerminalReloadAtMs = 0;
    private long lastTerminalLoadAtMs = 0;
    private long lastViewportPinAtMs = 0;
    private long lastTerminalFocusBurstAtMs = 0;
    private long lastTerminalFocusBurstModeGeneration = -1;
    private boolean lastTerminalFocusBurstRequestedKeyboard = false;
    private long terminalFocusGeneration = 0;
    private long lastLiveInputVisibilityBurstAtMs = 0;
    private long lastLiveInputVisibilityBurstModeGeneration = -1;
    private long liveInputVisibilityGeneration = 0;
    private long nativePickerQuietUntilMs = 0;
    private boolean readModeSuppressesKeyboard = false;
    private boolean terminalHistoryViewportActive = false;
    private boolean terminalTouchStartedInHistoryViewport = false;
    private boolean liveRestoreInFlight = false;
    private boolean terminalBottomRestoreInFlight = false;
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
    private boolean terminalForwardingTouchToViewer = false;
    private MotionEvent terminalViewerDownEvent = null;
    private boolean historyScrollRequestInFlight = false;
    private String pendingHistoryScrollWhere = "";
    private int pendingHistoryScrollRepeats = 0;
    private long pendingHistoryScrollGeneration = 0;
    private long terminalTouchGestureGeneration = 0;
    private long lastHistoryDragAtMs = 0;
    private long terminalLastHistoryDragEventAtMs = 0;
    private float webViewScale = 1.0f;
    private long viewerPanUnlockedUntilMs = 0;
    private int lastImeInsetBottom = 0;
    private boolean sessionSwitchInFlight = false;
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
        webView = new TerminalWebView(this);
        configureWebView(webView);
        setContentView(buildLayout(webView));
        if (getIntent().getBooleanExtra("pin_shortcut", false)) {
            requestHomeShortcutOnce();
        }
        loadTerminal();
        handleIncomingMediaShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingMediaShare(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UPLOAD_MEDIA) {
            nativePickerQuietUntilMs = System.currentTimeMillis() + 8000;
        }
        if (requestCode == REQUEST_UPLOAD_MEDIA && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                uri = data.getClipData().getItemAt(0).getUri();
            }
            if (uri == null) {
                toast("No media selected");
                return;
            }
            uploadMediaUri(uri, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
            focusTerminalInputSoon(false);
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
            focusTerminalInputSoon(false);
            scheduleBlankTerminalWatchdog("window-focus");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isDockedPromptComposerVisible()) {
            // WHY: the docked composer intentionally has no extra Cancel button.
            // Android Back is the standard way to dismiss a focused text field, and
            // keeping dismissal here prevents a second row of hidden-looking buttons
            // from returning under the toolbar.
            hideDockedPromptComposer(true, false);
            return true;
        }
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
        promptComposerBar = buildPromptComposer();
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
        root.addView(promptComposerBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)
        ));
        return root;
    }

    private LinearLayout buildPromptComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setVisibility(View.GONE);
        composer.setPadding(dp(8), dp(6), dp(8), dp(6));
        composer.setBackgroundColor(Color.rgb(24, 24, 37));

        promptComposerInput = new EditText(this);
        promptComposerInput.setSingleLine(false);
        promptComposerInput.setMinLines(1);
        promptComposerInput.setMaxLines(4);
        promptComposerInput.setTextSize(16);
        promptComposerInput.setTextColor(Color.rgb(205, 214, 244));
        promptComposerInput.setHintTextColor(Color.rgb(127, 132, 156));
        promptComposerInput.setHint("Type prompt - Start sends");
        promptComposerInput.setContentDescription("Type prompt");
        promptComposerInput.setInputType(TERMINAL_INPUT_TYPE);
        promptComposerInput.setImeOptions(TERMINAL_IME_OPTIONS);
        promptComposerInput.setGravity(android.view.Gravity.CENTER_VERTICAL);
        promptComposerInput.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(Color.rgb(30, 30, 46));
        inputBackground.setCornerRadius(dp(6));
        promptComposerInput.setBackground(inputBackground);
        promptComposerInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitDockedPrompt();
                return true;
            }
            return false;
        });
        // WHY: do not add another Send/Cancel row below the existing toolbar. That
        // looked like hidden duplicate controls and made users think text was not
        // going into the terminal. The existing thumb-side Start button becomes Send
        // while this native composer is open, so there is one visible action path.
        composer.addView(promptComposerInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        ));

        return composer;
    }

    private LinearLayout bottomBar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(6), dp(5), dp(6), dp(5));
        toolbar.setMinimumHeight(dp(TOOLBAR_HEIGHT_DP));
        toolbar.setBackgroundColor(Color.rgb(24, 24, 37));
        LinearLayout topRow = toolbarRow();
        LinearLayout bottomRow = toolbarRow();
        topRow.addView(toolbarButton("Active", v -> showActiveSessions()));
        topRow.addView(toolbarButton("Old", v -> showOldSessions()));
        topRow.addView(toolbarButton("New", v -> control("/new?fast=1", "New session opened")));
        // WHY: during upgrades, the user should not have to close the Android
        // task or hunt for the same tmux tab just to refresh the terminal
        // transport. This button preserves the current tmux window, returns it
        // to live bottom, reloads only the WebView/ttyd connection, and focuses
        // xterm again.
        topRow.addView(toolbarButton("Refresh", v -> refreshTerminalTransport()));
        // WHY: live-bottom is the user's most frequent recovery when one-finger
        // return-to-bottom still misses the final prompt line. Keep it as a one-tap
        // button on the right side of the top row instead of burying it in the
        // fallback Scroll dialog. This must call the fast `/live-bottom` path, not
        // the heavy proof `/scroll?where=bottom` path that caused refresh loops.
        topRow.addView(toolbarButton("Bottom", v -> goLiveBottom()));
        // WHY: v1.33 removed visible Live/Read/View access and stranded the
        // proven scrollback/top/reader recovery paths behind an uncalled method.
        // Keep one plain Scroll entry on the main bar so one-finger gesture bugs,
        // Codex transcript pager drift, or keyboard focus failures never leave
        // the phone with no way back to history top, page movement, or reader mode.
        Button scrollButton = toolbarButton("Scroll", v -> showViewControls());
        scrollButton.setOnLongClickListener(v -> {
            // WHY: Scroll must stay a small scroll-only fallback. The command
            // palette remains available for less common actions, but hiding it
            // behind long-press prevents the old giant "Terminal Controls" menu
            // from returning when the user only wants live bottom/top/page moves.
            showCommandPalette();
            return true;
        });
        bottomRow.addView(scrollButton);
        // WHY: phone paste must be a first-class action, not a keyboard long-press
        // trick. The button opens explicit copy/paste controls backed by Android
        // clipboard APIs and tmux paste buffers, so prompts can be moved between
        // phone apps and the exact active desktop pane.
        Button copyPasteButton = toolbarButton("Copy/Paste", v -> showCopyPasteControls());
        copyPasteButton.setOnLongClickListener(v -> {
            // WHY: upload is also available as its own toolbar button, but a
            // long-press here preserves muscle memory for "send phone content into
            // this terminal" without hiding the older copy/paste menu.
            pickMediaForUpload();
            return true;
        });
        bottomRow.addView(copyPasteButton);
        // WHY: screenshots, videos, PDFs, and other reference files need a one-tap
        // path from the app chrome itself. Keeping Upload separate from Copy/Paste
        // avoids burying the fastest media path in a dialog while preserving every
        // existing toolbar control that prior plan receipts protect.
        bottomRow.addView(toolbarButton("Upload", v -> pickMediaForUpload()));
        bottomRow.addView(toolbarButton("Close", v -> confirmClose()));
        // WHY: the user reported that a single smart combined button was not
        // predictable under pressure. Keep the two thumb-side actions separate:
        // Start always means "submit/send Enter", while Stop always means
        // "interrupt with Escape". Long-press Start opens the native composer so
        // a full prompt can be sent as one tmux paste when WebView/IME live typing
        // starts corrupting characters. Normal terminal-body taps now open the same
        // native typing layer instead of direct xterm IME input.
        startToolbarButton = toolbarButton("Start", v -> startCurrentTask());
        startToolbarButton.setOnLongClickListener(v -> {
            showSafePromptComposer();
            return true;
        });
        bottomRow.addView(startToolbarButton);
        bottomRow.addView(toolbarButton("Stop", v -> stopCurrentTask()));
        toolbar.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        toolbar.addView(bottomRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        return toolbar;
    }

    private LinearLayout toolbarRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));
        return row;
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        button.setTextSize(label.length() > 9 ? 10 : 11);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(44));
        button.setGravity(android.view.Gravity.CENTER);
        button.setPadding(dp(3), 0, dp(3), 0);
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
        setTouchableBackground(button, Color.rgb(49, 50, 68), Color.rgb(137, 180, 250));
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setOnClickListener(v -> {
            flashTap(v);
            if (listener != null) {
                listener.onClick(v);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void setTouchableBackground(View view, int baseColor, int rippleColor) {
        GradientDrawable base = new GradientDrawable();
        base.setColor(baseColor);
        base.setCornerRadius(dp(6));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), base, null));
        view.setHapticFeedbackEnabled(true);
    }

    private void flashTap(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        view.animate().cancel();
        view.setAlpha(0.72f);
        view.animate().alpha(1f).setDuration(140).start();
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        // WHY: Android's `stateVisible` is an activity-start keyboard request.
        // This app must not open or reconnect by creating an IME/editor session;
        // only a deliberate tap, paste, safe prompt, or Start/Stop flow may ask
        // for terminal typing focus. Keeping adjustResize alone preserves the
        // "keyboard stays below the visible composer" layout without restarting
        // Samsung/Gboard composition when WEzterm resumes.
        window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
            // ACTION_DOWN is consumed so one-finger vertical scroll can stay tmux
            // owned from the start; when a second finger appears, replay the saved
            // DOWN into WebView before forwarding this event so native pinch zoom
            // has a complete gesture stream.
            if (!terminalMultiTouchGesture) {
                // WHY: a second finger changes ownership from tmux history to the
                // Android/WebView viewer. Any outstanding one-finger scroll response
                // now belongs to an old gesture and must not later mark live-bottom
                // or replay queued movement after the user started zooming/panning.
                terminalTouchGestureGeneration++;
                terminalTouchReachedLiveBottom = false;
                clearPendingHistoryScroll();
            }
            terminalMultiTouchGesture = true;
            terminalHistoryDragActive = false;
            allowViewerPanBriefly();
            return forwardTouchToViewer(event);
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
            terminalBottomRestoreInFlight = false;
            terminalForwardingTouchToViewer = false;
            recycleTerminalViewerDownEvent();
            terminalViewerDownEvent = MotionEvent.obtain(event);
            terminalTouchReachedLiveBottom = false;
            terminalTouchStartedInHistoryViewport = terminalHistoryViewportActive || readModeSuppressesKeyboard;
            // WHY: touch-scroll HTTP responses can arrive after the finger has
            // already changed direction, released, or started a new gesture. Tagging
            // every request with this generation keeps stale server replies from
            // triggering the bottom restore/refresh jump on a later gesture.
            terminalTouchGestureGeneration++;
            clearPendingHistoryScroll();
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
            // WHY: one-finger vertical history must be owned by WEzterm/tmux from
            // ACTION_DOWN. Letting WebView receive DOWN and then stealing MOVE for
            // `/touch-scroll` starts native document scroll, which the viewport pin
            // fights with `scrollTo(0,0)`, producing the repeated up/down refresh
            // loop. Taps, one-finger horizontal pan, and two-finger zoom are
            // forwarded back to WebView only after they are classified.
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            addTerminalMovement(event);
            if (terminalMultiTouchGesture) {
                return forwardTouchToViewer(event);
            }
            if (terminalHorizontalPanActive) {
                return forwardTouchToViewer(event);
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
                return forwardTouchToViewer(event);
            }
            if (!terminalHistoryDragActive) {
                if (absDy < terminalTouchSlop || absDy < absDx * 1.2f) {
                    return true;
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
            boolean wasForwardingTouchToViewer = terminalForwardingTouchToViewer;
            if (action == MotionEvent.ACTION_UP && terminalHistoryDragActive) {
                dispatchHistoryReleaseFling(event);
            }
            boolean shouldRestoreTyping = action == MotionEvent.ACTION_UP
                    && startedInHistoryViewport
                    && !terminalHistoryDragActive
                    && !wasMultiTouch
                    && !wasHorizontalPan
                    && !movedPastTapSlop;
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
            if (wasForwardingTouchToViewer) {
                forwardTouchToViewer(event);
                recycleTerminalViewerDownEvent();
                return true;
            }
            if (shouldRestoreTyping) {
                restoreLiveForTyping("Typing ready");
                recycleTerminalViewerDownEvent();
                return true;
            }
            if (action == MotionEvent.ACTION_UP && reachedLiveBottom) {
                // WHY: the finger has reached tmux scroll position 0, but tmux is
                // still in copy-mode unless Android explicitly exits it. v1.56
                // avoided the old refresh loop by doing nothing here, but that left
                // the phone stranded at a `[0/N]` history footer instead of the live
                // composer. Use the lightweight tmux-only touch endpoint to cancel
                // copy-mode, and deliberately skip WebView reload, xterm
                // scrollToBottom, scrollIntoView, and IME focus helpers that caused
                // the repeated page-refresh/snap regression.
                restoreTouchLiveBottomQuietly();
                recycleTerminalViewerDownEvent();
                return true;
            }
            if (action == MotionEvent.ACTION_UP
                    && !startedInHistoryViewport
                    && !consumed
                    && !wasMultiTouch
                    && !wasHorizontalPan
                    && !movedPastTapSlop) {
                if (isTopTerminalTap(event)) {
                    // WHY: the tmux status/tab strip still needs the original
                    // browser tap stream so the user can use the top tab targets
                    // when zoomed in. Do not also request Android IME focus here:
                    // top-strip taps are navigation, not typing, and a second
                    // delayed editor focus can recommit Samsung/Gboard composition.
                    forwardTapToViewer(event);
                } else {
                    // WHY: terminal body taps open the native composer instead of
                    // focusing xterm's hidden textarea. Android Chrome/WebView plus
                    // xterm.js has a known mobile composition failure mode where the
                    // same composed word can be committed twice. WEzTerm's phone UI
                    // gives long-form typing to a native EditText composer and submits
                    // the finished prompt through the control server's single
                    // paste+Enter path. The top tmux/status strip still receives
                    // WebView taps.
                    showDockedPromptComposer("tap-up");
                }
            }
            recycleTerminalViewerDownEvent();
            return true;
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

    private boolean forwardTouchToViewer(MotionEvent event) {
        if (webView == null) {
            return true;
        }
        beginForwardingTouchToViewer();
        webView.onTouchEvent(event);
        return true;
    }

    private void beginForwardingTouchToViewer() {
        if (terminalForwardingTouchToViewer || webView == null) {
            return;
        }
        terminalForwardingTouchToViewer = true;
        if (terminalViewerDownEvent != null) {
            // WHY: WEzterm consumes ACTION_DOWN until it knows whether the gesture
            // is vertical tmux scroll, a tap, horizontal viewer pan, or pinch. When
            // the gesture is not tmux-owned, replay the original DOWN into WebView
            // before forwarding MOVE/UP so top tmux tab clicks and Android zoom/pan
            // still see a complete touch stream.
            webView.onTouchEvent(terminalViewerDownEvent);
        }
    }

    private void forwardTapToViewer(MotionEvent upEvent) {
        beginForwardingTouchToViewer();
        if (webView != null) {
            webView.onTouchEvent(upEvent);
        }
    }

    private boolean isTopTerminalTap(MotionEvent event) {
        // WHY: only the tmux/ttyd chrome near the top needs a real WebView tap
        // after WEzterm consumes ACTION_DOWN for gesture classification. Terminal
        // body taps are typing-focus requests, so forwarding them to xterm and then
        // running a native focus fallback gives Android two competing IME owners.
        return event.getY() <= dp(48);
    }

    private void recycleTerminalViewerDownEvent() {
        if (terminalViewerDownEvent != null) {
            terminalViewerDownEvent.recycle();
            terminalViewerDownEvent = null;
        }
        terminalForwardingTouchToViewer = false;
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
        String where = totalDy > 0 ? "lineUp" : "lineDown";
        if ("lineDown".equals(where)) {
            // WHY: upward flicks are for racing through old output, so a large burst
            // is useful there. Downward flicks are the return-to-live path. v1.58's
            // tiny downward cap made real phone swipes stall in copy-mode many
            // lines above the prompt. Match the server-supported touch batch so a
            // deliberate thumb flick can reach the live composer, but keep it
            // single-burst so it cannot schedule a second bottom-edge bounce.
            repeats = Math.min(repeats, HISTORY_DRAG_DOWN_RELEASE_MAX_REPEATS);
            fullFling = false;
        }
        // WHY: real fast flicks produce fewer ACTION_MOVE samples than slow drags,
        // especially through WebView and ADB input. v1.44 therefore moved fewer
        // lines for a fast flick than for a slow drag of the same distance. Add
        // one bounded release burst based on total gesture velocity so fast flicks
        // jump farther while slow finger movement remains line-by-line.
        final String flingWhere = where;
        final int flingRepeats = repeats;
        final long flingGeneration = terminalTouchGestureGeneration;
        scrollTerminalFromTouch(flingWhere, flingRepeats);
        if (fullFling) {
            // WHY: if a fast flick releases while a MOVE request is still in
            // flight, the first release burst is intentionally coalesced into one
            // pending batch. A second short-delay burst gives real fling velocity
            // the extra distance users expect without affecting slow drags. Tie
            // it to the same touch generation and suppress it after a pinch so a
            // user can zoom the stopped history section without a delayed line
            // scroll moving the text underneath their fingers.
            uiHandler.postDelayed(() -> {
                if (flingGeneration == terminalTouchGestureGeneration
                        && !terminalMultiTouchGesture
                        && terminalHistoryViewportActive
                        && readModeSuppressesKeyboard) {
                    scrollTerminalFromTouch(flingWhere, flingRepeats);
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
        int maxRepeats = "lineDown".equals(where)
                ? HISTORY_DRAG_DOWN_MAX_REPEATS
                : HISTORY_DRAG_MAX_PAGES_PER_STEP;
        int boundedRepeats = Math.max(1, Math.min(maxRepeats, repeats));
        long gestureGeneration = terminalTouchGestureGeneration;
        if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
            return;
        }
        if ("lineUp".equals(where)) {
            terminalTouchReachedLiveBottom = false;
        }
        if (historyScrollRequestInFlight) {
            if (where.equals(pendingHistoryScrollWhere)
                    && pendingHistoryScrollGeneration == gestureGeneration) {
                pendingHistoryScrollRepeats = Math.min(
                        maxRepeats,
                        pendingHistoryScrollRepeats + boundedRepeats
                );
            } else {
                pendingHistoryScrollWhere = where;
                pendingHistoryScrollRepeats = boundedRepeats;
                pendingHistoryScrollGeneration = gestureGeneration;
            }
            return;
        }
        sendHistoryScrollFromTouch(where, boundedRepeats, gestureGeneration);
    }

    private void sendHistoryScrollFromTouch(String where, int repeats, long gestureGeneration) {
        long readModeGeneration = terminalHistoryViewportActive
                ? terminalModeGeneration
                : enterReadMode();
        historyScrollRequestInFlight = true;
        String path = "/touch-scroll?where=" + urlEncode(where)
                + "&repeat=" + Math.max(1, repeats);
        getJson(path, payload -> {
            historyScrollRequestInFlight = false;
            if (gestureGeneration != terminalTouchGestureGeneration) {
                // WHY: delayed `/scroll` responses are expected on a mobile network.
                // They may still be valid tmux commands, but they no longer describe
                // the user's current finger gesture. Do not let them mark live-bottom
                // or keep the terminal in read mode for a newer touch/zoom/tap.
                drainPendingHistoryScroll();
                return;
            }
            if ("lineDown".equals(where) && touchScrollReachedLiveBottom(payload)) {
                // WHY: the server has reached tmux's real live bottom. During
                // ACTION_MOVE this must remain a stop signal, not a live-typing
                // restore, or the screen flips to the prompt before the user's
                // finger has finished scrolling down. Clear queued down-scrolls
                // and keep read-mode until ACTION_UP performs one explicit bottom
                // restore. v1.60 proved a fast physical return swipe could land a
                // a short bottom band and strand the phone in copy-mode, so
                // `touchScrollReachedLiveBottom` also treats a tiny tmux lineDown
                // remainder as the bottom edge and restores quietly if the finger
                // has already lifted.
                terminalTouchReachedLiveBottom = true;
                clearPendingHistoryScroll();
                if (!terminalHistoryDragActive) {
                    restoreTouchLiveBottomQuietly();
                    return;
                }
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
        return (payload.optBoolean("atLiveBottom", false)
                && "tmux".equals(payload.optString("layer", ""))
                && "tmux-linedown".equals(payload.optString("action", "")))
                || isNearTmuxLiveBottom(payload);
    }

    private boolean isNearTmuxLiveBottom(JSONObject payload) {
        if (payload == null
                || !"tmux".equals(payload.optString("layer", ""))
                || !"tmux-linedown".equals(payload.optString("action", ""))) {
            return false;
        }
        if (!payload.has("scrollPosition")) {
            return false;
        }
        int scrollPosition = payload.optInt("scrollPosition", Integer.MAX_VALUE);
        return scrollPosition >= 0 && scrollPosition <= TOUCH_SCROLL_LIVE_BOTTOM_SNAP_LINES;
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
        long nextGeneration = pendingHistoryScrollGeneration;
        clearPendingHistoryScroll();
        if (nextGeneration != terminalTouchGestureGeneration) {
            return;
        }
        sendHistoryScrollFromTouch(nextWhere, Math.max(1, nextRepeats), nextGeneration);
    }

    private void clearPendingHistoryScroll() {
        pendingHistoryScrollWhere = "";
        pendingHistoryScrollRepeats = 0;
        pendingHistoryScrollGeneration = 0;
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
        focusTerminalInputSoon(false);
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
        long generation = leaveReadModeForLiveInput(false);
        getJsonWithRetry("/fix-view", payload -> {
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
            client.put("appVersion", APP_VERSION_NAME);
            client.put("webViewUrl", webView == null ? "" : webView.getUrl());
            client.put("readMode", readModeSuppressesKeyboard);
            client.put("historyViewport", terminalHistoryViewportActive);
            client.put("nativeComposerVisible", promptComposerBar != null
                    && promptComposerBar.getVisibility() == View.VISIBLE);
            client.put("lastTerminalLoadAtMs", lastTerminalLoadAtMs);
            client.put("webViewScale", webViewScale);
            client.put("webViewScrollX", webView == null ? 0 : webView.getScrollX());
            client.put("webViewScrollY", webView == null ? 0 : webView.getScrollY());
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

    private void startCurrentTask() {
        if (promptComposerBar != null
                && promptComposerBar.getVisibility() == View.VISIBLE
                && promptComposerInput != null) {
            if (promptComposerInput.getText().toString().trim().length() == 0) {
                hideDockedPromptComposer(true, false);
                return;
            }
            // WHY: Start sits under the user's thumb and now doubles as "send the
            // visible native composer" when that composer is open. Otherwise the
            // user could type a full prompt safely, tap the obvious Start button,
            // and send only a raw Enter to xterm while leaving the prompt unsent.
            submitDockedPrompt();
            return;
        }
        // WHY: Start is deliberately not "smart". It sends Enter regardless of
        // whether the pane looks idle or running, matching the desktop habit of
        // pressing Enter to submit the prompt currently visible in the composer.
        // Stop remains a separate explicit interrupt button beside it.
        sendEnterToTerminal();
    }

    private void stopCurrentTask() {
        control("/stop", "Stop sent", true);
    }

    private void sendEnterToTerminal() {
        long generation = leaveReadModeForLiveInput();
        getJson("/send-enter", payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Send failed"));
                return;
            }
            toast("Sent");
            hideDockedPromptComposer(false, true);
            focusTerminalInputSoon(false);
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void showSafePromptComposer() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(8);
        // WHY: the native prompt must support phone dictation. v1.54 forced
        // visible-password and no-personalized-learning flags to fight
        // autocorrect, but Samsung/Gboard can treat those as private/incognito
        // entry and remove the mic. Keep the controlled paste+Enter submit path
        // for correctness, while advertising normal visible text to the IME so
        // voice input stays available.
        input.setInputType(TERMINAL_INPUT_TYPE);
        input.setImeOptions(TERMINAL_IME_OPTIONS);
        int pad = dp(10);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Type prompt")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) -> {
                    String text = input.getText().toString();
                    submitSafePrompt(text);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDockedPromptComposer(String reason) {
        if (promptComposerBar == null || promptComposerInput == null) {
            showSafePromptComposer();
            return;
        }
        // WHY: this native composer is the default phone typing surface. It avoids
        // the fragile Android WebView/xterm hidden-textarea IME path that repeatedly
        // duplicated Samsung/Gboard/voice input in real use, while keeping voice
        // dictation available through normal visible text flags.
        hideHistoryTouchOverlayQuietly();
        terminalHistoryViewportActive = false;
        readModeSuppressesKeyboard = false;
        terminalModeGeneration++;
        terminalTouchGestureGeneration++;
        terminalFocusGeneration++;
        liveInputVisibilityGeneration++;
        promptComposerBar.setVisibility(View.VISIBLE);
        updateStartButtonLabel();
        promptComposerInput.requestFocus();
        promptComposerInput.setSelection(promptComposerInput.getText().length());
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.showSoftInput(promptComposerInput, InputMethodManager.SHOW_IMPLICIT);
        }
        scrollViewerToTypingPosition();
    }

    private void submitDockedPrompt() {
        if (promptComposerInput == null) {
            return;
        }
        String text = promptComposerInput.getText().toString();
        submitSafePrompt(text);
    }

    private void hideDockedPromptComposer(boolean clearText, boolean keepKeyboardState) {
        if (promptComposerBar == null || promptComposerInput == null) {
            return;
        }
        if (clearText) {
            promptComposerInput.setText("");
        }
        promptComposerInput.clearFocus();
        promptComposerBar.setVisibility(View.GONE);
        updateStartButtonLabel();
        if (!keepKeyboardState) {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(promptComposerInput.getWindowToken(), 0);
            }
        }
    }

    private void updateStartButtonLabel() {
        if (startToolbarButton == null || promptComposerBar == null) {
            return;
        }
        startToolbarButton.setText(promptComposerBar.getVisibility() == View.VISIBLE ? "Send" : "Start");
    }

    private boolean isDockedPromptComposerVisible() {
        return promptComposerBar != null && promptComposerBar.getVisibility() == View.VISIBLE;
    }

    private void submitSafePrompt(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            toast("Prompt is empty");
            return;
        }
        long generation = leaveReadModeForLiveInput();
        postText("/submit-text", value, payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Send failed"));
                return;
            }
            toast("Prompt sent");
            hideDockedPromptComposer(true, false);
            focusTerminalInputSoon(false);
        }, exc -> toast("WEzterm control is not reachable"));
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
        // WHY: this is a normal user recovery/tap-to-type action, not a proof
        // capture. The full `/scroll?where=bottom` endpoint gathers visible pane
        // evidence and Android then ran xterm.scrollToBottom/scrollIntoView, which
        // created the rapid top/bottom refresh loop at the exact moment the user
        // wanted to type. `/live-bottom` uses the server's fast tmux/Codex/reader
        // live-return primitive and leaves WebView's transport alone.
        getJsonWithRetry("/live-bottom", payload -> {
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
            pinTerminalViewportLocal();
            showDockedPromptComposer("live-bottom");
        }, exc -> {
            liveRestoreInFlight = false;
            toast("WEzterm control is not reachable");
        });
    }

    private void restoreTouchLiveBottomQuietly() {
        if (terminalBottomRestoreInFlight) {
            return;
        }
        terminalBottomRestoreInFlight = true;
        long gestureGeneration = terminalTouchGestureGeneration;
        // WHY: this path is only for the end of a one-finger down-scroll. It must
        // exit tmux copy-mode so the real live bottom/composer is visible, but it
        // must not run the heavier `/scroll?where=bottom` recovery or reload/focus
        // helpers. Those helpers are still available through explicit Scroll ->
        // live bottom and Refresh, where a visible recovery jump is intentional.
        getJson("/touch-scroll?where=bottom&repeat=1", payload -> {
            terminalBottomRestoreInFlight = false;
            if (gestureGeneration != terminalTouchGestureGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Bottom restore failed"));
                return;
            }
            leaveReadModeAfterTouchBottom();
            pinTerminalViewportLocal();
        }, exc -> {
            terminalBottomRestoreInFlight = false;
            toast("WEzterm control is not reachable");
        });
    }

    private void leaveReadModeAfterTouchBottom() {
        // WHY: quiet touch-bottom restore is not a typing/focus command. It only
        // reconciles Android's read-mode overlay with tmux after the server exits
        // copy-mode. Calling the normal live-input method here would re-run
        // viewport pin JS, xterm focus probes, IME show calls, and scrollIntoView,
        // recreating the bottom-edge refresh loop this path exists to avoid.
        terminalModeGeneration++;
        terminalHistoryViewportActive = false;
        terminalTouchStartedInHistoryViewport = false;
        terminalTouchReachedLiveBottom = false;
        readModeSuppressesKeyboard = false;
        clearPendingHistoryScroll();
        hideHistoryTouchOverlayQuietly();
    }

    private long leaveReadModeForLiveInput() {
        return leaveReadModeForLiveInput(true);
    }

    private long leaveReadModeForLiveInput(boolean pinAfterOverlay) {
        long generation = ++terminalModeGeneration;
        terminalHistoryViewportActive = false;
        terminalTouchStartedInHistoryViewport = false;
        terminalTouchReachedLiveBottom = false;
        terminalTouchGestureGeneration++;
        readModeSuppressesKeyboard = false;
        clearPendingHistoryScroll();
        if (pinAfterOverlay) {
            hideHistoryTouchOverlay();
        } else {
            // WHY: tap-to-type and Scroll -> live-bottom already wait for the
            // server's fast `/live-bottom` recovery. Running the full document
            // scroll pin here, before that recovery finishes, made the phone look
            // like it was refreshing up/down when the user tapped to place the
            // cursor. The success callback still does the cheap local pin.
            hideHistoryTouchOverlayQuietly();
        }
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
        hideHistoryTouchOverlayQuietly();
        pinTerminalViewportSoon("leave-read-mode");
    }

    private void hideHistoryTouchOverlayQuietly() {
        if (historyTouchOverlay != null) {
            historyTouchOverlay.setVisibility(View.GONE);
        }
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
        if (isTerminalGestureRecoveryActive()) {
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
            if (webView != null && !isTerminalGestureRecoveryActive()) {
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
        String[] labels = new String[]{
                "Go to live bottom / type",
                "Go to history top",
                "Read current session",
                "Page up",
                "Page down"
        };
        new AlertDialog.Builder(this)
                .setTitle("Scroll")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        goLiveBottom();
                    } else if (which == 1) {
                        enterReadMode();
                        scrollTerminal("top", "History top", false);
                    } else if (which == 2) {
                        enterReadMode();
                        control("/read-session", "Session reader", false);
                    } else if (which == 3) {
                        enterReadMode();
                        scrollTerminal("pageUp", "Page up", false);
                    } else if (which == 4) {
                        enterReadMode();
                        scrollTerminal("pageDown", "Page down", false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCommandPalette() {
        String[] labels = new String[]{
                "Active Sessions",
                "Old Sessions",
                "Refresh current session",
                "Needs Attention",
                "Copy/Paste",
                "Type prompt safely",
                "Upload media from phone",
                "Go to live bottom / type",
                "Start / send Enter",
                "Go to history top",
                "Read current session",
                "Page up",
                "Page down",
                "Install/update over Tailscale",
                "Create bug report",
                "Stop current task",
                "Rename current session"
        };
        new AlertDialog.Builder(this)
                .setTitle("Command palette")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        showActiveSessions();
                    } else if (which == 1) {
                        showOldSessions();
                    } else if (which == 2) {
                        refreshTerminalTransport();
                    } else if (which == 3) {
                        showNeedsAttention();
                    } else if (which == 4) {
                        showCopyPasteControls();
                    } else if (which == 5) {
                        showSafePromptComposer();
                    } else if (which == 6) {
                        pickMediaForUpload();
                    } else if (which == 7) {
                        goLiveBottom();
                    } else if (which == 8) {
                        startCurrentTask();
                    } else if (which == 9) {
                        enterReadMode();
                        scrollTerminal("top", "History top", false);
                    } else if (which == 10) {
                        openFullSessionReader();
                    } else if (which == 11) {
                        enterReadMode();
                        scrollTerminal("pageUp", "Page up", false);
                    } else if (which == 12) {
                        enterReadMode();
                        scrollTerminal("pageDown", "Page down", false);
                    } else if (which == 13) {
                        openInstallPage();
                    } else if (which == 14) {
                        createBugReport();
                    } else if (which == 15) {
                        stopCurrentTask();
                    } else if (which == 16) {
                        showRenameCurrentTab();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCopyPasteControls() {
        String[] labels = new String[]{
                "Paste phone clipboard into terminal",
                "Copy visible terminal text",
                "Type prompt safely",
                "Upload media from phone"
        };
        new AlertDialog.Builder(this)
                .setTitle("Copy/Paste")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        pasteClipboardIntoTerminal();
                    } else if (which == 1) {
                        copyVisibleTerminalToClipboard();
                    } else if (which == 2) {
                        showSafePromptComposer();
                    } else if (which == 3) {
                        pickMediaForUpload();
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
        String clipboardText = clipboardTextFromClip(clip);
        if (clipboardText.isEmpty()) {
            toast("Phone clipboard is empty");
            return;
        }
        // WHY: pasting is a live-input action. Clear local read-mode flags before
        // the server forces tmux/Codex to live bottom, or delayed read-mode focus
        // guards can hide the keyboard immediately after the paste.
        long generation = leaveReadModeForLiveInput();
        postText("/paste", clipboardText, payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Paste failed"));
                return;
            }
            toast("Pasted");
            hideDockedPromptComposer(false, true);
            focusTerminalInputSoon(false);
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private String clipboardTextFromClip(ClipData clip) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < clip.getItemCount(); index++) {
            ClipData.Item item = clip.getItemAt(index);
            if (item == null) {
                continue;
            }
            // WHY: Android clipboards may carry multiple items, and some apps expose
            // raw text while others expose URI-backed or styled text. Reading only
            // item 0 made phone paste look like it dropped everything after the first
            // selected fragment. Prefer raw text when present, then use Android's
            // documented coercion path for the rest, preserving spaces/newlines and
            // joining multi-item clips with newlines before the tmux paste-buffer hop.
            CharSequence itemText = item.getText();
            if (itemText == null) {
                itemText = item.coerceToText(this);
            }
            if (itemText == null || itemText.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(itemText);
        }
        return builder.toString();
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

    private void pickMediaForUpload() {
        // WHY: screenshots/media should move from the phone to the desktop over
        // the same Tailscale control channel as Copy/Paste. ACTION_OPEN_DOCUMENT
        // gives WEzTerm one user-selected URI instead of broad storage access, so
        // Android does not need READ_MEDIA_* permissions and future agents cannot
        // turn this into a background gallery scraper.
        // WHY: launching Android's document picker pauses/resumes the Activity.
        // Treat the return as a native-dialog transition, not a terminal failure,
        // so the blank watchdog and passive page lifecycle probes cannot reload
        // or scroll the WebView while the user is trying to attach media.
        nativePickerQuietUntilMs = System.currentTimeMillis() + 8000;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/*",
                "video/*",
                "application/pdf",
                "text/*",
                "application/octet-stream"
        });
        try {
            startActivityForResult(intent, REQUEST_UPLOAD_MEDIA);
        } catch (Exception exc) {
            nativePickerQuietUntilMs = 0;
            toast("No Android file picker available");
        }
    }

    private void handleIncomingMediaShare(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                uploadMediaUri(uri, true);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris == null || uris.isEmpty()) {
                return;
            }
            toast("Uploading " + uris.size() + " files");
            for (Uri uri : uris) {
                if (uri != null) {
                    uploadMediaUri(uri, true);
                }
            }
        }
    }

    private void uploadMediaUri(Uri uri, boolean fromShare) {
        String displayName = queryMediaDisplayName(uri);
        String contentType = getContentResolver().getType(uri);
        if (contentType == null || contentType.trim().isEmpty()) {
            contentType = "application/octet-stream";
        }
        long declaredSize = queryMediaSize(uri);
        if (declaredSize > MAX_MEDIA_UPLOAD_BYTES) {
            toast("Media is too large for phone upload: " + humanBytes(declaredSize));
            return;
        }
        toast(fromShare ? "Uploading shared media" : "Uploading media");
        String finalContentType = contentType;
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(CONTROL_URL + "/upload-media?filename=" + urlEncode(displayName));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10 * 60 * 1000);
                connection.setRequestProperty("Content-Type", finalContentType);
                connection.setRequestProperty("X-WEzTerm-Filename", displayName);
                if (declaredSize >= 0) {
                    // WHY: Android's HttpURLConnection docs warn that POST bodies are
                    // buffered in memory unless a streaming mode is selected. For
                    // videos, fixed-length streaming is the fastest safe path when
                    // OpenableColumns.SIZE is available because it sends the selected
                    // URI straight to the desktop without building a giant byte[].
                    connection.setFixedLengthStreamingMode(declaredSize);
                } else {
                    // WHY: some content providers do not expose a size. Chunked
                    // streaming keeps the upload video-safe instead of falling back
                    // to HttpURLConnection's full-body memory buffer.
                    connection.setChunkedStreamingMode(MEDIA_UPLOAD_STREAM_CHUNK_BYTES);
                }
                long uploadedBytes;
                try (OutputStream outputStream = connection.getOutputStream()) {
                    uploadedBytes = streamUriToOutput(uri, outputStream);
                }
                if (uploadedBytes <= 0) {
                    throw new IllegalArgumentException("Selected media is empty");
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String body = readAll(stream);
                JSONObject payload = new JSONObject(body);
                uiHandler.post(() -> showUploadedMediaResult(payload));
            } catch (Exception exc) {
                uiHandler.post(() -> toast("Media upload failed: " + exc.getMessage()));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private long streamUriToOutput(Uri uri, OutputStream outputStream) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new IllegalArgumentException("Cannot open selected media");
        }
        try (InputStream input = inputStream) {
            byte[] buffer = new byte[MEDIA_UPLOAD_STREAM_CHUNK_BYTES];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MEDIA_UPLOAD_BYTES) {
                    throw new IllegalArgumentException("Media is too large for phone upload");
                }
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
            return total;
        }
    }

    private String queryMediaDisplayName(Uri uri) {
        String name = queryOpenableColumn(uri, OpenableColumns.DISPLAY_NAME);
        if (name == null || name.trim().isEmpty()) {
            String lastSegment = uri.getLastPathSegment();
            name = lastSegment == null || lastSegment.trim().isEmpty()
                    ? "phone-media"
                    : lastSegment;
        }
        return name;
    }

    private long queryMediaSize(Uri uri) {
        String value = queryOpenableColumn(uri, OpenableColumns.SIZE);
        if (value == null || value.trim().isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String queryOpenableColumn(Uri uri, String column) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{column}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(column);
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getString(index);
                }
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private void showUploadedMediaResult(JSONObject payload) {
        if (!payload.optBoolean("ok", false)) {
            toast(payload.optString("error", "Media upload failed"));
            return;
        }
        String path = payload.optString("path", "");
        if (path.isEmpty()) {
            toast("Media uploaded, but no path returned");
            return;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("WEzterm uploaded media path", path));
        }
        String filename = payload.optString("filename", "uploaded media");
        String message = filename + "\n" + humanBytes(payload.optLong("bytes", 0)) + "\n\n" + path;
        new AlertDialog.Builder(this)
                .setTitle("Uploaded media")
                .setMessage(message)
                .setPositiveButton("Paste path", (dialog, which) -> postText("/paste", path, pastePayload -> {
                    if (!pastePayload.optBoolean("ok", false)) {
                        toast(pastePayload.optString("error", "Paste path failed"));
                        return;
                    }
                    toast("Uploaded path pasted");
                    hideDockedPromptComposer(false, true);
                    focusTerminalInputSoon(false);
                }, exc -> toast("WEzterm control is not reachable")))
                .setNegativeButton("Copy path", (dialog, which) -> toast("Uploaded path copied"))
                .setNeutralButton("OK", null)
                .show();
    }

    private String humanBytes(long bytes) {
        if (bytes < 0) {
            return "unknown size";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format("%.1f KB", kib);
        }
        double mib = kib / 1024.0;
        if (mib < 1024) {
            return String.format("%.1f MB", mib);
        }
        return String.format("%.1f GB", mib / 1024.0);
    }

    private void showTabs() {
        showActiveSessions();
    }

    private void showActiveSessions() {
        getJsonWithRetry("/sessions", payload -> showActiveSessionsDialog(payload, "Active Sessions", true), exc ->
                getJsonWithRetry("/tabs", payload -> showActiveSessionsDialog(payload, "Active Sessions", false))
        );
    }

    private void showOldSessions() {
        getJsonWithRetry("/sessions", this::showOldSessionsDialog, exc ->
                toast("WEzterm control is not reachable")
        );
    }

    private void showNeedsAttention() {
        getJsonWithRetry("/needs-attention", payload -> showActiveSessionsDialog(payload, "Needs Attention", false));
    }

    private void showActiveSessionsDialog(JSONObject payload, String title, boolean preferGroups) throws Exception {
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
        header.setText("Active: " + viewSession + " (" + session + ")");
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
                .setPositiveButton("New session", (d, which) -> control("/new?fast=1", "New session"))
                .setNeutralButton("Rename current", (d, which) -> showRenameCurrentTab())
                .setNegativeButton("Cancel", null)
                .show();
        dialogRef[0] = dialog;
        // WHY: Android AlertDialog can focus a child row or preserve a measured
        // scroll position, which made Active Sessions open in the middle. The phone picker
        // is always a "start from the newest/attention section" surface.
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void showOldSessionsDialog(JSONObject payload) throws Exception {
        JSONArray oldSessions = payload.optJSONArray("oldSessions");
        if (oldSessions == null) {
            oldSessions = payload.optJSONArray("recentCodexSessions");
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFocusable(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setFocusable(false);
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        scrollView.addView(list);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        if (oldSessions == null || oldSessions.length() == 0) {
            addSectionHeader(list, "No old sessions found", 0);
        } else {
            String currentDate = "";
            for (int i = 0; i < oldSessions.length(); i++) {
                JSONObject session = oldSessions.getJSONObject(i);
                // WHY: old-session recovery is for user-owned parent Codex
                // threads. Showing subagent/explorer/worker threads here was
                // explicitly confusing because those are implementation helpers,
                // not sessions the user expects to resume from the phone.
                if (!"user".equals(session.optString("threadSource", "user"))) {
                    continue;
                }
                String dateLabel = session.optString("dateLabel", session.optString("updatedGroup", "Older"));
                if (!dateLabel.equals(currentDate)) {
                    currentDate = dateLabel;
                    addSectionHeader(list, currentDate, countOldSessionsForDate(oldSessions, currentDate));
                }
                addOldSessionRow(list, session, dialogRef);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Old Sessions")
                .setView(scrollView)
                .setPositiveButton("Active Sessions", (d, which) -> showActiveSessions())
                .setNegativeButton("Cancel", null)
                .show();
        dialogRef[0] = dialog;
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private int countOldSessionsForDate(JSONArray oldSessions, String dateLabel) throws Exception {
        int count = 0;
        for (int i = 0; i < oldSessions.length(); i++) {
            JSONObject session = oldSessions.getJSONObject(i);
            if (!"user".equals(session.optString("threadSource", "user"))) {
                continue;
            }
            String sessionDate = session.optString("dateLabel", session.optString("updatedGroup", "Older"));
            if (dateLabel.equals(sessionDate)) {
                count++;
            }
        }
        return count;
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
        setTouchableBackground(openPanel,
                window.optBoolean("active", false)
                        ? Color.rgb(69, 71, 90)
                        : Color.rgb(49, 50, 68),
                Color.rgb(137, 180, 250));
        View.OnClickListener openSessionClick = v -> {
            flashTap(v);
            selectTabForTyping(index, windowId, title, dialogRef);
        };
        openPanel.setClickable(true);
        openPanel.setOnClickListener(openSessionClick);

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
        // WHY: the full session title is now a long-press copy target. Android
        // child views with long-click listeners can intercept taps instead of
        // letting the parent card open the session, which made Active switching
        // feel like it needed 3-6 taps. Give every visible text/status target the
        // same one-tap open action while preserving long-press title copy.
        titleText.setClickable(true);
        titleText.setOnClickListener(openSessionClick);
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
        detailText.setClickable(true);
        detailText.setOnClickListener(openSessionClick);

        // WHY: users need to know whether a Codex tab is still actively working
        // before switching or closing it. The dot is driven by control-server
        // pane evidence, not by the mutable title string, so scanability improves
        // without changing the stable windowId close/select target.
        titleRow.addView(statusDot, new LinearLayout.LayoutParams(
                dp(18),
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        statusDot.setClickable(true);
        statusDot.setOnClickListener(openSessionClick);
        titleRow.addView(titleText, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        titleRow.setClickable(true);
        titleRow.setOnClickListener(openSessionClick);
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
        close.setText("Close");
        close.setAllCaps(false);
        close.setTextSize(12);
        close.setTextColor(Color.rgb(30, 30, 46));
        setTouchableBackground(close, Color.rgb(243, 139, 168), Color.rgb(245, 194, 231));
        close.setPadding(dp(3), 0, dp(3), 0);
        close.setOnClickListener(v -> {
            flashTap(v);
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

    private void addOldSessionRow(
            LinearLayout list,
            JSONObject session,
            AlertDialog[] dialogRef
    ) {
        String sessionId = session.optString("id", "");
        String title = session.optString("title", sessionId);
        String cwd = session.optString("cwd", "");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        LinearLayout openPanel = new LinearLayout(this);
        openPanel.setOrientation(LinearLayout.VERTICAL);
        openPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        openPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        setTouchableBackground(openPanel, Color.rgb(49, 50, 68), Color.rgb(137, 180, 250));
        openPanel.setClickable(true);
        openPanel.setOnClickListener(v -> {
            flashTap(v);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            confirmResumeOldSession(sessionId, cwd, title);
        });

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(15);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.rgb(205, 214, 244));
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
                        ClipData.newPlainText("WEzterm old session title", title)
                );
                toast("Old session title copied");
            }
            return true;
        });

        // WHY: the user asked for old sessions "just by date and the name of
        // each." Do not add process names, subagent labels, pane IDs, or tmux
        // details here. The Resume button is the action; the row content stays
        // the saved parent session name so the phone list reads like a normal
        // recent-session picker.
        openPanel.addView(titleText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button resume = new Button(this);
        resume.setText("Resume");
        resume.setAllCaps(false);
        resume.setTextSize(12);
        resume.setTextColor(Color.rgb(30, 30, 46));
        setTouchableBackground(resume, Color.rgb(166, 227, 161), Color.rgb(148, 226, 213));
        resume.setPadding(dp(3), 0, dp(3), 0);
        resume.setOnClickListener(v -> {
            flashTap(v);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            confirmResumeOldSession(sessionId, cwd, title);
        });

        row.addView(openPanel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(
                dp(92),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        resume.setMinHeight(dp(72));
        resumeParams.setMargins(dp(6), 0, 0, 0);
        row.addView(resume, resumeParams);
        list.addView(row);
    }

    private void confirmResumeOldSession(String sessionId, String cwd, String title) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            toast("Old session id missing");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Resume old session?")
                .setMessage(title)
                .setPositiveButton("Resume", (dialog, which) -> {
                    String path = "/resume-session?fast=1&sessionId=" + urlEncode(sessionId)
                            + "&cwd=" + urlEncode(cwd);
                    control(path, "Old session opened");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClose() {
        // WHY: closing the active session should not rebuild the whole picker
        // payload. `/tabs` now includes per-window status checks, which are useful
        // for the picker but made the main Close button feel disconnected.
        getJsonWithRetry("/active", payload -> {
            JSONObject window = payload.optJSONObject("window");
            if (window == null) {
                confirmClose(-1, "", "current session");
                return;
            }
            confirmClose(window.getInt("index"), window.optString("windowId", ""), window.optString("title", "current session"));
        });
    }

    private void confirmClose(int index, String windowId, String title) {
        new AlertDialog.Builder(this)
                .setTitle("Close " + title + "?")
                .setMessage("This closes that active session and whatever is running inside it.")
                .setPositiveButton("Close", (dialog, which) -> {
                    String path = index >= 0
                            ? "/close?fast=1&windowId=" + urlEncode(windowId) + "&index=" + index
                            : "/close?fast=1";
                    control(path, "Closed session");
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
                        .setTitle("Rename current session")
                        .setMessage("Use a short name that describes this work.")
                        .setView(input)
                        .setPositiveButton("Save", (dialog, which) -> {
                            String name = input.getText().toString().trim();
                            control("/rename?windowId=" + urlEncode(windowId) + "&index=" + index + "&name=" + urlEncode(name), "Renamed session");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            toast("No current session found");
        });
    }

    private String tabLabel(JSONObject window, String session) throws Exception {
        String state = window.getBoolean("active") ? "Current: " : "Open: ";
        String title = window.optString("title", window.optString("name", "shell"));
        String detail = window.optString("detail", window.optString("command", ""));
        String path = window.optString("shortPath", "");
        return state + title
                + "\nSession " + session
                + " · " + detail
                + " · " + path;
    }

    private String tabDetail(JSONObject window, String session) throws Exception {
        String state = window.getBoolean("active") ? "Current session" : "Tap to open";
        String status = window.optString("statusLabel", "Done");
        String attention = window.optString("attentionReason", "");
        String activity = window.optString("activityGroup", "");
        String detail = window.optString("detail", window.optString("command", ""));
        String path = window.optString("shortPath", "");
        return status
                + (attention.isEmpty() ? "" : " - " + attention)
                + " - " + state
                + (activity.isEmpty() ? "" : " - " + activity)
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
        selectTabForTyping(index, windowId, title, null);
    }

    private void selectTabForTyping(int index, String windowId, String title, AlertDialog[] dialogRef) {
        if (sessionSwitchInFlight) {
            toast("Opening session...");
            return;
        }
        sessionSwitchInFlight = true;
        toast("Opening " + title);
        // WHY: opening a tab from the phone should never require a separate Live
        // tap or Enter press. The select happens first, then the selected tab is
        // forced back to the live bottom and xterm is focused so the next prompt
        // can be typed immediately.
        long generation = leaveReadModeForLiveInput();
        // WHY: switching tabs used to wait for the server to rebuild the full
        // tab list, including pane-tail status reads for every Codex window,
        // before returning to live typing. `/select-live` combines select and
        // bottom restore server-side, so the phone performs one round trip and
        // stale double-taps cannot stack slow switch requests.
        String path = "/select-live?fast=1&windowId=" + urlEncode(windowId) + "&index=" + index;
        getJsonWithRetry(path, payload -> {
            sessionSwitchInFlight = false;
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "Command failed");
                toast(error);
                return;
            }
            // WHY: v1.55 tried to force a visible repaint by reloading WebView
            // after every Active Sessions row tap. In real use that looked like a
            // page refresh, froze taps long enough that the user tapped 3-4 times,
            // and restarted the same focus/scroll helpers that make typing jump.
            // `/select-live` already selects tmux and exits copy-mode server-side;
            // keep Android's response lightweight and let ttyd paint the selected
            // tmux window without tearing down the WebSocket transport.
            if (dialogRef != null && dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            toast("Opened " + title);
            pinTerminalViewportLocal();
            focusTerminalInputSoon(false);
        }, exc -> {
            sessionSwitchInFlight = false;
            toast("WEzterm control is not reachable");
        });
    }

    private void control(String path, String message, boolean refocusTerminal) {
        long readModeGeneration = refocusTerminal ? leaveReadModeForLiveInput() : enterReadMode();
        long touchGeneration = terminalTouchGestureGeneration;
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
                if (readModeGeneration == terminalModeGeneration
                        && touchGeneration == terminalTouchGestureGeneration) {
                    // WHY: generic toolbar/control actions are asynchronous. If the
                    // user starts a newer tap, scroll, picker, or typing action while
                    // the HTTP request is in flight, this callback must not become a
                    // late second IME/focus request that duplicates Samsung/Gboard
                    // composition. `leaveReadModeForLiveInput` already guards read
                    // state, but normal live taps advance only the touch generation,
                    // so both guards are required to block stale Stop/New/Close
                    // callbacks after typing has begun.
                    focusTerminalInputSoon(false);
                }
            } else {
                keepReadModeIfCurrent(readModeGeneration);
            }
        });
    }

    private void focusTerminalInputSoon() {
        focusTerminalInputSoon(false);
    }

    private void focusTerminalInputSoon(boolean requestKeyboard) {
        if (webView == null) {
            return;
        }
        if (readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            hideKeyboardForReadMode();
            return;
        }
        if (requestKeyboard && keyboardVisibleForFocusedWebView()) {
            // WHY: a visible IME already has the WebView as its served editor.
            // Re-running xterm textarea focus while Samsung/Gboard is composing is
            // the duplicate-word regression; leave the current input connection
            // alone unless the keyboard is actually hidden or focus was lost.
            return;
        }
        long now = System.currentTimeMillis();
        if (lastTerminalFocusBurstModeGeneration == terminalModeGeneration
                && now - lastTerminalFocusBurstAtMs < TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS
                && (!requestKeyboard || lastTerminalFocusBurstRequestedKeyboard)) {
            // WHY: this method is called by resume, window-focus, page-finished,
            // Refresh, live-bottom restore, paste, and tap-up. Letting every caller
            // enqueue its own 2+ second retry chain reintroduced the old Samsung
            // IME bug where stale focus callbacks fired after typing began and text
            // appeared duplicated. Collapse near-simultaneous callers into the
            // current burst. Do not also run live-input visibility here: that JS
            // calls xterm scrollToBottom/scrollIntoView and made a plain tap look
            // like the terminal randomly snapped up/down.
            return;
        }
        lastTerminalFocusBurstAtMs = now;
        lastTerminalFocusBurstModeGeneration = terminalModeGeneration;
        lastTerminalFocusBurstRequestedKeyboard = requestKeyboard;
        if (requestKeyboard) {
            // WHY: deliberate typing focus owns the next few frames. Any earlier
            // Refresh/Paste/Start visibility retry that is still queued would run
            // xterm.scrollToBottom, scrollIntoView, and WebView scroll correction
            // while the IME is composing, which is the visible tap-refresh loop and
            // duplicate-text regression the phone screenshots show.
            liveInputVisibilityGeneration++;
            lastLiveInputVisibilityBurstAtMs = 0;
            lastLiveInputVisibilityBurstModeGeneration = -1;
        }
        long generation = ++terminalFocusGeneration;
        // WHY: returning from Android's media picker, Activity resume, window
        // focus, page-finished, and Active switching are passive lifecycle events.
        // They may need reconnect-overlay detection, but they must not reopen the
        // IME or focus xterm's textarea. Doing so resizes WebView and starts the
        // up/down refresh loop shown in the phone screenshots. Only deliberate
        // typing actions pass requestKeyboard=true.
        focusTerminalInput(generation, requestKeyboard);
        if (!requestKeyboard) {
            // WHY: passive lifecycle focus probes may need one extra frame to see
            // ttyd's DOM after a load/reload, but they never focus the xterm
            // textarea or show the IME. Deliberate tap-to-type is stricter below:
            // Android/Samsung composition must see one keyboard activation only.
            postTerminalFocusRetry(generation, false, 180);
        }
    }

    private void postTerminalFocusRetry(long generation, boolean requestKeyboard, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (generation != terminalFocusGeneration) {
                return;
            }
            focusTerminalInput(generation, requestKeyboard);
        }, delayMs);
    }

    private void focusTerminalInput(long generation, boolean requestKeyboard) {
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
        if (requestKeyboard && keyboardVisibleForFocusedWebView()) {
            return;
        }
        if (requestKeyboard && !webView.hasFocus()) {
            webView.requestFocusFromTouch();
            webView.requestFocus(View.FOCUS_DOWN);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        webView.evaluateJavascript(
                terminalFocusAndReconnectProbeScript(requestKeyboard),
                value -> handleTerminalFocusProbe(value, generation, requestKeyboard)
        );
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
        // WHY: Android/WebView pinch and zoomed one-finger panning often emit a
        // final single-pointer frame after the second finger lifts. A 900 ms unlock
        // still allowed delayed viewport pins, live-bottom visibility helpers, or
        // document-scroll cleanup to snap the viewer back while the user was trying
        // to inspect the zoomed section. Keep this long enough for the gesture and
        // immediate paint to settle, but bounded so accidental document scroll still
        // gets cleaned later.
        viewerPanUnlockedUntilMs = System.currentTimeMillis() + VIEWER_PAN_UNLOCK_MS;
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
        keepLiveInputVisible(reason, generation);
        if (!"focus".equals(reason) && !"tap-up".equals(reason)) {
            // WHY: repeated xterm.scrollToBottom/scrollIntoView calls after a
            // simple tap looked like random up/down refreshing. Keep retries only
            // for explicit recovery actions such as Refresh, paste, and safe
            // prompt. Active switching and toolbar live-bottom now use fast
            // server-side live recovery and must not run this retry burst.
            postLiveInputVisibilityRetry(reason, generation, 220);
            postLiveInputVisibilityRetry(reason, generation, 650);
            postLiveInputVisibilityRetry(reason, generation, 1300);
        }
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
                || isTerminalGestureRecoveryActive()) {
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
                + "el.setAttribute('enterkeyhint','send');"
                + "el.setAttribute('aria-autocomplete','none');"
                + "el.setAttribute('data-gramm','false');"
                + "el.setAttribute('data-ms-editor','false');"
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
                    || isTerminalGestureRecoveryActive()) {
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

    private String terminalFocusAndReconnectProbeScript(boolean requestKeyboard) {
        // WHY: this script is intentionally narrow. Earlier broad scans for the
        // word "reconnect" caused random reloads when terminal output merely
        // mentioned reconnecting. The actual ttyd failure is a small overlay
        // text node like "Press ↵ to Reconnect" outside xterm's terminal rows.
        // Detect only that overlay shape, then let Android reload the WebView so
        // the phone reconnects without the user pressing Enter.
        String focusInput = requestKeyboard
                ? "if(!already&&t&&typeof t.focus==='function'){t.focus();}"
                        + "if(!already&&root&&typeof root.focus==='function'){root.focus();}"
                : "";
        String focusTextarea = requestKeyboard
                ? "if(document.activeElement!==el){try{el.focus({preventScroll:true});}catch(e){el.focus();}}"
                : "";
        return "(function(){"
                + "try{"
                + "var t=window.term||window.terminal;"
                + "var el=document.querySelector('.xterm-helper-textarea, .xterm textarea, textarea');"
                + "var root=document.querySelector('.xterm');"
                + "var already=!!(el&&document.activeElement===el);"
                + focusInput
                + "if(el){"
                + "el.setAttribute('autocapitalize','none');"
                + "el.setAttribute('autocomplete','off');"
                + "el.setAttribute('autocorrect','off');"
                + "el.setAttribute('spellcheck','false');"
                + "el.setAttribute('enterkeyhint','send');"
                + "el.setAttribute('aria-autocomplete','none');"
                + "el.setAttribute('data-gramm','false');"
                + "el.setAttribute('data-ms-editor','false');"
                + focusTextarea
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

    private void handleTerminalFocusProbe(String value, long generation, boolean requestKeyboard) {
        if (generation != terminalFocusGeneration) {
            return;
        }
        if (isTerminalGestureRecoveryActive()) {
            return;
        }
        JSONObject probe = parseJavascriptObject(value);
        String probeStatus = probe == null ? "" : probe.optString("status", "");
        if (requestKeyboard && "no-input".equals(probeStatus)) {
            // WHY: one live tap must not restart Samsung/Gboard composition. This
            // is the only keyboard-path retry left. It is allowed only when the
            // first probe proves xterm's helper textarea does not exist yet. If the
            // textarea was focused or focus-requested, retrying would create a
            // second editor connection/showSoftInput cycle and Samsung or Gboard
            // can recommit the same composing text, which is the duplicate typing
            // regression the user is reporting.
            postTerminalFocusRetry(generation, true, 180);
            return;
        }
        if (probe == null || !probe.optBoolean("needsReconnect", false)) {
            return;
        }
        // WHY: the user-facing contract is that WEzterm reconnects when opened
        // or focused. Pressing Enter here would send a real key into whichever
        // Codex/tmux pane is active after reconnect, so reload the WebView
        // transport instead of synthesizing keyboard input.
        reloadTerminalForReconnect();
    }

    private boolean keyboardVisibleForFocusedWebView() {
        return lastImeInsetBottom > 0 && webView != null && webView.hasFocus();
    }

    private boolean isTerminalGestureRecoveryActive() {
        // WHY: bottom-edge recovery is still part of the physical scroll gesture
        // after ACTION_UP has cleared `terminalHistoryDragActive`. Reconnect
        // probes, blank-watchdog probes, IME show calls, and zoomed viewport
        // pinning must not run during that async gap or the phone appears to
        // refresh/reconnect repeatedly right before live bottom.
        return terminalHistoryDragActive
                || terminalBottomRestoreInFlight
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive;
    }

    private void reloadTerminalForReconnect() {
        if (isTerminalGestureRecoveryActive()) {
            return;
        }
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
        uiHandler.postDelayed(() -> focusTerminalInputSoon(false), 800);
        scheduleBlankTerminalWatchdog("reconnect-reload");
    }

    private void scheduleBlankTerminalWatchdog(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (System.currentTimeMillis() < nativePickerQuietUntilMs) {
            // WHY: Android's document picker and share sheet legitimately obscure
            // the WebView while the terminal process is still healthy. Running the
            // blank watchdog during that handoff caused reload/page-finished/focus
            // bursts that looked like the whole phone terminal was refreshing.
            return;
        }
        if (isTerminalGestureRecoveryActive()) {
            return;
        }
        long generation = lastTerminalLoadAtMs;
        uiHandler.postDelayed(() -> verifyTerminalPainted(reason, generation), 2600);
    }

    private void verifyTerminalPainted(String reason, long loadGeneration) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (isTerminalGestureRecoveryActive()
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
        if (isTerminalGestureRecoveryActive()
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
        uiHandler.postDelayed(() -> focusTerminalInputSoon(false), 900);
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
        lastTerminalFocusBurstRequestedKeyboard = false;
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
        getJsonAttempt(path, callback, failureCallback, 1);
    }

    private void getJsonWithRetry(String path, JsonCallback callback) {
        getJsonWithRetry(path, callback, null);
    }

    private void getJsonWithRetry(String path, JsonCallback callback, FailureCallback failureCallback) {
        getJsonAttempt(path, callback, failureCallback, CONTROL_SAFE_RETRY_ATTEMPTS);
    }

    private void getJsonAttempt(
            String path,
            JsonCallback callback,
            FailureCallback failureCallback,
            int attemptsRemaining
    ) {
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
                if (attemptsRemaining > 1) {
                    uiHandler.postDelayed(
                            () -> getJsonAttempt(
                                    path,
                                    callback,
                                    failureCallback,
                                    attemptsRemaining - 1
                            ),
                            CONTROL_SAFE_RETRY_DELAY_MS
                    );
                    return;
                }
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
            int keyboardReserve;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()
                );
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = bars.top;
                lastImeInsetBottom = ime.bottom;
                bottom = bars.bottom;
                keyboardReserve = Math.max(0, ime.bottom - bars.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                lastImeInsetBottom = 0;
                bottom = insets.getSystemWindowInsetBottom();
                keyboardReserve = 0;
            }
            // WHY: Android system bars steal real pixels on the S25 Ultra, but the
            // IME is not part of the toolbar's visual height. v1.64 added
            // `ime.bottom` to this two-row toolbar after `adjustResize`, which made
            // the bottom bar expand into a huge blank panel and caused the live
            // terminal bottom to appear hidden behind buttons. Keep the toolbar
            // fixed to its button rows plus the navigation inset. If this edge-to-
            // edge WebView window still reports an IME inset instead of being fully
            // resized, reserve that keyboard space on the root below the toolbar;
            // never add it to the toolbar height itself.
            view.setPadding(0, top, 0, keyboardReserve);
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

    private static class TerminalWebView extends WebView {
        TerminalWebView(Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection connection = super.onCreateInputConnection(outAttrs);
            if (outAttrs != null) {
                // WHY: this WebView is a terminal, not prose input. Samsung/Android
                // prediction can rewrite terminal text while xterm's hidden
                // textarea is composing. Do not mark the field as password or
                // no-personalized-learning: those flags caused keyboard
                // incognito/private mode and removed voice dictation. Keep the
                // editor visible/normal for the IME, and rely on the safe prompt
                // paste path for long prompts that must not be autocorrected.
                outAttrs.inputType = TERMINAL_INPUT_TYPE;
                outAttrs.imeOptions = (outAttrs.imeOptions & ~EditorInfo.IME_MASK_ACTION)
                        | TERMINAL_IME_OPTIONS;
            }
            return connection;
        }
    }

    private class TerminalWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            focusTerminalInputSoon(false);
            // WHY: WebView reports page-finished before the next painted frame is
            // guaranteed to reflect ttyd/xterm. The old visibility call ran
            // xterm.scrollToBottom, scrollIntoView, and document scroll resets from
            // a passive lifecycle callback, which is the refresh/jump loop seen
            // when returning from Upload or tapping into the terminal. Keep this
            // callback passive; explicit Refresh/Live/Paste/Start actions own
            // forced live-bottom visibility.
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
