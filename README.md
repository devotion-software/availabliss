# Availabliss

Find the time that works for everyone. Connect your calendars (or mark busy
times by hand), create a group, say what you're looking for (a weekend, an
evening from 6, two hours on a weekday, school holidays only …) and Availabliss
highlights the dates where everyone is free. Propose one, everyone accepts, and
it lands in your calendar with one tap.

| Part | Stack | Folder |
| ---- | ----- | ------ |
| Web app + JSON API | SvelteKit 3 (Svelte 5), Better Auth, Drizzle ORM, MariaDB, Redis, Bun | [`web/`](web/) |
| iOS app | SwiftUI, EventKit, APNs (XcodeGen project) | [`ios/`](ios/) |
| Android app | Kotlin, Jetpack Compose, CalendarContract, FCM | [`android/`](android/) |
| Docs | API contract, design system, signed-off design concept | [`docs/`](docs/) |

## Getting started (web + API)

Prerequisites: [Bun](https://bun.sh) ≥ 1.3 and Docker.

```bash
make setup     # web/.env from the example, bun install, MariaDB + Redis + Mailpit, migrations
make dev       # http://localhost:5190
```

Sign in with any email address: the one-time code is caught by Mailpit at
<http://localhost:8027>. Google, Apple and Microsoft sign-in / calendars need
the credentials described in [`web/.env.example`](web/.env.example).

Run `make help` for every task. The useful ones:

| Command | What it does |
| ------- | ------------ |
| `make dev` | Web app + API with hot reload |
| `make test` | Unit tests (no database needed) |
| `make test-integration` | Service tests against the dev database (`DATABASE_URL`); they create and delete their own users and groups |
| `make smoke` | End-to-end API test against the running dev server |
| `make seed-demo` / `make clean-demo` | Create / delete demo users, groups, busy time and chat to explore the apps |
| `make check` | Type-check |
| `make db-generate` / `make db-migrate` | Create / apply a migration after schema changes |
| `make school-holidays FILE=…csv` | Import school holiday dates per local authority |
| `make docker-build` / `make docker-run` | Build and run the production image |
| `make ios-open` / `make ios-build` | Generate and open the Xcode project / build for the simulator |
| `make ios-uitest` | Sign in on the simulator as the demo user and tour every main screen (XCUITest) |
| `make android-build` / `make android-test` | Build the Android debug APK / run its unit tests |
| `make android-tour` | Sign in on the emulator as the demo user and tour every main screen |

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to `main`
and every pull request, and only builds the platforms a change touches:

| Job | Runs |
| --- | ---- |
| Web | type-check, unit tests with coverage, production build |
| Smoke | the production build against MariaDB + Mailpit, then `scripts/smoke.ts` end to end |
| Docker | builds the Coolify image |
| Android | unit tests and the debug APK |
| iOS | XcodeGen + a simulator build |

## How it fits together

- **One API for every client.** The SvelteKit app serves the web UI and
  `/api/v1` ([contract](docs/API.md)). The web uses a session cookie; the native
  apps send `Authorization: Bearer <token>` (Better Auth `bearer` plugin).
- **Privacy.** Only busy *time ranges* are stored: Google uses the freeBusy API,
  Microsoft and CalDAV read start/end only, and native apps upload ranges from
  on-device calendars. Labels on manual blocks are private to their owner.
  Calendar credentials are encrypted at rest (AES-256-GCM, `SECRETS_KEY`).
- **Availability engine** (`web/src/lib/server/availability/`) is pure and unit
  tested: it expands a group's requirement into candidate slots, checks every
  synced member is free, and returns per-day states (hidden / busy / free /
  match) plus which weekday columns are worth drawing.
- **Live updates** stream over Server-Sent Events (`/api/v1/live`), fanned out
  across instances by Redis pub/sub; native apps also get APNs / FCM pushes.
- **Holidays.** Bank holidays come from [Nager.Date](https://date.nager.at)
  (cached in the database). School holidays come from the `school_holidays`
  table (import a CSV per local authority) or ranges a group enters itself.

## Deploying (Coolify)

The root [`Dockerfile`](Dockerfile) builds the web app exactly like Duet: Bun
builds, a slim Node image runs `scripts/migrate.js` and then the server on port
3000. In Coolify:

1. New resource → this repository → **Dockerfile** build pack (base directory `/`).
2. Add a MariaDB database and set `DATABASE_URL`.
3. Add a Redis database and set `REDIS_URL`.
4. Set the environment variables from `web/.env.example`, at minimum
   `BETTER_AUTH_SECRET`, `BETTER_AUTH_URL` (the public URL), `SECRETS_KEY`,
   `RESEND_API_KEY` and `EMAIL_FROM`.
5. Health check: `GET /api/v1/health` (it also reports `redis: ok | down | off`).

Shared state lives in Redis ([`web/src/lib/server/redis.ts`](web/src/lib/server/redis.ts)):
live updates fan out over pub/sub, native sign-in hand-off codes and rate-limit
counters are expiring keys, and a lock stops every instance running the
background calendar sync at once. Instances are stateless, so you can run more
than one. Without `REDIS_URL` the app falls back to in-memory state, which only
works with a single instance.
