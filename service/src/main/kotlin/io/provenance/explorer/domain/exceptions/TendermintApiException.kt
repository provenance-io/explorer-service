package io.provenance.explorer.domain.exceptions

class TendermintApiException(message: String) : Exception(message)

class TendermintApiCustomException(val method: String, val httpMethod: String, val url: String, val status: Int, val body: String?) :
    Exception("$method: failed to $httpMethod $url: $status${body?.let { " body:$body" } ?: ""}")

class TendermintApiNotFoundException(message: String) : Exception(message)
