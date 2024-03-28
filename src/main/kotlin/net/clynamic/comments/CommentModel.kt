package net.clynamic.comments

import io.swagger.v3.oas.annotations.media.Schema
import net.clynamic.common.Page
import java.time.Instant

data class Comment(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val projectId: Int,
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val content: String,
    @field:Schema(nullable = true)
    val hiddenBy: Int?,
    @field:Schema(required = true)
    val createdAt: Instant,
    @field:Schema(nullable = true)
    val updatedAt: Instant?,
)

data class CommentPage(
    override val items: List<Comment>,
    override val total: Long,
    override val page: Int,
    override val pages: Int,
) : Page<Comment>()

data class CommentRequest(
    @field:Schema(required = true)
    val projectId: Int,
    @field:Schema(required = true)
    val userId: Int,
    @field:Schema(required = true)
    val content: String,
)

data class CommentUpdate(
    val content: String? = null,
    val hiddenBy: Int? = null,
    val editedOn: Instant? = null,
)

data class CommentEdit(
    @field:Schema(nullable = true)
    val content: String? = null,
)
