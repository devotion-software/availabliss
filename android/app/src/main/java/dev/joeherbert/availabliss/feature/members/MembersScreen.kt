package dev.joeherbert.availabliss.feature.members

import androidx.compose.runtime.rememberUpdatedState
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.Role
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.common.InviteRecipient
import dev.joeherbert.availabliss.feature.common.InviteRecipientsField
import dev.joeherbert.availabliss.feature.common.rememberPhotoPicker

/** Members & invites for one group. */
@Composable
fun MembersScreen(onBack: () -> Unit, onLeftGroup: () -> Unit, viewModel: MembersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    val context = LocalContext.current
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)

    LaunchedEffect(state.pendingShareUrl) {
        val url = state.pendingShareUrl ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join ${state.groupName} on Availabliss")
            putExtra(Intent.EXTRA_TEXT, "Join ${state.groupName} on Availabliss so we can find a time that works: $url")
        }
        context.startActivity(Intent.createChooser(send, "Share invite link"))
        viewModel.onShareHandled()
    }
    LaunchedEffect(state.left) { if (state.left) onLeftGroup() }

    val currentOnBack by rememberUpdatedState(onBack)
    val pickPhoto = rememberPhotoPicker(onUnreadable = viewModel::photoUnreadable, onPicked = viewModel::setPhoto)
    // One instance for the screen's lifetime so member rows skip recomposition.
    val actions = remember(viewModel) {
        MembersActions(
            onBack = { currentOnBack() },
            onShareLink = viewModel::shareInviteLink,
            onInviteDraftChange = viewModel::onInviteDraftChange,
            onAddRecipient = viewModel::addInviteRecipient,
            onRemoveRecipient = viewModel::removeInviteRecipient,
            onSendInvites = viewModel::sendInvites,
            onRevokeInvite = viewModel::revokeInvite,
            onPromote = { viewModel.setRole(it, Role.Admin) },
            onDemote = { viewModel.setRole(it, Role.Member) },
            onRemove = viewModel::requestRemove,
            onLeave = viewModel::requestLeave,
            onDelete = viewModel::requestDelete,
            onRetry = viewModel::refresh,
            onPickPhoto = pickPhoto,
            onRemovePhoto = { viewModel.setPhoto(null) },
        )
    }
    MembersContent(state = state, snackbar = snackbar, actions = actions)

    state.confirm?.let { action ->
        ConfirmDialog(
            action = action,
            groupName = state.groupName,
            working = state.working,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirm,
        )
    }
}

data class MembersActions(
    val onBack: () -> Unit = {},
    val onShareLink: () -> Unit = {},
    val onInviteDraftChange: (String) -> Unit = {},
    val onAddRecipient: () -> Unit = {},
    val onRemoveRecipient: (InviteRecipient) -> Unit = {},
    val onSendInvites: () -> Unit = {},
    val onRevokeInvite: (InviteUi) -> Unit = {},
    val onPromote: (MemberUi) -> Unit = {},
    val onDemote: (MemberUi) -> Unit = {},
    val onRemove: (MemberUi) -> Unit = {},
    val onLeave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
)

@Composable
fun MembersContent(state: MembersUiState, snackbar: SnackbarHostState, actions: MembersActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    AvailablissBackground(OrbLayout.GroupCalendar) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(
                title = "Members",
                subtitle = state.groupName.takeIf { it.isNotBlank() }?.let { "$it · ${state.members.size} members" },
                onBack = actions.onBack,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.s),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                state.error?.let { ErrorBanner(it, onRetry = actions.onRetry) }

                if (state.groupName.isNotBlank()) GroupCard(state, actions)

                // Only owners and admins can invite.
                if (state.isAdmin) InviteCard(state, actions)

                SectionLabel("${state.members.size} members", Modifier.padding(start = 4.dp))
                if (state.loading && state.members.isEmpty()) {
                    LoadingIndicator()
                } else {
                    GlassPanel(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxs),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        state.members.forEachIndexed { i, member ->
                            if (i > 0) RowDivider()
                            MemberRow(member, actions)
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().padding(top = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!state.isOwner) TextAction("Leave group", actions.onLeave, color = colors.warnInk)
                    if (state.isOwner) TextAction("Delete group", actions.onDelete, color = colors.warnInk)
                }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** The group's tile and name. Admins tap the tile to add, change or remove the group's photo. */
@Composable
private fun GroupCard(state: MembersUiState, actions: MembersActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var menu by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                val tile = Modifier.clip(Shapes.controlLarge)
                GroupTile(
                    state.monogram,
                    colors.tile(state.tileColor),
                    image = state.groupImage,
                    size = 56.dp,
                    radius = 18.dp,
                    modifier = if (state.isAdmin) {
                        tile.clickable(enabled = !state.uploadingPhoto, onClickLabel = if (state.groupImage == null) "Add a group photo" else "Change the group photo") {
                            if (state.groupImage == null) actions.onPickPhoto() else menu = true
                        }
                    } else {
                        tile
                    },
                )
                if (state.isAdmin) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(colors.solid),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.uploadingPhoto) CircularProgressIndicator(Modifier.size(12.dp), color = colors.onSolid, strokeWidth = 2.dp)
                        else Icon(if (state.groupImage == null) AvIcons.Photo else AvIcons.Pencil, null, tint = colors.onSolid, modifier = Modifier.size(11.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = colors.bg) {
                    DropdownMenuItem(text = { Text("Choose a new photo", style = type.bodySmallStrong) }, onClick = { menu = false; actions.onPickPhoto() })
                    DropdownMenuItem(
                        text = { Text("Remove photo", style = type.bodySmallStrong, color = colors.warnInk) },
                        onClick = { menu = false; actions.onRemovePhoto() },
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.groupName, style = type.cardTitle, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.isAdmin) {
                    Text(
                        if (state.groupImage == null) "Tap the tile to add a group photo" else "Tap the photo to change it",
                        style = type.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: MemberUi, actions: MembersActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Avatar(AvatarPerson(member.userId, member.name, member.image), size = 34.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (member.isMe) "${member.name} (you)" else member.name,
                style = type.bodyMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (member.synced) "Synced" else "Not synced yet",
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = if (member.synced) colors.muted else colors.warnInk,
            )
        }
        StatusPill(member.role.label(), member.role.tone())
        if (member.hasActions) {
            Box {
                PlainIconButton(AvIcons.More, "Manage ${member.name}", { menuOpen = true }, size = 40.dp, iconSize = 18.dp, tint = colors.muted)
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.bg) {
                    if (member.canPromote) {
                        DropdownMenuItem(text = { Text("Make admin", style = type.bodySmallStrong) }, onClick = { menuOpen = false; actions.onPromote(member) })
                    }
                    if (member.canDemote) {
                        DropdownMenuItem(text = { Text("Make member", style = type.bodySmallStrong) }, onClick = { menuOpen = false; actions.onDemote(member) })
                    }
                    if (member.canRemove) {
                        DropdownMenuItem(
                            text = { Text("Remove from group", style = type.bodySmallStrong, color = colors.warnInk) },
                            onClick = { menuOpen = false; actions.onRemove(member) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(action: ConfirmAction, groupName: String, working: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AvailablissTheme.colors
    val (title, body, cta) = when (action) {
        is ConfirmAction.Remove -> Triple("Remove ${action.member.name}?", "They'll lose access to $groupName and its chat.", "Remove")
        ConfirmAction.Leave -> Triple("Leave $groupName?", "You'll need a new invite to come back.", "Leave")
        ConfirmAction.DeleteGroup -> Triple("Delete $groupName?", "This deletes the group, its chat and plans for everyone. It can't be undone.", "Delete")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        shape = Shapes.cardLarge,
        tonalElevation = 0.dp,
        title = { Text(title, style = AvailablissTheme.type.headlineSmall, color = colors.ink) },
        text = { Text(body, style = AvailablissTheme.type.body, color = colors.muted) },
        confirmButton = { TextAction(cta, onConfirm, color = colors.warnInk, enabled = !working) },
        dismissButton = { TextAction("Cancel", onDismiss, color = colors.muted) },
    )
}

private fun Role.label() = when (this) {
    Role.Owner -> "Owner"
    Role.Admin -> "Admin"
    Role.Member -> "Member"
}

private fun Role.tone() = when (this) {
    Role.Owner -> PillTone.Indigo
    Role.Admin -> PillTone.Mint
    Role.Member -> PillTone.Neutral
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MembersPreview() {
    fun m(id: String, name: String, role: Role, synced: Boolean, me: Boolean = false) =
        MemberUi(id, name, null, role, synced, me, canPromote = role == Role.Member, canDemote = role == Role.Admin, canRemove = !me)
    AvailablissPreviewTheme {
        MembersContent(
            state = MembersUiState(
                groupName = "Lake District crew",
                members = listOf(
                    m("1", "Sam", Role.Owner, true, me = true),
                    m("2", "Ana", Role.Admin, true),
                    m("3", "Priya", Role.Member, true),
                    m("4", "Leo", Role.Member, false),
                ),
                myRole = Role.Owner,
                loading = false,
            ),
            snackbar = SnackbarHostState(),
            actions = MembersActions(),
        )
    }
}

/** Two ways in: a link anyone can use, or invites for particular people (by email or username) that only they can accept; then the live invites. */
@Composable
private fun InviteCard(state: MembersUiState, actions: MembersActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Text("Invite people", style = type.cardTitle, color = colors.ink)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Share a link", style = type.bodySmallStrong, color = colors.ink)
            Text("Anyone with the link can join.", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        PrimaryButton(
            text = "Share invite link",
            onClick = actions.onShareLink,
            icon = AvIcons.Share,
            height = 50.dp,
            shape = Shapes.control,
            loading = state.creatingLink,
            modifier = Modifier.fillMaxWidth(),
        )
        RowDivider()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Or invite particular people", style = type.bodySmallStrong, color = colors.ink)
            Text(
                "Add their email or username. Emails get their own invite by email, usernames in Activity. Only that person can join.",
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        InviteRecipientsField(
            state = state.recipients,
            onDraftChange = actions.onInviteDraftChange,
            onAdd = actions.onAddRecipient,
            onRemove = actions.onRemoveRecipient,
            label = "Emails or usernames",
            height = 48.dp,
            shape = Shapes.controlSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = if (state.sendingInvites) "Sending…" else "Send invites",
            onClick = actions.onSendInvites,
            height = 44.dp,
            shape = Shapes.controlSmall,
            enabled = state.recipients.canSend && !state.sendingInvites,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.invites.isNotEmpty()) {
            RowDivider()
            Text("Invites", style = type.bodySmallStrong, color = colors.ink)
            Column {
                state.invites.forEachIndexed { index, invite ->
                    if (index > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (invite.isLink) AvIcons.Link else if (invite.byUsername) AvIcons.Person else AvIcons.Mail, null, tint = colors.accentInk, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(invite.title, style = type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                            Text(invite.status, style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
                        }
                        TextAction("Revoke", onClick = { actions.onRevokeInvite(invite) }, color = colors.warnInk)
                    }
                }
            }
        }
    }
}
