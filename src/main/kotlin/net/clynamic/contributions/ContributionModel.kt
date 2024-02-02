package net.clynamic.contributions

import java.time.Instant

data class Contribution(
    val id: Int,
    val projectId: Int,
    val userId: Int,
    val postId: Int,
    val createdAt: Instant,
)

data class ContributionRequest(
    val projectId: Int,
    val userId: Int,
    val postId: Int,
)
