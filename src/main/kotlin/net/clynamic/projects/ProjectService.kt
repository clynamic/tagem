package net.clynamic.projects

import com.fasterxml.jackson.core.type.TypeReference
import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.NoSuchRecordException
import net.clynamic.common.Visibility
import net.clynamic.common.instant
import net.clynamic.common.json
import net.clynamic.common.setAll
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
    IntSqlService<ProjectRequest, Project, ProjectUpdate, ProjectsService.Projects>(database) {
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

    private suspend fun query(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        private: Visibility = Visibility.None,
        deleted: Visibility = Visibility.None,
    ): Query {
        return super.query(page, size, sort, order)
            .let { base ->
                when (private) {
                    is Visibility.None -> base.andWhere { table.isPrivate eq false }
                    is Visibility.Only -> base.andWhere { table.isPrivate eq false or (table.userId eq private.id) }
                    is Visibility.All -> base
                }
            }
            .let { base ->
                when (deleted) {
                    is Visibility.None -> base.andWhere { table.isDeleted eq false }
                    is Visibility.Only -> base.andWhere { table.isDeleted eq false or (table.userId eq deleted.id) }
                    is Visibility.All -> base
                }
            }
    }

    override suspend fun query(page: Int?, size: Int?, sort: String?, order: SortOrder?): Query {
        return query(page, size, sort, order, Visibility.None, Visibility.None)
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

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        user: Int? = null,
        private: Visibility = Visibility.None,
        deleted: Visibility = Visibility.None,
    ): List<Project> = dbQuery {
        query(page, size, sort, order, private, deleted)
            .let { base -> user?.let { base.andWhere { table.userId eq it } } ?: base }
            .toModelList()
    }
}

class ProjectVersionsService(database: Database) :
    IntSqlService<ProjectVersionRequest, ProjectVersion, Nothing, ProjectVersionsService.ProjectVersions>(
        database
    ) {
    object ProjectVersions : IntServiceTable() {
        val projectId = integer("project_id").references(
            ProjectsService.Projects.id,
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

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        project: Int? = null,
    ): List<ProjectVersion> = dbQuery {
        query(page, size, sort, order)
            .let { base -> project?.let { base.andWhere { table.projectId eq it } } ?: base }
            .toModelList()
    }
}
