package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
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
            message(getString(R.string.protected_storage_error), error.getMessage());
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
                    displayName.isEmpty() ? "OSPy" : displayName,
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
                        error.getMessage());
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
                        data.optString("title", "OSPy"),
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
                content.removeAllViews();
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
                content.addView(text(localizedError(error), 16, true));
                Button retry = button(getString(R.string.refresh), GREEN);
                retry.setOnClickListener(v -> load(path, renderer));
                content.addView(retry);
            }
        });
    }

    private void renderOverview(JSONObject data) {
        JSONObject instance = data.optJSONObject("instance");
        JSONObject irrigation = data.optJSONObject("irrigation");
        if (irrigation != null) {
            heading(getString(R.string.irrigation));
            LinearLayout summary = cardColumn();
            boolean schedulerEnabled =
                    irrigation.optBoolean("scheduler_enabled");
            boolean manualMode = irrigation.optBoolean("manual_mode");
            boolean rainBlock = irrigation.optBoolean("rain_block");
            addIrrigationControl(
                    summary, getString(R.string.scheduler), schedulerEnabled,
                    jsonBoolean("scheduler_enabled", !schedulerEnabled));
            addIrrigationControl(
                    summary, getString(R.string.manual_mode), manualMode,
                    jsonBoolean("manual_mode", !manualMode));
            addRainDelayControl(
                    summary, rainBlock,
                    irrigation.optLong("rain_block_seconds", 0));
            JSONArray active = irrigation.optJSONArray("active_stations");
            addPair(summary, getString(R.string.active_stations),
                    active == null ? "0" : String.valueOf(active.length()));
            if (active != null) {
                for (int i = 0; i < active.length(); i++) {
                    JSONObject station = active.optJSONObject(i);
                    if (station != null) {
                        int remaining = station.optInt("remaining_seconds", -1);
                        addPair(
                                summary,
                                station.optString("name"),
                                remaining >= 0
                                        ? remaining + " " +
                                                getString(R.string.seconds_short)
                                        : getString(R.string.running));
                    }
                }
            }
            content.addView(summary);
        }
        line(
                getString(R.string.updated),
                formatTimestamp(data.optString("updated")));
        Button stop = button(getString(R.string.stop_all), RED);
        stop.setOnClickListener(v -> confirmAction(
                getString(R.string.confirm_stop_all),
                "/stations/actions/stop-all",
                () -> load("/overview", "overview")));
        content.addView(stop);
        heading(getString(R.string.today_schedule));
        loadTimeline("/schedule?date=today");
        JSONArray warnings = data.optJSONArray("warnings");
        if (warnings != null && warnings.length() > 0) {
            heading(getString(R.string.warnings));
            for (int i = 0; i < warnings.length(); i++) {
                JSONObject warning = warnings.optJSONObject(i);
                if (warning != null) {
                    content.addView(statusCard(
                            getString(R.string.warnings),
                            warning.optString("message"), "warning"));
                }
            }
        }
        if (instance != null) {
            TextView version = text(
                    getString(R.string.version) + ": " +
                            instance.optString("version"),
                    11, false);
            version.setTextColor(MUTED);
            version.setGravity(Gravity.END);
            content.addView(version, new LinearLayout.LayoutParams(-1, -2));
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
                    ? " · " + remaining + " " +
                            getString(R.string.seconds_short)
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
        final int generation = loadGeneration;
        api.request("GET", path, null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                if (generation != loadGeneration ||
                        !"overview".equals(currentRenderer)) return;
                JSONObject data = response.optJSONObject("data");
                JSONArray items = data == null ? null : data.optJSONArray("items");
                if (items == null || items.length() == 0) {
                    content.addView(text(
                            getString(R.string.no_scheduled_runs), 14, false));
                    return;
                }
                List<JSONObject> completed = new ArrayList<>();
                List<JSONObject> currentAndNext = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null || item.optBoolean("is_master")) continue;
                    String state = item.optString("state");
                    if ("completed".equals(state)) completed.add(item);
                    else currentAndNext.add(item);
                }
                LinearLayout timeline = cardColumn();
                TextView now = text(
                        getString(R.string.now) + " " +
                                new java.text.SimpleDateFormat(
                                        "HH:mm", Locale.getDefault())
                                        .format(new java.util.Date()),
                        14, true);
                now.setTextColor(NAVY);
                timeline.addView(now);
                int completedStart = Math.max(0, completed.size() - 2);
                for (int i = completedStart; i < completed.size(); i++) {
                    addTimelineRow(timeline, completed.get(i));
                }
                int upcomingLimit = Math.min(currentAndNext.size(), 5);
                for (int i = 0; i < upcomingLimit; i++) {
                    addTimelineRow(timeline, currentAndNext.get(i));
                }
                if (completedStart == completed.size() && upcomingLimit == 0) {
                    timeline.addView(text(
                            getString(R.string.no_scheduled_runs), 14, false));
                }
                content.addView(timeline);
            }

            @Override public void failure(String error) {
                if (generation == loadGeneration &&
                        "overview".equals(currentRenderer)) {
                    content.addView(statusCard(
                            getString(R.string.schedule),
                            localizedError(error), "warning"));
                }
            }
        });
    }

    private void addTimelineRow(LinearLayout parent, JSONObject item) {
        String state = item.optString("state");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(5), dp(7), dp(5), dp(7));
        LinearLayout header = actionRow();
        String interval = shortTime(item.optString("start")) + "\u2013" +
                shortTime(item.optString("end"));
        header.addView(text(
                        interval + "  " + item.optInt("station_number") + ". " +
                                item.optString("station_name"), 14, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(badge(timelineState(state), state));
        row.addView(header);
        if ("running".equals(state)) {
            int percent = (int) Math.round(100 * item.optDouble("progress", 0));
            TextView remaining = text(
                    getString(R.string.remaining) + ": " +
                            formatDuration(item.optLong("remaining_seconds", 0)) +
                            " \u00b7 " + percent + " %", 12, false);
            remaining.setTextColor(MUTED);
            row.addView(remaining);
            ProgressBar progress = new ProgressBar(
                    this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(percent);
            row.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        } else if ("blocked".equals(state)) {
            TextView reason = text(
                    item.optString(
                            "blocked_reason",
                            getString(R.string.rain_delay)), 12, false);
            reason.setTextColor(AMBER);
            row.addView(reason);
        }
        parent.addView(row);
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
                            String.valueOf(fields.opt(key)));
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
                message("OSPy", localizedError(error));
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
                LinearLayout days = actionRow();
                JSONArray chosenDays = fields.optJSONArray("days");
                String[] labels = new DateFormatSymbols().getShortWeekdays();
                for (int day = 0; day < 7; day++) {
                    CheckBox check = new CheckBox(this);
                    check.setText(labels[((day + 1) % 7) + 1]);
                    check.setTag(day);
                    check.setChecked(jsonArrayContains(chosenDays, day));
                    dayChecks.add(check);
                    days.addView(check);
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
        trigger.setEnabled(false);
        trigger.setText(getString(R.string.loading));
        LinearLayout mobileContent = cardColumn();
        api.request(
                "GET", "/plugins/" + plugin.optString("id") + "/mobile",
                null, new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        trigger.setEnabled(true);
                        trigger.setText(getString(R.string.collapse));
                        parent.addView(mobileContent);
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
                            if (series != null && series.length() > 0) {
                                card.addView(new MobileChartView(
                                        MainActivity.this, series));
                            }
                            mobileContent.addView(card);
                        }
                    }

                    @Override public void failure(String error) {
                        trigger.setEnabled(true);
                        trigger.setText(getString(R.string.mobile_data));
                        message("OSPy", localizedError(error));
                    }
                });
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
            card.addView(text("OSPy", 19, true));
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
                    "OSPy", getString(R.string.no_data), "warning"));
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
                message("OSPy", localizedError(error));
            }
        });
    }

    private void put(String path, JSONObject body, Runnable done) {
        api.request("PUT", path, body, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                done.run();
            }

            @Override public void failure(String error) {
                message("OSPy", localizedError(error));
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
                                error.getMessage());
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

    private void addIrrigationControl(
            LinearLayout parent, String label, boolean enabled,
            JSONObject change) {
        LinearLayout row = actionRow();
        TextView name = text(label, 14, true);
        name.setTextColor(MUTED);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 0.38f));
        TextView currentState = text(state(enabled), 14, false);
        row.addView(
                currentState, new LinearLayout.LayoutParams(0, -2, 0.25f));
        Button toggle = compactButton(
                getString(enabled ? R.string.turn_off : R.string.turn_on),
                enabled ? RED : GREEN);
        toggle.setOnClickListener(v -> put(
                "/irrigation", change,
                () -> load("/overview", "overview")));
        row.addView(toggle);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addRainDelayControl(
            LinearLayout parent, boolean enabled, long remainingSeconds) {
        LinearLayout row = actionRow();
        TextView name = text(getString(R.string.rain_delay), 14, true);
        name.setTextColor(MUTED);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 0.38f));

        String value = enabled
                ? getString(
                        R.string.rain_delay_remaining,
                        formatDuration(remainingSeconds))
                : getString(R.string.inactive);
        TextView currentState = text(value, 14, false);
        row.addView(
                currentState, new LinearLayout.LayoutParams(0, -2, 0.35f));

        Button action = compactButton(
                getString(enabled ? R.string.turn_off : R.string.set),
                enabled ? RED : GREEN);
        action.setOnClickListener(v -> {
            if (enabled) {
                JSONObject change = new JSONObject();
                try {
                    change.put("rain_delay_hours", 0);
                } catch (Exception ignored) {
                }
                put("/irrigation", change,
                        () -> load("/overview", "overview"));
            } else {
                showRainDelayDialog();
            }
        });
        row.addView(action);
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
                                load("/overview", "overview");
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
        if (days > 0) return days + " d " + hours + " h";
        if (hours > 0) return hours + " h " + minutes + " min";
        return minutes + " min";
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
