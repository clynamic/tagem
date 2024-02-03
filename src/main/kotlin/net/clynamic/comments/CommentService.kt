package net.clynamic.comments

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.NoSuchRecordException
import net.clynamic.common.Visibility
import net.clynamic.common.instant
import net.clynamic.common.setAll
import net.clynamic.projects.ProjectsService
import net.clynamic.users.UsersService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class CommentsService(database: Database) :
    IntSqlService<CommentRequest, Comment, CommentUpdate, CommentsService.Comments>(database) {
    object Comments : IntServiceTable() {
        val projectId = integer("project_id").references(ProjectsService.Projects.id)
        val userId = integer("user_id").references(UsersService.Users.id)
        val content = text("content")
        val hiddenBy = integer("hidden_by").references(UsersService.Users.id).nullable()
        val createdAt = instant("created_at")
        val updatedAt = instant("updated_at").nullable()
    }

    override val table: Comments
        get() = Comments

    override fun toModel(row: ResultRow): Comment {
        return Comment(
            id = row[Comments.id],
            userId = row[Comments.userId],
            projectId = row[Comments.projectId],
            content = row[Comments.content],
            hiddenBy = row[Comments.hiddenBy],
            createdAt = row[Comments.createdAt],
            updatedAt = row[Comments.updatedAt],
        )
    }

    override fun fromUpdate(statement: UpdateStatement, update: CommentUpdate) {
        statement.setAll {
            Comments.content set update.content
            Comments.hiddenBy set update.hiddenBy
            Comments.updatedAt set update.editedOn
        }
    }

    override fun fromRequest(statement: InsertStatement<*>, request: CommentRequest) {
        statement.setAll {
            Comments.projectId set request.projectId
            Comments.userId set request.userId
            Comments.content set request.content
            Comments.createdAt set Instant.now()
        }
    }

    private suspend fun query(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        hidden: Visibility = Visibility.None,
    ): Query {
        return super.query(page, size, sort, order)
            .let { base ->
                when (hidden) {
                    is Visibility.None -> base.andWhere { table.hiddenBy.isNull() }
                    is Visibility.Only -> base.andWhere { table.hiddenBy.isNull() or (table.userId eq hidden.id) }
                    is Visibility.All -> base
                }
            }
    }

    override suspend fun query(page: Int?, size: Int?, sort: String?, order: SortOrder?): Query {
        return query(page, size, sort, order, Visibility.None)
    }

    suspend fun readOrNull(
        id: Int,
        hidden: Visibility = Visibility.None,
    ): Comment? {
        return super.readOrNull(id).let {
            when (hidden) {
                is Visibility.None -> it?.takeIf { it.hiddenBy == null }
                is Visibility.Only -> it?.takeIf { it.hiddenBy == null || it.userId == hidden.id }
                is Visibility.All -> it
            }
        }
    }

    suspend fun read(id: Int, hidden: Visibility = Visibility.None): Comment {
        return readOrNull(id, hidden) ?: throw NoSuchRecordException(id, "Comment")
    }

    override suspend fun read(id: Int): Comment = read(id, Visibility.None)

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        user: Int? = null,
        project: Int? = null,
        hidden: Visibility = Visibility.None,
    ): List<Comment> = dbQuery {
        query(page, size, sort, order, hidden)
            .let { base -> user?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base -> project?.let { base.andWhere { table.projectId eq it } } ?: base }
            .toModelList()
    }
}
