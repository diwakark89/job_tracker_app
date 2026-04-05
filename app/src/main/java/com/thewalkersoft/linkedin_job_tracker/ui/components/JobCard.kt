package com.thewalkersoft.linkedin_job_tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.data.JobEntity
import com.thewalkersoft.linkedin_job_tracker.data.JobStatus
import com.thewalkersoft.linkedin_job_tracker.data.displayName
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.ui.model.JobSyncDotState
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeGreen
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeGreenBg
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeGreenBgDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeGreenDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeRed
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeRedBg
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeRedBgDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeRedDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeYellow
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeYellowBg
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeYellowBgDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.SyncBadgeYellowDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiSubtleCardDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiSubtleCardLight
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
    val isDark = isSystemInDarkTheme()
    val cardDescription = stringResource(
        R.string.sem_job_card_status,
        job.companyName,
        job.status.displayName()
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = cardDescription
            }
            .clickable { onJobClick() },
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) UiSubtleCardDark else UiSubtleCardLight
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = job.companyName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (job.jobTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = job.jobTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatTimestamp(job.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = job.jobDescription,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncBadge(syncDotState)
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
private fun SyncBadge(state: JobSyncDotState) {
    val isDark = isSystemInDarkTheme()

    val (label, bgColor, textColor) = when (state) {
        JobSyncDotState.GREEN -> Triple(
            "Synced",
            if (isDark) SyncBadgeGreenBgDark else SyncBadgeGreenBg,
            if (isDark) SyncBadgeGreenDark else SyncBadgeGreen
        )
        JobSyncDotState.YELLOW -> Triple(
            "Pending",
            if (isDark) SyncBadgeYellowBgDark else SyncBadgeYellowBg,
            if (isDark) SyncBadgeYellowDark else SyncBadgeYellow
        )
        JobSyncDotState.RED -> Triple(
            "Not synced",
            if (isDark) SyncBadgeRedBgDark else SyncBadgeRedBg,
            if (isDark) SyncBadgeRedDark else SyncBadgeRed
        )
    }
    val syncDescription = stringResource(R.string.sem_sync_state, label)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bgColor,
        modifier = Modifier.semantics {
            contentDescription = syncDescription
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
        JobStatus.RESUME_REJECTED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobResumeRejectedRed
        JobStatus.INTERVIEW_REJECTED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewRejectedRed
        JobStatus.INTERVIEW -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewingYellow
        JobStatus.INTERVIEWING -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobInterviewingYellow
        JobStatus.OFFER -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobOfferGreen
        JobStatus.APPLIED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobAppliedBlue
        JobStatus.SAVED -> com.thewalkersoft.linkedin_job_tracker.ui.theme.JobSavedGray
    }

    val contentColor = Color.White
    val statusDescription = stringResource(R.string.sem_status_change, status.displayName())

    FilterChip(
        selected = false,
        onClick = onClick,
        label = {
            Text(
                text = status.displayName(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 90.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = containerColor,
            labelColor = contentColor,
            selectedLabelColor = contentColor
        ),
        border = null,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.semantics {
            contentDescription = statusDescription
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
