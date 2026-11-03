package dev.joeherbert.availabliss.feature.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.deeplink.DeepLink
import dev.joeherbert.availabliss.core.network.model.NotificationKind
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing

/** Activity tab: in-app notification history. */
@Composable
fun ActivityScreen(onOpenLink: (DeepLink) -> Unit, viewModel: ActivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    val permission = rememberNotificationPermission()

    ActivityContent(
        state = state,
        snackbar = snackbar,
        showPermissionCard = permission.denied,
        onOpenSettings = permission.openSettings,
        onItem = { item -> viewModel.open(item)?.let(onOpenLink) },
        onMarkAllRead = viewModel::markAllRead,
        onRetry = viewModel::refresh,
    )
}

/** POST_NOTIFICATIONS (API 33+): asks once, then exposes whether it was denied. */
private class NotificationPermission(val denied: Boolean, val openSettings: () -> Unit)

@Composable
private fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    var isGranted by rememberSaveable { mutableStateOf(granted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        isGranted = result
        asked = true
    }
    LaunchedEffect(Unit) {
        isGranted = granted()
        if (!isGranted && !asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return NotificationPermission(
        denied = !isGranted && asked,
        openSettings = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        },
    )
}

@Composable
fun ActivityContent(
    state: ActivityUiState,
    snackbar: SnackbarHostState,
    showPermissionCard: Boolean,
    onOpenSettings: () -> Unit,
    onItem: (NotificationUi) -> Unit,
    onMarkAllRead: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    AvailablissBackground(OrbLayout.Main) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Activity",
                subtitle = if (state.unread > 0) "${state.unread} new" else "You're all caught up",
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.s),
            ) {
                if (state.items.any { it.unread }) TextAction("Mark all read", onMarkAllRead)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.s, bottom = Spacing.tabBarClearance + 24.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (showPermissionCard) {
                    item(key = "permission") { PermissionCard(onOpenSettings) }
                }
                state.error?.let { error -> item(key = "error") { ErrorBanner(error, onRetry = onRetry) } }
                when {
                    state.loading && state.items.isEmpty() -> item(key = "loading") { LoadingIndicator() }
                    state.items.isEmpty() && state.error == null -> item(key = "empty") {
                        EmptyState(
                            title = "Nothing yet",
                            body = "Proposals, plans and messages from your groups will show up here.",
                            icon = AvIcons.Bell,
                        )
                    }
                    else -> items(state.items, key = { it.id }) { item -> NotificationRow(item, onClick = { onItem(item) }) }
                }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.tabBarClearance))
    }
}

@Composable
private fun NotificationRow(item: NotificationUi, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = buildString {
                if (item.unread) append("Unread. ")
                append(item.title).append(". ").append(item.body).append(". ").append(item.time)
            }
        },
        shape = Shapes.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        onClick = onClick,
        onClickLabel = "Open",
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                Icon(item.kind.icon(), null, tint = colors.accentInk, modifier = Modifier.size(17.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        item.title,
                        style = type.bodySmallStrong,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(item.time, style = type.micro.copy(fontWeight = FontWeight.Medium), color = colors.muted)
                    if (item.unread) Box(Modifier.size(8.dp).clip(Shapes.pill).background(colors.accent))
                }
                Text(item.body, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PermissionCard(onOpenSettings: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(Modifier.fillMaxWidth(), shape = Shapes.row, contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Icon(AvIcons.Bell, null, tint = colors.warnInk, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text("Notifications are off", style = AvailablissTheme.type.bodySmallStrong, color = colors.ink)
                Text(
                    "Turn them on to hear when a time is proposed or everyone's in.",
                    style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
            }
            TextAction("Turn on", onOpenSettings)
        }
    }
}

private fun NotificationKind.icon(): ImageVector = when (this) {
    NotificationKind.ProposalCreated -> AvIcons.ProposalCheck
    NotificationKind.ProposalConfirmed -> AvIcons.Check
    NotificationKind.Nudge -> AvIcons.Clock
    NotificationKind.InviteAccepted -> AvIcons.AddPerson
    NotificationKind.Message -> AvIcons.Chat
    NotificationKind.EventCreated -> AvIcons.CalendarAdd
    NotificationKind.Unknown -> AvIcons.Bell
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ActivityPreview() {
    AvailablissPreviewTheme {
        ActivityContent(
            state = ActivityUiState(
                loading = false,
                unread = 1,
                items = listOf(
                    NotificationUi("1", NotificationKind.ProposalCreated, "Ana proposed a time", "Fri 13 – Sun 15 Nov · Lake District crew", "2h ago", true, null),
                    NotificationUi("2", NotificationKind.ProposalConfirmed, "Everyone's in", "Book club · Wed 11 Nov · 18:30", "1d ago", false, null),
                ),
            ),
            snackbar = SnackbarHostState(),
            showPermissionCard = false,
            onOpenSettings = {},
            onItem = {},
            onMarkAllRead = {},
            onRetry = {},
        )
    }
}
