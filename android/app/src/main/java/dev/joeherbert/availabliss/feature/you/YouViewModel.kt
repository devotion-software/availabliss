package dev.joeherbert.availabliss.feature.you

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.joeherbert.availabliss.core.auth.AuthRepository
import dev.joeherbert.availabliss.core.common.UserMessage
import dev.joeherbert.availabliss.core.billing.PlanRepository
import dev.joeherbert.availabliss.core.data.AccountsRepository
import dev.joeherbert.availabliss.core.data.AppPreferences
import dev.joeherbert.availabliss.core.data.GroupsRepository
import dev.joeherbert.availabliss.core.data.LinkResult
import dev.joeherbert.availabliss.core.data.TemplatesRepository
import dev.joeherbert.availabliss.core.data.Appearance
import dev.joeherbert.availabliss.core.data.UserRepository
import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.model.LinkProviders
import dev.joeherbert.availabliss.core.network.model.LinkedAccounts
import dev.joeherbert.availabliss.core.network.model.MergeCandidate
import dev.joeherbert.availabliss.core.network.model.Plan
import dev.joeherbert.availabliss.core.network.model.User
import dev.joeherbert.availabliss.feature.timing.Templates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class YouUiState(
    val user: User? = null,
    val appearance: Appearance = Appearance(),
    /** Non-null while the edit-name sheet is open. */
    val nameDraft: String? = null,
    val savingName: Boolean = false,
    val addingPasskey: Boolean = false,
    /** Linked Apple / Google sign-ins; null until loaded. */
    val accounts: LinkedAccounts? = null,
    /** Providers this app can link itself (Google via Credential Manager; not Apple on Android). */
    val linkableProviders: Set<String> = emptySet(),
    /** The provider being linked (or merged) right now. */
    val linkingProvider: String? = null,
    /** Non-null while asking whether to merge the account that sign-in already belongs to. */
    val pendingMerge: LinkResult.NeedsMerge? = null,
    val confirmingDelete: Boolean = false,
    val deleting: Boolean = false,
    /** Set after the account is deleted; the UI then runs sign-out cleanup. */
    val deleted: Boolean = false,
    /** Your plan (Free / Pro); null until loaded. */
    val plan: Plan? = null,
    /** Your timing templates; null until loaded. */
    val templates: List<TemplateRowUi>? = null,
    /** The template being renamed, and its name so far. */
    val renamingTemplateId: String? = null,
    val templateNameDraft: String = "",
    val message: UserMessage? = null,
) {
    /** "United Kingdom · GB-ENG" */
    val regionSummary: String
        get() {
            val country = user?.country ?: return "Not set"
            val name = runCatching { Locale.Builder().setRegion(country).build().displayCountry }.getOrNull()?.ifBlank { null } ?: country
            return listOfNotNull(name, user.subdivision?.substringAfter('-')).joinToString(" · ")
        }

    /** "System · Dawn" */
    val appearanceSummary: String get() = "${appearance.mode.name} · ${appearance.theme.name}"
}

/** Body of the "merge accounts?" dialog. */
internal fun mergePrompt(account: MergeCandidate): String {
    val groups = when (account.groups) {
        0 -> "in no groups"
        1 -> "in 1 group"
        else -> "in ${account.groups} groups"
    }
    return "Merge ${account.name} (${account.email}, $groups) into this account? Its groups, busy times, calendars, " +
        "chats and Pro move here, then the other account is deleted. This can't be undone."
}

@HiltViewModel
class YouViewModel @Inject constructor(
    private val users: UserRepository,
    private val auth: AuthRepository,
    private val templatesRepo: TemplatesRepository,
    private val plans: PlanRepository,
    private val accounts: AccountsRepository,
    private val groups: GroupsRepository,
    prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        YouUiState(linkableProviders = setOf(LinkProviders.APPLE, LinkProviders.GOOGLE).filter(accounts::canLink).toSet()),
    )
    val state: StateFlow<YouUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { plans.plan.collect { plan -> _state.update { it.copy(plan = plan) } } }
        viewModelScope.launch {
            combine(users.user, prefs.appearance) { user, appearance -> user to appearance }
                .collect { (user, appearance) -> _state.update { it.copy(user = user, appearance = appearance) } }
        }
    }

    fun refreshPlan() {
        viewModelScope.launch { plans.refresh() }
    }

    /** After the upgrade sheet switched Pro on. */
    fun upgraded(message: String) = _state.update { it.copy(message = UserMessage(message)) }

    // region Timing templates
    /** Reloads the list (templates are saved from New group and the Timing tab). */
    fun refreshTemplates() {
        viewModelScope.launch {
            when (val result = templatesRepo.list()) {
                is ApiResult.Success -> _state.update { it.copy(templates = result.value.map(TemplateRowUi::from)) }
                is ApiResult.Failure -> _state.update { it.copy(templates = it.templates ?: emptyList()) }
            }
        }
    }

    fun startRenamingTemplate(id: String) = _state.update { s ->
        s.copy(renamingTemplateId = id, templateNameDraft = s.templates?.firstOrNull { it.id == id }?.name.orEmpty())
    }

    fun onTemplateNameChange(value: String) = _state.update { it.copy(templateNameDraft = value.take(Templates.MAX_NAME)) }

    fun cancelRenamingTemplate() = _state.update { it.copy(renamingTemplateId = null) }

    fun saveTemplateName() {
        val id = _state.value.renamingTemplateId ?: return
        val name = _state.value.templateNameDraft.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            when (val result = templatesRepo.rename(id, name)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        renamingTemplateId = null,
                        templates = s.templates?.map { if (it.id == id) TemplateRowUi.from(result.value) else it },
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }

    fun deleteTemplate(id: String) {
        val template = _state.value.templates?.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            when (val result = templatesRepo.delete(id)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(templates = s.templates?.filterNot { it.id == id }, message = UserMessage("Deleted “${template.name}”"))
                }
                is ApiResult.Failure -> _state.update { it.copy(message = UserMessage(result.error.message)) }
            }
        }
    }
    // endregion

    fun startEditingName() = _state.update { it.copy(nameDraft = it.user?.name.orEmpty()) }

    fun onNameChange(value: String) = _state.update { it.copy(nameDraft = value.take(80)) }

    fun cancelEditingName() = _state.update { it.copy(nameDraft = null) }

    fun saveName() {
        val draft = _state.value.nameDraft?.trim().orEmpty()
        if (draft.isEmpty() || _state.value.savingName) return
        viewModelScope.launch {
            _state.update { it.copy(savingName = true) }
            val result = users.update { set("name", draft) }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(savingName = false, nameDraft = null)
                    is ApiResult.Failure -> it.copy(savingName = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    /**
     * Call from a UI-scoped coroutine (rememberCoroutineScope): the Credential Manager sheet
     * runs there, anchored to [activity], so viewModelScope never holds the Activity.
     */
    suspend fun addPasskey(activity: Context) {
        if (_state.value.addingPasskey) return
        _state.update { it.copy(addingPasskey = true) }
        val outcome = try {
            auth.passkeyRegistration(activity)
        } catch (e: CancellationException) {
            _state.update { it.copy(addingPasskey = false) }
            throw e
        }
        viewModelScope.launch {
            val error = auth.completePasskeyRegistration(outcome, name = android.os.Build.MODEL)
            _state.update {
                it.copy(
                    addingPasskey = false,
                    message = when {
                        error == null -> UserMessage("Passkey added. You can use it to sign in next time.")
                        error.isEmpty() -> it.message // cancelled
                        else -> UserMessage(error)
                    },
                )
            }
        }
    }

    // region Sign-in methods (Apple / Google)
    fun refreshAccounts() {
        viewModelScope.launch {
            when (val result = accounts.list()) {
                is ApiResult.Success -> _state.update { it.copy(accounts = result.value) }
                // Keep whatever we had; the panel still shows the email row.
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** As [addPasskey]: call from a UI-scoped coroutine. */
    suspend fun link(provider: String, activity: Context) {
        val s = _state.value
        if (s.linkingProvider != null || provider !in s.linkableProviders) return
        _state.update { it.copy(linkingProvider = provider) }
        val outcome = try {
            accounts.googleCredential(activity)
        } catch (e: CancellationException) {
            _state.update { it.copy(linkingProvider = null) }
            throw e
        }
        runLink(provider) { accounts.linkGoogle(outcome) }
    }

    fun confirmMerge() {
        val pending = _state.value.pendingMerge ?: return
        if (_state.value.linkingProvider != null) return
        _state.update { it.copy(pendingMerge = null) }
        runLink(pending.request.provider) { accounts.link(pending.request.copy(merge = true)) }
    }

    fun cancelMerge() = _state.update { it.copy(pendingMerge = null) }

    private fun runLink(provider: String, block: suspend () -> LinkResult) {
        viewModelScope.launch {
            _state.update { it.copy(linkingProvider = provider) }
            val result = block()
            _state.update {
                when (result) {
                    is LinkResult.Linked -> it.copy(
                        linkingProvider = null,
                        message = UserMessage(if (result.merged) "Accounts merged." else "${LinkProviders.label(provider)} linked."),
                    )
                    is LinkResult.NeedsMerge -> it.copy(linkingProvider = null, pendingMerge = result)
                    LinkResult.Cancelled -> it.copy(linkingProvider = null)
                    is LinkResult.Failed -> it.copy(linkingProvider = null, message = UserMessage(result.message))
                }
            }
            if (result is LinkResult.Linked) {
                refreshAccounts()
                if (result.merged) {
                    // The other account's groups, Pro, templates and profile now live here.
                    launch { groups.refreshGroups() }
                    launch { plans.refresh() }
                    launch { users.refresh() }
                    refreshTemplates()
                }
            }
        }
    }
    // endregion

    fun requestDelete() = _state.update { it.copy(confirmingDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            when (val result = users.deleteAccount()) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, confirmingDelete = false, deleted = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(deleting = false, confirmingDelete = false, message = UserMessage(result.error.message))
                }
            }
        }
    }

    fun onMessageShown(message: UserMessage) =
        _state.update { if (it.message?.id == message.id) it.copy(message = null) else it }
}
