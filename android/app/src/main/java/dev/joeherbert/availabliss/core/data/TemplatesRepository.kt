package dev.joeherbert.availabliss.core.data

import dev.joeherbert.availabliss.core.network.ApiResult
import dev.joeherbert.availabliss.core.network.AvailablissApi
import dev.joeherbert.availabliss.core.network.map
import dev.joeherbert.availabliss.core.network.model.CreateTemplateRequest
import dev.joeherbert.availabliss.core.network.model.RenameTemplateRequest
import dev.joeherbert.availabliss.core.network.model.Requirement
import dev.joeherbert.availabliss.core.network.model.RequirementTemplate
import javax.inject.Inject
import javax.inject.Singleton

/** Your saved timing templates (`/me/templates`), reused when creating groups. */
@Singleton
class TemplatesRepository @Inject constructor(
    private val api: AvailablissApi,
    private val call: ApiCaller,
) {
    suspend fun list(): ApiResult<List<RequirementTemplate>> = call { api.getTemplates() }.map { it.templates }

    /** Fails with a validation error at the limit (50). */
    suspend fun create(name: String, requirement: Requirement): ApiResult<RequirementTemplate> =
        call { api.createTemplate(CreateTemplateRequest(name.trim(), requirement)) }

    suspend fun rename(id: String, name: String): ApiResult<RequirementTemplate> =
        call { api.renameTemplate(id, RenameTemplateRequest(name.trim())) }

    suspend fun delete(id: String): ApiResult<Unit> = call { api.deleteTemplate(id) }
}
