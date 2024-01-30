package net.clynamic.contributions

import net.clynamic.common.ServiceTable
import net.clynamic.common.SqlService
import net.clynamic.common.setAll
import net.clynamic.projects.ProjectsService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class ContributionsService(database: Database) :
    SqlService<ContributionRequest, Contribution, ContributionUpdate, ContributionId, ContributionsService.Contributions>(
        database
    ) {
    object Contributions : ServiceTable<ContributionId>() {
        val projectId = integer("projectId").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val userId = integer("userId").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val changes = integer("changes")

        override fun selector(id: ContributionId): Op<Boolean> =
            projectId.eq(id.projectId) and userId.eq(id.userId)

        override fun toId(row: ResultRow): ContributionId = ContributionId(
            projectId = row[projectId],
            userId = row[userId]
        )
    }

    override val table: Contributions
        get() = Contributions

    override fun toModel(row: ResultRow): Contribution {
        return Contribution(
            projectId = row[Contributions.projectId],
            userId = row[Contributions.userId],
            changes = row[Contributions.changes]
        )
    }

    override fun fromUpdate(statement: UpdateBuilder<*>, update: ContributionUpdate) {
        statement.setAll {
            Contributions.changes set update.changes
        }
    }

    override fun fromRequest(statement: UpdateBuilder<*>, request: ContributionRequest) {
        statement.setAll {
            Contributions.projectId set request.projectId
            Contributions.userId set request.userId
            Contributions.changes set request.changes
        }
    }

    suspend fun increment(id: ContributionId): Unit = dbQuery {
        read(id)?.let { contribution ->
            update(id, ContributionUpdate(contribution.changes + 1))
        } ?: create(ContributionRequest(id.projectId, id.userId, 1))
    }

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        projectId: Int? = null,
        userId: Int? = null
    ) = dbQuery {
        query(page, size, sort, order)
            .let { base -> projectId?.let { base.andWhere { table.projectId eq it } } ?: base }
            .let { base -> userId?.let { base.andWhere { table.userId eq it } } ?: base }
            .map { toModel(it) }
    }
}