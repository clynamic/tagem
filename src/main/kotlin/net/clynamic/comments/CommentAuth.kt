package net.clynamic.comments

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import net.clynamic.common.DATABASE_KEY
import net.clynamic.users.UserPrincipal
import net.clynamic.users.UserRank
import net.clynamic.users.UsersService

suspend fun ApplicationCall.hiddenComments(): HiddenComments {
    val service = UsersService(attributes[DATABASE_KEY])
    val userId = principal<UserPrincipal>()?.id

    return if (userId != null) {
        val user = service.read(userId)
        if (user != null && user.rank >= UserRank.Janitor) {
            HiddenComments.All
        } else {
            HiddenComments.Only(userId)
        }
    } else {
        HiddenComments.None
    }
}