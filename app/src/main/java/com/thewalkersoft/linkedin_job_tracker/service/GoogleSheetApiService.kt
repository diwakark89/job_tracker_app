package com.thewalkersoft.linkedin_job_tracker.service

import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface GoogleSheetApiService {
    @POST("exec") // Upload new job
    suspend fun uploadJob(
        @Body job: JobEntity
    ): Response<GoogleSheetResponse>

    @POST("exec?action=updateJob") // Update existing job in sheet
    suspend fun updateJob(
        @Body job: JobEntity
    ): Response<GoogleSheetResponse>

    @POST("exec?action=deleteJob") // Delete job from sheet
    suspend fun deleteJob(
        @Body job: JobEntity
    ): Response<GoogleSheetResponse>

    @GET("exec") // Download all jobs
    suspend fun downloadJobs(): List<JobEntity>
}