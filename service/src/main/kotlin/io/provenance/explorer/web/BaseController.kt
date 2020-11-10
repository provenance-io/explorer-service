package io.provenance.explorer.web

import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

open class BaseController {
    fun user() = SecurityContextHolder.getContext().authentication.details as UUID
}