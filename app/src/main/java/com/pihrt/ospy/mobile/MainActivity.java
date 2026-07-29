package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

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
            showInstallations();
            return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            showInstallations();
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
                        showInstallations();
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence message) {
                        if (code != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                            toast(message.toString());
                        }
                    }
                });
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
        if (instance != null) {
            heading(instance.optString("name", "OSPy"));
            line(getString(R.string.version), instance.optString("version"));
        }
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
            JSONObject rainChange = new JSONObject();
            try {
                rainChange.put("rain_delay_hours", rainBlock ? 0 : 24);
            } catch (Exception ignored) {
            }
            addIrrigationControl(
                    summary, getString(R.string.rain_delay), rainBlock,
                    rainChange);
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
        JSONObject weather = data.optJSONObject("weather");
        if (weather != null) {
            heading(getString(R.string.weather));
            renderWeather(weather, false);
        }
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
            LinearLayout card = card();
            TextView label = text(
                    program.optInt("number") + ". " + program.optString("name") +
                            "\n" + program.optString("summary"), 15, true);
            card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            Button run = button(getString(R.string.run), GREEN);
            run.setEnabled(program.optBoolean("enabled"));
            run.setAlpha(program.optBoolean("enabled") ? 1.0f : 0.45f);
            run.setOnClickListener(v -> confirmAction(
                    getString(
                            R.string.confirm_run, program.optString("name")),
                    "/programs/" + program.optString("id") + "/actions/run"));
            card.addView(run);
            content.addView(card);
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
            card.addView(badge(
                    sensor.optBoolean("enabled")
                            ? getString(R.string.enabled)
                            : getString(R.string.disabled),
                    sensor.optBoolean("enabled") ? "ok" : "stopped"));
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
        if (events.length() == 0) {
            content.addView(text(getString(R.string.no_data), 16, false));
            return;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
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
            card.addView(statuses);
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
        heading(getString(R.string.notifications));
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
            load("/updates", "system");
        });
        notificationSettings.addView(notificationToggle);
        content.addView(notificationSettings);
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
        Button installationList = button(
                getString(R.string.installations), NAVY);
        installationList.setOnClickListener(v -> showInstallations());
        systemActions.addView(installationList);
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

    private JSONObject jsonBoolean(String key, boolean value) {
        JSONObject result = new JSONObject();
        try {
            result.put(key, value);
        } catch (Exception ignored) {
        }
        return result;
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
        } else if ("warning".equals(status)) {
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

    private static String readable(String value) {
        if (value == null || value.isEmpty()) return "";
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
