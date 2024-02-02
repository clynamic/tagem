package net.clynamic.interactions

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.instant
import net.clynamic.common.setAll
import net.clynamic.projects.ProjectsService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.UpdateStatement
import java.time.Instant

class InteractionsService(database: Database) :
    IntSqlService<InteractionRequest, Interaction, Nothing, InteractionsService.Interactions>(
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

    override fun fromRequest(statement: InsertStatement<*>, request: InteractionRequest) {
        statement.setAll {
            Interactions.endpoint set request.endpoint
            Interactions.origin set request.origin
            Interactions.userId set request.userId
            Interactions.response set request.response
            Interactions.createdAt set Instant.now()
        }
    }

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        endpoint: String? = null,
        origin: String? = null,
        userId: Int? = null,
        response: Int? = null,
    ): List<Interaction> = dbQuery {
        query(page, size, sort, order)
            .let { base -> endpoint?.let { base.andWhere { table.endpoint eq it } } ?: base }
            .let { base -> origin?.let { base.andWhere { table.origin eq it } } ?: base }
            .let { base -> userId?.let { base.andWhere { table.userId eq it } } ?: base }
            .let { base -> response?.let { base.andWhere { table.response eq it } } ?: base }
            .toModelList()
    }
}