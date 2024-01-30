package net.clynamic.users

import io.ktor.server.auth.Principal

data class User(
    val id: Int,
    val name: String,
    val rank: UserRank,
    val strikes: Int,
    val isBanned: Boolean
)

data class UserRequest(
    val id: Int,
    val name: String,
    val rank: UserRank = UserRank.Visitor,
    val strikes: Int = 0,
    val isBanned: Boolean = false
)

data class UserUpdate(
    val name: String? = null,
    val rank: UserRank? = null,
    val strikes: Int? = null,
    val isBanned: Boolean? = null
)

data class UserCredentials(
    val username: String,
    val password: String
)

data class UserInfo(
    val id: Int,
    val name: String,
    val contributions: Int,
) {
    private val rank: UserRank
        get() = when {
            contributions >= 1000 -> UserRank.Privileged
            contributions >= 100 -> UserRank.Member
            else -> UserRank.Visitor
        }

    val request: UserRequest
        get() = UserRequest(id, name, rank)

    val update: UserUpdate
        get() = UserUpdate(name, rank)
}

data class UserPrincipal(
    val id: Int,
    val name: String,
    val rank: UserRank
) : Principal {
    companion object {
        const val USER_ID_CLAIM = "user_id"
        const val USER_NAME_CLAIM = "user_name"
        const val USER_RANK_CLAIM = "user_rank"
    }
}