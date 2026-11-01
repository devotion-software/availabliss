package dev.joeherbert.availabliss.feature.calendars

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.auth.CustomTabs
import dev.joeherbert.availabliss.core.common.DateTimes
import dev.joeherbert.availabliss.core.network.model.CalendarConnection
import dev.joeherbert.availabliss.core.network.model.CalendarProvider
import dev.joeherbert.availabliss.core.network.model.ConnectedCalendar
import dev.joeherbert.availabliss.core.network.model.ConnectionStatus
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ColorSwatch
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.SurfacePanel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.feature.common.parseHex
import java.time.Instant

/** Requests READ_CALENDAR (and WRITE_CALENDAR for one-tap "Add to calendar"). */
@Composable
fun rememberCalendarPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        onResult(results[Manifest.permission.READ_CALENDAR] == true)
    }
    return { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }
}

/** Opens [CalendarSourcesUiState.pendingUrl] (OAuth start) in a Custom Tab. */
@Composable
fun PendingUrlEffect(url: String?, onOpened: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(url) {
        val target = url ?: return@LaunchedEffect
        CustomTabs.open(context, target)
        onOpened()
    }
}

/** Callbacks for [CalendarSourcesContent]. */
data class CalendarSourcesActions(
    val onRequestPermission: () -> Unit = {},
    val onToggleDevice: (externalId: String, busy: Boolean) -> Unit = { _, _ -> },
    val onToggleConnection: (connectionId: String, calendarId: String, busy: Boolean) -> Unit = { _, _, _ -> },
    val onConnect: () -> Unit = {},
    val onReconnect: (provider: String) -> Unit = {},
    val onSyncConnection: (String) -> Unit = {},
    val onDisconnect: (String) -> Unit = {},
    val onSyncDevice: () -> Unit = {},
)

/**
 * Glass sections for each calendar source (device accounts and connections) and the
 * "Connect another calendar" row, as in Calendars.dc.html. [manage] adds the
 * settings-only controls (sync status, Sync now, Disconnect).
 */
@Composable
fun CalendarSourcesContent(
    state: CalendarSourcesUiState,
    actions: CalendarSourcesActions,
    modifier: Modifier = Modifier,
    manage: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!state.hasPermission) {
            PermissionCard(actions.onRequestPermission)
        } else {
            state.deviceSources.forEach { source -> DeviceSourceSection(source, actions.onToggleDevice) }
            if (manage) DeviceSyncCard(state.lastDeviceSyncAt, state.syncingDevice, actions.onSyncDevice)
        }
        state.connections.forEach { connection ->
            ConnectionSection(
                connection = connection,
                busy = connection.id in state.busyConnections,
                manage = manage,
                actions = actions,
            )
        }
        ConnectRow(actions.onConnect)
    }
}

@Composable
private fun SourceHeader(title: String, account: String, status: String? = null) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = AvailablissTheme.type.captionStrong, color = colors.muted)
        Box(Modifier.weight(1f))
        status?.let { StatusPill(it, PillTone.Apricot) }
        Text(account, style = AvailablissTheme.type.captionStrong, color = colors.muted, maxLines = 1)
    }
}

@Composable
private fun DeviceSourceSection(source: DeviceSource, onToggle: (String, Boolean) -> Unit) {
    GlassPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.Top) {
        SourceHeader(source.title, source.account)
        source.calendars.forEach { cal ->
            RowDivider()
            CalendarToggle(cal.name, cal.color, cal.busy) { onToggle(cal.externalId, it) }
        }
    }
}

@Composable
private fun CalendarToggle(name: String, color: String?, busy: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = AvailablissTheme.colors
    ToggleRow(
        title = name,
        checked = busy,
        onCheckedChange = { if (enabled) onChange(it) },
        accessibilityLabel = "$name counts as busy",
        leading = { ColorSwatch(color?.let(::parseHex) ?: colors.accent) },
    )
}

@Composable
private fun ConnectionSection(
    connection: CalendarConnection,
    busy: Boolean,
    manage: Boolean,
    actions: CalendarSourcesActions,
) {
    val colors = AvailablissTheme.colors
    GlassPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.Top) {
        SourceHeader(
            title = connection.provider.title,
            account = connection.label,
            status = when (connection.status) {
                ConnectionStatus.Ok -> null
                ConnectionStatus.Error -> "Sync error"
                ConnectionStatus.ReauthRequired -> "Reconnect"
            },
        )
        connection.calendars.forEach { cal ->
            RowDivider()
            CalendarToggle(cal.name, cal.color, cal.busy, enabled = !busy) {
                actions.onToggleConnection(connection.id, cal.id, it)
            }
        }
        if (connection.status == ConnectionStatus.ReauthRequired && connection.provider.oauthKey != null) {
            RowDivider()
            TextAction("Reconnect ${connection.provider.title}", { connection.provider.oauthKey?.let(actions.onReconnect) })
        }
        if (manage) {
            RowDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    connection.lastSyncedAt?.let { "Synced ${DateTimes.relative(it)}" } ?: "Not synced yet",
                    style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                    modifier = Modifier.weight(1f),
                )
                TextAction("Sync now", { actions.onSyncConnection(connection.id) }, enabled = !busy, style = AvailablissTheme.type.label)
                TextAction("Disconnect", { actions.onDisconnect(connection.id) }, enabled = !busy, color = colors.warnInk, style = AvailablissTheme.type.label)
            }
        }
    }
}

@Composable
private fun DeviceSyncCard(lastSyncAt: Long?, syncing: Boolean, onSync: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().glass(Shapes.row).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AvIcons.Refresh, null, tint = colors.accentInk, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("This phone", style = AvailablissTheme.type.bodySmallStrong, color = colors.ink)
            Text(
                when {
                    syncing -> "Syncing…"
                    lastSyncAt != null -> "Busy times synced ${DateTimes.relative(DateTimes.toIso(Instant.ofEpochMilli(lastSyncAt)))}"
                    else -> "Not synced yet"
                },
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        TextAction("Sync now", onSync, enabled = !syncing, style = AvailablissTheme.type.label)
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(AvIcons.Calendar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Use this phone's calendars", style = AvailablissTheme.type.bodyMedium, color = colors.ink)
                Text(
                    "We only read when you're busy. Event titles never leave your phone.",
                    style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
            }
        }
        PrimaryButton("Allow calendar access", onRequest, height = 46.dp, shape = Shapes.controlSmall, textStyle = AvailablissTheme.type.bodySmallStrong, style = ButtonStyle.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConnectRow(onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .glass(Shapes.row)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(AvIcons.Plus, null, tint = colors.accentInk, modifier = Modifier.size(16.dp)) }
        Text("Connect another calendar", style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, modifier = Modifier.weight(1f))
        Text("Google · Outlook · iCloud", style = AvailablissTheme.type.caption, color = colors.muted, maxLines = 1)
    }
}

@Composable
internal fun IconTile(icon: ImageVector) {
    val colors = AvailablissTheme.colors
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = colors.accentInk, modifier = Modifier.size(17.dp))
    }
}

/** "Connect a calendar" sheet: providers, then the iCloud CalDAV form. */
@Composable
fun ConnectCalendarSheet(
    state: CalendarSourcesUiState,
    onDismiss: () -> Unit,
    onGoogle: () -> Unit,
    onMicrosoft: () -> Unit,
    onICloud: () -> Unit,
    onBack: () -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmitCalDav: () -> Unit,
) {
    val sheet = state.sheet ?: return
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = if (sheet == ConnectSheet.ICloud) "Connect iCloud" else "Connect a calendar", onDismissRequest = onDismiss) {
        when (sheet) {
            ConnectSheet.Choose -> SurfacePanel {
                ProviderRow("Google Calendar", "Sign in with Google", AvIcons.Calendar, onGoogle)
                RowDivider()
                ProviderRow("Outlook", "Microsoft 365, Outlook.com, Hotmail", AvIcons.Mail, onMicrosoft)
                RowDivider()
                ProviderRow("iCloud", "Apple ID + app-specific password", AvIcons.CloudCheck, onICloud)
            }
            ConnectSheet.ICloud -> {
                Text(
                    "Create an app-specific password at appleid.apple.com under Sign-In and Security. We only read when you're busy.",
                    style = AvailablissTheme.type.body,
                    color = colors.muted,
                )
                AvTextField(
                    value = state.calDav.username,
                    onValueChange = onUsername,
                    label = "Apple ID",
                    placeholder = "you@icloud.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    height = 48.dp,
                    shape = Shapes.controlSmall,
                )
                AvTextField(
                    value = state.calDav.appPassword,
                    onValueChange = onPassword,
                    label = "App-specific password",
                    placeholder = "xxxx-xxxx-xxxx-xxxx",
                    password = true,
                    keyboardType = KeyboardType.Password,
                    onImeAction = onSubmitCalDav,
                    height = 48.dp,
                    shape = Shapes.controlSmall,
                    error = state.calDav.error,
                )
                PrimaryButton(
                    "Connect iCloud",
                    onSubmitCalDav,
                    style = ButtonStyle.Accent,
                    height = 52.dp,
                    shape = Shapes.control,
                    enabled = state.calDav.canSubmit,
                    loading = state.calDav.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextAction("Back", onBack, modifier = Modifier.fillMaxWidth(), color = colors.muted)
            }
        }
    }
}

@Composable
private fun ProviderRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = AvailablissTheme.type.bodyMedium, color = colors.ink)
            Text(subtitle, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(16.dp))
    }
}

val CalendarProvider.title: String
    get() = when (this) {
        CalendarProvider.Google -> "Google"
        CalendarProvider.Microsoft -> "Outlook"
        CalendarProvider.CalDav -> "iCloud"
        CalendarProvider.Device -> "Phone"
    }

/** The `:provider` path value for OAuth providers, else null. */
val CalendarProvider.oauthKey: String?
    get() = when (this) {
        CalendarProvider.Google -> "google"
        CalendarProvider.Microsoft -> "microsoft"
        else -> null
    }

@Preview
@Composable
private fun CalendarSourcesPreview() {
    AvailablissPreviewTheme {
        CalendarSourcesContent(
            state = CalendarSourcesUiState(
                loading = false,
                hasPermission = true,
                deviceSources = listOf(
                    DeviceSource(
                        "On this phone", "Personal",
                        listOf(
                            DeviceCalendarRow("1", "Personal", "#FF8A5E", true),
                            DeviceCalendarRow("2", "Family", "#6FD8BB", true),
                            DeviceCalendarRow("3", "Birthdays", "#C9A6F5", false),
                        ),
                    ),
                ),
                connections = listOf(
                    CalendarConnection(
                        id = "c1", provider = CalendarProvider.Microsoft, label = "Work",
                        calendars = listOf(ConnectedCalendar("a", "Calendar", "#5B8DEF", true), ConnectedCalendar("b", "Team holidays", "#F2C14E", false)),
                    ),
                ),
            ),
            actions = CalendarSourcesActions(),
            modifier = Modifier.padding(20.dp),
        )
    }
}
