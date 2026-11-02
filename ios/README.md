# Availabliss for iOS

Native SwiftUI client for the Availabliss API (`docs/API.md`), styled after the signed-off
concept (`docs/DESIGN.md`, `docs/design-concept/`).

- iOS 17+, SwiftUI, Swift 6 language mode (strict concurrency), `@Observable`, async/await
- **One third-party dependency:** [RevenueCat](https://github.com/RevenueCat/purchases-ios-spm)
  (Swift Package Manager, declared in `project.yml`) for Pro purchases. The paywall is our own
  (`Features/Plan/UpgradeSheet.swift`); RevenueCatUI isn't used. Google sign-in uses OAuth 2.0 +
  PKCE in `ASWebAuthenticationSession` and exchanges the code for an ID token (iOS OAuth clients
  are public clients — no secret), so the GoogleSignIn SDK isn't needed.

## Setup

```sh
brew install xcodegen
cp ios/Config/Secrets.example.xcconfig ios/Config/Secrets.xcconfig   # then edit it
make ios-open        # = cd ios && xcodegen generate && open Availabliss.xcodeproj
```

`Availabliss.xcodeproj` is generated — don't commit it; edit `project.yml` instead.

### Configuration (`Config/*.xcconfig`)

| Setting | What |
| ------- | ---- |
| `API_BASE_URL` | Host serving `/api/v1` and `/api/auth`. Write URLs as `https:/$()/host` (`//` starts an xcconfig comment). For `make dev` in the simulator: `http:/$()/localhost:5190`. |
| `ASSOCIATED_DOMAIN_HOST` | Host for universal links and passkeys (`applinks:` + `webcredentials:`), no scheme. Must serve `/.well-known/apple-app-site-association`. |
| `GOOGLE_IOS_CLIENT_ID` / `GOOGLE_REVERSED_CLIENT_ID` | iOS OAuth client from Google Cloud Console. Empty = Google button hidden. |
| `DEVELOPMENT_TEAM` | Your Apple team ID. |
| `APS_ENVIRONMENT` | `development` or `production`. |

`Base.xcconfig` holds committed defaults; `Secrets.xcconfig` (git-ignored) overrides them.
Values reach the app through Info.plist keys (`AVAPIBaseURL`, …) read by `App/AppConfig.swift`.

### Capabilities

`project.yml` writes the entitlements; enable the matching capabilities on the App ID
(`dev.joeherbert.availabliss`) in the developer portal:

- **Sign in with Apple** — and add the bundle id / Services ID to the server's Apple provider
  config so Better Auth accepts the identity token audience.
- **Associated Domains** — `applinks:` (invite links, `/app/groups/:id`, `/app/proposals/:id`) and
  `webcredentials:` (passkeys). On the server set `APPLE_TEAM_ID` and `IOS_BUNDLE_ID` so the
  AASA file lists this app. The passkey relying-party ID must equal `ASSOCIATED_DOMAIN_HOST`.
- **Push Notifications** — create an APNs key (`.p8`) and configure the server with it.
  The app registers its token with `POST /devices` after sign-in.
- **Background Modes** — Background fetch (calendar refresh, `BGAppRefreshTask` id
  `dev.joeherbert.availabliss.calendar-refresh`) and Remote notifications.

### Pro purchases (RevenueCat)

Pro is sold through RevenueCat on every platform; see `docs/BILLING.md` for the RevenueCat project,
entitlement, offering and webhook. For the iOS app:

1. **App Store Connect**: create the auto-renewable subscriptions (e.g. `pro_monthly`,
   `pro_annual`, in one subscription group) and any lifetime purchase, and sign the Paid
   Applications agreement. Products come from App Store Connect; nothing is hard-coded in the app.
2. **RevenueCat**: add the App Store app (bundle id `dev.joeherbert.availabliss`) with its App Store
   Connect API / in-app purchase key, attach the products to the `pro` entitlement and to the
   *current* offering (`$rc_annual`, `$rc_monthly`, …).
3. **Server**: set `REVENUECAT_IOS_KEY` to the app's **public** SDK key. The app reads it from
   `GET /me/plan` (`purchase.ios`), so there's no key in the build; while it's unset the upgrade
   sheet says to upgrade on the web instead.
4. **Xcode**: the In-App Purchase capability is on by default for App IDs; no entitlement is
   needed. To test in the simulator without App Store Connect, add a StoreKit configuration file
   to the scheme (Edit Scheme → Run → Options) with matching product ids.

The app configures RevenueCat lazily (the first time the upgrade sheet opens) with the signed-in
user's id as the app user id, logs out of RevenueCat on sign-out, and calls
`POST /me/plan/refresh` after a purchase or restore; the server stays the source of truth for Pro.
Grant Pro without a purchase for testing with `make set-plan EMAIL=… PLAN=pro DAYS=1`.

### Google client id

1. Google Cloud Console → Credentials → Create OAuth client ID → **iOS**, bundle id
   `dev.joeherbert.availabliss`.
2. Put the client id and its reversed form in `Secrets.xcconfig`. The reversed id is registered as
   a URL scheme automatically (redirect `com.googleusercontent.apps.…:/oauth2redirect`).
3. The server's Google provider must accept this iOS client id as an ID-token audience (Better Auth
   `google.clientId` can list several ids).

### Fonts

See `Availabliss/Resources/Fonts/README.md` — drop in the Bricolage Grotesque and Instrument Sans
TTFs. The app falls back to the system font if they're missing.

## Architecture

```
Availabliss/
  App/            entry point, AppDelegate (APNs), AppModel (DI container + lifecycle), Router, RootView, tabs
  Core/
    Networking/   APIClient (one generic send), Endpoint<Response>, API (every endpoint), APIError, JSON coding
    Auth/         Keychain, SessionStore, AuthService (OTP, Apple, Google, passkeys), ASAuthorization / web-auth wrappers
    Live/         SSE client for GET /api/v1/live with fan-out AsyncStreams and backoff reconnects
    Push/         APNs registration
    DeepLinks/    URL + push payload → DeepLink
    Billing/      RevenueCat wrapper for Pro purchases
    Calendar/     EventKit: permission, busy calendars, device sync upload, add-to-calendar, background refresh
    Utilities/    date/text formatting
  Models/         Codable types mirroring docs/API.md (CalendarDay / WallTime for YYYY-MM-DD / HH:MM)
  DesignSystem/   Theme tokens (colour scheme × colour theme), Glass, Orbs, buttons, SegmentedControl, Chip,
                  switch style, Avatar(Stack), GroupTile, StatusPill, rings, TabBar, BottomSheet, Stripes
  Features/       one folder per screen: View + ViewModel
```

- Every request goes through `APIClient.send(_:)` with a typed `Endpoint<Response>` from `API`.
  Dates are ISO-8601 with fractional seconds; non-2xx bodies decode to `APIError`; a 401 signs out.
- Auth: the Better Auth bearer token comes from the `set-auth-token` header and lives in the Keychain.
- Theme: `ThemeProvider` resolves `Theme` from the colour scheme and the user's `colorTheme`
  (from `GET /me`) and puts it in the environment; Appearance changes apply instantly and are saved
  with `PATCH /me`.
- Device calendars: only start/end times from calendars marked busy are uploaded
  (`PUT /calendars/device`, next 120 days, stable device id in the Keychain) on launch,
  on `EKEventStoreChanged` (debounced) and from a `BGAppRefreshTask`.
- Live updates: the SSE stream runs while the app is active; screens subscribe in `.task` and
  refetch / patch state per event type. Push covers the background.

## Building and testing

```sh
make ios-build                              # simulator build (IOS_SIM="iPhone 18 Pro")
make seed-demo && make ios-uitest          # sign in + tour, screenshots in ios/build/uitest-screens
```

Both targets use Xcode even if `xcode-select` still points at the Command Line Tools
(run `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` to make it permanent).
`ios-uitest` resets the simulator's Keychain first, so each run starts signed out.
For the local dev server set `API_BASE_URL = http:/$()/localhost:5190` in
`Config/Secrets.xcconfig`; `NSAllowsLocalNetworking` lets debug builds use plain http locally.
