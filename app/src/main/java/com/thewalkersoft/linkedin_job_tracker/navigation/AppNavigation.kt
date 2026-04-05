package com.thewalkersoft.linkedin_job_tracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncFailureInfo
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobDetailsMissingScreen
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobDetailsScreen
import com.thewalkersoft.linkedin_job_tracker.ui.screens.JobListScreen
import com.thewalkersoft.linkedin_job_tracker.ui.screens.SyncDashboardScreen
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    jobs: List<JobEntity>,
    allJobs: List<JobEntity>,
    searchQuery: String,
    statusFilter: JobStatus?,
    isScraping: Boolean,
    message: String?,
    cloudHealth: String,
    jobSyncStateById: Map<String, JobSyncDotState>,
    jobSyncFailureById: Map<String, JobSyncFailureInfo>,
    syncFailureJobs: List<JobSyncFailureInfo>,
    isManualSyncRunning: Boolean,
    manualSyncProgressLabel: String,
    queueStatus: Int = 0,
    lastSyncTime: Long? = null,
    manualSyncUiState: JobViewModel.ManualSyncUiState = JobViewModel.ManualSyncUiState(),
    pendingJobsByUrl: Map<String, Long> = emptyMap(),
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (JobStatus?) -> Unit,
    onStatusChange: (JobEntity, JobStatus) -> Unit,
    onDeleteJob: (String) -> Unit,
    onManualSyncClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onEditJob: (JobEntity, String, String, String, String) -> Unit,
    onRestoreJob: (JobEntity) -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
    // Diagnostics – debug-only; default no-ops keep non-debug callers unchanged
    diagnosticsStep: Int = 0,
    onRequestDiagnosticsReset: () -> Unit = {},
    onConfirmDiagnosticsStep1: () -> Unit = {},
    onConfirmCancelWorker: () -> Unit = {},
    onDeclineCancelWorker: () -> Unit = {},
    onDismissDiagnostics: () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Screen.JobList.route,
        modifier = modifier
    ) {
        // Job List Screen
        composable(Screen.JobList.route) {
            JobListScreen(
                jobs = jobs,
                allJobs = allJobs,
                searchQuery = searchQuery,
                statusFilter = statusFilter,
                isScraping = isScraping,
                message = message,
                cloudHealth = cloudHealth,
                jobSyncStateById = jobSyncStateById,
                isManualSyncRunning = isManualSyncRunning,
                manualSyncProgressLabel = manualSyncProgressLabel,
                pendingJobsByUrl = pendingJobsByUrl,
                queueStatus = queueStatus,
                lastSyncTime = lastSyncTime,
                onSearchQueryChange = onSearchQueryChange,
                onStatusFilterChange = onStatusFilterChange,
                onStatusChange = onStatusChange,
                onDeleteJob = onDeleteJob,
                onEditJob = onEditJob,
                onManualSyncClick = onManualSyncClick,
                onRestoreJob = onRestoreJob,
                onMessageShown = onMessageShown,
                diagnosticsStep = diagnosticsStep,
                onRequestDiagnosticsReset = onRequestDiagnosticsReset,
                onConfirmDiagnosticsStep1 = onConfirmDiagnosticsStep1,
                onConfirmCancelWorker = onConfirmCancelWorker,
                onDeclineCancelWorker = onDeclineCancelWorker,
                onDismissDiagnostics = onDismissDiagnostics,
                onJobClick = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                },
                onSyncDashboardClick = {
                    navController.navigate(Screen.SyncDashboard.route)
                }
            )
        }

        // Job Details Screen
        composable(
            route = Screen.JobDetails.route,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
            val job = allJobs.firstOrNull { it.id == jobId }

            if (job == null) {
                JobDetailsMissingScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            } else {
                JobDetailsScreen(
                    job = job,
                    syncFailure = jobSyncFailureById[job.id],
                    onNavigateBack = { navController.navigateUp() },
                    onStatusChange = { newStatus ->
                        onStatusChange(job, newStatus)
                    },
                    onOpenUrl = onOpenUrl,
                    onEdit = { companyName, jobUrl, jobTitle, jobDescription ->
                        onEditJob(job, companyName, jobUrl, jobTitle, jobDescription)
                    },
                    onDelete = {
                        onDeleteJob(job.id)
                    }
                )
            }
        }

        // Sync Dashboard Screen
        composable(Screen.SyncDashboard.route) {
            SyncDashboardScreen(
                cloudHealth = cloudHealth,
                queueStatus = queueStatus,
                lastSyncTime = lastSyncTime,
                failedJobs = syncFailureJobs,
                isManualSyncRunning = isManualSyncRunning,
                manualSyncUiState = manualSyncUiState,
                onNavigateBack = { navController.navigateUp() },
                onManualSyncClick = onManualSyncClick,
                onJobClick = { jobId ->
                    navController.navigate(Screen.JobDetails.createRoute(jobId))
                }
            )
        }
    }
}

@Preview
@Composable
fun AppNavigationPreview() {
    LinkedIn_Job_TrackerTheme {
        val sampleJobs = listOf(
            JobEntity(
                id = "sample-1",
                companyName = "Google",
                jobUrl = "https://careers.google.com",
                jobDescription = "Software Engineer",
                status = JobStatus.APPLIED,
                createdAt = System.currentTimeMillis()
            ),
            JobEntity(
                id = "sample-2",
                companyName = "Meta",
                jobUrl = "https://www.metacareers.com/",
                jobDescription = "Product Manager",
                status = JobStatus.SAVED,
                createdAt = System.currentTimeMillis()
            )
        )
        AppNavigation(
            navController = rememberNavController(),
            jobs = sampleJobs,
            allJobs = sampleJobs,
            searchQuery = "",
            statusFilter = null,
            isScraping = false,
            message = null,
            cloudHealth = "Offline",
            jobSyncStateById = emptyMap(),
            jobSyncFailureById = emptyMap(),
            syncFailureJobs = emptyList(),
            isManualSyncRunning = false,
            manualSyncProgressLabel = "",
            onSearchQueryChange = { _ -> },
            onStatusFilterChange = { _ -> },
            onStatusChange = { _, _ -> },
            onDeleteJob = { _ -> },
            onManualSyncClick = {},
            onOpenUrl = { _ -> },
            onEditJob = { _, _, _, _, _ -> },
            onRestoreJob = { _ -> },
            onMessageShown = {}
        )
    }
}
