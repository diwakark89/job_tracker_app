package com.thewalkersoft.linkedin_job_tracker.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        Log.d(SyncDiagnostics.TAG, "[outbox][start] operations=${operations.size}")
        operations.forEach { operation ->
            attempted++
            val redactedUrl = SyncDiagnostics.redactJobUrl(operation.jobUrl)
            Log.d(
                SyncDiagnostics.TAG,
                "[outbox][attempt] key=${operation.key} type=${operation.type} jobId=${operation.jobId ?: "n/a"} jobUrl=$redactedUrl"
            )

            val acknowledgedThisOp = when (operation.type) {
                OutboxOperationType.UPSERT -> {
                    val job = dao.getJobByUrl(operation.jobUrl)
                    if (job == null) {
                        val reason = SyncDiagnostics.buildFailureReason(
                            stage = "outboxUpsert",
                            detail = "Local job missing for queued replay"
                        )
                        preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                        Log.w(
                            SyncDiagnostics.TAG,
                            "[outbox][missingLocal] key=${operation.key} type=${operation.type} jobUrl=$redactedUrl reason=$reason"
                        )
                        false
                    } else {
                        val result = repository.pushJob(job)
                        if (result.success) {
                            preferences.clearLastSyncFailureReason(operation.jobUrl)
                            preferences.acknowledgeOperation(operation.key)
                            Log.d(
                                SyncDiagnostics.TAG,
                                "[outbox][acknowledged] key=${operation.key} type=${operation.type} jobId=${job.id} jobUrl=$redactedUrl"
                            )
                            true
                        } else {
                            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                                stage = "outboxUpsert",
                                detail = "Replay failed"
                            )
                            preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                            Log.w(
                                SyncDiagnostics.TAG,
                                "[outbox][failed] key=${operation.key} type=${operation.type} jobId=${job.id} jobUrl=$redactedUrl reason=$reason"
                            )
                            false
                        }
                    }
                }

                OutboxOperationType.DELETE -> {
                    val jobId = operation.jobId
                    if (jobId.isNullOrBlank()) {
                        val reason = SyncDiagnostics.buildFailureReason(
                            stage = "outboxDelete",
                            detail = "Queued delete is missing jobId"
                        )
                        preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                        Log.w(
                            SyncDiagnostics.TAG,
                            "[outbox][invalidDelete] key=${operation.key} type=${operation.type} jobUrl=$redactedUrl reason=$reason"
                        )
                        false
                    } else {
                        val result = repository.pushDelete(jobId, operation.jobUrl)
                        if (result.acknowledged) {
                            // Delete replay remains idempotent; NOT_FOUND is terminal success.
                            preferences.clearLastSyncFailureReason(operation.jobUrl)
                            preferences.acknowledgeOperation(operation.key)
                            Log.d(
                                SyncDiagnostics.TAG,
                                "[outbox][acknowledged] key=${operation.key} type=${operation.type} jobId=$jobId jobUrl=$redactedUrl state=${result.state}"
                            )
                            true
                        } else {
                            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                                stage = "outboxDelete",
                                detail = "Replay failed"
                            )
                            preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                            Log.w(
                                SyncDiagnostics.TAG,
                                "[outbox][failed] key=${operation.key} type=${operation.type} jobId=$jobId jobUrl=$redactedUrl reason=$reason"
                            )
                            false
                        }
                    }
                }

                OutboxOperationType.SHARED_LINK -> {
                    val shared = operation.sharedUrl
                    if (shared.isNullOrBlank()) {
                        val reason = SyncDiagnostics.buildFailureReason(
                            stage = "outboxSharedLink",
                            detail = "Queued shared link is empty"
                        )
                        preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                        Log.w(
                            SyncDiagnostics.TAG,
                            "[outbox][invalidSharedLink] key=${operation.key} type=${operation.type} jobUrl=$redactedUrl reason=$reason"
                        )
                        false
                    } else {
                        val result = repository.pushSharedLink(shared)
                        if (result.success) {
                            preferences.clearLastSyncFailureReason(operation.jobUrl)
                            preferences.acknowledgeOperation(operation.key)
                            Log.d(
                                SyncDiagnostics.TAG,
                                "[outbox][acknowledged] key=${operation.key} type=${operation.type} jobUrl=$redactedUrl"
                            )
                            true
                        } else {
                            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                                stage = "outboxSharedLink",
                                detail = "Replay failed"
                            )
                            preferences.saveLastSyncFailureReason(operation.jobUrl, reason)
                            Log.w(
                                SyncDiagnostics.TAG,
                                "[outbox][failed] key=${operation.key} type=${operation.type} jobUrl=$redactedUrl reason=$reason"
                            )
                            false
                        }
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
        pullResult.syncedJobUrls.forEach(preferences::clearLastSyncFailureReason)
        pullResult.failedJobReasons.forEach { (jobUrl, reason) ->
            preferences.saveLastSyncFailureReason(jobUrl, reason)
        }
        pullResult.summaryFailureReason?.let { reason ->
            Log.w(SyncDiagnostics.TAG, "[outbox][pullSummaryFailure] reason=$reason")
        }

        val pulledUpdates = pullResult.inserted + pullResult.updatedFromRemote + pullResult.uploaded
        val totalFailed = failed + pullResult.failedPush
        preferences.saveLastSyncFailedPushCount(totalFailed)
        if (pullResult.success && totalFailed == 0) {
            preferences.saveLastSyncTimeMillis(System.currentTimeMillis())
        }

        Log.d(
            SyncDiagnostics.TAG,
            "[outbox][complete] attempted=$attempted acknowledged=$acknowledged failed=$totalFailed pulledUpdates=$pulledUpdates"
        )

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

