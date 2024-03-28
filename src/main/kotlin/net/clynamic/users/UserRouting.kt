package net.clynamic.users

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.JWT_KEY
import net.clynamic.common.id
import net.clynamic.common.paged
import java.io.IOException

fun Application.configureUsersRouting() {
    val jwtKey = attributes[JWT_KEY]
    val service = UsersService(attributes[DATABASE_KEY])
    val client = UsersClient()

    intercept(Plugins) {
        val principal = call.authentication.principal<JWTPrincipal>()
        if (principal != null) {
            val id = principal.getClaim("user_id", Int::class) ?: return@intercept call.respond(
                HttpStatusCode.Unauthorized,
                "Invalid token"
            )

            val user = service.readOrNull(id) ?: return@intercept call.respond(
                HttpStatusCode.Unauthorized,
                "Invalid token"
            )

            if (user.isBanned) {
                call.respond(HttpStatusCode.Forbidden, "Your account has been suspended")
                return@intercept finish()
            }
        }
    }

    routing {
        post("/login", {
            tags = listOf("auth")
            description = "Fetch a JWT token"
            operationId = "login"
            request {
                body<UserCredentials> {
                    description = "User credentials"
                    required = true
                }
            }
            response {
                HttpStatusCode.Created to {
                    description = "The JWT token"
                    body<String> {}
                }
            }
        }) {
            val credentials = call.receive<UserCredentials>()
            val userInfo: UserInfo

            try {
                userInfo = client.authenticate(credentials.username, credentials.password)
            } catch (e: IOException) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    "Invalid credentials: ${e.message}"
                )
            }

            val request = userInfo.request
                ?: return@post call.respond(
                    HttpStatusCode.Forbidden,
                    "You do not have enough contributions to create an account"
                )

            val user = service.dbQuery {
                var user = service.readOrNull(userInfo.id)
                if (user == null) {
                    val id = service.create(request)
                    user = service.read(id)
                } else {
                    user = user.copy(
                        // contribution rank or database rank, whichever is higher
                        rank = UserRank.entries.toTypedArray()[maxOf(
                            user.rank.ordinal,
                            request.rank.ordinal
                        )]
                    )
                    service.update(userInfo.id, userInfo.update)
                }
                return@dbQuery user
            }

            val token = JWT.create()
                .withClaim(UserPrincipal.USER_ID_CLAIM, user.id)
                .withClaim(UserPrincipal.USER_NAME_CLAIM, user.name)
                .withClaim(UserPrincipal.USER_RANK_CLAIM, user.rank.name)
                .sign(Algorithm.HMAC256(jwtKey))
            call.respond(HttpStatusCode.Created, token)
        }
        get("/users/{id}", {
            tags = listOf("users")
            description = "Get a user by ID"
            operationId = "user"
            request {
                pathParameter<Int>("id") {
                    description = "The user ID"
                    required = true
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "The user"
                    body<User> {}
                }
                HttpStatusCode.NotFound to {
                    description = "User not found"
                }
            }
        }) {
            val id = call.parameters.id
            val user = service.read(id)
            call.respond(HttpStatusCode.OK, user)
        }
        get("/users",
            {
                tags = listOf("users")
                description = "Get a page of users"
                operationId = "users"
                request {
                    queryParameter<Int?>("page") { description = "The page number" }
                    queryParameter<Int?>("size") { description = "The page size" }
                    queryParameter<String?>("sort") { description = "The sort field" }
                    queryParameter<String?>("order") { description = "The sort order" }
                }
                response {
                    HttpStatusCode.OK to {
                        description = "The users"
                        body<UserPage> {}
                    }
                }
            }) {
            val options = UserPageOptions().paged(call)
            val users = service.page(options)
            call.respond(HttpStatusCode.OK, users)
        }
        authenticate {
            authorize({
                ranked(UserRank.Admin)
            }) {
                patch("/users/{id}/rank", {
                    tags = listOf("users")
                    description = "Update a user's rank"
                    operationId = "updateUserRank"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") {
                            description = "The user ID"
                            required = true
                        }
                        body<UserRank> {
                            description = "The new user rank"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "User permissions were updated"
                        }
                        HttpStatusCode.NotFound to {
                            description = "User not found"
                        }
                    }
                }) {
                    val id = call.parameters.id
                    val rank = call.receive<UserRank>()
                    service.update(id, UserUpdate(rank = rank))
                    call.respond(HttpStatusCode.OK, "User permissions were updated")
                }
                delete("/users/{id}", {
                    tags = listOf("users")
                    description = "Ban a user"
                    operationId = "banUser"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") {
                            description = "The user ID"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "User was banned"
                        }
                        HttpStatusCode.NotFound to {
                            description = "User not found"
                        }
                    }
                }) {
                    val id = call.parameters.id
                    service.update(id, UserUpdate(isBanned = true))
                    call.respond(HttpStatusCode.OK, "User was banned")
                }
                patch("/users/{id}/restore", {
                    tags = listOf("users")
                    description = "Pardon a user"
                    operationId = "unbanUser"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") {
                            description = "The user ID"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "User was restored"
                        }
                        HttpStatusCode.NotFound to {
                            description = "User not found"
                        }
                    }
                }) {
                    val id = call.parameters.id
                    service.update(id, UserUpdate(isBanned = false))
                    call.respond(HttpStatusCode.OK, "User was restored")
                }
            }
        }
    }
}
