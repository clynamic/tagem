package net.clynamic.projects

import java.time.Instant

// These values are not actually unused, as they will be passed from and to the client
@Suppress("unused")
enum class SelectionMode {
    One,
    Many,
}

data class Project(
    val id: Int,
    val userId: Int,
    val version: Int,
    val name: String,
    val meta: String,
    val description: String,
    val guidelines: String,
    val tags: List<String>,
    val mode: SelectionMode,
    val options: List<ProjectOption>,
    val conditionals: List<String>,
    val isPrivate: Boolean,
    val isDeleted: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?,
)

data class ProjectRequest(
    val userId: Int,
    val name: String,
    val meta: String,
    val description: String,
    val guidelines: String,
    val tags: List<String>,
    val mode: SelectionMode,
    val options: List<ProjectOption>,
    val conditionals: List<String>?,
    val isPrivate: Boolean?,
)

data class ProjectUpdate(
    val name: String? = null,
    val description: String? = null,
    val guidelines: String? = null,
    val tags: List<String>? = null,
    val mode: SelectionMode? = null,
    val options: List<ProjectOption>? = null,
    val conditionals: List<String>? = null,
    val isPrivate: Boolean? = null,
    val isDeleted: Boolean? = null,
)

data class ProjectEdit(
    val name: String? = null,
    val description: String? = null,
    val guidelines: String? = null,
    val tags: List<String>? = null,
    val mode: SelectionMode? = null,
    val options: List<ProjectOption>? = null,
    val conditionals: List<String>? = null,
    val isPrivate: Boolean? = null,
)

data class ProjectOption(
    val name: String,
    val add: List<String>,
    val remove: List<String>,
)

data class ProjectVersion(
    val id: Int,
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