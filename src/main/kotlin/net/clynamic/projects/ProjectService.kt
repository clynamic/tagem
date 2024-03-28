package net.clynamic.projects

import com.fasterxml.jackson.core.type.TypeReference
import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.NoSuchRecordException
import net.clynamic.common.PageOptionsBase
import net.clynamic.common.Visibility
import net.clynamic.common.instant
import net.clynamic.common.json
import net.clynamic.common.like
import net.clynamic.common.setAll
import net.clynamic.projects.ProjectVersionsService.ProjectVersions
import net.clynamic.projects.ProjectsService.Projects
import net.clynamic.users.UsersService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ProjectsService(database: Database) :
    IntSqlService<ProjectRequest, Project, ProjectUpdate, Projects, ProjectPageOptions>(database) {
    object Projects : IntServiceTable() {
        val userId = integer("user_id").references(UsersService.Users.id)
        val version = integer("version")
        val name = text("name")
        val meta = text("meta")
        val description = text("description")
        val guidelines = text("guidelines")
        val tags = json("tags", object : TypeReference<List<String>>() {})
        val mode = enumeration<SelectionMode>("mode")
        val options = json("options", object : TypeReference<List<ProjectOption>>() {})
        val conditionals = json("conditionals", object : TypeReference<List<String>>() {})
        val isPrivate = bool("is_private")
        val isDeleted = bool("is_deleted")
        val createdAt = instant("created_at")
        val updatedAt = instant("updated_at").nullable()

        init {
            uniqueIndex(id, meta)
        }
    }

    override val table: Projects
        get() = Projects

    override fun toModel(row: ResultRow): Project {
        return Project(
            id = row[Projects.id],
            userId = row[Projects.userId],
            version = row[Projects.version],
            name = row[Projects.name],
            meta = row[Projects.meta],
            description = row[Projects.description],
            guidelines = row[Projects.guidelines],
            tags = row[Projects.tags],
            mode = row[Projects.mode],
            options = row[Projects.options],
            conditionals = row[Projects.conditionals],
            isPrivate = row[Projects.isPrivate],
            isDeleted = row[Projects.isDeleted],
            createdAt = row[Projects.createdAt],
            updatedAt = row[Projects.updatedAt]
        )
    }

    private val versionsService = ProjectVersionsService(database)

    override fun fromRequest(statement: InsertStatement<*>, request: ProjectRequest) {
        statement.setAll {
            Projects.version set 1
            Projects.name set request.name
            Projects.meta set request.meta
            Projects.userId set request.userId
            Projects.description set request.description
            Projects.guidelines set request.guidelines
            Projects.tags set request.tags
            Projects.mode set request.mode
            Projects.options set request.options
            Projects.conditionals set (request.conditionals ?: emptyList())
            Projects.isPrivate set (request.isPrivate ?: false)
            Projects.isDeleted set false
            Projects.createdAt set Instant.now()
        }
    }

    override suspend fun query(
        options: ProjectPageOptions,
    ): Query {
        return super.query(options)
            .let { base -> options.user?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base -> options.name?.let { base.andWhere { table.name like "%$it%" } } ?: base }
            .let { base ->
                options.description?.let { base.andWhere { table.description like "%$it%" } }
                    ?: base
            }
            .let { base ->
                options.guidelines?.let { base.andWhere { table.guidelines like "%$it%" } } ?: base
            }
            .let { base ->
                options.tags?.let {
                    it.fold(base) { acc, tag ->
                        acc.andWhere { table.tags like "%\"$tag\"%" }
                    }
                } ?: base
            }
            .let { base ->
                options.search?.let {
                    base.andWhere {
                        (table.name like "%$it%").or(table.description like "%$it%")
                            .or(table.guidelines like "%$it%")
                    }
                } ?: base
            }
            .let { base ->
                when (options.private) {
                    is Visibility.None -> base.andWhere { table.isPrivate eq false }
                    is Visibility.Only -> base.andWhere { table.isPrivate eq false or (table.userId eq options.private.id) }
                    is Visibility.All -> base
                }
            }
            .let { base ->
                when (options.deleted) {
                    is Visibility.None -> base.andWhere { table.isDeleted eq false }
                    is Visibility.Only -> base.andWhere { table.isDeleted eq false or (table.userId eq options.deleted.id) }
                    is Visibility.All -> base
                }
            }
    }

    suspend fun readOrNull(
        id: Int,
        deleted: Visibility = Visibility.None,
    ): Project? {
        return super.readOrNull(id).let {
            when (deleted) {
                is Visibility.None -> it?.takeIf { !it.isDeleted }
                is Visibility.Only -> it?.takeIf { !it.isDeleted || (it.userId == deleted.id) }
                is Visibility.All -> it
            }
        }
    }

    suspend fun read(id: Int, deleted: Visibility = Visibility.None): Project {
        return readOrNull(id, deleted) ?: throw NoSuchRecordException(id, "Project")
    }

    override suspend fun read(id: Int): Project = read(id, Visibility.None)

    override suspend fun create(request: ProjectRequest): Int = dbQuery {
        val id = super.create(request)
        versionsService.create(ProjectVersionRequest(read(id)))
        id
    }

    override fun fromUpdate(statement: UpdateStatement, update: ProjectUpdate) {
        statement.setAll {
            Projects.name set update.name
            Projects.description set update.description
            Projects.guidelines set update.guidelines
            Projects.tags set update.tags
            Projects.mode set update.mode
            Projects.options set update.options
            Projects.conditionals set update.conditionals
            Projects.isPrivate set update.isPrivate
            Projects.isDeleted set update.isDeleted
            Projects.updatedAt set Instant.now()
        }
    }

    override suspend fun update(id: Int, update: ProjectUpdate): Unit = dbQuery {
        val previous = read(id)
        super.update(id, update)
        table.update({ table.selector(id) }) { it.setAll { table.version set previous.version + 1 } }
        versionsService.create(ProjectVersionRequest(read(id)))
    }
}

class ProjectVersionsService(database: Database) :
    IntSqlService<ProjectVersionRequest, ProjectVersion, Nothing, ProjectVersions, ProjectVersionPageOptions>(
        database
    ) {
    object ProjectVersions : IntServiceTable() {
        val projectId = integer("project_id").references(
            id,
            onDelete = ReferenceOption.CASCADE
        )
        val version = integer("version")
        val name = text("name")
        val meta = text("meta")
        val description = text("description")
        val guidelines = text("guidelines")
        val tags = json("tags", object : TypeReference<List<String>>() {})
        val mode = enumeration<SelectionMode>("mode")
        val options = json("options", object : TypeReference<List<ProjectOption>>() {})
        val conditionals = json("conditionals", object : TypeReference<List<String>>() {})
        val createdAt = instant("created_at")

        init {
            uniqueIndex(projectId, version)
        }
    }

    override val table: ProjectVersions
        get() = ProjectVersions

    override fun toModel(row: ResultRow): ProjectVersion {
        return ProjectVersion(
            id = row[ProjectVersions.id],
            projectId = row[ProjectVersions.projectId],
            version = row[ProjectVersions.version],
            name = row[ProjectVersions.name],
            meta = row[ProjectVersions.meta],
            description = row[ProjectVersions.description],
            guidelines = row[ProjectVersions.guidelines],
            tags = row[ProjectVersions.tags],
            mode = row[ProjectVersions.mode],
            options = row[ProjectVersions.options],
            conditionals = row[ProjectVersions.conditionals],
            createdAt = row[ProjectVersions.createdAt],
        )
    }

    override suspend fun query(options: ProjectVersionPageOptions): Query =
        super.query(options)
            .let { base ->
                options.project?.let { base.andWhere { table.projectId eq it } } ?: base
            }

    override fun fromUpdate(statement: UpdateStatement, update: Nothing) {
        throw UnsupportedOperationException("Project versions cannot be updated")
    }

    override fun fromRequest(statement: InsertStatement<*>, request: ProjectVersionRequest) {
        statement.setAll {
            ProjectVersions.projectId set request.projectId
            ProjectVersions.version set request.version
            ProjectVersions.name set request.name
            ProjectVersions.meta set request.meta
            ProjectVersions.description set request.description
            ProjectVersions.guidelines set request.guidelines
            ProjectVersions.tags set request.tags
            ProjectVersions.mode set request.mode
            ProjectVersions.options set request.options
            ProjectVersions.conditionals set request.conditionals
            ProjectVersions.createdAt set Instant.now()
        }
    }
}

data class ProjectPageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = true,
    val name: String? = null,
    val description: String? = null,
    val guidelines: String? = null,
    val tags: List<String>? = null,
    val search: String? = null,
    val user: Int? = null,
    val private: Visibility = Visibility.None,
    val deleted: Visibility = Visibility.None,
) : PageOptionsBase<ProjectPageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ): ProjectPageOptions =
        duplicate(
            page,
            size,
            sort,
            order,
            limited,
            name,
            description,
            guidelines,
            tags,
            search,
            user,
            private,
            deleted
        )

    fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
        name: String? = this.name,
        description: String? = this.description,
        guidelines: String? = this.guidelines,
        tags: List<String>? = this.tags,
        search: String? = this.search,
        user: Int? = this.user,
        private: Visibility = this.private,
        deleted: Visibility = this.deleted,
    ): ProjectPageOptions =
        copy(
            page = page,
            size = size,
            sort = sort,
            order = order,
            limited = limited,
            name = name,
            description = description,
            guidelines = guidelines,
            tags = tags,
            search = search,
            user = user,
            private = private,
            deleted = deleted,
        )
}

data class ProjectVersionPageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = true,
    val project: Int? = null,
) : PageOptionsBase<ProjectVersionPageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ): ProjectVersionPageOptions =
        duplicate(page, size, sort, order, limited, project)

    fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
        project: Int? = this.project,
    ): ProjectVersionPageOptions =
        copy(
            page = page,
            size = size,
            sort = sort,
            order = order,
            limited = limited,
            project = project,
        )
}