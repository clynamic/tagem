package net.clynamic.users

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.RouteScopedPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.application.isHandled
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.logging.KtorSimpleLogger
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

internal typealias OwnershipCheck = suspend (Int, Int) -> Boolean?

data class RankCheck(
    val rank: UserRank,
    val ownershipCheck: OwnershipCheck? = null,
    val orHigher: Boolean = false,
) {
    fun toShortString(): String {
        return "${rank}${if (orHigher) "+" else ""}${if (ownershipCheck != null) "?" else ""}"
    }
}

class RankConfig {
    val entries = mutableListOf<RankCheck>()

    fun ranked(rank: UserRank, ownershipCheck: OwnershipCheck? = null) {
        entries.add(RankCheck(rank, ownershipCheck))
    }

    fun rankedOrHigher(rank: UserRank, ownershipCheck: OwnershipCheck? = null) {
        entries.add(RankCheck(rank, ownershipCheck, true))
    }
}

class RanksRouteSelector(
    private val entries: List<RankCheck>
) : RouteSelector(
) {
    override fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int
    ): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String =
        "(ranks ${entries.joinToString(separator = ",") { it.toShortString() }})"
}

class RanksInterceptorConfig {
    val entries = mutableListOf<RankCheck>()
}

val RanksInterceptors: RouteScopedPlugin<RanksInterceptorConfig> =
    createRouteScopedPlugin(
        "RanksInterceptors",
        { RanksInterceptorConfig() }
    ) {
        val logger = KtorSimpleLogger("net.clynamic.tagme.users.Ranking")

        onCallReceive { call ->
            if (call.isHandled) return@onCallReceive

            val userId = call.principal<UserPrincipal>()?.id
            if (userId == null) {
                logger.trace("Ranking cancelled for ${call.request.local.uri} due to invalid token")
                return@onCallReceive call.respond(
                    HttpStatusCode.Unauthorized, "Missing or invalid Token"
                )
            }

            val service = UsersService(call.application.attributes[DATABASE_KEY])

            val rank = service.read(userId)?.rank
            if (rank == null) {
                logger.trace("Ranking cancelled for ${call.request.local.uri} due to missing rank")
                return@onCallReceive call.respond(
                    HttpStatusCode.Unauthorized,
                    "Token User not found"
                )
            }

            val hasValidRank = pluginConfig.entries.any { entry ->
                logger.trace("Ranking ${call.request.local.uri} against ${entry.toShortString()}")
                val isRank = rank == entry.rank
                val isOrHigher = entry.orHigher && rank >= entry.rank
                val hasOwnership = if (entry.ownershipCheck != null) {
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        logger.trace("Ranking cancelled for ${call.request.local.uri} due to missing ID parameter")
                        return@onCallReceive call.respond(
                            HttpStatusCode.BadRequest,
                            "Missing ID parameter"
                        )
                    }
                    val result = entry.ownershipCheck.invoke(userId, id)
                    if (result == null) {
                        logger.trace("Ranking cancelled for ${call.request.local.uri} due to non-existent resource")
                        return@onCallReceive call.respond(
                            HttpStatusCode.NotFound,
                            "Resource not found"
                        )
                    }
                    result
                } else {
                    true
                }

                val permitted = (isRank || isOrHigher) && hasOwnership

                if (permitted) {
                    logger.trace("Ranking succeeded for ${call.request.local.uri} with ${entry.toShortString()}")
                } else {
                    if (!isRank && !isOrHigher) {
                        logger.trace("Ranking failed for ${call.request.local.uri} with ${entry.toShortString()} due to insufficient rank")
                    } else {
                        logger.trace("Ranking failed for ${call.request.local.uri} with ${entry.toShortString()} due to insufficient ownership")
                    }
                }

                return@any permitted
            }

            if (!hasValidRank) {
                logger.trace("Ranking failed for ${call.request.local.uri} due to insufficient permissions")
                return@onCallReceive call.respond(
                    HttpStatusCode.Forbidden,
                    "Insufficient permissions"
                )
            }
        }
    }

fun Route.authorize(block: RankConfig.() -> Unit, build: Route.() -> Unit): Route {
    val config = RankConfig().apply(block)
    val ranksRoute = createChild(RanksRouteSelector(config.entries))
    ranksRoute.install(RanksInterceptors) {
        entries.addAll(config.entries)
    }
    ranksRoute.build()
    return ranksRoute
}