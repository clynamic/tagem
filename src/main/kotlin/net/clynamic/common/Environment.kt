package net.clynamic.common

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory
import java.io.File

val ENVIRONMENT_KEY = AttributeKey<Dotenv>("environment")

fun Application.configureEnvironment() {
    attributes.put(ENVIRONMENT_KEY, dotenv)
}

val dotenv: Dotenv by lazy {
    loadEnvironment()
}

private fun loadEnvironment(): Dotenv {
    val logger = LoggerFactory.getLogger("Environment")

    val envFile = File(".env")
    if (envFile.exists()) {
        logger.info("The .env file exists")
        val envVars = envFile.readLines().mapNotNull { line ->
            line.split("=").takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()

        logger.info("Environment variables found:")
        envVars.forEach { (key, value) ->
            logger.info("$key=$value")
        }
    } else {
        logger.info("The .env file does not exist")
    }

    val dotenv = dotenv {
        ignoreIfMalformed = true
        ignoreIfMissing = true
    }

    if (dotenv.entries().isEmpty()) {
        logger.warn("No environment variables found")
    }

    return dotenv
}
