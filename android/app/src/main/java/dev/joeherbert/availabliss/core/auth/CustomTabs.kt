package dev.joeherbert.availabliss.core.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/** Opens a URL in a Custom Tab (OAuth, Sign in with Apple), falling back to the browser. */
object CustomTabs {
    fun open(context: Context, url: String, toolbarColor: Int? = null) {
        val uri = Uri.parse(url)
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .apply {
                toolbarColor?.let {
                    setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build())
                }
            }
            .build()
        try {
            intent.launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
