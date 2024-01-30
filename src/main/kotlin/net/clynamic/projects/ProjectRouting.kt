package net.clynamic.projects

import io.github.smiley4.ktorswaggerui.dsl.delete
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.patch
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.put
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

fun Application.configureProjectsRouting() {
    val service = ProjectsService(attributes[DATABASE_KEY])

    routing {
        post("/projects", {
            tags = listOf("projects")
            description = "Create a project"
            request {
                body<ProjectRequest> {
                    description = "New project properties"
                }
            }
            response {
                HttpStatusCode.Created to {
                    body<Int> {
                        description = "The new project ID"
                    }
                }
            }
        }) {
            val request = call.receive<ProjectRequest>()
            val id = service.create(request)
            call.response.headers.append("Location", "/projects/${id}")
            call.respond(HttpStatusCode.Created, id)
        }
        get("/projects/{id}", {
            tags = listOf("projects")
            description = "Get a project by ID"
            request {
                pathParameter<Int>("id") { description = "The project ID" }
            }
            response {
                HttpStatusCode.OK to {
                    body<Project> {}
                }
                HttpStatusCode.NotFound to {
                    description = "Project not found"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val project = service.read(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, project)
        }
        get("/projects", {
            tags = listOf("projects")
            description = "Get a page of projects"
            request {
                queryParameter<Int?>("page") { description = "The page number" }
                queryParameter<Int?>("size") { description = "The page size" }
                queryParameter<String?>("sort") { description = "The sort field" }
                queryParameter<SortOrder?>("order") { description = "The sort order" }
                queryParameter<Int?>("user") { description = "User ID to filter by association" }
            }
            response {
                HttpStatusCode.OK to {
                    body<List<Project>> {}
                }
            }
        }) {
            val (page, size) = call.getPageAndSize()
            val (sort, order) = call.getSortAndOrder()
            val user = call.parameters["user"]?.toIntOrNull()
            val projects = service.page(page, size, sort, order, user)
            call.respond(HttpStatusCode.OK, projects)
        }
        put("/projects/{id}", {
            tags = listOf("projects")
            description = "Update a project by ID"
            request {
                pathParameter<Int>("id") { description = "The project ID" }
                body<ProjectUpdate> {
                    description = "New project properties"
                }
            }
            response {
                HttpStatusCode.NoContent to {
                    description = "Project updated"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest)

            val update = call.receive<ProjectUpdate>()
            service.update(id, update)
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/projects/{id}", {
            tags = listOf("projects")
            description = "Delete a project by ID"
            request {
                pathParameter<Int>("id") { description = "The project ID" }
            }
            response {
                HttpStatusCode.NoContent to {
                    description = "Project deleted"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            service.update(id, ProjectUpdate(isDeleted = true))
            call.respond(HttpStatusCode.NoContent)
        }
        patch("/projects/{id}/restore", {
            tags = listOf("projects")
            description = "Restore a project by ID"
            request {
                pathParameter<Int>("id") { description = "The project ID" }
            }
            response {
                HttpStatusCode.NoContent to {
                    description = "Project restored"
                }
            }
        }) {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest)

            service.update(id, ProjectUpdate(isDeleted = false))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}