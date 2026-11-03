package dev.joeherbert.availabliss.feature.chat

import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.chat.ChatComposer
import dev.joeherbert.availabliss.designsystem.component.chat.ChatMeta
import dev.joeherbert.availabliss.designsystem.component.chat.MessageBubble
import dev.joeherbert.availabliss.designsystem.component.chat.PollCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.component.chat.ReadReceipt
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Everything the chat UI can ask for. */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadOlder: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onToggleMenu: () -> Unit = {},
    val onPoll: () -> Unit = {},
    val onProposeTime: () -> Unit = {},
    val onAddEvent: () -> Unit = {},
    val onOpenProposal: (String) -> Unit = {},
    val onOpenEvent: (String) -> Unit = {},
    val onVote: (messageId: String, optionId: String) -> Unit = { _, _ -> },
    val onClosePoll: (messageId: String) -> Unit = {},
    val onOpenInfo: (messageId: String) -> Unit = {},
)

/** The group's chat (full screen; back returns to the calendar tab). */
@Composable
fun GroupChatTab(
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onProposeTime: () -> Unit,
    onAddEvent: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    UserMessageEffect(state.message, snackbar, viewModel::onMessageShown)
    LifecycleResumeEffect(Unit) {
        viewModel.onVisibilityChanged(true)
        onPauseOrDispose { viewModel.onVisibilityChanged(false) }
    }
    BackHandler(enabled = state.menuOpen) { viewModel.closeMenu() }

    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenProposal by rememberUpdatedState(onOpenProposal)
    val currentOnOpenEvent by rememberUpdatedState(onOpenEvent)
    val currentOnProposeTime by rememberUpdatedState(onProposeTime)
    val currentOnAddEvent by rememberUpdatedState(onAddEvent)
    // One instance for the screen's lifetime, so rows skip recomposition while you type.
    val actions = remember(viewModel) {
        ChatActions(
            onBack = { currentOnBack() },
            onRetry = viewModel::load,
            onLoadOlder = viewModel::loadOlder,
            onDraftChange = viewModel::onDraftChange,
            onSend = viewModel::send,
            onToggleMenu = viewModel::onToggleMenu,
            onPoll = viewModel::openPollSheet,
            onProposeTime = {
                viewModel.closeMenu()
                currentOnProposeTime()
            },
            onAddEvent = {
                viewModel.closeMenu()
                currentOnAddEvent()
            },
            onOpenProposal = { currentOnOpenProposal(it) },
            onOpenEvent = { currentOnOpenEvent(it) },
            onVote = viewModel::onVote,
            onClosePoll = viewModel::onClosePoll,
            onOpenInfo = viewModel::openInfo,
        )
    }

    GroupChatContent(state = state, draft = viewModel.draft, actions = actions)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AvSnackbarHost(snackbar, Modifier.statusBarsPadding().padding(top = 72.dp))
    }

    state.info?.let { MessageInfoSheet(it, onDismiss = viewModel::closeInfo) }
    state.pollDraft?.let { draft ->
        PollComposerSheet(
            draft = draft,
            actions = PollSheetActions(
                onQuestion = viewModel::onPollQuestion,
                onOption = viewModel::onPollOption,
                onAddOption = viewModel::onAddPollOption,
                onRemoveOption = viewModel::onRemovePollOption,
                onMulti = viewModel::onPollMulti,
                onSubmit = viewModel::submitPoll,
                onDismiss = viewModel::closePollSheet,
            ),
        )
    }
}

@Composable
fun GroupChatContent(state: ChatUiState, draft: String, actions: ChatActions) {
    val listState = rememberLazyListState()
    AvailablissBackground(OrbLayout.Chat) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            ChatHeader(state, actions.onBack)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.items.isEmpty() -> LoadingIndicator(Modifier.align(Alignment.Center))
                    state.error != null && state.items.isEmpty() ->
                        ErrorBanner(state.error, Modifier.padding(16.dp).align(Alignment.Center), onRetry = actions.onRetry)
                    state.items.isEmpty() -> Text(
                        "No messages yet. Say hello, or start a poll.",
                        style = AvailablissTheme.type.labelRegular,
                        color = AvailablissTheme.colors.muted,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> MessageList(state, actions, listState)
                }
            }
            ChatComposer(
                text = draft,
                onTextChange = actions.onDraftChange,
                onSend = actions.onSend,
                menuOpen = state.menuOpen,
                onToggleMenu = actions.onToggleMenu,
                onPoll = actions.onPoll,
                onProposeTime = actions.onProposeTime,
                onAddEvent = actions.onAddEvent,
                sending = state.sending,
            )
        }
    }
}

@Composable
private fun ChatHeader(state: ChatUiState, onBack: () -> Unit) {
    val colors = AvailablissTheme.colors
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 14.dp)
            .fillMaxWidth()
            .glass(Shapes.card)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlainIconButton(AvIcons.Back, "Back to calendar", onBack)
        GroupTile(state.monogram, colors.tile(state.tileColor), size = 40.dp, radius = 14.dp)
        Column(Modifier.weight(1f)) {
            Text(state.groupName, style = AvailablissTheme.type.bodyStrong.copy(fontWeight = AvWeights.Display650), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.membersSummary.isNotEmpty()) {
                Text(state.membersSummary, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        PlainIconButton(AvIcons.Calendar, "Group calendar", onBack)
    }
}

@Composable
private fun MessageList(state: ChatUiState, actions: ChatActions, listState: LazyListState) {
    val loadOlder by rememberUpdatedState(actions.onLoadOlder)
    // Page back when the oldest rows (end of the reversed list) come into view.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - PAGE_PREFETCH
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadOlder() }
    }
    // Keep the newest message in view when one arrives while you're at the bottom.
    val newestKey = state.items.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(state.items, key = { it.key }, contentType = { it::class }) { item ->
            ChatRow(item, actions)
        }
        if (state.loadingOlder) item(key = "loading-older") { LoadingIndicator() }
    }
}

@Composable
private fun ChatRow(item: ChatItem, actions: ChatActions) {
    when (item) {
        is ChatItem.Day -> ChatMeta(item.label)
        is ChatItem.System -> ChatMeta(item.text)
        is ChatItem.Text -> MessageBubble(
            text = item.text,
            own = item.own,
            author = item.author,
            showAvatar = item.showAvatar,
            onClick = if (item.own) ({ actions.onOpenInfo(item.id) }) else null,
            accessibilityLabel = item.accessibilityLabel,
        )
        is ChatItem.ProposalCard -> ProposalCard(
            ui = item.card ?: ProposalCardUi("", "", "Proposed a time", "Loading…", ""),
            onClick = { actions.onOpenProposal(item.proposalId) },
        )
        is ChatItem.EventCard -> ProposalCard(
            ui = item.card,
            // Events from a confirmed proposal open the proposal; others open the event itself.
            onClick = { item.proposalId?.let(actions.onOpenProposal) ?: actions.onOpenEvent(item.eventId) },
            actionLabel = "View",
        )
        is ChatItem.PollItem -> Box(Modifier.fillMaxWidth(), contentAlignment = if (item.own) Alignment.CenterEnd else Alignment.CenterStart) {
            PollCard(
                poll = item.poll,
                onToggle = { optionId -> actions.onVote(item.id, optionId) },
                onClose = { actions.onClosePoll(item.id) },
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
            )
        }
        is ChatItem.Receipt -> ReadReceipt(
            label = item.label,
            seen = item.seen,
            seenBy = item.seenBy,
            onClick = { actions.onOpenInfo(item.messageId) },
            modifier = Modifier.padding(top = 0.dp),
        )
    }
}

private const val PAGE_PREFETCH = 5

@Preview
@Composable
private fun GroupChatPreview() {
    val priya = AvatarPerson("p", "Priya")
    AvailablissPreviewTheme {
        GroupChatContent(
            ChatUiState(
                groupName = "Lake District crew",
                monogram = "LD",
                tileColor = TileColor.T1,
                membersSummary = "Ana, Priya, Tom and 3 others",
                loading = false,
                items = listOf(
                    ChatItem.Receipt("m3", "Seen by 3 of 5", true, listOf(AvatarPerson("a", "Ana"), priya)),
                    ChatItem.Text("m3", "I'll book somewhere to stay. Vote below", own = true, author = null, showAvatar = false, accessibilityLabel = ""),
                    ChatItem.ProposalCard("m2", "p1", ProposalCardUi("NOV", "13", "Ana proposed a time", "Fri 13 – Sun 15 Nov", "4 of 6 accepted")),
                    ChatItem.Text("m1", "Has everyone put their calendar in yet? Leo, Jade…", own = false, author = priya, showAvatar = true, accessibilityLabel = ""),
                    ChatItem.Day("Today", "2026-11-01"),
                ),
            ),
            draft = "",
            actions = ChatActions(),
        )
    }
}
