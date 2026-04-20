package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncFailureInfo
import com.thewalkersoft.linkedin_job_tracker.ui.theme.*
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DiscardAction {
    STAY_ON_DETAILS,
    NAVIGATE_BACK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsScreen(
    job: JobEntity,
    syncFailure: JobSyncFailureInfo? = null,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onStatusChange: (JobStatus) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSaveEdit: suspend (String, String, String) -> JobViewModel.JobEditSaveResult,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    var isEditMode by rememberSaveable(job.id) { mutableStateOf(false) }
    var isSaving by rememberSaveable(job.id) { mutableStateOf(false) }
    var discardAction by remember { mutableStateOf(DiscardAction.STAY_ON_DETAILS) }

    var companyDraft by rememberSaveable(job.id) { mutableStateOf(job.companyName) }
    var titleDraft by rememberSaveable(job.id) { mutableStateOf(job.jobTitle) }
    var descriptionDraft by rememberSaveable(job.id) { mutableStateOf(job.jobDescription) }

    val isDirty = companyDraft != job.companyName || titleDraft != job.jobTitle || descriptionDraft != job.jobDescription

    val isDark = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val saveStateDescription = if (isSaving) {
        stringResource(R.string.sem_details_save_in_progress)
    } else {
        stringResource(R.string.sem_details_save_ready)
    }
    val readOnlyUrlStateDescription = stringResource(R.string.sem_details_url_read_only)

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            onMessageShown()
        }
    }

    // Keep drafts in sync with source data when not actively editing.
    LaunchedEffect(job.id, job.updatedAt, isEditMode) {
        if (!isEditMode) {
            companyDraft = job.companyName
            titleDraft = job.jobTitle
            descriptionDraft = job.jobDescription
        }
    }

    fun handleDiscardConfirmation(target: DiscardAction) {
        if (isSaving) return
        if (isDirty) {
            discardAction = target
            showDiscardDialog = true
        } else {
            isEditMode = false
            if (target == DiscardAction.NAVIGATE_BACK) {
                onNavigateBack()
            }
        }
    }

    BackHandler(enabled = isEditMode) {
        handleDiscardConfirmation(DiscardAction.NAVIGATE_BACK)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Job Details", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                handleDiscardConfirmation(DiscardAction.NAVIGATE_BACK)
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(
                            enabled = !isSaving,
                            onClick = { handleDiscardConfirmation(DiscardAction.STAY_ON_DETAILS) }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            enabled = !isSaving && companyDraft.isNotBlank(),
                            modifier = Modifier.semantics { stateDescription = saveStateDescription },
                            onClick = {
                                if (isSaving) return@Button

                                val companyToSave = companyDraft.trim()
                                val titleToSave = titleDraft.trim()
                                val descriptionToSave = descriptionDraft

                                isSaving = true
                                coroutineScope.launch {
                                    when (val result = onSaveEdit(companyToSave, titleToSave, descriptionToSave)) {
                                        is JobViewModel.JobEditSaveResult.Success -> {
                                            result.message?.let {
                                                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
                                            }
                                            isEditMode = false
                                            companyDraft = companyToSave
                                            titleDraft = titleToSave
                                            descriptionDraft = descriptionToSave
                                        }

                                        is JobViewModel.JobEditSaveResult.Failure -> {
                                            snackbarHostState.showSnackbar(message = result.message, duration = SnackbarDuration.Short)
                                        }
                                    }
                                    isSaving = false
                                }
                            }
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Save")
                        }
                    } else {
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.cd_edit)
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_delete)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card with company and job info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) UiSubtleCardDark else UiSubtleCardLight
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = companyDraft,
                            onValueChange = { companyDraft = it },
                            label = { Text("Company") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = companyDraft.isBlank(),
                            supportingText = {
                                if (companyDraft.isBlank()) {
                                    Text("Company name cannot be empty")
                                }
                            }
                        )
                    } else {
                        Text(
                            text = job.companyName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isEditMode) {
                        OutlinedTextField(
                            value = titleDraft,
                            onValueChange = { titleDraft = it },
                            label = { Text("Job Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else if (job.jobTitle.isNotBlank()) {
                        Text(
                            text = job.jobTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = formatTimestamp(job.createdAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box {
                    StatusChipLarge(
                        status = job.status,
                        onClick = { showStatusMenu = true }
                    )
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        JobStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.displayName()) },
                                onClick = {
                                    onStatusChange(status)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                }
            }

            if (syncFailure != null) {
                LastSyncIssueCard(syncFailure = syncFailure)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LinkedIn URL",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isEditMode) {
                    OutlinedTextField(
                        value = job.jobUrl,
                        onValueChange = {},
                        label = { Text("LinkedIn URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                stateDescription = readOnlyUrlStateDescription
                            },
                        readOnly = true,
                        singleLine = true
                    )
                    TextButton(
                        onClick = { onOpenUrl(job.jobUrl) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.cd_open_link)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Browser")
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(job.jobUrl) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Open in Browser",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.cd_open_link),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Description",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) UiSubtleCardDark else UiSubtleCardLight
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = descriptionDraft,
                            onValueChange = { descriptionDraft = it },
                            label = { Text("Description") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .heightIn(min = 180.dp),
                            minLines = 6
                        )
                    } else {
                        Text(
                            text = job.jobDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Job?") },
            text = { Text("This will remove the job from your tracker. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        onNavigateBack()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) showDiscardDialog = false
            },
            title = { Text("Discard changes?") },
            text = { Text("Discard unsaved changes?") },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        showDiscardDialog = false
                        isEditMode = false
                        companyDraft = job.companyName
                        titleDraft = job.jobTitle
                        descriptionDraft = job.jobDescription
                        if (discardAction == DiscardAction.NAVIGATE_BACK) {
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = { showDiscardDialog = false }
                ) {
                    Text("Keep editing")
                }
            }
        )
    }
}

@Composable
private fun LastSyncIssueCard(syncFailure: JobSyncFailureInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Last Sync Issue",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = syncFailure.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Text(
                    text = "Updated ${formatTimestampWithRelative(syncFailure.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusChipLarge(
    status: JobStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (status) {
        JobStatus.RESUME_REJECTED -> JobResumeRejectedRed
        JobStatus.INTERVIEW_REJECTED -> JobInterviewRejectedRed
        JobStatus.INTERVIEW -> JobInterviewingYellow
        JobStatus.INTERVIEWING -> JobInterviewingYellow
        JobStatus.OFFER -> JobOfferGreen
        JobStatus.APPLIED -> JobAppliedBlue
        JobStatus.SAVED -> JobSavedGray
    }

    val contentColor = Color.White
    val statusDescription = stringResource(R.string.sem_status_change, status.displayName())

    FilterChip(
        selected = true,
        onClick = onClick,
        label = {
            Text(
                text = status.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 100.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = containerColor,
            containerColor = containerColor,
            selectedLabelColor = contentColor
        ),
        border = null,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.semantics {
            contentDescription = statusDescription
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatTimestampWithRelative(timestamp: Long): String {
    val absolute = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(timestamp))
    val diff = System.currentTimeMillis() - timestamp
    val relative = when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        diff < 604_800_000 -> "${diff / 86_400_000} days ago"
        else -> "${diff / 604_800_000} weeks ago"
    }
    return "$absolute ($relative)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailsMissingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Job no longer available.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun JobDetailsScreenPreview() {
    LinkedIn_Job_TrackerTheme {
        JobDetailsScreen(
            job = JobEntity(
                id = "job-details-preview",
                companyName = "Google",
                jobUrl = "https://careers.google.com/jobs/results/12345/",
                jobDescription = "Software Engineer position at Google. This is a sample job description. The ideal candidate will have experience with Kotlin, Jetpack Compose, and Android development. They should also be familiar with modern Android development practices and have a passion for creating beautiful and performant user interfaces.",
                status = JobStatus.APPLIED,
                createdAt = System.currentTimeMillis()
            ),
            syncFailure = JobSyncFailureInfo(
                jobId = "job-details-preview",
                companyName = "Google",
                jobTitle = "",
                jobUrl = "https://careers.google.com/jobs/results/12345/",
                reason = "pushJob: HTTP 409: duplicate key value violates unique constraint",
                updatedAt = System.currentTimeMillis() - 120_000L
            ),
            onNavigateBack = {},
            onStatusChange = {},
            onOpenUrl = {},
            onSaveEdit = { _, _, _ -> JobViewModel.JobEditSaveResult.Success() },
            onDelete = {}
        )
    }
}
