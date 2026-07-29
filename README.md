# OSPy Mobile

First native Android client for the stable OSPy `/api/v1`. The application
never parses web-interface HTML.

## Included in version 0.2.3

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
- Compact navigation with a clearly highlighted active section and native,
  localized cards for weather, event logs, diagnostics and update status.
- Resilient Home and sensor rendering when an optional API field is unavailable.
- Correct first launch after the Android notification-permission prompt.
- Live Home and station state refresh, including a known countdown or an
  explicit running state for direct starts without a scheduled end.
- Editing of a saved installation name, address and per-installation
  unverified-certificate choice.
- An application notification switch on the System screen.
- Home refreshes automatically every ten seconds, shows the last successful
  server refresh and reloads immediately after a control action.
- Home controls the scheduler and manual mode. Rain delay accepts a duration
  selected by the user, displays the remaining time and can be cancelled.
- Initial live-event synchronization does not replay old weather or diagnostic
  notifications after login.
- Sensor cards use the typed API display contract, showing only the relevant
  measured value and unit plus connection, firmware, communication and address
  information instead of legacy arrays and numeric type codes.
- Installation cards keep the name and address on separate rows with their
  actions below, so long local HTTPS addresses remain readable.

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

OSPy commonly uses a locally generated HTTPS certificate that Android cannot
verify. The pairing form therefore offers an explicit per-installation
**Trust an unverified HTTPS certificate** option. It disables certificate-chain
and host-name verification only for that saved installation and is off by
default. Enable it only for your own OSPy on a trusted private network. Use a
publicly trusted certificate for any installation exposed through the Internet.
If Android cannot resolve a local name such as `ospy`, enter the device's local
IP address instead.

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
