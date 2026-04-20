package com.thewalkersoft.linkedin_job_tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.navigation.AppNavigation
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobListScreen
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: JobViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle shared intent
        viewModel.handleIntent(intent)

        setContent {
            LinkedIn_Job_TrackerTheme {
                val navController = rememberNavController()
                val jobs by viewModel.jobs.collectAsState()
                val allJobs by viewModel.allJobs.collectAsState()
                val searchQuery by viewModel.searchQuery.collectAsState()
                val statusFilter by viewModel.statusFilter.collectAsState()
                val isScraping by viewModel.isScraping.collectAsState()
                val message by viewModel.message.collectAsState()
                val cloudHealth by viewModel.cloudHealth.collectAsStateWithLifecycle()
                val diagnosticsStep by viewModel.diagnosticsStep.collectAsState()
                val manualSyncUiState by viewModel.manualSyncUiState.collectAsState()
                val jobSyncStateById by viewModel.jobSyncStateById.collectAsState()
                val jobSyncFailureById by viewModel.jobSyncFailureById.collectAsState()
                val syncFailureJobs by viewModel.syncFailureJobs.collectAsState()
                val pendingJobsByUrl by viewModel.pendingJobsByUrl.collectAsState()
                val queueStatus by viewModel.queueStatus.collectAsState()
                val lastSyncTime by viewModel.lastSyncTime.collectAsState()

                AppNavigation(
                    navController = navController,
                    jobs = jobs,
                    allJobs = allJobs,
                    searchQuery = searchQuery,
                    statusFilter = statusFilter,
                    isScraping = isScraping,
                    message = message,
                    cloudHealth = cloudHealth,
                    jobSyncStateById = jobSyncStateById,
                    jobSyncFailureById = jobSyncFailureById,
                    syncFailureJobs = syncFailureJobs,
                    isManualSyncRunning = manualSyncUiState.isRunning,
                    manualSyncProgressLabel = "Syncing: ${manualSyncUiState.acknowledged}/${manualSyncUiState.attempted} queued, failed ${manualSyncUiState.failed}",
                    queueStatus = queueStatus,
                    lastSyncTime = lastSyncTime,
                    manualSyncUiState = manualSyncUiState,
                    pendingJobsByUrl = pendingJobsByUrl,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onStatusFilterChange = viewModel::onStatusFilterChange,
                    onStatusChange = { job, status -> viewModel.updateJobStatus(job, status) },
                    onDeleteJob = viewModel::deleteJob,
                    onManualSyncClick = viewModel::runManualSync,
                    onOpenUrl = ::openUrl,
                    onEditJob = { job, companyName, jobUrl, jobTitle, jobDescription ->
                        viewModel.updateJob(job, companyName, jobUrl, jobTitle, jobDescription)
                    },
                    onSaveDetailsEdit = { job, companyName, jobTitle, jobDescription ->
                        viewModel.updateJobFromDetails(job, companyName, jobTitle, jobDescription)
                    },
                    onRestoreJob = viewModel::restoreJob,
                    onMessageShown = viewModel::clearMessage,
                    diagnosticsStep = diagnosticsStep,
                    onRequestDiagnosticsReset = viewModel::requestDiagnosticsReset,
                    onConfirmDiagnosticsStep1 = viewModel::confirmResetQueue,
                    onConfirmCancelWorker = viewModel::confirmCancelWorker,
                    onDeclineCancelWorker = viewModel::declineCancelWorker,
                    onDismissDiagnostics = viewModel::dismissDiagnosticsReset,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(intent)
    }
}

@Preview(showBackground = true)
@Composable
private fun JobListScreenPreview() {
    LinkedIn_Job_TrackerTheme {
        val sampleJobs = listOf(
            JobEntity(
                id = "preview-1",
                companyName = "Contoso",
                jobUrl = "https://www.linkedin.com/jobs/view/123456",
                jobDescription = "Sample job description for preview.",
                jobTitle = "Senior Software Engineer",
                status = JobStatus.INTERVIEW,
                createdAt = System.currentTimeMillis()
            ),
            JobEntity(
                id = "preview-2",
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654321",
                jobDescription = "Another sample description for preview.",
                jobTitle = "Full Stack Developer",
                status = JobStatus.SAVED,
                createdAt = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = "preview-3",
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654324",
                jobDescription = "Another sample description for preview.",
                jobTitle = "Product Manager",
                status = JobStatus.INTERVIEW,
                createdAt = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = "preview-4",
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654325",
                jobDescription = "Another sample description for preview.",
                jobTitle = "DevOps Engineer",
                status = JobStatus.APPLIED,
                createdAt = System.currentTimeMillis() - 86_400_000
            ),
            JobEntity(
                id = "preview-5",
                companyName = "Fabrikam",
                jobUrl = "https://www.linkedin.com/jobs/view/654321",
                jobDescription = "Another sample description for preview.",
                jobTitle = "Data Scientist",
                status = JobStatus.RESUME_REJECTED,
                createdAt = System.currentTimeMillis() - 86_400_000
            )
        )

        JobListScreen(
            jobs = sampleJobs,
            allJobs = sampleJobs,
            searchQuery = "",
            statusFilter = null,
            isScraping = false,
            message = null,
            cloudHealth = "Cloud: Offline",
            jobSyncStateById = emptyMap(),
            isManualSyncRunning = false,
            manualSyncProgressLabel = "",
            onSearchQueryChange = {},
            onStatusFilterChange = {},
            onStatusChange = { _, _ -> },
            onDeleteJob = {},
            onEditJob = { _, _, _, _, _ -> },
            onManualSyncClick = {},
            onRestoreJob = {},
            onMessageShown = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
