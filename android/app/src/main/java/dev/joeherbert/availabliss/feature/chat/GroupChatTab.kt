package dev.joeherbert.availabliss.feature.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.joeherbert.availabliss.core.network.model.MessageLocation
import dev.joeherbert.availabliss.core.network.model.TileColor
import dev.joeherbert.availabliss.designsystem.component.AvSnackbarHost
import dev.joeherbert.availabliss.designsystem.component.AvailablissBackground
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.ErrorBanner
import dev.joeherbert.availabliss.designsystem.component.GlassIconButton
import dev.joeherbert.availabliss.designsystem.component.GroupTile
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.OrbLayout
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.UserMessageEffect
import dev.joeherbert.availabliss.designsystem.component.chat.BubbleAction
import dev.joeherbert.availabliss.designsystem.component.chat.ChatComposer
import dev.joeherbert.availabliss.designsystem.component.chat.ChatMeta
import dev.joeherbert.availabliss.designsystem.component.chat.ConfirmedBanner
import dev.joeherbert.availabliss.designsystem.component.chat.MAX_ATTACHMENTS
import dev.joeherbert.availabliss.designsystem.component.chat.MessageBubble
import dev.joeherbert.availabliss.designsystem.component.chat.PollCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCard
import dev.joeherbert.availabliss.designsystem.component.chat.ProposalCardUi
import dev.joeherbert.availabliss.designsystem.component.chat.ReadReceipt
import dev.joeherbert.availabliss.designsystem.component.chat.SwipeToReply
import dev.joeherbert.availabliss.designsystem.component.glass
import dev.joeherbert.availabliss.designsystem.component.rememberSnackbarHostState
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvWeights
import dev.joeherbert.availabliss.designsystem.theme.AvailablissPreviewTheme
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import dev.joeherbert.availabliss.feature.common.AddToCalendarButton
import dev.joeherbert.availabliss.feature.common.AddToCalendarEffect
import dev.joeherbert.availabliss.feature.common.AddToCalendarState
import dev.joeherbert.availabliss.feature.proposal.ProposalPrefill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val onEditPoll: (messageId: String) -> Unit = {},
    /** "Propose this" on a date poll's option: the propose form, prefilled with its time. */
    val onProposeOption: ((messageId: String, optionId: String) -> Unit)? = null,
    val onOpenInfo: (messageId: String) -> Unit = {},
    val onEditMessage: (messageId: String) -> Unit = {},
    val onDeleteMessage: (messageId: String) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onReply: (messageId: String) -> Unit = {},
    val onCancelReply: () -> Unit = {},
    val onAddToCalendar: (eventId: String) -> Unit = {},
    /** Set when the chat wasn't opened over its group's calendar: the header's group name opens it. */
    val onOpenGroup: (() -> Unit)? = null,
    /** Opens the thread a message is part of (tapping a quote, or View thread). */
    val onOpenThread: (messageId: String) -> Unit = {},
    /** In a thread: Show in chat in a message's long-press menu. */
    val onShowInChat: ((messageId: String) -> Unit)? = null,
    val onShowInChatHandled: () -> Unit = {},
    /** Tapped a photo (opens the viewer) or a file (downloads and opens it) in a message. */
    val onOpenAttachment: (messageId: String, attachmentId: String) -> Unit = { _, _ -> },
    /** Photos / File in the + menu: open the system pickers. */
    val onPhotos: () -> Unit = {},
    val onFiles: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onRetryAttachment: (String) -> Unit = {},
    /** Location in the + menu: the sheet to share where you are or a place you find. */
    val onLocation: () -> Unit = {},
    /** Tapped a shared place: ask which maps app to open it in. */
    val onOpenLocation: (MessageLocation) -> Unit = {},
    /** Search, and Photos and files, in the header. */
    val onSearch: () -> Unit = {},
    val onMedia: () -> Unit = {},
)

/** The group's chat (full screen; back returns to the calendar tab). */
@Composable
fun GroupChatTab(
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
    onProposeTime: () -> Unit,
    onAddEvent: () -> Unit,
    /** Set when the chat wasn't opened over its group's calendar (e.g. from Activity): the group's name opens it. */
    onOpenGroup: (() -> Unit)? = null,
    /** "Propose this" on a date poll: the propose form with that time. */
    onProposeSlot: (ProposalPrefill) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberSnackbarHostState()
    val viewerSnackbar = rememberSnackbarHostState()
    // While the photo viewer is open, its messages ("Saved to Pictures") show over it.
    UserMessageEffect(state.message, if (state.viewer != null) viewerSnackbar else snackbar, viewModel::onMessageShown)
    // The system photo picker (photos and GIFs, no permission needed) and file picker; the app keeps at most 10 per message.
    val photoPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(MAX_ATTACHMENTS)) { uris -> viewModel.onPicked(uris) }
    val filePicker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris -> viewModel.onPicked(uris) }
    AddToCalendarEffect(state.calendar, viewModel::onCalendarIntentHandled, viewModel::chooseCalendar, viewModel::cancelCalendarChoice)
    LifecycleResumeEffect(Unit) {
        viewModel.onVisibilityChanged(true)
        onPauseOrDispose { viewModel.onVisibilityChanged(false) }
    }
    BackHandler(enabled = state.menuOpen) { viewModel.closeMenu() }
    BackHandler(enabled = state.editing && !state.menuOpen) { viewModel.cancelEdit() }
    BackHandler(enabled = state.replying != null && !state.editing && !state.menuOpen) { viewModel.cancelReply() }

    // A shared place that was tapped: which maps app to open it in.
    var openingLocation by remember { mutableStateOf<MessageLocation?>(null) }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenProposal by rememberUpdatedState(onOpenProposal)
    val currentOnOpenEvent by rememberUpdatedState(onOpenEvent)
    val currentOnProposeTime by rememberUpdatedState(onProposeTime)
    val currentOnAddEvent by rememberUpdatedState(onAddEvent)
    val currentOnProposeSlot by rememberUpdatedState(onProposeSlot)
    // One instance for the screen's lifetime, so rows skip recomposition while you type.
    val actions = remember(viewModel) {
        ChatActions(
            onBack = { currentOnBack() },
            onSearch = viewModel::openSearch,
            onMedia = viewModel::openMedia,
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
            onEditPoll = viewModel::onEditPoll,
            onProposeOption = { messageId, optionId -> viewModel.proposalFor(messageId, optionId)?.let { currentOnProposeSlot(it) } },
            onOpenInfo = viewModel::openInfo,
            onEditMessage = viewModel::startEdit,
            onDeleteMessage = viewModel::requestDelete,
            onCancelEdit = viewModel::cancelEdit,
            onReply = viewModel::startReply,
            onCancelReply = viewModel::cancelReply,
            onAddToCalendar = viewModel::onAddToCalendar,
            onOpenGroup = onOpenGroup,
            onOpenThread = viewModel::openThread,
            onShowInChatHandled = viewModel::onShowInChatHandled,
            onOpenAttachment = viewModel::openAttachment,
            onPhotos = {
                viewModel.closeMenu()
                photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            onFiles = {
                viewModel.closeMenu()
                filePicker.launch(arrayOf("*/*"))
            },
            onRemoveAttachment = viewModel::removeAttachment,
            onRetryAttachment = viewModel::retryAttachment,
            onLocation = viewModel::openLocationSheet,
            onOpenLocation = { openingLocation = it },
        )
    }
    // The same rows inside the thread sheet, where tapping one offers Reply / Show in chat.
    val threadActions = remember(viewModel) {
        actions.copy(
            onOpenInfo = viewModel::openThreadActions,
            onReply = viewModel::replyInThread,
            onEditMessage = {},
            onDeleteMessage = {},
            onOpenThread = {},
            onShowInChat = viewModel::showInChat,
        )
    }

    GroupChatContent(state = state, draft = viewModel.draft, actions = actions)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AvSnackbarHost(snackbar, Modifier.statusBarsPadding().padding(top = 72.dp))
    }

    state.thread?.let { thread ->
        ThreadSheet(
            thread = thread,
            draft = viewModel.threadDraft,
            rowActions = threadActions,
            calendar = state.calendar,
            downloads = state.downloads,
            sheetActions = ThreadSheetActions(
                onDraftChange = viewModel::onThreadDraftChange,
                onSend = viewModel::sendInThread,
                onReplyToLatest = { viewModel.replyInThread(null) },
                onReply = viewModel::replyInThread,
                onShowInChat = viewModel::showInChat,
                onCloseActions = viewModel::closeThreadActions,
                onDismiss = viewModel::closeThread,
            ),
        )
    }
    state.info?.let { MessageInfoSheet(it, onDismiss = viewModel::closeInfo) }
    state.locationSheet?.let { sheet ->
        LocationSheet(
            sheet = sheet,
            onQuery = viewModel::onPlaceQuery,
            onSearch = viewModel::searchPlaces,
            onClear = viewModel::clearPlaceSearch,
            onPick = viewModel::pickPlace,
            onSend = viewModel::sendLocation,
            onDismiss = viewModel::closeLocationSheet,
        )
    }
    state.media?.let { media ->
        ChatMediaSheet(
            media = media,
            onKind = viewModel::showMediaKind,
            onLoadMore = viewModel::loadMoreMedia,
            onRetry = { viewModel.showMediaKind(media.kind) },
            onViewPhoto = viewModel::viewMediaPhoto,
            onOpenFile = viewModel::openMediaFile,
            onShowInChat = viewModel::showMediaInChat,
            onDismiss = viewModel::closeMedia,
        )
    }
    state.search?.let { search ->
        ChatSearchSheet(search, onQuery = viewModel::onSearchQuery, onPick = viewModel::pickSearchResult, onDismiss = viewModel::closeSearch)
    }
    openingLocation?.let { location -> OpenLocationSheet(location, onDismiss = { openingLocation = null }) }
    state.viewer?.let { viewer ->
        AttachmentViewer(
            viewer = viewer,
            downloads = state.downloads,
            snackbar = viewerSnackbar,
            onShare = viewModel::shareAttachment,
            onSave = viewModel::saveAttachment,
            onDismiss = viewModel::closeViewer,
            onShowInChat = viewer.messageIds?.let { ids -> { index: Int -> ids.getOrNull(index)?.let(viewModel::showMediaInChat) } },
        )
    }
    if (state.confirmDeleteId != null) {
        DeleteMessageDialog(onConfirm = viewModel::confirmDelete, onDismiss = viewModel::dismissDelete)
    }
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
            ChatHeader(state, actions.onBack, actions.onSearch, actions.onMedia, actions.onOpenGroup)
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
                editing = state.editing,
                onCancelEdit = actions.onCancelEdit,
                replyingTo = state.replying?.name,
                replyingSnippet = state.replying?.snippet.orEmpty(),
                onCancelReply = actions.onCancelReply,
                attachments = state.attachments,
                onRemoveAttachment = actions.onRemoveAttachment,
                onRetryAttachment = actions.onRetryAttachment,
                onPhotos = actions.onPhotos,
                onFiles = actions.onFiles,
                onLocation = actions.onLocation,
                allowEmptyText = state.editingCaption,
            )
        }
    }
}

@Composable
private fun ChatHeader(state: ChatUiState, onBack: () -> Unit, onSearch: () -> Unit, onMedia: () -> Unit, onOpenGroup: (() -> Unit)? = null) {
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
        PlainIconButton(AvIcons.Back, if (onOpenGroup == null) "Back to calendar" else "Back", onBack)
        Row(
            Modifier
                .weight(1f)
                .clip(Shapes.inner)
                .then(if (onOpenGroup != null) Modifier.clickable(onClickLabel = "Open the group", onClick = onOpenGroup) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GroupTile(state.monogram, colors.tile(state.tileColor), image = state.groupImage, size = 40.dp, radius = 14.dp)
            Column(Modifier.weight(1f)) {
                Text(state.groupName, style = AvailablissTheme.type.bodyStrong.copy(fontWeight = AvWeights.Display650), color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.membersSummary.isNotEmpty()) {
                    Text(state.membersSummary, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onOpenGroup != null) {
                Icon(AvIcons.Forward, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
            }
        }
        PlainIconButton(AvIcons.Photo, "Photos and files", onMedia)
        PlainIconButton(AvIcons.Search, "Search chat", onSearch)
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
    val scope = rememberCoroutineScope()
    val items by rememberUpdatedState(state.items)
    // Scrolled back from the latest message (past its read receipt row): the jump button shows.
    val thresholdPx = with(LocalDensity.current) { JUMP_THRESHOLD.toPx() }
    val scrolledBack by remember(listState) {
        derivedStateOf {
            val newest = if (items.firstOrNull() is ChatItem.Receipt) 1 else 0
            val first = listState.firstVisibleItemIndex
            first > newest || (first == newest && listState.firstVisibleItemScrollOffset > thresholdPx)
        }
    }
    // Messages from others that arrived while you were scrolled back, counted on the jump button.
    var unseen by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrolledBack) { if (!scrolledBack) unseen = 0 }
    // A new newest message: at the bottom, keep it in view; scrolled back, count it and stay put
    // (unless you sent it, then go to it).
    val newestKey = state.items.firstOrNull { it !is ChatItem.Day && it !is ChatItem.Receipt }?.key
    var previousNewest by remember { mutableStateOf(newestKey) }
    LaunchedEffect(newestKey) {
        val before = previousNewest
        previousNewest = newestKey
        if (before == null || newestKey == null || before == newestKey) return@LaunchedEffect
        val oldIndex = items.indexOfFirst { it.key == before }
        val added = if (oldIndex < 0) emptyList() else items.subList(0, oldIndex).filter { it.isMessage }
        // Judged by the previous newest message (the list may already have laid out the new one):
        // still in view means you were at the bottom.
        val wasAtBottom = listState.layoutInfo.visibleItemsInfo.any { it.key == before } &&
            listState.firstVisibleItemScrollOffset <= thresholdPx
        when {
            added.any { it.isOwn } || wasAtBottom -> listState.animateScrollToItem(0)
            else -> unseen += added.size
        }
    }
    // "Show in chat" from a thread scrolls to the message (when it's loaded) and flashes it.
    var highlighted by remember { mutableStateOf<String?>(null) }
    val showOriginal: (String) -> Unit = remember(listState) {
        { id ->
            val index = items.indexOfFirst { it.key == id }
            if (index >= 0) scope.launch {
                listState.animateScrollToItem(index)
                // Lift it from the bottom edge to the middle.
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.let { info ->
                    val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    listState.animateScrollBy(-(viewport / 2f - info.size / 2f).coerceAtLeast(0f))
                }
                highlighted = id
                delay(1200)
                if (highlighted == id) highlighted = null
            }
        }
    }

    val showRequest = state.showInChat
    LaunchedEffect(showRequest) {
        val request = showRequest ?: return@LaunchedEffect
        // The rows are rebuilt off the main thread, so give them a moment to include it.
        withTimeoutOrNull(2_000) { snapshotFlow { items.any { it.key == request.messageId } }.first { it } }
        showOriginal(request.messageId)
        actions.onShowInChatHandled()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
        ) {
            items(state.items, key = { it.key }, contentType = { it::class }) { item ->
                val flash by animateColorAsState(
                    if (highlighted != null && highlighted == item.key) AvailablissTheme.colors.accentSoft else Color.Transparent,
                    animationSpec = tween(if (highlighted == item.key) 200 else 600),
                    label = "quote-highlight",
                )
                Box(Modifier.drawBehind { drawRoundRect(flash, topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()), size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()), cornerRadius = CornerRadius(18.dp.toPx())) }) {
                    ChatRow(item, actions, state.calendar, actions.onOpenThread, downloads = state.downloads)
                }
            }
            if (state.loadingOlder) item(key = "loading-older") { LoadingIndicator() }
        }
        AnimatedVisibility(
            visible = scrolledBack,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 8.dp),
        ) {
            JumpToLatestButton(unseen) {
                unseen = 0
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }
}

/** Floats over the bottom of the chat while you're scrolled back: a down arrow back to the latest message, with how many new ones arrived meanwhile. */
@Composable
private fun JumpToLatestButton(count: Int, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    Box {
        GlassIconButton(
            AvIcons.ArrowDown,
            if (count > 0) "Jump to latest message, $count new" else "Jump to latest message",
            onClick,
        )
        if (count > 0) {
            Text(
                if (count > 99) "99+" else "$count",
                style = AvailablissTheme.type.micro.copy(fontWeight = FontWeight.Bold),
                color = colors.onAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** A message from someone (not a day label, read receipt or system line). */
private val ChatItem.isMessage: Boolean
    get() = this is ChatItem.Text || this is ChatItem.PollItem || this is ChatItem.ProposalCard || this is ChatItem.EventCard || this is ChatItem.Confirmed

private val ChatItem.isOwn: Boolean
    get() = (this is ChatItem.Text && own) || (this is ChatItem.PollItem && own)

private val JUMP_THRESHOLD = 200.dp

/** One chat row. [onQuoteClick] gets the message's own id in the chat (to open its thread), or the quoted id in a thread. */
@Composable
internal fun ChatRow(
    item: ChatItem,
    actions: ChatActions,
    calendar: AddToCalendarState,
    onQuoteClick: (String) -> Unit,
    quoteOpensThread: Boolean = true,
    downloads: Map<String, Float> = emptyMap(),
) {
    when (item) {
        is ChatItem.Day -> ChatMeta(item.label)
        is ChatItem.System -> ChatMeta(item.text)
        is ChatItem.Text -> SwipeToReply(enabled = item.canReply, onReply = { actions.onReply(item.id) }) {
            MessageBubble(
                text = item.text,
                own = item.own,
                author = item.author,
                showAvatar = item.showAvatar,
                authorName = item.authorName,
                onClick = if (item.deleted) null else ({ actions.onOpenInfo(item.id) }),
                accessibilityLabel = item.accessibilityLabel,
                edited = item.edited,
                deleted = item.deleted,
                menuActions = remember(item.id, item.canEdit, item.canReply, item.inThread, actions) {
                    listOfNotNull(
                        BubbleAction("Reply") { actions.onReply(item.id) }.takeIf { item.canReply },
                        BubbleAction("View thread") { actions.onOpenThread(item.id) }.takeIf { item.inThread },
                        actions.onShowInChat?.let { show -> BubbleAction("Show in chat") { show(item.id) } },
                        BubbleAction("Edit") { actions.onEditMessage(item.id) }.takeIf { item.canEdit },
                        BubbleAction("Delete", destructive = true) { actions.onDeleteMessage(item.id) }.takeIf { item.canEdit },
                    )
                },
                quote = item.quote,
                onQuoteClick = { quotedId -> onQuoteClick(if (quoteOpensThread) item.id else quotedId) },
                attachments = item.attachments,
                onOpenAttachment = { attachmentId -> actions.onOpenAttachment(item.id, attachmentId) },
                downloads = if (item.attachments.isEmpty()) emptyMap() else downloads,
                location = item.location,
                onOpenLocation = { item.location?.let(actions.onOpenLocation) },
            )
        }
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
        is ChatItem.Confirmed -> ConfirmedBanner(
            title = item.title,
            detail = item.detail,
            onClick = item.proposalId?.let { id -> { actions.onOpenProposal(id) } },
            // Above its neighbours, so the confetti burst can spill over them.
            modifier = Modifier.zIndex(1f),
            burstKey = item.id.takeIf { item.recent },
            headline = item.headline,
            count = item.count,
        ) {
            AddToCalendarButton(
                calendar.status(item.eventId),
                onClick = { actions.onAddToCalendar(item.eventId) },
                height = 40.dp,
            )
        }
        is ChatItem.PollItem -> SwipeToReply(enabled = item.canReply, onReply = { actions.onReply(item.id) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = if (item.own) Alignment.CenterEnd else Alignment.CenterStart) {
                PollCard(
                    poll = item.poll,
                    onToggle = { optionId -> actions.onVote(item.id, optionId) },
                    onClose = { actions.onClosePoll(item.id) },
                    onEdit = { actions.onEditPoll(item.id) },
                    onPropose = actions.onProposeOption?.let { propose -> { optionId -> propose(item.id, optionId) } },
                    modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                )
            }
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
                    ChatItem.Confirmed("m5", "e1", "p1", "Lake District weekend", "Fri 13 – Sun 15 Nov · Ambleside", recent = false),
                    ChatItem.Receipt("m3", "Seen by 3 of 5", true, listOf(AvatarPerson("a", "Ana"), priya)),
                    ChatItem.Text("m3", "I'll book somewhere to stay. Vote below", own = true, author = null, showAvatar = false, accessibilityLabel = "", canEdit = true),
                    ChatItem.Text("m4", "You deleted this message", own = true, author = null, showAvatar = false, accessibilityLabel = "Message deleted", deleted = true),
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
