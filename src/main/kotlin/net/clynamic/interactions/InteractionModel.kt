package net.clynamic.interactions

import java.time.Instant

data class Interaction(
    val id: Int,
    val endpoint: String,
    val origin: String,
    val userId: Int?,
    val response: Int,
    val createdAt: Instant,
)

data class InteractionRequest(
    val endpoint: String,
    val origin: String,
    val userId: Int?,
    val response: Int,
)