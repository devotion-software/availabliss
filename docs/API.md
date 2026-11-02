# Availabliss API (v1)

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
| Apple / Google (web flow from a native app, e.g. Apple on Android) | Open `GET /api/v1/auth/native/:provider/start` in a Custom Tab / browser sheet → the server redirects to `availabliss://auth/callback?code=<one-time code>` (or `?error=`) → `POST /api/v1/auth/native/exchange` `{ "code" }` → `{ "token" }`. Codes are single-use and expire after 2 minutes. |
| Google / Apple (web) | `POST /api/auth/sign-in/social` `{ "provider", "callbackURL": "/app" }` → redirect to the returned `url` |
| Passkey | `GET /api/auth/passkey/generate-authenticate-options` → platform WebAuthn assertion → `POST /api/auth/passkey/verify-authentication` `{ "response": <AuthenticationResponseJSON> }` |
| Add a passkey (signed in) | `GET /api/auth/passkey/generate-register-options` → platform attestation → `POST /api/auth/passkey/verify-registration` `{ "response": <RegistrationResponseJSON>, "name"? }` |
| Sign out | `POST /api/auth/sign-out` |

Passkeys on native need the relying-party domain associated with the app. The web
app serves `/.well-known/apple-app-site-association` and `/.well-known/assetlinks.json`
from the `APPLE_TEAM_ID`, `IOS_BUNDLE_ID`, `ANDROID_PACKAGE` and
`ANDROID_SHA256_FINGERPRINTS` env vars.

## Errors

Non-2xx responses have the body:

```json
{ "message": "Human readable message", "code": "validation_error", "issues": [ { "path": "name", "message": "Required" } ] }
```

`code` is one of `unauthorized` (401), `forbidden` (403), `not_found` (404),
`validation_error` (400), `conflict` (409), `rate_limited` (429), `server_error` (500).
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
| `POST /calendars/caldav`, `POST /calendars/oauth/:provider/url` | 10 / 10 minutes |
| `PUT /calendars/device` | 30 / minute |
| `POST /groups` | 20 / hour |
| `POST /groups/:id/invites` | 30 / hour |
| `POST /groups/:id/messages` | 30 / minute |
| `GET /groups/:id/availability[/day]` | 120 / minute |
| `POST /requirements/preview` | 120 / minute |
| `GET /regions/detect` | 30 / minute per IP |
| `POST /api/auth/email-otp/send-verification-otp` | 5 / minute per IP and 5 / 15 minutes per email address |
| `POST /api/auth/sign-in/email-otp` | 10 / minute per IP |

Over a limit the response is `429` with `code: "rate_limited"`, a readable
`message` and a `Retry-After` header in seconds. Limits are enforced in
production (`RATE_LIMIT=on|off` overrides).

## Shared types

```ts
type Role = 'owner' | 'admin' | 'member';
type ThemeMode = 'light' | 'dark' | 'system';
type ColorTheme = 'dawn' | 'lagoon' | 'sunset' | 'orchid' | 'grove';

interface User {
  id: string;
  name: string;
  email: string;
  image: string | null;
  themeMode: ThemeMode;
  colorTheme: ColorTheme;
  country: string | null;        // ISO 3166-1 alpha-2, e.g. "GB"
  subdivision: string | null;    // ISO 3166-2, e.g. "GB-ENG"
  schoolAuthority: string | null;// id from GET /regions/school-authorities
  timezone: string;              // IANA, e.g. "Europe/London"
  onboarded: boolean;            // false until the user finishes calendar setup
}

interface Member {
  userId: string;
  name: string;
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
  dates: DateRange[];                          // if non-empty, ONLY these date ranges are considered
  timezone: string;                            // IANA zone the times above are in
}

interface DateRange { from: string; to: string } // inclusive YYYY-MM-DD

interface GroupSummary {
  id: string;
  name: string;
  color: string;            // tile colour token: 't1' … 't5'
  monogram: string;         // 1–3 chars
  role: Role;
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
  createdAt: string;
}
```

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

A slot "works" when every **synced** member is free for the whole slot. Members who
haven't synced are ignored and reported in `pendingMemberIds`. A member counts as
synced once they have connected any calendar or marked any busy time by hand.

- An empty `days` list means any day of the week.
- Bank holidays follow the region (`country` / `subdivision`) of the group owner.
- `duration` slots are reported as each maximal free stretch at least `minutes`
  long inside the window, so a proposal can pick any part of it.

## Endpoints

Legend: 🔒 signed in · 👑 group admin or owner · ⭐ group owner

### Me

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/me` | | `User` |
| PATCH 🔒 | `/me` | `Partial<Pick<User, 'name' \| 'themeMode' \| 'colorTheme' \| 'country' \| 'subdivision' \| 'schoolAuthority' \| 'timezone' \| 'onboarded'>>` | `User` |
| GET 🔒 | `/me/busy?from&to` (ISO instants) | | `{ intervals: BusyInterval[] }` (own merged busy time; `label` is only ever returned to the owner) |
| DELETE 🔒 | `/me` | | `204` (deletes the account and all data) |

```ts
interface BusyInterval { start: string; end: string; source: 'calendar' | 'manual'; label: string | null; calendarId: string | null; blockId: string | null }
```

### Regions & holidays

| Method | Path | Response |
| ------ | ---- | -------- |
| GET | `/regions/countries` | `{ countries: { code: string; name: string }[] }` |
| GET | `/regions/subdivisions?country=GB` | `{ subdivisions: { code: string; name: string }[] }` |
| GET | `/regions/school-authorities?country=GB` | `{ authorities: { id: string; name: string }[] }` |
| GET | `/regions/detect` | `{ country: string \| null; subdivision: string \| null; timezone: string \| null }` (from request headers / IP; best-effort) |
| GET | `/holidays?country&subdivision&from&to` | `{ holidays: { date: string; name: string }[] }` |

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
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/calendars` | | `{ connections: CalendarConnection[] }` |
| GET 🔒 | `/calendars/oauth/:provider/start?redirect=` | | `302` to Google / Microsoft consent. `provider` = `google` \| `microsoft`. `redirect` = where to land afterwards (web path, or `availabliss://calendars` for native, opened via ASWebAuthenticationSession / Custom Tabs). |
| POST 🔒 | `/calendars/oauth/:provider/url` | `{ redirect? }` | `{ url }`: the provider consent URL with signed state embedded. **Native apps use this** and open `url` in ASWebAuthenticationSession / Custom Tabs (those can't send the bearer token). |
| GET | `/calendars/oauth/:provider/callback` | | `302` to `redirect` with `?connected=<connectionId>` or `?error=` |
| POST 🔒 | `/calendars/caldav` | `{ username, appPassword, serverUrl? }` (defaults to iCloud) | `CalendarConnection` |
| PUT 🔒 | `/calendars/device` | `DeviceSync` | `CalendarConnection` |
| PATCH 🔒 | `/calendars/:connectionId/calendars/:calendarId` | `{ busy: boolean }` | `CalendarConnection` |
| POST 🔒 | `/calendars/:connectionId/sync` | | `CalendarConnection` |
| DELETE 🔒 | `/calendars/:connectionId` | | `204` |

```ts
/** Native apps upload busy time from on-device calendars. Titles never leave the device. */
interface DeviceSync {
  deviceId: string;             // stable per install
  deviceName: string;           // "Sam's iPhone"
  rangeStart: string;           // window the busy list covers; replaces previous data in it
  rangeEnd: string;
  calendars: { externalId: string; name: string; color: string | null; busy?: boolean }[];
  busy: { calendarExternalId: string; start: string; end: string }[];
}
```

### Manual busy blocks ("My time")

```ts
interface ManualBlock {
  id: string;
  start: string; end: string;   // first occurrence
  allDay: boolean;
  repeat: 'none' | 'weekly' | 'fortnightly';
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

### Groups

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups` | | `{ groups: GroupSummary[] }` |
| POST 🔒 | `/groups` | `{ name, color?, requirement? }` | `Group` (creator is owner) |
| GET 🔒 | `/groups/:id` | | `Group` |
| PATCH 👑 | `/groups/:id` | `{ name?, color?, requirement? }` | `Group` |
| DELETE ⭐ | `/groups/:id` | | `204` |
| PATCH 👑 | `/groups/:id/members/:userId` | `{ role: 'admin' \| 'member' }` | `Member` |
| DELETE 👑/self | `/groups/:id/members/:userId` | | `204` (self = leave) |
| POST 👑 | `/groups/:id/nudge` | | `{ nudged: number }` (push/email to unsynced members, rate limited to once per 12h) |
| POST 👑 | `/groups/:id/invites` | `{ email?: string }` | `{ token, url, expiresAt }` |
| GET | `/invites/:token` | | `{ groupName, inviterName, memberCount, expiresAt }` |
| POST 🔒 | `/invites/:token/accept` | | `Group` |

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
  members: { userId: string; pending: boolean; busy: { start: string; end: string; manual: boolean }[] }[];
  everyoneFree: { start: string; end: string }[];
}
```

### Proposals

```ts
type ResponseKind = 'pending' | 'accepted' | 'declined';
interface Proposal {
  id: string; groupId: string;
  title: string | null; location: string | null; note: string | null;
  start: string; end: string; allDay: boolean;
  status: 'open' | 'confirmed' | 'cancelled';
  createdBy: string; createdAt: string;
  eventId: string | null;               // set once confirmed
  responses: { userId: string; response: ResponseKind; setBy: string | null; updatedAt: string }[];
  busyMemberIds: string[];              // open proposals: members busy at this time (empty otherwise)
}
```

A time doesn't have to work for everyone to be proposed. Clients warn first
(`/groups/:id/conflicts`) and show `busyMemberIds` while people reply.

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/groups/:id/proposals?status=` | | `{ proposals: Proposal[] }` |
| POST 🔒 | `/groups/:id/proposals` | `{ start, end, allDay?, title?, location?, note? }` | `Proposal` (creator auto-accepts; posts a chat card) |
| GET 🔒 | `/groups/:id/conflicts?start=&end=` | | `{ busyMemberIds: string[] }` (who's busy then; ISO instants) |
| GET 🔒 | `/proposals/:id` | | `Proposal` |
| POST 🔒 | `/proposals/:id/respond` | `{ response: 'accepted' \| 'declined' }` | `Proposal` |
| POST 👑 | `/proposals/:id/override` | `{ userId, response: ResponseKind }` | `Proposal` |
| DELETE 🔒 | `/proposals/:id` | | `204` (creator or admin; marks cancelled) |

When every member has accepted, the proposal becomes `confirmed`, an `Event` is
created, and every member gets a push notification and a `proposal.confirmed`
live event.

### Events

```ts
interface GroupEvent {
  id: string; groupId: string; proposalId: string | null;
  title: string; location: string | null; note: string | null;
  start: string; end: string; allDay: boolean;   // all-day: end is the exclusive midnight after the last day
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
  receipt: { delivered: number; seen: number; recipients: number; seenBy: string[] } | null; // only on your own messages; seenBy in the order they read it
}
interface Poll {
  id: string; question: string; multi: boolean; closed: boolean;
  options: { id: string; label: string; voterIds: string[] }[];
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
| POST 🔒 | `/groups/:id/messages` | `{ body }` or `{ poll: { question, options: string[], multi? } }` | `Message` |
| POST 🔒 | `/groups/:id/messages/receipts` | `{ upTo: messageId, state: 'delivered' \| 'seen' }` | `204` |
| GET 🔒 | `/messages/:id/info` | | `MessageInfo` (author only) |
| POST 🔒 | `/polls/:id/vote` | `{ optionIds: string[] }` (empty = remove vote) | `Poll` |
| POST 🔒 | `/polls/:id/close` | | `Poll` (poll author or admin) |

### Live updates

`GET /api/v1/live` 🔒 is a Server-Sent Events stream covering every group the user
belongs to. Each event is `data: <json>\n\n` with:

```ts
interface LiveEvent {
  type:
    | 'message.created' | 'message.receipts' | 'poll.updated'
    | 'proposal.created' | 'proposal.updated' | 'proposal.confirmed'
    | 'event.changed' | 'group.updated' | 'member.changed' | 'availability.changed'
    | 'notification.created';
  groupId: string | null;
  payload: unknown;                    // the affected resource (Message, Poll, Proposal, ...)
}
```

Payloads by type: `message.created` → `Message`; `poll.updated` → `Poll`;
`proposal.created` / `proposal.updated` → `Proposal`; `proposal.confirmed` →
`{ proposal: Proposal, event: GroupEvent }`; `event.changed` → `GroupEvent` (or
`{ id, deleted: true }`); `group.updated` → `Group`; `member.changed` → `Member`
(or `{ userId, removed: true }`); `availability.changed` → `{ userId }` (refetch
availability); `notification.created` → `Notification`; `message.receipts` →
`{ userId, state, receipts: { messageId, delivered, seen, recipients, seenBy }[] }`.

Native apps keep this open while foregrounded and rely on push otherwise.

### Notifications & devices

```ts
interface Notification {
  id: string; groupId: string | null;
  kind: 'proposal.created' | 'proposal.confirmed' | 'nudge' | 'invite.accepted' | 'message' | 'event.created';
  title: string; body: string;
  data: Record<string, string>;        // e.g. { proposalId }
  readAt: string | null; createdAt: string;
}
```

| Method | Path | Body | Response |
| ------ | ---- | ---- | -------- |
| GET 🔒 | `/notifications` | | `{ notifications: Notification[]; unread: number }` |
| POST 🔒 | `/notifications/read` | `{ ids?: string[] }` (omit = all) | `204` |
| POST 🔒 | `/devices` | `{ platform: 'ios' \| 'android', token, name? }` | `204` (registers APNs / FCM token) |
| DELETE 🔒 | `/devices/:token` | | `204` |

Push payloads carry `{ kind, groupId, proposalId?, eventId?, messageId? }` so the app
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
| `availabliss://calendars` | Calendar settings (OAuth return) |
