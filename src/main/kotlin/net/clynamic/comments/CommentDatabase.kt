package net.clynamic.comments

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.instant
import net.clynamic.common.setAll
import net.clynamic.projects.ProjectsService
import net.clynamic.users.UsersService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class CommentsService(database: Database) :
    IntSqlService<CommentRequest, Comment, CommentUpdate, CommentsService.Comments>(database) {
    object Comments : IntServiceTable() {
        val projectId = integer("project_id").references(ProjectsService.Projects.id)
        val userId = integer("user_id").references(UsersService.Users.id)
        val content = text("content")
        val isHidden = bool("is_hidden")
        val addedOn = instant("added_on")
        val editedOn = instant("edited_on").nullable()
    }

    override val table: Comments
        get() = Comments

    override fun toModel(row: ResultRow): Comment {
        return Comment(
            id = row[Comments.id],
            userId = row[Comments.userId],
            projectId = row[Comments.projectId],
            content = row[Comments.content],
            isHidden = row[Comments.isHidden],
            addedOn = row[Comments.addedOn],
            editedOn = row[Comments.editedOn],
        )
    }

    override fun fromUpdate(statement: UpdateStatement, update: CommentUpdate) {
        statement.setAll {
            Comments.content set update.content
            Comments.isHidden set update.isHidden
            Comments.editedOn set update.editedOn
        }
    }

    override fun fromRequest(statement: InsertStatement<*>, request: CommentRequest) {
        statement.setAll {
            Comments.projectId set request.projectId
            Comments.userId set request.userId
            Comments.content set request.content
            Comments.isHidden set false
            Comments.addedOn set Instant.now()
        }
    }

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        user: Int? = null,
        project: Int? = null
    ): List<Comment> = dbQuery {
        query(page, size, sort, order)
            .let { base -> user?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base -> project?.let { base.andWhere { table.projectId eq it } } ?: base }
            .allToModel()
    }
}