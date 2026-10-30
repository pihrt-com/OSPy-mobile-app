package com.pihrt.ospy.mobile;

import android.app.Activity;
import android.os.CancellationSignal;
import android.os.Build;
import android.util.Log;

import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.CreateRestoreCredentialRequest;
import androidx.credentials.CreateRestoreCredentialResponse;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.GetRestoreCredentialOption;
import androidx.credentials.RestoreCredential;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

final class RestoreCredentials {
    interface Callback {
        void success(Installation installation, ApiClient api);
        void failure(String message);
    }

    private final Activity activity;
    private final InstallationStore installationStore;
    private final RestoreMetadataStore metadataStore;
    private final AppPreferences preferences;
    private final CredentialManager credentialManager;

    RestoreCredentials(
            Activity activity, InstallationStore installationStore,
            AppPreferences preferences) {
        this.activity = activity;
        this.installationStore = installationStore;
        this.preferences = preferences;
        metadataStore = new RestoreMetadataStore(activity);
        credentialManager = CredentialManager.create(activity);
    }

    Installation restoredInstallation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null;
        return metadataStore.restoredInstallation();
    }

    void registerIfNeeded(ApiClient api, Installation installation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        if (installation.id.equals(preferences.restoreKeyInstallationId())) {
            metadataStore.save(installation);
            return;
        }
        api.request(
                "POST", "/auth/restore/register/options", new JSONObject(),
                new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        try {
                            JSONObject data = response.getJSONObject("data");
                            createRestoreCredential(
                                    api, installation,
                                    data.getString("challenge_id"),
                                    data.getJSONObject("options"), true);
                        } catch (Exception error) {
                            logFailure("invalid registration options", error);
                        }
                    }

                    @Override public void failure(String message) {
                        Log.w("OSPyRestore", "Registration options failed: " +
                                ApiClient.errorCode(message));
                    }
                });
    }

    private void createRestoreCredential(
            ApiClient api, Installation installation, String challengeId,
            JSONObject options, boolean cloudBackup) {
        CreateRestoreCredentialRequest request =
                new CreateRestoreCredentialRequest(options.toString(), cloudBackup);
        credentialManager.createCredentialAsync(
                activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<
                        CreateCredentialResponse, CreateCredentialException>() {
                    @Override public void onResult(CreateCredentialResponse result) {
                        if (!(result instanceof CreateRestoreCredentialResponse)) {
                            Log.w("OSPyRestore", "Unexpected registration response");
                            return;
                        }
                        String credentialJson =
                                ((CreateRestoreCredentialResponse) result)
                                        .getResponseJson();
                        try {
                            JSONObject body = new JSONObject()
                                    .put("challenge_id", challengeId)
                                    .put("credential", new JSONObject(credentialJson));
                            api.request(
                                    "POST", "/auth/restore/register", body,
                                    new ApiClient.Callback() {
                                        @Override public void success(JSONObject response) {
                                            preferences.setRestoreKeyInstallationId(
                                                    installation.id);
                                            metadataStore.save(installation);
                                        }

                                        @Override public void failure(String message) {
                                            Log.w(
                                                    "OSPyRestore",
                                                    "Registration verification failed: " +
                                                            ApiClient.errorCode(message));
                                        }
                                    });
                        } catch (Exception error) {
                            logFailure("registration response", error);
                        }
                    }

                    @Override public void onError(CreateCredentialException error) {
                        if (cloudBackup && error instanceof E2eeUnavailableException) {
                            createRestoreCredential(
                                    api, installation, challengeId, options, false);
                            return;
                        }
                        logFailure("credential creation", error);
                    }
                });
    }

    void restore(Installation installation, Callback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback.failure("Restore Credentials require Android 9 or newer");
            return;
        }
        ApiClient restoringApi = new ApiClient(installation, installationStore);
        restoringApi.restoreOptions(
                installation.username,
                new ApiClient.Callback() {
                    @Override public void success(JSONObject response) {
                        try {
                            JSONObject data = response.getJSONObject("data");
                            getRestoreCredential(
                                    restoringApi, installation,
                                    data.getString("challenge_id"),
                                    data.getJSONObject("options"), callback);
                        } catch (Exception error) {
                            callback.failure(error.getClass().getSimpleName());
                        }
                    }

                    @Override public void failure(String message) {
                        callback.failure(message);
                    }
                });
    }

    void clearIfOwned(Installation installation) {
        if (!installation.id.equals(preferences.restoreKeyInstallationId())) return;
        preferences.setRestoreKeyInstallationId("");
        metadataStore.clear();
        ClearCredentialStateRequest request = new ClearCredentialStateRequest(
                ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL);
        credentialManager.clearCredentialStateAsync(
                request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) {
                    }

                    @Override public void onError(ClearCredentialException error) {
                        logFailure("credential deletion", error);
                    }
                });
    }

    private void getRestoreCredential(
            ApiClient api, Installation installation, String challengeId,
            JSONObject options, Callback callback) {
        GetRestoreCredentialOption option =
                new GetRestoreCredentialOption(options.toString());
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        credentialManager.getCredentialAsync(
                activity, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<
                        GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (!(credential instanceof RestoreCredential)) {
                            callback.failure("Unexpected Restore Credential response");
                            return;
                        }
                        api.restore(
                                challengeId,
                                ((RestoreCredential) credential)
                                        .getAuthenticationResponseJson(),
                                new ApiClient.Callback() {
                                    @Override public void success(JSONObject response) {
                                        preferences.setRestoreKeyInstallationId(
                                                installation.id);
                                        metadataStore.save(installation);
                                        callback.success(installation, api);
                                    }

                                    @Override public void failure(String message) {
                                        callback.failure(message);
                                    }
                                });
                    }

                    @Override public void onError(GetCredentialException error) {
                        if (error instanceof NoCredentialException) {
                            callback.failure("No Restore Credential is available");
                            return;
                        }
                        callback.failure(error.getClass().getSimpleName());
                    }
                });
    }

    private static void logFailure(String stage, Exception error) {
        Log.w(
                "OSPyRestore",
                "Restore Credentials " + stage + " failed: " +
                        error.getClass().getSimpleName());
    }
}
