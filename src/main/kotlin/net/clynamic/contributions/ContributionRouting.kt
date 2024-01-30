package net.clynamic.contributions

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.getPageAndSize
import net.clynamic.common.getSortAndOrder
import org.jetbrains.exposed.sql.SortOrder

fun Application.configureContributionsRouting() {
    val service = ContributionsService(attributes[DATABASE_KEY])

    routing {
        post("/contributions", {
            tags = listOf("contributions")
            description = "Increment a user's contribution count"
            request {
                body<ContributionId> {
                    description = "The contribution ID, consisting of a project ID and user ID"
                }
            }
            response {
                HttpStatusCode.NoContent to {
                    description = "Contribution count incremented"
                }
            }
        }) {
            val contributionId = call.receive<ContributionId>()
            service.increment(contributionId)
            call.respond(HttpStatusCode.NoContent)
        }
        get("/contributions/{project}/{user}", {
            tags = listOf("contributions")
            description = "Get a contribution by project and user ID"
            request {
                pathParameter<Int>("project") { description = "The project ID" }
                pathParameter<Int>("user") { description = "The user ID" }
            }
            response {
                HttpStatusCode.OK to {
                    body<Contribution> {}
                }
                HttpStatusCode.NotFound to {
                    description = "Contribution not found"
                }
            }
        }) {
            val projectId = call.parameters["project"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val userId = call.parameters["user"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val contribution =
                service.read(
                    ContributionId(projectId, userId)
                ) ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(HttpStatusCode.OK, contribution)
        }
        get("/contributions", {
            tags = listOf("contributions")
            description = "Get a page of contributions"
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
                    body<List<Contribution>> {}
                }
            }
        }) {
            val (page, size) = call.getPageAndSize()
            val (sort, order) = call.getSortAndOrder()
            val user = call.parameters["user"]?.toIntOrNull()
            val project = call.parameters["project"]?.toIntOrNull()
            val projects = service.page(page, size, sort, order, user, project)
            call.respond(HttpStatusCode.OK, projects)
        }
    }
}