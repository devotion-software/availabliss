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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.joeherbert.availabliss.core.data.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * "Waiting for you": how many proposals are waiting for your reply and up to three of their
 * groups, each opening its proposal; "You're all caught up" when there are none.
 */
class WaitingWidget : GlanceAppWidget() {

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
                    Content(snapshot)
                }
            }
        }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot?) {
        val context = LocalContext.current
        WidgetCard(onClick = openApp(context)) {
            WidgetLabel("Waiting for you")
            val waiting = snapshot?.waiting
            when {
                snapshot == null || !snapshot.signedIn -> NotReady(snapshot)
                waiting == null -> WidgetMessage("Open Availabliss", "to load your groups")
                snapshot.waitingCount == 0 || waiting.isEmpty() -> WidgetMessage("You're all caught up", "Nothing's waiting for your reply")
                else -> Waiting(waiting, snapshot.waitingCount)
            }
        }
    }

    @Composable
    private fun ColumnScope.Waiting(waiting: List<WidgetWaiting>, count: Int) {
        val context = LocalContext.current
        Text(
            text = WidgetSnapshots.waitingTitle(count),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
        )
        val shown = waiting.take(WidgetSnapshots.SHOWN_WAITING)
        shown.forEach { item ->
            Spacer(GlanceModifier.height(6.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .cornerRadius(12.dp)
                    .background(GlanceTheme.colors.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .clickable(openApp(context, item.link)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.groupName,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "Reply",
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
        }
        WidgetSnapshots.moreLabel(count, shown.size)?.let { more ->
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = more,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

/** Hosts [WaitingWidget]; the first one added fetches fresh data. */
class WaitingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitingWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.enqueue(context, delaySeconds = 0)
    }
}
