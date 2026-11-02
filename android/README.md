# Availabliss for Android

Native Android client for Availabliss: Kotlin, Jetpack Compose (Material 3 restyled
with the Availabliss "Dawn glass" tokens), Hilt, Retrofit + OkHttp + kotlinx.serialization,
OkHttp SSE, DataStore, EncryptedSharedPreferences, Credential Manager, FCM, WorkManager
and Custom Tabs. It talks to the same `/api/v1` API as the web and iOS apps
(see [`docs/API.md`](../docs/API.md)) and implements [`docs/DESIGN.md`](../docs/DESIGN.md).

## Requirements

- **Android Studio** (its bundled JDK runs Gradle; `make android-build` picks it up
  automatically when `JAVA_HOME` isn't set).
- **Android SDK platform 37** (`compileSdk`/`targetSdk` 37, `minSdk` 26). Android Studio's
  SDK Manager installs it.
- Gradle **9.8.2** via the committed wrapper, AGP **9.5.0** (built-in Kotlin, so there is no
  `kotlin-android` plugin), Kotlin **2.4**, KSP **2.3**.

```sh
make android-build      # debug APK → app/build/outputs/apk/debug/app-debug.apk
make android-test       # unit tests
```

### Running against the local dev server

- The emulator reaches the host as `http://10.0.2.2:5190` (the default `apiBaseUrl`). The dev
  server binds to `127.0.0.1` for this, and `TRUSTED_ORIGINS=http://10.0.2.2:5190` in
  `web/.env` lets Better Auth accept the app's sign-in requests.
- Android 17 blocks apps from reaching local-network addresses without
  `ACCESS_LOCAL_NETWORK`. Only **debug** builds declare it (`src/debug/AndroidManifest.xml`) and
  ask for it on launch; release builds talk to the public API and never ask.
- End-to-end tour on a running emulator (signs in with a real email code from Mailpit and
  screenshots every main screen into `android/build/tour-screens`):

  ```sh
  make seed-demo && make android-tour
  ```

## Configuration

All app configuration is read from `android/local.properties` (git-ignored), falling back
to Gradle properties (`-P…` or `~/.gradle/gradle.properties`), then to dev defaults:

```properties
sdk.dir=/Users/you/Library/Android/sdk

# Base URL of the SvelteKit app serving /api/v1 and /api/auth. No trailing slash.
# Default: http://10.0.2.2:5190 (the host machine from the Android emulator).
availabliss.apiBaseUrl=http://10.0.2.2:5190

# OAuth 2.0 *Web* client id from Google Cloud (used as serverClientId for Credential Manager).
availabliss.googleWebClientId=1234567890-abc.apps.googleusercontent.com

# Host for verified app links (https://<host>/invite/:token, /app/...).
availabliss.appLinkHost=availabliss.app
```

These become `BuildConfig.API_BASE_URL`, `BuildConfig.GOOGLE_WEB_CLIENT_ID` and
`BuildConfig.APP_LINK_HOST` (plus a manifest placeholder for the app-link host).

Cleartext HTTP is allowed **only in debug** (`app/src/debug/res/xml/network_security_config.xml`);
release builds are HTTPS-only. With a physical device use your machine's LAN IP or
`adb reverse tcp:5190 tcp:5190` and `http://localhost:5190`.

## Firebase Cloud Messaging (optional)

The build works without Firebase. To enable push:

1. Create a Firebase project and add an Android app with package `dev.joeherbert.availabliss`.
2. Download `google-services.json` to `android/app/google-services.json` (git-ignored).
3. Rebuild. The `com.google.gms.google-services` plugin is applied only when that file
   exists, and `BuildConfig.FCM_ENABLED` becomes `true`.
4. On the server set `FCM_SERVICE_ACCOUNT` (base64 service-account JSON).

The app registers its FCM token with `POST /api/v1/devices` after sign in and removes it
(`DELETE /devices/:token`) on sign out. Channels: General, Group chat, Proposals, Events,
Reminders. Taps deep-link using the push `data` keys (`kind`, `groupId`, `proposalId`, …).

## Google sign-in

1. In Google Cloud create an **OAuth client of type Web** (its id goes in
   `availabliss.googleWebClientId` and in the server's `GOOGLE_CLIENT_IDS`).
2. Create an **OAuth client of type Android** for package `dev.joeherbert.availabliss`
   with the SHA-1 of each signing key you use (debug and release). It is not referenced
   in code but must exist for Credential Manager to return ID tokens.
3. The server must list the web client id first in `GOOGLE_CLIENT_IDS`
   (the ID token audience is the web client id).

The app gets an ID token with Credential Manager (`GetSignInWithGoogleOption`) and calls
`POST /api/auth/sign-in/social { provider: "google", idToken: { token } }`.

## Passkeys and app links (assetlinks.json)

Passkeys and verified app links need the web domain associated with the app. The web app
serves `/.well-known/assetlinks.json` from `ANDROID_PACKAGE` and
`ANDROID_SHA256_FINGERPRINTS`. Get the SHA-256 of each signing certificate:

```sh
# Debug key
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA256
# Release key
keytool -list -v -keystore /path/to/release.jks -alias <alias> | grep SHA256
# Or, for a Play App Signing app: Play Console → Setup → App signing → SHA-256
```

Set on the server, e.g.:

```
ANDROID_PACKAGE=dev.joeherbert.availabliss
ANDROID_SHA256_FINGERPRINTS=AA:BB:...,CC:DD:...
```

Passkey sign in calls `GET /api/auth/passkey/generate-authenticate-options`, passes the JSON
to Credential Manager, then `POST /api/auth/passkey/verify-authentication`. Better Auth keeps
the WebAuthn challenge in a cookie, so the app keeps an in-memory cookie jar for
`/api/auth/passkey/*` only (`PasskeyCookieJar`). "Add a passkey" lives on the You tab.

## Sign in with Apple (web flow)

Android has no Apple SDK, so Sign in with Apple runs as a web flow in a **Custom Tab**:

1. The app opens `{apiBaseUrl}/api/v1/auth/native/apple/start`. The server starts Better Auth's
   Apple sign-in inside the Custom Tab (so its state cookie lives there) and redirects to Apple.
2. Apple returns to the server, which finishes the sign-in and redirects to
   `availabliss://auth/callback?code=<one-time code>`.
3. The app exchanges the code at `POST /api/v1/auth/native/exchange` for its bearer token, then
   loads `/me`. Codes are single-use and expire after two minutes, so the token never appears in
   a URL.

## Release signing

Create `android/keystore.properties` (git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=availabliss
keyPassword=…
```

`./gradlew bundleRelease` then signs with it (R8 minification + resource shrinking on).
Without the file, release builds are unsigned.

## Architecture

Single `:app` module, packages by layer then feature:

```
dev.joeherbert.availabliss
├── AvailablissApp / MainActivity      Hilt app, WorkManager config, deep-link intake, theme
├── navigation/                        Type-safe routes, RootViewModel (signed out / onboarding / main), NavHost + floating tab bar
├── core/
│   ├── network/   AvailablissApi (every /api/v1 endpoint), BetterAuthApi, models mirroring API.md,
│   │              ApiError (sealed) + ApiResult, AuthInterceptor (Bearer), PasskeyCookieJar, JsonPatch
│   ├── auth/      TokenStore (EncryptedSharedPreferences), AuthRepository (OTP, Google, passkey, Apple),
│   │              CredentialAuthenticator (Credential Manager), SessionManager (post-sign-in + sign-out cleanup)
│   ├── data/      Repositories (user, regions, calendars, blocks, groups, proposals, events, chat, notifications), DataStore prefs
│   ├── calendar/  CalendarContract reader, device busy upload, WorkManager worker + scheduler, ContentObserver, calendar inserter
│   ├── live/      SSE client (/api/v1/live) with backoff, typed payloads, foreground-only lifecycle observer
│   ├── push/      FCM service, token registration, channels, notifications with deep links
│   ├── deeplink/  DeepLink parsing (custom scheme, app links, push data) + pending-link bus
│   └── common/    DateTimes (wire formats ⇄ java.time + display formats), coroutine qualifiers
├── designsystem/  AvailablissTheme (all DESIGN.md tokens, light/dark × 5 themes, animated), typography (Downloadable Google Fonts),
│                  icons transcribed from the concept SVGs, GlassPanel/glass modifier, Orbs (RenderEffect blur on API 31+),
│                  buttons, SegmentedControl, Chip, Toggle, Avatar/AvatarStack, GroupTile, StatusPill, SyncRing, TabBar,
│                  bottom sheet, fields/OTP, pickers, availability grids, chat components, previews
└── feature/       signin, onboarding, calendars, appearance, you, home, newgroup, group, availability, timing,
                   proposal, chat, events, mytime, members, activity, common (add-to-calendar, member names)
```

Each feature is a `Screen` + `@HiltViewModel` exposing an immutable `UiState` via
`StateFlow` (unidirectional data flow); composables are stateless `…Content` functions.

**Why Retrofit over Ktor:** OkHttp is required anyway for SSE (`okhttp-sse`), so Retrofit on
the same client gives one HTTP stack, one interceptor chain (bearer auth, logging) and
first-party kotlinx.serialization support.

**JSON:** `encodeDefaults = false` + `explicitNulls = true`: optional request fields default to
`null` and are omitted; required-nullable fields (e.g. `Requirement.window`) have no default
and are sent as explicit `null`. Partial updates (`PATCH /me`, `/blocks/:id`, `/events/:id`) use
`jsonPatch { set(...) }` so "clear" and "unchanged" are distinguishable.

**Device calendars:** only busy time ranges (never titles) from calendars the user marked as
busy are uploaded for the next 120 days via `PUT /calendars/device`, with a stable per-install
`deviceId`. Uploads run on launch, on calendar provider changes (debounced ContentObserver
while the process runs) and every 6 hours (WorkManager).

**Glass on Android:** there is no backdrop blur, so panels are the translucent `glass` fill over
orbs that are blurred with `RenderEffect` (API 31+) or drawn as soft radial gradients (API 26–30).
The panel shadow is painted outside the shape only (like CSS `box-shadow`) and needs API 28+.

## Tests

```sh
./gradlew testDebugUnitTest
```

Unit tests cover error-body parsing, JSON encoding rules and the timing-preset ⇄ `Requirement` mapping.

## Connecting Google / Microsoft calendars

The app calls `POST /api/v1/calendars/oauth/:provider/url { redirect: "availabliss://calendars" }`
with its bearer token, opens the returned consent `url` in a Custom Tab, and handles the
`availabliss://calendars?connected=<id>` (or `?error=`) return. iCloud uses the CalDAV form
(`POST /calendars/caldav`, app-specific password).

## API notes and assumptions

- **All-day ends** are exclusive: an all-day event or proposal ends at local midnight after its last
  day (documented in docs/API.md).
- **`BusyInterval.calendarId`** equals `CalendarConnection.calendars[].id` (used for the calendar
  colour dots on My time).
- **Not in the API:** an attendee count on `GroupEvent` (the "N going" line uses the member count) and
  the group owner's region on `Group` (the bank-holiday subtitle says "group owner's region"). Owners
  can delete a group but not leave it.
- **Repeating busy blocks** are edited and deleted as a whole series; the API has no per-occurrence
  exceptions.
