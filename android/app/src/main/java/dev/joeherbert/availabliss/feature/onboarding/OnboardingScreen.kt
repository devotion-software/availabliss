package dev.joeherbert.availabliss.feature.onboarding

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.joeherbert.availabliss.feature.timing.TimingPreset
import dev.joeherbert.availabliss.designsystem.component.SecondaryButton
import dev.joeherbert.availabliss.designsystem.component.Chip
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PrimaryButton
import dev.joeherbert.availabliss.designsystem.component.StepIndicator
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.feature.calendars.CalendarSourcesActions
import dev.joeherbert.availabliss.feature.calendars.CalendarSourcesContent
import dev.joeherbert.availabliss.feature.calendars.CalendarSourcesViewModel
import dev.joeherbert.availabliss.feature.calendars.ConnectCalendarSheet
import dev.joeherbert.availabliss.feature.calendars.PendingUrlEffect
import dev.joeherbert.availabliss.feature.calendars.rememberCalendarPermissionRequest
import dev.joeherbert.availabliss.feature.common.InviteRecipientsField
import dev.joeherbert.availabliss.feature.common.UsernameField

/** The sign-up wizard: about you, calendars, your first group, invite people. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    regionViewModel: RegionViewModel = hiltViewModel(),
    sourcesViewModel: CalendarSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val region by regionViewModel.state.collectAsStateWithLifecycle()
    val sources by sourcesViewModel.state.collectAsStateWithLifecycle()
    val username by regionViewModel.username.collectAsStateWithLifecycle()
    val recipients by viewModel.inviteRecipients.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    val requestPermission = rememberCalendarPermissionRequest(
        blocked = sources.permissionBlocked,
        onResult = sourcesViewModel::onPermissionResult,
    )

    val currentRequestPermission by rememberUpdatedState(requestPermission)
    val sourceActions = remember(sourcesViewModel) {
        CalendarSourcesActions(
            onRequestPermission = { currentRequestPermission() },
            onToggleDevice = sourcesViewModel::toggleDeviceCalendar,
            onToggleConnection = sourcesViewModel::toggleConnectionCalendar,
            onConnect = sourcesViewModel::openConnectSheet,
            onReconnect = sourcesViewModel::connectProvider,
        )
    }

    LaunchedEffect(region.saved) {
        if (region.saved) {
            regionViewModel.onSaveHandled()
            viewModel.goToCalendars()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.step == OnboardingStep.Calendars) sourcesViewModel.refresh()
    }
    PendingUrlEffect(sources.pendingUrl, sourcesViewModel::onUrlOpened)
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    UserMessageEffect(region.message, snackbar, regionViewModel::onMessageShown)
    UserMessageEffect(sources.message, snackbar, sourcesViewModel::onMessageShown)
    BackHandler(enabled = state.step == OnboardingStep.Calendars, onBack = viewModel::goToRegion)
    BackHandler(enabled = state.step == OnboardingStep.Group, onBack = viewModel::backToCalendars)
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(state.pendingShareUrl) {
        val url = state.pendingShareUrl ?: return@LaunchedEffect
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "Join ${state.groupName.trim()} on Availabliss so we can find a time that works: $url")
        }
        context.startActivity(android.content.Intent.createChooser(send, "Share invite link"))
        viewModel.onShareHandled()
    }

    AvailablissBackground(if (state.step == OnboardingStep.Region) OrbLayout.SignIn else OrbLayout.Calendars) {
        AnimatedContent(state.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { step ->
            when (step) {
                OnboardingStep.Region -> OnboardingPage(
                    stepNumber = 1,
                    steps = state.steps,
                    onBack = null,
                    title = "About you",
                    subtitle = "Your name is how your groups see you, and people can invite you by your username. Your region sets bank holidays and school term dates.",
                    footer = {
                        PrimaryButton(
                            "Continue",
                            { regionViewModel.save(includingName = true) },
                            enabled = region.canSave && region.name.isNotBlank() && username.canSave,
                            loading = region.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                ) {
                    AvTextField(
                        value = region.name,
                        onValueChange = regionViewModel::onNameChange,
                        label = "Your name",
                        placeholder = "First and last name",
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UsernameField(username, regionViewModel::onUsernameChange, Modifier.fillMaxWidth())
                    RegionForm(region, regionViewModel::openPicker, regionViewModel::useDeviceTimezone)
                }
                OnboardingStep.Calendars -> OnboardingPage(
                    stepNumber = 2,
                    steps = state.steps,
                    onBack = viewModel::goToRegion,
                    title = "Which calendars mean you're busy?",
                    subtitle = "Events in these calendars block out your time. Only “busy” is shared, never the details.",
                    footer = {
                        PrimaryButton(
                            "Continue",
                            { viewModel.calendarsDone(byHand = false) },
                            loading = state.finishing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextAction(
                            "No calendar? Mark busy times yourself",
                            { viewModel.calendarsDone(byHand = true) },
                            color = AvailablissTheme.colors.ink,
                            enabled = !state.finishing,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    },
                ) {
                    CalendarSourcesContent(
                        state = sources,
                        actions = sourceActions,
                    )
                    RegionSummaryCard(region.summaryTitle, region.summarySubtitle, onChange = viewModel::goToRegion)
                }
                OnboardingStep.Group -> OnboardingPage(
                    stepNumber = 3,
                    steps = state.steps,
                    onBack = viewModel::backToCalendars,
                    title = "Start your first group",
                    subtitle = "Who are you trying to find a time with? You can change everything later.",
                    footer = {
                        PrimaryButton(
                            "Create group",
                            viewModel::createGroup,
                            enabled = state.groupName.isNotBlank(),
                            loading = state.creating,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextAction(
                            "Skip for now",
                            viewModel::finish,
                            color = AvailablissTheme.colors.ink,
                            enabled = !state.finishing && !state.creating,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    },
                ) {
                    AvTextField(
                        value = state.groupName,
                        onValueChange = viewModel::onGroupNameChange,
                        label = "Group name",
                        placeholder = "e.g. Lake District crew",
                        capitalization = KeyboardCapitalization.Words,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("What are you planning?", style = AvailablissTheme.type.label, color = AvailablissTheme.colors.muted)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TimingPreset.entries.filter { it != TimingPreset.Custom }.forEach { preset ->
                            Chip(preset.label, selected = state.preset == preset, onClick = { viewModel.onPresetChange(preset) }, height = 40.dp)
                        }
                    }
                }
                OnboardingStep.Invite -> OnboardingPage(
                    stepNumber = 4,
                    steps = state.steps,
                    onBack = null,
                    title = "Invite people to ${state.groupName.trim()}",
                    subtitle = "Once they've added their busy times, you'll see when everyone's free.",
                    footer = {
                        PrimaryButton(
                            if (state.inviteLink == null && state.invited.isEmpty()) "Skip for now" else "Done",
                            viewModel::finish,
                            loading = state.finishing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                ) {
                    GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Share a link", style = AvailablissTheme.type.bodySmallStrong, color = AvailablissTheme.colors.ink)
                        Text("Anyone with the link can join.", style = AvailablissTheme.type.caption, color = AvailablissTheme.colors.muted)
                        PrimaryButton("Share invite link", viewModel::shareLink, icon = AvIcons.Share, loading = state.creatingLink, modifier = Modifier.fillMaxWidth())
                    }
                    GlassPanel(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Or invite particular people", style = AvailablissTheme.type.bodySmallStrong, color = AvailablissTheme.colors.ink)
                        Text("Add their email or username. Each person gets their own invite; only they can use it.", style = AvailablissTheme.type.caption, color = AvailablissTheme.colors.muted)
                        InviteRecipientsField(
                            state = recipients,
                            onDraftChange = viewModel::onInviteDraftChange,
                            onAdd = viewModel::addInviteRecipient,
                            onRemove = viewModel::removeInviteRecipient,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryButton(
                            if (state.sendingInvites) "Sending…" else "Send invites",
                            viewModel::sendInvites,
                            enabled = recipients.canSend && !state.sendingInvites,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.invited.isNotEmpty()) {
                            Text("Invited: ${state.invited.joinToString()}", style = AvailablissTheme.type.caption, color = AvailablissTheme.colors.muted)
                        }
                    }
                }
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    RegionPickerSheet(region, regionViewModel::onQueryChange, regionViewModel::pick, regionViewModel::closePicker)
    ConnectCalendarSheet(
        state = sources,
        onDismiss = sourcesViewModel::closeConnectSheet,
        onGoogle = { sourcesViewModel.connectProvider("google") },
        onMicrosoft = { sourcesViewModel.connectProvider("microsoft") },
        onICloud = sourcesViewModel::chooseICloud,
        onBack = sourcesViewModel::backToProviders,
        onUsername = sourcesViewModel::onCalDavUsername,
        onPassword = sourcesViewModel::onCalDavPassword,
        onSubmitCalDav = sourcesViewModel::submitCalDav,
    )
}

/** Shared onboarding page frame: back + step indicator, title, scrolling body, pinned footer. */
@Composable
private fun OnboardingPage(
    stepNumber: Int,
    steps: Int,
    onBack: (() -> Unit)?,
    title: String,
    subtitle: String,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AvailablissTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) GlassIconButton(AvIcons.Back, "Back", onBack)
                Spacer(Modifier.weight(1f))
                StepIndicator(current = stepNumber, total = steps)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = AvailablissTheme.type.displaySmall, color = colors.ink, modifier = Modifier.semantics { heading() })
                Text(subtitle, style = AvailablissTheme.type.body.copy(lineHeight = AvailablissTheme.type.body.fontSize * 1.45f), color = colors.muted)
            }
            content()
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) { footer() }
    }
}

/** "United Kingdom · England / School holidays: …" card with Change. */
@Composable
fun RegionSummaryCard(title: String, subtitle: String, onChange: () -> Unit) {
    val colors = AvailablissTheme.colors
    GlassPanel(shape = dev.joeherbert.availabliss.designsystem.theme.Shapes.row, contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AvIcons.Location, null, tint = colors.okInk, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = AvailablissTheme.type.bodySmallStrong, color = colors.ink)
                Text(subtitle, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
            }
            TextAction("Change", onChange, style = AvailablissTheme.type.label)
        }
    }
}
