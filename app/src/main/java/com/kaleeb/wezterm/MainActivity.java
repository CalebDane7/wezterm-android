package com.kaleeb.wezterm;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String TERMINAL_URL = "http://100.113.254.7:8089/terminal-renderer";
    private static final String MAGIC_DNS_TERMINAL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8089/terminal-renderer";
    private static final int APK_CAPTURE_RENDERER_COLS = 132;
    private static final String[] TERMINAL_URLS = {
            TERMINAL_URL,
            MAGIC_DNS_TERMINAL_URL
    };
    private static final String CONTROL_URL = "http://100.113.254.7:8089";
    private static final String MAGIC_DNS_CONTROL_URL = "http://kaleeblaptop-1.taildbdeee.ts.net:8089";
    private static final String[] CONTROL_URLS = {
            CONTROL_URL,
            MAGIC_DNS_CONTROL_URL
    };
    private static final String INSTALL_URL = "http://100.113.254.7:8091/install.html";
    private static final String[] WOL_MAC_ADDRESSES = {
            "10:98:19:3A:A8:56",
            "10:91:D1:F2:56:FC"
    };
    private static final String[] WOL_TARGETS = {
            "255.255.255.255",
            "192.168.0.255",
            "192.168.0.81",
            "192.168.0.130"
    };
    private static final int[] WOL_PORTS = {
            9,
            7
    };
    private static final String PREFS = "wezterm";
    private static final String PREF_PIN_REQUESTED = "pin_requested";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final String PREF_UPLOAD_PATH_PREFIX = "upload_path_";
    private static final String PREF_UPLOAD_FILENAME_PREFIX = "upload_filename_";
    private static final String PREF_UPLOAD_BYTES_PREFIX = "upload_bytes_";
    private static final String PREF_UPLOAD_UPDATED_PREFIX = "upload_updated_";
    private static final String APP_VERSION_NAME = "2.79";
    private static final String UPLOAD_LOG_TAG = "WEztermUpload";
    private static final int TERMINAL_INPUT_TYPE = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_VARIATION_NORMAL
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
    private static final int TERMINAL_IME_OPTIONS = EditorInfo.IME_ACTION_SEND
            | EditorInfo.IME_FLAG_NO_FULLSCREEN;
    private static final int DEFAULT_FONT_SIZE = 12;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 18;
    private static final int TOOLBAR_HEIGHT_DP = 92;
    private static final int TITLE_STRIP_HEIGHT_DP = 18;
    private static final int PROMPT_COMPOSER_INPUT_HEIGHT_DP = 44;
    private static final int PROMPT_COMPOSER_VERTICAL_PADDING_DP = 4;
    private static final long HISTORY_DRAG_THROTTLE_MS = 16;
    private static final int HISTORY_DRAG_LINE_THRESHOLD_DP = 5;
    private static final int HISTORY_DRAG_PAGES_PER_STEP = 1;
    private static final int HISTORY_DRAG_MAX_PAGES_PER_STEP = 24;
    private static final int HISTORY_DRAG_DOWN_MAX_REPEATS = 10;
    private static final int HISTORY_DRAG_DOWN_RELEASE_MAX_REPEATS = 14;
    private static final int HISTORY_DRAG_MOMENTUM_MAX_FRAMES = 24;
    private static final long HISTORY_DRAG_MOMENTUM_FRAME_MS = 45;
    private static final float HISTORY_DRAG_MOMENTUM_DECAY = 0.87f;
    private static final float HISTORY_DRAG_MOMENTUM_STOP_VELOCITY_PX_PER_SEC = 140f;
    private static final float HISTORY_DRAG_MOMENTUM_REPEAT_VELOCITY_DIVISOR = 540f;
    private static final int HISTORY_DRAG_MOMENTUM_UP_MAX_REPEATS = 16;
    private static final int HISTORY_DRAG_MOMENTUM_DOWN_MAX_REPEATS = 6;
    private static final long HISTORY_DRAG_RELEASE_LONG_GESTURE_MS = 650;
    private static final long HISTORY_DRAG_REPEATED_FLING_WINDOW_MS = 700;
    private static final int HISTORY_DRAG_REPEATED_FLING_BOOST_REPEATS = 8;
    private static final int HISTORY_DRAG_SLOW_MOVE_MAX_REPEATS = 3;
    private static final int HISTORY_DRAG_SLOW_PENDING_MAX_REPEATS = 6;
    private static final int HISTORY_DRAG_FAST_MOVE_REPEATS = 6;
    private static final int HISTORY_DRAG_FLING_MOVE_REPEATS = 10;
    private static final long TOUCH_SCROLL_RENDER_PULSE_MS = 16;
    private static final long TOUCH_SCROLL_RENDER_PULSE_WINDOW_MS = 850;
    private static final float HISTORY_DRAG_RELEASE_MIN_LINES = 2f;
    private static final int TOUCH_SCROLL_LIVE_BOTTOM_SNAP_LINES = 3;
    private static final float HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC = 1200f;
    private static final float HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC = 2400f;
    private static final float HISTORY_DRAG_FAST_DISTANCE_LINES = 3f;
    private static final float HISTORY_DRAG_FLING_DISTANCE_LINES = 7f;
    private static final float WEBVIEW_ZOOMED_SCALE_THRESHOLD = 1.02f;
    private static final float ZOOMED_HORIZONTAL_PAN_DRIFT_RATIO = 0.70f;
    private static final long VIEWER_PAN_UNLOCK_MS = 2500;
    private static final long TERMINAL_FOCUS_BURST_MIN_INTERVAL_MS = 700;
    private static final long LIVE_INPUT_VISIBILITY_BURST_MIN_INTERVAL_MS = 220;
    private static final long PROMPT_COMPOSER_SOFT_INPUT_MIN_INTERVAL_MS = 900;
    private static final long TOOLBAR_STATUS_POLL_MS = 5000;
    private static final long STATUS_DOT_PULSE_MS = 520;
    private static final float STATUS_DOT_DIM_ALPHA = 0.38f;
    private static final float STATUS_DOT_FULL_ALPHA = 1.0f;
    private static final long ENTRY_LIVE_BOTTOM_SETTLE_MIN_INTERVAL_MS = 900;
    private static final long BLANK_TAIL_MASK_MAX_LIFETIME_MS = 2200;
    private static final long FULL_FRAME_SESSION_SWITCH_SHIELD_MAX_MS = 70;
    private static final long IMMEDIATE_DOT_FILLER_SHIELD_MAX_LIFETIME_MS = 2600;
    private static final long ACTIVE_SWITCH_WEBVIEW_LOWER_DOT_SHIELD_MS = 7000;
    private static final float NATIVE_LOWER_DOT_SHIELD_TOP_FRACTION = 0.40f;
    private static final long DOT_ROW_SCRUBBER_MAX_LIFETIME_MS = 120000;
    private static final long PASSIVE_SWITCH_XTERM_SETTLE_LAST_DELAY_MS = 2600;
    private static final long WOL_COOLDOWN_MS = 30000;
    private static final long TERMINAL_WAKE_RETRY_DELAY_MS = 7000;
    private static final int VISIBLE_WEBVIEW_PAINT_SAMPLE_STEP_PX = 8;
    private static final int VISIBLE_WEBVIEW_MIN_BRIGHT_SAMPLES = 350;
    private static final double VISIBLE_WEBVIEW_MIN_BRIGHT_RATIO = 0.008;
    private static final int CONTROL_SAFE_RETRY_ATTEMPTS = 3;
    private static final int CONTROL_SAFE_RETRY_DELAY_MS = 140;
    private static final long SELECTED_CLOSE_TARGET_MAX_AGE_MS = 10 * 60 * 1000;
    private static final long PASSIVE_NAVIGATION_TOUCH_SUPPRESS_MS = 2600;
    private static final int PASSIVE_TAB_OPEN_BOTTOM_RETRY_LIMIT = 5;
    private static final long PASSIVE_TAB_OPEN_BOTTOM_RETRY_MS = 260;
    private static final int REQUEST_UPLOAD_MEDIA = 5201;
    private static final long MAX_MEDIA_UPLOAD_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MEDIA_UPLOAD_STREAM_CHUNK_BYTES = 1024 * 1024;
    private static final int LOCAL_HISTORY_CHUNK_LINES = 500;
    private static final int LOCAL_HISTORY_MAX_DISPLAY_CHARS = 90000;
    private WebView webView;
    private FrameLayout terminalFrame;
    private View historyTouchOverlay;
    private View sessionSwitchPaintShield;
    private View sessionSwitchLowerPaintShield;
    private PopupWindow sessionSwitchLowerPopupShield;
    private LinearLayout promptComposerBar;
    private EditText promptComposerInput;
    private TextView sessionTitleStrip;
    private Button startToolbarButton;
    private TextView toolbarStatusDot;
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
    private long lastPromptComposerShowSoftInputAtMs = 0;
    private long terminalFitGeneration = 0;
    private long visibleWebViewPaintGeneration = 0;
    private long sessionSwitchLiveViewportGeneration = 0;
    private long blankTailMaskGeneration = 0;
    private long passiveSwitchXtermSettleGeneration = 0;
    private long sessionSwitchPaintShieldGeneration = 0;
    private long sessionSwitchLowerPaintShieldGeneration = 0;
    private long sessionSwitchWebViewLayerGeneration = 0;
    private long viewerTypingPositionGeneration = 0;
    private long entryLiveBottomSettleGeneration = 0;
    private long entryBottomCoreGeneration = 0;
    private long lastEntryLiveBottomSettleAtMs = 0;
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
    private float terminalLastViewerPanX = 0;
    private boolean terminalHistoryDragActive = false;
    private boolean terminalMultiTouchGesture = false;
    private boolean terminalTouchExceededTapSlop = false;
    private boolean terminalHorizontalPanActive = false;
    private boolean terminalTouchReachedLiveBottom = false;
    private boolean terminalTouchStartedDuringPassiveSuppression = false;
    private boolean terminalForwardingTouchToViewer = false;
    private MotionEvent terminalViewerDownEvent = null;
    private long terminalBodyTapSuppressedUntilMs = 0;
    private boolean historyScrollRequestInFlight = false;
    private String pendingHistoryScrollWhere = "";
    private int pendingHistoryScrollRepeats = 0;
    private long pendingHistoryScrollGeneration = 0;
    private String pendingHistoryScrollTargetKey = "";
    private boolean terminalHistoryMomentumActive = false;
    private boolean terminalHistoryMomentumFrameScheduled = false;
    private float terminalHistoryMomentumVelocityPxPerSec = 0f;
    private int terminalHistoryMomentumFramesRemaining = 0;
    private long terminalHistoryMomentumGeneration = 0;
    private String terminalHistoryMomentumWhere = "";
    private String terminalHistoryMomentumTargetKey = "";
    private String lastHistoryFlingWhere = "";
    private long lastHistoryFlingAtMs = 0;
    private boolean touchScrollRenderPulseScheduled = false;
    private long touchScrollRenderPulseUntilMs = 0;
    private long terminalTouchGestureGeneration = 0;
    private String terminalTouchStableWindowId = "";
    private long terminalTouchDownWallClockMs = 0;
    private long lastHistoryDragAtMs = 0;
    private long terminalLastHistoryDragEventAtMs = 0;
    private float webViewScale = 1.0f;
    private boolean keyZoomViewerStateActive = false;
    private long viewerPanUnlockedUntilMs = 0;
    private int lastImeInsetBottom = 0;
    private boolean sessionSwitchInFlight = false;
    private String selectedPhoneWindowId = "";
    private int selectedPhoneWindowIndex = -1;
    private String selectedPhoneWindowTitle = "";
    private long selectedPhoneWindowUpdatedAtMs = 0;
    private String currentPhoneWindowId = "";
    private boolean activityResumed = false;
    private boolean promptComposerProgrammaticTextChange = false;
    private String promptComposerDraftTargetKey = "";
    private long promptComposerDraftLocalGeneration = 0;
    private long promptComposerVisibilityGeneration = 0;
    private boolean promptComposerSubmitInFlight = false;
    private String promptComposerSubmitFingerprint = "";
    private long toolbarStatusGeneration = 0;
    private long terminalWakeRetryGeneration = 0;
    private long lastWakeOnLanAtMs = 0;
    private long lastNavigationHideAtMs = 0;
    private int terminalUrlIndex = 0;
    private String activeTerminalBaseUrl = TERMINAL_URL;
    private String activeControlBaseUrl = CONTROL_URL;
    private String pendingUploadPickerKind = "";
    private final ArrayDeque<OptionKeyDispatch> optionKeyDispatchQueue = new ArrayDeque<>();

    private static final class UploadAssociation {
        final String windowId;
        final String path;
        final String filename;
        final long bytes;
        final long updatedAtMs;

        UploadAssociation(String windowId, String path, String filename, long bytes, long updatedAtMs) {
            this.windowId = windowId == null ? "" : windowId;
            this.path = path == null ? "" : path;
            this.filename = filename == null || filename.trim().isEmpty()
                    ? "uploaded media"
                    : filename.trim();
            this.bytes = bytes;
            this.updatedAtMs = updatedAtMs;
        }
    }
    private boolean optionKeyDispatchInFlight = false;
    private VelocityTracker terminalVelocityTracker;

    private interface JsonCallback {
        void onResult(JSONObject payload) throws Exception;
    }

    private interface FailureCallback {
        void onFailure(Exception exc);
    }

    private static class OptionKeyDispatch {
        final String key;
        final String message;
        final String targetKey;

        OptionKeyDispatch(String key, String message, String targetKey) {
            this.key = key;
            this.message = message;
            this.targetKey = targetKey;
        }
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
        hideNavigationDeadStrip("create");
        wakeLaptopForTerminal("app-open");
        loadTerminal();
        handleIncomingMediaShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            restorePreferredEndpointsOnLauncherReentry();
        }
        handleIncomingMediaShare(intent);
    }

    private void restorePreferredEndpointsOnLauncherReentry() {
        if (webView == null) {
            return;
        }
        // WHY: WEzterm is singleTask, so Android can bring an already-running
        // task forward without calling onCreate(). The 2026-06-17 failure left a
        // stale MagicDNS WebView/control endpoint on screen even after v2.04
        // preferred the direct Tailnet IP. A launcher re-entry is the user's
        // explicit reconnect path, so reset both endpoint owners to the proven
        // direct IP and reload only when the current WebView is not already there.
        terminalUrlIndex = 0;
        activeTerminalBaseUrl = TERMINAL_URL;
        activeControlBaseUrl = CONTROL_URL;
        wakeLaptopForTerminal("launcher-reentry");
        String currentUrl = webView.getUrl();
        if (currentUrl == null || !currentUrl.startsWith(TERMINAL_URL)) {
            loadTerminalAtIndex(0, "launcher-reentry");
        } else {
            pinTerminalViewportSoon("launcher-reentry");
            focusTerminalInputSoon(false);
            keepLiveInputVisibleSoon("launcher-reentry");
            scheduleBlankTerminalWatchdog("launcher-reentry");
        }
        scheduleToolbarStatusDotRefresh(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UPLOAD_MEDIA) {
            nativePickerQuietUntilMs = System.currentTimeMillis() + 8000;
        }
        if (requestCode == REQUEST_UPLOAD_MEDIA) {
            String pickerKind = pendingUploadPickerKind;
            pendingUploadPickerKind = "";
            Log.i(UPLOAD_LOG_TAG, "picker-result kind=" + pickerKind
                    + " result=" + resultCode
                    + " hasData=" + (data != null)
                    + " hasClip=" + (data != null && data.getClipData() != null));
            if (resultCode != RESULT_OK || data == null) {
                return;
            }
            List<Uri> uris = uploadUrisFromResult(data);
            if (uris.isEmpty()) {
                toast("No media selected");
                Log.w(UPLOAD_LOG_TAG, "picker-result-empty kind=" + pickerKind);
                return;
            }
            for (Uri uri : uris) {
                prepareReadAccessForUpload(data, uri);
                uploadMediaUri(uri, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        keepScreenAwakeForActiveTerminal();
        wakeLaptopForTerminal("resume");
        if (webView != null) {
            webView.onResume();
            if (isDockedPromptComposerVisible()) {
                restoreDockedPromptComposerFocus("resume");
            } else {
                focusTerminalInputSoon(false);
            }
            scheduleBlankTerminalWatchdog("resume");
            settleEntryLiveBottomSoon("resume");
        }
        scheduleToolbarStatusDotRefresh(0);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        toolbarStatusGeneration++;
        promptComposerDraftLocalGeneration++;
        stopStatusDotPulsesInTree(getWindow().getDecorView());
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && webView != null) {
            hideNavigationDeadStrip("window-focus");
            if (isDockedPromptComposerVisible()) {
                restoreDockedPromptComposerFocus("window-focus");
            } else {
                focusTerminalInputSoon(false);
            }
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
            hideDockedPromptComposer(false, false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        if (handleViewerZoomKey(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private View buildLayout(WebView view) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(30, 30, 46));
        root.setKeepScreenOn(true);
        LinearLayout toolbar = bottomBar();
        promptComposerBar = buildPromptComposer();
        sessionTitleStrip = buildSessionTitleStrip();
        applySystemBarPadding(root, toolbar);

        terminalFrame = new FrameLayout(this);
        terminalFrame.setKeepScreenOn(true);
        terminalFrame.setClipChildren(true);
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
        sessionSwitchPaintShield = new View(this);
        sessionSwitchPaintShield.setBackgroundColor(Color.BLACK);
        sessionSwitchPaintShield.setClickable(true);
        sessionSwitchPaintShield.setVisibility(View.GONE);
        sessionSwitchPaintShield.setAlpha(0f);
        terminalFrame.addView(sessionSwitchPaintShield, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        sessionSwitchLowerPaintShield = new View(this);
        sessionSwitchLowerPaintShield.setBackgroundColor(Color.BLACK);
        sessionSwitchLowerPaintShield.setClickable(false);
        sessionSwitchLowerPaintShield.setVisibility(View.GONE);
        sessionSwitchLowerPaintShield.setAlpha(0f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sessionSwitchLowerPaintShield.setElevation(dp(24));
        }
        terminalFrame.addView(sessionSwitchLowerPaintShield, new FrameLayout.LayoutParams(
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
        root.addView(sessionTitleStrip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(TITLE_STRIP_HEIGHT_DP)
        ));
        // WHY: the native composer must remain above the APK controls. A rejected
        // v2.63 attempt moved it below the toolbar, which made the expected phone
        // text board disappear from above the buttons. Keep the text line in the
        // established place, and reclaim space by tightening chrome instead of
        // moving typing below the controls.
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(TOOLBAR_HEIGHT_DP)
        ));
        return root;
    }

    private long showSessionSwitchPaintShield(String reason) {
        long generation = ++sessionSwitchPaintShieldGeneration;
        if (sessionSwitchPaintShield == null) {
            return generation;
        }
        // WHY: v2.33 makes Active/Old switching render-owned instead of
        // mask-owned. A very short full-frame shield may hide the first broken
        // compositor frame, but the previous lower native/Popup/WebView shields
        // were able to turn the user's dotted-tail complaint into a black
        // lower-rectangle complaint. Clear those broad masks before each switch;
        // only xterm's bounded row scrubber may touch actual dotted rows.
        sessionSwitchPaintShield.animate().cancel();
        clearBroadSessionSwitchVisualMasks(reason + "-pre-switch");
        holdWebViewSoftwareLayerForSessionSwitch(reason, FULL_FRAME_SESSION_SWITCH_SHIELD_MAX_MS);
        sessionSwitchPaintShield.setAlpha(1f);
        sessionSwitchPaintShield.setVisibility(View.VISIBLE);
        sessionSwitchPaintShield.bringToFront();
        hideSessionSwitchPaintShieldSoon(reason + "-max-full-frame-native", generation,
                FULL_FRAME_SESSION_SWITCH_SHIELD_MAX_MS);
        return generation;
    }

    private long showSessionSwitchLowerPaintShield(String reason, float topFraction, long delayMs) {
        long generation = ++sessionSwitchLowerPaintShieldGeneration;
        // WHY: this helper is intentionally a no-op now. Keeping the method as a
        // cleanup boundary avoids risky call-site churn, but v2.33 must never show
        // a broad lower black shield as a "dot fix"; the proof now fails that
        // exact black-lower-terminal screenshot. Actual dotted glyph rows are
        // handled by the row scrubber and must be followed by real live-bottom
        // paint, not a native/WebView/PopupWindow cover.
        clearBroadSessionSwitchVisualMasks(reason + "-v233-disabled-lower-shield");
        return generation;
    }

    private void showSessionSwitchLowerPopupShield(String reason, float topFraction) {
        if (terminalFrame == null || !terminalFrame.isShown()) {
            return;
        }
        int frameWidth = terminalFrame.getWidth();
        int frameHeight = terminalFrame.getHeight();
        if (frameWidth <= 0 || frameHeight <= 0) {
            terminalFrame.post(() -> showSessionSwitchLowerPopupShield(reason, topFraction));
            return;
        }
        float boundedTopFraction = Math.max(0.30f, Math.min(0.58f, topFraction));
        int topPx = Math.round(frameHeight * boundedTopFraction);
        topPx = Math.max(dp(180), Math.min(frameHeight - dp(72), topPx));
        int popupHeight = Math.max(1, frameHeight - topPx);
        int[] location = new int[2];
        terminalFrame.getLocationOnScreen(location);
        if (sessionSwitchLowerPopupShield == null) {
            View popupView = new View(this);
            popupView.setBackgroundColor(Color.BLACK);
            popupView.setClickable(false);
            // WHY: v2.29 proved the normal native child shield can still miss the
            // readable proof frame over Android WebView. Use a separate PopupWindow
            // only during passive Active-switch settle so the lower stale dotted
            // raster is covered above WebView composition. This is still lower-area
            // only, non-touchable, short-lived, and dismissed by the same
            // typing/read/touch cleanup path; it does not touch title/session naming.
            sessionSwitchLowerPopupShield = new PopupWindow(popupView, frameWidth, popupHeight, false);
            sessionSwitchLowerPopupShield.setTouchable(false);
            sessionSwitchLowerPopupShield.setOutsideTouchable(false);
            sessionSwitchLowerPopupShield.setClippingEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                sessionSwitchLowerPopupShield.setElevation(dp(96));
            }
        } else {
            sessionSwitchLowerPopupShield.setWidth(frameWidth);
            sessionSwitchLowerPopupShield.setHeight(popupHeight);
        }
        try {
            if (sessionSwitchLowerPopupShield.isShowing()) {
                sessionSwitchLowerPopupShield.update(location[0], location[1] + topPx, frameWidth, popupHeight, false);
            } else {
                sessionSwitchLowerPopupShield.showAtLocation(terminalFrame, android.view.Gravity.NO_GRAVITY,
                        location[0], location[1] + topPx);
            }
        } catch (WindowManager.BadTokenException exc) {
            // Activity is no longer in a state that can own a popup; the normal
            // cleanup path will remove any stale shield on the next transition.
        }
    }

    private void dismissSessionSwitchLowerPopupShield(String reason) {
        if (sessionSwitchLowerPopupShield == null) {
            return;
        }
        if (sessionSwitchLowerPopupShield.isShowing()) {
            sessionSwitchLowerPopupShield.dismiss();
        }
        sessionSwitchLowerPopupShield = null;
    }

    private void updateSessionSwitchLowerPaintShieldLayout(float topFraction) {
        if (terminalFrame == null || sessionSwitchLowerPaintShield == null) {
            return;
        }
        int frameHeight = terminalFrame.getHeight();
        if (frameHeight <= 0) {
            terminalFrame.post(() -> updateSessionSwitchLowerPaintShieldLayout(topFraction));
            return;
        }
        float boundedTopFraction = Math.max(0.30f, Math.min(0.55f, topFraction));
        int topPx = Math.round(frameHeight * boundedTopFraction);
        topPx = Math.max(dp(180), Math.min(frameHeight - dp(72), topPx));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        sessionSwitchLowerPaintShield.setLayoutParams(params);
        sessionSwitchLowerPaintShield.setTranslationY(topPx);
        sessionSwitchLowerPaintShield.requestLayout();
        sessionSwitchLowerPaintShield.invalidate();
    }

    private void hideSessionSwitchLowerPaintShieldSoon(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> hideSessionSwitchLowerPaintShield(reason, generation),
                Math.max(0, delayMs));
    }

    private void hideSessionSwitchLowerPaintShield(String reason, long generation) {
        if (sessionSwitchLowerPaintShield == null || generation != sessionSwitchLowerPaintShieldGeneration) {
            return;
        }
        sessionSwitchLowerPaintShield.animate().cancel();
        sessionSwitchLowerPaintShield.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> {
                    if (sessionSwitchLowerPaintShield != null
                            && generation == sessionSwitchLowerPaintShieldGeneration) {
                        sessionSwitchLowerPaintShield.setVisibility(View.GONE);
                        sessionSwitchLowerPaintShield.setAlpha(0f);
                    }
                })
                .start();
        dismissSessionSwitchLowerPopupShield(reason);
    }

    private void forceHideSessionSwitchLowerPaintShield(String reason) {
        sessionSwitchLowerPaintShieldGeneration++;
        restoreWebViewLayerAfterSessionSwitch(reason);
        dismissSessionSwitchLowerPopupShield(reason);
        if (sessionSwitchLowerPaintShield == null) {
            removeActiveSwitchWebViewLowerDotShield(reason);
            removeBroadWebViewBlackMasks(reason);
            return;
        }
        sessionSwitchLowerPaintShield.animate().cancel();
        sessionSwitchLowerPaintShield.setVisibility(View.GONE);
        sessionSwitchLowerPaintShield.setAlpha(0f);
        removeActiveSwitchWebViewLowerDotShield(reason);
        removeBroadWebViewBlackMasks(reason);
    }

    private void clearBroadSessionSwitchVisualMasks(String reason) {
        forceHideSessionSwitchLowerPaintShield(reason);
        removeBroadWebViewBlackMasks(reason);
    }

    private void removeBroadWebViewBlackMasks(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        // WHY: the v2.32 screenshot showed that lower black overlays can be just
        // as confusing as the dotted field they replaced. This cleanup removes
        // only broad mask elements/timers; the row-level dot scrubber can remain
        // active because it hides individual proven filler rows instead of
        // covering the readable terminal viewport.
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "window.__weztermBlankTailMaskExpiresAt=0;"
                        + "if(window.__weztermBlankTailMaskTimer){clearInterval(window.__weztermBlankTailMaskTimer);window.__weztermBlankTailMaskTimer=null;}"
                        + "if(window.__weztermBlankTailMaskObserver){try{window.__weztermBlankTailMaskObserver.disconnect();}catch(e){}window.__weztermBlankTailMaskObserver=null;}"
                        + "window.__weztermImmediateDotFillerShieldExpiresAt=0;"
                        + "if(window.__weztermImmediateDotFillerShieldTimer){clearTimeout(window.__weztermImmediateDotFillerShieldTimer);window.__weztermImmediateDotFillerShieldTimer=null;}"
                        + "window.__weztermActiveSwitchLowerDotShieldExpiresAt=0;"
                        + "if(window.__weztermActiveSwitchLowerDotShieldTimer){clearInterval(window.__weztermActiveSwitchLowerDotShieldTimer);window.__weztermActiveSwitchLowerDotShieldTimer=null;}"
                        + "var ids=['wezterm-blank-tail-mask','wezterm-immediate-dot-filler-shield','wezterm-active-switch-lower-dot-shield'];"
                        + "for(var i=0;i<ids.length;i++){var el=document.getElementById(ids[i]);if(el&&el.parentNode){el.parentNode.removeChild(el);}}"
                        + "return 'broad-black-masks-removed:" + sanitizeJavascriptReason(reason) + "';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
    }

    private void holdWebViewSoftwareLayerForSessionSwitch(String reason, long delayMs) {
        if (webView == null) {
            return;
        }
        long generation = ++sessionSwitchWebViewLayerGeneration;
        // WHY: v2.27 proved the lower dotted field can survive DOM row hiding and
        // WebView-local overlays, which means the failing surface is Android's
        // WebView hardware-composited terminal raster. During passive session
        // switching only, hold WebView in a software layer long enough for the
        // UI-dump/readable screenshot path to repaint. Restore the default layer
        // afterward so normal terminal scrolling keeps its hardware path.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        webView.invalidate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.postInvalidateOnAnimation();
        }
        uiHandler.postDelayed(() -> {
            if (generation == sessionSwitchWebViewLayerGeneration) {
                restoreWebViewLayerAfterSessionSwitch(reason);
            }
        }, Math.max(1000L, Math.min(9000L, delayMs)));
    }

    private void restoreWebViewLayerAfterSessionSwitch(String reason) {
        sessionSwitchWebViewLayerGeneration++;
        if (webView == null) {
            return;
        }
        webView.setLayerType(View.LAYER_TYPE_NONE, null);
        webView.invalidate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webView.postInvalidateOnAnimation();
        }
    }

    private void installActiveSwitchWebViewLowerDotShield(String reason, float topFraction, long delayMs) {
        // WHY: user proof on 2026-06-26 showed the old WebView-local lower shield
        // is the wrong layer for Active/zoom fixes: covering stale/dotted paint with
        // a fixed black rectangle can become the giant black bottom box. Keep this
        // helper as a cleanup boundary for old call sites, but permanently disable
        // creation; real repair must come from renderer/live-bottom paint and viewer
        // ownership guards, never from a broad lower mask.
        removeActiveSwitchWebViewLowerDotShield(reason + "-v279-disabled");
    }

    private void removeActiveSwitchWebViewLowerDotShield(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "window.__weztermActiveSwitchLowerDotShieldExpiresAt=0;"
                        + "if(window.__weztermActiveSwitchLowerDotShieldTimer){clearInterval(window.__weztermActiveSwitchLowerDotShieldTimer);window.__weztermActiveSwitchLowerDotShieldTimer=null;}"
                        + "var shield=document.getElementById('wezterm-active-switch-lower-dot-shield');"
                        + "if(shield&&shield.parentNode){shield.parentNode.removeChild(shield);}"
                        + "return 'active-switch-webview-lower-dot-shield-removed:" + sanitizeJavascriptReason(reason) + "';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
    }

    private void hideSessionSwitchPaintShieldSoon(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> hideSessionSwitchPaintShield(reason, generation), Math.max(0, delayMs));
    }

    private void hideSessionSwitchPaintShield(String reason, long generation) {
        if (sessionSwitchPaintShield == null || generation != sessionSwitchPaintShieldGeneration) {
            return;
        }
        sessionSwitchPaintShield.animate().cancel();
        sessionSwitchPaintShield.animate()
                .alpha(0f)
                .setDuration(60)
                .withEndAction(() -> {
                    if (sessionSwitchPaintShield != null && generation == sessionSwitchPaintShieldGeneration) {
                        sessionSwitchPaintShield.setVisibility(View.GONE);
                        sessionSwitchPaintShield.setAlpha(0f);
                    }
                })
                .start();
    }

    private void forceHideSessionSwitchPaintShield(String reason) {
        sessionSwitchPaintShieldGeneration++;
        if (sessionSwitchPaintShield == null) {
            return;
        }
        sessionSwitchPaintShield.animate().cancel();
        sessionSwitchPaintShield.setVisibility(View.GONE);
        sessionSwitchPaintShield.setAlpha(0f);
        forceHideSessionSwitchLowerPaintShield(reason);
    }

    private LinearLayout buildPromptComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setVisibility(View.GONE);
        // WHY: the 2026-06-18 phone screenshot showed the empty native composer
        // consuming terminal space while the keyboard was already open. Keep the
        // native composer as the single typing owner, but make its idle row compact
        // so the terminal still has room to repaint above the IME.
        composer.setPadding(dp(8), dp(PROMPT_COMPOSER_VERTICAL_PADDING_DP),
                dp(8), dp(PROMPT_COMPOSER_VERTICAL_PADDING_DP));
        composer.setBackgroundColor(Color.rgb(24, 24, 37));

        promptComposerInput = new PromptComposerEditText(this);
        promptComposerInput.setSingleLine(false);
        promptComposerInput.setMinLines(1);
        promptComposerInput.setMaxLines(4);
        promptComposerInput.setTextSize(16);
        promptComposerInput.setTextColor(Color.rgb(205, 214, 244));
        promptComposerInput.setHintTextColor(Color.rgb(127, 132, 156));
        promptComposerInput.setHint("Type prompt - tap Send");
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
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                // WHY: phone Enter/IME action must match the visible Send button,
                // just like desktop Enter submits the current prompt. Keep both
                // paths on the single `/submit-text` paste+Enter route so Enter
                // cannot become a raw tmux key while a native draft is visible.
                submitDockedPrompt();
                return true;
            }
            return false;
        });
        promptComposerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (promptComposerProgrammaticTextChange || !isDockedPromptComposerVisible()) {
                    return;
                }
                // WHY: do not mirror every Android text change into tmux. That
                // old hidden `/draft-delta` mirror is the exact repeated regression:
                // Samsung/Gboard/voice composition could paste partial text into
                // tmux invisibly, duplicate words, leave Backspace with nothing
                // local to delete, or finish in the wrong tab after Active
                // switching. Normal phone typing is local-only until the visible
                // toolbar Send button calls the single `/submit-text` path.
                if (editable.length() == 0 || !hasStableWindowId(promptComposerDraftTargetKey)) {
                    // WHY: once a visible native draft has text, its target is
                    // pinned to the stable tmux `@windowId` where typing started.
                    // Toolbar `/active` polling can change `currentPhoneWindowId`
                    // while the composer is still open; retargeting on every edit
                    // is how a correction can paste into another session.
                    promptComposerDraftTargetKey = promptComposerTargetKey();
                }
                promptComposerDraftLocalGeneration++;
            }
        });
        // WHY: do not add another Send/Cancel row below the existing toolbar. That
        // looked like hidden duplicate controls and made users think text was not
        // going into the terminal. The existing thumb-side Start button becomes Send
        // while this native composer is open, so there is one visible action path.
        composer.addView(promptComposerInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(PROMPT_COMPOSER_INPUT_HEIGHT_DP)
        ));

        return composer;
    }

    private TextView buildSessionTitleStrip() {
        TextView title = new TextView(this);
        // WHY: phone users lose the desktop tmux title bar but still need a
        // constant target check before typing/sending. Keep this native, compact,
        // and read-only so it cannot resize tmux or steal WebView focus.
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTextSize(12);
        title.setTextColor(Color.rgb(166, 173, 200));
        title.setBackgroundColor(Color.rgb(24, 24, 37));
        title.setPadding(dp(8), 0, dp(8), 0);
        title.setText("WEzTerm");
        title.setContentDescription("Active session title");
        title.setOnClickListener(view -> showRememberedUploadForCurrentWindow());
        title.setOnLongClickListener(view -> pasteRememberedUploadForCurrentWindow());
        return title;
    }

    private LinearLayout bottomBar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(dp(5), dp(3), dp(5), dp(3));
        toolbar.setMinimumHeight(dp(TOOLBAR_HEIGHT_DP));
        toolbar.setBackgroundColor(Color.rgb(24, 24, 37));
        LinearLayout topRow = toolbarRow();
        LinearLayout bottomRow = toolbarRow();
        toolbarStatusDot = toolbarStatusDotView();
        topRow.addView(toolbarStatusDot, new LinearLayout.LayoutParams(
                dp(18),
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        topRow.addView(toolbarNavigationButton("Active", v -> showActiveSessions()));
        topRow.addView(toolbarNavigationButton("Old", v -> showOldSessions()));
        topRow.addView(toolbarNavigationButton("Workspace", v -> showWorkspaces()));
        topRow.addView(toolbarNavigationButton("New", v ->
                controlAndSettleLiveBottom("/new?fast=1", "", "new-session")));
        // WHY: during upgrades, the user should not have to close the Android
        // task or hunt for the same tmux tab just to refresh the terminal
        // transport. This button preserves the current tmux window, returns it
        // to live bottom, reloads only the WebView/ttyd connection, and focuses
        // xterm again.
        topRow.addView(toolbarNavigationButton("Refresh", v -> refreshTerminalTransport()));
        // WHY: live-bottom is the user's most frequent recovery when one-finger
        // return-to-bottom still misses the final prompt line. Keep it as a one-tap
        // button on the right side of the top row instead of burying it in the
        // fallback Scroll dialog. This must call the fast `/live-bottom` path, not
        // the heavy proof `/scroll?where=bottom` path that caused refresh loops.
        topRow.addView(toolbarNavigationButton("Bottom", v -> goLiveBottom()));
        // WHY: v1.33 removed visible Live/Read/View access and stranded the
        // proven scrollback/top/reader recovery paths behind an uncalled method.
        // Keep one plain Scroll entry on the main bar so one-finger gesture bugs,
        // Codex transcript pager drift, or keyboard focus failures never leave
        // the phone with no way back to history top, page movement, or reader mode.
        Button scrollButton = toolbarNavigationButton("Scroll", v -> showViewControls());
        scrollButton.setOnLongClickListener(v -> {
            // WHY: Scroll must stay a small scroll-only fallback. The command
            // palette remains available for less common actions, but hiding it
            // behind long-press prevents the old giant "Terminal Controls" menu
            // from returning when the user only wants live bottom/top/page moves.
            hideDockedPromptComposerForNavigation("toolbar-scroll-long-press");
            showCommandPalette();
            return true;
        });
        bottomRow.addView(scrollButton);
        // WHY: phone paste must be a first-class action, not a keyboard long-press
        // trick. The button opens explicit copy/paste controls backed by Android
        // clipboard APIs and tmux paste buffers, so prompts can be moved between
        // phone apps and the exact active desktop pane.
        Button copyPasteButton = toolbarNavigationButton("Copy/Paste", v -> showCopyPasteControls());
        copyPasteButton.setOnLongClickListener(v -> {
            // WHY: upload is also available as its own toolbar button, but a
            // long-press here preserves muscle memory for "send phone content into
            // this terminal" without hiding the older copy/paste menu.
            hideDockedPromptComposerForNavigation("toolbar-copy-paste-long-press");
            pickMediaForUpload();
            return true;
        });
        bottomRow.addView(copyPasteButton);
        // WHY: screenshots, videos, PDFs, and other reference files need a one-tap
        // path from the app chrome itself. Keeping Upload separate from Copy/Paste
        // avoids burying the fastest media path in a dialog while preserving every
        // existing toolbar control that prior plan receipts protect.
        bottomRow.addView(toolbarNavigationButton("Upload", v -> pickMediaForUpload()));
        // WHY: Close kills the selected tmux window — the other destructive action,
        // so it shares Stop's red role. Construction still goes through the guarded
        // toolbarNavigationButton("Close", v -> confirmClose()) call; the button is
        // only tinted afterward.
        Button closeButton = toolbarNavigationButton("Close", v -> confirmClose());
        applyToolbarActionRole(closeButton, Color.rgb(243, 139, 168), Color.rgb(245, 194, 231));
        bottomRow.addView(closeButton);
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
        // WHY: Start (and its visible `Send` state while the composer is open) is the
        // positive go action — tint it the green role plate. The Start<->Send toggle
        // only calls setText, never re-applies a background, so this single tint holds
        // for both labels.
        applyToolbarActionRole(startToolbarButton, Color.rgb(166, 227, 161), Color.rgb(148, 226, 213));
        bottomRow.addView(startToolbarButton);
        Button stopButton = toolbarButton("Stop", v -> stopCurrentTask());
        stopButton.setOnLongClickListener(v -> {
            hideDockedPromptComposerForNavigation("toolbar-stop-long-press");
            showKeyControls();
            return true;
        });
        // WHY: Stop interrupts the running task (Escape) — tint it the red
        // destructive role so it reads as "stop/danger" at a glance, distinct from
        // the green Start beside it.
        applyToolbarActionRole(stopButton, Color.rgb(243, 139, 168), Color.rgb(245, 194, 231));
        bottomRow.addView(stopButton);
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
        row.setPadding(0, dp(1), 0, dp(1));
        return row;
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = button(label, listener);
        if (!"Scroll".equals(label) && !"Copy/Paste".equals(label) && !"Start".equals(label)) {
            installPlainToolbarTapHandler(button);
        }
        // WHY: 10-11sp toolbar labels were a logged "font too small" complaint on
        // QHD, but real v2.62 proof showed `Workspace` clipping at 12sp inside
        // the six-column toolbar. Keep short commands large and step long labels
        // down so the visible APK button still says Workspace, not Workspac.
        button.setTextSize(label.length() >= 9 ? 10 : 13);
        button.setSingleLine(true);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        // WHY: the 2026-06-23 bottom proof showed the fixed two-row toolbar had
        // become oversized phone chrome. Keep two thumb rows and the same buttons,
        // but cap each row to a compact 42dp target so the terminal can use the
        // viewport instead of losing it to button real estate.
        button.setMinHeight(dp(42));
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

    private Button toolbarNavigationButton(String label, View.OnClickListener listener) {
        return toolbarButton(label, view -> {
            hideDockedPromptComposerForNavigation("toolbar-" + label.toLowerCase(Locale.US));
            listener.onClick(view);
        });
    }

    // WHY: v2.05 rendered all 11 toolbar buttons in identical slate chrome, so
    // Start (send Enter), Stop (interrupt), and Close (kill the tmux window) looked
    // exactly like neutral navigation and forced read-then-tap on the two most
    // consequential one-handed controls. Color-code by action role using the SAME
    // Catppuccin plates the Resume/Close dialog buttons already use — green for the
    // go/Send action, red for destructive Stop/Close — so the operator recognizes
    // intent by color, not by reading each label. Applied AFTER construction so the
    // guarded toolbarButton()/toolbarNavigationButton() label strings, the ripple,
    // flashTap press feedback, and the ACTION_DOWN tap handler are all preserved.
    // Dark text Color.rgb(30,30,46) matches the dialogs and keeps AA contrast on the
    // colored plates. Start and Stop stay separate buttons (v1.54) — color reinforces
    // the split, it must never merge them into one smart button.
    private void applyToolbarActionRole(Button button, int baseColor, int rippleColor) {
        setTouchableBackground(button, baseColor, rippleColor);
        button.setTextColor(Color.rgb(30, 30, 46));
    }

    private TextView toolbarStatusDotView() {
        TextView dot = new StatusDotTextView(this);
        dot.setText("●");
        // WHY: the single always-visible status dot was a 10sp glyph — the app's only
        // at-a-glance state channel but the smallest thing on the bar. Raised to 14sp
        // for faster Working/Ready/Problem recognition. It still fits the fixed dp(18)
        // status cell and the weighted top row, and the pulse stays dot-only/lifecycle-
        // scoped (startStatusDotPulse animates View.ALPHA only) per v1.93.
        dot.setTextSize(14);
        dot.setGravity(android.view.Gravity.CENTER);
        dot.setIncludeFontPadding(false);
        dot.setContentDescription("Session status");
        // WHY: the user can spend minutes reading scrollback while a Codex tab
        // finishes or starts waiting for approval. This tiny dot reuses the same
        // control-server status evidence and colors as Active Sessions, but it lives
        // in the always-visible button area so the user does not need to open the
        // tab picker just to learn whether the current tab is Working/Ready/Done.
        dot.setOnClickListener(v -> showActiveSessions());
        applySessionStatusDot(dot, "unknown", false, "Unknown");
        return dot;
    }

    private void applySessionStatusDot(TextView dot, String status, boolean needsAttention, String statusLabel) {
        if (dot == null) {
            return;
        }
        dot.setContentDescription(statusLabel == null || statusLabel.isEmpty() ? "Session status" : statusLabel);
        if ("running".equals(status)) {
            dot.setTextColor(Color.rgb(166, 227, 161));
            startStatusDotPulse(dot);
        } else if ("problem".equals(status)) {
            stopStatusDotPulse(dot);
            dot.setTextColor(Color.rgb(243, 139, 168));
        } else if (needsAttention) {
            stopStatusDotPulse(dot);
            dot.setTextColor(Color.rgb(249, 226, 175));
        } else {
            stopStatusDotPulse(dot);
            dot.setTextColor(Color.rgb(127, 132, 156));
        }
    }

    private void startStatusDotPulse(TextView dot) {
        Object tag = dot.getTag();
        if (tag instanceof ObjectAnimator) {
            ObjectAnimator existing = (ObjectAnimator) tag;
            if (existing.isStarted() || existing.isRunning()) {
                return;
            }
        }
        stopStatusDotPulse(dot);
        // WHY: desktop tmux lag came from animating/redrawing more than the fixed
        // status dot. Keep the phone pulse on the tiny TextView's alpha property
        // only, reuse it while the state stays running, and cancel it on detach or
        // pause so background WEzTerm cannot keep invalidating the WebView surface.
        dot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dot.setAlpha(STATUS_DOT_FULL_ALPHA);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(
                dot,
                View.ALPHA,
                STATUS_DOT_DIM_ALPHA,
                STATUS_DOT_FULL_ALPHA
        );
        pulse.setDuration(STATUS_DOT_PULSE_MS);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        dot.setTag(pulse);
        pulse.start();
    }

    private void stopStatusDotPulse(TextView dot) {
        if (dot == null) {
            return;
        }
        Object tag = dot.getTag();
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel();
        }
        dot.setTag(null);
        dot.animate().cancel();
        dot.clearAnimation();
        dot.setLayerType(View.LAYER_TYPE_NONE, null);
        dot.setAlpha(STATUS_DOT_FULL_ALPHA);
    }

    private void stopStatusDotPulsesInTree(View root) {
        if (root == null) {
            return;
        }
        if (root instanceof StatusDotTextView) {
            stopStatusDotPulse((TextView) root);
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            stopStatusDotPulsesInTree(group.getChildAt(index));
        }
    }

    private void scheduleToolbarStatusDotRefresh(long delayMs) {
        if (toolbarStatusDot == null || !activityResumed) {
            return;
        }
        long generation = ++toolbarStatusGeneration;
        uiHandler.postDelayed(() -> {
            if (generation != toolbarStatusGeneration || !activityResumed) {
                return;
            }
            refreshToolbarStatusDot(generation);
        }, Math.max(0, delayMs));
    }

    private void refreshToolbarStatusDot(long generation) {
        if (toolbarStatusDot == null || !activityResumed || generation != toolbarStatusGeneration) {
            return;
        }
        // WHY: this poll only feeds the compact title strip/status dot. Keep it
        // on the read-only active endpoint so a stale APK cannot resize the
        // shared tmux window while desktop/web clients are open.
        getJson("/active?readOnly=1", payload -> {
            if (generation != toolbarStatusGeneration || !activityResumed) {
                return;
            }
            JSONObject window = payload.optJSONObject("window");
            if (window != null) {
                rememberActivePhoneWindow(window, "toolbar-status");
                updateSessionTitleStrip(window);
                applySessionStatusDot(
                        toolbarStatusDot,
                        window.optString("status", "unknown"),
                        window.optBoolean("needsAttention", false),
                        window.optString("statusLabel", "Unknown")
                );
            }
            scheduleToolbarStatusDotRefresh(TOOLBAR_STATUS_POLL_MS);
        }, exc -> {
            if (generation != toolbarStatusGeneration || !activityResumed) {
                return;
            }
            updateSessionTitleStrip("WEzterm control unreachable");
            applySessionStatusDot(toolbarStatusDot, "unknown", true, "WEzterm control unreachable");
            scheduleToolbarStatusDotRefresh(TOOLBAR_STATUS_POLL_MS);
        });
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

    private void installPlainToolbarTapHandler(Button button) {
        // WHY: real phone proof showed center taps on the `Active` toolbar button
        // could be ignored while an offset tap inside the same visible button
        // worked. Plain toolbar buttons have no protected long-press secondary
        // action, so fire on ACTION_DOWN before Samsung's IME/composer layout can
        // cancel or move the ACTION_UP. Do not install this on Scroll, Copy/Paste,
        // or Start; those buttons intentionally keep long-press behavior.
        button.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setPressed(true);
                    view.performClick();
                    return true;
                case MotionEvent.ACTION_UP:
                    view.setPressed(false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    return true;
                default:
                    return true;
            }
        });
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
        keepScreenAwakeForActiveTerminal();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.rgb(16, 16, 20));
            // WHY: Samsung's three-button navigation area is outside the app's
            // clickable toolbar, but painting it black made it read as a giant dead
            // gap under WEzTerm's buttons. Match the toolbar plate so the system
            // nav strip is visually attached instead of a separate black spacer.
            window.setNavigationBarColor(Color.rgb(24, 24, 37));
        }
        hideNavigationDeadStrip("configure-window");
    }

    private void hideNavigationDeadStrip(String reason) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        View decor = window.getDecorView();
        if (decor == null) {
            return;
        }
        // WHY: matching the navigation-bar color still left a full Android nav
        // region below the toolbar that the user read as wasted black space. Use
        // Android's immersive navigation-bar API with transient swipe reveal so
        // the APK buttons occupy the actual bottom without a permanent spacer.
        long now = System.currentTimeMillis();
        boolean requestInsets = now - lastNavigationHideAtMs > 900;
        lastNavigationHideAtMs = now;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
                controller.hide(WindowInsets.Type.navigationBars());
            }
            if (requestInsets) {
                decor.post(decor::requestApplyInsets);
            }
            return;
        }
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void keepScreenAwakeForActiveTerminal() {
        // WHY: real phone proof and real phone coding sessions are long-running.
        // If Android locks while WEzterm is the foreground terminal, ADB proof
        // falls onto the secure Bouncer and the user loses the live control
        // surface. Reassert the window flag on create/resume and set
        // keepScreenOn on the terminal views below; this prevents sleep only
        // while this activity is visible and does not bypass the lockscreen or
        // change any tmux/scroll/zoom behavior.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (webView != null) {
            webView.setKeepScreenOn(true);
        }
        if (historyTouchOverlay != null) {
            historyTouchOverlay.setKeepScreenOn(true);
        }
    }

    private void configureWebView(WebView view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setKeepScreenOn(true);
        // WHY: the terminal body must fail dark, not purple/blue, while xterm is
        // reconnecting or repainting. The real dotted-canvas regression was in
        // the WebView/xterm render layer, not tmux output; leaving the native
        // WebView background slate lets blank canvas cells show through during
        // session switches.
        view.setBackgroundColor(Color.BLACK);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        view.setScrollContainer(false);
        // WHY: Android's edge effects can make a WebView pan look like the page
        // is refreshing or fighting the user's finger. The terminal already has
        // explicit Live/Read controls, so native overscroll feedback is noise.
        view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        view.addOnLayoutChangeListener((changedView, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) == (oldRight - oldLeft) && (bottom - top) == (oldBottom - oldTop)) {
                return;
            }
            // WHY: Android can shrink the WebView for the native composer,
            // keyboard, or system bars without a ttyd page reload. If xterm keeps
            // painting against the previous row/canvas height, the phone exposes a
            // dotted blank field and a zoomed viewer cannot pan to the true prompt
            // bottom. Dispatch a lightweight resize/redraw only; do not revive
            // xterm scrollToBottom, WebView reload, or IME focus bursts here. v2.47
            // also keeps the row-level dot scrubber alive during composer/keyboard
            // layout shrink, because the uploaded 21:23 phone screenshot proved the
            // stale full-view dot grid can appear while the native composer is open,
            // not only after an Active Sessions switch.
            fitTerminalToCurrentViewSoon("webview-layout");
        });
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
        boolean passiveNavigationTapSuppressed =
                System.currentTimeMillis() < terminalBodyTapSuppressedUntilMs;
        if (passiveNavigationTapSuppressed
                && action != MotionEvent.ACTION_DOWN
                && !terminalTouchStartedDuringPassiveSuppression
                && !terminalHistoryDragActive
                && !terminalMultiTouchGesture
                && !terminalHorizontalPanActive) {
            // WHY: passive tab-open suppression exists to swallow the stray
            // ACTION_UP from an Active/Old picker row so it cannot reopen the
            // native composer. It must not also block a real user scroll. If the
            // gesture did not start in this terminal view, consume only that
            // orphaned release/cancel path; terminal ACTION_DOWN below still owns
            // real scrolls so it can cancel black/dot masks and hide the keyboard.
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                terminalHistoryDragActive = false;
                terminalMultiTouchGesture = false;
                terminalTouchExceededTapSlop = false;
                terminalHorizontalPanActive = false;
                terminalTouchReachedLiveBottom = false;
                terminalTouchStartedInHistoryViewport = false;
                terminalTouchStartedDuringPassiveSuppression = false;
                recycleTerminalVelocityTracker();
                recycleTerminalViewerDownEvent();
            }
            return true;
        }
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
                cancelHistoryMomentum();
                clearPendingHistoryScroll();
                cancelViewerTypingPositionRetries("multi-touch");
                cancelLiveInputVisibilityRetries("multi-touch");
            }
            terminalMultiTouchGesture = true;
            terminalHistoryDragActive = false;
            allowViewerPanBriefly();
            return forwardTouchToViewer(event);
        }
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) {
            pinTerminalViewportLocal();
        }

        if (action == MotionEvent.ACTION_DOWN) {
            boolean startedInsidePassiveSuppression = passiveNavigationTapSuppressed;
            if (passiveNavigationTapSuppressed) {
                // WHY: passive tab/open suppression is for orphan releases from a disappearing picker,
                // not for the user's next real tap. Once a new
                // terminal ACTION_DOWN starts, let ACTION_UP open the native composer
                // promptly; otherwise repeated switch-settle calls can make typing
                // feel blocked for many seconds.
                terminalBodyTapSuppressedUntilMs = 0;
                passiveNavigationTapSuppressed = false;
            }
            if (!passiveNavigationTapSuppressed) {
                cancelXtermBlankTailMask("terminal-touch");
            }
            resetTerminalVelocityTracker(event);
            terminalTouchStartX = event.getX();
            terminalTouchStartY = event.getY();
            terminalLastHistoryDragY = terminalTouchStartY;
            terminalLastViewerPanX = terminalTouchStartX;
            terminalLastHistoryDragEventAtMs = event.getEventTime();
            terminalTouchDownWallClockMs = System.currentTimeMillis();
            terminalHistoryDragActive = false;
            terminalMultiTouchGesture = false;
            terminalTouchExceededTapSlop = false;
            terminalHorizontalPanActive = false;
            terminalBottomRestoreInFlight = false;
            terminalForwardingTouchToViewer = false;
            terminalTouchStartedDuringPassiveSuppression = startedInsidePassiveSuppression;
            recycleTerminalViewerDownEvent();
            terminalViewerDownEvent = MotionEvent.obtain(event);
            terminalTouchReachedLiveBottom = false;
            terminalTouchStartedInHistoryViewport = terminalHistoryViewportActive || readModeSuppressesKeyboard;
            terminalTouchStableWindowId = visibleTerminalTargetKey();
            // WHY: touch-scroll HTTP responses can arrive after the finger has
            // already changed direction, released, or started a new gesture. Tagging
            // every request with this generation keeps stale server replies from
            // triggering the bottom restore/refresh jump on a later gesture.
            terminalTouchGestureGeneration++;
            cancelHistoryMomentum();
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
                panZoomedViewerHorizontally(event);
                return forwardTouchToViewer(event);
            }
            float dx = event.getX() - terminalTouchStartX;
            float dy = event.getY() - terminalTouchStartY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            if (absDx > terminalTouchSlop || absDy > terminalTouchSlop) {
                terminalTouchExceededTapSlop = true;
            }
            if (!terminalHistoryDragActive && shouldHandOffToViewerHorizontalPan(absDx, absDy)) {
                // WHY: one-finger horizontal movement is the user's line-reading
                // pan inside ttyd/WebView. The app must not treat it as a live
                // tap or a server history gesture, or xterm focus will recenter
                // the viewport and recreate the "snaps back left" bug. Hand off
                // as soon as horizontal intent is clear; waiting for a large dx
                // swallows the first part of the native pan and kills momentum.
                terminalHorizontalPanActive = true;
                terminalLastViewerPanX = terminalTouchStartX;
                allowViewerPanBriefly();
                cancelViewerTypingPositionRetries("horizontal-pan");
                cancelLiveInputVisibilityRetries("horizontal-pan");
                panZoomedViewerHorizontally(event);
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
                if (terminalTouchStartedDuringPassiveSuppression) {
                    hideDockedPromptComposerForNavigation("passive-nav-scroll-start");
                }
                terminalHistoryDragActive = true;
                terminalLastHistoryDragY = terminalTouchStartY;
                enterReadMode();
                keepCaptureRendererPulsingDuringTouch("touch-scroll-start");
            }

            processHistoryDragEventSamples(event);
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
            boolean startedDuringPassiveSuppression = terminalTouchStartedDuringPassiveSuppression;
            if (action == MotionEvent.ACTION_UP && terminalHistoryDragActive) {
                dispatchHistoryReleaseFling(event);
                keepCaptureRendererPulsingDuringTouch("touch-scroll-release");
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
            terminalTouchStartedDuringPassiveSuppression = false;
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
                // WHY: the composer itself is the success signal. A tap-to-type
                // success Toast sits over the same bottom area the user is trying to
                // inspect, recreating the v2.57 popup-over-prompt regression while
                // the keyboard/composer is opening.
                restoreLiveForTyping("");
                recycleTerminalViewerDownEvent();
                return true;
            }
            if (startedDuringPassiveSuppression
                    && System.currentTimeMillis() < terminalBodyTapSuppressedUntilMs
                    && action == MotionEvent.ACTION_UP
                    && !consumed
                    && !wasMultiTouch
                    && !wasHorizontalPan
                    && !movedPastTapSlop) {
                // WHY: this is the protected passive-navigation tap swallow for
                // stale timed releases only. A fresh ACTION_DOWN now clears the
                // timeout so deliberate tap-to-type opens the native composer; a
                // simple tap right after a tab switch/Bottom should do nothing only
                // when it is that stale release without a fresh terminal down event;
                // a vertical drag from the same window already bypassed this and became
                // tmux history scroll above.
                recycleTerminalViewerDownEvent();
                return true;
            }
            if (action == MotionEvent.ACTION_UP
                    && reachedLiveBottom) {
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

    private boolean shouldHandOffToViewerHorizontalPan(float absDx, float absDy) {
        if (isViewerZoomed()) {
            // WHY: zoomed horizontal point-of-view pan has natural thumb drift.
            // The old normal-scale dominance check let a mostly-right drag become
            // ambiguous and then tmux-owned vertical history, which is exactly the
            // user-reported "cannot move my point of view to the right" failure.
            // Keep clear vertical drags tmux-owned, but hand off zoomed side-pan
            // before the WebView loses the native gesture stream.
            return absDx >= terminalTouchSlop
                    && absDx >= absDy * ZOOMED_HORIZONTAL_PAN_DRIFT_RATIO;
        }
        return absDx >= terminalTouchSlop * 1.25f
                && absDx > absDy * 1.05f;
    }

    private void panZoomedViewerHorizontally(MotionEvent event) {
        if (webView == null || event == null || event.getPointerCount() != 1 || !isViewerZoomed()) {
            return;
        }
        float x = event.getX();
        int deltaX = Math.round(terminalLastViewerPanX - x);
        terminalLastViewerPanX = x;
        if (deltaX == 0) {
            return;
        }
        // WHY: v2.75 proved that replaying the original DOWN into WebView was
        // necessary but not sufficient once WEzTerm had already consumed the
        // gesture start. Keep zoomed one-finger horizontal reading viewer-owned by
        // directly scrolling the WebView's horizontal viewport; do not reroute this
        // through tmux history, `/touch-scroll`, font resize, or zoom reset.
        allowViewerPanBriefly();
        webView.scrollBy(deltaX, 0);
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

    private void cancelHistoryMomentum() {
        // WHY: active post-release inertia must die when a new touch or pinch takes
        // over. Leaving stale momentum state alive can replay scroll movement after
        // the visible APK target has changed, and the user's stop touch must halt
        // the fling immediately like a native scroller. Keep the last-fling
        // direction/time separately so a deliberate second flick can accelerate.
        terminalHistoryMomentumActive = false;
        terminalHistoryMomentumFrameScheduled = false;
        terminalHistoryMomentumVelocityPxPerSec = 0f;
        terminalHistoryMomentumFramesRemaining = 0;
        terminalHistoryMomentumGeneration = 0;
        terminalHistoryMomentumWhere = "";
        terminalHistoryMomentumTargetKey = "";
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
        beginForwardingTouchToViewer(event);
        webView.onTouchEvent(event);
        return true;
    }

    private void beginForwardingTouchToViewer(MotionEvent currentEvent) {
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
            MotionEvent downEvent = downEventForViewerHandoff(currentEvent);
            webView.onTouchEvent(downEvent);
            downEvent.recycle();
        }
    }

    private void forwardTapToViewer(MotionEvent upEvent) {
        beginForwardingTouchToViewer(upEvent);
        if (webView != null) {
            webView.onTouchEvent(upEvent);
        }
    }

    private MotionEvent downEventForViewerHandoff(MotionEvent currentEvent) {
        if (currentEvent == null
                || currentEvent.getActionMasked() == MotionEvent.ACTION_UP
                || currentEvent.getPointerCount() < 1
                || (!terminalTouchExceededTapSlop && !terminalMultiTouchGesture && !terminalHorizontalPanActive)) {
            return MotionEvent.obtain(terminalViewerDownEvent);
        }
        if (terminalHorizontalPanActive && !terminalMultiTouchGesture) {
            // WHY: one-finger zoomed horizontal pan must replay the original DOWN.
            // v2.72 synthesized DOWN at the first MOVE point, which erased the
            // horizontal delta WebView needs to visually move the zoomed viewport.
            // Keep the synthetic-current handoff for pinch, but let one-finger
            // line-reading pan deliver the real down->move displacement.
            return MotionEvent.obtain(terminalViewerDownEvent);
        }
        // WHY: WEzterm holds ACTION_DOWN until it knows whether the gesture is
        // tmux-history or Android/WebView-owned. Replaying the original DOWN after
        // the finger has already moved makes native WebView pinch/pan reconcile a
        // stale start point with the current two-finger event, which feels like the
        // zoomed viewport jumps. For non-tap viewer handoff, synthesize the DOWN at
        // the current primary pointer so WebView starts the native gesture where
        // the user actually began panning/zooming.
        return MotionEvent.obtain(
                currentEvent.getDownTime(),
                currentEvent.getEventTime(),
                MotionEvent.ACTION_DOWN,
                currentEvent.getX(0),
                currentEvent.getY(0),
                currentEvent.getMetaState()
        );
    }

    private boolean isTopTerminalTap(MotionEvent event) {
        // WHY: only the tmux/ttyd chrome near the top needs a real WebView tap
        // after WEzterm consumes ACTION_DOWN for gesture classification. Terminal
        // body taps are typing-focus requests, so forwarding them to xterm and then
        // running a native focus fallback gives Android two competing IME owners.
        // WHY: the old 48dp strip included the first shell/prompt row on the
        // real phone. Tapping that row forwarded the gesture to xterm's hidden
        // textarea, bypassing the native composer and reviving duplicate-word
        // input. Only the actual tmux/ttyd tab strip is WebView-owned.
        return event.getY() <= dp(24);
    }

    private void recycleTerminalViewerDownEvent() {
        if (terminalViewerDownEvent != null) {
            terminalViewerDownEvent.recycle();
            terminalViewerDownEvent = null;
        }
        terminalForwardingTouchToViewer = false;
    }

    private void processHistoryDragEventSamples(MotionEvent event) {
        // WHY: Android may batch multiple MOVE coordinates into one MotionEvent.
        // Using only the final point makes the laptop/tmux state look smooth while
        // the APK jumps in larger chunks. Process historical Y samples with their
        // own event times, but keep the protected `/touch-scroll` throttle and
        // repeat caps so a fast flick is still fast and a slow drag stays readable.
        int historySize = event.getHistorySize();
        for (int i = 0; i < historySize; i++) {
            processHistoryDragSample(event.getHistoricalY(i), event.getHistoricalEventTime(i));
        }
        processHistoryDragSample(event.getY(), event.getEventTime());
    }

    private void processHistoryDragSample(float y, long eventTimeMs) {
        float step = y - terminalLastHistoryDragY;
        // WHY: v1.42 used page-sized HTTP scrolls. That could not paint
        // continuously under a finger, so the screen appeared frozen and then
        // jumped to a random-looking page. Use line-sized tmux copy-mode movement
        // for drag; the explicit Scroll menu still owns jump-to-top, page-up/down,
        // reader, and live-bottom recovery.
        int lineThreshold = Math.max(terminalTouchSlop, dp(HISTORY_DRAG_LINE_THRESHOLD_DP));
        if (Math.abs(step) < lineThreshold
                || eventTimeMs - lastHistoryDragAtMs < HISTORY_DRAG_THROTTLE_MS) {
            return;
        }
        int repeats = historyDragRepeats(step, lineThreshold, eventTimeMs);
        terminalLastHistoryDragY = y;
        terminalLastHistoryDragEventAtMs = eventTimeMs;
        lastHistoryDragAtMs = eventTimeMs;
        String where = step > 0 ? "lineUp" : "lineDown";
        if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
            // WHY: once tmux has hit the live bottom, extra downward finger motion
            // has nowhere meaningful to go. Sending more lineDown requests would
            // re-enter/cancel copy-mode on every MOVE and looks like a page
            // refresh/bounce at the bottom. Swallow only the continued downward
            // edge; reversing upward still immediately re-enters tmux history.
            return;
        }
        if ("lineUp".equals(where)) {
            terminalTouchReachedLiveBottom = false;
        }
        keepCaptureRendererPulsingDuringTouch("touch-scroll-move");
        scrollTerminalFromTouch(where, repeats);
    }

    private int historyDragRepeats(float step, int lineThreshold, long eventTimeMs) {
        float velocity = 0f;
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.computeCurrentVelocity(1000);
            velocity = Math.abs(terminalVelocityTracker.getYVelocity());
        }
        long eventDeltaMs = Math.max(1, eventTimeMs - terminalLastHistoryDragEventAtMs);
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
        // dispatched history step. Distance alone must not promote a slow drag:
        // the latest P3 complaint was that slowed constants still felt jumpy
        // because backend cadence gaps turned one slow MOVE into a fast batch.
        int repeats = HISTORY_DRAG_PAGES_PER_STEP;
        boolean fastByVelocity = velocity >= HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC;
        boolean flingByVelocity = velocity >= HISTORY_DRAG_FLING_VELOCITY_PX_PER_SEC;
        if (flingByVelocity && distanceLines >= HISTORY_DRAG_FLING_DISTANCE_LINES) {
            repeats = HISTORY_DRAG_FLING_MOVE_REPEATS;
        } else if (fastByVelocity && distanceLines >= HISTORY_DRAG_FAST_DISTANCE_LINES) {
            repeats = HISTORY_DRAG_FAST_MOVE_REPEATS;
        } else if (distanceLines >= 2f) {
            repeats = HISTORY_DRAG_SLOW_MOVE_MAX_REPEATS;
        }
        return Math.max(1, Math.min(HISTORY_DRAG_MAX_PAGES_PER_STEP, repeats));
    }

    private void dispatchHistoryReleaseFling(MotionEvent event) {
        float totalDy = event.getY() - terminalTouchStartY;
        float absDy = Math.abs(totalDy);
        long durationMs = Math.max(1, event.getEventTime() - event.getDownTime());
        long wallDurationMs = terminalTouchDownWallClockMs > 0
                ? Math.max(1, System.currentTimeMillis() - terminalTouchDownWallClockMs)
                : durationMs;
        float signedReleaseVelocity = totalDy * 1000f / durationMs;
        if (terminalVelocityTracker != null) {
            terminalVelocityTracker.computeCurrentVelocity(1000);
            float trackerVelocity = terminalVelocityTracker.getYVelocity();
            if (Math.abs(trackerVelocity) > Math.abs(signedReleaseVelocity)) {
                signedReleaseVelocity = trackerVelocity;
            }
        }
        float releaseVelocity = Math.abs(signedReleaseVelocity);
        int lineThreshold = Math.max(terminalTouchSlop, dp(HISTORY_DRAG_LINE_THRESHOLD_DP));
        if (absDy < lineThreshold * HISTORY_DRAG_RELEASE_MIN_LINES
                || releaseVelocity < HISTORY_DRAG_FAST_VELOCITY_PX_PER_SEC) {
            return;
        }
        if (wallDurationMs > HISTORY_DRAG_RELEASE_LONG_GESTURE_MS) {
            // WHY: a long deliberate read-drag can cover enough distance to look
            // fast by average velocity, but it must stop when the finger stops. Only
            // quick flicks should start inertia; synthetic/WebView-routed UP events
            // can reset MotionEvent downTime or report a bogus high tracker velocity
            // at the end of slow movement, so use the app-observed ACTION_DOWN clock.
            return;
        }
        String where = signedReleaseVelocity > 0 ? "lineUp" : "lineDown";
        if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
            // WHY: a release fling in the same downward direction after tmux has
            // already reported live bottom would queue extra lineDown requests at
            // the edge. That is the exact "refresh/bounce before I get to the
            // bottom" symptom; wait for finger-up restore instead.
            return;
        }
        if (where.equals(lastHistoryFlingWhere)
                && System.currentTimeMillis() - lastHistoryFlingAtMs <= HISTORY_DRAG_REPEATED_FLING_WINDOW_MS) {
            // WHY: native-feeling scroll accelerates when the user flicks again in
            // the same direction. Add a bounded velocity boost instead of stacking
            // delayed HTTP bursts that would replay after the user stops the fling.
            releaseVelocity += HISTORY_DRAG_REPEATED_FLING_BOOST_REPEATS
                    * HISTORY_DRAG_MOMENTUM_REPEAT_VELOCITY_DIVISOR;
        }
        lastHistoryFlingWhere = where;
        lastHistoryFlingAtMs = System.currentTimeMillis();
        int repeats = historyMomentumRepeats(where, releaseVelocity);
        if ("lineDown".equals(where)) {
            // WHY: upward flicks are for racing through old output, so a large burst
            // is useful there. Downward flicks are the return-to-live path. v1.58's
            // tiny downward cap made real phone swipes stall in copy-mode many
            // lines above the prompt, but v1.60's full server-supported touch batch
            // made real downward gestures feel frozen and then jump to the bottom
            // once queued work caught up. Keep downward movement low-repeat and
            // heavily decayed compared with upward history movement so ttyd can repaint intermediate
            // positions while the near-bottom guard still exits copy-mode cleanly.
            // WHY: raw finger distance cannot prove tmux is near live bottom. v2.69
            // removes the old direct-bottom shortcut because a long return flick
            // could jump straight to the prompt before `/touch-scroll` reported a
            // tmux lineDown at or near scroll position 0. Quiet bottom restore is
            // still protected, but only from the server-owned near-bottom signal.
            repeats = Math.min(repeats, HISTORY_DRAG_DOWN_RELEASE_MAX_REPEATS);
        }
        // WHY: real fast flicks produce fewer ACTION_MOVE samples than slow drags,
        // especially through WebView and ADB input. v1.44 therefore moved fewer
        // lines for a fast flick than for a slow drag of the same distance. v2.70's
        // two bounded bursts protected target drift but did not feel like real
        // inertial scrolling. Start with one bounded release step, then run a
        // cancellable decaying momentum loop that a new touch immediately stops.
        final long flingGeneration = terminalTouchGestureGeneration;
        String targetKey = terminalTouchStableWindowId;
        scrollTerminalFromTouch(where, repeats, true, targetKey);
        startHistoryMomentum(where, releaseVelocity * HISTORY_DRAG_MOMENTUM_DECAY, flingGeneration, targetKey);
    }

    private void startHistoryMomentum(String where, float velocityPxPerSec, long gestureGeneration, String targetKey) {
        if (velocityPxPerSec < HISTORY_DRAG_MOMENTUM_STOP_VELOCITY_PX_PER_SEC) {
            return;
        }
        terminalHistoryMomentumActive = true;
        terminalHistoryMomentumFrameScheduled = false;
        terminalHistoryMomentumVelocityPxPerSec = velocityPxPerSec;
        terminalHistoryMomentumFramesRemaining = HISTORY_DRAG_MOMENTUM_MAX_FRAMES;
        terminalHistoryMomentumGeneration = gestureGeneration;
        terminalHistoryMomentumWhere = where;
        terminalHistoryMomentumTargetKey = targetKey == null ? "" : targetKey;
        keepCaptureRendererPulsingDuringTouch("touch-scroll-momentum");
        scheduleHistoryMomentumFrame();
    }

    private void scheduleHistoryMomentumFrame() {
        if (!terminalHistoryMomentumActive || terminalHistoryMomentumFrameScheduled) {
            return;
        }
        terminalHistoryMomentumFrameScheduled = true;
        uiHandler.postDelayed(this::runHistoryMomentumFrame, HISTORY_DRAG_MOMENTUM_FRAME_MS);
    }

    private void runHistoryMomentumFrame() {
        terminalHistoryMomentumFrameScheduled = false;
        if (!terminalHistoryMomentumActive) {
            return;
        }
        if (terminalHistoryMomentumGeneration != terminalTouchGestureGeneration
                || terminalMultiTouchGesture
                || !terminalHistoryViewportActive
                || !readModeSuppressesKeyboard
                || terminalHistoryMomentumFramesRemaining <= 0
                || terminalHistoryMomentumVelocityPxPerSec < HISTORY_DRAG_MOMENTUM_STOP_VELOCITY_PX_PER_SEC) {
            cancelHistoryMomentum();
            return;
        }
        if ("lineDown".equals(terminalHistoryMomentumWhere) && terminalTouchReachedLiveBottom) {
            cancelHistoryMomentum();
            return;
        }
        int repeats = historyMomentumRepeats(terminalHistoryMomentumWhere, terminalHistoryMomentumVelocityPxPerSec);
        scrollTerminalFromTouch(
                terminalHistoryMomentumWhere,
                repeats,
                true,
                terminalHistoryMomentumTargetKey
        );
        terminalHistoryMomentumVelocityPxPerSec *= HISTORY_DRAG_MOMENTUM_DECAY;
        terminalHistoryMomentumFramesRemaining--;
        scheduleHistoryMomentumFrame();
    }

    private int historyMomentumRepeats(String where, float velocityPxPerSec) {
        int maxRepeats = "lineDown".equals(where)
                ? HISTORY_DRAG_MOMENTUM_DOWN_MAX_REPEATS
                : HISTORY_DRAG_MOMENTUM_UP_MAX_REPEATS;
        int repeats = Math.round(velocityPxPerSec / HISTORY_DRAG_MOMENTUM_REPEAT_VELOCITY_DIVISOR);
        return Math.max(1, Math.min(maxRepeats, repeats));
    }

    private void scrollTerminalFromTouch(String where, int repeats) {
        scrollTerminalFromTouch(where, repeats, false, terminalTouchStableWindowId);
    }

    private void scrollTerminalFromTouch(String where, int repeats, boolean fromMomentum, String targetKey) {
        // WHY: normal WebView scrolling moves ttyd/xterm's browser scrollback,
        // which records tmux redraw artifacts instead of the real pane history
        // visible to Codex. Deliberate one-finger vertical drags use the server
        // history path, but now as small lineUp/lineDown commands so the screen
        // tracks the finger instead of jumping by whole pages.
        // WHY: keep one request in flight and coalesce by direction so stale
        // responses cannot fight the user's finger. v2.77 keeps the old "no
        // huge delayed catch-up burst" guard, but stops replacing every slow
        // sample with a single tiny pending step. The 2026-06-26 regression proof
        // showed a 1400 px slow drag moving only about 9 rows. Preserve enough
        // bounded distance inside one network/tmux request window for low-speed
        // tracking, while still capping the replay so slow reading cannot become
        // the old jumpy page-scroll failure.
        int maxRepeats = "lineDown".equals(where)
                ? HISTORY_DRAG_DOWN_MAX_REPEATS
                : HISTORY_DRAG_MAX_PAGES_PER_STEP;
        int boundedRepeats = Math.max(1, Math.min(maxRepeats, repeats));
        long gestureGeneration = terminalTouchGestureGeneration;
        String stableTargetKey = targetKey == null || targetKey.trim().isEmpty()
                ? terminalTouchStableWindowId
                : targetKey.trim();
        if (terminalTouchReachedLiveBottom && "lineDown".equals(where)) {
            return;
        }
        if ("lineUp".equals(where)) {
            terminalTouchReachedLiveBottom = false;
        }
        if (historyScrollRequestInFlight) {
            if (where.equals(pendingHistoryScrollWhere)
                    && pendingHistoryScrollGeneration == gestureGeneration
                    && pendingHistoryScrollTargetKey.equals(stableTargetKey)) {
                if ("lineDown".equals(where) || fromMomentum) {
                    // WHY: returning toward live bottom needs real acceleration
                    // too. Replacing every in-flight lineDown sample with the
                    // newest tiny step made one-finger swipe-up stall far above
                    // the prompt. Accumulate only inside the existing per-request
                    // cap so this cannot replay an unbounded old gesture.
                    pendingHistoryScrollRepeats = Math.min(
                            maxRepeats,
                            pendingHistoryScrollRepeats + boundedRepeats
                    );
                } else if (boundedRepeats <= HISTORY_DRAG_SLOW_MOVE_MAX_REPEATS) {
                    // WHY: slow upward reading movement must track the finger even
                    // when `/touch-scroll` is still in flight. Accumulate a small,
                    // explicit slow cap instead of every MOVE; this fixes the
                    // low-speed "finger moves but rows barely move" regression
                    // without reviving the v1.42 page-sized delayed jump.
                    pendingHistoryScrollRepeats = Math.min(
                            HISTORY_DRAG_SLOW_PENDING_MAX_REPEATS,
                            pendingHistoryScrollRepeats + boundedRepeats
                    );
                } else {
                    // WHY: fast upward movement is a deliberate history flick, not
                    // slow reading. Keep bounded accumulation here so the latest
                    // fast-flick complaint does not regress into sluggish history
                    // movement while slow drags stay small above.
                    pendingHistoryScrollRepeats = Math.min(
                            maxRepeats,
                            pendingHistoryScrollRepeats + boundedRepeats
                    );
                }
            } else {
                pendingHistoryScrollWhere = where;
                pendingHistoryScrollRepeats = boundedRepeats;
                pendingHistoryScrollGeneration = gestureGeneration;
                pendingHistoryScrollTargetKey = stableTargetKey;
            }
            return;
        }
        sendHistoryScrollFromTouch(where, boundedRepeats, gestureGeneration, stableTargetKey);
    }

    private void sendHistoryScrollFromTouch(String where, int repeats, long gestureGeneration, String targetKey) {
        long readModeGeneration = terminalHistoryViewportActive
                ? terminalModeGeneration
                : enterReadMode();
        historyScrollRequestInFlight = true;
        String path = appendStableWindowQuery("/touch-scroll?where=" + urlEncode(where)
                + "&repeat=" + Math.max(1, repeats), targetKey);
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
                cancelHistoryMomentum();
                clearPendingHistoryScroll();
                if (!terminalHistoryDragActive) {
                    restoreTouchLiveBottomQuietly();
                    return;
                }
                if (readModeGeneration == terminalModeGeneration) {
                    keepReadModeIfCurrent(readModeGeneration);
                }
                refreshCaptureRendererSoon("touch-scroll-bottom-edge");
                return;
            }
            if (readModeGeneration == terminalModeGeneration) {
                keepReadModeIfCurrent(readModeGeneration);
            }
            refreshCaptureRendererSoon("touch-scroll");
            keepCaptureRendererPulsingDuringTouch("touch-scroll-response");
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
        String nextTargetKey = pendingHistoryScrollTargetKey;
        clearPendingHistoryScroll();
        if (nextGeneration != terminalTouchGestureGeneration) {
            return;
        }
        sendHistoryScrollFromTouch(nextWhere, Math.max(1, nextRepeats), nextGeneration, nextTargetKey);
    }

    private void keepCaptureRendererPulsingDuringTouch(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        // WHY: the phone-visible terminal is a read-only capture renderer. Tmux can
        // scroll smoothly on the laptop while the APK looks choppy if the renderer
        // waits for the 550 ms poll or a single delayed refresh. Keep `/touch-scroll` itself lightweight,
        // but run a frame-rate bounded repaint loop during an
        // active finger gesture so slow drags show intermediate rows and fast flicks
        // still use the existing VelocityTracker/release-burst path.
        long now = System.currentTimeMillis();
        touchScrollRenderPulseUntilMs = Math.max(
                touchScrollRenderPulseUntilMs,
                now + TOUCH_SCROLL_RENDER_PULSE_WINDOW_MS
        );
        if (touchScrollRenderPulseScheduled) {
            return;
        }
        touchScrollRenderPulseScheduled = true;
        postCaptureRendererPulseFrame(reason);
    }

    private void postCaptureRendererPulseFrame(String reason) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Choreographer.getInstance().postFrameCallback(frameTimeNanos ->
                    runCaptureRendererTouchPulse(reason));
            return;
        }
        uiHandler.post(() -> runCaptureRendererTouchPulse(reason));
    }

    private void runCaptureRendererTouchPulse(String reason) {
        touchScrollRenderPulseScheduled = false;
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > touchScrollRenderPulseUntilMs) {
            return;
        }
        refreshCaptureRendererNow(reason);
        boolean stillTouchScrolling = terminalHistoryDragActive
                || terminalHistoryMomentumActive
                || historyScrollRequestInFlight
                || !pendingHistoryScrollWhere.isEmpty()
                || terminalBottomRestoreInFlight;
        if (!stillTouchScrolling) {
            return;
        }
        touchScrollRenderPulseScheduled = true;
        uiHandler.postDelayed(
                () -> postCaptureRendererPulseFrame(reason),
                TOUCH_SCROLL_RENDER_PULSE_MS
        );
    }

    private void clearPendingHistoryScroll() {
        pendingHistoryScrollWhere = "";
        pendingHistoryScrollRepeats = 0;
        pendingHistoryScrollGeneration = 0;
        pendingHistoryScrollTargetKey = "";
    }

    private void loadTerminal() {
        loadTerminalAtIndex(0, "load");
    }

    private void loadTerminalAtIndex(int index, String reason) {
        terminalUrlIndex = Math.max(0, Math.min(index, TERMINAL_URLS.length - 1));
        activeTerminalBaseUrl = TERMINAL_URLS[terminalUrlIndex];
        int fontSize = prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE);
        markTerminalLoadStarted();
        // WHY: the live host currently answers immediately on the direct Tailnet
        // IP while the MagicDNS name can stall before the phone reaches the
        // control server. Prefer the proven IP and keep MagicDNS only as a
        // fallback so Active/Old titles and the visual renderer come from the
        // same shared server.
        // WHY: the 2026-06-22 gap/black-box regression proved raw ttyd is the
        // wrong visual owner for APK/web: fitting ttyd mutates shared tmux
        // geometry, while not fitting leaves a huge bottom gap. The APK renders
        // the control server's read-only capture stream and keeps tmux commands
        // on stable @windowId control endpoints.
        webView.loadUrl(terminalUrlWithOptions(activeTerminalBaseUrl, fontSize));
        pinTerminalViewportSoon(reason);
        focusTerminalInputSoon(false);
        keepLiveInputVisibleSoon(reason);
        scheduleBlankTerminalWatchdog(reason);
    }

    private String terminalUrlWithOptions(String baseUrl) {
        return terminalUrlWithOptions(baseUrl, prefs.getInt(PREF_FONT_SIZE, DEFAULT_FONT_SIZE));
    }

    private String terminalUrlWithOptions(String baseUrl, int fontSize) {
        return baseUrl
                + "?fontSize=" + fontSize
                // WHY: the capture renderer is read-only, so it must emulate the
                // old readable 132-column APK grid without resizing tmux. Letting
                // it auto-measure the narrow WebView produced the 55-column Claude
                // wrap regression; resizing tmux would revive the Windows/web
                // black-box regression.
                + "&cols=" + APK_CAPTURE_RENDERER_COLS
                + "&disableLeaveAlert=true"
                + "&rendererType=dom"
                + "&customGlyphs=false"
                + "&scrollOnUserInput=true";
    }

    private boolean isKnownTerminalUrl(String url) {
        if (url == null) {
            return false;
        }
        for (String baseUrl : TERMINAL_URLS) {
            if (url.startsWith(baseUrl)) {
                return true;
            }
        }
        return false;
    }

    private void handleTerminalLoadFailure(String failedUrl) {
        wakeLaptopForTerminal("terminal-load-failed");
        if (webView == null || !isKnownTerminalUrl(failedUrl)) {
            scheduleTerminalWakeRetry("terminal-load-failed");
            return;
        }
        if (terminalUrlIndex + 1 < TERMINAL_URLS.length) {
            loadTerminalAtIndex(terminalUrlIndex + 1, "terminal-url-fallback");
            return;
        }
        scheduleTerminalWakeRetry("terminal-load-failed");
    }

    private void scheduleTerminalWakeRetry(String reason) {
        long generation = ++terminalWakeRetryGeneration;
        uiHandler.postDelayed(() -> {
            if (generation != terminalWakeRetryGeneration || webView == null || !activityResumed) {
                return;
            }
            // WHY: after a real sleep/wake, retry the proven direct Tailnet IP
            // first. Retrying whatever failed last can leave the phone stuck on a
            // stale MagicDNS URL even after Wake-on-LAN brings the laptop back.
            loadTerminalAtIndex(0, reason + "-wake-retry");
        }, TERMINAL_WAKE_RETRY_DELAY_MS);
    }

    private void wakeLaptopForTerminal(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastWakeOnLanAtMs < WOL_COOLDOWN_MS) {
            return;
        }
        lastWakeOnLanAtMs = now;
        new Thread(() -> sendWakeOnLanPackets()).start();
    }

    private void sendWakeOnLanPackets() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (String macAddress : WOL_MAC_ADDRESSES) {
                byte[] packet = wakeOnLanPacket(macAddress);
                for (String target : WOL_TARGETS) {
                    InetAddress address = InetAddress.getByName(target);
                    for (int port : WOL_PORTS) {
                        socket.send(new DatagramPacket(packet, packet.length, address, port));
                    }
                }
            }
        } catch (Exception ignored) {
            // WHY: Wake-on-LAN is a best-effort preflight for the sleeping-laptop
            // case. It must never block app open, WebView reconnect, toolbar
            // actions, or the title/control API when the phone is off the home LAN
            // or Android refuses local broadcast traffic.
        }
    }

    private byte[] wakeOnLanPacket(String macAddress) throws Exception {
        String normalized = macAddress.replace(":", "").replace("-", "").trim();
        if (normalized.length() != 12) {
            throw new IllegalArgumentException("Bad MAC address");
        }
        byte[] macBytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            macBytes[i] = (byte) Integer.parseInt(normalized.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] packet = new byte[6 + 16 * macBytes.length];
        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xff;
        }
        for (int i = 6; i < packet.length; i += macBytes.length) {
            System.arraycopy(macBytes, 0, packet, i, macBytes.length);
        }
        return packet;
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
        // WHY: visible Scroll menu actions must target the same stable tmux
        // window as touch-scroll. A bare `/scroll` follows whichever pane
        // `main_phone` currently selected and reintroduces the cross-lane
        // scroll drift that made Page up/Page down look broken on the APK.
        String path = appendStableWindowQuery("/scroll?where=" + urlEncode(where), visibleTerminalTargetKey());
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
        // WHY: Bottom is visual recovery. A success Toast sits over the live
        // prompt area on the APK and recreated the "can't see bottom text"
        // regression; failures still toast from the HTTP control callback.
        restoreLiveForViewing("");
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
            // WHY: Refresh is a visual transport repair. A normal success Toast
            // covers the live prompt/native composer just like the old Bottom,
            // Send, and Active-open popups. The repaint itself is the success
            // signal; keep only real failure Toasts on this path.
            reloadTerminalTransportOnly("fix-view");
        }, exc -> {
            reloadTerminalTransportOnly("fix-view");
        });
    }

    private void reloadTerminalTransportOnly(String reason) {
        if (webView == null) {
            return;
        }
        String currentUrl = webView.getUrl();
        markTerminalLoadStarted();
        webView.loadUrl(isKnownTerminalUrl(currentUrl)
                ? currentUrl
                : terminalUrlWithOptions(activeTerminalBaseUrl));
        pinTerminalViewportSoon(reason);
        focusTerminalInputSoon();
        keepLiveInputVisibleSoon(reason);
        scheduleBlankTerminalWatchdog(reason);
    }

    private void refreshActiveSwitchTransportOnceSoon(String reason, long generation) {
        uiHandler.postDelayed(() -> {
            if (webView == null
                    || generation != terminalModeGeneration
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || isDockedPromptComposerVisible()) {
                return;
            }
            // WHY: v2.31 proved shields, DOM row clamps, software layers, and the
            // automatic Bottom-core settle could all leave the real phone readable
            // frame dotted, while one manual Refresh cleared the same WebView. This
            // is a single passive transport refresh after Active switching only:
            // keep the same tmux window, do not restart ttyd/control/title lanes,
            // do not open the keyboard, and do not loop reloads.
            reloadTerminalTransportOnly(reason + "-one-shot-active-refresh");
        }, 420);
    }

    private void settleEntryLiveBottomSoon(String reason) {
        if (webView == null) {
            return;
        }
        long generation = ++entryLiveBottomSettleGeneration;
        long bottomCoreGeneration = ++entryBottomCoreGeneration;
        settleEntryLiveBottom(reason, generation, bottomCoreGeneration, 120);
        settleEntryLiveBottom(reason, generation, bottomCoreGeneration, 520);
        settleEntryLiveBottom(reason, generation, bottomCoreGeneration, 1100);
        settleEntryLiveBottom(reason, generation, bottomCoreGeneration, 2300);
    }

    private void settleEntryLiveBottom(String reason, long generation, long bottomCoreGeneration, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (webView == null
                    || !activityResumed
                    || generation != entryLiveBottomSettleGeneration
                    || bottomCoreGeneration != entryBottomCoreGeneration
                    || isDockedPromptComposerVisible()
                    || isTerminalGestureRecoveryActive()
                    || isViewerPanAllowed()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.evaluateJavascript(terminalDomReadyScript(), value -> {
                    if (!javascriptBoolean(value)
                            || generation != entryLiveBottomSettleGeneration
                            || bottomCoreGeneration != entryBottomCoreGeneration) {
                        return;
                    }
                    runEntryBottomCoreRecovery(reason, generation, bottomCoreGeneration);
                });
                return;
            }
            runEntryBottomCoreRecovery(reason, generation, bottomCoreGeneration);
        }, Math.max(0, delayMs));
    }

    private void runEntryBottomCoreRecovery(String reason, long generation, long bottomCoreGeneration) {
        if (webView == null
                || !activityResumed
                || generation != entryLiveBottomSettleGeneration
                || bottomCoreGeneration != entryBottomCoreGeneration
                || isDockedPromptComposerVisible()
                || isTerminalGestureRecoveryActive()
                || isViewerPanAllowed()) {
            return;
        }
            long now = System.currentTimeMillis();
            if (now - lastEntryLiveBottomSettleAtMs < ENTRY_LIVE_BOTTOM_SETTLE_MIN_INTERVAL_MS) {
                return;
            }
            lastEntryLiveBottomSettleAtMs = now;
            long modeGeneration = leaveReadModeForLiveInput(false);
            // WHY: The user-proven Android APK failure is that first entry and tab
            // return can show xterm's dotted or black stale rows until the toolbar
            // Bottom button is pressed. Reuse the exact Bottom-core helper only after
            // ttyd/xterm exists so the repaint work has a DOM to operate on, but keep
            // this as passive navigation: no native composer, no keyboard, no WebView
            // reload, and no hidden xterm typing focus.
            runBottomButtonLiveBottomRecovery(
                    reason + "-entry-bottom-core",
                    modeGeneration,
                    "",
                    false,
                    true,
                    null
            );
    }

    private String terminalDomReadyScript() {
        return "(function(){"
                + "try{"
                + "return !!(document.querySelector('[data-mantis-capture-renderer=\"1\"]')||window.term||window.terminal||document.querySelector('.xterm-rows,.xterm-screen,.xterm-helper-textarea,canvas'));"
                + "}catch(e){return false;}"
                + "})()";
    }

    private boolean javascriptBoolean(String value) {
        return "true".equals(String.valueOf(value));
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
        // WHY: Stop is the phone equivalent of the desktop Escape key. Do not
        // submit drafts, queue second-press state, or invent a phone-only stop
        // flow here; Send/Enter owns prompt submission and Stop owns one Escape.
        sendStopInterrupt(promptComposerTargetKey());
    }

    private void sendEnterToTerminal() {
        long generation = leaveReadModeForLiveInput();
        getJson(appendStableWindowQuery("/send-enter"), payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Send failed"));
                return;
            }
            // WHY: success Toasts sit directly over the live prompt/native
            // composer on the phone. Start/Send success is already visible in the
            // terminal state; keep error Toasts, but do not cover typing with a
            // redundant "Sent" popup.
            hideDockedPromptComposer(false, true);
            focusTerminalInputSoon(false);
            settleLiveBottomAfterSend("send-enter");
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void sendStopInterrupt(String targetKey) {
        control(appendStableWindowQuery("/stop", targetKey), "Stop sent", true);
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
        if ("tap-up".equals(reason) && System.currentTimeMillis() < terminalBodyTapSuppressedUntilMs) {
            // WHY: the real-phone Active Sessions proof for v2.13 still showed the
            // docked composer reopening as `Send` after a row tap. That happens when
            // the dialog's release lands on the terminal body after the picker closes.
            // Active switching is navigation, not typing, so swallow only an orphan
            // tap-up that has no fresh terminal ACTION_DOWN. A real new tap clears
            // the passive suppression in handleTerminalTouch and must open the native composer promptly.
            // normal deliberate terminal taps after the suppression window still open the composer.
            hideDockedPromptComposerForSessionSwitch("passive-switch-tap-up-block");
            return;
        }
        boolean wasVisible = promptComposerBar.getVisibility() == View.VISIBLE;
        cancelXtermBlankTailMask("composer-" + reason);
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
        long composerGeneration = ++promptComposerVisibilityGeneration;
        if (!wasVisible) {
            resetPromptComposerLocalDraftForCurrentTarget();
        }
        promptComposerBar.setVisibility(View.VISIBLE);
        updateStartButtonLabel();
        restoreDockedPromptComposerFocus(reason);
        reassertDockedPromptComposerFocus(reason, composerGeneration, 120);
        reassertDockedPromptComposerFocus(reason, composerGeneration, 360);
        fitTerminalToCurrentViewSoon("composer-" + reason);
        refreshCaptureRendererForLayoutChange("composer-" + reason);
        alignViewerForComposerReason(reason);
        // WHY: do not arm a WebView bitmap reload when opening the native
        // composer. v1.85 proved that low-paint sampling during this resize can
        // reload a healthy mostly-black terminal while the user is typing, leaving
        // the app black with only a cursor. Composer proof now lives in the
        // real-phone UI harness; runtime recovery stays explicit through Refresh.
    }

    private void alignViewerForComposerReason(String reason) {
        if ("tap-up".equals(reason)) {
            // WHY: a plain terminal-body tap should only put the cursor into the
            // native composer. Running the zoomed true-bottom scroll helper here
            // made a tap look like the same rapid up/down refresh as the Bottom
            // button. Explicit Bottom and finger-up bottom recovery still own
            // bottom alignment; tap-to-type must stay visually calm.
            cancelViewerTypingPositionRetries("composer-tap-up");
            return;
        }
        if ("live-bottom".equals(reason)) {
            scrollViewerToTypingPositionOnce("composer-live-bottom", 260);
            return;
        }
        scrollViewerToTypingPositionSoon("composer-" + reason);
    }

    private void resetPromptComposerLocalDraftForCurrentTarget() {
        if (promptComposerInput == null) {
            promptComposerDraftTargetKey = promptComposerTargetKey();
            promptComposerDraftLocalGeneration++;
            return;
        }
        String targetKey = promptComposerTargetKey();
        if (!targetKey.equals(promptComposerDraftTargetKey)
                && promptComposerInput.getText().length() > 0) {
            // WHY: a visible native draft belongs to the stable tmux `@windowId`
            // where it was typed. Preserving that text after an Active-tab switch
            // lets Send paste the old draft into a different conversation, which
            // looks like the same random paste/duplicate regression the user has
            // reported repeatedly. Because normal typing is local-only now, the
            // only safe cross-tab behavior is to clear the preserved local draft
            // when the target window changes.
            promptComposerProgrammaticTextChange = true;
            try {
                promptComposerInput.setText("");
            } finally {
                promptComposerProgrammaticTextChange = false;
            }
        }
        promptComposerDraftTargetKey = targetKey;
        promptComposerDraftLocalGeneration++;
    }

    private void clearUnsentDraft() {
        if (promptComposerInput == null) {
            toast("No draft to clear");
            return;
        }
        String visibleDraft = promptComposerInput.getText().toString();
        if (visibleDraft.isEmpty()) {
            toast("No draft to clear");
            return;
        }
        promptComposerDraftLocalGeneration++;
        promptComposerProgrammaticTextChange = true;
        try {
            promptComposerInput.setText("");
        } finally {
            promptComposerProgrammaticTextChange = false;
        }
        promptComposerDraftTargetKey = promptComposerTargetKey();
        // WHY: Clear now only clears the visible native composer. It must not send
        // a calculated `/draft-delta` backspace into tmux, because normal phone typing is local-only now
        // and is no longer mirrored there. If a stale tmux prompt exists from
        // an older build or hidden xterm path, empty-composer Backspace/Delete and
        // Option keys send literal tmux keys as an explicit recovery action.
        toast("Draft cleared");
    }

    private void submitDockedPromptText(String text) {
        submitDockedPromptText(text, "");
    }

    private void submitDockedPromptText(String text, String successToast) {
        submitDockedPromptText(text, successToast, promptComposerDraftSubmitTargetKey());
    }

    private void submitDockedPromptText(String text, String successToast, String targetKey) {
        submitDockedPromptText(text, successToast, targetKey, null, null);
    }

    private void submitDockedPromptText(
            String text,
            String successToast,
            String targetKey,
            Runnable afterSuccess,
            Runnable afterFailure
    ) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            toast("Prompt is empty");
            if (afterFailure != null) {
                afterFailure.run();
            }
            return;
        }
        // WHY: toolbar Send is the only submit owner for phone text. The previous
        // live-mirror design pasted drafts through `/draft-delta` while the user
        // was still composing, then only sent Enter here; that repeatedly caused
        // duplicate words, invisible stale tmux drafts, and wrong-tab paste after
        // Active switching. Send must paste the complete visible native composer
        // text once through `/submit-text`, which also presses Enter once.
        submitSafePrompt(value, successToast, targetKey, afterSuccess, afterFailure);
    }

    private void settleLiveBottomAfterSend(String reason) {
        long generation = terminalModeGeneration;
        liveInputVisibilityGeneration++;
        cancelViewerTypingPositionRetries(reason);
        uiHandler.postDelayed(() -> {
            if (generation != terminalModeGeneration || readModeSuppressesKeyboard || terminalHistoryViewportActive) {
                return;
            }
            // WHY: the control server already sent Enter at live bottom. After that,
            // Android hides the native composer and IME, which changes the WebView
            // height after the tmux submit has already happened. Do one delayed
            // server live-bottom settle plus a passive xterm resize so the phone
            // returns to the bottom automatically after Send, without reloading
            // ttyd or running the old multi-step scroll/keyboard burst.
            getJsonWithRetry(appendStableWindowQuery("/live-bottom"), payload -> {
                if (generation != terminalModeGeneration) {
                    return;
                }
                pinTerminalViewportLocal();
                fitTerminalToCurrentViewSoon(reason);
                keepToolbarOnlyXtermSettleAliveAfterControlAction(reason);
                if (shouldPreserveZoomedViewerForPassiveBottom(reason)) {
                    cancelViewerTypingPositionRetries(reason + "-preserve-zoomed-send");
                } else {
                    scrollViewerToTypingPositionOnce(reason, 180);
                }
                scheduleToolbarStatusDotRefresh(150);
            }, exc -> toast("WEzterm control is not reachable"));
        }, 420);
    }

    private void submitDockedPrompt() {
        if (promptComposerInput == null) {
            return;
        }
        String text = promptComposerInput.getText().toString();
        submitDockedPromptText(text);
    }

    private void hideDockedPromptComposer(boolean clearText, boolean keepKeyboardState) {
        if (promptComposerBar == null || promptComposerInput == null) {
            return;
        }
        if (clearText) {
            promptComposerProgrammaticTextChange = true;
            try {
                promptComposerInput.setText("");
            } finally {
                promptComposerProgrammaticTextChange = false;
            }
            promptComposerDraftTargetKey = promptComposerTargetKey();
            promptComposerDraftLocalGeneration++;
        }
        promptComposerVisibilityGeneration++;
        promptComposerInput.clearFocus();
        promptComposerBar.setVisibility(View.GONE);
        updateStartButtonLabel();
        if (!keepKeyboardState) {
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(promptComposerInput.getWindowToken(), 0);
            }
            hideTerminalKeyboardQuietly("composer-hide");
        }
        fitTerminalToCurrentViewSoon("composer-hide");
        refreshCaptureRendererForLayoutChange("composer-hide");
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

    private void restoreDockedPromptComposerFocus(String reason) {
        if (promptComposerBar == null || promptComposerInput == null || !isDockedPromptComposerVisible()) {
            return;
        }
        // WHY: Android only honors soft-input requests for the focused view in a
        // focused window. Resume/window-focus/page-finished callbacks used to probe
        // xterm after the native composer was visible, which could leave typing
        // attached to a hidden WebView textarea while the phone composer looked
        // missing or stale. Preserve the native EditText as the single typing owner.
        promptComposerBar.setVisibility(View.VISIBLE);
        updateStartButtonLabel();
        boolean alreadyFocused = promptComposerInput.hasFocus();
        boolean imeAlreadyVisibleForComposer = alreadyFocused && lastImeInsetBottom > 0;
        promptComposerInput.requestFocus();
        promptComposerInput.setSelection(promptComposerInput.getText().length());
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        long now = System.currentTimeMillis();
        if (inputMethodManager != null
                && !imeAlreadyVisibleForComposer
                && now - lastPromptComposerShowSoftInputAtMs >= PROMPT_COMPOSER_SOFT_INPUT_MIN_INTERVAL_MS) {
            // WHY: the native composer owns phone typing, but repeated `showSoftInput` calls
            // during the 120/360 ms settle callbacks can still ask Samsung/Gboard/voice
            // input to reconnect to the same visible editor.
            // That is the same duplicate-composition class the old xterm textarea
            // focus loop caused, so one bounded request is enough while the IME is
            // already visible or was just requested.
            lastPromptComposerShowSoftInputAtMs = now;
            inputMethodManager.showSoftInput(promptComposerInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void reassertDockedPromptComposerFocus(String reason, long composerGeneration, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (composerGeneration != promptComposerVisibilityGeneration
                    || !activityResumed
                    || !isDockedPromptComposerVisible()) {
                return;
            }
            restoreDockedPromptComposerFocus(reason + "-settle");
        }, delayMs);
    }

    private void hideDockedPromptComposerForSessionSwitch(String reason) {
        if (!isDockedPromptComposerVisible()) {
            hideTerminalKeyboardQuietly(reason);
            return;
        }
        // WHY: Active Sessions row taps are navigation, not typing. If the
        // docked native composer stays focused while Android switches tabs, the
        // IME keeps WebView resized and the toolbar remains in Send mode, exposing
        // the dotted xterm canvas the user reported. Hide the composer and IME on
        // successful selection, but do not clear text; an unsent draft is user
        // input and must not be destroyed by a tab switch.
        liveInputVisibilityGeneration++;
        cancelViewerTypingPositionRetries(reason);
        hideDockedPromptComposer(false, false);
    }

    private void suppressTerminalBodyTapForPassiveNavigation(String reason) {
        // WHY: opening Active/Old/Crashed/New is navigation, not typing. The
        // recurring real-phone failure was one passive tab switch bringing back
        // three separate bugs at once: ACTION_UP fell through from the picker and
        // reopened the native composer, the toolbar stayed on Send/keyboard, and
        // xterm painted dotted stale rows instead of the same full-height bottom
        // state the manual Bottom button gives. Suppress stale terminal-body
        // releases and hidden-textarea focus callbacks through the async select +
        // bottom settle window; a fresh terminal ACTION_DOWN clears this timeout so
        // the next deliberate tap still opens the native composer normally.
        terminalBodyTapSuppressedUntilMs = Math.max(
                terminalBodyTapSuppressedUntilMs,
                System.currentTimeMillis() + PASSIVE_NAVIGATION_TOUCH_SUPPRESS_MS
        );
        terminalFocusGeneration++;
        hideTerminalKeyboardQuietly(reason);
    }

    private void hideDockedPromptComposerForReadMode(String reason) {
        if (!isDockedPromptComposerVisible()) {
            hideTerminalKeyboardQuietly(reason);
            return;
        }
        // WHY: scrollback/history is reading, not typing. Leaving the docked
        // composer visible while tmux is in copy-mode shrinks the terminal,
        // leaves Start labeled as Send, and makes the phone look like the
        // scrollback area is not displaying properly. Hide the composer and IME
        // before read mode takes over, but keep any draft text for the next
        // deliberate typing action.
        liveInputVisibilityGeneration++;
        cancelViewerTypingPositionRetries(reason);
        hideDockedPromptComposer(false, false);
    }

    private void hideDockedPromptComposerForNavigation(String reason) {
        if (!isDockedPromptComposerVisible()) {
            hideTerminalKeyboardQuietly(reason);
            return;
        }
        // WHY: picker/dialog actions are navigation or reading, not prompt
        // composition. If Active/Old/Scroll opens while the native composer is
        // focused, Android keeps the IME up, shrinks the terminal/dialog, and can
        // make session rows or scrollback look clipped. Hide the composer before
        // opening the dialog, but preserve the draft so the user's typed text
        // returns on the next deliberate typing tap.
        terminalFocusGeneration++;
        liveInputVisibilityGeneration++;
        cancelViewerTypingPositionRetries(reason);
        hideDockedPromptComposer(false, false);
    }

    private void submitSafePrompt(String text) {
        submitSafePrompt(text, "");
    }

    private void submitSafePrompt(String text, String successToast) {
        submitSafePrompt(text, successToast, promptComposerTargetKey());
    }

    private void submitSafePrompt(String text, String successToast, String targetKey) {
        submitSafePrompt(text, successToast, targetKey, null, null);
    }

    private void submitSafePrompt(
            String text,
            String successToast,
            String targetKey,
            Runnable afterSuccess,
            Runnable afterFailure
    ) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            toast("Prompt is empty");
            if (afterFailure != null) {
                afterFailure.run();
            }
            return;
        }
        String stableTargetKey = hasStableWindowId(targetKey) ? targetKey.trim() : promptComposerTargetKey();
        String submitFingerprint = stableTargetKey + "\n" + value;
        if (!beginPromptComposerSubmit(submitFingerprint)) {
            return;
        }
        String submitIdempotencyKey = "phone-submit-" + UUID.randomUUID().toString();
        long generation = leaveReadModeForLiveInput();
        // WHY: visible phone drafts are local-only until Send, so the submit
        // request must use the draft's pinned `@windowId`, not a later `/active`
        // value. This is the durable guard against prompts being pasted into a
        // different active session after tab switches, polling, or proof setup.
        postTextWithIdempotency(appendStableWindowQuery("/submit-text", stableTargetKey), value, submitIdempotencyKey, payload -> {
            if (generation != terminalModeGeneration) {
                finishPromptComposerSubmit(submitFingerprint);
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                finishPromptComposerSubmit(submitFingerprint);
                toast(payload.optString("error", "Send failed"));
                return;
            }
            if (successToast != null && !successToast.trim().isEmpty()) {
                toast(successToast);
            }
            finishPromptComposerSubmit(submitFingerprint);
            hideDockedPromptComposer(true, false);
            focusTerminalInputSoon(false);
            settleLiveBottomAfterSend("submit-text");
            if (afterSuccess != null) {
                afterSuccess.run();
            }
        }, exc -> {
            finishPromptComposerSubmit(submitFingerprint);
            toast("WEzterm control is not reachable");
            if (afterFailure != null) {
                afterFailure.run();
            }
        });
    }

    private boolean beginPromptComposerSubmit(String fingerprint) {
        if (promptComposerSubmitInFlight) {
            if (!fingerprint.equals(promptComposerSubmitFingerprint)) {
                toast("Prompt send in progress");
            }
            // WHY: Android IMEs can deliver both an editor-action callback and an
            // Enter key event for one visible Send. The composer is cleared only
            // after `/submit-text` succeeds, so duplicate callbacks in that
            // async window must be ignored instead of pasting the same draft
            // twice into tmux.
            return false;
        }
        promptComposerSubmitInFlight = true;
        promptComposerSubmitFingerprint = fingerprint;
        return true;
    }

    private void finishPromptComposerSubmit(String fingerprint) {
        if (!promptComposerSubmitInFlight || !fingerprint.equals(promptComposerSubmitFingerprint)) {
            return;
        }
        promptComposerSubmitInFlight = false;
        promptComposerSubmitFingerprint = "";
    }

    private long enterReadMode() {
        long generation = ++terminalModeGeneration;
        cancelXtermBlankTailMask("enter-read-mode");
        hideDockedPromptComposerForReadMode("read-mode");
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
        long generation = leaveReadModeForLiveInput();
        runBottomButtonLiveBottomRecovery("live-bottom", generation, message, true, false, null);
    }

    private void restoreLiveForViewing(String message) {
        long generation = leaveReadModeForLiveInput(false);
        // WHY: the visible Bottom toolbar button is navigation/readability
        // recovery, not a typing command. Opening the native composer here shrank
        // the terminal, left the keyboard up when the user immediately scrolled
        // history, and made the black/dot transition mask feel stuck. Keep Bottom
        // passive and full-screen; tapping the terminal body is the deliberate
        // request to type.
        runBottomButtonLiveBottomRecovery("live-bottom-view", generation, message, false, false, null);
    }

    private void restoreLiveAfterTabOpen(String reason, long generation, long paintShieldGeneration) {
        Runnable afterSuccess = paintShieldGeneration > 0
                ? () -> {
                    // WHY: v2.22 moved the long-lived dotted-field protection out of
                    // the full-frame native shield. Extending this layer back to the
                    // old 2200 ms window recreates the all-black Active-switch failure;
                    // delayed xterm dots are handled by the bounded WebView lower-area
                    // shield and glyph scrubbers instead.
                    hideSessionSwitchPaintShieldSoon(reason + "-bottom-core-painted",
                            paintShieldGeneration, FULL_FRAME_SESSION_SWITCH_SHIELD_MAX_MS);
                }
                : null;
        // WHY: v2.06 proved tab-open must reuse Bottom-core to clear dotted/black
        // xterm rows, but the next real-phone screenshots showed auto-opening the
        // composer/keyboard revived duplicate typing and made Backspace useless
        // while Android was still settling. Tab-open is navigation; the user taps
        // the terminal body when they actually want the native composer.
        runBottomButtonLiveBottomRecovery(reason + "-tab-open-bottom", generation, "", false, true, afterSuccess);
    }

    private void runBottomButtonLiveBottomRecovery(
            String reason,
            long generation,
            String message,
            boolean showComposer,
            boolean quietFailure,
            Runnable afterSuccess
    ) {
        runBottomButtonLiveBottomRecovery(
                reason,
                generation,
                message,
                showComposer,
                quietFailure,
                afterSuccess,
                0
        );
    }

    private void runBottomButtonLiveBottomRecovery(
            String reason,
            long generation,
            String message,
            boolean showComposer,
            boolean quietFailure,
            Runnable afterSuccess,
            int retryCount
    ) {
        if (liveRestoreInFlight) {
            if (!showComposer
                    && quietFailure
                    && reason.contains("tab-open")
                    && retryCount < PASSIVE_TAB_OPEN_BOTTOM_RETRY_LIMIT) {
                // WHY: Active/Old/Crashed/New tab-open is supposed to behave as
                // though the user pressed Bottom immediately after opening the
                // tab. If a previous `/live-bottom` confirmation is still in
                // flight, returning here silently skips that automatic Bottom and
                // recreates the user-reported "I have to hit Bottom myself" bug.
                // Retry only passive tab-open recoveries; deliberate Bottom/tap to
                // type keeps the existing single-owner liveRestoreInFlight guard.
                uiHandler.postDelayed(() -> runBottomButtonLiveBottomRecovery(
                        reason,
                        generation,
                        message,
                        false,
                        true,
                        afterSuccess,
                        retryCount + 1
                ), PASSIVE_TAB_OPEN_BOTTOM_RETRY_MS);
            }
            return;
        }
        // WHY: history paging and one-finger swipe requests are asynchronous HTTP
        // calls. A stale pageUp/pageDown response used to arrive after Live/tap and
        // put the app back into keyboard-suppressed read mode. Bottom and tab-open
        // now share this exact `/live-bottom` core because the real APK dot-grid
        // regression only cleared after pressing Bottom manually. Do not split this
        // back into a second "almost Bottom" tab-open path.
        liveRestoreInFlight = true;
        // WHY: this is a normal user recovery/tap-to-type action, not a proof
        // capture. The full `/scroll?where=bottom` endpoint gathers visible pane
        // evidence and Android then ran xterm.scrollToBottom/scrollIntoView, which
        // created the rapid top/bottom refresh loop at the exact moment the user
        // wanted to type. `/live-bottom` uses the server's fast tmux/Codex/reader
        // live-return primitive and leaves WebView's transport alone.
        getJsonWithRetry(appendStableWindowQuery("/live-bottom"), payload -> {
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
            refreshCaptureRendererForLayoutChange(reason + "-live-bottom");
            if (showComposer) {
                showDockedPromptComposer("live-bottom");
            } else {
                // WHY: real-phone v2.11 proof started from a bad `Send`/composer
                // state and Active switching selected the new tmux tab but could
                // leave Android's IME/composer snapshot alive afterward. Passive
                // tab-open must finish like Start-toolbar navigation: preserve any
                // draft for the next deliberate tap, but do not leave the keyboard,
                // native composer, or hidden xterm focus attached to the selected
                // session.
                clearBroadSessionSwitchVisualMasks(reason + "-live-bottom-confirmed");
                hideDockedPromptComposerForSessionSwitch(reason + "-post-bottom");
                keepPassiveTabOpenPlainSoon(reason);
                fitTerminalToCurrentViewSoon(reason);
                keepToolbarOnlyXtermSettleAlive(reason);
                alignLiveBottomViewportForPassiveEntrySoon(reason);
                // WHY: explicit Bottom confirms tmux is at live bottom, but a
                // zoomed Android WebView can still be panned above the final prompt.
                // Use bounded viewer-only retries after the Bottom core settles so
                // one skipped frame cannot strand the user above the text. Preserve
                // scrollX and do not resize tmux, reset pinch zoom, or revive the old
                // scrollToBottom/scrollIntoView loop.
                if (shouldPreserveZoomedViewerForPassiveBottom(reason)) {
                    cancelViewerTypingPositionRetries(reason + "-preserve-zoomed-viewer");
                } else {
                    scrollViewerToTypingPositionAfterBottom(reason);
                }
                normalizeXtermCanvasAfterSessionSwitch(reason);
                scheduleToolbarStatusDotRefresh(150);
            }
            if (afterSuccess != null) {
                afterSuccess.run();
            }
        }, exc -> {
            liveRestoreInFlight = false;
            if (!quietFailure) {
                toast("WEzterm control is not reachable");
            }
        });
    }

    private void keepPassiveTabOpenPlainSoon(String reason) {
        long generation = terminalModeGeneration;
        keepPassiveTabOpenPlain(reason, generation, 140);
        keepPassiveTabOpenPlain(reason, generation, 520);
        keepPassiveTabOpenPlain(reason, generation, 1100);
    }

    private void keepPassiveTabOpenPlain(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (generation != terminalModeGeneration || readModeSuppressesKeyboard || terminalHistoryViewportActive) {
                return;
            }
            hideDockedPromptComposerForSessionSwitch(reason + "-plain");
            suppressTerminalBodyTapForPassiveNavigation(reason + "-plain");
        }, Math.max(0, delayMs));
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
        getJson(appendStableWindowQuery("/touch-scroll?where=bottom&repeat=1", terminalTouchStableWindowId), payload -> {
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
            refreshCaptureRendererSoon("touch-bottom");
            fitTerminalToCurrentViewSoon("touch-bottom");
            keepToolbarOnlyXtermSettleAliveAfterControlAction("touch-bottom");
            scrollViewerToTypingPositionAfterBottom("touch-bottom");
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
        // WHY: reader/page controls are navigation, not input. Delayed xterm
        // focus retries can otherwise reopen Samsung's keyboard and cover the
        // transcript. This blur path must stay paired with the touch handler's
        // two-finger pass-through and tap-to-Live restore; otherwise fixing
        // scrollback reintroduces the older "cannot see what I am typing" bug.
        hideTerminalKeyboardQuietly("read-mode");
    }

    private void hideTerminalKeyboardQuietly(String reason) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (webView != null) {
            // WHY: hiding the native composer must not leave Android serving
            // xterm's hidden textarea. That state shows the keyboard while the
            // toolbar says Start and makes the terminal look vertically clipped.
            // Blur xterm and hide IME from both WebView and decor tokens.
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
        hideDockedPromptComposerForNavigation("scroll-dialog");
        String[] labels = new String[]{
                "Go to live bottom / type",
                "Go to history top",
                "Read current session",
                "Local history search",
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
                        // WHY: success Toasts for visual scroll controls cover
                        // the terminal bottom/prompt on Android; errors still toast.
                        scrollTerminal("top", "", false);
                    } else if (which == 2) {
                        enterReadMode();
                        control("/read-session", "Session reader", false);
                    } else if (which == 3) {
                        showLocalHistoryViewer();
                    } else if (which == 4) {
                        enterReadMode();
                        scrollTerminal("pageUp", "", false);
                    } else if (which == 5) {
                        enterReadMode();
                        scrollTerminal("pageDown", "", false);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLocalHistoryViewer() {
        hideDockedPromptComposerForNavigation("local-history");
        getJsonWithRetry("/active", payload -> {
            JSONObject window = payload.optJSONObject("window");
            if (window == null) {
                toast("Active session not found");
                return;
            }
            String windowId = window.optString("windowId", "");
            String title = window.optString("title", window.optString("name", "Local history"));
            if (windowId.trim().isEmpty()) {
                toast("Active session id missing");
                return;
            }
            fetchLocalHistoryChunk(windowId, title, "");
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void fetchLocalHistoryChunk(String windowId, String title, String start) {
        String path = "/scrollback/chunk?windowId=" + urlEncode(windowId)
                + "&lines=" + LOCAL_HISTORY_CHUNK_LINES;
        if (start != null && !start.trim().isEmpty()) {
            path += "&start=" + urlEncode(start);
        }
        getJsonWithRetry(path, payload -> {
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Local history unavailable"));
                return;
            }
            try {
                showLocalHistoryDialog(windowId, title, payload);
            } catch (Exception exc) {
                toast("Local history failed: " + exc.getMessage());
            }
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void showLocalHistoryDialog(String windowId, String title, JSONObject payload) throws Exception {
        // WHY: C5 local history is intentionally a read-only local history
        // viewer. It consumes the C4 `/scrollback/chunk` cache contract
        // (`windowId:paneId:generation`) and renders cached rows in native
        // Android widgets. It must not select tmux windows, enter copy-mode,
        // send keys, reload WebView, focus xterm, or touch the one-finger
        // gesture/Bottom paths that took days to stabilize.
        List<String> rows = rowsFromPayload(payload);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(8), dp(12), dp(4));
        container.setBackgroundColor(Color.rgb(8, 8, 10));

        TextView statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(205, 214, 244));
        statusText.setTextSize(12);
        statusText.setPadding(0, 0, 0, dp(6));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search cached history");
        search.setContentDescription("Search cached history");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTextColor(Color.rgb(245, 245, 245));
        search.setHintTextColor(Color.rgb(127, 132, 156));
        search.setTextSize(14);

        TextView historyText = new TextView(this);
        historyText.setTextColor(Color.rgb(225, 225, 225));
        historyText.setBackgroundColor(Color.BLACK);
        historyText.setTypeface(Typeface.MONOSPACE);
        historyText.setTextSize(11);
        historyText.setTextIsSelectable(true);
        historyText.setPadding(dp(8), dp(8), dp(8), dp(8));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(historyText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        container.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        container.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(420)
        );
        scrollParams.setMargins(0, dp(8), 0, 0);
        container.addView(scrollView, scrollParams);

        updateLocalHistoryText(historyText, statusText, rows, "", payload);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateLocalHistoryText(historyText, statusText, rows, text.toString(), payload);
                scrollView.post(() -> scrollView.scrollTo(0, 0));
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Local history")
                .setView(container)
                .setPositiveButton("Older", null)
                .setNeutralButton("Latest", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(shown -> {
            Button olderButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button latestButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Object prevStart = payload.opt("prevStart");
            boolean hasOlder = prevStart != null && prevStart != JSONObject.NULL;
            olderButton.setEnabled(hasOlder);
            olderButton.setOnClickListener(view -> {
                if (!hasOlder) {
                    toast("No older cached rows");
                    return;
                }
                dialog.dismiss();
                fetchLocalHistoryChunk(windowId, title, String.valueOf(prevStart));
            });
            latestButton.setOnClickListener(view -> {
                dialog.dismiss();
                fetchLocalHistoryChunk(windowId, title, "");
            });
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        });
        dialog.show();
    }

    private List<String> rowsFromPayload(JSONObject payload) throws Exception {
        List<String> rows = new ArrayList<>();
        JSONArray array = payload.optJSONArray("rows");
        if (array == null) {
            return rows;
        }
        for (int index = 0; index < array.length(); index++) {
            rows.add(array.optString(index, ""));
        }
        return rows;
    }

    private void updateLocalHistoryText(
            TextView historyText,
            TextView statusText,
            List<String> rows,
            String query,
            JSONObject payload
    ) {
        String rendered = renderLocalHistoryRows(rows, query);
        historyText.setText(rendered);
        String cleanQuery = query == null ? "" : query.trim();
        int matchCount = cleanQuery.isEmpty() ? rows.size() : countLocalHistoryMatches(rows, cleanQuery);
        String start = payload.optString("start", "?");
        String end = payload.optString("end", "?");
        String generationKey = payload.optString("generationKey", "windowId:paneId:generation");
        statusText.setText(titleForLocalHistory(payload)
                + "\nCached rows: " + rows.size()
                + (cleanQuery.isEmpty() ? "" : " - Matches: " + matchCount)
                + " - Range: " + start + "-" + end
                + "\nCache key: " + generationKey);
    }

    private String titleForLocalHistory(JSONObject payload) {
        String windowId = payload.optString("windowId", "");
        String paneId = payload.optString("paneId", "");
        return "Read-only local history"
                + (windowId.isEmpty() ? "" : " - " + windowId)
                + (paneId.isEmpty() ? "" : " " + paneId);
    }

    private int countLocalHistoryMatches(List<String> rows, String query) {
        String needle = query.toLowerCase();
        int count = 0;
        for (String row : rows) {
            if (row.toLowerCase().contains(needle)) {
                count++;
            }
        }
        return count;
    }

    private String renderLocalHistoryRows(List<String> rows, String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            return trimLocalHistoryText(joinRows(rows));
        }
        String needle = cleanQuery.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String row : rows) {
            if (row.toLowerCase().contains(needle)) {
                matches.add(row);
            }
        }
        if (matches.isEmpty()) {
            return "No cached history matches: " + cleanQuery;
        }
        return trimLocalHistoryText(joinRows(matches));
    }

    private String joinRows(List<String> rows) {
        if (rows.isEmpty()) {
            return "No cached history rows available.";
        }
        StringBuilder builder = new StringBuilder();
        for (String row : rows) {
            builder.append(row).append('\n');
        }
        return builder.toString();
    }

    private String trimLocalHistoryText(String text) {
        if (text.length() <= LOCAL_HISTORY_MAX_DISPLAY_CHARS) {
            return text;
        }
        int omitted = text.length() - LOCAL_HISTORY_MAX_DISPLAY_CHARS;
        return text.substring(text.length() - LOCAL_HISTORY_MAX_DISPLAY_CHARS)
                + "\n\n[trimmed " + omitted + " older characters from this Android view]";
    }

    private void showCommandPalette() {
        hideDockedPromptComposerForNavigation("command-palette");
        String[] labels = new String[]{
                "Active Sessions",
                "Old Sessions",
                "Restore Crashed Sessions",
                "Refresh current session",
                "Needs Attention",
                "Copy/Paste",
                "Type prompt safely",
                "Upload media from phone",
                "Go to live bottom / type",
                "Start / send Enter",
                "Go to history top",
                "Read current session",
                "Local history search",
                "Page up",
                "Page down",
                "Install/update over Tailscale",
                "Create bug report",
                "Stop current task",
                "Rename current session",
                "Clear unsent draft",
                "Option keys"
        };
        new AlertDialog.Builder(this)
                .setTitle("Command palette")
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        showActiveSessions();
                    } else if (which == 1) {
                        showOldSessions();
                    } else if (which == 2) {
                        showCrashedSessions();
                    } else if (which == 3) {
                        refreshTerminalTransport();
                    } else if (which == 4) {
                        showNeedsAttention();
                    } else if (which == 5) {
                        showCopyPasteControls();
                    } else if (which == 6) {
                        showSafePromptComposer();
                    } else if (which == 7) {
                        pickMediaForUpload();
                    } else if (which == 8) {
                        goLiveBottom();
                    } else if (which == 9) {
                        startCurrentTask();
                    } else if (which == 10) {
                        enterReadMode();
                        // WHY: these are visual navigation controls. Success
                        // Toasts cover the live prompt area; error Toasts remain.
                        scrollTerminal("top", "", false);
                    } else if (which == 11) {
                        openFullSessionReader();
                    } else if (which == 12) {
                        showLocalHistoryViewer();
                    } else if (which == 13) {
                        enterReadMode();
                        scrollTerminal("pageUp", "", false);
                    } else if (which == 14) {
                        enterReadMode();
                        scrollTerminal("pageDown", "", false);
                    } else if (which == 15) {
                        openInstallPage();
                    } else if (which == 16) {
                        createBugReport();
                    } else if (which == 17) {
                        stopCurrentTask();
                    } else if (which == 18) {
                        showRenameCurrentTab();
                    } else if (which == 19) {
                        clearUnsentDraft();
                    } else if (which == 20) {
                        showKeyControls();
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
                "Upload media from phone",
                "Clear unsent draft",
                "Option keys"
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
                    } else if (which == 4) {
                        clearUnsentDraft();
                    } else if (which == 5) {
                        showKeyControls();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showKeyControls() {
        hideDockedPromptComposerForNavigation("key-controls");
        // WHY: CLI option pickers can expose any number of choices, so fixed
        // one/two/three shortcuts are the wrong phone model. This dialog stays
        // open while the user taps Move up/down, Backspace, Delete, Home, or End,
        // making arbitrary Claude/Codex option lists usable without reopening a
        // one-shot list for every arrow key. Select/Escape/Done are the deliberate
        // close points.
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(14), dp(10), dp(14), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Option keys")
                .setView(grid)
                .setNegativeButton("Done", null)
                .create();
        // WHY: this must remain a custom persistent grid, not a one-shot
        // setItems list. CLI prompts often need several navigation/edit keys in
        // a row, and reopening the Copy/Paste menu after every Up/Down/Home/End
        // tap is the regression that made phone option pickers unusable.
        addKeyControlRow(grid, new String[]{"Move up", "Move down", "Select"}, new String[]{"Up", "Down", "Enter"}, dialog);
        addKeyControlRow(grid, new String[]{"Backspace", "Delete", "Tab"}, new String[]{"Backspace", "Delete", "Tab"}, dialog);
        addKeyControlRow(grid, new String[]{"Home", "End", "Escape"}, new String[]{"Home", "End", "Escape"}, dialog);
        dialog.show();
    }

    private void addKeyControlRow(LinearLayout grid, String[] labels, String[] keys, AlertDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            final String key = keys[i];
            Button button = toolbarButton(label, v -> {
                // WHY: the real APK proof showed Delete could be lost when a
                // Backspace toast/focus transition was still settling. Persistent
                // Option Keys already give visible feedback by staying open, so
                // do not show per-key toasts for Backspace/Delete/Home/End/Tab/
                // Up/Down. Keep only Select's closing confirmation.
                enqueueOptionTerminalKey(key, "Enter".equals(key) ? "Selected" : "");
                if ("Enter".equals(key) || "Escape".equals(key)) {
                    dialog.dismiss();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    dp(52),
                    1
            );
            params.setMargins(dp(3), 0, dp(3), 0);
            row.addView(button, params);
        }
        grid.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void enqueueOptionTerminalKey(String key, String message) {
        // WHY: Option Keys are quick repeated taps from the phone. The real APK
        // proof showed Backspace could reach tmux while the Android HTTP
        // callback was still settling, then the next Delete tap was lost. Keep
        // these control keys ordered on the UI thread so Backspace/Delete/Home/
        // End/Up/Down/Enter arrive at tmux in the same sequence the user tapped.
        optionKeyDispatchQueue.add(new OptionKeyDispatch(key, message, optionKeyTargetKey()));
        drainOptionTerminalKeyQueue();
    }

    private String optionKeyTargetKey() {
        // WHY: selectedPhoneWindowId is also the protected Close target, so it
        // can intentionally outlive a later active-window refresh. Option Keys
        // are live input for the window the phone is currently controlling; if
        // they prefer the stale close target, Backspace/Delete can look tapped
        // while landing in an older tab. Prefer the current phone window and only
        // fall back to selected row memory when active state is unavailable.
        if (hasStableWindowId(currentPhoneWindowId)) {
            return currentPhoneWindowId.trim();
        }
        if (hasStableWindowId(selectedPhoneWindowId)) {
            return selectedPhoneWindowId.trim();
        }
        return "unknown:" + terminalModeGeneration;
    }

    private void drainOptionTerminalKeyQueue() {
        if (optionKeyDispatchInFlight) {
            return;
        }
        OptionKeyDispatch dispatch = optionKeyDispatchQueue.poll();
        if (dispatch == null) {
            return;
        }
        optionKeyDispatchInFlight = true;
        sendTerminalKey(dispatch.key, dispatch.message, dispatch.targetKey, () -> {
            optionKeyDispatchInFlight = false;
            drainOptionTerminalKeyQueue();
        });
    }

    private void sendTerminalKey(String key, String message) {
        sendTerminalKey(key, message, null);
    }

    private void sendTerminalKey(String key, String message, Runnable after) {
        sendTerminalKey(key, message, promptComposerTargetKey(), after);
    }

    private void sendTerminalKey(String key, String message, String targetKey, Runnable after) {
        // WHY: Claude/Codex option pickers need arrow/select keys that are not
        // text composition. Route them through tmux `send-keys` after a Bottom-like
        // live restore, without focusing the hidden xterm textarea or asking the
        // Android IME to reconnect to the native composer.
        long generation = leaveReadModeForLiveInput(false);
        getJson(appendStableWindowQuery("/send-key?key=" + urlEncode(key), targetKey), payload -> {
            try {
                if (generation != terminalModeGeneration) {
                    return;
                }
                if (!payload.optBoolean("ok", false)) {
                    toast(payload.optString("error", "Key failed"));
                    return;
                }
                if (message != null && !message.isEmpty()) {
                    toast(message);
                }
                pinTerminalViewportLocal();
                fitTerminalToCurrentViewSoon("send-key");
                keepToolbarOnlyXtermSettleAliveAfterControlAction("send-key");
                alignLiveBottomViewportForPassiveEntrySoon("send-key");
            } finally {
                if (after != null) {
                    after.run();
                }
            }
        }, exc -> {
            try {
                toast("WEzterm control is not reachable");
            } finally {
                if (after != null) {
                    after.run();
                }
            }
        });
    }

    private boolean nativeComposerVisibleTextEmpty() {
        return isDockedPromptComposerVisible()
                && promptComposerInput != null
                && promptComposerInput.getText().toString().isEmpty();
    }

    private void sendEmptyComposerBackspaceToTerminal() {
        // WHY: the real-phone failure can leave tmux/Codex containing draft text
        // that arrived through xterm's hidden textarea while the visible native
        // composer is empty. In that state Android Backspace has nothing local to
        // delete, so the user is stuck with prompt text they cannot remove. Route
        // empty-composer Backspace to tmux BSpace; once v2.07's top-tap and IME
        // ownership fixes prevent new hidden-xterm drafts, this remains a safe
        // recovery for inherited stale prompt text.
        sendTerminalKey("Backspace", "");
    }

    private void sendEmptyComposerDeleteToTerminal() {
        // WHY: Backspace was already guarded, but hardware/Gboard forward-delete
        // can arrive as KEYCODE_FORWARD_DEL or deleteSurroundingText afterLength.
        // When the visible native composer is empty, route that explicit recovery
        // key to tmux instead of making the user unable to delete stale text that
        // already reached the pane.
        sendTerminalKey("Delete", "");
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
        postText(appendStableWindowQuery("/paste"), clipboardText, payload -> {
            if (generation != terminalModeGeneration) {
                return;
            }
            if (!payload.optBoolean("ok", false)) {
                toast(payload.optString("error", "Paste failed"));
                return;
            }
            settleLiveBottomAfterPaste("clipboard-paste");
            hideDockedPromptComposer(false, true);
            focusTerminalInputSoon(false);
        }, exc -> toast("WEzterm control is not reachable"));
    }

    private void settleLiveBottomAfterPaste(String reason) {
        long generation = terminalModeGeneration;
        // WHY: upload/clipboard paste success is visible as text in the terminal.
        // A normal success Toast covers the exact bottom prompt/attachment path the
        // phone workflow needs to inspect, so paste success must settle the live
        // viewer quietly while keeping error Toasts for real failures.
        uiHandler.postDelayed(() -> {
            if (generation != terminalModeGeneration || readModeSuppressesKeyboard || terminalHistoryViewportActive) {
                return;
            }
            getJsonWithRetry(appendStableWindowQuery("/live-bottom"), payload -> {
                if (generation != terminalModeGeneration) {
                    return;
                }
                pinTerminalViewportLocal();
                fitTerminalToCurrentViewSoon(reason);
                keepToolbarOnlyXtermSettleAliveAfterControlAction(reason);
                scrollViewerToTypingPositionOnce(reason, 180);
                scheduleToolbarStatusDotRefresh(150);
            }, exc -> toast("WEzterm control is not reachable"));
        }, 180);
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
        // the same Tailscale control channel as Copy/Paste. Android's Photo
        // Picker returns a user-selected image/video URI directly after selection
        // without broad storage access or the old file-picker confirmation step;
        // keep ACTION_OPEN_DOCUMENT as the fallback/files path when Photo Picker
        // is unavailable.
        // WHY: launching Android's document picker pauses/resumes the Activity.
        // Treat the return as a native-dialog transition, not a terminal failure,
        // so the blank watchdog and passive page lifecycle probes cannot reload
        // or scroll the WebView while the user is trying to attach media.
        nativePickerQuietUntilMs = System.currentTimeMillis() + 8000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent photoPicker = new Intent(MediaStore.ACTION_PICK_IMAGES);
            photoPicker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (launchUploadPicker(photoPicker, "photo-picker")) {
                return;
            }
        }
        if (!launchUploadPicker(uploadDocumentPickerIntent(), "document-picker")) {
            nativePickerQuietUntilMs = 0;
            toast("No Android file picker available");
        }
    }

    private boolean launchUploadPicker(Intent intent, String pickerKind) {
        pendingUploadPickerKind = pickerKind == null ? "" : pickerKind;
        try {
            startActivityForResult(intent, REQUEST_UPLOAD_MEDIA);
            Log.i(UPLOAD_LOG_TAG, "picker-launch kind=" + pendingUploadPickerKind
                    + " action=" + intent.getAction());
            return true;
        } catch (Exception exc) {
            Log.w(UPLOAD_LOG_TAG, "picker-launch-failed kind=" + pendingUploadPickerKind
                    + " error=" + exc.getClass().getSimpleName());
            pendingUploadPickerKind = "";
            return false;
        }
    }

    private Intent uploadDocumentPickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/*",
                "video/*",
                "application/pdf",
                "text/*",
                "application/octet-stream"
        });
        return intent;
    }

    private void handleIncomingMediaShare(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            List<Uri> uris = uploadUrisFromShareIntent(intent);
            for (Uri uri : uris) {
                prepareReadAccessForUpload(intent, uri);
                uploadMediaUri(uri, true);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            List<Uri> uris = uploadUrisFromShareIntent(intent);
            for (Uri uri : uris) {
                prepareReadAccessForUpload(intent, uri);
                uploadMediaUri(uri, true);
            }
        }
    }

    private List<Uri> uploadUrisFromResult(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data == null) {
            return uris;
        }
        addUploadUriIfMissing(uris, data.getData());
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                ClipData.Item item = clipData.getItemAt(index);
                if (item != null) {
                    addUploadUriIfMissing(uris, item.getUri());
                }
            }
        }
        return uris;
    }

    private List<Uri> uploadUrisFromShareIntent(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) {
            return uris;
        }
        Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        addUploadUriIfMissing(uris, single);
        ArrayList<Uri> extraUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (extraUris != null) {
            for (Uri uri : extraUris) {
                addUploadUriIfMissing(uris, uri);
            }
        }
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
            for (int index = 0; index < clipData.getItemCount(); index++) {
                ClipData.Item item = clipData.getItemAt(index);
                if (item != null) {
                    addUploadUriIfMissing(uris, item.getUri());
                }
            }
        }
        Log.i(UPLOAD_LOG_TAG, "share-uris count=" + uris.size()
                + " action=" + intent.getAction());
        return uris;
    }

    private void addUploadUriIfMissing(List<Uri> uris, Uri uri) {
        if (uri == null) {
            return;
        }
        String value = uri.toString();
        for (Uri existing : uris) {
            if (existing != null && existing.toString().equals(value)) {
                return;
            }
        }
        uris.add(uri);
    }

    private void prepareReadAccessForUpload(Intent data, Uri uri) {
        if (data == null || uri == null) {
            return;
        }
        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (takeFlags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
            Log.i(UPLOAD_LOG_TAG, "uri-read-persisted scheme=" + uri.getScheme()
                    + " authority=" + uri.getAuthority());
        } catch (Exception exc) {
            // Photo Picker grants transient read access and does not always allow
            // persistable permissions. The upload happens immediately, so this is
            // diagnostic only; do not block a valid one-tap media selection.
            Log.i(UPLOAD_LOG_TAG, "uri-read-transient scheme=" + uri.getScheme()
                    + " authority=" + uri.getAuthority()
                    + " reason=" + exc.getClass().getSimpleName());
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
        String finalContentType = contentType;
        String uploadTargetWindowId = uploadAssociationWindowId();
        Log.i(UPLOAD_LOG_TAG, "upload-start source=" + (fromShare ? "share" : "picker")
                + " scheme=" + uri.getScheme()
                + " authority=" + uri.getAuthority()
                + " mime=" + finalContentType
                + " declaredSize=" + declaredSize
                + " targetWindow=" + uploadTargetWindowId);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(controlUrlForPath("/upload-media?filename=" + urlEncode(displayName)));
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
                Log.i(UPLOAD_LOG_TAG, "upload-response code=" + code
                        + " bytes=" + uploadedBytes
                        + " bodyChars=" + (body == null ? 0 : body.length()));
                JSONObject payload = new JSONObject(body);
                uiHandler.post(() -> showUploadedMediaResult(payload, uploadTargetWindowId));
            } catch (Exception exc) {
                Log.e(UPLOAD_LOG_TAG, "upload-failed source=" + (fromShare ? "share" : "picker")
                        + " scheme=" + uri.getScheme()
                        + " authority=" + uri.getAuthority()
                        + " error=" + exc.getClass().getSimpleName(), exc);
                wakeLaptopForTerminal("upload-unreachable");
                scheduleTerminalWakeRetry("upload-unreachable");
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

    private void showUploadedMediaResult(JSONObject payload, String targetWindowId) {
        if (!payload.optBoolean("ok", false)) {
            Log.w(UPLOAD_LOG_TAG, "upload-result-error error=" + payload.optString("error", ""));
            toast(payload.optString("error", "Media upload failed"));
            return;
        }
        String path = payload.optString("path", "");
        if (path.isEmpty()) {
            Log.w(UPLOAD_LOG_TAG, "upload-result-missing-path");
            toast("Media uploaded, but no path returned");
            return;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("WEzterm uploaded media path", path));
        }
        String filename = payload.optString("filename", "uploaded media");
        long bytes = payload.optLong("bytes", 0);
        UploadAssociation upload = rememberUploadedMediaResult(targetWindowId, path, filename, bytes);
        if (upload == null) {
            upload = new UploadAssociation("", path, filename, bytes, System.currentTimeMillis());
        }
        Log.i(UPLOAD_LOG_TAG, "upload-result-ok bytes=" + bytes
                + " filename=" + filename
                + " targetWindow=" + targetWindowId);
        showUploadedMediaInline(upload);
    }

    private void showUploadedMediaInline(UploadAssociation upload) {
        // WHY: the v2.57 upload "success" dialog was still a foreground modal over
        // the exact post-upload typing area. Keep normal upload completion silent
        // and reuse the protected title strip/clipboard/composer path instead;
        // real upload errors still use Toasts so failures remain visible.
        copyUploadPathToClipboard(upload);
        updateSessionTitleStrip(currentSessionTitleDisplay());
        stageUploadedMediaPathForSend(upload);
        settleLiveBottomAfterPaste("upload-result");
    }

    private void stageUploadedMediaPathForSend(UploadAssociation upload) {
        if (upload == null || upload.path == null || upload.path.trim().isEmpty()) {
            return;
        }
        if (promptComposerBar == null || promptComposerInput == null) {
            focusTerminalInputSoon(false);
            return;
        }
        String uploadPath = upload.path.trim();
        showDockedPromptComposer("upload-result");
        String existingText = promptComposerInput.getText().toString();
        String nextText;
        if (existingText.trim().isEmpty()) {
            nextText = uploadPath;
        } else if (existingText.contains(uploadPath)) {
            nextText = existingText;
        } else {
            nextText = existingText + (existingText.endsWith("\n") ? "" : "\n") + uploadPath;
        }
        // WHY: choosing a phone image is the user's attach step. The returned
        // desktop path must be visible in the same native composer that Send
        // submits, not only hidden in the title strip or clipboard.
        promptComposerProgrammaticTextChange = true;
        try {
            promptComposerInput.setText(nextText);
            promptComposerInput.setSelection(promptComposerInput.getText().length());
        } finally {
            promptComposerProgrammaticTextChange = false;
        }
        promptComposerDraftTargetKey = hasStableWindowId(upload.windowId)
                ? upload.windowId.trim()
                : promptComposerTargetKey();
        promptComposerDraftLocalGeneration++;
        updateStartButtonLabel();
        restoreDockedPromptComposerFocus("upload-result");
        Log.i(UPLOAD_LOG_TAG, "upload-staged-for-send pathChars=" + uploadPath.length()
                + " targetWindow=" + promptComposerDraftTargetKey);
    }

    private UploadAssociation rememberUploadedMediaResult(String targetWindowId, String path, String filename, long bytes) {
        String stableWindowId = targetWindowId;
        if (!hasStableWindowId(stableWindowId)) {
            stableWindowId = uploadAssociationWindowId();
        }
        if (!hasStableWindowId(stableWindowId) || prefs == null || path == null || path.trim().isEmpty()) {
            return null;
        }
        UploadAssociation upload = new UploadAssociation(
                stableWindowId.trim(),
                path.trim(),
                filename,
                bytes,
                System.currentTimeMillis()
        );
        // WHY: the server upload endpoint only returns a transient result payload.
        // Persist the last upload by immutable tmux `@windowId` so refresh, Active
        // navigation, app return, or an in-flight upload completion cannot make the
        // attachment/path look randomly detached from the session that owns it.
        prefs.edit()
                .putString(uploadPrefsKey(PREF_UPLOAD_PATH_PREFIX, upload.windowId), upload.path)
                .putString(uploadPrefsKey(PREF_UPLOAD_FILENAME_PREFIX, upload.windowId), upload.filename)
                .putLong(uploadPrefsKey(PREF_UPLOAD_BYTES_PREFIX, upload.windowId), upload.bytes)
                .putLong(uploadPrefsKey(PREF_UPLOAD_UPDATED_PREFIX, upload.windowId), upload.updatedAtMs)
                .apply();
        if (upload.windowId.equals(uploadAssociationWindowId())) {
            updateSessionTitleStrip(currentSessionTitleDisplay());
        }
        return upload;
    }

    private UploadAssociation rememberedUploadForWindow(String windowId) {
        if (!hasStableWindowId(windowId) || prefs == null) {
            return null;
        }
        String stableWindowId = windowId.trim();
        String path = prefs.getString(uploadPrefsKey(PREF_UPLOAD_PATH_PREFIX, stableWindowId), "");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        String filename = prefs.getString(uploadPrefsKey(PREF_UPLOAD_FILENAME_PREFIX, stableWindowId), "uploaded media");
        long bytes = prefs.getLong(uploadPrefsKey(PREF_UPLOAD_BYTES_PREFIX, stableWindowId), 0);
        long updatedAtMs = prefs.getLong(uploadPrefsKey(PREF_UPLOAD_UPDATED_PREFIX, stableWindowId), 0);
        return new UploadAssociation(stableWindowId, path.trim(), filename, bytes, updatedAtMs);
    }

    private UploadAssociation currentRememberedUpload() {
        return rememberedUploadForWindow(uploadAssociationWindowId());
    }

    private String uploadPrefsKey(String prefix, String windowId) {
        return prefix + (windowId == null ? "" : windowId.trim());
    }

    private String uploadAssociationWindowId() {
        if (hasStableWindowId(currentPhoneWindowId)) {
            return currentPhoneWindowId.trim();
        }
        if (hasStableWindowId(selectedPhoneWindowId)) {
            return selectedPhoneWindowId.trim();
        }
        return "";
    }

    private void clearRememberedUploadForWindow(String windowId, String reason) {
        if (!hasStableWindowId(windowId) || prefs == null) {
            return;
        }
        String stableWindowId = windowId.trim();
        prefs.edit()
                .remove(uploadPrefsKey(PREF_UPLOAD_PATH_PREFIX, stableWindowId))
                .remove(uploadPrefsKey(PREF_UPLOAD_FILENAME_PREFIX, stableWindowId))
                .remove(uploadPrefsKey(PREF_UPLOAD_BYTES_PREFIX, stableWindowId))
                .remove(uploadPrefsKey(PREF_UPLOAD_UPDATED_PREFIX, stableWindowId))
                .apply();
    }

    private void showRememberedUploadForCurrentWindow() {
        UploadAssociation upload = currentRememberedUpload();
        if (upload == null) {
            toast("No upload associated with this session");
            return;
        }
        copyUploadPathToClipboard(upload);
        updateSessionTitleStrip(currentSessionTitleDisplay());
        focusTerminalInputSoon(false);
    }

    private boolean pasteRememberedUploadForCurrentWindow() {
        UploadAssociation upload = currentRememberedUpload();
        if (upload == null) {
            toast("No upload associated with this session");
            return true;
        }
        pasteUploadedMediaPath(upload);
        return true;
    }

    private void copyUploadPathToClipboard(UploadAssociation upload) {
        if (upload == null || upload.path == null || upload.path.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("WEzterm uploaded media path", upload.path));
        }
    }

    private void pasteUploadedMediaPath(UploadAssociation upload) {
        if (upload == null || upload.path == null || upload.path.trim().isEmpty()) {
            toast("No upload path available");
            return;
        }
        postText(appendStableWindowQuery("/paste", upload.windowId), upload.path, pastePayload -> {
            if (!pastePayload.optBoolean("ok", false)) {
                toast(pastePayload.optString("error", "Paste path failed"));
                return;
            }
            settleLiveBottomAfterPaste("upload-paste");
            hideDockedPromptComposer(false, true);
            focusTerminalInputSoon(false);
        }, exc -> toast("WEzterm control is not reachable"));
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
        hideDockedPromptComposerForNavigation("active-dialog");
        // WHY: Active Sessions is the phone's hot tab switcher. It must not wait
        // for `/sessions`, which also scans old Codex sessions and heavier
        // per-window pane status. Use the light `/tabs` payload for immediate
        // stable `@windowId` Open/Close; Old Sessions has its own old-only
        // saved-session endpoint so it cannot inherit live-window rows or broad
        // `/sessions` latency.
        getJsonWithRetry("/tabs?light=1", payload -> showActiveSessionsDialog(payload, "Active Sessions", true), exc ->
                getJsonWithRetry("/tabs", payload -> showActiveSessionsDialog(payload, "Active Sessions", true))
        );
    }

    private void showOldSessions() {
        hideDockedPromptComposerForNavigation("old-dialog");
        // WHY: Old Sessions is a saved-session picker, not a live-session scan.
        // Keep APK Old on the same `/sessions?oldOnly=1` contract as the web
        // remote so Android cannot drift into showing live rows or stale
        // process-name titles when the broad `/sessions` payload changes.
        getJsonWithRetry("/sessions?oldOnly=1", this::showOldSessionsDialog, exc ->
                toast("WEzterm control is not reachable")
        );
    }

    private void showWorkspaces() {
        hideDockedPromptComposerForNavigation("workspace-dialog");
        // WHY: the phone Workspace button must be the same user-facing restore
        // surface as desktop `[Workspace]` and `/web`, not a backend-only command.
        // The control server owns snapshot recommendation so Android cannot drift
        // into loading a reduced post-restart `last.json`.
        getJsonWithRetry("/workspace-list?limit=40", this::showWorkspaceDialog, exc ->
                toast("WEzterm control is not reachable")
        );
    }

    private void showCrashedSessions() {
        hideDockedPromptComposerForNavigation("crashed-dialog");
        getJsonWithRetry("/crashed-sessions", this::showCrashedSessionsDialog, exc ->
                toast("WEzterm control is not reachable")
        );
    }

    private void showNeedsAttention() {
        hideDockedPromptComposerForNavigation("needs-attention-dialog");
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
        addActiveDialogActions(list, dialogRef);
        JSONObject activeWindow = activeWindowFromPayload(payload);
        JSONArray groups = payload.optJSONArray("groups");
        if (preferGroups && groups != null && groups.length() > 0) {
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                JSONArray windows = group.optJSONArray("windows");
                if (windows == null || windows.length() == 0) {
                    continue;
                }
                // WHY: `/tabs` now groups Active Sessions by action state/color
                // for phone scanning: needs-input first, completed middle,
                // working bottom. Do not pull Current above those groups again;
                // active row stays in its state bucket without prefixing the visible title.
                List<JSONObject> groupRows = sortedWindows(windows);
                if (groupRows.isEmpty()) {
                    continue;
                }
                addSectionHeader(list, group.optString("label", "Sessions"), groupRows.size());
                addTabRows(list, groupRows, session, dialogRef, null, false);
            }
        } else {
            JSONArray windows = payload.optJSONArray("displayWindows");
            if (windows == null) {
                windows = payload.optJSONArray("topWindows");
            }
            if (windows == null) {
                windows = payload.getJSONArray("windows");
            }
            List<JSONObject> rows = sortedWindows(windows);
            if (rows.isEmpty()) {
                addSectionHeader(list, "Nothing needs attention", 0);
            } else {
                addTabRows(list, rows, session, dialogRef, null, !preferGroups);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scrollView)
                .show();
        dialogRef[0] = dialog;
        // WHY: Android AlertDialog can focus a child row or preserve a measured
        // scroll position, which made Active Sessions open in the middle. The phone picker
        // is always a "start from the newest/attention section" surface.
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void addActiveDialogActions(LinearLayout list, AlertDialog[] dialogRef) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, dp(8));
        // WHY: the stock AlertDialog three-button footer can stack or disappear
        // below the phone viewport in Active Sessions, exactly as the user
        // reported. Keep these actions inside the scrollable dialog content as a
        // compact row so New/Rename/Cancel are all visible and scroll with the
        // session list instead of being clipped by Android's button panel.
        actions.addView(activeDialogActionButton("New", v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            controlAndSettleLiveBottom("/new?fast=1", "", "new-session");
        }));
        actions.addView(activeDialogActionButton("Rename", v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showRenameCurrentTab();
        }));
        actions.addView(activeDialogActionButton("Cancel", v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        }));
        list.addView(actions);
    }

    private Button activeDialogActionButton(String label, View.OnClickListener listener) {
        Button action = new Button(this);
        action.setText(label);
        action.setAllCaps(false);
        action.setTextSize(12);
        action.setSingleLine(true);
        action.setTextColor(Color.rgb(205, 214, 244));
        action.setGravity(android.view.Gravity.CENTER);
        action.setPadding(dp(4), 0, dp(4), 0);
        setTouchableBackground(action, Color.rgb(49, 50, 68), Color.rgb(137, 180, 250));
        action.setOnClickListener(v -> {
            flashTap(v);
            listener.onClick(v);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1
        );
        params.setMargins(dp(2), 0, dp(2), 0);
        action.setLayoutParams(params);
        return action;
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
                .setNeutralButton("Crashed", (d, which) -> showCrashedSessions())
                .setNegativeButton("Cancel", null)
                .show();
        dialogRef[0] = dialog;
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void showWorkspaceDialog(JSONObject payload) throws Exception {
        JSONArray snapshots = payload.optJSONArray("snapshots");
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFocusable(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setFocusable(false);
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        scrollView.addView(list);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        addWorkspaceDialogActions(list, dialogRef);

        String recommendedReason = payload.optString("recommendedReason", "");
        if (!recommendedReason.trim().isEmpty()) {
            TextView note = new TextView(this);
            note.setText("Default restore: " + recommendedReason);
            note.setTextSize(12);
            note.setTextColor(Color.rgb(166, 173, 200));
            note.setPadding(0, 0, 0, dp(8));
            list.addView(note);
        }

        if (snapshots == null || snapshots.length() == 0) {
            addSectionHeader(list, "No workspace snapshots found", 0);
        } else {
            for (int i = 0; i < snapshots.length(); i++) {
                addWorkspaceSnapshotRow(list, snapshots.getJSONObject(i), dialogRef);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Workspace")
                .setView(scrollView)
                .setNegativeButton("Cancel", null)
                .show();
        dialogRef[0] = dialog;
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void addWorkspaceDialogActions(LinearLayout list, AlertDialog[] dialogRef) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, dp(8));
        actions.addView(activeDialogActionButton("Save", v -> saveWorkspaceAndReopenDialog(dialogRef)));
        actions.addView(activeDialogActionButton("Close out", v -> confirmCloseOutWorkspace(dialogRef)));
        actions.addView(activeDialogActionButton("Cancel", v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
        }));
        list.addView(actions);
    }

    private void showCrashedSessionsDialog(JSONObject payload) throws Exception {
        JSONArray crashedSessions = payload.optJSONArray("crashedSessions");
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFocusable(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setFocusable(false);
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        scrollView.addView(list);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        if (crashedSessions == null || crashedSessions.length() == 0) {
            addSectionHeader(list, "No crashed sessions found", 0);
        } else {
            String currentDate = "";
            for (int i = 0; i < crashedSessions.length(); i++) {
                JSONObject session = crashedSessions.getJSONObject(i);
                if (!"user".equals(session.optString("threadSource", "user"))) {
                    continue;
                }
                String dateLabel = session.optString("dateLabel", session.optString("updatedGroup", "Older"));
                if (!dateLabel.equals(currentDate)) {
                    currentDate = dateLabel;
                    addSectionHeader(list, currentDate, countCrashedSessionsForDate(crashedSessions, currentDate));
                }
                addCrashedSessionRow(list, session, dialogRef);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Restore Crashed Sessions")
                .setView(scrollView)
                .setPositiveButton("Old Sessions", (d, which) -> showOldSessions())
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

    private int countCrashedSessionsForDate(JSONArray crashedSessions, String dateLabel) throws Exception {
        int count = 0;
        for (int i = 0; i < crashedSessions.length(); i++) {
            JSONObject session = crashedSessions.getJSONObject(i);
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

    private JSONObject activeWindowFromPayload(JSONObject payload) throws Exception {
        JSONArray windows = payload.optJSONArray("windows");
        if (windows == null) {
            return null;
        }
        for (int i = 0; i < windows.length(); i++) {
            JSONObject window = windows.getJSONObject(i);
            if (window.optBoolean("active", false)) {
                return window;
            }
        }
        return null;
    }

    private boolean sameWindow(JSONObject left, JSONObject right) {
        if (left == null || right == null) {
            return false;
        }
        String leftId = left.optString("windowId", "");
        String rightId = right.optString("windowId", "");
        if (!leftId.isEmpty() && leftId.equals(rightId)) {
            return true;
        }
        return left.optInt("index", -1) == right.optInt("index", -2);
    }

    private List<JSONObject> sortedWindows(JSONArray windows) throws Exception {
        return sortedWindows(windows, null);
    }

    private List<JSONObject> sortedWindows(JSONArray windows, JSONObject skipWindow) throws Exception {
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < windows.length(); i++) {
            JSONObject window = windows.getJSONObject(i);
            if (sameWindow(window, skipWindow)) {
                continue;
            }
            sorted.add(window);
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
            AlertDialog[] dialogRef,
            JSONObject skipWindow,
            boolean includeChildRows
    ) throws Exception {
        for (int i = 0; i < windows.size(); i++) {
            JSONObject window = windows.get(i);
            addTabRow(list, window, session, dialogRef);
            // WHY: `/tabs` keeps child metadata so the parent row can say
            // "1 child lane" and diagnostics can inspect workers. The phone's
            // visible Active Sessions list is operator-facing, so child/proof
            // rows must not reappear under their parent while web stays clean.
            if (!includeChildRows) {
                continue;
            }
            JSONArray children = window.optJSONArray("children");
            if (children == null) {
                continue;
            }
            List<JSONObject> childRows = sortedWindows(children, skipWindow);
            for (int childIndex = 0; childIndex < childRows.size(); childIndex++) {
                addTabRow(list, childRows.get(childIndex), session, dialogRef);
            }
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
        // WHY: Mantis owns the shared title system in `/sessions`; Android is a
        // display client. Do not summarize titles here or the phone APK will
        // drift from desktop tmux, web remote, and Old Sessions.
        String title = window.optString("title", window.optString("name", "shell"));
        String detail = tabDetail(window, session);
        String status = window.optString("status", "idle");
        String statusLabel = window.optString("statusLabel", "Done");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(window.optBoolean("isChild", false) ? dp(18) : 0, dp(3), 0, dp(3));

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

        TextView statusDot = new StatusDotTextView(this);
        statusDot.setText("●");
        statusDot.setTextSize(14);
        statusDot.setGravity(android.view.Gravity.CENTER);
        statusDot.setIncludeFontPadding(false);
        statusDot.setContentDescription(statusLabel);
        applySessionStatusDot(statusDot, status, window.optBoolean("needsAttention", false), statusLabel);

        TextView titleText = new TextView(this);
        titleText.setText(title);
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
        // WHY: old sessions must use the same server-provided title as Active
        // Sessions, web remote, and tmux. The APK should never invent a second
        // naming table from raw prompts or process names.
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
            openOldSessionDirectly(sessionId, cwd, title);
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
            openOldSessionDirectly(sessionId, cwd, title);
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

    private void addWorkspaceSnapshotRow(
            LinearLayout list,
            JSONObject snapshot,
            AlertDialog[] dialogRef
    ) {
        String path = snapshot.optString("path", "");
        String title = workspaceSnapshotTitle(snapshot);
        String detail = workspaceSnapshotDetail(snapshot);

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
            openWorkspaceSnapshot(path, title, dialogRef);
        });

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(15);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(snapshot.optBoolean("isRecommended", false)
                ? Color.rgb(166, 227, 161)
                : Color.rgb(205, 214, 244));
        titleText.setSingleLine(false);
        titleText.setMaxLines(Integer.MAX_VALUE);
        titleText.setEllipsize(null);
        titleText.setIncludeFontPadding(false);
        titleText.setHorizontallyScrolling(false);

        TextView detailText = new TextView(this);
        detailText.setText(detail);
        detailText.setTextSize(12);
        detailText.setTextColor(Color.rgb(166, 173, 200));
        detailText.setSingleLine(false);
        detailText.setMaxLines(3);
        detailText.setIncludeFontPadding(false);

        openPanel.addView(titleText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        openPanel.addView(detailText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button load = new Button(this);
        load.setText("Load");
        load.setAllCaps(false);
        load.setTextSize(12);
        load.setTextColor(Color.rgb(30, 30, 46));
        setTouchableBackground(load, Color.rgb(166, 227, 161), Color.rgb(148, 226, 213));
        load.setPadding(dp(3), 0, dp(3), 0);
        load.setOnClickListener(v -> {
            flashTap(v);
            openWorkspaceSnapshot(path, title, dialogRef);
        });

        row.addView(openPanel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                dp(92),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        load.setMinHeight(dp(72));
        loadParams.setMargins(dp(6), 0, 0, 0);
        row.addView(load, loadParams);
        list.addView(row);
    }

    private void addCrashedSessionRow(
            LinearLayout list,
            JSONObject session,
            AlertDialog[] dialogRef
    ) {
        String sessionId = session.optString("id", "");
        String title = session.optString("title", sessionId);
        String cwd = session.optString("cwd", "");
        String detail = crashedSessionDetail(session);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        LinearLayout openPanel = new LinearLayout(this);
        openPanel.setOrientation(LinearLayout.VERTICAL);
        openPanel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        openPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        setTouchableBackground(openPanel, Color.rgb(49, 50, 68), Color.rgb(249, 226, 175));
        openPanel.setClickable(true);
        openPanel.setOnClickListener(v -> {
            flashTap(v);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            restoreCrashedSessionDirectly(sessionId, cwd, title);
        });

        TextView titleText = new TextView(this);
        titleText.setText(title);
        titleText.setTextSize(15);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.rgb(249, 226, 175));
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
                        ClipData.newPlainText("WEzterm crashed session", title + "\n" + sessionId)
                );
                toast("Crashed session copied");
            }
            return true;
        });

        TextView detailText = new TextView(this);
        detailText.setText(detail);
        detailText.setTextSize(12);
        detailText.setTextColor(Color.rgb(166, 173, 200));
        detailText.setSingleLine(false);
        detailText.setMaxLines(3);
        detailText.setIncludeFontPadding(false);

        // WHY: Restore Crashed Sessions is intentionally not an alias for Old
        // Sessions. The server only sends sessions that were once seen live and
        // later disappeared without an approved tmux `[x]`/Android Close marker.
        // Keep the latest server title as the primary row text and keep the raw
        // first/last prompt out of the title area so this list stays scannable.
        openPanel.addView(titleText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        openPanel.addView(detailText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        Button restore = new Button(this);
        restore.setText("Restore");
        restore.setAllCaps(false);
        restore.setTextSize(12);
        restore.setTextColor(Color.rgb(30, 30, 46));
        setTouchableBackground(restore, Color.rgb(249, 226, 175), Color.rgb(148, 226, 213));
        restore.setPadding(dp(3), 0, dp(3), 0);
        restore.setOnClickListener(v -> {
            flashTap(v);
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            restoreCrashedSessionDirectly(sessionId, cwd, title);
        });

        row.addView(openPanel, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(
                dp(92),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        restore.setMinHeight(dp(72));
        restoreParams.setMargins(dp(6), 0, 0, 0);
        row.addView(restore, restoreParams);
        list.addView(row);
    }

    private String crashedSessionDetail(JSONObject session) {
        String reason = session.optBoolean("interrupted", false)
                ? "Interrupted before completion"
                : "No clean close marker";
        String shortPath = session.optString("shortPath", "");
        if (!shortPath.isEmpty()) {
            reason += " · " + shortPath;
        }
        return reason;
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
                    controlAndSettleLiveBottom(path, "", "old-session");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openOldSessionDirectly(String sessionId, String cwd, String title) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            toast("Old session id missing");
            return;
        }
        // WHY: Old Sessions is navigation, not a destructive action. Keeping a
        // confirm/list dialog in front after the user taps Resume made the phone
        // look stuck in the picker instead of opening the selected Codex session.
        String path = "/resume-session?fast=1&sessionId=" + urlEncode(sessionId)
                + "&cwd=" + urlEncode(cwd);
        controlAndSettleLiveBottom(path, "", "old-session", title);
    }

    private void confirmResumeCrashedSession(String sessionId, String cwd, String title) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            toast("Crashed session id missing");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Restore crashed session?")
                .setMessage(title)
                .setPositiveButton("Restore", (dialog, which) -> {
                    String path = "/resume-session?fast=1&sessionId=" + urlEncode(sessionId)
                            + "&cwd=" + urlEncode(cwd);
                    controlAndSettleLiveBottom(path, "", "crashed-session");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreCrashedSessionDirectly(String sessionId, String cwd, String title) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            toast("Crashed session id missing");
            return;
        }
        String path = "/resume-session?fast=1&sessionId=" + urlEncode(sessionId)
                + "&cwd=" + urlEncode(cwd);
        controlAndSettleLiveBottom(path, "", "crashed-session", title);
    }

    private String workspaceSnapshotTitle(JSONObject snapshot) {
        return workspaceSnapshotTime(snapshot) + " · " + workspaceSnapshotLabel(snapshot);
    }

    private String workspaceSnapshotTime(JSONObject snapshot) {
        String createdAt = snapshot.optString("createdAt", "");
        if (createdAt.trim().isEmpty()) {
            return "unknown";
        }
        return createdAt.replace("T", " ").substring(0, Math.min(19, createdAt.length()));
    }

    private String workspaceSnapshotLabel(JSONObject snapshot) {
        String reason = snapshot.optString("saveReason", "");
        if (snapshot.optBoolean("isRecommended", false)) {
            return "Recommended workspace";
        }
        if (snapshot.optBoolean("isLatest", false)) {
            return "autosave".equals(reason) ? "Latest autosave" : "Latest workspace";
        }
        if ("workspace-close-out-all".equals(reason) || "close-out".equals(reason)) {
            return "Close-out save";
        }
        return reason.trim().isEmpty() ? "Saved workspace" : reason;
    }

    private String workspaceSnapshotDetail(JSONObject snapshot) {
        return snapshot.optInt("windowCount", 0) + " windows · "
                + snapshot.optInt("exactRestoreCount", 0) + " exact · "
                + snapshot.optString("path", "");
    }

    private void saveWorkspaceAndReopenDialog(AlertDialog[] dialogRef) {
        if (dialogRef[0] != null) {
            dialogRef[0].dismiss();
        }
        getJsonWithRetry("/workspace-save", payload -> showWorkspaces(), exc ->
                toast("WEzterm control is not reachable")
        );
    }

    private void confirmCloseOutWorkspace(AlertDialog[] dialogRef) {
        if (dialogRef[0] != null) {
            dialogRef[0].dismiss();
        }
        new AlertDialog.Builder(this)
                .setTitle("Close out workspace?")
                .setMessage("This saves the current workspace first, then closes the saved tmux windows and keeps a restore anchor.")
                .setPositiveButton("Save + Close", (dialog, which) ->
                        getJsonWithRetry("/workspace-close-out?yes=1", payload -> {
                            if (!payload.optBoolean("ok", false)) {
                                toast(payload.optString("error", "Workspace close-out failed"));
                                return;
                            }
                            clearRememberedCloseTarget("workspace-close-out");
                            refreshCaptureRendererSoon("workspace-close-out");
                            scheduleToolbarStatusDotRefresh(0);
                        }, exc -> toast("WEzterm control is not reachable"))
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openWorkspaceSnapshot(String path, String title, AlertDialog[] dialogRef) {
        if (path == null || path.trim().isEmpty()) {
            toast("Workspace snapshot path missing");
            return;
        }
        if (dialogRef[0] != null) {
            dialogRef[0].dismiss();
        }
        // WHY: Workspace Load opens several tmux windows, then should land on the
        // saved active tab just like Old Sessions lands on the resumed tab. Route
        // through the Bottom-like settle path so the phone does not fire a hidden
        // restore and leave the user staring at the previous session.
        String requestPath = "/workspace-restore?yes=1&path=" + urlEncode(path);
        controlAndSettleLiveBottom(requestPath, "", "workspace-restore", title);
    }

    private void confirmClose() {
        // WHY: the bottom Close button must close the phone-selected tmux window,
        // not whichever grouped `main_view_*` window the server happens to report
        // as active after a slow Active Sessions switch. Keep the exact stable
        // `@windowId` captured by the last successful open and refuse to fall
        // back to raw `/close?fast=1`, which previously closed the wrong session.
        if (hasRememberedCloseTarget()) {
            confirmClose(selectedPhoneWindowIndex, selectedPhoneWindowId, selectedPhoneWindowTitle);
            return;
        }
        getJsonWithRetry("/active", payload -> {
            JSONObject window = payload.optJSONObject("window");
            if (window == null) {
                toast("Close target missing; open Active and pick the session first");
                return;
            }
            String windowId = window.optString("windowId", "");
            if (!hasStableWindowId(windowId)) {
                toast("Close target missing; open Active and pick the session first");
                return;
            }
            int index = window.getInt("index");
            String title = window.optString("title", "current session");
            rememberSelectedPhoneWindow(index, windowId, title, "active-close");
            confirmClose(index, windowId, title);
        });
    }

    private void confirmClose(int index, String windowId, String title) {
        if (!hasStableWindowId(windowId)) {
            toast("Close target missing; open Active and pick the session first");
            return;
        }
        String stableWindowId = windowId.trim();
        String safeTitle = title == null || title.trim().isEmpty() ? stableWindowId : title.trim();
        new AlertDialog.Builder(this)
                .setTitle("Close " + safeTitle + "?")
                .setMessage("This closes that active session and whatever is running inside it.")
                .setPositiveButton("Close", (dialog, which) -> {
                    String path = "/close?fast=1&windowId=" + urlEncode(stableWindowId);
                    if (index >= 0) {
                        path += "&index=" + index;
                    }
                    clearRememberedCloseTarget("close-dispatched");
                    clearRememberedUploadForWindow(stableWindowId, "close-dispatched");
                    control(path, "Closed session");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean hasStableWindowId(String windowId) {
        return windowId != null && windowId.trim().startsWith("@");
    }

    private void rememberActivePhoneWindow(JSONObject window, String reason) {
        if (window == null) {
            return;
        }
        String windowId = window.optString("windowId", "");
        if (hasStableWindowId(windowId)) {
            currentPhoneWindowId = windowId.trim();
        }
    }

    private void updateSessionTitleStrip(JSONObject window) {
        if (window == null) {
            return;
        }
        String title = window.optString("title", "");
        if (title.trim().isEmpty()) {
            title = window.optString("name", "");
        }
        if (title.trim().isEmpty()) {
            title = window.optString("windowId", "WEzTerm");
        }
        updateSessionTitleStrip(title);
    }

    private void updateSessionTitleStrip(String title) {
        if (sessionTitleStrip == null) {
            return;
        }
        String display = title == null || title.trim().isEmpty() ? "WEzTerm" : title.trim();
        UploadAssociation upload = currentRememberedUpload();
        if (upload == null) {
            sessionTitleStrip.setText(display);
            sessionTitleStrip.setContentDescription("Active session: " + display);
            return;
        }
        // WHY: v2.67 proved that upload state belongs in the composer when the
        // user is about to Send. Prefixing the title strip with `Upload: ...`
        // made the active-session label look like a filename/title regression.
        // Keep the visible strip as the session title while preserving tap and
        // long-press upload recovery through the accessibility description.
        sessionTitleStrip.setText(display);
        sessionTitleStrip.setContentDescription(
                "Active session: " + display + ". Last upload for " + upload.windowId + ": "
                        + upload.path + ". Tap copies upload path. Long press pastes upload path."
        );
    }

    private String currentSessionTitleDisplay() {
        if (selectedPhoneWindowTitle != null && !selectedPhoneWindowTitle.trim().isEmpty()) {
            return selectedPhoneWindowTitle.trim();
        }
        if (hasStableWindowId(currentPhoneWindowId)) {
            return currentPhoneWindowId.trim();
        }
        return "WEzTerm";
    }

    private String promptComposerTargetKey() {
        if (hasStableWindowId(currentPhoneWindowId)) {
            return currentPhoneWindowId.trim();
        }
        if (hasStableWindowId(selectedPhoneWindowId)) {
            return selectedPhoneWindowId.trim();
        }
        return "unknown:" + terminalModeGeneration;
    }

    private String visibleTerminalTargetKey() {
        // WHY: touch scrolling and Scroll-menu actions operate on the terminal the
        // APK is visibly showing. `/active` polling can change `currentPhoneWindowId`
        // when another tmux lane selects `main_phone`, while the read-only capture
        // still shows the previous window. Prefer the selected/visible window for
        // scroll gestures so fast fling batches cannot land in a different lane.
        if (hasStableWindowId(selectedPhoneWindowId)) {
            return selectedPhoneWindowId.trim();
        }
        if (hasStableWindowId(currentPhoneWindowId)) {
            return currentPhoneWindowId.trim();
        }
        return "unknown:" + terminalModeGeneration;
    }

    private String promptComposerDraftSubmitTargetKey() {
        if (promptComposerInput != null
                && promptComposerInput.getText().length() > 0
                && hasStableWindowId(promptComposerDraftTargetKey)) {
            return promptComposerDraftTargetKey.trim();
        }
        return promptComposerTargetKey();
    }

    private String appendStableWindowQuery(String path) {
        return appendStableWindowQuery(path, promptComposerTargetKey());
    }

    private String appendStableWindowQuery(String path, String targetKey) {
        if (!hasStableWindowId(targetKey)) {
            return path;
        }
        String separator = path.contains("?") ? "&" : "?";
        // WHY: phone text/key/Stop actions must target the stable tmux
        // `@windowId` that owned the visible native composer, not whichever tab
        // becomes active while HTTP, Active switching, or proof scripts are in
        // flight. Without this query, a typed correction can paste into another session
        // and look like duplicate/random text.
        return path + separator + "windowId=" + urlEncode(targetKey.trim());
    }

    private boolean hasRememberedCloseTarget() {
        if (!hasStableWindowId(selectedPhoneWindowId)) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - selectedPhoneWindowUpdatedAtMs;
        return ageMs >= 0 && ageMs <= SELECTED_CLOSE_TARGET_MAX_AGE_MS;
    }

    private void rememberSelectedPhoneWindow(int index, String windowId, String title, String reason) {
        if (!hasStableWindowId(windowId)) {
            return;
        }
        // WHY: close safety follows the immutable tmux `@windowId`, not the
        // shifting index or a later `/active` lookup. This protects the exact
        // Active Sessions row the user opened from being replaced by another
        // `main_view_*` window during slow phone switching.
        selectedPhoneWindowId = windowId.trim();
        currentPhoneWindowId = selectedPhoneWindowId;
        selectedPhoneWindowIndex = index;
        selectedPhoneWindowTitle = title == null || title.trim().isEmpty()
                ? selectedPhoneWindowId
                : title.trim();
        selectedPhoneWindowUpdatedAtMs = System.currentTimeMillis();
        updateSessionTitleStrip(selectedPhoneWindowTitle);
    }

    private void clearRememberedCloseTarget(String reason) {
        selectedPhoneWindowId = "";
        selectedPhoneWindowIndex = -1;
        selectedPhoneWindowTitle = "";
        selectedPhoneWindowUpdatedAtMs = 0;
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

    private void controlAndSettleLiveBottom(String path, String message, String reason) {
        controlAndSettleLiveBottom(path, message, reason, message);
    }

    private void controlAndSettleLiveBottom(String path, String message, String reason, String targetTitle) {
        if (sessionSwitchInFlight) {
            return;
        }
        suppressTerminalBodyTapForPassiveNavigation(reason + "-dispatch");
        hideDockedPromptComposerForSessionSwitch(reason + "-dispatch");
        sessionSwitchInFlight = true;
        long paintShieldGeneration = showSessionSwitchPaintShield(reason);
        long generation = leaveReadModeForLiveInput();
        clearBroadSessionSwitchVisualMasks(reason + "-after-live-mode-cleanup");
        getJsonWithRetry(path, payload -> {
            if (!payload.optBoolean("ok", false)) {
                sessionSwitchInFlight = false;
                String error = payload.optString("error", "Command failed");
                forceHideSessionSwitchPaintShield(reason + "-failed");
                toast(error);
                return;
            }
            // WHY: New/Old/Crashed opens create or select a tmux window just like
            // Active Sessions, but they used to fall through the generic control
            // callback and skip the Bottom-like settle. That is the path that lets
            // xterm's dotted blank tail survive until the user manually taps Bottom.
            // Keep the settle passive and non-blocking: no WebView reload, no IME
            // focus request, no composer text destruction, and no second blocking
            // `/live-bottom` before the picker/confirm UI disappears.
            rememberOpenedWindowFromPayload(payload, reason, targetTitle);
            String openedWindowId = payload.optString("windowId", "");
            if ("resume-session-opened".equals(payload.optString("action", ""))
                    && hasStableWindowId(openedWindowId)) {
                // WHY: the real-phone Old Sessions proof opened a resumed tmux
                // window but left the visible phone view on the previous tab.
                // Route returned saved-session windowIds through the same
                // `/select-live` path that Active Sessions already proves instead
                // of trusting tmux's grouped select-window side effect.
                String selectPath = "/select-live?fast=1&windowId=" + urlEncode(openedWindowId);
                getJsonWithRetry(selectPath, selectPayload -> {
                    sessionSwitchInFlight = false;
                    if (!selectPayload.optBoolean("ok", false)) {
                        String error = selectPayload.optString("error", "Command failed");
                        forceHideSessionSwitchPaintShield(reason + "-select-live-failed");
                        toast(error);
                        return;
                    }
                    rememberOpenedWindowFromPayload(payload, reason + "-select-live", targetTitle);
                    finishBottomLikeControlOpen(generation, paintShieldGeneration, message, reason);
                }, exc -> {
                    sessionSwitchInFlight = false;
                    forceHideSessionSwitchPaintShield(reason + "-select-live-unreachable");
                    toast("WEzterm control is not reachable");
                });
                return;
            }
            sessionSwitchInFlight = false;
            finishBottomLikeControlOpen(generation, paintShieldGeneration, message, reason);
        }, exc -> {
            sessionSwitchInFlight = false;
            forceHideSessionSwitchPaintShield(reason + "-unreachable");
            toast("WEzterm control is not reachable");
        });
    }

    private void rememberOpenedWindowFromPayload(JSONObject payload, String reason, String fallbackTitle) {
        String windowId = payload.optString("windowId", "");
        if (!hasStableWindowId(windowId)) {
            return;
        }
        String title = payload.optString("title",
                payload.optString("name",
                        fallbackTitle == null || fallbackTitle.trim().isEmpty()
                                ? payload.optString("action", windowId)
                                : fallbackTitle));
        rememberSelectedPhoneWindow(payload.optInt("index", -1), windowId, title, reason);
    }

    private void finishBottomLikeControlOpen(long generation, long paintShieldGeneration, String message, String reason) {
        suppressTerminalBodyTapForPassiveNavigation(reason + "-settle");
        hideDockedPromptComposerForSessionSwitch(reason);
        long settleGeneration = terminalModeGeneration;
        settleSelectedTabViewport(reason);
        restoreLiveAfterTabOpen(reason, settleGeneration, paintShieldGeneration);
        confirmSelectedTabLiveBottomSoon(reason, settleGeneration);
        hideSessionSwitchPaintShieldSoon(reason + "-safety", paintShieldGeneration, 3200);
        if (generation != terminalModeGeneration) {
            return;
        }
        // WHY: Active/New/Old/Crashed open success is already visible as the
        // selected terminal. Android Toasts sit over the bottom typing/upload zone
        // and block fast retap after choosing a session, so normal session-open
        // success must stay silent while errors still toast from failure paths.
        // WHY: passive tab-open settle must finish in the toolbar-only Start
        // state. Refocusing the hidden xterm textarea here reintroduced the
        // duplicate-writing and no-Backspace IME path while the real fix only
        // needs Bottom-core repaint/resize work above.
        suppressTerminalBodyTapForPassiveNavigation(reason + "-complete");
    }

    private void selectTabForTyping(int index, String windowId, String title) {
        selectTabForTyping(index, windowId, title, null);
    }

    private void selectTabForTyping(int index, String windowId, String title, AlertDialog[] dialogRef) {
        if (sessionSwitchInFlight) {
            return;
        }
        suppressTerminalBodyTapForPassiveNavigation("select-live-dispatch");
        hideDockedPromptComposerForSessionSwitch("select-live-dispatch");
        if (dialogRef != null && dialogRef[0] != null) {
            // WHY: tapping an Active Sessions row is a navigation command. The
            // picker must leave the screen immediately after the tap instead of
            // waiting for settle calls; otherwise slow `/select-live` paths look
            // stuck and users can hit Close while thinking the new tab is open.
            dialogRef[0].dismiss();
        }
        sessionSwitchInFlight = true;
        long paintShieldGeneration = showSessionSwitchPaintShield("select-live");
        // WHY: opening a tab from the phone should never require a separate Live
        // tap or Enter press. The select happens first, then the selected tab is
        // forced back to the live bottom. Android keeps this as passive navigation:
        // no WebView reload, no xterm scroll burst, and no native composer/IME
        // left open unless the user deliberately taps to type again.
        long generation = leaveReadModeForLiveInput();
        clearBroadSessionSwitchVisualMasks("select-live-after-live-mode-cleanup");
        // WHY: switching tabs used to wait for the server to rebuild the full
        // tab list, including pane-tail status reads for every Codex window,
        // before returning to live typing. `/select-live` combines select and
        // bottom restore server-side, so the phone performs one round trip and
        // stale double-taps cannot stack slow switch requests.
        String path = "/select-live?fast=1&windowId=" + urlEncode(windowId) + "&index=" + index;
        getJsonWithRetry(path, payload -> {
            sessionSwitchInFlight = false;
            if (!payload.optBoolean("ok", false)) {
                String error = payload.optString("error", "Command failed");
                forceHideSessionSwitchPaintShield("select-live-failed");
                toast(error);
                return;
            }
            // WHY: `/select-live` already selects the stable tmux window and runs
            // the fast bottom restore server-side. Waiting on a second
            // `/live-bottom` before dismissing Active Sessions made slow phone
            // switches look stuck in the picker, which encouraged users to hit
            // Close while the UI could still be pointing at the previous active
            // window. Remember the exact `@windowId`, dismiss immediately, then
            // let ttyd paint the selected session through the existing late
            // confirmation settle instead of reloading the WebView.
            rememberSelectedPhoneWindow(index, windowId, title, "select-live");
            finishSelectedTabOpen(generation, paintShieldGeneration, title, dialogRef);
        }, exc -> {
            sessionSwitchInFlight = false;
            forceHideSessionSwitchPaintShield("select-live-unreachable");
            toast("WEzterm control is not reachable");
        });
    }

    private void finishSelectedTabOpen(long generation, long paintShieldGeneration, String title, AlertDialog[] dialogRef) {
        sessionSwitchInFlight = false;
        // WHY: a row tap in the Active Sessions dialog can finish with ACTION_UP
        // after the dialog disappears. If that release falls through to the
        // terminal body, WEzTerm treats it as a fresh tap-to-type and immediately
        // reopens the native composer after a passive session switch. Suppress only
        // terminal-body touches for the short switch settle window; toolbar buttons
        // and later deliberate terminal taps still work.
        suppressTerminalBodyTapForPassiveNavigation("select-live-settle");
        if (dialogRef != null && dialogRef[0] != null) {
            dialogRef[0].dismiss();
        }
        // WHY: the server-side session switch has already happened by this
        // point. If a stale tap or IME callback changed terminalModeGeneration
        // while `/select-live` was in flight, the old early return skipped
        // this cleanup and left the newly active tab vertically clipped by
        // the native composer. Always reclaim passive navigation space before
        // checking freshness; drafts are preserved for the next typing tap.
        hideDockedPromptComposerForSessionSwitch("select-live");
        long switchSettleGeneration = terminalModeGeneration;
        clearBroadSessionSwitchVisualMasks("select-live-success-render-owned");
        settleSelectedTabViewport("select-live");
        restoreLiveAfterTabOpen("select-live", switchSettleGeneration, paintShieldGeneration);
        confirmSelectedTabLiveBottomSoon("select-live", switchSettleGeneration);
        // WHY: v2.32's one-shot WebView refresh cleared some dotted proof frames,
        // but it also made real phone switches feel slow and could leave the user
        // staring at black terminal paint instead of the selected live bottom.
        // v2.33 keeps the same server-side `/select-live` + `/live-bottom` path
        // and lets explicit Refresh remain the manual transport repair.
        hideSessionSwitchPaintShieldSoon("select-live-safety", paintShieldGeneration, 3200);
        if (generation != terminalModeGeneration) {
            return;
        }
        // WHY: the 2026-06-22 real APK screenshots showed "Opened <title>" was
        // still covering/freezing the bottom typing/upload area after v2.55/v2.56.
        // The selected tab and status dot are the success signal; keep this path
        // silent unless an error occurs.
        // WHY: Active switch must end like an automatic Bottom press, not like a
        // tap-to-type. Keeping this path detached from xterm focus is what makes
        // the visible toolbar return to Start and prevents the duplicate-writing
        // hidden-textarea regression from coming back with the dotted-grid fix.
        suppressTerminalBodyTapForPassiveNavigation("select-live-complete");
    }

    private void settleSelectedTabViewport(String reason) {
        // WHY: `/select-live` already moves tmux to the selected window and exits
        // copy-mode server-side. Android's remaining job is only to let ttyd/xterm
        // repaint into the full toolbar-only height. Do not reload WebView, do not
        // open the native composer, and do not run the old focus/scrollIntoView
        // retry train here; those were the visible refresh storm. The one extra
        // xterm canvas normalize below is Active-switch-only because the
        // user-proven failure was entering a tab with a full dotted xterm blank
        // field below the Codex prompt. A bounded xterm atlas clear + row
        // repaint lets xterm redraw its own cells without forcing canvas/theme
        // colors, which is the regression that caused black-with-cursor screens.
        cancelViewerTypingPositionRetries(reason);
        pinTerminalViewportLocal();
        fitTerminalToCurrentViewSoon(reason);
        alignLiveBottomViewportForPassiveEntrySoon(reason);
        normalizeXtermCanvasAfterSessionSwitch(reason);
        keepPassiveSwitchXtermSettleAlive(reason);
        // WHY: v1.84/v1.85 proved that a visible-bitmap watchdog can mistake a
        // valid mostly-black terminal bottom for a blank foreground pane and
        // start WebView reloads after the terminal first appears. Active switching
        // keeps its dedicated xterm settle path and physical dot-grid proof, but
        // bitmap sampling is now proof-only so it cannot turn a live terminal into
        // the user's black screen with only a jumping cursor.
        cancelVisibleWebViewPaintWatchdog("select-live-settle");
        scheduleToolbarStatusDotRefresh(150);
    }

    private void confirmSelectedTabLiveBottomSoon(String reason, long generation) {
        // WHY: the control server's `/select-live` already does select+bottom, but
        // the `Phone Crash Restore` proof showed a real Android path where tmux
        // was still in copy-mode after the dialog dismissed and xterm repainted.
        // A delayed server-owned `/live-bottom` confirmation exits tmux copy-mode
        // without WebView reloads, focus bursts, canvas/theme black mutation, or
        // gesture changes. This is intentionally Active-switch-only; normal
        // tap-to-type and scrolling keep their existing ownership.
        uiHandler.postDelayed(() -> confirmSelectedTabLiveBottom(reason, generation), 260);
        uiHandler.postDelayed(() -> confirmSelectedTabLiveBottom(reason, generation), 760);
        uiHandler.postDelayed(() -> confirmSelectedTabLiveBottom(reason, generation), 1600);
        uiHandler.postDelayed(() -> confirmSelectedTabLiveBottom(reason, generation), 2600);
    }

    private void confirmSelectedTabLiveBottom(String reason, long generation) {
        if (generation != terminalModeGeneration || readModeSuppressesKeyboard || terminalHistoryViewportActive) {
            return;
        }
        restoreLiveAfterTabOpen(reason + "-confirm", generation, 0);
    }

    private void normalizeXtermCanvasAfterSessionSwitch(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = ++sessionSwitchLiveViewportGeneration;
        long maskGeneration = blankTailMaskGeneration;
        normalizeXtermCanvasAfterSessionSwitch(reason, generation, maskGeneration, 40);
        normalizeXtermCanvasAfterSessionSwitch(reason, generation, maskGeneration, 140);
        normalizeXtermCanvasAfterSessionSwitch(reason, generation, maskGeneration, 360);
    }

    private void keepPassiveSwitchXtermSettleAlive(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = ++passiveSwitchXtermSettleGeneration;
        // WHY: Active/New/Old tab-open must behave like an automatic Bottom
        // press without opening the native composer. The older blank-tail settle
        // callbacks are intentionally cancelled by typing/read/touch, but repeated
        // passive `/live-bottom` confirmations can also replace those callbacks
        // before the real xterm paint that exposes the dotted lower field. Keep a
        // separate passive-switch settle train alive through the repaint window;
        // `cancelXtermBlankTailMask` below still kills it on real touch, typing,
        // or read mode so it cannot become the old stuck black-bottom mask.
        webView.evaluateJavascript(xtermCanvasSettleScript(reason + "-passive-switch-immediate", true), null);
        keepPassiveSwitchXtermSettleAlive(reason, generation, 120);
        keepPassiveSwitchXtermSettleAlive(reason, generation, 360);
        keepPassiveSwitchXtermSettleAlive(reason, generation, 900);
        keepPassiveSwitchXtermSettleAlive(reason, generation, 1600);
        keepPassiveSwitchXtermSettleAlive(reason, generation, PASSIVE_SWITCH_XTERM_SETTLE_LAST_DELAY_MS);
    }

    private void keepToolbarOnlyXtermSettleAlive(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = ++passiveSwitchXtermSettleGeneration;
        // WHY: the 2026-06-18 22:10 phone screenshot proved the same lower
        // dotted xterm blank field can appear in the plain toolbar-only state,
        // not only after Active/Old switching or while the composer is open.
        // Bottom-core entry/live-bottom already owns tmux copy-mode exit and
        // phone-size resize; keep the proven row/canvas scrubber alive for that
        // toolbar-only repaint window too. This must stay passive: no WebView
        // reload, no hidden xterm focus, no IME reopen, and no broad black
        // lower shield.
        webView.evaluateJavascript(xtermCanvasSettleScript(reason + "-toolbar-only-entry-dot-scrub", true), null);
        keepToolbarOnlyXtermSettleAlive(reason, generation, 120);
        keepToolbarOnlyXtermSettleAlive(reason, generation, 360);
        keepToolbarOnlyXtermSettleAlive(reason, generation, 900);
        keepToolbarOnlyXtermSettleAlive(reason, generation, 1600);
        keepToolbarOnlyXtermSettleAlive(reason, generation, PASSIVE_SWITCH_XTERM_SETTLE_LAST_DELAY_MS);
    }

    private void keepToolbarOnlyXtermSettleAliveAfterControlAction(String reason) {
        // WHY: v2.49 covers the root gap exposed after the 2026-06-18 22:41
        // screenshot: normal toolbar-only actions such as Send/Enter, option keys,
        // and touch-bottom recovery can refit or align xterm after tmux is already
        // at live bottom without passing through Bottom-core. Without re-arming the
        // existing bounded row/canvas scrubber, Android can repaint the lower
        // viewport as dotted filler even though tmux text and phone size are right.
        // Keep this as a reuse of the proven helper so it still refuses to run in
        // read mode, while the composer is visible, during gestures, or while a
        // zoom/pan is active; do not add WebView reloads, hidden xterm focus, IME
        // reopen, or a broad black lower mask.
        keepToolbarOnlyXtermSettleAlive(reason + "-toolbar-action");
    }

    private void keepToolbarOnlyXtermSettleAlive(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (webView == null
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                    || generation != passiveSwitchXtermSettleGeneration
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || isDockedPromptComposerVisible()
                    || isTerminalGestureRecoveryActive()
                    || isViewerPanAllowed()) {
                return;
            }
            webView.evaluateJavascript(xtermCanvasSettleScript(reason + "-toolbar-only-entry-dot-scrub", true), null);
        }, Math.max(0, delayMs));
    }

    private void keepPassiveSwitchXtermSettleAlive(String reason, long generation, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (webView == null
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                    || generation != passiveSwitchXtermSettleGeneration
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || isTerminalGestureRecoveryActive()
                    || isViewerPanAllowed()) {
                return;
            }
            webView.evaluateJavascript(xtermCanvasSettleScript(reason + "-passive-switch-guard", true), null);
        }, Math.max(0, delayMs));
    }

    private void normalizeXtermCanvasAfterSessionSwitch(String reason, long generation, long maskGeneration, long delayMs) {
        uiHandler.postDelayed(() -> {
            if (webView == null
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                    || generation != sessionSwitchLiveViewportGeneration
                    || maskGeneration != blankTailMaskGeneration
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || isTerminalGestureRecoveryActive()
                    || isViewerPanAllowed()) {
                return;
            }
            // WHY: This is not the generic `keepLiveInputVisibleSoon` path. That
            // older helper also did `scrollIntoView`, focus probing, and retry
            // bursts that made tapping/type recovery look like repeated page
            // refreshes. The `Phone Crash Restore` tab still needs an
            // Active-switch-only xterm settle, but v1.85 showed that forcing CSS
            // backgrounds onto every canvas/theme layer can hide real text when
            // Android repaints. Keep this to document-scroll pinning, xterm
            // live-bottom, texture refresh, and row repaint only. Never focus the
            // hidden textarea, never show IME, never reload WebView, and never
            // move a zoomed/two-finger WebView viewport. v1.90 added a transparent
            // canvas-layer clear before xterm's own refresh because tmux capture
            // proved the dotted field is not real pane text. v1.91 adds an
            // Active-switch-only blank-tail scrub below xterm's current cursor row:
            // it blacks out only rows that should be empty at live bottom, not the
            // whole canvas/theme, so it cannot recreate the black-with-cursor
            // regression while removing stale dot pixels in blank cells. v1.94
            // switches the pre-redraw canvas clear from transparent clearRect to
            // source-over black canvas fill because real-phone v1.93 proof showed
            // transparent cells could still expose stale compositor dot pixels.
            // WHY: the v1.99 DOM blank-tail mask is a short live-bottom transition
            // cover. Typing, tap-to-compose, or one-finger scroll must cancel queued
            // mask installs, or a delayed settle callback can repaint the mask as a
            // large lower-half blackout after the user has moved on from passive settle.
            // v2.02 keeps an independent tail-only dotted-row scrubber alive longer
            // than the black mask because Android/xterm can repaint dotted filler
            // rows after the mask expires. It hides only repeated blank-tail dots and
            // is removed with the same typing/read/touch cancellation boundary.
            // v2.04 also treats repeated separator-only rows as blank-tail filler:
            // tmux/Codex horizontal rules render as dotted rows on Android, and
            // leaving them visible made Active/Old opens look stuck in a dot grid.
            // v2.08 makes that scrubber buffer/cursor-aware: legitimate CLI output
            // can contain dot-only progress rows or separator rows, so a DOM row is
            // hidden only when xterm's backing buffer line is blank/whitespace or,
            // if buffer text is unavailable, the row is below the live cursor.
            // APK-DOTS-BLACK-BOTTOM keeps that exact xterm buffer line truth in the
            // lower-tail fallback too: real dot/separator CLI content remains visible
            // and only proven blank-tail rows may be hidden or covered.
            webView.evaluateJavascript(xtermCanvasSettleScript(reason, true), null);
        }, Math.max(0, delayMs));
    }

    private String xtermCanvasSettleScript(String reason, boolean forceLiveBottom) {
        String safeReason = sanitizeJavascriptReason(reason);
        String liveBottom = forceLiveBottom
                ? "if(t&&typeof t.scrollToBottom==='function'){t.scrollToBottom();}"
                    + "var viewport=document.querySelector('.xterm-viewport');"
                    + "if(viewport){viewport.scrollTop=viewport.scrollHeight;}"
                : "";
        return "(function(){"
                + "try{"
                + "var scrolling=document.scrollingElement;"
                + "if(scrolling){scrolling.scrollTop=0;scrolling.scrollLeft=0;}"
                + "var html=document.documentElement,body=document.body;"
                + "if(html){html.scrollTop=0;html.scrollLeft=0;}"
                + "if(body){body.scrollTop=0;body.scrollLeft=0;}"
                + "window.scrollTo(0,0);"
                + "var t=window.term||window.terminal;"
                + "function forceXtermCustomGlyphsFalse(){"
                + "try{"
                + "if(!t){return;}"
                + "if(t.options){t.options.customGlyphs=false;}"
                + "if(typeof t.setOption==='function'){t.setOption('customGlyphs',false);}"
                + "/* WHY: v2.39 forces xterm's live runtime option because the ttyd URL/client option can still reach the renderer as a non-boolean value. The xterm bundle checks customGlyphs with strict false; if it is the string 'false', blank cells can still draw as dots. Set the terminal option before every passive fit/settle redraw so Active/Old switches cannot regress to the sparse lower dotted field without relying on a WebView reload or broad black mask. */"
                + "}catch(e){}"
                + "}"
                + "forceXtermCustomGlyphsFalse();"
                + "function fitXtermToPhoneViewport(){"
                + "try{"
                + "if(t&&typeof t.fit==='function'){t.fit();}"
                + "/* WHY: v2.40 fixes the remaining broad Active-switch proof failure where the selected tmux pane row count stayed at the old 38-row desktop height while the phone WebView had many more xterm rows. Those extra rows rendered as lower dotted filler even though tmux capture had no dots. ttyd exposes the fit addon as window.term.fit(); call it during passive session-switch settle so the pty/tmux row count matches the visible phone viewport without reloading WebView, focusing the hidden textarea, opening IME, or restoring a black lower shield. */"
                + "}catch(e){}"
                + "}"
                + "fitXtermToPhoneViewport();"
                + "function clearXtermCanvasLayers(){"
                + "var canvases=document.querySelectorAll('.xterm-screen canvas,.xterm canvas');"
                + "for(var i=0;i<canvases.length;i++){"
                + "var c=canvases[i];"
                + "try{var ctx=c.getContext&&c.getContext('2d');"
                + "if(ctx){var w=c.width||c.clientWidth||0,h=c.height||c.clientHeight||0;"
                + "if(w&&h){ctx.save();ctx.globalCompositeOperation='source-over';ctx.fillStyle='#000000';ctx.fillRect(0,0,w,h);ctx.restore();}}}catch(e){}"
                + "}"
                + "try{var r=t&&t._core&&t._core._renderService;"
                + "if(r&&typeof r.clear==='function'){r.clear();}}catch(e){}"
                + "}"
                + "function scrubCanvasDotRows(){"
                + "try{"
                + "var canvases=document.querySelectorAll('.xterm-screen canvas,.xterm canvas');"
                + "for(var ci=0;ci<canvases.length;ci++){"
                + "var c=canvases[ci];"
                + "var ctx=c.getContext&&c.getContext('2d',{willReadFrequently:true});"
                + "if(!ctx||!c.width||!c.height){continue;}"
                + "var start=Math.floor(c.height*0.34);"
                + "var width=c.width,height=c.height-start;"
                + "if(width<80||height<20){continue;}"
                + "var data=ctx.getImageData(0,start,width,height).data;"
                + "var threshold=Math.max(48,Math.floor(width/24));"
                + "ctx.save();"
                + "ctx.fillStyle='rgb(26,29,36)';"
                + "for(var y=0;y<height;y++){"
                + "var hits=0;"
                + "var firstHit=-1,lastHit=-1;"
                + "for(var x=0;x<width;x+=3){"
                + "var p=(y*width+x)*4;"
                + "var r=data[p],g=data[p+1],b=data[p+2];"
                + "if(r>95&&g>95&&b>95&&Math.max(r,g,b)-Math.min(r,g,b)<90){hits++;if(firstHit<0){firstHit=x;}lastHit=x;}"
                + "}"
                + "if(hits>=threshold&&firstHit>=0&&(lastHit-firstHit)>width*0.65){"
                + "/* WHY: v2.38 lowers the v2.35 repeated bright dot rows in the lower terminal canvas threshold to match the real proof detector's sparse full-width dot rows. The 2026-06-18 Active-title screenshot had about the detector-level row density, so width/9 missed it and left the lower terminal as dots. Keep the span gate so normal text/progress rows are not wiped; paint only detected full-width one-pixel filler rows, never install a full lower black rectangle, never reload WebView, and never touch the native composer. */"
                + "ctx.fillRect(0,Math.max(0,start+y-1),width,3);"
                + "y+=2;"
                + "}"
                + "}"
                + "ctx.restore();"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function clearCanvasDotRowScrubber(){"
                + "try{"
                + "window.__weztermCanvasDotRowScrubExpiresAt=0;"
                + "if(window.__weztermCanvasDotRowScrubTimer){clearInterval(window.__weztermCanvasDotRowScrubTimer);window.__weztermCanvasDotRowScrubTimer=null;}"
                + "}catch(e){}"
                + "}"
                + "function installCanvasDotRowScrubber(){"
                + "try{"
                + "window.__weztermCanvasDotRowScrubExpiresAt=Date.now()+" + PASSIVE_SWITCH_XTERM_SETTLE_LAST_DELAY_MS + ";"
                + "function scrub(){try{if(!window.__weztermCanvasDotRowScrubExpiresAt||Date.now()>window.__weztermCanvasDotRowScrubExpiresAt){clearCanvasDotRowScrubber();return;}scrubCanvasDotRows();}catch(e){}}"
                + "scrub();"
                + "if(window.__weztermCanvasDotRowScrubTimer){clearInterval(window.__weztermCanvasDotRowScrubTimer);}"
                + "window.__weztermCanvasDotRowScrubTimer=setInterval(scrub,80);"
                + "/* WHY: v2.36 keeps the v2.35 canvas-dot fix alive across Android's late xterm repaint. The timer is bounded to the passive switch window and removes only detected bright dot rows; it is not a lower shield, PopupWindow, WebView reload, or keyboard/focus action. */"
                + "}catch(e){}"
                + "}"
                + "function isDotOnlyText(text){"
                + "var raw=String(text||'');"
                + "if(!raw.length){return false;}"
                + "var s=raw.replace(/[\\s\\u00a0\\u2000-\\u200d\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]/g,'');"
                + "s=s.split('.').join('').split('·').join('').split('∙').join('').split('⋅').join('').split('•').join('').split('˙').join('');"
                + "s=s.split('─').join('').split('━').join('').split('═').join('').split('╌').join('').split('┄').join('').split('┈').join('').split('╴').join('').split('╶').join('').split('╼').join('').split('╾').join('');"
                + "/* WHY: v2.20 strips Braille/dot-block glyphs because the v2.19 real-phone screenshot showed Codex filler as U+2800-style dot cells, not ASCII periods. Without this classifier the row scrubber sees visible dots but isDotOnlyText returns false, so Active Sessions can regress to the exact dotted lower field again. */"
                + "var brailleStripped='';"
                + "for(var bi=0;bi<s.length;bi++){"
                + "var code=s.charCodeAt(bi);"
                + "if(code<0x2800||code>0x28ff){brailleStripped+=s.charAt(bi);}"
                + "}"
                + "s=brailleStripped;"
                + "return s.length===0;"
                + "}"
                + "function isBlankLikeText(text){"
                + "return String(text||'').replace(/[\\s\\u00a0\\u2000-\\u200d\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]/g,'').length===0;"
                + "}"
                + "function xtermBufferLineText(rowIndex){"
                + "try{"
                + "if(!t||!t.buffer||!t.buffer.active||typeof t.buffer.active.getLine!=='function'){return null;}"
                + "var viewportY=Number(t.buffer.active.viewportY)||0;"
                + "var line=t.buffer.active.getLine(viewportY+rowIndex);"
                + "if(!line||typeof line.translateToString!=='function'){return null;}"
                + "return line.translateToString(true);"
                + "}catch(e){return null;}"
                + "}"
                + "function isBlankBufferLine(rowIndex){"
                + "var text=xtermBufferLineText(rowIndex);"
                + "if(text===null){return false;}"
                + "return isBlankLikeText(text);"
                + "}"
                + "function isScrubbableBlankTailRow(rowIndex){"
                + "var text=xtermBufferLineText(rowIndex);"
                + "if(text!==null){return isBlankLikeText(text);}"
                + "return isPastLiveCursorRow(rowIndex);"
                + "}"
                + "function rowHasMeaningfulTerminalContent(rowIndex,rawText){"
                + "var text=xtermBufferLineText(rowIndex);"
                + "if(text!==null){"
                + "/* WHY: the v2.14 real-phone Active switch proof showed dot-only Codex filler can be present as real xterm buffer text. During this bounded settle script, dot-only rows are visual filler, not meaningful terminal content; otherwise the mask top follows the dots to the bottom and the user sees the same dotted field again. Normal non-dot buffer text still stays meaningful. */"
                + "return !isBlankLikeText(text)&&!isDotOnlyText(text);"
                + "}"
                + "var dom=String(rawText||'').replace(/\\u00a0/g,' ').trim();"
                + "return !!(dom&&!isDotOnlyText(rawText));"
                + "}"
                + "function hasReadableDomText(rawText){"
                + "var dom=String(rawText||'').replace(/\\u00a0/g,' ').trim();"
                + "if(!dom||isDotOnlyText(dom)){return false;}"
                + "return /[A-Za-z0-9_@#:$\\/\\\\-]/.test(dom);"
                + "}"
                + "function isPastLiveCursorRow(rowIndex){"
                + "try{"
                + "if(!t||!t.buffer||!t.buffer.active){return false;}"
                + "var cursorY=Number(t.buffer.active.cursorY);"
                + "return !isNaN(cursorY)&&rowIndex>cursorY;"
                + "}catch(e){return false;}"
                + "}"
                + "function visualDotFillerTopCss(screen,screenRect,rowNodes){"
                + "try{"
                + "if(!screenRect||!screenRect.height||!rowNodes||!rowNodes.length){return null;}"
                + "var seenNonDot=false;"
                + "var filler=[];"
                + "for(var i=0;i<rowNodes.length;i++){"
                + "var row=rowNodes[i];"
                + "var rawText=row.textContent||'';"
                + "var rr=row.getBoundingClientRect&&row.getBoundingClientRect();"
                + "if(!rr){continue;}"
                + "var rowTop=rr.top-screenRect.top;"
                + "if(isDotOnlyText(rawText)&&seenNonDot&&rowTop>=screenRect.height*0.25){"
                + "filler.push(row);"
                + "}else if(String(rawText||'').replace(/\\u00a0/g,' ').trim()&&!isDotOnlyText(rawText)){"
                + "seenNonDot=true;"
                + "filler=[];"
                + "}"
                + "}"
                + "if(filler.length>=12){"
                + "var first=filler[0].getBoundingClientRect&&filler[0].getBoundingClientRect();"
                + "if(first){return Math.max(0,first.top-screenRect.top);}"
                + "}"
                + "}catch(e){}"
                + "return null;"
                + "}"
                + "function bufferDotFillerTopCss(screenRect){"
                + "try{"
                + "if(!t||!t.buffer||!t.buffer.active||typeof t.buffer.active.getLine!=='function'||typeof t.rows!=='number'||!screenRect||!screenRect.height){return null;}"
                + "var rows=Math.max(1,Number(t.rows)||1);"
                + "var viewportY=Number(t.buffer.active.viewportY)||0;"
                + "var lastMeaningful=-1;"
                + "var currentRun=[];"
                + "var bestRun=[];"
                + "for(var i=0;i<rows;i++){"
                + "var line=t.buffer.active.getLine(viewportY+i);"
                + "var text=line&&typeof line.translateToString==='function'?line.translateToString(true):'';"
                + "var dot=isDotOnlyText(text);"
                + "var blank=isBlankLikeText(text);"
                + "if(dot&&i>=Math.floor(rows*0.25)&&(lastMeaningful>=0||i>=Math.floor(rows*0.35))){"
                + "currentRun.push(i);"
                + "if(currentRun.length>bestRun.length){bestRun=currentRun.slice(0);}"
                + "}else if(!blank&&!dot){"
                + "lastMeaningful=i;"
                + "currentRun=[];"
                + "}else if(blank){"
                + "currentRun=[];"
                + "}"
                + "}"
                + "if(bestRun.length>=12){"
                + "/* WHY: v2.23 covers the real-phone case where the lower dotted field persists in xterm's backing buffer even when DOM row selectors or row visibility cleanup do not expose usable row nodes. This remains passive-switch bounded and lower-screen/run-length gated; it does not focus xterm, reload WebView, open the keyboard, or hide arbitrary dot output outside the protected settle window. */"
                + "return Math.max(0,Math.min(screenRect.height-1,bestRun[0]*(screenRect.height/rows)));"
                + "}"
                + "}catch(e){}"
                + "return null;"
                + "}"
                + "function shouldHideDotOnlyRow(row,rowIndex,topCss,screenRect){"
                + "if(!isDotOnlyText(row.textContent||'')){return false;}"
                + "if(!isScrubbableBlankTailRow(rowIndex)){return false;}"
                + "if(typeof topCss!=='number'){return false;}"
                + "var rr=row.getBoundingClientRect&&row.getBoundingClientRect();"
                + "return !!(rr&&rr.top-screenRect.top>=topCss-1);"
                + "}"
                + "function hideDotOnlyRows(){"
                + "try{"
                + "var screen=document.querySelector('.xterm-screen')||document.querySelector('.xterm');"
                + "if(!screen){return;}"
                + "var screenRect=screen.getBoundingClientRect&&screen.getBoundingClientRect();"
                + "if(!screenRect||!screenRect.height){return;}"
                + "var rowNodes=document.querySelectorAll('.xterm-rows>div,.xterm-rows>span');"
                + "var topCss=blankTailTopCss(screen);"
                + "var hideRows=[];"
                + "var tailDotRows=[];"
                + "var lowerBlankDotRows=[];"
                + "var visualFillerDotRows=[];"
                + "var hardBlankTailRows=[];"
                + "var sustainedLowerDotRows=[];"
                + "var currentLowerDotRun=[];"
                + "var lastMeaningfulRowIndex=-1;"
                + "var lastMeaningfulBottomCss=null;"
                + "var lastReadableBottomCss=null;"
                + "if(typeof topCss==='number'){"
                + "for(var i=0;i<rowNodes.length;i++){"
                + "var row=rowNodes[i];"
                + "if(shouldHideDotOnlyRow(row,i,topCss,screenRect)){hideRows.push(row);}"
                + "}"
                + "}"
                + "for(var k=0;k<rowNodes.length;k++){"
                + "var candidate=rowNodes[k];"
                + "var rawText=candidate.textContent||'';"
                + "var trimmed=rawText.replace(/\\u00a0/g,' ').trim();"
                + "var rr=candidate.getBoundingClientRect&&candidate.getBoundingClientRect();"
                + "var candidateIsDotOnly=isDotOnlyText(rawText);"
                + "var candidateHasReadableText=hasReadableDomText(rawText);"
                + "if(candidateHasReadableText&&rr){lastReadableBottomCss=Math.min(screenRect.height,Math.max(0,rr.bottom-screenRect.top));hardBlankTailRows=[];}"
                + "else if(lastReadableBottomCss!==null&&rr&&rr.top-screenRect.top>=Math.max(lastReadableBottomCss-1,screenRect.height*0.35)&&!candidateHasReadableText){hardBlankTailRows.push(candidate);}"
                + "if(candidateIsDotOnly&&rr&&rr.top-screenRect.top>=screenRect.height*0.25){"
                + "currentLowerDotRun.push(candidate);"
                + "if(currentLowerDotRun.length>sustainedLowerDotRows.length){sustainedLowerDotRows=currentLowerDotRun.slice(0);}"
                + "}else if(trimmed&&!candidateIsDotOnly){"
                + "currentLowerDotRun=[];"
                + "}"
                + "if(lastMeaningfulRowIndex>=0&&isDotOnlyText(rawText)&&rr&&lastMeaningfulBottomCss!==null){"
                + "var visualRowTop=rr.top-screenRect.top;"
                + "if(visualRowTop>=lastMeaningfulBottomCss-1&&visualRowTop>=screenRect.height*0.35){visualFillerDotRows.push(candidate);continue;}"
                + "}"
                + "if(rowHasMeaningfulTerminalContent(k,rawText)){"
                + "lastMeaningfulRowIndex=k;tailDotRows=[];"
                + "if(rr){lastMeaningfulBottomCss=Math.min(screenRect.height,Math.max(0,rr.bottom-screenRect.top));}"
                + "}else if(lastMeaningfulRowIndex>=0&&isDotOnlyText(rawText)&&isScrubbableBlankTailRow(k)&&rr&&lastMeaningfulBottomCss!==null){"
                + "var rowTop=rr.top-screenRect.top;"
                + "if(rowTop>=lastMeaningfulBottomCss-1&&rowTop>=screenRect.height*0.35){tailDotRows.push(candidate);}"
                + "}else if(lastMeaningfulRowIndex<0&&isDotOnlyText(rawText)&&isScrubbableBlankTailRow(k)&&rr){"
                + "var lowerRowTop=rr.top-screenRect.top;"
                + "if(lowerRowTop>=screenRect.height*0.18){lowerBlankDotRows.push(candidate);}"
                + "}"
                + "}"
                + "if(hideRows.length<3&&tailDotRows.length>=6){"
                + "/* WHY: the user repeatedly hit a lower-tail dotted field after Active/Bottom even though tmux text was normal. The buffer-aware path above protects legitimate CLI dot/progress rows, and this fallback now uses the same xterm buffer line truth before hiding anything. Real Android/xterm DOM can still paint a long repeated dot tail below the last real row. Hide only that tail fallback, never all dot rows, and only after the buffer-aware gate proves the rows are blank tail. */"
                + "hideRows=tailDotRows;"
                + "}else if(hideRows.length<3&&lastMeaningfulRowIndex<0&&lowerBlankDotRows.length>=12){"
                + "/* WHY: the 2026-06-17 Active Sessions regression showed a full lower-screen dotted field immediately after switching tabs. In that transition xterm can expose only blank-backed dotted filler rows before any meaningful DOM row is available, so the last-real-row tail fallback never starts. Hide lower-tail blank-backed dot rows even when no prompt row is visible, but still require the same xterm buffer/cursor blank-tail gate and never revive the old broad all-dot-row fallback. */"
                + "hideRows=lowerBlankDotRows;"
                + "}else if(hideRows.length<3&&visualFillerDotRows.length>=12){"
                + "/* WHY: real-phone v2.12 Active-session switching proved Codex/xterm can render a large lower-screen dot-only filler field as actual terminal glyphs, not blank buffer rows. The user's invariant is visual: changing Active Sessions must land on a readable live-bottom view, not a half-screen dot field. Hide only a sustained lower-screen dot-only filler run below the last meaningful row during the short passive switch scrubber window; do not hide arbitrary dot output, and do not focus xterm, reload WebView, or open the keyboard to clear it. */"
                + "hideRows=visualFillerDotRows;"
                + "}else if(hideRows.length<3&&sustainedLowerDotRows.length>=12){"
                + "/* WHY: v2.18 exists because v2.17 still showed the same Active Sessions dotted field when the dot rows were real xterm/Codex filler rather than blank buffer rows. The phone contract is visual readability, so a sustained lower-screen run of dot-only rows during passive tab-open is filler even if xterm's buffer reports glyphs. This fallback is lower-screen and run-length gated; typing/read/touch cancellation removes it so it cannot hide legitimate command output forever. */"
                + "hideRows=sustainedLowerDotRows;"
                + "}else if(hideRows.length<3&&hardBlankTailRows.length>=8){"
                + "/* WHY: v2.26 still failed the @0->@59 real-phone proof because the lower xterm tail repainted as dots after the immediate frame while the dot classifier did not select those rows. During passive Active-switch settle, rows below the last readable prompt/log line are blank-tail surface even when stale renderer glyphs make them look nonblank. Hide only that lower unreadable tail, restore it through the same typing/read/touch cleanup, and do not change title/session selection. */"
                + "hideRows=hardBlankTailRows;"
                + "}"
                + "for(var j=0;j<rowNodes.length;j++){"
                + "var current=rowNodes[j];"
                + "var shouldHide=hideRows.length>=3&&hideRows.indexOf(current)>=0;"
                + "if(shouldHide){"
                + "current.setAttribute('data-wezterm-dot-row-hidden','1');"
                + "current.style.setProperty('visibility','hidden','important');"
                + "current.style.setProperty('color','transparent','important');"
                + "current.style.setProperty('-webkit-text-fill-color','transparent','important');"
                + "current.style.setProperty('text-shadow','none','important');"
                + "current.style.setProperty('background','#000','important');"
                + "}else if(current.getAttribute('data-wezterm-dot-row-hidden')==='1'){"
                + "current.removeAttribute('data-wezterm-dot-row-hidden');"
                + "current.style.removeProperty('visibility');"
                + "current.style.removeProperty('color');"
                + "current.style.removeProperty('-webkit-text-fill-color');"
                + "current.style.removeProperty('text-shadow');"
                + "current.style.removeProperty('background');"
                + "}"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function restoreDotOnlyRows(){"
                + "try{"
                + "var rowNodes=document.querySelectorAll('[data-wezterm-dot-row-hidden=\"1\"]');"
                + "for(var i=0;i<rowNodes.length;i++){"
                + "var row=rowNodes[i];"
                + "row.removeAttribute('data-wezterm-dot-row-hidden');"
                + "row.style.removeProperty('visibility');"
                + "row.style.removeProperty('color');"
                + "row.style.removeProperty('-webkit-text-fill-color');"
                + "row.style.removeProperty('text-shadow');"
                + "row.style.removeProperty('background');"
                + "}"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function clearDotRowScrubber(revealRows){"
                + "try{"
                + "window.__weztermDotRowScrubExpiresAt=0;"
                + "if(window.__weztermDotRowScrubTimer){clearInterval(window.__weztermDotRowScrubTimer);window.__weztermDotRowScrubTimer=null;}"
                + "if(window.__weztermDotRowScrubObserver){try{window.__weztermDotRowScrubObserver.disconnect();}catch(e){}window.__weztermDotRowScrubObserver=null;}"
                + "if(revealRows){restoreDotOnlyRows();}"
                + "}catch(e){}"
                + "}"
                + "function installDotRowScrubber(){"
                + "try{"
                + "window.__weztermDotRowScrubExpiresAt=Date.now()+" + DOT_ROW_SCRUBBER_MAX_LIFETIME_MS + ";"
                + "function scrub(){try{if(!window.__weztermDotRowScrubExpiresAt||Date.now()>window.__weztermDotRowScrubExpiresAt){clearDotRowScrubber(true);return;}hideDotOnlyRows();}catch(e){}}"
                + "scrub();"
                + "if(window.__weztermDotRowScrubTimer){clearInterval(window.__weztermDotRowScrubTimer);}"
                + "window.__weztermDotRowScrubTimer=setInterval(scrub,180);"
                + "if(window.__weztermDotRowScrubObserver){try{window.__weztermDotRowScrubObserver.disconnect();}catch(e){}}"
                + "var rowsNode=document.querySelector('.xterm-rows');"
                + "if(typeof MutationObserver!=='undefined'&&rowsNode){"
                + "window.__weztermDotRowScrubObserver=new MutationObserver(scrub);"
                + "window.__weztermDotRowScrubObserver.observe(rowsNode,{childList:true,subtree:true,characterData:true});"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function blankTailTopCss(screen){"
                + "try{"
                + "var rect=screen&&screen.getBoundingClientRect&&screen.getBoundingClientRect();"
                + "if(!rect||!rect.height){return null;}"
                + "var rowNodes=document.querySelectorAll('.xterm-rows>div,.xterm-rows>span');"
                + "var visualTop=visualDotFillerTopCss(screen,rect,rowNodes);"
                + "if(typeof visualTop==='number'){"
                + "/* WHY: v2.13 real-phone proof showed the dotted field can be actual Codex/xterm glyph rows, so the buffer-aware blank-tail cursor gate treats them as meaningful and leaves the mask at the bottom. For the short Active/Bottom settle only, sustained lower-screen dot-only glyph filler should be visually masked from the first filler row; the dot-row scrubber remains bounded and is removed on typing/read-mode so normal dot output is not permanently hidden. */"
                + "return visualTop;"
                + "}"
                + "var bufferTop=bufferDotFillerTopCss(rect);"
                + "if(typeof bufferTop==='number'){return bufferTop;}"
                + "var lastBottom=null;"
                + "for(var i=0;i<rowNodes.length;i++){"
                + "var rawText=rowNodes[i].textContent||'';"
                + "if(rowHasMeaningfulTerminalContent(i,rawText)){"
                + "var rr=rowNodes[i].getBoundingClientRect&&rowNodes[i].getBoundingClientRect();"
                + "if(rr){lastBottom=Math.min(rect.height,Math.max(0,rr.bottom-rect.top));}"
                + "}"
                + "}"
                + "if(lastBottom!==null&&lastBottom<rect.height-1){return lastBottom;}"
                + "}catch(e){}"
                + "try{"
                + "if(!t||!t.buffer||!t.buffer.active||typeof t.rows!=='number'){return null;}"
                + "var cursorY=t.buffer.active.cursorY;"
                + "if(typeof cursorY!=='number'){return null;}"
                + "var rows=Math.max(1,t.rows);"
                + "var fallbackRect=screen&&screen.getBoundingClientRect&&screen.getBoundingClientRect();"
                + "if(!fallbackRect||!fallbackRect.height){return null;}"
                + "return Math.min(fallbackRect.height,Math.max(0,(cursorY+1)*(fallbackRect.height/rows)));"
                + "}catch(e){}"
                + "return null;"
                + "}"
                + "function scrubBlankTail(){"
                + "try{"
                + "var screen=document.querySelector('.xterm-screen')||document.querySelector('.xterm');"
                + "if(!screen){return;}"
                + "var rect=screen.getBoundingClientRect();"
                + "if(!rect||!rect.height){return;}"
                + "var topCss=blankTailTopCss(screen);"
                + "if(typeof topCss!=='number'){return;}"
                + "var canvases=document.querySelectorAll('.xterm-screen canvas,.xterm canvas');"
                + "for(var i=0;i<canvases.length;i++){"
                + "var c=canvases[i];"
                + "try{var ctx=c.getContext&&c.getContext('2d');"
                + "var cr=c.getBoundingClientRect&&c.getBoundingClientRect();"
                + "if(ctx&&cr&&cr.height){"
                + "var scaleY=(c.height||cr.height)/cr.height;"
                + "var y=Math.floor(topCss*scaleY);"
                + "if(y<c.height){ctx.save();ctx.globalCompositeOperation='source-over';ctx.fillStyle='#000000';ctx.fillRect(0,y,c.width,c.height-y);ctx.restore();}"
                + "}"
                + "}catch(e){}"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function clearBlankTailMask(){"
                + "try{"
                + "window.__weztermBlankTailMaskExpiresAt=0;"
                + "if(window.__weztermBlankTailMaskTimer){clearInterval(window.__weztermBlankTailMaskTimer);window.__weztermBlankTailMaskTimer=null;}"
                + "if(window.__weztermBlankTailMaskObserver){try{window.__weztermBlankTailMaskObserver.disconnect();}catch(e){}window.__weztermBlankTailMaskObserver=null;}"
                + "var existing=document.getElementById('wezterm-blank-tail-mask');"
                + "if(existing&&existing.parentNode){existing.parentNode.removeChild(existing);}"
                + "}catch(e){}"
                + "}"
                + "function installBlankTailMask(){"
                + "try{"
                + "if(!t||!t.buffer||!t.buffer.active||typeof t.rows!=='number'){return;}"
                + "var screen=document.querySelector('.xterm-screen')||document.querySelector('.xterm');"
                + "if(!screen){return;}"
                + "window.__weztermBlankTailMaskExpiresAt=Date.now()+" + BLANK_TAIL_MASK_MAX_LIFETIME_MS + ";"
                + "if(getComputedStyle(screen).position==='static'){screen.style.position='relative';}"
                + "var mask=document.getElementById('wezterm-blank-tail-mask');"
                + "if(!mask){"
                + "mask=document.createElement('div');"
                + "mask.id='wezterm-blank-tail-mask';"
                + "mask.setAttribute('aria-hidden','true');"
                + "mask.style.pointerEvents='none';"
                + "mask.style.position='absolute';"
                + "mask.style.left='0';"
                + "mask.style.right='0';"
                + "mask.style.bottom='0';"
                + "mask.style.background='#000';"
                + "mask.style.zIndex='2147483647';"
                + "mask.style.transform='translateZ(0)';"
                + "screen.appendChild(mask);"
                + "}"
                + "function update(){"
                + "try{"
                + "if(!window.__weztermBlankTailMaskExpiresAt||Date.now()>window.__weztermBlankTailMaskExpiresAt){clearBlankTailMask();return;}"
                + "hideDotOnlyRows();"
                + "var rect=screen.getBoundingClientRect();"
                + "if(!rect||!rect.height){mask.style.display='none';return;}"
                + "var top=blankTailTopCss(screen);"
                + "if(typeof top!=='number'){mask.style.display='none';return;}"
                + "if(top>=rect.height-1){mask.style.display='none';}"
                + "else{mask.style.display='block';mask.style.top=top+'px';}"
                + "}catch(e){}"
                + "}"
                + "update();"
                + "if(window.__weztermBlankTailMaskTimer){clearInterval(window.__weztermBlankTailMaskTimer);}"
                + "window.__weztermBlankTailMaskTimer=setInterval(update,250);"
                + "if(window.__weztermBlankTailMaskObserver){try{window.__weztermBlankTailMaskObserver.disconnect();}catch(e){}}"
                + "var rowsNode=document.querySelector('.xterm-rows');"
                + "if(typeof MutationObserver!=='undefined'&&rowsNode){"
                + "window.__weztermBlankTailMaskObserver=new MutationObserver(update);"
                + "window.__weztermBlankTailMaskObserver.observe(rowsNode,{childList:true,subtree:true,characterData:true});"
                + "}"
                + "}catch(e){}"
                + "}"
                + "function clearImmediateDotFillerShield(){"
                + "try{"
                + "window.__weztermImmediateDotFillerShieldExpiresAt=0;"
                + "if(window.__weztermImmediateDotFillerShieldTimer){clearTimeout(window.__weztermImmediateDotFillerShieldTimer);window.__weztermImmediateDotFillerShieldTimer=null;}"
                + "var existing=document.getElementById('wezterm-immediate-dot-filler-shield');"
                + "if(existing&&existing.parentNode){existing.parentNode.removeChild(existing);}"
                + "}catch(e){}"
                + "}"
                + "function installImmediateDotFillerShield(){"
                + "try{"
                + "var screen=document.querySelector('.xterm-screen')||document.querySelector('.xterm');"
                + "if(!screen){return;}"
                + "var rect=screen.getBoundingClientRect&&screen.getBoundingClientRect();"
                + "if(!rect||!rect.height){return;}"
                + "var rowNodes=document.querySelectorAll('.xterm-rows>div,.xterm-rows>span');"
                + "var top=visualDotFillerTopCss(screen,rect,rowNodes);"
                + "if(typeof top!=='number'){top=bufferDotFillerTopCss(rect);}"
                + "if(typeof top!=='number'){top=rect.height*0.30;}"
                + "var mask=document.getElementById('wezterm-immediate-dot-filler-shield');"
                + "if(!mask){"
                + "mask=document.createElement('div');"
                + "mask.id='wezterm-immediate-dot-filler-shield';"
                + "mask.setAttribute('aria-hidden','true');"
                + "mask.style.pointerEvents='none';"
                + "mask.style.position='fixed';"
                + "mask.style.background='#000';"
                + "mask.style.zIndex='2147483647';"
                + "mask.style.transform='translateZ(0)';"
                + "(document.body||document.documentElement).appendChild(mask);"
                + "}"
                + "/* WHY: v2.16 proved an overlay appended inside .xterm-screen can still sit below xterm's rendered row/canvas layer on the real phone. The immediate Active-switch proof needs the dotted filler hidden before xterm/DOM row cleanup catches up. Keep this fixed body-level shield inside the WebView, bounded to the lower terminal area and short-lived; typing/read-mode cleanup removes it so it cannot become the old black-bottom regression. */"
                + "mask.style.display='block';"
                + "var viewportWidth=window.innerWidth||document.documentElement.clientWidth||rect.right;"
                + "var viewportHeight=window.innerHeight||document.documentElement.clientHeight||rect.bottom;"
                + "mask.style.left=Math.max(0,rect.left)+'px';"
                + "mask.style.right=Math.max(0,viewportWidth-rect.right)+'px';"
                + "mask.style.top=Math.max(0,Math.min(viewportHeight-1,rect.top+top))+'px';"
                + "mask.style.bottom=Math.max(0,viewportHeight-rect.bottom)+'px';"
                + "window.__weztermImmediateDotFillerShieldExpiresAt=Date.now()+" + IMMEDIATE_DOT_FILLER_SHIELD_MAX_LIFETIME_MS + ";"
                + "if(window.__weztermImmediateDotFillerShieldTimer){clearTimeout(window.__weztermImmediateDotFillerShieldTimer);}"
                + "window.__weztermImmediateDotFillerShieldTimer=setTimeout(clearImmediateDotFillerShield," + IMMEDIATE_DOT_FILLER_SHIELD_MAX_LIFETIME_MS + ");"
                + "}catch(e){}"
                + "}"
                + "clearXtermCanvasLayers();"
                + "if(t&&typeof t.clearTextureAtlas==='function'){t.clearTextureAtlas();}"
                + liveBottom
                + "window.dispatchEvent(new Event('resize'));"
                + "fitXtermToPhoneViewport();"
                + "function redraw(){if(t&&typeof t.refresh==='function'&&typeof t.rows==='number'){t.refresh(0,Math.max(0,t.rows-1));}}"
                + "redraw();"
                + "installCanvasDotRowScrubber();"
                + "hideDotOnlyRows();"
                + "installDotRowScrubber();"
                + "clearBlankTailMask();"
                + "clearImmediateDotFillerShield();"
                + "if(typeof requestAnimationFrame==='function'){requestAnimationFrame(function(){clearXtermCanvasLayers();redraw();installCanvasDotRowScrubber();hideDotOnlyRows();installDotRowScrubber();clearBlankTailMask();clearImmediateDotFillerShield();requestAnimationFrame(function(){redraw();installCanvasDotRowScrubber();hideDotOnlyRows();installDotRowScrubber();clearBlankTailMask();clearImmediateDotFillerShield();});});}"
                + "else{setTimeout(function(){clearXtermCanvasLayers();redraw();installCanvasDotRowScrubber();hideDotOnlyRows();installDotRowScrubber();clearBlankTailMask();clearImmediateDotFillerShield();},50);}"
                + "return 'xterm-canvas-settle:" + safeReason + "';"
                + "}catch(e){return 'err:'+String(e);}"
                + "})()";
    }

    private void removeXtermBlankTailMask(String reason) {
        boolean keepSessionSwitchLowerShield = shouldKeepSessionSwitchLowerShieldDuringMaskCleanup(reason);
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!keepSessionSwitchLowerShield) {
                forceHideSessionSwitchLowerPaintShield(reason);
            }
            return;
        }
        if (!keepSessionSwitchLowerShield) {
            forceHideSessionSwitchLowerPaintShield(reason);
        }
        // WHY: the live-bottom blank-tail mask exists only to cover stale xterm
        // dotted cells below the live cursor. Read/history mode must show the
        // real scrollback surface, so remove the mask before tmux copy-mode or
        // local history owns the view. v2.33 also removes passive-session-switch
        // lower shields here; keeping them alive made a black covered lower
        // terminal pass the old dotted-field proof even though it was not a real
        // live-bottom render.
        String keepSwitchShield = keepSessionSwitchLowerShield ? "true" : "false";
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var keepSwitchShield=" + keepSwitchShield + ";"
                        + "window.__weztermBlankTailMaskExpiresAt=0;"
                        + "if(window.__weztermBlankTailMaskTimer){clearInterval(window.__weztermBlankTailMaskTimer);window.__weztermBlankTailMaskTimer=null;}"
                        + "if(window.__weztermBlankTailMaskObserver){try{window.__weztermBlankTailMaskObserver.disconnect();}catch(e){}window.__weztermBlankTailMaskObserver=null;}"
                        + "if(!keepSwitchShield){"
                        + "window.__weztermDotRowScrubExpiresAt=0;"
                        + "if(window.__weztermDotRowScrubTimer){clearInterval(window.__weztermDotRowScrubTimer);window.__weztermDotRowScrubTimer=null;}"
                        + "if(window.__weztermDotRowScrubObserver){try{window.__weztermDotRowScrubObserver.disconnect();}catch(e){}window.__weztermDotRowScrubObserver=null;}"
                        + "window.__weztermImmediateDotFillerShieldExpiresAt=0;"
                        + "if(window.__weztermImmediateDotFillerShieldTimer){clearTimeout(window.__weztermImmediateDotFillerShieldTimer);window.__weztermImmediateDotFillerShieldTimer=null;}"
                        + "window.__weztermActiveSwitchLowerDotShieldExpiresAt=0;"
                        + "if(window.__weztermActiveSwitchLowerDotShieldTimer){clearInterval(window.__weztermActiveSwitchLowerDotShieldTimer);window.__weztermActiveSwitchLowerDotShieldTimer=null;}"
                        + "window.__weztermCanvasDotRowScrubExpiresAt=0;"
                        + "if(window.__weztermCanvasDotRowScrubTimer){clearInterval(window.__weztermCanvasDotRowScrubTimer);window.__weztermCanvasDotRowScrubTimer=null;}"
                        + "var hiddenRows=document.querySelectorAll('[data-wezterm-dot-row-hidden=\"1\"]');"
                        + "for(var i=0;i<hiddenRows.length;i++){var row=hiddenRows[i];row.removeAttribute('data-wezterm-dot-row-hidden');row.style.removeProperty('visibility');row.style.removeProperty('color');row.style.removeProperty('-webkit-text-fill-color');row.style.removeProperty('text-shadow');row.style.removeProperty('background');}"
                        + "var activeShield=document.getElementById('wezterm-active-switch-lower-dot-shield');"
                        + "if(activeShield&&activeShield.parentNode){activeShield.parentNode.removeChild(activeShield);}"
                        + "var dotShield=document.getElementById('wezterm-immediate-dot-filler-shield');"
                        + "if(dotShield&&dotShield.parentNode){dotShield.parentNode.removeChild(dotShield);}"
                        + "}"
                        + "var mask=document.getElementById('wezterm-blank-tail-mask');"
                        + "if(mask&&mask.parentNode){mask.parentNode.removeChild(mask);}"
                        + "return 'blank-tail-mask-removed:" + sanitizeJavascriptReason(reason) + "';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
    }

    private boolean shouldKeepSessionSwitchLowerShieldDuringMaskCleanup(String reason) {
        // WHY: v2.31 intentionally kept lower shields alive during passive
        // session switching so the proof would not show dots. The 2026-06-17
        // black-lower screenshot proved that was a false-green: a covered lower
        // terminal is not a live-bottom render. Cleanup must remove every broad
        // lower shield regardless of reason; only row-level dot scrubber state may
        // survive bounded passive settle.
        return false;
    }

    private void cancelXtermBlankTailMask(String reason) {
        // WHY: `removeXtermBlankTailMask` clears the DOM layer that already exists,
        // while this generation also cancels delayed live-bottom settle callbacks
        // that have not run yet. Without that second guard, typing, tap-to-compose,
        // or one-finger scroll can remove the mask and then see it reinstalled a
        // few frames later as a large black lower-half overlay.
        blankTailMaskGeneration++;
        passiveSwitchXtermSettleGeneration++;
        removeXtermBlankTailMask(reason);
    }

    private String sanitizeJavascriptReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.replace("\\", "/").replace("'", "").replace("\"", "");
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
            refreshCaptureRendererSoon("control-" + path);
        });
    }

    private void refreshCaptureRendererSoon(String reason) {
        refreshCaptureRenderer(reason, true);
    }

    private void refreshCaptureRendererNow(String reason) {
        refreshCaptureRenderer(reason, false);
    }

    private void refreshCaptureRendererForLayoutChange(String reason) {
        refreshCaptureRendererSoon(reason);
        // WHY: the read-only capture renderer computes rows inside the WebView.
        // Android can deliver composer/IME layout and WebView resize in separate
        // frames, so one immediate refresh may still use the pre-keyboard height.
        // Bounded follow-ups make the real terminal prompt/cursor row repaint
        // above the native composer without reloading WebView, focusing xterm, or
        // resizing the shared tmux window.
        uiHandler.postDelayed(() -> refreshCaptureRendererNow(reason + "-layout-settle-1"), 260);
        uiHandler.postDelayed(() -> refreshCaptureRendererNow(reason + "-layout-settle-2"), 620);
    }

    private void refreshCaptureRenderer(String reason, boolean includeFollowUp) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        // WHY: APK scroll/buttons still mutate tmux state through the proven
        // `/scroll` and `/touch-scroll` endpoints. The v2.53 read-only renderer
        // regressed by polling slowly and looking frozen after those controls.
        // Refresh only the capture renderer object; never reload the WebView,
        // focus xterm, or resize tmux from this path.
        String followUp = includeFollowUp ? "setTimeout(run,180);" : "";
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var r=window.__mantisCaptureRenderer;"
                        + "if(r&&typeof r.refresh==='function'){"
                        + "var run=function(){r.refresh();};"
                        + "if(typeof requestAnimationFrame==='function'){requestAnimationFrame(run);}else{setTimeout(run,16);}"
                        + followUp
                        + "return 'capture-refresh';"
                        + "}"
                        + "return 'not-capture';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
    }

    private void focusTerminalInputSoon() {
        focusTerminalInputSoon(false);
    }

    private void focusTerminalInputSoon(boolean requestKeyboard) {
        if (webView == null) {
            return;
        }
        if (isDockedPromptComposerVisible()) {
            restoreDockedPromptComposerFocus("skip-terminal-focus");
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
        if (isDockedPromptComposerVisible()) {
            restoreDockedPromptComposerFocus("block-terminal-focus");
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
        if (keyZoomViewerStateActive || webViewScale > WEBVIEW_ZOOMED_SCALE_THRESHOLD) {
            return true;
        }
        if (webView == null) {
            return false;
        }
        // WHY: real Samsung/WebView pinch proof showed a visual zoom can outlive
        // the cached onScaleChanged value. If this gate goes false while the
        // cleanup path cannot treat a visibly zoomed viewer as unzoomed: while
        // the viewer is still actually zoomed, delayed document pins and xterm settle
        // scripts that contain scrollTo(0,0) can drag the viewport to the top
        // corner and strand a huge lower black field. Read the WebView's actual
        // scale as a guard only; do not convert pinch zoom into tmux resize,
        // terminal font changes, zoom reset, raw ttyd, or /touch-scroll routing.
        float actualScale = webView.getScale();
        if (actualScale > WEBVIEW_ZOOMED_SCALE_THRESHOLD) {
            webViewScale = Math.max(webViewScale, actualScale);
            return true;
        }
        return false;
    }

    private boolean handleViewerZoomKey(int keyCode, KeyEvent event) {
        if (webView == null
                || (keyCode != KeyEvent.KEYCODE_ZOOM_IN && keyCode != KeyEvent.KEYCODE_ZOOM_OUT)) {
            return false;
        }
        if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        // WHY: real closure for zoomed one-finger pan requires proof while the
        // Android/WebView viewer is actually zoomed.
        // Samsung/ADB key zoom did not change WebView scale by default, so keep hardware/automation zoom keys
        // wired directly to WebView zoom as an accessibility and proof setup path.
        // Do not translate this into tmux/font resize or touch-scroll behavior.
        boolean changed = keyCode == KeyEvent.KEYCODE_ZOOM_IN
                ? webView.zoomIn()
                : webView.zoomOut();
        if (changed) {
            if (keyCode == KeyEvent.KEYCODE_ZOOM_IN) {
                // WHY: some WebView builds visually zoom from zoomIn() without
                // promptly calling onScaleChanged. Keep that key-created zoom state
                // visible to isViewerPanAllowed() so cleanup/pinning code cannot
                // treat the next one-finger line-reading pan as an unzoomed page.
                keyZoomViewerStateActive = true;
                webViewScale = Math.max(webViewScale, WEBVIEW_ZOOMED_SCALE_THRESHOLD + 0.03f);
            }
            allowViewerPanBriefly();
            cancelViewerTypingPositionRetries("key-zoom");
            cancelLiveInputVisibilityRetries("key-zoom");
        }
        return true;
    }

    private void cancelLiveInputVisibilityRetries(String reason) {
        // WHY: live-bottom and input-visibility helpers enqueue delayed
        // scrollToBottom/scrollIntoView work. Once the user starts a native
        // WebView pinch or zoomed pan, those old callbacks belong to the previous
        // viewer position and can pull the zoomed page toward a corner. Use the
        // same generation that passive entry alignment already checks.
        liveInputVisibilityGeneration++;
    }

    private boolean isViewerPanAllowed() {
        return isViewerZoomed()
                || terminalMultiTouchGesture
                || terminalHorizontalPanActive
                || System.currentTimeMillis() < viewerPanUnlockedUntilMs;
    }

    private boolean isViewerGestureSettleActive() {
        // WHY: WebView can emit scale/scroll callbacks after ACTION_UP or after
        // onScaleChanged. During this bounded settle window the viewer still owns
        // position; passive terminal cleanup must not run scrollToBottom or
        // document-scroll pins that make pinch zoom jump toward a corner.
        return terminalMultiTouchGesture
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

    private void alignLiveBottomViewportForPassiveEntrySoon(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = ++liveInputVisibilityGeneration;
        alignLiveBottomViewportForPassiveEntry(reason, generation);
        uiHandler.postDelayed(() -> alignLiveBottomViewportForPassiveEntry(reason, generation), 140);
        uiHandler.postDelayed(() -> alignLiveBottomViewportForPassiveEntry(reason, generation), 420);
        uiHandler.postDelayed(() -> alignLiveBottomViewportForPassiveEntry(reason, generation), 900);
    }

    private void alignLiveBottomViewportForPassiveEntry(String reason, long generation) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        if (generation != liveInputVisibilityGeneration
                || readModeSuppressesKeyboard
                || terminalHistoryViewportActive
                || isTerminalGestureRecoveryActive()) {
            return;
        }
        // WHY: Active switching and first entry must look exactly like the user
        // pressed Bottom when the WebView is unzoomed. While zoomed or recently
        // panned, Android/WebView owns the viewer; the visibility script switches to
        // attribute-only mode so stale passive callbacks cannot drag xterm/document
        // layers to a corner and create the giant black bottom field. It still
        // never reloads ttyd, never focuses the hidden textarea, never opens the native composer,
        // and never shows the IME.
        webView.evaluateJavascript(liveInputVisibilityScript(isViewerPanAllowed()), null);
        if (isViewerPanAllowed()) {
            cancelViewerTypingPositionRetries(reason + "-passive-preserve-viewer");
        }
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
        scrollViewerToTypingPositionSoon(reason);
    }

    private String liveInputVisibilityScript(boolean preserveViewerPan) {
        String resetDocumentScroll = preserveViewerPan
                ? ""
                : "var scrolling=document.scrollingElement;"
                        + "if(scrolling){scrolling.scrollTop=0;scrolling.scrollLeft=0;}"
                        + "if(document.documentElement){document.documentElement.scrollTop=0;document.documentElement.scrollLeft=0;}"
                        + "if(document.body){document.body.scrollTop=0;document.body.scrollLeft=0;}"
                        + "window.scrollTo(0,0);";
        String terminalBottomScroll = preserveViewerPan
                ? ""
                : "var t=window.term||window.terminal;"
                        + "if(t&&typeof t.scrollToBottom==='function'){t.scrollToBottom();}"
                        + "var viewport=document.querySelector('.xterm-viewport');"
                        + "if(viewport){viewport.scrollTop=viewport.scrollHeight;}"
                        + "var screen=document.querySelector('.xterm-screen,.xterm');"
                        + "if(screen&&typeof screen.scrollIntoView==='function'){screen.scrollIntoView({block:'end',inline:'nearest'});}";
        return "(function(){"
                + "try{"
                + terminalBottomScroll
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
                + (preserveViewerPan ? "return 'visible-preserve-viewer';" : "return 'visible';")
                + "}catch(e){return 'err';}"
                + "})()";
    }

    private boolean shouldPreserveZoomedViewerForPassiveBottom(String reason) {
        if (!isViewerPanAllowed()) {
            return false;
        }
        String normalized = reason == null ? "" : reason.toLowerCase(Locale.US);
        // WHY: tab-open/send/passive confirmations already move tmux to live bottom
        // server-side. When Android WebView is zoomed, a blind viewer `scrollTo(maxY)`
        // can reveal the renderer's black lower field and look like the viewport
        // snapped away from the message. Preserve explicit Bottom/touch-bottom
        // recovery, but keep passive tab and send settles out of the zoom viewer.
        return normalized.contains("tab-open")
                || normalized.contains("passive-live-bottom")
                || normalized.contains("-confirm")
                || normalized.startsWith("select-live")
                || normalized.startsWith("new-session")
                || normalized.startsWith("old-session")
                || normalized.startsWith("crashed-session")
                || normalized.startsWith("workspace-restore")
                || normalized.startsWith("submit-text")
                || normalized.startsWith("send-enter")
                || normalized.startsWith("send-key");
    }

    private void fitTerminalToCurrentViewSoon(String reason) {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return;
        }
        long generation = ++terminalFitGeneration;
        fitTerminalToCurrentView(reason, generation);
        uiHandler.postDelayed(() -> fitTerminalToCurrentView(reason, generation), 140);
        uiHandler.postDelayed(() -> fitTerminalToCurrentView(reason, generation), 420);
        if (shouldRunLayoutDotScrubber(reason)) {
            keepLayoutXtermDotScrubberAlive(reason, generation);
        }
    }

    private boolean shouldRunLayoutDotScrubber(String reason) {
        return reason != null
                && ("webview-layout".equals(reason) || reason.startsWith("composer-"));
    }

    private void keepLayoutXtermDotScrubberAlive(String reason, long generation) {
        // WHY: composer/keyboard layout shrink can expose the full-view dotted
        // field even when tmux capture has no dot rows. Reuse the proven xterm
        // row/canvas scrubber during layout-only refits, but keep forceLiveBottom
        // false so tap-to-type does not scroll, focus xterm, reload WebView, open
        // the IME, or reintroduce the old black-lower-mask false-green.
        runLayoutXtermDotScrubber(reason, generation);
        uiHandler.postDelayed(() -> runLayoutXtermDotScrubber(reason, generation), 180);
        uiHandler.postDelayed(() -> runLayoutXtermDotScrubber(reason, generation), 520);
        uiHandler.postDelayed(() -> runLayoutXtermDotScrubber(reason, generation), 1100);
    }

    private void runLayoutXtermDotScrubber(String reason, long generation) {
        if (webView == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                || generation != terminalFitGeneration
                || readModeSuppressesKeyboard
                || terminalHistoryViewportActive
                || liveRestoreInFlight
                || terminalBottomRestoreInFlight
                || isTerminalGestureRecoveryActive()
                || isViewerPanAllowed()) {
            return;
        }
        webView.evaluateJavascript(xtermCanvasSettleScript(reason + "-layout-dot-scrub", false), null);
    }

    private void fitTerminalToCurrentView(String reason, long generation) {
        if (webView == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT
                || generation != terminalFitGeneration
                || readModeSuppressesKeyboard
                || terminalHistoryViewportActive
                || liveRestoreInFlight
                || terminalBottomRestoreInFlight) {
            return;
        }
        // WHY: ttyd's xterm instance is inside the WebView and already listens
        // for browser resize events. The Android shell is the layer that knows
        // when the native toolbar/composer/IME changes the available height, so
        // this script only normalizes the container height and emits resize.
        // v1.85 showed that painting every xterm canvas black from this passive
        // layout path can leave Android with a cursor-only terminal after a
        // reload/watchdog race. Background black belongs to ttyd's theme and the
        // native WebView color; this passive fit must not overwrite xterm canvas
        // layers, scrollToBottom, scrollIntoView, or focus because those were the
        // root of the rapid refresh loop and duplicate typing. v1.87 keeps only
        // xterm's own texture-atlas clear plus redraw here: the user's 16:36
        // screenshot proved stale atlas cells can show a repeated dotted grid
        // below real text after composer/keyboard layout changes, and clearing
        // the atlas fixes that class without hiding glyphs or reconnecting ttyd.
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var html=document.documentElement,body=document.body;"
                        + "if(html){html.style.height='100%';html.style.minHeight='0';html.style.overflow='hidden';}"
                        + "if(body){body.style.height='100%';body.style.minHeight='0';body.style.margin='0';body.style.overflow='hidden';}"
                        + "var nodes=document.querySelectorAll('#terminal,.terminal-container,.xterm,.xterm-screen,.xterm-viewport');"
                        + "for(var i=0;i<nodes.length;i++){nodes[i].style.minHeight='0';nodes[i].style.maxHeight='100%';}"
                        + "window.dispatchEvent(new Event('resize'));"
                        + "var t=window.term||window.terminal;"
                        + "try{"
                        + "if(t){"
                        + "if(t.options){t.options.customGlyphs=false;}"
                        + "if(typeof t.setOption==='function'){t.setOption('customGlyphs',false);}"
                        + "}"
                        + "}catch(e){}"
                        + "/* WHY: v2.39 repeats the xterm customGlyphs=false runtime guard in the passive fit path, not only in the Active-switch settle path. Layout/refit runs after WebView resize, composer hide, Refresh, and resume; if the live xterm option drifts back to a non-boolean value, blank cells can repaint as dotted glyph rows even though the URL says customGlyphs=false. */"
                        + "try{"
                        + "if(t&&typeof t.fit==='function'){t.fit();}"
                        + "}catch(e){}"
                        + "/* WHY: v2.40 uses ttyd's exposed xterm fit addon during passive layout fits. A plain window resize event did not always resize the selected tmux pane before proof screenshots, leaving old short-pane content with phone-height blank rows that repaint as dots. Fit sends the real xterm resize path without WebView reload, keyboard focus, or lower masking. */"
                        + "if(t&&typeof t.clearTextureAtlas==='function'){t.clearTextureAtlas();}"
                        + "function redraw(){if(t&&typeof t.refresh==='function'&&typeof t.rows==='number'){t.refresh(0,Math.max(0,t.rows-1));}}"
                        + "redraw();"
                        + "if(typeof requestAnimationFrame==='function'){requestAnimationFrame(redraw);}else{setTimeout(redraw,40);}"
                        + "return 'fit:" + reason + "';"
                        + "}catch(e){return 'err';}"
                + "})()",
                null
        );
    }

    private void scrollViewerToTypingPositionSoon(String reason) {
        // WHY: this is WebView-viewer positioning only. It is used after Android
        // has already decided to show the native composer or return a one-finger
        // down-scroll to live bottom. Retrying at paint-sized delays fixes the
        // zoomed "cannot reach the bottom" case without reviving xterm
        // scrollToBottom, document scroll reset, keyboard focus, or reload loops.
        long generation = ++viewerTypingPositionGeneration;
        scrollViewerToTypingPosition(reason, generation);
        uiHandler.postDelayed(() -> scrollViewerToTypingPosition(reason, generation), 140);
        uiHandler.postDelayed(() -> scrollViewerToTypingPosition(reason, generation), 420);
    }

    private void scrollViewerToTypingPositionOnce(String reason, long delayMs) {
        // WHY: Bottom/Send/tap are explicit user actions that must not look like a
        // page repeatedly sliding up and down. Keep a one-shot zoomed-viewer
        // correction for the "cannot reach true bottom" case, but do not enqueue
        // the older multi-frame retry train on tap-to-type or post-Send settle.
        long generation = ++viewerTypingPositionGeneration;
        uiHandler.postDelayed(() -> scrollViewerToTypingPosition(reason, generation), Math.max(0, delayMs));
    }

    private void scrollViewerToTypingPositionAfterBottom(String reason) {
        // WHY: Bottom/touch-bottom are explicit recovery actions. Earlier one-shot
        // alignment could fire while WebView content height was still settling or
        // while the Bottom HTTP callback was clearing gesture state, so zoomed users
        // saw "at bottom" with the final text still below the viewport. Keep this
        // viewer-only and bounded: it preserves horizontal pan, does not change
        // tmux/window size, and remains generation-cancellable by later pinch/pan.
        long generation = ++viewerTypingPositionGeneration;
        scrollViewerToTypingPosition(reason + "-bottom-align", generation, false);
        uiHandler.postDelayed(() -> scrollViewerToTypingPosition(reason + "-bottom-align", generation, false), 180);
        uiHandler.postDelayed(() -> scrollViewerToTypingPosition(reason + "-bottom-align", generation, false), 520);
    }

    private void cancelViewerTypingPositionRetries(String reason) {
        // WHY: delayed bottom-position retries from composer/live-bottom recovery
        // must not fire after the user starts a native WebView pan or pinch. Those
        // stale scrollTo(maxY) calls are perceived as two-finger movement jumping.
        viewerTypingPositionGeneration++;
    }

    private void scrollViewerToTypingPosition(String reason, long generation) {
        if (webView == null || !isViewerZoomed()) {
            return;
        }
        scrollViewerToTypingPosition(reason, generation, true);
    }

    private void scrollViewerToTypingPosition(String reason, long generation, boolean blockDuringGestureRecovery) {
        if (webView == null || !isViewerZoomed()) {
            return;
        }
        webView.postDelayed(() -> {
            if (webView == null
                    || !isViewerZoomed()
                    || generation != viewerTypingPositionGeneration
                    || readModeSuppressesKeyboard
                    || terminalHistoryViewportActive
                    || (blockDuringGestureRecovery && isTerminalGestureRecoveryActive())) {
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
                + "if(document.querySelector('[data-mantis-capture-renderer=\"1\"]')){return ({status:'capture-renderer',needsReconnect:false});}"
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
        // after ACTION_UP has cleared `terminalHistoryDragActive`. The same is
        // true for the bounded viewer-settle window after pinch/zoomed pan:
        // passive cleanup must not run document pins, scrollToBottom, reconnect
        // probes, or IME show calls while WebView still owns the viewpoint.
        return terminalHistoryDragActive
                || terminalBottomRestoreInFlight
                || isViewerGestureSettleActive();
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

    private void scheduleVisibleWebViewPaintWatchdog(String reason) {
        // WHY: v1.84/v1.85 tried to heal a foreground black WebView by sampling
        // the native WebView bitmap and automatically reloading ttyd on low paint.
        // Real Codex panes often have a mostly blank black live-bottom area; the
        // watchdog treated that valid state as failure, then repeated WebView
        // reloads until the user saw exactly the regression reported here: an all
        // black terminal with only a jumping cursor. Keep the method as a named
        // guardrail for future readers, but make bitmap sampling proof-only. The
        // visible Refresh button remains the explicit transport reload path.
        cancelVisibleWebViewPaintWatchdog("disabled-visible-paint-watchdog-" + reason);
    }

    private void cancelVisibleWebViewPaintWatchdog(String reason) {
        // WHY: delayed visible-paint checks carry only a generation. Cancel them
        // whenever the UI enters a state where dark pixels are expected, especially
        // Active-switch xterm canvas normalization. Otherwise a stale check from
        // resume/load can fire in the wrong state and reload a healthy terminal.
        visibleWebViewPaintGeneration++;
    }

    private void verifyVisibleWebViewPainted(String reason, long generation, long loadGeneration) {
        if (webView == null
                || generation != visibleWebViewPaintGeneration
                || loadGeneration != lastTerminalLoadAtMs
                || !activityResumed
                || readModeSuppressesKeyboard
                || terminalHistoryViewportActive
                || isTerminalGestureRecoveryActive()) {
            return;
        }
        VisiblePaintStats stats = sampleVisibleWebViewPaint();
        if (!stats.valid || !stats.lowPaint) {
            return;
        }
        // WHY: visible-bitmap sampling is now a proof harness concern, not a
        // runtime reload trigger. See scheduleVisibleWebViewPaintWatchdog().
        return;
    }

    private VisiblePaintStats sampleVisibleWebViewPaint() {
        if (webView == null || webView.getWidth() <= 0 || webView.getHeight() <= 0) {
            return new VisiblePaintStats(false, 0, 0, 0.0, false);
        }
        Bitmap bitmap = null;
        try {
            int width = webView.getWidth();
            int height = webView.getHeight();
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);
            int bright = 0;
            int samples = 0;
            for (int y = 0; y < height; y += VISIBLE_WEBVIEW_PAINT_SAMPLE_STEP_PX) {
                for (int x = 0; x < width; x += VISIBLE_WEBVIEW_PAINT_SAMPLE_STEP_PX) {
                    int pixel = bitmap.getPixel(x, y);
                    samples++;
                    if (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel) > 120) {
                        bright++;
                    }
                }
            }
            double ratio = samples <= 0 ? 0.0 : (double) bright / (double) samples;
            boolean lowPaint = bright < VISIBLE_WEBVIEW_MIN_BRIGHT_SAMPLES
                    || ratio < VISIBLE_WEBVIEW_MIN_BRIGHT_RATIO;
            return new VisiblePaintStats(true, bright, samples, ratio, lowPaint);
        } catch (Throwable ignored) {
            // WHY: fail open if Android cannot draw the WebView into a bitmap.
            // This watchdog must never become a new random-reload source; it only
            // handles the measured black foreground pane.
            return new VisiblePaintStats(false, 0, 0, 0.0, false);
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

    private void reloadTerminalForVisibleBlank(String reason, VisiblePaintStats stats) {
        if (webView == null || isTerminalGestureRecoveryActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBlankTerminalReloadAtMs < 12000) {
            return;
        }
        lastBlankTerminalReloadAtMs = now;
        long reloadGeneration = markTerminalLoadStarted();
        boolean composerVisible = isDockedPromptComposerVisible();
        toast("Repainting terminal");
        String currentUrl = webView.getUrl();
        webView.loadUrl(isKnownTerminalUrl(currentUrl)
                ? currentUrl
                : terminalUrlWithOptions(activeTerminalBaseUrl));
        if (composerVisible && promptComposerInput != null) {
            promptComposerInput.requestFocus();
        }
        pinTerminalViewportSoon("visible-blank-" + reason);
        // WHY: the composer-open black-WebView failure is recovered by reloading
        // only the WebView transport, not by moving typing back into xterm. If a
        // future edit blindly refocuses the hidden terminal textarea here, Samsung
        // can reopen the old duplicate-typing/IME path and the user's native draft
        // can appear to disappear. Keep focus on the native composer whenever it
        // is visible; passive terminal focus is only for toolbar-only states.
        if (!composerVisible && !keyboardVisibleForFocusedWebView()) {
            focusTerminalInputSoon(false);
        }
        scheduleBlankTerminalWatchdog("visible-blank-" + reason);
        uiHandler.postDelayed(
                () -> verifyTerminalPainted("visible-blank-" + reason, reloadGeneration),
                3200
        );
        scheduleVisibleWebViewPaintWatchdog("visible-blank-" + reason
                + "-" + stats.brightSamples + "-" + stats.sampleRatio);
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
        webView.evaluateJavascript(terminalPaintProbeScript(),
                value -> handleTerminalPaintProbe(value, reason, loadGeneration));
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
            boolean hasCapture = probe.optBoolean("hasCapture", false);
            boolean hasTerm = probe.optBoolean("hasTerm", false);
            boolean hasCanvas = probe.optBoolean("hasCanvas", false);
            int canvasWidth = probe.optInt("canvasWidth", 0);
            int canvasHeight = probe.optInt("canvasHeight", 0);
            boolean hasError = probe.has("error");
            if (hasCapture) {
                missingTerminal = false;
            } else {
            // WHY: ttyd is forced to xterm's canvas renderer for Android
            // performance. Canvas output can be visibly painted while DOM text is
            // empty, so `textLength == 0` must never trigger a reload by itself.
            // That false positive caused random "refresh" while reading output.
            // v1.86 also stops treating "low paint" as a runtime reload signal.
            // The v1.83-v1.85 low-paint checks were not phone-visible proven before
            // shipping and they repeatedly reloaded healthy mostly-black Codex
            // bottoms into the black/cursor-only state. Only a missing renderer or
            // zero-sized canvas can reload automatically; visible-paint checks live
            // in the proof scripts where a failure blocks the release instead of
            // disturbing the user's terminal.
            missingTerminal = hasError || (!hasTerm && !hasCanvas)
                    || (hasTerm && hasCanvas && (canvasWidth <= 0 || canvasHeight <= 0));
            }
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
        String currentUrl = webView.getUrl();
        webView.loadUrl(isKnownTerminalUrl(currentUrl)
                ? currentUrl
                : terminalUrlWithOptions(activeTerminalBaseUrl));
        uiHandler.postDelayed(() -> focusTerminalInputSoon(false), 900);
        uiHandler.postDelayed(() -> verifyTerminalPainted("blank-watchdog-" + reason, reloadGeneration), 3200);
    }

    private String terminalPaintProbeScript() {
        // WHY: ttyd's Android client uses xterm's canvas renderer. DOM text can be
        // empty even when terminal output is visible, so runtime recovery must only
        // check that the renderer exists and has nonzero canvas dimensions. The
        // v1.83-v1.85 paint-sampling watchdogs turned healthy mostly-black Codex
        // live-bottom states into repeated WebView reloads and the black/cursor
        // regression. Pixel-paint thresholds now belong to the real-phone proof
        // harness, where they fail the build instead of reloading the user's pane.
        return "(function(){"
                + "try{"
                + "var capture=document.querySelector('[data-mantis-capture-renderer=\"1\"]');"
                + "var term=document.querySelector('.xterm');"
                + "var rows=document.querySelector('.xterm-rows');"
                + "var text=(rows&&rows.innerText||document.body&&document.body.innerText||'').trim();"
                + "var canvas=document.querySelector('canvas');"
                + "return ({hasCapture:!!capture,hasTerm:!!term,hasCanvas:!!canvas,canvasWidth:canvas?canvas.width:0,canvasHeight:canvas?canvas.height:0,textLength:text.length});"
                + "}catch(e){return ({error:String(e)});}"
                + "})()";
    }

    private static class VisiblePaintStats {
        final boolean valid;
        final int brightSamples;
        final int totalSamples;
        final double sampleRatio;
        final boolean lowPaint;

        VisiblePaintStats(boolean valid, int brightSamples, int totalSamples, double sampleRatio, boolean lowPaint) {
            this.valid = valid;
            this.brightSamples = brightSamples;
            this.totalSamples = totalSamples;
            this.sampleRatio = sampleRatio;
            this.lowPaint = lowPaint;
        }
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

    private String controlBaseUrl(int index) {
        if (index < 0 || index >= CONTROL_URLS.length) {
            return CONTROL_URL;
        }
        return CONTROL_URLS[index];
    }

    private String controlUrlForPath(String path) {
        return activeControlBaseUrl + path;
    }

    private void getJsonAttempt(
            String path,
            JsonCallback callback,
            FailureCallback failureCallback,
            int attemptsRemaining
    ) {
        getJsonAttempt(path, callback, failureCallback, attemptsRemaining, 0);
    }

    private void getJsonAttempt(
            String path,
            JsonCallback callback,
            FailureCallback failureCallback,
            int attemptsRemaining,
            int controlUrlIndex
    ) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            String baseUrl = controlBaseUrl(controlUrlIndex);
            try {
                URL url = new URL(baseUrl + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String body = readAll(stream);
                JSONObject payload = new JSONObject(body);
                activeControlBaseUrl = baseUrl;
                uiHandler.post(() -> {
                    try {
                        callback.onResult(payload);
                    } catch (Exception exc) {
                        toast(exc.getMessage());
                    }
                });
            } catch (Exception exc) {
                if (controlUrlIndex + 1 < CONTROL_URLS.length) {
                    uiHandler.postDelayed(
                            () -> getJsonAttempt(
                                    path,
                                    callback,
                                    failureCallback,
                                    attemptsRemaining,
                                    controlUrlIndex + 1
                            ),
                            CONTROL_SAFE_RETRY_DELAY_MS
                    );
                    return;
                }
                if (attemptsRemaining > 1) {
                    uiHandler.postDelayed(
                            () -> getJsonAttempt(
                                    path,
                                    callback,
                                    failureCallback,
                                    attemptsRemaining - 1,
                                    0
                            ),
                            CONTROL_SAFE_RETRY_DELAY_MS
                    );
                    return;
                }
                wakeLaptopForTerminal("control-unreachable");
                scheduleTerminalWakeRetry("control-unreachable");
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
        postTextAttempt(path, text, callback, failureCallback, 0);
    }

    private void postTextWithIdempotency(
            String path,
            String text,
            String idempotencyKey,
            JsonCallback callback,
            FailureCallback failureCallback
    ) {
        postTextAttempt(path, text, callback, failureCallback, 0, idempotencyKey);
    }

    private void postTextAttempt(
            String path,
            String text,
            JsonCallback callback,
            FailureCallback failureCallback,
            int controlUrlIndex
    ) {
        postTextAttempt(path, text, callback, failureCallback, controlUrlIndex, "");
    }

    private void postTextAttempt(
            String path,
            String text,
            JsonCallback callback,
            FailureCallback failureCallback,
            int controlUrlIndex,
            String idempotencyKey
    ) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            String baseUrl = controlBaseUrl(controlUrlIndex);
            try {
                URL url = new URL(baseUrl + path);
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
                    // WHY: `/submit-text` mutates tmux by pasting and pressing Enter.
                    // If the phone loses the first response and retries against the
                    // fallback control URL, the same visible Send must not paste
                    // twice. Use the standard Idempotency-Key retry pattern while
                    // keeping legacy callers without this header working.
                    connection.setRequestProperty("Idempotency-Key", idempotencyKey.trim());
                }
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
                activeControlBaseUrl = baseUrl;
                uiHandler.post(() -> {
                    try {
                        callback.onResult(payload);
                    } catch (Exception exc) {
                        toast(exc.getMessage());
                    }
                });
            } catch (Exception exc) {
                if (controlUrlIndex + 1 < CONTROL_URLS.length) {
                    uiHandler.postDelayed(
                            () -> postTextAttempt(
                                    path,
                                    text,
                                    callback,
                                    failureCallback,
                                    controlUrlIndex + 1,
                                    idempotencyKey
                            ),
                            CONTROL_SAFE_RETRY_DELAY_MS
                    );
                    return;
                }
                wakeLaptopForTerminal("control-unreachable");
                scheduleTerminalWakeRetry("control-unreachable");
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
            toolbar.setPadding(dp(5), dp(3), dp(5), dp(3) + bottom);
            ViewGroup.LayoutParams params = toolbar.getLayoutParams();
            if (params != null) {
                params.height = dp(TOOLBAR_HEIGHT_DP) + bottom;
                toolbar.setLayoutParams(params);
            }
            if (keyboardReserve == 0) {
                view.post(() -> hideNavigationDeadStrip("insets-no-keyboard"));
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

    private final class PromptComposerEditText extends EditText {
        PromptComposerEditText(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                // WHY: the native prompt composer is a command composer, not a
                // multiline notes field. Phone Enter must behave like the visible
                // Send button and use the same pinned `/submit-text` path.
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    submitDockedPrompt();
                }
                return true;
            }
            if (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_DEL
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && nativeComposerVisibleTextEmpty()) {
                sendEmptyComposerBackspaceToTerminal();
                return true;
            }
            if (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && nativeComposerVisibleTextEmpty()) {
                sendEmptyComposerDeleteToTerminal();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && isDockedPromptComposerVisible()) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    // WHY: Samsung/Gboard often consumes the first Back press by
                    // hiding only the IME, leaving a focused composer bar and
                    // Send button that still shrink the terminal. Treat Back as a read/navigation dismissal
                    // for the docked composer and keep the draft available for
                    // the next deliberate typing tap.
                    hideDockedPromptComposer(false, false);
                }
                return true;
            }
            return super.onKeyPreIme(keyCode, event);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection connection = super.onCreateInputConnection(outAttrs);
            if (connection == null) {
                return null;
            }
            return new InputConnectionWrapper(connection, false) {
                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    if (beforeLength > 0 && nativeComposerVisibleTextEmpty()) {
                        sendEmptyComposerBackspaceToTerminal();
                        return true;
                    }
                    if (afterLength > 0 && nativeComposerVisibleTextEmpty()) {
                        sendEmptyComposerDeleteToTerminal();
                        return true;
                    }
                    return super.deleteSurroundingText(beforeLength, afterLength);
                }

                @Override
                public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                    if (beforeLength > 0 && nativeComposerVisibleTextEmpty()) {
                        sendEmptyComposerBackspaceToTerminal();
                        return true;
                    }
                    if (afterLength > 0 && nativeComposerVisibleTextEmpty()) {
                        sendEmptyComposerDeleteToTerminal();
                        return true;
                    }
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
                }
            };
        }
    }

    private final class StatusDotTextView extends TextView {
        StatusDotTextView(Context context) {
            super(context);
        }

        @Override
        protected void onDetachedFromWindow() {
            stopStatusDotPulse(this);
            super.onDetachedFromWindow();
        }
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
            // callback mostly passive; it may ask the control server to put tmux
            // at live bottom and repaint the current xterm viewport, but it must
            // not reload WebView, open the native composer, or focus the IME.
            // The user's 2026-06-15 APK screenshot proved that waiting for a
            // manual Bottom tap leaves dotted stale blank rows on entry.
            settleEntryLiveBottomSoon("page-finished");
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request == null || !request.isForMainFrame()) {
                return;
            }
            Uri uri = request.getUrl();
            if (uri == null) {
                return;
            }
            handleTerminalLoadFailure(uri.toString());
        }

        @Override
        public void onScaleChanged(WebView view, float oldScale, float newScale) {
            super.onScaleChanged(view, oldScale, newScale);
            webViewScale = newScale;
            keyZoomViewerStateActive = newScale > WEBVIEW_ZOOMED_SCALE_THRESHOLD;
            allowViewerPanBriefly();
            cancelViewerTypingPositionRetries("scale-change");
            cancelLiveInputVisibilityRetries("scale-change");
            // WHY: WebView scale is the Android viewer zoom. Do not translate this
            // into ttyd/tmux font changes or tmux resize commands; one-finger
            // history remains tmux-owned and two-finger positioning stays viewer-owned.
            // Also invalidate delayed live-bottom/input callbacks here: v2.77
            // fixed the regression where stale post-entry alignment could run
            // after a pinch and make the zoomed position rise toward a corner.
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (uri != null && isKnownTerminalUrl(uri.toString())) {
                return false;
            }
            return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !isKnownTerminalUrl(url);
        }
    }
}
