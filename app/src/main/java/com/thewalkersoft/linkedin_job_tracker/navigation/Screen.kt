package com.thewalkersoft.linkedin_job_tracker.navigation

sealed class Screen(val route: String) {
    object JobList : Screen("job_list")
    object JobDetails : Screen("job_details/{jobId}") {
        fun createRoute(jobId: String) = "job_details/$jobId"
    }
    object SyncDashboard : Screen("sync_dashboard")
}

