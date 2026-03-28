package com.thewalkersoft.linkedin_job_tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.workDataOf
import androidx.work.WorkerParameters
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.util.PreferencesManager

class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val dao = JobDatabase.getDatabase(applicationContext).jobDao()
        val preferences = PreferencesManager(applicationContext)
        val repository = SupabaseRepository(dao)


        var attempted = 0
        var acknowledged = 0
        var failed = 0

        preferences.compactOutbox()

        val operations = preferences.getOutboxOperations()
        operations.forEach { operation ->
            attempted++
            val acknowledgedThisOp = when (operation.type) {
                OutboxOperationType.UPSERT -> {
                    val job = dao.getJobByUrl(operation.jobUrl)
                    if (job != null && repository.pushJob(job)) {
                        preferences.acknowledgeOperation(operation.key)
                        true
                    } else {
                        false
                    }
                }

                OutboxOperationType.DELETE -> {
                    val result = operation.jobId?.let { repository.pushDelete(it) }
                    if (result == SupabaseRepository.DeletePushResult.SUCCESS || result == SupabaseRepository.DeletePushResult.NOT_FOUND) {
                        // Intent: keep backward compatibility for pre-tombstone queued DELETE operations.
                        // Tradeoff: legacy hard-delete replay can remove rows that now prefer tombstone semantics.
                        // Invariant: delete replay remains idempotent; NOT_FOUND is terminal success.
                        preferences.acknowledgeOperation(operation.key)
                        true
                    } else {
                        false
                    }
                }

                OutboxOperationType.SHARED_LINK -> {
                    val shared = operation.sharedUrl
                    if (!shared.isNullOrBlank() && repository.pushSharedLink(shared)) {
                        preferences.acknowledgeOperation(operation.key)
                        true
                    } else {
                        false
                    }
                }
            }

            if (acknowledgedThisOp) {
                acknowledged++
            } else {
                failed++
            }

            setProgress(
                workDataOf(
                    KEY_ATTEMPTED to attempted,
                    KEY_ACKNOWLEDGED to acknowledged,
                    KEY_FAILED to failed,
                    KEY_PULLED_UPDATES to 0
                )
            )
        }

        val pullResult = repository.pullCloudJobsToRoom()
        val pulledUpdates = pullResult.inserted + pullResult.updatedFromRemote + pullResult.uploaded
        val totalFailed = failed + pullResult.failedPush
        preferences.saveLastSyncFailedPushCount(totalFailed)
        if (pullResult.success && totalFailed == 0) {
            preferences.saveLastSyncTimeMillis(System.currentTimeMillis())
        }
        return Result.success(
            workDataOf(
                KEY_ATTEMPTED to attempted,
                KEY_ACKNOWLEDGED to acknowledged,
                KEY_FAILED to totalFailed,
                KEY_PULLED_UPDATES to pulledUpdates
            )
        )
    }

    companion object {
        const val KEY_ATTEMPTED = "attempted"
        const val KEY_ACKNOWLEDGED = "acknowledged"
        const val KEY_FAILED = "failed"
        const val KEY_PULLED_UPDATES = "pulled_updates"
    }
}

