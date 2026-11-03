package dev.joeherbert.availabliss.feature.you

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.BuildConfig
import dev.joeherbert.availabliss.core.network.model.LinkProviders
import dev.joeherbert.availabliss.core.network.model.LinkedAccount
import dev.joeherbert.availabliss.core.network.model.LinkedAccounts
import dev.joeherbert.availabliss.core.network.model.User
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PillTone
import dev.joeherbert.availabliss.designsystem.component.StatusPill
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.component.SectionLabel
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import dev.joeherbert.availabliss.feature.calendars.IconTile
import dev.joeherbert.availabliss.feature.plan.UpgradeSheet

@Composable
fun YouScreen(
    onOpenAppearance: () -> Unit,
    onOpenCalendars: () -> Unit,
    onOpenRegion: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Credential Manager sheets run in this scope so the ViewModel never holds the Activity.
    val scope = rememberCoroutineScope()
    val snackbar = rememberSnackbarHostState()

    LaunchedEffect(state.deleted) { if (state.deleted) onSignOut() }
    LaunchedEffect(Unit) {
        viewModel.refreshTemplates()
        viewModel.refreshPlan()
        viewModel.refreshAccounts()
    }
    var upgrading by rememberSaveable { mutableStateOf(false) }
    if (upgrading) UpgradeSheet(onDismiss = { upgrading = false }, onUpgraded = viewModel::upgraded)
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)

    AvailablissBackground(OrbLayout.Main) {
        YouContent(
            state = state,
            onEditName = viewModel::startEditingName,
            onOpenCalendars = onOpenCalendars,
            onOpenAppearance = onOpenAppearance,
            onOpenRegion = onOpenRegion,
            onAddPasskey = { scope.launch { viewModel.addPasskey(context) } },
            onLink = { provider -> scope.launch { viewModel.link(provider, context) } },
            onSignOut = onSignOut,
            onDelete = viewModel::requestDelete,
            onOpenPro = { upgrading = true },
            templateActions = TemplateActions(
                onRename = viewModel::startRenamingTemplate,
                onNameChange = viewModel::onTemplateNameChange,
                onSaveName = viewModel::saveTemplateName,
                onCancelRename = viewModel::cancelRenamingTemplate,
                onDelete = viewModel::deleteTemplate,
            ),
        )
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.tabBarClearance))
    }

    state.nameDraft?.let { draft ->
        AvBottomSheet(title = "Your name", onDismissRequest = viewModel::cancelEditingName) {
            Text("This is how you appear to your groups.", style = AvailablissTheme.type.body, color = AvailablissTheme.colors.muted)
            AvTextField(value = draft, onValueChange = viewModel::onNameChange, placeholder = "Your name", imeAction = ImeAction.Done, onImeAction = viewModel::saveName)
            PrimaryButton(
                "Save",
                viewModel::saveName,
                height = 52.dp,
                shape = Shapes.control,
                enabled = draft.isNotBlank(),
                loading = state.savingName,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    state.pendingMerge?.let { pending ->
        val colors = AvailablissTheme.colors
        AlertDialog(
            onDismissRequest = viewModel::cancelMerge,
            containerColor = colors.bg,
            shape = Shapes.cardLarge,
            tonalElevation = 0.dp,
            title = { Text("Merge accounts?", style = AvailablissTheme.type.headlineSmall, color = colors.ink) },
            text = { Text(mergePrompt(pending.account), style = AvailablissTheme.type.body, color = colors.muted) },
            confirmButton = { TextAction("Merge", viewModel::confirmMerge, color = colors.warnInk) },
            dismissButton = { TextAction("Cancel", viewModel::cancelMerge, color = colors.muted) },
        )
    }

    if (state.confirmingDelete) {
        val colors = AvailablissTheme.colors
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = colors.bg,
            shape = Shapes.cardLarge,
            tonalElevation = 0.dp,
            title = { Text("Delete your account?", style = AvailablissTheme.type.headlineSmall, color = colors.ink) },
            text = {
                Text(
                    "This removes you from every group and deletes your busy times, blocks and messages. It can't be undone.",
                    style = AvailablissTheme.type.body,
                    color = colors.muted,
                )
            },
            confirmButton = {
                if (state.deleting) {
                    CircularProgressIndicator(color = colors.warnInk, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    TextAction("Delete", viewModel::confirmDelete, color = colors.warnInk)
                }
            },
            dismissButton = { TextAction("Cancel", viewModel::cancelDelete, color = colors.muted) },
        )
    }
}

@Composable
fun YouContent(
    state: YouUiState,
    onEditName: () -> Unit,
    onOpenCalendars: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenRegion: () -> Unit,
    onAddPasskey: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onLink: (provider: String) -> Unit = {},
    onOpenPro: () -> Unit = {},
    templateActions: TemplateActions = TemplateActions(),
) {
    val colors = AvailablissTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // Keeps a template's rename field above the keyboard.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = Spacing.tabBarClearance),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("You", style = AvailablissTheme.type.displayMedium, color = colors.ink, modifier = Modifier.semantics { heading() })

        state.user?.let { user -> ProfileCard(user, onEditName) }

        GlassPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.Top) {
            SettingsRow(AvIcons.CalendarSync, "Calendars", "Connected calendars and what counts as busy", onOpenCalendars)
            RowDivider()
            SettingsRow(AvIcons.Palette, "Appearance", state.appearanceSummary, onOpenAppearance)
            RowDivider()
            SettingsRow(AvIcons.Globe, "Region & holidays", state.regionSummary, onOpenRegion)
            RowDivider()
            SettingsRow(
                AvIcons.Passkey,
                "Add a passkey",
                "Sign in with your fingerprint, face or screen lock",
                onAddPasskey,
                loading = state.addingPasskey,
            )
        }

        SignInMethodsPanel(state, onLink)

        PlanSection(state.plan, onOpenPro)

        TemplatesSection(state, templateActions)

        GlassPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.Top) {
            SettingsRow(AvIcons.SignOut, "Sign out", null, onSignOut, showChevron = false)
            RowDivider()
            SettingsRow(AvIcons.Trash, "Delete account", null, onDelete, showChevron = false, tint = colors.warnInk)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(AvIcons.Shield, null, tint = colors.muted, modifier = Modifier.padding(top = 1.dp).size(14.dp))
            Text(
                "Your groups only ever see when you're busy, never what you're doing.",
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
            )
        }

        Text(
            "Availabliss v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileCard(user: User, onEditName: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(shape = Shapes.cardLarge, contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(AvatarPerson(user.id, user.name, user.image), size = 56.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.name, style = AvailablissTheme.type.titleLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.email, style = AvailablissTheme.type.labelRegular, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassIconButton(AvIcons.Pencil, "Edit your name", onEditName, iconSize = 18.dp)
        }
    }
}

/** "Sign-in methods": the email code row, then one row per provider the server can link. */
@Composable
private fun SignInMethodsPanel(state: YouUiState, onLink: (String) -> Unit) {
    val user = state.user ?: return
    val colors = AvailablissTheme.colors
    GlassPanel(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        SectionLabel("Sign-in methods")
        SettingsRow(AvIcons.Mail, "Email code", user.email, onClick = null, showChevron = false)
        state.accounts?.available.orEmpty().forEach { provider ->
            val linked = state.accounts?.isLinked(provider) == true
            val linkable = provider in state.linkableProviders
            val label = LinkProviders.label(provider)
            RowDivider()
            SettingsRow(
                icon = when (provider) {
                    LinkProviders.APPLE -> AvIcons.Apple
                    LinkProviders.GOOGLE -> AvIcons.Google
                    else -> AvIcons.Link
                },
                title = label,
                subtitle = when {
                    linked -> null
                    // No Apple SDK on Android (sign-in is a web flow with no id token), so link it elsewhere.
                    !linkable && provider == LinkProviders.APPLE -> "Link on the web or iPhone app"
                    !linkable -> "Link on the web"
                    else -> "Sign in with $label too"
                },
                onClick = if (!linked && linkable) ({ onLink(provider) }) else null,
                showChevron = false,
                loading = state.linkingProvider == provider,
                trailing = when {
                    linked -> ({ StatusPill("Linked", PillTone.Mint, icon = AvIcons.Check) })
                    linkable -> ({ Text("Link", style = AvailablissTheme.type.bodySmallStrong, color = colors.accentInk) })
                    else -> ({ Text("Not linked", style = AvailablissTheme.type.caption, color = colors.muted) })
                },
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    showChevron: Boolean = true,
    loading: Boolean = false,
    tint: Color = AvailablissTheme.colors.ink,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (subtitle != null) 62.dp else 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = !loading, role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = AvailablissTheme.type.bodyMedium, color = tint)
            subtitle?.let { Text(it, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        when {
            loading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            trailing != null -> trailing()
            showChevron -> Icon(AvIcons.Forward, null, tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun YouPreview() {
    AvailablissPreviewTheme {
        AvailablissBackground(OrbLayout.Main) {
            YouContent(
                state = YouUiState(
                    user = User(id = "u1", name = "Sam Taylor", email = "sam@example.com", country = "GB", subdivision = "GB-ENG"),
                    accounts = LinkedAccounts(
                        linked = listOf(LinkedAccount(LinkProviders.APPLE, "2026-10-01T09:00:00Z")),
                        available = listOf(LinkProviders.APPLE, LinkProviders.GOOGLE),
                    ),
                    linkableProviders = setOf(LinkProviders.GOOGLE),
                ),
                onEditName = {}, onOpenCalendars = {}, onOpenAppearance = {}, onOpenRegion = {}, onAddPasskey = {}, onSignOut = {}, onDelete = {},
            )
        }
    }
}
