package io.provenance.explorer.domain.exceptions

import kotlin.contracts.contract

inline fun requireToMessage(value: Boolean, lazyMessage: () -> String): String? {
    contract {
        returns() implies value
    }
    return if (!value) { lazyMessage() } else null
}

inline fun <T : Any> requireNotNullToMessage(value: T?, lazyMessage: () -> String): String? {
    contract {
        returns() implies (value != null)
    }
    return if (value == null) { lazyMessage() } else null
}

fun validate(vararg validations: String?) {
    validations.filterNotNull()
        .let {
            if (it.isNotEmpty()) {
                val msg = it.joinToString("; \n\t", "Validation Failures: \n\t")
                throw InvalidArgumentException(msg)
            }
        }
}
