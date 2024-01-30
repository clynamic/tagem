package net.clynamic.contributions

import java.time.Instant

data class Contribution(
    val id: Int,
    val projectId: Int,
    val userId: Int,
    val postId: Int,
    val createdOn: Instant,
)

data class ContributionRequest(
    val projectId: Int,
    val userId: Int,
    val postId: Int,
)
