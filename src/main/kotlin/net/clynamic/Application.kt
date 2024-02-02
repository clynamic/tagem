package net.clynamic

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import net.clynamic.comments.configureCommentsRouting
import net.clynamic.common.configureAuth
import net.clynamic.common.configureCors
import net.clynamic.common.configureDatabase
import net.clynamic.common.configureEnvironment
import net.clynamic.common.configureErrors
import net.clynamic.common.configureMonitoring
import net.clynamic.common.configureSerialization
import net.clynamic.common.configureSwagger
import net.clynamic.common.dotenv
import net.clynamic.contributions.configureContributionsRouting
import net.clynamic.interactions.configureInteractionsRouting
import net.clynamic.projects.configureProjectsRouting
import net.clynamic.users.configureUsersRouting

fun main() {
    val port = dotenv["PORT"]?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureEnvironment()
    configureDatabase()
    configureCors()
    configureAuth()
    configureSwagger()
    configureErrors()
    configureUsersRouting()
    configureProjectsRouting()
    configureCommentsRouting()
    configureContributionsRouting()
    configureInteractionsRouting()
}
