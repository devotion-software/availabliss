package dev.joeherbert.availabliss.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.ConfirmOption
import dev.joeherbert.availabliss.designsystem.component.ConfirmSheet
import dev.joeherbert.availabliss.designsystem.component.LoadingIndicator
import dev.joeherbert.availabliss.designsystem.component.chat.ChatComposer
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the thread sheet's own controls do (its rows use a [ChatActions]). */
data class ThreadSheetActions(
    val onDraftChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onReplyToLatest: () -> Unit,
    val onReply: (messageId: String) -> Unit,
    val onShowInChat: (messageId: String) -> Unit,
    val onCloseActions: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * A thread: the first message of a chain of replies and every reply under it, oldest first,
 * updating live, with a composer that replies within it (to the latest message unless you pick
 * another). Tapping a message offers Reply and Show in chat; tapping a quote goes to the original.
 */
@Composable
fun ThreadSheet(
    thread: ThreadUi,
    draft: String,
    rowActions: ChatActions,
    addedToCalendar: Set<String>,
    sheetActions: ThreadSheetActions,
) {
    val colors = AvailablissTheme.colors
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val items by rememberUpdatedState(thread.items)
    var highlighted by remember { mutableStateOf<String?>(null) }
    val highlight: (String) -> Unit = remember(listState) {
        { id ->
            val index = items.indexOfFirst { it.key == id }
            if (index >= 0) scope.launch {
                listState.animateScrollToItem(index)
                highlighted = id
                delay(1200)
                if (highlighted == id) highlighted = null
            }
        }
    }
    // Once loaded, show where you came from; then follow new replies down.
    LaunchedEffect(thread.loading) {
        if (!thread.loading && thread.count > 1) highlight(thread.focusId)
    }
    val lastKey = thread.items.lastOrNull()?.key
    var previousLast by remember { mutableStateOf(lastKey) }
    LaunchedEffect(lastKey) {
        val before = previousLast
        previousLast = lastKey
        if (before != null && lastKey != null && before != lastKey) listState.animateScrollToItem(items.lastIndex)
    }

    AvBottomSheet(title = "Thread", onDismissRequest = sheetActions.onDismiss, modifier = Modifier.fillMaxHeight(), scrollable = false) {
        if (thread.count > 0) {
            Text(
                if (thread.count == 1) "1 message" else "${thread.count} messages",
                style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.muted,
                modifier = Modifier.offset(y = (-10).dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (thread.loading && thread.items.isEmpty()) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(thread.items, key = { it.key }, contentType = { it::class }) { item ->
                        val flash by animateColorAsState(
                            if (highlighted != null && highlighted == item.key) colors.accentSoft else Color.Transparent,
                            animationSpec = tween(if (highlighted == item.key) 200 else 600),
                            label = "thread-highlight",
                        )
                        Box(Modifier.drawBehind { drawRoundRect(flash, topLeft = Offset(-6.dp.toPx(), -6.dp.toPx()), size = Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()), cornerRadius = CornerRadius(18.dp.toPx())) }) {
                            ChatRow(item, rowActions, addedToCalendar, onQuoteClick = highlight, quoteOpensThread = false)
                        }
                    }
                    if (thread.truncated) item(key = "truncated") {
                        Text(
                            "Later replies aren’t shown here. Find them in the chat.",
                            style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        thread.replying?.let { replying ->
            ChatComposer(
                text = draft,
                onTextChange = sheetActions.onDraftChange,
                onSend = sheetActions.onSend,
                menuOpen = false,
                onToggleMenu = {},
                onPoll = {},
                onProposeTime = {},
                onAddEvent = {},
                sending = thread.sending,
                replyingTo = replying.name,
                replyingSnippet = replying.snippet,
                onCancelReply = sheetActions.onReplyToLatest,
                showMenu = false,
                placeholder = "Reply in thread",
                canCancelReply = thread.picked,
                cancelReplyLabel = "Reply to the latest message instead",
                focusOnReply = thread.picked,
            )
        }
    }

    thread.actions?.let { a ->
        ConfirmSheet(
            title = a.title,
            message = a.snippet,
            options = listOfNotNull(
                ConfirmOption("Reply", { sheetActions.onReply(a.messageId) }, AvIcons.Reply).takeIf { a.canReply },
                ConfirmOption("Show in chat", { sheetActions.onShowInChat(a.messageId) }, AvIcons.Chat, ConfirmOption.Kind.Secondary),
            ),
            onDismiss = sheetActions.onCloseActions,
        )
    }
}
