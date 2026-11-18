package dev.joeherbert.availabliss.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.Role as GroupRole
import dev.joeherbert.availabliss.core.network.model.Member
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ButtonStyle
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.ToggleRow
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.common.rememberPhotoPicker
import dev.joeherbert.availabliss.feature.newgroup.GroupColorPicker

/**
 * Group settings, from the group's More menu: its photo, name and colour, Archive / Unarchive, Leave
 * group and Delete group (each confirmed in a bottom sheet). Everyone can open it and leave; only the
 * owner and admins can change things, and only the owner can delete the group. Each part is its own
 * card, so further settings slot in as more cards. [onDeleted] also follows leaving: back to the groups.
 */
@Composable
fun GroupSettingsScreen(onBack: () -> Unit, onDeleted: () -> Unit, viewModel: GroupSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::messageShown)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.deleted, state.left) { if (state.deleted || state.left) currentOnDeleted() }

    val pickPhoto = rememberPhotoPicker(onUnreadable = viewModel::photoUnreadable, onPicked = viewModel::setPhoto)
    val actions = remember(viewModel) {
        GroupSettingsActions(
            onBack = { currentOnBack() },
            onRetry = viewModel::refresh,
            onPickPhoto = pickPhoto,
            onRemovePhoto = { viewModel.setPhoto(null) },
            onNameChange = viewModel::onNameChange,
            onSaveName = viewModel::saveName,
            onColor = viewModel::setColor,
            onMyNameChange = viewModel::onMyNameChange,
            onSaveMyName = { viewModel.saveMyName() },
            onUseAccountName = { viewModel.saveMyName(null) },
            onToggleArchived = viewModel::toggleArchived,
            onAutoArchive = viewModel::setAutoArchive,
            onDelete = viewModel::requestDelete,
            onLeave = viewModel::requestLeave,
        )
    }
    GroupSettingsContent(state, snackbar, actions)

    if (state.confirmingDelete) {
        DeleteGroupSheet(state.groupName, state.deleting, onConfirm = viewModel::delete, onDismiss = viewModel::dismissDelete)
    }
    if (state.confirmingLeave) {
        LeaveGroupSheet(
            state,
            onPick = viewModel::pickForLeave,
            onMakeAdmin = viewModel::makeAdminForLeave,
            onLeave = viewModel::leave,
            onDeleteInstead = viewModel::deleteInsteadOfLeaving,
            onDismiss = viewModel::dismissLeave,
        )
    }
}

data class GroupSettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onSaveName: () -> Unit = {},
    val onColor: (TileColor) -> Unit = {},
    val onMyNameChange: (String) -> Unit = {},
    val onSaveMyName: () -> Unit = {},
    val onUseAccountName: () -> Unit = {},
    val onToggleArchived: () -> Unit = {},
    val onAutoArchive: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onLeave: () -> Unit = {},
)

@Composable
fun GroupSettingsContent(state: GroupSettingsUiState, snackbar: SnackbarHostState, actions: GroupSettingsActions) {
    AvailablissBackground(OrbLayout.GroupCalendar) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = "Group settings",
                subtitle = state.groupName.takeIf { it.isNotBlank() },
                onBack = actions.onBack,
                backDescription = "Back to calendar",
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
                if (state.loading && state.groupName.isBlank()) {
                    LoadingIndicator()
                } else if (state.groupName.isNotBlank()) {
                    PhotoCard(state, actions)
                    DetailsCard(state, actions)
                    MyNameCard(state, actions)
                    if (state.canEdit) ArchiveCard(state, actions)
                    LeaveCard(state, actions)
                    if (state.canDelete) DeleteCard(actions)
                }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** The group's photo, shown instead of its monogram tile. Admins tap it to add, change or remove it. */
@Composable
private fun PhotoCard(state: GroupSettingsUiState, actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    var menu by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box {
                val tile = Modifier.clip(Shapes.controlLarge)
                GroupTile(
                    state.monogram,
                    colors.tile(state.color),
                    image = state.image,
                    size = 64.dp,
                    radius = 20.dp,
                    modifier = if (state.canEdit) {
                        tile.clickable(enabled = !state.uploadingPhoto, onClickLabel = if (state.image == null) "Add a group photo" else "Change the group photo") {
                            if (state.image == null) actions.onPickPhoto() else menu = true
                        }
                    } else {
                        tile
                    },
                )
                if (state.canEdit) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.solid),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.uploadingPhoto) CircularProgressIndicator(Modifier.size(12.dp), color = colors.onSolid, strokeWidth = 2.dp)
                        else Icon(if (state.image == null) AvIcons.Photo else AvIcons.Pencil, null, tint = colors.onSolid, modifier = Modifier.size(12.dp))
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
                Text("Group photo", style = type.bodySmallStrong, color = colors.ink)
                Text(
                    when {
                        !state.canEdit -> "Only the group's owner and admins can change the photo, name and colour."
                        state.image == null -> "Tap the tile to add one. It's shown instead of the “${state.monogram}” tile."
                        else -> "Everyone in the group sees it. Tap it to change it."
                    },
                    style = type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(state: GroupSettingsUiState, actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.canEdit) {
            AvTextField(
                value = state.nameDraft,
                onValueChange = actions.onNameChange,
                label = "Name",
                placeholder = "Group name",
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
                onImeAction = actions.onSaveName,
            )
            if (state.nameChanged) {
                PrimaryButton(
                    text = "Save name",
                    onClick = actions.onSaveName,
                    style = ButtonStyle.Accent,
                    height = 48.dp,
                    shape = Shapes.control,
                    loading = state.savingName,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            SectionLabel("Name")
            Text(state.groupName, style = AvailablissTheme.type.cardTitle, color = colors.ink)
        }
        SectionLabel("Colour", Modifier.padding(top = 4.dp))
        GroupColorPicker(
            state.color,
            state.monogram,
            actions.onColor,
            modifier = if (state.canEdit) Modifier else Modifier.alpha(0.6f),
            enabled = state.canEdit,
        )
    }
}

/** Everyone can choose the name they go by in this group (a nickname, say); blank goes back to the account name. */
@Composable
private fun MyNameCard(state: GroupSettingsUiState, actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Your name in this group", style = type.bodySmallStrong, color = colors.ink)
            Text(
                if (state.myNameInGroup == null) {
                    "${state.groupName} sees you as ${state.accountName}, your account name. Choose another name just for this group."
                } else {
                    "Only ${state.groupName} sees this. Everywhere else you're ${state.accountName}."
                },
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        AvTextField(
            value = state.myNameDraft,
            onValueChange = actions.onMyNameChange,
            placeholder = state.accountName,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            onImeAction = actions.onSaveMyName,
        )
        if (state.myNameChanged) {
            PrimaryButton(
                text = "Save name",
                onClick = actions.onSaveMyName,
                style = ButtonStyle.Accent,
                height = 48.dp,
                shape = Shapes.control,
                loading = state.savingMyName,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.myNameInGroup != null) {
            TextAction("Use ${state.accountName} instead", actions.onUseAccountName, enabled = !state.savingMyName)
        }
    }
}

@Composable
private fun ArchiveCard(state: GroupSettingsUiState, actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (state.archived) "Archived" else "Archive", style = type.bodySmallStrong, color = colors.ink)
            Text(
                if (state.archived) "It's under Archived on everyone's groups list. Unarchive it to bring it back."
                else "Moves it to Archived at the bottom of everyone's groups list. Nothing is deleted, and you can unarchive it any time.",
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        SecondaryButton(
            text = when {
                state.archiving -> if (state.archived) "Unarchiving…" else "Archiving…"
                state.archived -> "Unarchive group"
                else -> "Archive group"
            },
            onClick = actions.onToggleArchived,
            icon = AvIcons.Archive,
            height = 44.dp,
            shape = Shapes.controlSmall,
            enabled = !state.archiving,
            modifier = Modifier.fillMaxWidth(),
        )
        RowDivider()
        ToggleRow(
            title = "Archive automatically",
            subtitle = "Once its last event has passed and nothing else is planned. A new plan brings it back.",
            checked = state.autoArchive,
            onCheckedChange = actions.onAutoArchive,
            modifier = Modifier.alpha(if (state.savingAutoArchive) 0.5f else 1f),
        )
    }
}

@Composable
private fun LeaveCard(state: GroupSettingsUiState, actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Leave group", style = type.bodySmallStrong, color = colors.ink)
            Text(
                if (state.role == GroupRole.Owner) "You'll choose an admin to become the owner. You'll need a new invite to come back."
                else "You'll need a new invite to come back.",
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        WarningButton("Leave group", AvIcons.SignOut, onClick = actions.onLeave, height = 44.dp, shape = Shapes.controlSmall, textStyle = type.bodySmallStrong)
    }
}

/** Only the owner sees it. Its own card, like Leave group, so deleting reads as a deliberate step. */
@Composable
private fun DeleteCard(actions: GroupSettingsActions) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Delete group", style = type.bodySmallStrong, color = colors.ink)
            Text(
                "Deletes it for everyone in it, with its chat, plans and photos.",
                style = type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }
        WarningButton("Delete group", AvIcons.Trash, onClick = actions.onDelete, height = 44.dp, shape = Shapes.controlSmall, textStyle = type.bodySmallStrong)
    }
}

/**
 * Confirms leaving, following the server's rules: a member (or an admin while another admin stays)
 * just confirms; with nobody else an admin, you first pick someone to make an admin; the owner then
 * picks which admin becomes the owner; the only member is sent to Delete group instead.
 */
@Composable
private fun LeaveGroupSheet(
    state: GroupSettingsUiState,
    onPick: (String) -> Unit,
    onMakeAdmin: () -> Unit,
    onLeave: () -> Unit,
    onDeleteInstead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AvailablissTheme.colors
    val others = state.otherMembers
    val title = when {
        others.isEmpty() -> "You're the only member"
        state.leaveNeedsAdmin -> "Make someone an admin"
        else -> "Leave group?"
    }
    AvBottomSheet(title = title, onDismissRequest = onDismiss) {
        when {
            others.isEmpty() -> {
                SheetCaption("Nobody else is in ${state.groupName}, so there's no one to hand it to. Delete the group instead if you're done with it.")
                if (state.role == GroupRole.Owner) WarningButton("Delete group", AvIcons.Trash, onClick = onDeleteInstead)
            }
            state.leaveNeedsAdmin -> {
                SheetCaption("A group needs an admin, and nobody else in ${state.groupName} is one. Choose someone to make an admin, then you can leave.")
                MemberPicker(others, state.leavePick, onPick)
                PrimaryButton(
                    text = "Make admin",
                    onClick = onMakeAdmin,
                    icon = AvIcons.Shield,
                    style = ButtonStyle.Accent,
                    height = 52.dp,
                    shape = Shapes.control,
                    loading = state.leaving,
                    enabled = state.leavePick != null && !state.leaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                if (state.leaveChoosesOwner) {
                    SheetCaption("Choose who becomes the owner. They'll look after ${state.groupName}, and its bank holidays will follow their region.")
                    MemberPicker(state.otherAdmins, state.leavePick, onPick)
                }
                SheetCaption("You'll need a new invite to come back.")
                WarningButton(
                    "Leave group",
                    AvIcons.SignOut,
                    onClick = onLeave,
                    loading = state.leaving,
                    enabled = !state.leaving && (!state.leaveChoosesOwner || state.leavePick != null),
                )
            }
        }
        SecondaryButton("Cancel", onClick = onDismiss, height = 52.dp, enabled = !state.leaving, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SheetCaption(text: String) = Text(text, style = AvailablissTheme.type.body, color = AvailablissTheme.colors.muted)

/** One person to pick, as radio rows. */
@Composable
private fun MemberPicker(members: List<Member>, picked: String?, onPick: (String) -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(
        Modifier.fillMaxWidth().selectableGroup(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        members.forEachIndexed { i, member ->
            if (i > 0) RowDivider()
            val selected = member.userId == picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(member.userId) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(AvatarPerson(member.userId, member.name, member.image), size = 34.dp)
                Text(member.name, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                when (member.role) {
                    GroupRole.Owner -> StatusPill("Owner", PillTone.Indigo)
                    GroupRole.Admin -> StatusPill("Admin", PillTone.Mint)
                    GroupRole.Member -> Unit
                }
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(colors.accent) else Modifier.border(1.5.dp, colors.line2, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(AvIcons.Check, null, tint = colors.onAccent, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** A destructive `warn-bg` / `warn-ink` button, as Delete group's. */
@Composable
private fun WarningButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    height: Dp = 52.dp,
    shape: Shape = Shapes.control,
    textStyle: TextStyle = AvailablissTheme.type.bodyStrong,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = AvailablissTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(colors.warnBg)
            .alpha(if (enabled || loading) 1f else 0.5f)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = colors.warnInk, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = colors.warnInk, modifier = Modifier.size(18.dp))
                Text(text, style = textStyle, color = colors.warnInk)
            }
        }
    }
}

/** "Delete group?" with a destructive Delete and Cancel. Closing it keeps the group. */
@Composable
private fun DeleteGroupSheet(groupName: String, deleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = AvailablissTheme.colors
    AvBottomSheet(title = "Delete group?", onDismissRequest = onDismiss) {
        Text(
            "$groupName will be deleted for everyone in it, with its chat, plans and photos. This can't be undone.",
            style = AvailablissTheme.type.body,
            color = colors.muted,
        )
        WarningButton("Delete group", AvIcons.Trash, onClick = onConfirm, loading = deleting)
        SecondaryButton("Cancel", onClick = onDismiss, height = 52.dp, enabled = !deleting, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun GroupSettingsPreview() {
    AvailablissPreviewTheme {
        GroupSettingsContent(
            GroupSettingsUiState(
                loading = false,
                groupName = "Lake District crew",
                monogram = "LD",
                color = TileColor.T3,
                role = GroupRole.Owner,
                nameDraft = "Lake District crew",
            ),
            SnackbarHostState(),
            GroupSettingsActions(),
        )
    }
}
