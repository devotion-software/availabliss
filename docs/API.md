# Availabliss API (v1): the guide

**The reference is the spec.** Every endpoint, its body and query, its response, its status codes
and its rate limit are in [`openapi.json`](openapi.json) (OpenAPI 3.1). It is generated from
`web/src/lib/api-contract.ts` (bodies and queries from the routes' zod schemas, responses from
`web/src/lib/types.ts` and `web/src/lib/api-types.ts`, descriptions from their comments) by
`make openapi` / `bun run openapi`. Don't edit it by hand: CI fails when it's out of date, a unit
test fails when a route under `web/src/routes/api/v1` isn't in the contract, and
`make models-check` fails when an iOS or Android model doesn't match it.

To read it, run `make dev` and open <http://localhost:5190/dev/api-docs> (only served while
developing). To change it, change the code and its comments, then `make openapi`.

This file is the prose around it: conventions, sign-in, errors, and the rules behind the endpoints
that don't fit in a field description.

The SvelteKit app in `web/` serves both the web UI and this JSON API. The iOS and Android apps are
clients of exactly the same endpoints.

## Conventions

- Base path: `/api/v1`. Auth endpoints (Better Auth): `/api/auth/*`.
- Content type: `application/json` (UTF-8) for requests and responses.
- Timestamps: ISO 8601 UTC strings (`2026-11-13T18:00:00.000Z`). Instants sent to the server may use
  `Z` or an offset.
- Dates (calendar days): `YYYY-MM-DD`. Wall-clock times: `HH:MM` (24h).
- IDs: opaque strings (36 chars max).
- Weekdays: integers `0` = Monday … `6` = Sunday.
- `POST`s that create something answer **`201`** (blocks, CalDAV connections, groups, events,
  proposals, messages, preferences, invites, busy ignores, templates, attachments); everything else
  answers `200`, or `204` when there's nothing to return.
- Endpoints that take a body need a JSON one, even when every field is optional: send `{}` (e.g.
  `POST /groups/:id/leave`, `/notifications/read`, `/groups/:id/planner`); a missing body is `400`.
- Clients must tolerate fields and enum values they don't know: show an unknown notification kind
  with a generic icon, an unknown attachment kind as a file, an unknown reply as `pending`.

**Pictures** (`User.image`, `Member.image`, `Group.image`, `GroupEvent.image`, attachments …) are
private files sent as signed links, `<host>/media/<key>?e=<expiry>&s=<signature>` (attachments:
`/api/v1/attachments/:id?e=…&s=…`). A link works for 7–8 days and stays the same all day, so cache
by URL; it changes daily, so refetch the resource for a fresh one. Without a valid signature they're
`404` (an attachment is still served to a signed-in member of its group). A picture from a sign-in
provider (e.g. Google) is its own URL, passed through as it is. Uploaded profile and group photos
are shrunk to fit 512 px, event covers 1600 px.

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

Native apps get an id token exactly as for sign-in and send it to `POST /api/v1/me/accounts/link`.
If that sign-in already has its own account, the answer is `409` with the other account's `name`,
`email` and `groups`: show it, then send again with `merge: true` to merge it into this account (its
groups, busy time, calendars, chats, templates and Pro move here; the other account is deleted). An
id token that can't be verified (e.g. expired while the merge prompt was open) is `400`, never
`401`, so it can't be mistaken for the session ending.

The web links with a redirect: `POST /api/auth/link-social` `{ provider, callbackURL,
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
responding to or cancelling a cancelled proposal, overriding a reply or confirming once it's
confirmed, or voting in a closed poll. `upgrade_required` means the action needs Pro (see Plans):
show the upgrade screen rather than a plain error. `issues` is only present for `validation_error`.

### Rate limits

Every `/api/v1` request counts against a per-user budget (per IP when signed out): 600 reads and
120 writes a minute signed in, 120 requests a minute signed out. Some endpoints have tighter limits
of their own; the spec lists each one (`x-rate-limit`, and in the endpoint's description).

Over a limit the response is `429` with `code: "rate_limited"`, a readable `message` and a
`Retry-After` header in seconds. Limits are enforced in production (`RATE_LIMIT=on|off` overrides).

## Requirements (a group's timing)

`Requirement.length` picks the shape of a candidate slot:

| UI preset | Requirement |
| --------- | ----------- |
| 2 hours / 4 hours | `{ "type": "duration", "minutes": 120 }`, `window` = when it may happen (e.g. `08:00`–`23:00`) |
| An evening from 6 | `{ "type": "days", "count": 1 }`, `window: { "from": "18:00", "to": "23:59" }` |
| One day | `{ "type": "days", "count": 1 }`, `window: null` |
| A weekend | `{ "type": "span", "startDay": 4, "startTime": "18:00", "endDay": 6, "endTime": "23:59", "weeksLater": 0 }` |
| Tue → Thu | `{ "type": "span", "startDay": 1, "startTime": "00:00", "endDay": 3, "endTime": "23:59", "weeksLater": 0 }` |
| Tue → following Thu | as above with `"weeksLater": 1` |
| Tue → Thu 3 weeks later | as above with `"weeksLater": 3` (up to 4) |
| A week | `{ "type": "days", "count": 7 }` |

A slot "works" when every **synced** member is free for the whole slot. With a `quorum` (or optional
members), it works when at least `quorum` of the synced, non-optional members are free; such slots
carry `missingMemberIds` (everyone synced who's busy then, optional members included; `[]` when
nobody is), `match` days matched only by those get `partial: true`, and the day view adds `mostFree`
stretches. Rank slots with fewer missing members first. Without either field, nothing changes.
Members who haven't synced are ignored and reported in `pendingMemberIds`. A member counts as synced
once they have connected any calendar or marked any busy time by hand.

Server calendars are kept at least three years ahead, and as far as any of the member's groups
search (up to ten). Phones upload as far ahead as the server asks (`CalendarConnection.syncUntil`),
in fixed ranges so only changed ones are re-sent. Where a member's calendars stop short of the
range, they're listed in `checkedUntil`; their busy time past that date is unknown, so they're
treated as free there, like an unsynced member.

- An empty `days` list means any day of the week.
- Bank holidays follow the region (`country` / `subdivision`) of the group owner. With
  `bankHolidays`, they're allowed days whatever `days` says, and a start-to-end span stretches over
  adjacent ones (long weekends: Good Friday starts "Fri 18:00" on Thu 18:00, Easter Monday ends
  "Sun night" on Mon night).
- `duration` slots are reported as each maximal free stretch at least `minutes` long inside the
  window, so a proposal can pick any part of it.
- `POST /requirements/preview` counts the slots a timing produces before anyone's busy time is
  considered (the timing editor's "13 weekends fit this timing").

**Preferred times** are guidelines: they never block a date. Availability marks the slots and
`match` days they fit with `preferredBy` (user ids). For Pro viewers each slot also has `score`
("best date": preferences weighted by who's compromised in recent plans, plus notice) and `fairFor`
(who it suits that missed out or said maybe last time); sort by score.

**Timing templates** are a user's saved timings, reused when creating groups. When applying one,
clients restart its search range from today (same length) and drop specific dates that are already
over.

**Rhythm.** A group that meets regularly can set `rhythm` (weekly, fortnightly or monthly: the same
weekday in the same week of the next month, "the first Friday"). `GET /groups/:id/next` gives the
free time nearest the next one (a week or so either side, everyone-free first; null without a
rhythm). With `prompt: true`, once a plan has ended and nothing newer is planned or proposed, the
server posts "Same again? … works for everyone" in the chat and sends a `reminder`
(`data.reminder: 'next'`, with the slot's `start`/`end`), once per plan.

**Automatic archiving.** With `autoArchive` on, the server archives the group (for everyone, as an
admin archiving it would; `group.updated` is published) once its latest event has ended and nothing
else is planned: no event still to come or under way, and no open proposal for a time still ahead.
It's checked every 15 minutes. It happens once per event: after someone unarchives it, it isn't
archived again until a newer event has passed. A new proposal or event in an archived group with
`autoArchive` on unarchives it.

## Accounts

**Usernames** are 3–20 characters: lowercase letters, numbers, `_` and `.` (a dot can't come first,
last or twice in a row). They're sent with or without a leading `@`, in any case, and stored
lowercase. Every account gets one when it's created.

**Busy time.** `GET /me/busy` is your own merged busy time. A calendar event's title and its
calendar's name (`label`) are only ever sent to their owner; titles are stored encrypted. Marking a
calendar event "not busy" (`POST /me/busy/ignores`, optionally for the whole series) makes it not
count for anyone. `busyBufferMinutes` is counted as busy before and after each busy time when
finding free slots and clashes, but isn't added to `/me/busy`.

**My time** (manual busy blocks) is for blocking time ahead: busy time only matters for finding
dates to come. A new block that has already passed (a one-off that ended before yesterday in your
profile's time zone, or a repeat whose `until` is before yesterday) is `400` "That time has already
passed". Earlier today is fine, and the day's grace covers a phone in another time zone. Edits
aren't checked, so a series that started long ago can still be changed or ended. The apps only open
today onwards: past days are shown muted and can't be tapped, and the date pickers start at today
(or an edited block's own start).

### Plans (Pro)

Free accounts can own 3 groups (joining other people's is unlimited); Pro is unlimited, and
`POST /groups` past the limit is `402 upgrade_required`. Pro is bought through RevenueCat in the
App Store, Play Store or on the web (RevenueCat Web Billing / Stripe), always with **the user's id
as the RevenueCat app user id**, so it follows the account to every platform. Call
`POST /me/plan/refresh` after a purchase or restore. See [BILLING.md](BILLING.md) for the
RevenueCat setup.

**AI planner** (optional, Pro). Offered only when the server has `ANTHROPIC_API_KEY` set
(`Plan.aiPlanner.available`) and usable on Pro (`Plan.aiPlanner.included`). Nothing runs unless
someone asks. Claude sees the group's name and timing, the slots that already work for everyone,
named holidays, member counts and the asker's notes, never names, calendars or busy time.

### Calendar feed (webcal)

`GET /me/calendar-feed` gives your secret feed link (made the first time; `POST …/reset` replaces
it). The feed, `/calendar/<token>.ics` (outside `/api/v1`, optional `?group=<id>`), has your groups'
plans from 60 days back, leaving out ones you said you can't make, with stable `UID`s and a rising
`SEQUENCE`, so calendar apps that subscribe pick up edits and cancellations. Offer `webcalUrl` to
Apple Calendar / Outlook, `https://calendar.google.com/calendar/r?cid=<webcalUrl>` for Google
Calendar, and `url` to copy.

## Calendars

**Google and Outlook** connect with OAuth. The web opens `GET /calendars/oauth/:provider/start` and
comes back to its `redirect` path with `?connected=<connectionId>` or `?error=` (`wrong_account` when
the session isn't the one that started). Native apps can't send the bearer token from a browser
sheet, so they call `POST /calendars/oauth/:provider/url`, open the `url` in
ASWebAuthenticationSession / Custom Tabs with `redirect: "availabliss://calendars"`, get back
`?complete=<code>&provider=<provider>` (or `?error=`), and finish with
`POST /calendars/oauth/:provider/complete` (`403` if a different user started, `400` if the code is
used or older than 10 minutes). Each state works once.

**iCloud** (or any CalDAV server) connects with an app-specific password (`POST /calendars/caldav`).

**Phone calendars** are read by the apps and uploaded with `PUT /calendars/device`: start and end
times only, plus each event's title stored encrypted and only shown to you. Each upload covers a
range and replaces what was there (only differences are written); a sync sent as several ranges
says `more: true` on all but the last, so groups are refreshed once.

**Sync now** (`POST /calendars/:id/sync`) pulls a server calendar straight away; a failed sync shows
as `status: 'error'` or `'reauth_required'`, not an HTTP error. At most once per connection per
30 s: a call in between returns the connection as it is. Phone calendars aren't pulled.

**Change notifications** from Google and Microsoft (`/calendars/push/*`, not called by clients; no
auth, not rate limited) carry a token derived from `SECRETS_KEY` and the calendar; anything without
a matching one is ignored. A valid one syncs that connection a few seconds later.

## Groups

**Names in a group.** Each member can choose the name they go by in one group
(`PATCH /groups/:id/members/me`). Within that group it replaces their account name everywhere the
server names them: `Member.name` (so the members list, chat authors, proposal replies and busy
lists, which the apps resolve by `userId`), reply quotes (`replyTo.authorName`), the invites list
(`invitedBy`), system messages ("Sam can't make it any more") and the Activity items and pushes
about that group. Outside the group (invite emails and previews, `/me`) it's always the account
name. System messages keep the name used when they were posted.

**Roles.** Admins can rename the group, change its timing, archive it, manage invites and photos,
change roles and remove people (never the owner). Only the owner can delete the group. Someone whose
role changes gets a `role.changed` notification.

**Leaving a group.** Anyone can leave, but a group always keeps an owner, so always at least one
admin:

- A member, or an admin while the owner stays, just leaves.
- The owner names an existing admin (`newOwnerId`) who becomes the owner as they leave. No
  `newOwnerId`, or one that isn't another admin of the group, is `400 validation_error` (issue path
  `newOwnerId`). Bank holidays then follow the new owner's region.
- If nobody else is an admin, leaving as an admin is `409 conflict` ("Make someone else an admin
  before you leave"): promote someone with `PATCH /groups/:id/members/:userId` `{ role: 'admin' }`
  first.
- The only member can't leave (`409 conflict`): they delete the group instead.

The new owner gets a `role.changed` notification. Everyone left in the group gets `member.changed`
with the new owner's `Member` (when ownership moved), then `{ userId, removed: true }` for the
leaver; the leaver's own apps get that too, so they close the group.

**Invites.** With no emails, an invite is a link anyone can use until it expires (14 days), by up to
`maxUses` people: a number from 1 to 1000, or `null` for no limit. Left out it's 1 (single-use, so
older apps keep making single-use links). Each person who joins with it counts once (someone already
in the group doesn't); joining and counting happen together, so people joining at the same moment
can't go past the limit, and anyone who misses out isn't added. Once full it's off the invites
list, and `GET /invites/:token` and accept say `404` "This invite link has been used up" until it
expires.

- **Emails:** each address gets its own emailed invite that only someone signed in with that address
  can accept; it's used up once they join. An address that already has an account also gets an
  Activity item and a push, as for a username.
- **Usernames:** each person gets their own invite in Activity (an `invite.received` notification
  and a push, `data: { token }`) that only they can accept; it's used up once they join. A username
  nobody has is `404`, and someone already in the group is `409`, before any invite is sent.
- **`userIds`** work like usernames, but only for people who are already in a group with you (anyone
  else is `404`, before any invite is sent). The apps use it for "New group with these people": they
  create the group (`POST /groups`) and then invite the people they kept ticked.

Someone invited personally can accept (`POST /invites/:token/accept`) or decline
(`POST /invites/:token/decline`, which removes it and its Activity item; nobody is told). Joining
removes any other invites to that group sent to you. Accepting answers with `joined: false` when you
were already a member; the apps then skip asking what name to go by.

**Nudge** (`POST /groups/:id/nudge`) pushes or emails the members who haven't synced, at most once
every 12 hours.

## Proposals

A time doesn't have to work for everyone to be proposed. Clients warn first
(`GET /groups/:id/conflicts`) and show `busyMemberIds` while people reply. The creator accepts
automatically, and a chat card is posted.

A `maybe` is counted apart from `accepted` and doesn't confirm a proposal: only when every member
has firmly accepted does it confirm itself (the creator or an admin can still "Confirm anyway"; the
chat message then says e.g. "3 of 5 can make it, 1 maybe"). Show maybes beside the yeses
("3 in · 1 maybe") and rank dates by firm yeses first.

When every member has accepted, the proposal becomes `confirmed`, an event is created, and every
member gets a push notification and a `proposal.confirmed` live event. The creator or an admin can
also confirm it before then with `POST /proposals/:id/confirm` ("Confirm anyway", e.g. when someone
can't make it; `409` until someone besides the caller has accepted); the same happens, with
`confirmedBy` set, a chat message "It's on for … (3 of 4 can make it)" instead of "Everyone's in
for …", and a push titled "It's on". Replies stay as they were, so `responses` says who's coming
(`accepted`), who can't (`declined`) and who never replied (`pending`). Members with no response
joined the group after it was confirmed.

Replies can still change once confirmed (members who joined since can't reply: `409`). The plan
stays confirmed. When someone who was coming says they can't make it any more, their response gets
`droppedOut: true`, the proposal `needsReview: true`, the chat gets "Priya Shah can't make it any
more", and the creator and admins get a `proposal.changed` notification. One of them then either
keeps the plan (`POST /proposals/:id/keep`, which clears the review; they stay as can't make it) or
cancels it (`DELETE /proposals/:id`, which deletes the event, posts "… cancelled the plan for …" and
notifies everyone else with `proposal.changed`). Someone changing back to `accepted` just updates
(and clears their `droppedOut`). Copies people added to their own calendars are left alone, as when
an event is deleted.

**Events** can also be added straight to the calendar (`POST /groups/:id/events`), with no proposal.
An all-day event's `end` is the exclusive midnight after its last day. The creator or an admin can
edit or delete it. Native apps add plans to the phone's calendar themselves (EventKit /
CalendarContract); the web uses `GET /events/:id/ics` and `GET /me/plans.ics`.

## Chat

Messages are `text`, `poll`, `proposal` (a proposal's card), `event` or `system` (group updates,
which can't be replied to, reacted to or searched). Only the sender can edit or delete their own
text messages; a deleted message stays in the chat as "This message was deleted" with no body, and
its attachments are deleted for good. Read receipts (`POST …/messages/receipts` with `delivered` or
`seen` up to a message) feed `receipt` on your own messages and `GET /messages/:id/info`.

**Older apps** only know `body`, so newer kinds of content also put a fallback there:

- Attachments: `body` is the caption, or without one a one-line summary: "📷 Photo", "GIF",
  "📷 3 photos", "📎 Plan.pdf", "📎 2 files", "📎 2 attachments", "🎟️ Concert ticket",
  "🎟️ 2 passes". Apps that know `attachments` show those and `caption` instead.
- A shared place (a one-off pin, not live tracking): "📍 Dishoom Shoreditch" (or "📍 Location"
  without a name), the address if any, and a Google Maps link, one per line. Apps that know
  `location` show a card. It can't be edited, only deleted.

**Attachments** are uploaded first (`POST /groups/:id/attachments`, one file a request), then sent
by id in `attachmentIds` (up to 10, your own unsent uploads to this group). Uploads not sent within
a day are deleted.

- Photos (JPEG, PNG, WebP, GIF, HEIC where the server can read it; up to 15 MB) are turned upright,
  stripped of metadata (location included), shrunk to at most 2560 px and stored as JPEG (PNG if
  transparent), so `name`, `mime` and `size` can differ from what was sent. An animated GIF is kept
  as it is.
- Files (up to 25 MB) must be one of: pdf, txt, md, csv, rtf, ics, vcf, doc(x), xls(x), ppt(x),
  odt, ods, odp, pages, numbers, key, epub, zip, mp3, m4a, wav, pkpass, pkpasses (by extension; a
  file sent as `application/vnd.apple.pkpass` without one gets `.pkpass`). Anything else is `400`.
- A Wallet pass whose pass.json can be read comes back as `kind: 'pass'` with its details (else as a
  plain file); the file itself is kept exactly as sent, because it's signed.
- Videos (mp4, m4v, mov) come back as `kind: 'video'` with `duration` read from the file's header
  (one that can't be read is `400`); up to 3 minutes and 100 MB, or with Pro 10 minutes and 300 MB.
  They're kept as sent, so the apps shrink them to 720p first, and can send a JPEG / PNG frame as
  `poster`, which becomes `thumbnailUrl` and sets `width` / `height`. Served with range requests.
- Photos, GIFs and passes are served `inline` (a pass as `application/vnd.apple.pkpass`, so Safari
  offers to add it to Wallet); files as a download. Add `&download=1` to download a photo.
  Deleting the group deletes every attachment sent in it.

**Replies and threads.** `replyToId` must be a message in the same chat (else `400`), not a group
update (`400`) and not deleted (`409`). `replyTo` quotes the message as it is now; quotes aren't
republished, so when a quoted message changes, refresh the `replyTo` of any loaded message quoting
it. `GET /messages/:id/thread` follows `replyTo` up to the first message of the chain and returns
it with every reply below it.

**Reactions.** One emoji per person per message: reacting again replaces it. The author gets a push
("Sam reacted ❤️ to “…”"), not for their own messages; there's no Activity item.

**Polls.** A date poll's options carry the `slot` each stands for; show who's busy per option with
the conflicts endpoint, and let its creator propose the winner. Only the poll's author can edit
(while open) or close it. When editing, an option with an `id` keeps its votes, one without is new,
and one left out goes with its votes; switching to pick-one keeps each person's first pick.

## Live updates

`GET /api/v1/live` 🔒 is a Server-Sent Events stream covering every group the user belongs to.
Each event is `data: <LiveEvent json>\n\n`. Payloads by type:

| Type | Payload |
| ---- | ------- |
| `message.created` | `Message` |
| `message.updated` | `Message` (edited or deleted; `receipt` is null, so keep your own) |
| `message.receipts` | `{ userId, state, receipts: { messageId, delivered, seen, recipients, seenBy }[] }` (the newest 500 changed messages; all of them are marked) |
| `message.reactions` | `{ messageId, reactions: MessageReaction[] }` |
| `poll.updated` | `Poll` |
| `proposal.created`, `proposal.updated` | `Proposal` |
| `proposal.confirmed` | `{ proposal: Proposal, event: GroupEvent }` |
| `event.changed` | `GroupEvent`, or `{ id, deleted: true }` |
| `group.updated` | `Group` (each member gets it with their own `role`) |
| `member.changed` | `Member`, or `{ userId, removed: true }` |
| `availability.changed` | `{ userId }`: refetch availability |
| `notification.created` | `AppNotification` |

When a group is deleted, each member gets `member.changed` with their own `{ userId, removed: true }`,
as if they'd been removed. Deleting an account sends the remaining members of each of its groups
`member.changed` `{ userId, removed: true }`. Changing `busyBufferMinutes` publishes
`availability.changed`.

Native apps keep this open while foregrounded and rely on push otherwise.

## Notifications & devices

**Activity** (`GET /notifications`) records everything, whatever the push settings. Kinds clients
don't know are shown with a generic icon and open the group (or proposal) from `data`.

Chats (`kind: 'message'`) get **one Activity item per group**: each new message brings it to the top
with `title` = the group's name, `body` = `"Sam: See you there"` (or `"Sam: 📷 Photo"` for a photo
without a caption, `"Sam: 📍 Dishoom Shoreditch"` for a place) and `data.count` = messages since it
was last read. It stays unread until the chat itself is read (a `seen` receipt), not when Activity
is opened, and `POST /notifications/read` with no ids marks everything else read. Updates to an
existing item (a new message, or it being read) arrive as `notification.created` with the **same
id**: replace the item you have, and move it to the top when its `createdAt` is newer. The Chats tab
(`GET /chats`) lists every chat, latest message first; archived groups only appear there while
something in them is unread.

`proposal.created` tells each other member "Priya Shah proposed Sat 12 Dec · 18:00–21:00. Can you
make it?" (`title` = the group's name, `data.proposalId`). The proposer gets their own item too,
"You proposed Sat 12 Dec · 18:00–21:00.", with `data.own = 'true'`: it is created already read and
sends no push.

`role.changed` tells someone their role in a group changed, with `data.role` the new one: `admin`
("Sam Taylor made you an admin."), `member` ("You're no longer an admin. Sam Taylor changed your
role.") or `owner` ("You're now the owner. Sam Taylor left and handed the group to you.", when the
owner leaves). `title` is the group's name; it opens the group. Nobody is told about a change to
their own role, or a "change" to the role they already have.

**Reminders** (`kind: 'reminder'`): a job every 5 minutes sends, once each, a push and Activity item
the day before a confirmed plan (24 hours ahead; 09:00 the day before for all-day plans, in the
group's time zone) and an hour before it, to members going (yes or maybe; everyone for events added
directly), as each chose in `User.eventReminders`; `data` has `eventId`, `reminder: 'day' | 'hour'`
and `proposalId` when it came from one. A day before an open proposal, members who haven't replied
get one too (`data.proposalId`, `reminder: 'reply'`). Android shows them in the `reminder` channel.

**Push settings.** Before each push the server leaves out anyone who turned that category off (chat
= `message`; proposals = `proposal.created`, `proposal.changed`; plans = `proposal.confirmed`,
`event.created`; reminders = `reminder`; nudges = `nudge`), muted the group, or is in quiet hours
(local times in `User.timezone`, may wrap midnight). With `catchUp`, when quiet hours end one push
(`kind: 'quiet.summary'`, with `groupId` if it was all one group) says how many updates came in
meanwhile that are still unread. Invites and role changes always push. Turning plans off also stops
the confirmation email.

**Push payloads** carry `{ kind, groupId, proposalId?, eventId?, messageId?, token? }` (`token`: an
`invite.received` invite, opened like `/invite/:token`) so the app can deep link. On iOS these keys
are **top-level** in the APNs payload, next to `aps`; on Android they are the FCM `data` map.
Android notification channel id = `kind`. Apps register their APNs / FCM token with
`POST /devices` and remove it with `DELETE /devices/:token`.

## Server configuration the native apps depend on

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
