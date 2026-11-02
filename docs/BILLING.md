# Pro billing (RevenueCat)

Free accounts can own **3 groups**. **Pro** removes the limit. Purchases go
through [RevenueCat](https://www.revenuecat.com) on every platform:

| Platform | Store | SDK |
| -------- | ----- | --- |
| iOS | App Store (StoreKit) | `purchases-ios` |
| Android | Google Play Billing | `purchases-android` |
| Web | RevenueCat Web Billing, paid out through Stripe | `@revenuecat/purchases-js` |

Every client configures RevenueCat with **the Availabliss user id as the app
user id**, so a purchase on one platform unlocks Pro everywhere. The server
mirrors the entitlement into `user_plans` and enforces limits; clients only show
state from `GET /api/v1/me/plan`.

## One-time setup

1. **RevenueCat project** → add an App Store app (bundle id
   `dev.joeherbert.availabliss`), a Play Store app (package
   `dev.joeherbert.availabliss`) and a **Web Billing** app connected to Stripe.
2. **Products**: create the subscriptions in App Store Connect and Play Console
   (e.g. `pro_monthly`, `pro_annual`), and the matching Web Billing products.
3. **Entitlement** `pro` (or set `REVENUECAT_ENTITLEMENT`), attached to every product.
4. **Offering**: make one *current* offering with `$rc_monthly` / `$rc_annual`
   packages (and `$rc_lifetime` if you sell one). The apps and web list the
   current offering's packages.
5. **Webhook**: Integrations → Webhooks → URL
   `https://<your host>/api/v1/billing/revenuecat`, Authorization header value =
   a long random string, also set as `REVENUECAT_WEBHOOK_AUTH`.
6. **Keys** (server env, see `web/.env.example`):
   - `REVENUECAT_SECRET_KEY`: a v1 secret API key. After every webhook the
     server re-reads the subscriber from RevenueCat rather than trusting the
     event body. Without it, the event body is used.
   - `REVENUECAT_IOS_KEY`, `REVENUECAT_ANDROID_KEY`, `REVENUECAT_WEB_KEY`: the
     public SDK keys. The API hands them to the clients, so you don't have to
     rebuild the apps to set them. A missing key hides purchasing on that platform.

## Testing without RevenueCat

```bash
make set-plan EMAIL=sam@example.com PLAN=pro            # 30 days
make set-plan EMAIL=sam@example.com PLAN=pro DAYS=lifetime
make set-plan EMAIL=sam@example.com PLAN=free
```

## How state flows

1. The person buys in an app or on the web; the SDK talks to RevenueCat.
2. The client calls `POST /api/v1/me/plan/refresh`, and the server reads the
   subscriber from RevenueCat straight away.
3. RevenueCat's webhook covers everything after that (renewals, cancellations,
   expiry, refunds, transfers).
4. Cancelling keeps Pro until the paid period ends. Groups made while on Pro
   are never deleted; going back to free only stops you creating new ones.
