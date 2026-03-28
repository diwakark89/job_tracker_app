package com.thewalkersoft.linkedin_job_tracker.sync

import android.util.Log
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDao
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.service.SharedLinkRequest
import java.time.Instant

class SupabaseRepository(
    private val dao: JobDao
) {

    /**
     * Guardrail for cross-device clock skew. Outside this window, client lastModified wins.
     * Within this window, we prefer comparing server-managed updatedAt when available.
     */
    private val clockSkewToleranceMillis = 2 * 60 * 1000L

    data class PullResult(
        val success: Boolean,
        val inserted: Int = 0,
        val updatedFromRemote: Int = 0,
        val uploaded: Int = 0,
        val preservedLocal: Int = 0,
        val failedPush: Int = 0
    )

    fun isConfigured(): Boolean = SupabaseClient.isCloudConfigured()

    suspend fun pushJob(job: JobEntity): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val response = SupabaseClient.instance.upsertJob(listOf(job))
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                Log.w(
                    "SupabaseRepository",
                    "pushJob failed: code=${response.code()} body=$errorBody"
                )
            }
            response.isSuccessful
        }.getOrElse {
            Log.w("SupabaseRepository", "pushJob failed: ${it.message}")
            false
        }
    }

    suspend fun pushDelete(jobId: String): DeletePushResult {
        if (!isConfigured()) return DeletePushResult.RETRY
        return runCatching {
            val response = SupabaseClient.instance.deleteJobById("eq.$jobId")
            when {
                response.isSuccessful -> DeletePushResult.SUCCESS
                response.code() == 404 -> DeletePushResult.NOT_FOUND
                else -> DeletePushResult.RETRY
            }
        }.getOrElse {
            Log.w("SupabaseRepository", "pushDelete failed: ${it.message}")
            DeletePushResult.RETRY
        }
    }

    suspend fun pushSharedLink(rawUrl: String): Boolean {
        if (!isConfigured()) return false
        return runCatching {
            val response = SupabaseClient.instance.insertSharedLink(
                listOf(SharedLinkRequest(url = rawUrl))
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                Log.w(
                    "SupabaseRepository",
                    "pushSharedLink failed: code=${response.code()} body=$errorBody"
                )
            }
            response.isSuccessful
        }.getOrElse {
            Log.w("SupabaseRepository", "pushSharedLink failed: ${it.message}")
            false
        }
    }

    suspend fun pullCloudJobsToRoom(): PullResult {
        if (!isConfigured()) return PullResult(success = false)
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

            remoteJobs.forEach { remoteJob ->
                val localJob = localJobsByUrl[remoteJob.jobUrl] ?: dao.getJobByUrl(remoteJob.jobUrl)
                when {
                    localJob == null -> {
                        // Intent: persist remote tombstones locally so future pulls/replays cannot resurrect rows.
                        // Tradeoff: tombstoned rows remain stored in Room and are hidden via DAO filters.
                        // Invariant: local absence of active rows must not imply "upload back" when remote is tombstoned.
                        dao.upsertJob(remoteJob)
                        inserted++
                    }

                    shouldReplaceLocalWithRemote(localJob, remoteJob) -> {
                        // Keep the local primary key for the same business record (jobUrl).
                        dao.upsertJob(remoteJob.copy(id = localJob.id))
                        updatedFromRemote++
                    }

                    else -> {
                        // Local record wins on ties; only push when local is confidently newer.
                        if (shouldPushLocalToRemote(localJob, remoteJob)) {
                            val response = SupabaseClient.instance.upsertJob(listOf(localJob))
                            if (response.isSuccessful) {
                                uploaded++
                            } else {
                                failedPush++
                                val errorBody = response.errorBody()?.string().orEmpty()
                                Log.w(
                                    "SupabaseRepository",
                                    "pullCloudJobsToRoom push(local newer) failed: code=${response.code()} body=$errorBody"
                                )
                            }
                        } else {
                            preservedLocal++
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
                    } else {
                        failedPush++
                        val errorBody = response.errorBody()?.string().orEmpty()
                        Log.w(
                            "SupabaseRepository",
                            "pullCloudJobsToRoom push(local missing remotely) failed: code=${response.code()} body=$errorBody"
                        )
                    }
                }
            }

            Log.d(
                "SupabaseRepository",
                "pullCloudJobsToRoom: inserted=$inserted updatedFromRemote=$updatedFromRemote uploaded=$uploaded preservedLocal=$preservedLocal failedPush=$failedPush"
            )
            PullResult(
                success = true,
                inserted = inserted,
                updatedFromRemote = updatedFromRemote,
                uploaded = uploaded,
                preservedLocal = preservedLocal,
                failedPush = failedPush
            )
        }.getOrElse {
            Log.w("SupabaseRepository", "pullCloudJobsToRoom failed: ${it.message}")
            PullResult(success = false)
        }
    }

    private fun shouldReplaceLocalWithRemote(localJob: JobEntity, remoteJob: JobEntity): Boolean {
        if (remoteJob.lastModified == localJob.lastModified) {
            // Intent: prefer cloud when timestamps tie to keep cross-device convergence deterministic.
            // Tradeoff: an unsynced local restore can be overwritten by the cloud row on exact tie.
            // Invariant: tie outcome is always remote-wins, independent of payload shape.
            return true
        }

        val delta = remoteJob.lastModified - localJob.lastModified
        if (delta > clockSkewToleranceMillis) return true
        if (delta < -clockSkewToleranceMillis) return false

        Log.d(
            "SupabaseRepository",
            "[SYNC-SKEW] Skew-window conflict(jobUrl=${localJob.jobUrl}): remote-lastModified=${remoteJob.lastModified}, local-lastModified=${localJob.lastModified}, delta=$delta"
        )

        val remoteUpdatedAt = parseIsoTimestampToMillis(remoteJob.updatedAt)
        val localUpdatedAt = parseIsoTimestampToMillis(localJob.updatedAt)
        if (remoteUpdatedAt != null && localUpdatedAt != null && remoteUpdatedAt != localUpdatedAt) {
            Log.d(
                "SupabaseRepository",
                "[SYNC-SKEW] Tie-break by updatedAt(jobUrl=${localJob.jobUrl}): remote-updatedAt=$remoteUpdatedAt, local-updatedAt=$localUpdatedAt, action=replace-local=${remoteUpdatedAt > localUpdatedAt}"
            )
            return remoteUpdatedAt > localUpdatedAt
        }

        Log.d(
            "SupabaseRepository",
            "[SYNC-SKEW] Tie-break fallback(jobUrl=${localJob.jobUrl}): insufficient server timestamps, action=keep-local"
        )

        // Preserve local when confidence is low (tie/near-tie and missing server timestamps).
        return false
    }

    private fun shouldPushLocalToRemote(localJob: JobEntity, remoteJob: JobEntity): Boolean {
        if (localJob.lastModified == remoteJob.lastModified) {
            // Intent: mirror the remote-wins tie policy by skipping local push on exact timestamp ties.
            // Tradeoff: local tie updates are deferred until they become strictly newer.
            // Invariant: tie handling never causes oscillation between push/pull in consecutive sync runs.
            return false
        }

        val delta = localJob.lastModified - remoteJob.lastModified
        if (delta > clockSkewToleranceMillis) return true
        if (delta < -clockSkewToleranceMillis) return false

        Log.d(
            "SupabaseRepository",
            "[SYNC-SKEW] Skew-window upload check(jobUrl=${localJob.jobUrl}): local-lastModified=${localJob.lastModified}, remote-lastModified=${remoteJob.lastModified}, delta=$delta"
        )

        val remoteUpdatedAt = parseIsoTimestampToMillis(remoteJob.updatedAt)
        val localUpdatedAt = parseIsoTimestampToMillis(localJob.updatedAt)
        if (remoteUpdatedAt != null && localUpdatedAt != null && remoteUpdatedAt != localUpdatedAt) {
            Log.d(
                "SupabaseRepository",
                "[SYNC-SKEW] Tie-break by updatedAt(jobUrl=${localJob.jobUrl}): local-updatedAt=$localUpdatedAt, remote-updatedAt=$remoteUpdatedAt, action=push-local=${localUpdatedAt > remoteUpdatedAt}"
            )
            return localUpdatedAt > remoteUpdatedAt
        }

        Log.d(
            "SupabaseRepository",
            "[SYNC-SKEW] Tie-break fallback(jobUrl=${localJob.jobUrl}): insufficient server timestamps, action=skip-push"
        )

        // Tie or uncertain ordering -> keep local as-is without forcing a push.
        return false
    }

    private fun parseIsoTimestampToMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    enum class DeletePushResult {
        SUCCESS,
        NOT_FOUND,
        RETRY
    }
}

