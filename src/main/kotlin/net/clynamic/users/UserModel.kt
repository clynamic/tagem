package net.clynamic.users

import io.ktor.server.auth.Principal
import io.swagger.v3.oas.annotations.media.Schema

data class User(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val rank: UserRank,
    @field:Schema(required = true)
    val strikes: Int,
    @field:Schema(required = true)
    val isBanned: Boolean,
)

data class UserRequest(
    @field:Schema(required = true)
    val id: Int,
    @field:Schema(required = true)
    val name: String,
    @field:Schema(required = true)
    val rank: UserRank,
    @field:Schema(required = true, defaultValue = "0")
    val strikes: Int = 0,
    @field:Schema(required = true, defaultValue = "false")
    val isBanned: Boolean = false,
)

data class UserUpdate(
    @field:Schema(nullable = true)
    val name: String? = null,
    @field:Schema(nullable = true)
    val rank: UserRank? = null,
    @field:Schema(nullable = true)
    val strikes: Int? = null,
    @field:Schema(nullable = true)
    val isBanned: Boolean? = null,
)

data class UserCredentials(
    @field:Schema(required = true)
    val username: String,
    @field:Schema(required = true)
    val password: String,
)

data class UserInfo(
    val id: Int,
    val name: String,
    val contributions: Int,
) {
    private val rank: UserRank?
        get() = when {
            contributions >= 1000 -> UserRank.Privileged
            contributions >= 100 -> UserRank.Member
            else -> null // not eligible for account creation
        }

    val request: UserRequest?
        get() = rank?.let { UserRequest(id, name, it) }

    val update: UserUpdate
        get() = UserUpdate(name, rank)
}

data class UserPrincipal(
    val id: Int,
    val name: String,
    val rank: UserRank,
) : Principal {
    companion object {
        const val USER_ID_CLAIM = "user_id"
        const val USER_NAME_CLAIM = "user_name"
        const val USER_RANK_CLAIM = "user_rank"
    }
}