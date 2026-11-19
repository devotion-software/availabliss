package dev.joeherbert.availabliss.core.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * "Next plan": the soonest plan you're going to (title, when, group), opening its proposal or
 * group. Draws from the cached [WidgetSnapshot]; "when" is worked out at each redraw, so
 * "Tomorrow" becomes "Today" with the widget's periodic update.
 */
class NextPlanWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetSnapshotStore(context)
        val prefs = AppPreferences(context.applicationContext)
        val initialSnapshot = store.current()
        val initialAppearance = prefs.appearance.first()
        provideContent {
            val snapshot by store.snapshot.collectAsState(initialSnapshot)
            val appearance by prefs.appearance.collectAsState(initialAppearance)
            GlanceTheme(colors = widgetColors(appearance)) {
                CompositionLocalProvider(LocalWidgetAppearance provides appearance) {
                    Content(snapshot, Instant.now())
                }
            }
        }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot?, now: Instant) {
        val context = LocalContext.current
        val plan = snapshot?.plans?.let { WidgetSnapshots.nextPlan(it, now) }
        WidgetCard(onClick = openApp(context, plan?.link ?: if (snapshot?.signedIn == true) DeepLink.MyPlans else null)) {
            WidgetLabel("Next plan")
            when {
                snapshot == null || !snapshot.signedIn -> NotReady(snapshot)
                snapshot.plans == null -> WidgetMessage("Open Availabliss", "to load your plans")
                plan == null -> WidgetMessage("No plans coming up", "Find a time with one of your groups")
                else -> Plan(plan, now)
            }
        }
    }

    @Composable
    private fun ColumnScope.Plan(plan: WidgetPlan, now: Instant) {
        Text(
            text = plan.title,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = WidgetSnapshots.whenLabel(plan, now),
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        if (plan.groupName.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = plan.groupName,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

/** Hosts [NextPlanWidget]; the first one added fetches fresh data. */
class NextPlanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextPlanWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.enqueue(context, delaySeconds = 0)
    }
}
