package io.provenance.explorer.config.filters

import io.provenance.explorer.config.ExplorerAuthentication
import io.provenance.explorer.domain.logger
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

const val UUID_HEADER = "x-uuid"

class HeaderInterceptFilter : OncePerRequestFilter() {
    private val log = logger()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val xuuid = request.getHeader(UUID_HEADER)
        log.info("uuid: $xuuid request: ${request.requestURI}")

        if (!xuuid.isNullOrBlank()) {
            SecurityContextHolder.getContext().authentication = ExplorerAuthentication(UUID.fromString(xuuid))
        }
        filterChain.doFilter(request, response)
    }
}
