# UI tests

`AppTourUITests` signs in with a real email code and walks the main screens,
attaching a screenshot of each to the test result.

```bash
make db-up && make dev                      # API on :5190, Mailpit on :8027
make seed-demo                              # demo user, groups, busy time and chat
make ios-uitest                             # signs in as the demo user (or EMAIL=…)
```

The Mailpit code for a new sign-in is read from its search API, so clear out old
mail for that address if a test picks up a stale code. Screenshots are exported
to `ios/build/uitest-screens/`.
