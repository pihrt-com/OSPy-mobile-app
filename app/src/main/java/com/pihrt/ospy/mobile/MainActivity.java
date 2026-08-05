package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.View;
import android.view.WindowInsets;
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
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(43, 138, 30);
    private static final int LIGHT_GREEN = Color.rgb(232, 244, 230);
    private static final int NAVY = Color.rgb(48, 59, 92);
    private static final int RED = Color.rgb(146, 27, 37);
    private static final int LIGHT_RED = Color.rgb(252, 232, 234);
    private static final int AMBER = Color.rgb(204, 132, 0);
    private static final int LIGHT_AMBER = Color.rgb(255, 245, 218);
    private static final int TEXT = Color.rgb(30, 30, 30);
    private static final int MUTED = Color.rgb(96, 102, 112);
    private static final long LIVE_REFRESH_MS = 10_000L;

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
    private boolean unlockStarted = false;

    // The overview is kept attached during background refreshes. Only values
    // that actually changed are updated, preventing the page and timeline from
    // disappearing and being recreated every ten seconds.
    private LinearLayout overviewRoot;
    private LinearLayout overviewIrrigationSection;
    private OverviewControlBinding overviewSchedulerControl;
    private OverviewControlBinding overviewManualControl;
    private OverviewControlBinding overviewRainControl;
    private PairBinding overviewActiveCount;
    private LinearLayout overviewActiveStationsContainer;
    private final List<PairBinding> overviewActiveStationRows = new ArrayList<>();
    private String overviewActiveStationStructure = "";
    private TextView overviewUpdatedView;
    private TextView overviewVersionView;
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
            if (current != null && !requestInFlight &&
                    ("overview".equals(currentRenderer) ||
                            "stations".equals(currentRenderer))) {
                fetch(currentPath, currentRenderer, false, loadGeneration);
            }
            refreshHandler.postDelayed(this, LIVE_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(GREEN);
        getWindow().setNavigationBarColor(NAVY);
        installationStore = new InstallationStore(this);
        appPreferences = new AppPreferences(this);
        notifications = new NotificationCenter(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                notifications.isEnabled() &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        } else {
            unlock();
        }
        refreshHandler.postDelayed(refreshTask, LIVE_REFRESH_MS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10) unlock();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTask);
        if (liveUpdates != null) liveUpdates.stop();
        super.onDestroy();
    }

    private void unlock() {
        if (unlockStarted) return;
        unlockStarted = true;
        if (Build.VERSION.SDK_INT < 28) {
            afterUnlock();
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            afterUnlock();
            return;
        }
        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.unlock))
                .setSubtitle(getString(R.string.unlock_subtitle));
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setDeviceCredentialAllowed(true);
        } else {
            builder.setNegativeButton(
                    getString(android.R.string.cancel), getMainExecutor(),
                    (dialog, which) -> finish());
        }
        builder.build().authenticate(
                new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        afterUnlock();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence message) {
                        if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                            toast(message.toString());
                        }
                    }
                });
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
        openFirstReachable(candidates, 0);
    }

    private void openFirstReachable(List<Installation> candidates, int index) {
        if (index >= candidates.size()) {
            showInstallations();
            toast(getString(R.string.no_reachable_installation));
            return;
        }
        Installation candidate = candidates.get(index);
        new ApiClient(candidate, installationStore).probe(new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                open(candidate);
            }

            @Override public void failure(String error) {
                openFirstReachable(candidates, index + 1);
            }
        });
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

    private void shell(String heading) {
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(244, 246, 248));

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(10), dp(12), dp(10));
        toolbar.setBackgroundColor(GREEN);
        title = text(heading, 22, true);
        title.setTextColor(Color.WHITE);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
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

        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(24));
        contentScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);

        page.setOnApplyWindowInsetsListener((view, insets) -> {
            toolbar.setPadding(
                    dp(16), dp(10) + insets.getSystemWindowInsetTop(),
                    dp(12), dp(10));
            content.setPadding(
                    dp(12), dp(10), dp(12),
                    dp(24) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        page.requestApplyInsets();
    }

    private void showInstallations() {
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
        currentRenderer = "";
        activeRequest = ++requestSequence;
        requestInFlight = false;
        shell(getString(R.string.app_settings));

        LinearLayout notificationSettings = card();
        notificationSettings.addView(
                text(getString(R.string.notifications), 16, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button notificationToggle = button(
                getString(notifications.isEnabled()
                        ? R.string.enabled : R.string.disabled),
                notifications.isEnabled() ? GREEN : NAVY);
        notificationToggle.setOnClickListener(v -> {
            boolean enabled = !notifications.isEnabled();
            notifications.setEnabled(enabled);
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            }
            showAppSettings();
        });
        notificationSettings.addView(notificationToggle);
        content.addView(notificationSettings);

        LinearLayout networkSettings = cardColumn();
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

        Button back = button(getString(R.string.back), RED);
        back.setOnClickListener(v -> {
            if (current == null) showInstallations();
            else showDashboard();
        });
        content.addView(back);
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
        currentPath = "";
        currentRenderer = "";
        shell(getString(R.string.add_installation));
        EditText url = input(getString(R.string.server_url), false);
        url.setText("https://");
        EditText user = input(getString(R.string.username), false);
        EditText password = input(getString(R.string.password), true);
        EditText secondFactor = input(getString(R.string.two_factor), false);
        CheckBox unverified = new CheckBox(this);
        unverified.setText(getString(R.string.unverified_certificate));
        unverified.setTextSize(15);
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

        LinearLayout actions = actionRow();
        Button connect = button(getString(R.string.connect), GREEN);
        connect.setOnClickListener(v -> {
            String base = Installation.normalize(url.getText().toString());
            if (!(base.startsWith("https://") || base.startsWith("http://"))) {
                toast(getString(R.string.complete_address));
                return;
            }
            connect.setEnabled(false);
            boolean allowUnverified = base.startsWith("https://") &&
                    unverified.isChecked();
            if (pairingInstallation == null ||
                    !pairingInstallation.baseUrl.equals(base) ||
                    pairingInstallation.allowUnverifiedCertificate != allowUnverified) {
                pairingInstallation = new Installation(
                        UUID.randomUUID().toString(), base, base,
                        user.getText().toString().trim(), "", allowUnverified);
                pairingApi = new ApiClient(pairingInstallation, installationStore);
            }
            pairingApi.pair(
                    user.getText().toString().trim(),
                    password.getText().toString(),
                    secondFactor.getText().toString(),
                    new ApiClient.Callback() {
                        @Override public void success(JSONObject response) {
                            installations.add(pairingInstallation);
                            open(pairingInstallation);
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
        unverified.setChecked(installation.allowUnverifiedCertificate);
        content.addView(name);
        content.addView(url);
        content.addView(unverified);

        LinearLayout actions = actionRow();
        Button save = button(getString(R.string.save), GREEN);
        save.setOnClickListener(v -> {
            String base = Installation.normalize(url.getText().toString());
            if (!(base.startsWith("https://") || base.startsWith("http://"))) {
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
                installationStore.upsert(changed);
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
        appPreferences.setLastInstallationId(installation.id);
        current = installation;
        api = new ApiClient(installation, installationStore);
        showDashboard();
        if (liveUpdates != null) liveUpdates.stop();
        liveUpdates = new LiveUpdates(api, event -> {
            if ("notification".equals(event.optString("event"))) {
                JSONObject data = event.optJSONObject("data");
                if (data != null) notifications.show(
                        data.optInt("id", (int) System.currentTimeMillis()),
                        data.optString("title", getString(R.string.app_name)),
                        data.optString("message", ""));
            }
            String type = event.optString("event");
            if (type.startsWith("station.") ||
                    "stations.changed".equals(type) ||
                    "conditions.changed".equals(type) ||
                    type.startsWith("program.") ||
                    "plugin.action".equals(type)) {
                if (!requestInFlight && currentPath != null &&
                        !currentPath.isEmpty()) {
                    fetch(currentPath, currentRenderer, false, loadGeneration);
                }
            }
        });
        liveUpdates.start();
    }

    private void showDashboard() {
        shell(current.name);
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

    private void renderOverview(JSONObject data) {
        ensureOverviewLayout();

        JSONObject instance = data.optJSONObject("instance");
        JSONObject irrigation = data.optJSONObject("irrigation");
        if (irrigation == null) {
            overviewIrrigationSection.setVisibility(View.GONE);
        } else {
            overviewIrrigationSection.setVisibility(View.VISIBLE);
            boolean schedulerEnabled =
                    irrigation.optBoolean("scheduler_enabled");
            boolean manualMode = irrigation.optBoolean("manual_mode");
            boolean rainBlock = irrigation.optBoolean("rain_block");

            updateOverviewToggle(
                    overviewSchedulerControl, schedulerEnabled,
                    "scheduler_enabled");
            updateOverviewToggle(
                    overviewManualControl, manualMode, "manual_mode");
            updateOverviewRainControl(
                    rainBlock, irrigation.optLong("rain_block_seconds", 0));
            updateOverviewActiveStations(
                    irrigation.optJSONArray("active_stations"));
        }

        String updated = data.optString("updated");
        updateOverviewServerDate(updated);
        overviewUpdatedView.setText(
                getString(R.string.updated) + ": " +
                        formatTimestamp(updated));

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
        overviewActiveCount = null;
        overviewActiveStationsContainer = null;
        overviewActiveStationRows.clear();
        overviewActiveStationStructure = "";
        overviewUpdatedView = null;
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
        overviewSchedulerControl = createOverviewControl(
                summary, getString(R.string.scheduler), 0.25f);
        overviewManualControl = createOverviewControl(
                summary, getString(R.string.manual_mode), 0.25f);
        overviewRainControl = createOverviewControl(
                summary, getString(R.string.rain_delay), 0.35f);
        overviewActiveCount = createPairBinding(
                summary, getString(R.string.active_stations));
        overviewActiveStationsContainer = new LinearLayout(this);
        overviewActiveStationsContainer.setOrientation(LinearLayout.VERTICAL);
        summary.addView(
                overviewActiveStationsContainer,
                new LinearLayout.LayoutParams(-1, -2));
        overviewIrrigationSection.addView(summary);
        overviewRoot.addView(overviewIrrigationSection);

        overviewUpdatedView = text("", 15, false);
        overviewRoot.addView(overviewUpdatedView);

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

        overviewVersionView = text("", 11, false);
        overviewVersionView.setTextColor(MUTED);
        overviewVersionView.setGravity(Gravity.END);
        overviewRoot.addView(
                overviewVersionView,
                new LinearLayout.LayoutParams(-1, -2));

        LinearLayout timeline = cardColumn();
        overviewTimelineNow = text("", 14, true);
        overviewTimelineNow.setTextColor(NAVY);
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
        if (selectedScheduleDay == ScheduleDay.TODAY) {
            return "/schedule?date=today";
        }
        return "/schedule?date=" + selectedScheduleDate();
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
        view.setTextColor(NAVY);
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

    private void updateOverviewToggle(
            OverviewControlBinding binding, boolean enabled, String key) {
        binding.value.setText(state(enabled));
        binding.action.setText(getString(
                enabled ? R.string.turn_off : R.string.turn_on));
        styleButton(binding.action, enabled ? RED : GREEN, false);
        binding.action.setOnClickListener(v -> put(
                "/irrigation", jsonBoolean(key, !enabled),
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
        return station.optInt("number", -1) + "|" +
                station.optString("name");
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
        for (int i = 0; i < stationItems.length(); i++) {
            JSONObject station = stationItems.optJSONObject(i);
            if (station == null) continue;
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
                    !station.optBoolean("is_master_two")) {
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

    private void renderPrograms(JSONArray programItems) {
        for (int i = 0; i < programItems.length(); i++) {
            JSONObject program = programItems.optJSONObject(i);
            if (program == null) continue;
            LinearLayout card = cardColumn();
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
            run.setEnabled(enabled);
            run.setAlpha(enabled ? 1.0f : 0.45f);
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
            card.addView(actions);
            card.addView(details);
            content.addView(card);
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
                JSONObject data = response.optJSONObject("data");
                JSONArray items = data == null ? null : data.optJSONArray("items");
                updateOverviewTimeline(items);
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

    private void updateOverviewTimeline(JSONArray items) {
        List<JSONObject> completed = new ArrayList<>();
        List<JSONObject> currentAndNext = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || item.optBoolean("is_master")) continue;
                String itemState = item.optString("state");
                if ("completed".equals(itemState)) completed.add(item);
                else currentAndNext.add(item);
            }
        }

        List<JSONObject> visible = new ArrayList<>();
        if (selectedScheduleDay == ScheduleDay.TODAY) {
            // Keep Home compact for the live current day: the last two
            // completed runs, all running rows and the nearest future rows.
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
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null || item.optBoolean("is_master")) continue;
                    visible.add(item);
                }
            }
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
                        getString(R.string.no_scheduled_runs), 14, false));
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
            binding.detail.setText(item.optString(
                    "blocked_reason", getString(R.string.rain_delay)));
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
                            programFieldValue(key, fields.opt(key)));
                }
            }
        }
        return details;
    }

    private void loadProgramEditor(JSONObject program) {
        api.request("GET", "/stations", null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                JSONArray allStations = response.optJSONArray("data");
                showProgramEditor(
                        program, allStations == null ? new JSONArray() : allStations);
            }

            @Override public void failure(String error) {
                message(getString(R.string.app_name), localizedError(error));
            }
        });
    }

    private void showProgramEditor(
            JSONObject program, JSONArray allStations) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(4), dp(18), dp(4));
        EditText name = input(getString(R.string.program), false);
        name.setText(program.optString("name"));
        form.addView(name);

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
        JSONObject fields = program.optJSONObject("editor") == null
                ? null : program.optJSONObject("editor").optJSONObject("fields");
        int type = program.optInt("type", -1);
        EditText start = null;
        EditText duration = null;
        EditText pause = null;
        EditText repeats = null;
        List<CheckBox> dayChecks = new ArrayList<>();
        EditText advanced = null;
        if ((type == 0 || type == 2) && fields != null) {
            start = input(getString(R.string.start_time), false);
            start.setInputType(InputType.TYPE_CLASS_DATETIME);
            start.setText(minutesToTime(fields.optInt("start_minute")));
            form.addView(labelledInput(getString(R.string.start_time), start));
            duration = numericInput(String.valueOf(
                    fields.optInt("duration_minutes", 1)));
            form.addView(labelledInput(getString(R.string.duration), duration));
            pause = numericInput(String.valueOf(
                    fields.optInt("pause_minutes", 0)));
            form.addView(labelledInput(getString(R.string.pause), pause));
            repeats = numericInput(String.valueOf(
                    fields.optInt("repeat_count", 0)));
            form.addView(labelledInput(getString(R.string.repeat_count), repeats));
            if (type == 0) {
                GridLayout days = new GridLayout(this);
                days.setColumnCount(4);
                days.setRowCount(2);
                JSONArray chosenDays = fields.optJSONArray("days");
                for (int day = 0; day < 7; day++) {
                    CheckBox check = new CheckBox(this);
                    check.setText(weekdayName(day));
                    check.setTag(day);
                    check.setChecked(jsonArrayContains(chosenDays, day));
                    dayChecks.add(check);
                    GridLayout.LayoutParams dayParams = new GridLayout.LayoutParams();
                    dayParams.width = 0;
                    dayParams.columnSpec = GridLayout.spec(day % 4, 1, 1f);
                    dayParams.rowSpec = GridLayout.spec(day / 4);
                    days.addView(check, dayParams);
                }
                form.addView(text(getString(R.string.days), 14, true));
                form.addView(days);
            }
        } else {
            advanced = new EditText(this);
            advanced.setText(
                    originalTypeData == null ? "[]" : originalTypeData.toString());
            advanced.setMinLines(3);
            advanced.setGravity(Gravity.TOP);
            form.addView(labelledInput(
                    getString(R.string.advanced_schedule_data), advanced));
        }
        scroll.addView(form);

        final EditText startField = start;
        final EditText durationField = duration;
        final EditText pauseField = pause;
        final EditText repeatsField = repeats;
        final EditText advancedField = advanced;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(program.optString("name"))
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
                                JSONArray typeData;
                                if (startField != null) {
                                    typeData = new JSONArray(
                                            originalTypeData == null
                                                    ? "[]" : originalTypeData.toString());
                                    while (typeData.length() < (type == 0 ? 5 : 6)) {
                                        typeData.put(0);
                                    }
                                    typeData.put(0, timeToMinutes(
                                            startField.getText().toString()));
                                    typeData.put(1, positiveInteger(durationField));
                                    typeData.put(2, nonNegativeInteger(pauseField));
                                    typeData.put(3, nonNegativeInteger(repeatsField));
                                    if (type == 0) {
                                        JSONArray chosen = new JSONArray();
                                        for (CheckBox check : dayChecks) {
                                            if (check.isChecked()) {
                                                chosen.put(check.getTag());
                                            }
                                        }
                                        if (chosen.length() == 0) {
                                            throw new IllegalArgumentException();
                                        }
                                        typeData.put(4, chosen);
                                    }
                                } else {
                                    typeData = new JSONArray(
                                            advancedField.getText().toString());
                                }
                                JSONObject payload = new JSONObject();
                                payload.put("name", name.getText().toString().trim());
                                payload.put("stations", stations);
                                payload.put("type", type);
                                payload.put("type_data", typeData);
                                put(
                                        "/programs/" + program.optString("id"),
                                        payload, () -> {
                                            dialog.dismiss();
                                            load("/programs", "programs");
                                        });
                            } catch (Exception error) {
                                toast(getString(R.string.invalid_program_data));
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
        loadPluginMobileRange(plugin, parent, trigger, "today", null, null);
    }

    private void loadPluginMobileRange(
            JSONObject plugin, LinearLayout parent, Button trigger,
            String selectedRange, LocalDateTime customFrom,
            LocalDateTime customTo) {
        trigger.setEnabled(false);
        trigger.setText(getString(R.string.loading));
        LinearLayout mobileContent;
        Object existing = trigger.getTag();
        if (existing instanceof LinearLayout) {
            mobileContent = (LinearLayout) existing;
            mobileContent.removeAllViews();
            mobileContent.setVisibility(View.VISIBLE);
        } else {
            mobileContent = cardColumn();
            trigger.setTag(mobileContent);
            parent.addView(mobileContent);
        }
        mobileContent.addView(text(getString(R.string.loading), 14, false));

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
        api.request(
                "GET", path,
                null, new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        trigger.setEnabled(true);
                        trigger.setText(getString(R.string.collapse));
                        mobileContent.removeAllViews();
                        trigger.setOnClickListener(v -> {
                            boolean visible =
                                    mobileContent.getVisibility() == View.VISIBLE;
                            mobileContent.setVisibility(
                                    visible ? View.GONE : View.VISIBLE);
                            trigger.setText(getString(
                                    visible ? R.string.expand : R.string.collapse));
                        });
                        JSONObject data = response.optJSONObject("data");
                        if (data == null) {
                            mobileContent.addView(text(
                                    getString(R.string.no_mobile_data), 14, false));
                            return;
                        }
                        JSONObject status = data.optJSONObject("status");
                        if (status != null) {
                            LinearLayout statusCard = statusCardContainer(
                                    status.optString("status", "ok"));
                            statusCard.addView(text(
                                    status.optString(
                                            "title",
                                            getString(R.string.operating_data)),
                                    15, true));
                            addPairIfPresent(
                                    statusCard, status, "summary",
                                    getString(R.string.status));
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
                            if (candidate != null && candidate.has("series")) {
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
                            JSONArray series = mobileCard.optJSONArray("series");
                            if (hasSeriesPoints(series)) {
                                card.addView(new MobileChartView(
                                        MainActivity.this, series));
                            } else if (series != null) {
                                TextView empty = text(
                                        getString(R.string.no_data_period),
                                        13, false);
                                empty.setTextColor(MUTED);
                                card.addView(empty);
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
                        trigger.setEnabled(true);
                        trigger.setText(getString(R.string.mobile_data));
                        message(getString(R.string.app_name), localizedError(error));
                    }
                });
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
            default:
                return card.optString(
                        "title", getString(R.string.operating_data));
        }
    }

    private String mobileMetricLabel(JSONObject metric) {
        String id = metric.optString("id");
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
            case "station_current":
                return metric.optString(
                        "label", getString(R.string.station_consumption));
            default:
                if (id.startsWith("master_") && id.endsWith("_current")) {
                    return getString(R.string.current_master_consumption);
                }
                if (id.startsWith("master_") && id.endsWith("_total")) {
                    return getString(R.string.total_master_consumption);
                }
                return metric.optString("label", id);
        }
    }

    private String mobileMetricValue(JSONObject metric) {
        String id = metric.optString("id");
        String value = String.valueOf(metric.opt("value"));
        if ("trend".equals(id)) {
            switch (value.toLowerCase(Locale.ROOT)) {
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
        return value;
    }

    private void addMobileImage(LinearLayout parent, JSONObject imageData) {
        try {
            byte[] bytes = Base64.decode(
                    imageData.optString("data_base64"), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;
            ImageView image = new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription(getString(R.string.radar_image));
            parent.addView(image, new LinearLayout.LayoutParams(-1, -2));
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
            addPairIfPresent(card, event, "status", getString(R.string.status));
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
        for (int i = 0; i < pluginItems.length(); i++) {
            JSONObject plugin = pluginItems.optJSONObject(i);
            if (plugin == null) continue;
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
            content.addView(card);
        }
    }

    private void renderSystem(Object value) {
        JSONObject root = value instanceof JSONObject
                ? (JSONObject) value : new JSONObject();
        JSONObject ospy = root.optJSONObject("ospy");
        if (ospy != null) {
            LinearLayout card = cardColumn();
            card.addView(text(getString(R.string.app_name), 19, true));
            addPairIfPresent(card, ospy, "status", getString(R.string.status));
            addPairIfPresent(card, ospy, "summary", getString(R.string.details));
            JSONObject details = ospy.optJSONObject("details");
            if (details != null) {
                addPairIfPresent(
                        card, details, "current_version",
                        getString(R.string.current_version));
                addPairIfPresent(
                        card, details, "current_commit",
                        getString(R.string.current_commit));
                addPairIfPresent(
                        card, details, "target_commit",
                        getString(R.string.target_commit));
                addPairIfPresent(
                        card, details, "stable_release",
                        getString(R.string.stable_release));
                addPairIfPresent(
                        card, details, "update_channel",
                        getString(R.string.update_channel));
                addBooleanPairIfPresent(
                        card, details, "automatic_update",
                        getString(R.string.automatic_update));
                addBooleanPairIfPresent(
                        card, details, "update_available",
                        getString(R.string.update_available));
            }
            content.addView(card);
        } else {
            content.addView(statusCard(
                    getString(R.string.app_name),
                    getString(R.string.no_data), "warning"));
        }
        heading(getString(R.string.actions));
        LinearLayout actions = actionRow();
        Button check = button(getString(R.string.check_updates), GREEN);
        check.setOnClickListener(v -> post(
                "/updates/actions/check", new JSONObject(),
                () -> toast(getString(R.string.accepted))));
        actions.addView(check);
        Button backup = button(getString(R.string.create_backup), NAVY);
        backup.setOnClickListener(v -> post(
                "/backups", new JSONObject(),
                () -> toast(getString(R.string.accepted))));
        actions.addView(backup);
        content.addView(actions);
        LinearLayout systemActions = actionRow();
        Button restart = button(getString(R.string.restart_ospy), RED);
        restart.setOnClickListener(v -> confirmAction(
                getString(R.string.confirm_restart),
                "/system/actions/restart-ospy"));
        systemActions.addView(restart);
        content.addView(systemActions);
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
        value.setBackground(background(Color.WHITE, NAVY, 10));
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
        if ("error".equals(status) || "critical".equals(status)) {
            value.setBackground(background(LIGHT_RED, RED, 10));
        } else if ("warning".equals(status)) {
            value.setBackground(background(LIGHT_AMBER, AMBER, 10));
        } else {
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
        view.setTextColor(NAVY);
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
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(text(label, 13, true));
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    private EditText numericInput(String value) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        input.setSingleLine(true);
        return input;
    }

    private int positiveInteger(EditText input) {
        int value = Integer.parseInt(input.getText().toString().trim());
        if (value <= 0) throw new IllegalArgumentException();
        return value;
    }

    private int nonNegativeInteger(EditText input) {
        int value = Integer.parseInt(input.getText().toString().trim());
        if (value < 0) throw new IllegalArgumentException();
        return value;
    }

    private static boolean jsonArrayContains(JSONArray values, int needle) {
        if (values == null) return false;
        for (int i = 0; i < values.length(); i++) {
            if (values.optInt(i, Integer.MIN_VALUE) == needle) return true;
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

    private String programFieldValue(String key, Object value) {
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
        return String.valueOf(value);
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
        int color = NAVY;
        if ("ok".equals(status) || "success".equals(status) ||
                "running".equals(status)) {
            color = GREEN;
        } else if ("warning".equals(status) || "blocked".equals(status)) {
            color = AMBER;
        } else if ("error".equals(status) || "critical".equals(status)) {
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
        if (value == null) return "";
        switch (value.toLowerCase(Locale.ROOT)) {
            case "ok":
            case "success":
                return getString(R.string.ok_status);
            case "running":
                return getString(R.string.running);
            case "stopped":
            case "not_running":
                return getString(R.string.stopped);
            case "warning":
                return getString(R.string.warning_status);
            case "error":
            case "critical":
                return getString(R.string.error_status);
            default:
                return value;
        }
    }

    private String localizedError(String value) {
        if (value == null || value.isEmpty()) {
            return getString(R.string.request_failed);
        }
        String lower = value.toLowerCase(Locale.ROOT);
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
        if (lower.contains("internal server error") ||
                lower.equals("internal_error")) {
            return getString(R.string.request_failed);
        }
        return value;
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
        switch (value.toLowerCase(Locale.ROOT)) {
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
