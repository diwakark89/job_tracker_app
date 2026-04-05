package com.thewalkersoft.linkedin_job_tracker.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thewalkersoft.linkedin_job_tracker.R
import com.thewalkersoft.linkedin_job_tracker.ui.theme.CloudIconBlue
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiPillBlue
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiPillOnBlue
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiSubtleCardDark
import com.thewalkersoft.linkedin_job_tracker.ui.theme.UiSubtleCardLight
import com.thewalkersoft.linkedin_job_tracker.ui.theme.WarningAmber
import com.thewalkersoft.linkedin_job_tracker.ui.theme.WarningAmberDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A card-style sync status banner that displays cloud health, sync state,
 * queue count, last-sync time, a Sync button, and a settings gear icon.
 *
 * Matches the design: cloud icon (left) → two-line status text → Sync button → gear icon.
 */
@Composable
fun SyncStatusBanner(
    cloudHealth: String,
    isManualSyncRunning: Boolean,
    queueStatus: Int,
    lastSyncTime: Long?,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val syncLabel = if (isManualSyncRunning) "Running" else "Idle"
    val formattedTime = lastSyncTime?.let {
        SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "Never"
    val hasError = cloudHealth.contains("Error", ignoreCase = true)
    val warningTint = if (isDark) WarningAmberDark else WarningAmber
    val bannerColor = if (isDark) UiSubtleCardDark else UiSubtleCardLight

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = bannerColor,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = stringResource(R.string.cd_cloud_status),
                    tint = CloudIconBlue,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cloud $cloudHealth",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasError) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.cd_cloud_error),
                            tint = warningTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sync $syncLabel | Queue $queueStatus | Last $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onSyncClick,
                enabled = !isManualSyncRunning,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = UiPillBlue,
                    contentColor = UiPillOnBlue
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Text(
                    text = if (isManualSyncRunning) "Syncing..." else "Sync",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            ) {
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_sync_settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

