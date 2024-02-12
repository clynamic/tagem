package net.clynamic.interactions

import io.github.smiley4.ktorswaggerui.dsl.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.getPageAndSize
import net.clynamic.common.getSortAndOrder
import net.clynamic.common.id
import net.clynamic.users.UserPrincipal
import net.clynamic.users.UserRank
import net.clynamic.users.authorize
import org.jetbrains.exposed.sql.SortOrder

fun Application.configureInteractionsRouting() {
    val service = InteractionsService(attributes[DATABASE_KEY])

    intercept(ApplicationCallPipeline.Call) {
        val endpoint = call.request.uri
        val origin = call.request.origin.remoteHost
        val userId = call.principal<UserPrincipal>()?.id
        proceed()
        val responseStatus = call.response.status()?.value ?: -1
        val request = InteractionRequest(
            endpoint = endpoint,
            origin = origin,
            userId = userId,
            response = responseStatus
        )
        service.create(request)
    }

    routing {
        authenticate {
            authorize({
                rankedOrHigher(UserRank.Admin)
            }) {
                get("/interactions/{id}", {
                    tags = listOf("interactions")
                    description = "Get an interaction by ID"
                    operationId = "interaction"
                    request {
                        pathParameter<Int>("id") {
                            description = "The interaction ID"
                            required = true
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "The interaction"
                            body<Interaction> {}
                        }
                        HttpStatusCode.NotFound to {
                            description = "Interaction not found"
                        }
                    }
                }) {
                    val id = call.parameters.id
                    val interaction = service.read(id)
                    call.respond(HttpStatusCode.OK, interaction)
                }
                get("/interactions", {
                    tags = listOf("interactions")
                    description = "Get a page of interactions"
                    operationId = "interactions"
                    request {
                        queryParameter<Int?>("page") { description = "The page number" }
                        queryParameter<Int?>("size") { description = "The page size" }
                        queryParameter<String?>("sort") { description = "The sort field" }
                        queryParameter<SortOrder?>("order") { description = "The sort order" }
                        queryParameter<String?>("endpoint") {
                            description = "Endpoint to filter by"
                        }
                        queryParameter<String?>("origin") { description = "Origin to filter by" }
                        queryParameter<Int?>("user") {
                            description = "User ID to filter by association"
                        }
                        queryParameter<Int?>("response") {
                            description = "Response status to filter by"
                        }
                    }
                    response {
                        HttpStatusCode.OK to {
                            description = "The page of interactions"
                            body<List<Interaction>> {}
                        }
                    }
                }) {
                    val (page, size) = call.getPageAndSize()
                    val (sort, order) = call.getSortAndOrder()
                    val endpoint = call.parameters["endpoint"]
                    val origin = call.parameters["origin"]
                    val user = call.parameters["user"]?.toIntOrNull()
                    val response = call.parameters["response"]?.toIntOrNull()
                    val interactions =
                        service.page(page, size, sort, order, endpoint, origin, user, response)
                    call.respond(HttpStatusCode.OK, interactions)
                }
            }
        }
    }
}