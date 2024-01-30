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
import net.clynamic.common.getPageAndSize
import net.clynamic.common.getSortAndOrder
import java.io.IOException

fun Application.configureUsersRouting() {
    val jwtKey = attributes[JWT_KEY]
    val service = UsersService(attributes[DATABASE_KEY])
    val client = UsersClient()

    intercept(Plugins) {
        val principal = call.authentication.principal<JWTPrincipal>()
        if (principal != null) {
            val id = principal.getClaim("user_id", Int::class)

            if (id == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                return@intercept finish()
            }

            val user = service.read(id)

            if (user == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid token")
                return@intercept finish()
            }

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
            request {
                body<UserCredentials> {
                    description = "User credentials"
                }
            }
            response {
                HttpStatusCode.Created to {
                    body<String> {
                        description = "The JWT token"
                    }
                }
            }
        }) {
            val credentials = call.receive<UserCredentials>()
            val userInfo: UserInfo

            try {
                userInfo = client.authenticate(credentials.username, credentials.password)
            } catch (e: IOException) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials: ${e.message}")
                return@post
            }

            val user = service.dbQuery {
                var user = service.read(userInfo.id)
                if (user == null) {
                    val id = service.create(userInfo.request)
                    user = service.read(id)!!
                } else {
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
            request {
                pathParameter<Int>("id") { description = "The user ID" }
            }
            response {
                HttpStatusCode.OK to {
                    body<User> {}
                }
                HttpStatusCode.NotFound to {
                    description = "User not found"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                return@get
            }
            val user = service.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound, "User not found")
            }
        }
        get("/users",
            {
                tags = listOf("users")
                description = "Get a page of users"
                request {
                    queryParameter<Int?>("page") { description = "The page number" }
                    queryParameter<Int?>("size") { description = "The page size" }
                    queryParameter<String?>("sort") { description = "The sort field" }
                    queryParameter<String?>("order") { description = "The sort order" }
                }
                response {
                    HttpStatusCode.OK to {
                        body<List<User>> {}
                    }
                }
            }) {
            val (page, size) = call.getPageAndSize()
            val (sort, order) = call.getSortAndOrder()
            val users = service.page(page, size, sort, order)
            call.respond(HttpStatusCode.OK, users)
        }
        authenticate {
            permissions({
                ranked(UserRank.Admin)
            }) {
                patch("/users/{id}/rank", {
                    tags = listOf("users")
                    description = "Update a user's rank"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The user ID" }
                        body<UserRank> {
                            description = "The new user rank"
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
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                    val rank = call.receive<UserRank>()
                    service.update(id, UserUpdate(rank = rank))
                    call.respond(HttpStatusCode.OK, "User permissions were updated")
                }
                delete("/users/{id}", {
                    tags = listOf("users")
                    description = "Ban a user"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The user ID" }
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
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                    service.update(id, UserUpdate(isBanned = true))
                    call.respond(HttpStatusCode.OK, "User was banned")
                }
                patch("/users/{id}/restore", {
                    tags = listOf("users")
                    description = "Pardon a user"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The user ID" }
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
                    val id = call.parameters["id"]?.toIntOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@patch
                    }
                    service.update(id, UserUpdate(isBanned = false))
                    call.respond(HttpStatusCode.OK, "User was restored")
                }
            }
        }
    }
}
