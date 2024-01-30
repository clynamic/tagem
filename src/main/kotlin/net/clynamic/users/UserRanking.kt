package net.clynamic.users

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import net.clynamic.common.DATABASE_KEY

enum class UserRank {
    /**
     * A user who has made at least 100 contributions.
     * They can contribute to projects.
     */
    Member,

    /**
     * A user who has made at least 1000 contributions.
     * They can create projects.
     */
    Privileged,

    /**
     * A user who has been granted janitorial privileges.
     * They can edit and delete any project.
     */
    Janitor,

    /**
     * A user who is an administrator.
     * They can edit and delete any project as well as manage users.
     */
    Admin
}

class PermissionEntry(
    val rank: UserRank,
    val ownershipCheck: (suspend (Int, Int) -> Boolean)? = null
)

class Permission {
    val entries = mutableListOf<PermissionEntry>()

    fun ranked(rank: UserRank, ownershipCheck: (suspend (Int, Int) -> Boolean)? = null) {
        entries.add(PermissionEntry(rank, ownershipCheck))
    }
}

fun Route.permissions(block: Permission.() -> Unit, routeBlock: Route.() -> Unit) {
    val permission = Permission().apply(block)

    this@permissions.routeBlock()

    this@permissions.intercept(ApplicationCallPipeline.Call) {
        val userId = call.principal<UserPrincipal>()?.id ?: return@intercept call.respond(
            HttpStatusCode.Unauthorized
        )
        val service = UsersService(call.attributes[DATABASE_KEY])

        val rank = service.read(userId)?.rank
            ?: return@intercept call.respond(HttpStatusCode.Unauthorized)

        val hasValidRank = permission.entries.any { entry ->
            rank == entry.rank && (entry.ownershipCheck == null || entry.ownershipCheck.invoke(
                userId,
                call.parameters["id"]?.toIntOrNull()
                    ?: return@intercept call.respond(HttpStatusCode.BadRequest)
            ))
        }

        if (!hasValidRank) {
            return@intercept call.respond(HttpStatusCode.Forbidden)
        }
    }
}