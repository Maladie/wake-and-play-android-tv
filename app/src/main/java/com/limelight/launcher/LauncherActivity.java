package com.limelight.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.hardware.BatteryState;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class LauncherActivity extends Activity {
    private static final String TAG = "WakeAndPlay";
    private static final MoonlightTarget[] MOONLIGHT_TARGETS = {
            new MoonlightTarget("com.limelight.unofficial"),
            new MoonlightTarget("com.limelight"),
            new MoonlightTarget("com.limelight.debug")
    };
    private static final String ACTION_STREAM = "com.limelight.action.STREAM";
    private static final String ACTION_RETURN_STREAM = "com.limelight.action.RETURN_STREAM";
    private static final String ACTION_DISCONNECT_STREAM = "com.limelight.action.DISCONNECT_STREAM";
    private static final String ACTION_QUIT_STREAM_APP = "com.limelight.action.QUIT_STREAM_APP";
    private static final String ACTION_OPEN_SETTINGS = "com.limelight.action.OPEN_SETTINGS";
    private static final String EXTRA_HOST_UUID = "com.limelight.extra.HOST_UUID";
    private static final String EXTRA_APP_ID = "com.limelight.extra.APP_ID";
    private static final String EXTRA_APP_NAME = "com.limelight.extra.APP_NAME";
    private static final String EXTRA_EXTERNAL_FRONTEND = "com.limelight.extra.EXTERNAL_FRONTEND";
    private static final String EXTRA_EXTERNAL_FRONTEND_PACKAGE = "com.limelight.extra.EXTERNAL_FRONTEND_PACKAGE";
    private static final String EXTRA_EXTERNAL_FRONTEND_MESSAGE = "com.limelight.extra.EXTERNAL_FRONTEND_MESSAGE";
    private static final String EXTRA_EXTERNAL_FRONTEND_ANIMATION_EPOCH = "com.limelight.extra.EXTERNAL_FRONTEND_ANIMATION_EPOCH";
    private static final String EXTRA_EXTERNAL_FRONTEND_REDUCED_MOTION = "com.limelight.extra.EXTERNAL_FRONTEND_REDUCED_MOTION";
    private static final String EXTRA_HOST_GATEWAY_ENDPOINT = "com.limelight.extra.HOST_GATEWAY_ENDPOINT";
    private static final String EXTRA_HOST_GATEWAY_TOKEN = "com.limelight.extra.HOST_GATEWAY_TOKEN";
    private static final String EXTRA_HOST_GATEWAY_CERTIFICATE = "com.limelight.extra.HOST_GATEWAY_CERTIFICATE";
    private static final String EXTRA_DISCORD_PROFILE_ID = "com.limelight.extra.DISCORD_PROFILE_ID";
    private static final long HOST_TIMEOUT_MS = 90_000;
    private static final long CONTROLLER_BATTERY_REFRESH_MS = 60_000;
    private static final int REQUEST_BLUETOOTH_CONNECT = 7001;
    private static final String HISTORY_PREFS = "launch_history";
    private static final String PREF_LAST_HOST_UUID = "last_host_uuid";
    private static final String PREF_LAST_APP_ID = "last_app_id";
    private static final String PREF_LAST_APP_NAME = "last_app_name";
    private static final String PREF_LAST_LAUNCH_AT = "last_launch_at";
    private static final String PREF_UI_SOUNDS = "ui_sounds";
    private static final String PREF_REDUCED_MOTION = "reduced_motion";
    private static final int DISCORD_BLURPLE = 0xFF5865F2;
    private static final int DISCORD_GREEN = 0xFF23A559;
    private static final int DISCORD_RED = 0xFFDA373C;
    private static final int DISCORD_ORANGE = 0xFFF0A61B;
    private static final int DISCORD_SURFACE = 0xFF313338;
    private static final int DISCORD_TOOL = 0xFF404249;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final LifecycleRequestGate dashboardRequests = new LifecycleRequestGate();
    private final InputManager.InputDeviceListener controllerDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    scheduleControllerRefresh();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    scheduleControllerRefresh();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    scheduleControllerRefresh();
                }
            };
    private final AtomicBoolean launchCancelled = new AtomicBoolean(false);
    private final AtomicBoolean hostProbeRunning = new AtomicBoolean(false);
    private final AtomicInteger hostProbeRequest = new AtomicInteger();
    private final AtomicInteger backdropRequest = new AtomicInteger();
    private final AtomicInteger integrationPanelRequest = new AtomicInteger();
    private final AtomicInteger communityAvailabilityRequest = new AtomicInteger();
    private final HostGatewayClient hostGatewayClient = new HostGatewayClient();
    private final Set<String> discordAutoConnectAttempted = new HashSet<>();
    private final Map<String, TextView> hostStatusViews = new HashMap<>();
    private List<Host> visibleHosts = Collections.emptyList();
    private StreamStatus currentStreamStatus;
    private long lastHostProbeAt;
    private boolean initialFocusPending;
    private boolean userNavigationStarted;
    private long initialFocusDeadline;
    private boolean sessionStatusLoaded;
    private boolean dashboardActive;
    private boolean controllerListenerRegistered;
    private InputManager inputManager;
    private HostGatewayStore hostGatewayStore;
    private LinearLayout controllerRow;
    private LinearLayout hostRow;
    private LinearLayout appRow;
    private TextView controllersLabel;
    private HorizontalScrollView controllerScroll;
    private HorizontalScrollView hostScroll;
    private HorizontalScrollView appScroll;
    private TextView emptyState;
    private TextView appsLabel;
    private TextView appEmptyState;
    private TextView sessionState;
    private LinearLayout resumeButton;
    private TextView resumeTitle;
    private TextView resumeSubtitle;
    private LinearLayout sessionButton;
    private TextView sessionTitle;
    private TextView communityButton;
    private TextView settingsButton;
    private FrameLayout modalLayer;
    private ScrollView sidePanelScroll;
    private LinearLayout sidePanel;
    private Runnable sidePanelBackAction;
    private ImageView artworkBackdrop;
    private ImageView artworkHero;
    private View artworkScrim;
    private Bitmap currentBackdropBitmap;
    private Bitmap currentHeroBitmap;
    private final List<Bitmap> retiredArtworkBitmaps = new ArrayList<>();
    private FrameLayout homeLayer;
    private FrameLayout loadingLayer;
    private GenerativeSlideshow slideshow;
    private TextView loadingTitle;
    private TextView loadingMessage;
    private TextView loadingStatus;
    private final Random loadingMessageRandom = new Random();
    private int lastLoadingMessageIndex = -1;
    private ControllerInfo pendingController;
    private BluetoothAction pendingBluetoothAction;
    private Host selectedHost;
    private View selectedHostCard;
    private View lastFocusedTile;
    private View lastContentFocus;
    private View focusAfterExternalActivity;
    private Runnable communityExitAction;
    private Host discordShortcutHost;
    private HostGatewayClient.Connection discordShortcutConnection;
    private TextView discordShortcutVoiceStatus;
    private TextView discordShortcutMuteAction;
    private TextView discordShortcutLeaveAction;
    private boolean discordShortcutVoiceConnected;
    private boolean discordShortcutMuted;
    private boolean restoreFocusAfterResume;
    private boolean uiSoundsEnabled;
    private boolean reducedMotion;
    private int glassAccentColor = 0xFF66549A;
    private int controllerScrollPosition;
    private int hostScrollPosition;
    private int appScrollPosition;
    private String appScrollHostUuid;
    private Host lastLaunchHost;
    private StreamApp lastLaunchApp;
    private final String[] loadingMessages = {
            "Loading content…",
            "Combobulating resources…",
            "Waking the gaming rig…",
            "Negotiating photons…",
            "Aligning virtual displays…",
            "Preparing controller uplink…",
            "Calibrating couch coordinates…",
            "Julification in progress…",
            "Pampering guinea pigs…",
            "Did you know the scientific name for a guinea pig is Cavia porcellus?",
            "Polishing pixels…",
            "Feeding the hamsters in the server room…",
            "Convincing the GPU to cooperate…",
            "Rolling for initiative…",
            "Untangling imaginary network cables…",
            "Teaching photons to take the shortest route…",
            "Asking packets to form an orderly queue…",
            "Warming up tiny digital dragons…",
            "Checking the couch-to-screen alignment…",
            "Applying ceremonial RGB lighting…",
            "Almost ready…"
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        hostGatewayStore = new HostGatewayStore(this);
        uiSoundsEnabled = history().getBoolean(PREF_UI_SOUNDS, true);
        reducedMotion = history().getBoolean(PREF_REDUCED_MOTION, false);
        // Always submit a complete opaque frame. Sony's compositor otherwise
        // reuses partially damaged buffers during task hand-off.
        getWindow().setFormat(PixelFormat.OPAQUE);
        setContentView(buildUi());
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dashboardActive = true;
        discordAutoConnectAttempted.clear();
        int dashboardToken = dashboardRequests.activate();
        if (inputManager != null && !controllerListenerRegistered) {
            inputManager.registerInputDeviceListener(controllerDeviceListener, mainHandler);
            controllerListenerRegistered = true;
        }
        launchCancelled.set(false);
        if (homeLayer != null) {
            userNavigationStarted = false;
            initialFocusPending = !restoreFocusAfterResume;
            // Resume may become visible after the status-provider response. Give it
            // one short opportunity to become the initial focus, never a delayed
            // opportunity to replace focus after navigation has already begun.
            initialFocusDeadline = initialFocusPending ? Long.MAX_VALUE : 0L;
            sessionStatusLoaded = false;
            showHome();
            refreshDashboard(dashboardToken);
            if (initialFocusPending) {
                // The initial provider reads above are complete now, so the
                // deadline begins when the UI is actually ready for input.
                initialFocusDeadline = SystemClock.uptimeMillis() + 450L;
            }
            if (restoreFocusAfterResume) {
                restoreFocusAfterResume = false;
                mainHandler.postDelayed(this::restoreExternalActivityFocus, 650);
            } else {
                mainHandler.postDelayed(this::finishInitialFocus, 120);
            }
        }
    }

    @Override
    protected void onPause() {
        dashboardActive = false;
        dashboardRequests.deactivate();
        backdropRequest.incrementAndGet();
        hostProbeRequest.incrementAndGet();
        hostProbeRunning.set(false);
        integrationPanelRequest.incrementAndGet();
        communityAvailabilityRequest.incrementAndGet();
        if (inputManager != null && controllerListenerRegistered) {
            inputManager.unregisterInputDeviceListener(controllerDeviceListener);
            controllerListenerRegistered = false;
        }
        super.onPause();
        if (controllerScroll != null) controllerScrollPosition = controllerScroll.getScrollX();
        if (hostScroll != null) hostScrollPosition = hostScroll.getScrollX();
        if (appScroll != null) appScrollPosition = appScroll.getScrollX();
        mainHandler.removeCallbacksAndMessages(null);
        if (slideshow != null) slideshow.stop();
        if (homeLayer != null && homeLayer.getVisibility() == View.VISIBLE) {
            // Force a complete redraw when Android TV brings this task back.
            // Some TV compositors otherwise reuse a partially damaged buffer.
            homeLayer.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && homeLayer != null && homeLayer.getVisibility() == View.VISIBLE) {
            mainHandler.postDelayed(this::focusResumeActionIfPending, 60);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (artworkBackdrop != null) artworkBackdrop.setImageDrawable(null);
        if (artworkHero != null) artworkHero.setImageDrawable(null);
        recycleCurrentArtwork();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (modalLayer != null && modalLayer.getVisibility() == View.VISIBLE) {
            Runnable backAction = sidePanelBackAction;
            if (backAction != null) {
                sidePanelBackAction = null;
                backAction.run();
            } else {
                hideSidePanel(true);
            }
        } else if (loadingLayer.getVisibility() == View.VISIBLE) {
            launchCancelled.set(true);
            showHome();
        } else {
            showExitConfirmationPanel();
        }
    }

    private void showExitConfirmationPanel() {
        TextView cancel = panelAction("CANCEL");
        TextView exit = discordAction("EXIT WAKE & PLAY", DISCORD_RED);
        Runnable dismiss = () -> hideSidePanel(true);
        cancel.setOnClickListener(v -> dismiss.run());
        exit.setOnClickListener(v -> {
            hideSidePanel(false);
            finishAndRemoveTask();
        });
        showSidePanel("WAKE & PLAY", "Close Wake & Play?",
                "The active host session will not be stopped.",
                dismiss, panelActionRow(cancel, exit));
    }

    private void restoreExternalActivityFocus() {
        View target = focusAfterExternalActivity;
        focusAfterExternalActivity = null;
        if (target != null && target.isShown() && target.requestFocus()) return;
        if (settingsButton != null && settingsButton.isShown()) settingsButton.requestFocus();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 &&
                modalLayer != null && modalLayer.getVisibility() == View.VISIBLE &&
                discordShortcutConnection != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_X) {
                runDiscordControllerShortcut(false);
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y) {
                runDiscordControllerShortcut(true);
                return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && isNavigationKey(event.getKeyCode())) {
            // If the user starts navigating before the asynchronous dashboard data
            // arrives, their choice wins over the delayed default Resume focus.
            userNavigationStarted = true;
            initialFocusPending = false;
            if (modalLayer != null && modalLayer.getVisibility() == View.VISIBLE &&
                    (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ||
                            event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) &&
                    moveSidePanelFocusVertically(event.getKeyCode())) {
                playUiSound(event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                        ? AudioManager.FX_FOCUS_NAVIGATION_UP
                        : AudioManager.FX_FOCUS_NAVIGATION_DOWN, 0.28f);
                return true;
            }
            if (event.getRepeatCount() == 0) {
                if (isConfirmKey(event.getKeyCode())) {
                    playUiSound(AudioManager.FX_KEY_CLICK, 0.42f);
                } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                        event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
                    playUiSound(AudioManager.FX_FOCUS_NAVIGATION_DOWN, 0.3f);
                }
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                        event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
                    onBackPressed();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean moveSidePanelFocusVertically(int keyCode) {
        View focused = getCurrentFocus();
        if (focused == null || !isDescendantOf(focused, sidePanel)) return false;
        List<List<View>> rows = sidePanelFocusRows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<View> row = rows.get(rowIndex);
            int column = row.indexOf(focused);
            if (column < 0) continue;
            int targetRow = keyCode == KeyEvent.KEYCODE_DPAD_UP
                    ? rowIndex - 1 : rowIndex + 1;
            if (targetRow < 0) {
                sidePanelScroll.smoothScrollTo(0, 0);
                return true;
            }
            if (targetRow >= rows.size()) {
                sidePanelScroll.fullScroll(View.FOCUS_DOWN);
                return true;
            }
            List<View> destinationRow = rows.get(targetRow);
            View target = destinationRow.get(Math.min(column, destinationRow.size() - 1));
            target.requestFocus();
            Rect targetRect = new Rect();
            target.getDrawingRect(targetRect);
            sidePanel.offsetDescendantRectToMyCoords(target, targetRect);
            int viewportTop = sidePanelScroll.getScrollY() + dp(72);
            int viewportBottom = sidePanelScroll.getScrollY() +
                    sidePanelScroll.getHeight() - dp(72);
            if (targetRect.top < viewportTop) {
                sidePanelScroll.smoothScrollTo(0, Math.max(0, targetRect.top - dp(72)));
            } else if (targetRect.bottom > viewportBottom) {
                sidePanelScroll.smoothScrollTo(0,
                        targetRect.bottom - sidePanelScroll.getHeight() + dp(72));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE &&
                (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            float strongestAxis = Math.max(
                    Math.max(Math.abs(event.getAxisValue(MotionEvent.AXIS_X)),
                            Math.abs(event.getAxisValue(MotionEvent.AXIS_Y))),
                    Math.max(Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_X)),
                            Math.abs(event.getAxisValue(MotionEvent.AXIS_HAT_Y))));
            if (strongestAxis > 0.35f) {
                userNavigationStarted = true;
                initialFocusPending = false;
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private static boolean isNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                keyCode == KeyEvent.KEYCODE_BACK;
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 6, 10));

        homeLayer = new FrameLayout(this);
        homeLayer.setBackgroundColor(Color.rgb(5, 6, 10));
        homeLayer.addView(new GenerativeBackdrop(this), match());

        artworkBackdrop = new ImageView(this);
        artworkBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkBackdrop.setAlpha(0f);
        homeLayer.addView(artworkBackdrop, match());

        artworkHero = new ImageView(this);
        artworkHero.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artworkHero.setPadding(dp(34), dp(66), dp(34), dp(66));
        artworkHero.setAlpha(0f);
        FrameLayout.LayoutParams heroParams = new FrameLayout.LayoutParams(
                dp(520), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        heroParams.rightMargin = dp(18);
        homeLayer.addView(artworkHero, heroParams);

        artworkScrim = new View(this);
        artworkScrim.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF405060A, 0xC405060A, 0x7005060A}));
        artworkScrim.setAlpha(0f);
        homeLayer.addView(artworkScrim, match());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(64), dp(26), dp(64), dp(8));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        homeLayer.addView(content, match());

        TextView title = text("WAKE & PLAY", 30, Color.WHITE, true);
        content.addView(title, wrap());
        TextView subtitle = text("Choose a host and application. We will wake the PC and start the stream.", 15, 0xFFBCC3DD, false);
        LinearLayout.LayoutParams subtitleParams = wrap();
        subtitleParams.topMargin = dp(5);
        content.addView(subtitle, subtitleParams);

        sessionState = text("MOONLIGHT · IDLE", 13, 0xFF9CA6C5, true);
        LinearLayout.LayoutParams sessionParams = wrap();
        sessionParams.topMargin = dp(9);
        content.addView(sessionState, sessionParams);

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        quickActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams quickActionsParams = wrap();
        quickActionsParams.topMargin = dp(6);
        content.addView(quickActions, quickActionsParams);

        resumeButton = new LinearLayout(this);
        resumeButton.setOrientation(LinearLayout.VERTICAL);
        resumeButton.setGravity(Gravity.CENTER_VERTICAL);
        resumeButton.setFocusable(true);
        resumeButton.setClickable(true);
        resumeButton.setSoundEffectsEnabled(false);
        resumeButton.setMinimumWidth(dp(260));
        resumeButton.setMinimumHeight(dp(54));
        resumeButton.setPadding(dp(20), dp(6), dp(20), dp(6));
        resumeTitle = text("▶  RETURN TO GAME", 15, 0xFFF7F2FF, true);
        resumeSubtitle = text("", 11, 0xFFC8BCE8, false);
        LinearLayout.LayoutParams resumeSubtitleParams = wrap();
        resumeSubtitleParams.topMargin = dp(2);
        resumeButton.addView(resumeTitle, wrap());
        resumeButton.addView(resumeSubtitle, resumeSubtitleParams);
        resumeButton.setVisibility(View.GONE);
        resumeButton.setOnClickListener(v -> {
            if (isActiveStream(currentStreamStatus)) {
                returnToActiveStream(currentStreamStatus.moonlightPackage);
            } else if (lastLaunchHost != null && lastLaunchApp != null) {
                beginAppLaunch(lastLaunchHost, lastLaunchApp);
            }
        });
        resumeButton.setOnFocusChangeListener((v, focused) -> stylePrimaryButton(resumeButton, focused));
        stylePrimaryButton(resumeButton, false);
        quickActions.addView(resumeButton, wrap());

        sessionButton = new LinearLayout(this);
        sessionButton.setOrientation(LinearLayout.HORIZONTAL);
        sessionButton.setGravity(Gravity.CENTER);
        sessionButton.setFocusable(true);
        sessionButton.setClickable(true);
        sessionButton.setSoundEffectsEnabled(false);
        sessionButton.setMinimumHeight(dp(54));
        sessionButton.setPadding(dp(16), dp(6), dp(16), dp(6));
        ImageView sessionIcon = new ImageView(this);
        sessionIcon.setImageResource(R.drawable.ic_active_session);
        sessionIcon.setColorFilter(0xFFDCCFFF);
        LinearLayout.LayoutParams sessionIconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        sessionIconParams.rightMargin = dp(9);
        sessionButton.addView(sessionIcon, sessionIconParams);
        sessionTitle = text("SESSION", 14, 0xFFF1EAFF, true);
        sessionButton.addView(sessionTitle, wrap());
        sessionButton.setVisibility(View.GONE);
        sessionButton.setOnClickListener(v -> showSessionPanel());
        sessionButton.setOnFocusChangeListener((v, focused) -> styleCompactButton(sessionButton, focused));
        styleCompactButton(sessionButton, false);
        LinearLayout.LayoutParams sessionButtonParams = wrap();
        sessionButtonParams.leftMargin = dp(10);
        quickActions.addView(sessionButton, sessionButtonParams);

        LinearLayout topActions = new LinearLayout(this);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams topActionsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        topActionsParams.topMargin = dp(30);
        topActionsParams.rightMargin = dp(64);
        homeLayer.addView(topActions, topActionsParams);

        communityButton = text("●  DISCORD", 13, 0xFFD2C4FF, true);
        communityButton.setFocusable(true);
        communityButton.setClickable(true);
        communityButton.setSoundEffectsEnabled(false);
        communityButton.setPadding(dp(12), dp(5), dp(12), dp(5));
        communityButton.setVisibility(View.GONE);
        communityButton.setOnClickListener(v -> showCommunityForSelectedHost());
        communityButton.setOnFocusChangeListener((v, focused) ->
                styleCompactButton(communityButton, focused));
        styleCompactButton(communityButton, false);
        LinearLayout.LayoutParams communityParams = wrap();
        communityParams.rightMargin = dp(10);
        topActions.addView(communityButton, communityParams);

        settingsButton = text("⚙  OPTIONS", 13, 0xFFD2C4FF, true);
        settingsButton.setFocusable(true);
        settingsButton.setClickable(true);
        settingsButton.setSoundEffectsEnabled(false);
        settingsButton.setPadding(dp(12), dp(5), dp(12), dp(5));
        settingsButton.setOnClickListener(v -> showOptionsPanel());
        settingsButton.setOnFocusChangeListener((v, focused) -> styleCompactButton(settingsButton, focused));
        styleCompactButton(settingsButton, false);
        topActions.addView(settingsButton, wrap());

        resumeButton.setId(View.generateViewId());
        sessionButton.setId(View.generateViewId());
        communityButton.setId(View.generateViewId());
        settingsButton.setId(View.generateViewId());
        resumeButton.setNextFocusRightId(sessionButton.getId());
        sessionButton.setNextFocusLeftId(resumeButton.getId());
        communityButton.setNextFocusRightId(settingsButton.getId());
        communityButton.setNextFocusDownId(resumeButton.getId());
        updateCommunityButtonVisibility(false);

        controllersLabel = sectionLabel("CONTROLLERS");
        LinearLayout.LayoutParams sectionParams = wrap();
        sectionParams.topMargin = dp(11);
        content.addView(controllersLabel, sectionParams);
        controllerScroll = horizontalScroll();
        controllerRow = horizontalRow();
        controllerScroll.addView(controllerRow);
        LinearLayout.LayoutParams controllerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        controllerParams.topMargin = dp(5);
        content.addView(controllerScroll, controllerParams);

        TextView hostsLabel = sectionLabel("STREAMING HOSTS");
        LinearLayout.LayoutParams hostsLabelParams = wrap();
        hostsLabelParams.topMargin = dp(11);
        content.addView(hostsLabel, hostsLabelParams);
        hostScroll = horizontalScroll();
        hostRow = horizontalRow();
        hostScroll.addView(hostRow);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        hostParams.topMargin = dp(6);
        content.addView(hostScroll, hostParams);

        emptyState = text("No saved hosts found in a compatible Moonlight X installation.", 17, 0xFFBDC4D8, false);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setVisibility(View.GONE);
        content.addView(emptyState, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));

        appsLabel = sectionLabel("APPS");
        LinearLayout.LayoutParams appsLabelParams = wrap();
        appsLabelParams.topMargin = dp(10);
        content.addView(appsLabel, appsLabelParams);
        appScroll = horizontalScroll();
        appScroll.setPadding(0, 0, dp(12), dp(10));
        appRow = horizontalRow();
        appScroll.addView(appRow);
        // Keep the complete 2:3 artwork visible even when the controller row is
        // present. A weighted remainder shrank this viewport below the tile's
        // required height and clipped posters at the top and bottom.
        LinearLayout.LayoutParams appParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120));
        appParams.topMargin = dp(5);
        content.addView(appScroll, appParams);

        appEmptyState = text("Choose a streaming host to see its applications.", 16, 0xFFBDC4D8, false);
        appEmptyState.setGravity(Gravity.CENTER);
        appRow.addView(appEmptyState, new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT));

        buildSidePanelLayer();

        loadingLayer = new FrameLayout(this);
        loadingLayer.setVisibility(View.GONE);
        // Keep the complete hand-off screen in one opaque compositor layer.
        // Without this, the task switch can preserve only the independently
        // invalidated loading message and briefly drop the title/background.
        loadingLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        loadingLayer.setClickable(true);
        slideshow = new GenerativeSlideshow(this);
        loadingLayer.addView(slideshow, match());
        View shade = new View(this);
        shade.setBackgroundColor(0x57000000);
        loadingLayer.addView(shade, match());

        LinearLayout loadingCopy = new LinearLayout(this);
        loadingCopy.setOrientation(LinearLayout.VERTICAL);
        loadingCopy.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(dp(850), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        loadingLayer.addView(loadingCopy, copyParams);
        loadingTitle = text("", 38, Color.WHITE, true);
        loadingTitle.setGravity(Gravity.CENTER);
        loadingCopy.addView(loadingTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        loadingMessage = text("", 23, 0xFFE5E8F5, false);
        loadingMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(18);
        loadingCopy.addView(loadingMessage, msgParams);
        loadingStatus = text("", 14, 0xFFB8C0D9, false);
        loadingStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(14);
        loadingCopy.addView(loadingStatus, statusParams);
        TextView cancelHint = text("Press BACK to return to Wake & Play", 14, 0xBFFFFFFF, false);
        cancelHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(34);
        loadingCopy.addView(cancelHint, hintParams);

        root.addView(homeLayer, match());
        root.addView(loadingLayer, match());
        return root;
    }

    private void refreshDashboard(int dashboardToken) {
        refreshControllersAsync(dashboardToken);
        // Perform the small local provider reads before the first focus decision.
        // If these arrive later, making Resume visible can cause Android TV to
        // redirect focus while the user is already navigating the dashboard.
        renderStreamStatus(loadStreamStatus());
        renderHosts(loadHosts());
        mainHandler.postDelayed(() -> refreshSessionStatusAsync(dashboardToken), 1500);
    }

    private void refreshSessionStatusAsync() {
        refreshSessionStatusAsync(dashboardRequests.currentToken());
    }

    private void refreshSessionStatusAsync(int dashboardToken) {
        if (!isCurrentDashboardRequest(dashboardToken)) return;
        executor.execute(() -> {
            StreamStatus status = loadStreamStatus();
            mainHandler.post(() -> {
                if (!isCurrentDashboardRequest(dashboardToken)) return;
                renderStreamStatus(status);
                if (homeLayer.getVisibility() == View.VISIBLE) {
                    mainHandler.postDelayed(
                            () -> refreshSessionStatusAsync(dashboardToken), 1500);
                }
            });
        });
    }

    private boolean isCurrentDashboardRequest(int dashboardToken) {
        return dashboardRequests.isCurrent(dashboardToken) &&
                !isFinishing() && !isDestroyed();
    }

    private StreamStatus loadStreamStatus() {
        for (MoonlightTarget target : MOONLIGHT_TARGETS) {
            Uri uri = target.statusUri;
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    StreamStatus status = new StreamStatus();
                    status.state = value(cursor, "state");
                    status.stage = value(cursor, "stage");
                    status.host = value(cursor, "host");
                    status.app = value(cursor, "app");
                    status.computer = value(cursor, "computer");
                    status.bitrateKbps = integer(cursor, "bitrate_kbps", 0);
                    status.width = integer(cursor, "width", 0);
                    status.height = integer(cursor, "height", 0);
                    status.fps = integer(cursor, "fps", 0);
                    status.hdr = integer(cursor, "hdr", 0) != 0;
                    status.activityAlive = integer(cursor, "activity_alive", 0) != 0;
                    status.startedAt = longValue(cursor, "started_at", 0L);
                    status.updatedAt = longValue(cursor, "updated_at", 0L);
                    status.moonlightPackage = target.packageName;
                    return status;
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to read stream status from " + uri, error);
            }
        }
        return null;
    }

    private void renderStreamStatus(StreamStatus status) {
        sessionStatusLoaded = true;
        currentStreamStatus = status;
        updateResumeAction();
        updateSessionHostStatus();
        if (System.currentTimeMillis() - lastHostProbeAt >= 10_000) {
            refreshHostAvailabilityAsync();
        }
        if (status == null || status.state == null || status.state.isEmpty()) {
            sessionState.setText("MOONLIGHT · STATUS UNAVAILABLE");
            sessionState.setTextColor(0xFF9CA6C5);
            return;
        }
        if (isActiveState(status.state) && !status.activityAlive) {
            StringBuilder idle = new StringBuilder("MOONLIGHT · IDLE");
            if (status.app != null && !status.app.isEmpty()) idle.append(" · LAST: ").append(status.app);
            if (status.updatedAt > 0) idle.append(" · ").append(formatRelative(System.currentTimeMillis() - status.updatedAt));
            sessionState.setText(idle.toString());
            sessionState.setTextColor(0xFF9CA6C5);
            return;
        }
        String state = status.state.toUpperCase(Locale.ROOT);
        StringBuilder label = new StringBuilder("streaming".equals(status.state) ? "● " :
                "reconnecting".equals(status.state) ? "◐ " : "MOONLIGHT · ").append(state);
        if ("streaming".equals(status.state) || "reconnecting".equals(status.state)) {
            if (status.app != null && !status.app.isEmpty()) label.append(" · ").append(status.app);
            String quality = formatStreamQuality(status);
            if (!quality.isEmpty()) label.append(" · ").append(quality);
            if (status.hdr) label.append(" · HDR");
            if (status.startedAt > 0) {
                label.append(" · ").append(formatElapsed(System.currentTimeMillis() - status.startedAt));
            }
        } else if ("connecting".equals(status.state) && status.stage != null && !status.stage.isEmpty()) {
            label.append(" · ").append(status.stage);
        }
        sessionState.setText(label.toString());
        sessionState.setTextColor("error".equals(status.state) ? 0xFFFF8A80 :
                "streaming".equals(status.state) ? 0xFF69F0AE :
                        "reconnecting".equals(status.state) ? 0xFFFFB74D : 0xFF9CA6C5);
    }

    private void refreshControllersAsync() {
        refreshControllersAsync(dashboardRequests.currentToken());
    }

    private void refreshControllersAsync(int dashboardToken) {
        if (!isCurrentDashboardRequest(dashboardToken)) return;
        executor.execute(() -> {
            List<ControllerInfo> controllers = loadControllers();
            mainHandler.post(() -> {
                if (!isCurrentDashboardRequest(dashboardToken)) return;
                renderControllers(controllers);
                mainHandler.postDelayed(controllerRefreshRunnable,
                        CONTROLLER_BATTERY_REFRESH_MS);
            });
        });
    }

    private void scheduleControllerRefresh() {
        if (!dashboardActive) return;
        mainHandler.removeCallbacks(controllerRefreshRunnable);
        mainHandler.postDelayed(controllerRefreshRunnable, 150);
    }

    private final Runnable controllerRefreshRunnable = this::refreshControllersAsync;

    private List<ControllerInfo> loadControllers() {
        List<InputDevice> devices = new ArrayList<>();
        Set<String> descriptors = new HashSet<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || !isGamepad(device)) continue;
            String deviceName = device.getName();
            if (deviceName != null && deviceName.toLowerCase(Locale.ROOT).startsWith("virtual-")) continue;
            String descriptor = device.getDescriptor();
            if (descriptor != null && !descriptors.add(descriptor)) continue;
            devices.add(device);
        }
        Collections.sort(devices,
                (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        List<ControllerInfo> controllers = new ArrayList<>();
        for (InputDevice device : devices) {
            int percentage = -1;
            boolean charging = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BatteryState battery = device.getBatteryState();
                if (battery.isPresent()) {
                    if (!Float.isNaN(battery.getCapacity())) percentage = Math.round(battery.getCapacity() * 100f);
                    charging = battery.getStatus() == BatteryState.STATUS_CHARGING;
                }
            }
            controllers.add(new ControllerInfo(device.getId(), device.getDescriptor(), device.getName(), percentage, charging));
        }
        return controllers;
    }

    private void renderControllers(List<ControllerInfo> controllers) {
        controllerRow.removeAllViews();
        boolean hasControllers = !controllers.isEmpty();
        controllersLabel.setText(hasControllers ? "CONTROLLERS" : "CONTROLLERS · NONE");
        controllerScroll.setVisibility(hasControllers ? View.VISIBLE : View.GONE);
        int player = 1;
        for (ControllerInfo controller : controllers) {
            controllerRow.addView(controllerCard(player++, controller), cardSpacing());
        }
        controllerScroll.post(() -> controllerScroll.scrollTo(controllerScrollPosition, 0));
    }

    private View controllerCard(int player, ControllerInfo controller) {
        String name = controller.name;
        int percentage = controller.percentage;
        boolean charging = controller.charging;
        LinearLayout card = cardBase(dp(220), dp(50));
        TextView icon = text("🎮", 23, Color.WHITE, false);
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("P" + player + "  " + compactControllerName(name), 14, Color.WHITE, true);
        label.setSingleLine(true);
        copy.addView(label, wrap());
        int batteryColor = percentage < 0 ? 0xFFB3B8C8 : charging ? 0xFF64B5F6 : percentage <= 10 ? 0xFFFF5252 : percentage <= 30 ? 0xFFFFB74D : 0xFF69F0AE;
        String battery = charging ? "⚡ " : "▰ ";
        TextView level = text(battery + (percentage < 0 ? "Battery unavailable" : percentage + "%"), 14, batteryColor, false);
        level.setSingleLine(true);
        copy.addView(level, wrap());
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final int controllerNumber = player;
        card.setOnClickListener(v -> showControllerMenu(controllerNumber, controller));
        card.setOnFocusChangeListener((v, focused) -> {
            styleCard(card, focused);
            noteTileFocus(card, focused);
        });
        return card;
    }

    private void showControllerMenu(int player, ControllerInfo controller) {
        boolean canIdentify = ControllerActions.canIdentify(controller.deviceId);
        boolean canPowerOff = ControllerActions.canDisconnect();
        String[] actions = {
                canIdentify ? "Identify controller" : "Identify controller · unavailable",
                canPowerOff ? "Power off controller" : "Power off controller · unavailable",
                "Unpair controller"
        };
        new AlertDialog.Builder(this)
                .setTitle("P" + player + " · " + compactControllerName(controller.name))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (canIdentify) identifyController(controller);
                        else showUnavailableControllerFeature("Sony does not expose LED or vibration controls for this controller to apps.");
                    } else if (which == 1) {
                        if (canPowerOff) confirmPowerOff(controller);
                        else showUnavailableControllerFeature("This Android TV version does not let apps power off Bluetooth controllers.");
                    } else {
                        confirmUnpair(controller);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUnavailableControllerFeature(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Feature unavailable")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void identifyController(ControllerInfo controller) {
        ControllerActions.identify(controller.deviceId, mainHandler,
                (success, message) -> mainHandler.post(() ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()));
    }

    private void confirmPowerOff(ControllerInfo controller) {
        new AlertDialog.Builder(this)
                .setTitle("Power off controller?")
                .setMessage("The controller will disconnect from the TV. Press the PS button to connect it again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Power off", (dialog, which) ->
                        runBluetoothAction(controller, BluetoothAction.POWER_OFF))
                .show();
    }

    private void confirmUnpair(ControllerInfo controller) {
        new AlertDialog.Builder(this)
                .setTitle("Unpair controller?")
                .setMessage("The Bluetooth pairing will be removed. To use this controller again, pair it with the TV once more.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unpair", (dialog, which) ->
                        runBluetoothAction(controller, BluetoothAction.UNPAIR))
                .show();
    }

    private void runBluetoothAction(ControllerInfo controller, BluetoothAction action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            pendingController = controller;
            pendingBluetoothAction = action;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        executeBluetoothAction(controller, action);
    }

    private void executeBluetoothAction(ControllerInfo controller, BluetoothAction action) {
        ControllerActions.ResultCallback callback = (success, message) -> mainHandler.post(() -> {
            if (success) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                refreshControllersAsync();
            } else {
                showBluetoothFailure(message);
            }
        });
        executor.execute(() -> {
            if (action == BluetoothAction.POWER_OFF) {
                ControllerActions.disconnect(this, controller.deviceId, callback);
            } else {
                ControllerActions.unpair(controller.deviceId, callback);
            }
        });
    }

    private void showBluetoothFailure(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Operation unavailable")
                .setMessage(message)
                .setNegativeButton("Close", null)
                .setPositiveButton("Bluetooth settings", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    } catch (Exception error) {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_BLUETOOTH_CONNECT) return;
        ControllerInfo controller = pendingController;
        BluetoothAction action = pendingBluetoothAction;
        pendingController = null;
        pendingBluetoothAction = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && controller != null && action != null) {
            executeBluetoothAction(controller, action);
        } else {
            Toast.makeText(this, "Bluetooth access is required for this operation.", Toast.LENGTH_LONG).show();
        }
    }

    private void renderHosts(List<Host> hosts) {
        hostRow.removeAllViews();
        hostStatusViews.clear();
        visibleHosts = new ArrayList<>(hosts);
        selectedHostCard = null;
        resolveLastLaunch(hosts);
        emptyState.setVisibility(hosts.isEmpty() ? View.VISIBLE : View.GONE);
        for (Host host : hosts) {
            View card = hostCard(host);
            hostRow.addView(card, cardSpacing());
            if (selectedHost != null && host.uuid.equals(selectedHost.uuid)) {
                selectedHostCard = card;
                selectedHost = host;
                styleHostCard(card, false, true);
            }
        }
        if (!hosts.isEmpty()) {
            if (selectedHostCard == null) {
                selectedHostCard = hostRow.getChildAt(0);
                styleHostCard(selectedHostCard, false, true);
                selectedHost = hosts.get(0);
            }
            // onPause() removes pending UI callbacks so a provider result cannot
            // redraw this Activity while Moonlight is covering it. If that happens
            // before the app list callback runs, the selected host is still kept
            // but its temporary loading row would otherwise survive every resume.
            // Always issue a fresh app query for the selected host when rebuilding
            // the dashboard.
            selectHost(selectedHost, false);
        } else {
            selectedHost = null;
            renderApps(null, Collections.emptyList());
        }
        updateSessionHostStatus();
        refreshHostAvailabilityAsync();
        hostScroll.post(() -> hostScroll.scrollTo(hostScrollPosition, 0));
    }

    private View hostCard(Host host) {
        LinearLayout card = cardBase(dp(250), dp(82));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(7), dp(16), dp(7));
        styleHostCard(card, false, false);
        TextView icon = text("▣", 16, 0xFF9E8ACB, true);
        card.addView(icon, wrap());
        TextView name = text(host.name, 16, Color.WHITE, true);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = wrap(); nameParams.topMargin = dp(1);
        card.addView(name, nameParams);
        TextView address = text("CHECKING · " + (host.address != null ? host.address : "Address unavailable"), 10, 0xFFABB3CA, false);
        address.setSingleLine(true);
        LinearLayout.LayoutParams addrParams = wrap(); addrParams.topMargin = dp(1);
        card.addView(address, addrParams);
        hostStatusViews.put(host.uuid, address);
        card.setOnClickListener(v -> {
            if (selectedHostCard != null && selectedHostCard != card) {
                styleHostCard(selectedHostCard, selectedHostCard.hasFocus(), false);
            }
            selectedHostCard = card;
            styleHostCard(card, card.hasFocus(), true);
            selectHost(host, true);
        });
        card.setOnFocusChangeListener((v, focused) -> {
            styleHostCard(card, focused, card == selectedHostCard);
            noteTileFocus(card, focused);
        });
        return card;
    }

    private void selectHost(Host host, boolean moveFocusToApps) {
        int dashboardToken = dashboardRequests.currentToken();
        if (!isCurrentDashboardRequest(dashboardToken)) return;
        selectedHost = host;
        refreshCommunityAvailability(host);
        clearArtworkBackdrop();
        appsLabel.setText("APPS · " + host.name.toUpperCase(Locale.ROOT));
        appRow.removeAllViews();
        TextView loading = text("Loading cached applications…", 16, 0xFFBDC4D8, false);
        appRow.addView(loading, new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.MATCH_PARENT));
        executor.execute(() -> {
            List<StreamApp> apps = loadApps(host);
            mainHandler.post(() -> {
                if (!isCurrentDashboardRequest(dashboardToken)) return;
                if (selectedHost == null || !host.uuid.equals(selectedHost.uuid)) return;
                renderApps(host, apps);
                // Loading is asynchronous. Only continue the user's explicit
                // host-to-app navigation if they are still focused on that host.
                if (moveFocusToApps && !apps.isEmpty() && selectedHostCard != null &&
                        selectedHostCard.hasFocus()) {
                    appRow.getChildAt(0).requestFocus();
                }
            });
        });
    }

    private void renderApps(Host host, List<StreamApp> apps) {
        appRow.removeAllViews();
        lastFocusedTile = null;
        int restoredScroll = host != null && host.uuid.equals(appScrollHostUuid) ? appScrollPosition : 0;
        appScrollHostUuid = host != null ? host.uuid : null;
        appScroll.post(() -> appScroll.scrollTo(restoredScroll, 0));
        if (host == null) {
            clearArtworkBackdrop();
            appsLabel.setText("APPS");
            appRow.addView(text("Choose a streaming host to see its applications.", 16, 0xFFBDC4D8, false),
                    new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        if (apps.isEmpty()) {
            clearArtworkBackdrop();
            appRow.addView(text("No cached applications found. Refresh this host once in Moonlight X.", 16, 0xFFFFB74D, false),
                    new LinearLayout.LayoutParams(dp(720), ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        for (StreamApp app : apps) {
            appRow.addView(appCard(host, app), cardSpacing());
        }
    }

    private View appCard(Host host, StreamApp app) {
        LinearLayout card = cardBase(dp(300), dp(110));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        ImageView poster = new ImageView(this);
        // Moonlight posters use a 2:3 portrait ratio (normally 600x900). Keep the
        // complete cover visible instead of cropping its top and bottom into the
        // old, almost-square thumbnail.
        poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable placeholder = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF302255, 0xFF142A46});
        placeholder.setCornerRadius(dp(9));
        poster.setBackground(placeholder);
        card.addView(poster, new LinearLayout.LayoutParams(dp(56), dp(84)));
        loadPosterAsync(app.posterUri, poster);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(app.name, 16, Color.WHITE, true);
        name.setSingleLine(true);
        copy.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        long playedAt = appPlayedAt(host, app);
        String metadataLabel = playedAt > 0
                ? "LAST PLAYED · " + formatRelative(Math.max(0L,
                System.currentTimeMillis() - playedAt)).toUpperCase(Locale.ROOT)
                : "READY";
        TextView metadata = text(metadataLabel, 10, 0xFFAAAFC2, true);
        LinearLayout.LayoutParams metadataParams = wrap();
        metadataParams.topMargin = dp(5);
        copy.addView(metadata, metadataParams);
        TextView action = text("PLAY  ›", 12, 0xFFB99CFF, true);
        action.setAlpha(0f);
        LinearLayout.LayoutParams actionParams = wrap();
        actionParams.topMargin = dp(5);
        copy.addView(action, actionParams);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        copyParams.leftMargin = dp(14);
        card.addView(copy, copyParams);
        card.setOnClickListener(v -> beginAppLaunch(host, app));
        card.setOnFocusChangeListener((v, focused) -> {
            styleCard(card, focused);
            action.animate().cancel();
            if (reducedMotion) {
                action.setAlpha(focused ? 1f : 0f);
            } else {
                action.animate().alpha(focused ? 1f : 0f).setDuration(120).start();
            }
            if (focused) {
                noteTileFocus(card, true);
                showArtworkBackdrop(app.posterUri, poster);
            }
        });
        return card;
    }

    private void noteTileFocus(View tile, boolean focused) {
        if (!focused) return;
        if (lastFocusedTile != null && lastFocusedTile != tile) playTileFocusSound();
        lastFocusedTile = tile;
    }

    private void playTileFocusSound() {
        playUiSound(AudioManager.FX_FOCUS_NAVIGATION_RIGHT, 0.28f);
    }

    private void playUiSound(int effect, float volume) {
        if (!uiSoundsEnabled) return;
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) audioManager.playSoundEffect(effect, volume);
    }

    private void loadPosterAsync(Uri uri, ImageView target) {
        if (uri == null) return;
        int dashboardToken = dashboardRequests.currentToken();
        executor.execute(() -> {
            try {
                Bitmap bitmap = decodePoster(uri, 512);
                if (bitmap != null) {
                    mainHandler.post(() -> {
                        if (isCurrentDashboardRequest(dashboardToken) &&
                                target.isAttachedToWindow()) {
                            target.setImageBitmap(bitmap);
                        } else if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    });
                }
            } catch (Exception ignored) { }
        });
    }

    private Bitmap decodePoster(Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, bounds);
        }
        int sample = 1;
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private void showArtworkBackdrop(Uri uri, ImageView poster) {
        if (uri == null || artworkBackdrop == null) {
            clearArtworkBackdrop();
            return;
        }
        int dashboardToken = dashboardRequests.currentToken();
        if (!isCurrentDashboardRequest(dashboardToken)) return;
        int request = backdropRequest.incrementAndGet();
        showArtworkPreview(request, poster != null ? poster.getDrawable() : null);
        // Ignore only extremely short focus fly-overs. The visible preview above
        // starts immediately, while the blurred layer is prepared off the UI thread.
        mainHandler.postDelayed(() -> {
            if (request != backdropRequest.get() ||
                    !isCurrentDashboardRequest(dashboardToken)) return;
            executor.execute(() -> loadArtworkBackdrop(dashboardToken, request, uri));
        }, 35);
    }

    private void showArtworkPreview(int request, Drawable source) {
        if (source == null || request != backdropRequest.get()) return;
        // The tile drawable is an immediate preview for the hero only. Stretching
        // the small poster over the whole screen made it look blurred, then the
        // later blurred backdrop replacement appeared as a second layout jump.
        setArtworkPreview(artworkHero, cloneDrawable(source), 0.58f);
        artworkScrim.animate().cancel();
        artworkScrim.animate().alpha(1f).setDuration(180).start();
    }

    private void setArtworkPreview(ImageView target, Drawable next, float alpha) {
        if (next == null) return;
        target.animate().cancel();
        Drawable current = target.getDrawable();
        if (current == null || reducedMotion) {
            target.setImageDrawable(next);
        } else {
            TransitionDrawable transition = new TransitionDrawable(new Drawable[]{current, next});
            transition.setCrossFadeEnabled(true);
            target.setImageDrawable(transition);
            transition.startTransition(180);
        }
        target.setAlpha(alpha);
    }

    private Drawable cloneDrawable(Drawable drawable) {
        Drawable.ConstantState state = drawable != null ? drawable.getConstantState() : null;
        return state != null ? state.newDrawable(getResources()).mutate() : drawable;
    }

    private void loadArtworkBackdrop(int dashboardToken, int request, Uri uri) {
        ArtworkBackdropResult result = null;
        Bitmap hero = null;
        try {
            hero = decodePoster(uri, 900);
            if (hero != null) result = new ArtworkBackdropResult(blurForBackdrop(hero), hero);
        } catch (Exception error) {
            if (hero != null && !hero.isRecycled()) hero.recycle();
            Log.w(TAG, "Unable to load artwork backdrop", error);
        }
        ArtworkBackdropResult loaded = result;
        mainHandler.post(() -> applyArtworkBackdrop(dashboardToken, request, loaded));
    }

    private void refreshCommunityAvailability(Host host) {
        int request = communityAvailabilityRequest.incrementAndGet();
        updateCommunityButtonVisibility(false);
        if (host == null || host.uuid == null) return;
        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) return;
        executor.submit(() -> {
            boolean available = false;
            try {
                available = hostGatewayClient.getCapabilities(connection).discord;
            } catch (IOException error) {
                Log.w(TAG, "Unable to check Community availability for " + host.uuid, error);
            }
            boolean show = available;
            mainHandler.post(() -> {
                if (request != communityAvailabilityRequest.get() ||
                        selectedHost == null || !host.uuid.equals(selectedHost.uuid) ||
                        !dashboardActive) return;
                updateCommunityButtonVisibility(show);
            });
        });
    }

    private void updateCommunityButtonVisibility(boolean visible) {
        if (communityButton == null || settingsButton == null ||
                resumeButton == null || sessionButton == null) return;
        communityButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            communityButton.setNextFocusRightId(settingsButton.getId());
            communityButton.setNextFocusDownId(resumeButton.getId());
            settingsButton.setNextFocusLeftId(communityButton.getId());
            settingsButton.setNextFocusDownId(sessionButton.getId());
            resumeButton.setNextFocusUpId(communityButton.getId());
            sessionButton.setNextFocusUpId(settingsButton.getId());
        } else {
            settingsButton.setNextFocusLeftId(resumeButton.getId());
            settingsButton.setNextFocusDownId(resumeButton.getId());
            resumeButton.setNextFocusUpId(settingsButton.getId());
            sessionButton.setNextFocusUpId(settingsButton.getId());
        }
    }

    private void showCommunityForSelectedHost() {
        Host host = selectedHost;
        if (host == null || host.uuid == null) {
            Toast.makeText(this, "Choose a streaming host first.", Toast.LENGTH_LONG).show();
            return;
        }
        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) {
            Toast.makeText(this, "Pair this host gateway first.", Toast.LENGTH_LONG).show();
            return;
        }
        showDiscordPanel(host, connection, () -> hideSidePanel(true));
    }

    private void applyArtworkBackdrop(int dashboardToken, int request,
                                      ArtworkBackdropResult result) {
        if (request != backdropRequest.get() ||
                !isCurrentDashboardRequest(dashboardToken)) {
            if (result != null) result.recycle();
            return;
        }
        if (result == null) {
            clearArtworkBackdrop();
            return;
        }

        artworkBackdrop.animate().cancel();
        artworkHero.animate().cancel();
        artworkScrim.animate().cancel();

        Drawable oldBackdropDrawable = artworkBackdrop.getDrawable();
        Drawable oldHeroDrawable = artworkHero.getDrawable();
        Bitmap oldBackdrop = currentBackdropBitmap;
        Bitmap oldHero = currentHeroBitmap;
        currentBackdropBitmap = result.backdrop;
        currentHeroBitmap = result.hero;
        if (oldBackdrop != null) retiredArtworkBitmaps.add(oldBackdrop);
        if (oldHero != null) retiredArtworkBitmaps.add(oldHero);

        glassAccentColor = sampleArtworkAccent(result.hero);
        refreshGlassStyles();

        if (oldBackdropDrawable == null || oldHeroDrawable == null || reducedMotion) {
            artworkBackdrop.setImageBitmap(currentBackdropBitmap);
            artworkHero.setImageBitmap(currentHeroBitmap);
            artworkBackdrop.setAlpha(0.28f);
            artworkHero.setAlpha(0.58f);
            artworkHero.setScaleX(1f);
            artworkHero.setScaleY(1f);
            artworkScrim.setAlpha(1f);
            recycleRetiredArtwork();
            return;
        }

        TransitionDrawable backdropTransition = new TransitionDrawable(new android.graphics.drawable.Drawable[]{
                oldBackdropDrawable,
                new BitmapDrawable(getResources(), currentBackdropBitmap)});
        backdropTransition.setCrossFadeEnabled(true);
        artworkBackdrop.setImageDrawable(backdropTransition);
        // Preview and final hero represent the same poster. Crossfading the two
        // resolutions produces a briefly doubled/soft image, so replace it in
        // the existing FIT_CENTER geometry without applying a scale animation.
        artworkHero.setImageBitmap(currentHeroBitmap);
        artworkBackdrop.setAlpha(0.28f);
        artworkHero.setAlpha(0.58f);
        artworkHero.setScaleX(1f);
        artworkHero.setScaleY(1f);
        backdropTransition.startTransition(420);
        artworkScrim.animate().alpha(1f).setDuration(260).start();
        mainHandler.postDelayed(() -> {
            if (request != backdropRequest.get() || isFinishing() || isDestroyed()) return;
            artworkBackdrop.setImageBitmap(currentBackdropBitmap);
            artworkHero.setImageBitmap(currentHeroBitmap);
            recycleRetiredArtwork();
        }, 500);
    }

    private void clearArtworkBackdrop() {
        int request = backdropRequest.incrementAndGet();
        if (artworkBackdrop == null) return;
        artworkBackdrop.animate().cancel();
        artworkHero.animate().cancel();
        artworkScrim.animate().cancel();
        artworkBackdrop.animate().alpha(0f).setDuration(300).start();
        artworkScrim.animate().alpha(0f).setDuration(300).start();
        artworkHero.animate().alpha(0f).setDuration(300).withEndAction(() -> {
            if (request != backdropRequest.get()) return;
            artworkBackdrop.setImageDrawable(null);
            artworkHero.setImageDrawable(null);
            recycleCurrentArtwork();
        }).start();
    }

    private void recycleCurrentArtwork() {
        if (currentBackdropBitmap != null && !currentBackdropBitmap.isRecycled()) currentBackdropBitmap.recycle();
        if (currentHeroBitmap != null && !currentHeroBitmap.isRecycled()) currentHeroBitmap.recycle();
        currentBackdropBitmap = null;
        currentHeroBitmap = null;
        recycleRetiredArtwork();
    }

    private void recycleRetiredArtwork() {
        for (Bitmap bitmap : retiredArtworkBitmaps) {
            if (bitmap != null && bitmap != currentBackdropBitmap && bitmap != currentHeroBitmap && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        retiredArtworkBitmaps.clear();
    }

    private static int sampleArtworkAccent(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return 0xFF66549A;
        long red = 0, green = 0, blue = 0, count = 0;
        int stepX = Math.max(1, bitmap.getWidth() / 20);
        int stepY = Math.max(1, bitmap.getHeight() / 20);
        for (int y = stepY / 2; y < bitmap.getHeight(); y += stepY) {
            for (int x = stepX / 2; x < bitmap.getWidth(); x += stepX) {
                int color = bitmap.getPixel(x, y);
                int brightness = Color.red(color) + Color.green(color) + Color.blue(color);
                if (brightness < 90 || brightness > 690) continue;
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
                count++;
            }
        }
        if (count == 0) return 0xFF66549A;
        int sampled = Color.rgb((int)(red / count), (int)(green / count), (int)(blue / count));
        return blendColor(0xFF66549A, sampled, 0.42f);
    }

    private static Bitmap blurForBackdrop(Bitmap source) {
        int width = 180;
        int height = Math.max(180, Math.min(320,
                Math.round(source.getHeight() * (width / (float) Math.max(1, source.getWidth())))));
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        if (small == source) small = source.copy(Bitmap.Config.ARGB_8888, true);

        int[] input = new int[width * height];
        int[] output = new int[input.length];
        small.getPixels(input, 0, width, 0, 0, width, height);
        int radius = 2;
        for (int pass = 0; pass < 1; pass++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = 0, red = 0, green = 0, blue = 0, count = 0;
                    for (int ky = Math.max(0, y - radius); ky <= Math.min(height - 1, y + radius); ky++) {
                        int row = ky * width;
                        for (int kx = Math.max(0, x - radius); kx <= Math.min(width - 1, x + radius); kx++) {
                            int color = input[row + kx];
                            alpha += Color.alpha(color);
                            red += Color.red(color);
                            green += Color.green(color);
                            blue += Color.blue(color);
                            count++;
                        }
                    }
                    output[y * width + x] = Color.argb(alpha / count, red / count, green / count, blue / count);
                }
            }
            int[] swap = input;
            input = output;
            output = swap;
        }
        small.setPixels(input, 0, width, 0, 0, width, height);
        return small;
    }

    private void beginAppLaunch(Host host, StreamApp app) {
        launchCancelled.set(false);
        maybePrepareDiscordForStream(host);
        homeLayer.setVisibility(View.GONE);
        loadingLayer.setVisibility(View.VISIBLE);
        loadingTitle.setText(app.name);
        setLoadingStatus("Preparing wake sequence...");
        lastLoadingMessageIndex = -1;
        if (!reducedMotion) rotateLoadingMessage();
        else loadingMessage.setText("Preparing your game...");
        slideshow.start(!reducedMotion);
        executor.execute(() -> wakeAndWait(host, app));
    }

    private void rotateLoadingMessage() {
        if (loadingLayer.getVisibility() != View.VISIBLE) return;
        loadingMessage.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            int nextIndex;
            do {
                nextIndex = loadingMessageRandom.nextInt(loadingMessages.length);
            } while (loadingMessages.length > 1 && nextIndex == lastLoadingMessageIndex);
            lastLoadingMessageIndex = nextIndex;
            loadingMessage.setText(loadingMessages[nextIndex]);
            loadingMessage.animate().alpha(1f).setDuration(260).start();
        }).start();
        mainHandler.postDelayed(this::rotateLoadingMessage, 2100);
    }

    private void wakeAndWait(Host host, StreamApp app) {
        long deadline = System.currentTimeMillis() + HOST_TIMEOUT_MS;
        long nextWake = 0;
        setLoadingStatus("Checking " + host.address + " for a running streaming host...");
        while (!launchCancelled.get() && System.currentTimeMillis() < deadline) {
            int readyPort = findReadyPort(host);
            if (readyPort > 0) {
                setLoadingStatus("Host responded on port " + readyPort + ". Starting Moonlight...");
                mainHandler.postDelayed(() -> openMoonlight(host, app), 350);
                return;
            }
            if (System.currentTimeMillis() >= nextWake) {
                setLoadingStatus("Sending Wake-on-LAN packet...");
                sendWakeOnLan(host.macAddress);
                nextWake = System.currentTimeMillis() + 5000;
            } else {
                setLoadingStatus("Waiting for Vibepollo or Sunshine at " + host.address + "...");
            }
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { return; }
        }
        if (!launchCancelled.get()) {
            mainHandler.post(() -> {
                loadingMessage.setText("The host did not become ready. Press BACK and try again.");
                loadingMessage.setTextColor(0xFFFF8A80);
                loadingStatus.setText("No compatible host service responded within 90 seconds.");
                loadingStatus.setTextColor(0xFFFFB4AB);
            });
        }
    }

    private int findReadyPort(Host host) {
        if (host.address == null || host.address.isEmpty()) return -1;
        int[] ports = {host.port > 0 ? host.port : 47989, 47984, 47989};
        for (int port : ports) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host.address, port), 650);
                return port;
            } catch (IOException ignored) {}
        }
        return -1;
    }

    private void setLoadingStatus(String status) {
        Runnable update = () -> {
            if (loadingStatus == null || loadingLayer.getVisibility() != View.VISIBLE) return;
            loadingStatus.setTextColor(0xFFB8C0D9);
            loadingStatus.setText(status);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) update.run();
        else mainHandler.post(update);
    }

    private void openMoonlight(Host host, StreamApp app) {
        if (launchCancelled.get()) return;
        setLoadingStatus("Opening Moonlight for " + host.name + "...");
        slideshow.stop();
        Intent intent = new Intent(ACTION_STREAM);
        intent.setPackage(host.moonlightPackage);
        intent.putExtra(EXTRA_HOST_UUID, host.uuid);
        intent.putExtra(EXTRA_APP_ID, String.valueOf(app.appId));
        intent.putExtra(EXTRA_APP_NAME, app.name);
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND, true);
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND_PACKAGE, getPackageName());
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND_MESSAGE, loadingMessage.getText().toString());
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND_ANIMATION_EPOCH, slideshow.getStartedAt());
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND_REDUCED_MOTION, reducedMotion);
        putHostGatewayExtras(intent, host);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            rememberLastLaunch(host, app);
            // Moonlight X takes over with a matching loader. Avoid exposing an
            // intermediate frame or cross-fading the two loading surfaces.
            overridePendingTransition(0, 0);
        } catch (Exception error) {
            loadingMessage.setText("Compatible Moonlight X is not installed or does not accept the public launch intent.");
            loadingMessage.setTextColor(0xFFFF8A80);
            loadingStatus.setText("The public Moonlight launch intent was rejected.");
            loadingStatus.setTextColor(0xFFFFB4AB);
        }
    }

    private List<Host> loadHosts() {
        ContentResolver resolver = getContentResolver();
        for (MoonlightTarget target : MOONLIGHT_TARGETS) {
            Uri uri = target.hostsUri;
            try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
                if (cursor == null) continue;
                List<Host> hosts = new ArrayList<>();
                while (cursor.moveToNext()) {
                    Host host = new Host();
                    host.moonlightPackage = target.packageName;
                    host.uuid = value(cursor, "uuid");
                    host.name = value(cursor, "name");
                    String local = value(cursor, "local_address");
                    String manual = value(cursor, "manual_address");
                    host.address = local != null && !local.isEmpty() ? local : manual;
                    host.port = integer(cursor, local != null && !local.isEmpty() ? "local_port" : "manual_port", 47989);
                    host.macAddress = value(cursor, "mac_address");
                    if (host.uuid != null && host.name != null) hosts.add(host);
                }
                String lastHostUuid = history().getString(PREF_LAST_HOST_UUID, null);
                Collections.sort(hosts, (left, right) -> {
                    int leftPriority = left.uuid.equals(lastHostUuid) ? 0 : 1;
                    int rightPriority = right.uuid.equals(lastHostUuid) ? 0 : 1;
                    int byPriority = Integer.compare(leftPriority, rightPriority);
                    return byPriority != 0 ? byPriority :
                            left.name.compareToIgnoreCase(right.name);
                });
                Log.i(TAG, "Loaded " + hosts.size() + " host(s) from " + uri);
                if (!hosts.isEmpty()) return hosts;
            } catch (RuntimeException error) {
                Log.e(TAG, "Unable to query saved hosts from " + uri, error);
            }
        }
        return Collections.emptyList();
    }

    private List<StreamApp> loadApps(Host host) {
        Uri uri = Uri.parse("content://apps." + host.moonlightPackage + "/apps/" + Uri.encode(host.uuid));
        List<StreamApp> apps = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null) return apps;
            while (cursor.moveToNext()) {
                StreamApp app = new StreamApp();
                app.appId = integer(cursor, "app_id", -1);
                app.name = value(cursor, "name");
                String poster = value(cursor, "poster_uri");
                app.posterUri = poster != null ? Uri.parse(poster) : null;
                if (app.appId >= 0 && app.name != null) apps.add(app);
            }
            Log.i(TAG, "Loaded " + apps.size() + " app(s) from " + uri);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to query cached apps from " + uri, error);
        }
        Collections.sort(apps, (left, right) -> {
            int byRecent = Long.compare(appPlayedAt(host, right), appPlayedAt(host, left));
            return byRecent != 0 ? byRecent :
                    left.name.compareToIgnoreCase(right.name);
        });
        return apps;
    }

    private void resolveLastLaunch(List<Host> hosts) {
        lastLaunchHost = null;
        lastLaunchApp = null;
        updateResumeAction();

        SharedPreferences history = history();
        String hostUuid = history.getString(PREF_LAST_HOST_UUID, null);
        int appId = history.getInt(PREF_LAST_APP_ID, -1);
        String appName = history.getString(PREF_LAST_APP_NAME, null);
        if (hostUuid == null || appId < 0 || appName == null) return;

        for (Host host : hosts) {
            if (!hostUuid.equals(host.uuid)) continue;
            StreamApp app = new StreamApp();
            app.appId = appId;
            app.name = appName;
            lastLaunchHost = host;
            lastLaunchApp = app;
            updateResumeAction();
            return;
        }
    }

    private void rememberLastLaunch(Host host, StreamApp app) {
        long now = System.currentTimeMillis();
        history().edit()
                .putString(PREF_LAST_HOST_UUID, host.uuid)
                .putInt(PREF_LAST_APP_ID, app.appId)
                .putString(PREF_LAST_APP_NAME, app.name)
                .putLong(PREF_LAST_LAUNCH_AT, now)
                .putLong(appHistoryKey(host.uuid, app.appId), now)
                .putLong(hostHistoryKey(host.uuid), now)
                .apply();
        lastLaunchHost = host;
        lastLaunchApp = app;
        updateResumeAction();
    }

    private void updateResumeAction() {
        if (resumeButton == null) return;
        if (isActiveStream(currentStreamStatus)) {
            String app = currentStreamStatus.app != null && !currentStreamStatus.app.isEmpty()
                    ? currentStreamStatus.app : "Active stream";
            resumeTitle.setText("▶  RETURN TO GAME");
            resumeSubtitle.setText(app);
            resumeButton.setContentDescription("Return to game, " + app);
            resumeButton.setVisibility(View.VISIBLE);
            if (sessionButton != null) {
                sessionTitle.setText("SESSION");
                sessionButton.setContentDescription("Active session controls");
                sessionButton.setVisibility(View.VISIBLE);
            }
        } else if (lastLaunchHost != null && lastLaunchApp != null) {
            resumeTitle.setText("▶  RESUME LAST");
            resumeSubtitle.setText(lastLaunchApp.name);
            resumeButton.setContentDescription("Resume last game, " + lastLaunchApp.name);
            resumeButton.setVisibility(View.VISIBLE);
            if (sessionButton != null) sessionButton.setVisibility(View.GONE);
        } else {
            resumeButton.setVisibility(View.GONE);
            if (sessionButton != null) sessionButton.setVisibility(View.GONE);
        }
        focusResumeActionIfPending();
    }

    private void focusResumeActionIfPending() {
        if (userNavigationStarted || SystemClock.uptimeMillis() > initialFocusDeadline) {
            initialFocusPending = false;
            return;
        }
        if (!initialFocusPending || resumeButton == null || resumeButton.getVisibility() != View.VISIBLE) return;
        resumeButton.post(() -> {
            if (userNavigationStarted || SystemClock.uptimeMillis() > initialFocusDeadline) {
                initialFocusPending = false;
                return;
            }
            if (!initialFocusPending || resumeButton.getVisibility() != View.VISIBLE || !resumeButton.isShown()) return;
            if (modalLayer != null && modalLayer.getVisibility() == View.VISIBLE) return;
            if (!getWindow().getDecorView().hasWindowFocus()) return;
            if (resumeButton.requestFocus()) initialFocusPending = false;
        });
    }

    private void finishInitialFocus() {
        if (!initialFocusPending || isFinishing() || isDestroyed()) return;
        if (userNavigationStarted || SystemClock.uptimeMillis() > initialFocusDeadline) {
            initialFocusPending = false;
            return;
        }
        if (!getWindow().getDecorView().hasWindowFocus()) {
            mainHandler.postDelayed(this::finishInitialFocus, 75);
            return;
        }
        if (!sessionStatusLoaded) {
            mainHandler.postDelayed(this::finishInitialFocus, 75);
            return;
        }
        if (resumeButton != null && resumeButton.getVisibility() == View.VISIBLE && resumeButton.isShown()) {
            if (resumeButton.requestFocus()) initialFocusPending = false;
            return;
        }
        if (selectedHostCard != null && selectedHostCard.isShown()) {
            selectedHostCard.requestFocus();
        }
        initialFocusPending = false;
    }

    private static boolean isActiveStream(StreamStatus status) {
        return status != null && status.activityAlive && isActiveState(status.state);
    }

    private static boolean isActiveState(String state) {
        return "streaming".equals(state) || "connecting".equals(state) || "reconnecting".equals(state);
    }

    private void returnToActiveStream(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        Intent intent = new Intent(ACTION_RETURN_STREAM);
        intent.setPackage(packageName);
        // A surviving Moonlight transport may recreate its Activity without the
        // original launch extras (for example after a package update). Re-attach
        // the frontend contract on every return so BACK still hands control to
        // Wake & Play instead of finishing the stream.
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND, true);
        intent.putExtra(EXTRA_EXTERNAL_FRONTEND_PACKAGE, getPackageName());
        Host integrationHost = null;
        for (Host host : visibleHosts) {
            if (isSameHost(currentStreamStatus, host)) {
                integrationHost = host;
                break;
            }
        }
        if (integrationHost == null) integrationHost = selectedHost;
        putHostGatewayExtras(intent, integrationHost);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            overridePendingTransition(0, 0);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to return to active stream", error);
            Toast.makeText(this, "The active stream is no longer available.", Toast.LENGTH_LONG).show();
            refreshSessionStatusAsync();
        }
    }

    private void buildSidePanelLayer() {
        modalLayer = new FrameLayout(this);
        modalLayer.setVisibility(View.GONE);
        modalLayer.setFocusable(false);

        View dim = new View(this);
        dim.setBackgroundColor(0xA005060A);
        dim.setClickable(true);
        dim.setOnClickListener(v -> hideSidePanel(true));
        modalLayer.addView(dim, match());

        sidePanelScroll = new ScrollView(this);
        sidePanelScroll.setFillViewport(true);
        sidePanelScroll.setVerticalScrollBarEnabled(false);
        sidePanelScroll.setFadingEdgeLength(0);

        sidePanel = new LinearLayout(this);
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanel.setPadding(dp(34), dp(26), dp(34), dp(20));
        GradientDrawable panelBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xF51A1D2A, 0xF0221B38, 0xFA090B12});
        panelBackground.setCornerRadii(new float[]{dp(24), dp(24), 0, 0, 0, 0, dp(24), dp(24)});
        panelBackground.setStroke(dp(1), 0x707B6AA9);
        sidePanelScroll.setBackground(panelBackground);
        sidePanelScroll.setElevation(dp(18));
        sidePanelScroll.addView(sidePanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(510), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        modalLayer.addView(sidePanelScroll, panelParams);
        homeLayer.addView(modalLayer, match());
    }

    private TextView panelAction(String label) {
        TextView action = text(label, 14, 0xFFF0E9FF, true);
        action.setFocusable(true);
        action.setClickable(true);
        action.setSoundEffectsEnabled(false);
        action.setMinHeight(dp(44));
        action.setPadding(dp(16), dp(7), dp(16), dp(7));
        action.setOnFocusChangeListener((v, focused) -> styleCompactButton(action, focused));
        styleCompactButton(action, false);
        return action;
    }

    private TextView discordAction(String label, int color) {
        TextView action = text(label, 14, Color.WHITE, true);
        action.setTag("discord_action");
        action.setFocusable(true);
        action.setClickable(true);
        action.setSoundEffectsEnabled(uiSoundsEnabled);
        action.setMinHeight(dp(48));
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(16), dp(8), dp(16), dp(8));
        action.setBackground(discordActionBackground(color));
        action.setOnFocusChangeListener((view, focused) -> {
            action.setTextColor(focused ? Color.WHITE : 0xFFE3E5E8);
            action.setElevation(dp(focused ? 8 : 2));
        });
        return action;
    }

    private Drawable discordActionBackground(int color) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                discordActionShape(blendColor(color, Color.WHITE, 0.18f), true));
        states.addState(new int[]{android.R.attr.state_focused},
                discordActionShape(color, true));
        states.addState(new int[0],
                discordActionShape(blendColor(color, 0xFF111214, 0.30f), false));
        return states;
    }

    private Drawable discordActionShape(int color, boolean emphasized) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{withAlpha(color, 0xF2), withAlpha(blendColor(color, 0xFF111214, 0.20f), 0xF2)});
        background.setCornerRadius(dp(9));
        background.setStroke(dp(emphasized ? 2 : 1),
                emphasized ? 0xFFFFFFFF : 0x384F545C);
        return background;
    }

    private TextView discordSection(String label) {
        TextView section = text(label, 11, 0xFFB5BAC1, true);
        section.setLetterSpacing(0.08f);
        section.setPadding(dp(4), dp(13), dp(4), dp(5));
        section.setFocusable(false);
        return section;
    }

    private void pulseDiscordAction(View action) {
        if (action == null) return;
        action.animate().cancel();
        if (reducedMotion) {
            action.setAlpha(0.72f);
            action.postDelayed(() -> action.setAlpha(1f), 100);
        } else {
            action.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.78f)
                    .setDuration(70).withEndAction(() -> action.animate()
                            .scaleX(1f).scaleY(1f).alpha(1f).setDuration(110).start()).start();
        }
    }

    private void putHostGatewayExtras(Intent intent, Host host) {
        if (intent == null || host == null || hostGatewayStore == null) return;
        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) return;
        intent.putExtra(EXTRA_HOST_GATEWAY_ENDPOINT, connection.endpoint);
        intent.putExtra(EXTRA_HOST_GATEWAY_TOKEN, connection.token);
        intent.putExtra(EXTRA_HOST_GATEWAY_CERTIFICATE, connection.certificateSha256);
        intent.putExtra(EXTRA_DISCORD_PROFILE_ID, connection.profileId);
    }

    private LinearLayout panelActionRow(View... actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int index = 0; index < actions.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index > 0) params.leftMargin = dp(8);
            row.addView(actions[index], params);
        }
        return row;
    }

    private LinearLayout panelWeightedActionRow(View main, View secondary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(main, new LinearLayout.LayoutParams(
                0, dp(44), 2f));
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        secondaryParams.leftMargin = dp(8);
        row.addView(secondary, secondaryParams);
        return row;
    }

    private SeekBar panelVolumeSlider(int progress) {
        SeekBar slider = new SeekBar(this);
        slider.setFocusable(true);
        slider.setMax(200);
        slider.setKeyProgressIncrement(10);
        slider.setProgress(Math.max(0, Math.min(200,
                Math.round(progress / 10f) * 10)));
        slider.setPadding(dp(14), dp(8), dp(14), dp(8));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF8D7AD1));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFE9E3FF));
        return slider;
    }

    private void showSidePanel(String eyebrow, String title, String details, View... actions) {
        showSidePanel(eyebrow, title, details, (Runnable) null, actions);
    }

    private void showSidePanel(String eyebrow, String title, String details,
                               Runnable backAction, View... actions) {
        if (modalLayer == null) return;
        boolean wasVisible = modalLayer.getVisibility() == View.VISIBLE;
        View previousFocus = getCurrentFocus();
        String previousFocusKey = wasVisible ? sidePanelFocusKey(previousFocus) : null;
        int previousScrollY = wasVisible ? sidePanelScroll.getScrollY() : 0;
        if (!wasVisible) lastContentFocus = previousFocus;
        sidePanelBackAction = backAction;
        sidePanel.removeAllViews();

        TextView eyebrowView = text(eyebrow, 12, 0xFFAFA4C9, true);
        sidePanel.addView(eyebrowView, wrap());
        TextView titleView = text(title, 29, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = wrap();
        titleParams.topMargin = dp(10);
        sidePanel.addView(titleView, titleParams);
        if (details != null && !details.isEmpty()) {
            TextView detailsView = text(details, 14, 0xFFC1C5D6, false);
            detailsView.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailsParams.topMargin = dp(10);
            detailsParams.bottomMargin = dp(16);
            sidePanel.addView(detailsView, detailsParams);
        }

        for (View action : actions) {
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            actionParams.bottomMargin = dp(6);
            sidePanel.addView(action, actionParams);
        }

        TextView hint = text(backAction != null ? "BACK  ·  PREVIOUS" : "BACK  ·  CLOSE",
                11, 0x8FFFFFFF, true);
        LinearLayout.LayoutParams hintParams = wrap();
        hintParams.topMargin = dp(12);
        sidePanel.addView(hint, hintParams);

        rebuildSidePanelFocusNavigation();
        View firstAction = firstVisibleSidePanelAction();
        View restoredAction = previousFocusKey == null
                ? null : findSidePanelAction(previousFocusKey);

        modalLayer.setVisibility(View.VISIBLE);
        modalLayer.setAlpha(1f);
        sidePanelScroll.animate().cancel();
        sidePanelScroll.scrollTo(0, wasVisible && restoredAction != null ? previousScrollY : 0);
        if (reducedMotion || wasVisible) {
            sidePanelScroll.setTranslationX(0f);
        } else {
            sidePanelScroll.setTranslationX(dp(510));
            sidePanelScroll.animate().translationX(0f).setDuration(180).start();
        }
        View focusTarget = restoredAction != null ? restoredAction : firstAction;
        if (focusTarget != null) focusTarget.post(focusTarget::requestFocus);
    }

    private String sidePanelFocusKey(View view) {
        if (view == null || !isDescendantOf(view, sidePanel)) return null;
        CharSequence description = view.getContentDescription();
        if (description != null && description.length() > 0) return description.toString();
        if (view instanceof TextView) {
            CharSequence label = ((TextView) view).getText();
            if (label != null && label.length() > 0) return label.toString();
        }
        return null;
    }

    private boolean isDescendantOf(View view, ViewGroup ancestor) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private View findSidePanelAction(String focusKey) {
        for (List<View> row : sidePanelFocusRows()) {
            for (View action : row) {
                if (focusKey.equals(sidePanelFocusKey(action))) return action;
            }
        }
        return null;
    }

    private View firstVisibleSidePanelAction() {
        for (List<View> row : sidePanelFocusRows()) {
            if (!row.isEmpty()) return row.get(0);
        }
        return null;
    }

    private void rebuildSidePanelFocusNavigation() {
        List<List<View>> rows = sidePanelFocusRows();
        for (List<View> row : rows) {
            for (View action : row) {
                if (action.getId() == View.NO_ID) action.setId(View.generateViewId());
            }
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<View> row = rows.get(rowIndex);
            for (int column = 0; column < row.size(); column++) {
                View action = row.get(column);
                List<View> previousRow = rows.get(Math.max(0, rowIndex - 1));
                List<View> nextRow = rows.get(Math.min(rows.size() - 1, rowIndex + 1));
                View previous = previousRow.get(Math.min(column, previousRow.size() - 1));
                View next = nextRow.get(Math.min(column, nextRow.size() - 1));
                View left = row.get(Math.max(0, column - 1));
                View right = row.get(Math.min(row.size() - 1, column + 1));
                action.setNextFocusUpId(previous.getId());
                action.setNextFocusDownId(next.getId());
                action.setNextFocusLeftId(left.getId());
                action.setNextFocusRightId(right.getId());
            }
        }
    }

    private List<List<View>> sidePanelFocusRows() {
        List<List<View>> rows = new ArrayList<>();
        if (sidePanel == null) return rows;
        for (int index = 0; index < sidePanel.getChildCount(); index++) {
            View child = sidePanel.getChildAt(index);
            if (child.getVisibility() != View.VISIBLE) continue;
            List<View> row = new ArrayList<>();
            collectFocusableViews(child, row);
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    private static void collectFocusableViews(View view, List<View> result) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view.isFocusable()) result.add(view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectFocusableViews(group.getChildAt(index), result);
        }
    }

    private void hideSidePanel(boolean restoreFocus) {
        if (modalLayer == null || modalLayer.getVisibility() != View.VISIBLE) return;
        Runnable finish = () -> {
            modalLayer.setVisibility(View.GONE);
            sidePanelScroll.setTranslationX(0f);
            sidePanelBackAction = null;
            communityExitAction = null;
            clearDiscordControllerShortcuts();
            if (restoreFocus && lastContentFocus != null && lastContentFocus.isShown()) {
                lastContentFocus.requestFocus();
            }
        };
        sidePanelScroll.animate().cancel();
        if (reducedMotion) {
            finish.run();
        } else {
            sidePanelScroll.animate().translationX(dp(510)).setDuration(150).withEndAction(finish).start();
        }
    }

    private void dismissSidePanelImmediately() {
        sidePanelBackAction = null;
        communityExitAction = null;
        clearDiscordControllerShortcuts();
        if (sidePanelScroll != null) {
            sidePanelScroll.animate().cancel();
            sidePanelScroll.setTranslationX(0f);
        }
        if (modalLayer != null) modalLayer.setVisibility(View.GONE);
    }

    private void showOptionsPanel() {
        clearDiscordControllerShortcuts();
        TextView soundAction = panelAction("UI SOUNDS  ·  " + (uiSoundsEnabled ? "ON" : "OFF"));
        TextView motionAction = panelAction("REDUCED MOTION  ·  " + (reducedMotion ? "ON" : "OFF"));
        TextView integrationsAction = panelAction("HOST INTEGRATIONS  ›");
        TextView moonlightAction = panelAction("MOONLIGHT SETTINGS  ›");
        soundAction.setOnClickListener(v -> {
            uiSoundsEnabled = !uiSoundsEnabled;
            history().edit().putBoolean(PREF_UI_SOUNDS, uiSoundsEnabled).apply();
            soundAction.setText("UI SOUNDS  ·  " + (uiSoundsEnabled ? "ON" : "OFF"));
        });
        motionAction.setOnClickListener(v -> {
            reducedMotion = !reducedMotion;
            history().edit().putBoolean(PREF_REDUCED_MOTION, reducedMotion).apply();
            motionAction.setText("REDUCED MOTION  ·  " + (reducedMotion ? "ON" : "OFF"));
            refreshGlassStyles();
        });
        integrationsAction.setOnClickListener(v -> showIntegrationsPanel());
        moonlightAction.setOnClickListener(v -> openMoonlightSettings());
        showSidePanel("WAKE & PLAY", "Options",
                "Tune the console interface or open Moonlight's streaming preferences.",
                soundAction, motionAction, integrationsAction, moonlightAction);
    }

    private TextView panelStatus(String value) {
        TextView status = text(value, 14, 0xFFC1C5D6, false);
        status.setLineSpacing(dp(3), 1f);
        status.setFocusable(false);
        status.setPadding(dp(4), dp(6), dp(4), dp(14));
        return status;
    }

    private void showIntegrationsPanel() {
        clearDiscordControllerShortcuts();
        Host host = selectedHost;
        if (host == null || host.uuid == null || host.address == null || host.address.isEmpty()) {
            Toast.makeText(this, "Choose a streaming host first.", Toast.LENGTH_LONG).show();
            return;
        }

        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) {
            TextView pairAction = panelAction("PAIR HOST GATEWAY");
            pairAction.setOnClickListener(v -> showPairingDialog(host));
            showSidePanel("HOST INTEGRATIONS", host.name,
                    "Start the Wake & Play Host Gateway on this PC, then enter its six-digit pairing code. " +
                            "Discord and repair options remain hidden until the corresponding Bridge is detected.",
                    this::showOptionsPanel, pairAction);
            return;
        }

        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Gateway: checking…");
        TextView profileAction = panelAction("INTEGRATION PROFILE  ·  " +
                connection.profileId.toUpperCase(Locale.ROOT) + "  ›");
        TextView vibepolloStatus = panelStatus("Vibepollo Bridge: checking…");
        TextView discordStatus = panelStatus("Discord Bridge: checking…");
        TextView vibepolloAction = panelAction("VIBEPOLLO FIX  ›");
        TextView discordAction = panelAction("OPEN DISCORD  ›");
        TextView refreshAction = panelAction("REFRESH STATUS");
        TextView unpairAction = panelAction("FORGET THIS GATEWAY");
        vibepolloAction.setVisibility(View.GONE);
        discordAction.setVisibility(View.GONE);
        profileAction.setOnClickListener(v -> showIntegrationProfilesPanel(host));
        vibepolloAction.setOnClickListener(v -> showVibepolloFixPanel(host, connection));
        discordAction.setOnClickListener(v -> showDiscordPanel(
                host, connection, this::showIntegrationsPanel));
        refreshAction.setOnClickListener(v -> showIntegrationsPanel());
        unpairAction.setOnClickListener(v -> confirmForgetGateway(host));
        showSidePanel("HOST INTEGRATIONS", host.name,
                "Paired gateway: " + connection.endpoint,
                this::showOptionsPanel,
                status, profileAction, vibepolloStatus, discordStatus,
                vibepolloAction, discordAction, refreshAction, unpairAction);

        executor.submit(() -> {
            try {
                HostGatewayClient.IntegrationProfiles profiles = null;
                try {
                    profiles = hostGatewayClient.getIntegrationProfiles(connection);
                } catch (IOException ignored) {
                    // Older Gateways do not expose the profile catalog. Their
                    // single default Bridge remains usable during migration.
                }
                HostGatewayClient.IntegrationProfile selectedProfile = profiles != null ?
                        profiles.find(connection.profileId) : null;
                if (profiles != null && selectedProfile == null) {
                    mainHandler.post(() -> {
                        if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                        status.setText("Gateway: online\nSelected profile is no longer registered.");
                        profileAction.setText("SELECT INTEGRATION PROFILE  ›");
                        vibepolloStatus.setText("Vibepollo Bridge: select a profile");
                        discordStatus.setText("Discord Bridge: select a profile");
                    });
                    return;
                }
                HostGatewayClient.Capabilities capabilities = hostGatewayClient.getCapabilities(connection);
                HostGatewayClient.IntegrationProfiles loadedProfiles = profiles;
                HostGatewayClient.IntegrationProfile loadedProfile = selectedProfile;
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Gateway: online");
                    if (loadedProfile != null) {
                        boolean active = loadedProfiles.suggestedProfileId.equals(loadedProfile.id);
                        profileAction.setText("INTEGRATION PROFILE  ·  " +
                                loadedProfile.name.toUpperCase(Locale.ROOT) +
                                (active ? "  ·  ACTIVE" : "") + "  ›");
                    }
                    vibepolloStatus.setText("Vibepollo Bridge: " +
                            (capabilities.vibepolloFix ? "online" : "offline"));
                    discordStatus.setText("Discord Bridge: " +
                            (capabilities.discord ? "online" : "offline"));
                    vibepolloAction.setVisibility(capabilities.vibepolloFix ? View.VISIBLE : View.GONE);
                    discordAction.setVisibility(capabilities.discord ? View.VISIBLE : View.GONE);
                    rebuildSidePanelFocusNavigation();
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Gateway: unavailable\n" + friendlyGatewayError(error));
                    vibepolloStatus.setText("Vibepollo Bridge: status unavailable");
                    discordStatus.setText("Discord Bridge: status unavailable");
                });
            }
        });
    }

    private void showIntegrationProfilesPanel(Host host) {
        clearDiscordControllerShortcuts();
        if (host == null || host.uuid == null) return;
        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) {
            showIntegrationsPanel();
            return;
        }
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Loading profiles from Gateway…");
        TextView retry = panelAction("REFRESH PROFILES");
        retry.setOnClickListener(v -> showIntegrationProfilesPanel(host));
        showSidePanel("HOST INTEGRATIONS", "Integration profile",
                "Choose which Windows session, Discord account and Vibepollo configuration " +
                        "Wake & Play should control on this host.",
                this::showIntegrationsPanel, status, retry);
        executor.submit(() -> {
            try {
                HostGatewayClient.IntegrationProfiles profiles =
                        hostGatewayClient.getIntegrationProfiles(connection);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    renderIntegrationProfilesPanel(host, profiles);
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Profiles unavailable\n" + friendlyGatewayError(error) +
                            "\nUpdate and restart the Wake & Play Host Gateway if needed.");
                });
            }
        });
    }

    private void renderIntegrationProfilesPanel(
            Host host, HostGatewayClient.IntegrationProfiles profiles) {
        String selectedId = hostGatewayStore.selectedIntegrationProfileId(host.uuid);
        List<View> actions = new ArrayList<>();
        if (profiles.profiles.isEmpty()) {
            actions.add(panelStatus("No integration profiles are registered on this host."));
        } else {
            for (HostGatewayClient.IntegrationProfile profile : profiles.profiles) {
                boolean selected = profile.id.equals(selectedId);
                boolean suggested = profile.id.equals(profiles.suggestedProfileId);
                StringBuilder label = new StringBuilder();
                label.append(selected ? "✓  " : "")
                        .append(profile.name.toUpperCase(Locale.ROOT));
                if (suggested) label.append("  ·  ACTIVE");
                label.append("\nDISCORD ").append(
                        profile.discordRpcConnected ? "CONNECTED" :
                                profile.discordBridgeOnline ? "READY" : "OFFLINE");
                label.append("  ·  VIBEPOLLO ").append(
                        profile.vibepolloBridgeOnline ? "ONLINE" : "OFFLINE");
                if (profile.virtualHereAvailable) label.append("  ·  USB READY");
                TextView action = panelAction(label.toString());
                action.setContentDescription("Integration profile " + profile.name +
                        (selected ? ", selected" : ""));
                action.setOnClickListener(v -> {
                    hostGatewayStore.setSelectedIntegrationProfileId(host.uuid, profile.id);
                    clearDiscordControllerShortcuts();
                    refreshCommunityAvailability(host);
                    showIntegrationsPanel();
                });
                actions.add(action);
            }
        }
        TextView refresh = panelAction("REFRESH PROFILES");
        refresh.setOnClickListener(v -> showIntegrationProfilesPanel(host));
        actions.add(refresh);
        showSidePanel("HOST INTEGRATIONS", "Integration profile",
                "The selection is saved separately for " + host.name +
                        " and follows Discord, Vibepollo, audio, USB and the Moonlight overlay.",
                this::showIntegrationsPanel, actions.toArray(new View[0]));
    }

    private boolean isCurrentIntegrationPanel(int request, String hostUuid) {
        return dashboardActive && request == integrationPanelRequest.get() &&
                modalLayer != null && modalLayer.getVisibility() == View.VISIBLE &&
                selectedHost != null && hostUuid.equals(selectedHost.uuid);
    }

    private void showPairingDialog(Host host) {
        EditText code = new EditText(this);
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        code.setHint("6-digit code");
        code.setTextColor(Color.WHITE);
        code.setHintTextColor(0xFF9298AD);
        code.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Pair " + host.name)
                .setMessage("Enter the code displayed by Start-WakePlayGateway.ps1 on the host PC.")
                .setView(code)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Pair", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = code.getText().toString().trim();
                    if (!value.matches("[0-9]{6}")) {
                        code.setError("Enter six digits");
                        return;
                    }
                    dialog.dismiss();
                    beginGatewayPairing(host, value);
                }));
        dialog.show();
    }

    private void beginGatewayPairing(Host host, String code) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Pairing securely with " + host.address + "…");
        TextView openAction = panelAction("OPEN HOST INTEGRATIONS");
        TextView closeAction = panelAction("CLOSE");
        openAction.setVisibility(View.GONE);
        openAction.setOnClickListener(v -> showIntegrationsPanel());
        closeAction.setOnClickListener(v -> hideSidePanel(true));
        showSidePanel("HOST INTEGRATIONS", "Pairing", null,
                this::showIntegrationsPanel, status, openAction, closeAction);
        String endpoint = HostGatewayClient.endpointForHost(host.address);
        executor.submit(() -> {
            try {
                HostGatewayClient.Pairing pairing = hostGatewayClient.pair(endpoint, code, "Wake & Play Android TV");
                hostGatewayStore.save(host.uuid, pairing.connection);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Paired successfully. The host certificate has been pinned to this TV.");
                    openAction.setVisibility(View.VISIBLE);
                    rebuildSidePanelFocusNavigation();
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Pairing failed\n" + friendlyGatewayError(error));
                });
            }
        });
    }

    private void confirmForgetGateway(Host host) {
        TextView cancel = panelAction("CANCEL");
        TextView confirm = panelAction("FORGET GATEWAY");
        cancel.setOnClickListener(v -> showIntegrationsPanel());
        confirm.setOnClickListener(v -> {
            integrationPanelRequest.incrementAndGet();
            hostGatewayStore.remove(host.uuid);
            showIntegrationsPanel();
        });
        showSidePanel("HOST INTEGRATIONS", "Forget paired gateway?",
                "Wake & Play will delete the local client token and certificate pin. " +
                        "The host can be paired again with a new code.",
                this::showIntegrationsPanel, cancel, confirm);
    }

    private void showVibepolloFixPanel(Host host, HostGatewayClient.Connection connection) {
        clearDiscordControllerShortcuts();
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Loading Vibepollo status…");
        TextView restart = panelAction("RESTART VIBEPOLLO");
        TextView resetDisplay = panelAction("RESET REMEMBERED DISPLAY");
        TextView exportLogs = panelAction("EXPORT VIBEPOLLO LOGS");
        TextView refresh = panelAction("REFRESH STATUS");
        restart.setOnClickListener(v -> confirmVibepolloAction(host, connection, "restart",
                "Restart Vibepollo?", "The active host service will be restarted. An active stream may be interrupted."));
        resetDisplay.setOnClickListener(v -> confirmVibepolloAction(host, connection, "reset-display",
                "Reset remembered display?", "Vibepollo will forget its persisted display choice and select it again on the next session."));
        exportLogs.setOnClickListener(v -> runVibepolloAction(host, connection, "export-logs", "Exporting Vibepollo logs…"));
        refresh.setOnClickListener(v -> showVibepolloFixPanel(host, connection));
        showSidePanel("VIBEPOLLO FIX", host.name,
                "Integration profile: " + connection.profileId +
                        "\nRepair actions run on the paired host. Restart and display reset " +
                        "always require confirmation.",
                this::showIntegrationsPanel, status, restart, resetDisplay, exportLogs, refresh);

        executor.submit(() -> {
            try {
                HostGatewayClient.RepairStatus repair = hostGatewayClient.getVibepolloRepairStatus(connection);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    StringBuilder value = new StringBuilder(repair.online ? "Vibepollo online" : "Vibepollo API unavailable");
                    if (!repair.version.isEmpty()) value.append("\nVersion ").append(repair.version);
                    if (!repair.error.isEmpty()) value.append("\nLast API error: ").append(repair.error);
                    status.setText(value.toString());
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Unable to load Vibepollo status\n" + friendlyGatewayError(error));
                });
            }
        });
    }

    private void confirmVibepolloAction(Host host, HostGatewayClient.Connection connection,
                                        String action, String title, String warning) {
        TextView cancel = panelAction("CANCEL");
        TextView confirm = panelAction("CONFIRM");
        cancel.setOnClickListener(v -> showVibepolloFixPanel(host, connection));
        confirm.setOnClickListener(v -> runVibepolloAction(host, connection, action, title + "…"));
        showSidePanel("VIBEPOLLO FIX", title, warning,
                () -> showVibepolloFixPanel(host, connection), cancel, confirm);
    }

    private void runVibepolloAction(Host host, HostGatewayClient.Connection connection,
                                     String action, String progress) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus(progress);
        TextView back = panelAction("BACK TO VIBEPOLLO FIX");
        TextView close = panelAction("CLOSE");
        back.setVisibility(View.GONE);
        back.setOnClickListener(v -> showVibepolloFixPanel(host, connection));
        close.setOnClickListener(v -> hideSidePanel(true));
        showSidePanel("VIBEPOLLO FIX", "Host operation", null,
                () -> showVibepolloFixPanel(host, connection), status, back, close);
        executor.submit(() -> {
            try {
                hostGatewayClient.runVibepolloRepair(connection, action);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Operation completed successfully.");
                    back.setVisibility(View.VISIBLE);
                    rebuildSidePanelFocusNavigation();
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Operation failed\n" + friendlyGatewayError(error));
                    back.setVisibility(View.VISIBLE);
                    rebuildSidePanelFocusNavigation();
                });
            }
        });
    }

    private void maybePrepareDiscordForStream(Host host) {
        if (host == null || host.uuid == null) return;
        HostGatewayClient.Connection connection = hostGatewayStore.load(host.uuid);
        if (connection == null) return;
        String profileKey = host.uuid + ":" + connection.profileId;
        if (!hostGatewayStore.isDiscordAutoConnectEnabled(
                host.uuid, connection.profileId) ||
                !discordAutoConnectAttempted.add(profileKey)) return;
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordStatus status = hostGatewayClient.getDiscordStatus(connection);
                if (!status.bridgeOnline) return;
                if (!status.rpcConnected) {
                    hostGatewayClient.startDiscord(connection);
                    for (int attempt = 0; attempt < 8 && !status.rpcConnected; attempt++) {
                        Thread.sleep(attempt == 0 ? 3_000 : 750);
                        status = hostGatewayClient.getDiscordStatus(connection);
                    }
                }
                if (status.rpcConnected && !status.authenticated) {
                    hostGatewayClient.connectDiscord(connection, false);
                    status = hostGatewayClient.getDiscordStatus(connection);
                }
                HostGatewayStore.DiscordChannelSelection lastChannel =
                        hostGatewayStore.loadLastDiscordChannel(
                                host.uuid, connection.profileId);
                if (status.authenticated &&
                        hostGatewayStore.isDiscordAutoJoinLastEnabled(
                                host.uuid, connection.profileId) &&
                        lastChannel != null) {
                    hostGatewayClient.joinDiscordChannel(connection,
                            lastChannel.channelId, lastChannel.guildId,
                            lastChannel.guildName, lastChannel.channelName);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException error) {
                Log.w(TAG, "Discord auto-connect failed for " + host.uuid, error);
            }
        });
    }

    private void showDiscordPanel(Host host, HostGatewayClient.Connection connection,
                                  Runnable exitAction) {
        communityExitAction = exitAction;
        showDiscordPanel(host, connection);
    }

    private void showDiscordPanel(Host host, HostGatewayClient.Connection connection) {
        // The Discord landing page is the server browser. Keep the legacy
        // dashboard below as a defensive fallback for an invalid connection.
        if (connection != null) {
            showDiscordServersPanel(host, connection, false);
            return;
        }
        Runnable exitAction = communityExitAction != null
                ? communityExitAction : () -> hideSidePanel(true);
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Discord Bridge: checking…");
        TextView voice = panelStatus("Voice channel: checking…");
        TextView quickMute = panelAction("□ / X  MUTE MICROPHONE");
        TextView quickLeave = panelAction("△ / Y  LEAVE VOICE");
        LinearLayout voiceRow = panelActionRow(quickMute, quickLeave);
        voiceRow.setVisibility(View.GONE);
        TextView quickLabel = panelStatus("RECENT VOICE CHANNELS");
        quickLabel.setVisibility(View.GONE);
        TextView[] quickChannels = new TextView[]{
                panelAction("CHANNEL 1"), panelAction("CHANNEL 2")};
        for (TextView quick : quickChannels) quick.setVisibility(View.GONE);
        LinearLayout quickRow = panelActionRow(quickChannels[0], quickChannels[1]);
        quickRow.setVisibility(View.GONE);
        TextView saved = panelAction("★  SAVED CHANNELS");
        TextView servers = panelAction("▦  ALL SERVERS");
        TextView people = panelAction("PEOPLE");
        people.setVisibility(View.GONE);
        TextView voiceControls = panelAction("◉  VOICE CONTROLS");
        TextView virtualHere = panelAction("USB  DEVICES");
        TextView settings = panelAction("⚙  SETTINGS");

        quickMute.setOnClickListener(v -> runDiscordControllerShortcut(false));
        quickLeave.setOnClickListener(v -> runDiscordControllerShortcut(true));
        saved.setOnClickListener(v -> showDiscordSavedChannelsPanel(host, connection, false));
        servers.setOnClickListener(v -> showDiscordServersPanel(host, connection, false));
        people.setOnClickListener(v -> showDiscordParticipantsPanel(host, connection, true));
        voiceControls.setOnClickListener(v -> showDiscordVoiceControlsPanel(host, connection));
        virtualHere.setOnClickListener(v -> showVirtualHerePanel(host, connection, false));
        settings.setOnClickListener(v -> showDiscordSettingsPanel(host, connection));

        showSidePanel("DISCORD", host.name,
                "Integration profile: " + connection.profileId,
                exitAction,
                status, voiceRow, quickRow,
                panelActionRow(saved, servers),
                panelActionRow(people, voiceControls),
                panelActionRow(virtualHere, settings));
        activateDiscordControllerShortcuts(host, connection, status,
                quickMute, quickLeave);

        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordStatus discord = hostGatewayClient.getDiscordStatus(connection);
                HostGatewayClient.DiscordVoice loadedVoice = null;
                HostGatewayClient.DiscordHome loadedHome = null;
                if (discord.authenticated) {
                    try {
                        loadedVoice = hostGatewayClient.getDiscordVoice(connection, false);
                    } catch (IOException error) {
                        Log.w(TAG, "Unable to load Discord voice state", error);
                    }
                    try {
                        loadedHome = hostGatewayClient.getDiscordHome(connection, false);
                    } catch (IOException error) {
                        Log.w(TAG, "Unable to load Discord shortcuts", error);
                    }
                }
                HostGatewayClient.DiscordVoice currentVoice = loadedVoice;
                HostGatewayClient.DiscordHome home = loadedHome;
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    if (!discord.bridgeOnline) {
                        status.setText("Discord Bridge: offline" +
                                (discord.error.isEmpty() ? "" : "\n" + discord.error));
                    } else if (!discord.rpcConnected) {
                        status.setText("Discord Bridge: online\n" +
                                discordUnavailableMessage(discord));
                    } else if (!discord.authenticated) {
                        status.setText("Discord Bridge: online\nDiscord RPC: authorization required");
                    } else {
                        status.setText("ONLINE  ·  Discord connected");
                    }
                    if (currentVoice == null || !currentVoice.connected) {
                        voice.setText("Voice channel: not connected");
                        if (discord.authenticated) {
                            status.setText("ONLINE  ·  Voice not connected");
                        }
                        voiceRow.setVisibility(View.GONE);
                        updateDiscordShortcutVoice(null);
                    } else {
                        voice.setText("◉  " + currentVoice.channelName +
                                "  ·  " + currentVoice.participants + " participant" +
                                (currentVoice.participants == 1 ? "" : "s"));
                        status.setText("ONLINE  ·  " + currentVoice.channelName +
                                "  ·  " + currentVoice.participants + " participant" +
                                (currentVoice.participants == 1 ? "" : "s"));
                        hostGatewayStore.saveLastDiscordChannel(host.uuid, connection.profileId,
                                currentVoice.channelId, currentVoice.guildId, "",
                                currentVoice.channelName);
                        voiceRow.setVisibility(View.VISIBLE);
                        people.setText("PEOPLE  ·  " + currentVoice.participants);
                        people.setVisibility(View.VISIBLE);
                        updateDiscordShortcutVoice(currentVoice);
                    }

                    if (home != null) {
                        List<HostGatewayClient.DiscordChannel> shortcuts = new ArrayList<>();
                        Set<String> seen = new HashSet<>();
                        for (HostGatewayClient.DiscordChannel channel : home.favorites) {
                            if (seen.add(channel.id)) shortcuts.add(channel);
                        }
                        for (HostGatewayClient.DiscordChannel channel : home.recent) {
                            if (seen.add(channel.id)) shortcuts.add(channel);
                        }
                        int count = Math.min(quickChannels.length, shortcuts.size());
                        for (int index = 0; index < count; index++) {
                            HostGatewayClient.DiscordChannel channel = shortcuts.get(index);
                            TextView quick = quickChannels[index];
                            String peopleSuffix = channel.people > 0
                                    ? "  ·  " + channel.people + " online" : "";
                            quick.setText((channel.favorite ? "★  " : "◉  ") +
                                    channel.name + peopleSuffix);
                            quick.setOnClickListener(v -> runDiscordOperation(host, connection,
                                    "Joining " + channel.name + "…",
                                    "Connected to " + channel.name + ".",
                                    () -> showDiscordPanel(host, connection),
                                    () -> joinDiscordChannelAndRemember(host, connection, channel)));
                            quick.setVisibility(View.VISIBLE);
                        }
                        quickLabel.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                        quickRow.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                        rebuildSidePanelFocusNavigation();
                    } else {
                        quickLabel.setVisibility(View.GONE);
                        quickRow.setVisibility(View.GONE);
                    }
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Discord unavailable\n" + friendlyGatewayError(error));
                });
            }
        });
    }

    private void showDiscordVoiceControlsPanel(Host host,
                                                HostGatewayClient.Connection connection) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Voice channel: checking…");
        TextView mute = panelAction("MICROPHONE  ·  CHECKING");
        TextView deafen = panelAction("HEADPHONES  ·  CHECKING");
        TextView leave = panelAction("LEAVE VOICE CHANNEL");
        TextView audio = panelAction("AUDIO DEVICES");
        TextView refresh = panelAction("REFRESH VOICE STATUS");
        TextView back = panelAction("BACK TO DISCORD");
        Runnable parent = () -> showDiscordPanel(host, connection);

        mute.setOnClickListener(v -> runDiscordOperation(host, connection,
                "Toggling microphone…", "Microphone state changed.",
                () -> showDiscordVoiceControlsPanel(host, connection),
                () -> hostGatewayClient.setDiscordVoiceFlag(connection, "mute", "toggle")));
        deafen.setOnClickListener(v -> runDiscordOperation(host, connection,
                "Toggling headphones…", "Headphone state changed.",
                () -> showDiscordVoiceControlsPanel(host, connection),
                () -> hostGatewayClient.setDiscordVoiceFlag(connection, "deafen", "toggle")));
        leave.setOnClickListener(v -> runDiscordOperation(host, connection,
                "Leaving the voice channel…", "Voice channel disconnected.",
                () -> showDiscordVoiceControlsPanel(host, connection),
                () -> hostGatewayClient.leaveDiscordChannel(connection)));
        audio.setOnClickListener(v -> showDiscordAudioPanel(host, connection));
        refresh.setOnClickListener(v -> showDiscordVoiceControlsPanel(host, connection));
        back.setOnClickListener(v -> parent.run());

        showSidePanel("DISCORD", "Voice controls", null, parent,
                status, panelActionRow(mute, deafen),
                panelActionRow(leave, audio), panelActionRow(refresh, back));
        activateDiscordControllerShortcuts(host, connection, status, mute, leave);
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordVoice current =
                        hostGatewayClient.getDiscordVoice(connection, false);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    if (!current.connected) {
                        status.setText("Voice channel: not connected");
                        mute.setText("MICROPHONE  ·  UNAVAILABLE");
                        deafen.setText("HEADPHONES  ·  UNAVAILABLE");
                        updateDiscordShortcutVoice(null);
                    } else {
                        status.setText("Voice channel: " + current.channelName +
                                "\nParticipants: " + current.participants);
                        mute.setText("MICROPHONE  ·  " + (current.muted ? "MUTED" : "ON"));
                        deafen.setText("HEADPHONES  ·  " + (current.deafened ? "DEAFENED" : "ON"));
                        hostGatewayStore.saveLastDiscordChannel(host.uuid, connection.profileId,
                                current.channelId, current.guildId, "", current.channelName);
                        updateDiscordShortcutVoice(current);
                    }
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Voice controls unavailable\n" + friendlyGatewayError(error));
                    mute.setText("MICROPHONE  ·  UNAVAILABLE");
                    deafen.setText("HEADPHONES  ·  UNAVAILABLE");
                });
            }
        });
    }

    private void showDiscordAudioPanel(Host host,
                                       HostGatewayClient.Connection connection) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView loading = panelStatus("Loading Discord and Windows audio devices…");
        TextView back = panelAction("BACK TO VOICE CONTROLS");
        back.setOnClickListener(v -> showDiscordVoiceControlsPanel(host, connection));
        showSidePanel("DISCORD", "Audio devices", null,
                () -> showDiscordVoiceControlsPanel(host, connection), loading, back);
        activateDiscordControllerShortcuts(host, connection, null, null, null);
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordAudioState audio =
                        hostGatewayClient.getDiscordAudioState(connection);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    List<View> views = new ArrayList<>();
                    int[] systemVolume = new int[]{audio.systemVolume};
                    boolean[] systemMuted = new boolean[]{audio.systemMuted};
                    TextView systemStatus = panelStatus("");
                    updateSystemAudioStatus(systemStatus, audio.systemAvailable,
                            systemVolume[0], systemMuted[0], audio.error);
                    views.add(systemStatus);
                    if (audio.systemAvailable) {
                        TextView down = panelAction("−5");
                        TextView up = panelAction("+5");
                        TextView mute = panelAction(systemMuted[0] ? "UNMUTE" : "MUTE");
                        AtomicBoolean busy = new AtomicBoolean(false);
                        down.setOnClickListener(v -> runSystemAudioAction(request, host,
                                connection, systemStatus, mute, systemVolume,
                                systemMuted, busy, -5));
                        up.setOnClickListener(v -> runSystemAudioAction(request, host,
                                connection, systemStatus, mute, systemVolume,
                                systemMuted, busy, 5));
                        mute.setOnClickListener(v -> runSystemAudioAction(request, host,
                                connection, systemStatus, mute, systemVolume,
                                systemMuted, busy, 0));
                        views.add(panelActionRow(down, up, mute));
                    }
                    addAudioDeviceActions(views, "DISCORD INPUT",
                            audio.discordDevices, "input", host, connection);
                    addAudioDeviceActions(views, "DISCORD OUTPUT",
                            audio.discordDevices, "output", host, connection);
                    addAudioDeviceActions(views, "WINDOWS INPUT",
                            audio.systemDevices, "input", host, connection);
                    addAudioDeviceActions(views, "WINDOWS OUTPUT",
                            audio.systemDevices, "output", host, connection);
                    TextView refresh = panelAction("REFRESH");
                    TextView done = panelAction("BACK");
                    refresh.setOnClickListener(v -> showDiscordAudioPanel(host, connection));
                    done.setOnClickListener(v -> showDiscordVoiceControlsPanel(host, connection));
                    views.add(panelActionRow(refresh, done));
                    showSidePanel("DISCORD", "Audio devices", null,
                            () -> showDiscordVoiceControlsPanel(host, connection),
                            views.toArray(new View[0]));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    TextView failed = panelStatus("Audio devices unavailable\n" +
                            friendlyGatewayError(error));
                    TextView retry = panelAction("RETRY");
                    TextView done = panelAction("BACK");
                    retry.setOnClickListener(v -> showDiscordAudioPanel(host, connection));
                    done.setOnClickListener(v -> showDiscordVoiceControlsPanel(host, connection));
                    showSidePanel("DISCORD", "Audio devices", null,
                            () -> showDiscordVoiceControlsPanel(host, connection),
                            failed, panelActionRow(retry, done));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            }
        });
    }

    private void addAudioDeviceActions(List<View> views, String label,
                                       List<HostGatewayClient.AudioDevice> devices,
                                       String flow, Host host,
                                       HostGatewayClient.Connection connection) {
        List<HostGatewayClient.AudioDevice> matching = new ArrayList<>();
        for (HostGatewayClient.AudioDevice device : devices) {
            if (flow.equals(device.flow)) matching.add(device);
        }
        if (matching.isEmpty()) return;
        views.add(panelStatus(label));
        for (HostGatewayClient.AudioDevice device : matching) {
            TextView action = panelAction((device.current ? "✓  " : "") + device.name);
            action.setOnClickListener(v -> {
                if (device.current) {
                    Toast.makeText(this, "This device is already selected.",
                            Toast.LENGTH_SHORT).show();
                } else {
                    runDiscordAudioDeviceOperation(host, connection, device);
                }
            });
            views.add(action);
        }
    }

    private void runDiscordAudioDeviceOperation(Host host,
                                                HostGatewayClient.Connection connection,
                                                HostGatewayClient.AudioDevice device) {
        executor.submit(() -> {
            try {
                hostGatewayClient.selectAudioDevice(connection, device);
                mainHandler.post(() -> {
                    Toast.makeText(this, "Selected " + device.name + ".",
                            Toast.LENGTH_SHORT).show();
                    showDiscordAudioPanel(host, connection);
                });
            } catch (IOException error) {
                mainHandler.post(() -> Toast.makeText(this,
                        "Audio device: " + friendlyGatewayError(error),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateSystemAudioStatus(TextView status, boolean available,
                                         int volume, boolean muted, String error) {
        status.setText(available
                ? "WINDOWS VOLUME  ·  " + volume + "%" + (muted ? "  ·  MUTED" : "")
                : "Windows audio unavailable" +
                (error == null || error.isEmpty() ? "" : "\n" + error));
    }

    private void runSystemAudioAction(int request, Host host,
                                      HostGatewayClient.Connection connection,
                                      TextView status, TextView muteAction,
                                      int[] volume, boolean[] muted,
                                      AtomicBoolean busy, int delta) {
        if (!busy.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                if (delta == 0) hostGatewayClient.toggleSystemMute(connection);
                else hostGatewayClient.changeSystemVolume(connection, delta);
                mainHandler.post(() -> {
                    busy.set(false);
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    if (delta == 0) muted[0] = !muted[0];
                    else volume[0] = Math.max(0, Math.min(100, volume[0] + delta));
                    updateSystemAudioStatus(status, true, volume[0], muted[0], "");
                    muteAction.setText(muted[0] ? "UNMUTE" : "MUTE");
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    busy.set(false);
                    Toast.makeText(this, "Windows audio: " +
                            friendlyGatewayError(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void activateDiscordControllerShortcuts(
            Host host, HostGatewayClient.Connection connection,
            TextView voiceStatus, TextView muteAction, TextView leaveAction) {
        discordShortcutHost = host;
        discordShortcutConnection = connection;
        discordShortcutVoiceStatus = voiceStatus;
        discordShortcutMuteAction = muteAction;
        discordShortcutLeaveAction = leaveAction;
    }

    private void showDiscordParticipantsPanel(Host host,
                                               HostGatewayClient.Connection connection,
                                               boolean force) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView loading = panelStatus("Loading people in the voice channel…");
        TextView back = panelAction("BACK TO DISCORD");
        back.setOnClickListener(v -> showDiscordPanel(host, connection));
        showSidePanel("DISCORD", "People", null,
                () -> showDiscordPanel(host, connection), loading, back);
        activateDiscordControllerShortcuts(host, connection, null, null, null);
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordVoice current =
                        hostGatewayClient.getDiscordVoice(connection, force);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    List<View> views = new ArrayList<>();
                    TextView[] voiceActions = new TextView[2];
                    if (!current.connected) {
                        views.add(panelStatus("Discord is not connected to a voice channel."));
                    } else {
                        views.add(panelStatus("◉  " + current.channelName + "  ·  " +
                                current.participants + " participant" +
                                (current.participants == 1 ? "" : "s")));
                        TextView voiceMute = discordAction(
                                current.muted ? "MIC MUTED  ·  UNMUTE" : "MIC ON  ·  MUTE",
                                current.muted ? DISCORD_RED : DISCORD_GREEN);
                        TextView voiceLeave = discordAction("LEAVE", DISCORD_RED);
                        voiceMute.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Toggling microphone…",
                                    "Microphone updated.",
                                    () -> showDiscordParticipantsPanel(host, connection, true),
                                    () -> hostGatewayClient.setDiscordVoiceFlag(
                                            connection, "mute", "toggle"));
                        });
                        voiceLeave.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Leaving voice…",
                                    "Voice disconnected.",
                                    () -> showDiscordParticipantsPanel(host, connection, true),
                                    () -> hostGatewayClient.leaveDiscordChannel(connection));
                        });
                        voiceActions[0] = voiceMute;
                        voiceActions[1] = voiceLeave;
                        views.add(panelActionRow(voiceMute, voiceLeave));
                        for (HostGatewayClient.DiscordParticipant participant :
                                current.participantList) {
                            TextView person = panelStatus("");
                            int[] volume = new int[]{participant.volume};
                            boolean[] muted = new boolean[]{participant.muted};
                            renderDiscordParticipantStatus(person, participant,
                                    volume[0], muted[0]);
                            views.add(person);
                            if (participant.self) continue;
                            SeekBar slider = panelVolumeSlider(volume[0]);
                            TextView mute = panelAction(muted[0] ? "UNMUTE" : "MUTE");
                            AtomicBoolean muteBusy = new AtomicBoolean(false);
                            AtomicBoolean volumeBusy = new AtomicBoolean(false);
                            AtomicInteger desiredVolume = new AtomicInteger(volume[0]);
                            Runnable commitVolume = () -> queueDiscordParticipantVolume(
                                    request, host, connection, participant, volume,
                                    muted, person, desiredVolume, volumeBusy);
                            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress,
                                                              boolean fromUser) {
                                    if (!fromUser) return;
                                    int snapped = Math.max(0, Math.min(200,
                                            Math.round(progress / 10f) * 10));
                                    if (snapped != progress) seekBar.setProgress(snapped);
                                    volume[0] = snapped;
                                    desiredVolume.set(snapped);
                                    renderDiscordParticipantStatus(person, participant,
                                            volume[0], muted[0]);
                                }

                                @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {
                                    commitVolume.run();
                                }
                            });
                            slider.setOnKeyListener((v, keyCode, event) -> {
                                if (event.getAction() == KeyEvent.ACTION_UP &&
                                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                                    commitVolume.run();
                                }
                                return false;
                            });
                            mute.setOnClickListener(v -> runDiscordParticipantAction(
                                    request, host, connection, participant, volume, muted,
                                    person, mute, muteBusy, 0));
                            views.add(panelWeightedActionRow(slider, mute));
                        }
                    }
                    TextView refresh = panelAction("REFRESH");
                    TextView done = panelAction("BACK");
                    refresh.setOnClickListener(v -> showDiscordParticipantsPanel(
                            host, connection, true));
                    done.setOnClickListener(v -> showDiscordPanel(host, connection));
                    views.add(panelActionRow(refresh, done));
                    showSidePanel("DISCORD", "People", null,
                            () -> showDiscordPanel(host, connection),
                            views.toArray(new View[0]));
                    activateDiscordControllerShortcuts(host, connection, null,
                            voiceActions[0], voiceActions[1]);
                    updateDiscordShortcutVoice(current);
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    TextView failed = panelStatus("People unavailable\n" +
                            friendlyGatewayError(error));
                    TextView retry = panelAction("RETRY");
                    TextView done = panelAction("BACK");
                    retry.setOnClickListener(v -> showDiscordParticipantsPanel(
                            host, connection, true));
                    done.setOnClickListener(v -> showDiscordPanel(host, connection));
                    showSidePanel("DISCORD", "People", null,
                            () -> showDiscordPanel(host, connection),
                            failed, panelActionRow(retry, done));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            }
        });
    }

    private void renderDiscordParticipantStatus(TextView view,
                                                HostGatewayClient.DiscordParticipant participant,
                                                int volume, boolean muted) {
        view.setText((participant.speaking ? "▶  " : "●  ") + participant.name +
                (participant.self ? "  ·  YOU" : "  ·  " + volume + "%") +
                (muted ? "  ·  MUTED" : ""));
        view.setTextColor(participant.speaking ? 0xFF69F0AE : 0xFFC1C5D6);
    }

    private void runDiscordParticipantAction(
            int request, Host host, HostGatewayClient.Connection connection,
            HostGatewayClient.DiscordParticipant participant,
            int[] volume, boolean[] muted, TextView status, TextView muteAction,
            AtomicBoolean busy, int delta) {
        if (!busy.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                if (delta == 0) {
                    hostGatewayClient.toggleDiscordParticipantMute(connection, participant.id);
                } else {
                    hostGatewayClient.changeDiscordParticipantVolume(
                            connection, participant.id, delta);
                }
                mainHandler.post(() -> {
                    busy.set(false);
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    if (delta == 0) muted[0] = !muted[0];
                    else volume[0] = Math.max(0, Math.min(200, volume[0] + delta));
                    renderDiscordParticipantStatus(status, participant,
                            volume[0], muted[0]);
                    muteAction.setText(muted[0] ? "UNMUTE" : "MUTE");
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    busy.set(false);
                    Toast.makeText(this, "Discord user control: " +
                                    friendlyGatewayError(error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void queueDiscordParticipantVolume(
            int request, Host host, HostGatewayClient.Connection connection,
            HostGatewayClient.DiscordParticipant participant,
            int[] volume, boolean[] muted, TextView status,
            AtomicInteger desiredVolume, AtomicBoolean busy) {
        if (!busy.compareAndSet(false, true)) return;
        executor.submit(() -> {
            int sent = -1;
            try {
                do {
                    sent = desiredVolume.get();
                    hostGatewayClient.setDiscordParticipantVolume(
                            connection, participant.id, sent);
                } while (desiredVolume.get() != sent);
                int applied = sent;
                mainHandler.post(() -> {
                    busy.set(false);
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    volume[0] = applied;
                    renderDiscordParticipantStatus(status, participant,
                            volume[0], muted[0]);
                    if (desiredVolume.get() != applied) {
                        queueDiscordParticipantVolume(request, host, connection,
                                participant, volume, muted, status,
                                desiredVolume, busy);
                    }
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    busy.set(false);
                    Toast.makeText(this, "Discord user volume: " +
                                    friendlyGatewayError(error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void clearDiscordControllerShortcuts() {
        discordShortcutHost = null;
        discordShortcutConnection = null;
        discordShortcutVoiceStatus = null;
        discordShortcutMuteAction = null;
        discordShortcutLeaveAction = null;
        discordShortcutVoiceConnected = false;
        discordShortcutMuted = false;
    }

    private void updateDiscordShortcutVoice(HostGatewayClient.DiscordVoice voice) {
        discordShortcutVoiceConnected = voice != null && voice.connected;
        discordShortcutMuted = discordShortcutVoiceConnected && voice.muted;
        if (discordShortcutMuteAction != null &&
                discordShortcutMuteAction.isAttachedToWindow()) {
            discordShortcutMuteAction.setText(discordShortcutVoiceConnected
                    ? "□ / X  " + (discordShortcutMuted ? "UNMUTE" : "MUTE MICROPHONE")
                    : "□ / X  MICROPHONE UNAVAILABLE");
        }
        if (discordShortcutLeaveAction != null &&
                discordShortcutLeaveAction.isAttachedToWindow()) {
            discordShortcutLeaveAction.setText("△ / Y  LEAVE VOICE");
        }
    }

    private void runDiscordControllerShortcut(boolean leaveVoice) {
        Host host = discordShortcutHost;
        HostGatewayClient.Connection connection = discordShortcutConnection;
        if (host == null || connection == null) return;
        if (!discordShortcutVoiceConnected) {
            Toast.makeText(this, "Discord is not connected to a voice channel.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordVoice updated;
                if (leaveVoice) {
                    hostGatewayClient.leaveDiscordChannel(connection);
                    updated = null;
                } else {
                    hostGatewayClient.setDiscordVoiceFlag(connection, "mute", "toggle");
                    updated = hostGatewayClient.getDiscordVoice(connection, true);
                }
                HostGatewayClient.DiscordVoice result = updated;
                mainHandler.post(() -> {
                    if (discordShortcutConnection != connection) return;
                    updateDiscordShortcutVoice(result);
                    if (discordShortcutVoiceStatus != null &&
                            discordShortcutVoiceStatus.isAttachedToWindow()) {
                        if (result == null || !result.connected) {
                            discordShortcutVoiceStatus.setText("Voice channel: not connected");
                        } else {
                            discordShortcutVoiceStatus.setText("◉  " + result.channelName +
                                    "  ·  " + (result.muted ? "microphone muted" :
                                    result.participants + " participant" +
                                            (result.participants == 1 ? "" : "s")));
                        }
                    }
                    Toast.makeText(this, leaveVoice ? "Left Discord voice." :
                                    (discordShortcutMuted ? "Microphone muted." : "Microphone on."),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (IOException error) {
                mainHandler.post(() -> Toast.makeText(this,
                        "Discord shortcut failed: " + friendlyGatewayError(error),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showVirtualHerePanel(Host host,
                                      HostGatewayClient.Connection connection,
                                      boolean force) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView loading = panelStatus("Loading USB devices…");
        TextView back = panelAction("BACK TO DISCORD");
        back.setOnClickListener(v -> showDiscordPanel(host, connection));
        showSidePanel("VIRTUALHERE", "USB devices",
                "Integration profile: " + connection.profileId +
                        "\nConnect host USB devices without leaving Wake & Play.",
                () -> showDiscordPanel(host, connection), loading, back);
        activateDiscordControllerShortcuts(host, connection, null, null, null);
        executor.submit(() -> {
            try {
                HostGatewayClient.VirtualHereState state =
                        hostGatewayClient.getVirtualHereState(connection, force);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    List<View> actions = new ArrayList<>();
                    String summary = state.installed
                            ? state.running ? "VirtualHere client: online" :
                            "VirtualHere client: not running"
                            : "VirtualHere client: not installed";
                    if (!state.error.isEmpty()) summary += "\n" + state.error;
                    actions.add(panelStatus(summary));
                    int deviceCount = 0;
                    for (HostGatewayClient.VirtualHereServer server : state.servers) {
                        actions.add(panelStatus("SERVER  ·  " +
                                (!server.name.isEmpty() ? server.name : server.hostname)));
                        for (HostGatewayClient.VirtualHereDevice device : server.devices) {
                            deviceCount++;
                            String stateLabel = device.inUseByMe ? "CONNECTED" :
                                    device.available ? "AVAILABLE" :
                                    device.inUse ? "IN USE" : "OFFLINE";
                            TextView use = panelAction((device.inUseByMe ? "■  " : "USB  ") +
                                    device.name + "  ·  " + stateLabel);
                            TextView auto = panelAction(device.autoUse ?
                                    "AUTO  ·  ON" : "AUTO USE");
                            if (device.inUseByMe) {
                                use.setOnClickListener(v -> runVirtualHereOperation(host,
                                        connection, "stop", device.address,
                                        "Disconnecting " + device.name + "…"));
                            } else if (device.available) {
                                use.setOnClickListener(v -> runVirtualHereOperation(host,
                                        connection, "use", device.address,
                                        "Connecting " + device.name + "…"));
                            } else {
                                use.setOnClickListener(v -> Toast.makeText(this,
                                        device.boundHostname.isEmpty()
                                                ? "This USB device is unavailable."
                                                : "In use by " + device.boundHostname + ".",
                                        Toast.LENGTH_LONG).show());
                            }
                            auto.setOnClickListener(v -> {
                                if (device.autoUse) {
                                    Toast.makeText(this, "Auto use is already enabled.",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    runVirtualHereOperation(host, connection, "auto",
                                            device.address,
                                            "Enabling auto use for " + device.name + "…");
                                }
                            });
                            actions.add(panelActionRow(use, auto));
                        }
                    }
                    if (deviceCount == 0) {
                        actions.add(panelStatus("No USB devices are currently advertised by a VirtualHere server."));
                    }
                    TextView restart = panelAction("RESTART VIRTUALHERE");
                    TextView refresh = panelAction("REFRESH");
                    TextView done = panelAction("BACK");
                    restart.setOnClickListener(v -> runVirtualHereOperation(host,
                            connection, "restart", null, "Restarting VirtualHere…"));
                    refresh.setOnClickListener(v -> showVirtualHerePanel(host, connection, true));
                    done.setOnClickListener(v -> showDiscordPanel(host, connection));
                    actions.add(panelActionRow(restart, refresh));
                    actions.add(done);
                    showSidePanel("VIRTUALHERE", "USB devices", null,
                            () -> showDiscordPanel(host, connection),
                            actions.toArray(new View[0]));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    TextView failed = panelStatus("VirtualHere unavailable\n" +
                            friendlyGatewayError(error));
                    TextView retry = panelAction("RETRY");
                    TextView done = panelAction("BACK TO DISCORD");
                    retry.setOnClickListener(v -> showVirtualHerePanel(host, connection, true));
                    done.setOnClickListener(v -> showDiscordPanel(host, connection));
                    showSidePanel("VIRTUALHERE", "USB devices", null,
                            () -> showDiscordPanel(host, connection),
                            failed, panelActionRow(retry, done));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            }
        });
    }

    private void runVirtualHereOperation(Host host,
                                         HostGatewayClient.Connection connection,
                                         String action, String address,
                                         String progress) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus(progress);
        showSidePanel("VIRTUALHERE", "USB devices", null,
                () -> showVirtualHerePanel(host, connection, true), status);
        activateDiscordControllerShortcuts(host, connection, null, null, null);
        executor.submit(() -> {
            try {
                hostGatewayClient.runVirtualHereAction(connection, action, address);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    showVirtualHerePanel(host, connection, true);
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    Toast.makeText(this, "VirtualHere: " + friendlyGatewayError(error),
                            Toast.LENGTH_LONG).show();
                    showVirtualHerePanel(host, connection, true);
                });
            }
        });
    }

    private void showDiscordSettingsPanel(Host host,
                                          HostGatewayClient.Connection connection) {
        int request = integrationPanelRequest.incrementAndGet();
        TextView status = panelStatus("Discord profile " + connection.profileId + ": checking…");
        String profileId = connection.profileId;
        boolean autoConnectEnabled = hostGatewayStore.isDiscordAutoConnectEnabled(
                host.uuid, profileId);
        HostGatewayStore.DiscordChannelSelection lastChannel =
                hostGatewayStore.loadLastDiscordChannel(host.uuid, profileId);
        boolean autoJoinEnabled = hostGatewayStore.isDiscordAutoJoinLastEnabled(
                host.uuid, profileId);
        TextView autoConnect = panelAction("START DISCORD WITH STREAM  ·  " +
                (autoConnectEnabled ? "ON" : "OFF"));
        TextView lastChannelStatus = panelStatus("Last voice channel: " +
                (lastChannel != null ? lastChannel.channelName : "not recorded yet"));
        TextView autoJoin = panelAction("AUTO-JOIN LAST CHANNEL  ·  " +
                (lastChannel == null ? "UNAVAILABLE" : autoJoinEnabled ? "ON" : "OFF"));
        TextView start = panelAction("START DISCORD ON HOST");
        TextView connect = panelAction("CONNECT / AUTHORIZE RPC");
        TextView refresh = panelAction("REFRESH PROFILE STATUS");
        TextView back = panelAction("BACK TO DISCORD");
        Runnable parent = () -> showDiscordPanel(host, connection);

        autoConnect.setOnClickListener(v -> {
            boolean enabled = !hostGatewayStore.isDiscordAutoConnectEnabled(host.uuid, profileId);
            hostGatewayStore.setDiscordAutoConnectEnabled(host.uuid, profileId, enabled);
            autoConnect.setText("START DISCORD WITH STREAM  ·  " + (enabled ? "ON" : "OFF"));
            lastChannelStatus.setVisibility(enabled ? View.VISIBLE : View.GONE);
            autoJoin.setVisibility(enabled ? View.VISIBLE : View.GONE);
            rebuildSidePanelFocusNavigation();
            if (enabled) {
                discordAutoConnectAttempted.remove(
                        host.uuid + ":" + profileId);
            }
        });
        autoJoin.setOnClickListener(v -> {
            if (lastChannel == null) {
                Toast.makeText(this,
                        "Join a Discord voice channel once to remember it.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            boolean enabled = !hostGatewayStore.isDiscordAutoJoinLastEnabled(host.uuid, profileId);
            hostGatewayStore.setDiscordAutoJoinLastEnabled(host.uuid, profileId, enabled);
            autoJoin.setText("AUTO-JOIN LAST CHANNEL  ·  " + (enabled ? "ON" : "OFF"));
        });
        start.setOnClickListener(v -> runDiscordOperation(host, connection,
                "Starting Discord in the Bridge profile…", "Discord start requested.",
                () -> showDiscordSettingsPanel(host, connection),
                () -> hostGatewayClient.startDiscord(connection)));
        connect.setOnClickListener(v -> runDiscordOperation(host, connection,
                "Connecting Discord RPC…", "Discord RPC connected.",
                () -> showDiscordSettingsPanel(host, connection),
                () -> hostGatewayClient.connectDiscord(connection, false)));
        refresh.setOnClickListener(v -> showDiscordSettingsPanel(host, connection));
        back.setOnClickListener(v -> parent.run());
        lastChannelStatus.setVisibility(autoConnectEnabled ? View.VISIBLE : View.GONE);
        autoJoin.setVisibility(autoConnectEnabled ? View.VISIBLE : View.GONE);
        showSidePanel("DISCORD", "Settings",
                "Automation applies to the selected host and Discord profile.",
                parent, status, autoConnect, lastChannelStatus, autoJoin,
                start, connect, refresh, back);

        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordStatus discord = hostGatewayClient.getDiscordStatus(connection);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    if (!discord.bridgeOnline) {
                        status.setText("Bridge profile: offline" +
                                (discord.error.isEmpty() ? "" : "\n" + discord.error));
                    } else if (!discord.rpcConnected) {
                        status.setText("Bridge profile: online\n" +
                                discordUnavailableMessage(discord));
                    } else if (!discord.authenticated) {
                        status.setText("Bridge profile: online\nDiscord RPC: authorization required");
                    } else {
                        status.setText("Bridge profile: online\nDiscord RPC: authenticated");
                    }
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    status.setText("Profile status unavailable\n" + friendlyGatewayError(error));
                });
            }
        });
    }

    private void showDiscordSavedChannelsPanel(Host host,
                                                HostGatewayClient.Connection connection,
                                                boolean force) {
        int request = showDiscordLoadingPanel(host, "FAVORITES & RECENT",
                "Loading saved channels…", () -> showDiscordPanel(host, connection));
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordHome home = hostGatewayClient.getDiscordHome(connection, force);
                List<HostGatewayClient.DiscordChannel> channels = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (HostGatewayClient.DiscordChannel channel : home.favorites) {
                    if (seen.add(channel.id)) channels.add(channel);
                }
                for (HostGatewayClient.DiscordChannel channel : home.recent) {
                    if (seen.add(channel.id)) channels.add(channel);
                }
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    renderDiscordChannels(host, connection, "FAVORITES & RECENT",
                            "Saved voice channels for " + host.name + ".", channels,
                            () -> showDiscordSavedChannelsPanel(host, connection, true),
                            () -> showDiscordPanel(host, connection));
                });
            } catch (IOException error) {
                showDiscordLoadError(request, host, "FAVORITES & RECENT", error,
                        () -> showDiscordSavedChannelsPanel(host, connection, true),
                        () -> showDiscordPanel(host, connection));
            }
        });
    }

    private void showDiscordServersPanel(Host host, HostGatewayClient.Connection connection,
                                         boolean force) {
        Runnable exitAction = communityExitAction != null
                ? communityExitAction : () -> hideSidePanel(true);
        int request = showDiscordLoadingPanel(host, "Servers",
                "Loading your Discord servers…", exitAction);
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordHome home = hostGatewayClient.getDiscordHome(connection, force);
                HostGatewayClient.DiscordVoice loadedVoice = null;
                try {
                    loadedVoice = hostGatewayClient.getDiscordVoice(connection, force);
                } catch (IOException error) {
                    Log.w(TAG, "Unable to load Discord voice on server browser", error);
                }
                HostGatewayClient.DiscordVoice voice = loadedVoice;
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    List<View> actions = new ArrayList<>();
                    if (voice != null && voice.connected) {
                        actions.add(discordSection("CURRENT VOICE"));
                        actions.add(panelStatus("●  " + voice.channelName + "  ·  " +
                                voice.participants + " participant" +
                                (voice.participants == 1 ? "" : "s")));
                        TextView mute = discordAction(
                                voice.muted ? "MIC MUTED  ·  UNMUTE" : "MIC ON  ·  MUTE",
                                voice.muted ? DISCORD_RED : DISCORD_GREEN);
                        TextView leave = discordAction("LEAVE", DISCORD_RED);
                        mute.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Toggling microphone…",
                                    "Microphone updated.",
                                    () -> showDiscordServersPanel(host, connection, true),
                                    () -> hostGatewayClient.setDiscordVoiceFlag(
                                            connection, "mute", "toggle"));
                        });
                        leave.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Leaving voice…",
                                    "Voice disconnected.",
                                    () -> showDiscordServersPanel(host, connection, true),
                                    () -> hostGatewayClient.leaveDiscordChannel(connection));
                        });
                        actions.add(panelActionRow(mute, leave));
                        updateDiscordShortcutVoice(voice);
                    } else {
                        actions.add(panelStatus("Voice disconnected  ·  Select a server and channel to join."));
                        updateDiscordShortcutVoice(null);
                    }

                    actions.add(discordSection("TOOLS"));
                    TextView settings = discordAction("SETTINGS", DISCORD_TOOL);
                    TextView usb = discordAction("USB", DISCORD_TOOL);
                    TextView refresh = discordAction("REFRESH", DISCORD_SURFACE);
                    settings.setOnClickListener(v -> {
                        pulseDiscordAction(v);
                        showDiscordSettingsPanel(host, connection);
                    });
                    usb.setOnClickListener(v -> {
                        pulseDiscordAction(v);
                        showVirtualHerePanel(host, connection, false);
                    });
                    refresh.setOnClickListener(v -> {
                        pulseDiscordAction(v);
                        showDiscordServersPanel(host, connection, true);
                    });
                    actions.add(panelActionRow(settings, usb));
                    actions.add(refresh);

                    actions.add(discordSection("YOUR SERVERS"));
                    if (home.guilds.isEmpty()) {
                        actions.add(panelStatus("No Discord servers are available for this account."));
                    } else {
                        for (HostGatewayClient.DiscordGuild guild : home.guilds) {
                            TextView action = discordAction(guild.name + "  ›", DISCORD_BLURPLE);
                            action.setOnClickListener(v -> {
                                pulseDiscordAction(v);
                                showDiscordChannelsPanel(host, connection, guild, false);
                            });
                            actions.add(action);
                        }
                    }
                    showSidePanel("DISCORD", "Servers",
                            host.name + "  ·  Profile " + connection.profileId,
                            exitAction,
                            actions.toArray(new View[0]));
                    activateDiscordControllerShortcuts(host, connection, null, null, null);
                });
            } catch (IOException error) {
                showDiscordLoadError(request, host, "Servers", error,
                        () -> showDiscordServersPanel(host, connection, true),
                        exitAction);
            }
        });
    }

    private void showDiscordChannelsPanel(Host host, HostGatewayClient.Connection connection,
                                          HostGatewayClient.DiscordGuild guild, boolean force) {
        int request = showDiscordLoadingPanel(host, guild.name,
                "Loading voice channels…", () -> showDiscordServersPanel(host, connection, false));
        executor.submit(() -> {
            try {
                List<HostGatewayClient.DiscordChannel> channels =
                        hostGatewayClient.getDiscordChannels(connection, guild, force);
                HostGatewayClient.DiscordVoice loadedVoice = null;
                try {
                    loadedVoice = hostGatewayClient.getDiscordVoice(connection, force);
                } catch (IOException error) {
                    Log.w(TAG, "Unable to load Discord voice on channel browser", error);
                }
                HostGatewayClient.DiscordVoice voice = loadedVoice;
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    renderDiscordChannels(host, connection, guild,
                            channels, voice,
                            () -> showDiscordChannelsPanel(host, connection, guild, true),
                            () -> showDiscordServersPanel(host, connection, false));
                });
            } catch (IOException error) {
                showDiscordLoadError(request, host, guild.name, error,
                        () -> showDiscordChannelsPanel(host, connection, guild, true),
                        () -> showDiscordServersPanel(host, connection, false));
            }
        });
    }

    private void renderDiscordChannels(Host host, HostGatewayClient.Connection connection,
                                       HostGatewayClient.DiscordGuild guild,
                                       List<HostGatewayClient.DiscordChannel> channels,
                                       HostGatewayClient.DiscordVoice voice,
                                       Runnable refreshAction, Runnable backAction) {
        List<View> actions = new ArrayList<>();
        if (voice != null && voice.connected) {
            actions.add(discordSection("CURRENT VOICE"));
            actions.add(panelStatus("●  " + voice.channelName + "  ·  " +
                    voice.participants + " participant" +
                    (voice.participants == 1 ? "" : "s")));
            TextView people = discordAction("PEOPLE  ·  " + voice.participants,
                    DISCORD_BLURPLE);
            TextView leave = discordAction("LEAVE", DISCORD_RED);
            people.setOnClickListener(v -> {
                pulseDiscordAction(v);
                showDiscordParticipantsPanel(host, connection, true);
            });
            leave.setOnClickListener(v -> {
                pulseDiscordAction(v);
                runDiscordOperation(host, connection, "Leaving voice…",
                        "Voice disconnected.", refreshAction,
                        () -> hostGatewayClient.leaveDiscordChannel(connection));
            });
            actions.add(panelActionRow(people, leave));
            updateDiscordShortcutVoice(voice);
        }
        actions.add(discordSection("TOOLS"));
        TextView settings = discordAction("SETTINGS", DISCORD_TOOL);
        TextView usb = discordAction("USB", DISCORD_TOOL);
        TextView refresh = discordAction("REFRESH", DISCORD_SURFACE);
        settings.setOnClickListener(v -> {
            pulseDiscordAction(v);
            showDiscordSettingsPanel(host, connection);
        });
        usb.setOnClickListener(v -> {
            pulseDiscordAction(v);
            showVirtualHerePanel(host, connection, false);
        });
        refresh.setOnClickListener(v -> {
            pulseDiscordAction(v);
            refreshAction.run();
        });
        actions.add(panelActionRow(settings, usb));
        actions.add(refresh);

        actions.add(discordSection("VOICE CHANNELS"));
        if (channels.isEmpty()) {
            actions.add(panelStatus("No voice channels are available."));
        } else {
            for (HostGatewayClient.DiscordChannel channel : channels) {
                boolean active = voice != null && voice.connected &&
                        channel.id.equals(voice.channelId);
                String count = channel.people >= 0 ? "  ·  " + channel.people + " people" : "";
                String prefix = active ? "●  " : channel.favorite ? "★  " : "#  ";
                TextView action = discordAction(prefix + channel.name + count,
                        active ? DISCORD_GREEN : DISCORD_SURFACE);
                action.setOnClickListener(v -> {
                    pulseDiscordAction(v);
                    showDiscordChannelPanel(host, connection, guild, channel, false);
                });
                actions.add(action);
            }
        }
        showSidePanel("DISCORD", guild.name, "Select a channel to see its people.",
                backAction, actions.toArray(new View[0]));
    }

    private void renderDiscordChannels(Host host, HostGatewayClient.Connection connection,
                                       String title, String details,
                                       List<HostGatewayClient.DiscordChannel> channels,
                                       Runnable refreshAction, Runnable backAction) {
        List<View> actions = new ArrayList<>();
        actions.add(discordSection("VOICE CHANNELS"));
        for (HostGatewayClient.DiscordChannel channel : channels) {
            TextView action = discordAction((channel.favorite ? "★  " : "#  ") +
                    channel.name, DISCORD_SURFACE);
            action.setOnClickListener(v -> {
                pulseDiscordAction(v);
                showDiscordChannelPanel(host, connection,
                        new HostGatewayClient.DiscordGuild(channel.guildId, channel.guildName),
                        channel, false);
            });
            actions.add(action);
        }
        TextView refresh = discordAction("REFRESH", DISCORD_SURFACE);
        refresh.setOnClickListener(v -> {
            pulseDiscordAction(v);
            refreshAction.run();
        });
        actions.add(refresh);
        showSidePanel("DISCORD", title, details, backAction, actions.toArray(new View[0]));
    }

    private void showDiscordChannelPanel(Host host,
                                         HostGatewayClient.Connection connection,
                                         HostGatewayClient.DiscordGuild guild,
                                         HostGatewayClient.DiscordChannel channel,
                                         boolean force) {
        Runnable backAction = () -> showDiscordChannelsPanel(host, connection, guild, false);
        int request = showDiscordLoadingPanel(host, "# " + channel.name,
                "Loading channel…", backAction);
        executor.submit(() -> {
            try {
                HostGatewayClient.DiscordVoice voice =
                        hostGatewayClient.getDiscordVoice(connection, force);
                mainHandler.post(() -> {
                    if (!isCurrentIntegrationPanel(request, host.uuid)) return;
                    boolean active = voice.connected && channel.id.equals(voice.channelId);
                    List<View> views = new ArrayList<>();
                    views.add(discordSection("VOICE CHANNEL"));
                    if (active) {
                        views.add(panelStatus("●  Connected  ·  " + voice.participants +
                                " participant" + (voice.participants == 1 ? "" : "s")));
                        TextView mute = discordAction(
                                voice.muted ? "MIC MUTED  ·  UNMUTE" : "MIC ON  ·  MUTE",
                                voice.muted ? DISCORD_RED : DISCORD_GREEN);
                        TextView leave = discordAction("LEAVE", DISCORD_RED);
                        mute.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Toggling microphone…",
                                    "Microphone updated.",
                                    () -> showDiscordChannelPanel(
                                            host, connection, guild, channel, true),
                                    () -> hostGatewayClient.setDiscordVoiceFlag(
                                            connection, "mute", "toggle"));
                        });
                        leave.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Leaving #" + channel.name + "…",
                                    "Voice disconnected.",
                                    () -> showDiscordChannelPanel(
                                            host, connection, guild, channel, true),
                                    () -> hostGatewayClient.leaveDiscordChannel(connection));
                        });
                        views.add(panelActionRow(mute, leave));
                        views.add(discordSection("PEOPLE"));
                        if (voice.participantList.isEmpty()) {
                            views.add(panelStatus("Nobody else is visible in this channel."));
                        }
                        for (HostGatewayClient.DiscordParticipant participant :
                                voice.participantList) {
                            TextView person = panelStatus("");
                            int[] volume = new int[]{participant.volume};
                            boolean[] muted = new boolean[]{participant.muted};
                            renderDiscordParticipantStatus(person, participant,
                                    volume[0], muted[0]);
                            views.add(person);
                            if (participant.self) continue;
                            SeekBar slider = panelVolumeSlider(volume[0]);
                            TextView participantMute = discordAction(
                                    muted[0] ? "UNMUTE" : "MUTE",
                                    muted[0] ? DISCORD_GREEN : DISCORD_RED);
                            AtomicBoolean muteBusy = new AtomicBoolean(false);
                            AtomicBoolean volumeBusy = new AtomicBoolean(false);
                            AtomicInteger desiredVolume = new AtomicInteger(volume[0]);
                            Runnable commitVolume = () -> queueDiscordParticipantVolume(
                                    request, host, connection, participant, volume,
                                    muted, person, desiredVolume, volumeBusy);
                            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                @Override
                                public void onProgressChanged(SeekBar seekBar, int progress,
                                                              boolean fromUser) {
                                    if (!fromUser) return;
                                    int snapped = Math.max(0, Math.min(200,
                                            Math.round(progress / 10f) * 10));
                                    if (snapped != progress) seekBar.setProgress(snapped);
                                    volume[0] = snapped;
                                    desiredVolume.set(snapped);
                                    renderDiscordParticipantStatus(person, participant,
                                            volume[0], muted[0]);
                                }

                                @Override public void onStartTrackingTouch(SeekBar seekBar) {}

                                @Override
                                public void onStopTrackingTouch(SeekBar seekBar) {
                                    commitVolume.run();
                                }
                            });
                            slider.setOnKeyListener((v, keyCode, event) -> {
                                if (event.getAction() == KeyEvent.ACTION_UP &&
                                        (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                                    commitVolume.run();
                                }
                                return false;
                            });
                            participantMute.setOnClickListener(v -> {
                                pulseDiscordAction(v);
                                runDiscordParticipantAction(request, host, connection,
                                        participant, volume, muted, person,
                                        participantMute, muteBusy, 0);
                            });
                            views.add(panelWeightedActionRow(slider, participantMute));
                        }
                        updateDiscordShortcutVoice(voice);
                        activateDiscordControllerShortcuts(host, connection, null, mute, leave);
                    } else {
                        if (voice.connected) {
                            views.add(panelStatus("You are currently connected to ● " +
                                    voice.channelName + ". Joining here will switch channels."));
                            TextView leaveCurrent = discordAction("LEAVE " + voice.channelName,
                                    DISCORD_RED);
                            leaveCurrent.setOnClickListener(v -> {
                                pulseDiscordAction(v);
                                runDiscordOperation(host, connection, "Leaving voice…",
                                        "Voice disconnected.",
                                        () -> showDiscordChannelPanel(
                                                host, connection, guild, channel, true),
                                        () -> hostGatewayClient.leaveDiscordChannel(connection));
                            });
                            views.add(leaveCurrent);
                        } else {
                            views.add(panelStatus((channel.people >= 0
                                    ? channel.people + " people visible  ·  " : "") +
                                    "Join to see and control participants."));
                        }
                        TextView join = discordAction("JOIN #" + channel.name, DISCORD_GREEN);
                        join.setOnClickListener(v -> {
                            pulseDiscordAction(v);
                            runDiscordOperation(host, connection, "Joining #" + channel.name + "…",
                                    "Connected to #" + channel.name + ".",
                                    () -> showDiscordChannelPanel(
                                            host, connection, guild, channel, true),
                                    () -> joinDiscordChannelAndRemember(host, connection, channel));
                        });
                        views.add(join);
                        updateDiscordShortcutVoice(voice.connected ? voice : null);
                    }
                    views.add(discordSection("TOOLS"));
                    TextView settings = discordAction("SETTINGS", DISCORD_TOOL);
                    TextView usb = discordAction("USB", DISCORD_TOOL);
                    settings.setOnClickListener(v -> {
                        pulseDiscordAction(v);
                        showDiscordSettingsPanel(host, connection);
                    });
                    usb.setOnClickListener(v -> {
                        pulseDiscordAction(v);
                        showVirtualHerePanel(host, connection, false);
                    });
                    views.add(panelActionRow(settings, usb));
                    showSidePanel("DISCORD", "# " + channel.name, guild.name,
                            backAction, views.toArray(new View[0]));
                });
            } catch (IOException error) {
                showDiscordLoadError(request, host, "# " + channel.name, error,
                        () -> showDiscordChannelPanel(host, connection, guild, channel, true),
                        backAction);
            }
        });
    }

    private int showDiscordLoadingPanel(Host host, String title, String message, Runnable backAction) {
        int request = integrationPanelRequest.incrementAndGet();
        // Keep the current Discord page in place while a child page or refresh is loaded.
        // Replacing it with a transient loading page caused a visible double layout/focus jump.
        if (modalLayer != null && modalLayer.getVisibility() == View.VISIBLE) return request;
        TextView status = panelStatus(message);
        TextView back = panelAction("BACK");
        back.setOnClickListener(v -> backAction.run());
        showSidePanel("DISCORD", title, null, backAction, status, back);
        return request;
    }

    private void showDiscordLoadError(int request, Host host, String title, Throwable error,
                                      Runnable retryAction, Runnable backAction) {
        mainHandler.post(() -> {
            if (!isCurrentIntegrationPanel(request, host.uuid)) return;
            TextView status = panelStatus("Unable to load Discord\n" + friendlyGatewayError(error));
            TextView retry = panelAction("RETRY");
            TextView back = panelAction("BACK");
            retry.setOnClickListener(v -> retryAction.run());
            back.setOnClickListener(v -> backAction.run());
            showSidePanel("DISCORD", title, null, backAction, status, retry, back);
        });
    }

    private void runDiscordOperation(Host host, HostGatewayClient.Connection connection,
                                     String progress, String success, Runnable returnAction,
                                     DiscordOperation operation) {
        Toast.makeText(this, progress, Toast.LENGTH_SHORT).show();
        executor.submit(() -> {
            try {
                operation.run();
                mainHandler.post(() -> {
                    Toast.makeText(this, success, Toast.LENGTH_SHORT).show();
                    returnAction.run();
                });
            } catch (IOException error) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Discord: " + friendlyGatewayError(error),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void joinDiscordChannelAndRemember(Host host,
                                               HostGatewayClient.Connection connection,
                                               HostGatewayClient.DiscordChannel channel)
            throws IOException {
        hostGatewayClient.joinDiscordChannel(connection, channel);
        hostGatewayStore.saveLastDiscordChannel(host.uuid, connection.profileId,
                channel.id, channel.guildId, channel.guildName, channel.name);
    }

    private interface DiscordOperation {
        void run() throws IOException;
    }

    private static String discordUnavailableMessage(HostGatewayClient.DiscordStatus status) {
        if (status != null && status.error != null && !status.error.trim().isEmpty()) {
            return status.error.trim();
        }
        return "Discord is not active in this Bridge profile. Open Discord Settings to start it.";
    }

    private static String friendlyGatewayError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        message = message.trim();
        return message.length() > 240 ? message.substring(0, 240) + "…" : message;
    }

    private void showSessionPanel() {
        clearDiscordControllerShortcuts();
        StreamStatus status = currentStreamStatus;
        if (!isActiveStream(status)) {
            Toast.makeText(this, "The active stream is no longer available.", Toast.LENGTH_LONG).show();
            refreshSessionStatusAsync();
            return;
        }

        String app = status.app != null && !status.app.isEmpty() ? status.app : "Active stream";
        StringBuilder details = new StringBuilder();
        if (status.computer != null && !status.computer.isEmpty()) details.append(status.computer);
        if (status.width > 0 && status.height > 0) {
            appendDetail(details, status.width + "×" + status.height + (status.fps > 0 ? " @ " + status.fps + " FPS" : ""));
        }
        if (status.bitrateKbps > 0) appendDetail(details, Math.round(status.bitrateKbps / 1000f) + " Mbps");
        if (status.hdr) appendDetail(details, "HDR");
        if (status.startedAt > 0) {
            appendDetail(details, "Playing for " + formatElapsed(System.currentTimeMillis() - status.startedAt));
        }

        TextView returnAction = panelAction("▶  RETURN TO GAME");
        TextView disconnectAction = panelAction("DISCONNECT THIS TV");
        TextView quitAction = panelAction("END APP ON HOST");
        returnAction.setOnClickListener(v -> {
            dismissSidePanelImmediately();
            returnToActiveStream(status.moonlightPackage);
        });
        disconnectAction.setOnClickListener(v -> {
            hideSidePanel(true);
            confirmStreamControl(ACTION_DISCONNECT_STREAM, false);
        });
        quitAction.setOnClickListener(v -> {
            hideSidePanel(true);
            confirmStreamControl(ACTION_QUIT_STREAM_APP, true);
        });
        showSidePanel("ACTIVE SESSION", app,
                details.length() > 0 ? details.toString() : "Moonlight is streaming to this TV.",
                returnAction, disconnectAction, quitAction);
    }

    private static void appendDetail(StringBuilder details, String value) {
        if (details.length() > 0) details.append(" · ");
        details.append(value);
    }

    private void confirmStreamControl(String action, boolean quitHostApp) {
        new AlertDialog.Builder(this)
                .setTitle(quitHostApp ? "End app on host?" : "Disconnect this TV?")
                .setMessage(quitHostApp
                        ? "The running application will be closed on the host and this stream will end."
                        : "The stream will end, but the application will remain running on the host.")
                .setPositiveButton(quitHostApp ? "End app" : "Disconnect",
                        (dialog, which) -> sendStreamControl(action))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendStreamControl(String action) {
        StreamStatus status = currentStreamStatus;
        if (!isActiveStream(status) || status.moonlightPackage == null) return;
        Intent intent = new Intent(action);
        intent.setPackage(status.moonlightPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
            overridePendingTransition(0, 0);
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to control active stream", error);
            Toast.makeText(this, "Moonlight rejected the session control request.", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshHostAvailabilityAsync() {
        if (visibleHosts.isEmpty() || !hostProbeRunning.compareAndSet(false, true)) return;
        int dashboardToken = dashboardRequests.currentToken();
        if (!isCurrentDashboardRequest(dashboardToken)) {
            hostProbeRunning.set(false);
            return;
        }
        int request = hostProbeRequest.incrementAndGet();
        lastHostProbeAt = System.currentTimeMillis();
        List<Host> hosts = new ArrayList<>(visibleHosts);
        executor.execute(() -> {
            Map<String, Boolean> availability = new HashMap<>();
            for (Host host : hosts) availability.put(host.uuid, findReadyPort(host) > 0);
            mainHandler.post(() -> {
                if (request != hostProbeRequest.get()) return;
                hostProbeRunning.set(false);
                if (!isCurrentDashboardRequest(dashboardToken)) return;
                for (Host host : hosts) {
                    if (!isActiveForHost(currentStreamStatus, host)) {
                        boolean online = Boolean.TRUE.equals(availability.get(host.uuid));
                        if (online) {
                            setHostStatus(host, "● ONLINE · Host ready" + lastPlayedSuffix(host), 0xFF69F0AE);
                        } else if (host.macAddress != null && !host.macAddress.isEmpty()) {
                            setHostStatus(host, "◐ SLEEPING · Wake-on-LAN ready" + lastPlayedSuffix(host), 0xFFFFB74D);
                        } else {
                            setHostStatus(host, "○ OFFLINE" + lastPlayedSuffix(host), 0xFFFF8A80);
                        }
                    }
                }
                updateSessionHostStatus();
            });
        });
    }

    private void updateSessionHostStatus() {
        StreamStatus status = currentStreamStatus;
        if (!isActiveStream(status)) return;
        for (Host host : visibleHosts) {
            if (!isActiveForHost(status, host)) continue;
            setHostStatus(host, "● THIS TV · Host ready", 0xFF69F0AE);
        }
    }

    private static boolean isActiveForHost(StreamStatus status, Host host) {
        if (!isActiveStream(status) || host == null) return false;
        if (status.computer != null && host.name != null && status.computer.equalsIgnoreCase(host.name)) return true;
        return status.host != null && host.address != null && status.host.equalsIgnoreCase(host.address);
    }

    private void setHostStatus(Host host, String label, int color) {
        TextView view = hostStatusViews.get(host.uuid);
        if (view == null) return;
        view.setText(label);
        view.setTextColor(color);
    }

    private String lastPlayedSuffix(Host host) {
        long timestamp = hostPlayedAt(host);
        StreamStatus status = currentStreamStatus;
        if (status != null && !isActiveStream(status) && isSameHost(status, host)) {
            timestamp = Math.max(timestamp, status.updatedAt);
        }
        return timestamp > 0 ? " · Played " + formatRelative(System.currentTimeMillis() - timestamp) : "";
    }

    private long appPlayedAt(Host host, StreamApp app) {
        SharedPreferences prefs = history();
        long timestamp = prefs.getLong(appHistoryKey(host.uuid, app.appId), 0L);
        if (timestamp <= 0 && host.uuid.equals(prefs.getString(PREF_LAST_HOST_UUID, null)) &&
                app.appId == prefs.getInt(PREF_LAST_APP_ID, -1)) {
            timestamp = prefs.getLong(PREF_LAST_LAUNCH_AT, 0L);
        }
        return timestamp;
    }

    private long hostPlayedAt(Host host) {
        SharedPreferences prefs = history();
        long timestamp = prefs.getLong(hostHistoryKey(host.uuid), 0L);
        if (timestamp <= 0 && host.uuid.equals(prefs.getString(PREF_LAST_HOST_UUID, null))) {
            timestamp = prefs.getLong(PREF_LAST_LAUNCH_AT, 0L);
        }
        return timestamp;
    }

    private static String appHistoryKey(String hostUuid, int appId) {
        return "played_at.app." + hostUuid + "." + appId;
    }

    private static String hostHistoryKey(String hostUuid) {
        return "played_at.host." + hostUuid;
    }

    private static boolean isSameHost(StreamStatus status, Host host) {
        if (status == null || host == null) return false;
        if (status.computer != null && host.name != null && status.computer.equalsIgnoreCase(host.name)) return true;
        return status.host != null && host.address != null && status.host.equalsIgnoreCase(host.address);
    }

    private static String formatElapsed(long milliseconds) {
        long seconds = Math.max(0L, milliseconds / 1000L);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String formatRelative(long milliseconds) {
        long minutes = Math.max(0L, milliseconds / 60_000L);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }

    private static String formatStreamQuality(StreamStatus status) {
        if (status == null || status.height <= 0) return "";
        String resolution;
        if (status.height >= 2100) {
            resolution = "4K";
        } else {
            resolution = status.height + "p";
        }
        return status.fps > 0 ? resolution + status.fps : resolution;
    }

    private SharedPreferences history() {
        return getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE);
    }

    private void openMoonlightSettings() {
        List<String> packages = new ArrayList<>();
        if (selectedHost != null) packages.add(selectedHost.moonlightPackage);
        for (MoonlightTarget target : MOONLIGHT_TARGETS) {
            if (!packages.contains(target.packageName)) packages.add(target.packageName);
        }
        for (String packageName : packages) {
            Intent intent = new Intent(ACTION_OPEN_SETTINGS);
            intent.setPackage(packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (intent.resolveActivity(getPackageManager()) == null) continue;
            try {
                focusAfterExternalActivity = lastContentFocus != null ? lastContentFocus : settingsButton;
                restoreFocusAfterResume = true;
                dismissSidePanelImmediately();
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return;
            } catch (RuntimeException ignored) {
                restoreFocusAfterResume = false;
                focusAfterExternalActivity = null;
                if (settingsButton != null) settingsButton.requestFocus();
            }
        }
        Toast.makeText(this, "Compatible Moonlight X settings are not available.", Toast.LENGTH_LONG).show();
    }

    private void sendWakeOnLan(String mac) {
        byte[] macBytes = parseMac(mac);
        if (macBytes == null) return;
        byte[] payload = new byte[6 + 16 * macBytes.length];
        for (int i = 0; i < 6; i++) payload[i] = (byte) 0xFF;
        for (int i = 6; i < payload.length; i += macBytes.length) System.arraycopy(macBytes, 0, payload, i, macBytes.length);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            Set<InetAddress> destinations = new HashSet<>();
            destinations.add(InetAddress.getByName("255.255.255.255"));
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InterfaceAddress address : iface.getInterfaceAddresses()) {
                    if (address.getBroadcast() != null) destinations.add(address.getBroadcast());
                }
            }
            for (InetAddress destination : destinations) {
                socket.send(new DatagramPacket(payload, payload.length, destination, 9));
            }
        } catch (Exception ignored) {}
    }

    private static byte[] parseMac(String value) {
        if (value == null) return null;
        String[] parts = value.split("[:-]");
        if (parts.length != 6) return null;
        byte[] bytes = new byte[6];
        try {
            for (int i = 0; i < 6; i++) bytes[i] = (byte) Integer.parseInt(parts[i], 16);
            return bytes;
        } catch (NumberFormatException error) { return null; }
    }

    private void showHome() {
        launchCancelled.set(true);
        mainHandler.removeCallbacksAndMessages(null);
        slideshow.stop();
        loadingMessage.setTextColor(0xFFE5E8F5);
        loadingStatus.setTextColor(0xFFB8C0D9);
        loadingStatus.setText("");
        loadingLayer.setVisibility(View.GONE);
        homeLayer.setVisibility(View.VISIBLE);
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private LinearLayout cardBase(int width, int height) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setFocusable(true);
        card.setClickable(true);
        card.setSoundEffectsEnabled(false);
        card.setMinimumWidth(width);
        card.setMinimumHeight(height);
        styleCard(card, false);
        return card;
    }

    private void styleCard(View card, boolean focused) {
        int focusedTop = withAlpha(blendColor(0xFF715BA8, glassAccentColor, 0.48f), 0xC8);
        int focusedBottom = withAlpha(blendColor(0xFF403362, glassAccentColor, 0.32f), 0xB1);
        int restingMiddle = withAlpha(blendColor(0xFF242B3D, glassAccentColor, 0.18f), 0x34);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{focusedTop, focusedBottom}
                        : new int[]{0x18FFFFFF, restingMiddle, 0x5A131825});
        background.setCornerRadius(dp(14));
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xFFECE7FF : 0x5C9AA6C4);
        card.setBackground(background);
        card.setElevation(dp(focused ? 9 : 3));
        card.setTranslationZ(dp(focused ? 2 : 0));
        animateFocusScale(card, focused ? 1.022f : 1f, 125);
    }

    private void styleHostCard(View card, boolean focused, boolean selected) {
        int focusedTop = withAlpha(blendColor(0xFF62577F, glassAccentColor, 0.36f), 0xAF);
        int focusedBottom = withAlpha(blendColor(0xFF353047, glassAccentColor, 0.24f), 0x9A);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                focused
                        ? new int[]{focusedTop, focusedBottom}
                        : selected
                        ? new int[]{0x18FFFFFF, 0x38212838, 0x58161B28}
                        : new int[]{0x10FFFFFF, 0x301C2333, 0x50131825});
        background.setCornerRadius(dp(12));
        background.setStroke(
                dp(focused ? 2 : 1),
                focused ? 0xFFDCD5F2 : selected ? 0x4C8B94AD : 0x407C89B2);
        card.setBackground(background);
        card.setElevation(dp(focused ? 8 : selected ? 4 : 2));
        card.setTranslationZ(dp(focused ? 3 : 0));
        animateFocusScale(card, focused ? 1.015f : 1f, 120);
    }

    private void stylePrimaryButton(View button, boolean focused) {
        int top = withAlpha(blendColor(0xFF644FA0, glassAccentColor, 0.42f), focused ? 0xE0 : 0xA9);
        int bottom = withAlpha(blendColor(0xFF42366D, glassAccentColor, 0.28f), focused ? 0xCA : 0x8F);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        background.setCornerRadius(dp(12));
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xFFF0EBFF : 0x706E5AA4);
        button.setBackground(background);
        button.setElevation(dp(focused ? 8 : 3));
        animateFocusScale(button, focused ? 1.01f : 1f, 120);
    }

    private void styleCompactButton(View button, boolean focused) {
        int top = withAlpha(blendColor(0xFF58478F, glassAccentColor, 0.38f), focused ? 0xC7 : 0x32);
        int bottom = withAlpha(blendColor(0xFF342B59, glassAccentColor, 0.22f), focused ? 0xB0 : 0x65);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        background.setCornerRadius(dp(10));
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xFFE9E3FF : 0x387C89B2);
        button.setBackground(background);
        // Compact actions already use a stronger stroke for focus. Scaling a
        // MATCH_PARENT panel action makes its background extend outside the
        // layout slot and appear to escape the surrounding frame.
        button.animate().cancel();
        button.setScaleX(1f);
        button.setScaleY(1f);
    }

    private void animateFocusScale(View view, float scale, long duration) {
        view.animate().cancel();
        if (reducedMotion || !view.isLaidOut()) {
            view.setScaleX(scale);
            view.setScaleY(scale);
        } else {
            view.animate().scaleX(scale).scaleY(scale).setDuration(duration).start();
        }
    }

    private void refreshGlassStyles() {
        refreshCardRow(controllerRow, false);
        refreshCardRow(appRow, false);
        refreshCardRow(hostRow, true);
        if (resumeButton != null) stylePrimaryButton(resumeButton, resumeButton.hasFocus());
        if (sessionButton != null) styleCompactButton(sessionButton, sessionButton.hasFocus());
        if (communityButton != null && communityButton.getVisibility() == View.VISIBLE) {
            styleCompactButton(communityButton, communityButton.hasFocus());
        }
        if (settingsButton != null) styleCompactButton(settingsButton, settingsButton.hasFocus());
        if (sidePanel != null) {
            List<View> actions = new ArrayList<>();
            collectFocusableViews(sidePanel, actions);
            for (View action : actions) {
                if ("discord_action".equals(action.getTag())) continue;
                styleCompactButton(action, action.hasFocus());
            }
        }
    }

    private void refreshCardRow(LinearLayout row, boolean hosts) {
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (!child.isFocusable()) continue;
            if (hosts) styleHostCard(child, child.hasFocus(), child == selectedHostCard);
            else styleCard(child, child.hasFocus());
        }
    }

    private static int blendColor(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView sectionLabel(String value) { return text(value, 13, 0xFF9CA6C5, true); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }
    private HorizontalScrollView horizontalScroll() {
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.setHorizontalScrollBarEnabled(false);
        view.setClipToPadding(false);
        view.setClipChildren(false);
        view.setPadding(0, 0, dp(12), 0);
        view.setFocusable(false);
        return view;
    }
    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }
    private LinearLayout.LayoutParams cardSpacing() { LinearLayout.LayoutParams p = wrap(); p.rightMargin = dp(14); return p; }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static boolean isGamepad(InputDevice device) { int sources = device.getSources(); return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK; }
    private static String compactControllerName(String name) { return name != null && name.toLowerCase(Locale.ROOT).contains("dualsense") ? "DualSense" : name != null ? name : "Controller"; }
    private static String value(Cursor cursor, String column) { int i = cursor.getColumnIndex(column); return i >= 0 && !cursor.isNull(i) ? cursor.getString(i) : null; }
    private static int integer(Cursor cursor, String column, int fallback) { int i = cursor.getColumnIndex(column); return i >= 0 && !cursor.isNull(i) ? cursor.getInt(i) : fallback; }
    private static long longValue(Cursor cursor, String column, long fallback) { int i = cursor.getColumnIndex(column); return i >= 0 && !cursor.isNull(i) ? cursor.getLong(i) : fallback; }

    private static final class ControllerInfo {
        final int deviceId;
        final String descriptor;
        final String name;
        final int percentage;
        final boolean charging;

        ControllerInfo(int deviceId, String descriptor, String name, int percentage, boolean charging) {
            this.deviceId = deviceId;
            this.descriptor = descriptor;
            this.name = name;
            this.percentage = percentage;
            this.charging = charging;
        }
    }

    private enum BluetoothAction { POWER_OFF, UNPAIR }

    private static final class MoonlightTarget {
        final String packageName;
        final Uri hostsUri;
        final Uri statusUri;

        MoonlightTarget(String packageName) {
            this.packageName = packageName;
            hostsUri = Uri.parse("content://hosts." + packageName + "/hosts");
            statusUri = Uri.parse("content://streamstatus." + packageName + "/current");
        }
    }

    private static final class Host {
        String uuid;
        String name;
        String address;
        String macAddress;
        String moonlightPackage;
        int port;
    }
    private static final class StreamApp { int appId; String name; Uri posterUri; }
    private static final class StreamStatus {
        String state;
        String stage;
        String host;
        String app;
        String computer;
        String moonlightPackage;
        int bitrateKbps;
        int width;
        int height;
        int fps;
        boolean hdr;
        boolean activityAlive;
        long startedAt;
        long updatedAt;
    }

    private static final class ArtworkBackdropResult {
        final Bitmap backdrop;
        final Bitmap hero;

        ArtworkBackdropResult(Bitmap backdrop, Bitmap hero) {
            this.backdrop = backdrop;
            this.hero = hero;
        }

        void recycle() {
            if (backdrop != null && !backdrop.isRecycled()) backdrop.recycle();
            if (hero != null && !hero.isRecycled()) hero.recycle();
        }
    }

    private static class GenerativeBackdrop extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        GenerativeBackdrop(android.content.Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            paint.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF090B14, 0xFF171633, 0xFF311A58}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint); paint.setShader(null);
            paint.setColor(0x287C4DFF); canvas.drawCircle(w * .80f, h * .18f, h * .42f, paint);
            paint.setColor(0x2037B5FF); canvas.drawCircle(w * .18f, h * .88f, h * .50f, paint);
        }
    }

    private static final class GenerativeSlideshow extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running;
        private long startedAt;
        GenerativeSlideshow(android.content.Context context) { super(context); }
        void start(boolean animate) { running = animate; startedAt = SystemClock.uptimeMillis(); invalidate(); }
        long getStartedAt() { return startedAt; }
        void stop() { running = false; }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float phase = running ? (SystemClock.uptimeMillis() - startedAt) / 9000f : 0f;
            paint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFF090B14, 0xFF171633, 0xFF311A58}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            paint.setColor(0x347C4DFF);
            canvas.drawCircle(w * (.76f + .05f * (float)Math.sin(phase)),
                    h * (.20f + .05f * (float)Math.cos(phase)), h * .44f, paint);
            paint.setColor(0x2937B5FF);
            canvas.drawCircle(w * (.20f + .04f * (float)Math.cos(phase * .8f)),
                    h * (.84f + .04f * (float)Math.sin(phase * .8f)), h * .52f, paint);
            if (running) postInvalidateDelayed(32);
        }
    }
}
