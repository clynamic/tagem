package net.clynamic.interactions

import io.swagger.v3.oas.annotations.media.Schema
import net.clynamic.common.Page
import java.time.Instant

data class Interaction(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val endpoint: String,
    @field:Schema(required = true)
    val origin: String,
    @field:Schema(nullable = true)
    val userId: Int?,
    @field:Schema(required = true)
    val response: Int,
    @field:Schema(required = true)
    val createdAt: Instant,
)

data class InteractionPage(
    override val items: List<Interaction>,
    override val total: Long,
    override val page: Int,
    override val pages: Int,
) : Page<Interaction>()

data class InteractionRequest(
    val endpoint: String,
    val origin: String,
    val userId: Int?,
    val response: Int,
)