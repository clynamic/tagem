package net.clynamic.projects

import com.fasterxml.jackson.core.type.TypeReference
import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.json
import net.clynamic.common.setAll
import net.clynamic.users.UsersService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class ProjectsService(database: Database) :
    IntSqlService<ProjectRequest, Project, ProjectUpdate, ProjectsService.Projects>(database) {
    object Projects : IntServiceTable() {
        val userId = integer("user_id").references(UsersService.Users.id)
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
    }

    override val table: Projects
        get() = Projects

    override fun toModel(row: ResultRow): Project {
        return Project(
            id = row[Projects.id],
            userId = row[Projects.userId],
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
        )
    }

    override fun fromUpdate(statement: UpdateBuilder<*>, update: ProjectUpdate) {
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
        }
    }

    override fun fromRequest(statement: UpdateBuilder<*>, request: ProjectRequest) {
        statement.setAll {
            Projects.name set request.name
            Projects.meta set request.meta
            Projects.userId set request.userId
            Projects.description set request.description
            Projects.guidelines set request.guidelines
            Projects.tags set request.tags
            Projects.mode set request.mode
            Projects.options set request.options
            Projects.conditionals set request.conditionals
            Projects.isPrivate set request.isPrivate
        }
    }

    internal suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        user: Int? = null,
    ): List<Project> = dbQuery {
        query(page, size, sort, order)
            .let { base -> user?.let { base.andWhere { table.userId eq it } } ?: base }
            .map { toModel(it) }
    }
}