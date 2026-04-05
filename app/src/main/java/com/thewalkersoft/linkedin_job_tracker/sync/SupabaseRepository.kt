package com.thewalkersoft.linkedin_job_tracker.sync

import android.util.Log
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDao
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.service.SharedLinkRequest

class SupabaseRepository(
    private val dao: JobDao
) {

    /**
     * Guardrail for cross-device clock skew. Outside this window, client updatedAt wins.
     * Within this window the ordering is uncertain; local is preserved as-is.
     */
    private val clockSkewToleranceMillis = 2 * 60 * 1000L

    data class PushResult(
        val success: Boolean,
        val failureReason: String? = null
    )

    data class DeletePushResult(
        val state: State,
        val failureReason: String? = null
    ) {
        enum class State {
            SUCCESS,
            NOT_FOUND,
            RETRY
        }

        val acknowledged: Boolean
            get() = state == State.SUCCESS || state == State.NOT_FOUND
    }

    data class PullResult(
        val success: Boolean,
        val inserted: Int = 0,
        val updatedFromRemote: Int = 0,
        val uploaded: Int = 0,
        val preservedLocal: Int = 0,
        val failedPush: Int = 0,
        val failedJobReasons: Map<String, String> = emptyMap(),
        val syncedJobUrls: Set<String> = emptySet(),
        val summaryFailureReason: String? = null
    )

    fun isConfigured(): Boolean = SupabaseClient.isCloudConfigured()

    suspend fun pushJob(job: JobEntity): PushResult {
        val redactedUrl = SyncDiagnostics.redactJobUrl(job.jobUrl)
        if (!isConfigured()) {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushJob",
                detail = "Supabase not configured"
            )
            Log.w(SyncDiagnostics.TAG, "[pushJob][skip] jobId=${job.id} jobUrl=$redactedUrl reason=$reason")
            return PushResult(success = false, failureReason = reason)
        }

        Log.d(SyncDiagnostics.TAG, "[pushJob][start] jobId=${job.id} jobUrl=$redactedUrl updatedAt=${job.updatedAt}")
        return runCatching {
            val response = SupabaseClient.instance.upsertJob(listOf(job))
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val reason = SyncDiagnostics.buildFailureReason(
                    stage = "pushJob",
                    detail = errorBody.ifBlank { "Supabase upsert failed" },
                    httpCode = response.code()
                )
                Log.w(
                    SyncDiagnostics.TAG,
                    "[pushJob][failure] jobId=${job.id} jobUrl=$redactedUrl code=${response.code()} reason=$reason"
                )
                PushResult(success = false, failureReason = reason)
            } else {
                Log.d(SyncDiagnostics.TAG, "[pushJob][success] jobId=${job.id} jobUrl=$redactedUrl")
                PushResult(success = true)
            }
        }.getOrElse {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushJob",
                detail = SyncDiagnostics.throwableDetail(it)
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[pushJob][exception] jobId=${job.id} jobUrl=$redactedUrl reason=$reason"
            )
            PushResult(success = false, failureReason = reason)
        }
    }

    suspend fun pushDelete(jobId: String, jobUrl: String? = null): DeletePushResult {
        val redactedUrl = SyncDiagnostics.redactJobUrl(jobUrl)
        if (!isConfigured()) {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushDelete",
                detail = "Supabase not configured"
            )
            Log.w(SyncDiagnostics.TAG, "[pushDelete][skip] jobId=$jobId jobUrl=$redactedUrl reason=$reason")
            return DeletePushResult(DeletePushResult.State.RETRY, failureReason = reason)
        }

        Log.d(SyncDiagnostics.TAG, "[pushDelete][start] jobId=$jobId jobUrl=$redactedUrl")
        return runCatching {
            val response = SupabaseClient.instance.deleteJobById("eq.$jobId")
            when {
                response.isSuccessful -> {
                    Log.d(SyncDiagnostics.TAG, "[pushDelete][success] jobId=$jobId jobUrl=$redactedUrl")
                    DeletePushResult(DeletePushResult.State.SUCCESS)
                }
                response.code() == 404 -> {
                    Log.d(SyncDiagnostics.TAG, "[pushDelete][notFound] jobId=$jobId jobUrl=$redactedUrl treatedAsSynced=true")
                    DeletePushResult(DeletePushResult.State.NOT_FOUND)
                }
                else -> {
                    val errorBody = response.errorBody()?.string().orEmpty()
                    val reason = SyncDiagnostics.buildFailureReason(
                        stage = "pushDelete",
                        detail = errorBody.ifBlank { "Supabase delete failed" },
                        httpCode = response.code()
                    )
                    Log.w(
                        SyncDiagnostics.TAG,
                        "[pushDelete][failure] jobId=$jobId jobUrl=$redactedUrl code=${response.code()} reason=$reason"
                    )
                    DeletePushResult(DeletePushResult.State.RETRY, failureReason = reason)
                }
            }
        }.getOrElse {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushDelete",
                detail = SyncDiagnostics.throwableDetail(it)
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[pushDelete][exception] jobId=$jobId jobUrl=$redactedUrl reason=$reason"
            )
            DeletePushResult(DeletePushResult.State.RETRY, failureReason = reason)
        }
    }

    suspend fun pushSharedLink(rawUrl: String): PushResult {
        val redactedUrl = SyncDiagnostics.redactJobUrl(rawUrl)
        if (!isConfigured()) {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushSharedLink",
                detail = "Supabase not configured"
            )
            Log.w(SyncDiagnostics.TAG, "[pushSharedLink][skip] jobUrl=$redactedUrl reason=$reason")
            return PushResult(success = false, failureReason = reason)
        }

        Log.d(SyncDiagnostics.TAG, "[pushSharedLink][start] jobUrl=$redactedUrl")
        return runCatching {
            val response = SupabaseClient.instance.insertSharedLink(
                listOf(SharedLinkRequest(url = rawUrl))
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                val reason = SyncDiagnostics.buildFailureReason(
                    stage = "pushSharedLink",
                    detail = errorBody.ifBlank { "Supabase shared link insert failed" },
                    httpCode = response.code()
                )
                Log.w(
                    SyncDiagnostics.TAG,
                    "[pushSharedLink][failure] jobUrl=$redactedUrl code=${response.code()} reason=$reason"
                )
                PushResult(success = false, failureReason = reason)
            } else {
                Log.d(SyncDiagnostics.TAG, "[pushSharedLink][success] jobUrl=$redactedUrl")
                PushResult(success = true)
            }
        }.getOrElse {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pushSharedLink",
                detail = SyncDiagnostics.throwableDetail(it)
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[pushSharedLink][exception] jobUrl=$redactedUrl reason=$reason"
            )
            PushResult(success = false, failureReason = reason)
        }
    }

    suspend fun pullCloudJobsToRoom(): PullResult {
        if (!isConfigured()) {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pullCloudJobsToRoom",
                detail = "Supabase not configured"
            )
            Log.w(SyncDiagnostics.TAG, "[pull][skip] reason=$reason")
            return PullResult(success = false, summaryFailureReason = reason)
        }
        return runCatching {
            val remoteJobs = SupabaseClient.instance.getJobs()
            val localJobs = dao.getAllJobsOnce()
            val remoteJobsByUrl = remoteJobs.associateBy { it.jobUrl }
            val localJobsByUrl = localJobs.associateBy { it.jobUrl }

            var inserted = 0
            var updatedFromRemote = 0
            var uploaded = 0
            var preservedLocal = 0
            var failedPush = 0
            val failedJobReasons = mutableMapOf<String, String>()
            val syncedJobUrls = mutableSetOf<String>()

            Log.d(
                SyncDiagnostics.TAG,
                "[pull][start] localCount=${localJobs.size} remoteCount=${remoteJobs.size}"
            )

            remoteJobs.forEach { remoteJob ->
                val localJob = localJobsByUrl[remoteJob.jobUrl] ?: dao.getJobByUrl(remoteJob.jobUrl)
                when {
                    localJob == null -> {
                        // Intent: persist remote tombstones locally so future pulls/replays cannot resurrect rows.
                        // Tradeoff: tombstoned rows remain stored in Room and are hidden via DAO filters.
                        // Invariant: local absence of active rows must not imply "upload back" when remote is tombstoned.
                        dao.upsertJob(remoteJob)
                        inserted++
                        syncedJobUrls += remoteJob.jobUrl
                        Log.d(
                            SyncDiagnostics.TAG,
                            "[pull][insertLocal] jobId=${remoteJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(remoteJob.jobUrl)}"
                        )
                    }

                    shouldReplaceLocalWithRemote(localJob, remoteJob) -> {
                        // Keep the local primary key for the same business record (jobUrl).
                        dao.upsertJob(remoteJob.copy(id = localJob.id))
                        updatedFromRemote++
                        syncedJobUrls += localJob.jobUrl
                        Log.d(
                            SyncDiagnostics.TAG,
                            "[pull][updateLocal] localJobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} remoteUpdatedAt=${remoteJob.updatedAt}"
                        )
                    }

                    else -> {
                        // Local record wins on ties; only push when local is confidently newer.
                        if (shouldPushLocalToRemote(localJob, remoteJob)) {
                            val response = SupabaseClient.instance.upsertJob(listOf(localJob))
                            if (response.isSuccessful) {
                                uploaded++
                                syncedJobUrls += localJob.jobUrl
                                Log.d(
                                    SyncDiagnostics.TAG,
                                    "[pull][uploadLocalWinner] jobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)}"
                                )
                            } else {
                                failedPush++
                                val errorBody = response.errorBody()?.string().orEmpty()
                                val reason = SyncDiagnostics.buildFailureReason(
                                    stage = "pullUploadLocalWinner",
                                    detail = errorBody.ifBlank { "Supabase upsert failed" },
                                    httpCode = response.code()
                                )
                                failedJobReasons[localJob.jobUrl] = reason
                                Log.w(
                                    SyncDiagnostics.TAG,
                                    "[pull][uploadLocalWinnerFailed] jobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} code=${response.code()} reason=$reason"
                                )
                            }
                        } else {
                            preservedLocal++
                            Log.d(
                                SyncDiagnostics.TAG,
                                "[pull][preserveLocal] jobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} localUpdatedAt=${localJob.updatedAt} remoteUpdatedAt=${remoteJob.updatedAt}"
                            )
                        }
                    }
                }
            }

            // Upload jobs that exist only in local storage.
            // Intent: upload only active jobs returned by DAO-visible list.
            // Tradeoff: local tombstones are not part of this loop and must sync via delete/tombstone push paths.
            // Invariant: pull reconciliation never re-uploads a locally deleted job as active.
            localJobs.forEach { localJob ->
                if (!remoteJobsByUrl.containsKey(localJob.jobUrl)) {
                    val response = SupabaseClient.instance.upsertJob(listOf(localJob))
                    if (response.isSuccessful) {
                        uploaded++
                        syncedJobUrls += localJob.jobUrl
                        Log.d(
                            SyncDiagnostics.TAG,
                            "[pull][uploadLocalMissingRemote] jobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)}"
                        )
                    } else {
                        failedPush++
                        val errorBody = response.errorBody()?.string().orEmpty()
                        val reason = SyncDiagnostics.buildFailureReason(
                            stage = "pullUploadLocalMissingRemote",
                            detail = errorBody.ifBlank { "Supabase upsert failed" },
                            httpCode = response.code()
                        )
                        failedJobReasons[localJob.jobUrl] = reason
                        Log.w(
                            SyncDiagnostics.TAG,
                            "[pull][uploadLocalMissingRemoteFailed] jobId=${localJob.id} jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} code=${response.code()} reason=$reason"
                        )
                    }
                }
            }

            Log.d(
                SyncDiagnostics.TAG,
                "[pull][complete] inserted=$inserted updatedFromRemote=$updatedFromRemote uploaded=$uploaded preservedLocal=$preservedLocal failedPush=$failedPush"
            )
            PullResult(
                success = true,
                inserted = inserted,
                updatedFromRemote = updatedFromRemote,
                uploaded = uploaded,
                preservedLocal = preservedLocal,
                failedPush = failedPush,
                failedJobReasons = failedJobReasons,
                syncedJobUrls = syncedJobUrls
            )
        }.getOrElse {
            val reason = SyncDiagnostics.buildFailureReason(
                stage = "pullCloudJobsToRoom",
                detail = SyncDiagnostics.throwableDetail(it)
            )
            Log.w(SyncDiagnostics.TAG, "[pull][exception] reason=$reason")
            PullResult(success = false, summaryFailureReason = reason)
        }
    }

    private fun shouldReplaceLocalWithRemote(localJob: JobEntity, remoteJob: JobEntity): Boolean {
        if (remoteJob.updatedAt == localJob.updatedAt) {
            // Intent: prefer cloud when timestamps tie to keep cross-device convergence deterministic.
            // Tradeoff: an unsynced local restore can be overwritten by the cloud row on exact tie.
            // Invariant: tie outcome is always remote-wins, independent of payload shape.
            return true
        }

        val delta = remoteJob.updatedAt - localJob.updatedAt
        if (delta > clockSkewToleranceMillis) return true
        if (delta < -clockSkewToleranceMillis) return false

        Log.d(
            SyncDiagnostics.TAG,
            "[pull][skewWindowRemoteCheck] jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} remoteUpdatedAt=${remoteJob.updatedAt} localUpdatedAt=${localJob.updatedAt} delta=$delta"
        )

        // Within skew window — updatedAt is the unified Long field; no secondary server timestamp
        // available for tie-break. Preserve local to avoid spurious overwrites.
        return false
    }

    private fun shouldPushLocalToRemote(localJob: JobEntity, remoteJob: JobEntity): Boolean {
        if (localJob.updatedAt == remoteJob.updatedAt) {
            // Intent: mirror the remote-wins tie policy by skipping local push on exact timestamp ties.
            // Tradeoff: local tie updates are deferred until they become strictly newer.
            // Invariant: tie handling never causes oscillation between push/pull in consecutive sync runs.
            return false
        }

        val delta = localJob.updatedAt - remoteJob.updatedAt
        if (delta > clockSkewToleranceMillis) return true
        if (delta < -clockSkewToleranceMillis) return false

        Log.d(
            SyncDiagnostics.TAG,
            "[pull][skewWindowUploadCheck] jobUrl=${SyncDiagnostics.redactJobUrl(localJob.jobUrl)} localUpdatedAt=${localJob.updatedAt} remoteUpdatedAt=${remoteJob.updatedAt} delta=$delta"
        )

        // Within skew window — no secondary server timestamp available, skip push to avoid oscillation.
        return false
    }
}

