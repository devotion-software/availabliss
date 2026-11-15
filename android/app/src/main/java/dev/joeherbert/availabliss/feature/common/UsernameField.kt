package dev.joeherbert.availabliss.feature.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.joeherbert.availabliss.core.common.Usernames
import dev.joeherbert.availabliss.core.network.ApiError
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.UsernameCheck
import dev.joeherbert.availabliss.designsystem.component.AvTextField
import dev.joeherbert.availabliss.designsystem.icon.AvIcons
import dev.joeherbert.availabliss.designsystem.theme.AvailablissTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UsernameStatus {
    /** Already yours (or nothing to save). */
    data object Unchanged : UsernameStatus
    data object Checking : UsernameStatus
    data object Available : UsernameStatus
    /** Someone else has it. */
    data object Taken : UsernameStatus
    data class Invalid(val problem: String) : UsernameStatus
    /** The lookup failed (offline, rate limited): the server decides on save. */
    data object Unknown : UsernameStatus
}

data class UsernameFieldState(
    /** As shown after the "@": lowercase, no spaces. */
    val text: String = "",
    /** The account's username now. */
    val current: String? = null,
    val status: UsernameStatus = UsernameStatus.Unchanged,
) {
    val normalized: String get() = Usernames.normalize(text)

    /** True when there's a new username to send with `PATCH /me`. */
    val changed: Boolean get() = normalized != current.orEmpty()

    /** Fine to save: not invalid, taken or still being checked. */
    val canSave: Boolean
        get() = status == UsernameStatus.Unchanged || status == UsernameStatus.Available || status == UsernameStatus.Unknown
}

/**
 * Live validation for a username being edited: the local rules straight away, then a
 * debounced `GET /usernames/:username` (rate limited, so not on every keystroke).
 */
class UsernameEditor(
    private val scope: CoroutineScope,
    private val lookup: suspend (String) -> ApiResult<UsernameCheck>,
    private val debounceMillis: Long = 400,
) {
    private val _state = MutableStateFlow(UsernameFieldState())
    val state: StateFlow<UsernameFieldState> = _state.asStateFlow()

    private var touched = false
    private var job: Job? = null

    /** Starts from the account's [current] username (no-op once they've typed). */
    fun prefill(current: String?) {
        if (touched) return
        job?.cancel()
        _state.value = UsernameFieldState(text = current.orEmpty(), current = current)
    }

    /** Starts over from [current], e.g. each time an edit sheet opens. */
    fun reset(current: String?) {
        touched = false
        prefill(current)
    }

    fun onChange(raw: String) {
        touched = true
        val text = raw.filterNot(Char::isWhitespace).trimStart('@').lowercase().take(Usernames.MAX + 10)
        job?.cancel()
        val s = _state.value
        val status = when {
            text == s.current.orEmpty() -> UsernameStatus.Unchanged
            else -> Usernames.problem(text)?.let(UsernameStatus::Invalid) ?: UsernameStatus.Checking
        }
        _state.value = s.copy(text = text, status = status)
        if (status != UsernameStatus.Checking) return
        job = scope.launch {
            delay(debounceMillis)
            val result = lookup(text)
            _state.update { now ->
                if (now.text != text) return@update now
                now.copy(
                    status = when (result) {
                        is ApiResult.Success -> result.value.let { check ->
                            when {
                                check.problem != null -> UsernameStatus.Invalid(check.problem)
                                check.available -> UsernameStatus.Available
                                else -> UsernameStatus.Taken
                            }
                        }
                        is ApiResult.Failure -> UsernameStatus.Unknown
                    },
                )
            }
        }
    }

    /**
     * Shows a failed save on the field: 409 is "Taken", a validation error about the username its message.
     * Returns false for any other error (the caller shows it).
     */
    fun onSaveError(error: ApiError): Boolean {
        val status = when (error) {
            is ApiError.Conflict -> UsernameStatus.Taken
            is ApiError.Validation -> UsernameStatus.Invalid(error.issueFor("username") ?: return false)
            else -> return false
        }
        _state.update { it.copy(status = status) }
        return true
    }

    /** After a successful save: what was saved is now the account's. */
    fun saved(username: String?) {
        touched = false
        prefill(username)
    }
}

/** "@ samtaylor" with live feedback: the problem, "Taken", or a subtle check when it's free. */
@Composable
fun UsernameField(
    state: UsernameFieldState,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = "Username",
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val colors = AvailablissTheme.colors
    AvTextField(
        value = state.text,
        onValueChange = onValueChange,
        label = label,
        placeholder = "username",
        prefix = "@",
        keyboardType = KeyboardType.Ascii,
        imeAction = imeAction,
        onImeAction = onImeAction,
        error = when (val status = state.status) {
            is UsernameStatus.Invalid -> status.problem
            UsernameStatus.Taken -> "Taken"
            else -> null
        },
        trailing = when (state.status) {
            UsernameStatus.Checking -> {
                { CircularProgressIndicator(Modifier.size(16.dp), color = colors.muted, strokeWidth = 2.dp) }
            }
            UsernameStatus.Available -> {
                { Icon(AvIcons.Check, "Available", tint = colors.okInk, modifier = Modifier.size(18.dp)) }
            }
            else -> null
        },
        modifier = modifier,
    )
}
