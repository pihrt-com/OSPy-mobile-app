# OSPy Mobile

First native Android client for the stable OSPy `/api/v1`. The application
never parses web-interface HTML.

## Included in version 0.1.0

- Multiple saved OSPy installations, for example Home, Cottage and Greenhouse.
- Refresh tokens encrypted by an AES-GCM key stored in Android Keystore.
- Application unlock with biometrics or the device credential when configured.
- Password and OSPy 2FA pairing; short-lived access tokens stay in memory.
- Native Home, Stations, Programs, Sensors, Weather, Logs, Diagnostics,
  Plug-ins and System screens.
- Immediate station start/stop, Stop All and program run actions.
- Update check, system-backup creation and OSPy restart actions.
- Foreground live-change polling through the documented SSE fallback, including
  local Android notifications for OSPy notification events.
- Czech and English application resources.

The app uses only Android platform APIs and `org.json`; it has no analytics,
advertising, cloud relay or third-party runtime library.

## Build

Install Android Studio with Android SDK 35 and JDK 17. Open this directory,
allow Gradle to synchronize and run or build the `app` configuration.
GitHub Actions also builds a debug APK on every push and pull request using
JDK 17 and Gradle 8.9.

The repository intentionally does not contain `local.properties`, SDK files,
signing keys or built APK files.

## Connection and security

Update OSPy to a version that provides `/api/v1`. Add the full HTTP(S) address,
user name, password and optional 2FA code. Use HTTPS whenever OSPy is reachable
outside a trusted private network. Cleartext HTTP remains allowed for existing
LAN-only OSPy installations and is visibly present in the saved address.

Removing an installation deletes its locally protected refresh token. Use the
OSPy paired-device endpoint or OSPy web administration to revoke a lost device.
Android backup is disabled so tokens cannot leave the device through application
backup.

## Architecture

- `ApiClient` implements JSON requests, automatic token refresh and rotation.
- `KeystoreStore` encrypts the saved installation list.
- `InstallationStore` supports multiple OSPy systems.
- `LiveUpdates` uses `/changes` as the reconnect-safe fallback to `/stream`.
- `MainActivity` renders native Android views and sends explicit API actions.

The complete server-side contract is documented in the
[OSPy Mobile API v1 reference](https://github.com/martinpihrt/OSPy/blob/master/api/docs/Mobile_API_v1.md)
and exposed by every installation at `/api/v1/openapi.json`.
