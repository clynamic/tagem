package net.clynamic.comments

import java.time.Instant

data class Comment(
    val id: Int,
    val projectId: Int,
    val userId: Int,
    val content: String,
    val isHidden: Boolean,
    val addedOn: Instant,
    val editedOn: Instant?,
)

data class CommentRequest(
    val projectId: Int,
    val userId: Int,
    val content: String,
    val isHidden: Boolean,
)

data class CommentUpdate(
    val content: String? = null,
    val isHidden: Boolean? = null,
    val editedOn: Instant? = null,
)
