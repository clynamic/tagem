package net.clynamic.projects

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.clynamic.common.DATABASE_KEY
import net.clynamic.common.Visibility
import net.clynamic.users.UserPrincipal
import net.clynamic.users.UserRank
import net.clynamic.users.UsersService

suspend fun ApplicationCall.privateProjects(): Visibility {
    val service = UsersService(application.attributes[DATABASE_KEY])
    val userId = principal<UserPrincipal>()?.id

    return if (userId != null) {
        val user = service.read(userId)
        if (user.rank >= UserRank.Janitor) {
            Visibility.All
        } else {
            Visibility.Only(userId)
        }
    } else {
        Visibility.None
    }
}

suspend fun ApplicationCall.deletedProjects(): Visibility {
    val service = UsersService(application.attributes[DATABASE_KEY])
    val userId = principal<UserPrincipal>()?.id

    return if (userId != null) {
        val user = service.read(userId)
        if (user.rank >= UserRank.Janitor) {
            Visibility.All
        } else {
            Visibility.None
        }
    } else {
        Visibility.None
    }
}