package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncFailureInfo
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncDashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingFailedJob_opensDetailsForThatJobId() {
        var clickedJobId: String? = null
        val failedJob = JobSyncFailureInfo(
            jobId = "failed-job-123",
            companyName = "Tap Test Co",
            jobTitle = "Android Engineer",
            jobUrl = "https://www.linkedin.com/jobs/view/123",
            reason = "pushJob: HTTP 500",
            updatedAt = 1_700_000_000_000
        )

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                SyncDashboardScreen(
                    cloudHealth = "Cloud: Healthy",
                    queueStatus = 1,
                    lastSyncTime = null,
                    failedJobs = listOf(failedJob),
                    isManualSyncRunning = false,
                    manualSyncUiState = JobViewModel.ManualSyncUiState(),
                    onNavigateBack = {},
                    onManualSyncClick = {},
                    onJobClick = { clickedJobId = it }
                )
            }
        }

        composeRule.onNodeWithText("Tap Test Co").performClick()

        composeRule.runOnIdle {
            assertEquals("failed-job-123", clickedJobId)
        }
    }
}

