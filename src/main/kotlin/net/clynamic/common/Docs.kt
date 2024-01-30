package net.clynamic.common

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.dsl.AuthScheme
import io.github.smiley4.ktorswaggerui.dsl.AuthType
import io.ktor.server.application.Application
import io.ktor.server.application.install

fun Application.configureSwagger() {
    install(SwaggerUI) {
        swagger {
            forwardRoot = true
        }
        info {
            title = "Tag 'em API"
            version = "1.0.0"
            description = "API for Tag 'em, the backend for TagMe!"
        }
        securityScheme("jwt") {
            type = AuthType.HTTP
            scheme = AuthScheme.BEARER
            bearerFormat = "JWT"
        }
    }
}
