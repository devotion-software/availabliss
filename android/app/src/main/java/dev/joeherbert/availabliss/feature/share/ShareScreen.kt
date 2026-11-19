package dev.joeherbert.availabliss.feature.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.GroupSummary
import dev.joeherbert.availabliss.core.share.ShareInbox
import dev.joeherbert.availabliss.core.share.SharedContent
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.EmptyState
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassPanel
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.ScreenHeader
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.designsystem.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val groups: GroupsRepository,
    private val inbox: ShareInbox,
) : ViewModel() {
    val shared = inbox.pending
    val groupList = groups.groups
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    init {
        load()
    }

    fun load() {
        _failed.value = false
        viewModelScope.launch { _failed.value = groups.refreshGroups() is ApiResult.Failure && groups.groups.value == null }
    }

    fun pick(groupId: String) = inbox.sendTo(groupId)

    fun cancel() = inbox.cancel()
}

/**
 * "Send to…" after choosing Availabliss on another app's share sheet: your group chats (not
 * archived), and what's being shared. Picking one opens its chat with it attached, ready to send.
 */
@Composable
fun ShareScreen(onBack: () -> Unit, onOpenChat: (groupId: String) -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val shared by viewModel.shared.collectAsStateWithLifecycle()
    val groups by viewModel.groupList.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    ShareContent(
        shared = shared,
        groups = groups?.filter { !it.archived },
        failed = failed,
        onRetry = viewModel::load,
        onBack = {
            viewModel.cancel()
            onBack()
        },
        onPick = { id ->
            viewModel.pick(id)
            onOpenChat(id)
        },
    )
}

@Composable
fun ShareContent(
    shared: SharedContent?,
    groups: List<GroupSummary>?,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    // Nothing (left) to share, e.g. after sending: there's nothing for this screen to do.
    LaunchedEffect(shared) { if (shared == null) onBack() }
    val colors = AvailablissTheme.colors
    AvailablissBackground(OrbLayout.Chat) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(
                title = "Send to…",
                subtitle = shared?.let(::summary).orEmpty(),
                onBack = onBack,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.screen, bottom = Spacing.s),
            )
            when {
                groups == null && failed -> ErrorBanner("Couldn't load your groups.", Modifier.padding(Spacing.screen), onRetry = onRetry)
                groups == null -> LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                groups.isEmpty() -> EmptyState(
                    title = "No group chats yet",
                    body = "Make a group or join one, then share with it from here.",
                    icon = AvIcons.Chat,
                    modifier = Modifier.padding(Spacing.screen),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    items(groups, key = { it.id }) { group ->
                        GlassPanel(
                            Modifier.fillMaxWidth(),
                            shape = Shapes.row,
                            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
                            onClick = { onPick(group.id) },
                            onClickLabel = "Send to ${group.name}",
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                                GroupTile(group.monogram, colors.tile(group.color), image = group.image, size = 40.dp, radius = 13.dp)
                                Text(group.name, style = AvailablissTheme.type.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "3 items", "1 item", or "Text". */
private fun summary(content: SharedContent): String = when (val n = content.uris.size) {
    0 -> "Text"
    1 -> "1 item"
    else -> "$n items"
}
