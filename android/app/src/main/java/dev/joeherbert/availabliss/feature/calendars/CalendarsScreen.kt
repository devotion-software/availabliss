package dev.joeherbert.availabliss.feature.calendars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState

/** Settings: connected calendars and which ones mean busy. */
@Composable
fun CalendarsScreen(onBack: () -> Unit, viewModel: CalendarSourcesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    val requestPermission = rememberCalendarPermissionRequest(viewModel::onPermissionResult)

    // Returning from the OAuth Custom Tab (or system settings) lands here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    PendingUrlEffect(state.pendingUrl, viewModel::onUrlOpened)
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)

    AvailablissBackground(OrbLayout.Calendars) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(title = "Calendars", subtitle = "Only “busy” is shared, never the details", onBack = onBack)
            if (state.loading && state.deviceSources.isEmpty() && state.connections.isEmpty()) {
                LoadingIndicator()
            } else {
                CalendarSourcesContent(
                    state = state,
                    manage = true,
                    actions = CalendarSourcesActions(
                        onRequestPermission = requestPermission,
                        onToggleDevice = viewModel::toggleDeviceCalendar,
                        onToggleConnection = viewModel::toggleConnectionCalendar,
                        onConnect = viewModel::openConnectSheet,
                        onReconnect = viewModel::connectProvider,
                        onSyncConnection = viewModel::syncConnection,
                        onDisconnect = viewModel::disconnect,
                        onSyncDevice = { viewModel.syncDeviceNow() },
                    ),
                )
            }
        }
        AvSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    ConnectCalendarSheet(
        state = state,
        onDismiss = viewModel::closeConnectSheet,
        onGoogle = { viewModel.connectProvider("google") },
        onMicrosoft = { viewModel.connectProvider("microsoft") },
        onICloud = viewModel::chooseICloud,
        onBack = viewModel::backToProviders,
        onUsername = viewModel::onCalDavUsername,
        onPassword = viewModel::onCalDavPassword,
        onSubmitCalDav = viewModel::submitCalDav,
    )
}
