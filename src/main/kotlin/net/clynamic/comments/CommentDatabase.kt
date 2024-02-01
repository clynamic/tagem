package net.clynamic.comments

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
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
            hiddenBy = row[Comments.hiddenBy],
            addedOn = row[Comments.addedOn],
            editedOn = row[Comments.editedOn],
        )
    }

    override fun fromUpdate(statement: UpdateStatement, update: CommentUpdate) {
        statement.setAll {
            Comments.content set update.content
            Comments.hiddenBy set update.hiddenBy
            Comments.editedOn set update.editedOn
        }
    }

    override fun fromRequest(statement: InsertStatement<*>, request: CommentRequest) {
        statement.setAll {
            Comments.projectId set request.projectId
            Comments.userId set request.userId
            Comments.content set request.content
            Comments.addedOn set Instant.now()
        }
    }

    private suspend fun query(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        hiddenComments: HiddenComments = HiddenComments.None,
    ): Query {
        return super.query(page, size, sort, order)
            .let { base ->
                when (hiddenComments) {
                    is HiddenComments.None -> base.andWhere { table.hiddenBy.isNull() }
                    is HiddenComments.Only -> base.andWhere { table.hiddenBy.isNull() or (table.userId eq hiddenComments.id) }
                    is HiddenComments.All -> base
                }
            }
    }

    override suspend fun query(page: Int?, size: Int?, sort: String?, order: SortOrder?): Query {
        return query(page, size, sort, order, HiddenComments.None)
    }

    suspend fun read(id: Int, hiddenComments: HiddenComments = HiddenComments.None): Comment? {
        return super.read(id).let {
            when (hiddenComments) {
                is HiddenComments.None -> it?.takeIf { it.hiddenBy == null }
                is HiddenComments.Only -> it?.takeIf { it.userId == hiddenComments.id }
                is HiddenComments.All -> it
            }
        }
    }

    override suspend fun read(id: Int): Comment? = read(id, HiddenComments.None)

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        user: Int? = null,
        project: Int? = null,
        hiddenComments: HiddenComments = HiddenComments.None,
    ): List<Comment> = dbQuery {
        query(page, size, sort, order, hiddenComments)
            .let { base -> user?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base -> project?.let { base.andWhere { table.projectId eq it } } ?: base }
            .allToModel()
    }
}

/**
 * How to handle hidden comments in a query
 */
sealed class HiddenComments {
    /**
     * Only visible comments
     */
    data object None : HiddenComments()

    /**
     * Visible comments or hidden comments authored by the given user
     */
    data class Only(val id: Int) : HiddenComments()

    /**
     * All comments regardless of visibility
     */
    data object All : HiddenComments()
}
