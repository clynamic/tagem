package net.clynamic.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.response.respond
import okhttp3.Interceptor
import okhttp3.Response
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.io.IOException


class HttpErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            throw IOException("Http Request Failed: $response")
        }

        return response
    }
}

class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestWithUserAgent = originalRequest.newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(requestWithUserAgent)
    }
}

class ExposedExceptionHandler : (ApplicationCallPipeline) -> Unit {
    override fun invoke(pipeline: ApplicationCallPipeline) {
        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            try {
                proceed()
            } catch (e: ExposedSQLException) {
                handleException(call, e)
            }
        }
    }

    private suspend fun handleException(call: ApplicationCall, e: ExposedSQLException) {
        val (status, message) = when (e.sqlState) {
            "23505" -> HttpStatusCode.Conflict to "A conflict occurred due to a duplicate entry or constraint violation."
            "23503", "787" -> HttpStatusCode.BadRequest to "A bad request was made due to constraint violation or syntax error."
            "42000", "42804", "23514" -> HttpStatusCode.BadRequest to "A bad request was made."
            else -> HttpStatusCode.InternalServerError to "An internal server error occurred."
        }

        call.respond(status, message)
    }
}
