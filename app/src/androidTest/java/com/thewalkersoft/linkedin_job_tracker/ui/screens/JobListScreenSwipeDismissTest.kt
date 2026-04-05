package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class JobListScreenSwipeDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeLeft_requestsDelete_andRowRemainsVisibleAfterReset() {
        val deleteRequests = AtomicInteger(0)
        val companyName = "Swipe Test Company"
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cardDescription = context.getString(
            R.string.sem_job_card_status,
            companyName,
            JobStatus.SAVED.displayName()
        )
        val job = JobEntity(
            id = "swipe-test-id",
            companyName = companyName,
            jobUrl = "https://www.linkedin.com/jobs/view/swipe-test",
            jobDescription = "Test description",
            status = JobStatus.SAVED,
            createdAt = System.currentTimeMillis()
        )

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                SwipeToDismissBox(
                    job = job,
                    syncDotState = JobSyncDotState.RED,
                    onRequestDelete = { deleteRequests.incrementAndGet() },
                    onStatusChange = {}
                )
            }
        }

        composeRule.onNode(hasContentDescription(cardDescription)).performTouchInput { swipeLeft() }

        composeRule.waitUntil(timeoutMillis = 3_000) {
            deleteRequests.get() > 0
        }

        composeRule.onNode(hasContentDescription(cardDescription))
            .assert(hasContentDescription(cardDescription))
        assertEquals(1, deleteRequests.get())
    }
}

