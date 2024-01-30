package net.clynamic.common

import io.ktor.server.application.ApplicationCall
import org.jetbrains.exposed.sql.SortOrder

fun ApplicationCall.getPageAndSize(): Pair<Int?, Int?> {
    val page = this.parameters["page"]?.toIntOrNull()
    val size = this.parameters["size"]?.toIntOrNull()
    return Pair(page, size)
}

fun ApplicationCall.getSortAndOrder(): Pair<String?, SortOrder?> {
    val sort = this.parameters["sort"]
    val order = this.parameters["order"]?.let {
        SortOrder.valueOf(it.lowercase())
    }
    return Pair(sort, order)
}