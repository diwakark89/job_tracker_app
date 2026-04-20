package com.thewalkersoft.linkedin_job_tracker.service

import com.google.gson.annotations.SerializedName
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

private const val JOBS_FINAL_PATH = "rest/v1/jobs_final"

interface SupabaseApiService {
    @GET(JOBS_FINAL_PATH)
    suspend fun getJobs(
        @Query("select") select: String = "*",
        @Query("order") order: String = "saved_at.desc"
    ): List<JobEntity>

    @POST(JOBS_FINAL_PATH)
    suspend fun upsertJob(
        @Body jobs: List<JobFinalUpsertRequest>,
        @Query("on_conflict") onConflict: String = "job_url",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE(JOBS_FINAL_PATH)
    suspend fun deleteJobById(
        @Query("id") jobIdEq: String
    ): Response<Unit>

    @POST("rest/v1/shared_links")
    suspend fun insertSharedLink(
        @Body links: List<SharedLinkRequest>,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    @POST("rest/v1/shared_links")
    suspend fun insertSharedLinkWithoutStatus(
        @Body links: List<SharedLinkFallbackRequest>,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>
}

data class SharedLinkRequest(
    val url: String,
    val source: String = "android-share-intent",
    val status: String = "Pending"
)

data class SharedLinkFallbackRequest(
    val url: String,
    val source: String = "android-share-intent"
)

data class JobFinalUpsertRequest(
    @SerializedName("id")
    val jobId: String,
    @SerializedName("company_name")
    val companyName: String,
    @SerializedName("job_url")
    val jobUrl: String,
    @SerializedName("description")
    val jobDescription: String,
    @SerializedName("role_title")
    val jobTitle: String,
    @SerializedName("job_status")
    val status: JobStatus,
    val language: String,
    @SerializedName("saved_at")
    val savedAt: Long,
    @SerializedName("modified_at")
    val updatedAt: Long,
    @SerializedName("is_deleted")
    val isDeleted: Boolean,
    @SerializedName("match_score")
    val matchScore: Int,
    @SerializedName("source_platform")
    val sourcePlatform: String,
    val tags: List<String>? = null
) {
    companion object {
        fun from(job: JobEntity): JobFinalUpsertRequest = JobFinalUpsertRequest(
            jobId = job.id,
            companyName = job.companyName,
            jobUrl = job.jobUrl,
            jobDescription = job.jobDescription,
            jobTitle = job.jobTitle,
            status = job.status,
            language = job.language,
            savedAt = job.createdAt,
            updatedAt = job.updatedAt,
            isDeleted = job.isDeleted,
            matchScore = job.matchScore ?: 90,
            sourcePlatform = job.sourcePlatform ?: "linkedin"
        )
    }
}

