package net.clynamic.contributions

import io.swagger.v3.oas.annotations.media.Schema
import net.clynamic.common.Page
import java.time.Instant

data class Contribution(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val projectId: Int,
    @field:Schema(required = true)
    val projectVersion: Int,
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val postId: Int,
    @field:Schema(required = true)
    val createdAt: Instant,
)

data class ContributionPage(
    override val items: List<Contribution>,
    override val total: Long,
    override val page: Int,
    override val pages: Int,
) : Page<Contribution>()

data class ContributionInsert(
    val projectId: Int,
    val projectVersion: Int,
    val userId: Int,
    val postId: Int,
)

data class ContributionRequest(
    @field:Schema(required = true)
    val projectId: Int,
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val postId: Int,
)
