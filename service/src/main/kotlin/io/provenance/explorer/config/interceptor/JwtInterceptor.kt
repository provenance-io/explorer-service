package io.provenance.explorer.config.interceptor

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.exceptions.InvalidJwtException
import io.provenance.explorer.domain.extensions.fromBase64
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.HandlerInterceptor

class JwtInterceptor : HandlerInterceptor {

    companion object {
        const val X_ADDRESS = "x-address"
        const val X_PUBLIC_KEY = "x-public-key"
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val jwt = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix("Bearer")?.trimStart()

        if (!jwt.isNullOrBlank()) {
            try {
                val authPayload = jwt.toAuthPayload()
                request.setAttribute(X_ADDRESS, authPayload.addr)
                request.setAttribute(X_PUBLIC_KEY, authPayload.sub)
            } catch (e: Exception) {
                throw InvalidJwtException("JWT token is invalid, permission denied")
            }
        }

        return super.preHandle(
            request,
            response,
            handler
        )
    }
}

fun String.toAuthPayload() = OBJECT_MAPPER.readValue(this.split(".")[1].fromBase64(), AuthPayload::class.java)

data class AuthPayload(
    val addr: String,
    val sub: String,
    val iss: String,
    val exp: Long,
    val iat: Long
)
