package net.clynamic.contributions

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.getPageAndSize
import net.clynamic.common.getSortAndOrder
import net.clynamic.common.id
import net.clynamic.users.UserRank
import net.clynamic.users.authorize
import org.jetbrains.exposed.sql.SortOrder

fun Application.configureContributionsRouting() {
    val service = ContributionsService(attributes[DATABASE_KEY])

    routing {
        get("/contributions/{id}", {
            tags = listOf("contributions")
            description = "Get a contribution by ID"
            request {
                pathParameter<Int>("id") { description = "The contribution ID" }
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
            val id = call.parameters.id
            val contribution = service.read(id)
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
        authenticate {
            authorize({
                rankedOrHigher(UserRank.Member)
            }) {
                post("/contributions", {
                    tags = listOf("contributions")
                    description = "Create a contribution"
                    securitySchemeName = "jwt"
                    request {
                        body<ContributionRequest> {
                            description = "New contribution properties"
                        }
                    }
                    response {
                        HttpStatusCode.Created to {
                            body<Int> {
                                description = "The new contribution ID"
                            }
                        }
                    }
                }) {
                    val contributionId = call.receive<ContributionRequest>()
                    val id = service.create(contributionId)
                    call.respond(HttpStatusCode.Created, id)
                }
            }
        }
    }
}