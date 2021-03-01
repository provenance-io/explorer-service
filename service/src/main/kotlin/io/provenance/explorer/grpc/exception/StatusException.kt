package io.provenance.explorer.grpc.exception

import io.grpc.Status

enum class StatusException(
    private val status: Status,
    private val exceptions: List<Class<out Throwable>>
) {
    INVALID_ARGUMENT(Status.INVALID_ARGUMENT, listOf(IllegalArgumentException::class.java)),
    MALFORMED_STREAM(Status.INVALID_ARGUMENT, listOf(MalformedStreamException::class.java)),
    NOT_FOUND(Status.NOT_FOUND, listOf(NotFoundException::class.java)),
    ILLEGAL_STATE(Status.INTERNAL, listOf(IllegalStateException::class.java));

    companion object {
        val whiteListNonLoggable = setOf(Status.INVALID_ARGUMENT, Status.NOT_FOUND)

        val exceptionToStatus = values()
            .flatMap { statusException ->
                statusException.exceptions.map { it to statusException.status }
            }.toMap()

        fun getStatus(t: Throwable): Status {
            return exceptionToStatus[t.javaClass] ?: Status.UNKNOWN
        }
    }
}

class MalformedStreamException(msg: String) : Exception(msg)

class NotFoundException(message: String) : RuntimeException(message)
