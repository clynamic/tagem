package net.clynamic.contributions

import net.clynamic.common.IntServiceTable
import net.clynamic.common.IntSqlService
import net.clynamic.common.PageOptionsBase
import net.clynamic.common.instant
import net.clynamic.common.setAll
import net.clynamic.contributions.ContributionsService.Contributions
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

class ContributionsService(database: Database) :
    IntSqlService<ContributionInsert, Contribution, Nothing, Contributions, ContributionPageOptions>(
        database
    ) {
    object Contributions : IntServiceTable() {
        val projectId = integer("project_id").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val projectVersion = integer("project_version")
        val userId = integer("user_id").references(
            ProjectsService.Projects.id,
            onDelete = ReferenceOption.CASCADE
        )
        val postId = integer("post_id")
        val createdAt = instant("created_at")
    }

    override val table: Contributions
        get() = Contributions

    override fun toModel(row: ResultRow): Contribution = Contribution(
        id = row[Contributions.id],
        projectId = row[Contributions.projectId],
        projectVersion = row[Contributions.projectVersion],
        userId = row[Contributions.userId],
        postId = row[Contributions.postId],
        createdAt = row[Contributions.createdAt],
    )

    override suspend fun query(options: ContributionPageOptions): Query {
        return super.query(options)
            .let { base ->
                options.projectId?.let { base.andWhere { table.projectId eq it } } ?: base
            }.let { base -> options.userId?.let { base.andWhere { table.userId eq it } } ?: base }
    }

    override fun fromUpdate(statement: UpdateStatement, update: Nothing) {
        throw UnsupportedOperationException("Cannot update a contribution")
    }

    override fun fromRequest(statement: InsertStatement<*>, request: ContributionInsert) {
        statement.setAll {
            Contributions.projectId set request.projectId
            Contributions.projectVersion set request.projectVersion
            Contributions.userId set request.userId
            Contributions.postId set request.postId
            Contributions.createdAt set Instant.now()
        }
    }

    private val projectService = ProjectsService(database)

    suspend fun create(request: ContributionRequest): Int {
        val projectVersion = projectService.read(request.projectId).version
        return create(
            ContributionInsert(
                request.projectId,
                projectVersion,
                request.userId,
                request.postId,
            )
        )
    }
}

data class ContributionPageOptions(
    override val page: Int? = null,
    override val size: Int? = null,
    override val sort: String? = null,
    override val order: SortOrder? = null,
    override val limited: Boolean = true,
    val projectId: Int? = null,
    val userId: Int? = null,
) : PageOptionsBase<ContributionPageOptions>() {
    override fun duplicate(
        page: Int?,
        size: Int?,
        sort: String?,
        order: SortOrder?,
        limited: Boolean,
    ): ContributionPageOptions = duplicate(
        page = page,
        size = size,
        sort = sort,
        order = order,
        limited = limited,
        projectId = projectId,
        userId = userId
    )

    fun duplicate(
        page: Int? = this.page,
        size: Int? = this.size,
        sort: String? = this.sort,
        order: SortOrder? = this.order,
        limited: Boolean = this.limited,
        projectId: Int? = this.projectId,
        userId: Int? = this.userId,
    ): ContributionPageOptions = copy(
        page = page,
        size = size,
        sort = sort,
        order = order,
        limited = limited,
        projectId = projectId,
        userId = userId
    )
}