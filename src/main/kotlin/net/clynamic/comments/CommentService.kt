package net.clynamic.comments

import net.clynamic.comments.CommentsService.Comments
import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.NoSuchRecordException
import net.clynamic.common.PageOptionsBase
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
    IntSqlService<CommentRequest, Comment, CommentUpdate, Comments, CommentPageOptions>(database) {
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

    override suspend fun query(options: CommentPageOptions): Query =
        super.query(options)
            .let { base -> options.user?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base ->
                options.project?.let { base.andWhere { table.projectId eq it } } ?: base
            }
            .let { base ->
                when (options.hidden) {
                    is Visibility.None -> base.andWhere { table.hiddenBy.isNull() }
                    is Visibility.Only -> base.andWhere { table.hiddenBy.isNull() or (table.userId eq options.hidden.id) }
                    is Visibility.All -> base
                }
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
}

data class CommentPageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = true,
    val user: Int? = null,
    val project: Int? = null,
    val hidden: Visibility = Visibility.None,
) : PageOptionsBase<CommentPageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ) = copy(
        page = page,
        size = size,
        sort = sort,
        order = order,
        limited = limited,
    )

    fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
        user: Int? = this.user,
        project: Int? = this.project,
        hidden: Visibility = this.hidden,
    ) = copy(
        page = page,
        size = size,
        sort = sort,
        order = order,
        limited = limited,
        user = user,
        project = project,
        hidden = hidden,
    )
}