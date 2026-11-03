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

/** First-run setup: region, then which calendars mean busy. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    regionViewModel: RegionViewModel = hiltViewModel(),
    sourcesViewModel: CalendarSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val region by regionViewModel.state.collectAsStateWithLifecycle()
    val sources by sourcesViewModel.state.collectAsStateWithLifecycle()
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

    AvailablissBackground(if (state.step == OnboardingStep.Region) OrbLayout.SignIn else OrbLayout.Calendars) {
        AnimatedContent(state.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onboarding") { step ->
            when (step) {
                OnboardingStep.Region -> OnboardingPage(
                    stepNumber = 1,
                    onBack = null,
                    title = "Where are you based?",
                    subtitle = "We use this for bank holidays and school term dates, so groups never land on a day you're away.",
                    footer = {
                        PrimaryButton(
                            "Continue",
                            regionViewModel::save,
                            enabled = region.canSave,
                            loading = region.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                ) {
                    RegionForm(region, regionViewModel::openPicker, regionViewModel::useDeviceTimezone)
                }
                OnboardingStep.Calendars -> OnboardingPage(
                    stepNumber = 2,
                    onBack = viewModel::goToRegion,
                    title = "Which calendars mean you're busy?",
                    subtitle = "Events in these calendars block out your time. Only “busy” is shared, never the details.",
                    footer = {
                        PrimaryButton(
                            "Continue",
                            { viewModel.finish(markBusyByHand = false) },
                            loading = state.finishing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextAction(
                            "No calendar? Mark busy times yourself",
                            { viewModel.finish(markBusyByHand = true) },
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
                StepIndicator(current = stepNumber, total = 2)
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
