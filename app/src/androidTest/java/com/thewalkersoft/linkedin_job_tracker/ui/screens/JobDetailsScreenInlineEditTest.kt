package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.ui.theme.LinkedIn_Job_TrackerTheme
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class JobDetailsScreenInlineEditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancelWithDirtyDraft_showsDiscardDialog_andKeepEditingKeepsInlineMode() {
        var navigateBackCalls = 0

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                JobDetailsScreen(
                    job = sampleJob(),
                    onNavigateBack = { navigateBackCalls++ },
                    onStatusChange = {},
                    onOpenUrl = {},
                    onSaveEdit = { _, _, _ -> JobViewModel.JobEditSaveResult.Success() },
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Acme Corp").performTextReplacement("Acme Corp Updated")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()

        composeRule.onAllNodesWithText("Discard unsaved changes?").assertCountEquals(0)
        composeRule.onNodeWithText("Save").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Discard").performClick()

        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, navigateBackCalls)
        }
    }

    @Test
    fun backWithDirtyDraft_discardNavigatesBack() {
        var navigateBackCalls = 0

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                JobDetailsScreen(
                    job = sampleJob(),
                    onNavigateBack = { navigateBackCalls++ },
                    onStatusChange = {},
                    onOpenUrl = {},
                    onSaveEdit = { _, _, _ -> JobViewModel.JobEditSaveResult.Success() },
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Acme Corp").performTextReplacement("Acme Corp Updated")
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        composeRule.onNodeWithText("Discard").performClick()

        composeRule.runOnIdle {
            assertEquals(1, navigateBackCalls)
        }
    }

    @Test
    fun saveInFlight_disablesActions_andPreventsDoubleSubmit() {
        val saveCalls = AtomicInteger(0)
        val saveGate = CompletableDeferred<Unit>()

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                JobDetailsScreen(
                    job = sampleJob(),
                    onNavigateBack = {},
                    onStatusChange = {},
                    onOpenUrl = {},
                    onSaveEdit = { _, _, _ ->
                        saveCalls.incrementAndGet()
                        saveGate.await()
                        JobViewModel.JobEditSaveResult.Success()
                    },
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Acme Corp").performTextReplacement("Acme Corp Updated")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(1, saveCalls.get())
        }

        saveGate.complete(Unit)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, saveCalls.get())
        }
    }

    @Test
    fun saveSuccess_withMessage_showsSnackbar_andExitsEditMode() {
        val successMessage = "Saved locally; cloud sync queued."

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                JobDetailsScreen(
                    job = sampleJob(),
                    onNavigateBack = {},
                    onStatusChange = {},
                    onOpenUrl = {},
                    onSaveEdit = { _, _, _ -> JobViewModel.JobEditSaveResult.Success(successMessage) },
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Acme Corp").performTextReplacement("Acme Corp Updated")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText(successMessage).assertIsDisplayed()
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Edit").assertIsDisplayed()
    }

    @Test
    fun cancelInEditMode_withoutDirtyChanges_exitsEditMode_withoutDiscardDialog() {
        var navigateBackCalls = 0

        composeRule.setContent {
            LinkedIn_Job_TrackerTheme {
                JobDetailsScreen(
                    job = sampleJob(),
                    onNavigateBack = { navigateBackCalls++ },
                    onStatusChange = {},
                    onOpenUrl = {},
                    onSaveEdit = { _, _, _ -> JobViewModel.JobEditSaveResult.Success() },
                    onDelete = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Edit").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onAllNodesWithText("Discard unsaved changes?").assertCountEquals(0)
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Edit").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(0, navigateBackCalls)
        }
    }

    private fun sampleJob(): JobEntity = JobEntity(
        id = "details-inline-test-id",
        companyName = "Acme Corp",
        jobUrl = "https://www.linkedin.com/jobs/view/123456",
        jobDescription = "Sample description",
        jobTitle = "Android Engineer",
        status = JobStatus.SAVED,
        createdAt = 1_700_000_000_000L
    )
}

