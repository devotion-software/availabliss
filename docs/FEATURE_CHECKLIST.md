# Feature checklist

Features usually land as separate per-platform commits, so one platform can fall
behind without anyone noticing. Before calling a feature done, tick off every row
that applies (copy this list into the Duet task, the PR description or the last
commit's notes).

## Platforms

- [ ] **Server / API**: route, validation (`web/src/lib/schemas.ts`), service, live event if others should see it straight away, push / email if relevant
- [ ] **Web**: page or component, phone and desktop widths, light and dark
- [ ] **iOS**: screen or view, Dynamic Type, VoiceOver labels, light and dark
- [ ] **Android**: screen or composable, TalkBack labels, light and dark
- [ ] Older app versions cope with any new field or enum value (unknown values decode safely)

## Docs and tests

- [ ] [`docs/API.md`](API.md) updated for new or changed endpoints, fields and live events
- [ ] Unit tests for new logic (`make test`, `make android-test`)
- [ ] Integration test for new database behaviour (`make test-integration`)
- [ ] `scripts/smoke.ts` covers a new end-to-end API flow, if there is one
- [ ] Demo data (`make seed-demo`) shows the feature, if it's visible in the apps

## Before release

- [ ] Migrations run cleanly on a copy of production data (`make db-migrate`)
- [ ] New environment variables are in `web/.env.example` and set in Coolify
- [ ] Wording matches across the three platforms
