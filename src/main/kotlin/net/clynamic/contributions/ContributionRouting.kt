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
import net.clynamic.common.id
import net.clynamic.common.paged
import net.clynamic.users.UserRank
import net.clynamic.users.authorize
import org.jetbrains.exposed.sql.SortOrder

fun Application.configureContributionsRouting() {
    val service = ContributionsService(attributes[DATABASE_KEY])

    routing {
        get("/contributions/{id}", {
            tags = listOf("contributions")
            description = "Get a contribution by ID"
            operationId = "contribution"
            request {
                pathParameter<Int>("id") {
                    description = "The contribution ID"
                    required = true
                }
            }
            response {
                HttpStatusCode.OK to {
                    description = "The contribution"
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
            operationId = "contributions"
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
                    description = "The contributions"
                    body<ContributionPage> {}
                }
            }
        }) {
            val options = ContributionPageOptions().paged(call).duplicate(
                projectId = call.parameters["user"]?.toIntOrNull(),
                userId = call.parameters["project"]?.toIntOrNull(),
            )
            val projects = service.page(options)
            call.respond(HttpStatusCode.OK, projects)
        }
        authenticate {
            authorize({
                rankedOrHigher(UserRank.Member)
            }) {
                post("/contributions", {
                    tags = listOf("contributions")
                    description = "Create a contribution"
                    operationId = "createContribution"
                    securitySchemeName = "jwt"
                    request {
                        body<ContributionRequest> {
                            description = "New contribution properties"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.Created to {
                            description = "The new contribution ID"
                            body<Int> {}
                        }
                    }
                }) {
                    val request = call.receive<ContributionRequest>()
                    val id = service.create(request)
                    call.respond(HttpStatusCode.Created, id)
                }
            }
        }
    }
}