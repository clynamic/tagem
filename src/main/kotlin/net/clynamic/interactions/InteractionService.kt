package net.clynamic.interactions

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.PageOptionsBase
import net.clynamic.common.instant
import net.clynamic.common.setAll
import net.clynamic.interactions.InteractionsService.Interactions
import net.clynamic.projects.ProjectsService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class InteractionsService(database: Database) :
    IntSqlService<InteractionRequest, Interaction, Nothing, Interactions, InteractionPageOptions>(
        database
    ) {
    object Interactions : IntServiceTable() {
        val endpoint = text("endpoint")
        val origin = text("origin")
        val userId = integer("user_id").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        ).nullable()
        val response = integer("response")
        val createdAt = instant("created_at")
    }

    override val table: Interactions
        get() = Interactions

    override fun toModel(row: ResultRow): Interaction {
        return Interaction(
            id = row[Interactions.id],
            endpoint = row[Interactions.endpoint],
            origin = row[Interactions.origin],
            userId = row[Interactions.userId],
            response = row[Interactions.response],
            createdAt = row[Interactions.createdAt],
        )
    }

    override fun fromUpdate(statement: UpdateStatement, update: Nothing) {
        throw UnsupportedOperationException("Cannot update an interaction")
    }

    override suspend fun query(options: InteractionPageOptions): Query = dbQuery {
        super.query(options)
            .let { base ->
                options.endpoint?.let { base.andWhere { table.endpoint eq it } } ?: base
            }
            .let { base -> options.origin?.let { base.andWhere { table.origin eq it } } ?: base }
            .let { base -> options.userId?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base ->
                options.response?.let { base.andWhere { table.response eq it } } ?: base
            }
    }

    override fun fromRequest(statement: InsertStatement<*>, request: InteractionRequest) {
        statement.setAll {
            Interactions.endpoint set request.endpoint
            Interactions.origin set request.origin
            Interactions.userId set request.userId
            Interactions.response set request.response
            Interactions.createdAt set Instant.now()
        }
    }
}

data class InteractionPageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = false,
    val endpoint: String? = null,
    val origin: String? = null,
    val userId: Int? = null,
    val response: Int? = null,
) : PageOptionsBase<InteractionPageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ): InteractionPageOptions =
        duplicate(
            page,
            size,
            sort,
            order,
            limited,
            endpoint,
            origin,
            userId,
            response,
        )

    fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
        endpoint: String? = this.endpoint,
        origin: String? = this.origin,
        userId: Int? = this.userId,
        response: Int? = this.response,
    ): InteractionPageOptions = InteractionPageOptions(
        page,
        size,
        sort,
        order,
        limited,
        endpoint,
        origin,
        userId,
        response,
    )
}