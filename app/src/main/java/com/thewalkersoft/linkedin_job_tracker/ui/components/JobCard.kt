package com.thewalkersoft.linkedin_job_tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobCard(
    job: JobEntity,
    onStatusChange: (JobStatus) -> Unit,
    modifier: Modifier = Modifier,
    syncDotState: JobSyncDotState = JobSyncDotState.RED,
    onJobClick: () -> Unit = {}
) {
    var showStatusMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onJobClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Company Name
            Text(
                text = job.companyName,
                style = MaterialTheme.typography.titleLarge
            )

            // Job Title
            if (job.jobTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = job.jobTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp
            Text(
                text = formatTimestamp(job.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Description
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = job.jobDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SyncDotIndicator(syncDotState)
                Box {
                    StatusChip(
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
        }
    }
}

@Composable
private fun SyncDotIndicator(state: JobSyncDotState) {
    val (label, color) = when (state) {
        JobSyncDotState.GREEN -> "Synced" to Color(0xFF2E7D32)
        JobSyncDotState.YELLOW -> "Pending" to Color(0xFFF9A825)
        JobSyncDotState.RED -> "Never synced" to Color(0xFFC62828)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusChip(
    status: JobStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (status) {
        JobStatus.RESUME_REJECTED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobResumeRejectedRed.copy(alpha = 0.35f)
        JobStatus.INTERVIEW_REJECTED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewRejectedRed.copy(alpha = 0.35f)
        JobStatus.INTERVIEW -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewingYellow.copy(alpha = 0.35f)
        JobStatus.APPLIED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobAppliedBlue.copy(alpha = 0.35f)
        JobStatus.SAVED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobSavedGray.copy(alpha = 0.35f)
    }

    val contentColor = Color.White

    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = status.displayName(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 90.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        ),
        modifier = modifier
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
