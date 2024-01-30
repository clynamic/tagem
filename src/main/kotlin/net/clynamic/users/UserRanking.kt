package net.clynamic.users

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
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

typealias OwnershipCheck = suspend (Int, Int) -> Boolean?

class PermissionEntry(
    val rank: UserRank,
    val ownershipCheck: OwnershipCheck? = null,
    val orHigher: Boolean = false,
)

class Permission {
    val entries = mutableListOf<PermissionEntry>()

    fun ranked(rank: UserRank, ownershipCheck: OwnershipCheck? = null) {
        entries.add(PermissionEntry(rank, ownershipCheck))
    }

    fun rankedOrHigher(rank: UserRank, ownershipCheck: OwnershipCheck? = null) {
        entries.add(PermissionEntry(rank, ownershipCheck, true))
    }
}

class PermissionsRouteSelector : RouteSelector() {
    override fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int
    ): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(permissions)"
}

fun Route.permissions(block: Permission.() -> Unit, routeBlock: Route.() -> Unit) {
    val permission = Permission().apply(block)

    val permissionRoute = this.createChild(PermissionsRouteSelector())

    permissionRoute.routeBlock()

    permissionRoute.intercept(ApplicationCallPipeline.Call) {
        val userId = call.principal<UserPrincipal>()?.id ?: return@intercept call.respond(
            HttpStatusCode.Unauthorized, "Missing or invalid Token"
        )
        val service = UsersService(call.application.attributes[DATABASE_KEY])

        val rank = service.read(userId)?.rank
            ?: return@intercept call.respond(HttpStatusCode.Unauthorized, "Token User not found")

        val hasValidRank = permission.entries.any { entry ->
            val isRank = rank == entry.rank
            val isOrHigher = entry.orHigher && rank.ordinal >= entry.rank.ordinal
            val hasOwnership = if (entry.ownershipCheck != null) {
                val result = entry.ownershipCheck.invoke(
                    userId,
                    call.parameters["id"]?.toIntOrNull()
                        ?: return@intercept call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing ID parameter"
                        )
                )
                result ?: return@intercept call.respond(HttpStatusCode.NotFound)
            } else {
                true
            }

            return@any (isRank || isOrHigher) && hasOwnership
        }

        if (!hasValidRank) {
            return@intercept call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
        }
    }
}