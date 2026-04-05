package com.thewalkersoft.linkedin_job_tracker.ui.model

import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.util.SyncFailureDiagnostic

data class JobSyncFailureInfo(
    val jobId: String,
    val companyName: String,
    val jobTitle: String,
    val jobUrl: String,
    val reason: String,
    val updatedAt: Long
)

fun buildJobSyncFailureInfoList(
    jobs: List<JobEntity>,
    diagnosticsByUrl: Map<String, SyncFailureDiagnostic>
): List<JobSyncFailureInfo> {
    return jobs.mapNotNull { job ->
        diagnosticsByUrl[job.jobUrl]?.let { diagnostic ->
            JobSyncFailureInfo(
                jobId = job.id,
                companyName = job.companyName,
                jobTitle = job.jobTitle,
                jobUrl = job.jobUrl,
                reason = diagnostic.reason,
                updatedAt = diagnostic.updatedAt
            )
        }
    }.sortedByDescending { it.updatedAt }
}

