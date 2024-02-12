package net.clynamic.projects

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// These values are not actually unused, as they will be passed from and to the client
@Suppress("unused")
enum class SelectionMode {
    One,
    Many,
}

data class Project(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val version: Int,
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val meta: String,
    @field:Schema(required = true)
    val description: String,
    @field:Schema(required = true)
    val guidelines: String,
    @field:Schema(required = true)
    val tags: List<String>,
    @field:Schema(required = true)
    val mode: SelectionMode,
    @field:Schema(required = true)
    val options: List<ProjectOption>,
    @field:Schema(required = true)
    val conditionals: List<String>,
    @field:Schema(required = true)
    val isPrivate: Boolean,
    @field:Schema(required = true)
    val isDeleted: Boolean,
    @field:Schema(required = true)
    val createdAt: Instant,
    @field:Schema(nullable = true)
    val updatedAt: Instant?,
)

data class ProjectRequest(
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val meta: String,
    @field:Schema(required = true)
    val description: String,
    @field:Schema(required = true)
    val guidelines: String,
    @field:Schema(required = true)
    val tags: List<String>,
    @field:Schema(required = true)
    val mode: SelectionMode,
    @field:Schema(required = true)
    val options: List<ProjectOption>,
    @field:Schema(nullable = true)
    val conditionals: List<String>?,
    @field:Schema(nullable = true, defaultValue = "false")
    val isPrivate: Boolean?,
)

data class ProjectUpdate(
    @field:Schema(nullable = true)
    val name: String? = null,
    @field:Schema(nullable = true)
    val description: String? = null,
    @field:Schema(nullable = true)
    val guidelines: String? = null,
    @field:Schema(nullable = true)
    val tags: List<String>? = null,
    @field:Schema(nullable = true)
    val mode: SelectionMode? = null,
    @field:Schema(nullable = true)
    val options: List<ProjectOption>? = null,
    @field:Schema(nullable = true)
    val conditionals: List<String>? = null,
    @field:Schema(nullable = true)
    val isPrivate: Boolean? = null,
    @field:Schema(nullable = true)
    val isDeleted: Boolean? = null,
)

data class ProjectEdit(
    @field:Schema(nullable = true)
    val name: String? = null,
    @field:Schema(nullable = true)
    val description: String? = null,
    @field:Schema(nullable = true)
    val guidelines: String? = null,
    @field:Schema(nullable = true)
    val tags: List<String>? = null,
    // This breaks a bunch of things, so it's commented out for now
    // @field:Schema(nullable = true)
    val mode: SelectionMode? = null,
    @field:Schema(nullable = true)
    val options: List<ProjectOption>? = null,
    @field:Schema(nullable = true)
    val conditionals: List<String>? = null,
    @field:Schema(nullable = true)
    val isPrivate: Boolean? = null,
)

data class ProjectOption(
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val add: List<String>,
    @field:Schema(required = true)
    val remove: List<String>,
)

data class ProjectVersion(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val projectId: Int,
    @field:Schema(required = true)
    val version: Int,
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val meta: String,
    @field:Schema(required = true)
    val description: String,
    @field:Schema(required = true)
    val guidelines: String,
    @field:Schema(required = true)
    val tags: List<String>,
    @field:Schema(required = true)
    val mode: SelectionMode,
    @field:Schema(required = true)
    val options: List<ProjectOption>,
    @field:Schema(required = true)
    val conditionals: List<String>,
    @field:Schema(required = true)
    val createdAt: Instant,
)

data class ProjectVersionRequest(
    val projectId: Int,
    val version: Int,
    val name: String,
    val meta: String,
    val description: String,
    val guidelines: String,
    val tags: List<String>,
    val mode: SelectionMode,
    val options: List<ProjectOption>,
    val conditionals: List<String>,
) {
    constructor(project: Project) : this(
        projectId = project.id,
        version = project.version,
        name = project.name,
        meta = project.meta,
        description = project.description,
        guidelines = project.guidelines,
        tags = project.tags,
        mode = project.mode,
        options = project.options,
        conditionals = project.conditionals,
    )
}