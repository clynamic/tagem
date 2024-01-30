package net.clynamic.users

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.clynamic.common.HttpErrorInterceptor
import net.clynamic.common.UserAgentInterceptor
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class UsersClient {
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpErrorInterceptor())
        .addInterceptor(UserAgentInterceptor("tagem/1.0.0 (binaryfloof)"))
        .build()

    private val mapper = jacksonObjectMapper()

    suspend fun authenticate(username: String, password: String): UserInfo {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(
                    HttpUrl.Builder().scheme("https").host("e621.net").addPathSegment("users")
                        .addPathSegment("$username.json")
                        .build()
                )
                .header(
                    io.ktor.http.HttpHeaders.Authorization,
                    "Basic ${"$username:$password".encodeBase64()}"
                )
                .build()

            val response = client.newCall(request).execute()
            return@withContext response.use {
                val body = response.body!!.string()

                val map = mapper.readValue<Map<String, Any>>(body)

                return@use UserInfo(
                    map["id"] as Int,
                    map["name"] as String,
                    map["post_update_count"] as Int
                )
            }
        }
    }
}