package com.pihrt.ospy.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(43, 138, 30);
    private static final int NAVY = Color.rgb(48, 59, 92);
    private static final int RED = Color.rgb(146, 27, 37);

    private InstallationStore installationStore;
    private List<Installation> installations = new ArrayList<>();
    private Installation current;
    private ApiClient api;
    private ApiClient pairingApi;
    private Installation pairingInstallation;
    private LiveUpdates liveUpdates;
    private NotificationCenter notifications;
    private LinearLayout page;
    private TextView title;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        installationStore = new InstallationStore(this);
        notifications = new NotificationCenter(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        unlock();
    }

    @Override
    protected void onDestroy() {
        if (liveUpdates != null) liveUpdates.stop();
        super.onDestroy();
    }

    private void unlock() {
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
                .setSubtitle("Biometrics or device credential");
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setDeviceCredentialAllowed(true);
        } else {
            builder.setNegativeButton(
                    getString(android.R.string.cancel), getMainExecutor(),
                    (dialog, which) -> finish());
        }
        BiometricPrompt prompt = builder.build();
        prompt.authenticate(new CancellationSignal(), getMainExecutor(),
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

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(8), dp(12));
        bar.setBackgroundColor(GREEN);
        title = new TextView(this);
        title.setText(heading);
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(null, 1);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(bar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
    }

    private void showInstallations() {
        shell(getString(R.string.app_name));
        try {
            installations = installationStore.load();
        } catch (Exception error) {
            message("Protected storage error", error.getMessage());
            installations = new ArrayList<>();
        }
        heading("Installations");
        for (Installation installation : installations) {
            LinearLayout row = card();
            TextView name = text(installation.name + "\n" + installation.baseUrl, 17, true);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button open = button("OPEN", GREEN);
            open.setOnClickListener(v -> open(installation));
            row.addView(open);
            Button remove = button("×", RED);
            remove.setOnClickListener(v -> confirmRemove(installation));
            row.addView(remove);
            content.addView(row);
        }
        Button add = button(getString(R.string.add_installation), NAVY);
        add.setOnClickListener(v -> showPairing());
        content.addView(add);
    }

    private void showPairing() {
        shell(getString(R.string.add_installation));
        EditText url = input(getString(R.string.server_url), false);
        url.setText("https://");
        EditText user = input(getString(R.string.username), false);
        EditText password = input(getString(R.string.password), true);
        EditText secondFactor = input(getString(R.string.two_factor), false);
        content.addView(url);
        content.addView(user);
        content.addView(password);
        content.addView(secondFactor);

        Button connect = button(getString(R.string.connect), GREEN);
        connect.setOnClickListener(v -> {
            String base = Installation.normalize(url.getText().toString());
            if (!(base.startsWith("https://") || base.startsWith("http://"))) {
                toast("Enter a complete HTTP or HTTPS address.");
                return;
            }
            connect.setEnabled(false);
            if (pairingInstallation == null ||
                    !pairingInstallation.baseUrl.equals(base)) {
                pairingInstallation = new Installation(
                        UUID.randomUUID().toString(), base, base,
                        user.getText().toString().trim(), "");
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
                            message("Connection failed", error);
                        }
                    });
        });
        content.addView(connect);
        Button back = button("BACK", RED);
        back.setOnClickListener(v -> showInstallations());
        content.addView(back);
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
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        addNav(navigation, R.string.overview, "/overview", "overview");
        addNav(navigation, R.string.stations, "/stations", "stations");
        addNav(navigation, R.string.programs, "/programs", "programs");
        addNav(navigation, R.string.sensors, "/sensors", "generic");
        addNav(navigation, R.string.weather, "/weather/forecast", "generic");
        addNav(navigation, R.string.logs, "/logs/events?limit=100", "generic");
        addNav(navigation, R.string.diagnostics, "/diagnostics/components", "generic");
        addNav(navigation, R.string.plugins, "/plugins", "plugins");
        addNav(navigation, R.string.system, "/updates", "system");
        horizontal.addView(navigation);
        page.addView(horizontal, 1, new LinearLayout.LayoutParams(-1, -2));
        load("/overview", "overview");
    }

    private void addNav(LinearLayout navigation, int label, String path, String renderer) {
        Button button = button(getString(label), NAVY);
        button.setOnClickListener(v -> load(path, renderer));
        navigation.addView(button);
    }

    private void load(String path, String renderer) {
        content.removeAllViews();
        content.addView(text(getString(R.string.loading), 17, false));
        api.request("GET", path, null, new ApiClient.Callback() {
            @Override public void success(JSONObject response) {
                content.removeAllViews();
                Object data = response.opt("data");
                if ("overview".equals(renderer)) renderOverview((JSONObject) data);
                else if ("stations".equals(renderer)) renderStations((JSONArray) data);
                else if ("programs".equals(renderer)) renderPrograms((JSONArray) data);
                else if ("plugins".equals(renderer)) renderPlugins((JSONArray) data);
                else if ("system".equals(renderer)) renderSystem(data);
                else renderGeneric(data);
            }
            @Override public void failure(String error) {
                content.removeAllViews();
                content.addView(text(error, 17, true));
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
            line("Version", instance.optString("version"));
        }
        if (irrigation != null) {
            heading("Irrigation");
            line("Scheduler", irrigation.optBoolean("scheduler_enabled") ? "ON" : "OFF");
            line("Manual mode", irrigation.optBoolean("manual_mode") ? "ON" : "OFF");
            line("Rain", irrigation.optBoolean("rain_block") ? "ACTIVE" : "inactive");
            JSONArray active = irrigation.optJSONArray("active_stations");
            line("Active stations", active == null ? "0" : String.valueOf(active.length()));
        }
        Button stop = button(getString(R.string.stop_all), RED);
        stop.setOnClickListener(v -> confirmAction(
                "Stop all irrigation?", "/stations/actions/stop-all"));
        content.addView(stop);
        JSONObject weather = data.optJSONObject("weather");
        if (weather != null) {
            heading("Weather");
            renderGeneric(weather);
        }
    }

    private void renderStations(JSONArray stations) {
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.optJSONObject(i);
            if (station == null) continue;
            LinearLayout card = card();
            String state = station.optBoolean("running") ? "RUNNING" : "stopped";
            TextView label = text(
                    station.optInt("number") + ". " + station.optString("name") +
                            "\n" + state + " · " +
                            station.optInt("remaining_seconds") + " s",
                    17, true);
            card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (!station.optBoolean("is_master") &&
                    !station.optBoolean("is_master_two")) {
                String action = station.optBoolean("running") ? "stop" : "start";
                Button toggle = button(action.toUpperCase(),
                        station.optBoolean("running") ? RED : GREEN);
                toggle.setOnClickListener(v -> post(
                        "/stations/" + station.optString("id") + "/actions/" + action,
                        new JSONObject(), () -> load("/stations", "stations")));
                card.addView(toggle);
            }
            content.addView(card);
        }
        Button stop = button(getString(R.string.stop_all), RED);
        stop.setOnClickListener(v -> confirmAction(
                "Stop all irrigation?", "/stations/actions/stop-all"));
        content.addView(stop);
    }

    private void renderPrograms(JSONArray programs) {
        for (int i = 0; i < programs.length(); i++) {
            JSONObject program = programs.optJSONObject(i);
            if (program == null) continue;
            LinearLayout card = card();
            TextView label = text(
                    program.optInt("number") + ". " + program.optString("name") +
                            "\n" + program.optString("summary"), 16, true);
            card.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            Button run = button("RUN", GREEN);
            run.setEnabled(program.optBoolean("enabled"));
            run.setOnClickListener(v -> confirmAction(
                    "Run " + program.optString("name") + "?",
                    "/programs/" + program.optString("id") + "/actions/run"));
            card.addView(run);
            content.addView(card);
        }
    }

    private void renderPlugins(JSONArray plugins) {
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject plugin = plugins.optJSONObject(i);
            if (plugin == null) continue;
            JSONObject health = plugin.optJSONObject("health");
            String status = health == null ? "" : health.optString("status");
            content.addView(cardText(
                    plugin.optString("name", plugin.optString("id")) + " " +
                            plugin.optString("version") + "\n" +
                            (plugin.optBoolean("running") ? "running" : "stopped") +
                            (status.isEmpty() ? "" : " · " + status)));
        }
    }

    private void renderSystem(Object data) {
        renderGeneric(data);
        heading("Actions");
        Button check = button("CHECK UPDATES", GREEN);
        check.setOnClickListener(v -> post(
                "/updates/actions/check", new JSONObject(), () -> toast("Check started")));
        content.addView(check);
        Button backup = button("CREATE BACKUP", NAVY);
        backup.setOnClickListener(v -> post(
                "/backups", new JSONObject(), () -> toast("Backup started")));
        content.addView(backup);
        Button restart = button("RESTART OSPy", RED);
        restart.setOnClickListener(v -> confirmAction(
                "Restart OSPy?", "/system/actions/restart-ospy"));
        content.addView(restart);
        Button installations = button("INSTALLATIONS", NAVY);
        installations.setOnClickListener(v -> showInstallations());
        content.addView(installations);
    }

    private void renderGeneric(Object data) {
        if (data instanceof JSONArray) {
            JSONArray array = (JSONArray) data;
            if (array.length() == 0) content.addView(text("No data", 16, false));
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                content.addView(cardText(pretty(item)));
            }
        } else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = object.opt(key);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    heading(readable(key));
                    renderGeneric(value);
                } else {
                    line(readable(key), String.valueOf(value));
                }
            }
        } else {
            content.addView(text(String.valueOf(data), 16, false));
        }
    }

    private void post(String path, JSONObject body, Runnable done) {
        api.request("POST", path, body, new ApiClient.Callback() {
            @Override public void success(JSONObject response) { done.run(); }
            @Override public void failure(String error) { message("OSPy", error); }
        });
    }

    private void confirmAction(String prompt, String path) {
        new AlertDialog.Builder(this)
                .setMessage(prompt)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        post(path, new JSONObject(), () -> toast("Accepted")))
                .show();
    }

    private void confirmRemove(Installation installation) {
        new AlertDialog.Builder(this)
                .setMessage("Remove " + installation.name + "?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        installationStore.remove(installation.id);
                        showInstallations();
                    } catch (Exception error) {
                        message("Protected storage error", error.getMessage());
                    }
                }).show();
    }

    private LinearLayout card() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.HORIZONTAL);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(2), NAVY);
        value.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, 0, 0, dp(8));
        value.setLayoutParams(layout);
        return value;
    }

    private View cardText(String value) {
        LinearLayout card = card();
        card.addView(text(value, 15, false));
        return card;
    }

    private void heading(String value) {
        TextView view = text(value, 19, true);
        view.setTextColor(NAVY);
        view.setPadding(0, dp(12), 0, dp(6));
        content.addView(view);
    }

    private void line(String key, String value) {
        content.addView(text(key + ": " + value, 16, false));
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.rgb(30, 30, 30));
        view.setTextSize(size);
        view.setPadding(dp(6), dp(5), dp(6), dp(5));
        if (bold) view.setTypeface(null, 1);
        return view;
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(22));
        button.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, dp(48));
        layout.setMargins(dp(4), dp(4), dp(4), dp(4));
        button.setLayoutParams(layout);
        return button;
    }

    private EditText input(String hint, boolean password) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextSize(17);
        view.setSingleLine(true);
        if (password) view.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return view;
    }

    private static String readable(String value) {
        if (value == null || value.isEmpty()) return "";
        String text = value.replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String pretty(Object value) {
        try {
            if (value instanceof JSONObject) return ((JSONObject) value).toString(2);
            if (value instanceof JSONArray) return ((JSONArray) value).toString(2);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private void message(String title, String value) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(value == null ? "" : value)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
