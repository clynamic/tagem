package net.clynamic.common

import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.sql.Database

val DATABASE_KEY = AttributeKey<Database>("database")

fun Application.configureDatabase() {
    val useDisk = attributes[ENVIRONMENT_KEY].get("USE_DISK", "false")
    val fileName = "tagem.db"

    if (useDisk.toBoolean()) {
        attributes.put(
            DATABASE_KEY,
            Database.connect(
                url = "jdbc:sqlite:${fileName}",
                driver = "org.sqlite.JDBC"
            )
        )
    } else {
        attributes.put(
            DATABASE_KEY,
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
                user = "root",
            )
        )
    }

    ExposedExceptionHandler().invoke(this)
}

