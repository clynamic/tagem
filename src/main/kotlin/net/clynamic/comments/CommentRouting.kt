package net.clynamic.comments

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.getPageAndSize
import net.clynamic.common.getSortAndOrder
import net.clynamic.users.UserPrincipal
import net.clynamic.users.UserRank
import net.clynamic.users.authorize
import org.jetbrains.exposed.sql.SortOrder
import java.time.Duration
import java.time.Instant

fun Application.configureCommentsRouting() {
    val service = CommentsService(attributes[DATABASE_KEY])

    routing {
        get("/comments/{id}", {
            tags = listOf("comments")
            description = "Get a comment by ID"
            request {
                pathParameter<Int>("id") { description = "The comment ID" }
            }
            response {
                HttpStatusCode.OK to {
                    body<Comment> {}
                }
                HttpStatusCode.NotFound to {
                    description = "Comment not found"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val hiddenComments = call.hiddenComments()
            val project =
                service.read(id, hiddenComments) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, project)
        }
        get("/comments", {
            tags = listOf("comments")
            description = "Get a page of comments"
            request {
                queryParameter<Int?>("page") { description = "The page number" }
                queryParameter<Int?>("size") { description = "The page size" }
                queryParameter<String?>("sort") { description = "The sort field" }
                queryParameter<SortOrder?>("order") { description = "The sort order" }
                queryParameter<Int?>("user") { description = "User ID to filter by association" }
                queryParameter<Int?>("project") {
                    description = "Project ID to filter by association"
                }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<Comment>> {}
                }
            }
        }) {
            val (page, size) = call.getPageAndSize()
            val (sort, order) = call.getSortAndOrder()
            val user = call.parameters["user"]?.toIntOrNull()
            val project = call.parameters["project"]?.toIntOrNull()
            val hiddenComments = call.hiddenComments()
            val projects = service.page(page, size, sort, order, user, project, hiddenComments)
            call.respond(HttpStatusCode.OK, projects)
        }
        authenticate {
            post("/comments", {
                tags = listOf("comments")
                description = "Create a comment"
                securitySchemeName = "jwt"
                request {
                    body<CommentRequest> {
                        description = "New comment properties"
                    }
                }
                response {
                    HttpStatusCode.Created to {
                        body<Int> {
                            description = "The new comment ID"
                        }
                    }
                }
            }) {
                val comment = call.receive<CommentRequest>()
                val id = service.create(comment)
                call.response.headers.append("Location", "/comments/${id}")
                call.respond(HttpStatusCode.Created, id)
            }
            authorize({
                rankedOrHigher(UserRank.Member) { userId, commentId ->
                    service.read(commentId)?.let {
                        it.userId == userId && (it.hiddenBy == null || it.hiddenBy == userId)
                    }
                }
            }) {
                put("/comments/{id}", {
                    tags = listOf("comments")
                    description = "Edit a comment by ID"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The comment ID" }
                        body<CommentEdit> {
                            description = "New comment properties"
                        }
                    }
                    response {
                        HttpStatusCode.NoContent to {
                            description = "Comment edited"
                        }
                    }
                }) {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest)

                    service.dbQuery {
                        val edit = call.receive<CommentEdit>()

                        val comment =
                            service.read(id) ?: return@dbQuery call.respond(HttpStatusCode.NotFound)

                        val update =
                            if (comment.addedOn.plus(Duration.ofMinutes(5))
                                    .isBefore(Instant.now())
                            ) {
                                CommentUpdate(content = edit.content, editedOn = Instant.now())
                            } else {
                                // Comments younger than 5 minutes can be ninja edited
                                CommentUpdate(content = edit.content)
                            }

                        service.update(id, update)
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
            authorize({
                rankedOrHigher(UserRank.Member) { userId, commentId ->
                    service.read(commentId)?.let {
                        if (it.hiddenBy == null) {
                            it.userId == userId
                        } else {
                            it.hiddenBy == userId
                        }
                    }
                }
                rankedOrHigher(UserRank.Janitor)
            }) {
                delete("/comments/{id}", {
                    tags = listOf("comments")
                    description = "Hide a comment by ID"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The comment ID" }
                    }
                    response {
                        HttpStatusCode.NoContent to {
                            description = "Comment hidden"
                        }
                    }
                }) {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest)
                    val userId = call.principal<UserPrincipal>()?.id
                        ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                    service.update(id, CommentUpdate(hiddenBy = userId))
                    call.respond(HttpStatusCode.NoContent)
                }
                patch("/comments/{id}/restore", {
                    tags = listOf("comments")
                    description = "Restore a comment by ID"
                    securitySchemeName = "jwt"
                    request {
                        pathParameter<Int>("id") { description = "The comment ID" }
                    }
                    response {
                        HttpStatusCode.NoContent to {
                            description = "Comment restored"
                        }
                    }
                }) {
                    val id = call.parameters["id"]?.toIntOrNull()
                        ?: return@patch call.respond(HttpStatusCode.BadRequest)

                    service.update(id, CommentUpdate(hiddenBy = null))
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
