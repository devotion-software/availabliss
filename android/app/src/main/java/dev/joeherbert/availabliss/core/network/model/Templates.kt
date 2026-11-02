package dev.joeherbert.availabliss.core.network.model

import kotlinx.serialization.Serializable

/** A saved timing to reuse when creating groups (`/me/templates`). */
@Serializable
data class RequirementTemplate(
    val id: String,
    val name: String,
    val requirement: Requirement,
    val createdAt: String,
)

@Serializable
data class TemplatesResponse(val templates: List<RequirementTemplate>)

@Serializable
data class CreateTemplateRequest(val name: String, val requirement: Requirement)

@Serializable
data class RenameTemplateRequest(val name: String)
