package dev.joeherbert.availabliss.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/** Glass snackbar host. */
@Composable
fun AvSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        Box(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glass(Shapes.row, AvailablissTheme.colors, fill = AvailablissTheme.colors.bg)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(data.visuals.message, style = AvailablissTheme.type.labelRegular, color = AvailablissTheme.colors.ink)
        }
    }
}

/** Shows [message] once in [hostState], then calls [onShown] so the ViewModel can clear it. */
@Composable
fun UserMessageEffect(message: UserMessage?, hostState: SnackbarHostState, onShown: (UserMessage) -> Unit) {
    LaunchedEffect(message?.id) {
        val m = message ?: return@LaunchedEffect
        hostState.showSnackbar(m.text)
        onShown(m)
    }
}

@Composable
fun rememberSnackbarHostState(): SnackbarHostState = remember { SnackbarHostState() }
