package com.thewalkersoft.linkedin_job_tracker.data

import com.google.gson.annotations.SerializedName
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "jobs",
    indices = [Index(value = ["jobUrl"], unique = true)]
)
data class JobEntity(
    @PrimaryKey
    val id: String,
    val companyName: String,
    val jobUrl: String,
    val jobDescription: String,
    val jobTitle: String = "",
    val status: JobStatus = JobStatus.SAVED,
    val timestamp: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    @SerializedName("is_deleted")
    val isDeleted: Boolean = false,
    val matchScore: Int? = null,
    val language: String = "English",
    val prepNotes: String? = null,
    val sourcePlatform: String? = null,
    val filterReason: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEW,
    RESUME_REJECTED,
    INTERVIEW_REJECTED
}

fun JobStatus.displayName(): String {
    return when (this) {
        JobStatus.SAVED -> "Saved"
        JobStatus.APPLIED -> "Applied"
        JobStatus.INTERVIEW -> "Interview"
        JobStatus.RESUME_REJECTED -> "Resume-Rejected"
        JobStatus.INTERVIEW_REJECTED -> "Interview-Rejected"
    }
}

fun parseJobStatus(value: String): JobStatus {
    val normalized = value.trim().uppercase().replace("-", "_").replace(" ", "_")
    return when (normalized) {
        "REJECTED" -> JobStatus.RESUME_REJECTED
        "INTERVIEWING" -> JobStatus.INTERVIEW
        "OFFER" -> JobStatus.INTERVIEW
        else -> runCatching { JobStatus.valueOf(normalized) }.getOrDefault(JobStatus.SAVED)
    }
}
