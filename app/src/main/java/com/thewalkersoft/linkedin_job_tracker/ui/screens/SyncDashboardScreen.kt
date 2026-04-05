package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncFailureInfo
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiPillBlue
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiPillOnBlue
import com.thewalkersoft.linkedin_job_tracker.viewmodel.JobViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDashboardScreen(
    cloudHealth: String,
    queueStatus: Int,
    lastSyncTime: Long?,
    failedJobs: List<JobSyncFailureInfo>,
    isManualSyncRunning: Boolean,
    manualSyncUiState: JobViewModel.ManualSyncUiState,
    onNavigateBack: () -> Unit,
    onManualSyncClick: () -> Unit,
    onJobClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sync Dashboard", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Last Sync Status Card
            item {
                LastSyncStatusCard(lastSyncTime = lastSyncTime)
            }

            // Current Sync Status
            item {
                CurrentSyncStatusCard(
                    cloudHealth = cloudHealth,
                    isManualSyncRunning = isManualSyncRunning,
                    onManualSyncClick = onManualSyncClick
                )
            }

            // Queue Status Card
            item {
                QueueStatusCard(queueStatus = queueStatus)
            }

            if (failedJobs.isNotEmpty()) {
                item {
                    FailedJobsCard(failedJobs = failedJobs, onJobClick = onJobClick)
                }
            }

            // Active Syncs (if running)
            if (isManualSyncRunning) {
                item {
                    ActiveSyncCard(manualSyncUiState = manualSyncUiState)
                }
            }

            // Sync Statistics Card
            item {
                SyncStatisticsCard(manualSyncUiState = manualSyncUiState)
            }

            // Helpful Info
            item {
                InfoCard()
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FailedJobsCard(
    failedJobs: List<JobSyncFailureInfo>,
    onJobClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Jobs with Sync Issues",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "These jobs have the most recent stored sync failure reason.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            failedJobs.forEachIndexed { index, failedJob ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onJobClick(failedJob.jobId) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = failedJob.companyName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }

                    if (failedJob.jobTitle.isNotBlank()) {
                        Text(
                            text = failedJob.jobTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f)
                        )
                    }

                    Text(
                        text = failedJob.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Text(
                        text = "Updated ${formatDetailedTime(failedJob.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f)
                    )
                }

                if (index != failedJobs.lastIndex) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.14f))
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun LastSyncStatusCard(lastSyncTime: Long?) {
    val synced = lastSyncTime != null
    val cardDescription = stringResource(R.string.sem_last_sync_information)
    val lastSyncStateDescription = if (synced) {
        stringResource(R.string.sem_last_sync_has_previous)
    } else {
        stringResource(R.string.sem_last_sync_never)
    }
    val syncedContentDescription = stringResource(R.string.cd_synced)
    val timeLabel = if (lastSyncTime != null) {
        val date = Date(lastSyncTime)
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
        val relativeTime = getRelativeTimeString(lastSyncTime)
        "${sdf.format(date)} ($relativeTime)"
    } else {
        "Never synced"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = cardDescription
                stateDescription = lastSyncStateDescription
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last Sync",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = syncedContentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun CurrentSyncStatusCard(
    cloudHealth: String,
    isManualSyncRunning: Boolean,
    onManualSyncClick: () -> Unit
) {
    val currentSyncDescription = stringResource(R.string.sem_current_sync_status)
    val syncStateDescription = if (isManualSyncRunning) {
        stringResource(R.string.sem_sync_in_progress)
    } else {
        stringResource(R.string.sem_sync_idle)
    }
    val healthLabel = cloudHealth.replace("Cloud: ", "")
    val isErrorState = healthLabel.contains("error", ignoreCase = true)
    val statusIcon = when {
        isManualSyncRunning -> Icons.Default.Schedule
        isErrorState -> Icons.Default.Close
        else -> Icons.Default.Check
    }
    val statusIconTint = when {
        isManualSyncRunning -> MaterialTheme.colorScheme.tertiary
        isErrorState -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = currentSyncDescription
                stateDescription = syncStateDescription
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isManualSyncRunning)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusIconTint
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = healthLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isManualSyncRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onManualSyncClick,
                enabled = !isManualSyncRunning,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.semantics { role = Role.Button },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = UiPillBlue,
                    contentColor = UiPillOnBlue
                )
            ) {
                Text(
                    text = if (isManualSyncRunning) "Syncing..." else "Run Sync",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QueueStatusCard(queueStatus: Int) {
    val queueStatusDescription = stringResource(R.string.sem_queue_status)
    val statusColor = when {
        queueStatus == 0 -> Color(0xFF2E7D32) // Green
        queueStatus < 3 -> Color(0xFFF9A825) // Orange/Yellow
        else -> Color(0xFFC62828) // Red
    }

    val statusLabel = when {
        queueStatus == 0 -> "All synced"
        queueStatus == 1 -> "1 operation queued"
        else -> "$queueStatus operations queued"
    }

    val statusDescription = when {
        queueStatus == 0 -> stringResource(R.string.sem_queue_empty)
        queueStatus == 1 -> stringResource(R.string.sem_queue_one_operation)
        else -> stringResource(R.string.sem_queue_operations, queueStatus)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = queueStatusDescription
                stateDescription = statusDescription
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (queueStatus == 0) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Queue Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (queueStatus == 0) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = statusColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = queueStatus.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActiveSyncCard(manualSyncUiState: JobViewModel.ManualSyncUiState) {
    val activeSyncDescription = stringResource(R.string.sem_active_sync_progress)
    val activeSyncStateDescription = stringResource(
        R.string.sem_active_sync_state,
        manualSyncUiState.acknowledged,
        manualSyncUiState.attempted
    )
    val progress = if (manualSyncUiState.attempted > 0) {
        manualSyncUiState.acknowledged.toFloat() / manualSyncUiState.attempted.toFloat()
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = activeSyncDescription
                stateDescription = activeSyncStateDescription
            },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Active Sync Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Acknowledged
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Acknowledged",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${manualSyncUiState.acknowledged}/${manualSyncUiState.attempted}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Failed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = manualSyncUiState.failed.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Pulled Updates
            if (manualSyncUiState.pulledUpdates > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pulled Updates",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = manualSyncUiState.pulledUpdates.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = { progress }
            )
        }
    }
}

@Composable
private fun SyncStatisticsCard(manualSyncUiState: JobViewModel.ManualSyncUiState) {
    val syncStatisticsDescription = stringResource(R.string.sem_sync_statistics)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = syncStatisticsDescription },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Attempted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Attempted:", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = manualSyncUiState.attempted.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Acknowledged
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Acknowledged:", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = manualSyncUiState.acknowledged.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Failed
            if (manualSyncUiState.failed > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Failed:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = manualSyncUiState.failed.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Pulled
            if (manualSyncUiState.pulledUpdates > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Pulled Updates:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = manualSyncUiState.pulledUpdates.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    val aboutSyncDescription = stringResource(R.string.sem_about_sync_information)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = aboutSyncDescription },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ℹ️ About Sync",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• Jobs are synced bidirectionally with the cloud\n" +
                        "• Queued operations retry automatically every 60 minutes\n" +
                        "• Failed syncs are shown in red\n" +
                        "• Tap 'Sync' button to manually trigger a sync now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private fun getRelativeTimeString(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} hours ago"
        diff < 604_800_000 -> "${diff / 86_400_000} days ago"
        else -> "${diff / 604_800_000} weeks ago"
    }
}

private fun formatDetailedTime(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
    return "${sdf.format(date)} (${getRelativeTimeString(timestamp)})"
}
