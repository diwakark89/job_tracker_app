package com.thewalkersoft.linkedin_job_tracker.sync

import java.util.UUID

enum class OutboxOperationType {
    UPSERT,
    DELETE,
    SHARED_LINK
}

data class OutboxOperation(
    val key: String = UUID.randomUUID().toString(),
    val type: OutboxOperationType,
    val jobId: String? = null,
    val jobUrl: String,
    val sharedUrl: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

