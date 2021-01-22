package io.provenance.explorer.web

import io.provenance.explorer.domain.TendermintApiException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler

open class BaseController {
    @ExceptionHandler(TendermintApiException::class)
    fun endpointExceptionHandler(ex: TendermintApiException): ResponseEntity<Any> =
            ResponseEntity<Any>(ex.message, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
}
