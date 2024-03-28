package net.clynamic.common

import io.ktor.server.application.ApplicationCall
import org.jetbrains.exposed.sql.SortOrder

/**
 * Applies the page options from the call to a page options object.
 */
fun <T : PageOptionsBase<T>> PageOptionsBase<T>.paged(call: ApplicationCall): T {
    return duplicate(
        page = call.parameters["page"]?.toIntOrNull(),
        size = call.parameters["size"]?.toIntOrNull(),
        sort = call.parameters["sort"],
        order = call.parameters["order"]?.let {
            try {
                SortOrder.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        },
    )
}