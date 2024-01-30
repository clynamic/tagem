package net.clynamic.common

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory

fun Application.configureCors() {
    val logger = LoggerFactory.getLogger("Cors")

    val hostUrl = attributes[ENVIRONMENT_KEY].get("HOST_URL", null)

    install(CORS) {
        hostUrl?.let {
            logger.info("Allowing requests from $hostUrl")
            allowHost(hostUrl, listOf("http", "https"))
        }
        allowSameOrigin = true
    }
}