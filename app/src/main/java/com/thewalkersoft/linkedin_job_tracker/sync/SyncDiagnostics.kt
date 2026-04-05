package com.thewalkersoft.linkedin_job_tracker.sync

import java.net.URI

internal object SyncDiagnostics {
    const val TAG = "SyncTrace"
    private const val MAX_REASON_LENGTH = 180
    private val whitespaceRegex = Regex("\\s+")

    fun redactJobUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isBlank()) return "n/a"

        return runCatching {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val authority = uri.rawAuthority.orEmpty()
            val path = uri.rawPath.orEmpty()

            if (scheme.isBlank() || authority.isBlank()) {
                trimmed.substringBefore('?').substringBefore('#')
            } else {
                buildString {
                    append(scheme)
                    append("://")
                    append(authority)
                    append(path)
                }
            }
        }.getOrElse {
            trimmed.substringBefore('?').substringBefore('#')
        }
    }

    fun compactDetail(detail: String?): String? {
        val normalized = detail
            ?.replace(whitespaceRegex, " ")
            ?.trim()
            .orEmpty()
        return normalized.takeIf { it.isNotEmpty() }?.take(MAX_REASON_LENGTH)
    }

    fun buildFailureReason(stage: String, detail: String?, httpCode: Int? = null): String {
        val parts = buildList {
            add(stage.ifBlank { "sync" })
            if (httpCode != null) add("HTTP $httpCode")
            compactDetail(detail)?.let(::add)
        }
        return parts.joinToString(": ").take(MAX_REASON_LENGTH)
    }

    fun throwableDetail(throwable: Throwable): String {
        val type = throwable::class.java.simpleName.ifBlank { "Exception" }
        val message = compactDetail(throwable.message)
        return if (message != null) "$type: $message" else type
    }
}

