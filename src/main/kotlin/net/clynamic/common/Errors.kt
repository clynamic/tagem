package net.clynamic.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureErrors() {
    install(StatusPages) {
        // Service errors when a record is not found
        exception<NoSuchRecordException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, cause.message ?: "Not found")
        }
    }
}