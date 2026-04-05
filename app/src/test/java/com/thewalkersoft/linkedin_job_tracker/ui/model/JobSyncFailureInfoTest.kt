package com.thewalkersoft.linkedin_job_tracker.ui.model

import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.util.SyncFailureDiagnostic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobSyncFailureInfoTest {

    @Test
    fun buildJobSyncFailureInfoList_onlyIncludesJobsWithDiagnostics_sortedNewestFirst() {
        val olderJob = JobEntity(
            id = "1",
            companyName = "Older Co",
            jobUrl = "https://www.linkedin.com/jobs/view/1",
            jobDescription = "Older",
            jobTitle = "Backend Engineer"
        )
        val newerJob = JobEntity(
            id = "2",
            companyName = "Newer Co",
            jobUrl = "https://www.linkedin.com/jobs/view/2",
            jobDescription = "Newer",
            jobTitle = "Android Engineer"
        )
        val missingDiagnosticJob = JobEntity(
            id = "3",
            companyName = "Healthy Co",
            jobUrl = "https://www.linkedin.com/jobs/view/3",
            jobDescription = "Healthy",
            jobTitle = "QA Engineer"
        )

        val failures = buildJobSyncFailureInfoList(
            jobs = listOf(olderJob, newerJob, missingDiagnosticJob),
            diagnosticsByUrl = mapOf(
                olderJob.jobUrl to SyncFailureDiagnostic(
                    reason = "pushJob: HTTP 500: older failure",
                    updatedAt = 100L
                ),
                newerJob.jobUrl to SyncFailureDiagnostic(
                    reason = "pushJob: HTTP 409: newer failure",
                    updatedAt = 200L
                )
            )
        )

        assertEquals(2, failures.size)
        assertEquals("2", failures.first().jobId)
        assertEquals("pushJob: HTTP 409: newer failure", failures.first().reason)
        assertEquals("1", failures.last().jobId)
        assertTrue(failures.none { it.jobId == "3" })
    }
}

