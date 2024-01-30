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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.UpdateBuilder
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

    override fun fromUpdate(statement: UpdateBuilder<*>, update: CommentUpdate) {
        statement.setAll {
            Comments.content to update.content
            Comments.isHidden to update.isHidden
            Comments.editedOn to update.editedOn
        }
    }

    override fun fromRequest(statement: UpdateBuilder<*>, request: CommentRequest) {
        statement.setAll {
            Comments.projectId to request.projectId
            Comments.userId to request.userId
            Comments.content to request.content
            Comments.isHidden to request.isHidden
            Comments.addedOn to Instant.now()
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
            .map { toModel(it) }
    }
    
    suspend fun isOwner(id: Int, userId: Int): Boolean = dbQuery {
        table.select { table.id eq id and (table.userId eq userId) }.count() == 1L
    }
}