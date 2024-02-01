package net.clynamic.comments

import java.time.Instant

data class Comment(
    val id: Int,
    val projectId: Int,
    val userId: Int,
    val content: String,
    val hiddenBy: Int?,
    val addedOn: Instant,
    val editedOn: Instant?,
)

data class CommentRequest(
    val projectId: Int,
    val userId: Int,
    val content: String,
)

data class CommentUpdate(
    val content: String? = null,
    val hiddenBy: Int? = null,
    val editedOn: Instant? = null,
)

data class CommentEdit(
    val content: String? = null,
)
