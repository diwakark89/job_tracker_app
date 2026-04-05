package com.thewalkersoft.linkedin_job_tracker.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.thewalkersoft.linkedin_job_tracker.client.SupabaseClient
import com.thewalkersoft.linkedin_job_tracker.data.JobDatabase
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.scraper.JobScraper
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperation
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxOperationType
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxWorkScheduler
import com.thewalkersoft.linkedin_job_tracker.sync.RealtimeConnectionState
import com.thewalkersoft.linkedin_job_tracker.sync.RealtimeJobEvent
import com.thewalkersoft.linkedin_job_tracker.sync.SupabaseRealtimeManager
import com.thewalkersoft.linkedin_job_tracker.sync.SupabaseRepository
import com.thewalkersoft.linkedin_job_tracker.sync.OutboxSyncWorker
import com.thewalkersoft.linkedin_job_tracker.sync.SyncDiagnostics
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncFailureInfo
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import com.thewalkersoft.linkedin_job_tracker.ui.model.buildJobSyncFailureInfoList
import com.thewalkersoft.linkedin_job_tracker.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class JobViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = JobDatabase.getDatabase(application).jobDao()
    private val repository = SupabaseRepository(dao)
    private val preferencesManager = PreferencesManager(application)
    private val realtimeManager = SupabaseRealtimeManager()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<JobStatus?>(null)
    val statusFilter: StateFlow<JobStatus?> = _statusFilter.asStateFlow()

    private val _isScraping = MutableStateFlow(false)
    val isScraping: StateFlow<Boolean> = _isScraping.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _cloudHealth = MutableStateFlow("Cloud: Offline")
    val cloudHealth: StateFlow<String> = _cloudHealth.asStateFlow()

    /** 0 = hidden, 1 = step-1 dialog, 2 = step-2 dialog. Debug-only. */
    private val _diagnosticsStep = MutableStateFlow(0)
    val diagnosticsStep: StateFlow<Int> = _diagnosticsStep.asStateFlow()

    data class ManualSyncUiState(
        val isRunning: Boolean = false,
        val attempted: Int = 0,
        val acknowledged: Int = 0,
        val failed: Int = 0,
        val pulledUpdates: Int = 0
    )

    private val _manualSyncUiState = MutableStateFlow(ManualSyncUiState())
    val manualSyncUiState: StateFlow<ManualSyncUiState> = _manualSyncUiState.asStateFlow()

    private val _jobSyncStateById = MutableStateFlow<Map<String, JobSyncDotState>>(emptyMap())
    val jobSyncStateById: StateFlow<Map<String, JobSyncDotState>> = _jobSyncStateById.asStateFlow()

    private val _jobSyncFailureById = MutableStateFlow<Map<String, JobSyncFailureInfo>>(emptyMap())
    val jobSyncFailureById: StateFlow<Map<String, JobSyncFailureInfo>> = _jobSyncFailureById.asStateFlow()

    private val _syncFailureJobs = MutableStateFlow<List<JobSyncFailureInfo>>(emptyList())
    val syncFailureJobs: StateFlow<List<JobSyncFailureInfo>> = _syncFailureJobs.asStateFlow()

    // Pending jobs: URL -> (timestamp, isProcessing)
    private val _pendingJobsByUrl = MutableStateFlow<Map<String, Long>>(emptyMap())
    val pendingJobsByUrl: StateFlow<Map<String, Long>> = _pendingJobsByUrl.asStateFlow()

    // Queue status: count of operations in outbox
    private val _queueStatus = MutableStateFlow(0)
    val queueStatus: StateFlow<Int> = _queueStatus.asStateFlow()

    // Last sync time
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private var loadingRefCount = 0
    private var manualSyncRequested = false
    private var handledManualWorkId: UUID? = null

    // All jobs without any filter (for calculating counts)
    val allJobs: StateFlow<List<JobEntity>> = dao.getAllJobs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val jobs: StateFlow<List<JobEntity>> = combine(
        _searchQuery,
        _statusFilter,
        allJobs
    ) { query, selectedStatus, allJobs ->
        val normalizedQuery = query.trim()
        allJobs.filter { job ->
            val matchesQuery = normalizedQuery.isBlank() ||
                job.companyName.contains(normalizedQuery, ignoreCase = true)
            val matchesStatus = selectedStatus == null || job.status == selectedStatus
            matchesQuery && matchesStatus
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onStatusFilterChange(status: JobStatus?) {
        _statusFilter.value = status
    }

    fun clearMessage() {
        _message.value = null
    }

    init {
        OutboxWorkScheduler.schedule(application)

        if (!repository.isConfigured()) {
            _cloudHealth.value = "Cloud: Not Configured"
            _message.value = "Supabase not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY."
        }

        // 1. Warm Room cache from cloud
        viewModelScope.launch {
            val pullResult = repository.pullCloudJobsToRoom()
            applyPullDiagnostics(pullResult)
            preferencesManager.saveLastSyncFailedPushCount(pullResult.failedPush)
            if (pullResult.success && pullResult.failedPush == 0) {
                preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
            }
            refreshPerJobSyncState()
            refreshCloudHealth()
        }

        // 2. Open WebSocket – UI recomposes on every INSERT/UPDATE/DELETE
        realtimeManager.connect()

        // 3. Mirror realtime connection state into cloudHealth banner
        viewModelScope.launch {
            realtimeManager.connectionState.collect { refreshCloudHealth() }
        }

        // 3b. Keep per-job sync indicator state current.
        viewModelScope.launch {
            allJobs.collect {
                refreshPerJobSyncState()
            }
        }

        // 4. Apply incoming job changes directly into Room
        viewModelScope.launch {
            realtimeManager.jobEvents.collect { processRealtimeEvent(it) }
        }

        // 5. Execute deferred diagnostics reset once active worker becomes idle
        viewModelScope.launch {
            WorkManager.getInstance(application)
                .getWorkInfosForUniqueWorkFlow(OutboxWorkScheduler.WORK_NAME)
                .collect { workInfos ->
                    val isActive = workInfos.any { it.state == WorkInfo.State.RUNNING }
                    if (!isActive && preferencesManager.isPendingDiagnosticsReset()) {
                        executeDiagnosticsResetNow()
                    }
                }
        }

        // 6. Observe one-shot sync work for manual progress and completion messaging.
        viewModelScope.launch {
            WorkManager.getInstance(application)
                .getWorkInfosForUniqueWorkFlow(OutboxWorkScheduler.IMMEDIATE_WORK_NAME)
                .collect { workInfos ->
                    val workInfo = workInfos.firstOrNull() ?: return@collect

                    val attempted = workInfo.progress.getInt(OutboxSyncWorker.KEY_ATTEMPTED, 0)
                    val acknowledged = workInfo.progress.getInt(OutboxSyncWorker.KEY_ACKNOWLEDGED, 0)
                    val failed = workInfo.progress.getInt(OutboxSyncWorker.KEY_FAILED, 0)
                    val pulledUpdates = workInfo.progress.getInt(OutboxSyncWorker.KEY_PULLED_UPDATES, 0)

                    if (manualSyncRequested) {
                        _manualSyncUiState.value = _manualSyncUiState.value.copy(
                            isRunning = workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING,
                            attempted = attempted,
                            acknowledged = acknowledged,
                            failed = failed,
                            pulledUpdates = pulledUpdates
                        )
                    }

                    if (manualSyncRequested && workInfo.state.isFinished && handledManualWorkId != workInfo.id) {
                        handledManualWorkId = workInfo.id

                        val output = workInfo.outputData
                        val outAttempted = output.getInt(OutboxSyncWorker.KEY_ATTEMPTED, attempted)
                        val outAcknowledged = output.getInt(OutboxSyncWorker.KEY_ACKNOWLEDGED, acknowledged)
                        val outFailed = output.getInt(OutboxSyncWorker.KEY_FAILED, failed)
                        val outPulled = output.getInt(OutboxSyncWorker.KEY_PULLED_UPDATES, pulledUpdates)
                        val totalSynced = outAcknowledged + outPulled

                        _manualSyncUiState.value = ManualSyncUiState(
                            isRunning = false,
                            attempted = outAttempted,
                            acknowledged = outAcknowledged,
                            failed = outFailed,
                            pulledUpdates = outPulled
                        )

                        _message.value = when (workInfo.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                if (outFailed == 0) {
                                    "Sync complete. Synced $totalSynced updates ($outAcknowledged outbox + $outPulled pull)."
                                } else {
                                    "Sync completed with issues: synced $totalSynced, failed $outFailed."
                                }
                            }

                            WorkInfo.State.CANCELLED -> {
                                "Sync cancelled."
                            }

                            WorkInfo.State.FAILED -> {
                                "Sync failed. Please retry."
                            }

                            else -> null
                        }

                        Log.d(
                            SyncDiagnostics.TAG,
                            "[manualSync][complete] state=${workInfo.state} attempted=$outAttempted acknowledged=$outAcknowledged failed=$outFailed pulledUpdates=$outPulled"
                        )

                        manualSyncRequested = false
                        endLoading()
                        refreshPerJobSyncState()
                        refreshCloudHealth()
                    }
                }
        }

        // 7. Monitor pending jobs: remove when corresponding job appears in allJobs
        viewModelScope.launch {
            combine(pendingJobsByUrl, allJobs) { pending, jobs ->
                pending to jobs
            }.collect { (pending, jobs) ->
                val jobsByUrl = jobs.associateBy { it.jobUrl }
                val stillPending = pending.filter { (url, _) ->
                    jobsByUrl[url] == null
                }
                if (stillPending != pending) {
                    _pendingJobsByUrl.value = stillPending
                    // Notify user about newly scraped jobs
                    val newlyScraped = pending.keys - stillPending.keys
                    newlyScraped.forEach { url ->
                        val job = jobsByUrl[url]
                        if (job != null) {
                            _message.value = "Job '${job.jobTitle}' from ${job.companyName} added!"
                        }
                    }
                }
            }
        }

        // 8. Update queue status periodically
        viewModelScope.launch {
            while (true) {
                _queueStatus.value = preferencesManager.getOutboxOperations().size
                _lastSyncTime.value = preferencesManager.getLastSyncTimeMillis()
                refreshSyncFailureDiagnostics()
                kotlinx.coroutines.delay(2000) // Update every 2 seconds
            }
        }

        // 9. Update cloud health periodically (includes queue info)
        viewModelScope.launch {
            while (true) {
                refreshCloudHealth()
                kotlinx.coroutines.delay(5000) // Update every 5 seconds
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeManager.disconnect()
    }

    // ── Realtime event processing ─────────────────────────────────────────────

    private suspend fun processRealtimeEvent(event: RealtimeJobEvent) {
        try {
            when (event) {
                is RealtimeJobEvent.Insert -> parseJob(event.record)?.let { job ->
                    if (job.isDeleted) {
                        // Intent: apply cloud tombstones immediately so deleted jobs disappear at once.
                        // Tradeoff: local row is physically removed, so restore needs a later upsert from user/cloud.
                        // Invariant: repeated tombstone events and later pulls must stay idempotent (no resurrection).
                        dao.deleteJob(job.id)
                    } else {
                        dao.upsertJob(job)
                    }
                }
                is RealtimeJobEvent.Update -> parseJob(event.record)?.let { job ->
                    // Intent: a remote is_deleted=true update is treated as an immediate local delete operation.
                    // Tradeoff: this is stricter than hide-only behavior and favors remote deletion consistency.
                    // Invariant: delete-by-id is idempotent; replayed updates remain safe.
                    if (job.isDeleted) dao.deleteJob(job.id) else dao.upsertJob(job)
                }
                is RealtimeJobEvent.Delete -> {
                    val id = event.oldRecord.get("id")?.asString
                    if (!id.isNullOrBlank()) dao.deleteJob(id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Realtime event processing error: ${e.message}")
        }
    }

    private fun parseJob(json: JsonObject): JobEntity? =
        runCatching {
            SupabaseClient.supabaseGson.fromJson(json, JobEntity::class.java)
        }.getOrElse {
            Log.w(TAG, "Failed to parse job from realtime payload: ${it.message}")
            null
        }

    fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            viewModelScope.launch {
                val linkedInJobUrl = extractLinkedInJobUrl(sharedText)
                if (linkedInJobUrl == null) {
                    _message.value = "No valid LinkedIn job link found in shared text."
                    return@launch
                }

                handleSharedLink(linkedInJobUrl)
                // Keep instant UX by scraping locally right away while cloud sync continues.
                scrapeAndSaveJob(linkedInJobUrl)
            }
        }
    }

    fun saveJob(job: JobEntity) {
        viewModelScope.launch {
            dao.upsertJob(job)
            queueOrPushUpsert(job)
            _message.value = "Saved locally"
        }
    }

    private suspend fun handleSharedLink(sharedJobUrl: String) {
        // Add to pending jobs
        _pendingJobsByUrl.value = _pendingJobsByUrl.value + (sharedJobUrl to System.currentTimeMillis())

        val result = repository.pushSharedLink(sharedJobUrl)
        if (!result.success) {
            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                stage = "handleSharedLink",
                detail = "Shared link push failed"
            )
            preferencesManager.saveLastSyncFailureReason(sharedJobUrl, reason)
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.SHARED_LINK,
                    jobUrl = sharedJobUrl,
                    sharedUrl = sharedJobUrl
                )
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[sharedLink][queued] jobUrl=${SyncDiagnostics.redactJobUrl(sharedJobUrl)} reason=$reason queueSize=${preferencesManager.getOutboxOperations().size}"
            )
            OutboxWorkScheduler.kick(getApplication())
        } else {
            preferencesManager.clearLastSyncFailureReason(sharedJobUrl)
            preferencesManager.saveLastSyncFailedPushCount(0)
            preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
            Log.d(
                SyncDiagnostics.TAG,
                "[sharedLink][synced] jobUrl=${SyncDiagnostics.redactJobUrl(sharedJobUrl)}"
            )
        }
        refreshCloudHealth()
        _message.value = "Processing LinkedIn job..."
    }

    fun scrapeAndSaveJob(url: String) {
        viewModelScope.launch {
            val normalizedUrl = normalizeLinkedInJobUrl(url)
            beginLoading()
            try {
                // Check if job already exists
                val existingJob = dao.getJobByUrl(normalizedUrl)
                if (existingJob != null) {
                    _message.value = "Job already saved! Current status: ${existingJob.status.displayName()}"
                    return@launch
                }

                // Scrape all job information at once
                val jobInfo = JobScraper.scrapeJobInfo(normalizedUrl)

                val job = JobEntity(
                    id = UUID.randomUUID().toString(),
                    companyName = jobInfo.companyName,
                    jobUrl = normalizedUrl,
                    jobDescription = jobInfo.description,
                    jobTitle = jobInfo.jobTitle,
                    status = JobStatus.SAVED
                )
                saveJob(job)
            } catch (e: Exception) {
                _message.value = "Failed to save job: ${e.message}"
            } finally {
                endLoading()
            }
        }
    }

    fun runManualSync() {
        if (_manualSyncUiState.value.isRunning) return
        Log.d(
            SyncDiagnostics.TAG,
            "[manualSync][start] queueSize=${preferencesManager.getOutboxOperations().size} lastSync=${preferencesManager.getLastSyncTimeMillis() ?: -1L}"
        )
        manualSyncRequested = true
        handledManualWorkId = null
        _manualSyncUiState.value = ManualSyncUiState(isRunning = true)
        beginLoading()
        OutboxWorkScheduler.kick(getApplication())
        refreshCloudHealth()
    }

    fun updateJobStatus(job: JobEntity, newStatus: JobStatus) {
        viewModelScope.launch {
            val updatedJob = job.copy(
                status = newStatus,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertJob(updatedJob)
            queueOrPushUpsert(updatedJob)
        }
    }

    fun updateJob(job: JobEntity, companyName: String, jobUrl: String, jobTitle: String, jobDescription: String) {
        viewModelScope.launch {
            val updatedJob = job.copy(
                companyName = companyName,
                jobUrl = jobUrl,
                jobTitle = jobTitle,
                jobDescription = jobDescription,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertJob(updatedJob)
            queueOrPushUpsert(updatedJob)
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            val job = dao.getAllJobsOnce().firstOrNull { it.id == jobId }
            if (job == null) {
                _message.value = "❌ Job not found"
                return@launch
            }
            val tombstonedJob = job.copy(
                isDeleted = true,
                updatedAt = System.currentTimeMillis()
            )
            dao.upsertJob(tombstonedJob)
            queueOrPushUpsert(tombstonedJob)
        }
    }

    fun restoreJob(job: JobEntity) {
        viewModelScope.launch {
            val restoredJob = job.copy(
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )
            saveJob(restoredJob)
        }
    }

    private suspend fun queueOrPushUpsert(job: JobEntity) {
        val result = repository.pushJob(job)
        if (!result.success) {
            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                stage = "queueOrPushUpsert",
                detail = "Direct upsert failed"
            )
            preferencesManager.saveLastSyncFailureReason(job.jobUrl, reason)
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.UPSERT,
                    jobId = job.id,
                    jobUrl = job.jobUrl,
                    lastModified = job.updatedAt
                )
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[directUpsert][queued] jobId=${job.id} jobUrl=${SyncDiagnostics.redactJobUrl(job.jobUrl)} reason=$reason queueSize=${preferencesManager.getOutboxOperations().size}"
            )
            OutboxWorkScheduler.kick(getApplication())
        } else {
            preferencesManager.clearLastSyncFailureReason(job.jobUrl)
            preferencesManager.saveLastSyncFailedPushCount(0)
            preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
            Log.d(
                SyncDiagnostics.TAG,
                "[directUpsert][synced] jobId=${job.id} jobUrl=${SyncDiagnostics.redactJobUrl(job.jobUrl)}"
            )
        }
        refreshPerJobSyncState()
        refreshCloudHealth()
    }

    private suspend fun queueOrPushDelete(job: JobEntity) {
        val result = repository.pushDelete(job.id, job.jobUrl)
        if (!result.acknowledged) {
            val reason = result.failureReason ?: SyncDiagnostics.buildFailureReason(
                stage = "queueOrPushDelete",
                detail = "Direct delete failed"
            )
            preferencesManager.saveLastSyncFailureReason(job.jobUrl, reason)
            preferencesManager.enqueueOperation(
                OutboxOperation(
                    type = OutboxOperationType.DELETE,
                    jobId = job.id,
                    jobUrl = job.jobUrl,
                    lastModified = System.currentTimeMillis()
                )
            )
            Log.w(
                SyncDiagnostics.TAG,
                "[directDelete][queued] jobId=${job.id} jobUrl=${SyncDiagnostics.redactJobUrl(job.jobUrl)} reason=$reason queueSize=${preferencesManager.getOutboxOperations().size}"
            )
            OutboxWorkScheduler.kick(getApplication())
        } else {
            preferencesManager.clearLastSyncFailureReason(job.jobUrl)
            preferencesManager.saveLastSyncFailedPushCount(0)
            preferencesManager.saveLastSyncTimeMillis(System.currentTimeMillis())
            Log.d(
                SyncDiagnostics.TAG,
                "[directDelete][synced] jobId=${job.id} jobUrl=${SyncDiagnostics.redactJobUrl(job.jobUrl)} state=${result.state}"
            )
        }
        refreshPerJobSyncState()
        refreshCloudHealth()
    }

    private fun applyPullDiagnostics(pullResult: SupabaseRepository.PullResult) {
        pullResult.syncedJobUrls.forEach(preferencesManager::clearLastSyncFailureReason)
        pullResult.failedJobReasons.forEach { (jobUrl, reason) ->
            preferencesManager.saveLastSyncFailureReason(jobUrl, reason)
        }
        pullResult.summaryFailureReason?.let { reason ->
            Log.w(SyncDiagnostics.TAG, "[pull][summaryFailure] reason=$reason")
        }
    }

    private fun refreshPerJobSyncState() {
        val lastSyncMillis = preferencesManager.getLastSyncTimeMillis()
        val pendingByUrl = preferencesManager.getOutboxOperations()
            .filter { it.type != OutboxOperationType.SHARED_LINK }
            .map { it.jobUrl }
            .toSet()

        _jobSyncStateById.value = allJobs.value.associate { job ->
            val state = when {
                lastSyncMillis == null -> JobSyncDotState.RED
                pendingByUrl.contains(job.jobUrl) -> JobSyncDotState.YELLOW
                job.updatedAt >= lastSyncMillis -> JobSyncDotState.YELLOW
                else -> JobSyncDotState.GREEN
            }
            job.id to state
        }

        refreshSyncFailureDiagnostics()
    }

    private fun refreshSyncFailureDiagnostics() {
        val failures = buildJobSyncFailureInfoList(
            jobs = allJobs.value,
            diagnosticsByUrl = preferencesManager.getAllSyncFailureReasons()
        )
        _syncFailureJobs.value = failures
        _jobSyncFailureById.value = failures.associateBy { it.jobId }
    }

    private fun beginLoading() {
        loadingRefCount += 1
        _isScraping.value = true
    }

    private fun endLoading() {
        loadingRefCount = (loadingRefCount - 1).coerceAtLeast(0)
        _isScraping.value = loadingRefCount > 0
    }

    private fun refreshCloudHealth() {
        val state = realtimeManager.connectionState.value
        val queueSize = preferencesManager.getOutboxOperations().size
        val failedPush = preferencesManager.getLastSyncFailedPushCount()
        val (rQ, rC, rR) = preferencesManager.getRollingMetricsSummary()
        val lastMs = preferencesManager.getLastSyncTimeMillis()
        val lastLabel = if (lastMs != null)
            SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(lastMs))
        else "never"
        val stateLabel = when (state) {
            RealtimeConnectionState.CONNECTED    -> "Live ●"
            RealtimeConnectionState.CONNECTING   -> "Connecting…"
            RealtimeConnectionState.DISCONNECTED -> "Offline ○"
            RealtimeConnectionState.ERROR        -> "Error ⚠"
        }
        val syncLabel = when {
            failedPush > 0 -> "Failed($failedPush)"
            queueSize > 0 -> "Pending($queueSize)"
            state == RealtimeConnectionState.CONNECTED -> "Synced"
            else -> "Idle"
        }
        _cloudHealth.value =
            "Cloud: $stateLabel | Sync: $syncLabel | Queue: $queueSize | 60m q/c/r: $rQ/$rC/$rR | Last: $lastLabel"
    }

    // ── Diagnostics (debug-only) ──────────────────────────────────────────────

    fun requestDiagnosticsReset() { _diagnosticsStep.value = 1 }

    fun confirmResetQueue() {
        _diagnosticsStep.value = 0
        if (isSyncWorkerActive()) {
            _diagnosticsStep.value = 2
        } else {
            executeDiagnosticsResetNow()
        }
    }

    fun confirmCancelWorker() {
        _diagnosticsStep.value = 0
        WorkManager.getInstance(getApplication())
            .cancelUniqueWork(OutboxWorkScheduler.WORK_NAME)
        executeDiagnosticsResetNow()
        OutboxWorkScheduler.schedule(getApplication())
    }

    fun declineCancelWorker() {
        _diagnosticsStep.value = 0
        if (isSyncWorkerActive()) {
            // Persist deferred flag; WorkManager observer in init will trigger reset once idle
            preferencesManager.setPendingDiagnosticsReset(true)
        } else {
            executeDiagnosticsResetNow()
        }
    }

    fun dismissDiagnosticsReset() { _diagnosticsStep.value = 0 }

    private fun executeDiagnosticsResetNow() {
        preferencesManager.clearMetricsAndOutbox()
        preferencesManager.setPendingDiagnosticsReset(false)
        refreshCloudHealth()
        Log.d(TAG, "Diagnostics reset executed")
    }

    private fun isSyncWorkerActive(): Boolean =
        runCatching {
            WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWork(OutboxWorkScheduler.WORK_NAME)
                .get()
                .any { it.state == WorkInfo.State.RUNNING }
        }.getOrDefault(false)


    companion object {
        private const val TAG = "JobViewModel"
        private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    }

    private fun extractLinkedInJobUrl(sharedText: String): String? {
        val linkedInUrl = URL_REGEX.findAll(sharedText)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']', '"', '\'') }
            .firstOrNull { candidate ->
                candidate.contains("linkedin.com/jobs", ignoreCase = true)
            }
            ?: return null

        return normalizeLinkedInJobUrl(linkedInUrl)
    }

    private fun normalizeLinkedInJobUrl(rawUrl: String): String {
        val parsed = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return rawUrl
        val host = parsed.host?.lowercase(Locale.US) ?: return rawUrl
        if (!host.contains("linkedin.com")) return rawUrl

        val normalizedHost = if (host.startsWith("www.")) host else "www.$host"
        val normalizedPath = parsed.path?.trimEnd('/').orEmpty().ifBlank { "/" }

        return Uri.Builder()
            .scheme((parsed.scheme ?: "https").lowercase(Locale.US))
            .encodedAuthority(normalizedHost)
            .encodedPath(normalizedPath)
            .build()
            .toString()
    }
}
