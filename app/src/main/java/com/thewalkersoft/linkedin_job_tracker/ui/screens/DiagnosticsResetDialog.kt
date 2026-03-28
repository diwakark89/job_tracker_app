package com.thewalkersoft.linkedin_job_tracker.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Two-step destructive diagnostics reset dialog.
 *
 * Step 1 – confirms clearing queue + metrics.
 * Step 2 – asks whether to also cancel the active WorkManager sync job.
 *           Declining defers the reset until the job finishes naturally.
 *
 * Visible only in DEBUG builds (controlled by the caller via [BuildConfig.DEBUG]).
 *
 * @param step  0 = hidden, 1 = step 1 dialog, 2 = step 2 dialog.
 */
@Composable
fun DiagnosticsResetDialog(
    step: Int,
    onConfirmStep1: () -> Unit,
    onConfirmCancelWorker: () -> Unit,
    onDeclineCancelWorker: () -> Unit,
    onDismiss: () -> Unit
) {
    when (step) {
        1 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Reset Queue & Metrics?",
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "This permanently clears the offline outbox queue and all metric " +
                        "counters. Unsynced operations will be lost.\n\n" +
                        "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmStep1) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )

        2 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Cancel Active Sync Job?",
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "A background sync job is currently running.\n\n" +
                        "• Cancel Job & Reset Now — stops the job immediately then resets.\n" +
                        "• Wait & Reset After — defers the reset until the job finishes naturally."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmCancelWorker) {
                    Text("Cancel Job & Reset Now", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeclineCancelWorker) {
                    Text("Wait & Reset After")
                }
            }
        )
    }
}

