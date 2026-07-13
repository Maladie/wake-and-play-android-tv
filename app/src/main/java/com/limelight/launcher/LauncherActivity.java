package com.limelight.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.hardware.BatteryState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LauncherActivity extends Activity {
    private static final String TAG = "WakeAndPlay";
    private static final MoonlightTarget[] MOONLIGHT_TARGETS = {
            new MoonlightTarget("com.limelight.unofficial"),
            new MoonlightTarget("com.limelight"),
            new MoonlightTarget("com.limelight.debug")
    };
    private static final String ACTION_STREAM = "com.limelight.action.STREAM";
    private static final String ACTION_OPEN_SETTINGS = "com.limelight.action.OPEN_SETTINGS";
    private static final String EXTRA_HOST_UUID = "com.limelight.extra.HOST_UUID";
    private static final String EXTRA_APP_ID = "com.limelight.extra.APP_ID";
    private static final String EXTRA_APP_NAME = "com.limelight.extra.APP_NAME";
    private static final String EXTRA_EXTERNAL_FRONTEND = "com.limelight.extra.EXTERNAL_FRONTEND";
    private static final long HOST_TIMEOUT_MS = 90_000;
    private static final int REQUEST_BLUETOOTH_CONNECT = 7001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final AtomicBoolean launchCancelled = new AtomicBoolean(false);
    private LinearLayout controllerRow;
    private LinearLayout hostRow;
    private LinearLayout appRow;
    private TextView emptyState;
    private TextView appsLabel;
    private TextView appEmptyState;
    private TextView sessionState;
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
        setContentView(buildUi());
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        launchCancelled.set(false);
        if (homeLayer != null) {
            showHome();
            refreshDashboard();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacksAndMessages(null);
        if (slideshow != null) slideshow.stop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (loadingLayer.getVisibility() == View.VISIBLE) {
            launchCancelled.set(true);
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 6, 10));

        homeLayer = new FrameLayout(this);
        homeLayer.addView(new GenerativeBackdrop(this), match());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(64), dp(26), dp(64), dp(24));
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

        TextView settingsButton = text("MOONLIGHT SETTINGS  ›", 13, 0xFFD2C4FF, true);
        settingsButton.setFocusable(true);
        settingsButton.setClickable(true);
        settingsButton.setPadding(dp(12), dp(5), dp(12), dp(5));
        settingsButton.setOnClickListener(v -> openMoonlightSettings());
        settingsButton.setOnFocusChangeListener((v, focused) -> styleCompactButton(settingsButton, focused));
        styleCompactButton(settingsButton, false);
        LinearLayout.LayoutParams settingsParams = wrap();
        settingsParams.topMargin = dp(6);
        content.addView(settingsButton, settingsParams);

        TextView controllersLabel = sectionLabel("CONTROLLERS");
        LinearLayout.LayoutParams sectionParams = wrap();
        sectionParams.topMargin = dp(11);
        content.addView(controllersLabel, sectionParams);
        HorizontalScrollView controllerScroll = horizontalScroll();
        controllerRow = horizontalRow();
        controllerScroll.addView(controllerRow);
        LinearLayout.LayoutParams controllerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        controllerParams.topMargin = dp(5);
        content.addView(controllerScroll, controllerParams);

        TextView hostsLabel = sectionLabel("STREAMING HOSTS");
        LinearLayout.LayoutParams hostsLabelParams = wrap();
        hostsLabelParams.topMargin = dp(11);
        content.addView(hostsLabel, hostsLabelParams);
        HorizontalScrollView hostScroll = horizontalScroll();
        hostRow = horizontalRow();
        hostScroll.addView(hostRow);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
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
        HorizontalScrollView appScroll = horizontalScroll();
        appRow = horizontalRow();
        appScroll.addView(appRow);
        LinearLayout.LayoutParams appParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        appParams.topMargin = dp(5);
        content.addView(appScroll, appParams);

        appEmptyState = text("Choose a streaming host to see its applications.", 16, 0xFFBDC4D8, false);
        appEmptyState.setGravity(Gravity.CENTER);
        appRow.addView(appEmptyState, new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT));

        loadingLayer = new FrameLayout(this);
        loadingLayer.setVisibility(View.GONE);
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
        TextView cancelHint = text("Press BACK to cancel", 14, 0xBFFFFFFF, false);
        cancelHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(34);
        loadingCopy.addView(cancelHint, hintParams);

        root.addView(homeLayer, match());
        root.addView(loadingLayer, match());
        return root;
    }

    private void refreshDashboard() {
        refreshControllersAsync();
        refreshSessionStatusAsync();
        executor.execute(() -> {
            List<Host> hosts = loadHosts();
            mainHandler.post(() -> renderHosts(hosts));
        });
    }

    private void refreshSessionStatusAsync() {
        executor.execute(() -> {
            StreamStatus status = loadStreamStatus();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderStreamStatus(status);
                if (homeLayer.getVisibility() == View.VISIBLE) {
                    mainHandler.postDelayed(this::refreshSessionStatusAsync, 1500);
                }
            });
        });
    }

    private StreamStatus loadStreamStatus() {
        for (MoonlightTarget target : MOONLIGHT_TARGETS) {
            Uri uri = target.statusUri;
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    StreamStatus status = new StreamStatus();
                    status.state = value(cursor, "state");
                    status.stage = value(cursor, "stage");
                    status.app = value(cursor, "app");
                    status.computer = value(cursor, "computer");
                    status.bitrateKbps = integer(cursor, "bitrate_kbps", 0);
                    return status;
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to read stream status from " + uri, error);
            }
        }
        return null;
    }

    private void renderStreamStatus(StreamStatus status) {
        if (status == null || status.state == null || status.state.isEmpty()) {
            sessionState.setText("MOONLIGHT · STATUS UNAVAILABLE");
            sessionState.setTextColor(0xFF9CA6C5);
            return;
        }
        String state = status.state.toUpperCase(Locale.ROOT);
        StringBuilder label = new StringBuilder("MOONLIGHT · ").append(state);
        if ("streaming".equals(status.state) || "reconnecting".equals(status.state)) {
            if (status.app != null && !status.app.isEmpty()) label.append(" · ").append(status.app);
            if (status.bitrateKbps > 0) label.append(" · ").append(Math.round(status.bitrateKbps / 1000f)).append(" Mbps");
        } else if ("connecting".equals(status.state) && status.stage != null && !status.stage.isEmpty()) {
            label.append(" · ").append(status.stage);
        }
        sessionState.setText(label.toString());
        sessionState.setTextColor("error".equals(status.state) ? 0xFFFF8A80 :
                "streaming".equals(status.state) ? 0xFF69F0AE :
                        "reconnecting".equals(status.state) ? 0xFFFFB74D : 0xFF9CA6C5);
    }

    private void refreshControllersAsync() {
        executor.execute(() -> {
            List<ControllerInfo> controllers = loadControllers();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                renderControllers(controllers);
                mainHandler.postDelayed(this::refreshControllersAsync, 5_000);
            });
        });
    }

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
        devices.sort(Comparator.comparing(InputDevice::getName));
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
        int player = 1;
        for (ControllerInfo controller : controllers) {
            controllerRow.addView(controllerCard(player++, controller), cardSpacing());
        }
        if (controllers.isEmpty()) {
            controllerRow.addView(text("No controllers connected", 15, 0xFFB6BDD2, false), cardSpacing());
        }
    }

    private View controllerCard(int player, ControllerInfo controller) {
        String name = controller.name;
        int percentage = controller.percentage;
        boolean charging = controller.charging;
        LinearLayout card = cardBase(dp(220), dp(50));
        TextView icon = text("🎮", 23, Color.WHITE, false);
        card.addView(icon, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
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
        card.setOnFocusChangeListener((v, focused) -> styleCard(card, focused));
        return card;
    }

    private void showControllerMenu(int player, ControllerInfo controller) {
        boolean canIdentify = ControllerActions.canIdentify(controller.deviceId);
        boolean canPowerOff = ControllerActions.canDisconnect();
        String[] actions = {
                canIdentify ? "Wskaż kontroler" : "Wskaż kontroler · niedostępne",
                canPowerOff ? "Wyłącz kontroler" : "Wyłącz kontroler · niedostępne",
                "Odłącz kontroler"
        };
        new AlertDialog.Builder(this)
                .setTitle("P" + player + " · " + compactControllerName(controller.name))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        if (canIdentify) identifyController(controller);
                        else showUnavailableControllerFeature("Sony nie udostępnia aplikacjom sterowania LED ani wibracją tego kontrolera.");
                    } else if (which == 1) {
                        if (canPowerOff) confirmPowerOff(controller);
                        else showUnavailableControllerFeature("Ta wersja Android TV nie udostępnia aplikacjom funkcji wyłączenia kontrolera Bluetooth.");
                    } else {
                        confirmUnpair(controller);
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void showUnavailableControllerFeature(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Funkcja niedostępna")
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
                .setTitle("Wyłączyć kontroler?")
                .setMessage("Kontroler zostanie rozłączony z telewizorem. Aby połączyć go ponownie, naciśnij przycisk PS.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Wyłącz", (dialog, which) ->
                        runBluetoothAction(controller, BluetoothAction.POWER_OFF))
                .show();
    }

    private void confirmUnpair(ControllerInfo controller) {
        new AlertDialog.Builder(this)
                .setTitle("Odparować kontroler?")
                .setMessage("Parowanie Bluetooth zostanie usunięte. Aby ponownie użyć kontrolera, trzeba będzie sparować go z telewizorem od nowa.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Odłącz", (dialog, which) ->
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
                .setTitle("Operacja niedostępna")
                .setMessage(message)
                .setNegativeButton("Zamknij", null)
                .setPositiveButton("Ustawienia Bluetooth", (dialog, which) -> {
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
            Toast.makeText(this, "Dostęp do Bluetooth jest wymagany dla tej operacji.", Toast.LENGTH_LONG).show();
        }
    }

    private void renderHosts(List<Host> hosts) {
        hostRow.removeAllViews();
        selectedHostCard = null;
        emptyState.setVisibility(hosts.isEmpty() ? View.VISIBLE : View.GONE);
        for (Host host : hosts) {
            View card = hostCard(host);
            hostRow.addView(card, cardSpacing());
            if (selectedHost != null && host.uuid.equals(selectedHost.uuid)) {
                selectedHostCard = card;
                selectedHost = host;
                styleCard(card, true);
            }
        }
        if (!hosts.isEmpty()) {
            if (selectedHostCard == null) {
                selectedHostCard = hostRow.getChildAt(0);
                styleCard(selectedHostCard, true);
                selectHost(hosts.get(0), false);
                selectedHostCard.requestFocus();
            }
        } else {
            selectedHost = null;
            renderApps(null, Collections.emptyList());
        }
    }

    private View hostCard(Host host) {
        LinearLayout card = cardBase(dp(270), dp(105));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text("▣", 24, 0xFFB99CFF, true);
        card.addView(icon, wrap());
        TextView name = text(host.name, 18, Color.WHITE, true);
        name.setSingleLine(true);
        LinearLayout.LayoutParams nameParams = wrap(); nameParams.topMargin = dp(3);
        card.addView(name, nameParams);
        TextView address = text(host.address != null ? host.address : "Address unavailable", 12, 0xFFABB3CA, false);
        address.setSingleLine(true);
        LinearLayout.LayoutParams addrParams = wrap(); addrParams.topMargin = dp(2);
        card.addView(address, addrParams);
        card.setOnClickListener(v -> {
            if (selectedHostCard != null && selectedHostCard != card) styleCard(selectedHostCard, false);
            selectedHostCard = card;
            styleCard(card, true);
            selectHost(host, true);
        });
        card.setOnFocusChangeListener((v, focused) ->
                styleCard(card, focused || card == selectedHostCard));
        return card;
    }

    private void selectHost(Host host, boolean moveFocusToApps) {
        selectedHost = host;
        appsLabel.setText("APPS · " + host.name.toUpperCase(Locale.ROOT));
        appRow.removeAllViews();
        TextView loading = text("Loading cached applications…", 16, 0xFFBDC4D8, false);
        appRow.addView(loading, new LinearLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.MATCH_PARENT));
        executor.execute(() -> {
            List<StreamApp> apps = loadApps(host);
            mainHandler.post(() -> {
                if (selectedHost == null || !host.uuid.equals(selectedHost.uuid)) return;
                renderApps(host, apps);
                if (moveFocusToApps && !apps.isEmpty()) appRow.getChildAt(0).requestFocus();
            });
        });
    }

    private void renderApps(Host host, List<StreamApp> apps) {
        appRow.removeAllViews();
        if (host == null) {
            appsLabel.setText("APPS");
            appRow.addView(text("Choose a streaming host to see its applications.", 16, 0xFFBDC4D8, false),
                    new LinearLayout.LayoutParams(dp(560), ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        if (apps.isEmpty()) {
            appRow.addView(text("No cached applications found. Refresh this host once in Moonlight X.", 16, 0xFFFFB74D, false),
                    new LinearLayout.LayoutParams(dp(720), ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        for (StreamApp app : apps) {
            appRow.addView(appCard(host, app), cardSpacing());
        }
    }

    private View appCard(Host host, StreamApp app) {
        LinearLayout card = cardBase(dp(285), dp(125));
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholder = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF302255, 0xFF142A46});
        placeholder.setCornerRadius(dp(9));
        poster.setBackground(placeholder);
        card.addView(poster, new LinearLayout.LayoutParams(dp(82), dp(96)));
        loadPosterAsync(app.posterUri, poster);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(app.name, 16, Color.WHITE, true);
        name.setSingleLine(true);
        copy.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView action = text("PLAY  ›", 12, 0xFFB99CFF, true);
        LinearLayout.LayoutParams actionParams = wrap();
        actionParams.topMargin = dp(10);
        copy.addView(action, actionParams);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        copyParams.leftMargin = dp(14);
        card.addView(copy, copyParams);
        card.setOnClickListener(v -> beginAppLaunch(host, app));
        card.setOnFocusChangeListener((v, focused) -> styleCard(card, focused));
        return card;
    }

    private void loadPosterAsync(Uri uri, ImageView target) {
        if (uri == null) return;
        executor.execute(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
                int sample = 1;
                while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, sample);
                Bitmap bitmap;
                try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(input, null, options);
                }
                if (bitmap != null) mainHandler.post(() -> target.setImageBitmap(bitmap));
            } catch (Exception ignored) { }
        });
    }

    private void beginAppLaunch(Host host, StreamApp app) {
        launchCancelled.set(false);
        homeLayer.setVisibility(View.GONE);
        loadingLayer.setVisibility(View.VISIBLE);
        loadingTitle.setText(app.name);
        setLoadingStatus("Preparing wake sequence...");
        lastLoadingMessageIndex = -1;
        rotateLoadingMessage();
        slideshow.start();
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
                hosts.sort(Comparator.comparing(h -> h.name.toLowerCase(Locale.ROOT)));
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
        apps.sort(Comparator.comparing(app -> app.name.toLowerCase(Locale.ROOT)));
        return apps;
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
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return;
            } catch (RuntimeException ignored) { }
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
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setFocusable(true);
        card.setClickable(true);
        card.setMinimumWidth(width);
        card.setMinimumHeight(height);
        styleCard(card, false);
        return card;
    }

    private void styleCard(View card, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(14));
        background.setColor(focused ? 0xFF56418F : 0xC9181C2B);
        background.setStroke(dp(focused ? 3 : 1), focused ? 0xFFFFFFFF : 0x387C89B2);
        card.setBackground(background);
        card.setScaleX(focused ? 1.045f : 1f);
        card.setScaleY(focused ? 1.045f : 1f);
    }

    private void styleCompactButton(View button, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(focused ? 0xFF56418F : 0x65181C2B);
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xFFFFFFFF : 0x387C89B2);
        button.setBackground(background);
    }

    private TextView sectionLabel(String value) { return text(value, 13, 0xFF9CA6C5, true); }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }
    private HorizontalScrollView horizontalScroll() {
        HorizontalScrollView view = new HorizontalScrollView(this);
        view.setHorizontalScrollBarEnabled(false);
        view.setClipToPadding(false);
        view.setClipChildren(false);
        view.setPadding(dp(12), 0, dp(12), 0);
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
    private static final class StreamStatus { String state; String stage; String app; String computer; int bitrateKbps; }

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
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private float phase;
        private int palette;
        private boolean running;
        private final Runnable next = new Runnable() { @Override public void run() { if (!running) return; palette = random.nextInt(5); phase = random.nextFloat(); invalidate(); handler.postDelayed(this, 4700); } };
        GenerativeSlideshow(android.content.Context context) { super(context); }
        void start() { running = true; handler.removeCallbacks(next); next.run(); }
        void stop() { running = false; handler.removeCallbacks(next); }
        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int[][] palettes = {{0xFF100C28,0xFF47206C,0xFF087F8C},{0xFF071A2B,0xFF143D59,0xFFF4B41A},{0xFF190B28,0xFF5C164E,0xFFEE4266},{0xFF081C15,0xFF1B4332,0xFF52B788},{0xFF101820,0xFF3A506B,0xFF5BC0BE}};
            int[] colors = palettes[palette % palettes.length];
            paint.setShader(new LinearGradient(0, h * phase, w, h * (1f - phase), colors, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
            Random seeded = new Random(palette * 997L + Float.floatToIntBits(phase));
            for (int i=0;i<22;i++) { paint.setColor(0x16FFFFFF + (i%3)*0x08000000); float r=h*(.025f+seeded.nextFloat()*.18f); canvas.drawCircle(seeded.nextFloat()*w, seeded.nextFloat()*h, r, paint); }
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Math.max(2, h*.004f)); paint.setColor(0x45FFFFFF);
            for(int i=0;i<7;i++){ float y=h*(.18f+i*.105f); canvas.drawLine(-w*.1f,y,w*1.1f,y-w*.12f,paint); }
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
