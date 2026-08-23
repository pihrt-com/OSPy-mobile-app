package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.text.DateFormat;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends ComponentActivity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private static final int REQUEST_UNLOCK_CREDENTIAL = 11;
    private static final int REQUEST_SAVE_BACKUP = 41;
    private static final long LIVE_REFRESH_MS = 10_000L;
    private static final long UPDATE_OPERATION_POLL_MS = 1_500L;
    private static final long UPDATE_RECONNECT_POLL_MS = 3_000L;
    private static final long UPDATE_INITIAL_RESTART_WAIT_MS = 7_000L;
    private static final long UPDATE_APPLY_VERIFY_WAIT_MS = 45_000L;
    private static final long UPDATE_RECONNECT_TIMEOUT_MS = 120_000L;

    // The application is drawn programmatically, therefore both themes use a
    // runtime palette in addition to the Android window theme.
    private int GREEN;
    private int LIGHT_GREEN;
    private int NAVY;
    private int RED;
    private int LIGHT_RED;
    private int AMBER;
    private int LIGHT_AMBER;
    private int TEXT;
    private int MUTED;
    private int BACKGROUND;
    private int SURFACE;
    private int CARD_BORDER;
    private int HEADING;

    private InstallationStore installationStore;
    private AppPreferences appPreferences;
    private List<Installation> installations = new ArrayList<>();
    private Installation current;
    private ApiClient api;
    private ApiClient pairingApi;
    private Installation pairingInstallation;
    private LiveUpdates liveUpdates;
    private NotificationCenter notifications;
    private LinearLayout page;
    private LinearLayout toolbar;
    private TextView title;
    private LinearLayout content;
    private ScrollView contentScroll;
    private final Map<Button, String> navigationButtons = new LinkedHashMap<>();
    private String activeSection = "";
    private String currentPath = "";
    private String currentRenderer = "";
    private int loadGeneration = 0;
    private int requestSequence = 0;
    private int activeRequest = 0;
    private boolean requestInFlight = false;
    private int connectionAttemptGeneration = 0;
    private boolean unlockStarted = false;
    private byte[] pendingBackupData;
    private float overviewPullStartY = 0f;
    private boolean overviewPullTracking = false;
    private boolean overviewPullArmed = false;
    private JSONObject activePluginMobile;
    private LinearLayout activePluginMobileParent;
    private LinearLayout activePluginMobileContent;
    private Button activePluginMobileTrigger;
    private String activePluginMobileRange = "today";
    private LocalDateTime activePluginMobileFrom;
    private LocalDateTime activePluginMobileTo;
    private boolean pluginMobileRequestInFlight = false;
    private int pluginMobileRequestSequence = 0;

    // Notification transition baseline. The first overview response only
    // establishes the state and never creates notifications for old activity.
    private final Map<String, String> previousActiveStations = new LinkedHashMap<>();
    private boolean notificationBaselineReady = false;
    private boolean notificationSnapshotInFlight = false;
    private Boolean previousRainBlock;

    // System update operations are asynchronous in API v1. Keep their state
    // separate from normal page requests so progress survives a screen redraw.
    private final Handler systemOperationHandler = new Handler(Looper.getMainLooper());
    private String systemOperationId = "";
    private String systemOperationKind = "";
    private String systemOperationStatus = "";
    private String systemOperationError = "";
    private int systemOperationProgress = 0;
    private long systemOperationStartedAt = 0;
    private long systemReconnectStartedAt = 0;
    private boolean systemOperationPolling = false;
    private boolean systemWaitingForReconnect = false;
    private String pendingSystemAnnouncement = "";
    private boolean systemUpdateAvailable = false;
    private String systemCurrentCommit = "";
    private String systemTargetCommit = "";
    private String systemApplyCommitBefore = "";
    private String systemApplyTargetCommit = "";
    private TextView systemOperationStatusView;
    private ProgressBar systemOperationProgressView;
    private Button systemCheckButton;
    private Button systemInstallButton;

    // The overview is kept attached during background refreshes. Only values
    // that actually changed are updated, preventing the page and timeline from
    // disappearing and being recreated every ten seconds.
    private LinearLayout overviewRoot;
    private LinearLayout overviewIrrigationSection;
    private OverviewSwitchBinding overviewSchedulerControl;
    private OverviewSwitchBinding overviewManualControl;
    private OverviewControlBinding overviewRainControl;
    private OverviewControlBinding overviewWaterLevelControl;
    private PairBinding overviewActiveCount;
    private LinearLayout overviewActiveStationsContainer;
    private final List<PairBinding> overviewActiveStationRows = new ArrayList<>();
    private String overviewActiveStationStructure = "";
    private TextView overviewVersionView;
    private TextView connectionStatusView;
    private LocalDateTime lastSuccessfulApiUpdate;
    private LinearLayout overviewWarningsSection;
    private String overviewWarningsSignature = "";
    private TextView overviewTimelineNow;
    private LinearLayout overviewTimelineRowsContainer;
    private TextView overviewTimelineError;
    private Button overviewYesterdayButton;
    private Button overviewTodayButton;
    private Button overviewTomorrowButton;
    private final List<TimelineRowBinding> overviewTimelineRows = new ArrayList<>();
    private String overviewTimelineStructure = "";
    // API v1 intentionally omits predicted intervals as soon as their start
    // time is in the past. A rain-blocked interval is not an active or finished
    // run, so it would disappear exactly when it should be shown as blocked.
    // Keep previously received rain-blocked rows until their scheduled end.
    private final Map<String, JSONObject> overviewBlockedTimelineCache =
            new LinkedHashMap<>();
    private boolean overviewRainBlockActive = false;
    private int overviewTimelineRequestSequence = 0;
    private int activeOverviewTimelineRequest = 0;
    private boolean overviewTimelineRequestInFlight = false;
    private boolean overviewTimelineRefreshPending = false;
    private String overviewTimelineActivePath = "";
    private String overviewTimelinePendingPath = "";
    private LocalDate overviewServerDate;
    private ScheduleDay selectedScheduleDay = ScheduleDay.TODAY;

    private enum ScheduleDay {
        YESTERDAY(-1),
        TODAY(0),
        TOMORROW(1);

        final int offsetDays;

        ScheduleDay(int offsetDays) {
            this.offsetDays = offsetDays;
        }
    }

    private static final class OverviewControlBinding {
        final TextView value;
        final Button action;

        OverviewControlBinding(TextView value, Button action) {
            this.value = value;
            this.action = action;
        }
    }

    private static final class PairBinding {
        final TextView label;
        final TextView value;

        PairBinding(TextView label, TextView value) {
            this.label = label;
            this.value = value;
        }
    }

    private static final class TimelineRowBinding {
        final String key;
        final String state;
        final TextView title;
        final TextView status;
        final TextView detail;
        final ProgressBar progress;

        TimelineRowBinding(
                String key, String state, TextView title, TextView status,
                TextView detail, ProgressBar progress) {
            this.key = key;
            this.state = state;
            this.title = title;
            this.status = status;
            this.detail = detail;
            this.progress = progress;
        }
    }
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (current != null && "plugins".equals(currentRenderer) &&
                    activePluginMobile != null &&
                    activePluginMobileContent != null &&
                    activePluginMobileContent.getParent() != null &&
                    activePluginMobileContent.getVisibility() == View.VISIBLE &&
                    !pluginMobileRequestInFlight) {
                loadPluginMobileRange(
                        activePluginMobile, activePluginMobileParent,
                        activePluginMobileTrigger, activePluginMobileRange,
                        activePluginMobileFrom, activePluginMobileTo, true);
            } else if (current != null && !requestInFlight &&
                    ("overview".equals(currentRenderer) ||
                            "stations".equals(currentRenderer))) {
                fetch(currentPath, currentRenderer, false, loadGeneration);
            }
            refreshHandler.postDelayed(this, LIVE_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        appPreferences = new AppPreferences(this);
        setTheme(appPreferences.darkTheme()
                ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        applyThemePalette();
        applySystemBarAppearance();
        installationStore = new InstallationStore(this);
        notifications = new NotificationCenter(this);
        NotificationScheduler.update(this, true);
        if (Build.VERSION.SDK_INT >= 33 &&
                notifications.isEnabled() &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        } else {
            unlock();
        }
        refreshHandler.postDelayed(refreshTask, LIVE_REFRESH_MS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && !unlockStarted) unlock();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTask);
        systemOperationHandler.removeCallbacksAndMessages(null);
        if (liveUpdates != null) liveUpdates.stop();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNLOCK_CREDENTIAL) {
            if (resultCode == RESULT_OK) afterUnlock();
            else finish();
            return;
        }
        if (requestCode != REQUEST_SAVE_BACKUP || resultCode != RESULT_OK ||
                data == null || data.getData() == null || pendingBackupData == null) {
            return;
        }
        try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
            if (output == null) throw new IllegalStateException("output unavailable");
            output.write(pendingBackupData);
            toast(getString(R.string.backup_saved));
        } catch (Exception error) {
            message(
                    getString(R.string.app_name),
                    getString(R.string.backup_save_failed));
        } finally {
            pendingBackupData = null;
        }
    }

    private void unlock() {
        if (unlockStarted) return;
        unlockStarted = true;
        if (!appPreferences.appLockEnabled()) {
            afterUnlock();
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            afterUnlock();
            return;
        }
        // Android 10+ can offer biometrics and the device credential in one
        // system prompt. Older supported releases use the secure device
        // credential screen, which is reliable even when no biometric sensor
        // is enrolled.
        if (Build.VERSION.SDK_INT < 29) {
            launchDeviceCredentialUnlock(keyguard);
            return;
        }
        new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.unlock))
                .setSubtitle(getString(R.string.unlock_subtitle))
                .setDeviceCredentialAllowed(true)
                .build()
                .authenticate(
                        new CancellationSignal(), getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(
                                    BiometricPrompt.AuthenticationResult result) {
                                afterUnlock();
                            }

                            @Override
                            public void onAuthenticationError(
                                    int code, CharSequence message) {
                                if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                                    toast(message.toString());
                                }
                                finish();
                            }
                        });
    }

    private void launchDeviceCredentialUnlock(KeyguardManager keyguard) {
        Intent intent = keyguard.createConfirmDeviceCredentialIntent(
                getString(R.string.unlock),
                getString(R.string.unlock_subtitle));
        if (intent == null) {
            afterUnlock();
            return;
        }
        startActivityForResult(intent, REQUEST_UNLOCK_CREDENTIAL);
    }

    private void afterUnlock() {
        try {
            installations = installationStore.load();
        } catch (Exception error) {
            showInstallations();
            return;
        }
        if (!appPreferences.openLastInstallation() || installations.isEmpty()) {
            showInstallations();
            return;
        }
        boolean preferPrivate = appPreferences.watchNetwork() && isOnWifi();
        List<Installation> candidates = new ArrayList<>();
        String lastId = appPreferences.lastInstallationId();
        for (Installation installation : installations) {
            if (appPreferences.watchNetwork() &&
                    installation.isPrivateAddress() != preferPrivate) continue;
            if (installation.id.equals(lastId)) candidates.add(0, installation);
            else candidates.add(installation);
        }
        if (candidates.isEmpty()) {
            for (Installation installation : installations) {
                if (installation.id.equals(lastId)) candidates.add(0, installation);
                else candidates.add(installation);
            }
        }
        openFirstReachable(candidates);
    }

    private void openFirstReachable(List<Installation> candidates) {
        if (candidates.isEmpty()) {
            showInstallations();
            toast(getString(R.string.no_reachable_installation));
            return;
        }
        int attemptGeneration = ++connectionAttemptGeneration;
        showConnecting();
        // Probe saved installations concurrently. One unreachable address now
        // costs at most one timeout instead of blocking every later candidate
        // for another full timeout.
        AtomicBoolean selected = new AtomicBoolean(false);
        AtomicInteger remaining = new AtomicInteger(candidates.size());
        for (Installation candidate : candidates) {
            ApiClient candidateApi = new ApiClient(candidate, installationStore);
            candidateApi.probe(new ApiClient.Callback() {
                @Override public void success(JSONObject response) {
                    if (attemptGeneration != connectionAttemptGeneration) return;
                    if (selected.compareAndSet(false, true)) {
                        open(candidate, candidateApi);
                    }
                }

                @Override public void failure(String error) {
                    if (attemptGeneration != connectionAttemptGeneration) return;
                    if (remaining.decrementAndGet() == 0 &&
                            selected.compareAndSet(false, true)) {
                        showInstallations();
                        toast(getString(R.string.no_reachable_installation));
                    }
                }
            });
        }
    }

    private void showConnecting() {
        currentPath = "";
        currentRenderer = "";
        shell(getString(R.string.app_name));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(48), dp(24), dp(24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.opensprinkler_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setAdjustViewBounds(true);
        logo.setContentDescription(null);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(logo, new LinearLayout.LayoutParams(-1, dp(72)));

        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(dp(48), dp(48));
        progressParams.setMargins(0, dp(28), 0, dp(18));
        panel.addView(progress, progressParams);

        TextView status = text(
                getString(R.string.connecting_last_installation), 18, true);
        status.setGravity(Gravity.CENTER);
        panel.addView(status, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = text(getString(R.string.connecting_please_wait), 14, false);
        detail.setTextColor(MUTED);
        detail.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(8), 0, dp(24));
        panel.addView(detail, detailParams);

        Button choose = button(getString(R.string.choose_another_installation), NAVY);
        choose.setOnClickListener(v -> showInstallations());
        panel.addView(choose);
        content.addView(panel, new LinearLayout.LayoutParams(-1, -1));
    }

    private boolean isOnWifi() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities =
                network == null ? null : manager.getNetworkCapabilities(network);
        return capabilities != null &&
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void applyThemePalette() {
        if (appPreferences != null && appPreferences.darkTheme()) {
            GREEN = Color.rgb(52, 154, 43);
            LIGHT_GREEN = Color.rgb(24, 58, 27);
            NAVY = Color.rgb(67, 82, 122);
            RED = Color.rgb(184, 58, 69);
            LIGHT_RED = Color.rgb(66, 27, 33);
            AMBER = Color.rgb(216, 154, 36);
            LIGHT_AMBER = Color.rgb(61, 50, 24);
            TEXT = Color.rgb(239, 243, 248);
            MUTED = Color.rgb(174, 184, 197);
            BACKGROUND = Color.rgb(16, 20, 25);
            SURFACE = Color.rgb(28, 35, 44);
            CARD_BORDER = Color.rgb(82, 97, 119);
            HEADING = Color.rgb(184, 204, 235);
        } else {
            GREEN = Color.rgb(43, 138, 30);
            LIGHT_GREEN = Color.rgb(232, 244, 230);
            NAVY = Color.rgb(48, 59, 92);
            RED = Color.rgb(146, 27, 37);
            LIGHT_RED = Color.rgb(252, 232, 234);
            AMBER = Color.rgb(204, 132, 0);
            LIGHT_AMBER = Color.rgb(255, 245, 218);
            TEXT = Color.rgb(30, 30, 30);
            MUTED = Color.rgb(96, 102, 112);
            BACKGROUND = Color.rgb(244, 246, 248);
            SURFACE = Color.WHITE;
            CARD_BORDER = NAVY;
            HEADING = NAVY;
        }
    }

    private void applySystemBarAppearance() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(
                        getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(
                appPreferences == null || !appPreferences.darkTheme());
    }

    private boolean deviceSecurityConfigured() {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    private void shell(String heading) {
        shell(heading, false);
    }

    private void shell(String heading, boolean showInstallationLogo) {
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BACKGROUND);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(10), dp(12), dp(10));
        toolbar.setBackgroundColor(GREEN);

        if (showInstallationLogo) {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.opensprinkler_logo);
            logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            logo.setAdjustViewBounds(true);
            logo.setContentDescription(null);
            logo.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            toolbar.addView(
                    logo, new LinearLayout.LayoutParams(dp(111), dp(28)));

            title = text(heading, 18, true);
            title.setTextColor(Color.WHITE);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(0, dp(28), 1);
            titleParams.setMargins(dp(8), 0, dp(8), 0);
            toolbar.addView(title, titleParams);
        } else {
            title = text(heading, 22, true);
            title.setTextColor(Color.WHITE);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            toolbar.addView(
                    title, new LinearLayout.LayoutParams(0, -2, 1));
        }

        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setColorFilter(Color.WHITE);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setContentDescription(getString(R.string.app_settings));
        settings.setOnClickListener(v -> showAppSettings());
        toolbar.addView(
                settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        page.addView(toolbar);

        connectionStatusView = text("", 14, true);
        connectionStatusView.setTextColor(RED);
        connectionStatusView.setBackground(background(LIGHT_RED, RED, 10));
        connectionStatusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams connectionParams =
                new LinearLayout.LayoutParams(-1, -2);
        connectionParams.setMargins(dp(12), dp(8), dp(12), 0);
        connectionStatusView.setVisibility(View.GONE);
        page.addView(connectionStatusView, connectionParams);

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(24));
        contentScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        configurePullToRefresh();
        setContentView(page);

        ViewCompat.setOnApplyWindowInsetsListener(page, (view, windowInsets) -> {
            Insets systemBars = Build.VERSION.SDK_INT >= 35
                    ? windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout())
                    : Insets.NONE;
            toolbar.setPadding(
                    dp(16) + systemBars.left,
                    dp(10) + systemBars.top,
                    dp(12) + systemBars.right,
                    dp(10));
            content.setPadding(
                    dp(12) + systemBars.left,
                    dp(10),
                    dp(12) + systemBars.right,
                    dp(24) + systemBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(page);
    }

    private void configurePullToRefresh() {
        if (contentScroll == null) return;
        contentScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        contentScroll.setOnTouchListener((view, event) -> {
            boolean refreshableVisible = isPullRefreshable();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    overviewPullTracking =
                            refreshableVisible && contentScroll.getScrollY() == 0;
                    overviewPullStartY = event.getY();
                    overviewPullArmed = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!refreshableVisible || contentScroll.getScrollY() > 0) {
                        resetOverviewPullGesture();
                        break;
                    }
                    if (!overviewPullTracking) {
                        overviewPullTracking = true;
                        overviewPullStartY = event.getY();
                    }
                    overviewPullArmed =
                            event.getY() - overviewPullStartY >= dp(72);
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    boolean refresh = overviewPullTracking &&
                            overviewPullArmed && refreshableVisible &&
                            contentScroll.getScrollY() == 0 && !requestInFlight;
                    resetOverviewPullGesture();
                    if (refresh) {
                        // A full reload deliberately mirrors switching to a
                        // different tab and then returning to Overview.
                        load(currentPath, currentRenderer);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    resetOverviewPullGesture();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void resetOverviewPullGesture() {
        overviewPullTracking = false;
        overviewPullArmed = false;
        overviewPullStartY = 0f;
    }

    private void showInstallations() {
        connectionAttemptGeneration++;
        currentPath = "";
        currentRenderer = "";
        shell(getString(R.string.app_name));
        try {
            installations = installationStore.load();
        } catch (Exception error) {
            message(
                    getString(R.string.protected_storage_error),
                    getString(R.string.protected_storage_error_detail));
            installations = new ArrayList<>();
        }
        heading(getString(R.string.installations));
        for (Installation installation : installations) {
            LinearLayout row = cardColumn();
            row.addView(text(installation.name, 17, true));
            TextView address = text(installation.baseUrl, 14, false);
            address.setTextIsSelectable(true);
            row.addView(address);
            LinearLayout actions = actionRow();
            Button open = button(getString(R.string.open), GREEN);
            open.setOnClickListener(v -> open(installation));
            actions.addView(open);
            Button edit = button(getString(R.string.edit), NAVY);
            edit.setOnClickListener(v -> showEditInstallation(installation));
            actions.addView(edit);
            Button reconnect = button(getString(R.string.reconnect), NAVY);
            reconnect.setOnClickListener(v -> showPairing(installation));
            actions.addView(reconnect);
            Button remove = button("×", RED);
            remove.setContentDescription(getString(R.string.remove));
            remove.setOnClickListener(v -> confirmRemove(installation));
            actions.addView(remove);
            row.addView(actions);
            content.addView(row);
        }
        Button add = button(getString(R.string.add_installation), NAVY);
        add.setOnClickListener(v -> showPairing());
        content.addView(add);
    }

    private void showAppSettings() {
        currentPath = "";
        currentRenderer = "app_settings";
        activeRequest = ++requestSequence;
        requestInFlight = false;
        shell(getString(R.string.app_settings));

        LinearLayout securitySettings = cardColumn();
        securitySettings.addView(text(
                getString(R.string.security), 16, true));
        TextView lockDescription = text(
                getString(R.string.app_lock_description), 13, false);
        lockDescription.setTextColor(MUTED);
        securitySettings.addView(lockDescription);
        addPreferenceToggle(
                securitySettings, getString(R.string.app_lock),
                appPreferences.appLockEnabled(),
                enabled -> appPreferences.setAppLockEnabled(enabled));
        if (appPreferences.appLockEnabled() && !deviceSecurityConfigured()) {
            TextView warning = text(
                    getString(R.string.no_device_security), 13, true);
            warning.setTextColor(RED);
            securitySettings.addView(warning);
            Button securityButton = button(
                    getString(R.string.open_device_security_settings), NAVY);
            securityButton.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                } catch (Exception error) {
                    message(
                            getString(R.string.app_settings),
                            getString(R.string.cannot_open_link));
                }
            });
            securitySettings.addView(securityButton);
        }
        content.addView(securitySettings);

        LinearLayout appearanceSettings = cardColumn();
        appearanceSettings.addView(text(
                getString(R.string.appearance), 16, true));
        TextView appearanceDescription = text(
                getString(R.string.dark_theme_description), 13, false);
        appearanceDescription.setTextColor(MUTED);
        appearanceSettings.addView(appearanceDescription);
        addPreferenceToggle(
                appearanceSettings, getString(R.string.dark_theme),
                appPreferences.darkTheme(), enabled -> {
                    appPreferences.setDarkTheme(enabled);
                    setTheme(enabled ? R.style.AppThemeDark : R.style.AppTheme);
                    applyThemePalette();
                    applySystemBarAppearance();
                });
        content.addView(appearanceSettings);

        LinearLayout notificationSettings = cardColumn();
        LinearLayout notificationHeader = actionRow();
        notificationHeader.addView(
                text(getString(R.string.notifications), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button notificationToggle = compactButton(
                getString(notifications.isEnabled()
                        ? R.string.enabled : R.string.disabled),
                notifications.isEnabled() ? GREEN : NAVY);
        notificationToggle.setOnClickListener(v -> {
            boolean enabled = !notifications.isEnabled();
            notifications.setEnabled(enabled);
            NotificationScheduler.update(this, true);
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS);
            }
            showAppSettings();
        });
        notificationHeader.addView(notificationToggle);
        notificationSettings.addView(notificationHeader);
        TextView notificationDescription = text(
                getString(R.string.notification_preferences_description),
                13, false);
        notificationDescription.setTextColor(MUTED);
        notificationSettings.addView(notificationDescription);
        if (Build.VERSION.SDK_INT >= 33 && notifications.isEnabled() &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            TextView blocked = text(
                    getString(R.string.notifications_blocked_by_android),
                    13, true);
            blocked.setTextColor(RED);
            notificationSettings.addView(blocked);
            Button openNotificationSettings = compactButton(
                    getString(R.string.open_notification_settings), NAVY);
            openNotificationSettings.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                } catch (Exception error) {
                    message(
                            getString(R.string.app_settings),
                            getString(R.string.cannot_open_link));
                }
            });
            notificationSettings.addView(openNotificationSettings);
        }
        addNotificationPreference(
                notificationSettings, R.string.notify_station_started,
                NotificationCenter.CATEGORY_STATION_STARTED);
        addNotificationPreference(
                notificationSettings, R.string.notify_station_stopped,
                NotificationCenter.CATEGORY_STATION_STOPPED);
        addNotificationPreference(
                notificationSettings, R.string.notify_rain_delay,
                NotificationCenter.CATEGORY_RAIN);
        addNotificationPreference(
                notificationSettings, R.string.notify_diagnostics,
                NotificationCenter.CATEGORY_DIAGNOSTICS);
        addNotificationPreference(
                notificationSettings, R.string.notify_updates,
                NotificationCenter.CATEGORY_UPDATES);
        addNotificationPreference(
                notificationSettings, R.string.notify_automation,
                NotificationCenter.CATEGORY_AUTOMATION);
        addNotificationPreference(
                notificationSettings, R.string.notify_other,
                NotificationCenter.CATEGORY_OTHER);
        TextView speechDescription = text(
                getString(R.string.notification_speech_description), 13, false);
        speechDescription.setTextColor(MUTED);
        notificationSettings.addView(speechDescription);
        addPreferenceToggle(
                notificationSettings,
                getString(R.string.notification_speech),
                notifications.isSpeechEnabled(),
                notifications::setSpeechEnabled);
        if (notifications.isSpeechEnabled()) {
            SpeechAnnouncer.initialize(this);
            addPreferenceToggle(
                    notificationSettings,
                    getString(R.string.notification_speech_all_categories),
                    notifications.speaksAllCategories(),
                    notifications::setSpeaksAllCategories);
            TextView speechStatus = text("", 13, false);
            speechStatus.setTextColor(MUTED);
            notificationSettings.addView(speechStatus);
            refreshSpeechStatus(speechStatus);
            Button testSpeech = compactButton(
                    getString(R.string.notification_speech_test), NAVY);
            testSpeech.setOnClickListener(v -> SpeechAnnouncer.speak(
                    this, getString(R.string.notification_speech_test_message)));
            notificationSettings.addView(testSpeech);
        }
        content.addView(notificationSettings);

        LinearLayout pushRegistration = cardColumn();
        pushRegistration.addView(text(
                getString(R.string.push_registration), 16, true));
        TextView pushDescription = text(
                getString(R.string.push_registration_description), 13, false);
        pushDescription.setTextColor(MUTED);
        pushRegistration.addView(pushDescription);
        PushSyncStatusStore pushStatusStore = new PushSyncStatusStore(this);
        if (installations.isEmpty()) {
            pushRegistration.addView(text(
                    getString(R.string.push_status_no_installations), 14, false));
        } else {
            for (Installation installation : installations) {
                PushSyncStatusStore.Snapshot snapshot =
                        pushStatusStore.load(installation.id);
                TextView status = text(
                        getString(
                                R.string.push_status_installation,
                                installation.name,
                                pushStatusText(snapshot)),
                        14, PushSyncStatusStore.ERROR.equals(snapshot.state));
                status.setTextColor(PushSyncStatusStore.ERROR.equals(snapshot.state)
                        ? RED : TEXT);
                pushRegistration.addView(status);
                if (snapshot.updated > 0L) {
                    String attempted = DateFormat.getDateTimeInstance(
                            DateFormat.SHORT, DateFormat.MEDIUM)
                            .format(new Date(snapshot.updated));
                    TextView updated = text(getString(
                            R.string.push_status_last_attempt, attempted),
                            12, false);
                    updated.setTextColor(MUTED);
                    pushRegistration.addView(updated);
                }
                if (!snapshot.detail.isEmpty()) {
                    TextView detail = text(getString(
                            R.string.push_status_technical_detail,
                            pushTechnicalDetail(snapshot.detail)), 12, false);
                    detail.setTextColor(MUTED);
                    pushRegistration.addView(detail);
                }
            }
        }
        Button retryPush = button(
                getString(R.string.retry_push_registration), NAVY);
        retryPush.setOnClickListener(v -> {
            retryPush.setEnabled(false);
            retryPush.setText(getString(R.string.push_retry_started));
            PushRegistrationManager.syncAll(this, () -> {
                if (!isFinishing() &&
                        "app_settings".equals(currentRenderer)) {
                    showAppSettings();
                }
            });
        });
        pushRegistration.addView(retryPush);
        content.addView(pushRegistration);

        LinearLayout networkSettings = cardColumn();
        networkSettings.addView(text(
                getString(R.string.connection_settings), 16, true));
        addPreferenceToggle(
                networkSettings, getString(R.string.watch_network),
                appPreferences.watchNetwork(),
                enabled -> appPreferences.setWatchNetwork(enabled));
        addPreferenceToggle(
                networkSettings, getString(R.string.open_last_installation),
                appPreferences.openLastInstallation(),
                enabled -> appPreferences.setOpenLastInstallation(enabled));
        content.addView(networkSettings);

        LinearLayout languageSettings = cardColumn();
        languageSettings.addView(text(
                getString(R.string.application_language), 16, true));
        TextView languageDescription = text(
                getString(R.string.application_language_description), 13, false);
        languageDescription.setTextColor(MUTED);
        languageSettings.addView(languageDescription);
        if (Build.VERSION.SDK_INT >= 33) {
            Button languageButton = button(
                    getString(R.string.open_language_settings), NAVY);
            languageButton.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception error) {
                    message(
                            getString(R.string.app_settings),
                            getString(R.string.cannot_open_link));
                }
            });
            languageSettings.addView(languageButton);
        }
        content.addView(languageSettings);

        LinearLayout installationsCard = card();
        installationsCard.addView(
                text(getString(R.string.saved_ospy_systems), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button installationsButton = button(
                getString(R.string.open), NAVY);
        installationsButton.setOnClickListener(v -> showInstallations());
        installationsCard.addView(installationsButton);
        content.addView(installationsCard);

        heading(getString(R.string.about_app));
        LinearLayout about = cardColumn();
        addPair(
                about, getString(R.string.app_version),
                BuildConfig.VERSION_NAME);
        about.addView(linkButton(
                getString(R.string.ospy_github),
                "https://github.com/martinpihrt/OSPy"));
        about.addView(linkButton(
                getString(R.string.plugins_github),
                "https://github.com/martinpihrt/OSPy-plugins"));
        about.addView(linkButton(
                getString(R.string.app_source_github),
                "https://github.com/martinpihrt/OSPy-mobile-app"));
        about.addView(linkButton(
                getString(R.string.google_play),
                "https://play.google.com/store/apps/details?id=" +
                        getPackageName()));
        content.addView(about);

        LinearLayout helpCard = card();
        helpCard.addView(
                text(getString(R.string.app_help), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button helpButton = button(getString(R.string.open), NAVY);
        helpButton.setOnClickListener(v -> showAppHelp());
        helpCard.addView(helpButton);
        content.addView(helpCard);

        Button back = button(getString(R.string.back), RED);
        back.setOnClickListener(v -> {
            if (current == null) showInstallations();
            else showDashboard();
        });
        content.addView(back);
    }

    private static final class OverviewSwitchBinding {
        final LinearLayout track;
        final TextView leftLabel;
        final TextView rightLabel;
        final Switch toggle;
        final String label;

        OverviewSwitchBinding(
                LinearLayout track, TextView leftLabel, TextView rightLabel,
                Switch toggle, String label) {
            this.track = track;
            this.leftLabel = leftLabel;
            this.rightLabel = rightLabel;
            this.toggle = toggle;
            this.label = label;
        }
    }

    private boolean isPullRefreshable() {
        if (currentPath == null || currentPath.isEmpty()) return false;
        switch (currentRenderer) {
            case "overview":
            case "stations":
            case "programs":
            case "sensors":
            case "weather":
            case "logs":
            case "diagnostics":
            case "plugins":
                return true;
            default:
                return false;
        }
    }

    private void showAppHelp() {
        currentPath = "";
        currentRenderer = "app_help";
        activeRequest = ++requestSequence;
        requestInFlight = false;
        shell(getString(R.string.app_help));

        addHelpSection(R.string.overview, R.string.help_home);
        addHelpSection(R.string.refresh, R.string.help_refresh);
        addHelpSection(R.string.stations, R.string.help_stations);
        addHelpSection(R.string.programs, R.string.help_programs);
        addHelpSection(R.string.sensors, R.string.help_sensors);
        addHelpSection(R.string.weather, R.string.help_weather);
        addHelpSection(R.string.logs, R.string.help_logs);
        addHelpSection(R.string.diagnostics, R.string.help_diagnostics);
        addHelpSection(R.string.plugins, R.string.help_plugins);
        addHelpSection(R.string.system, R.string.help_system);
        addHelpSection(R.string.app_settings, R.string.help_settings);
        addHelpSection(R.string.notifications, R.string.help_notification_speech);

        Button back = button(getString(R.string.back), RED);
        back.setOnClickListener(v -> showAppSettings());
        content.addView(back);
    }

    private void addHelpSection(int titleResource, int bodyResource) {
        LinearLayout section = cardColumn();
        section.addView(text(getString(titleResource), 16, true));
        TextView body = text(getString(bodyResource), 14, false);
        body.setTextColor(MUTED);
        section.addView(body);
        content.addView(section);
    }

    private String pushStatusText(PushSyncStatusStore.Snapshot snapshot) {
        if (PushSyncStatusStore.ERROR.equals(snapshot.state)) {
            return getString(
                    R.string.push_status_error,
                    pushStageText(snapshot.stage));
        }
        return pushStageText(snapshot.state);
    }

    private String pushStageText(String stage) {
        switch (stage) {
            case PushSyncStatusStore.FCM:
                return getString(R.string.push_status_fcm);
            case PushSyncStatusStore.APP_CHECK:
                return getString(R.string.push_status_app_check);
            case PushSyncStatusStore.OSPY:
                return getString(R.string.push_status_ospy);
            case PushSyncStatusStore.RELAY:
                return getString(R.string.push_status_relay);
            case PushSyncStatusStore.SAVE:
                return getString(R.string.push_status_save);
            case PushSyncStatusStore.READY:
                return getString(R.string.push_status_ready);
            case PushSyncStatusStore.DISABLED:
                return getString(R.string.push_status_disabled);
            default:
                return getString(R.string.push_status_never);
        }
    }

    private String pushTechnicalDetail(String detail) {
        switch (detail) {
            case PushSyncStatusStore.DETAIL_APP_CHECK_ATTESTATION_FAILED:
                return getString(
                        R.string.push_detail_app_check_attestation_failed);
            case PushSyncStatusStore.DETAIL_APP_CHECK_RATE_LIMITED:
                return getString(R.string.push_detail_app_check_rate_limited);
            case PushSyncStatusStore.DETAIL_TIMEOUT:
                return getString(R.string.push_detail_timeout);
            case PushSyncStatusStore.DETAIL_NETWORK:
                return getString(R.string.push_detail_network);
            case PushSyncStatusStore.DETAIL_UNEXPECTED:
                return getString(R.string.push_detail_unexpected);
            default:
                // Preserve structured HTTP details produced by OSPy and the
                // relay, but hide legacy R8-only names such as "ed".
                if (detail.matches("[a-z]{1,3}")) {
                    return getString(R.string.push_detail_unexpected);
                }
                return detail;
        }
    }

    private interface PreferenceSetter {
        void set(boolean enabled);
    }

    private void addPreferenceToggle(
            LinearLayout parent, String label, boolean enabled,
            PreferenceSetter setter) {
        LinearLayout row = actionRow();
        row.addView(text(label, 15, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button toggle = compactButton(
                getString(enabled ? R.string.enabled : R.string.disabled),
                enabled ? GREEN : NAVY);
        toggle.setOnClickListener(v -> {
            setter.set(!enabled);
            showAppSettings();
        });
        row.addView(toggle);
        parent.addView(row);
    }

    private void addNotificationPreference(
            LinearLayout parent, int labelResource, String category) {
        CheckBox option = new CheckBox(this);
        option.setText(getString(labelResource));
        option.setTextColor(TEXT);
        option.setTextSize(14);
        option.setChecked(notifications.isCategoryEnabled(category));
        option.setEnabled(notifications.isEnabled());
        option.setOnCheckedChangeListener((button, checked) ->
                notifications.setCategoryEnabled(category, checked));
        parent.addView(option, new LinearLayout.LayoutParams(-1, -2));
    }

    private void refreshSpeechStatus(TextView view) {
        if (view == null || view.getParent() == null ||
                !"app_settings".equals(currentRenderer) ||
                !notifications.isSpeechEnabled()) return;
        String language = Locale.forLanguageTag(SpeechAnnouncer.languageTag())
                .getDisplayName(getResources().getConfiguration().getLocales().get(0));
        String detail;
        switch (SpeechAnnouncer.status()) {
            case SpeechAnnouncer.STATUS_CHECKING:
                detail = getString(R.string.notification_speech_status_checking);
                break;
            case SpeechAnnouncer.STATUS_READY:
                detail = getString(R.string.notification_speech_status_ready, language);
                break;
            case SpeechAnnouncer.STATUS_SPEAKING:
                detail = getString(R.string.notification_speech_status_speaking);
                break;
            case SpeechAnnouncer.STATUS_SPOKEN:
                detail = getString(R.string.notification_speech_status_spoken);
                break;
            case SpeechAnnouncer.STATUS_MISSING_DATA:
                detail = getString(R.string.notification_speech_status_missing_data, language);
                break;
            case SpeechAnnouncer.STATUS_UNSUPPORTED:
                detail = getString(R.string.notification_speech_status_unsupported, language);
                break;
            default:
                detail = getString(R.string.notification_speech_status_error);
                break;
        }
        view.setText(getString(R.string.notification_speech_status, detail));
        view.setTextColor(
                SpeechAnnouncer.STATUS_ERROR.equals(SpeechAnnouncer.status()) ||
                        SpeechAnnouncer.STATUS_MISSING_DATA.equals(SpeechAnnouncer.status()) ||
                        SpeechAnnouncer.STATUS_UNSUPPORTED.equals(SpeechAnnouncer.status())
                        ? RED : MUTED);
        view.postDelayed(() -> refreshSpeechStatus(view), 500L);
    }

    private Button linkButton(String label, String url) {
        Button link = button(label, NAVY);
        link.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception error) {
                message(
                        getString(R.string.app_settings),
                        getString(R.string.cannot_open_link));
            }
        });
        return link;
    }

    private void showPairing() {
        showPairing(null);
    }

    private void showPairing(Installation reconnectTarget) {
        currentPath = "";
        currentRenderer = "";
        pairingApi = null;
        pairingInstallation = null;
        shell(getString(reconnectTarget == null
                ? R.string.add_installation : R.string.reconnect_installation));
        EditText url = input(getString(R.string.server_url), false);
        url.setHint(R.string.server_url_hint);
        EditText user = input(getString(R.string.username), false);
        EditText password = input(getString(R.string.password), true);
        EditText secondFactor = input(getString(R.string.two_factor), false);
        CheckBox unverified = new CheckBox(this);
        unverified.setText(getString(R.string.unverified_certificate));
        unverified.setTextSize(15);
        unverified.setTextColor(TEXT);
        TextView warning = text(
                getString(R.string.unverified_certificate_warning), 13, false);
        warning.setTextColor(RED);
        warning.setVisibility(View.GONE);
        unverified.setOnCheckedChangeListener(
                (button, checked) -> warning.setVisibility(
                        checked ? View.VISIBLE : View.GONE));
        content.addView(url);
        content.addView(user);
        content.addView(password);
        content.addView(secondFactor);
        content.addView(unverified);
        content.addView(warning);
        if (reconnectTarget != null) {
            url.setText(reconnectTarget.baseUrl);
            user.setText(reconnectTarget.username);
            unverified.setChecked(reconnectTarget.allowUnverifiedCertificate);
        }

        LinearLayout actions = actionRow();
        Button connect = button(getString(R.string.connect), GREEN);
        connect.setOnClickListener(v -> {
            String base = Installation.normalize(url.getText().toString());
            if (!Installation.isValidBaseUrl(base)) {
                toast(getString(R.string.complete_address));
                return;
            }
            connect.setEnabled(false);
            boolean allowUnverified = base.startsWith("https://") &&
                    unverified.isChecked();
            String username = user.getText().toString().trim();
            Installation savedIdentity = reconnectTarget;
            if (savedIdentity == null) {
                for (Installation saved : installations) {
                    if (saved.baseUrl.equals(base) &&
                            saved.username.equals(username)) {
                        savedIdentity = saved;
                        break;
                    }
                }
            }
            if (pairingInstallation == null ||
                    !pairingInstallation.baseUrl.equals(base) ||
                    !pairingInstallation.username.equals(username) ||
                    (savedIdentity != null &&
                            !pairingInstallation.id.equals(savedIdentity.id)) ||
                    pairingInstallation.allowUnverifiedCertificate != allowUnverified) {
                pairingInstallation = savedIdentity == null
                        ? new Installation(
                                UUID.randomUUID().toString(), base, base,
                                username, "", allowUnverified)
                        : new Installation(
                                savedIdentity.id, savedIdentity.name, base,
                                username, savedIdentity.refreshToken,
                                allowUnverified);
                pairingApi = new ApiClient(pairingInstallation, installationStore);
            }
            pairingApi.pair(
                    username,
                    password.getText().toString(),
                    secondFactor.getText().toString(),
                    new ApiClient.Callback() {
                        @Override public void success(JSONObject response) {
                            installations.removeIf(item ->
                                    item.id.equals(pairingInstallation.id));
                            installations.add(pairingInstallation);
                            open(pairingInstallation, pairingApi);
                            PushRegistrationManager.syncAll(MainActivity.this);
                            pairingApi = null;
                            pairingInstallation = null;
                        }

                        @Override public void failure(String error) {
                            connect.setEnabled(true);
                            message(
                                    getString(R.string.connection_failed),
                                    localizedError(error));
                        }
                    });
        });
        actions.addView(connect);
        Button back = button(getString(R.string.back), RED);
        back.setOnClickListener(v -> showInstallations());
        actions.addView(back);
        content.addView(actions);
    }

    private void showEditInstallation(Installation installation) {
        currentPath = "";
        currentRenderer = "";
        shell(getString(R.string.edit_installation));
        EditText name = input(getString(R.string.installation_name), false);
        name.setText(installation.name);
        EditText url = input(getString(R.string.server_url), false);
        url.setText(installation.baseUrl);
        CheckBox unverified = new CheckBox(this);
        unverified.setText(getString(R.string.unverified_certificate));
        unverified.setTextSize(15);
        unverified.setTextColor(TEXT);
        unverified.setChecked(installation.allowUnverifiedCertificate);
        content.addView(name);
        content.addView(url);
        content.addView(unverified);

        LinearLayout actions = actionRow();
        Button save = button(getString(R.string.save), GREEN);
        save.setOnClickListener(v -> {
            String base = Installation.normalize(url.getText().toString());
            if (!Installation.isValidBaseUrl(base)) {
                toast(getString(R.string.complete_address));
                return;
            }
            String displayName = name.getText().toString().trim();
            Installation changed = new Installation(
                    installation.id,
                    displayName.isEmpty() ? getString(R.string.app_name) : displayName,
                    base,
                    installation.username,
                    installation.refreshToken,
                    base.startsWith("https://") && unverified.isChecked());
            try {
                installationStore.updateMetadata(changed);
                showInstallations();
            } catch (Exception error) {
                message(
                        getString(R.string.protected_storage_error),
                        getString(R.string.protected_storage_error_detail));
            }
        });
        actions.addView(save);
        Button back = button(getString(R.string.back), RED);
        back.setOnClickListener(v -> showInstallations());
        actions.addView(back);
        content.addView(actions);
    }

    private void open(Installation installation) {
        open(installation, new ApiClient(installation, installationStore));
    }

    private void open(Installation installation, ApiClient connectedApi) {
        connectionAttemptGeneration++;
        appPreferences.setLastInstallationId(installation.id);
        current = installation;
        api = connectedApi;
        resetNotificationBaseline();
        resetSystemOperationState();
        showDashboard();
        if (liveUpdates != null) liveUpdates.stop();
        liveUpdates = new LiveUpdates(api, this::handleLiveEvent);
        liveUpdates.start();
        NotificationScheduler.update(this, false);
        // A confirmed foreground connection is the most reliable opportunity
        // to retry FCM/App Check and relay registration on restrictive phones.
        PushRegistrationManager.syncAll(this);
    }

    private void resetNotificationBaseline() {
        previousActiveStations.clear();
        previousRainBlock = null;
        notificationBaselineReady = false;
        notificationSnapshotInFlight = false;
    }

    private void handleLiveEvent(JSONObject event) {
        String type = event.optString("event");
        JSONObject data = event.optJSONObject("data");

        if ("notification".equals(type) && data != null) {
            handleServerNotification(data);
        } else if ("operation.completed".equals(type) ||
                "operation.failed".equals(type)) {
            handleOperationEvent(type, data);
        }

        boolean irrigationStateEvent = type.startsWith("station.") ||
                "stations.changed".equals(type) ||
                "conditions.changed".equals(type);
        if (irrigationStateEvent && !"overview".equals(currentRenderer)) {
            refreshNotificationSnapshot();
        }
        if (irrigationStateEvent ||
                type.startsWith("program.") ||
                "plugin.action".equals(type) ||
                "plugin.configured".equals(type)) {
            if (!requestInFlight && currentPath != null &&
                    !currentPath.isEmpty()) {
                fetch(currentPath, currentRenderer, false, loadGeneration);
            }
        }
    }

    private void handleServerNotification(JSONObject data) {
        if (current == null) return;
        notifications.showServerNotification(current, data);
    }

    private void handleOperationEvent(String eventType, JSONObject data) {
        if (data == null) return;
        String operationId = data.optString("id");
        String kind = data.optString("kind");
        if (!systemOperationId.isEmpty() &&
                systemOperationId.equals(operationId)) {
            systemOperationHandler.removeCallbacksAndMessages(null);
            systemOperationHandler.post(systemWaitingForReconnect
                    ? this::probeSystemAfterUpdate
                    : this::pollSystemOperation);
            return;
        }
        if (!kind.startsWith("update.")) return;
        boolean failed = "operation.failed".equals(eventType);
        notifications.show(
                NotificationCenter.CATEGORY_UPDATES,
                getString(failed
                        ? R.string.update_operation_failed
                        : R.string.update_operation_completed),
                failed
                        ? data.optString(
                                "error", getString(R.string.operation_failed))
                        : getString(R.string.update_operation_completed_message));
    }

    private void showDashboard() {
        shell(current.name, true);
        navigationButtons.clear();
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(6), dp(4), dp(6), dp(4));
        addNav(navigation, R.string.overview, "/overview", "overview");
        addNav(navigation, R.string.stations, "/stations", "stations");
        addNav(navigation, R.string.programs, "/programs", "programs");
        addNav(navigation, R.string.sensors, "/sensors", "sensors");
        addNav(navigation, R.string.weather, "/weather/forecast", "weather");
        addNav(navigation, R.string.logs, "/logs/events?limit=100", "logs");
        addNav(
                navigation, R.string.diagnostics,
                "/diagnostics/components", "diagnostics");
        addNav(navigation, R.string.plugins, "/plugins", "plugins");
        addNav(navigation, R.string.system, "/updates", "system");
        horizontal.addView(navigation);
        page.addView(horizontal, 1, new LinearLayout.LayoutParams(-1, -2));
        load("/overview", "overview");
    }

    private void addNav(
            LinearLayout navigation, int label, String path, String renderer) {
        Button item = compactButton(getString(label), NAVY);
        item.setOnClickListener(v -> load(path, renderer));
        navigationButtons.put(item, renderer);
        navigation.addView(item);
    }

    private void selectNavigation(String section) {
        activeSection = section;
        for (Map.Entry<Button, String> entry : navigationButtons.entrySet()) {
            boolean selected = entry.getValue().equals(section);
            styleButton(entry.getKey(), selected ? GREEN : NAVY, selected);
            entry.getKey().setAlpha(selected ? 1.0f : 0.82f);
        }
    }

    private void load(String path, String renderer) {
        clearActivePluginMobile();
        currentPath = path;
        currentRenderer = renderer;
        int generation = ++loadGeneration;
        // Do not let a response for the previous screen prevent the newly
        // selected screen from starting its own request.
        requestInFlight = false;
        resetOverviewViewState();
        selectNavigation(renderer);
        content.removeAllViews();
        content.addView(text(getString(R.string.loading), 16, false));
        fetch(path, renderer, true, generation);
    }

    private void fetch(
            String path, String renderer, boolean showFailure, int generation) {
        if (requestInFlight) return;
        requestInFlight = true;
        int requestNumber = ++requestSequence;
        activeRequest = requestNumber;
        int scrollY = contentScroll == null ? 0 : contentScroll.getScrollY();
        api.request("GET", path, null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (requestNumber != activeRequest) return;
                requestInFlight = false;
                if (generation != loadGeneration ||
                        !path.equals(currentPath) ||
                        !renderer.equals(currentRenderer)) return;
                updateConnectionStatus(true);

                boolean updateOverviewInPlace =
                        "overview".equals(renderer) && !showFailure &&
                                overviewRoot != null &&
                                overviewRoot.getParent() == content;
                if (!updateOverviewInPlace) content.removeAllViews();

                Object data = response.opt("data");
                if ("overview".equals(renderer) && data instanceof JSONObject) {
                    renderOverview((JSONObject) data);
                } else if ("stations".equals(renderer) && data instanceof JSONArray) {
                    renderStations((JSONArray) data);
                } else if ("programs".equals(renderer) && data instanceof JSONArray) {
                    renderPrograms((JSONArray) data);
                } else if ("sensors".equals(renderer) && data instanceof JSONArray) {
                    renderSensors((JSONArray) data);
                } else if ("weather".equals(renderer) && data instanceof JSONObject) {
                    renderWeather((JSONObject) data, true);
                } else if ("logs".equals(renderer) && data instanceof JSONArray) {
                    renderLogs((JSONArray) data);
                } else if ("diagnostics".equals(renderer) &&
                        data instanceof JSONObject) {
                    renderDiagnostics((JSONObject) data);
                } else if ("plugins".equals(renderer) && data instanceof JSONArray) {
                    renderPlugins((JSONArray) data);
                } else if ("system".equals(renderer)) {
                    renderSystem(data);
                } else {
                    renderFallback(data);
                }
                if (!showFailure && contentScroll != null) {
                    contentScroll.post(() -> contentScroll.scrollTo(0, scrollY));
                }
            }

            @Override public void failure(String error) {
                if (requestNumber != activeRequest) return;
                requestInFlight = false;
                if (generation != loadGeneration ||
                        !path.equals(currentPath) ||
                        !renderer.equals(currentRenderer)) return;
                updateConnectionStatus(false);
                if (!showFailure) return;
                content.removeAllViews();
                resetOverviewViewState();
                content.addView(text(localizedError(error), 16, true));
                Button retry = button(getString(R.string.refresh), GREEN);
                retry.setOnClickListener(v -> load(path, renderer));
                content.addView(retry);
            }
        });
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            lastSuccessfulApiUpdate = LocalDateTime.now();
            if (connectionStatusView != null) {
                connectionStatusView.setVisibility(View.GONE);
            }
            return;
        }
        if (connectionStatusView == null) return;
        String lastUpdate = lastSuccessfulApiUpdate == null
                ? getString(R.string.not_available)
                : lastSuccessfulApiUpdate.format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        connectionStatusView.setText(
                getString(R.string.connection_lost) + "\n" +
                        getString(R.string.last_successful_update) + ": " +
                        lastUpdate);
        connectionStatusView.setVisibility(View.VISIBLE);
    }

    private void renderOverview(JSONObject data) {
        ensureOverviewLayout();

        JSONObject instance = data.optJSONObject("instance");
        JSONObject irrigation = data.optJSONObject("irrigation");
        if (irrigation == null) {
            overviewIrrigationSection.setVisibility(View.GONE);
            overviewRainBlockActive = false;
            overviewBlockedTimelineCache.clear();
        } else {
            overviewIrrigationSection.setVisibility(View.VISIBLE);
            handleOverviewNotificationTransitions(irrigation);
            boolean schedulerEnabled =
                    irrigation.optBoolean("scheduler_enabled");
            boolean manualMode = irrigation.optBoolean("manual_mode");
            boolean rainBlock = irrigation.optBoolean("rain_block");
            overviewRainBlockActive = rainBlock;
            if (!rainBlock) {
                overviewBlockedTimelineCache.clear();
            }

            updateOverviewSwitch(
                    overviewSchedulerControl, !schedulerEnabled,
                    "scheduler_enabled", false, GREEN, RED);
            updateOverviewSwitch(
                    overviewManualControl, manualMode,
                    "manual_mode", true, GREEN, NAVY);
            updateOverviewRainControl(
                    rainBlock, irrigation.optLong("rain_block_seconds", 0));
            double userLevelPercent = irrigation.optDouble(
                    "level_adjustment_percent", 100.0);
            updateOverviewWaterLevelControl(
                    userLevelPercent,
                    irrigation.isNull("level_adjustment")
                            ? userLevelPercent
                            : irrigation.optDouble(
                                    "level_adjustment", 1.0) * 100.0);
            updateOverviewActiveStations(
                    irrigation.optJSONArray("active_stations"));
        }

        String updated = data.optString("updated");
        updateOverviewServerDate(updated);

        JSONArray warnings = data.optJSONArray("warnings");
        updateOverviewWarnings(warnings);

        if (instance == null || instance.optString("version").isEmpty()) {
            overviewVersionView.setVisibility(View.GONE);
        } else {
            overviewVersionView.setText(
                    getString(R.string.version) + ": " +
                            instance.optString("version"));
            overviewVersionView.setVisibility(View.VISIBLE);
        }

        // Keep the existing timeline visible while fresh data for the selected
        // calendar day is loaded.
        updateScheduleDayButtons();
        updateOverviewTimelineHeader();
        loadTimeline(schedulePathForSelectedDay());
    }


    private void resetOverviewViewState() {
        activeOverviewTimelineRequest = ++overviewTimelineRequestSequence;
        overviewTimelineRequestInFlight = false;
        overviewTimelineRefreshPending = false;
        overviewTimelineActivePath = "";
        overviewTimelinePendingPath = "";
        overviewServerDate = null;
        selectedScheduleDay = ScheduleDay.TODAY;
        overviewRoot = null;
        overviewIrrigationSection = null;
        overviewSchedulerControl = null;
        overviewManualControl = null;
        overviewRainControl = null;
        overviewWaterLevelControl = null;
        overviewActiveCount = null;
        overviewActiveStationsContainer = null;
        overviewActiveStationRows.clear();
        overviewActiveStationStructure = "";
        overviewVersionView = null;
        overviewWarningsSection = null;
        overviewWarningsSignature = "";
        overviewTimelineNow = null;
        overviewTimelineRowsContainer = null;
        overviewTimelineError = null;
        overviewYesterdayButton = null;
        overviewTodayButton = null;
        overviewTomorrowButton = null;
        overviewTimelineRows.clear();
        overviewTimelineStructure = "";
        overviewBlockedTimelineCache.clear();
        overviewRainBlockActive = false;
    }

    private void ensureOverviewLayout() {
        if (overviewRoot != null && overviewRoot.getParent() == content) return;

        resetOverviewViewState();
        overviewRoot = new LinearLayout(this);
        overviewRoot.setOrientation(LinearLayout.VERTICAL);

        overviewIrrigationSection = new LinearLayout(this);
        overviewIrrigationSection.setOrientation(LinearLayout.VERTICAL);
        overviewIrrigationSection.addView(
                overviewHeading(getString(R.string.irrigation)));

        LinearLayout summary = cardColumn();
        overviewSchedulerControl = createOverviewSwitch(
                summary, getString(R.string.scheduler),
                getString(R.string.on), getString(R.string.off));
        overviewManualControl = createOverviewSwitch(
                summary, getString(R.string.operating_mode),
                getString(R.string.scheduler), getString(R.string.manual_mode));
        overviewRainControl = createOverviewControl(
                summary, getString(R.string.rain_delay), 0.35f);
        overviewWaterLevelControl = createOverviewControl(
                summary, getString(R.string.water_level_adjustment), 0.35f);
        overviewActiveCount = createPairBinding(
                summary, getString(R.string.active_stations));
        overviewActiveStationsContainer = new LinearLayout(this);
        overviewActiveStationsContainer.setOrientation(LinearLayout.VERTICAL);
        summary.addView(
                overviewActiveStationsContainer,
                new LinearLayout.LayoutParams(-1, -2));
        overviewIrrigationSection.addView(summary);
        overviewRoot.addView(overviewIrrigationSection);

        Button stop = button(getString(R.string.stop_all), RED);
        stop.setOnClickListener(v -> confirmAction(
                getString(R.string.confirm_stop_all),
                "/stations/actions/stop-all",
                this::refreshCurrentOverview));
        overviewRoot.addView(stop);

        overviewRoot.addView(
                overviewHeading(getString(R.string.schedule)));

        LinearLayout scheduleDaySelector = actionRow();
        overviewYesterdayButton = createScheduleDayButton(
                scheduleDaySelector, getString(R.string.yesterday),
                ScheduleDay.YESTERDAY);
        overviewTodayButton = createScheduleDayButton(
                scheduleDaySelector, getString(R.string.today),
                ScheduleDay.TODAY);
        overviewTomorrowButton = createScheduleDayButton(
                scheduleDaySelector, getString(R.string.tomorrow),
                ScheduleDay.TOMORROW);
        overviewRoot.addView(
                scheduleDaySelector,
                new LinearLayout.LayoutParams(-1, -2));
        updateScheduleDayButtons();

        LinearLayout timeline = cardColumn();
        overviewTimelineNow = text("", 14, true);
        overviewTimelineNow.setTextColor(HEADING);
        timeline.addView(overviewTimelineNow);
        overviewTimelineRowsContainer = new LinearLayout(this);
        overviewTimelineRowsContainer.setOrientation(LinearLayout.VERTICAL);
        overviewTimelineRowsContainer.addView(text(
                getString(R.string.loading), 14, false));
        timeline.addView(
                overviewTimelineRowsContainer,
                new LinearLayout.LayoutParams(-1, -2));
        overviewTimelineError = text("", 13, false);
        overviewTimelineError.setTextColor(RED);
        overviewTimelineError.setVisibility(View.GONE);
        timeline.addView(overviewTimelineError);
        overviewRoot.addView(timeline);

        overviewWarningsSection = new LinearLayout(this);
        overviewWarningsSection.setOrientation(LinearLayout.VERTICAL);
        overviewWarningsSection.setVisibility(View.GONE);
        overviewRoot.addView(overviewWarningsSection);

        overviewVersionView = text("", 11, false);
        overviewVersionView.setTextColor(MUTED);
        overviewVersionView.setGravity(Gravity.END);
        overviewRoot.addView(
                overviewVersionView,
                new LinearLayout.LayoutParams(-1, -2));

        content.addView(
                overviewRoot, new LinearLayout.LayoutParams(-1, -2));
    }

    private Button createScheduleDayButton(
            LinearLayout parent, String label, ScheduleDay day) {
        Button button = compactButton(label, NAVY);
        LinearLayout.LayoutParams layout =
                new LinearLayout.LayoutParams(0, -2, 1);
        layout.setMargins(dp(3), dp(3), dp(3), dp(6));
        button.setLayoutParams(layout);
        button.setOnClickListener(v -> selectScheduleDay(day));
        parent.addView(button);
        return button;
    }

    private void selectScheduleDay(ScheduleDay day) {
        boolean changed = selectedScheduleDay != day;
        selectedScheduleDay = day;
        updateScheduleDayButtons();
        updateOverviewTimelineHeader();

        if (changed) {
            // Invalidate an older response immediately. It may still arrive,
            // but its callback is ignored and cannot replace the selected day.
            activeOverviewTimelineRequest = ++overviewTimelineRequestSequence;
            overviewTimelineRequestInFlight = false;
            overviewTimelineRefreshPending = false;
            overviewTimelineActivePath = "";
            overviewTimelinePendingPath = "";
            showOverviewTimelineLoading();
        }
        loadTimeline(schedulePathForSelectedDay());
    }

    private void updateScheduleDayButtons() {
        styleScheduleDayButton(
                overviewYesterdayButton,
                selectedScheduleDay == ScheduleDay.YESTERDAY);
        styleScheduleDayButton(
                overviewTodayButton,
                selectedScheduleDay == ScheduleDay.TODAY);
        styleScheduleDayButton(
                overviewTomorrowButton,
                selectedScheduleDay == ScheduleDay.TOMORROW);
    }

    private void styleScheduleDayButton(Button button, boolean selected) {
        if (button == null) return;
        styleButton(button, selected ? GREEN : NAVY, selected);
        button.setAlpha(selected ? 1.0f : 0.82f);
    }

    private void showOverviewTimelineLoading() {
        overviewTimelineStructure = "";
        overviewTimelineRows.clear();
        if (overviewTimelineRowsContainer != null) {
            overviewTimelineRowsContainer.removeAllViews();
            overviewTimelineRowsContainer.addView(text(
                    getString(R.string.loading), 14, false));
        }
        if (overviewTimelineError != null) {
            overviewTimelineError.setVisibility(View.GONE);
        }
    }

    private void updateOverviewServerDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) return;
        try {
            overviewServerDate = LocalDate.parse(timestamp.substring(0, 10));
        } catch (Exception ignored) {
            // Keep the previous server date. The device date is used only when
            // the API response does not contain a parseable ISO date.
        }
    }

    private LocalDate selectedScheduleDate() {
        LocalDate base = overviewServerDate == null
                ? LocalDate.now() : overviewServerDate;
        return base.plusDays(selectedScheduleDay.offsetDays);
    }

    private String schedulePathForSelectedDay() {
        return OverviewTimelinePolicy.pathFor(
                selectedScheduleDay.offsetDays, selectedScheduleDate());
    }

    private int selectedScheduleDayLabel() {
        switch (selectedScheduleDay) {
            case YESTERDAY:
                return R.string.yesterday;
            case TOMORROW:
                return R.string.tomorrow;
            case TODAY:
            default:
                return R.string.today;
        }
    }

    private void updateOverviewTimelineHeader() {
        if (overviewTimelineNow == null) return;
        LocalDate date = selectedScheduleDate();
        String formattedDate = DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .format(date);
        String label = getString(selectedScheduleDayLabel());
        if (selectedScheduleDay == ScheduleDay.TODAY) {
            String currentTime = new java.text.SimpleDateFormat(
                    "HH:mm", Locale.getDefault())
                    .format(new java.util.Date());
            overviewTimelineNow.setText(
                    label + " · " + formattedDate + " · " +
                            getString(R.string.now) + " " + currentTime);
        } else {
            overviewTimelineNow.setText(label + " · " + formattedDate);
        }
    }

    private TextView overviewHeading(String value) {
        TextView view = text(value, 18, true);
        view.setTextColor(HEADING);
        view.setPadding(0, dp(10), 0, dp(5));
        return view;
    }

    private OverviewControlBinding createOverviewControl(
            LinearLayout parent, String label, float valueWeight) {
        LinearLayout row = actionRow();
        TextView name = text(label, 14, true);
        name.setTextColor(MUTED);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 0.38f));
        TextView currentState = text("", 14, false);
        row.addView(
                currentState,
                new LinearLayout.LayoutParams(0, -2, valueWeight));
        Button action = compactButton("", GREEN);
        row.addView(action);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return new OverviewControlBinding(currentState, action);
    }

    private OverviewSwitchBinding createOverviewSwitch(
            LinearLayout parent, String label, String left, String right) {
        TextView name = text(label, 14, true);
        name.setTextColor(MUTED);
        name.setPadding(dp(3), dp(5), dp(3), dp(2));
        parent.addView(name, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout track = actionRow();
        track.setPadding(dp(12), dp(3), dp(12), dp(3));
        TextView leftLabel = text(left, 13, false);
        leftLabel.setGravity(Gravity.CENTER);
        leftLabel.setTextColor(Color.WHITE);
        track.addView(leftLabel, new LinearLayout.LayoutParams(0, -2, 1));

        Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        toggle.setTrackTintList(ColorStateList.valueOf(0x66FFFFFF));
        toggle.setMinWidth(dp(54));
        track.addView(toggle, new LinearLayout.LayoutParams(-2, -2));

        TextView rightLabel = text(right, 13, false);
        rightLabel.setGravity(Gravity.CENTER);
        rightLabel.setTextColor(Color.WHITE);
        track.addView(rightLabel, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(dp(3), 0, dp(3), dp(6));
        parent.addView(track, layout);
        return new OverviewSwitchBinding(
                track, leftLabel, rightLabel, toggle, label);
    }

    private PairBinding createPairBinding(
            LinearLayout parent, String labelText) {
        LinearLayout row = actionRow();
        TextView label = text(labelText, 14, true);
        label.setTextColor(MUTED);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 0.42f));
        TextView value = text("", 14, false);
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 0.58f));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return new PairBinding(label, value);
    }

    private void updateOverviewSwitch(
            OverviewSwitchBinding binding, boolean rightSelected, String key,
            boolean rightApiValue, int leftColor, int rightColor) {
        binding.toggle.setOnCheckedChangeListener(null);
        binding.toggle.setChecked(rightSelected);
        binding.track.setBackground(background(
                rightSelected ? rightColor : leftColor,
                rightSelected ? rightColor : leftColor, 20));
        binding.leftLabel.setTypeface(
                Typeface.DEFAULT,
                rightSelected ? Typeface.NORMAL : Typeface.BOLD);
        binding.rightLabel.setTypeface(
                Typeface.DEFAULT,
                rightSelected ? Typeface.BOLD : Typeface.NORMAL);
        binding.leftLabel.setAlpha(rightSelected ? 0.72f : 1.0f);
        binding.rightLabel.setAlpha(rightSelected ? 1.0f : 0.72f);
        String activeLabel = rightSelected
                ? binding.rightLabel.getText().toString()
                : binding.leftLabel.getText().toString();
        binding.toggle.setContentDescription(
                binding.label + ": " + activeLabel);
        binding.track.setOnClickListener(v ->
                binding.toggle.setChecked(!binding.toggle.isChecked()));
        binding.toggle.setOnCheckedChangeListener((button, checked) -> put(
                "/irrigation",
                jsonBoolean(key, checked ? rightApiValue : !rightApiValue),
                this::refreshCurrentOverview));
    }

    private void updateOverviewRainControl(
            boolean enabled, long remainingSeconds) {
        overviewRainControl.value.setText(
                enabled
                        ? getString(
                                R.string.rain_delay_remaining,
                                formatDuration(remainingSeconds))
                        : getString(R.string.inactive));
        overviewRainControl.action.setText(getString(
                enabled ? R.string.turn_off : R.string.set));
        styleButton(
                overviewRainControl.action, enabled ? RED : GREEN, false);
        overviewRainControl.action.setOnClickListener(v -> {
            if (enabled) {
                JSONObject change = new JSONObject();
                try {
                    change.put("rain_delay_hours", 0);
                } catch (Exception ignored) {
                }
                put("/irrigation", change, this::refreshCurrentOverview);
            } else {
                showRainDelayDialog();
            }
        });
    }

    private void updateOverviewWaterLevelControl(
            double userPercent, double effectivePercent) {
        overviewWaterLevelControl.value.setText(getString(
                R.string.water_level_value,
                Math.round(userPercent), Math.round(effectivePercent)));
        overviewWaterLevelControl.action.setText(getString(R.string.set));
        styleButton(overviewWaterLevelControl.action, GREEN, false);
        overviewWaterLevelControl.action.setOnClickListener(
                v -> showWaterLevelDialog(userPercent));
    }

    private void showWaterLevelDialog(double currentPercent) {
        EditText input = numericInput(String.valueOf(Math.round(currentPercent)));
        new AlertDialog.Builder(this)
                .setTitle(R.string.water_level_adjustment)
                .setMessage(R.string.water_level_adjustment_description)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.reset, (dialog, which) -> {
                    JSONObject change = new JSONObject();
                    try {
                        change.put("level_adjustment_percent", 100);
                    } catch (Exception ignored) {
                    }
                    put("/irrigation", change, this::refreshCurrentOverview);
                })
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        double value = Double.parseDouble(
                                input.getText().toString().trim());
                        if (value < 0 || value > 1000) throw new NumberFormatException();
                        JSONObject change = new JSONObject();
                        change.put("level_adjustment_percent", value);
                        put("/irrigation", change, this::refreshCurrentOverview);
                    } catch (Exception error) {
                        message(
                                getString(R.string.water_level_adjustment),
                                getString(R.string.invalid_water_level_adjustment));
                    }
                })
                .show();
    }

    private void refreshCurrentOverview() {
        if (!"overview".equals(currentRenderer) ||
                currentPath == null || currentPath.isEmpty() ||
                requestInFlight) return;
        fetch(currentPath, currentRenderer, false, loadGeneration);
    }

    private void updateOverviewActiveStations(JSONArray active) {
        int count = active == null ? 0 : active.length();
        overviewActiveCount.value.setText(String.valueOf(count));

        StringBuilder structure = new StringBuilder();
        for (int i = 0; i < count; i++) {
            JSONObject station = active.optJSONObject(i);
            if (station == null) continue;
            structure.append(stationStableKey(station))
                    .append('|')
                    .append(station.optString("name"))
                    .append(';');
        }
        String newStructure = structure.toString();
        if (!newStructure.equals(overviewActiveStationStructure)) {
            overviewActiveStationStructure = newStructure;
            overviewActiveStationsContainer.removeAllViews();
            overviewActiveStationRows.clear();
            for (int i = 0; i < count; i++) {
                JSONObject station = active.optJSONObject(i);
                if (station == null) continue;
                overviewActiveStationRows.add(createPairBinding(
                        overviewActiveStationsContainer,
                        station.optString("name")));
            }
        }

        int row = 0;
        for (int i = 0; i < count; i++) {
            JSONObject station = active.optJSONObject(i);
            if (station == null) continue;
            if (row >= overviewActiveStationRows.size()) break;
            PairBinding binding = overviewActiveStationRows.get(row++);
            binding.label.setText(station.optString("name"));
            int remaining = station.optInt("remaining_seconds", -1);
            binding.value.setText(
                    remaining >= 0
                            ? formatCountdown(remaining)
                            : getString(R.string.running));
        }
    }

    private String stationStableKey(JSONObject station) {
        String id = station.optString("id");
        return id.isEmpty()
                ? String.valueOf(station.optInt("number", -1))
                : id;
    }

    private void handleOverviewNotificationTransitions(JSONObject irrigation) {
        Map<String, String> activeNow = new LinkedHashMap<>();
        JSONArray active = irrigation.optJSONArray("active_stations");
        if (active != null) {
            for (int index = 0; index < active.length(); index++) {
                JSONObject station = active.optJSONObject(index);
                if (station == null) continue;
                activeNow.put(
                        stationStableKey(station),
                        station.optString(
                                "name",
                                getString(R.string.station)));
            }
        }
        boolean rainBlock = irrigation.optBoolean("rain_block");

        if (notificationBaselineReady) {
            for (Map.Entry<String, String> station : activeNow.entrySet()) {
                if (!previousActiveStations.containsKey(station.getKey())) {
                    notifications.show(
                            NotificationCenter.CATEGORY_STATION_STARTED,
                            getString(R.string.station_started_notification_title),
                            getString(
                                    R.string.station_started_notification_message,
                                    station.getValue()));
                }
            }
            for (Map.Entry<String, String> station :
                    previousActiveStations.entrySet()) {
                if (!activeNow.containsKey(station.getKey())) {
                    notifications.show(
                            NotificationCenter.CATEGORY_STATION_STOPPED,
                            getString(R.string.station_stopped_notification_title),
                            getString(
                                    R.string.station_stopped_notification_message,
                                    station.getValue()));
                }
            }
            if (previousRainBlock != null &&
                    previousRainBlock.booleanValue() != rainBlock) {
                if (rainBlock) {
                    notifications.show(
                            NotificationCenter.CATEGORY_RAIN,
                            getString(R.string.rain_delay_started_notification_title),
                            getString(
                                    R.string.rain_delay_started_notification_message,
                                    formatDuration(irrigation.optLong(
                                            "rain_block_seconds", 0))));
                } else {
                    notifications.show(
                            NotificationCenter.CATEGORY_RAIN,
                            getString(R.string.rain_delay_cleared_notification_title),
                            getString(R.string.rain_delay_cleared_notification_message));
                }
            }
        }

        previousActiveStations.clear();
        previousActiveStations.putAll(activeNow);
        previousRainBlock = rainBlock;
        notificationBaselineReady = true;
    }

    private void refreshNotificationSnapshot() {
        if (api == null || notificationSnapshotInFlight) return;
        notificationSnapshotInFlight = true;
        api.request("GET", "/overview", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                notificationSnapshotInFlight = false;
                JSONObject data = response.optJSONObject("data");
                JSONObject irrigation = data == null
                        ? null : data.optJSONObject("irrigation");
                if (irrigation != null) {
                    handleOverviewNotificationTransitions(irrigation);
                }
            }

            @Override public void failure(String error) {
                notificationSnapshotInFlight = false;
            }
        });
    }

    private void updateOverviewWarnings(JSONArray warnings) {
        StringBuilder signature = new StringBuilder();
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                JSONObject warning = warnings.optJSONObject(i);
                if (warning != null) {
                    signature.append(warning.optString("message")).append('\n');
                }
            }
        }
        String newSignature = signature.toString();
        if (newSignature.equals(overviewWarningsSignature)) return;
        overviewWarningsSignature = newSignature;
        overviewWarningsSection.removeAllViews();
        if (newSignature.isEmpty()) {
            overviewWarningsSection.setVisibility(View.GONE);
            return;
        }
        overviewWarningsSection.setVisibility(View.VISIBLE);
        overviewWarningsSection.addView(
                overviewHeading(getString(R.string.warnings)));
        for (int i = 0; i < warnings.length(); i++) {
            JSONObject warning = warnings.optJSONObject(i);
            if (warning != null) {
                overviewWarningsSection.addView(statusCard(
                        getString(R.string.warnings),
                        warning.optString("message"), "warning"));
            }
        }
    }

    private void renderStations(JSONArray stationItems) {
        final int generation = loadGeneration;
        content.addView(text(getString(R.string.loading), 16, false));
        api.request("GET", "/irrigation", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (generation != loadGeneration ||
                        !"stations".equals(currentRenderer)) return;
                JSONObject irrigation = response.optJSONObject("data");
                renderStationCards(
                        stationItems,
                        irrigation != null &&
                                irrigation.optBoolean("manual_mode"),
                        "");
            }

            @Override public void failure(String error) {
                if (generation != loadGeneration ||
                        !"stations".equals(currentRenderer)) return;
                renderStationCards(stationItems, false, localizedError(error));
            }
        });
    }

    private void renderStationCards(
            JSONArray stationItems, boolean manualMode, String stateError) {
        content.removeAllViews();
        if (!stateError.isEmpty()) {
            content.addView(statusCard(
                    getString(R.string.manual_mode), stateError, "warning"));
        } else if (!manualMode) {
            content.addView(statusCard(
                    getString(R.string.manual_mode),
                    getString(R.string.manual_mode_required_for_station_start),
                    "warning"));
        } else {
            content.addView(statusCard(
                    getString(R.string.manual_mode),
                    getString(R.string.manual_station_start_hint),
                    "ok"));
        }
        for (int i = 0; i < stationItems.length(); i++) {
            JSONObject station = stationItems.optJSONObject(i);
            if (station == null) continue;
            if (!station.optBoolean("enabled", true)) continue;
            LinearLayout card = card();
            String stationState = station.optBoolean("running")
                    ? getString(R.string.running) : getString(R.string.stopped);
            int remaining = station.optInt("remaining_seconds", 0);
            String timing = station.optBoolean("running") && remaining >= 0
                    ? " · " + formatCountdown(remaining)
                    : "";
            TextView label = text(
                    station.optInt("number") + ". " + station.optString("name") +
                            "\n" + stationState + timing,
                    16, true);
            card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (!station.optBoolean("is_master") &&
                    !station.optBoolean("is_master_two") &&
                    !station.optBoolean("is_program_master")) {
                boolean running = station.optBoolean("running");
                String action = running ? "stop" : "start";
                Button toggle = button(
                        getString(running ? R.string.stop : R.string.start),
                        running ? RED : GREEN);
                toggle.setOnClickListener(v -> post(
                        "/stations/" + station.optString("id") +
                                "/actions/" + action,
                        new JSONObject(),
                        () -> load("/stations", "stations")));
                if (!running) {
                    toggle.setContentDescription(getString(
                            R.string.start_station_button_description));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        toggle.setTooltipText(getString(
                                R.string.hold_start_for_duration));
                    }
                    toggle.setOnLongClickListener(v -> {
                        if (!manualMode) return false;
                        showTimedStationStartDialog(station);
                        return true;
                    });
                }
                if (!running && !manualMode) {
                    toggle.setEnabled(false);
                    toggle.setAlpha(0.45f);
                }
                card.addView(toggle);
            }
            content.addView(card);
        }
        Button stop = button(getString(R.string.stop_all), RED);
        stop.setOnClickListener(v -> confirmAction(
                getString(R.string.confirm_stop_all),
                "/stations/actions/stop-all"));
        content.addView(stop);
    }

    private void showTimedStationStartDialog(JSONObject station) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.HORIZONTAL);
        int padding = dp(16);
        form.setPadding(padding, 0, padding, 0);
        EditText minutes = numericInput("10");
        EditText seconds = numericInput("0");
        form.addView(
                labelledInput(getString(R.string.manual_minutes), minutes),
                new LinearLayout.LayoutParams(0, -2, 1));
        form.addView(
                labelledInput(getString(R.string.manual_seconds), seconds),
                new LinearLayout.LayoutParams(0, -2, 1));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(
                        R.string.timed_station_start,
                        station.optString("name")))
                .setMessage(R.string.timed_station_start_description)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.start, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final int duration;
            try {
                duration = manualDurationSeconds(
                        minutes.getText().toString(),
                        seconds.getText().toString());
            } catch (IllegalArgumentException error) {
                message(
                        getString(R.string.manual_run_duration),
                        getString(R.string.invalid_station_duration));
                return;
            }
            JSONObject body = new JSONObject();
            try {
                body.put("duration_seconds", duration);
            } catch (Exception error) {
                return;
            }
            api.request(
                    "POST",
                    "/stations/" + station.optString("id") + "/actions/start",
                    body,
                    new ApiClient.Callback() {
                        @Override public void success(JSONObject response) {
                            dialog.dismiss();
                            load("/stations", "stations");
                        }

                        @Override public void failure(String error) {
                            message(
                                    getString(R.string.app_name),
                                    localizedError(error));
                        }
                    });
        }));
        dialog.show();
    }

    static int manualDurationSeconds(String minuteValue, String secondValue) {
        try {
            int minutes = Integer.parseInt(minuteValue.trim());
            int seconds = Integer.parseInt(secondValue.trim());
            if (minutes < 0 || minutes > 999 || seconds < 0 || seconds > 59) {
                throw new IllegalArgumentException();
            }
            int total = minutes * 60 + seconds;
            if (total < 1 || total > 59999) throw new IllegalArgumentException();
            return total;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException();
        }
    }

    private void renderPrograms(JSONArray programItems) {
        final int generation = loadGeneration;
        api.request("GET", "/program-groups", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (generation != loadGeneration ||
                        !"programs".equals(currentRenderer)) return;
                JSONArray groups = response.optJSONArray("data");
                content.removeAllViews();
                renderProgramsWithGroups(
                        programItems, groups == null ? new JSONArray() : groups);
            }

            @Override public void failure(String error) {
                if (generation != loadGeneration ||
                        !"programs".equals(currentRenderer)) return;
                content.removeAllViews();
                renderProgramsWithGroups(programItems, new JSONArray());
            }
        });
    }

    private void renderProgramsWithGroups(
            JSONArray programItems, JSONArray groups) {
        Button addProgram = button(getString(R.string.add_program), GREEN);
        addProgram.setOnClickListener(v -> loadNewProgramEditor());
        content.addView(addProgram);
        for (int index = 0; index < groups.length(); index++) {
            JSONObject group = groups.optJSONObject(index);
            if (group == null) continue;
            LinearLayout groupCard = addProgramGroupCard(group);
            for (int programIndex = 0;
                    programIndex < programItems.length(); programIndex++) {
                JSONObject program = programItems.optJSONObject(programIndex);
                if (program != null && group.optString("id").equals(
                        program.optString("group_id", "default"))) {
                    addProgramCard(program, groupCard, true);
                }
            }
        }
        for (int i = 0; i < programItems.length(); i++) {
            JSONObject program = programItems.optJSONObject(i);
            if (program == null) continue;
            boolean shown = false;
            for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
                JSONObject group = groups.optJSONObject(groupIndex);
                if (group != null && group.optString("id").equals(
                        program.optString("group_id", "default"))) {
                    shown = true;
                    break;
                }
            }
            if (!shown) addProgramCard(program);
        }
    }

    private LinearLayout addProgramGroupCard(JSONObject group) {
        LinearLayout card = cardColumn();
        card.addView(text(group.optString("name"), 17, true));
        JSONArray nextRuns = group.optJSONArray("next_runs");
        if (nextRuns != null && nextRuns.length() > 0) {
            JSONObject next = nextRuns.optJSONObject(0);
            addPair(
                    card, getString(R.string.next_group_run),
                    next == null ? getString(R.string.not_available)
                            : formatTimestamp(next.optString("start")));
        }
        JSONObject postponement = group.optJSONObject("postponement");
        LinearLayout actions = actionRow();
        if (postponement == null) {
            Button postpone = compactButton(
                    getString(R.string.postpone_group), AMBER);
            postpone.setEnabled(nextRuns != null && nextRuns.length() > 0);
            postpone.setOnClickListener(v -> showPostponeGroupDialog(group));
            actions.addView(postpone);
        } else {
            addPair(
                    card, getString(R.string.postponed_until),
                    formatTimestamp(postponement.optString("target_start")));
            Button cancel = compactButton(
                    getString(R.string.cancel_postponement), RED);
            cancel.setOnClickListener(v -> api.request(
                    "DELETE",
                    "/program-groups/" + Uri.encode(group.optString("id")) +
                            "/postponements/" + Uri.encode(
                                    postponement.optString("id")),
                    null, reloadProgramsCallback()));
            actions.addView(cancel);
        }
        card.addView(actions);
        content.addView(card);
        return card;
    }

    private void showPostponeGroupDialog(JSONObject group) {
        JSONArray nextRuns = group.optJSONArray("next_runs");
        JSONObject next = nextRuns == null ? null : nextRuns.optJSONObject(0);
        LocalDateTime initial = LocalDateTime.now().plusDays(1);
        try {
            if (next != null) {
                initial = LocalDateTime.parse(next.optString("start")).plusHours(1);
            }
        } catch (Exception ignored) {
        }
        Button target = dateTimeButton(initial.toString());
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), dp(4));
        form.addView(text(getString(
                R.string.postpone_group_description,
                group.optString("name")), 14, false));
        form.addView(labelledView(getString(R.string.new_start), target));
        new AlertDialog.Builder(this)
                .setTitle(R.string.postpone_group)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    try {
                        JSONObject payload = new JSONObject().put(
                                "target_start",
                                ((LocalDateTime) target.getTag()).toString());
                        api.request(
                                "POST",
                                "/program-groups/" + Uri.encode(
                                        group.optString("id")) +
                                        "/postponements",
                                payload, reloadProgramsCallback());
                    } catch (Exception error) {
                        message(
                                getString(R.string.postpone_group),
                                getString(R.string.invalid_group_postponement));
                    }
                })
                .show();
    }

    private ApiClient.Callback reloadProgramsCallback() {
        return new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                load("/programs", "programs");
            }

            @Override public void failure(String error) {
                message(
                        getString(R.string.program_group),
                        localizedError(error));
            }
        };
    }

    private void addProgramCard(JSONObject program) {
        addProgramCard(program, content, false);
    }

    private void addProgramCard(
            JSONObject program, LinearLayout parent, boolean grouped) {
            LinearLayout card;
            if (grouped) {
                View divider = new View(this);
                divider.setBackgroundColor(CARD_BORDER);
                LinearLayout.LayoutParams dividerLayout =
                        new LinearLayout.LayoutParams(-1, dp(1));
                dividerLayout.setMargins(0, dp(8), 0, dp(8));
                parent.addView(divider, dividerLayout);
                card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.START);
                card.setPadding(0, 0, 0, dp(2));
            } else {
                card = cardColumn();
            }
            LinearLayout header = actionRow();
            TextView label = text(
                    program.optInt("number") + ". " + program.optString("name") +
                            "\n" + program.optString("summary"), 15, true);
            header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            boolean enabled = program.optBoolean("enabled");
            Button toggle = compactButton(
                    getString(enabled ? R.string.turn_off : R.string.turn_on),
                    enabled ? RED : GREEN);
            toggle.setOnClickListener(v -> put(
                    "/programs/" + program.optString("id"),
                    jsonBoolean("enabled", !enabled),
                    () -> load("/programs", "programs")));
            header.addView(toggle);
            Button run = button(getString(R.string.run), GREEN);
            run.setOnClickListener(v -> confirmAction(
                    getString(
                            R.string.confirm_run, program.optString("name")),
                    "/programs/" + program.optString("id") + "/actions/run"));
            header.addView(run);
            card.addView(header);
            LinearLayout details = programDetails(program);
            details.setVisibility(View.GONE);
            Button expand = compactButton(getString(R.string.expand), NAVY);
            expand.setOnClickListener(v -> {
                boolean show = details.getVisibility() != View.VISIBLE;
                details.setVisibility(show ? View.VISIBLE : View.GONE);
                expand.setText(getString(
                        show ? R.string.collapse : R.string.expand));
            });
            LinearLayout actions = actionRow();
            actions.addView(expand);
            Button edit = compactButton(getString(R.string.edit), NAVY);
            edit.setOnClickListener(v -> loadProgramEditor(program));
            actions.addView(edit);
            Button delete = compactButton(getString(R.string.delete_program), RED);
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_program)
                    .setMessage(getString(
                            R.string.confirm_delete_program,
                            program.optString("name")))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            api.request(
                                    "DELETE",
                                    "/programs/" + program.optString("id"),
                                    null,
                                    new ApiClient.Callback() {
                                        @Override public void success(JSONObject response) {
                                            load("/programs", "programs");
                                        }

                                        @Override public void failure(String error) {
                                            message(
                                                    getString(R.string.delete_program_failed),
                                                    localizedError(error));
                                        }
                                    }))
                    .show());
            card.addView(actions);
            card.addView(delete);
            card.addView(details);
            if (grouped) {
                parent.addView(card, new LinearLayout.LayoutParams(-1, -2));
            } else {
                parent.addView(card);
            }
    }

    private void loadTimeline(String path) {
        if (overviewTimelineRequestInFlight) {
            if (path.equals(overviewTimelineActivePath)) {
                overviewTimelineRefreshPending = true;
                overviewTimelinePendingPath = path;
                return;
            }
            // The user selected another day, or the OSPy local date changed.
            // Invalidate the older request and start the newly selected path.
            activeOverviewTimelineRequest = ++overviewTimelineRequestSequence;
            overviewTimelineRequestInFlight = false;
            overviewTimelineRefreshPending = false;
            overviewTimelineActivePath = "";
            overviewTimelinePendingPath = "";
        }

        overviewTimelineRequestInFlight = true;
        overviewTimelineRefreshPending = false;
        overviewTimelineActivePath = path;
        overviewTimelinePendingPath = "";
        final int generation = loadGeneration;
        final int requestNumber = ++overviewTimelineRequestSequence;
        activeOverviewTimelineRequest = requestNumber;
        api.request("GET", path, null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (requestNumber != activeOverviewTimelineRequest) return;
                if (generation != loadGeneration ||
                        !"overview".equals(currentRenderer) ||
                        overviewRoot == null ||
                        overviewRoot.getParent() != content ||
                        !path.equals(schedulePathForSelectedDay())) {
                    overviewTimelineRequestInFlight = false;
                    overviewTimelineRefreshPending = false;
                    overviewTimelineActivePath = "";
                    overviewTimelinePendingPath = "";
                    return;
                }
                Object timelineData = response.opt("data");
                JSONObject schedule = timelineData instanceof JSONObject
                        ? (JSONObject) timelineData : null;
                JSONArray items = schedule != null
                        ? schedule.optJSONArray("items")
                        : timelineData instanceof JSONArray
                                ? (JSONArray) timelineData : null;
                String scheduleUpdated = schedule == null
                        ? "" : schedule.optString("updated");
                updateOverviewTimeline(items, scheduleUpdated);
                overviewTimelineError.setVisibility(View.GONE);
                finishOverviewTimelineRequest(generation);
            }

            @Override public void failure(String error) {
                if (requestNumber != activeOverviewTimelineRequest) return;
                if (generation != loadGeneration ||
                        !"overview".equals(currentRenderer) ||
                        overviewTimelineError == null ||
                        !path.equals(schedulePathForSelectedDay())) {
                    overviewTimelineRequestInFlight = false;
                    overviewTimelineRefreshPending = false;
                    overviewTimelineActivePath = "";
                    overviewTimelinePendingPath = "";
                    return;
                }
                // Keep the previous timeline on screen and only show a compact
                // warning. A temporary request failure must not blank the card.
                overviewTimelineError.setText(localizedError(error));
                overviewTimelineError.setVisibility(View.VISIBLE);
                finishOverviewTimelineRequest(generation);
            }
        });
    }

    private void finishOverviewTimelineRequest(int generation) {
        boolean refreshAgain = overviewTimelineRefreshPending;
        String nextPath = overviewTimelinePendingPath;
        overviewTimelineRequestInFlight = false;
        overviewTimelineRefreshPending = false;
        overviewTimelineActivePath = "";
        overviewTimelinePendingPath = "";
        if (refreshAgain && generation == loadGeneration &&
                "overview".equals(currentRenderer) &&
                overviewRoot != null && overviewRoot.getParent() == content) {
            loadTimeline(nextPath.isEmpty()
                    ? schedulePathForSelectedDay() : nextPath);
        }
    }

    private void updateOverviewTimeline(
            JSONArray items, String scheduleUpdated) {
        LocalDateTime scheduleNow = parseScheduleTime(scheduleUpdated);
        Map<String, JSONObject> received = new LinkedHashMap<>();

        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || item.optBoolean("is_master")) continue;
                String key = timelineStableKey(item);
                received.put(key, item);

                if (selectedScheduleDay == ScheduleDay.TODAY &&
                        isRainBlockedTimelineItem(item)) {
                    overviewBlockedTimelineCache.put(key, item);
                } else {
                    // A live API item is authoritative. If it changed to
                    // running/completed or is no longer rain-blocked, discard
                    // the older cached blocked representation.
                    overviewBlockedTimelineCache.remove(key);
                }
            }
        }

        if (selectedScheduleDay == ScheduleDay.TODAY) {
            mergeCurrentBlockedTimelineRows(received, scheduleNow);
        }

        List<JSONObject> normalizedItems = new ArrayList<>(received.values());
        normalizedItems.sort((left, right) ->
                left.optString("start").compareTo(right.optString("start")));

        List<JSONObject> completed = new ArrayList<>();
        List<JSONObject> currentAndNext = new ArrayList<>();
        for (JSONObject item : normalizedItems) {
            String itemState = item.optString("state");
            if ("completed".equals(itemState)) completed.add(item);
            else currentAndNext.add(item);
        }

        List<JSONObject> visible = new ArrayList<>();
        if (selectedScheduleDay == ScheduleDay.TODAY) {
            // Keep Home compact for the live current day: the last two
            // completed runs, all running/blocked rows and the nearest future
            // rows. Cached blocked rows cover the API gap after their start.
            int completedStart = Math.max(0, completed.size() - 2);
            for (int i = completedStart; i < completed.size(); i++) {
                visible.add(completed.get(i));
            }
            int upcomingLimit = Math.min(currentAndNext.size(), 5);
            for (int i = 0; i < upcomingLimit; i++) {
                visible.add(currentAndNext.get(i));
            }
        } else {
            // A deliberately selected past or future day is a day view, so do
            // not hide most of its entries behind the compact Home limits.
            visible.addAll(normalizedItems);
        }

        updateOverviewTimelineHeader();

        StringBuilder structure = new StringBuilder();
        if (visible.isEmpty()) {
            structure.append("empty");
        } else {
            for (JSONObject item : visible) {
                structure.append(timelineStableKey(item))
                        .append('|')
                        .append(item.optString("state"))
                        .append(';');
            }
        }
        String newStructure = structure.toString();
        if (!newStructure.equals(overviewTimelineStructure)) {
            overviewTimelineStructure = newStructure;
            overviewTimelineRowsContainer.removeAllViews();
            overviewTimelineRows.clear();
            if (visible.isEmpty()) {
                overviewTimelineRowsContainer.addView(text(
                        getString(selectedScheduleDay == ScheduleDay.YESTERDAY
                                ? R.string.no_station_history
                                : R.string.no_scheduled_runs),
                        14, false));
            } else {
                for (JSONObject item : visible) {
                    overviewTimelineRows.add(createTimelineRow(item));
                }
            }
        }

        if (!visible.isEmpty()) {
            for (int i = 0;
                    i < visible.size() && i < overviewTimelineRows.size(); i++) {
                updateTimelineRow(overviewTimelineRows.get(i), visible.get(i));
            }
        }
    }

    private void mergeCurrentBlockedTimelineRows(
            Map<String, JSONObject> received, LocalDateTime scheduleNow) {
        if (!overviewRainBlockActive) {
            overviewBlockedTimelineCache.clear();
            return;
        }

        List<String> remove = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry :
                overviewBlockedTimelineCache.entrySet()) {
            String key = entry.getKey();
            if (received.containsKey(key)) continue;

            JSONObject cached = entry.getValue();
            LocalDateTime start = parseScheduleTime(
                    cached.optString("start"));
            LocalDateTime end = parseScheduleTime(cached.optString("end"));
            boolean selectedDate = start.toLocalDate()
                    .equals(selectedScheduleDate());

            if (selectedDate && !scheduleNow.isBefore(start) &&
                    scheduleNow.isBefore(end)) {
                // The server no longer returns this interval because its start
                // is in the past. It is still within its planned runtime and
                // rain delay is active, therefore keep it visibly blocked.
                received.put(key, cached);
            } else {
                // Missing future entries were removed/rescheduled, and expired
                // entries no longer belong in the current timeline.
                remove.add(key);
            }
        }
        for (String key : remove) {
            overviewBlockedTimelineCache.remove(key);
        }
    }

    private boolean isRainBlockedTimelineItem(JSONObject item) {
        if (!"blocked".equals(item.optString("state"))) return false;
        String reason = item.optString("blocked_reason", "")
                .trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return reason.isEmpty() || reason.startsWith("rain_") ||
                "rain".equals(reason);
    }

    private LocalDateTime parseScheduleTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String normalized = value.trim();
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception ignored) {
            try {
                return java.time.OffsetDateTime.parse(normalized)
                        .toLocalDateTime();
            } catch (Exception ignoredOffset) {
                return LocalDateTime.now();
            }
        }
    }

    private String timelineStableKey(JSONObject item) {
        return item.optString("start") + "|" +
                item.optString("end") + "|" +
                item.optInt("station_number", -1) + "|" +
                item.optString("station_name");
    }

    private TimelineRowBinding createTimelineRow(JSONObject item) {
        String itemState = item.optString("state");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(5), dp(7), dp(5), dp(7));

        LinearLayout header = actionRow();
        TextView title = text("", 14, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = badge(timelineState(itemState), itemState);
        header.addView(status);
        row.addView(header);

        TextView detail = null;
        ProgressBar progress = null;
        if ("running".equals(itemState)) {
            detail = text("", 12, false);
            detail.setTextColor(MUTED);
            row.addView(detail);
            progress = new ProgressBar(
                    this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            row.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        } else if ("blocked".equals(itemState)) {
            detail = text("", 12, false);
            detail.setTextColor(AMBER);
            row.addView(detail);
        }

        overviewTimelineRowsContainer.addView(row);
        return new TimelineRowBinding(
                timelineStableKey(item), itemState, title, status,
                detail, progress);
    }

    private void updateTimelineRow(
            TimelineRowBinding binding, JSONObject item) {
        String interval = shortTime(item.optString("start")) + "\u2013" +
                shortTime(item.optString("end"));
        binding.title.setText(
                interval + "  " + item.optInt("station_number") + ". " +
                        item.optString("station_name"));
        binding.status.setText(timelineState(item.optString("state")));

        if ("running".equals(binding.state) &&
                binding.detail != null && binding.progress != null) {
            int percent = (int) Math.round(
                    100 * item.optDouble("progress", 0));
            binding.detail.setText(
                    getString(R.string.remaining) + ": " +
                            formatDuration(
                                    item.optLong("remaining_seconds", 0)) +
                            " \u00b7 " + percent + " %");
            binding.progress.setProgress(percent);
        } else if ("blocked".equals(binding.state) &&
                binding.detail != null) {
            binding.detail.setText(localizedBlockedReason(
                    item.optString("blocked_reason")));
        }
    }

    private String localizedBlockedReason(String reason) {
        String normalized = reason == null ? "" : reason.trim()
                .toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "":
            case "rain":
            case "rain_delay":
            case "rain_sensor":
            case "rain_sensed":
            case "rain_block":
                return getString(R.string.rain_delay);
            case "disabled_scheduler":
            case "scheduler_disabled":
                return getString(R.string.scheduler_disabled_reason);
            case "calendar_excluded":
                return getString(R.string.calendar_excluded_reason);
            case "public_holiday":
                return getString(R.string.public_holiday_reason);
            case "holiday_calendar_unavailable":
                return getString(R.string.holiday_calendar_unavailable_reason);
            case "service_outage":
                return getString(R.string.service_outage_reason);
            default:
                return reason;
        }
    }


    private LinearLayout programDetails(JSONObject program) {
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(5), dp(5), dp(5), dp(5));
        JSONArray stationDetails = program.optJSONArray("station_details");
        StringBuilder names = new StringBuilder();
        if (stationDetails != null) {
            for (int i = 0; i < stationDetails.length(); i++) {
                JSONObject station = stationDetails.optJSONObject(i);
                if (station == null) continue;
                if (names.length() > 0) names.append(", ");
                names.append(station.optInt("number"))
                        .append(". ")
                        .append(station.optString("name"));
            }
        }
        addPair(
                details, getString(R.string.program_stations),
                names.length() == 0 ? getString(R.string.no_data) : names.toString());
        JSONObject editor = program.optJSONObject("editor");
        if (editor != null) {
            addPair(
                    details, getString(R.string.schedule),
                    readable(editor.optString("type_name")));
            JSONObject fields = editor.optJSONObject("fields");
            if (fields != null) {
                java.util.Iterator<String> keys = fields.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    addPair(
                            details, readable(key),
                            programFieldValue(
                                    editor.optString("kind"), key,
                                    fields.opt(key)));
                }
            }
        }
        return details;
    }

    private void loadProgramEditor(JSONObject program) {
        api.request("GET", "/capabilities", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                JSONArray features = response.optJSONObject("data") == null
                        ? null : response.optJSONObject("data")
                                .optJSONArray("features");
                loadProgramEditorData(
                        program,
                        jsonArrayContains(features, "program_calendar") ||
                                program.has("calendar"),
                        jsonArrayContains(features, "solar_programs") ||
                                program.optInt("type", -1) == 7 ||
                                program.optInt("type", -1) == 8);
            }

            @Override public void failure(String error) {
                loadProgramEditorData(
                        program, program.has("calendar"),
                        program.optInt("type", -1) == 7 ||
                                program.optInt("type", -1) == 8);
            }
        });
    }

    private void loadProgramEditorData(
            JSONObject program, boolean calendarSupported,
            boolean solarSupported) {
        api.request("GET", "/stations", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                JSONArray allStations = response.optJSONArray("data");
                api.request(
                        "GET", "/program-groups", null,
                        new ApiClient.Callback() {
                            @Override public void success(JSONObject groupResponse) {
                                JSONArray allGroups = groupResponse.optJSONArray("data");
                                showProgramEditor(
                                        program,
                                        allStations == null
                                                ? new JSONArray() : allStations,
                                        allGroups == null
                                                ? new JSONArray() : allGroups,
                                        true, calendarSupported,
                                        solarSupported);
                            }

                            @Override public void failure(String error) {
                                // Older OSPy versions do not expose program groups.
                                // Editing the program must still work without them.
                                showProgramEditor(
                                        program,
                                        allStations == null
                                                ? new JSONArray() : allStations,
                                        new JSONArray(),
                                        false, calendarSupported,
                                        solarSupported);
                            }
                        });
            }

            @Override public void failure(String error) {
                message(getString(R.string.app_name), localizedError(error));
            }
        });
    }

    private void loadNewProgramEditor() {
        api.request("GET", "/capabilities", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                JSONObject data = response.optJSONObject("data");
                showNewProgramTypeDialog(data != null && jsonArrayContains(
                        data.optJSONArray("features"), "solar_programs"));
            }

            @Override public void failure(String error) {
                showNewProgramTypeDialog(false);
            }
        });
    }

    private void showNewProgramTypeDialog(boolean solarSupported) {
        String[] types = new String[solarSupported ? 9 : 7];
        for (int type = 0; type < types.length; type++) {
            types[type] = programTypeName(type);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_program_type)
                .setItems(types, (dialog, type) -> {
                    try {
                        loadProgramEditor(newProgramDraft(type));
                    } catch (Exception error) {
                        message(
                                getString(R.string.program_save_failed),
                                getString(R.string.invalid_program_data));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private JSONObject newProgramDraft(int type) throws Exception {
        JSONArray days = new JSONArray();
        for (int day = 0; day < 7; day++) days.put(day);
        String today = LocalDate.now().toString();
        JSONArray interval = new JSONArray().put(360).put(370);
        JSONArray intervals = new JSONArray().put(interval);
        JSONArray typeData;
        switch (type) {
            case 0:
                typeData = new JSONArray().put(360).put(10).put(0).put(0).put(days);
                break;
            case 1:
                typeData = new JSONArray().put(intervals).put(days);
                break;
            case 2:
                typeData = new JSONArray().put(360).put(10).put(0).put(0)
                        .put(1).put(today);
                break;
            case 3:
                typeData = new JSONArray().put(intervals).put(1).put(today);
                break;
            case 4:
                typeData = new JSONArray().put(intervals);
                break;
            case 5:
                typeData = new JSONArray().put(intervals);
                break;
            case 6:
                typeData = new JSONArray().put(5).put(10).put(5).put(0.0)
                        .put(new JSONArray().put(new JSONArray().put(360).put(1)));
                break;
            case 7:
            case 8:
                typeData = new JSONArray().put(10).put(0).put(0).put(days);
                break;
            default:
                throw new IllegalArgumentException();
        }
        JSONObject draft = new JSONObject()
                .put("id", "")
                .put("name", "")
                .put("enabled", false)
                .put("stations", new JSONArray())
                .put("type", type)
                .put("type_data", typeData)
                .put("group_id", "default");
        if (type == 5) {
            draft.put("start", today + "T00:00:00")
                    .put("modulo", 1440)
                    .put("manual", false)
                    .put("schedule", intervals);
        }
        return draft;
    }

    private String programTypeName(int type) {
        switch (type) {
            case 0: return getString(R.string.program_type_days_simple);
            case 1: return getString(R.string.program_type_days_advanced);
            case 2: return getString(R.string.program_type_repeat_simple);
            case 3: return getString(R.string.program_type_repeat_advanced);
            case 4: return getString(R.string.program_type_weekly_advanced);
            case 5: return getString(R.string.program_type_custom);
            case 6: return getString(R.string.program_type_weekly_weather);
            case 7: return getString(R.string.program_type_sunrise);
            case 8: return getString(R.string.program_type_sunset);
            default: return getString(R.string.unknown_status);
        }
    }

    private List<CheckBox> addProgramDays(
            LinearLayout form, JSONArray selectedDays) {
        List<CheckBox> result = new ArrayList<>();
        GridLayout days = new GridLayout(this);
        days.setColumnCount(4);
        days.setRowCount(2);
        for (int day = 0; day < 7; day++) {
            CheckBox check = new CheckBox(this);
            check.setTextColor(TEXT);
            check.setText(weekdayName(day));
            check.setTag(day);
            check.setChecked(jsonArrayContains(selectedDays, day));
            result.add(check);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(day % 4, 1, 1f);
            params.rowSpec = GridLayout.spec(day / 4);
            days.addView(check, params);
        }
        form.addView(text(getString(R.string.days), 14, true));
        form.addView(days);
        return result;
    }

    private static final int SCHEDULE_DAILY = 0;
    private static final int SCHEDULE_WEEKLY = 1;
    private static final int SCHEDULE_CUSTOM = 2;
    private static final int SCHEDULE_PRIORITY = 3;

    private final class ScheduleEditor {
        final int mode;
        final LinearLayout view;
        final LinearLayout rowsView;
        final List<ScheduleRow> rows = new ArrayList<>();

        ScheduleEditor(int mode, JSONArray values) {
            this.mode = mode;
            view = new LinearLayout(MainActivity.this);
            view.setOrientation(LinearLayout.VERTICAL);
            rowsView = new LinearLayout(MainActivity.this);
            rowsView.setOrientation(LinearLayout.VERTICAL);
            view.addView(text(getString(mode == SCHEDULE_PRIORITY
                    ? R.string.priority_times : R.string.program_intervals), 13, true));
            view.addView(rowsView);
            if (values != null) {
                for (int index = 0; index < values.length(); index++) {
                    JSONArray pair = values.optJSONArray(index);
                    if (pair != null && pair.length() == 2) {
                        add(pair.optInt(0, 360), pair.optInt(1,
                                mode == SCHEDULE_PRIORITY ? 1 : 370));
                    }
                }
            }
            if (rows.isEmpty()) add(360, mode == SCHEDULE_PRIORITY ? 1 : 370);
            Button add = compactButton(getString(mode == SCHEDULE_PRIORITY
                    ? R.string.add_priority_time : R.string.add_interval), GREEN);
            add.setOnClickListener(v -> add(
                    mode == SCHEDULE_WEEKLY || mode == SCHEDULE_PRIORITY
                            ? 360 : rows.size() * 60 + 360,
                    mode == SCHEDULE_PRIORITY ? 1 : rows.size() * 60 + 370));
            view.addView(add);
        }

        void add(int first, int second) {
            ScheduleRow row = new ScheduleRow(this, first, second);
            rows.add(row);
            rowsView.addView(row.view);
        }

        void remove(ScheduleRow row) {
            if (rows.size() == 1) {
                toast(getString(R.string.program_intervals_required));
                return;
            }
            rows.remove(row);
            rowsView.removeView(row.view);
        }

        JSONArray value() {
            JSONArray result = new JSONArray();
            for (ScheduleRow row : rows) result.put(row.value());
            return result;
        }
    }

    private final class ScheduleRow {
        final ScheduleEditor owner;
        final LinearLayout view;
        final Button firstTime;
        final Button secondTime;
        final Button firstDay;
        final Button secondDay;
        final EditText firstCycleDay;
        final EditText secondCycleDay;
        final EditText priority;
        int firstMinute;
        int secondMinute;

        ScheduleRow(ScheduleEditor owner, int first, int second) {
            this.owner = owner;
            firstMinute = Math.max(0, first);
            secondMinute = Math.max(0, second);
            view = cardColumn();
            firstTime = compactButton("", NAVY);
            secondTime = owner.mode == SCHEDULE_PRIORITY
                    ? null : compactButton("", NAVY);
            firstDay = owner.mode == SCHEDULE_WEEKLY || owner.mode == SCHEDULE_PRIORITY
                    ? compactButton("", NAVY) : null;
            secondDay = owner.mode == SCHEDULE_WEEKLY
                    ? compactButton("", NAVY) : null;
            firstCycleDay = owner.mode == SCHEDULE_CUSTOM
                    ? numericInput(String.valueOf(firstMinute / 1440 + 1)) : null;
            secondCycleDay = owner.mode == SCHEDULE_CUSTOM
                    ? numericInput(String.valueOf(secondMinute / 1440 + 1)) : null;
            priority = owner.mode == SCHEDULE_PRIORITY
                    ? numericInput(String.valueOf(secondMinute)) : null;

            if (owner.mode == SCHEDULE_PRIORITY) {
                view.addView(text(getString(R.string.priority_time), 13, true));
                view.addView(scheduleEndpoint(true));
                view.addView(labelledInput(getString(R.string.priority), priority));
            } else {
                view.addView(text(getString(R.string.interval_start), 13, true));
                view.addView(scheduleEndpoint(true));
                view.addView(text(getString(R.string.interval_end), 13, true));
                view.addView(scheduleEndpoint(false));
            }
            Button remove = compactButton(getString(R.string.remove), RED);
            remove.setOnClickListener(v -> owner.remove(this));
            view.addView(remove);
            updateLabels();
        }

        private LinearLayout scheduleEndpoint(boolean first) {
            LinearLayout row = actionRow();
            Button day = first ? firstDay : secondDay;
            EditText cycleDay = first ? firstCycleDay : secondCycleDay;
            Button time = first ? firstTime : secondTime;
            if (day != null) {
                day.setOnClickListener(v -> pickWeekday(first));
                row.addView(day, new LinearLayout.LayoutParams(0, -2, 0.45f));
            } else if (cycleDay != null) {
                row.addView(labelledInput(getString(R.string.cycle_day), cycleDay),
                        new LinearLayout.LayoutParams(0, -2, 0.45f));
            }
            time.setOnClickListener(v -> pickTime(first));
            row.addView(time, new LinearLayout.LayoutParams(0, -2, 0.55f));
            return row;
        }

        private void pickWeekday(boolean first) {
            String[] names = new String[7];
            for (int day = 0; day < 7; day++) names[day] = weekdayName(day);
            int minute = first ? firstMinute : secondMinute;
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.select_day)
                    .setSingleChoiceItems(names, (minute / 1440) % 7,
                            (dialog, selected) -> {
                                int changed = selected * 1440 + minute % 1440;
                                if (first) firstMinute = changed;
                                else secondMinute = changed;
                                dialog.dismiss();
                                updateLabels();
                            })
                    .show();
        }

        private void pickTime(boolean first) {
            int minute = first ? firstMinute : secondMinute;
            new TimePickerDialog(
                    MainActivity.this,
                    (picker, hour, selectedMinute) -> {
                        int changed = (minute / 1440) * 1440 + hour * 60 + selectedMinute;
                        if (first) firstMinute = changed;
                        else secondMinute = changed;
                        updateLabels();
                    },
                    (minute % 1440) / 60,
                    minute % 60,
                    true).show();
        }

        private void updateLabels() {
            firstTime.setText(minutesToTime(firstMinute));
            if (secondTime != null) secondTime.setText(minutesToTime(secondMinute));
            if (firstDay != null) firstDay.setText(weekdayName((firstMinute / 1440) % 7));
            if (secondDay != null) secondDay.setText(weekdayName((secondMinute / 1440) % 7));
        }

        JSONArray value() {
            int first = firstMinute;
            int second = secondMinute;
            if (owner.mode == SCHEDULE_CUSTOM) {
                first = (positiveInteger(firstCycleDay) - 1) * 1440 + firstMinute % 1440;
                second = (positiveInteger(secondCycleDay) - 1) * 1440 + secondMinute % 1440;
            }
            if (owner.mode == SCHEDULE_PRIORITY) {
                second = nonNegativeInteger(priority);
                if (first < 0 || first >= 7 * 1440) {
                    throw new IllegalArgumentException(
                            getString(R.string.program_intervals_format));
                }
            } else if (first < 0 || second <= first) {
                throw new IllegalArgumentException(
                        getString(R.string.program_interval_order));
            }
            return new JSONArray().put(first).put(second);
        }
    }

    private Button dateButton(String value) {
        LocalDate date;
        try { date = LocalDate.parse(value); }
        catch (Exception ignored) { date = LocalDate.now(); }
        Button button = compactButton(date.toString(), NAVY);
        button.setTag(date);
        button.setText(date.format(DateTimeFormatter.ofLocalizedDate(
                FormatStyle.MEDIUM)));
        button.setOnClickListener(v -> {
            LocalDate selected = (LocalDate) button.getTag();
            new DatePickerDialog(this, (picker, year, month, day) -> {
                LocalDate changed = LocalDate.of(year, month + 1, day);
                button.setTag(changed);
                button.setText(changed.format(DateTimeFormatter.ofLocalizedDate(
                        FormatStyle.MEDIUM)));
            }, selected.getYear(), selected.getMonthValue() - 1,
                    selected.getDayOfMonth()).show();
        });
        return button;
    }

    private Button dateTimeButton(String value) {
        LocalDateTime dateTime;
        try { dateTime = LocalDateTime.parse(value); }
        catch (Exception ignored) { dateTime = LocalDate.now().atStartOfDay(); }
        Button button = compactButton("", NAVY);
        button.setTag(dateTime);
        updateDateTimeButton(button);
        button.setOnClickListener(v -> {
            LocalDateTime selected = (LocalDateTime) button.getTag();
            new DatePickerDialog(this, (picker, year, month, day) ->
                    new TimePickerDialog(this, (timePicker, hour, minute) -> {
                        LocalDateTime changed = LocalDateTime.of(
                                year, month + 1, day, hour, minute);
                        button.setTag(changed);
                        updateDateTimeButton(button);
                    }, selected.getHour(), selected.getMinute(), true).show(),
                    selected.getYear(), selected.getMonthValue() - 1,
                    selected.getDayOfMonth()).show();
        });
        return button;
    }

    private void updateDateTimeButton(Button button) {
        LocalDateTime value = (LocalDateTime) button.getTag();
        button.setText(value.format(DateTimeFormatter.ofLocalizedDateTime(
                FormatStyle.MEDIUM, FormatStyle.SHORT)));
    }

    private JSONArray selectedProgramDays(List<CheckBox> checks) {
        JSONArray selected = new JSONArray();
        for (CheckBox check : checks) {
            if (check.isChecked()) selected.put(check.getTag());
        }
        if (selected.length() == 0) {
            throw new IllegalArgumentException(
                    getString(R.string.program_day_required));
        }
        return selected;
    }

    private String programDate(EditText input) {
        String value = input.getText().toString().trim();
        try {
            LocalDate.parse(value);
            return value;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    getString(R.string.program_date_format));
        }
    }

    private String programDateTime(EditText input) {
        String value = input.getText().toString().trim();
        try {
            LocalDateTime.parse(value);
            return value;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    getString(R.string.program_date_time_format));
        }
    }

    private int programTime(EditText input) {
        try {
            return timeToMinutes(input.getText().toString());
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    getString(R.string.program_time_format));
        }
    }

    private void showProgramEditor(
            JSONObject program, JSONArray allStations, JSONArray allGroups,
            boolean groupsSupported, boolean calendarSupported,
            boolean solarSupported) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), dp(4));
        int type = program.optInt("type", -1);
        form.addView(text(
                getString(R.string.program_type) + ": " + programTypeName(type),
                14, true));
        EditText name = input(getString(R.string.program), false);
        name.setText(program.optString("name"));
        form.addView(name);
        List<String> groupIds = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();
        int selectedGroup = 0;
        String currentGroup = program.optString("group_id", "default");
        for (int index = 0; index < allGroups.length(); index++) {
            JSONObject group = allGroups.optJSONObject(index);
            if (group == null) continue;
            groupIds.add(group.optString("id", "default"));
            groupNames.add(group.optString("name", group.optString("id")));
            if (currentGroup.equals(group.optString("id"))) {
                selectedGroup = groupIds.size() - 1;
            }
        }
        if (groupIds.isEmpty()) {
            groupIds.add("default");
            groupNames.add(getString(R.string.default_program_group));
        }
        Spinner groupSpinner = new Spinner(this);
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, groupNames);
        groupAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(groupAdapter);
        groupSpinner.setSelection(selectedGroup);
        form.addView(labelledView(getString(R.string.program_group), groupSpinner));
        CheckBox programEnabled = new CheckBox(this);
        programEnabled.setText(getString(R.string.program_enabled));
        programEnabled.setTextColor(TEXT);
        programEnabled.setChecked(program.optBoolean("enabled", true));
        form.addView(programEnabled);

        TextView stationHeading = text(
                getString(R.string.program_stations), 15, true);
        form.addView(stationHeading);
        List<CheckBox> stationChecks = new ArrayList<>();
        JSONArray selected = program.optJSONArray("stations");
        for (int i = 0; i < allStations.length(); i++) {
            JSONObject station = allStations.optJSONObject(i);
            if (station == null || station.optBoolean("is_master") ||
                    station.optBoolean("is_master_two")) continue;
            CheckBox check = new CheckBox(this);
            check.setTextColor(TEXT);
            check.setText(
                    station.optInt("number") + ". " +
                            station.optString("name"));
            check.setTag(station.optInt("legacy_index"));
            check.setChecked(jsonArrayContains(
                    selected, station.optInt("legacy_index")));
            stationChecks.add(check);
            form.addView(check);
        }

        JSONArray originalTypeData = program.optJSONArray("type_data");
        if (originalTypeData == null) originalTypeData = new JSONArray();
        JSONObject originalCalendar = program.optJSONObject("calendar");
        if (originalCalendar == null) originalCalendar = new JSONObject();
        EditText start = null;
        EditText duration = null;
        EditText pause = null;
        EditText repeats = null;
        EditText repeatDays = null;
        Button startDate = null;
        ScheduleEditor intervals = null;
        EditText modulo = null;
        Button customStart = null;
        CheckBox manual = null;
        EditText irrigationMin = null;
        EditText irrigationMax = null;
        EditText runMax = null;
        EditText sunOffset = null;
        EditText sunEarliest = null;
        EditText sunLatest = null;
        Spinner sunPolicy = null;
        List<CheckBox> dayChecks = new ArrayList<>();
        if (type == 0 || type == 2) {
            start = input(getString(R.string.start_time), false);
            start.setInputType(InputType.TYPE_CLASS_DATETIME);
            start.setText(minutesToTime(originalTypeData.optInt(0, 360)));
            form.addView(labelledInput(getString(R.string.start_time), start));
            duration = numericInput(String.valueOf(
                    originalTypeData.optInt(1, 10)));
            form.addView(labelledInput(getString(R.string.duration), duration));
            pause = numericInput(String.valueOf(
                    originalTypeData.optInt(2, 0)));
            form.addView(labelledInput(getString(R.string.pause), pause));
            repeats = numericInput(String.valueOf(
                    originalTypeData.optInt(3, 0)));
            form.addView(labelledInput(getString(R.string.repeat_count), repeats));
            if (type == 0) {
                dayChecks = addProgramDays(form, originalTypeData.optJSONArray(4));
            } else {
                repeatDays = numericInput(String.valueOf(
                        originalTypeData.optInt(4, 1)));
                form.addView(labelledInput(
                        getString(R.string.repeat_days), repeatDays));
                startDate = dateButton(originalTypeData.optString(
                        5, LocalDate.now().toString()));
                form.addView(labelledView(
                        getString(R.string.start_date), startDate));
            }
        } else if (type == 1) {
            intervals = new ScheduleEditor(
                    SCHEDULE_DAILY, originalTypeData.optJSONArray(0));
            form.addView(intervals.view);
            dayChecks = addProgramDays(form, originalTypeData.optJSONArray(1));
        } else if (type == 3) {
            intervals = new ScheduleEditor(
                    SCHEDULE_DAILY, originalTypeData.optJSONArray(0));
            form.addView(intervals.view);
            repeatDays = numericInput(String.valueOf(originalTypeData.optInt(1, 1)));
            form.addView(labelledInput(getString(R.string.repeat_days), repeatDays));
            startDate = dateButton(originalTypeData.optString(
                    2, LocalDate.now().toString()));
            form.addView(labelledView(getString(R.string.start_date), startDate));
        } else if (type == 4) {
            intervals = new ScheduleEditor(
                    SCHEDULE_WEEKLY, originalTypeData.optJSONArray(0));
            form.addView(intervals.view);
        } else if (type == 5) {
            JSONArray schedule = program.optJSONArray("schedule");
            intervals = new ScheduleEditor(
                    SCHEDULE_CUSTOM,
                    schedule == null ? originalTypeData.optJSONArray(0) : schedule);
            form.addView(intervals.view);
            modulo = numericInput(String.valueOf(program.optInt("modulo", 1440)));
            form.addView(labelledInput(getString(R.string.program_modulo), modulo));
            customStart = dateTimeButton(program.optString(
                    "start", LocalDate.now() + "T00:00:00"));
            form.addView(labelledView(
                    getString(R.string.start_date_time), customStart));
            manual = new CheckBox(this);
            manual.setText(R.string.run_once_schedule);
            manual.setTextColor(TEXT);
            manual.setChecked(program.optBoolean("manual"));
            form.addView(manual);
        } else if (type == 6) {
            irrigationMin = numericInput(String.valueOf(originalTypeData.optInt(0, 5)));
            irrigationMax = numericInput(String.valueOf(originalTypeData.optInt(1, 10)));
            runMax = numericInput(String.valueOf(originalTypeData.optInt(2, 5)));
            pause = numericInput(String.valueOf((int) Math.round(
                    originalTypeData.optDouble(3, 0.0) * 100.0)));
            intervals = new ScheduleEditor(
                    SCHEDULE_PRIORITY, originalTypeData.optJSONArray(4));
            form.addView(labelledInput(getString(R.string.irrigation_minimum), irrigationMin));
            form.addView(labelledInput(getString(R.string.irrigation_maximum), irrigationMax));
            form.addView(labelledInput(getString(R.string.maximum_run), runMax));
            form.addView(labelledInput(getString(R.string.pause_ratio_percent), pause));
            form.addView(intervals.view);
        } else if (type == 7 || type == 8) {
            duration = numericInput(String.valueOf(originalTypeData.optInt(0, 10)));
            pause = numericInput(String.valueOf(originalTypeData.optInt(1, 0)));
            repeats = numericInput(String.valueOf(originalTypeData.optInt(2, 0)));
            form.addView(labelledInput(getString(R.string.duration), duration));
            form.addView(labelledInput(getString(R.string.pause), pause));
            form.addView(labelledInput(getString(R.string.repeat_count), repeats));
            dayChecks = addProgramDays(form, originalTypeData.optJSONArray(3));
            sunOffset = numericInput(String.valueOf(
                    originalCalendar.optInt("sun_offset_minutes", 0)));
            sunOffset.setInputType(InputType.TYPE_CLASS_NUMBER |
                    InputType.TYPE_NUMBER_FLAG_SIGNED);
            sunEarliest = input(getString(R.string.program_time_format), false);
            sunEarliest.setText(minutesToTime(originalCalendar.optInt(
                    "sun_earliest_minute", 0)));
            sunLatest = input(getString(R.string.program_time_format), false);
            sunLatest.setText(minutesToTime(originalCalendar.optInt(
                    "sun_latest_minute", 1439)));
            String[] policies = new String[] {
                    getString(R.string.sun_window_clamp),
                    getString(R.string.sun_window_skip)};
            sunPolicy = new Spinner(this);
            ArrayAdapter<String> policyAdapter = new ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, policies);
            policyAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            sunPolicy.setAdapter(policyAdapter);
            sunPolicy.setSelection("skip".equals(originalCalendar.optString(
                    "sun_window_policy", "clamp")) ? 1 : 0);
            form.addView(labelledInput(getString(R.string.sun_time_offset), sunOffset));
            form.addView(labelledInput(getString(R.string.earliest_start_time), sunEarliest));
            form.addView(labelledInput(getString(R.string.latest_start_time), sunLatest));
            form.addView(labelledView(getString(R.string.outside_allowed_time), sunPolicy));
        } else {
            form.addView(text(getString(R.string.unsupported_program_type), 14, true));
        }

        if (calendarSupported) {
            form.addView(text(getString(R.string.calendar_rules), 15, true));
        }
        JSONArray allowedMonths = originalCalendar.optJSONArray("allowed_months");
        List<CheckBox> monthChecks = new ArrayList<>();
        GridLayout monthGrid = new GridLayout(this);
        monthGrid.setColumnCount(4);
        for (int month = 1; month <= 12; month++) {
            CheckBox check = new CheckBox(this);
            check.setTextColor(TEXT);
            check.setText(String.valueOf(month));
            check.setTag(month);
            check.setChecked(allowedMonths == null ||
                    jsonArrayContains(allowedMonths, month));
            monthChecks.add(check);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec((month - 1) % 4, 1, 1f);
            params.rowSpec = GridLayout.spec((month - 1) / 4);
            monthGrid.addView(check, params);
        }
        if (calendarSupported) {
            form.addView(labelledView(getString(R.string.allowed_months), monthGrid));
        }

        String[] parityLabels = new String[] {
                getString(R.string.all_days), getString(R.string.odd_days),
                getString(R.string.even_days)};
        Spinner dayParity = new Spinner(this);
        ArrayAdapter<String> parityAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, parityLabels);
        parityAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        dayParity.setAdapter(parityAdapter);
        String originalParity = originalCalendar.optString("day_parity", "all");
        dayParity.setSelection("odd".equals(originalParity) ? 1 :
                ("even".equals(originalParity) ? 2 : 0));
        if (calendarSupported) {
            form.addView(labelledView(
                    getString(R.string.calendar_day_parity), dayParity));
        }

        EditText monthDays = input(getString(R.string.month_days_hint), false);
        monthDays.setText(joinJsonValues(
                originalCalendar.optJSONArray("month_days"), ", "));
        if (calendarSupported) {
            form.addView(labelledInput(getString(R.string.days_in_month), monthDays));
        }
        CheckBox excludeHolidays = new CheckBox(this);
        excludeHolidays.setText(R.string.exclude_public_holidays);
        excludeHolidays.setTextColor(TEXT);
        excludeHolidays.setChecked(originalCalendar.optBoolean(
                "exclude_holidays", false));
        if (calendarSupported) form.addView(excludeHolidays);
        EditText excludedDateInput = input(
                getString(R.string.excluded_dates_hint), false);
        excludedDateInput.setSingleLine(false);
        excludedDateInput.setMinLines(2);
        excludedDateInput.setText(joinJsonValues(
                originalCalendar.optJSONArray("excluded_dates"), "\n"));
        if (calendarSupported) {
            form.addView(labelledInput(
                    getString(R.string.excluded_dates), excludedDateInput));
        }
        EditText excludedRangeInput = input(
                getString(R.string.excluded_ranges_hint), false);
        excludedRangeInput.setSingleLine(false);
        excludedRangeInput.setMinLines(2);
        excludedRangeInput.setText(joinExcludedRanges(
                originalCalendar.optJSONArray("excluded_ranges")));
        if (calendarSupported) {
            form.addView(labelledInput(
                    getString(R.string.excluded_ranges), excludedRangeInput));
        }
        scroll.addView(form);

        final EditText startField = start;
        final EditText durationField = duration;
        final EditText pauseField = pause;
        final EditText repeatsField = repeats;
        final EditText repeatDaysField = repeatDays;
        final Button startDateField = startDate;
        final ScheduleEditor intervalsField = intervals;
        final EditText moduloField = modulo;
        final Button customStartField = customStart;
        final CheckBox manualField = manual;
        final EditText irrigationMinField = irrigationMin;
        final EditText irrigationMaxField = irrigationMax;
        final EditText runMaxField = runMax;
        final EditText sunOffsetField = sunOffset;
        final EditText sunEarliestField = sunEarliest;
        final EditText sunLatestField = sunLatest;
        final Spinner sunPolicyField = sunPolicy;
        final List<CheckBox> dayCheckFields = dayChecks;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(program.optString("id").isEmpty()
                        ? getString(R.string.new_program)
                        : program.optString("name"))
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            try {
                                JSONArray stations = new JSONArray();
                                for (CheckBox check : stationChecks) {
                                    if (check.isChecked()) stations.put(check.getTag());
                                }
                                boolean creating = program.optString("id").isEmpty();
                                if (name.getText().toString().trim().isEmpty()) {
                                    toast(getString(R.string.program_name_required));
                                    return;
                                }
                                if (creating && stations.length() == 0) {
                                    toast(getString(R.string.program_station_required));
                                    return;
                                }
                                JSONArray typeData = new JSONArray();
                                if (type == 0 || type == 2) {
                                    typeData.put(programTime(startField))
                                            .put(positiveInteger(durationField))
                                            .put(nonNegativeInteger(pauseField))
                                            .put(nonNegativeInteger(repeatsField));
                                    if (type == 0) typeData.put(selectedProgramDays(dayCheckFields));
                                    else typeData.put(positiveInteger(repeatDaysField))
                                            .put(((LocalDate) startDateField.getTag()).toString());
                                } else if (type == 1) {
                                    typeData.put(intervalsField.value())
                                            .put(selectedProgramDays(dayCheckFields));
                                } else if (type == 3) {
                                    typeData.put(intervalsField.value())
                                            .put(positiveInteger(repeatDaysField))
                                            .put(((LocalDate) startDateField.getTag()).toString());
                                } else if (type == 4) {
                                    typeData.put(intervalsField.value());
                                } else if (type == 5) {
                                    typeData.put(intervalsField.value());
                                } else if (type == 6) {
                                    int pausePercent = nonNegativeInteger(pauseField);
                                    if (pausePercent > 100) {
                                        throw new IllegalArgumentException(
                                                getString(R.string.program_weather_values_invalid));
                                    }
                                    typeData.put(nonNegativeInteger(irrigationMinField))
                                            .put(positiveInteger(irrigationMaxField))
                                            .put(positiveInteger(runMaxField))
                                            .put(pausePercent / 100.0)
                                            .put(intervalsField.value());
                                } else if (type == 7 || type == 8) {
                                    typeData.put(positiveInteger(durationField))
                                            .put(nonNegativeInteger(pauseField))
                                            .put(nonNegativeInteger(repeatsField))
                                            .put(selectedProgramDays(dayCheckFields));
                                } else {
                                    throw new IllegalArgumentException(
                                            getString(R.string.unsupported_program_type));
                                }
                                JSONObject payload = new JSONObject();
                                payload.put("name", name.getText().toString().trim());
                                payload.put("stations", stations);
                                payload.put("enabled", programEnabled.isChecked());
                                payload.put("type", type);
                                payload.put("type_data", typeData);
                                if (calendarSupported) {
                                    JSONArray selectedMonths = new JSONArray();
                                    for (CheckBox check : monthChecks) {
                                        if (check.isChecked()) {
                                            selectedMonths.put(check.getTag());
                                        }
                                    }
                                    if (selectedMonths.length() == 0) {
                                        throw new IllegalArgumentException(getString(
                                                R.string.program_month_required));
                                    }
                                    String[] parityValues = {"all", "odd", "even"};
                                    JSONObject calendar = new JSONObject()
                                            .put("allowed_months", selectedMonths)
                                            .put("day_parity", parityValues[
                                                    dayParity.getSelectedItemPosition()])
                                            .put("month_days", integerList(
                                                    monthDays, 1, 31))
                                            .put("exclude_holidays",
                                                    excludeHolidays.isChecked())
                                            .put("excluded_dates", excludedDates(
                                                    excludedDateInput))
                                            .put("excluded_ranges", excludedRanges(
                                                    excludedRangeInput));
                                    if (type == 7 || type == 8) {
                                        int earliest = programTime(sunEarliestField);
                                        int latest = programTime(sunLatestField);
                                        if (latest < earliest) {
                                            throw new IllegalArgumentException(getString(
                                                    R.string.program_sun_window_order));
                                        }
                                        calendar.put("sun_offset_minutes", boundedInteger(
                                                        sunOffsetField, -720, 720,
                                                        R.string.program_sun_offset_format))
                                                .put("sun_earliest_minute", earliest)
                                                .put("sun_latest_minute", latest)
                                                .put("sun_window_policy",
                                                        sunPolicyField.getSelectedItemPosition()
                                                                == 1 ? "skip" : "clamp");
                                    }
                                    payload.put("calendar", calendar);
                                }
                                if (groupsSupported) {
                                    payload.put(
                                            "group_id",
                                            groupIds.get(groupSpinner.getSelectedItemPosition()));
                                }
                                if (type == 5) {
                                    String startValue = ((LocalDateTime)
                                            customStartField.getTag()).toString();
                                    payload.put("schedule", typeData.getJSONArray(0));
                                    payload.put("modulo", positiveInteger(moduloField));
                                    payload.put("manual", manualField.isChecked());
                                    payload.put("start", startValue);
                                }
                                String method = creating ? "POST" : "PUT";
                                String path = creating ? "/programs" :
                                        "/programs/" + program.optString("id");
                                api.request(method, path, payload, new ApiClient.Callback() {
                                    @Override public void success(JSONObject response) {
                                        dialog.dismiss();
                                        load("/programs", "programs");
                                    }

                                    @Override public void failure(String error) {
                                        message(
                                                getString(R.string.program_save_failed),
                                                localizedProgramSaveError(error));
                                    }
                                });
                            } catch (Exception error) {
                                String reason = error.getMessage();
                                message(
                                        getString(R.string.program_save_failed),
                                        reason == null || reason.trim().isEmpty()
                                                ? getString(R.string.invalid_program_data)
                                                : reason);
                            }
                        }));
        dialog.show();
    }

    private void renderStationRun(JSONObject event) {
        LinearLayout card = cardColumn();
        LinearLayout header = actionRow();
        header.addView(text(
                event.optInt("station_number") + ". " +
                        event.optString("station_name"), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(badge(
                timelineState(event.optString("state")),
                event.optString("state")));
        card.addView(header);
        addPair(
                card, getString(R.string.date_time),
                shortDateTime(event.optString("start")) + " – " +
                        shortTime(event.optString("end")));
        addPairIfPresent(
                card, event, "program_name", getString(R.string.program));
        addPair(
                card, getString(R.string.actual_duration),
                formatDuration(event.optLong("duration_seconds", 0)));
        content.addView(card);
    }

    private void loadPluginMobile(
            JSONObject plugin, LinearLayout parent, Button trigger) {
        loadPluginMobileRange(
                plugin, parent, trigger, "today", null, null, false);
    }

    private void loadPluginMobileRange(
            JSONObject plugin, LinearLayout parent, Button trigger,
            String selectedRange, LocalDateTime customFrom,
            LocalDateTime customTo) {
        loadPluginMobileRange(
                plugin, parent, trigger, selectedRange, customFrom,
                customTo, false);
    }

    private void loadPluginMobileRange(
            JSONObject plugin, LinearLayout parent, Button trigger,
            String selectedRange, LocalDateTime customFrom,
            LocalDateTime customTo, boolean backgroundRefresh) {
        if (pluginMobileRequestInFlight) {
            if (backgroundRefresh) return;
            pluginMobileRequestSequence++;
        }
        pluginMobileRequestInFlight = true;
        if (!backgroundRefresh) {
            trigger.setEnabled(false);
            trigger.setText(getString(R.string.loading));
        }
        LinearLayout mobileContent;
        Object existing = trigger.getTag();
        if (!backgroundRefresh && activePluginMobileContent != null &&
                activePluginMobileContent != existing &&
                activePluginMobileContent.getVisibility() == View.VISIBLE) {
            activePluginMobileContent.setVisibility(View.GONE);
            if (activePluginMobileTrigger != null) {
                activePluginMobileTrigger.setText(getString(R.string.expand));
            }
            clearActivePluginMobile();
            pluginMobileRequestInFlight = true;
        }
        if (existing instanceof LinearLayout) {
            mobileContent = (LinearLayout) existing;
            mobileContent.setVisibility(View.VISIBLE);
        } else {
            if (backgroundRefresh) {
                pluginMobileRequestInFlight = false;
                return;
            }
            mobileContent = cardColumn();
            trigger.setTag(mobileContent);
            parent.addView(mobileContent);
        }
        if (!backgroundRefresh) {
            mobileContent.removeAllViews();
            mobileContent.addView(text(getString(R.string.loading), 14, false));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = customFrom;
        LocalDateTime to = customTo;
        if (from == null || to == null) {
            to = now;
            switch (selectedRange) {
                case "hour":
                    from = now.minusHours(1);
                    break;
                case "week":
                    from = now.minusDays(7);
                    break;
                case "month":
                    from = now.minusMonths(1);
                    break;
                case "year":
                    from = now.minusYears(1);
                    break;
                default:
                    from = LocalDate.now().atStartOfDay();
                    selectedRange = "today";
                    break;
            }
        }
        String path = "/plugins/" + plugin.optString("id") + "/mobile" +
                "?from=" + Uri.encode(from.toString()) +
                "&to=" + Uri.encode(to.toString()) +
                "&max_points=400";
        final String activeRange = selectedRange;
        final LocalDateTime activeFrom = from;
        final LocalDateTime activeTo = to;
        final LocalDateTime refreshFrom =
                "custom".equals(activeRange) ? activeFrom : null;
        final LocalDateTime refreshTo =
                "custom".equals(activeRange) ? activeTo : null;
        final int activeGeneration = loadGeneration;
        final int mobileRequest = ++pluginMobileRequestSequence;
        api.request(
                "GET", path,
                null, new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        if (mobileRequest != pluginMobileRequestSequence) return;
                        pluginMobileRequestInFlight = false;
                        if (activeGeneration != loadGeneration ||
                                !"plugins".equals(currentRenderer) ||
                                mobileContent.getParent() == null) return;
                        trigger.setEnabled(true);
                        trigger.setText(getString(R.string.collapse));
                        mobileContent.removeAllViews();
                        trigger.setOnClickListener(v -> {
                            boolean visible =
                                    mobileContent.getVisibility() == View.VISIBLE;
                            if (visible) {
                                mobileContent.setVisibility(View.GONE);
                                trigger.setText(getString(R.string.expand));
                                if (activePluginMobileContent == mobileContent) {
                                    clearActivePluginMobile();
                                }
                            } else {
                                loadPluginMobileRange(
                                        plugin, parent, trigger, activeRange,
                                        refreshFrom, refreshTo, false);
                            }
                        });
                        activePluginMobile = plugin;
                        activePluginMobileParent = parent;
                        activePluginMobileContent = mobileContent;
                        activePluginMobileTrigger = trigger;
                        activePluginMobileRange = activeRange;
                        activePluginMobileFrom = refreshFrom;
                        activePluginMobileTo = refreshTo;
                        JSONObject data = response.optJSONObject("data");
                        if (data == null) {
                            mobileContent.addView(text(
                                    getString(R.string.no_mobile_data), 14, false));
                            return;
                        }
                        JSONObject status = data.optJSONObject("status");
                        if (status != null) {
                            boolean systemUpdatePlugin =
                                    "system_update".equals(plugin.optString("id"));
                            LinearLayout statusCard = statusCardContainer(
                                    status.optString("status", "ok"));
                            statusCard.addView(text(
                                    systemUpdatePlugin
                                            ? getString(R.string.system_update)
                                            : status.optString(
                                                    "title",
                                                    getString(R.string.operating_data)),
                                    15, true));
                            if (systemUpdatePlugin) {
                                addPair(
                                        statusCard, getString(R.string.status),
                                        localizedStatus(status.optString(
                                                "status", "unknown")));
                            } else {
                                addPairIfPresent(
                                        statusCard, status, "summary",
                                        getString(R.string.status));
                            }
                            addPairIfPresent(
                                    statusCard, status, "updated",
                                    getString(R.string.updated));
                            mobileContent.addView(statusCard);
                        }
                        JSONArray cards = data.optJSONArray("cards");
                        if (cards == null || cards.length() == 0) {
                            if (status == null) mobileContent.addView(text(
                                    getString(R.string.no_mobile_data), 14, false));
                            return;
                        }
                        boolean hasSeries = false;
                        for (int i = 0; i < cards.length(); i++) {
                            JSONObject candidate = cards.optJSONObject(i);
                            if (supportsHistory(candidate)) {
                                hasSeries = true;
                                break;
                            }
                        }
                        if (hasSeries) {
                            addHistoryRangeControls(
                                    mobileContent, plugin, parent, trigger,
                                    activeRange, activeFrom, activeTo);
                        }
                        for (int i = 0; i < cards.length(); i++) {
                            JSONObject mobileCard = cards.optJSONObject(i);
                            if (mobileCard == null) continue;
                            LinearLayout card = cardColumn();
                            card.addView(text(
                                    mobileCardTitle(mobileCard),
                                    15, true));
                            JSONArray metrics = mobileCard.optJSONArray("metrics");
                            if (metrics != null) {
                                for (int j = 0; j < metrics.length(); j++) {
                                    JSONObject metric = metrics.optJSONObject(j);
                                    if (metric == null) continue;
                                    String unit = metric.optString("unit");
                                    addPair(
                                            card, mobileMetricLabel(metric),
                                            mobileMetricValue(metric) +
                                                    (unit.isEmpty()
                                                            ? "" : " " + unit));
                                }
                            }
                            JSONObject image = mobileCard.optJSONObject("image");
                            if (image != null) {
                                addMobileImage(card, image);
                            }
                            String kind = mobileCard.optString("kind");
                            if ("gauge_dashboard".equals(kind)) {
                                renderMobileGaugeDashboard(card, mobileCard);
                            } else if ("daylight_timeline".equals(kind)) {
                                JSONObject timeline =
                                        mobileCard.optJSONObject("timeline");
                                if (timeline != null) {
                                    card.addView(new MobileDaylightView(
                                            MainActivity.this, timeline,
                                            appPreferences.darkTheme()));
                                }
                            } else {
                                JSONArray series =
                                        mobileCard.optJSONArray("series");
                                if (hasSeriesPoints(series)) {
                                    card.addView(new MobileChartView(
                                            MainActivity.this, series,
                                            appPreferences.darkTheme()));
                                } else if (supportsHistory(mobileCard)) {
                                    TextView empty = text(
                                            getString(R.string.no_data_period),
                                            13, false);
                                    empty.setTextColor(MUTED);
                                    card.addView(empty);
                                }
                            }
                            JSONObject history =
                                    mobileCard.optJSONObject("history");
                            if (history != null &&
                                    !history.optString("last_available").isEmpty()) {
                                addPair(
                                        card,
                                        getString(R.string.last_available_data),
                                        formatTimestamp(history.optString(
                                                "last_available")));
                            }
                            mobileContent.addView(card);
                        }
                    }

                    @Override public void failure(String error) {
                        if (mobileRequest != pluginMobileRequestSequence) return;
                        pluginMobileRequestInFlight = false;
                        if (activeGeneration != loadGeneration ||
                                !"plugins".equals(currentRenderer)) return;
                        trigger.setEnabled(true);
                        trigger.setText(getString(
                                backgroundRefresh
                                        ? R.string.collapse
                                        : R.string.mobile_data));
                        if (!backgroundRefresh) {
                            message(
                                    getString(R.string.app_name),
                                    localizedError(error));
                        }
                    }
                });
    }

    private void clearActivePluginMobile() {
        pluginMobileRequestSequence++;
        activePluginMobile = null;
        activePluginMobileParent = null;
        activePluginMobileContent = null;
        activePluginMobileTrigger = null;
        activePluginMobileRange = "today";
        activePluginMobileFrom = null;
        activePluginMobileTo = null;
        pluginMobileRequestInFlight = false;
    }

    private void renderMobileGaugeDashboard(
            LinearLayout parent, JSONObject mobileCard) {
        JSONArray gauges = mobileCard.optJSONArray("gauges");
        if (gauges == null || gauges.length() == 0) {
            parent.addView(text(getString(R.string.no_mobile_data), 14, false));
            return;
        }
        boolean canvasMode = !"text".equals(mobileCard.optString("mode"));
        int textSize = Math.max(12, Math.min(48,
                mobileCard.optInt("text_size", 18)));
        for (int index = 0; index < gauges.length(); index++) {
            JSONObject gauge = gauges.optJSONObject(index);
            if (gauge == null) continue;
            if (canvasMode) {
                parent.addView(new MobileGaugeView(
                        this, gauge, appPreferences.darkTheme()));
                continue;
            }
            LinearLayout row = actionRow();
            row.addView(
                    text(gauge.optString("label", gauge.optString("id")),
                            textSize, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            String value = gauge.optBoolean("available", !gauge.isNull("value"))
                    ? String.valueOf(gauge.opt("value"))
                    : getString(R.string.not_available);
            String unit = gauge.optString("unit");
            row.addView(text(
                    value + (unit.isEmpty() ? "" : " " + unit),
                    textSize, false));
            parent.addView(row);
        }
    }

    private static String joinJsonValues(JSONArray values, String separator) {
        if (values == null) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.length(); index++) {
            String value = values.optString(index).trim();
            if (value.isEmpty()) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static String joinExcludedRanges(JSONArray ranges) {
        if (ranges == null) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < ranges.length(); index++) {
            JSONObject range = ranges.optJSONObject(index);
            if (range == null) continue;
            String start = range.optString("start").trim();
            String end = range.optString("end").trim();
            if (start.isEmpty() || end.isEmpty()) continue;
            if (result.length() > 0) result.append('\n');
            result.append(start).append("..").append(end);
        }
        return result.toString();
    }

    private JSONArray integerList(EditText input, int minimum, int maximum) {
        JSONArray result = new JSONArray();
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return result;
        java.util.Set<Integer> unique = new java.util.TreeSet<>();
        try {
            for (String item : value.split("[,;\\s]+")) {
                if (item.isEmpty()) continue;
                int number = Integer.parseInt(item);
                if (number < minimum || number > maximum) {
                    throw new NumberFormatException();
                }
                unique.add(number);
            }
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(getString(
                    R.string.program_month_days_format));
        }
        for (Integer number : unique) result.put(number);
        return result;
    }

    private JSONArray excludedDates(EditText input) {
        JSONArray result = new JSONArray();
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return result;
        try {
            for (String item : value.split("[,;\\s]+")) {
                if (item.isEmpty()) continue;
                result.put(LocalDate.parse(item).toString());
            }
        } catch (Exception error) {
            throw new IllegalArgumentException(getString(
                    R.string.program_excluded_dates_format));
        }
        return result;
    }

    private JSONArray excludedRanges(EditText input) throws Exception {
        JSONArray result = new JSONArray();
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return result;
        for (String line : value.split("[\\r\\n;]+")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\.\\.", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException(getString(
                        R.string.program_excluded_ranges_format));
            }
            String start = parts[0].trim();
            String end = parts[1].trim();
            boolean annual;
            try {
                if (start.matches("\\d{2}-\\d{2}") &&
                        end.matches("\\d{2}-\\d{2}")) {
                    java.time.MonthDay.parse("--" + start);
                    java.time.MonthDay.parse("--" + end);
                    annual = true;
                } else {
                    LocalDate.parse(start);
                    LocalDate.parse(end);
                    annual = false;
                }
            } catch (Exception error) {
                throw new IllegalArgumentException(getString(
                        R.string.program_excluded_ranges_format));
            }
            result.put(new JSONObject()
                    .put("start", start).put("end", end)
                    .put("annual", annual));
        }
        return result;
    }

    private int boundedInteger(EditText input, int minimum, int maximum,
            int errorString) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < minimum || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(getString(errorString));
        }
    }

    private boolean hasSeriesPoints(JSONArray series) {
        if (series == null) return false;
        for (int index = 0; index < series.length(); index++) {
            JSONObject item = series.optJSONObject(index);
            JSONArray points = item == null ? null : item.optJSONArray("points");
            if (points != null && points.length() > 0) return true;
        }
        return false;
    }

    private boolean supportsHistory(JSONObject card) {
        if (card == null) return false;
        if ("chart".equals(card.optString("kind"))) return true;
        if (card.optJSONObject("history") != null) return true;
        return hasSeriesPoints(card.optJSONArray("series"));
    }

    private void addHistoryRangeControls(
            LinearLayout parent, JSONObject plugin, LinearLayout pluginParent,
            Button trigger, String selectedRange, LocalDateTime from,
            LocalDateTime to) {
        parent.addView(text(getString(R.string.history_range), 14, true));
        GridLayout ranges = new GridLayout(this);
        ranges.setColumnCount(3);
        String[] keys = {"hour", "today", "week", "month", "year", "custom"};
        int[] labels = {R.string.history_one_hour, R.string.today,
                R.string.history_seven_days, R.string.history_month,
                R.string.history_year, R.string.history_custom};
        for (int index = 0; index < keys.length; index++) {
            String key = keys[index];
            Button range = compactButton(
                    getString(labels[index]),
                    key.equals(selectedRange) ? GREEN : NAVY);
            if ("custom".equals(key)) {
                range.setOnClickListener(v -> showCustomHistoryRange(
                        plugin, pluginParent, trigger, from, to));
            } else {
                range.setOnClickListener(v -> loadPluginMobileRange(
                        plugin, pluginParent, trigger, key, null, null));
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(index % 3, 1, 1f);
            params.rowSpec = GridLayout.spec(index / 3);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            ranges.addView(range, params);
        }
        parent.addView(ranges, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showCustomHistoryRange(
            JSONObject plugin, LinearLayout parent, Button trigger,
            LocalDateTime currentFrom, LocalDateTime currentTo) {
        LocalDate initialFrom = currentFrom == null
                ? LocalDate.now() : currentFrom.toLocalDate();
        LocalDate initialTo = currentTo == null
                ? LocalDate.now() : currentTo.toLocalDate();
        DatePickerDialog startDialog = new DatePickerDialog(
                this, (startPicker, startYear, startMonth, startDay) -> {
                    LocalDate selectedFrom = LocalDate.of(
                            startYear, startMonth + 1, startDay);
                    DatePickerDialog endDialog = new DatePickerDialog(
                            this, (endPicker, endYear, endMonth, endDay) -> {
                                LocalDate selectedTo = LocalDate.of(
                                        endYear, endMonth + 1, endDay);
                                if (selectedTo.isBefore(selectedFrom)) {
                                    message(
                                            getString(R.string.history_range),
                                            getString(R.string.invalid_date_range));
                                    return;
                                }
                                loadPluginMobileRange(
                                        plugin, parent, trigger, "custom",
                                        selectedFrom.atStartOfDay(),
                                        selectedTo.plusDays(1).atStartOfDay());
                            }, initialTo.getYear(), initialTo.getMonthValue() - 1,
                            initialTo.getDayOfMonth());
                    endDialog.setTitle(getString(R.string.date_to));
                    endDialog.show();
                }, initialFrom.getYear(), initialFrom.getMonthValue() - 1,
                initialFrom.getDayOfMonth());
        startDialog.setTitle(getString(R.string.date_from));
        startDialog.show();
    }

    private String mobileCardTitle(JSONObject card) {
        switch (card.optString("id")) {
            case "temperatures":
                return getString(R.string.temperature_sensors);
            case "radar":
                return getString(R.string.radar_at_location);
            case "masters":
                return getString(R.string.master_station_consumption);
            case "stations":
                return getString(R.string.running_station_consumption);
            case "wind":
                return getString(R.string.wind_speed);
            case "sun_today":
                return getString(R.string.sunrise_sunset_timeline);
            case "synchronization":
                return getString(R.string.time_synchronization);
            case "overview":
                return getString(R.string.system_information);
            case "calculation":
                return getString(R.string.weather_calculation);
            case "system_update":
                return getString(R.string.system_update);
            default:
                return card.optString(
                        "title", getString(R.string.operating_data));
        }
    }

    private String mobileMetricLabel(JSONObject metric) {
        String id = metric.optString("id");
        String serverLabel = metric.optString("label");
        if (id.startsWith("update_") && !serverLabel.isEmpty()) {
            return readable(serverLabel);
        }
        if (id.startsWith("master_") || "station_current".equals(id)) {
            return serverLabel.isEmpty() ? id : serverLabel;
        }
        switch (id) {
            case "rain_state":
                return getString(R.string.rain_status);
            case "radar_source":
                return getString(R.string.radar_source);
            case "actual":
                return getString(R.string.current_value);
            case "maximum":
                return getString(R.string.maximum);
            case "trend":
                return getString(R.string.trend);
            case "pulses":
                return getString(R.string.pulses);
            case "dht_humidity":
                return getString(R.string.humidity);
            case "dawn":
                return getString(R.string.dawn);
            case "sunrise":
                return getString(R.string.sunrise);
            case "noon":
                return getString(R.string.solar_noon);
            case "sunset":
                return getString(R.string.sunset);
            case "dusk":
                return getString(R.string.dusk);
            case "moon_phase":
                return getString(R.string.moon_phase);
            case "moon_age":
                return getString(R.string.moon_age);
            case "ospy_time":
                return getString(R.string.ospy_time);
            case "last_sync":
                return getString(R.string.last_synchronization);
            case "ntp_time":
                return getString(R.string.ntp_time);
            case "rtc_time":
                return getString(R.string.rtc_time);
            case "platform":
                return getString(R.string.platform);
            case "python":
                return getString(R.string.python_version);
            case "uptime":
                return getString(R.string.uptime);
            case "cpu_temperature":
                return getString(R.string.cpu_temperature);
            case "cpu_usage":
                return getString(R.string.cpu_usage);
            case "memory_total":
                return getString(R.string.total_memory);
            case "memory_free":
                return getString(R.string.free_memory);
            case "ip_address":
                return getString(R.string.ip_address);
            case "mac_address":
                return getString(R.string.mac_address);
            case "method":
                return getString(R.string.calculation_method);
            case "calculated_at":
                return getString(R.string.calculated_at);
            case "days_used":
                return getString(R.string.used_days);
            case "rain":
                return getString(R.string.total_rainfall);
            case "water_needed":
                return getString(R.string.irrigation_needed);
            case "water_left":
                return getString(R.string.remaining_irrigation_need);
            case "adjustment":
                return getString(R.string.weather_adjustment);
            case "raw_water_adjustment":
                return getString(R.string.unrestricted_weather_adjustment);
            case "average_temperature_c":
                return getString(R.string.average_temperature);
            case "average_humidity":
                return getString(R.string.average_humidity);
            case "rain_yesterday":
                return getString(R.string.yesterday_rainfall);
            case "rain_today":
                return getString(R.string.today_rainfall);
            case "total_eto":
                return getString(R.string.total_eto);
            case "total_etc":
            case "etc":
                return getString(R.string.crop_evapotranspiration);
            case "effective_rain_mm":
                return getString(R.string.effective_rainfall);
            case "net_irrigation_mm":
                return getString(R.string.net_irrigation);
            case "gross_irrigation_mm":
                return getString(R.string.gross_irrigation);
            case "limit":
                return getString(R.string.limit);
            case "rain_mm":
                return getString(R.string.rainfall);
            case "temp":
            case "temperature":
                return getString(R.string.temperature);
            case "wind_ms":
                return getString(R.string.wind_speed);
            case "eto":
                return getString(R.string.eto);
            case "temperature_factor":
                return getString(R.string.temperature_factor);
            case "humidity_factor":
                return getString(R.string.humidity_factor);
            case "rain_factor":
                return getString(R.string.rain_factor);
            case "note":
                return getString(R.string.influence);
            case "state":
                return getString(R.string.state);
            case "model":
                return getString(R.string.model);
            case "updated":
                return getString(R.string.updated);
            default:
                if (id.startsWith("temperature_")) {
                    return indexedMetricLabel(
                            getString(R.string.temperature), id);
                }
                if (id.startsWith("humidity_")) {
                    return indexedMetricLabel(
                            getString(R.string.humidity), id);
                }
                if (id.startsWith("illuminance_")) {
                    return indexedMetricLabel(
                            getString(R.string.illuminance), id);
                }
                if (id.startsWith("power_")) {
                    return indexedMetricLabel(getString(R.string.power), id);
                }
                if (id.startsWith("retpower_")) {
                    return indexedMetricLabel(
                            getString(R.string.returned_power), id);
                }
                if (id.startsWith("voltage_")) {
                    return indexedMetricLabel(getString(R.string.voltage), id);
                }
                if (id.startsWith("battery_")) {
                    return indexedMetricLabel(getString(R.string.battery), id);
                }
                if (id.startsWith("rssi_")) {
                    return indexedMetricLabel(
                            getString(R.string.wifi_signal), id);
                }
                if (id.startsWith("output_")) {
                    return indexedMetricLabel(getString(R.string.output), id);
                }
                return metric.optString("label", id);
        }
    }

    private String indexedMetricLabel(String label, String id) {
        int separator = id.lastIndexOf('_');
        if (separator < 0 || separator + 1 >= id.length()) return label;
        try {
            return label + " " + (Integer.parseInt(id.substring(separator + 1)) + 1);
        } catch (NumberFormatException ignored) {
            return label;
        }
    }

    private String mobileMetricValue(JSONObject metric) {
        String id = metric.optString("id");
        Object rawValue = metric.opt("value");
        if (rawValue instanceof Boolean) {
            return getString((Boolean) rawValue ? R.string.on : R.string.off);
        }
        String value = String.valueOf(rawValue);
        if (id.startsWith("update_")) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "success":
                    return getString(R.string.success_status);
                case "none":
                    return getString(R.string.none_value);
                case "inactive":
                    return getString(R.string.inactive);
                case "running":
                    return getString(R.string.running);
                case "stopped":
                    return getString(R.string.stopped);
                case "waiting for healthy start":
                    return getString(R.string.waiting_healthy_start);
            }
            if (normalized.startsWith("stable (")) {
                return getString(R.string.stable_channel) + value.substring(6);
            }
            if (normalized.startsWith("test (")) {
                return getString(R.string.test_channel) + value.substring(4);
            }
        }
        if ("trend".equals(id)) {
            switch (MobileTrendState.normalize(value)) {
                case "rising":
                    return "\u2191 " + getString(R.string.rising);
                case "falling":
                    return "\u2193 " + getString(R.string.falling);
                case "steady":
                    return "\u2192 " + getString(R.string.steady);
                default:
                    return "\u00b7 " + getString(R.string.pending);
            }
        }
        if ("rain_state".equals(id)) {
            if ("rain".equalsIgnoreCase(value) ||
                    "raining".equalsIgnoreCase(value)) {
                return getString(R.string.rain_detected);
            }
            if ("dry".equalsIgnoreCase(value) ||
                    "no_rain".equalsIgnoreCase(value)) {
                return getString(R.string.no_rain_detected);
            }
        }
        if ("state".equals(id)) {
            if ("online".equalsIgnoreCase(value)) return getString(R.string.online);
            if ("offline".equalsIgnoreCase(value)) return getString(R.string.offline);
        }
        if (id.startsWith("output_")) {
            if ("on".equalsIgnoreCase(value)) return getString(R.string.on);
            if ("off".equalsIgnoreCase(value)) return getString(R.string.off);
        }
        if ("moon_phase".equals(id)) {
            switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "new moon":
                    return getString(R.string.new_moon);
                case "waxing moon":
                    return getString(R.string.waxing_moon);
                case "full moon":
                    return getString(R.string.full_moon);
                case "waning moon":
                    return getString(R.string.waning_moon);
            }
        }
        if ("limit".equals(id)) {
            if ("minimum limit applied".equalsIgnoreCase(value)) {
                return getString(R.string.minimum_limit_applied);
            }
            if ("maximum limit applied".equalsIgnoreCase(value)) {
                return getString(R.string.maximum_limit_applied);
            }
        }
        if ("not available".equalsIgnoreCase(value)) {
            return getString(R.string.not_available);
        }
        return value;
    }

    private void addMobileImage(LinearLayout parent, JSONObject imageData) {
        try {
            byte[] bytes = Base64.decode(
                    imageData.optString("data_base64"), Base64.DEFAULT);
            if (bytes.length == 0) return;
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription(getString(R.string.radar_image));
            parent.addView(image, new LinearLayout.LayoutParams(-1, -2));
            Glide.with(this).load(bytes).fitCenter().into(image);
            String updated = imageData.optString("updated");
            if (!updated.isEmpty()) {
                TextView label = text(
                        getString(R.string.updated) + ": " +
                                formatTimestamp(updated), 11, false);
                label.setTextColor(MUTED);
                parent.addView(label);
            }
        } catch (Exception ignored) {
            // A malformed optional image must not hide the remaining telemetry.
        }
    }

    private void renderSensors(JSONArray sensorItems) {
        if (sensorItems.length() == 0) {
            content.addView(text(getString(R.string.no_data), 16, false));
            return;
        }
        for (int i = 0; i < sensorItems.length(); i++) {
            JSONObject sensor = sensorItems.optJSONObject(i);
            if (sensor == null) continue;
            LinearLayout card = cardColumn();
            TextView name = text(
                    sensor.optInt("number") + ". " +
                            sensor.optString("name", getString(R.string.sensors)),
                    17, true);
            card.addView(name);
            LinearLayout sensorState = actionRow();
            boolean sensorEnabled = sensor.optBoolean("enabled");
            sensorState.addView(badge(
                    sensorEnabled
                            ? getString(R.string.enabled)
                            : getString(R.string.disabled),
                    sensorEnabled ? "ok" : "stopped"));
            Button sensorToggle = button(
                    getString(sensorEnabled
                            ? R.string.turn_off : R.string.turn_on),
                    sensorEnabled ? RED : GREEN);
            sensorToggle.setOnClickListener(v -> {
                JSONObject payload = new JSONObject();
                try {
                    payload.put("enabled", !sensorEnabled);
                } catch (Exception ignored) {
                    return;
                }
                put(
                        "/sensors/" + sensor.optString("id"),
                        payload,
                        () -> load("/sensors", "sensors"));
            });
            sensorState.addView(sensorToggle);
            card.addView(sensorState);
            JSONObject display = sensor.optJSONObject("display");
            JSONObject reading = display == null
                    ? null : display.optJSONObject("reading");
            if (display != null) {
                card.addView(badge(
                        display.optBoolean("connected")
                                ? getString(R.string.connected)
                                : getString(R.string.no_response),
                        display.optBoolean("connected") ? "ok" : "error"));
            }
            if (reading != null) {
                addPair(
                        card, getString(R.string.current_value),
                        sensorReading(reading));
            }
            addPairIfPresent(
                    card, sensor, "last_response_datetime",
                    getString(R.string.last_response));
            if (sensor.has("last_battery") && !sensor.isNull("last_battery")) {
                addPair(
                        card, getString(R.string.battery),
                        sensor.optString("last_battery") + " " +
                                (display == null ? "V" :
                                        display.optString("battery_unit", "V")));
            }
            if (sensor.has("rssi") && !sensor.isNull("rssi")) {
                addPair(
                        card, getString(R.string.signal),
                        sensor.optString("rssi") + " " +
                                (display == null ? "" :
                                        display.optString("signal_unit")));
            }
            if (display != null) {
                addPairIfPresent(
                        card, display, "firmware",
                        getString(R.string.firmware));
                addPair(
                        card, getString(R.string.sensor_type),
                        sensorType(display));
                addPair(
                        card, getString(R.string.communication),
                        communicationType(display.optString("communication")));
                addPairIfPresent(
                        card, display, "ip_address",
                        getString(R.string.ip_address));
            }
            JSONArray errors = sensor.optJSONArray("field_errors");
            if (errors != null && errors.length() > 0) {
                TextView unavailable = text(
                        getString(R.string.partial_sensor_data), 13, false);
                unavailable.setTextColor(AMBER);
                card.addView(unavailable);
            }
            content.addView(card);
        }
    }

    private void renderWeather(JSONObject weather, boolean showProvider) {
        JSONArray cards = weather.optJSONArray("cards");
        if (cards == null || cards.length() == 0) {
            content.addView(statusCard(
                    getString(R.string.weather),
                    getString(R.string.no_data), "warning"));
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < cards.length(); i++) {
                JSONObject item = cards.optJSONObject(i);
                if (item == null) continue;
                LinearLayout forecast = cardColumn();
                TextView icon = text(weatherIcon(item.optString("icon")), 28, false);
                icon.setGravity(Gravity.CENTER);
                forecast.addView(icon);
                TextView values = text(
                        item.optString("time") + "\n" +
                                item.optString("temperature") + "\n" +
                                item.optString("precipitation") + "\n" +
                                item.optString("description"),
                        14, false);
                values.setGravity(Gravity.CENTER);
                forecast.addView(values);
                LinearLayout.LayoutParams layout =
                        new LinearLayout.LayoutParams(0, -2, 1);
                layout.setMargins(dp(3), 0, dp(3), dp(8));
                forecast.setLayoutParams(layout);
                row.addView(forecast);
            }
            content.addView(row);
        }
        if (showProvider) {
            addStandalonePair(
                    getString(R.string.provider), weather.optString("provider"));
            addStandalonePair(
                    getString(R.string.updated), weather.optString("updated"));
        }
    }

    private void renderLogs(JSONArray events) {
        LinearLayout choices = actionRow();
        boolean stationLog = currentPath.contains("/logs/runs");
        Button eventsButton = compactButton(
                getString(R.string.event_log), stationLog ? NAVY : GREEN);
        eventsButton.setOnClickListener(v ->
                load("/logs/events?limit=100", "logs"));
        choices.addView(eventsButton);
        Button runsButton = compactButton(
                getString(R.string.station_log), stationLog ? GREEN : NAVY);
        runsButton.setOnClickListener(v ->
                load("/logs/runs?limit=100", "logs"));
        choices.addView(runsButton);
        content.addView(choices);
        if (events.length() == 0) {
            content.addView(text(getString(R.string.no_data), 16, false));
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            if (stationLog) {
                renderStationRun(event);
                continue;
            }
            LinearLayout card = cardColumn();
            LinearLayout header = actionRow();
            TextView subject = text(
                    event.optString("subject", event.optString("id")), 16, true);
            header.addView(subject, new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(badge(
                    localizedStatus(event.optString("level")),
                    event.optString("level")));
            card.addView(header);
            String dateTime = (
                    event.optString("date") + " " +
                            event.optString("time")).trim();
            addPair(card, getString(R.string.date_time), dateTime);
            addPairIfPresent(card, event, "category", getString(R.string.category));
            if (event.has("status") && !event.isNull("status")) {
                addPair(
                        card, getString(R.string.status),
                        localizedStatus(event.optString("status")));
            }
            content.addView(card);
        }
    }

    private void renderDiagnostics(JSONObject diagnostics) {
        String overall = diagnostics.optString("status", "ok");
        LinearLayout summary = card();
        summary.addView(
                text(getString(R.string.status), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(badge(localizedStatus(overall), overall));
        content.addView(summary);
        JSONArray items = diagnostics.optJSONArray("items");
        if (items == null) {
            renderFallback(diagnostics);
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            LinearLayout card = statusCardContainer(item.optString("status"));
            LinearLayout header = actionRow();
            header.addView(
                    text(item.optString("title", item.optString("id")), 16, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            header.addView(badge(
                    localizedStatus(item.optString("status")),
                    item.optString("status")));
            card.addView(header);
            addPairIfPresent(card, item, "summary", getString(R.string.status));
            addPairIfPresent(card, item, "details", getString(R.string.details));
            addPairIfPresent(card, item, "solution", getString(R.string.solution));
            content.addView(card);
        }
    }

    private void renderPlugins(JSONArray pluginItems) {
        List<JSONObject> runningPlugins = new ArrayList<>();
        List<JSONObject> stoppedPlugins = new ArrayList<>();
        List<JSONObject> pluginsWithoutData = new ArrayList<>();
        for (int i = 0; i < pluginItems.length(); i++) {
            JSONObject plugin = pluginItems.optJSONObject(i);
            if (plugin == null) continue;
            JSONObject mobile = plugin.optJSONObject("mobile");
            boolean mobileAvailable =
                    mobile != null && mobile.optBoolean("available");
            switch (PluginGrouping.classify(
                    plugin.optBoolean("running"), mobileAvailable)) {
                case RUNNING_WITH_DATA:
                    runningPlugins.add(plugin);
                    break;
                case STOPPED:
                    stoppedPlugins.add(plugin);
                    break;
                case RUNNING_WITHOUT_DATA:
                    pluginsWithoutData.add(plugin);
                    break;
            }
        }
        addPluginGroup(
                R.string.plugin_group_running, runningPlugins, true);
        addPluginGroup(
                R.string.plugin_group_stopped, stoppedPlugins, false);
        addPluginGroup(
                R.string.plugin_group_no_data, pluginsWithoutData, false);
    }

    private void addPluginGroup(
            int titleResource, List<JSONObject> plugins,
            boolean initiallyExpanded) {
        LinearLayout section = cardColumn();
        LinearLayout header = actionRow();
        header.addView(text(
                        getString(
                                R.string.plugin_group_title,
                                getString(titleResource), plugins.size()),
                        18, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button toggle = compactButton(
                getString(initiallyExpanded
                        ? R.string.collapse : R.string.expand), NAVY);
        header.addView(toggle);
        section.addView(header);

        LinearLayout groupContent = new LinearLayout(this);
        groupContent.setOrientation(LinearLayout.VERTICAL);
        groupContent.setVisibility(
                initiallyExpanded ? View.VISIBLE : View.GONE);
        for (JSONObject plugin : plugins) {
            renderPluginCard(plugin, groupContent);
        }
        section.addView(groupContent, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnClickListener(v -> {
            boolean expand = groupContent.getVisibility() != View.VISIBLE;
            groupContent.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(expand ? R.string.collapse : R.string.expand);
        });
        content.addView(section);
    }

    private void renderPluginCard(JSONObject plugin, LinearLayout parent) {
        JSONObject health = plugin.optJSONObject("health");
        String healthStatus = health == null
                ? "" : health.optString("status");
        LinearLayout card = cardColumn();
        card.addView(text(
                plugin.optString("name", plugin.optString("id")) +
                        (plugin.optString("version").isEmpty()
                                ? "" : " · " + plugin.optString("version")),
                16, true));
        LinearLayout statuses = actionRow();
        statuses.addView(badge(
                plugin.optBoolean("running")
                        ? getString(R.string.running)
                        : getString(R.string.stopped),
                plugin.optBoolean("running") ? "ok" : "stopped"));
        if (!healthStatus.isEmpty()) {
            statuses.addView(badge(
                    localizedStatus(healthStatus), healthStatus));
        }
        boolean pluginEnabled = plugin.optBoolean("enabled");
        Button pluginToggle = button(
                getString(pluginEnabled
                        ? R.string.turn_off : R.string.turn_on),
                pluginEnabled ? RED : GREEN);
        pluginToggle.setOnClickListener(v -> {
            JSONObject payload = new JSONObject();
            try {
                payload.put("enabled", !pluginEnabled);
            } catch (Exception ignored) {
                return;
            }
            put(
                    "/plugins/" + plugin.optString("id"),
                    payload,
                    () -> load("/plugins", "plugins"));
        });
        statuses.addView(pluginToggle);
        card.addView(statuses);
        JSONObject mobile = plugin.optJSONObject("mobile");
        boolean mobileAvailable =
                mobile != null && mobile.optBoolean("available");
        Button dataButton = compactButton(
                getString(R.string.mobile_data),
                mobileAvailable ? NAVY : MUTED);
        dataButton.setEnabled(mobileAvailable);
        dataButton.setOnClickListener(v ->
                loadPluginMobile(plugin, card, dataButton));
        card.addView(dataButton);
        parent.addView(card);
    }

    private void renderSystem(Object value) {
        JSONObject root = value instanceof JSONObject
                ? (JSONObject) value : new JSONObject();
        JSONObject ospy = root.optJSONObject("ospy");
        JSONObject details = ospy == null ? null : ospy.optJSONObject("details");
        systemUpdateAvailable = details != null &&
                details.optBoolean("update_available", false);
        systemCurrentCommit = details == null
                ? "" : details.optString("current_commit", "");
        systemTargetCommit = details == null
                ? "" : details.optString("target_commit", "");

        LinearLayout updateCard = cardColumn();
        updateCard.addView(text(getString(R.string.app_name), 19, true));
        if (ospy != null) {
            String statusCode = ospy.optString("status", "unknown");
            addPair(
                    updateCard, getString(R.string.status),
                    localizedStatus(statusCode));
            addPairIfPresent(
                    updateCard, ospy, "summary",
                    getString(R.string.details));
            if (details != null) {
                addPairIfPresent(
                        updateCard, details, "current_version",
                        getString(R.string.current_version));
                addPairIfPresent(
                        updateCard, details, "current_commit",
                        getString(R.string.current_commit));
                addPairIfPresent(
                        updateCard, details, "target_commit",
                        getString(R.string.target_commit));
                addPairIfPresent(
                        updateCard, details, "stable_release",
                        getString(R.string.stable_release));
                if (details.has("update_channel")) {
                    addPairIfPresent(
                            updateCard, details, "update_channel",
                            getString(R.string.update_channel));
                } else {
                    addPairIfPresent(
                            updateCard, details, "upstream_branch",
                            getString(R.string.update_channel));
                }
                addBooleanPairIfPresent(
                        updateCard, details, "automatic_update",
                        getString(R.string.automatic_update));
                addBooleanPairIfPresent(
                        updateCard, details, "update_available",
                        getString(R.string.update_available));
            }
        } else {
            addPair(
                    updateCard, getString(R.string.status),
                    getString(R.string.warning_status));
            updateCard.addView(text(getString(R.string.no_data), 14, false));
        }

        LinearLayout updateActions = actionRow();
        systemCheckButton = button(getString(R.string.check_updates), GREEN);
        systemCheckButton.setOnClickListener(v ->
                startSystemUpdateOperation("check"));
        updateActions.addView(systemCheckButton);

        systemInstallButton = button(
                getString(R.string.install_update), GREEN);
        systemInstallButton.setOnClickListener(v ->
                confirmSystemUpdate());
        boolean applyActive = "update.apply".equals(systemOperationKind) &&
                isSystemOperationActive();
        systemInstallButton.setVisibility(
                systemUpdateAvailable || applyActive ? View.VISIBLE : View.GONE);
        updateActions.addView(systemInstallButton);
        updateCard.addView(updateActions);

        systemOperationStatusView = text("", 13, true);
        systemOperationStatusView.setTextColor(MUTED);
        updateCard.addView(systemOperationStatusView);
        systemOperationProgressView = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        systemOperationProgressView.setMax(100);
        updateCard.addView(
                systemOperationProgressView,
                new LinearLayout.LayoutParams(-1, dp(8)));
        updateSystemOperationUi();
        content.addView(updateCard);

        LinearLayout systemActionsCard = cardColumn();
        systemActionsCard.addView(text(
                getString(R.string.system_actions), 16, true));
        LinearLayout systemActions = actionRow();
        Button backup = button(getString(R.string.create_backup), NAVY);
        backup.setOnClickListener(v -> post(
                "/backups", new JSONObject(),
                () -> toast(getString(R.string.accepted))));
        systemActions.addView(backup);
        Button restart = button(getString(R.string.restart_ospy), RED);
        restart.setOnClickListener(v -> confirmAction(
                getString(R.string.confirm_restart),
                "/system/actions/restart-ospy"));
        systemActions.addView(restart);
        systemActionsCard.addView(systemActions);
        content.addView(systemActionsCard);

        LinearLayout serviceOutagesCard = cardColumn();
        serviceOutagesCard.addView(text(
                getString(R.string.service_outages), 16, true));
        serviceOutagesCard.addView(text(
                getString(R.string.loading), 14, false));
        content.addView(serviceOutagesCard);
        loadServiceOutages(serviceOutagesCard);

        LinearLayout backupsCard = cardColumn();
        LinearLayout backupsHeader = new LinearLayout(this);
        backupsHeader.setOrientation(LinearLayout.HORIZONTAL);
        backupsHeader.setGravity(Gravity.CENTER_VERTICAL);
        backupsHeader.addView(
                text(getString(R.string.available_backups), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button expandBackups = compactButton(
                getString(R.string.expand), NAVY);
        backupsHeader.addView(expandBackups);
        backupsCard.addView(backupsHeader);

        LinearLayout backupsContent = new LinearLayout(this);
        backupsContent.setOrientation(LinearLayout.VERTICAL);
        backupsContent.setVisibility(View.GONE);
        LinearLayout backupActions = actionRow();
        LinearLayout backupList = new LinearLayout(this);
        backupList.setOrientation(LinearLayout.VERTICAL);
        Button refreshBackups = button(
                getString(R.string.refresh_backups), NAVY);
        refreshBackups.setOnClickListener(v -> loadBackups(backupList));
        backupActions.addView(refreshBackups);
        backupsContent.addView(backupActions);
        backupsContent.addView(backupList);
        backupsCard.addView(backupsContent);
        content.addView(backupsCard);

        boolean[] backupsLoaded = {false};
        expandBackups.setOnClickListener(v -> {
            boolean show = backupsContent.getVisibility() != View.VISIBLE;
            backupsContent.setVisibility(show ? View.VISIBLE : View.GONE);
            expandBackups.setText(getString(
                    show ? R.string.collapse : R.string.expand));
            if (show && !backupsLoaded[0]) {
                backupsLoaded[0] = true;
                loadBackups(backupList);
            }
        });

        if (!pendingSystemAnnouncement.isEmpty()) {
            String announcement = pendingSystemAnnouncement;
            pendingSystemAnnouncement = "";
            content.post(() -> showSystemAnnouncement(announcement));
        }
    }

    private void loadServiceOutages(LinearLayout card) {
        if (api == null || card == null) return;
        api.request("GET", "/service-outages", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (!"system".equals(currentRenderer) || card.getParent() == null) return;
                JSONArray outages = response.optJSONArray("data");
                renderServiceOutages(card, outages == null ? new JSONArray() : outages);
            }

            @Override public void failure(String error) {
                if (!"system".equals(currentRenderer) || card.getParent() == null) return;
                card.removeAllViews();
                card.addView(text(getString(R.string.service_outages), 16, true));
                TextView failure = text(localizedError(error), 13, false);
                failure.setTextColor(RED);
                card.addView(failure);
            }
        });
    }

    private void renderServiceOutages(LinearLayout card, JSONArray outages) {
        card.removeAllViews();
        card.addView(text(getString(R.string.service_outages), 16, true));
        card.addView(text(
                getString(R.string.service_outages_description), 13, false));
        if (outages.length() == 0) {
            TextView empty = text(getString(R.string.no_service_outages), 14, false);
            empty.setTextColor(MUTED);
            card.addView(empty);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < outages.length(); index++) {
            JSONObject item = outages.optJSONObject(index);
            if (item == null) continue;
            LinearLayout outage = new LinearLayout(this);
            outage.setOrientation(LinearLayout.VERTICAL);
            outage.setPadding(dp(10), dp(8), dp(10), dp(8));
            outage.setBackground(background(SURFACE, CARD_BORDER, 8));

            LinearLayout titleRow = actionRow();
            titleRow.addView(
                    text(item.optString("name"), 15, true),
                    new LinearLayout.LayoutParams(0, -2, 1));
            LocalDateTime start = parseServiceOutageDateTime(
                    item.optString("start"));
            int status = item.optBoolean("active", false)
                    ? R.string.service_outage_active
                    : start != null && start.isAfter(now)
                    ? R.string.service_outage_scheduled
                    : R.string.service_outage_expired;
            TextView state = text(getString(status), 13, true);
            state.setTextColor(item.optBoolean("active", false) ? RED : MUTED);
            titleRow.addView(state);
            outage.addView(titleRow);
            addPair(outage, getString(R.string.service_outage_start),
                    localizedServiceOutageDateTime(item.optString("start")));
            addPair(outage, getString(R.string.service_outage_end),
                    localizedServiceOutageDateTime(item.optString("end")));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            card.addView(outage, params);
        }
    }

    private LocalDateTime parseServiceOutageDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String localizedServiceOutageDateTime(String value) {
        LocalDateTime parsed = parseServiceOutageDateTime(value);
        return parsed == null ? value : parsed.format(
                DateTimeFormatter.ofLocalizedDateTime(
                        FormatStyle.MEDIUM, FormatStyle.SHORT));
    }

    private void resetSystemOperationState() {
        systemOperationHandler.removeCallbacksAndMessages(null);
        systemOperationId = "";
        systemOperationKind = "";
        systemOperationStatus = "";
        systemOperationError = "";
        systemOperationProgress = 0;
        systemOperationStartedAt = 0;
        systemReconnectStartedAt = 0;
        systemOperationPolling = false;
        systemWaitingForReconnect = false;
        pendingSystemAnnouncement = "";
        systemUpdateAvailable = false;
        systemCurrentCommit = "";
        systemTargetCommit = "";
        systemApplyCommitBefore = "";
        systemApplyTargetCommit = "";
        systemOperationStatusView = null;
        systemOperationProgressView = null;
        systemCheckButton = null;
        systemInstallButton = null;
    }

    private boolean isSystemOperationActive() {
        return systemWaitingForReconnect ||
                "pending".equals(systemOperationStatus) ||
                "running".equals(systemOperationStatus) ||
                "accepted".equals(systemOperationStatus);
    }

    private void confirmSystemUpdate() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_update))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        startSystemUpdateOperation("apply"))
                .show();
    }

    private void startSystemUpdateOperation(String action) {
        if (api == null || isSystemOperationActive()) return;
        systemOperationHandler.removeCallbacksAndMessages(null);
        systemOperationId = "";
        systemOperationKind = "update." + action;
        systemOperationStatus = "pending";
        systemOperationError = "";
        systemOperationProgress = 0;
        systemOperationStartedAt = System.currentTimeMillis();
        if ("apply".equals(action)) {
            systemApplyCommitBefore = systemCurrentCommit;
            systemApplyTargetCommit = systemTargetCommit;
        }
        systemOperationPolling = true;
        systemWaitingForReconnect = false;
        updateSystemOperationUi();

        api.request(
                "POST", "/updates/actions/" + action, new JSONObject(),
                new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        JSONObject operation = response.optJSONObject("data");
                        if (operation == null || operation.optString("id").isEmpty()) {
                            failSystemOperation(getString(R.string.request_failed));
                            return;
                        }
                        applySystemOperationData(operation);
                        systemOperationHandler.postDelayed(
                                MainActivity.this::pollSystemOperation,
                                UPDATE_OPERATION_POLL_MS);
                    }

                    @Override public void failure(String error) {
                        failSystemOperation(localizedError(error));
                    }
                });
    }

    private void applySystemOperationData(JSONObject operation) {
        systemOperationId = operation.optString("id", systemOperationId);
        systemOperationKind = operation.optString("kind", systemOperationKind);
        systemOperationStatus = operation.optString(
                "status", systemOperationStatus);
        systemOperationProgress = Math.max(
                0, Math.min(100, operation.optInt(
                        "progress", systemOperationProgress)));
        systemOperationError = operation.optString("error", "");
        updateSystemOperationUi();
    }

    private void pollSystemOperation() {
        if (api == null || systemOperationId.isEmpty() ||
                !systemOperationPolling || systemWaitingForReconnect) return;
        api.request(
                "GET", "/operations/" + systemOperationId, null,
                new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        JSONObject operation = response.optJSONObject("data");
                        if (operation == null) {
                            retrySystemOperationPoll();
                            return;
                        }
                        applySystemOperationData(operation);
                        if ("completed".equals(systemOperationStatus)) {
                            systemOperationPolling = false;
                            if ("update.apply".equals(systemOperationKind)) {
                                waitForSystemAfterUpdate();
                            } else {
                                refreshSystemAfterOperation("check");
                            }
                            return;
                        }
                        if ("failed".equals(systemOperationStatus)) {
                            failSystemOperation(systemOperationError.isEmpty()
                                    ? getString(R.string.operation_failed)
                                    : systemOperationError);
                            return;
                        }
                        retrySystemOperationPoll();
                    }

                    @Override public void failure(String error) {
                        if ("update.apply".equals(systemOperationKind)) {
                            // The normal update path may restart OSPy before
                            // the client can read the completed operation.
                            waitForSystemAfterUpdate();
                        } else if (System.currentTimeMillis() -
                                systemOperationStartedAt <
                                UPDATE_RECONNECT_TIMEOUT_MS) {
                            retrySystemOperationPoll();
                        } else {
                            failSystemOperation(localizedError(error));
                        }
                    }
                });
    }

    private void retrySystemOperationPoll() {
        if (!systemOperationPolling) return;
        if (System.currentTimeMillis() - systemOperationStartedAt >=
                UPDATE_RECONNECT_TIMEOUT_MS) {
            failSystemOperation(getString(R.string.update_operation_timeout));
            return;
        }
        systemOperationHandler.postDelayed(
                this::pollSystemOperation, UPDATE_OPERATION_POLL_MS);
    }

    private void waitForSystemAfterUpdate() {
        systemOperationHandler.removeCallbacksAndMessages(null);
        systemOperationPolling = false;
        systemWaitingForReconnect = true;
        systemReconnectStartedAt = System.currentTimeMillis();
        systemOperationStatus = "reconnecting";
        systemOperationProgress = Math.max(95, systemOperationProgress);
        updateSystemOperationUi();
        systemOperationHandler.postDelayed(
                this::probeSystemAfterUpdate, UPDATE_INITIAL_RESTART_WAIT_MS);
    }

    private void probeSystemAfterUpdate() {
        if (api == null || !systemWaitingForReconnect) return;
        api.request("GET", "/updates", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                JSONObject updateData = response.optJSONObject("data");
                if (!systemUpdateWasApplied(updateData)) {
                    if (System.currentTimeMillis() - systemReconnectStartedAt <
                            UPDATE_APPLY_VERIFY_WAIT_MS) {
                        systemOperationHandler.postDelayed(
                                MainActivity.this::probeSystemAfterUpdate,
                                UPDATE_RECONNECT_POLL_MS);
                        return;
                    }
                    failSystemOperation(getString(R.string.update_not_applied));
                    if ("system".equals(currentRenderer) && updateData != null) {
                        content.removeAllViews();
                        renderSystem(updateData);
                    }
                    return;
                }
                systemWaitingForReconnect = false;
                systemOperationStatus = "completed";
                systemOperationProgress = 100;
                systemOperationError = "";
                pendingSystemAnnouncement = "apply";
                notifications.show(
                        NotificationCenter.CATEGORY_UPDATES,
                        getString(R.string.update_operation_completed),
                        getString(R.string.update_operation_completed_message));
                displayRefreshedSystem(response);
            }

            @Override public void failure(String error) {
                if (System.currentTimeMillis() - systemReconnectStartedAt >=
                        UPDATE_RECONNECT_TIMEOUT_MS) {
                    failSystemOperation(
                            getString(R.string.update_reconnect_timeout));
                    return;
                }
                systemOperationHandler.postDelayed(
                        MainActivity.this::probeSystemAfterUpdate,
                        UPDATE_RECONNECT_POLL_MS);
            }
        });
    }


    private boolean systemUpdateWasApplied(JSONObject updateData) {
        if (updateData == null || systemApplyCommitBefore.isEmpty()) return true;
        JSONObject ospy = updateData.optJSONObject("ospy");
        JSONObject details = ospy == null ? null : ospy.optJSONObject("details");
        if (details == null) return true;
        String currentCommit = details.optString("current_commit", "");
        String targetCommit = details.optString("target_commit", "");
        boolean updateAvailable = details.optBoolean("update_available", false);
        if (!currentCommit.isEmpty() &&
                !currentCommit.equals(systemApplyCommitBefore)) return true;
        if (!systemApplyTargetCommit.isEmpty() &&
                systemApplyTargetCommit.equals(currentCommit)) return true;
        if (!targetCommit.isEmpty() && targetCommit.equals(currentCommit)) return true;
        return !updateAvailable && systemApplyTargetCommit.isEmpty();
    }

    private void refreshSystemAfterOperation(String announcement) {
        if (api == null) return;
        api.request("GET", "/updates", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                systemOperationStatus = "completed";
                systemOperationProgress = 100;
                systemOperationError = "";
                pendingSystemAnnouncement = announcement;
                displayRefreshedSystem(response);
            }

            @Override public void failure(String error) {
                failSystemOperation(localizedError(error));
            }
        });
    }

    private void displayRefreshedSystem(JSONObject response) {
        JSONObject data = response.optJSONObject("data");
        if ("system".equals(currentRenderer) &&
                data != null && content != null) {
            content.removeAllViews();
            renderSystem(data);
        } else {
            updateSystemOperationUi();
        }
    }

    private void failSystemOperation(String error) {
        systemOperationHandler.removeCallbacksAndMessages(null);
        systemOperationPolling = false;
        systemWaitingForReconnect = false;
        systemOperationStatus = "failed";
        systemOperationProgress = 100;
        systemOperationError = error == null ? "" : error;
        updateSystemOperationUi();
        notifications.show(
                NotificationCenter.CATEGORY_UPDATES,
                getString(R.string.update_operation_failed),
                systemOperationError.isEmpty()
                        ? getString(R.string.operation_failed)
                        : systemOperationError);
        if ("system".equals(currentRenderer)) {
            message(
                    getString(R.string.operation_error),
                    systemOperationError.isEmpty()
                            ? getString(R.string.operation_failed)
                            : systemOperationError);
        }
    }

    private void updateSystemOperationUi() {
        boolean visible = !systemOperationStatus.isEmpty();
        if (systemOperationStatusView != null) {
            systemOperationStatusView.setVisibility(
                    visible ? View.VISIBLE : View.GONE);
            systemOperationStatusView.setText(systemOperationDescription());
            systemOperationStatusView.setTextColor(
                    "failed".equals(systemOperationStatus) ? RED : MUTED);
        }
        if (systemOperationProgressView != null) {
            systemOperationProgressView.setVisibility(
                    visible ? View.VISIBLE : View.GONE);
            systemOperationProgressView.setProgress(systemOperationProgress);
        }
        boolean active = isSystemOperationActive();
        if (systemCheckButton != null) {
            systemCheckButton.setEnabled(!active);
            systemCheckButton.setText(getString(
                    "update.check".equals(systemOperationKind) && active
                            ? R.string.checking_updates
                            : R.string.check_updates));
        }
        if (systemInstallButton != null) {
            systemInstallButton.setEnabled(!active && systemUpdateAvailable);
            systemInstallButton.setText(getString(
                    "update.apply".equals(systemOperationKind) && active
                            ? R.string.installing_update
                            : R.string.install_update));
            systemInstallButton.setVisibility(
                    systemUpdateAvailable ||
                            ("update.apply".equals(systemOperationKind) && active)
                            ? View.VISIBLE : View.GONE);
        }
    }

    private String systemOperationDescription() {
        if (systemWaitingForReconnect ||
                "reconnecting".equals(systemOperationStatus)) {
            return getString(R.string.waiting_for_ospy_restart);
        }
        String action = "update.apply".equals(systemOperationKind)
                ? getString(R.string.installing_update)
                : getString(R.string.checking_updates);
        String state;
        switch (systemOperationStatus) {
            case "pending":
            case "accepted":
                state = getString(R.string.operation_pending);
                break;
            case "running":
                state = getString(R.string.operation_running);
                break;
            case "completed":
                state = getString(R.string.operation_completed);
                break;
            case "failed":
                state = getString(R.string.operation_failed);
                break;
            default:
                state = localizedStatus(systemOperationStatus);
                break;
        }
        String result = action + " · " + state + " · " +
                getString(R.string.operation_progress, systemOperationProgress);
        if (!systemOperationError.isEmpty()) {
            result += "\n" + systemOperationError;
        }
        return result;
    }

    private void showSystemAnnouncement(String announcement) {
        if (!"system".equals(currentRenderer)) return;
        if ("check".equals(announcement)) {
            String result = getString(systemUpdateAvailable
                    ? R.string.update_found
                    : R.string.no_update_available);
            notifications.show(
                    NotificationCenter.CATEGORY_UPDATES,
                    getString(R.string.update_check_complete), result);
            message(getString(R.string.update_check_complete), result);
        } else if ("apply".equals(announcement)) {
            message(
                    getString(R.string.update_operation_completed),
                    getString(R.string.update_operation_completed_message));
        }
    }

    private void loadBackups(LinearLayout target) {
        target.removeAllViews();
        target.addView(text(getString(R.string.loading), 14, false));
        api.request("GET", "/backups", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                target.removeAllViews();
                JSONArray backups = response.optJSONArray("data");
                if (backups == null || backups.length() == 0) {
                    target.addView(text(getString(R.string.no_backups), 14, false));
                    return;
                }
                for (int i = 0; i < backups.length(); i++) {
                    JSONObject backup = backups.optJSONObject(i);
                    if (backup == null) continue;
                    String name = backup.optString("name");
                    LinearLayout backupCard = cardColumn();
                    backupCard.addView(text(name, 15, true));
                    addPair(
                            backupCard, getString(R.string.backup_size),
                            formatFileSize(backup.optLong("size")));
                    long modified = Math.round(backup.optDouble("modified"));
                    if (modified > 0) {
                        String created = DateTimeFormatter.ofLocalizedDateTime(
                                        FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault())
                                .format(Instant.ofEpochSecond(modified)
                                        .atZone(ZoneId.systemDefault()));
                        addPair(
                                backupCard,
                                getString(R.string.backup_modified), created);
                    }
                    Button download = button(getString(R.string.download), GREEN);
                    download.setOnClickListener(v -> downloadBackup(name));
                    backupCard.addView(download);
                    target.addView(backupCard);
                }
            }

            @Override public void failure(String error) {
                target.removeAllViews();
                target.addView(text(localizedError(error), 14, true));
            }
        });
    }

    private void downloadBackup(String name) {
        api.download(
                "/backups/" + Uri.encode(name) + "/download",
                new ApiClient.DownloadCallback() {
                    @Override public void success(byte[] data) {
                        pendingBackupData = data;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/zip");
                        intent.putExtra(Intent.EXTRA_TITLE, name);
                        startActivityForResult(intent, REQUEST_SAVE_BACKUP);
                    }

                    @Override public void failure(String error) {
                        message(
                                getString(R.string.app_name),
                                localizedError(error));
                    }
                });
    }

    private String formatFileSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(
                    Locale.getDefault(), "%.1f MB",
                    bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(
                    Locale.getDefault(), "%.1f kB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private void renderFallback(Object data) {
        if (data instanceof JSONArray) {
            JSONArray array = (JSONArray) data;
            if (array.length() == 0) {
                content.addView(text(getString(R.string.no_data), 16, false));
            }
            for (int i = 0; i < array.length(); i++) {
                content.addView(cardText(String.valueOf(array.opt(i))));
            }
        } else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object item = object.opt(key);
                if (!(item instanceof JSONObject) && !(item instanceof JSONArray)) {
                    addStandalonePair(readable(key), String.valueOf(item));
                }
            }
        } else {
            content.addView(text(String.valueOf(data), 15, false));
        }
    }

    private void post(String path, JSONObject body, Runnable done) {
        api.request("POST", path, body, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                done.run();
            }

            @Override public void failure(String error) {
                message(getString(R.string.app_name), localizedError(error));
            }
        });
    }

    private void put(String path, JSONObject body, Runnable done) {
        api.request("PUT", path, body, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                done.run();
            }

            @Override public void failure(String error) {
                message(getString(R.string.app_name), localizedError(error));
            }
        });
    }

    private void confirmAction(String prompt, String path) {
        confirmAction(
                prompt, path,
                () -> toast(getString(R.string.accepted)));
    }

    private void confirmAction(String prompt, String path, Runnable done) {
        new AlertDialog.Builder(this)
                .setMessage(prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        post(path, new JSONObject(), done))
                .show();
    }

    private void confirmRemove(Installation installation) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_remove, installation.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        installationStore.remove(installation.id);
                        notifications.clearInstallation(installation.id);
                        PushRegistrationManager.clearLocalState(
                                this, installation.id);
                        NotificationScheduler.update(this, false);
                        showInstallations();
                    } catch (Exception error) {
                        message(
                                getString(R.string.protected_storage_error),
                                getString(R.string.protected_storage_error_detail));
                    }
                }).show();
    }

    private LinearLayout card() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(10), dp(9), dp(10), dp(9));
        value.setBackground(background(SURFACE, CARD_BORDER, 10));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, 0, 0, dp(8));
        value.setLayoutParams(layout);
        return value;
    }

    private LinearLayout cardColumn() {
        LinearLayout value = card();
        value.setOrientation(LinearLayout.VERTICAL);
        value.setGravity(Gravity.START);
        return value;
    }

    private LinearLayout statusCardContainer(String status) {
        LinearLayout value = cardColumn();
        String code = status == null
                ? "" : status.trim().toLowerCase(Locale.ROOT);
        if ("error".equals(code) || "critical".equals(code) ||
                "failed".equals(code) || "unhealthy".equals(code)) {
            value.setBackground(background(LIGHT_RED, RED, 10));
        } else if ("warning".equals(code) || "warn".equals(code) ||
                "degraded".equals(code) || "blocked".equals(code)) {
            value.setBackground(background(LIGHT_AMBER, AMBER, 10));
        } else if ("ok".equals(code) || "success".equals(code) ||
                "healthy".equals(code) || "good".equals(code) ||
                "running".equals(code) || "completed".equals(code) ||
                "online".equals(code)) {
            value.setBackground(background(LIGHT_GREEN, GREEN, 10));
        }
        return value;
    }

    private View statusCard(String heading, String detail, String status) {
        LinearLayout card = statusCardContainer(status);
        card.addView(text(heading, 16, true));
        if (detail != null && !detail.isEmpty()) {
            card.addView(text(detail, 14, false));
        }
        return card;
    }

    private View cardText(String value) {
        LinearLayout card = cardColumn();
        card.addView(text(value, 15, false));
        return card;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void heading(String value) {
        TextView view = text(value, 18, true);
        view.setTextColor(HEADING);
        view.setPadding(0, dp(10), 0, dp(5));
        content.addView(view);
    }

    private void line(String key, String value) {
        content.addView(text(key + ": " + value, 15, false));
    }

    private void addStandalonePair(String key, String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) return;
        LinearLayout row = cardColumn();
        addPair(row, key, value);
        content.addView(row);
    }

    private void addPair(LinearLayout parent, String key, String value) {
        LinearLayout row = actionRow();
        TextView label = text(key, 14, true);
        label.setTextColor(MUTED);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 0.42f));
        TextView contentValue = text(
                value == null || value.isEmpty() ? "—" : value, 14, false);
        row.addView(contentValue, new LinearLayout.LayoutParams(0, -2, 0.58f));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }



    private void showRainDelayDialog() {
        EditText hours = new EditText(this);
        hours.setInputType(
                InputType.TYPE_CLASS_NUMBER |
                        InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hours.setText("24");
        hours.setSelectAllOnFocus(true);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(24), dp(4), dp(24), 0);
        wrapper.addView(hours, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.rain_delay_hours)
                .setView(wrapper)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.set, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            double value;
                            try {
                                value = Double.parseDouble(
                                        hours.getText().toString()
                                                .trim().replace(',', '.'));
                            } catch (Exception error) {
                                hours.setError(
                                        getString(R.string.invalid_rain_delay));
                                return;
                            }
                            if (value <= 0 || value > 24 * 365) {
                                hours.setError(
                                        getString(R.string.invalid_rain_delay));
                                return;
                            }
                            JSONObject change = new JSONObject();
                            try {
                                change.put("rain_delay_hours", value);
                            } catch (Exception ignoredJson) {
                            }
                            put("/irrigation", change, () -> {
                                dialog.dismiss();
                                refreshCurrentOverview();
                            });
                        }));
        dialog.show();
    }

    private JSONObject jsonBoolean(String key, boolean value) {
        JSONObject result = new JSONObject();
        try {
            result.put(key, value);
        } catch (Exception ignored) {
        }
        return result;
    }

    private LinearLayout labelledInput(String label, EditText input) {
        return labelledView(label, input);
    }

    private LinearLayout labelledView(String label, View input) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(text(label, 13, true));
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    private EditText numericInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setText(value);
        input.setSingleLine(true);
        return input;
    }

    private int positiveInteger(EditText input) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    getString(R.string.program_positive_number_required));
        }
    }

    private int nonNegativeInteger(EditText input) {
        try {
            int value = Integer.parseInt(input.getText().toString().trim());
            if (value < 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    getString(R.string.program_non_negative_number_required));
        }
    }

    private static boolean jsonArrayContains(JSONArray values, int needle) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) {
            if (values.optInt(i, Integer.MIN_VALUE) == needle) return true;
        }
        return false;
    }

    private static boolean jsonArrayContains(JSONArray values, String needle) {
        if (values == null || needle == null) return false;
        for (int i = 0; i < values.length(); i++) {
            if (needle.equals(values.optString(i))) return true;
        }
        return false;
    }

    private static String minutesToTime(int minutes) {
        int normalized = Math.max(0, minutes) % (24 * 60);
        return String.format(
                Locale.ROOT, "%02d:%02d", normalized / 60, normalized % 60);
    }

    private static int timeToMinutes(String value) {
        String[] parts = value.trim().split(":");
        if (parts.length != 2) throw new IllegalArgumentException();
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException();
        }
        return hour * 60 + minute;
    }

    private String timelineState(String value) {
        if ("running".equals(value)) return getString(R.string.running);
        if ("completed".equals(value)) return getString(R.string.completed);
        if ("blocked".equals(value)) return getString(R.string.blocked);
        return getString(R.string.upcoming);
    }

    private static String shortDateTime(String value) {
        if (value == null || value.isEmpty()) return "—";
        String normalized = value.replace('T', ' ');
        return normalized.length() >= 16
                ? normalized.substring(0, 16) : normalized;
    }

    private static String shortTime(String value) {
        if (value == null || value.isEmpty()) return "—";
        String normalized = value.replace('T', ' ');
        return normalized.length() >= 16
                ? normalized.substring(11, 16) : normalized;
    }

    private String formatDuration(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600 + 59) / 60;
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        if (hours == 24) {
            days++;
            hours = 0;
        }
        if (days > 0) {
            String dayText = getResources().getQuantityString(
                    R.plurals.duration_days, (int) days, days);
            String hourText = getResources().getQuantityString(
                    R.plurals.duration_hours, (int) hours, hours);
            return getString(R.string.duration_join, dayText, hourText);
        }
        if (hours > 0) {
            String hourText = getResources().getQuantityString(
                    R.plurals.duration_hours, (int) hours, hours);
            String minuteText = getResources().getQuantityString(
                    R.plurals.duration_minutes, (int) minutes, minutes);
            return getString(R.string.duration_join, hourText, minuteText);
        }
        return getResources().getQuantityString(
                R.plurals.duration_minutes, (int) minutes, minutes);
    }

    private String formatCountdown(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (hours > 0) {
            parts.add(hours + " " + getString(R.string.hours_short));
        }
        if (minutes > 0 || hours > 0) {
            parts.add(minutes + " " + getString(R.string.minutes_short));
        }
        parts.add(remainder + " " + getString(R.string.seconds_short));
        return android.text.TextUtils.join(" ", parts);
    }

    private String programFieldValue(String kind, String key, Object value) {
        if ("start_minute".equals(key)) {
            return minutesToTime(value instanceof Number
                    ? ((Number) value).intValue() : 0);
        }
        if ("duration_minutes".equals(key) || "pause_minutes".equals(key)) {
            return String.valueOf(value) + " " + getString(R.string.minutes_short);
        }
        if ("days".equals(key) && value instanceof JSONArray) {
            JSONArray days = (JSONArray) value;
            List<String> labels = new ArrayList<>();
            for (int index = 0; index < days.length(); index++) {
                labels.add(weekdayName(days.optInt(index, -1)));
            }
            return android.text.TextUtils.join(", ", labels);
        }
        if (("intervals".equals(key) || "priority_intervals".equals(key)) &&
                value instanceof JSONArray) {
            JSONArray pairs = (JSONArray) value;
            List<String> labels = new ArrayList<>();
            boolean priority = "priority_intervals".equals(key);
            for (int index = 0; index < pairs.length(); index++) {
                JSONArray pair = pairs.optJSONArray(index);
                if (pair == null || pair.length() != 2) continue;
                String first = scheduleMinuteLabel(kind, pair.optInt(0));
                if (priority) {
                    labels.add(getString(
                            R.string.priority_time_value,
                            first, pair.optInt(1)));
                } else {
                    labels.add(getString(
                            R.string.interval_value,
                            first, scheduleMinuteLabel(kind, pair.optInt(1))));
                }
            }
            return labels.isEmpty()
                    ? getString(R.string.no_data)
                    : android.text.TextUtils.join("; ", labels);
        }
        if ("manual".equals(key) && value instanceof Boolean) {
            return state((Boolean) value);
        }
        if ("start".equals(key) || "start_date".equals(key)) {
            return formatTimestamp(String.valueOf(value));
        }
        return String.valueOf(value);
    }

    private String scheduleMinuteLabel(String kind, int minute) {
        int value = Math.max(0, minute);
        String time = minutesToTime(value);
        int day = value / 1440;
        if ("weekly_advanced".equals(kind) || "weekly_weather".equals(kind)) {
            return weekdayName(day % 7) + " " + time;
        }
        if (day > 0 || "custom".equals(kind)) {
            return getString(R.string.cycle_day_time, day + 1, time);
        }
        return time;
    }

    private String weekdayName(int day) {
        int[] labels = {R.string.weekday_monday_short,
                R.string.weekday_tuesday_short,
                R.string.weekday_wednesday_short,
                R.string.weekday_thursday_short,
                R.string.weekday_friday_short,
                R.string.weekday_saturday_short,
                R.string.weekday_sunday_short};
        return day >= 0 && day < labels.length
                ? getString(labels[day]) : getString(R.string.no_data);
    }

    private String formatTimestamp(String value) {
        if (value == null || value.isEmpty()) return getString(R.string.no_data);
        String formatted = value.replace('T', ' ');
        return formatted.length() >= 19
                ? formatted.substring(0, 19) : formatted;
    }

    private String sensorReading(JSONObject reading) {
        String status = reading.optString("status");
        if ("probe_error".equals(status)) return getString(R.string.probe_error);
        if ("pending".equals(status)) return getString(R.string.pending);
        if (!"ok".equals(status)) return getString(R.string.no_data);
        String stateCode = reading.optString("state");
        if (!stateCode.isEmpty()) return sensorState(stateCode);
        Object value = reading.opt("value");
        String unit = reading.optString("unit");
        return String.valueOf(value) + (unit.isEmpty() ? "" : " " + unit);
    }

    private String sensorState(String code) {
        if ("closed".equals(code)) return getString(R.string.closed);
        if ("open".equals(code)) return getString(R.string.open_state);
        if ("motion".equals(code)) return getString(R.string.motion);
        if ("no_motion".equals(code)) return getString(R.string.no_motion);
        return readable(code);
    }

    private String sensorType(JSONObject display) {
        String subtype = display.optString("subtype");
        String code = subtype.isEmpty() ? display.optString("type") : subtype;
        if ("temperature".equals(code) || code.startsWith("temperature_ds")) {
            return getString(R.string.temperature_sensor);
        }
        if ("ultrasonic".equals(code)) return getString(R.string.ultrasonic);
        if ("dry_contact".equals(code)) return getString(R.string.dry_contact);
        if ("leak_detector".equals(code)) {
            return getString(R.string.leak_detector);
        }
        if ("moisture".equals(code) || "soil_moisture".equals(code)) {
            return getString(R.string.moisture_sensor);
        }
        if ("motion".equals(code)) return getString(R.string.motion_sensor);
        return readable(code);
    }

    private String communicationType(String code) {
        if ("wifi_lan".equals(code)) return getString(R.string.wifi_lan);
        if ("radio".equals(code)) return getString(R.string.radio);
        return readable(code);
    }

    private void addPairIfPresent(
            LinearLayout parent, JSONObject object, String key, String label) {
        if (!object.has(key) || object.isNull(key)) return;
        String value = String.valueOf(object.opt(key));
        if (!value.isEmpty() && !"null".equals(value)) addPair(parent, label, value);
    }

    private void addBooleanPairIfPresent(
            LinearLayout parent, JSONObject object, String key, String label) {
        if (!object.has(key) || object.isNull(key)) return;
        addPair(parent, label, state(object.optBoolean(key)));
    }

    private TextView badge(String value, String status) {
        TextView badge = text(value, 12, true);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(Color.WHITE);
        String code = status == null
                ? "" : status.trim().toLowerCase(Locale.ROOT);
        int color = NAVY;
        if ("ok".equals(code) || "success".equals(code) ||
                "healthy".equals(code) || "good".equals(code) ||
                "running".equals(code) || "completed".equals(code) ||
                "online".equals(code)) {
            color = GREEN;
        } else if ("warning".equals(code) || "warn".equals(code) ||
                "degraded".equals(code) || "blocked".equals(code)) {
            color = AMBER;
        } else if ("error".equals(code) || "critical".equals(code) ||
                "failed".equals(code) || "unhealthy".equals(code) ||
                "offline".equals(code)) {
            color = RED;
        }
        badge.setBackground(background(color, color, 14));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
        layout.setMargins(dp(3), dp(2), dp(3), dp(2));
        badge.setLayoutParams(layout);
        badge.setPadding(dp(9), dp(4), dp(9), dp(4));
        return badge;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextColor(TEXT);
        view.setTextSize(size);
        view.setPadding(dp(5), dp(4), dp(5), dp(4));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label, int color) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextColor(Color.WHITE);
        value.setTextSize(13);
        value.setAllCaps(false);
        value.setMinHeight(dp(40));
        value.setMinimumHeight(0);
        value.setMinimumWidth(0);
        value.setPadding(dp(13), dp(6), dp(13), dp(6));
        styleButton(value, color, false);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
        layout.setMargins(dp(3), dp(3), dp(3), dp(3));
        value.setLayoutParams(layout);
        return value;
    }

    private Button compactButton(String label, int color) {
        Button value = button(label, color);
        value.setTextSize(12);
        value.setMinHeight(dp(38));
        value.setPadding(dp(11), dp(5), dp(11), dp(5));
        return value;
    }

    private void styleButton(Button button, int color, boolean selected) {
        button.setBackground(background(color, selected ? Color.WHITE : color, 20));
        button.setTypeface(
                Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(fill);
        value.setCornerRadius(dp(radius));
        value.setStroke(dp(1), stroke);
        return value;
    }

    private EditText input(String hint, boolean password) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(16);
        view.setTextColor(TEXT);
        view.setHintTextColor(MUTED);
        view.setSingleLine(true);
        view.setPadding(dp(8), dp(10), dp(8), dp(10));
        if (password) {
            view.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return view;
    }

    private String state(boolean enabled) {
        return getString(enabled ? R.string.on : R.string.off);
    }

    private String localizedStatus(String value) {
        if (value == null || value.isEmpty()) {
            return getString(R.string.unknown_status);
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "ok":
            case "success":
            case "healthy":
            case "good":
                return getString(R.string.ok_status);
            case "running":
                return getString(R.string.running);
            case "active":
                return getString(R.string.active);
            case "stopped":
            case "not_running":
                return getString(R.string.stopped);
            case "inactive":
                return getString(R.string.inactive);
            case "warning":
            case "warn":
            case "degraded":
                return getString(R.string.warning_status);
            case "error":
            case "critical":
            case "failed":
            case "unhealthy":
                return getString(R.string.error_status);
            case "pending":
                return getString(R.string.pending_status);
            case "accepted":
                return getString(R.string.accepted);
            case "completed":
                return getString(R.string.completed);
            case "enabled":
                return getString(R.string.enabled);
            case "disabled":
                return getString(R.string.disabled);
            case "unknown":
            case "not_reported":
                return getString(R.string.unknown_status);
            case "unavailable":
            case "not_available":
                return getString(R.string.not_available);
            case "online":
                return getString(R.string.online);
            case "offline":
                return getString(R.string.offline);
            default:
                return value;
        }
    }

    private String localizedError(String value) {
        if (value == null || value.isEmpty()) {
            return getString(R.string.request_failed);
        }
        String code = ApiClient.errorCode(value);
        String message = ApiClient.errorMessage(value);
        switch (code) {
            case "invalid_refresh_token":
            case "invalid_token":
            case "expired_token":
            case "token_expired":
                return getString(R.string.session_expired);
            case "insufficient_scope":
                return getString(R.string.permission_denied);
            case "not_found":
                return getString(R.string.item_not_found);
            case "invalid_group_postponement":
                return getString(R.string.invalid_group_postponement);
            case "group_postponement_not_found":
                return getString(R.string.group_postponement_not_found);
            case "invalid_station_duration":
                return getString(R.string.invalid_station_duration);
            default:
                if (code.startsWith("invalid_") || code.startsWith("missing_") ||
                        code.startsWith("unknown_") ||
                        code.startsWith("read_only_")) {
                    return getString(R.string.invalid_request);
                }
                if (!code.isEmpty()) return getString(R.string.request_failed);
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("certpathvalidatorexception") ||
                lower.contains("trust anchor") ||
                lower.contains("certificate_unknown")) {
            return getString(R.string.untrusted_certificate_error);
        }
        if (lower.contains("unable to resolve host") ||
                lower.contains("unknownhostexception") ||
                lower.contains("no address associated with hostname")) {
            return getString(R.string.unknown_host_error);
        }
        if (lower.contains("failed to connect") ||
                lower.contains("connectexception") ||
                lower.contains("connection refused") ||
                lower.contains("econnrefused")) {
            return getString(R.string.cannot_connect);
        }
        if (lower.contains("timed out") || lower.contains("timeout") ||
                lower.matches(".*after\\s+\\d+ms.*")) {
            return getString(R.string.connection_timeout);
        }
        if (lower.contains("internal server error") ||
                lower.equals("internal_error")) {
            return getString(R.string.request_failed);
        }
        return getString(R.string.request_failed);
    }

    private String localizedProgramSaveError(String value) {
        String code = ApiClient.errorCode(value);
        String reason = ApiClient.errorReason(value).toLowerCase(Locale.ROOT);
        if ("missing_program_fields".equals(code)) {
            return getString(R.string.program_definition_incomplete);
        }
        if ("invalid_program".equals(code)) {
            if (reason.contains("program group")) {
                return getString(R.string.program_group_invalid);
            }
            if (reason.contains("name")) {
                return getString(R.string.program_name_required);
            }
            if (reason.contains("station")) {
                return getString(R.string.program_station_invalid);
            }
            if (reason.contains("day")) {
                return getString(R.string.program_day_required);
            }
            if (reason.contains("time or duration") ||
                    reason.contains("repeating simple")) {
                return getString(R.string.program_time_values_invalid);
            }
            if (reason.contains("weather") || reason.contains("irrigation_")) {
                return getString(R.string.program_weather_values_invalid);
            }
            if (reason.contains("interval") || reason.contains("schedule")) {
                return getString(R.string.program_intervals_format);
            }
            if (reason.contains("start_date")) {
                return getString(R.string.program_date_format);
            }
            if (reason.contains("start")) {
                return getString(R.string.program_date_time_format);
            }
            if (reason.contains("modulo") || reason.contains("greater than zero")) {
                return getString(R.string.program_positive_number_required);
            }
            if (reason.contains("enabled") || reason.contains("manual")) {
                return getString(R.string.program_boolean_invalid);
            }
            if (reason.contains("program type")) {
                return getString(R.string.unsupported_program_type);
            }
            return getString(R.string.program_definition_rejected);
        }
        return localizedError(value);
    }

    private static String weatherIcon(String value) {
        if (value == null) return "☁";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("night") && lower.contains("clear")) return "☾";
        if (lower.contains("night")) return "☾☁";
        if (lower.contains("thunder")) return "⚡";
        if (lower.contains("snow")) return "❄";
        if (lower.contains("rain") || lower.contains("shower")) return "☂";
        if (lower.contains("fog") || lower.contains("mist")) return "≋";
        if (lower.contains("partly")) return "☀☁";
        if (lower.contains("clear")) return "☀";
        return "☁";
    }

    private String readable(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        switch (normalized) {
            case "days_simple":
                return getString(R.string.schedule_days);
            case "days_advanced":
                return getString(R.string.schedule_advanced);
            case "interval":
                return getString(R.string.schedule_interval);
            case "start_minute":
                return getString(R.string.start_time);
            case "duration_minutes":
                return getString(R.string.duration);
            case "pause_minutes":
                return getString(R.string.pause);
            case "repeat_count":
                return getString(R.string.repeat_count);
            case "days":
                return getString(R.string.days);
            case "enabled":
                return getString(R.string.enabled);
            case "automatic_update":
                return getString(R.string.automatic_update);
            case "update_available":
                return getString(R.string.update_available);
            case "checking":
                return getString(R.string.checking_updates);
            case "stable_release":
                return getString(R.string.stable_release);
            case "upstream_branch":
                return getString(R.string.upstream_branch);
            case "update_channel":
                return getString(R.string.update_channel);
            case "update_watchdog":
                return getString(R.string.update_watchdog);
            case "last_watchdog_result":
                return getString(R.string.last_watchdog_result);
            case "current_version":
                return getString(R.string.current_version);
            case "current_commit":
                return getString(R.string.current_commit);
            case "target_commit":
                return getString(R.string.target_commit);
        }
        String display = value.replace('_', ' ');
        return Character.toUpperCase(display.charAt(0)) + display.substring(1);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private void message(String heading, String value) {
        new AlertDialog.Builder(this)
                .setTitle(heading)
                .setMessage(value == null ? "" : value)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
