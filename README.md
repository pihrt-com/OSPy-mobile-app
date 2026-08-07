# OSPy Project
Open Sprinkler Python [Git-Hub source](https://github.com/martinpihrt/OSPy)

# OSPy Mobile APP
Android client for the OSPy `/api/v1`. App on [Google store](https://play.google.com/store/apps/details?id=com.pihrt.ospy.mobile)


## Included in version 0.3.11
- Centered the installation name in the free toolbar area between the OpenSprinkler logo and the settings button. The title now uses the same 28 dp visual height as the logo and a smaller 18 sp font.
- Fixed notification category routing: station start, station stop, rain, diagnostics and update server events now respect their matching settings instead of falling through to Other or being discarded.
- Added native Android JobScheduler background polling of the persistent API v1 notifications feed. Notifications continue to be collected when the activity is not open; periodic background execution is scheduled every 15 minutes and survives reboot/app replacement. Live in-app polling remains active while the app process is running.
- Added persistent per-installation notification cursors to prevent duplicate or historical notification floods.

## Included in version 0.3.10
- Added the supplied OpenSprinkler logo before the installation name. The branded toolbar remains visible on every installation tab.
- Added pull-to-refresh to the Home tab. Pulling down from the top performs the same complete reload as leaving Home and returning to it.
- Preserved the 0.3.9 fix that keeps a rain-blocked program visible until its planned end while rain delay remains active.

## Included in version 0.3.8
- Fixed: Both android:windowLightNavigationBar requires API level 27. The attribute has been moved from values/styles.xml to values-v27/styles.xml. Android 8.0 with API 26 will now use a compatible base theme. Both light and dark themes now use common base styles. Removed all deprecated options from gradle.properties that were listed in the Git-Hub log.
- Added recommended: android.dependency.excludeLibraryComponentsFromConstraints=true. Removed unnecessary Foojay Java toolchain plugin. GitHub Actions now always save: HTML Lint report, text Lint report, SARIF report, Gradle problems report. Debug APK will be uploaded even if it builds and then only Lint fails.
- Added missing TRANSLATIONS.md.
- Updated change documentation.

## Included in version 0.3.7
- Application authentication is enabled by default and can be disabled in the gear-menu settings. Android 10 and newer offer biometrics or the device credential; Android 8 and 9 use the secure device-credential screen.
- A light/dark theme switch applies a complete runtime colour palette to the native interface, cards, forms, status panels and plug-in charts.
- Phone notifications can be enabled separately for station start, station stop, rain protection, diagnostics, updates and other OSPy events.
- The System screen keeps update controls inside the OSPy information card, hides installation when no update is available and follows the asynchronous API operation until completion or failure.
- After an OSPy update and restart, the app reconnects, reloads the update state and refreshes the System screen automatically.
- Stable status codes such as `ok`, `warn`, `degraded`, `failed` and `unknown` are localized and use consistent status colours in every supported language.

## Included in version 0.3.6
- Plug-in cards show history controls only when the server supplies an actual graph, explicit history metadata or at least one measured series point.
- The System screen lists retained OSPy backups and downloads the selected ZIP through Android's system file picker.
- Administrators can confirm and start installation from the update channel already selected in the OSPy System Update plug-in.
- Binary backup downloads use the same access-token refresh and per-installation HTTPS certificate policy as JSON API requests.

## Included in version 0.3.5
- The Home screen shows a persistent localized connection warning after polling loses OSPy and includes the time of the last successful response.
- The duplicate Home refresh row was removed, the OSPy version was moved below the planner and rain-delay blocking reasons are localized.
- Native plug-in telemetry now localizes the Astro, real-time synchronization, system information, weather-calculation and Shelly metric labels and values.
- All user-visible Android and Keystore messages were audited against the English, Czech, German, Polish and Slovak XML catalogs.

## Included in version 0.3.4
- Rotating the device keeps the already unlocked application session and does not request biometrics or the device credential again.
- Disabled stations are omitted from the native Stations screen, matching the OSPy web interface.
- A disabled program can still be started manually while its automatic schedule remains disabled.
- Virtual water-meter labels supplied by OSPy identify the first and second master instead of being replaced by generic application labels.

## Included in version 0.3.3
- Station countdowns use localized hours, minutes and seconds.
- Program details show a readable start time, explicit duration and pause units, and localized weekday names; the editor arranges all seven weekdays across two rows.
- Every native plug-in chart supports 1 hour, Today, 7 days, Month, Year and a custom date range. Today is the default. Points use their real timestamps, empty ranges are identified clearly and the last available sample is shown.
- Long plug-in histories are requested from OSPy in a bounded range and are reduced on the server while preserving bucket minima and maxima.

## Included in version 0.3.2
- German, Polish and Slovak application resources.
- Smooth refresh home page
- Schedule days

## Included in version 0.3.1
- Multiple saved OSPy installations, for example Home, Cottage and Greenhouse.
- Refresh tokens encrypted by an AES-GCM key stored in Android Keystore.
- Application unlock with biometrics or the device credential when configured.
- Password and OSPy 2FA pairing; short-lived access tokens stay in memory.
- Native Home, Stations, Programs, Sensors, Weather, Logs, Diagnostics, Plug-ins and System screens.
- Immediate station start/stop, Stop All and program run actions.
- Update check, system-backup creation and OSPy restart actions.
- Foreground live-change polling through the documented SSE fallback, including local Android notifications for OSPy notification events.
- English, Czech application resources.
- Compact navigation with a clearly highlighted active section and native, localized cards for weather, event logs, diagnostics and update status.
- Resilient Home and sensor rendering when an optional API field is unavailable.
- Correct first launch after the Android notification-permission prompt.
- Live Home and station state refresh, including a known countdown or an explicit running state for direct starts without a scheduled end.
- Editing of a saved installation name, address and per-installation unverified-certificate choice.
- A gear in the top application bar opens application settings with the notification switch, saved OSPy systems, app version and official project, plug-in, source-code and Google Play links. The OSPy System tab is reserved for server status and administrator actions.
- Native sensor and plug-in cards can enable or disable an item through the protected API. Plug-in activation still requires prior permission approval and passes through OSPy's compatibility and lifecycle checks.
- Home refreshes automatically every ten seconds, shows the last successful server refresh and reloads immediately after a control action. Background refreshes update the existing values and timeline rows in place, so the Home screen does not disappear or flash during polling.
- Home controls the scheduler and manual mode. Rain delay accepts a duration selected by the user, displays the remaining time and can be cancelled.
- Initial live-event synchronization does not replay old weather or diagnostic notifications after login.
- Sensor cards use the typed API display contract, showing only the relevant measured value and unit plus connection, firmware, communication and address information instead of legacy arrays and numeric type codes.
- Installation cards keep the name and address on separate rows with their actions below, so long local HTTPS addresses remain readable.
- Home replaces the duplicate weather cards with a live, normalized watering timeline showing scheduled, running, blocked and completed station work.
- Programs show their stations and schedule details, support enable/disable and run actions, and provide a native editor for the stable Mobile API scheduling fields.
- Logs can switch between the OSPy event log and station-run history.
- Official plug-ins can expose optional read-only native metric and chart cards through the documented JSON-only plug-in adapter contract.
- Home uses the current local OSPy day, keeps only a compact recent/running/upcoming timeline and shows running progress and remaining time.
- Native plug-in cards can be collapsed again, localize their known metric names in the app, show chart legends and time bounds, and render a bounded current radar image.
- Optional network-aware installation selection prefers a saved private address on Wi-Fi and a public address outside Wi-Fi. The last reachable installation can be opened automatically after application unlock.

The app uses only Android platform APIs and `org.json`; it has no analytics, advertising, cloud relay or third-party runtime library.

## Localization

The app uses native Android string resources and includes English, Czech, German, Polish and Slovak. Android 13 and newer can select a language specifically for OSPy Mobile. See [TRANSLATIONS.md](TRANSLATIONS.md) for the POEditor, Weblate and Android Studio workflow and automated validation.

## Build

Install Android Studio with Android SDK 37 and JDK 17. Open this directory, allow Gradle to synchronize and run or build the `app` configuration. GitHub Actions also builds a debug APK on every push and pull request using JDK 17 and the Gradle version pinned by the repository wrapper. The workflow retains the debug APK and complete HTML, text and SARIF Lint reports for diagnosis.

The repository intentionally does not contain `local.properties`, SDK files, signing keys or built APK files.

## Connection and security

Update OSPy to a version that provides `/api/v1`. Add the full HTTP(S) address, user name, password and optional 2FA code. Use HTTPS whenever OSPy is reachable outside a trusted private network. Cleartext HTTP remains allowed for existing LAN-only OSPy installations and is visibly present in the saved address.

OSPy commonly uses a locally generated HTTPS certificate that Android cannot verify. The pairing form therefore offers an explicit per-installation **Trust an unverified HTTPS certificate** option. It disables certificate-chain and host-name verification only for that saved installation and is off by default. Enable it only for your own OSPy on a trusted private network. Use a publicly trusted certificate for any installation exposed through the Internet. If Android cannot resolve a local name such as `ospy`, enter the device's local IP address instead.

Removing an installation deletes its locally protected refresh token. Use the OSPy paired-device endpoint or OSPy web administration to revoke a lost device. Android backup is disabled so tokens cannot leave the device through application backup.

## Architecture

- `ApiClient` implements JSON requests, automatic token refresh and rotation.
- `KeystoreStore` encrypts the saved installation list.
- `InstallationStore` supports multiple OSPy systems.
- `LiveUpdates` uses `/changes` as the reconnect-safe fallback to `/stream`.
- `MainActivity` renders native Android views and sends explicit API actions.

The complete server-side contract is documented in the [OSPy Mobile API v1 reference](https://github.com/martinpihrt/OSPy/blob/master/api/docs/Mobile_API_v1.md) and exposed by every installation at `/api/v1/openapi.json`.
