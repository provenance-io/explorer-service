package io.provenance.explorer.config

import io.grpc.StatusRuntimeException
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.exceptions.InvalidJwtException
import io.provenance.explorer.domain.exceptions.TendermintApiException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import javax.servlet.http.HttpServletRequest

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalControllerAdvice : ResponseEntityExceptionHandler() {

    @ExceptionHandler(TendermintApiException::class)
    fun endpointExceptionHandler(ex: TendermintApiException): ResponseEntity<Any> =
        ResponseEntity<Any>(ex.message, HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR)

    @ExceptionHandler(StatusRuntimeException::class)
    fun grpcStatusException(ex: StatusRuntimeException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.info("404 on '${request.requestURI}' with error '${ex.message}'")
        return ResponseEntity<Any>(ex.message, HttpHeaders(), HttpStatus.NOT_FOUND)
    }

    /**
     * Catch InvalidJwtException, return 403
     */
    @ExceptionHandler(InvalidJwtException::class)
    fun handleInvalidJwt(ex: InvalidJwtException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.info("403 on '${request.requestURI}' with error '${ex.message}'")
        return ResponseEntity<Any>(ex.message, HttpHeaders(), HttpStatus.FORBIDDEN)
    }

    /**
     * Catch ResourceNotFoundException, return 404
     */
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(exception: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.info("404 on '${request.requestURI}' with error '${exception.message}'")
        return ResponseEntity.notFound().build()
    }

    /**
     * Catch IllegalArgumentException, return 404
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(exception: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.info("404 on '${request.requestURI}' with error '${exception.message}'")
        return ResponseEntity.notFound().build()
    }

    /**
     * Catch InvalidArgumentException, return 400
     */
    @ExceptionHandler(InvalidArgumentException::class)
    fun handleInvalidArg(exception: InvalidArgumentException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.info("400 on '${request.requestURI}' with error '${exception.message}'")
        return ResponseEntity<Any>(exception.message, HttpHeaders(), HttpStatus.BAD_REQUEST)
    }
}

open class ResourceNotFoundException(message: String? = "", cause: Throwable? = null) : RuntimeException(message, cause)
