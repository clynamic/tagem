package net.clynamic.contributions

data class Contribution(
    val projectId: Int,
    val userId: Int,
    val changes: Int
)

data class ContributionRequest(
    val projectId: Int,
    val userId: Int,
    val changes: Int
)

data class ContributionUpdate(
    val changes: Int? = null
)

data class ContributionId(
    val projectId: Int,
    val userId: Int
)