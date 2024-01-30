package net.clynamic.contributions

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
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant

class ContributionsService(database: Database) :
    IntSqlService<ContributionRequest, Contribution, Nothing, ContributionsService.Contributions>(
        database
    ) {
    object Contributions : IntServiceTable() {
        val projectId = integer("project_id").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val userId = integer("user_id").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val postId = integer("post_id")
        val createdOn = instant("created_on")
    }

    override val table: Contributions
        get() = Contributions

    override fun toModel(row: ResultRow): Contribution {
        return Contribution(
            id = row[Contributions.id],
            projectId = row[Contributions.projectId],
            userId = row[Contributions.userId],
            postId = row[Contributions.postId],
            createdOn = row[Contributions.createdOn],
        )
    }

    override fun fromUpdate(statement: UpdateBuilder<*>, update: Nothing) {
        // No-op
    }

    override fun fromRequest(statement: UpdateBuilder<*>, request: ContributionRequest) {
        statement.setAll {
            Contributions.projectId set request.projectId
            Contributions.userId set request.userId
            Contributions.postId set request.postId
            Contributions.createdOn set Instant.now()
        }
    }

    suspend fun page(
        page: Int? = null,
        size: Int? = null,
        sort: String? = null,
        order: SortOrder? = null,
        projectId: Int? = null,
        userId: Int? = null
    ): List<Contribution> = dbQuery {
        query(page, size, sort, order)
            .let { base -> projectId?.let { base.andWhere { table.projectId eq it } } ?: base }
            .let { base -> userId?.let { base.andWhere { table.userId eq it } } ?: base }
            .allToModel()
    }
}