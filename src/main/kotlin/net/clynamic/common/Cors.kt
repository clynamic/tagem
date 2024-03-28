package net.clynamic.common

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory

fun Application.configureCors() {
    val logger = LoggerFactory.getLogger("Cors")

    val hostUrl = attributes[ENVIRONMENT_KEY].get("HOST_URL", null)
    val hostUrls = attributes[ENVIRONMENT_KEY].get("HOST_URLS", null)

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Head)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true

        if (hostUrl != null) {
            logger.info("Allowing requests from $hostUrl")
            allowHost(hostUrl, listOf("http", "https"))
        }
        hostUrls?.split(",")?.map { it.trim() }?.forEach { hostUrl ->
            if (hostUrl.isNotEmpty()) {
                logger.info("Allowing requests from $hostUrl")
                allowHost(hostUrl, listOf("http", "https"))
            }
        }

        allowOrigins {
            arrayOf(
                it.matches(Regex("^(https?://)?(localhost)(:\\d+)?(/.*)?$")),
                it.matches(Regex("^(https?://)?(127\\.\\d+\\.\\d+\\.\\d+)(:\\d+)?(/.*)?$")),
                it.matches(Regex("^(https?://)?(0:0:0:0:0:0:0:1)(:\\d+)?(/.*)?$")),
            ).any()
        }
        allowSameOrigin = true
    }
}
