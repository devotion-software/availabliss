package dev.joeherbert.availabliss.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.common.InviteInput
import dev.joeherbert.availabliss.core.common.Usernames
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.UserSummary
import dev.joeherbert.availabliss.core.network.model.UsernameCheck
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.component.Avatar
import dev.joeherbert.availabliss.designsystem.component.AvatarPerson
import dev.joeherbert.availabliss.designsystem.component.PlainIconButton
import dev.joeherbert.availabliss.designsystem.component.TextAction
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import dev.joeherbert.availabliss.designsystem.theme.Shapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Someone added to an invite: an address, or a person found by their username. */
sealed interface InviteRecipient {
    val key: String

    data class Email(val address: String) : InviteRecipient {
        override val key: String get() = "email:$address"
    }

    data class Person(val user: UserSummary) : InviteRecipient {
        override val key: String get() = "user:${user.username}"
    }
}

data class InviteRecipientsState(
    /** What's typed but not added yet. */
    val draft: String = "",
    val recipients: List<InviteRecipient> = emptyList(),
    val lookingUp: Boolean = false,
    /** Why the draft couldn't be added ("No one has that username"). */
    val error: String? = null,
) {
    val emails: List<String> get() = recipients.filterIsInstance<InviteRecipient.Email>().map { it.address }
    val usernames: List<String> get() = recipients.filterIsInstance<InviteRecipient.Person>().map { it.user.username }

    /** "sam@example.com", "@priya" */
    val labels: List<String> get() = emails + usernames.map { "@$it" }

    val canSend: Boolean get() = (recipients.isNotEmpty() || draft.isNotBlank()) && !lookingUp
}

/**
 * The people an invite is for, typed as emails or usernames. Emails are added as typed;
 * usernames are looked up first so the person can be shown (or "No one has that username").
 */
class InviteRecipients(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> ApiResult<UsernameCheck>,
    /** The signed-in user's id, so inviting yourself is caught. */
    private val selfId: () -> String?,
) {
    private val _state = MutableStateFlow(InviteRecipientsState())
    val state: StateFlow<InviteRecipientsState> = _state.asStateFlow()

    private val adding = Mutex()

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text, error = null) }
        // A comma, space or new line finishes an entry.
        if (InviteInput.endsEntry(text)) add()
    }

    fun add() {
        scope.launch { addDraft() }
    }

    fun remove(recipient: InviteRecipient) = _state.update { s -> s.copy(recipients = s.recipients.filterNot { it.key == recipient.key }) }

    fun clear() {
        _state.value = InviteRecipientsState()
    }

    /**
     * Adds everything in the draft. Entries that can't be added stay in the draft with
     * [InviteRecipientsState.error] set. Returns true when the draft was used up.
     */
    suspend fun addDraft(): Boolean = adding.withLock {
        val typed = _state.value.draft
        val tokens = InviteInput.tokens(typed)
        if (tokens.isEmpty()) {
            _state.update { it.copy(draft = "") }
            return@withLock true
        }
        _state.update { it.copy(lookingUp = true, error = null) }
        val left = mutableListOf<String>()
        var error: String? = null
        for (token in tokens) {
            val problem = when (val input = InviteInput.parse(token)) {
                is InviteInput.Email ->
                    if (InviteInput.isValidEmail(input.address)) addRecipient(InviteRecipient.Email(input.address))
                    else "“$token” isn’t a valid email address"
                is InviteInput.Username -> addUsername(input.username)
            }
            if (problem != null) {
                left += token
                if (error == null) error = problem
            }
        }
        _state.update { s ->
            // Keep anything typed while looking up.
            val more = if (s.draft.startsWith(typed)) s.draft.removePrefix(typed).trim() else ""
            s.copy(draft = (left + listOf(more).filter { it.isNotEmpty() }).joinToString(", "), lookingUp = false, error = error)
        }
        left.isEmpty()
    }

    private suspend fun addUsername(username: String): String? {
        if (Usernames.problem(username) != null) return NO_ONE
        _state.value.recipients.firstOrNull { it is InviteRecipient.Person && it.user.username == username }?.let { return null }
        return when (val result = lookup(username)) {
            is ApiResult.Success -> {
                val user = result.value.user ?: return NO_ONE
                if (user.id == selfId()) "That's you" else addRecipient(InviteRecipient.Person(user))
            }
            is ApiResult.Failure -> result.error.message
        }
    }

    /** Adds [recipient] (once); returns a problem when the invite would be too big. */
    private fun addRecipient(recipient: InviteRecipient): String? {
        val s = _state.value
        if (s.recipients.any { it.key == recipient.key }) return null
        if (s.recipients.size >= MAX) return "Invite up to $MAX people at a time"
        _state.update { it.copy(recipients = it.recipients + recipient) }
        return null
    }

    companion object {
        const val MAX = 20
        const val NO_ONE = "No one has that username"
    }
}

/**
 * "Email or username" entry: the people added so far (avatar, name and @username for
 * people found by username), then the field. Commas, spaces, Return or Add add an entry.
 */
@Composable
fun InviteRecipientsField(
    state: InviteRecipientsState,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (InviteRecipient) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    height: Dp = 50.dp,
    shape: androidx.compose.ui.graphics.Shape = Shapes.control,
) {
    val colors = AvailablissTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AvTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            label = label,
            placeholder = "Email or username",
            error = state.error,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = onAdd,
            height = height,
            shape = shape,
            trailing = when {
                state.lookingUp -> {
                    { CircularProgressIndicator(Modifier.size(16.dp), color = colors.muted, strokeWidth = 2.dp) }
                }
                state.draft.isNotBlank() -> {
                    { TextAction("Add", onAdd, style = AvailablissTheme.type.label) }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        state.recipients.forEach { recipient -> RecipientRow(recipient, onRemove = { onRemove(recipient) }) }
    }
}

@Composable
private fun RecipientRow(recipient: InviteRecipient, onRemove: () -> Unit) {
    val colors = AvailablissTheme.colors
    val type = AvailablissTheme.type
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (recipient) {
            is InviteRecipient.Person -> {
                Avatar(AvatarPerson(recipient.user.id, recipient.user.name, recipient.user.image), size = 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(recipient.user.name.ifBlank { "@${recipient.user.username}" }, style = type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (recipient.user.name.isNotBlank()) {
                        Text("@${recipient.user.username}", style = type.caption.copy(fontWeight = FontWeight.Normal), color = colors.muted, maxLines = 1)
                    }
                }
            }
            is InviteRecipient.Email -> {
                Box(Modifier.size(32.dp).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(AvIcons.Mail, null, tint = colors.accentInk, modifier = Modifier.size(16.dp))
                }
                Text(recipient.address, style = type.bodySmallStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            }
        }
        val who = when (recipient) {
            is InviteRecipient.Person -> "@${recipient.user.username}"
            is InviteRecipient.Email -> recipient.address
        }
        PlainIconButton(AvIcons.Close, "Remove $who", onRemove, size = 36.dp, iconSize = 16.dp, tint = colors.muted)
    }
}
