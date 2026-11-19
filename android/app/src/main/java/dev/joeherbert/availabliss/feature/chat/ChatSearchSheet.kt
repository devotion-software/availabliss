package dev.joeherbert.availabliss.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.designsystem.component.AvBottomSheet
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.RowDivider
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes

/**
 * Search in the chat: matches as you type (from 2 characters), newest first, each with who sent it,
 * when, and the matching words picked out. Tapping one goes to it in the chat. Like web and iOS.
 */
@Composable
fun ChatSearchSheet(search: ChatSearchUi, onQuery: (String) -> Unit, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AvailablissTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AvBottomSheet(title = "Search chat", onDismissRequest = onDismiss) {
        AvTextField(
            value = search.query,
            onValueChange = onQuery,
            placeholder = "Messages, polls, places, files",
            imeAction = ImeAction.Search,
            focusRequester = focus,
            trailing = {
                when {
                    search.searching -> CircularProgressIndicator(color = colors.accentInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    search.query.isNotEmpty() -> PlainIconButton(AvIcons.Close, "Clear search", { onQuery("") }, size = 36.dp, iconSize = 14.dp, tint = colors.muted)
                    else -> PlainIconButton(AvIcons.Search, "Search", {}, size = 36.dp, iconSize = 18.dp, tint = colors.muted)
                }
            },
        )
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val results = search.results
            when {
                search.failed -> Note("Couldn't search. Check your connection and try again.")
                results == null -> Note("Search for at least 2 characters.")
                results.isEmpty() -> Note("No messages with “${search.query.trim()}”.")
                else -> {
                    Column(Modifier.fillMaxWidth().clip(Shapes.panel).background(colors.surface).padding(horizontal = 14.dp)) {
                        results.forEachIndexed { index, result ->
                            if (index > 0) RowDivider()
                            ResultRow(result) { onPick(result.messageId) }
                        }
                    }
                    if (search.hasMore) Note("Showing the newest ${results.size}. Search for something more specific to narrow it down.")
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResultUi, onClick: () -> Unit) {
    val colors = AvailablissTheme.colors
    val line = remember(result.snippet, colors) {
        buildAnnotatedString {
            if (result.isPoll) append("Poll: ")
            append(result.snippet.before)
            withStyle(SpanStyle(background = colors.accentSoft, color = colors.accentInk)) { append(result.snippet.match) }
            append(result.snippet.after)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = "Show in chat", onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(result.author, style = AvailablissTheme.type.caption, color = colors.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(result.day, style = AvailablissTheme.type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted)
        }
        Text(line, style = AvailablissTheme.type.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = AvailablissTheme.type.labelRegular, color = AvailablissTheme.colors.muted)
}
