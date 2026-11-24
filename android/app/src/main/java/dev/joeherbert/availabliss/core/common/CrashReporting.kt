package dev.joeherbert.availabliss.core.common

import android.content.Context
import dev.joeherbert.availabliss.BuildConfig
import io.sentry.DataCollection
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.User
import java.net.URI

/**
 * Sentry: crashes, ANRs, errors, traces and session replays. Off unless the build has a DSN
 * (`availabliss.sentryDsn` in local.properties). Events carry the signed-in user's id and nothing
 * else about them: no IP, no screenshots, and replays mask all text and images, so availability,
 * chat and calendar details never leave the phone.
 */
object CrashReporting {
    fun start(context: Context) {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        val production = !BuildConfig.DEBUG
        val apiHost = runCatching { URI(BuildConfig.API_BASE_URL).host }.getOrNull().orEmpty()
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = if (production) "production" else "development"
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            // No IP, cookies, headers (the bearer token), bodies, query strings or database values.
            options.dataCollection = DataCollection().apply {
                userInfo = false
                cookies = KeyValueCollectionBehavior.off()
                urlQueryParams = KeyValueCollectionBehavior.off()
                httpHeaders.request = KeyValueCollectionBehavior.off()
                httpHeaders.response = KeyValueCollectionBehavior.off()
                httpBodies = emptySet()
                databaseQueryData = false
            }
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            // A tenth of launches and screen loads are traced in production; every one in development.
            options.tracesSampleRate = if (production) 0.1 else 1.0
            // Trace headers only go to our own API, never to RevenueCat, Google or map tiles.
            options.setTracePropagationTargets(listOf(apiHost))
            options.sessionReplay.sessionSampleRate = if (production) 0.1 else 0.0
            options.sessionReplay.onErrorSampleRate = 1.0
            options.sessionReplay.setMaskAllText(true)
            options.sessionReplay.setMaskAllImages(true)
            // Picture and attachment links carry a signature in their query string.
            options.setBeforeBreadcrumb { breadcrumb, _ ->
                (breadcrumb.data["url"] as? String)?.let { breadcrumb.setData("url", withoutQuery(it)) }
                breadcrumb.removeData("http.query")
                breadcrumb
            }
            options.setBeforeSend { event: SentryEvent, _ ->
                event.request?.apply {
                    url = url?.let(::withoutQuery)
                    queryString = null
                    headers = null
                    cookies = null
                }
                event
            }
        }
    }

    /** Attaches the signed-in user's id to later events, or clears it on sign-out. */
    fun setUser(id: String?) {
        if (!Sentry.isEnabled()) return
        Sentry.setUser(id?.let { User().apply { this.id = it } })
    }

    private fun withoutQuery(url: String) = url.substringBefore('?')
}
