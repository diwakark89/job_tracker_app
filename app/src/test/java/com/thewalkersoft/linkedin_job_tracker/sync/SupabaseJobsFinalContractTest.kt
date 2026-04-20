package com.thewalkersoft.linkedin_job_tracker.sync

import com.google.gson.JsonParser
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.parseJobStatus
import com.thewalkersoft.linkedin_job_tracker.service.JobFinalUpsertRequest
import com.thewalkersoft.linkedin_job_tracker.service.SharedLinkFallbackRequest
import com.thewalkersoft.linkedin_job_tracker.service.SupabaseApiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

class SupabaseJobsFinalContractTest {

    @Test
    fun supabaseApiService_targetsJobsFinalEndpoints() {
        val getJobsPath = SupabaseApiService::class.java.declaredMethods
            .first { it.name == "getJobs" }
            .getAnnotation(GET::class.java)
            ?.value
        val upsertPath = SupabaseApiService::class.java.declaredMethods
            .first { it.name == "upsertJob" }
            .getAnnotation(POST::class.java)
            ?.value
        val deletePath = SupabaseApiService::class.java.declaredMethods
            .first { it.name == "deleteJobById" }
            .getAnnotation(DELETE::class.java)
            ?.value
        val sharedLinksPath = SupabaseApiService::class.java.declaredMethods
            .first { it.name == "insertSharedLink" }
            .getAnnotation(POST::class.java)
            ?.value
        val sharedLinksFallbackPath = SupabaseApiService::class.java.declaredMethods
            .first { it.name == "insertSharedLinkWithoutStatus" }
            .getAnnotation(POST::class.java)
            ?.value

        assertEquals("rest/v1/jobs_final", getJobsPath)
        assertEquals("rest/v1/jobs_final", upsertPath)
        assertEquals("rest/v1/jobs_final", deletePath)
        assertEquals("rest/v1/shared_links", sharedLinksPath)
        assertEquals("rest/v1/shared_links", sharedLinksFallbackPath)
    }

    @Test
    fun parseJobStatus_mapsLegacyRejectedToResumeRejected() {
        assertEquals(JobStatus.RESUME_REJECTED, parseJobStatus("REJECTED"))
        assertEquals(JobStatus.RESUME_REJECTED, parseJobStatus("Resume-Rejected"))
    }

    @Test
    fun parseJobStatus_acceptsLegacyDisplayValues() {
        assertEquals(JobStatus.SAVED, parseJobStatus("Saved"))
        assertEquals(JobStatus.APPLIED, parseJobStatus("Applied"))
        assertEquals(JobStatus.INTERVIEWING, parseJobStatus("Interviewing"))
        assertEquals(JobStatus.INTERVIEW_REJECTED, parseJobStatus("Interview-Rejected"))
    }

    @Test
    fun parseJobStatus_mapsUnknownValuesToSaved() {
        assertEquals(JobStatus.SAVED, parseJobStatus("SCRAPED"))
        assertEquals(JobStatus.SAVED, parseJobStatus("ENRICHED"))
        assertEquals(JobStatus.SAVED, parseJobStatus("totally-unknown-status"))
    }

    @Test
    fun supabaseGson_deserializesUppercaseJobStatusFromJobsFinalPayload() {
        val payload =
            """
            {
              "id": "job-1",
              "company_name": "Acme",
              "job_url": "https://www.linkedin.com/jobs/view/1",
              "description": "Android role",
              "role_title": "Android Engineer",
              "job_status": "INTERVIEWING",
              "language": "English",
              "saved_at": "2026-04-05T10:00:00.000Z",
              "modified_at": "2026-04-05T10:05:00.000Z",
              "is_deleted": false,
              "match_score": 90
            }
            """.trimIndent()

        val job = SupabaseClient.supabaseGson.fromJson(payload, JobEntity::class.java)

        assertEquals(JobStatus.INTERVIEWING, job.status)
        assertEquals("Acme", job.companyName)
        assertEquals("job-1", job.id)
        assertEquals(90, job.matchScore)
    }

    @Test
    fun supabaseGson_deserializesLegacyStatusPayloadForCompatibility() {
        val payload =
            """
            {
              "id": "job-2",
              "company_name": "Legacy Co",
              "job_url": "https://www.linkedin.com/jobs/view/2",
              "description": "Legacy role",
              "role_title": "Kotlin Engineer",
              "status": "REJECTED",
              "language": "English",
              "saved_at": "2026-04-05T10:00:00.000Z",
              "modified_at": "2026-04-05T10:05:00.000Z",
              "is_deleted": false
            }
            """.trimIndent()

        val job = SupabaseClient.supabaseGson.fromJson(payload, JobEntity::class.java)

        assertEquals(JobStatus.RESUME_REJECTED, job.status)
    }

    @Test
    fun jobFinalUpsertRequest_serializesJobsFinalFields() {
        val job = JobEntity(
            id = "job-3",
            companyName = "Final Co",
            jobUrl = "https://www.linkedin.com/jobs/view/3",
            jobDescription = "Final description",
            jobTitle = "Backend Engineer",
            status = JobStatus.APPLIED,
            matchScore = 95,
            sourcePlatform = "LinkedIn"
        )

        val jsonObject = JsonParser.parseString(
            SupabaseClient.supabaseGson.toJson(JobFinalUpsertRequest.from(job))
        ).asJsonObject

        assertTrue(jsonObject.has("id"))
        assertEquals("job-3", jsonObject.get("id").asString)
        assertTrue(jsonObject.has("job_status"))
        assertEquals("APPLIED", jsonObject.get("job_status").asString)
        assertTrue(jsonObject.has("match_score"))
        assertEquals(95, jsonObject.get("match_score").asInt)
        assertTrue(jsonObject.has("saved_at"))
        assertTrue(jsonObject.has("source_platform"))
        assertEquals("LinkedIn", jsonObject.get("source_platform").asString)
        assertFalse(jsonObject.has("job_id"))
        assertFalse(jsonObject.has("status"))
        assertFalse(jsonObject.has("created_at"))
    }

    @Test
    fun sharedLinkFallbackRequest_omitsStatusField() {
        val jsonObject = JsonParser.parseString(
            SupabaseClient.supabaseGson.toJson(
                SharedLinkFallbackRequest(url = "https://www.linkedin.com/jobs/view/5")
            )
        ).asJsonObject

        assertTrue(jsonObject.has("url"))
        assertTrue(jsonObject.has("source"))
        assertFalse(jsonObject.has("status"))
    }
}


