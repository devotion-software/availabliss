# Error and performance monitoring (Sentry)

Every platform reports to [Sentry](https://sentry.io): crashes and errors, a sample of traces, and
session replays. Without a DSN the SDK stays off, so development and CI send nothing.

| Platform | SDK | Where it starts | DSN from |
| -------- | --- | --------------- | -------- |
| Web server (API, pages, background jobs) | `@sentry/sveltekit` | `web/src/instrumentation.server.ts` | `PUBLIC_SENTRY_DSN` (runtime env) |
| Web browser | `@sentry/sveltekit` | `web/src/hooks.client.ts` | `PUBLIC_SENTRY_DSN`, handed over by `web/src/env.ts` |
| iOS app | `sentry-cocoa` (SPM) | `CrashReporting.start` in `AppModel.init` | `SENTRY_DSN` in `ios/Config/Secrets.xcconfig` |
| Android app | `io.sentry:sentry-android` + Gradle plugin | `CrashReporting.start` in `AvailablissApp.onCreate` | `availabliss.sentryDsn` in `android/local.properties` |

The share extension and widgets on iOS don't report: they're small, short-lived and memory-limited.

## Setting up Sentry

1. Make three projects in one Sentry organisation: **SvelteKit** (`availabliss-web`, used by the
   server and the browser), **iOS** (`availabliss-ios`) and **Android** (`availabliss-android`).
2. Make an organisation auth token (Settings → Auth Tokens) for uploading source maps, dSYMs and
   ProGuard mappings. It's a secret; DSNs are not.
3. Set the DSNs and upload settings below.

### Web (Coolify)

- Runtime environment: `PUBLIC_SENTRY_DSN`, and optionally `PUBLIC_SENTRY_ENVIRONMENT` (defaults
  to `production`; use e.g. `staging` for another deployment).
- Build: the build arguments `SENTRY_ORG` and `SENTRY_PROJECT`, and `SENTRY_AUTH_TOKEN` as a
  **build secret** (the Dockerfile mounts it for the build step only). Without the token the
  build still works, but browser stack traces stay minified.
- The browser sends to `*.ingest.sentry.io`, which the Content-Security-Policy allows
  (`connect-src`, and `worker-src blob:` for replays) in `web/vite.config.ts`.

Locally: `docker build --secret id=SENTRY_AUTH_TOKEN,env=SENTRY_AUTH_TOKEN --build-arg SENTRY_ORG=… --build-arg SENTRY_PROJECT=availabliss-web .`

### iOS

In `ios/Config/Secrets.xcconfig` (git-ignored):

```
SENTRY_DSN = https:/$()/<key>@o123.ingest.sentry.io/456
SENTRY_AUTH_TOKEN = sntrys_...
SENTRY_ORG = your-org
SENTRY_PROJECT = availabliss-ios
```

Note the `$()` trick: `//` starts a comment in xcconfig files. Archiving (a Release build) uploads
the dSYMs through the "Upload dSYMs to Sentry" build phase when the token is set and
[sentry-cli](https://docs.sentry.io/cli/installation/) is installed
(`brew install getsentry/tools/sentry-cli`). Run `make ios-project` after changing `project.yml`.

### Android

In `android/local.properties` (git-ignored), or as Gradle properties / `SENTRY_*` environment
variables in CI:

```properties
availabliss.sentryDsn=https://<key>@o123.ingest.sentry.io/456
sentry.authToken=sntrys_...
sentry.org=your-org
sentry.project=availabliss-android
```

Release builds upload their ProGuard mapping when the token is set.

## What's collected

- **Errors and crashes**, everywhere. The web server also reports every `log.error` (mostly failures
  that were caught, such as background jobs), tagged with its scope and the request id, so an
  event can be matched to its log lines.
- **Traces**: 10% in production, all of them elsewhere. Trace headers only go to our own API.
- **Session replays**: 10% of sessions in production, and every session that hits an error.
  All text, inputs and images are masked, so a replay shows the layout and taps, never what's on
  screen.
- **Who**: the signed-in user's id only. No email, name or IP address.

Kept out on purpose: cookies, request headers (the bearer token), request and response bodies,
query strings (picture and attachment links carry a signature), database values, AI planner
prompts, screenshots and the view hierarchy. On the web this is `DATA_COLLECTION` in
`web/src/lib/sentry.ts`; on iOS and Android it's in `CrashReporting`.

Releases are `availabliss-web@<package.json version>`, and the app's bundle id with its version and
build number on iOS and Android, so a regression can be traced to the version that brought it in.
