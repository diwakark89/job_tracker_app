package com.thewalkersoft.linkedin_job_tracker.service

import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseApiService {
    @GET("rest/v1/jobs")
    suspend fun getJobs(
        @Query("select") select: String = "*",
        @Query("order") order: String = "timestamp.desc"
    ): List<JobEntity>

    @POST("rest/v1/jobs")
    suspend fun upsertJob(
        @Body jobs: List<JobEntity>,
        @Query("on_conflict") onConflict: String = "jobUrl",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @DELETE("rest/v1/jobs")
    suspend fun deleteJobById(
        @Query("id") idEq: String
    ): Response<Unit>

    @POST("rest/v1/shared_links")
    suspend fun insertSharedLink(
        @Body links: List<SharedLinkRequest>,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>
}

data class SharedLinkRequest(
    val url: String,
    val source: String = "android-share-intent",
    val status: String = "Pending"
)

