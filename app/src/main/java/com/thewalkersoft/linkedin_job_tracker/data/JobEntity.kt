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
    @SerializedName(value = "job_id", alternate = ["id"])
    val id: String,
    @SerializedName("company_name")
    val companyName: String,
    @SerializedName("job_url")
    val jobUrl: String,
    @SerializedName("description")
    val jobDescription: String,
    @SerializedName("role_title")
    val jobTitle: String = "",
    @SerializedName(value = "job_status", alternate = ["status"])
    val status: JobStatus = JobStatus.SAVED,
    @SerializedName(value = "saved_at", alternate = ["created_at"])
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("modified_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("is_deleted")
    val isDeleted: Boolean = false,
    @SerializedName("match_score")
    val matchScore: Int? = 90,
    val language: String = "English",
    @SerializedName("prep_notes")
    val prepNotes: String? = null,
    @SerializedName("source_platform")
    val sourcePlatform: String? = null,
    @SerializedName("filter_reason")
    val filterReason: String? = null
)

enum class JobStatus {
    SAVED,
    APPLIED,
    INTERVIEW,
    INTERVIEWING,
    OFFER,
    RESUME_REJECTED,
    INTERVIEW_REJECTED
}

fun JobStatus.displayName(): String {
    return when (this) {
        JobStatus.SAVED -> "Saved"
        JobStatus.APPLIED -> "Applied"
        JobStatus.INTERVIEW -> "Interview"
        JobStatus.INTERVIEWING -> "Interviewing"
        JobStatus.OFFER -> "Offer"
        JobStatus.RESUME_REJECTED -> "Resume-Rejected"
        JobStatus.INTERVIEW_REJECTED -> "Interview-Rejected"
    }
}

fun parseJobStatus(value: String): JobStatus {
    val normalized = value.trim().uppercase().replace("-", "_").replace(" ", "_")
    if (normalized == "REJECTED") return JobStatus.RESUME_REJECTED
    return runCatching { JobStatus.valueOf(normalized) }.getOrDefault(JobStatus.SAVED)
}
