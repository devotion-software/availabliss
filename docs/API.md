# Availabliss API (v1)

> **Machine-readable contract:** [`openapi.json`](openapi.json) (OpenAPI 3.1) is generated from
> `web/src/lib/api-contract.ts` (bodies and queries from the routes' zod schemas, responses from
> `web/src/lib/types.ts` and `web/src/lib/api-types.ts`) by `make openapi` / `bun run openapi`. Don't
> edit it by hand: CI fails when it's out of date, and a unit test fails when a route under
> `web/src/routes/api/v1` isn't in the contract. This file stays the prose guide.

The SvelteKit app in `web/` serves both the web UI and this JSON API. The iOS and
Android apps are clients of exactly the same endpoints.

- Base path: `/api/v1`
- Auth endpoints (Better Auth): `/api/auth/*`
- Content type: `application/json` (UTF-8) for requests and responses.
- Timestamps: ISO 8601 UTC strings (`2026-11-13T18:00:00.000Z`).
- Dates (calendar days): `YYYY-MM-DD`. Wall-clock times: `HH:MM` (24h).
- IDs: opaque strings (36 chars max).
- Weekdays: integers `0` = Monday … `6` = Sunday.

## Authentication

| Client  | How requests are authenticated |
| ------- | ------------------------------ |
| Web     | Better Auth session cookie (set by the sign-in endpoints). |
| Native  | `Authorization: Bearer <token>` (Better Auth `bearer` plugin). The token is returned in the `set-auth-token` response header of every successful sign-in call. Store it in the Keychain / EncryptedSharedPreferences. |

### Sign-in flows

All sign-in calls return `{ "user": User, ... }` plus the `set-auth-token` header.

| Method | Calls |
| ------ | ----- |
| Email one-time code | `POST /api/auth/email-otp/send-verification-otp` `{ "email", "type": "sign-in" }` → then `POST /api/auth/sign-in/email-otp` `{ "email", "otp" }` |
| Google (native) | Get an ID token with Google Sign-In / Credential Manager, then `POST /api/auth/sign-in/social` `{ "provider": "google", "idToken": { "token": "<id token>" } }` |
| Apple (native) | Get an identity token with `ASAuthorizationAppleIDProvider`, then `POST /api/auth/sign-in/social` `{ "provider": "apple", "idToken": { "token": "<identity token>", "nonce": "<raw nonce>" } }` |
| Apple / Google (web flow from a native app, e.g. Apple on Android) | Make a random `code_verifier` (43–128 chars) and keep it in the app. Open `GET /api/v1/auth/native/:provider/start?code_challenge=<base64url(SHA-256(code_verifier))>` in a Custom Tab / browser sheet → the server redirects to `availabliss://auth/callback?code=<one-time code>` (or `?error=`) → `POST /api/v1/auth/native/exchange` `{ "code", "code_verifier" }` → `{ "token" }`. Codes are single-use (a wrong verifier also spends one) and expire after 2 minutes. The callback only issues a code to the browser that went through `start` (a signed httpOnly cookie), for the session that sign-in created. |
| Google / Apple (web) | `POST /api/auth/sign-in/social` `{ "provider", "callbackURL": "/app" }` → redirect to the returned `url` |
| Passkey | `GET /api/auth/passkey/generate-authenticate-options` → platform WebAuthn assertion → `POST /api/auth/passkey/verify-authentication` `{ "response": <AuthenticationResponseJSON> }` |
| Add a passkey (signed in) | `GET /api/auth/passkey/generate-register-options` → platform attestation → `POST /api/auth/passkey/verify-registration` `{ "response": <RegistrationResponseJSON>, "name"? }` |
| Sign out | `POST /api/auth/sign-out` |

### Linking Apple / Google to an account

Native apps get an id token exactly as for sign-in and send it to `POST /api/v1/me/accounts/link`
(see Endpoints). The web links with a redirect: `POST /api/auth/link-social` `{ provider, callbackURL,
errorCallbackURL }`. If that sign-in already has its own account the redirect comes back with
`?error=account_already_linked_to_different_user`; after the person confirms, the web calls
`POST /api/v1/me/accounts/merge-intent` (which sets a cookie in this browser), then signs in with the
provider with `callbackURL: "/link/complete"`. That route merges the account just signed in to into
the one that started, moves the session over and redirects to `/app/settings?merged=<provider>`
(or `?link_error=merge_expired | merge_not_signed_in | merge_wrong_account | merge_failed`).

Passkeys on native need the relying-party domain associated with the app. The web
app serves `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`
from the `APPLE_TEAM_ID`, `IOS_BUNDLE_ID`, `ANDROID_PACKAGE` and
`ANDROID_SHA256_FINGERPRINTS` env vars.

## Errors

Non-2xx responses have the body:

```json
{ "message": "Human readable message", "code": "validation_error", "issues": [ { "path": "name", "message": "Required" } ] }
```

`code` is one of `unauthorized` (401), `upgrade_required` (402), `forbidden` (403),
`not_found` (404), `validation_error` (400), `conflict` (409), `rate_limited` (429),
`server_error` (500). `conflict` also covers acting on something no longer open:
responding to or cancelling a cancelled proposal, overriding a reply or confirming once it's confirmed, or voting in a closed poll. `upgrade_required` means the action needs Pro (see Plans): show
the upgrade screen rather than a plain error.
`issues` is only present for `validation_error`.

### Rate limits

Every `/api/v1` request counts against a per-user budget (per IP when signed
out): 600 reads and 120 writes a minute signed in, 120 requests a minute signed
out. Some endpoints have tighter limits of their own:

| Endpoint | Limit |
| -------- | ----- |
| `POST /auth/native/exchange` | 10 / minute per IP |
| `GET /invites/:token`, `POST /invites/:token/accept` | 30 / 10 minutes per IP |
| `GET /live` | 20 connections / minute |
| `POST /calendars/:id/sync` | 6 / minute |
| `POST /calendars/caldav`, `POST /calendars/oauth/:provider/url`, `POST /calendars/oauth/:provider/complete` | 10 / 10 minutes |
| `PUT /calendars/device` | 30 / minute |
| `POST /groups` | 20 / hour |
| `POST /groups/:id/invites` | 30 / hour |
| `GET /usernames/:username` | 60 / minute |
| `POST /groups/:id/messages` | 30 / minute |
| `POST /groups/:id/attachments` | 60 / 10 minutes |
| `GET /groups/:id/availability[/day]` | 120 / minute |
| `POST /requirements/preview` | 120 / minute |
| `POST /groups/:id/planner` | 20 / hour |
| `POST /groups/:id/planner/draft` | 30 / hour |
| `GET /regions/detect` | 30 / minute per IP |
| `POST /api/auth/email-otp/send-verification-otp` | 5 / minute per IP and 5 / 15 minutes per email address |
| `POST /api/auth/sign-in/email-otp` | 10 / minute per IP |

Over a limit the response is `429` with `code: "rate_limited"`, a readable
`message` and a `Retry-After` header in seconds. Limits are enforced in
production (`RATE_LIMIT=on|off` overrides).

## Shared types

```ts
/**
 * Pictures (`User.image`, `Member.image`, `Group.image`, `GroupEvent.image` …) are private files
 * sent as signed links, `<host>/media/<key>?e=<expiry>&s=<signature>`. A link works for 7–8 days and
 * stays the same all day, so cache by URL; it changes daily. Without a valid signature /media is 404.
 * A picture from a sign-in provider (e.g. Google) is its own URL, passed through as it is.
 */
type Role = 'owner' | 'admin' | 'member';
type ThemeMode = 'light' | 'dark' | 'system';
type ColorTheme = 'dawn' | 'lagoon' | 'sunset' | 'orchid' | 'grove';

interface User {
  id: string;
  name: string;
  email: string;
  username: string | null;       // lowercase, no "@"; every account gets one when it's created (null only briefly for older accounts)
  image: string | null;
  themeMode: ThemeMode;
  colorTheme: ColorTheme;
  country: string | null;        // ISO 3166-1 alpha-2, e.g. "GB"
  subdivision: string | null;    // ISO 3166-2, e.g. "GB-ENG"
  schoolAuthority: string | null;// id from GET /regions/school-authorities
  timezone: string;              // IANA, e.g. "Europe/London"
  onboarded: boolean;            // false until the user finishes calendar setup
  eventReminders: 'both' | 'day' | 'hour' | 'off'; // pushes before confirmed plans you're going to (day before / hour before)
  showAddToCalendar: boolean;                 // "Add to calendar" on plans (off for people who subscribe to their plans instead)
  autoArchiveNewGroups: boolean;              // groups you create start with auto-archive on (default false)
  busyBufferMinutes: 0 | 15 | 30 | 60 | 120; // counted as busy before and after each busy time when finding free slots and clashes (not in /me/busy)
}

interface Member {
  userId: string;
  name: string;     // the name they go by in this group (nameInGroup), else their name, or their email address if they haven't set one
  nameInGroup: string | null;    // the name they chose for this group; null = their account name
  image: string | null;
  role: Role;
  synced: boolean;               // has a connected calendar or any manual block
  joinedAt: string;
}

/** What a group is looking for. See "Requirements" below. */
interface Requirement {
  length:
    | { type: 'duration'; minutes: number }                    // e.g. 120 = 2 hours
    | { type: 'days'; count: number }                          // whole consecutive days, 7 = a week
    | { type: 'span'; startDay: number; startTime: string;     // e.g. Fri 18:00 → Sun 23:59
        endDay: number; endTime: string; weeksLater: 0 | 1 };  // weeksLater 1 = "Tue → following Thu"
  window: { from: string; to: string } | null;  // daily time window, e.g. evening { "18:00", "23:59" }; null = whole day
  days: number[];                              // allowed weekdays (ignored for span)
  bankHolidays: boolean;                       // bank holidays count as allowed days
  schoolHolidaysOnly: boolean;                 // only inside school holidays (group's authority or customSchoolHolidays)
  schoolAuthority: string | null;
  customSchoolHolidays: DateRange[];
  range: DateRange;                            // search window
  dates: DateRange[];                          // if non-empty, ONLY these date ranges are considered (repeats of an identical range are dropped on save)
  timezone: string;                            // IANA zone the times above are in
  quorum?: number | null;                      // "most of us are free": a time works when at least this many required members are free (null/absent = everyone)
  optionalMemberIds?: string[];                // members who never stop a time from working
}

interface DateRange { from: string; to: string } // inclusive YYYY-MM-DD

interface GroupSummary {
  id: string;
  name: string;
  color: string;            // tile colour token: 't1' … 't5'
  monogram: string;         // 1–3 chars
  image: string | null;     // the group's photo, shown instead of the monogram tile
  role: Role;
  archived: boolean;        // an admin archived it: list it in a collapsed "Archived" section
  memberCount: number;
  syncedCount: number;
  requirementSummary: string; // "A weekend · Fri 18:00 → Sun · Nov – Jan"
  status:                     // drives the badge on the groups list
    | { kind: 'matches'; count: number; noun: 'weekend' | 'evening' | 'day' | 'week' | 'trip' | 'slot' } // "2 weekends work"
    | { kind: 'needs_reply'; proposalId: string }
    | { kind: 'confirmed'; eventId: string; start: string }
    | { kind: 'searching' };
  unreadCount: number;
  memberPreview: { userId: string; name: string; image: string | null }[]; // first 5, for avatar stacks
}

interface Group extends Omit<GroupSummary, 'status' | 'unreadCount' | 'memberPreview'> {
  requirement: Requirement;
  members: Member[];
  autoArchive: boolean;     // archive it automatically once its event has passed (see below); off by default
  rhythm: { every: 'week' | 'two-weeks' | 'month'; prompt: boolean } | null; // how often it meets; see "Rhythm" below
  createdAt: string;
}
```

**Rhythm.** A group that meets regularly can set `rhythm` (weekly, fortnightly or monthly: the same
weekday in the same week of the next month, "the first Friday"). `GET /groups/:id/next` → `{ suggestion:
{ target: 'YYYY-MM-DD', slot: Slot | null } | null }` gives the free time nearest the next one (a week or
so either side, everyone-free first; null without a rhythm). With `prompt: true`, once a plan has ended and
nothing newer is planned or proposed, the server posts "Same again? … works for everyone" in the chat and
sends a `reminder` (`data.reminder: 'next'`, with the slot's `start`/`end`), once per plan.

**Automatic archiving.** With `autoArchive` on, the server archives the group (for everyone, as an admin
archiving it would; `group.updated` is published) once its latest event has ended and nothing else is planned:
no event still to come or under way, and no open proposal for a time still ahead. It's checked every 15 minutes.
It happens once per event: after someone unarchives it, it isn't archived again until a newer event has passed.
A new proposal or event in an archived group with `autoArchive` on unarchives it.

## Requirements

`Requirement.length` picks the shape of a candidate slot:

| UI preset | Requirement |
| --------- | ----------- |
| 2 hours / 4 hours | `{ "type": "duration", "minutes": 120 }`, `window` = when it may happen (e.g. `08:00`–`23:00`) |
| An evening from 6 | `{ "type": "days", "count": 1 }`, `window: { "from": "18:00", "to": "23:59" }` |
| One day | `{ "type": "days", "count": 1 }`, `window: null` |
| A weekend | `{ "type": "span", "startDay": 4, "startTime": "18:00", "endDay": 6, "endTime": "23:59", "weeksLater": 0 }` |
| Tue → Thu | `{ "type": "span", "startDay": 1, "startTime": "00:00", "endDay": 3, "endTime": "23:59", "weeksLater": 0 }` |
| Tue → following Thu | as above with `"weeksLater": 1` |
| A week | `{ "type": "days", "count": 7 }` |

A slot "works" when every **synced** member is free for the whole slot. With a `quorum` (or optional members),
it works when at least `quorum` of the synced, non-optional members are free; such slots carry
`missingMemberIds` (everyone synced who's busy then, optional members included; `[]` when nobody is),
`match` days matched only by those get `partial: true`, and the day view adds `mostFree` stretches.
Rank slots with fewer missing members first. Without either field, nothing changes. Members who
haven't synced are ignored and reported in `pendingMemberIds`. A member counts as
synced once they have connected any calendar or marked any busy time by hand.

Server calendars are kept at least three years ahead, and as far as any of the
member's groups search (up to ten). Phones upload as far ahead as the server asks
(`CalendarConnection.syncUntil`), in fixed ranges so only changed ones are re-sent.
Where a member's calendars stop short of the range, they're listed in
`checkedUntil`; their busy time past that date is unknown, so they're treated as
free there, like an unsynced member.

- An empty `days` list means any day of the week.
- Bank holidays follow the region (`country` / `subdivision`) of the group owner.
- `duration` slots are reported as each maximal free stretch at least `minutes`
  long inside the window, so a proposal can pick any part of it.

## Endpoints

General rules (also in the generated [`openapi.json`](openapi.json)):

- `POST`s that create something answer **`201`** (blocks, CalDAV connections, groups, events, proposals,
  messages, preferences, invites, busy ignores, templates, attachments); everything else answers `200`.
- Endpoints that take a body need a JSON one, even when every field is optional: send `{}` (e.g.
  `POST /groups/:id/leave`, `/notifications/read`, `/groups/:id/planner`); a missing body is `400`.
- Instants are ISO 8601 with `Z` or an offset.

Legend: 🔒 signed in · 👑 group admin or owner · ⭐ group owner

### Health

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET | `/health` | | `{ ok: boolean; version: string; db: 'ok' \| 'down'; redis: 'ok' \| 'down' \| 'off' }` (`503` when the database doesn't answer; Redis being down still returns `200`) |

### Notification settings

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me/notification-settings` | | `NotificationSettings` |
| PATCH 🔒 | `/me/notification-settings` | `{ off?: NotificationCategory[], quietHours?: { from: 'HH:MM', to: 'HH:MM' } \| null }` | `NotificationSettings` |
| PUT 🔒 | `/groups/:id/mute` | `{ until: string \| null }` (ISO instant; null = until unmuted) | `NotificationSettings` |
| DELETE 🔒 | `/groups/:id/mute` | | `NotificationSettings` |

```ts
type NotificationCategory = 'chat' | 'proposals' | 'plans' | 'reminders' | 'nudges';
interface NotificationSettings {
  off: NotificationCategory[];                 // pushes (and the confirmation email) turned off
  quietHours: { from: string; to: string } | null; // local times in User.timezone; may wrap midnight
  mutes: { groupId: string; until: string | null }[]; // active mutes only
}
```

Before each push the server leaves out anyone who turned that category off (chat = `message`;
proposals = `proposal.created`, `proposal.changed`; plans = `proposal.confirmed`, `event.created`;
reminders = `reminder`; nudges = `nudge`), muted the group, or is in quiet hours. Invites and role
changes always push. Activity still records everything.

### My plans

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me/plans` | | `{ going: MyPlan[]; notGoing: MyPlan[]; past: MyPlan[] }`: every plan across your groups. `going`: upcoming ones you said yes or maybe to, haven't replied to, or that were added straight to the calendar (soonest first); `notGoing`: upcoming ones you said you can't make; `past`: the last year, newest first (at most 100) |
| GET 🔒 | `/me/plans.ics` | | `text/calendar` with every plan in `going` ("Add all to calendar" on the web) |

```ts
type MyPlan = GroupEvent & { groupName: string; myResponse: ResponseKind | null }; // null: added straight to the calendar
```

### Calendar feed (webcal)

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me/calendar-feed` | | `{ url, webcalUrl }`: your secret feed link (made the first time) |
| POST 🔒 | `/me/calendar-feed/reset` | | `{ url, webcalUrl }`: a new link; the old one stops working |
| GET | `/calendar/<token>.ics` (outside `/api/v1`; optional `?group=<id>`) | | `text/calendar`: your groups' plans (from 60 days back, leaving out ones you said you can't make), stable `UID`s and a rising `SEQUENCE`, so calendar apps that subscribe pick up edits and cancellations. `404` for an unknown or reset link |

Offer `webcalUrl` to Apple Calendar / Outlook, `https://calendar.google.com/calendar/r?cid=<webcalUrl>` for Google Calendar, and `url` to copy.

### Security reports

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| POST | `/csp-report` | a browser's Content-Security-Policy violation report (`application/csp-report` or Reporting API JSON) | `204`. Logged as `[csp] violation`; the web app's report-only policy sends these (see `csp` in `web/vite.config.ts`) |

### Me

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me` | | `User` |
| PATCH 🔒 | `/me` | `Partial<Pick<User, 'name' \| 'username' \| 'themeMode' \| 'colorTheme' \| 'country' \| 'subdivision' \| 'schoolAuthority' \| 'timezone' \| 'onboarded' \| 'busyBufferMinutes' \| 'eventReminders' \| 'showAddToCalendar' \| 'autoArchiveNewGroups'>>` | `User` (`409` if the username is taken; changing `busyBufferMinutes` publishes `availability.changed` to the user's groups) |
| GET 🔒 | `/usernames/:username` | | `UsernameCheck`: whether it can be yours, and who has it (to invite them) |
| GET 🔒 | `/me/busy?from&to` (ISO instants, `to` after `from`, at most 120 days apart) | | `{ intervals: BusyInterval[] }` (own merged busy time; `label` is only ever returned to the owner) |
| PUT 🔒 | `/me/avatar` | the image bytes (JPEG / PNG / WebP / HEIC, ≤ 10 MB) with its `image/*` content type | `User` (`image` is the new picture, shrunk to fit 512 px) |
| DELETE 🔒 | `/me/avatar` | | `User` (no picture: initials) |
| POST 🔒 | `/me/busy/ignores` | `{ calendarId, eventKey, start, end, series?: boolean }` | `{ id }` — that calendar event (or with `series`, every occurrence) no longer counts as busy |
| DELETE 🔒 | `/me/busy/ignores/:id` | | 204 — it counts as busy again |
| DELETE 🔒 | `/me` | | `204` (deletes the account and all data) |
| GET 🔒 | `/me/accounts` | | `{ linked: { provider: 'apple' \| 'google'; linkedAt; email: string \| null }[]; available: ('apple' \| 'google')[] }` (Apple / Google sign-ins on the account, and which ones this server can link) |
| POST 🔒 | `/me/accounts/link` | `{ provider: 'apple' \| 'google', idToken, nonce?, merge? }` | `{ linked: true, merged }`, or `409` `{ message, code: 'conflict', account: { name, email, groups } }` when that sign-in already has its own account. Show it, then send again with `merge: true` to merge it into this account (its groups, busy time, calendars, chats, templates and Pro move here; the other account is deleted). Native only: the id token comes from Sign in with Apple / Google. An id token that can't be verified (e.g. expired while the merge prompt was open) is `400`, never `401`, so it can't be mistaken for the session ending. |
| POST 🔒 | `/me/accounts/merge-intent` | `{ provider }` | `204` + a short-lived cookie (web only; see Linking on the web) |

```ts
interface BusyInterval {
  start: string; end: string; source: 'calendar' | 'manual'; label: string | null; calendarId: string | null; blockId: string | null;
  // Calendar busy only:
  title?: string | null;    // the event's name (label is the calendar's name). Stored encrypted; only ever sent to its owner
  eventKey?: string;        // opaque key of the event / its series ('' if the calendar gives no ids)
  recurring?: boolean;      // the event repeats, so "not busy" can cover every occurrence
  ignoreId?: string | null; // set when you've marked it not busy (it doesn't count as busy for anyone)
}
```

Usernames are 3–20 characters: lowercase letters, numbers, `_` and `.` (a dot can't come first,
last or twice in a row). They're sent with or without a leading `@`, in any case, and stored lowercase.

```ts
interface UserSummary { id: string; username: string; name: string; image: string | null }
interface UsernameCheck {
  username: string;          // as normalized
  problem: string | null;    // why it can't be used as written ("At least 3 characters" …)
  available: boolean;        // valid, and nobody else has it
  user: UserSummary | null;  // who has it (you included)
}
```

### Plans (Pro)

Free accounts can own 3 groups (joining other people's is unlimited); Pro is
unlimited. Pro is bought through RevenueCat in the App Store, Play Store or on
the web (RevenueCat Web Billing / Stripe), always with **the user's id as the
RevenueCat app user id**, so it follows the account to every platform. See
[BILLING.md](BILLING.md) for the RevenueCat setup.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me/plan` | | `Plan` |
| POST 🔒 | `/me/plan/refresh` | | `Plan` (re-reads RevenueCat; call after a purchase or restore) |
| POST | `/billing/revenuecat` | RevenueCat webhook | `{ ok: true }` (`Authorization` must equal `REVENUECAT_WEBHOOK_AUTH`) |

```ts
interface Plan {
  tier: 'free' | 'pro';
  proUntil: string | null;      // renews / ends; null on free or lifetime
  lifetime: boolean; willRenew: boolean;
  store: string | null;         // app_store, play_store, stripe, rc_billing, promotional
  groupLimit: number | null;    // null = unlimited
  groupsOwned: number; canCreateGroup: boolean;
  // RevenueCat public SDK keys; null hides purchase UI on that platform.
  purchase: { ios: string | null; android: string | null; web: string | null; entitlement: string };
  aiPlanner: { available: boolean; included: boolean }; // set up on the server / usable on this plan
}
```

`POST /groups` returns `402 upgrade_required` when a free account already owns 3.

### AI planner (optional, Pro)

Offered only when the server has `ANTHROPIC_API_KEY` set (`Plan.aiPlanner.available`)
and usable on Pro (`Plan.aiPlanner.included`). Nothing runs unless someone asks.
Claude sees the group's name and timing, the slots that already work for
everyone, named holidays, member counts and the asker's notes, never names,
calendars or busy time.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| POST 🔒 | `/groups/:id/planner` | `{ notes?: string (≤500) }` | `PlannerResult` (`402 upgrade_required` without Pro, `409` when no time works yet; 20 an hour) |
| POST 🔒 | `/groups/:id/planner/draft` | `{ start, end, notes? }` | `{ title, note }`: Pro, an AI-drafted proposal title and message for the time you picked (`404` when AI isn't set up here, `402 upgrade_required` without Pro; 30 an hour) |

```ts
interface PlannerResult {
  best: { slot: Slot; why: string };
  alternatives: { slot: Slot; why: string }[];   // up to 3
  ideas: { title: string; description: string }[];
  checklist: string[];
  proposalTitle: string; proposalNote: string;   // prefill for "Propose this"
}
```

### Timing templates

A user's saved group timings, reused when creating groups. When applying one,
clients restart its search range from today (same length) and drop specific
dates that are already over.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me/templates` | | `{ templates: RequirementTemplate[] }` (sorted by name) |
| POST 🔒 | `/me/templates` | `{ name, requirement }` | `RequirementTemplate` (max 50 per user) |
| PATCH 🔒 | `/me/templates/:id` | `{ name?, requirement? }` | `RequirementTemplate` |
| DELETE 🔒 | `/me/templates/:id` | | `204` |

```ts
interface RequirementTemplate { id: string; name: string; requirement: Requirement; createdAt: string }
```

### Regions & holidays

| Method | Path | Response |
| ------ | ---- | -------- |
| GET | `/regions/countries` | `{ countries: { code: string; name: string }[] }` |
| GET | `/regions/subdivisions?country=GB` | `{ subdivisions: { code: string; name: string }[] }` |
| GET | `/regions/school-authorities?country=GB` | `{ authorities: { id: string; name: string }[] }` |
| GET | `/regions/detect` | `{ country: string \| null; subdivision: string \| null; timezone: string \| null }` (from request headers / IP; best-effort) |
| GET | `/holidays?country&subdivision&from&to` (dates, at most 400 days apart) | `{ holidays: { date: string; name: string }[] }` |

### Calendars

```ts
type CalendarProvider = 'google' | 'microsoft' | 'caldav' | 'device';
interface CalendarConnection {
  id: string;
  provider: CalendarProvider;
  label: string;                // "you@gmail.com", "iCloud", "Sam's iPhone"
  status: 'ok' | 'error' | 'reauth_required';
  lastSyncedAt: string | null;
  calendars: { id: string; name: string; color: string | null; busy: boolean }[];
  syncUntil: string | null;     // phone calendars: how far ahead the app should read (null for server calendars)
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/calendars` | | `{ connections: CalendarConnection[] }` |
| GET 🔒 | `/calendars/oauth/:provider/start?redirect=` | | `302` to Google / Microsoft consent. `provider` = `google` \| `microsoft`. `redirect` = where to land afterwards (web path, or `availabliss://calendars` for native, opened via ASWebAuthenticationSession / Custom Tabs). |
| POST 🔒 | `/calendars/oauth/:provider/url` | `{ redirect? }` | `{ url }`: the provider consent URL with signed state embedded. **Native apps use this** and open `url` in ASWebAuthenticationSession / Custom Tabs (those can't send the bearer token). |
| GET | `/calendars/oauth/:provider/callback` | | Web (`redirect` is a path): links the calendar only if the session cookie belongs to the user who started the flow, then `302` to `redirect` with `?connected=<connectionId>` or `?error=` (`wrong_account` when it doesn't). Native (`availabliss://…`): links nothing and `302`s to `redirect` with `?complete=<code>&provider=<provider>` (or `?error=`). Each state works once. |
| POST 🔒 | `/calendars/oauth/:provider/complete` | `{ code }` | `CalendarConnection`. Native apps call this with the `complete` code from the callback. `403` if a different user started the flow, `400` if the code is used or older than 10 minutes. |
| POST 🔒 | `/calendars/caldav` | `{ username, appPassword, serverUrl? }` (defaults to iCloud) | `CalendarConnection` |
| PUT 🔒 | `/calendars/device` | `DeviceSync` | `CalendarConnection` |
| PATCH 🔒 | `/calendars/:connectionId/calendars/:calendarId` | `{ busy: boolean }` | `CalendarConnection` |
| POST 🔒 | `/calendars/:connectionId/sync` | | `CalendarConnection` ("Sync now": pulls a Google / Outlook / iCloud connection straight away and returns it with `status` and `lastSyncedAt`; a failed sync shows as `status: 'error'` or `'reauth_required'`, not an HTTP error. At most once per connection per 30 s: a call in between returns the connection as it is. Phone calendars aren't pulled; the app uploads with `PUT /calendars/device`.) |
| DELETE 🔒 | `/calendars/:connectionId` | | `204` |

Calendar change notifications (called by Google and Microsoft, not by clients;
no auth, not rate limited). Each channel carries a token derived from
`SECRETS_KEY` and the calendar, and anything without a matching one is ignored.
A valid notification syncs that calendar's connection a few seconds later.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| POST | `/calendars/push/google` | none (`X-Goog-Channel-ID`, `X-Goog-Channel-Token`, `X-Goog-Resource-State` headers) | `200` |
| POST | `/calendars/push/microsoft?validationToken=` | | `200`, the token echoed as `text/plain` (Graph's check when a subscription is created) |
| POST | `/calendars/push/microsoft` | Graph change notifications `{ value: [{ subscriptionId, clientState, changeType, resourceData }] }` | `202` |

```ts
/** Native apps upload busy time from on-device calendars. Titles never leave the device. */
interface DeviceSync {
  deviceId: string;             // stable per install
  deviceName: string;           // "Sam's iPhone"
  rangeStart: string;           // window the busy list covers; replaces previous data in it
  rangeEnd: string;             // (busy outside the window is ignored; only differences are written)
  coveredUntil?: string;        // a sync sent as several ranges: the end of the whole sync (defaults to rangeEnd)
  more?: boolean;               // more ranges of this sync follow: groups are refreshed once, after the last
  calendars: { externalId: string; name: string; color: string | null; busy?: boolean }[];
  busy: { calendarExternalId: string; start: string; end: string; eventKey?: string; title?: string | null }[]; // title: the event's name (stored encrypted, only shown to you)
}
```

### Manual busy blocks ("My time")

```ts
interface ManualBlock {
  id: string;
  start: string; end: string;   // first occurrence
  allDay: boolean;
  repeat: 'none' | 'daily' | 'weekdays' | 'weekly' | 'fortnightly'
        | 'monthly'          // same date each month (skipped in months without it)
        | 'monthlyWeekday'   // same weekday of the month, e.g. the 2nd Tuesday (the last one if it starts in a month's last 7 days)
        | 'yearly';          // same date each year (29 Feb: leap years only)
  until: string | null;         // YYYY-MM-DD, last date a repeat can occur
  note: string | null;          // private to the owner
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/blocks` | | `{ blocks: ManualBlock[] }` |
| POST 🔒 | `/blocks` | `Omit<ManualBlock, 'id'>` | `ManualBlock` |
| PATCH 🔒 | `/blocks/:id` | `Partial<Omit<ManualBlock, 'id'>>` | `ManualBlock` |
| DELETE 🔒 | `/blocks/:id` | | `204` |

My time is for blocking time ahead: busy time only matters for finding dates to come. A new block
that has already passed (a one-off that ended before yesterday in your profile's time zone, or a
repeat whose `until` is before yesterday) is `400` "That time has already passed". Earlier today is
fine, and the day's grace covers a phone in another time zone. Edits aren't checked, so a series that
started long ago can still be changed or ended. The apps only open today onwards: past days are shown
muted and can't be tapped, and the date pickers start at today (or an edited block's own start).

### Groups

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups` | | `{ groups: GroupSummary[] }` |
| POST 🔒 | `/groups` | `{ name, color?, requirement? }` | `Group` (creator is owner; `402 upgrade_required` past the free limit) |
| GET 🔒 | `/groups/:id` | | `Group` |
| PATCH 👑 | `/groups/:id` | `{ name?, color?, requirement?, archived?, autoArchive?, rhythm? }` | `Group` (publishes `group.updated`; `archived: true` archives it for everyone, `false` unarchives; `autoArchive` turns automatic archiving on or off) |
| PUT 👑 | `/groups/:id/image` | the image bytes (as `/me/avatar`) | `Group` (`image` shrunk to fit 512 px; publishes `group.updated`) |
| DELETE 👑 | `/groups/:id/image` | | `Group` (no photo: the monogram tile) |
| DELETE ⭐ | `/groups/:id` | | `204` |
| PATCH 🔒 | `/groups/:id/members/:userId` | `{ role?: 'admin' \| 'member', displayName?: string \| null }` (at least one) | `Member`. `role`: admins only (they get a `role.changed` notification). `displayName`: the name they go by in this group (null or blank clears it); yours, or as an admin anyone's. Publishes `member.changed` |
| PATCH 🔒 | `/groups/:id/members/me` | `{ displayName: string \| null }` (trimmed, ≤ 80; null or blank clears it) | `Member` (the name you go by in this group; publishes `member.changed`. See Names in a group) |
| DELETE 👑/self | `/groups/:id/members/:userId` | | `204` (an admin removes someone; never the owner. Yourself = leave, as `POST /groups/:id/leave` with no new owner) |
| POST 🔒 | `/groups/:id/leave` | `{ newOwnerId?: string }` (the owner only: the admin who becomes owner) | `204` (see Leaving a group) |
| POST 👑 | `/groups/:id/nudge` | | `{ nudged: number }` (push/email to unsynced members, rate limited to once per 12h) |
| GET 🔒 | `/groups/:id/preferences` | | `{ preferences: GroupPreference[] }` (every member's) |
| POST 🔒 | `/groups/:id/preferences` | `{ kind: 'dates', from, to }` or `{ kind: 'weekly', days?: Weekday[], window?: { from, to } \| null }` | `GroupPreference` |
| DELETE 🔒 | `/groups/:id/preferences/:prefId` | | 204 (your own only) |
| GET 👑 | `/groups/:id/invites` | | `{ invites: GroupInvite[] }` (live ones, newest first: unexpired and not used up; joining also removes any other invites to that group sent to you) |
| POST 👑 | `/groups/:id/invites` | `{ emails?: string[], usernames?: string[], userIds?: string[], email?: string, maxUses?: number \| null }` (≤ 20 people; `maxUses` 1–1000 or null for a link) | `{ token, url, expiresAt, invites: GroupInvite[] }` (token/url/expiresAt: the first) |
| DELETE 👑 | `/groups/:id/invites/:inviteId` | | 204 (the link stops working) |
| GET | `/invites/:token` | | `{ groupName, inviterName, memberCount, expiresAt, email: string \| null, username: string \| null, personal: boolean }` (`personal`: sent to one person's Activity, so they can decline it) |
| POST 🔒 | `/invites/:token/accept` | | `Group & { joined: boolean }` (`joined`: false when you were already a member; the apps then skip asking what name to go by. 403 if the invite was for a different address or username; 404 "This invite link has been used up" once a link is full) |
| POST 🔒 | `/invites/:token/decline` | | 204 (an invite sent to you in Activity: it's removed, with its Activity item; nobody is told. 403 for anyone else's invite or a link) |

**Names in a group.** Each member can choose the name they go by in one group (`PATCH /groups/:id/members/me`).
Within that group it replaces their account name everywhere the server names them: `Member.name` (so the members
list, chat authors, proposal replies and busy lists, which the apps resolve by `userId`), reply quotes
(`replyTo.authorName`), the invites list (`invitedBy`), system messages ("Sam can't make it any more") and the
Activity items and pushes about that group. Outside the group (invite emails and previews, `/me`) it's always the
account name. System messages keep the name used when they were posted.

**Leaving a group.** Anyone can leave, but a group always keeps an owner, so always at least one admin:

- A member, or an admin while the owner stays, just leaves.
- The owner names an existing admin (`newOwnerId`) who becomes the owner as they leave. No `newOwnerId`, or one that
  isn't another admin of the group, is `400 validation_error` (issue path `newOwnerId`). Bank holidays then follow the
  new owner's region.
- If nobody else is an admin, leaving as an admin is `409 conflict` ("Make someone else an admin before you leave"):
  promote someone with `PATCH /groups/:id/members/:userId` `{ role: 'admin' }` first.
- The only member can't leave (`409 conflict`): they delete the group instead.

The new owner gets a `role.changed` notification (see Notifications & devices). Everyone left in the group gets
`member.changed` with the new owner's `Member` (when ownership moved), then
`{ userId, removed: true }` for the leaver; the leaver's own apps get that too, so they close the group.

With no emails, an invite is a link anyone can use until it expires (14 days), by up to `maxUses`
people: a number from 1 to 1000, or `null` for no limit. Left out it's 1 (single-use, as before, so
older apps keep making single-use links). Each person who joins with it counts once (someone already in
the group doesn't); joining and counting happen together, so people joining at the same moment can't
go past the limit, and anyone who misses out isn't added. Once full it's off the invites list, and
`GET /invites/:token` and accept say `404` "This invite link has been used up" until it expires. With emails, each
address gets its own emailed invite that only someone signed in with that address can accept; it's
used up once they join. With usernames, each person gets their own invite in Activity (an
`invite.received` notification and a push, `data: { token }`) that only they can accept; it's used
up once they join. A username nobody has is `404`, and someone already in the group is `409`,
before any invite is sent. `userIds` works like `usernames` (an `invite.received` in their Activity, only
for them, whether or not they have a username) but only for people who are already in a group with you:
anyone else is `404`, before any invite is sent. The apps use it for "New group with these people": they
create the group (`POST /groups`) and then invite the people they kept ticked. Someone invited this way can
accept (`POST /invites/:token/accept`) or decline (`POST /invites/:token/decline`).

```ts
// Preferred times are guidelines: they never block a date. Availability marks the slots and
// `match` days they fit with `preferredBy: string[]` (user ids). For Pro viewers each slot also has
// `score` ("best date": preferences weighted by who's compromised in recent plans, plus notice) and
// `fairFor: string[]` (who it suits that missed out or said maybe last time); sort by score.
interface GroupPreference { id: string; userId: string; kind: 'dates' | 'weekly'; from: string | null; to: string | null; days: Weekday[]; window: { from: string; to: string } | null; label: string }
// name: the account name of the person it was sent to in Activity (by username or id), else null.
// maxUses: how many people can join with it (null: no limit; always 1 for an invite for one person); uses: how many have.
interface GroupInvite { id: string; email: string | null; username: string | null; name?: string | null; url: string; maxUses: number | null; uses: number; expiresAt: string; createdAt: string; invitedBy: string }
```

### Availability

| Method | Path | Response |
| ------ | ---- | -------- |
| POST 🔒 | `/requirements/preview` with `{ requirement }` | `{ count, from, to, summary }`: slots the requirement produces before anyone's busy time is considered (the timing editor's "13 weekends fit this timing") |
| GET 🔒 | `/groups/:id/availability?from&to` | `Availability` |
| GET 🔒 | `/groups/:id/availability/day?date` | `DayAvailability` |

```ts
interface Availability {
  from: string; to: string;               // YYYY-MM-DD
  visibleWeekdays: number[];              // weekday columns worth drawing (others can never match)
  days: AvailabilityDay[];                // one per date in [from, to]
  slots: Slot[];                          // every slot that works, sorted by start
  syncedCount: number; memberCount: number;
  pendingMemberIds: string[];             // not synced yet
  checkedUntil: { userId: string; until: string }[]; // synced members whose calendars are only known up to `until`
                                          // (ISO instant) before the end of this range: later days may hide clashes
}
interface AvailabilityDay {
  date: string;
  status: 'hidden' | 'busy' | 'free' | 'match';  // hidden = can't be part of any slot
  busyMemberIds: string[];                // synced members busy during the relevant window that day
  slotPosition: 'start' | 'middle' | 'end' | 'single' | null; // for drawing multi-day slots as one band
  holiday: string | null;                 // bank / school holiday name
}
interface Slot { start: string; end: string; label: string } // label: "Fri 13 – Sun 15 Nov"

interface DayAvailability {
  date: string;
  window: { start: string; end: string } | null;  // the part of the day the requirement cares about
  // Each member's busy time, merged. Calendar and hand-marked busy look the same: `manual` is always false
  // (kept so older app builds still decode).
  members: { userId: string; pending: boolean; busy: { start: string; end: string; manual: boolean }[] }[];
  everyoneFree: { start: string; end: string }[];
}
```

### Proposals

```ts
type ResponseKind = 'pending' | 'accepted' | 'maybe' | 'declined'; // maybe = a soft yes ("if I must"); clients must treat unknown values as pending
interface Proposal {
  id: string; groupId: string;
  title: string | null; location: string | null; note: string | null;
  start: string; end: string; allDay: boolean;
  status: 'open' | 'confirmed' | 'cancelled';
  createdBy: string; createdAt: string;
  eventId: string | null;               // set once confirmed
  confirmedBy?: string | null;          // confirmed before everyone accepted ("Confirm anyway"): who did it; null otherwise
  responses: { userId: string; response: ResponseKind; setBy: string | null; updatedAt: string;
               droppedOut?: boolean }[];   // confirmed: said they can't make it after it was confirmed, not yet reviewed
  needsReview?: boolean;                // confirmed: someone who was coming dropped out; the creator or an admin should keep or cancel it
  busyMemberIds: string[];              // open proposals: members busy at this time (empty otherwise)
}
```

A time doesn't have to work for everyone to be proposed. Clients warn first
(`/groups/:id/conflicts`) and show `busyMemberIds` while people reply.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups/:id/proposals?status=` | | `{ proposals: Proposal[] }` |
| POST 🔒 | `/groups/:id/proposals` | `{ start, end, allDay?, title?, location?, note? }` | `Proposal` (creator auto-accepts; posts a chat card) |
| GET 🔒 | `/groups/:id/conflicts?start=&end=` | | `{ busyMemberIds: string[] }` (who's busy then; ISO instants, `end` after `start`, at most 120 days apart) |
| GET 🔒 | `/proposals/:id` | | `Proposal` |
| POST 🔒 | `/proposals/:id/respond` | `{ response: 'accepted' \| 'maybe' \| 'declined' }` | `Proposal` (open, or confirmed: see below) |
| POST 👑 | `/proposals/:id/override` | `{ userId, response: ResponseKind }` | `Proposal` |
| POST 🔒 | `/proposals/:id/confirm` | | `Proposal` ("Confirm anyway": creator or admin; open only; `409` until someone besides the caller has accepted) |
| POST 🔒 | `/proposals/:id/keep` | | `Proposal` ("Keep the plan": creator or admin; confirmed only; clears `needsReview`) |
| DELETE 🔒 | `/proposals/:id` | | `204` (creator or admin; marks cancelled; a confirmed plan's event is deleted too) |

A `maybe` is counted apart from `accepted` and doesn't confirm a proposal: only when every member
has firmly accepted does it confirm itself (the creator or an admin can still "Confirm anyway";
the chat message then says e.g. "3 of 5 can make it, 1 maybe"). Show maybes beside the yeses
("3 in · 1 maybe") and rank dates by firm yeses first.

When every member has accepted, the proposal becomes `confirmed`, an `Event` is
created, and every member gets a push notification and a `proposal.confirmed`
live event. The creator or an admin can also confirm it before then with
`POST /proposals/:id/confirm` ("Confirm anyway", e.g. when someone can't make
it); the same happens, with `confirmedBy` set, a chat message "It's on for …
(3 of 4 can make it)" instead of "Everyone's in for …", and a push titled
"It's on". Replies stay as they were, so `responses` says who's coming
(`accepted`), who can't (`declined`) and who never replied (`pending`). Members
with no response joined the group after it was confirmed.

Replies can still change once confirmed (members who joined since can't reply:
`409`). The plan stays confirmed. When someone who was coming says they can't
make it any more, their response gets `droppedOut: true`, the proposal
`needsReview: true`, the chat gets "Priya Shah can't make it any more", and the
creator and admins get a `proposal.changed` notification. One of them then
either keeps the plan (`POST /proposals/:id/keep`, which clears the review;
they stay as can't make it) or cancels it (`DELETE /proposals/:id`, which
deletes the event, posts "… cancelled the plan for …" and notifies everyone
else with `proposal.changed`). Someone changing back to `accepted` just updates
(and clears their `droppedOut`). Copies people added to their own calendars are
left alone, as when an event is deleted.

### Events

```ts
interface GroupEvent {
  id: string; groupId: string; proposalId: string | null;
  title: string; location: string | null; note: string | null;
  start: string; end: string; allDay: boolean;   // all-day: end is the exclusive midnight after the last day
  image: string | null;                          // cover photo
  createdBy: string; createdAt: string;
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups/:id/events?from&to` | | `{ events: GroupEvent[] }` |
| GET 🔒 | `/events/:id` | | `GroupEvent` |
| POST 🔒 | `/groups/:id/events` | `{ title, start, end, allDay?, location?, note? }` | `GroupEvent` |
| PATCH 🔒 | `/events/:id` | partial of the above | `GroupEvent` (creator or admin) |
| DELETE 🔒 | `/events/:id` | | `204` (creator or admin) |
| PUT 🔒 | `/events/:id/image` | the image bytes (as `/me/avatar`) | `GroupEvent` (`image` shrunk to fit 1600 px; creator or admin; publishes `event.changed`) |
| DELETE 🔒 | `/events/:id/image` | | `GroupEvent` (no cover) |
| GET 🔒 | `/events/:id/ics` | | `text/calendar` file (web "Add to calendar"; native uses EventKit / CalendarContract directly) |
| GET 🔒 | `/me/upcoming` | | `{ events: (GroupEvent & { groupName: string })[] }` (next 60 days, all groups) |

### Chat

```ts
interface Message {
  id: string; groupId: string;
  authorId: string | null;              // null for system messages
  kind: 'text' | 'poll' | 'proposal' | 'event' | 'system';
  body: string | null;
  poll: Poll | null;
  proposalId: string | null;
  eventId: string | null;
  createdAt: string;
  editedAt: string | null;              // when the author last edited it
  deleted: boolean;                     // deleted by its author: show "This message was deleted" (body is null)
  receipt: { delivered: number; seen: number; recipients: number; seenBy: string[] } | null; // only on your own messages; seenBy in the order they read it
  replyTo: MessageReply | null;         // the message this one replies to (text messages only)
  attachments: Attachment[];            // photos, GIFs and files sent with it, in order ([] for most messages and once deleted)
  caption: string | null;               // with attachments: the caption, or null. Null for messages without attachments
  location: MessageLocation | null;     // a place shared in the chat (kind 'text'); null otherwise and once deleted
}
// A shared place is a one-off pin (not live tracking). Its `body` is a fallback for older apps:
// "📍 Dishoom Shoreditch" (or "📍 Location" without a name), the address if any, and a Google Maps link,
// one per line. Apps that know `location` show a card instead. It can't be edited, only deleted.
interface MessageLocation {
  lat: number; lng: number;             // WGS 84 degrees
  name: string | null;                  // a place's name; null for the sender's own position
  address: string | null;
  accuracy: number | null;              // metres, for a device's position; null for a searched place
}
// A text message can carry attachments with or without a caption. Its `body` is then the caption, or
// (with none) a one-line summary for older apps: "📷 Photo", "GIF", "📷 3 photos", "📎 Plan.pdf",
// "📎 2 files", "📎 2 attachments", "🎟️ Concert ticket", "🎟️ 2 passes". Apps that know `attachments`
// show those and `caption` instead.
interface Attachment {
  id: string;
  kind: 'image' | 'gif' | 'file' | 'pass'; // gif = animated (show `url` so it moves); pass = Apple Wallet pass.
                                        // More kinds may come: show an unknown kind as a file
  name: string;                         // file name, e.g. "Plan.pdf" or "IMG_0001.jpg"
  mime: string;
  size: number;                         // bytes stored
  width: number | null; height: number | null; // pixels, images and GIFs only
  url: string;                          // signed link to the file (no auth header needed); see below
  thumbnailUrl: string | null;          // smaller preview (at most 720 px) for photos, or a pass's logo (PNG, at most
                                        // 480×160); null for GIFs and files
  pass: WalletPass | null;              // kind 'pass' only: what to show on its card without downloading it
}
// An Apple Wallet pass (.pkpass, or a .pkpasses bundle of up to 10: details are the first one's). Read from
// its pass.json (English strings where it is localised); the file itself is kept exactly as sent (it's signed).
interface WalletPass {
  style: 'boardingPass' | 'eventTicket' | 'coupon' | 'storeCard' | 'generic';
  organizationName: string; description: string; // either may be ""
  logoText: string | null;
  passTypeIdentifier: string; serialNumber: string; // identify the pass (e.g. to tell whether it's already in Wallet)
  relevantDate: string | null;          // when it's for (event, departure), ISO 8601 UTC
  expirationDate: string | null; voided: boolean;
  eventName: string | null; venue: string | null;
  fields: { label: string | null; value: string; date: boolean }[]; // up to 4, most important first; with
                                        // `date` the value is an ISO 8601 UTC date-time to format locally
  backgroundColor: string | null; foregroundColor: string | null; labelColor: string | null; // "#rrggbb"
  count: number;                        // passes in the file (1 unless a .pkpasses bundle)
}
interface MessageReply {                // the quoted message, as it is now
  id: string;
  authorId: string | null; authorName: string | null;
  kind: 'text' | 'poll' | 'proposal' | 'event' | 'system';
  text: string | null;                  // one line, at most 140 characters (…): the text, or a poll's question, or the
                                        // attachment summary ("📷 Photo", "GIF", "📎 Plan.pdf") when sent without a caption, or
                                        // "📍 Dishoom Shoreditch" / "📍 Location" for a place; null once deleted
  deleted: boolean;                     // deleted since: show "Deleted message"
}
interface Poll {
  id: string; question: string; multi: boolean; closed: boolean;
  // A date poll's options carry `slot` (the time each stands for; label made in the group's time zone).
  // Show who's busy per option with the conflicts endpoint, and let its creator propose the winner.
  options: { id: string; label: string; voterIds: string[]; slot?: { start: string; end: string; allDay: boolean } | null }[];
}
interface MessageInfo {
  messageId: string; sentAt: string;
  seen: { userId: string; at: string }[];
  delivered: { userId: string; at: string }[];  // delivered but not seen
  pending: string[];                             // not delivered yet
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups/:id/messages?before=<messageId>&limit=50` | | `{ messages: Message[]; hasMore: boolean }` (newest last) |
| GET 🔒 | `/groups/:id/messages/search?q=<text>&limit=50` | | `{ messages: Message[]; hasMore: boolean }` (newest first): messages containing `q` (2–100 characters, any case) in their text or caption, a poll's question or options, a file's name, or a shared place's name or address. Deleted messages and group updates aren't searched. `hasMore` when more than `limit` matched |
| GET 🔒 | `/groups/:id/attachments?kind=media\|files&before=<attachmentId>&limit=60` | | `{ items: { messageId, authorId, createdAt, attachment: Attachment }[]; hasMore: boolean }`: the chat's media page. `media` (the default) is photos and GIFs, `files` is files and Wallet passes; newest first (in order within a message), `createdAt` being when its message was sent. Pass the last item's attachment id as `before` for the next page (`404` if it isn't in this group). Attachments of deleted messages and unsent uploads aren't listed |
| POST 🔒 | `/groups/:id/attachments` | `multipart/form-data` with one file in the field `file` | `201` `Attachment`, not yet in any message: send its id in `attachmentIds`. Photos (JPEG, PNG, WebP, GIF, HEIC where the server can read it; up to 15 MB) are turned upright, stripped of metadata (location included), shrunk to at most 2560 px and stored as JPEG (PNG if transparent), so `name`, `mime` and `size` can differ from what was sent; an animated GIF is kept as it is. Files (up to 25 MB) must be one of: pdf, txt, md, csv, rtf, ics, vcf, doc(x), xls(x), ppt(x), odt, ods, odp, pages, numbers, key, epub, zip, mp3, m4a, wav, mp4, mov, pkpass, pkpasses (by extension; a file sent as `application/vnd.apple.pkpass` without one gets `.pkpass`); anything else is `400`. A Wallet pass whose pass.json can be read comes back as `kind: 'pass'` with `pass` details (else as a plain `file`). Uploads not sent within a day are deleted |
| POST 🔒 | `/groups/:id/messages` | `{ body, replyToId? }`, or `{ attachmentIds: string[] (1–10), body?: caption, replyToId? }`, or `{ location: { lat (−90…90), lng (−180…180), name?, address?, accuracy? (metres, ≥ 0) }, replyToId? }`, or `{ poll: { question, options: string[], multi? } }`, or a date poll `{ poll: { question, slots: { start, end, allDay? }[] (2–5, different), multi? (default true) } }` | `Message`. `replyToId` must be a message in the same chat (else `400`), not a group update (`400`) and not deleted (`409`). `attachmentIds` must be your own unsent uploads to this group (else `400`) |
| POST 🔒 | `/groups/:id/messages/receipts` | `{ upTo: messageId, state: 'delivered' \| 'seen' }` | `204` |
| PATCH 🔒 | `/messages/:id` | `{ body }` | `Message` (the sender's own text messages only; a location can't be edited: `400`). With attachments `body` is the caption and may be `""` to remove it |
| DELETE 🔒 | `/messages/:id` | | `204` (the sender's own text messages only; it stays in the chat as deleted, and its attachments are deleted for good) |
| GET | `/attachments/:id?e=…&s=…`, `/attachments/:id/thumb?e=…&s=…` | | The file (or a photo's preview). These are the signed `url` / `thumbnailUrl` links from `Attachment`: no session needed, valid for 7–8 days and unchanged within a day (cacheable); refetch the message for fresh ones. Without a valid signature, only a signed-in member of the group gets it (else `404`). Photos, GIFs and Wallet passes are served `inline` (a pass as `application/vnd.apple.pkpass`, so Safari offers to add it to Wallet); files as a download (`Content-Disposition: attachment` with the name); add `&download=1` to download a photo. Not rate limited. Deleting the group deletes every attachment sent in it |
| GET 🔒 | `/messages/:id/info` | | `MessageInfo` (any member of the group) |
| GET 🔒 | `/messages/:id/thread` | | `{ rootId: string; messages: Message[]; truncated: boolean }`: the thread the message is in. `rootId` is the first message of its chain of replies (following `replyTo` up); `messages` is that message and every reply to it, and replies to those, oldest first (deleted ones included), at most 200 (the earliest; `truncated` when there were more). Any member of the group |
| POST 🔒 | `/polls/:id/vote` | `{ optionIds: string[] }` (empty = remove vote) | `Poll` |
| POST 🔒 | `/polls/:id/close` | | `Poll` (only the poll's author) |
| PATCH 🔒 | `/polls/:id` | `{ question?, multi?, options?: { id?, label }[] }` | `Poll` (only its author, while open; publishes `poll.updated`). `options` (text polls only, 2–10, in order): an `id` keeps that option and its votes, no `id` adds one, one left out goes with its votes. Switching to pick-one keeps each person's first pick |

### Live updates

`GET /api/v1/live` 🔒 is a Server-Sent Events stream covering every group the user
belongs to. Each event is `data: <json>\n\n` with:

```ts
interface LiveEvent {
  type:
    | 'message.created' | 'message.updated' | 'message.receipts' | 'poll.updated'
    | 'proposal.created' | 'proposal.updated' | 'proposal.confirmed'
    | 'event.changed' | 'group.updated' | 'member.changed' | 'availability.changed'
    | 'notification.created';
  groupId: string | null;
  payload: unknown;                    // the affected resource (Message, Poll, Proposal, ...)
}
```

Payloads by type: `message.created` → `Message`; `message.updated` → `Message`
(edited or deleted; `receipt` is null, so keep your own; also refresh the `replyTo` of any
loaded message quoting it, since quotes aren't republished); `poll.updated` → `Poll`;
`proposal.created` / `proposal.updated` → `Proposal`; `proposal.confirmed` →
`{ proposal: Proposal, event: GroupEvent }`; `event.changed` → `GroupEvent` (or
`{ id, deleted: true }`); `group.updated` → `Group` (each member gets it with their own `role`); `member.changed` → `Member`
(or `{ userId, removed: true }`); `availability.changed` → `{ userId }` (refetch
availability); `notification.created` → `Notification`; `message.receipts` →
`{ userId, state, receipts: { messageId, delivered, seen, recipients, seenBy }[] }`
(the newest 500 changed messages; all of them are marked).

When a group is deleted, each member gets `member.changed` with their own
`{ userId, removed: true }`, as if they'd been removed. Deleting an account sends
the remaining members of each of its groups `member.changed` `{ userId, removed: true }`.

Native apps keep this open while foregrounded and rely on push otherwise.

### Notifications & devices

```ts
interface Notification {
  id: string; groupId: string | null;
  groupColor: string | null;           // the group's tile colour (t1–t5), behind the item's icon
  kind: 'proposal.created' | 'proposal.confirmed' | 'proposal.changed' | 'nudge' | 'invite.accepted' | 'invite.received' | 'message' | 'event.created' | 'reminder'
      | 'role.changed';                // clients show kinds they don't know with a generic icon and open the group (or proposal) from `data`
  title: string; body: string;
  data: Record<string, string>;        // e.g. { proposalId }
  readAt: string | null; createdAt: string;
}
```

**Reminders** (`kind: 'reminder'`): a job every 5 minutes sends, once each, a push and Activity item
the day before a confirmed plan (24 hours ahead; 09:00 the day before for all-day plans, in the
group's time zone) and an hour before it, to members going (yes or maybe; everyone for events added
directly), as each chose in `User.eventReminders`; `data` has `eventId`, `reminder: 'day' | 'hour'` and
`proposalId` when it came from one. A day before an open proposal, members who haven't replied get
one too (`data.proposalId`, `reminder: 'reply'`). Android shows them in the `reminder` channel.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/notifications` | | `{ notifications: Notification[]; unread: number }` |
| POST 🔒 | `/notifications/read` | `{ ids?: string[] }` (omit = all except chats) | `204` |
| POST 🔒 | `/devices` | `{ platform: 'ios' \| 'android', token, name? }` | `204` (registers APNs / FCM token) |
| DELETE 🔒 | `/devices/:token` | | `204` |

Chats (`kind: 'message'`) get **one Activity item per group**: each new message brings it to
the top with `title` = the group's name, `body` = `"Sam: See you there"` (or `"Sam: 📷 Photo"` for a photo without a caption, `"Sam: 📍 Dishoom Shoreditch"` for a place) and `data.count` = messages
since it was last read. It stays unread until the chat itself is read (a `seen` receipt), not
when Activity is opened. Updates to an existing item (a new message, or it being read) arrive
as `notification.created` with the **same id**: replace the item you have, and move it to the
top when its `createdAt` is newer.

`proposal.created` tells each other member "Priya Shah proposed Sat 12 Dec · 18:00–21:00. Can
you make it?" (`title` = the group's name, `data.proposalId`). The proposer gets their own item
too, "You proposed Sat 12 Dec · 18:00–21:00.", with `data.own = 'true'`: it is created already
read and sends no push.

`role.changed` tells someone their role in a group changed, with `data.role` the new one:
`admin` ("Sam Taylor made you an admin."), `member` ("You're no longer an admin. Sam Taylor
changed your role.") or `owner` ("You're now the owner. Sam Taylor left and handed the group to
you.", when the owner leaves). `title` is the group's name; it opens the group. Nobody is told
about a change to their own role, or a "change" to the role they already have.

Push payloads carry `{ kind, groupId, proposalId?, eventId?, messageId?, token? }` (`token`: an
`invite.received` invite, opened like `/invite/:token`) so the app
can deep link. On iOS these keys are **top-level** in the APNs payload, next to
`aps`; on Android they are the FCM `data` map. Android notification channel id = `kind`.

### Server configuration the native apps depend on

- `GOOGLE_CLIENT_IDS` must list the web client id first, then the iOS and Android
  client ids, so native Google ID tokens are accepted.
- `IOS_BUNDLE_ID` must be set for native Sign in with Apple tokens (audience = bundle id).
- Passkey ceremonies use Better Auth's challenge cookie: keep cookies between the
  `generate-*-options` and `verify-*` calls (default URLSession / OkHttp cookie jar).
- `APPLE_TEAM_ID` + `IOS_BUNDLE_ID` and `ANDROID_PACKAGE` + `ANDROID_SHA256_FINGERPRINTS`
  populate `apple-app-site-association` and `assetlinks.json`.

## Deep links

`availabliss://` (native) and `https://<host>/` (universal / app links):

| Link | Opens |
| ---- | ----- |
| `/invite/:token` | Invite acceptance |
| `/app/groups/:id` | Group calendar |
| `/app/groups/:id/chat` | Group chat |
| `/app/proposals/:id` | Proposal |
| `availabliss://calendars` | Calendar settings (OAuth return: `?complete=<code>` to finish with `POST /calendars/oauth/:provider/complete`, or `?error=`) |
