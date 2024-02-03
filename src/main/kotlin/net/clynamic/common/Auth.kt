package net.clynamic.common

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.util.AttributeKey
import net.clynamic.users.UserPrincipal
import net.clynamic.users.UserRank
import java.io.File
import java.security.SecureRandom

val JWT_KEY = AttributeKey<String>("jwt-key")

fun generateSecretKey(): String {
    val random = SecureRandom()
    val bytes = ByteArray(32)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

fun getOrCreateSecretKey(filePath: String): String {
    val file = File(filePath)

    return if (file.exists() && file.isFile) {
        file.readText()
    } else {
        val newKey = generateSecretKey()
        file.writeText(newKey)
        newKey
    }
}

fun Application.configureAuth() {
    attributes.put(JWT_KEY, getOrCreateSecretKey("jwt.key"))
    val jwtKey = attributes[JWT_KEY]

    install(Authentication) {
        jwt {
            verifier(JWT.require(Algorithm.HMAC256(jwtKey)).build())
            validate {
                val id = it.payload.getClaim(UserPrincipal.USER_ID_CLAIM).asInt()
                val name = it.payload.getClaim(UserPrincipal.USER_NAME_CLAIM).asString()
                val rank = it.payload.getClaim(UserPrincipal.USER_RANK_CLAIM)
                    .asString().let { rank ->
                        UserRank.entries.find { el -> el.name == rank }
                    }

                if (id != null && name != null && rank != null) {
                    UserPrincipal(id, name, rank)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * How to handle resource visibility, e.g. deleted items, private items, etc.
 */
sealed class Visibility {
    /**
     * Only visible items
     */
    data object None : Visibility()

    /**
     * Visible items or hidden items belonging to a specific user
     */
    data class Only(val id: Int) : Visibility()

    /**
     * All items regardless of visibility
     */
    data object All : Visibility()
}

