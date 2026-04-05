package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.BuildConfig
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.ui.components.EditJobDialog
import com.thewalkersoft.linkedin_job_tracker.ui.components.JobCard
import com.thewalkersoft.linkedin_job_tracker.ui.components.LoadingOverlay
import com.thewalkersoft.linkedin_job_tracker.ui.components.PendingJobCard
import com.thewalkersoft.linkedin_job_tracker.ui.components.SyncStatusBanner
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    jobs: List<JobEntity>,
    allJobs: List<JobEntity>,
    searchQuery: String,
    statusFilter: JobStatus?,
    isScraping: Boolean,
    message: String?,
    cloudHealth: String,
    jobSyncStateById: Map<String, JobSyncDotState>,
    isManualSyncRunning: Boolean,
    manualSyncProgressLabel: String,
    pendingJobsByUrl: Map<String, Long> = emptyMap(),
    queueStatus: Int = 0,
    lastSyncTime: Long? = null,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (JobStatus?) -> Unit,
    onStatusChange: (JobEntity, JobStatus) -> Unit,
    onDeleteJob: (String) -> Unit,
    onEditJob: (JobEntity, String, String, String, String) -> Unit,
    onManualSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRestoreJob: (JobEntity) -> Unit = {},
    onMessageShown: () -> Unit = {},
    onJobClick: (String) -> Unit = {},
    onSyncDashboardClick: () -> Unit = {},
    // Diagnostics – debug-only; default no-ops keep previews unchanged
    diagnosticsStep: Int = 0,
    onRequestDiagnosticsReset: () -> Unit = {},
    onConfirmDiagnosticsStep1: () -> Unit = {},
    onConfirmCancelWorker: () -> Unit = {},
    onDeclineCancelWorker: () -> Unit = {},
    onDismissDiagnostics: () -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var isStatusMenuOpen by remember { mutableStateOf(false) }
    var pendingDeleteJob by remember { mutableStateOf<JobEntity?>(null) }
    var editingJob by remember { mutableStateOf<JobEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val statusOptions = remember { listOf<JobStatus?>(null) + JobStatus.entries }
    val listState = rememberLazyListState()
    val previousJobCount = remember { mutableStateOf(jobs.size) }

    // Calculate counts for each status
    val statusCounts = remember(allJobs) {
        val counts = mutableMapOf<JobStatus, Int>()
        allJobs.forEach { job ->
            counts[job.status] = (counts[job.status] ?: 0) + 1
        }
        counts
    }
    val totalCount = allJobs.size

    // Scroll to top when a new job is added
    LaunchedEffect(jobs.size) {
        if (jobs.size > previousJobCount.value && jobs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        previousJobCount.value = jobs.size
    }

    // Show message when it's available
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearch = { isSearchActive = false },
                                expanded = isSearchActive,
                                onExpandedChange = { isSearchActive = it },
                                placeholder = {
                                    Text(
                                        text = if (statusFilter == null) {
                                            "Search by company name"
                                        } else {
                                            "Search in ${statusFilter.displayName().lowercase()}"
                                                .replaceFirstChar { it.uppercase() }
                                        },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                                trailingIcon = {
                                    // Filter Icon
                                    Box {
                                        IconButton(onClick = { isStatusMenuOpen = true }) {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = "Filter",
                                                tint = if (statusFilter != null) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = isStatusMenuOpen,
                                            onDismissRequest = { isStatusMenuOpen = false }
                                        ) {
                                            statusOptions.forEach { status ->
                                                val label = status?.displayName() ?: "All Statuses"
                                                val count = if (status == null) {
                                                    totalCount
                                                } else {
                                                    statusCounts[status] ?: 0
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(label)
                                                            Text(
                                                                text = "($count)",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        onStatusFilterChange(status)
                                                        isStatusMenuOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        },
                        expanded = isSearchActive,
                        onExpandedChange = { isSearchActive = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Search suggestions can be added here if needed
                    }
                    // Sync status banner
                    SyncStatusBanner(
                        cloudHealth = cloudHealth,
                        isManualSyncRunning = isManualSyncRunning,
                        queueStatus = queueStatus,
                        lastSyncTime = lastSyncTime,
                        onSyncClick = onManualSyncClick,
                        onSettingsClick = onSyncDashboardClick
                    )

                    // Debug-only diagnostics reset trigger
                    if (BuildConfig.DEBUG) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onRequestDiagnosticsReset) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Diagnostics Reset [DEBUG]",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            if (jobs.isEmpty() && searchQuery.isEmpty() && statusFilter == null) {
                EmptyState(modifier = Modifier.padding(paddingValues))
            } else if (jobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    val statusLabel = statusFilter?.displayName()
                    val messageText = if (statusLabel == null) {
                        "No jobs found for \"$searchQuery\""
                    } else {
                        "No jobs found for \"$searchQuery\" in $statusLabel"
                    }
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Pending jobs (sorted by timestamp, newest first)
                    items(
                        pendingJobsByUrl.toList().sortedByDescending { it.second }.size,
                        key = { idx -> "pending_$idx" }
                    ) { idx ->
                        val (url, timestamp) = pendingJobsByUrl.toList().sortedByDescending { it.second }[idx]
                        PendingJobCard(jobUrl = url, timestamp = timestamp)
                    }
                    
                    // Regular jobs
                    items(jobs, key = { it.id }) { job ->
                        SwipeToDismissBox(
                            job = job,
                            syncDotState = jobSyncStateById[job.id] ?: JobSyncDotState.RED,
                            onRequestDelete = { pendingDeleteJob = job },
                            onStatusChange = { status -> onStatusChange(job, status) },
                            onJobClick = { onJobClick(job.id) }
                        )
                    }
                }
            }
        }

        if (pendingDeleteJob != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteJob = null },
                title = { Text("Delete job?") },
                text = { Text("This will remove the job from your tracker.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val job = pendingDeleteJob
                            if (job != null) {
                                onDeleteJob(job.id)
                                coroutineScope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Job deleted",
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestoreJob(job)
                                    }
                                }
                            }
                            pendingDeleteJob = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteJob = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Job Dialog
        editingJob?.let { job ->
            EditJobDialog(
                job = job,
                onDismiss = { editingJob = null },
                onSave = { companyName, jobUrl, jobTitle, jobDescription ->
                    onEditJob(job, companyName, jobUrl, jobTitle, jobDescription)
                    editingJob = null
                }
            )
        }

        LoadingOverlay(isLoading = isScraping)

        // Diagnostics reset (debug-only; shown only when BuildConfig.DEBUG and step > 0)
        if (BuildConfig.DEBUG && diagnosticsStep > 0) {
            DiagnosticsResetDialog(
                step = diagnosticsStep,
                onConfirmStep1 = onConfirmDiagnosticsStep1,
                onConfirmCancelWorker = onConfirmCancelWorker,
                onDeclineCancelWorker = onDeclineCancelWorker,
                onDismiss = onDismissDiagnostics
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissBox(
    job: JobEntity,
    syncDotState: JobSyncDotState,
    onRequestDelete: () -> Unit,
    onStatusChange: (JobStatus) -> Unit,
    modifier: Modifier = Modifier,
    onJobClick: () -> Unit = {},
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onRequestDelete()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        JobCard(
            job = job,
            onStatusChange = onStatusChange,
            syncDotState = syncDotState,
            onJobClick = onJobClick
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📋",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Jobs Tracked Yet",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Share a LinkedIn job posting to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
