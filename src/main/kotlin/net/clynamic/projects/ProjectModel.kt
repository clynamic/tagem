package net.clynamic.projects

enum class SelectionMode {
    One,
    Many,
}

data class Project(
    val id: Int,
    val userId: Int,
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
)

data class ProjectOption(
    val name: String,
    val add: List<String>,
    val remove: List<String>
)
